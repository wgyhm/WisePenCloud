package com.oriole.wisepen.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.mapper.DocumentInfoMapper;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档上传超时巡检定时任务。
 * <p>
 * 每隔 {@code wisepen.document.stale-check-delay-ms}（默认 5 分钟）扫描一次处于 UPLOADING 状态的文档。
 * 超时阈值按文件大小动态计算，防止大文件被误判；
 * 最小 {@code baseTimeoutMs}（默认 10 分钟），最大 {@code maxTimeoutMs}（默认 60 分钟）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentScheduleServiceImpl {

    private final DocumentInfoMapper documentInfoMapper;
    private final IDocumentProcessService documentProcessService;
    private final KafkaDocumentEventPublisher eventPublisher;
    private final RemoteStorageService remoteStorageService;
    private final DocumentProperties documentProperties;

    @Scheduled(fixedDelayString = "${wisepen.document.stale-check-delay-ms:300000}")
    public void detectStaleUploads() {
        List<DocumentInfoEntity> uploading = documentInfoMapper.selectList(
                Wrappers.<DocumentInfoEntity>lambdaQuery()
                        .eq(DocumentInfoEntity::getStatus, DocumentStatusEnum.UPLOADING)
        );

        if (uploading.isEmpty()) {
            return;
        }

        log.debug("巡检启动：发现 {} 个 UPLOADING 文档，开始超时检测", uploading.size());

        LocalDateTime now = LocalDateTime.now();
        for (DocumentInfoEntity doc : uploading) {
            long timeoutMs = calculateTimeoutMs(doc.getSize());
            LocalDateTime deadline = doc.getCreateTime().plusNanos(timeoutMs * 1_000_000L);
            if (now.isAfter(deadline)) {
                handleStaleDocument(doc);
            }
        }
    }

    private void handleStaleDocument(DocumentInfoEntity doc) {
        try {
            StorageRecordDTO record = remoteStorageService.getFileRecord(doc.getSourceObjectKey()).getData();
            if (record != null) {
                // storage 确认文件已存在，属于 OSS 回调丢失场景，执行补偿
                documentProcessService.updateStatus(doc.getDocumentId(), DocumentStatusEnum.UPLOADED);
                eventPublisher.publishParseTask(DocumentParseTaskMessage.builder()
                        .documentId(doc.getDocumentId())
                        .sourceObjectKey(doc.getSourceObjectKey())
                        .fileType(doc.getFileType())
                        .build());
                log.info("OSS 回调补偿成功，已重新派发解析任务: documentId={}", doc.getDocumentId());
            } else {
                documentProcessService.updateStatus(doc.getDocumentId(), DocumentStatusEnum.TRANSFER_TIMEOUT);
                log.warn("文档上传超时，文件未到达 OSS: documentId={}", doc.getDocumentId());
            }
        } catch (Exception e) {
            log.error("处理超时文档异常: documentId={}", doc.getDocumentId(), e);
        }
    }

    /**
     * 根据文件大小动态计算上传超时阈值（毫秒）。
     * <pre>
     *   timeout = clamp(baseTimeoutMs, size / assumedSpeedBps * 1000, maxTimeoutMs)
     * </pre>
     * 示例：100MB 文件（assumedSpeedBps=100KB/s）→ 约 17 分钟；
     * 500MB 文件 → 约 87 分钟，被 maxTimeoutMs 截断至 60 分钟。
     */
    private long calculateTimeoutMs(Long size) {
        if (size == null || size <= 0) {
            return documentProperties.getBaseTimeoutMs();
        }
        long sizeBasedMs = size * 1000L / documentProperties.getAssumedSpeedBps();
        return Math.max(documentProperties.getBaseTimeoutMs(),
                Math.min(documentProperties.getMaxTimeoutMs(), sizeBasedMs));
    }
}
