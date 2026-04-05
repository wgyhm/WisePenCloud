package com.oriole.wisepen.user.api.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenTransactionType {
	REFILL(1, "REFILL"),
	SPEND(2, "SPEND"),
	TRANSFER_IN(3, "TRANSFER_IN"),
	TRANSFER_OUT(4, "TRANSFER_OUT");

	@EnumValue
	@JsonValue
	private final int code;

	private final String desc;
}
