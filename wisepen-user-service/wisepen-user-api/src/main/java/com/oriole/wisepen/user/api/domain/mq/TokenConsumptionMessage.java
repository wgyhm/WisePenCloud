package com.oriole.wisepen.user.api.domain.mq;

import com.oriole.wisepen.user.api.enums.ModelType;
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
public class TokenConsumptionMessage implements Serializable{
	private Long userId;
	private Long groupId;
	private Integer usageTokens;
	private Long traceId;
	private ModelType modelType;
	private LocalDateTime requestTime;
}