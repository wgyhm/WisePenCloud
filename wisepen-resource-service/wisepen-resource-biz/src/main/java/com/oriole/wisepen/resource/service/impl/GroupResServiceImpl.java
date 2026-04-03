package com.oriole.wisepen.resource.service.impl;

import com.oriole.wisepen.resource.domain.dto.req.GroupResConfigUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.GroupResConfigResponse;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.repository.GroupResConfigRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.service.IEventPublisher;
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
        // 获取现有配置以作为对比基准
        GroupResConfigEntity existingConfig = groupResConfigRepository.findByGroupId(req.getGroupId()).orElse(new GroupResConfigEntity());
        // 获取旧的掩码：如果配置不存在，则系统此前的默认表现是 DEFAULT_MEMBER_ACTIONS
        Integer oldDefaultMask = existingConfig.getDefaultMemberActionsMask() != null
                ? existingConfig.getDefaultMemberActionsMask() : ResourceAction.DEFAULT_MEMBER_ACTIONS;

        GroupResConfigEntity entity = BeanUtil.copyProperties(req, GroupResConfigEntity.class);
        groupResConfigRepository.save(entity);

        // 如果兜底掩码发生了实质性变化，触发该组所有资源的重算
        if (entity.getDefaultMemberActionsMask() != null && !Objects.equals(oldDefaultMask, entity.getDefaultMemberActionsMask())) {
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
