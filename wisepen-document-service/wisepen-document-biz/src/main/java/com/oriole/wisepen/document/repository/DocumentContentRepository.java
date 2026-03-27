package com.oriole.wisepen.document.repository;

import com.oriole.wisepen.document.domain.entity.DocumentContentEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 文档纯文本内容 MongoDB Repository
 */
@Repository
public interface DocumentContentRepository extends MongoRepository<DocumentContentEntity, String> {

    void deleteByDocumentId(String documentId);
}
