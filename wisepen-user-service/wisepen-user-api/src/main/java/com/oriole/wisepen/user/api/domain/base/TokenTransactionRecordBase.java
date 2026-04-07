package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.enums.TokenTransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TokenTransactionRecordBase {
    String traceId;
    Long operatorId; // 操作方Id
    Integer tokenCount; // token 量
    TokenTransactionType tokenTransactionType;// token 交易类型
    String meta; // 元信息
}

