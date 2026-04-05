package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupMemberRedeemVoucherRequest implements Serializable {

	@NotNull(message = "主体类型不能为空")
	private Integer targetType;

	@NotNull(message = GroupValidationMsg.TARGET_USER_ID_NOT_NULL)
	private Long targetId;

	@NotBlank(message = "兑换码不能为空")
	private String code;
}
