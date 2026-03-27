package com.oriole.wisepen.document.consumer;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.constant.DocumentConstants;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.mq.KafkaDocumentEventPublisher;
import com.oriole.wisepen.document.service.IDocumentParserService;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import com.oriole.wisepen.document.util.WatermarkPreProcessor;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.oriole.wisepen.document.util.WatermarkAppendixBuilder;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_PARSE;

/**
 * 文档解析流水线消费者（Stage 3）
 * <p>
 * 消费 {@code wisepen-document-parse-topic} 上的解析任务，执行以下步骤：
 * <ol>
 *   <li>将文档状态推进至 {@code CONVERTING}</li>
 *   <li>通过 storage Feign 获取内网下载 URL，将源文件下载到本地临时目录</li>
 *   <li>Office 文件（doc/ppt/xls 等）经 jodconverter 转换为 PDF；PDF 文件直接使用</li>
 *   <li>使用 PDFBox PDFTextStripper 从 PDF 中提取纯文本内容</li>
 *   <li>向 storage 申请新的预签名直传 URL，后端自身将 PDF 上传至 OSS</li>
 *   <li>检查文档是否已被取消——若是，立即删除刚上传的预览文件（防孤儿对象）</li>
 *   <li>将纯文本写入 MongoDB，获取 textMongoId</li>
 *   <li>发布 {@link DocumentReadyMessage} 到就绪事件 Topic，触发 Stage 4</li>
 * </ol>
 * 任意步骤抛出异常时，文档状态回落为 {@code FAILED}，错误摘要写入 errorMessage 字段，
 * 并清理所有本地临时文件。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseConsumer {

    /** 复用单例 HttpClient，用于下载源文件和上传 PDF 预览至 OSS 预签名 URL */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final RemoteStorageService remoteStorageService;
    private final IDocumentParserService documentParserService;
    private final IDocumentProcessService documentProcessService;
    private final KafkaDocumentEventPublisher eventPublisher;
    private final DocumentProperties documentProperties;
    private final ObjectMapper objectMapper;
    private final WatermarkPreProcessor watermarkPreProcessor;

    @KafkaListener(topics = TOPIC_DOCUMENT_PARSE, groupId = "wisepen-document-parse-group")
    public void onDocumentParse(String payload) {
        DocumentParseTaskMessage msg;
        try {
            msg = objectMapper.readValue(payload, DocumentParseTaskMessage.class);
        } catch (Exception e) {
            log.error("DocumentParseTaskMessage 反序列化失败, payload={}", payload, e);
            return;
        }

        try {
            process(msg);
        } catch (Exception e) {
            log.error("文档解析失败: documentId={}", msg.getDocumentId(), e);
            documentProcessService.markFailed(msg.getDocumentId(), e.getMessage());
        }
    }

    // ==================== 核心流水线 ====================

    private void process(DocumentParseTaskMessage msg) throws Exception {
        // 用户可能在任务派发后取消了文档，提前退出避免浪费下载/转换资源
        if (!documentProcessService.isActive(msg.getDocumentId())) {
            log.info("文档已取消，跳过解析: documentId={}", msg.getDocumentId());
            return;
        }

        documentProcessService.updateStatus(msg.getDocumentId(), DocumentStatusEnum.CONVERTING);

        String downloadUrl = remoteStorageService.getDownloadUrl(msg.getSourceObjectKey(), null).getData();
        String ext = msg.getFileType().getExtension();
        File sourceFile = downloadSourceFile(downloadUrl, msg.getDocumentId(), ext);

        boolean isOffice = DocumentConstants.OFFICE_TYPES.contains(msg.getFileType());
        // Office 文件需要额外的转换临时文件；PDF 文件直接复用 sourceFile 对象
        File pdfFile = isOffice ? createCacheFile(msg.getDocumentId(), ".pdf") : sourceFile;
        // hookedPdf 声明在 try 外，以便 finally 能正确清理
        File hookedPdf = createCacheFile(msg.getDocumentId(), "_hook.pdf");

        try {
            // Office → PDF 格式转换（jodconverter 调用本地 LibreOffice 实例）
            if (isOffice) {
                documentParserService.convertToPdf(sourceFile, pdfFile);
            }

            // 基于 PDF 文件提取纯文本，用于后续建索引（PDFBox PDFTextStripper）
            String rawText = documentParserService.extractText(pdfFile);

            // 预埋空水印占位 Form XObject（/WisepenWM），生成 hooked PDF
            // 上传至 OSS 的是 hooked PDF，而非原始 pdfFile
            DocumentPdfMetaEntity meta = watermarkPreProcessor.processAndExtractMeta(pdfFile, hookedPdf);

            // 将 hooked PDF 上传至 OSS
            String previewKey = uploadPreviewPdf(msg.getDocumentId(), hookedPdf);

            // 上传预览是耗时操作，期间用户可能已取消文档；若已取消，立即删除刚上传的孤儿预览对象
            if (!documentProcessService.isActive(msg.getDocumentId())) {
                deleteOrphanPreview(previewKey);
                log.info("文档已取消，孤儿预览文件已清理: documentId={}", msg.getDocumentId());
                return;
            }

            // 将纯文本写入 MongoDB，获取 textMongoId 供 Stage 4 回写到 document_info
            String textMongoId = documentProcessService.saveContent(msg.getDocumentId(), rawText);
            meta.setDocumentId(msg.getDocumentId());
            documentProcessService.savePdfMeta(meta);

            eventPublisher.publishReadyEvent(DocumentReadyMessage.builder()
                    .documentId(msg.getDocumentId())
                    .previewObjectKey(previewKey)
                    .textMongoId(textMongoId)
                    .build());

            log.info("文档解析完成: documentId={}, previewKey={}", msg.getDocumentId(), previewKey);

        } finally {
            deleteSilently(sourceFile);
            if (isOffice) {
                deleteSilently(pdfFile);
            }
            deleteSilently(hookedPdf);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 流式下载源文件到本地缓存目录（使用 Java HttpClient 避免内存中间缓冲）。
     */
    private File downloadSourceFile(String url, String documentId, String ext) throws Exception {
        Path dir = Paths.get(documentProperties.getCachePath());
        Files.createDirectories(dir);
        Path target = dir.resolve(documentId + "_source." + ext);

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(target));
        return target.toFile();
    }

    /**
     * 向 storage 服务申请 PDF 预览文件的预签名直传 URL，然后由本服务后端
     * 自身通过 HTTP PUT 将 PDF 上传至 OSS（携带 OSS 回调 header）。
     *
     * @return previewObjectKey（PDF 在 OSS 中的 ObjectKey）
     */
    private String uploadPreviewPdf(String documentId, File pdfFile) throws Exception {
        UploadInitRespDTO storageData = remoteStorageService.initUpload(
                UploadInitReqDTO.builder()
                        .extension("pdf")
                        .scene(StorageSceneEnum.PRIVATE_DOC)
                        .bizPath(documentId + "_preview")
                        .build()
        ).getData();

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(storageData.getPutUrl()))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(pdfFile.toPath()));
        if (StrUtil.isNotBlank(storageData.getCallbackHeader())) {
            reqBuilder.header("x-oss-callback", storageData.getCallbackHeader());
        }
        HttpResponse<Void> resp = HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("PDF 上传至 OSS 失败, statusCode=" + resp.statusCode());
        }
        return storageData.getObjectKey();
    }

    /**
     * 删除已上传至 OSS 但因文档取消而成为孤儿的预览文件。
     * 失败时仅记录警告，不应影响取消流程的最终结果。
     */
    private void deleteOrphanPreview(String previewKey) {
        try {
            remoteStorageService.deleteFiles(List.of(previewKey));
        } catch (Exception e) {
            log.warn("孤儿预览文件清理失败，需人工处理: previewKey={}", previewKey, e);
        }
    }

    /** 在缓存目录下创建临时文件（用于存放 Office→PDF 转换产物）。 */
    private File createCacheFile(String documentId, String suffix) throws Exception {
        Path dir = Paths.get(documentProperties.getCachePath());
        Files.createDirectories(dir);
        return Files.createTempFile(dir, documentId + "_", suffix).toFile();
    }

    /** 静默删除本地临时文件，失败时仅打印警告，不影响主流程。 */
    private void deleteSilently(File file) {
        if (file != null && file.exists()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception e) {
                log.warn("临时文件删除失败: {}", file.getAbsolutePath());
            }
        }
    }
}
