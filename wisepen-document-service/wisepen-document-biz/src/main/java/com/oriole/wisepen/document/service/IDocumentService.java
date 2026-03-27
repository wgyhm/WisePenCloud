package com.oriole.wisepen.document.service;

import com.oriole.wisepen.document.api.domain.dto.req.DocumentUploadInitRequest;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentUploadInitResponse;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;

/**
 * 文档生命周期管理服务
 */
public interface IDocumentService {

    /**
     * 上传初始化：向 storage 服务申请预签名直传 URL，向 resource 服务注册资源占位，
     * 本地落库 document_info（status=UPLOADING），返回 putUrl 等给前端。
     *
     * @param request    初始化请求（md5、filename、extension、size）
     * @param uploaderId 上传者用户 ID
     * @return 初始化响应（documentId、putUrl、objectKey、flashUploaded）
     */
    DocumentUploadInitResponse initUploadDocument(DocumentUploadInitRequest request, Long uploaderId);

    /**
     * 重试转换：仅当文档处于 FAILED 状态时可调用，重置错误信息并重新派发解析任务。
     *
     * @param documentId 文档唯一 ID
     */
    void retryDocumentConvert(String documentId);

    /**
     * 删除文档或取消上传，在任意阶段均可调用。
     * <ul>
     *   <li>调用 storage 服务删除 OSS 上已有的文件对象</li>
     *   <li>调用 resource 服务移除资源注册记录</li>
     *   <li>删除 MongoDB 中的文本内容（若已写入）</li>
     *   <li>软删除本地 document_info 记录</li>
     * </ul>
     * 若文档正在转换中（CONVERTING），解析 Consumer 会在上传预览文件后检查活跃状态并清理孤儿文件。
     *
     * @param documentId 文档唯一 ID
     */
    void cancelOrDeleteDocument(String documentId);

    DocumentInfoEntity getDocumentInfo(String documentId);
}
