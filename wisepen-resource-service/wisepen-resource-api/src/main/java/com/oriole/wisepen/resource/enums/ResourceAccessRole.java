package com.oriole.wisepen.resource.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResourceAccessRole {
    OWNER(4),
    OWNER_SPECIFIED(3),
    GROUP_ADMIN(2),
    GROUP_MEMBER(1),
    NONE(0);

    private final int level;
}