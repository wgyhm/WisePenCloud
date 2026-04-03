package com.oriole.wisepen.resource.mq;

import com.oriole.wisepen.resource.domain.mq.AclRecalculateMessage;
import com.oriole.wisepen.resource.service.IResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.resource.constant.MqTopicConstants.TOPIC_ACL_RECALC;

@Slf4j
@Component
@RequiredArgsConstructor
public class AclRecalculateConsumer {

    private final IResourceService resourceService;

    @KafkaListener(topics = TOPIC_ACL_RECALC, groupId = "wisepen-resource-acl-recalc-group")
    public void onAclRecalculate(AclRecalculateMessage message) {
        log.debug("接收到 ACL 重算事件, ResourceId: {}", message.getResourceId());
        resourceService.
                calculateResourceGroupAcl(message.getResourceId());
    }
}