package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.enums.TokenTransactionType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WalletTransactionRecordResponse {
	Long traceId;
	LocalDateTime createTime;
	TokenTransactionType tokenTransactionType;
	Long amount;
	String meta;
	String operatorName;
}
