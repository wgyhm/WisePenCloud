package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GetGroupQuotasInfoResponse implements Serializable {
	Integer quotaUsed;
	Integer quotaLimit;
}
