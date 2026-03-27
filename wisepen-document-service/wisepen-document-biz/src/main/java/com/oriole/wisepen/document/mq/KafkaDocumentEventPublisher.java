package com.oriole.wisepen.document.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_PARSE;
import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_READY;

/**
 * 文档服务 Kafka 事件发布器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDocumentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发布文档解析任务（Stage 2 → Stage 3）
     */
    public void publishParseTask(DocumentParseTaskMessage msg) {
        send(TOPIC_DOCUMENT_PARSE, msg.getDocumentId(), msg);
    }

    /**
     * 发布文档处理就绪事件（Stage 3 → Stage 4）
     */
    public void publishReadyEvent(DocumentReadyMessage msg) {
        send(TOPIC_DOCUMENT_READY, msg.getDocumentId(), msg);
    }

    private void send(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
            log.debug("Kafka 事件已发布: topic={}, key={}", topic, key);
        } catch (Exception e) {
            log.error("Kafka 事件发布失败: topic={}, key={}", topic, key, e);
        }
    }
}
