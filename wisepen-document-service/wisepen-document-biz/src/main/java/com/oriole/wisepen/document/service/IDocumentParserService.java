package com.oriole.wisepen.document.service;

import java.io.File;

/**
 * 文档解析服务：封装 Office→PDF 格式转换和纯文本提取两个能力。
 * <p>
 * Office 转换依赖本地 LibreOffice 实例（jodconverter）；
 * 文本提取依赖 Apache Tika（自动发现 classpath 上的 PDFBox 解析器）。
 * </p>
 */
public interface IDocumentParserService {

    /**
     * 将 Office 文件（doc/docx/ppt/pptx/xls/xlsx）转换为 PDF。
     *
     * @param source 源 Office 文件
     * @param target 目标 PDF 文件（已创建的空文件）
     */
    void convertToPdf(File source, File target);

    /**
     * 从文件中提取纯文本内容（通常传入 PDF）。
     * <p>
     * Tika 通过 ServiceLoader 自动发现 classpath 上的解析器（本服务引入了
     * tika-parser-pdf-module，因此支持 PDF 文本提取）。
     * </p>
     *
     * @param file 要提取的本地文件
     * @return 提取出的纯文本，空文件时返回空字符串
     */
    String extractText(File file);
}
