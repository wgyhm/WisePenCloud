package com.oriole.wisepen.resource.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 标签可见性模式枚举
 */
@Getter
@AllArgsConstructor
public enum VisibilityModeEnum {

    ALL(0),
    ONLY_ADMIN(1),
    WHITELIST(2),
    BLACKLIST(3);

    @EnumValue
    @JsonValue
    private final int code;
}