package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinGroupReq {

	@NotBlank(message = "inviteCode 不能为空")
	private String inviteCode;
}