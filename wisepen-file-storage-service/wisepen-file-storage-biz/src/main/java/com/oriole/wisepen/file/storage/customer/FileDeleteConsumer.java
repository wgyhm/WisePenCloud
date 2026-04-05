package com.oriole.wisepen.file.storage.customer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.file.storage.service.IStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_DELETE;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeleteConsumer {

    private final IStorageService storageService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC_FILE_DELETE,
            groupId = "wisepen-storage-file-delete-group"
    )
    public void onObjectDeleted(String payload) throws JsonProcessingException {
        List<String> objectKeys = objectMapper.readValue(payload, new TypeReference<List<String>>() {});
        log.debug("接收到 Object 删除事件 objectKeys={}", objectKeys);
        storageService.deleteFiles(objectKeys);
        log.debug("已处理 Object 删除事件  objectKeys={}", objectKeys);
    }
}