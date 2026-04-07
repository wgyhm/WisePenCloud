package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.TokenTransactionType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WalletTransactionRecordResponse {
	String traceId;
	LocalDateTime createTime;
	TokenTransactionType tokenTransactionType;
	Long tokenCount;
	String meta;
	String operatorName;
}
