package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.component.InviteCodeGenerator;
import com.oriole.wisepen.user.domain.dto.GroupQueryResp;
import com.oriole.wisepen.user.domain.dto.PageResp;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.mapper.GroupMapper;
import com.oriole.wisepen.user.mapper.GroupMemberMapper;
import com.oriole.wisepen.user.mapper.GroupMemberQuotasMapper;
import com.oriole.wisepen.user.mapper.GroupWalletsMapper;
import com.oriole.wisepen.user.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public Boolean validatePermission(Long groupId){
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
			throw new ServiceException(ResultCode.GROUP_IS_EXISTED);
		}
        //校验权限

        IdentityType type= SecurityContextHolder.getIdentityType();
        if (group.getType()==2&&type!=IdentityType.TEACHER&&type!=IdentityType.ADMIN) {
            throw new ServiceException(ResultCode.NO_PERMISSION);
        }

        if (group.getType()==3&&type!=IdentityType.ADMIN) {
            throw new ServiceException(ResultCode.NO_PERMISSION);
        }
        // ...
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
            throw new ServiceException(ResultCode.GROUP_NOT_EXIST);
        }

        //不是 owner 修改的
        if (validatePermission(group.getId())==false) {
            throw new ServiceException(ResultCode.NO_PERMISSION);
        }

        groupMapper.updateById(group);
    }

    @Override
    public void deleteGroup(Long groupId) {

        //这个不存在
        if (validateIsExisted(groupId)==false) {
            throw new ServiceException(ResultCode.GROUP_NOT_EXIST);
        }
        //没有
        if (validatePermission(groupId)==false) {
            throw new ServiceException(ResultCode.NO_PERMISSION);
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

    @Override
    public PageResp<GroupQueryResp> getGroupIdsByUserIdAndType(Long userId, int type, int page, int size) {
        QueryWrapper<GroupMember> wrapper = new QueryWrapper<GroupMember>()
                .eq("user_id",userId)
                .eq("role",type)
                .eq("del_flag",0)
                .select("groupId");

        List<Long> ids=groupMemberMapper.selectList(wrapper)
                .stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());;

        List<Group> groups=groupMapper.selectBatchIds(ids);
        List<GroupQueryResp> groupQueryRespList= BeanUtil.copyToList(groups,GroupQueryResp.class);
        int total= groupQueryRespList.size();
        int totalPage = (total+size-1)/size;
        if (page > totalPage || page < 1) {
            throw new ServiceException(ResultCode.PAGE_NOT_EXIST);
        }

        int from=(page-1)*size;
        int to=Math.min(from+size,total);
        return new PageResp<GroupQueryResp>(totalPage,groupQueryRespList.subList(from,to));
    }

    @Override
    public Group getGroupById(Long id) {
        return groupMapper.selectById(id);
    }
}