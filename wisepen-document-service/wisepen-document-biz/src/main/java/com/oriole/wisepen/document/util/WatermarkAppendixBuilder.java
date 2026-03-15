package com.oriole.wisepen.document.util;

import com.oriole.wisepen.document.domain.entity.DocumentPdfMetaEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 增量更新水印附录构建器（O(1) 预埋模式）。
 *
 * <h3>工作原理</h3>
 * <p>Stage 3 在预览 PDF 中预埋了一个空的 Form XObject（/WisepenWM），
 * 并在每页 Content Stream 末尾追加了 {@code q /WisepenWM Do Q} 调用指令。
 * 预览时，本构建器仅在文件尾部追加 2 个新对象：
 * <ol>
 *   <li>微型点阵暗水印 Image XObject（128×128 Raw 灰度，16384 字节）</li>
 *   <li>Form XObject（覆盖 preHookObjNum，含明/暗水印绘制指令）</li>
 * </ol>
 * PDF 阅读器加载文件时，XREF 增量段的记录会覆盖旧对象定义，
 * 所有已有的 {@code /WisepenWM Do} 调用均会调用新版本。
 *
 * <h3>暗水印平铺策略</h3>
 * Tile 以其自然物理尺寸（{@link MicroDotCodec#TILE_PT} pt）无拉伸平铺。
 * 对于 A4 页面（595×842 pt）：
 * <ul>
 *   <li>列数：floor(595 / 61.44) = 9</li>
 *   <li>行数：floor(842 / 61.44) = 13</li>
 *   <li>总副本：117 份（优于旧方案的 81 份）</li>
 * </ul>
 * 由于 {@link MicroDotCodec#TILE_PT} 为常量，cols/rows 仅取决于存储的页面尺寸，
 * appendixSize 仍然可在 Stage 3 精确预量。
 *
 */
public final class WatermarkAppendixBuilder {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 明水印文本中 userId 段的固定字符宽度。public 供 Consumer 读取做 dummy 预量。 */
    public static final int USER_ID_FIELD_WIDTH = 16;

    private WatermarkAppendixBuilder() {
    }

    /**
     * 构建水印增量更新附录字节流。
     *
     * @param meta      从 MongoDB 加载的 PDF 结构元数据
     * @param userId    当前用户 ID
     * @param time      水印时间戳
     * @param aesKeyB64 AES-128 密钥（Base64）
     * @return 增量更新附录字节，拼接在 originalSize 之后即构成完整的虚拟 PDF
     */
    public static byte[] build(DocumentPdfMetaEntity meta,
                               String userId,
                               LocalDateTime time,
                               String aesKeyB64) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String wmText = String.format("%-" + USER_ID_FIELD_WIDTH + "s  %s", userId, time.format(TIME_FMT));

        float pageW = meta.getPages().get(0).getWidthPt();
        float pageH = meta.getPages().get(0).getHeightPt();
        float cx = pageW / 2f;
        float cy = pageH / 2f;

        long currentOffset = meta.getOriginalSize();
        List<long[]> xrefEntries = new ArrayList<>();

        // -------------------------------------------------------
        // 对象 1：微型点阵 Image XObject（128×128 Raw 灰度）
        // -------------------------------------------------------
        int darkImgObjNum = meta.getLastObjectId() + 1;
        xrefEntries.add(new long[]{darkImgObjNum, currentOffset});

        byte[] imgObj = buildImageXObject(darkImgObjNum,
                MicroDotCodec.buildRawTileBytes(userId, aesKeyB64));
        out.write(imgObj);
        currentOffset += imgObj.length;

        // -------------------------------------------------------
        // 对象 2：Form XObject（覆盖预埋占位符）
        // -------------------------------------------------------
        int formObjNum = meta.getPreHookObjNum();
        xrefEntries.add(new long[]{formObjNum, currentOffset});

        byte[] formObj = buildFormXObject(formObjNum, darkImgObjNum,
                wmText, pageW, pageH, cx, cy);
        out.write(formObj);
        currentOffset += formObj.length;

        // -------------------------------------------------------
        // XREF 增量段 + Trailer
        // -------------------------------------------------------
        long newXrefOffset = currentOffset;
        out.write(buildXref(xrefEntries));
        out.write(buildTrailer(darkImgObjNum + 1, meta.getXrefOffset(),
                newXrefOffset, meta.getCatalogObjNum()));

        return out.toByteArray();
    }

    // =========================================================================
    //  私有：对象序列化
    // =========================================================================

    private static byte[] buildImageXObject(int objNum, byte[] rawPixels) throws IOException {
        String header = objNum + " 0 obj\n" +
                "<< /Type /XObject /Subtype /Image" +
                " /Width " + MicroDotCodec.TILE_SIZE +
                " /Height " + MicroDotCodec.TILE_SIZE +
                " /ColorSpace /DeviceGray /BitsPerComponent 8" +
                " /Length " + rawPixels.length + " >>\nstream\n";
        String footer = "\nendstream\nendobj\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(rawPixels);
        out.write(footer.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static byte[] buildFormXObject(int objNum, int darkImgObjNum,
                                            String wmText,
                                            float pageW, float pageH,
                                            float cx, float cy) throws IOException {
        String contentStream = buildWatermarkContentStream(wmText, pageW, pageH, cx, cy);
        byte[] csBytes = contentStream.getBytes(StandardCharsets.US_ASCII);

        String dictHeader = objNum + " 0 obj\n" +
                "<< /Type /XObject /Subtype /Form\n" +
                "   /BBox [0 0 " + ff(pageW) + " " + ff(pageH) + "]\n" +
                "   /Resources <<\n" +
                "     /XObject << /DarkImg " + darkImgObjNum + " 0 R >>\n" +
                "     /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> >>\n" +
                // GS1: 明水印 25% 透明度；GS2: 暗水印 3% 透明度（抗物理拍摄最低阈值）
                "     /ExtGState << /GS1 << /Type /ExtGState /ca 0.250 /CA 0.250 >>\n" +
                "                   /GS2 << /Type /ExtGState /ca 0.030 /CA 0.030 >> >>\n" +
                "   >>\n" +
                "   /Length " + csBytes.length + "\n" +
                ">>\nstream\n";
        String footer = "\nendstream\nendobj\n";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(dictHeader.getBytes(StandardCharsets.US_ASCII));
        out.write(csBytes);
        out.write(footer.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    /**
     * 构建 Form XObject 的绘制指令 content stream。
     *
     * <p>暗水印使用自然步进平铺（无拉伸）：Tile 以 {@link MicroDotCodec#TILE_PT} pt
     * 的物理尺寸在页面上均匀铺设，A4 下约 9×13 = 117 份副本。
     * 由于 TILE_PT 为常量且格式化为 {@code %.3f}，内容流字节数完全由 cols/rows
     * 决定，而 cols/rows 由存储的页面尺寸唯一确定，appendixSize 仍可精确预量。
     */
    private static String buildWatermarkContentStream(String wmText,
                                                       float pageW, float pageH,
                                                       float cx, float cy) {
        StringBuilder sb = new StringBuilder();

        // 明水印：45° 对角文字，25% 透明度
        sb.append("q\n");
        sb.append("/GS1 gs\n");
        sb.append("0.400 g\n");
        sb.append("BT\n");
        sb.append("/F1 14 Tf\n");
        sb.append("0.707 0.707 -0.707 0.707 ").append(ff(cx)).append(' ').append(ff(cy)).append(" Tm\n");
        sb.append('(').append(wmText).append(") Tj\n");
        sb.append("ET\n");
        sb.append("Q\n");

        // 暗水印：自然步进平铺，3% 透明度
        float tilePt = MicroDotCodec.TILE_PT;
        int cols = (int) (pageW / tilePt);
        int rows = (int) (pageH / tilePt);

        sb.append("q\n");
        sb.append("/GS2 gs\n");
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                sb.append("q ")
                        .append(ff(tilePt)).append(" 0 0 ").append(ff(tilePt))
                        .append(' ').append(ff(col * tilePt))
                        .append(' ').append(ff(row * tilePt))
                        .append(" cm /DarkImg Do Q\n");
            }
        }
        sb.append("Q\n");

        return sb.toString();
    }

    // =========================================================================
    //  私有：XREF + Trailer
    // =========================================================================

    private static byte[] buildXref(List<long[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("xref\n".getBytes(StandardCharsets.US_ASCII));
        for (long[] entry : entries) {
            out.write((entry[0] + " 1\n").getBytes(StandardCharsets.US_ASCII));
            out.write(String.format("%010d 00000 n \r\n", entry[1])
                    .getBytes(StandardCharsets.US_ASCII));
        }
        return out.toByteArray();
    }

    private static byte[] buildTrailer(int size, long prevXref, long newXrefOffset, int catalogObjNum) {
        return ("trailer\n<< /Size " + size
                + " /Prev " + prevXref
                + " /Root " + catalogObjNum + " 0 R >>\n"
                + "startxref\n" + newXrefOffset + "\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII);
    }

    private static String ff(float v) {
        return String.format("%.3f", v);
    }
}
