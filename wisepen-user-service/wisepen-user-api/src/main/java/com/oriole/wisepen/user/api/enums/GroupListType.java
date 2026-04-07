package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum GroupListType {
    JOIN(1,"JOIN"),
    MANAGE(2,"MANAGE");
    @EnumValue
    @JsonValue
    private final int code;

    private final String desc;
    public static GroupListType getByCode(Integer code) {
        if (code == null) {return null;}
        return Arrays.stream(values())
                .filter(t -> t.getCode() == code)
                .findFirst()
                .orElse(null);
    }
}
