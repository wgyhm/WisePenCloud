package com.oriole.wisepen.document.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文档预览服务：增量更新 + Range Request 劫持模式。
 *
 * <h3>虚拟文件模型</h3>
 * <pre>
 *   [0 ──────── originalSize)   ← OSS 原始预埋 PDF，按 Range 透传
 *   [originalSize ── totalSize) ← 动态生成的水印附录（含真实 userId + 时间戳）
 * </pre>
 *
 * <p>对外宣告的 {@code Content-Length = originalSize + appendixSize}，
 * pdf.js 按需发起的 Range 请求均可被正确处理，前半段零内存透传 OSS，
 * 后半段内存生成约 10 KB 的增量附录。
 */
public interface IDocumentPreviewService {

    /**
     * 处理预览请求（含 Range 支持）。
     * <p>
     * 方法内部解析 {@code Range} 请求头，按虚拟文件模型分段响应：
     * <ul>
     *   <li>无 Range / 全量请求：先流式透传 OSS 原始字节，再追加水印附录。</li>
     *   <li>Range 落在原始段：向 OSS 发起同 Range 子请求，管道透传。</li>
     *   <li>Range 落在附录段：动态生成附录，按偏移切片返回。</li>
     *   <li>Range 跨越两段边界：OSS 尾部 + 附录头部拼接返回。</li>
     * </ul>
     *
     * @param request    HTTP 请求（读取 Range 头）
     * @param response   HTTP 响应（写入状态码、Content-Range、PDF 字节流）
     * @param documentId 文档唯一 ID
     * @param userId     当前用户 ID，写入水印用于溯源
     */
    void handlePreviewRequest(HttpServletRequest request,
                              HttpServletResponse response,
                              String documentId,
                              String userId);
}
