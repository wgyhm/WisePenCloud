package com.oriole.wisepen.document.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.service.IDocumentPreviewService;
import com.oriole.wisepen.document.service.IDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文档管理接口（对外开放）
 */
@RestController
@RequestMapping("/document")
@RequiredArgsConstructor
public class DocumentController {

    private final IDocumentService documentService;
    private final IDocumentPreviewService documentPreviewService;

    /**
     * 上传初始化：前端提交 md5/filename/extension/expectedSize，
     * 后端返回 OSS 预签名直传 URL，前端直接将文件字节流 PUT 到 OSS。
     */
    @PostMapping("/initDocUpload")
    public R<DocumentUploadInitResponse> initUpload(@Valid @RequestBody DocumentUploadInitRequest request) {
        Long uploaderId = SecurityContextHolder.getUserId();
        return R.ok(documentService.initUploadDocument(request, uploaderId));
    }

    /**
     * 重试转换：仅在文档处于 FAILED 状态时可调用，用于转换失败后的人工重试。
     */
    @PostMapping("/retryDocConvert")
    public R<Void> retryConvert(@RequestParam String documentId) {
        documentService.retryDocumentConvert(documentId);
        return R.ok();
    }

    @GetMapping("/getDocPreview")
    public void previewDocument(@RequestParam String documentId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        String userId = String.valueOf(SecurityContextHolder.getUserId());
        documentPreviewService.handlePreviewRequest(request, response, documentId, userId);
    }

    /**
     * 删除文档或取消上传。
     * <p>
     * 在任意阶段均可调用：上传中（取消上传）、转换中（异步退出）、已就绪（删除）。
     * </p>
     */
    @PostMapping("/removeDoc")
    public R<Void> cancelOrDeleteDocument(@RequestParam String documentId) {
        documentService.cancelOrDeleteDocument(documentId);
        return R.ok();
    }
}
