package com.oriole.wisepen.document.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.mapper.DocumentInfoMapper;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.resource.domain.dto.ResourceUpdateReqDTO;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_UPLOADED;

/**
 * 文件已上传事件消费者（Stage 2）
 * <p>
 * 消费 storage 服务发布的 {@code FileUploadedMessage}，完成以下工作：
 * <ol>
 *   <li>按 {@code objectKey} 查找对应的 {@code DocumentInfoEntity}</li>
 *   <li>状态守卫：仅处理 UPLOADING / UPLOADED 状态，幂等跳过其他状态</li>
 *   <li>"不信任前端"：用 OSS 回传的真实 size 覆盖 resource 服务的元数据</li>
 *   <li>推进文档状态至 UPLOADED</li>
 *   <li>派发 {@link DocumentParseTaskMessage} 到解析队列，触发 Stage 3</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadedConsumer {

    private static final Set<DocumentStatusEnum> PROCESSABLE_STATUSES =
            Set.of(DocumentStatusEnum.UPLOADING, DocumentStatusEnum.UPLOADED);

    private final DocumentInfoMapper documentInfoMapper;
    private final RemoteResourceService remoteResourceService;
    private final IDocumentProcessService documentProcessService;
    private final KafkaDocumentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-document-upload-callback-group")
    public void onFileUploaded(String payload) {
        FileUploadedMessage msg;
        try {
            msg = objectMapper.readValue(payload, FileUploadedMessage.class);
        } catch (Exception e) {
            log.error("FileUploadedMessage 反序列化失败, payload={}", payload, e);
            return;
        }

        try {
            process(msg);
        } catch (Exception e) {
            log.error("FileUploadedConsumer 处理失败, objectKey={}", msg.getObjectKey(), e);
        }
    }

    private void process(FileUploadedMessage msg) {
        // 按 objectKey 查找文档记录（一个 objectKey 对应唯一文档）
        DocumentInfoEntity doc = documentInfoMapper.selectOne(
                Wrappers.<DocumentInfoEntity>lambdaQuery()
                        .eq(DocumentInfoEntity::getSourceObjectKey, msg.getObjectKey())
                        .last("LIMIT 1")
        );

        if (doc == null) {
            log.warn("未找到对应文档, objectKey={}", msg.getObjectKey());
            return;
        }

        if (!PROCESSABLE_STATUSES.contains(doc.getStatus())) {
            log.debug("文档已处理过, 跳过. documentId={}, status={}", doc.getDocumentId(), doc.getStatus());
            return;
        }

        // 用 OSS 回传的真实 size 覆盖 resource 服务的占位元数据（documentId = resourceId）
        remoteResourceService.updateAttributes(
                ResourceUpdateReqDTO.builder()
                        .resourceId(doc.getDocumentId())
                        .size(msg.getSize())
                        .build()
        );

        // 推进状态机（秒传时已是 UPLOADED，此处为幂等更新）
        documentProcessService.updateStatus(doc.getDocumentId(), DocumentStatusEnum.UPLOADED);

        eventPublisher.publishParseTask(
                DocumentParseTaskMessage.builder()
                        .documentId(doc.getDocumentId())
                        .sourceObjectKey(doc.getSourceObjectKey())
                        .fileType(doc.getFileType())
                        .build()
        );

        log.info("文档上传回调处理完成: documentId={}, size={}", doc.getDocumentId(), msg.getSize());
    }
}
