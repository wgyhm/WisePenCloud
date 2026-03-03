package com.oriole.wisepen.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.GetGroupMemberQuotasResponse;
import com.oriole.wisepen.user.api.domain.dto.GetGroupQuotasInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.GetMyGroupQuotasResponse;
import com.oriole.wisepen.user.component.InviteCodeGenerator;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.domain.entity.GroupMemberQuotas;
import com.oriole.wisepen.user.domain.entity.GroupWallets;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.GroupMemberQuotasMapper;
import com.oriole.wisepen.user.mapper.GroupWalletsMapper;
import com.oriole.wisepen.user.service.GroupQuotasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupQuotasServiceImpl implements GroupQuotasService {
	private final GroupMapper groupMapper;
	private final GroupMemberMapper groupMemberMapper;
	private final GroupWalletsMapper groupWalletsMapper;
	private final GroupMemberQuotasMapper groupMemberQuotasMapper;
	private final InviteCodeGenerator inviteCodeGenerator;

	private void validateGroup(Long groupId){
		Group group =  groupMapper.selectById(groupId);
		if (group==null) {
			throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
		}
		if (group.getType()==GroupType.NORMAL_GROUP) {
			throw new ServiceException(GroupErrorCode.NORMAL_GROUP);
		}
	}

	private GroupMember findGroupMemberByGroupId(Long userId, Long groupId){
		LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<GroupMember>()
				.eq(GroupMember::getGroupId, groupId)
				.eq(GroupMember::getUserId, userId);
		return groupMemberMapper.selectOne(queryWrapper);
	}

	@Override
	public GetGroupQuotasInfoResponse getGroupQuotas(Long groupId) {
		validateGroup(groupId);
		GroupWallets groupWallets = groupWalletsMapper.selectById(groupId);
		GetGroupQuotasInfoResponse response = new GetGroupQuotasInfoResponse();
		response.setQuotaUsed(groupWallets.getQuotaUsed());
		response.setQuotaLimit(groupWallets.getQuotaUsed());
		return response;
	}

	@Override
	public PageResult<GetGroupMemberQuotasResponse> getGroupMemberQuotas(Long groupId, Integer page, Integer size) {
//		validateGroup(groupId);
//		Group group = groupMapper.selectById(groupId);
//		if (group.getType() == GroupType.NORMAL_GROUP) {
//			throw new ServiceException(GroupErrorCode.NORMAL_GROUP);
//		}
		return null;
	}

	@Override
	public PageResult<GetMyGroupQuotasResponse> getMyGroupQuotas(Integer page, Integer size) {
		Long userId = Long.valueOf(SecurityContextHolder.getUserId());
		List<Long> groupIds = groupMemberMapper.selectGroupIdsByUserId(userId);
		if (groupIds == null || groupIds.isEmpty()) {
			return new PageResult<>(0, page, size);
		}

		Page<Group> mpPage = new Page<>(page, size);
		LambdaQueryWrapper<Group> groupQueryWrapper = new LambdaQueryWrapper<Group>()
				.in(Group::getId, groupIds)
				.in(Group::getType, GroupType.ADVANCED_GROUP, GroupType.MARKET_GROUP)
				.select(Group::getId, Group::getName, Group::getType);
		IPage<Group> groupRecords = groupMapper.selectPage(mpPage, groupQueryWrapper);

		PageResult<GetMyGroupQuotasResponse> pageResult = new PageResult<>(groupRecords.getTotal(), page, size);
		if (groupRecords.getRecords().isEmpty()) {
			return pageResult;
		}

		List<Long> pagedGroupIds = groupRecords.getRecords().stream()
				.map(Group::getId)
				.toList();
		List<GroupWallets> groupWalletsList = groupWalletsMapper.selectBatchIds(pagedGroupIds);
		Map<Long, GroupWallets> groupWalletsMap = groupWalletsList.stream()
				.collect(Collectors.toMap(GroupWallets::getId, gw -> gw, (a, b) -> a));

		List<GetMyGroupQuotasResponse> records = groupRecords.getRecords().stream().map(group -> {
			GetMyGroupQuotasResponse response = new GetMyGroupQuotasResponse();
			response.setGroupId(group.getId());
			response.setGroupName(group.getName());

			GroupWallets groupWallets = groupWalletsMap.get(group.getId());
			response.setQuotaUsed(groupWallets == null ? 0 : groupWallets.getQuotaUsed());
			response.setQuotaLimit(groupWallets == null ? 0 : groupWallets.getQuotaLimit());
			return response;
		}).toList();
		pageResult.setList(records);
		return pageResult;
	}

	private int getMemberQuotasMax(Long groupId, Long targetUserId) {
		GroupMember groupMember = findGroupMemberByGroupId(targetUserId, groupId);
		if (groupMember==null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}
		GroupMemberQuotas groupMemberQuotas = groupMemberQuotasMapper.selectById(groupMember.getId());
		return groupMemberQuotas.getQuotaUsed();
	}

	@Override
	public void updateMemberQuotas(Long groupId, Long targetUserId, Integer newLimit){
		GroupMember groupMember = findGroupMemberByGroupId(targetUserId, groupId);
		if (groupMember == null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}
		GroupMemberQuotas groupMemberQuotas = groupMemberQuotasMapper.selectById(groupMember.getId());
		if (groupMemberQuotas == null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}
		groupMemberQuotas.setQuotaLimit(newLimit);
		groupMemberQuotasMapper.updateById(groupMemberQuotas);
	}

	@Override
	public void updateMembersQuotas(Long groupId, List<Long> targetUserIds, Integer newLimit){
		validateGroup(groupId);
		Long userId = Long.valueOf(SecurityContextHolder.getUserId());
		GroupMember groupMember = findGroupMemberByGroupId(userId, groupId);
		if (groupMember == null || groupMember.getRole() != GroupRoleType.OWNER) {
			throw new ServiceException(GroupErrorCode.NO_PERMISSION);
		}

		int quotaMax=0;
		for (Long targetUserId : targetUserIds) {
			int quotas=getMemberQuotasMax(groupId, targetUserId);
			if (quotas>quotaMax) {
				quotaMax=quotas;
			}
		}
		if (quotaMax>newLimit) {
			throw new ServiceException(GroupErrorCode.LIMIT_IS_LOW);
		}
		for (Long targetUserId : targetUserIds) {
			updateMemberQuotas(groupId, targetUserId, newLimit);
		}
	}
}
