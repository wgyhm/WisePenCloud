package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

@Data
public class GetMyGroupQuotasResp {
	Long groupId;
	String groupName;
	Integer quotaLimit;
	Integer quotaUsed;
	Integer groupBalance;
}
