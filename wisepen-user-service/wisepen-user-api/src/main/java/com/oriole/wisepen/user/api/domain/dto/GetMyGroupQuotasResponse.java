package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GetMyGroupQuotasResponse implements Serializable {
	Long groupId;
	String groupName;
	Integer quotaLimit;
	Integer quotaUsed;
	Integer groupBalance;
}
