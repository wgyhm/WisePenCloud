package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VoucherStatus {
	UNUSED(1,"UNUSED"),
	USED(2,"USED"),
	DISABLED(3,"DISABLED");

	@EnumValue
	@JsonValue
	private final int code;

	private final String desc;
}
