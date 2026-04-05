package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenTransferType {
    GROUP_INFLOW(1, "GROUP_INFLOW"),
    USER_INFLOW(2, "USER_INFLOW");

    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
}
