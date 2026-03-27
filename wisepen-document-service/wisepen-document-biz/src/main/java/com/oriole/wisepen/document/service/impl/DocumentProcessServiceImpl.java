package com.oriole.wisepen.document.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentContentEntity;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.mapper.DocumentInfoMapper;
import com.oriole.wisepen.document.repository.DocumentContentRepository;
import com.oriole.wisepen.document.repository.DocumentPdfMetaRepository;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessServiceImpl implements IDocumentProcessService {

    private final DocumentInfoMapper documentInfoMapper;
    private final DocumentContentRepository contentRepository;
    private final DocumentPdfMetaRepository pdfMetaRepository;

    @Override
    public void updateStatus(String documentId, DocumentStatusEnum status) {
        DocumentInfoEntity update = new DocumentInfoEntity();
        update.setDocumentId(documentId);
        update.setStatus(status);
        documentInfoMapper.updateById(update);
        log.debug("文档状态已更新: documentId={}, status={}", documentId, status);
    }

    @Override
    public String saveContent(String documentId, String rawText) {
        DocumentContentEntity content = new DocumentContentEntity();
        content.setDocumentId(documentId);
        content.setRawText(rawText);
        content.setCreateTime(LocalDateTime.now());
        String mongoId = contentRepository.save(content).getId();
        log.debug("文档文本已归档: documentId={}, textMongoId={}", documentId, mongoId);
        return mongoId;
    }

    @Override
    public void finalizeToReady(DocumentReadyMessage msg) {
        // BeanUtil 将 msg 中同名字段（documentId、previewObjectKey、textMongoId）直接映射到实体
        DocumentInfoEntity update = BeanUtil.copyProperties(msg, DocumentInfoEntity.class);
        update.setStatus(DocumentStatusEnum.READY);
        documentInfoMapper.updateById(update);
        log.info("文档已就绪: documentId={}", msg.getDocumentId());
    }

    @Override
    public void markFailed(String documentId, String errorMessage) {
        DocumentInfoEntity update = new DocumentInfoEntity();
        update.setDocumentId(documentId);
        update.setStatus(DocumentStatusEnum.FAILED);
        update.setErrorMessage(errorMessage);
        documentInfoMapper.updateById(update);
        log.warn("文档解析失败，已记录错误: documentId={}, error={}", documentId, errorMessage);
    }

    @Override
    public void resetForRetry(String documentId) {
        // 需要将 errorMessage 显式置为 NULL，updateById 会忽略 null 字段，故改用 update wrapper
        documentInfoMapper.update(null, Wrappers.<DocumentInfoEntity>lambdaUpdate()
                .eq(DocumentInfoEntity::getDocumentId, documentId)
                .set(DocumentInfoEntity::getStatus, DocumentStatusEnum.UPLOADED)
                .set(DocumentInfoEntity::getErrorMessage, null)
        );
        log.info("文档已重置为可重试状态: documentId={}", documentId);
    }

    @Override
    public boolean isActive(String documentId) {
        return documentInfoMapper.selectById(documentId) != null;
    }

    @Override
    public DocumentInfoEntity getDocumentInfo(String documentId) {
        return documentInfoMapper.selectById(documentId);
    }

    @Override
    public void savePdfMeta(DocumentPdfMetaEntity meta) {
        pdfMetaRepository.save(meta);
        log.debug("PDF 结构元数据已写入 MongoDB: documentId={}, pages={}, originalSize={}",
                meta.getDocumentId(), meta.getPages() == null ? 0 : meta.getPages().size(), meta.getOriginalSize());
    }

    @Override
    public DocumentPdfMetaEntity getPdfMeta(String documentId) {
        return pdfMetaRepository.findById(documentId).orElse(null);
    }
}
