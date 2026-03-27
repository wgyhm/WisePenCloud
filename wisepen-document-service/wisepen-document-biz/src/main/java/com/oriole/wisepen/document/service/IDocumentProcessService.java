package com.oriole.wisepen.document.service;

import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.domain.entity.DocumentInfoEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;

/**
 * 文档处理流水线服务：封装 document_info 状态机推进和 MongoDB 内容归档操作，
 * 供各阶段 Consumer 调用，避免 Consumer 直接持有 Mapper / Repository。
 */
public interface IDocumentProcessService {

    /**
     * 推进文档状态机。
     *
     * @param documentId 文档唯一 ID
     * @param status     目标状态
     */
    void updateStatus(String documentId, DocumentStatusEnum status);

    /**
     * 将提取出的纯文本归档到 MongoDB。
     *
     * @param documentId 文档唯一 ID
     * @param rawText    从 PDF 中提取的纯文本
     * @return MongoDB document 的 _id（即 textMongoId）
     */
    String saveContent(String documentId, String rawText);

    /**
     * Stage 4 最终收敛：将 {@link DocumentReadyMessage} 中的预览 ObjectKey、MongoDB 文本 ID
     * 回写到 document_info，并将状态推进至终态 READY。
     *
     * @param msg 就绪事件消息
     */
    void finalizeToReady(DocumentReadyMessage msg);

    /**
     * 将文档标记为 FAILED，并记录错误摘要供用户查看和重试。
     *
     * @param documentId   文档唯一 ID
     * @param errorMessage 错误摘要（不含堆栈，仅描述失败原因）
     */
    void markFailed(String documentId, String errorMessage);

    /**
     * 重试前重置：清除 errorMessage，将状态回拨至 UPLOADED，以便重新派发解析任务。
     * 仅在 FAILED 状态下调用有意义。
     *
     * @param documentId 文档唯一 ID
     */
    void resetForRetry(String documentId);

    /**
     * 判断文档是否仍处于活跃状态（未被软删除）。
     * Consumer 在长耗时操作前可调用此方法检测是否需要提前退出。
     *
     * @param documentId 文档唯一 ID
     * @return 文档存在且未删除时返回 true
     */
    boolean isActive(String documentId);

    /**
     * 按 ID 查询文档实体。
     *
     * @param documentId 文档唯一 ID
     * @return 对应的 {@link DocumentInfoEntity}，不存在时返回 null
     */
    DocumentInfoEntity getDocumentInfo(String documentId);

    /**
     * 将 Stage 3 从 PDF 文件中提取的结构元数据写入 MongoDB。
     *
     * @param meta 包含页面对象编号、文件大小、XREF 偏移等信息的实体
     */
    void savePdfMeta(DocumentPdfMetaEntity meta);

    /**
     * 查询文档的 PDF 结构元数据。
     *
     * @param documentId 文档唯一 ID
     * @return 对应的 {@link DocumentPdfMetaEntity}，文档尚未就绪或未找到时返回 null
     */
    DocumentPdfMetaEntity getPdfMeta(String documentId);
}
