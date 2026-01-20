package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.domain.enums.GroupIdentity;

public interface GroupMemberService {

	void joinGroup(Long userId, String inviteCode);
	void leaveGroup(Long userId, Long groupId);
	void kickGroupMember(Long userId, Long groupId, Long targetUserId);
	void getMemberList(Long groupId, int page, int size);
	void updateGroupMemberRole(Long groupId, Long targetUserId, int role);

	void becomeGroupOwner(Long userId, Long groupId);
}
