package com.oriole.wisepen.user.api.domain.dto.res;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupDetailInfoResponse extends GroupItemInfoResponse {
	private String inviteCode;
	private Integer tokenUsed;
	private Integer tokenBalance;
}