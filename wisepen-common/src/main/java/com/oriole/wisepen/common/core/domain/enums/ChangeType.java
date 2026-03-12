package com.oriole.wisepen.common.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ChangeType {
	REFILL(1,"REFILL"),
	SPEND(2,"SPEND");

	@EnumValue
	@JsonValue
	private final int code;

	private final String desc;

	public static ChangeType getByCode(Integer code) {
		if (code == null) {return null;}
		return Arrays.stream(values())
				.filter(t -> t.getCode() == code)
				.findFirst()
				.orElse(null);
	}
}
