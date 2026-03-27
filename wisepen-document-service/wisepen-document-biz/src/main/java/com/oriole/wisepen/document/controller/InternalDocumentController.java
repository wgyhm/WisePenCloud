package com.oriole.wisepen.document.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.exception.DocumentErrorCode;
import com.oriole.wisepen.document.mapper.DocumentInfoMapper;
import com.oriole.wisepen.document.service.IDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文档远程调用接口（供其他微服务通过 Feign 调用，网关应屏蔽外网访问）
 */
@RestController
@RequestMapping("/internal/document")
@RequiredArgsConstructor
public class InternalDocumentController {

    private final IDocumentService documentService;

    /**
     * 按 documentId（即 resourceId）查询文档基础信息
     */
    @GetMapping("/getDocInfo")
    public R<DocumentInfoEntity> getDocumentInfo(@RequestParam String documentId) {
        return R.ok(documentService.getDocumentInfo(documentId));
    }
}
