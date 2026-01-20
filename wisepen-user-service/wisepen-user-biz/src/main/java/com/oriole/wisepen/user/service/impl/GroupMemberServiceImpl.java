package com.oriole.wisepen.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.domain.enums.GroupIdentity;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

	private final GroupMapper groupMapper;
	private final GroupMemberMapper groupMemberMapper;

	private GroupMember findGroupMemberByGroupId(Long userId, Long groupId){
		LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<GroupMember>()
				.eq(GroupMember::getGroupId, groupId)
				.eq(GroupMember::getUserId, userId);
		return groupMemberMapper.selectOne(queryWrapper);
	}

	@Override
	public void joinGroup(Long userId, String inviteCode) {
		LambdaQueryWrapper<Group> queryWrapper = new LambdaQueryWrapper<Group>()
				.eq(Group::getInviteCode, inviteCode);
		Group group=groupMapper.selectOne(queryWrapper);

		if (group==null||group.getDelFlag()==1){
			throw new ServiceException(ResultCode.GROUP_NOT_EXIST);
		}

		GroupMember groupMember=new GroupMember();
		groupMember.setGroupId(group.getId());
		groupMember.setUserId(userId);
		//初始设为 member
		groupMember.setRole(GroupIdentity.MEMBER.getCode());

		groupMemberMapper.insert(groupMember);
	}

	@Override
	public void becomeGroupOwner(Long userId, Long groupId) {
		GroupMember groupMember=new GroupMember();
		groupMember.setGroupId(groupId);
		groupMember.setUserId(userId);
		//新建时默认为 OWNER
		groupMember.setRole(GroupIdentity.OWNER.getCode());

		groupMemberMapper.insert(groupMember);
	}

	@Override
	public void leaveGroup(Long userId, Long groupId) {
		GroupMember groupMember=findGroupMemberByGroupId(userId,groupId);

		if (groupMember==null) {
			throw new ServiceException(ResultCode.MEMBER_NOT_IN_GROUP);
		}

		Group group = groupMapper.selectById(groupMember.getGroupId());
		if (group.getDelFlag()==1) {
			throw new ServiceException(ResultCode.GROUP_NOT_EXIST);
		}

		if (group.getOwnerId().equals(userId)) {
			throw new ServiceException(ResultCode.MEMBER_IS_OWNER);
		}

		groupMemberMapper.deleteById(groupMember);
	}

	@Override
	public void kickGroupMember(Long userId, Long groupId, Long targetUserId) {
		GroupMember groupMember=findGroupMemberByGroupId(userId,groupId);
		GroupMember targetGroupMember=findGroupMemberByGroupId(targetUserId,groupId);
		if (targetGroupMember==null) {
			throw new ServiceException(ResultCode.MEMBER_NOT_IN_GROUP);
		}

		//权限不够，权限高的操控权限低的
		if (targetGroupMember.getRole()<=groupMember.getRole()) {
			throw new ServiceException(ResultCode.PERMISSION_IS_LOWER);
		}

		groupMemberMapper.deleteById(targetGroupMember);
	}

	@Override
	public void getMemberList(Long groupId, int page, int size) {

	}

	@Override
	public void updateGroupMemberRole(Long groupId, Long targetUserId, int role) {

	}


}
