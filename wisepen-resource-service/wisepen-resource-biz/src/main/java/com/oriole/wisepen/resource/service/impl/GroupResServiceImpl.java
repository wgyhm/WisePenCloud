package com.oriole.wisepen.resource.service.impl;

import com.oriole.wisepen.resource.domain.dto.req.GroupResConfigUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.GroupResConfigResponse;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.repository.GroupResConfigRepository;
import com.oriole.wisepen.resource.service.IGroupResService;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupResServiceImpl implements IGroupResService {

    public static final String CONFIG_TRASH_COLLECTION = "wisepen_group_res_config_trash";

    private final GroupResConfigRepository configRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public GroupResConfigResponse getGroupResConfig(String groupId) {
        GroupResConfigEntity entity = configRepository.findByGroupId(groupId).orElse(null);

        FileOrganizationLogic fileOrgLogic = entity != null ? entity.getFileOrgLogic() : FileOrganizationLogic.FOLDER;
        List<ResourceAction> defaultMemberActions = ResourceAction.permissionCodeToActions(entity != null ? entity.getDefaultMemberActionsMask() : ResourceAction.DEFAULT_MEMBER_ACTIONS);

        return new GroupResConfigResponse(groupId, fileOrgLogic, defaultMemberActions);
    }

    @Override
    public void upsertGroupResConfig(GroupResConfigUpdateRequest req) {
        GroupResConfigEntity entity = BeanUtil.copyProperties(req, GroupResConfigEntity.class);
        entity.setUpdateTime(new Date());
        configRepository.save(entity);
    }

    @Override
    public FileOrganizationLogic getFileOrgLogic(String groupId) {
        return configRepository.findByGroupId(groupId)
                .map(GroupResConfigEntity::getFileOrgLogic)
                .orElse(FileOrganizationLogic.FOLDER);
    }

    @Override
    public void softRemoveGroupResConfig(String groupId) {
        // 配置软删除 将 dissolvedAt 记录后移入 trash（兜底确保 dissolvedAt 存在）
        GroupResConfigEntity config = configRepository.findByGroupId(groupId)
                .orElseGet(() -> {
                    GroupResConfigEntity newEntity = new GroupResConfigEntity();
                    newEntity.setGroupId(groupId);
                    return newEntity;
                });
        config.setDissolvedAt(new Date());
        config.setUpdateTime(new Date());
        mongoTemplate.save(config, CONFIG_TRASH_COLLECTION);
        configRepository.deleteByGroupId(groupId);
        log.info("小组 {} 解散：资源配置已移入 trash，供定时任务 30 天后清理", groupId);
    }

    @Override
    public void hardRemoveGroupResConfig(String groupId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("groupId").is(groupId)),
                CONFIG_TRASH_COLLECTION
        );
    }
}
