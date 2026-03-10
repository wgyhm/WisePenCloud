package com.oriole.wisepen.user.event;

import org.springframework.context.ApplicationEvent;
import lombok.Getter;

/**
 * 群组成员 Token 消耗事件
 */
@Getter
public class GroupTokenConsumeEvent extends ApplicationEvent {

    private final Long groupId;
    private final Integer usedToken;

    public GroupTokenConsumeEvent(Object source, Long groupId, Integer usedToken) {
        super(source); // source 通常传触发事件的 this 对象
        this.groupId = groupId;
        this.usedToken = usedToken;
    }
}