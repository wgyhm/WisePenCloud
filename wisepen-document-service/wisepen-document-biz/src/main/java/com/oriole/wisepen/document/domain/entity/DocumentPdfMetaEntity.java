package com.oriole.wisepen.document.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * PDF 结构元数据（MongoDB），在 Stage 3 生成预览 PDF 后写入，
 * 供后续 Range-Request 劫持式增量更新预览接口使用。
 *
 * <p>与 document_info（MySQL）共用同一 documentId 作为主键，做到一对一关联。
 */
@Data
@Document(collection = "document_pdf_meta")
public class DocumentPdfMetaEntity {

    /** 与 MySQL document_info.document_id 一一对应 */
    @Id
    private String documentId;

    /**
     * 原始（无水印）预览 PDF 的字节大小。
     * Range 请求落在 [0, originalSize) 时直接透传 OSS；
     * 落在 [originalSize, originalSize+appendixSize) 时动态生成水印附录。
     */
    @Field("original_size")
    private long originalSize;

    /**
     * 原始 PDF 最后一个 XREF 段的字节偏移（即 startxref 之后的数字）。
     * 增量更新的新 Trailer 需通过 {@code /Prev} 指向此位置。
     */
    @Field("xref_offset")
    private long xrefOffset;

    /**
     * 原始 PDF 中最高的对象编号。
     * 增量更新中新增对象从 {@code lastObjectId + 1} 开始顺序分配。
     */
    @Field("last_object_id")
    private int lastObjectId;

    /**
     * 预先量算的水印增量更新附录大小（字节）。
     * 因 userId 为固定长度、时间戳格式固定，图像以 Raw 编码，
     * 附录大小仅取决于 PDF 结构（页数、每页字典大小），与具体用户无关。
     * <p>
     * {@code Content-Length = originalSize + appendixSize}
     * <p>
     * 0 表示尚未计算（流式预览功能未启用）。
     */
    @Field("appendix_size")
    private long appendixSize;

    /**
     * 文档目录（Catalog）对象编号，即原始 Trailer 中 {@code /Root} 所指向的对象。
     * 增量更新 Trailer 必须包含 /Root，否则 pdf.js 等严格解析器会抛出
     * "Invalid Root reference." 错误。
     */
    @Field("catalog_obj_num")
    private int catalogObjNum;

    /**
     * Stage 3 在 PDF 中预埋的空 Form XObject（命名为 /WisepenWM）的对象编号。
     * 增量更新附录通过覆盖此对象 ID 来"填充"真实水印内容，
     * 无需修改任何 Page Dict 或 Content Stream（O(1) 预埋方案的核心）。
     */
    @Field("pre_hook_obj_num")
    private int preHookObjNum;

    /** 各页的 PDF 对象信息，顺序与页面索引一致。 */
    @Field("pages")
    private List<PageMeta> pages;

    /**
     * 单页元数据，供增量更新时定位并覆盖页面字典对象。
     */
    @Data
    public static class PageMeta {

        /** 页面字典的 PDF 对象编号（PDF 对象格式：{@code objNum genNum obj}）。 */
        @Field("obj_num")
        private int objNum;

        /** 页面字典的代号，绝大多数情况下为 0。 */
        @Field("gen_num")
        private int genNum;

        /** 页面宽度（PDF 点，1pt = 1/72 inch）。 */
        @Field("width_pt")
        private float widthPt;

        /** 页面高度（PDF 点）。 */
        @Field("height_pt")
        private float heightPt;
    }
}
