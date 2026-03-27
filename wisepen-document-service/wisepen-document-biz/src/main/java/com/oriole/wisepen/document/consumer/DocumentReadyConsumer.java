package com.oriole.wisepen.document.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import com.oriole.wisepen.document.service.IDocumentProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_READY;

/**
 * 文档处理就绪事件消费者（Stage 4）
 * <p>
 * 消费 {@code wisepen-document-ready-topic} 上的就绪事件，执行最终收敛：
 * <ul>
 *   <li>将 PDF 预览 ObjectKey 和 MongoDB 文本 ID 回写到 {@code document_info}</li>
 *   <li>将文档状态推进至终态 {@code READY}</li>
 * </ul>
 * 状态到达 READY 后，下游模块（ES 索引、Agentic 等）可以订阅此 Topic 异步消费。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentReadyConsumer {

    private final IDocumentProcessService documentProcessService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_DOCUMENT_READY, groupId = "wisepen-document-ready-group")
    public void onDocumentReady(String payload) {
        DocumentReadyMessage msg;
        try {
            msg = objectMapper.readValue(payload, DocumentReadyMessage.class);
        } catch (Exception e) {
            log.error("DocumentReadyMessage 反序列化失败, payload={}", payload, e);
            return;
        }

        try {
            documentProcessService.finalizeToReady(msg);
        } catch (Exception e) {
            log.error("文档就绪状态收敛失败: documentId={}", msg.getDocumentId(), e);
        }
    }
}
