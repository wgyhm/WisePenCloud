package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.user.api.domain.dto.MemberListQueryResponse;

import java.util.List;

public interface GroupMemberService {

	void joinGroup(Long userId, String inviteCode);
	void leaveGroup(Long userId, Long groupId);
	void kickGroupMember(Long userId, Long groupId, Long targetUserId);

	void kickGroupMembers(Long operatorUserId, Long groupId, List<Long> targetUserIds);

	PageResult<MemberListQueryResponse> getMemberList(Long groupId, Integer page, Integer size);

	void updateGroupMemberRole(Long groupId, Long targetUserId, Integer role);

	void becomeGroupOwner(Long userId, Long groupId);
}
