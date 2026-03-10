package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupMemberJoinRequest implements Serializable {

	@NotBlank(message = GroupValidationMsg.INVITE_CODE_NOT_BLANK)
	private String inviteCode;
}