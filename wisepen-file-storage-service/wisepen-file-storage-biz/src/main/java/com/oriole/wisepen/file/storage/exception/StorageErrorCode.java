package com.oriole.wisepen.file.storage.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件模块错误码枚举
 * 范围：2000-2999
 *
 * @author Ian.xiong
 */
@Getter
@AllArgsConstructor
public enum StorageErrorCode implements IErrorCode {

    // ======== 基础校验错误 (30000) ========
    PROVIDER_NOT_SUPPORTED(30001, "不支持的存储驱动类型"),
    FILE_SIZE_EXCEEDED(30002, "图床文件大小超过限制"),
    FILE_TYPE_UNSUPPORTED(30003, "不支持的图片格式"),
    SCENE_TYPE_UNSUPPORTED(30004, "不支持的场景"),
    INVALID_URL_FORMAT(30005, "无效的永久 URL 格式"),

    // ======== 流程与安全错误 (30100) ========
    RECORD_NOT_FOUND(30100, "文件物理记录不存在"),
    CALLBACK_SIGNATURE_INVALID(30101, "非法回调请求，签名校验失败"),
    STS_TOKEN_GENERATE_FAILED(30102, "STS临时凭证生成失败"),
    CALLBACK_POLICY_GENERATE_FAILED(30103, "直传回调策略生成失败"),

    // ======== 底层读写操作错误 (30200) ========
    FILE_UPLOAD_ERROR(30200, "文件物理推流上传失败"),
    FILE_DOWNLOAD_ERROR(30201, "获取文件下载直链失败"),
    FILE_DELETE_ERROR(30202, "物理文件删除失败"),
    FILE_COPY_ERROR(30203, "物理文件拷贝（秒传）失败"),
    FILE_READ_ERROR(30204, "文件读取失败");

    private final Integer code;
    private final String msg;
}
