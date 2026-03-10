package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberDetailResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupMemberService {

	// 获取用户的所有组ID和角色
	Map<String, Integer> getGroupRoleMapByUserId(Long userId);

   // 主动退出群组
   void quitGroup(GroupMemberQuitRequest req, Long userId, GroupRoleType opGroupRoleType);

	// 踢出群成员 (不能踢出比自己权限高的人)
	void kickGroupMembers(GroupMemberKickRequest req, Long opUserId, GroupRoleType opGroupRoleType);

	// 更新组成员角色
	void updateGroupMemberRole(GroupMemberRoleUpdateRequest req, Long opUserId);

	// 更新组成员Token配额
	void updateGroupMemberTokenLimit(GroupMemberTokenLimitUpdateRequest req);

	// 获取成员详情
	GroupMemberDetailResponse getGroupMemberInfoByUserId(Long groupId, Long userId);

	// 获取群组成员分页列表
	PageResult<GroupMemberDetailResponse> getGroupMemberList(Long groupId, int page, int size);

	// 内部业务方法

	// 加入群组(以特定身份)
	void joinGroup(Long groupId, Long userId, GroupRoleType groupRoleType);

	// 更新组成员Token余额
	void updateGroupMemberTokenUsed(Long groupId, Long userId, Integer usedToken);

	// 移除全部组成员
	void removeAllGroupMembers(Long groupId);
}
