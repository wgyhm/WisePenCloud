package com.oriole.wisepen.resource.task;

import com.oriole.wisepen.resource.config.ResourceProperties;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.service.IGroupResService;
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

import static com.oriole.wisepen.resource.service.impl.GroupResServiceImpl.CONFIG_TRASH_COLLECTION;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceGcTask {

    private static final int RETENTION_DAYS = 30;

    private final ResourceProperties resourceProperties;
    private final MongoTemplate mongoTemplate;
    private final IGroupResService groupResService;
    private final ITagService tagService;

    @Scheduled(cron = "${wisepen.resource.physical-gc-cron:0 0 3 * * ?}")
    public void garbageCollection() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(resourceProperties.getDeletedRetentionDays());

        List<GroupResConfigEntity> expired = mongoTemplate.find(
                Query.query(Criteria.where("dissolvedAt").lt(threshold)),
                GroupResConfigEntity.class,
                CONFIG_TRASH_COLLECTION
        );

        if (expired.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超过 {} 天的小组，开始硬删除", expired.size(), RETENTION_DAYS);

        for (GroupResConfigEntity record : expired) {
            try {
                tagService.hardRemoveAllTagByGroupId(record.getGroupId());
                groupResService.hardRemoveGroupResConfig(record.getGroupId());
            } catch (Exception e) {
                log.error("小组 {} 硬删除失败，跳过本次处理", record.getGroupId(), e);
            }
        }
    }
}
