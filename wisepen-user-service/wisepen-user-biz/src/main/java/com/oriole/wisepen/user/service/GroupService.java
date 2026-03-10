package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.user.api.domain.dto.req.GroupCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupDeleteRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberJoinRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupItemInfoResponse;

import java.util.Map;
import java.util.Set;

public interface GroupService {

    // 创建群组
    void createGroup(GroupCreateRequest req, Long userId);

    // 加入群组
    void joinGroup(GroupMemberJoinRequest req, Long userId, Set<Long> userJoinedGroupIds);

    // 更新群组基础信息
    void updateGroup(GroupUpdateRequest req);

    // 删除群组
    void deleteGroup(GroupDeleteRequest req);

    // 获取指定用户的群组分页列表
    PageResult<GroupItemInfoResponse> listGroups(Long userId, GroupRoleType groupRoleType, int page, int size);

    // 获取群组的公开基础信息
    GroupItemInfoResponse getGroupBaseInfoById(Long groupId);

    // 获取群组的详细信息
    GroupDetailInfoResponse getGroupDetailInfoById(Long groupId);

    // 充值Token余额
    void refillGroupTokenBalance(Long groupId, Integer rechargedToken);

    // 更新组Token用量
    void updateGroupTokenUsed(Long groupId, Integer usedToken);
}
