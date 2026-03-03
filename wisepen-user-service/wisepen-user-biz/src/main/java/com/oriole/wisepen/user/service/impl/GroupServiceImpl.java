package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.*;
import com.oriole.wisepen.user.component.InviteCodeGenerator;
import com.oriole.wisepen.user.component.RedisSaver;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.domain.entity.GroupWallets;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.GroupMemberQuotasMapper;
import com.oriole.wisepen.user.mapper.GroupWalletsMapper;
import com.oriole.wisepen.user.service.GroupService;
import com.oriole.wisepen.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupWalletsMapper groupWalletsMapper;
    private final GroupMemberQuotasMapper groupMemberQuotasMapper;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final UserService userService;
    private final RedisSaver redisSaver;

    //组是否存在（被删除也算不存在）
    private Boolean validateIsExisted(Long groupId){
        Group res=groupMapper.selectOne(new LambdaQueryWrapper<Group>()
                .eq(Group::getId,groupId));
        return res!=null&&res.getDelFlag()==0;
    }
    //当前是否是 group 的owner
    private Boolean validatePermission (Long groupId){
        IdentityType type= SecurityContextHolder.getIdentityType();
        if (type==IdentityType.ADMIN) {
            return true;
        }

        Group res=groupMapper.selectOne(new LambdaQueryWrapper<Group>()
                .eq(Group::getId,groupId));

        Long userid= Long.valueOf(SecurityContextHolder.getUserId());
        return res.getOwnerId().equals(userid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createGroup(Group group) {
        // 可以在这里加业务逻辑，比如：校验组名重复
        //校验组名重复
        Group res=groupMapper.selectOne(new LambdaQueryWrapper<Group>().eq(Group::getName,group.getName()));

        //因为删除是软删除，所以暂时不考虑删除以后也能添加相同的组名
        if (res!=null) {
            throw new ServiceException(GroupErrorCode.GROUP_IS_EXISTED);
        }
        //校验权限

        IdentityType type= SecurityContextHolder.getIdentityType();
        if (group.getType()==GroupType.ADVANCED_GROUP&&type!=IdentityType.TEACHER&&type!=IdentityType.ADMIN) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }

        if (group.getType()==GroupType.MARKET_GROUP&&type!=IdentityType.ADMIN) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }
        // 保证 inviteCode 唯一
        String inviteCode=inviteCodeGenerator.generate16();
        group.setInviteCode(inviteCode);
        // 调用 MP 的 Mapper 方法
        groupMapper.insert(group);

        if (group.getType()!=GroupType.NORMAL_GROUP) {
            GroupWallets groupWallets = new GroupWallets();
            groupWallets.setId(group.getId());
            groupWallets.setQuotaUsed(0);
            groupWallets.setQuotaLimit(0);
            groupWalletsMapper.insert(groupWallets);
        }
    }

    @Override
    public Map<String, Integer> getGroupRoleMapByUserId(Long userId) {
        List<GroupMember> members = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getUserId, userId)
                        .select(GroupMember::getGroupId, GroupMember::getRole)
        );
        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyMap();
        }
        return members.stream()
                .collect(Collectors.toMap(
                        member -> String.valueOf(member.getGroupId()),
                        member -> member.getRole().getCode()
                ));
    }

    @Override
    public void updateGroup(Group group) {
        //这个不存在
        if (!validateIsExisted(group.getId())) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }

        //不是 owner 修改的
        if (!validatePermission(group.getId())) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }

        groupMapper.updateById(group);
    }

    @Override
    public void deleteGroup(Long groupId) {

        //这个不存在
        if (!validateIsExisted(groupId)) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }
        //没有
        if (!validatePermission(groupId)) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }

        LambdaUpdateWrapper<Group> wrapper = new LambdaUpdateWrapper<Group>()
                .eq(Group::getId, groupId)
                .set(Group::getDelFlag, 1);
        groupMapper.update(wrapper);

        LambdaUpdateWrapper<GroupMember> wrapper1 = new LambdaUpdateWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId);
        List<GroupMember> groupMembers = groupMemberMapper.selectList(wrapper1);
        groupMemberMapper.delete(wrapper1);
        for (GroupMember groupMember : groupMembers) {
            Long userId = groupMember.getUserId();
            redisSaver.updateGroupRoleMap(userId,getGroupRoleMapByUserId(userId));
        }
    }

    @Override
    public List<Long> getGroupIdsByUserId(Long userId) {
        return groupMemberMapper.selectGroupIdsByUserId(userId);
    }

    @Override
    public PageResult<GroupQueryResponse> getGroupIds(Long userId, Integer type, Integer page, Integer size) {

        Page<GroupMember> mpPage = new Page<>(page, size);

        LambdaQueryWrapper<GroupMember> w = new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getUserId, userId)
                .select(GroupMember::getGroupId);

        if (type==1) {
            w.in(GroupMember::getRole, 1,2);
        }

        IPage<GroupMember> memberPage = groupMemberMapper.selectPage(mpPage, w);

        if (memberPage.getRecords().isEmpty()) {
			return new PageResult<>(memberPage.getTotal(), page, size);
        }
        List<Long> groupIds = memberPage.getRecords().stream()
                .map(GroupMember::getGroupId)
                .distinct()
                .toList();


        List<Group> groups = groupMapper.selectBatchIds(groupIds);

        // 按 groupIds 顺序重排
        Map<Long, Group> id2Group = groups.stream()
                .collect(Collectors.toMap(Group::getId, g -> g, (a, b) -> a));

        List<GroupQueryResponse> records = groupIds.stream()
                .map(id2Group::get)
                .filter(Objects::nonNull)
                .map(g -> {
                    GroupQueryResponse groupQueryResponse = BeanUtil.copyProperties(g, GroupQueryResponse.class);
                    groupQueryResponse.setCreator(getCreatorByUserId(g.getOwnerId()));
                    return groupQueryResponse;
                })
                .toList();

        PageResult<GroupQueryResponse> pr=new PageResult<>(memberPage.getTotal(), page, size);
        pr.setList(records);
        return pr;
    }

    private CreatorInfo transformUserDTOToCreator(UserInfoDTO user) {
        if (user == null) {
            return null;
        }
        CreatorInfo creator = new CreatorInfo();
        creator.setAvatar(user.getAvatar());
        creator.setNickname(user.getNickname());
        creator.setName(user.getRealName());
        return creator;
    }

    private CreatorInfo getCreatorByUserId(Long userId) {
        UserInfoDTO creatorUser = userService.getUserInfoById(userId);
        return transformUserDTOToCreator(creatorUser);
    }

    @Override
    public GetGroupInfoResponse getGroupById(Long groupId) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return null;
        }

        GetGroupInfoResponse response = BeanUtil.copyProperties(group, GetGroupInfoResponse.class);
        response.setCreator(getCreatorByUserId(group.getOwnerId()));
        return response;
    }
}
