package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.common.core.domain.enums.ChangeType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupMemberGetTransactionsResponse {
	Long traceId;
	LocalDateTime createTime;
	ChangeType changeType;
	Long amount;
	String meta;
	String operatorName;
}
