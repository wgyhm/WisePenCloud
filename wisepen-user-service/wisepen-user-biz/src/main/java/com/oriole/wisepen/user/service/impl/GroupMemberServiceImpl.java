package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberDetailResponse;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.*;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.*;
import com.oriole.wisepen.user.service.IGroupMemberService;
import com.oriole.wisepen.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.min;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements IGroupMemberService {

	private final GroupMemberMapper groupMemberMapper;
	private final IUserService userService;
	private final RedisCacheManager redisCacheManager;

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
				.role(groupRoleType).joinTime(LocalDateTime.now())
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
}
