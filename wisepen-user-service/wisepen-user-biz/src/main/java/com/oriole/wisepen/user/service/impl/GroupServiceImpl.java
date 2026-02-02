package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.component.InviteCodeGenerator;
import com.oriole.wisepen.user.api.domain.dto.GroupQueryResp;
import com.oriole.wisepen.user.api.domain.dto.PageResp;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.GroupMemberQuotasMapper;
import com.oriole.wisepen.user.mapper.GroupWalletsMapper;
import com.oriole.wisepen.user.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Administrator
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class GroupServiceImpl implements GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupWalletsMapper groupWalletsMapper;
    private final GroupMemberQuotasMapper groupMemberQuotasMapper;
    private final InviteCodeGenerator inviteCodeGenerator;
    //组是否存在（被删除也算不存在）
    public Boolean validateIsExisted(Long groupId){
        Group res=groupMapper.selectOne(new LambdaQueryWrapper<Group>()
                .eq(Group::getId,groupId));
        return res!=null&&res.getDelFlag()==0;
    }
    //当前是否是 group 的owner
    public Boolean validatePermission (Long groupId){
        IdentityType type= SecurityContextHolder.getIdentityType();
        if (type==IdentityType.ADMIN) {
            return true;
        }

        Group res=groupMapper.selectOne(new LambdaQueryWrapper<Group>()
                .eq(Group::getId,groupId));

        Long userid=SecurityContextHolder.getUserId();
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
        if (group.getType()==2&&type!=IdentityType.TEACHER&&type!=IdentityType.ADMIN) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }

        if (group.getType()==3&&type!=IdentityType.ADMIN) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }
        // 保证 inviteCode 唯一
        String inviteCode=inviteCodeGenerator.generate8();
        while (true) {
            Group group1 = groupMapper.selectOne(new LambdaQueryWrapper<Group>().eq(Group::getInviteCode, inviteCode));
            if (group1==null) {
                break;
            }
            inviteCode=inviteCodeGenerator.generate8();
        }
        group.setInviteCode(inviteCodeGenerator.generate8());
        // 调用 MP 的 Mapper 方法
        groupMapper.insert(group);
    }

    @Override
    public void updateGroup(Group group) {
        Group res=groupMapper.selectOne(new LambdaQueryWrapper<Group>()
                .eq(Group::getId,group.getId()));
        //这个不存在
        if (validateIsExisted(group.getId())==false) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }

        //不是 owner 修改的
        if (validatePermission(group.getId())==false) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }

        groupMapper.updateById(group);
    }

    @Override
    public void deleteGroup(Long groupId) {

        //这个不存在
        if (validateIsExisted(groupId)==false) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }
        //没有
        if (validatePermission(groupId)==false) {
            throw new ServiceException(GroupErrorCode.NO_PERMISSION);
        }

        LambdaUpdateWrapper<Group> wrapper = new LambdaUpdateWrapper<Group>()
                .eq(Group::getId, groupId)
                .set(Group::getDelFlag, 1);

        groupMapper.update(wrapper);
    }

    @Override
    public List<Long> getGroupIdsByUserId(Long userId) {
        return groupMemberMapper.selectGroupIdsByUserId(userId);
    }


//    public PageResp<GroupQueryResp> getGroupIdsByUserIdAndType(Long userId, Integer type, Integer page, Integer size) {
//        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<GroupMember>()
//                .eq(GroupMember::getUserId,userId)
//                .eq(GroupMember::getRole,type)
//                .select(GroupMember::getGroupId);
//
//        List<Long> ids=groupMemberMapper.selectList(wrapper)
//                .stream()
//                .map(GroupMember::getGroupId)
//                .collect(Collectors.toList());
//        //ids为空会炸，返回没有任何小组
//        if (ids.isEmpty()) {
//            throw new  ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
//        }
//        List<Group> groups=groupMapper.selectBatchIds(ids);
//        List<GroupQueryResp> groupQueryRespList= BeanUtil.copyToList(groups,GroupQueryResp.class);
//        int total= groupQueryRespList.size();
//        Integer totalPage = (total+size-1)/size;
//        if (page > totalPage || page < 1) {
//            throw new ServiceException(GroupErrorCode.PAGE_NOT_EXIST);
//        }
//
//        int from=(page-1)*size;
//        int to=Math.min(from+size,total);
//        return new PageResp<GroupQueryResp>(totalPage,groupQueryRespList.subList(from,to));
//    }
    @Override
    public PageResp<GroupQueryResp> getGroupIds(Long userId, Integer type, Integer page, Integer size) {

        Page<GroupMember> mpPage = new Page<>(page, size);

        LambdaQueryWrapper<GroupMember> w = new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getUserId, userId)
                .select(GroupMember::getGroupId);

        if (type==1) {
            w.in(GroupMember::getRole, 1,2);
        }

        IPage<GroupMember> memberPage = groupMemberMapper.selectPage(mpPage, w);

        if (memberPage.getRecords().isEmpty()) {
            return new PageResp<>((int) memberPage.getPages(), Collections.emptyList());
        }
        List<Long> groupIds = memberPage.getRecords().stream()
                .map(GroupMember::getGroupId)
                .distinct()
                .toList();


        List<Group> groups = groupMapper.selectBatchIds(groupIds);

        // 按 groupIds 顺序重排
        Map<Long, Group> id2Group = groups.stream()
                .collect(Collectors.toMap(Group::getId, g -> g, (a, b) -> a));

        List<GroupQueryResp> records = groupIds.stream()
                .map(id2Group::get)
                .filter(Objects::nonNull)
                .map(g -> BeanUtil.copyProperties(g, GroupQueryResp.class))
                .toList();

        return new PageResp<>((int) memberPage.getPages(), records);
    }

    @Override
    public Group getGroupById(Long id) {
        return groupMapper.selectById(id);
    }
}