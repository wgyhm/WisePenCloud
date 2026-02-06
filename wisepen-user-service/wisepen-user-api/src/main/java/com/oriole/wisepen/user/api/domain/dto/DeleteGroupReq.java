package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteGroupReq {

	@NotNull(message = "groupId 不能为空")
	private Long groupId;
}
