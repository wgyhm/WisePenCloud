package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        LambdaQueryWrapper<GroupEntity> queryWrapper = new LambdaQueryWrapper<GroupEntity>()
                .eq(GroupEntity::getInviteCode, req.getInviteCode());
        GroupEntity group = groupMapper.selectOne(queryWrapper);
        if (group == null) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }

        if (userJoinedGroupIds.contains(group.getGroupId())) {
            throw new ServiceException(GroupErrorCode.MEMBER_IS_EXISTED);
        }

        groupMemberService.joinGroup(group.getGroupId(), userId, GroupRoleType.MEMBER);
    }

    @Override
    public void createGroup(GroupCreateRequest req, Long userId) {
        GroupEntity group = GroupEntity.builder()
                .ownerId(userId)
                .inviteCode(IdUtil.fastSimpleUUID().substring(0, 8))
                .tokenUsed(0)
                .tokenBalance(0)
                .build();

        BeanUtil.copyProperties(req, group, "ownerId", "inviteCode", "tokenUsed", "tokenBalance");
        groupMapper.insert(group);
        groupMemberService.joinGroup(group.getGroupId(), userId, GroupRoleType.OWNER);
        redisCacheManager.blockGroupChat(group.getGroupId());
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

        List<GroupEntity> groups = groupMapper.selectBatchIds(groupIds);
        Set<Long> ownerIds = groups.stream().map(GroupEntity::getOwnerId).collect(Collectors.toSet());
        Map<Long, UserDisplayBase> ownerMap = userService.getUserDisplayInfoByIds(ownerIds);

        List<GroupItemInfoResponse> responses = groups.stream().map(g -> {
            GroupItemInfoResponse resp = BeanUtil.copyProperties(g, GroupItemInfoResponse.class);
            resp.setOwnerInfo(ownerMap.get(g.getOwnerId()));
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
        if (group == null) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }

        if (GroupType.NORMAL_GROUP.equals(group.getGroupType())) {
            throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
        }

        LambdaUpdateWrapper<GroupEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GroupEntity::getGroupId, groupId)
                .setSql("token_balance = token_balance + " + rechargedToken);

        groupMapper.update(null, wrapper);
        redisCacheManager.unblockGroupChat(groupId);
    }

    @Override
    public void updateGroupTokenUsed(Long groupId, Integer usedToken) {
        LambdaUpdateWrapper<GroupEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GroupEntity::getGroupId, groupId)
                .setSql("token_used = token_used + " + usedToken)
                .setSql("token_balance = token_balance - " + usedToken);

        groupMapper.update(null, wrapper);

        GroupEntity group = groupMapper.selectById(groupId);
        if (group != null && group.getTokenBalance() <= 0) {
            redisCacheManager.blockGroupChat(groupId);
            log.warn("群组 {} 余额已欠费透支，当前余额: {}，已触发 Redis 熔断", groupId, group.getTokenBalance());
        }
    }

    @EventListener
    public void handleGroupTokenConsumeEvent(GroupTokenConsumeEvent event) {
        this.updateGroupTokenUsed(event.getGroupId(), event.getUsedToken());
    }
}
