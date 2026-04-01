package com.oriole.wisepen.document.api.domain.mq;

import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文档解析任务消息（派发至 Kafka 解析队列）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档唯一 ID（即 resource 服务的 resourceId） */
    private String documentId;

    /** 源文件在 OSS 中的 ObjectKey */
    private String sourceObjectKey;

    /** 文件类型 */
    private ResourceType fileType;
}
