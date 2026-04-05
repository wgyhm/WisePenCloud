package com.oriole.wisepen.user.api.domain.dto.res;


import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class GroupMemberTokenDetailResponse {
	GroupDisplayBase groupDisplayBase;
	Integer tokenUsed;
	Integer tokenLimit;
}
