package com.oriole.wisepen.document.consumer;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.constant.DocumentConstants;
import com.oriole.wisepen.document.api.domain.base.DocumentStatus;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import com.oriole.wisepen.document.config.DocumentProperties;
import com.oriole.wisepen.document.domain.entity.DocumentContentEntity;
import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;
import com.oriole.wisepen.document.service.IDocumentFileService;
import com.oriole.wisepen.document.service.IDocumentService;
import com.oriole.wisepen.document.util.WatermarkPreProcessor;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.feign.RemoteStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_PARSE;

/**
 * （Stage 3）文档解析
 * <p>
 * 消费 {@code wisepen-document-parse-topic} ，执行以下步骤：
 * <ol>
 *   <li>将文档状态推进至 {@code CONVERTING_AND_PARSING}</li>
 *   <li>获取内网下载 URL，将源文件下载到本地临时目录</li>
 *   <li>Office 文件经转换为 PDF；PDF 文件直接使用</li>
 *   <li>使用 PDFBox PDFTextStripper 从 PDF 中提取纯文本内容</li>
 *   <li>向 storage 申请新的预签名直传 URL，后端自身将 PDF 上传至 OSS</li>
 *   <li>同步相关信息到库，并结束文档解析阶段</li>
 * </ol>
 * 任意步骤抛出异常时，文档状态回落为 {@code FAILED}，错误摘要写入 errorMessage 字段，
 * 并清理所有本地临时文件。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentConversionAndParseConsumer {

    /** 复用单例 HttpClient，用于下载源文件和上传 PDF 预览至 OSS 预签名 URL */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final RemoteStorageService remoteStorageService;
    private final IDocumentFileService documentFileService;
    private final IDocumentService documentService;

    private final DocumentProperties documentProperties;
    private final ObjectMapper objectMapper;
    private final WatermarkPreProcessor watermarkPreProcessor;

    @KafkaListener(topics = TOPIC_DOCUMENT_PARSE, groupId = "wisepen-document-parse-group")
    public void onDocumentParse(String payload) throws IOException, InterruptedException {
        DocumentParseTaskMessage msg = objectMapper.readValue(payload, DocumentParseTaskMessage.class);
        process(msg);
    }

    private void process(DocumentParseTaskMessage msg) throws IOException, InterruptedException {

        DocumentStatus status = documentService.getDocumentStatus(msg.getDocumentId()).orElse(null);
        if (status != null && status.getStatus() != DocumentStatusEnum.UPLOADED) {
            log.info("文档处理前置状态异常，跳过解析 DocumentId={}", msg.getDocumentId());
            return;
        }

        // 将文档状态推进至 CONVERTING_AND_PARSING
        documentService.updateStatus(msg.getDocumentId(), new DocumentStatus(DocumentStatusEnum.CONVERTING_AND_PARSING));

        // 获取内网下载 URL，将源文件下载到本地临时目录
        String downloadUrl = remoteStorageService.getDownloadUrl(msg.getSourceObjectKey(), null).getData();
        String ext = msg.getFileType().getExtension();
        File sourceFile = downloadSourceFile(downloadUrl, msg.getDocumentId(), ext);

        // Office 文件经转换为 PDF；PDF 文件直接使用
        boolean isOffice = DocumentConstants.OFFICE_TYPES.contains(msg.getFileType());
        File pdfFile = isOffice ? createCacheFile(msg.getDocumentId(), ".pdf") : sourceFile;
        File hookedPdf = createCacheFile(msg.getDocumentId(), "_hook.pdf");
        try {
            // Office → PDF 格式转换
            if (isOffice) {
                documentFileService.convertToPdf(sourceFile, pdfFile);
            }
            // 基于 PDF 文件提取纯文本，用于后续全文检索
            String rawText = documentFileService.extractText(pdfFile);
            DocumentContentEntity content = DocumentContentEntity.builder().rawText(rawText).build();

            // 预埋空水印占位 Form XObject（/WisepenWM），生成 hooked PDF（预览PDF）
            // 上传至 OSS 的是 hooked PDF，而非原始 pdfFile
            DocumentPdfMetaEntity pdfMeta = watermarkPreProcessor.processAndExtractMeta(pdfFile, hookedPdf);
            // 将 hooked PDF 上传至 OSS
            String previewKey = uploadPreviewPdf(msg.getDocumentId(), hookedPdf);

            documentService.saveConversionAndParseResult(msg.getDocumentId(), previewKey, pdfMeta, content);
            documentService.finalizeToReady(msg.getDocumentId());
            log.info("文档解析完成 DocumentId={} | PreviewKey={}", msg.getDocumentId(), previewKey);

        } finally {
            deleteSilently(sourceFile);
            if (isOffice) {
                deleteSilently(pdfFile);
            }
            deleteSilently(hookedPdf);
        }
    }

    /** 流式下载源文件到本地缓存目录 */
    private File downloadSourceFile(String url, String documentId, String ext) throws IOException, InterruptedException {
        Path dir = Paths.get(documentProperties.getCachePath());
        Files.createDirectories(dir);
        Path target = dir.resolve(documentId + "_source." + ext);

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(target));
        return target.toFile();
    }

    /** 向 storage 服务申请 PDF 预览文件的预签名直传 URL，然后通过 HTTP PUT 将 PDF 上传至 OSS */
    private String uploadPreviewPdf(String documentId, File pdfFile) throws IOException, InterruptedException {
        UploadInitRespDTO storageData = remoteStorageService.initUpload(
                UploadInitReqDTO.builder()
                        .extension("pdf")
                        .scene(StorageSceneEnum.PRIVATE_DOC)
                        .bizTag(documentId + "_preview")
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
            throw new IllegalStateException("PDF 上传至 OSS 失败 StatusCode=" + resp.statusCode());
        }
        return storageData.getObjectKey();
    }

    /** 在缓存目录下创建临时文件（用于存放 Office→PDF 转换产物） */
    private File createCacheFile(String documentId, String suffix) throws IOException {
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
                log.warn("临时文件删除失败 Path={}", file.getAbsolutePath());
            }
        }
    }
}
