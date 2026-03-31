package com.oriole.wisepen.resource.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 平台资源类型枚举。
 * <p>
 * 覆盖所有受平台管理的资源类型，包括有实体文件的文档类型和无扩展名的笔记类型。
 * 通过 {@link #fromExtension(String)} 将文件扩展名字符串（大小写不敏感）转换为枚举值；
 * 返回 {@code null} 代表该扩展名未在平台注册，调用方视为不支持。
 * </p>
 * <p>
 * 注意：哪些类型允许上传、哪些类型需要格式转换，属于各业务微服务自身的策略，
 * 不在此枚举中声明。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ResourceType {

    /** 无扩展名的笔记，由笔记服务管理，不经过文件上传流程 */
    NOTE("note"),
    PDF("pdf"),
    DOC("doc"),
    DOCX("docx"),
    PPT("ppt"),
    PPTX("pptx"),
    XLS("xls"),
    XLSX("xlsx");

    /** 枚举标识值（文件类扩展名小写，NOTE 使用逻辑标识），同时作为 DB 存储值和 JSON 序列化值 */
    @EnumValue
    @JsonValue
    private final String extension;

    private static final Map<String, ResourceType> EXT_MAP = new HashMap<>();

    static {
        for (ResourceType e : values()) {
            EXT_MAP.put(e.extension, e);
        }
    }

    /**
     * 根据扩展名（大小写不敏感）查找对应枚举值。
     *
     * @param ext 扩展名或类型标识字符串，如 "docx"、"PDF"、"note"
     * @return 对应枚举值，不支持时返回 {@code null}
     */
    @JsonCreator
    public static ResourceType fromExtension(String ext) {
        if (ext == null) {
            return null;
        }
        return EXT_MAP.get(ext.toLowerCase());
    }
}
