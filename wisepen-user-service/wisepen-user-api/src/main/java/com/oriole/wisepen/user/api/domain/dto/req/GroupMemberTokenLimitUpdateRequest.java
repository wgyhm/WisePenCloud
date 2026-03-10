package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import com.oriole.wisepen.user.api.domain.base.GroupIdentityBase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data

public class GroupMemberTokenLimitUpdateRequest extends GroupIdentityBase {
	@NotNull(message = GroupValidationMsg.TARGET_USER_IDS_NOT_NULL)
	private List<Long> targetUserIds;

	@NotNull(message = GroupValidationMsg.TOKEN_LIMIT_NOT_NULL)
	private Integer newTokenLimit;
}
