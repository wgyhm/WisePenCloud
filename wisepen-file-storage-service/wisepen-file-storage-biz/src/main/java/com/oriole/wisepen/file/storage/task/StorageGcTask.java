package com.oriole.wisepen.file.storage.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageStatusEnum;
import com.oriole.wisepen.file.storage.config.StorageProperties;
import com.oriole.wisepen.file.storage.domain.entity.StorageRecordEntity;
import com.oriole.wisepen.file.storage.strategy.StorageManager;
import com.oriole.wisepen.file.storage.mapper.StorageRecordMapper;
import com.oriole.wisepen.file.storage.service.IStorageService;
import com.oriole.wisepen.file.storage.strategy.StorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存储服务专属垃圾回收器 (Garbage Collector)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageGcTask {

    private final IStorageService storageService;
    private final StorageRecordMapper storageRecordMapper;
    private final StorageManager storageManager;
    private final StorageProperties storageProperties;

    /**
     * 任务：死信占位符清理 (默认每2小时执行一次)
     * 用户申请了上传凭证，但一直没有回调，主动去云端查证一次，有文件则自愈补偿，无文件直接删除记录。
     */
    @Scheduled(cron = "${wisepen.storage.zombie-cleanup-cron:0 0 */2 * * ?}")
    public void cleanupUploadingZombies() {
        log.info("开始执行 [UPLOADING 死信清理] 定时任务...");
        LocalDateTime threshold = LocalDateTime.now().minusHours(storageProperties.getUploadingTimeoutHours());

        // 每次最多处理 500 条，防止内存溢出和数据库长事务
        List<StorageRecordEntity> zombies = storageRecordMapper.selectList(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getStatus, StorageStatusEnum.UPLOADING)
                        .le(StorageRecordEntity::getCreateTime, threshold)
                        .last("LIMIT 500")
        );

        if (zombies.isEmpty()) {
            return;
        }

        int recoveredCount = 0;
        int deletedCount = 0;

        for (StorageRecordEntity zombie : zombies) {
            try {
                StorageRecordDTO recoveredDto = storageService.compensateStatus(zombie);
                if (recoveredDto != null) {
                    recoveredCount++;
                } else {
                    // 云端确实没有，物理抹除占位符
                    storageRecordMapper.deleteById(zombie.getFileId());
                    deletedCount++;
                }
            } catch (Exception e) {
                // 单条失败不能影响后续遍历
                log.error("处理死信文件异常: {}", zombie.getObjectKey(), e);
            }
        }
        log.info("结束 [UPLOADING 死信清理]: 补偿恢复 {} 个, 物理抹除 {} 个无用占位符", recoveredCount, deletedCount);
    }

    /**
     * 任务：回收站物理垃圾回收 (默认每天凌晨 3 点执行)
     * 业务场景：将那些被业务端软删除 (DELETED) 且已经度过 30 天（默认）后悔期的文件，从物理云盘彻底抹除。
     */
    @Scheduled(cron = "${wisepen.storage.physical-gc-cron:0 0 3 * * ?}")
    public void physicalGarbageCollection() {
        log.info("开始执行 [DELETED 物理垃圾回收] 定时任务...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(storageProperties.getDeletedRetentionDays());

        List<StorageRecordEntity> trashes = storageRecordMapper.selectList(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
                        // 注意：这里用 update_time，因为软删除触发的是 update
                        .le(StorageRecordEntity::getUpdateTime, threshold)
                        .last("LIMIT 1000")
        );

        if (trashes.isEmpty()) {
            return;
        }

        int successCount = 0;
        for (StorageRecordEntity trash : trashes) {
            try {
                // 先删云端物理文件
                StorageProvider provider = storageManager.getProvider(trash.getConfigId());
                provider.deleteObject(trash.getObjectKey());

                // 物理删除不抛异常，再硬删除数据库记录
                storageRecordMapper.deleteById(trash.getFileId());
                successCount++;
            } catch (Exception e) {
                log.error("物理回收垃圾文件失败: {}", trash.getObjectKey(), e);
            }
        }
        log.info("结束 [DELETED 物理垃圾回收]: 彻底抹除 {} 个云端垃圾文件", successCount);
    }
}