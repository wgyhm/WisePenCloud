package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ModelType {
	STANDARD_MODEL(1,"STANDARD_MODEL",1),
	ADVANCED_MODEL(2,"ADVANCED_MODEL",10),
	UNKNOWN_MODEL(3,"UNKNOWN_MODEL",1);

	@EnumValue
	@JsonValue
	private final int code;
	private final String desc;
	private final int ratio;
}
