package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.TokenTransactionRecordBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.enums.TokenTransactionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class WalletTransactionRecordResponse extends TokenTransactionRecordBase {
	UserDisplayBase operatorDisplay;
	LocalDateTime createTime;
}
