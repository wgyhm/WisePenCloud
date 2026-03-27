package com.oriole.wisepen.document.repository;

import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * PDF 结构元数据的 MongoDB Repository。
 */
public interface DocumentPdfMetaRepository extends MongoRepository<DocumentPdfMetaEntity, String> {
}
