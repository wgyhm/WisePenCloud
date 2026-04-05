package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenPayerType {
	USER(1,"USER"),
	GROUP(2,"GROUP");

	@EnumValue
	@JsonValue
	private final int code;

	private final String desc;
}
