package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.oriole.wisepen.user.api.enums.TokenTransactionType;

import com.oriole.wisepen.user.api.enums.TokenPayerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_token_record")
public class TokenTransactionRecordEntity implements Serializable {
	@TableId(type = IdType.ASSIGN_ID)
	Long id;
	String traceId;

	Long operatorId; // 操作方Id

	Long payerId; // 计费方Id
	TokenPayerType payerType; // 计费方类型

	Integer tokenCount; // token 量
	TokenTransactionType tokenTransactionType;// token 交易类型

	String meta; // 元信息

	@TableField(value = "create_time", fill = FieldFill.INSERT)
	LocalDateTime createTime; // 交易发起时间
}
