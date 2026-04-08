package com.oriole.wisepen.file.storage.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 存储场景枚举：决定文件的物理存放路径与公开级别
 */
@Getter
@AllArgsConstructor
public enum StorageSceneEnum {

    PUBLIC_IMAGE_FOR_USER("public/images/user"),    // 公开图床 (如头像、文章插图，无需鉴权即可访问)
    PUBLIC_IMAGE_FOR_GROUP("public/images"),
    // TODO:public/images/note 是权宜之计，应在Q2改为 private/images/note
    PRIVATE_IMAGE_FOR_NOTE("public/images/note"),  // 私密图床 (如笔记中的图片，需 STS Token 访问)
    PRIVATE_DOC("private/docs");      // 文档 (如 PDF、Word，永远在私有域)

    private final String prefix;
}