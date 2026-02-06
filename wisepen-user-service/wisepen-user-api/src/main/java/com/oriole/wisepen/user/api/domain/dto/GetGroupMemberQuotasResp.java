package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

@Data
public class GetGroupMemberQuotasResp {
	Long userId;
	String realName;
	Integer quotaLimit;
	Integer quotaUsed;
}
