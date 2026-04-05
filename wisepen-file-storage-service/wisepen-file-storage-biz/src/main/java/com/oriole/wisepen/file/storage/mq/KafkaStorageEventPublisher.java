package com.oriole.wisepen.file.storage.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_UPLOADED;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaStorageEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishFileUploadedEvent(FileUploadedMessage msg) {
        try {
            // 统一使用 Jackson 序列化
            String jsonMessage = objectMapper.writeValueAsString(msg);
            // 将 MD5 作为 Kafka 的 Key，保证相同文件的消息能被路由到同一个 Partition，保证顺序消费
            kafkaTemplate.send(TOPIC_FILE_UPLOADED, msg.getMd5(), jsonMessage);
            log.debug("成功发布文件就绪事件, ObjectKey: {}", msg.getObjectKey());
        } catch (Exception e) {
            log.error("发布文件就绪事件失败, ObjectKey: {}", msg.getObjectKey(), e);
        }
    }
}