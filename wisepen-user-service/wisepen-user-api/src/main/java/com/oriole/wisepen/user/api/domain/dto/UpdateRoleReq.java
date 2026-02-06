package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleReq {

	@NotNull(message = "groupId 不能为空")
	private Long groupId;

	@NotNull(message = "targetUserId 不能为空")
	private Long targetUserId;

	@NotNull(message = "role 不能为空")
	// 推荐 Integer，便于校验；你 service 要 int 的话会自动拆箱
	private Integer role;
}
