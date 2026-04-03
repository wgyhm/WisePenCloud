package com.oriole.wisepen.resource.service.impl;

import com.oriole.wisepen.resource.constant.MqTopicConstants;
import com.oriole.wisepen.resource.domain.mq.AclRecalculateMessage;
import com.oriole.wisepen.resource.service.IEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_ACL_RECALC;

@Slf4j
@Component // 或者 @Service
@RequiredArgsConstructor
public class KafkaEventPublisherImpl implements IEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishAclRecalculateEvent(String resourceId, String triggerSource) {
        try {
            AclRecalculateMessage msg = AclRecalculateMessage.builder()
                    .resourceId(resourceId)
                    .triggerSource(triggerSource)
                    .build();

            kafkaTemplate.send(TOPIC_ACL_RECALC, resourceId, msg);
            log.debug("成功发布 ACL 重算事件, ResourceId: {}", resourceId);
        } catch (Exception e) {
            log.error("发布 ACL 重算事件失败, ResourceId: {}", resourceId, e);
        }
    }

    @Override
    public void publishResDeletedEvent(List<String> resourceIds) {
        try {
            kafkaTemplate.send(MqTopicConstants.TOPIC_RESOURCE_PHYSICAL_DESTROY, resourceIds);
            log.debug("成功发布资源删除事件, ResourceId: {}", resourceIds);
        } catch (Exception e) {
            log.error("发布资源删除事件失败, ResourceId: {}", resourceIds, e);
        }
    }
}