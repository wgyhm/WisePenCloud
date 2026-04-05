package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.ChangeType;
import com.oriole.wisepen.common.core.domain.enums.ConsumerType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberGetGroupTokenResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberGetTokenResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberGetTransactionsResponse;
import com.oriole.wisepen.user.api.enums.VoucherStatus;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.*;
import com.oriole.wisepen.user.event.GroupTokenConsumeEvent;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.*;
import com.oriole.wisepen.user.service.GroupMemberService;
import com.oriole.wisepen.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

	private final ApplicationEventPublisher eventPublisher;

	private final GroupMapper groupMapper;
	private final GroupMemberMapper groupMemberMapper;
	private final UserService userService;
	private final UserMapper userMapper;
	private final RedisCacheManager redisCacheManager;
	private final UserWalletsMapper userWalletsMapper;
	private final TokenRecordMapper tokenRecordMapper;
	private final VoucherMapper voucherMapper;

	@Override
	public Map<String, Integer> getGroupRoleMapByUserId(Long userId) {
		List<GroupMemberEntity> members = groupMemberMapper.selectList(
				new LambdaQueryWrapper<GroupMemberEntity>()
						.eq(GroupMemberEntity::getUserId, userId)
						.select(GroupMemberEntity::getGroupId, GroupMemberEntity::getRole)
		);
		if (CollectionUtils.isEmpty(members)) {
			return Collections.emptyMap();
		}
		return members.stream()
				.collect(Collectors.toMap(
						member -> String.valueOf(member.getGroupId()),
						member -> member.getRole().getCode()
				));
	}

	@Override
	public void joinGroup(Long groupId, Long userId, GroupRoleType groupRoleType) {
		GroupMemberEntity member = GroupMemberEntity.builder()
				.groupId(groupId).userId(userId)
				.role(groupRoleType).joinTime(new Date())
				.tokenLimit(0).tokenUsed(0)
				.build();
		groupMemberMapper.insert(member);

		// 更新 Redis
		redisCacheManager.updateGroupRoleMapInSession(userId, groupId, groupRoleType);
	}

	@Override
	public void quitGroup(GroupMemberQuitRequest req, Long userId, GroupRoleType opGroupRoleType) {
		if (GroupRoleType.OWNER.equals(opGroupRoleType)) {
			throw new ServiceException(GroupErrorCode.OWNER_QUIT_GROUP); // 群主不可直接退群
		}

		LambdaQueryWrapper<GroupMemberEntity> deleteWrapper = new LambdaQueryWrapper<>();
		deleteWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.eq(GroupMemberEntity::getUserId, userId);
		groupMemberMapper.delete(deleteWrapper);

		// 更新 Redis
		redisCacheManager.updateGroupRoleMapInSession(userId, req.getGroupId(), GroupRoleType.NOT_MEMBER);
	}

	@Override
	public void kickGroupMembers(GroupMemberKickRequest req, Long opUserId, GroupRoleType opGroupRoleType) {
		Set<Long> targetUserIdSet = req.getTargetUserIds().stream()
				.filter(id -> !id.equals(opUserId))
				.collect(Collectors.toSet()); // 不能踢自己

		if (targetUserIdSet.isEmpty()) {
			throw new ServiceException(GroupErrorCode.TARGET_MEMBER_NOT_EXIST);
		}

		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, targetUserIdSet);
		List<GroupMemberEntity> targetMembers = groupMemberMapper.selectList(wrapper);

		if (targetMembers.isEmpty()) {
			throw new ServiceException(GroupErrorCode.TARGET_MEMBER_NOT_EXIST);
		}

		List<Long> validUserIdsToDelete = new ArrayList<>(); // 收集符合踢出条件的 userId

		for (GroupMemberEntity target : targetMembers) {
			// 权限数值越小权限越大 (如 OWNER=1, ADMIN=2, MEMBER=3)，操作者的权限必须严格高于被踢者
			if (opGroupRoleType.getCode() < target.getRole().getCode()) {
				validUserIdsToDelete.add(target.getUserId());
			}
		}

		if (validUserIdsToDelete.isEmpty()) {
			throw new ServiceException(GroupErrorCode.TARGET_MEMBER_NOT_EXIST);
		}

		LambdaQueryWrapper<GroupMemberEntity> deleteWrapper = new LambdaQueryWrapper<>();
		deleteWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, validUserIdsToDelete);
		groupMemberMapper.delete(deleteWrapper);

		// 更新 Redis
		validUserIdsToDelete.forEach(targetId ->
				redisCacheManager.updateGroupRoleMapInSession(targetId, req.getGroupId(), GroupRoleType.NOT_MEMBER)
		);
	}

	@Override
	public GroupMemberDetailResponse getGroupMemberInfoByUserId(Long groupId, Long userId){
		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, groupId)
				.eq(GroupMemberEntity::getUserId, userId);

		GroupMemberEntity memberEntity = groupMemberMapper.selectOne(wrapper);

		if (memberEntity == null) {
			throw new ServiceException(GroupErrorCode.TARGET_MEMBER_NOT_EXIST);
		}

		UserDisplayBase userInfo = userService.getUserDisplayInfoByIds(Set.of(userId)).get(userId);

		GroupMemberDetailResponse resp = new GroupMemberDetailResponse();
		BeanUtil.copyProperties(memberEntity, resp);
		resp.setMemberId(memberEntity.getUserId());
		resp.setMemberInfo(userInfo);
		return resp;
	}

	@Override
	public PageResult<GroupMemberDetailResponse> getGroupMemberList(Long groupId, int page, int size) {
		Page<GroupMemberEntity> pageParam = new Page<>(page, size);
		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, groupId)
				// 按角色升序(OWNER排前面)，再按加入时间降序(新入群的在前面)
				.orderByAsc(GroupMemberEntity::getRole)
				.orderByDesc(GroupMemberEntity::getJoinTime);

		IPage<GroupMemberEntity> memberPage = groupMemberMapper.selectPage(pageParam, wrapper);

		// 提取当前页所有的 userId
		Set<Long> userIds = memberPage.getRecords().stream()
				.map(GroupMemberEntity::getUserId)
				.collect(Collectors.toSet());

		PageResult<GroupMemberDetailResponse> pageResult = new PageResult<>(memberPage.getTotal(), page, size);

		Map<Long, UserDisplayBase> userMap = userService.getUserDisplayInfoByIds(userIds);

		List<GroupMemberDetailResponse> records = memberPage.getRecords().stream().map(memberEntity -> {
			GroupMemberDetailResponse resp = new GroupMemberDetailResponse();
			BeanUtil.copyProperties(memberEntity, resp);
			resp.setMemberId(memberEntity.getUserId());
			resp.setMemberInfo(userMap.get(memberEntity.getUserId()));
			return resp;
		}).collect(Collectors.toList());

		pageResult.addAll(records);
		return pageResult;
	}

	@Override
	public PageResult<GroupMemberGetGroupTokenResponse> getAllGroupToken(Long userId, Integer page, Integer size) {
		Page<GroupMemberEntity> pageParam = new Page<>(page, size);
		LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(GroupMemberEntity::getUserId, userId)
				.orderByAsc(GroupMemberEntity::getRole)
				.orderByDesc(GroupMemberEntity::getJoinTime);
		IPage<GroupMemberEntity> memberPage = groupMemberMapper.selectPage(pageParam, wrapper);

		List<Long> groupIds = memberPage.getRecords().stream()
				.map(GroupMemberEntity::getGroupId)
				.collect(Collectors.toList());
		PageResult<GroupMemberGetGroupTokenResponse> pageResult = new PageResult<>(memberPage.getTotal(), page, size);
		if (groupIds.isEmpty()) {
			return pageResult;
		}

		Map<Long, GroupEntity> groupMap = groupMapper.selectBatchIds(groupIds).stream()
				.collect(Collectors.toMap(GroupEntity::getGroupId, group -> group));

		List<GroupMemberGetGroupTokenResponse> records = memberPage.getRecords().stream().map(memberEntity -> {
			GroupMemberGetGroupTokenResponse resp = new GroupMemberGetGroupTokenResponse();
			GroupEntity group = groupMap.get(memberEntity.getGroupId());
			if (group != null) {
				BeanUtil.copyProperties(group, resp);
			}
			BeanUtil.copyProperties(memberEntity, resp);
			return resp;
		}).collect(Collectors.toList());

		pageResult.addAll(records);
		return pageResult;
	}

	@Override
	public void updateGroupMemberRole(GroupMemberRoleUpdateRequest req, Long opUserId) {
		Set<Long> targetUserIdSet = req.getTargetUserIds().stream()
				.filter(id -> !id.equals(opUserId))
				.collect(Collectors.toSet()); // 不能更新自己的权限

		if (targetUserIdSet.isEmpty()) {
			throw new ServiceException(GroupErrorCode.TARGET_MEMBER_NOT_EXIST);
		}

		LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, targetUserIdSet);
		List<GroupMemberEntity> existMembers = groupMemberMapper.selectList(queryWrapper);

		if (existMembers.isEmpty()) {
			throw new ServiceException(GroupErrorCode.TARGET_MEMBER_NOT_EXIST);
		}

		List<Long> actualUserIdsToUpdate = existMembers.stream()
				.map(GroupMemberEntity::getUserId)
				.collect(Collectors.toList());

		LambdaUpdateWrapper<GroupMemberEntity> wrapper = new LambdaUpdateWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, actualUserIdsToUpdate)
				.set(GroupMemberEntity::getRole, req.getRole());

		groupMemberMapper.update(null, wrapper);

		actualUserIdsToUpdate.forEach(targetId ->
						redisCacheManager.updateGroupRoleMapInSession(targetId, req.getGroupId(), req.getRole())
		);
	}

	@Override
	public void removeAllGroupMembers(Long groupId) {
		LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(GroupMemberEntity::getGroupId, groupId);
		List<GroupMemberEntity> allMembers = groupMemberMapper.selectList(queryWrapper);

		groupMemberMapper.delete(queryWrapper);

		// 3. 精准同步：遍历所有被踢出的成员，将他们的 Redis 状态置为 NOT_MEMBER
		allMembers.forEach(member ->
				redisCacheManager.updateGroupRoleMapInSession(member.getUserId(), groupId, GroupRoleType.NOT_MEMBER)
		);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateGroupMemberTokenUsed(Long groupId, Long userId, Integer usedToken) {
		// 联动扣除群组大盘资金池
		eventPublisher.publishEvent(new GroupTokenConsumeEvent(this, groupId, usedToken));

		UpdateWrapper<GroupMemberEntity> wrapper = new UpdateWrapper<>();
		wrapper.eq("group_id", groupId)
				.eq("user_id", userId)
				.setSql("token_used = token_used + " + usedToken);

		groupMemberMapper.update(null, wrapper);

		// 查询个人最新额度消耗情况
		LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(GroupMemberEntity::getGroupId, groupId)
				.eq(GroupMemberEntity::getUserId, userId);
		GroupMemberEntity member = groupMemberMapper.selectOne(queryWrapper);

		// 如果个人已用量超过了上限，触发个人熔断
		if (member != null && member.getTokenUsed() >= member.getTokenLimit()) {
			redisCacheManager.blockGroupMemberChat(groupId, userId);
			log.warn("用户 {} 在群组 {} 的个人配额已爆仓，已用: {}, 上限: {}。已触发 Redis 熔断",
					userId, groupId, member.getTokenUsed(), member.getTokenLimit());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateGroupMemberTokenLimit(GroupMemberTokenLimitUpdateRequest req) {
		GroupEntity group = groupMapper.selectById(req.getGroupId());
		if (group == null) throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);

		if (GroupType.NORMAL_GROUP.equals(group.getGroupType())) {
			throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
		}

		// 批量更新额度，并防降额击穿
		LambdaUpdateWrapper<GroupMemberEntity> wrapper = new LambdaUpdateWrapper<>();
		wrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, req.getTargetUserIds())
				// 新给定的上限，绝对不能小于该用户已经用掉的额度
				.le(GroupMemberEntity::getTokenUsed, req.getNewTokenLimit())
				.set(GroupMemberEntity::getTokenLimit, req.getNewTokenLimit());

		int rows = groupMemberMapper.update(null, wrapper);

		if (rows == 0) {
			throw new ServiceException(GroupErrorCode.LIMIT_CANNOT_BE_LOWER_THAN_USED);
		}

		LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
				.in(GroupMemberEntity::getUserId, req.getTargetUserIds())
				// 必须严格小于 (<)！如果等于，说明额度依然是满的，不能解封
				.lt(GroupMemberEntity::getTokenUsed, req.getNewTokenLimit());

		List<GroupMemberEntity> validMembersToUnblock = groupMemberMapper.selectList(queryWrapper);

		validMembersToUnblock.forEach(member ->
				redisCacheManager.unblockGroupMemberChat(req.getGroupId(), member.getUserId())
		);


	}

	@Override
	public GroupMemberGetTokenResponse getGroupToken(Long userId, Long groupId) {
		GroupEntity group =  groupMapper.selectById(groupId);
		return BeanUtil.copyProperties(group, GroupMemberGetTokenResponse.class);
	}

	public void calculateToken(TokenCalculateMessage message) {
		LambdaQueryWrapper<TokenRecordEntity> tokenRecordEntityLambdaQueryWrapper = new LambdaQueryWrapper<TokenRecordEntity>().eq(TokenRecordEntity::getTraceId, message.getTraceId());
		if (tokenRecordMapper.selectOne(tokenRecordEntityLambdaQueryWrapper) != null) return;
		Long userId = message.getUserId();
		Long groupId = message.getGroupId();
		int tokenRest=message.getUsageTokens()*message.getModelType().getRatio();
		eventPublisher.publishEvent(new GroupTokenConsumeEvent(this, groupId, tokenRest));

		if (groupId!=null) {
			// 因为用户可能没有登录，所以不查 redis
			GroupEntity group = groupMapper.selectById(groupId);
			if (group!=null&&group.getGroupType()!=GroupType.NORMAL_GROUP) {
				LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<GroupMemberEntity>()
						.eq(GroupMemberEntity::getGroupId,groupId).eq(GroupMemberEntity::getUserId,userId);
				GroupMemberEntity groupMember = groupMemberMapper.selectOne(queryWrapper);


				int usage=min(min(group.getTokenBalance(),groupMember.getTokenLimit()-groupMember.getTokenUsed()),message.getUsageTokens());

				tokenRest-=usage;
				if (usage>0) {
					UserEntity userProfile= userMapper.selectById(userId);
					//更新 tokenRecord， 此时 tokenCount 应该是负的
					TokenRecordEntity tokenRecordEntity = TokenRecordEntity.builder()
							.traceId(message.getTraceId()).tokenCount(-message.getUsageTokens()*message.getModelType().getRatio())
							.changeType(ChangeType.SPEND).ownerType(ConsumerType.GROUP)
							.createTime(LocalDateTime.now()).meta(message.getModelType().getDesc())
							.operatorName(userProfile.getRealName())
							.targetId(groupId)
							.build();
					tokenRecordMapper.insert(tokenRecordEntity);
					groupMember.setTokenUsed(groupMember.getTokenUsed() + usage);
					groupMemberMapper.updateById(groupMember);

					group.setTokenUsed(group.getTokenUsed() + usage);
					group.setTokenBalance(group.getTokenBalance() - usage);
					groupMapper.updateById(group);

					if (groupMember != null && groupMember.getTokenUsed() >= groupMember.getTokenLimit()) {
						redisCacheManager.blockGroupMemberChat(groupId, userId);
						log.warn("用户 {} 在群组 {} 的个人配额已爆仓，已用: {}, 上限: {}。已触发 Redis 熔断",
								userId, groupId, groupMember.getTokenUsed(), groupMember.getTokenLimit());
					}
				}
			}
		}

		if (tokenRest<=0) {
			return;
		}
		//传入的时候要做对用户是否还有余额的检查？
		UserTokenPoolEntity user=userWalletsMapper.selectById(message.getUserId());
		user.setTokenBalance(user.getTokenBalance() - tokenRest);
		user.setTokenUsed(user.getTokenUsed()+tokenRest);
		userWalletsMapper.updateById(user);

		TokenRecordEntity tokenRecordEntity = TokenRecordEntity.builder()
				.traceId(message.getTraceId()).tokenCount(-message.getUsageTokens()*message.getModelType().getRatio())
				.changeType(ChangeType.SPEND).ownerType(ConsumerType.USER)
				.createTime(LocalDateTime.now()).meta(message.getModelType().getDesc())
				.targetId(userId)
				.build();
		tokenRecordMapper.insert(tokenRecordEntity);
	}
	private void validateGroupType(Long groupId) {
		GroupEntity group = groupMapper.selectById(groupId);
		if (GroupType.ADVANCED_GROUP!=group.getGroupType()) {
			throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
		}
	}
	@Override
	public GroupMemberGetTokenResponse getWalletInfo(ConsumerType targetType, Long targetId) {
		if (targetType==ConsumerType.USER) {
			UserTokenPoolEntity user=userWalletsMapper.selectById(targetId);
			return BeanUtil.copyProperties(user,GroupMemberGetTokenResponse.class);
		}
		else if (targetType==ConsumerType.GROUP) {
			GroupEntity group = groupMapper.selectById(targetId);;
			return BeanUtil.copyProperties(group,GroupMemberGetTokenResponse.class);
		}
		return null;
	}

	@Override
	@Transactional
	public void redeemVoucher(ConsumerType targetType, Long targetId, String code) {
		if (targetType==ConsumerType.GROUP) validateGroupType(targetId);

		LambdaQueryWrapper<VoucherEntity> wrapper = new LambdaQueryWrapper<VoucherEntity>().eq(VoucherEntity::getCode, code);
		VoucherEntity voucher = voucherMapper.selectOne(wrapper);
		if (voucher==null) throw new ServiceException(GroupErrorCode.VOUCHER_NOT_EXIST);
		if (voucher.getStatus() != VoucherStatus.UNUSED) throw new ServiceException(GroupErrorCode.VOUCHER_IS_USED);
		Date now = new Date();
		if (voucher.getExpireTime() != null && !now.before(voucher.getExpireTime())) {
			throw new ServiceException(GroupErrorCode.VOUCHER_IS_EXPIRED);
		}

		Long traceId = IdWorker.getId();
		String codeMeta = "****-****-****-" + code.substring(code.length() - 4);
		// 更新 Voucher，保证幂等
		int row = voucherMapper.update(
				null,
				new LambdaUpdateWrapper<VoucherEntity>()
						.eq(VoucherEntity::getVoucherId, voucher.getVoucherId())
						.eq(VoucherEntity::getStatus, VoucherStatus.UNUSED)
						.set(VoucherEntity::getStatus, VoucherStatus.USED)
		);
		if (row == 0) {
			throw new ServiceException(GroupErrorCode.VOUCHER_IS_USED);
		}
		// 更新 tokenRecord
		TokenRecordEntity tokenRecordEntity = TokenRecordEntity.builder()
				.traceId(traceId).tokenCount(voucher.getAmount())
				.changeType(ChangeType.REFILL).ownerType(targetType)
				.createTime(LocalDateTime.now()).meta(codeMeta)
				.targetId(targetId)
				.build();
		tokenRecordMapper.insert(tokenRecordEntity);

		// 更新个人 balance
		if (targetType==ConsumerType.GROUP) {
			LambdaUpdateWrapper<GroupEntity> updateWrapper = new LambdaUpdateWrapper<GroupEntity>()
					.eq(GroupEntity::getGroupId,targetId)
					.setSql("token_balance = token_balance + " + voucher.getAmount());
			groupMapper.update(null, updateWrapper);
		}
		else {
			LambdaUpdateWrapper<UserTokenPoolEntity> updateWrapper = new LambdaUpdateWrapper<UserTokenPoolEntity>()
					.eq(UserTokenPoolEntity::getUserId,targetId)
					.setSql("token_balance = token_balance + " + voucher.getAmount());
			userWalletsMapper.update(null, updateWrapper);
		}
	}

	@Override
	public PageResult<GroupMemberGetTransactionsResponse> getTransactions(ConsumerType targetType, Long targetId, Integer page, Integer size, ChangeType changeType) {
		if (targetType==ConsumerType.GROUP) validateGroupType(targetId);
		Page<TokenRecordEntity> pageParam = new Page<>(page, size);
		LambdaQueryWrapper<TokenRecordEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(TokenRecordEntity::getTargetId, targetId)
				.eq(TokenRecordEntity::getOwnerType, targetType)
				.eq(changeType != null, TokenRecordEntity::getChangeType, changeType)
				.orderByDesc(TokenRecordEntity::getCreateTime);
		IPage<TokenRecordEntity> transactionPage = tokenRecordMapper.selectPage(pageParam, wrapper);

		PageResult<GroupMemberGetTransactionsResponse> pageResult = new PageResult<>(transactionPage.getTotal(), page, size);
		if (CollectionUtils.isEmpty(transactionPage.getRecords())) {
			return pageResult;
		}
		List<GroupMemberGetTransactionsResponse> records = transactionPage.getRecords().stream()
				.map(record -> {
					GroupMemberGetTransactionsResponse resp = new GroupMemberGetTransactionsResponse();
					BeanUtil.copyProperties(record, resp);
					resp.setAmount((long) record.getTokenCount());
					return resp;
				})
				.collect(Collectors.toList());
		pageResult.addAll(records);
		return pageResult;
	}

	@Override
	public void exchangeTokenToOwner(Long userId, Long groupId, Integer amount) {
		GroupEntity group = groupMapper.selectById(groupId);
		if (GroupType.ADVANCED_GROUP!=group.getGroupType()) {
			throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
		}
		if (group.getTokenBalance()<amount) {
			throw new ServiceException(GroupErrorCode.LIMIT_CANNOT_BE_LOWER_THAN_USED);
		}
		LambdaUpdateWrapper<UserTokenPoolEntity> wrapper = new LambdaUpdateWrapper<UserTokenPoolEntity>()
				.eq(UserTokenPoolEntity::getUserId,userId)
				.setSql("token_balance = token_balance + " + amount);
		userWalletsMapper.update(null, wrapper);

		LambdaUpdateWrapper<GroupEntity> wrapper2 = new LambdaUpdateWrapper<GroupEntity>()
				.eq(GroupEntity::getGroupId,groupId)
				.setSql("token_balance = token_balance - " + amount);
		groupMapper.update(null, wrapper2);
	}

	@Override
	public void exchangeTokenToGroup(Long userId, Long groupId, Integer amount) {
		validateGroupType(groupId);
		UserTokenPoolEntity user = userWalletsMapper.selectById(userId);
		if (user.getTokenBalance()<amount) {
			throw new ServiceException(GroupErrorCode.LIMIT_CANNOT_BE_LOWER_THAN_USED);
		}
		LambdaUpdateWrapper<UserTokenPoolEntity> wrapper = new LambdaUpdateWrapper<UserTokenPoolEntity>()
				.eq(UserTokenPoolEntity::getUserId,userId)
				.setSql("token_balance = token_balance - " + amount);
		userWalletsMapper.update(null, wrapper);

		LambdaUpdateWrapper<GroupEntity> wrapper2 = new LambdaUpdateWrapper<GroupEntity>()
				.eq(GroupEntity::getGroupId,groupId)
				.setSql("token_balance = token_balance + " + amount);
		groupMapper.update(null, wrapper2);
	}
}
