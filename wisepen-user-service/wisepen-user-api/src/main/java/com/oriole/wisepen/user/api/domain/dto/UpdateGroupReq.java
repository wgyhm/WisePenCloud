package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateGroupReq {

	@NotNull(message = "groupId 不能为空")
	private Long groupId;

	private String groupName;
	private String description;
	private String coverUrl;
}
