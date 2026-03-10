package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.GroupCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupDeleteRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberJoinRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupItemInfoResponse;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.GroupEntity;
import com.oriole.wisepen.user.domain.entity.GroupMemberEntity;
import com.oriole.wisepen.user.event.GroupTokenConsumeEvent;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.service.GroupMemberService;
import com.oriole.wisepen.user.service.GroupService;
import com.oriole.wisepen.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserService userService;
    private final GroupMemberService groupMemberService;
    private final RedisCacheManager redisCacheManager;

    @Override
    public void joinGroup(GroupMemberJoinRequest req, Long userId, Set<Long> userJoinedGroupIds) {
        LambdaQueryWrapper<GroupEntity> queryWrapper = new LambdaQueryWrapper<GroupEntity>().eq(GroupEntity::getInviteCode, req.getInviteCode());
        GroupEntity group=groupMapper.selectOne(queryWrapper);
        if (group == null) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }

        if (userJoinedGroupIds.contains(group.getGroupId())) { // 检查是否在群内
            throw new ServiceException(GroupErrorCode.MEMBER_IS_EXISTED);
        }

        groupMemberService.joinGroup(group.getGroupId(), userId, GroupRoleType.MEMBER);
    }

    @Override
    public void createGroup(GroupCreateRequest req, Long userId) {
        GroupEntity group = GroupEntity.builder()
                .ownerId(userId)
                .inviteCode(IdUtil.fastSimpleUUID().substring(0, 8)) // 确保ID唯一
                .tokenUsed(0).tokenBalance(0)
                .build();

        BeanUtil.copyProperties(req, group, "ownerId", "inviteCode", "tokenUsed", "tokenPoolBalance");
        groupMapper.insert(group);
        groupMemberService.joinGroup(group.getGroupId(), userId, GroupRoleType.OWNER); // 用户加入群组
        redisCacheManager.blockGroupChat(group.getGroupId()); // 刚成立的组都是没Chat权限的，必须要充值
    }

    @Override
    public void updateGroup(GroupUpdateRequest req) {
        GroupEntity group = BeanUtil.copyProperties(req, GroupEntity.class);
        group.setUpdateTime(new Date());
        int rows = groupMapper.updateById(group);
        if (rows == 0) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }
    }

    @Override
    public void deleteGroup(GroupDeleteRequest req) {
        Long groupId = req.getGroupId();

        int rows = groupMapper.deleteById(groupId);
        if (rows == 0) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }
        groupMemberService.removeAllGroupMembers(groupId);
    }

    @Override
    public PageResult<GroupItemInfoResponse> listGroups(Long userId, GroupRoleType groupRoleType, int page, int size) {
        Page<GroupMemberEntity> memberPage = new Page<>(page, size);

        // 先查出该用户符合条件的所在组ID
        LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMemberEntity::getUserId, userId);
        if (groupRoleType != null) {
            wrapper.eq(GroupMemberEntity::getRole, groupRoleType.getCode());
        }
        Page<GroupMemberEntity> resultPage = groupMemberMapper.selectPage(memberPage, wrapper);

        List<Long> groupIds = resultPage.getRecords().stream()
                .map(GroupMemberEntity::getGroupId)
                .collect(Collectors.toList());

        PageResult<GroupItemInfoResponse> pageResult = new PageResult<>(resultPage.getTotal(), page, size);
        if (groupIds.isEmpty()) {
            return pageResult;
        }

        // 再批量获取群组信息
        List<GroupEntity> groups = groupMapper.selectBatchIds(groupIds);

        // 提取所有不重复的 ownerId (用 Set 去重)
        Set<Long> ownerIds = groups.stream().map(GroupEntity::getOwnerId).collect(Collectors.toSet());
        Map<Long, UserDisplayBase> ownerMap = userService.getUserDisplayInfoByIds(ownerIds); // 获取这些用户信息

        List<GroupItemInfoResponse> responses = groups.stream().map(g -> {
            GroupItemInfoResponse resp = BeanUtil.copyProperties(g, GroupItemInfoResponse.class);
            resp.setOwnerInfo(ownerMap.get(g.getOwnerId())); // 从 ownerMap 中快速匹配对应的群主信息
            return resp;
        }).collect(Collectors.toList());

        pageResult.addAll(responses);
        return pageResult;
    }

    public GroupEntity getGroupInfoById(Long groupId) {
        GroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }
        return group;
    }

    @Override
    public GroupItemInfoResponse getGroupBaseInfoById(Long groupId) {
        GroupEntity group = getGroupInfoById(groupId);
        GroupItemInfoResponse resp = BeanUtil.copyProperties(group, GroupItemInfoResponse.class);
        resp.setOwnerInfo(userService.getUserDisplayInfoById(group.getOwnerId()));
        return resp;
    }

    @Override
    public GroupDetailInfoResponse getGroupDetailInfoById(Long groupId) {
        GroupEntity group = getGroupInfoById(groupId);
        GroupDetailInfoResponse resp = BeanUtil.copyProperties(group, GroupDetailInfoResponse.class);
        resp.setOwnerInfo(userService.getUserDisplayInfoById(group.getOwnerId()));
        return resp;
    }

    @Override
    public void refillGroupTokenBalance(Long groupId, Integer rechargedToken) {
        GroupEntity group = groupMapper.selectById(groupId);
        if (group == null) throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);

        if (!GroupType.ADVANCED_GROUP.equals(group.getGroupType())) {
            throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
        }

        // 原子累加余额 (UPDATE sys_group SET token_balance = token_balance + ? WHERE id = ?)
        UpdateWrapper<GroupEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", groupId)
                .setSql("token_balance = token_balance + " + rechargedToken);

        groupMapper.update(null, wrapper);

        // [架构预留] 这里通常需要 insert 一条充值流水记录到 sys_token_record 表

        redisCacheManager.unblockGroupChat(groupId);
    }

    @Override
    public void updateGroupTokenUsed(Long groupId, Integer usedToken) {
        UpdateWrapper<GroupEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", groupId)
                .setSql("token_used = token_used + " + usedToken)
                .setSql("token_balance = token_balance - " + usedToken);

        groupMapper.update(null, wrapper);

        GroupEntity group = groupMapper.selectById(groupId);
        // 如果余额降到 0 或负数
        if (group != null && group.getTokenBalance() <= 0) {
            redisCacheManager.blockGroupChat(groupId);
            log.warn("群组 {} 余额已欠费透支，当前余额: {}，已触发 Redis 熔断", groupId, group.getTokenBalance());
        }
    }

    @EventListener
    public void handleGroupTokenConsumeEvent(GroupTokenConsumeEvent event) {
        // 直接复用原有的扣除大盘额度方法
        this.updateGroupTokenUsed(event.getGroupId(), event.getUsedToken());
    }
}
