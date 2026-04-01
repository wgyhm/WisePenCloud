package com.oriole.wisepen.document.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.constant.DocumentConstants;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.exception.DocumentErrorCode;
import com.oriole.wisepen.document.mapper.DocumentInfoMapper;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.repository.DocumentContentRepository;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import com.oriole.wisepen.document.service.IDocumentService;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文档上传服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements IDocumentService {

    private final DocumentInfoMapper documentInfoMapper;
    private final DocumentContentRepository contentRepository;
    private final IDocumentProcessService documentProcessService;
    private final KafkaDocumentEventPublisher eventPublisher;
    private final RemoteStorageService remoteStorageService;
    private final RemoteResourceService remoteResourceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadInitResponse initUploadDocument(DocumentUploadInitRequest request, Long uploaderId) {
        ResourceType fileType = ResourceType.fromExtension(request.getExtension());
        if (fileType == null || !DocumentConstants.ALLOWED_TYPES.contains(fileType)) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_TYPE_NOT_ALLOWED);
        }

        // 向 resource 服务注册资源占位，返回的 resourceId 即作为本文档的唯一 ID
        R<String> resourceR = remoteResourceService.createResource(ResourceCreateReqDTO.builder()
                .resourceName(request.getFilename())
                .resourceType(fileType)
                .ownerId(String.valueOf(uploaderId))
                .size(request.getSize())
                .build());
        if (resourceR.getCode() != 200 || resourceR.getData() == null) {
            log.error("resource 服务注册资源失败: code={}, msg={}", resourceR.getCode(), resourceR.getMsg());
            throw new ServiceException(DocumentErrorCode.DOCUMENT_UPLOAD_ERROR);
        }
        String documentId = resourceR.getData();

        // 向 storage 服务申请预签名直传 URL，以 documentId 作为 bizPath 便于追踪
        R<UploadInitRespDTO> storageR = remoteStorageService.initUpload(UploadInitReqDTO.builder()
                .md5(request.getMd5())
                .extension(fileType.getExtension())
                .scene(StorageSceneEnum.PRIVATE_DOC)
                .bizPath(documentId)
                .expectedSize(request.getSize())
                .build());
        if (storageR.getCode() != 200 || storageR.getData() == null) {
            log.error("storage 服务申请上传 URL 失败: code={}, msg={}", storageR.getCode(), storageR.getMsg());
            throw new ServiceException(DocumentErrorCode.DOCUMENT_UPLOAD_ERROR);
        }
        UploadInitRespDTO storageData = storageR.getData();

        // 确定初始状态：秒传直接进入 UPLOADED（跳过等待前端直传）
        DocumentStatusEnum initialStatus = Boolean.TRUE.equals(storageData.getFlashUploaded())
                ? DocumentStatusEnum.UPLOADED
                : DocumentStatusEnum.UPLOADING;

        DocumentInfoEntity doc = new DocumentInfoEntity();
        doc.setDocumentId(documentId);
        doc.setFileType(fileType);
        doc.setSize(request.getSize());
        doc.setSourceObjectKey(storageData.getObjectKey());
        doc.setStatus(initialStatus);
        documentInfoMapper.insert(doc);

        log.info("文档上传初始化完成: documentId={}, objectKey={}, flashUploaded={}",
                documentId, storageData.getObjectKey(), storageData.getFlashUploaded());

        DocumentUploadInitResponse resp = BeanUtil.copyProperties(storageData, DocumentUploadInitResponse.class);
        resp.setDocumentId(documentId);
        return resp;
    }

    @Override
    public void retryDocumentConvert(String documentId) {
        DocumentInfoEntity doc = documentInfoMapper.selectById(documentId);
        if (doc == null) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (doc.getStatus() != DocumentStatusEnum.FAILED) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_OPERATION_FORBIDDEN);
        }

        documentProcessService.resetForRetry(documentId);

        eventPublisher.publishParseTask(DocumentParseTaskMessage.builder()
                .documentId(documentId)
                .sourceObjectKey(doc.getSourceObjectKey())
                .fileType(doc.getFileType())
                .build());

        log.info("文档重试解析已触发: documentId={}", documentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrDeleteDocument(String documentId) {
        DocumentInfoEntity doc = documentInfoMapper.selectById(documentId);
        if (doc == null) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }

        // 收集所有已知的 OSS 对象键：source 在 initUploadDocument 时写入，preview 在 Stage 3 完成后写入
        List<String> objectKeys = Stream.of(doc.getSourceObjectKey(), doc.getPreviewObjectKey())
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        if (!objectKeys.isEmpty()) {
            try {
                remoteStorageService.deleteFiles(objectKeys);
            } catch (Exception e) {
                // 存储侧删除失败不阻塞本地清理，但需记录，运维可根据日志手动补偿
                log.warn("存储文件删除失败，继续清理本地记录: documentId={}", documentId, e);
            }
        }

        try {
            remoteResourceService.removeResource(documentId);
        } catch (Exception e) {
            log.warn("资源服务记录删除失败，继续清理本地记录: documentId={}", documentId, e);
        }

        if (StrUtil.isNotBlank(doc.getTextMongoId())) {
            contentRepository.deleteById(doc.getTextMongoId());
        }

        documentInfoMapper.deleteById(documentId);
        log.info("文档已删除/取消: documentId={}", documentId);
    }

    @Override
    public DocumentInfoEntity getDocumentInfo(String documentId) {
        DocumentInfoEntity documentInfoEntity = documentInfoMapper.selectById(documentId);
        if (documentInfoEntity == null) {
            throw new ServiceException(DocumentErrorCode.DOCUMENT_NOT_FOUND);
        }
        return documentInfoEntity;
    }
}
