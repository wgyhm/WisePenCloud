package com.oriole.wisepen.document.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.oriole.wisepen.document.api.domain.base.DocumentInfoBase;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_info")
public class DocumentInfoEntity extends DocumentInfoBase {

    /**
     * 文档唯一 ID，直接复用 resource 服务返回的 resourceId（全局唯一，无需自造 ID）。
     * MySQL 类型：VARCHAR，由服务层在 initUploadDocument 时写入。
     */
    @TableId(type = IdType.INPUT)
    private String documentId;

    /**
     * 文件类型（枚举，小写扩展名为 DB 存储值）。
     * 本服务处理流水线根据 {@link com.oriole.wisepen.document.api.constant.DocumentConstants#OFFICE_TYPES}
     * 决定是否执行 Office→PDF 转换。
     */
    private ResourceType fileType;

    /**
     * 前端申报的文件大小（字节），用于计算上传超时阈值。
     * 不作为最终文件大小的真实来源（以 OSS 回调为准）。
     */
    private Long size;

    /**
     * 预览可见页数上限（null 表示不限制）。
     * 由管理员或文档交易微服务写入，预览接口据此裁剪 PDF 页数。
     */
    private Integer maxPreviewPages;

    /**
     * 转换失败时记录的错误摘要，供用户重试前查看。
     * 仅在 status=FAILED 时有意义。
     */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
