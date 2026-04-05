package com.oriole.wisepen.file.storage.api.constant;
/**
 * 存储服务消息队列 Topic 常量
 */
public interface MqTopicConstants {
    /** 文件上传/秒传就绪事件 */
    String TOPIC_FILE_UPLOADED = "wisepen-storage-file-uploaded-topic";
    String TOPIC_FILE_DELETE = "wisepen-storage-file-delete-topic";
}