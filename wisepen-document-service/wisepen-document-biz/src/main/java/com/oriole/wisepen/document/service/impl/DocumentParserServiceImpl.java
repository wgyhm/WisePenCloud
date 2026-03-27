package com.oriole.wisepen.document.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.exception.DocumentErrorCode;
import com.oriole.wisepen.document.service.IDocumentParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.office.OfficeException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * 文档解析服务实现：整合 Office→PDF 转换（jodconverter）和纯文本提取（PDFBox）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserServiceImpl implements IDocumentParserService {

    private final DocumentConverter documentConverter;

    @Override
    public void convertToPdf(File source, File target) {
        long start = System.currentTimeMillis();
        log.info("Office→PDF 转换开始: {} ({} bytes)", source.getName(), source.length());
        try {
            documentConverter.convert(source).to(target).execute();
            log.info("Office→PDF 转换完成: {} ms", System.currentTimeMillis() - start);
        } catch (OfficeException e) {
            log.error("Office→PDF 转换失败: {} ms, file={}", System.currentTimeMillis() - start, source.getName(), e);
            throw new ServiceException(DocumentErrorCode.DOCUMENT_CONVERT_ERROR);
        }
    }

    @Override
    public String extractText(File pdfFile) {
        log.debug("文本提取开始: {}", pdfFile.getName());
        try (PDDocument doc = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 保留段落换行，去除多余空白
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            log.debug("文本提取完成: {} chars", text.length());
            return text;
        } catch (IOException e) {
            log.error("文本提取失败: {}", pdfFile.getName(), e);
            throw new ServiceException(DocumentErrorCode.DOCUMENT_READ_ERROR);
        }
    }
}
