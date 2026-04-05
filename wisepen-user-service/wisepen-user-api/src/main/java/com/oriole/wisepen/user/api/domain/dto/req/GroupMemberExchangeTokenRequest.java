package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupMemberExchangeTokenRequest {
	@NotNull(message = GroupValidationMsg.GROUP_ID_NOT_NULL)
	private Long groupId;

	@NotNull(message = "划转数量不能为空")
	@Min(value = 1, message = "划转数量必须大于等于1")
	private Integer amount;
}
