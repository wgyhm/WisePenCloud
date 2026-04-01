package com.oriole.wisepen.document.api.constant;

import com.oriole.wisepen.resource.enums.ResourceType;

import java.util.Set;

/**
 * 文档服务常量：声明本服务对文件类型的策略（允许上传范围、需要转换的格式），
 * 与平台通用类型枚举 {@link ResourceType} 解耦。
 */
public class DocumentConstants {

    /** 单文件最大体积：100MB */
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /** 本服务允许上传的文件类型白名单 */
    public static final Set<ResourceType> ALLOWED_TYPES = Set.of(
            ResourceType.PDF,
            ResourceType.DOC,
            ResourceType.DOCX,
            ResourceType.PPT,
            ResourceType.PPTX,
            ResourceType.XLS,
            ResourceType.XLSX
    );

    /**
     * 需要经过 Office→PDF 转换流水线的文件类型。
     * 不在此集合中的 {@link #ALLOWED_TYPES} 成员（即 PDF）可直接使用，无需转换。
     */
    public static final Set<ResourceType> OFFICE_TYPES = Set.of(
            ResourceType.DOC,
            ResourceType.DOCX,
            ResourceType.PPT,
            ResourceType.PPTX,
            ResourceType.XLS,
            ResourceType.XLSX
    );

    public static final int WATERMARK_SIZE = 64 * 1024; // 此值不可修改（除非全部重新预处理所有文件）
}
