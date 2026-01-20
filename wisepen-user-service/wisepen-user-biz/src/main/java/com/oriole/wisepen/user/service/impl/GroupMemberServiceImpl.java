package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.db.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.domain.dto.GroupQueryResp;
import com.oriole.wisepen.user.domain.dto.MemberListInNormalGroupQueryResp;
import com.oriole.wisepen.user.domain.dto.MemberListQueryResp;
import com.oriole.wisepen.user.domain.dto.PageResp;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.domain.entity.User;
import com.oriole.wisepen.user.domain.enums.GroupIdentity;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.UserMapper;
import com.oriole.wisepen.user.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

	private final GroupMapper groupMapper;
	private final GroupMemberMapper groupMemberMapper;
	private final UserMapper userMapper;

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
			throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
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
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}

		Group group = groupMapper.selectById(groupMember.getGroupId());
		if (group.getDelFlag()==1) {
			throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
		}

		if (group.getOwnerId().equals(userId)) {
			throw new ServiceException(GroupErrorCode.MEMBER_IS_OWNER);
		}

		groupMemberMapper.deleteById(groupMember);
	}

	@Override
	public void kickGroupMember(Long userId, Long groupId, Long targetUserId) {
		GroupMember groupMember=findGroupMemberByGroupId(userId,groupId);
		GroupMember targetGroupMember=findGroupMemberByGroupId(targetUserId,groupId);
		if (targetGroupMember==null) {
			throw new ServiceException(GroupErrorCode.MEMBER_NOT_IN_GROUP);
		}

		//权限不够，权限高的操控权限低的
		if (targetGroupMember.getRole()<=groupMember.getRole()) {
			throw new ServiceException(GroupErrorCode.PERMISSION_IS_LOWER);
		}

		groupMemberMapper.deleteById(targetGroupMember);
	}

	@Override
	public PageResp<MemberListQueryResp> getMemberList(Long groupId, Integer page, Integer size) {
		LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<GroupMember>()
				.eq(GroupMember::getGroupId,groupId)
				.select(GroupMember::getUserId);

		List<Long> ids=groupMemberMapper.selectList(wrapper)
				.stream()
				.map(GroupMember::getGroupId)
				.collect(Collectors.toList());
		//ids为空会炸，返回没有任何成员
		if (ids.isEmpty()) {
			throw new  ServiceException(GroupErrorCode.MEMBER_NOT_EXSIT);
		}
		List<User> users=userMapper.selectBatchIds(ids);
		List<MemberListQueryResp> memberListQueryRespList= BeanUtil.copyToList(users,MemberListQueryResp.class);
		Integer total= memberListQueryRespList.size();
		Integer totalPage = (total+size-1)/size;
		if (page > totalPage || page < 1) {
			throw new ServiceException(GroupErrorCode.PAGE_NOT_EXIST);
		}

		Integer from=(page-1)*size;
		Integer to=Math.min(from+size,total);
		return new PageResp<MemberListQueryResp>(totalPage,memberListQueryRespList.subList(from,to));
	}

	@Override
	public PageResp<MemberListInNormalGroupQueryResp> getMemberListInNormalGroup(Long groupId, Integer page, Integer size) {
		LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<GroupMember>()
				.eq(GroupMember::getGroupId,groupId)
				.select(GroupMember::getUserId);

		List<Long> ids=groupMemberMapper.selectList(wrapper)
				.stream()
				.map(GroupMember::getGroupId)
				.collect(Collectors.toList());
		//ids为空会炸，返回没有任何成员
		if (ids.isEmpty()) {
			throw new  ServiceException(GroupErrorCode.MEMBER_NOT_EXSIT);
		}
		List<User> users=userMapper.selectBatchIds(ids);
		List<MemberListInNormalGroupQueryResp> MemberListInNormalGroupQueryRespList= BeanUtil.copyToList(users,MemberListInNormalGroupQueryResp.class);
		Integer total= MemberListInNormalGroupQueryRespList.size();
		Integer totalPage = (total+size-1)/size;
		if (page > totalPage || page < 1) {
			throw new ServiceException(GroupErrorCode.PAGE_NOT_EXIST);
		}

		Integer from=(page-1)*size;
		Integer to=Math.min(from+size,total);
		return new PageResp<MemberListInNormalGroupQueryResp>(totalPage,MemberListInNormalGroupQueryRespList.subList(from,to));
	}

	@Override
	public void updateGroupMemberRole(Long groupId, Long targetUserId, Integer role) {

	}


}
