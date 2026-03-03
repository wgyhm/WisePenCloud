package com.oriole.wisepen.user.api.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SetGroupQuotasRequest {
	@NotNull
	Long groupId;
	@NotEmpty
	List<Long> targetUserIds;
	@NotNull
	@Min(0)
	Integer newLimit;
}
