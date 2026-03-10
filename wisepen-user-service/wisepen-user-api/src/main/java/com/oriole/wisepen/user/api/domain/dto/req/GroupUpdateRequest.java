package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupUpdateRequest extends GroupDisplayBase {
	@NotNull(message = GroupValidationMsg.GROUP_ID_NOT_NULL)
	private Long groupId;
}
