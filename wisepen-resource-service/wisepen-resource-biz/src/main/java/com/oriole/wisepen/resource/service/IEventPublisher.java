package com.oriole.wisepen.resource.service;

import java.util.List;

public interface IEventPublisher {
    void publishAclRecalculateEvent(String resourceId, String triggerSource);
    void publishResDeletedEvent(List<String> resourceIds);
}
