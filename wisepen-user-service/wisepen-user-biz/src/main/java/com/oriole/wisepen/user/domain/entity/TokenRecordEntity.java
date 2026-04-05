package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.oriole.wisepen.common.core.domain.enums.ChangeType;
import com.oriole.wisepen.common.core.domain.enums.ConsumerType;
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
public class TokenRecordEntity implements Serializable {
	@TableId(type = IdType.ASSIGN_ID)
	Long id;

	Long traceId;


	ConsumerType ownerType;
	int tokenCount;
	ChangeType changeType;
	String meta;
	LocalDateTime createTime;
	String operatorName;
	Long targetId;
}
