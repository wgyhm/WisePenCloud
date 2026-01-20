package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.domain.dto.GroupQueryResp;
import com.oriole.wisepen.user.domain.dto.PageResp;
import com.oriole.wisepen.user.domain.entity.Group;

import java.util.List;
public interface GroupService {
    // 创建分组 (业务方法)
    void createGroup(Group group);

    // 更新分组
    void updateGroup(Group group);

    // 删除分组（业务方法）
    void deleteGroup(Long groupId);

    // 获取用户的所有组ID (业务方法)
    List<Long> getGroupIdsByUserId(Long userId);

    // 获取用户的不同类型组ID (业务方法) (Type= 1-我管理的，2-我加入的）
    PageResp<GroupQueryResp> getGroupIdsByUserIdAndType(Long userId, int type, int page, int size);

    // 获取组的详情 (业务方法)
    Group getGroupById(Long id);
}
