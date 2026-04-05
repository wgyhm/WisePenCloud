package com.oriole.wisepen.note.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.note.api.domain.mq.NoteSnapshotMessage;
import com.oriole.wisepen.note.service.INoteVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.note.api.constant.MqTopicConstants.TOPIC_NOTE_SNAPSHOT;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteSnapshotConsumer {

    private final INoteVersionService noteVersionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC_NOTE_SNAPSHOT,
            groupId = "wisepen-note-snapshot-group",
            properties = {
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    public void onSnapshot(String payload) throws JsonProcessingException {
        NoteSnapshotMessage msg = objectMapper.readValue(payload, NoteSnapshotMessage.class);
        log.info("接收到 Note 快照（事件） ResourceId={} | Version={} | Type={}", msg.getResourceId(), msg.getVersion(), msg.getType());
        noteVersionService.createVersion(msg);
        log.info("已处理 Note 快照（事件） ResourceId={} | Version={} | Type={}", msg.getResourceId(), msg.getVersion(), msg.getType());
    }
}