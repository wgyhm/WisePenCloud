package com.oriole.wisepen.note.consumer;

import com.oriole.wisepen.note.service.impl.NoteServiceImpl;
import com.oriole.wisepen.resource.domain.mq.ResourceDeletedMessage;
import com.oriole.wisepen.resource.enums.ResourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_RESOURCE_PHYSICAL_DESTROY;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceDeletedConsumer {

    private final NoteServiceImpl noteService;

    @KafkaListener(
            topics = TOPIC_RESOURCE_PHYSICAL_DESTROY,
            groupId = "wisepen-resource-physical-destroy-group"
    )
    public void onResourceDeleted(ResourceDeletedMessage message) {
        Map<ResourceType, List<String>> typedMap = message.getTypedResourceIds();
        // 笔记服务只关心 NOTE 类型的资源
        List<String> noteIds = typedMap.get(ResourceType.NOTE);
        if (noteIds != null && !noteIds.isEmpty()) {
            log.info("接收到 Note 资源硬删除事件 ResourceIds={}", noteIds.toString());
            noteService.deleteNotes(noteIds);
            log.info("已处理 Note 资源硬删除事件 ResourceIds={}", noteIds.toString());
        }
    }
}