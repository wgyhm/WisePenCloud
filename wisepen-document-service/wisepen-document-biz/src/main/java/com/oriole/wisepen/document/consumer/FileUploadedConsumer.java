package com.oriole.wisepen.document.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.repository.DocumentInfoRepository;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_UPLOADED;

/**
 * （Stage 2）文档已上传
 * <p>
 * 消费 {@code FileUploadedMessage}，执行以下步骤：
 * <ol>
 *   <li>按 {@code objectKey} 查找对应的 {@code DocumentInfoEntity}</li>
 *   <li>状态守卫：仅处理 UPLOADING / UPLOADED 状态，幂等跳过其他状态</li>
 *   <li>用 OSS 回传的真实 size 覆盖上传元数据</li>
 *   <li>推进文档状态至 UPLOADED</li>
 *   <li>派发 {@link DocumentParseTaskMessage} 到解析队列，触发 Stage 3</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadedConsumer {

    private final DocumentInfoRepository documentInfoRepository;
    private final IDocumentService documentService;

    private final RemoteStorageService remoteStorageService;

    private final KafkaDocumentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-document-upload-callback-group")
    public void onFileUploaded(String payload) throws JsonProcessingException {
        FileUploadedMessage msg = objectMapper.readValue(payload, FileUploadedMessage.class);
        process(msg);
    }

    private void process(FileUploadedMessage msg) {
        if (msg.getScene() != StorageSceneEnum.PRIVATE_DOC || Boolean.TRUE.equals(msg.getFlashUploaded())){
            return; // 不处理非PRIVATE_DOC的上传通知，也不处理秒传的
        }

        DocumentInfoEntity entity = documentInfoRepository.findBySourceObjectKeyOrPreviewObjectKey(msg.getObjectKey()).orElse(null);
        if (entity == null) {
            // 用户已经取消文件处理，删除文档
            eventPublisher.publishFileDeleteEvent(List.of(msg.getObjectKey()));
            log.warn("未找到对应文档，已经删除上传的文件 ObjectKey={}", msg.getObjectKey());
            return;
        }

        if (DocumentStatusEnum.UPLOADING != entity.getDocumentStatus().getStatus()) {
            log.debug("文档已处理, 跳过 DocumentId={} | Status={}", entity.getDocumentId(), entity.getDocumentStatus().getStatus());
            return;
        }

        // 用 OSS 回传的真实 size 覆盖
        entity.getUploadMeta().setSize(msg.getSize());
        documentInfoRepository.save(entity);

        // 推进状态机
        documentService.updateStatus(entity.getDocumentId(), new DocumentStatus(DocumentStatusEnum.UPLOADED));
        eventPublisher.publishParseTask(
                DocumentParseTaskMessage.builder()
                        .documentId(entity.getDocumentId())
                        .sourceObjectKey(entity.getSourceObjectKey())
                        .fileType(entity.getUploadMeta().getFileType())
                        .build()
        );
        log.info("文档上传回调处理完成 DocumentId={}", entity.getDocumentId());
    }
}
