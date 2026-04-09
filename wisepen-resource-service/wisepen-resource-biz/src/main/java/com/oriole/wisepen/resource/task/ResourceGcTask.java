package com.oriole.wisepen.resource.task;

import com.oriole.wisepen.resource.config.ResourceProperties;
import com.oriole.wisepen.resource.domain.dto.req.TagDeleteRequest;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.service.IGroupResService;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.resource.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.oriole.wisepen.resource.constant.ResourceConstants.*;


@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceGcTask {

    private final ResourceProperties resourceProperties;
    private final MongoTemplate mongoTemplate;
    private final IGroupResService groupResService;
    private final IResourceService resourceService;
    private final ITagService tagService;

    @Scheduled(cron = "${wisepen.resource.physical-gc-cron:0 0 3 * * ?}")
    public void garbageCollection() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(resourceProperties.getDeletedRetentionDays());

        log.info("开始执行资源生命周期定时清理任务...");
        // 彻底删除TRASH_COLLECTION中超过 30 天的小组配置
        cleanupExpiredGroups(threshold);
        // 删除个人回收站 (.Trash) 中停留超过 30 天的资源，移入TRASH_COLLECTION
        cleanupExpiredResourcesInUserTrash(threshold);
        // 彻底删除TRASH_COLLECTION中超过 30 天的资源
        cleanupExpiredResources(threshold);

        log.info("资源生命周期清理任务执行完毕。");
    }

    private void cleanupExpiredGroups(LocalDateTime threshold) {
        List<GroupResConfigEntity> expired = mongoTemplate.find(
                Query.query(Criteria.where("dissolvedAt").lt(threshold)),
                GroupResConfigEntity.class,
                CONFIG_TRASH_COLLECTION
        );
        if (!expired.isEmpty()) {
            for (GroupResConfigEntity record : expired) {
                tagService.hardRemoveAllTagByGroupId(record.getGroupId());
                groupResService.hardRemoveGroupResConfigByGroupId(record.getGroupId());
            }
            log.info("发现 {} 个过期小组，已经硬删除", expired.size());
        }
    }

    private void cleanupExpiredResourcesInUserTrash(LocalDateTime threshold) {
        // 获取所有个人空间的 .Trash 根节点
        Query trashQuery = Query.query(Criteria.where("tagName").is(TRASH_TAG_NAME)
                .and("parentId").is("0")
                .and("groupId").regex("^" + PERSONAL_GROUP_PREFIX));
        List<TagEntity> trashNodes = mongoTemplate.find(trashQuery, TagEntity.class);

        int movedFolders = 0;
        int movedFiles = 0;

        for (TagEntity trashNode : trashNodes) {
            String groupId = trashNode.getGroupId();
            String trashTagId = trashNode.getTagId();

            // 处理回收站下的过期文件夹
            Query expiredFolderQuery = Query.query(Criteria.where("parentId").is(trashTagId)
                    .and("updateTime").lt(threshold));
            List<TagEntity> expiredFolders = mongoTemplate.find(expiredFolderQuery, TagEntity.class);

            for (TagEntity folder : expiredFolders) {
                TagDeleteRequest req = new TagDeleteRequest();
                req.setGroupId(groupId);
                req.setTargetTagId(folder.getTagId());
                tagService.deleteTag(req, true); // 强制删除该 FOLDER
                movedFolders++;
            }

            // 处理直接孤立在 .Trash 根目录下的过期散落文件
            Query expiredFileQuery = Query.query(Criteria.where("groupBinds")
                    .elemMatch(Criteria.where("groupId").is(groupId).and("tagIds").is(trashTagId))
                    .and("updateTime").lt(threshold));
            List<ResourceItemEntity> expiredFiles = mongoTemplate.find(expiredFileQuery, ResourceItemEntity.class);

            if (!expiredFiles.isEmpty()) {
                List<String> fileIds = expiredFiles.stream()
                        .map(ResourceItemEntity::getResourceId)
                        .collect(Collectors.toList());
                resourceService.softRemoveResources(fileIds); // 直接调批量软删接口，转移至 RESOURCE_TRASH_COLLECTION
                movedFiles += fileIds.size();
            }
        }
        if (movedFolders > 0 || movedFiles > 0) {
            log.info("删除 {} 个过期文件夹， {} 个散落文件", movedFolders, movedFiles);
        }
    }

    private void cleanupExpiredResources(LocalDateTime threshold) {
        Query physicalDeleteQuery = Query.query(Criteria.where("deletedAt").lt(threshold));
        List<ResourceItemEntity> expired = mongoTemplate.find(physicalDeleteQuery, ResourceItemEntity.class, RESOURCE_TRASH_COLLECTION);
        if (!expired.isEmpty()) {
            List<String> resourceIds = expired.stream()
                    .map(ResourceItemEntity::getResourceId)
                    .collect(Collectors.toList());
            resourceService.hardRemoveResources(resourceIds);
            log.info("发现 {} 个过期文件，已经硬删除", expired.size());
        }
    }
}
