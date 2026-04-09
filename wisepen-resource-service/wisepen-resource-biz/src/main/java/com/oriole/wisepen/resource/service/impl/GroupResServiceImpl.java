package com.oriole.wisepen.resource.service.impl;

import com.oriole.wisepen.resource.domain.dto.req.GroupResConfigUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.GroupResConfigResponse;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.repository.GroupResConfigRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.mq.IEventPublisher;
import com.oriole.wisepen.resource.service.IGroupResService;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.oriole.wisepen.resource.constant.ResourceConstants.CONFIG_TRASH_COLLECTION;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupResServiceImpl implements IGroupResService {

    private final GroupResConfigRepository groupResConfigRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final IEventPublisher aclEventPublisher;
    private final MongoTemplate mongoTemplate;

    @Override
    public GroupResConfigResponse getGroupResConfig(String groupId) {
        GroupResConfigEntity entity = groupResConfigRepository.findByGroupId(groupId).orElse(null);

        FileOrganizationLogic fileOrgLogic = entity != null ? entity.getFileOrgLogic() : FileOrganizationLogic.FOLDER;
        List<ResourceAction> defaultMemberActions = ResourceAction.permissionCodeToActions(entity != null ? entity.getDefaultMemberActionsMask() : ResourceAction.DEFAULT_MEMBER_ACTIONS);

        return new GroupResConfigResponse(groupId, fileOrgLogic, defaultMemberActions);
    }

    @Override
    public void upsertGroupResConfig(GroupResConfigUpdateRequest req) {
        GroupResConfigEntity entity = groupResConfigRepository.findByGroupId(req.getGroupId())
                .orElseGet(() -> {
                    GroupResConfigEntity newEntity = new GroupResConfigEntity();
                    newEntity.setGroupId(req.getGroupId());
                    newEntity.setDefaultMemberActionsMask(ResourceAction.DEFAULT_MEMBER_ACTIONS);
                    return newEntity;
                });
        boolean shouldRecalculate = false;

        if (req.getDefaultMemberActions() != null) {
            Integer newMask = ResourceAction.actionsToPermissionCode(req.getDefaultMemberActions());
            if (!Objects.equals(entity.getDefaultMemberActionsMask(), newMask)) {
                entity.setDefaultMemberActionsMask(newMask);
                shouldRecalculate = true;
            }
        }

        if (req.getFileOrgLogic() != null) entity.setFileOrgLogic(req.getFileOrgLogic());
        groupResConfigRepository.save(entity);

        // 如果兜底掩码发生了实质性变化，触发该组所有资源的重算
        if (shouldRecalculate) {
            // 从 MongoDB 查出所有绑定了该组的资源 ID
            List<ResourceItemEntity> affectedResources = resourceItemRepository.findByGroupId(req.getGroupId());
            if (affectedResources != null && !affectedResources.isEmpty()) {
                for (ResourceItemEntity resource : affectedResources) {
                    // 推送到 Kafka
                    aclEventPublisher.publishAclRecalculateEvent(resource.getResourceId(), "GROUP_DEFAULT_MASK_CHANGED");
                }
            }
        }
    }

    @Override
    public FileOrganizationLogic getFileOrgLogic(String groupId) {
        return groupResConfigRepository.findByGroupId(groupId)
                .map(GroupResConfigEntity::getFileOrgLogic)
                .orElse(FileOrganizationLogic.FOLDER);
    }

    @Override
    public void softRemoveGroupResConfigByGroupId(String groupId) {
        // 配置软删除 将 dissolvedAt 记录后移入 TRASH_COLLECTION
        GroupResConfigEntity config = groupResConfigRepository.findByGroupId(groupId)
                .orElseGet(() -> {
                    GroupResConfigEntity newEntity = new GroupResConfigEntity();
                    newEntity.setGroupId(groupId);
                    return newEntity;
                });
        config.setDissolvedAt(LocalDateTime.now());
        mongoTemplate.save(config, CONFIG_TRASH_COLLECTION);
        groupResConfigRepository.deleteByGroupId(groupId);
        log.info("小组 {} 解散：资源配置已移入 TRASH_COLLECTION，供定时任务 30 天后清理", groupId);
    }

    @Override
    public void hardRemoveGroupResConfigByGroupId(String groupId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("groupId").is(groupId)),
                CONFIG_TRASH_COLLECTION
        );
    }
}
