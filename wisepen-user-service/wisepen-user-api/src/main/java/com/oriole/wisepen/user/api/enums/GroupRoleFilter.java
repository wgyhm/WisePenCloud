package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GroupRoleFilter {
    JOINED(1,"JOINED"),
    MANAGED(2,"MANAGED");
    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;
}
