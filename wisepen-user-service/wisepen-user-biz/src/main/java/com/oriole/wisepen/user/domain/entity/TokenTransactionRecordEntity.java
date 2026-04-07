package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.oriole.wisepen.user.api.domain.base.TokenTransactionRecordBase;

import com.oriole.wisepen.user.api.enums.TokenPayerType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_token_record")
public class TokenTransactionRecordEntity extends TokenTransactionRecordBase implements Serializable {
	@TableId(type = IdType.ASSIGN_ID)
	Long id;
	Long payerId; // 计费方Id
	TokenPayerType payerType; // 计费方类型
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	LocalDateTime createTime; // 交易发起时间
}