package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupCreateRequest implements Serializable {

	@NotBlank(message = GroupValidationMsg.GROUP_NAME_NOT_BLANK)
	private String groupName; // 群组名称

	@NotNull(message = GroupValidationMsg.GROUP_TYPE_NOT_NULL)
	private GroupType groupType; // 群组类型

	@NotBlank(message = GroupValidationMsg.GROUP_DESCRIPTION_NOT_BLANK)
	private String groupDesc; // 群组描述

	private String groupCoverUrl; // 组封面URL(可选)
}
