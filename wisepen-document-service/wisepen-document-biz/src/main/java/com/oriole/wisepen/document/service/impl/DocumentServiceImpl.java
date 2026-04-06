package com.oriole.wisepen.document.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.constant.DocumentConstants;
import com.oriole.wisepen.document.api.domain.base.DocumentInfoBase;
import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.domain.base.DocumentUploadMeta;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentContentEntity;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.exception.DocumentErrorCode;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.repository.DocumentContentRepository;
import com.oriole.wisepen.document.repository.DocumentInfoRepository;
import com.oriole.wisepen.document.repository.DocumentPdfMetaRepository;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import com.oriole.wisepen.resource.domain.dto.ResourceCreateReqDTO;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.feign.RemoteResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档上传服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements IDocumentService {

    private final DocumentInfoRepository documentInfoRepository;
    private final DocumentContentRepository documentContentRepository;
    private final DocumentPdfMetaRepository documentPdfMetaRepository;
    private final KafkaDocumentEventPublisher eventPublisher;

    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;


    @Override
    public DocumentUploadInitResponse initUploadDocument(DocumentUploadInitRequest request, Long uploaderId) {
        ResourceType fileType = ResourceType.fromExtension(request.getExtension());
        if (fileType == null || !DocumentConstants.ALLOWED_TYPES.contains(fileType)) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        String documentId = IdUtil.fastSimpleUUID();
        // 向 storage 服务申请预签名直传 URL，以 documentId 作为 bizTag
        UploadInitRespDTO uploadInitRespDTO;
        try {
            uploadInitRespDTO = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                    .md5(request.getMd5())
                    .extension(fileType.getExtension())
                    .scene(StorageSceneEnum.PRIVATE_DOC)
                    .bizTag(documentId)
                    .expectedSize(request.getExpectedSize())
                    .build()).getData();
        }
        catch (Exception e) {
            log.warn("存储服务申请上传 URL 失败", e);
            throw new ServiceException(DocumentErrorCode.DOCUMENT_UPLOAD_ERROR, e.getMessage());
        }

        // NOTE: 无需检查是否为快传，Kafka将收到并处理文件上传成功的消息

        DocumentUploadMeta meta = DocumentUploadMeta.builder().fileType(fileType)
                .documentName(request.getFilename())
                .size(request.getExpectedSize())
                .uploaderId(uploaderId).build();

        DocumentInfoEntity doc = DocumentInfoEntity.builder()
                .documentId(documentId)
                .uploadMeta(meta)
                .sourceObjectKey(uploadInitRespDTO.getObjectKey())
                .documentStatus(new DocumentStatus(DocumentStatusEnum.UPLOADING)).build();
        documentInfoRepository.save(doc);

        log.info("文档上传初始化完成 DocumentId={} | ObjectKey={} | FlashUploaded={}",
                documentId, uploadInitRespDTO.getObjectKey(), uploadInitRespDTO.getFlashUploaded());

        DocumentUploadInitResponse resp = BeanUtil.copyProperties(uploadInitRespDTO, DocumentUploadInitResponse.class);
        resp.setDocumentId(documentId);
        return resp;
    }

    @Override
    public List<DocumentInfoBase> listPendingDocs(Long uploaderId) {
        List<DocumentInfoEntity> entities = documentInfoRepository.findByUploaderIdAndStatusIn(
                uploaderId,
                List.of(DocumentStatusEnum.UPLOADING,
                        DocumentStatusEnum.UPLOADED,
                        DocumentStatusEnum.TRANSFER_TIMEOUT,
                        DocumentStatusEnum.CONVERTING_AND_PARSING,
                        DocumentStatusEnum.FAILED
                )
        );
        return entities.stream()
                .map(entity -> (DocumentInfoBase) entity)
                .collect(Collectors.toList());
    }

    @Override
    public void assertDocumentUploader(String documentId, Long uploaderId) {
        DocumentInfoEntity entity = documentInfoRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        if (!uploaderId.equals(entity.getUploadMeta().getUploaderId())) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
    }

    @Override
    public void retryDocProcess(String documentId) {
        DocumentInfoEntity entity = documentInfoRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        if (entity.getDocumentStatus().getStatus() != DocumentStatusEnum.FAILED && entity.getDocumentStatus().getStatus() != DocumentStatusEnum.REGISTERING_RES_TIMEOUT) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_RETRY_STATE_INVALID);
        }
        switch (entity.getDocumentStatus().getStatus()) {
            case FAILED:
                this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.UPLOADED));
                eventPublisher.publishParseTask(BeanUtil.copyProperties(entity, DocumentParseTaskMessage.class));
                break;
            case REGISTERING_RES_TIMEOUT:
                this.finalizeToReady(documentId);
                break;
        }
        log.info("文档解析事件已发送 DocumentId={}", documentId);
    }

    @Override
    public void deletedDocument(String documentId) {
        DocumentInfoEntity entity = documentInfoRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        if (entity.getDocumentStatus().getStatus() == DocumentStatusEnum.READY) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_ALREADY_READY);
        }

        if (entity.getDocumentStatus().getStatus() == DocumentStatusEnum.CONVERTING_AND_PARSING) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_CONVERTING_AND_PARSING);
        }

        // NOTE：在CONVERTING_AND_PARSING或READY时不能终止
        // 但由于CONVERTING_AND_PARSING可能意外失败而未能进入READY，仍需清理所有CONVERTING_AND_PARSING可能产生的资产
        deleteDocuments(List.of(entity));
        log.info("文档处理任务已终止 DocumentId={}", documentId);
    }

    @Override
    public void deleteDocuments(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }

        // 查询获取所有文档实体
        Iterable<DocumentInfoEntity> entities = documentInfoRepository.findAllByResourceIdIn(resourceIds);
        deleteDocuments(entities);
    }

    private void deleteDocuments(Iterable<DocumentInfoEntity> entities) {
        List<String> documentIds = new ArrayList<>();
        List<String> allObjectKeys = new ArrayList<>();

        // 收集 OSS Keys
        for (DocumentInfoEntity entity : entities) {
            documentIds.add(entity.getDocumentId());
            if (StrUtil.isNotBlank(entity.getSourceObjectKey())) {
                allObjectKeys.add(entity.getSourceObjectKey());
            }
            if (StrUtil.isNotBlank(entity.getPreviewObjectKey())) {
                allObjectKeys.add(entity.getPreviewObjectKey());
            }
        }

        // 发送 OSS 文件删除消息
        if (!allObjectKeys.isEmpty()) {
            eventPublisher.publishFileDeleteEvent(allObjectKeys);
        }

        // 一次操作删除所有关联记录
        documentContentRepository.deleteAllById(documentIds);
        documentPdfMetaRepository.deleteAllById(documentIds);
        documentInfoRepository.deleteAllById(documentIds);
    }

    public Optional<DocumentStatus> getDocumentStatus(String documentId) {
        return documentInfoRepository.findById(documentId).map(DocumentInfoEntity::getDocumentStatus);
    }

    public DocumentStatus refreshDocumentStatus(String documentId) {
        DocumentInfoEntity entity = documentInfoRepository.findById(documentId)
                .orElseThrow(() -> new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND));

        // 如果状态不是正在上传
        if (entity.getDocumentStatus().getStatus() != DocumentStatusEnum.UPLOADING) {
            return entity.getDocumentStatus();
        } else {
            // 主动检查存储状态
            StorageRecordDTO storageRecordDTO;
            try {
                storageRecordDTO = remoteStorageService.getFileRecord(entity.getSourceObjectKey()).getData();
            } catch (Exception e) {
                log.warn("存储文件状态获取失败 DocumentId={}", documentId, e);
                throw new ServiceException(DocumentErrorCode.DOCUMENT_OPERATION_ERROR, e.getMessage());
            }
            if (storageRecordDTO != null) { // 未上传完成的文件无法获取storageRecordDTO
                entity.setDocumentStatus(new DocumentStatus(DocumentStatusEnum.UPLOADED));
                entity.setSourceObjectKey(storageRecordDTO.getObjectKey());
                documentInfoRepository.save(entity);
                eventPublisher.publishParseTask(
                        DocumentParseTaskMessage.builder()
                                .documentId(documentId)
                                .sourceObjectKey(storageRecordDTO.getObjectKey())
                                .fileType(entity.getUploadMeta().getFileType())
                                .build()
                );
            }
            return entity.getDocumentStatus();
        }
    }

    @Override
    public DocumentInfoBase getDocumentInfo(String resourceId) {
        return documentInfoRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Override
    public void updateStatus(String documentId, DocumentStatus status) {
        documentInfoRepository.updateStatusById(documentId, status);
        log.debug("文档状态已更新 DocumentId={} | Status={}", documentId, status);
    }

    @Override
    public void saveConversionAndParseResult(String documentId, String previewObjectKey, DocumentPdfMetaEntity meta, DocumentContentEntity content) {
        documentInfoRepository.updatePreviewObjectKeyById(documentId, previewObjectKey);
        content.setDocumentId(documentId);
        documentContentRepository.save(content);
        meta.setDocumentId(documentId);
        documentPdfMetaRepository.save(meta);
    }

    @Override
    public void finalizeToReady(String documentId) {
        this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.REGISTERING_RES));

        DocumentInfoEntity entity = documentInfoRepository.findById(documentId).orElse(null);
        if (entity == null || entity.getDocumentStatus().getStatus() == DocumentStatusEnum.READY) {
            return;
        }

        // 向 resource 服务注册资源
        String resourceId;
        try {
            resourceId = remoteResourceService.createResource(
                    ResourceCreateReqDTO.builder()
                            .resourceName(entity.getUploadMeta().getDocumentName())
                            .resourceType(entity.getUploadMeta().getFileType())
                            .ownerId(entity.getUploadMeta().getUploaderId().toString())
                            .size(entity.getUploadMeta().getSize())
                            .build()
            ).getData();
        } catch (Exception e) {
            log.error("resource 服务注册资源失败 DocumentId={}", documentId, e);
            this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.REGISTERING_RES_TIMEOUT));
            throw new ServiceException(DocumentErrorCode.DOCUMENT_REGISTER_RESOURCE_ERROR, e.getMessage());
        }
        documentInfoRepository.updateResourceIdById(documentId, resourceId);
        this.updateStatus(documentId, new DocumentStatus(DocumentStatusEnum.READY));

        eventPublisher.publishReadyEvent(DocumentReadyMessage.builder()
                .resourceId(resourceId)
                .content(documentContentRepository.findById(documentId).map(DocumentContentEntity::getRawText).orElse(null))
                .build());

        log.debug("文档已就绪 DocumentId={}", documentId);
    }
}
