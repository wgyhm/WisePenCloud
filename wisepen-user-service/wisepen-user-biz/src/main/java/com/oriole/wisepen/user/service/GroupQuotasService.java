package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.user.api.domain.dto.GetGroupMemberQuotasResponse;
import com.oriole.wisepen.user.api.domain.dto.GetGroupQuotasInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.GetMyGroupQuotasResponse;

import java.util.List;

public interface GroupQuotasService {
	GetGroupQuotasInfoResponse getGroupQuotas(Long groupId);
	PageResult<GetGroupMemberQuotasResponse> getGroupMemberQuotas(Long groupId, Integer page, Integer size);
	PageResult<GetMyGroupQuotasResponse> getMyGroupQuotas(Integer page,Integer size);
	void updateMemberQuotas(Long groupId, Long targetUserId, Integer newLimit);
	void updateMembersQuotas(Long groupId, List<Long> targetUserIds, Integer newLimit);
}
