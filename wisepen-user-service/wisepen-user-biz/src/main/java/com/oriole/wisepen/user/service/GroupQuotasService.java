package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.domain.dto.GetGroupMemberQuotasResponse;
import com.oriole.wisepen.user.api.domain.dto.GetMyGroupQuotasResponse;
import com.oriole.wisepen.user.api.domain.dto.PageResponse;

import java.util.List;

public interface GroupQuotasService {
	PageResponse<GetGroupMemberQuotasResponse> getGroupMemberQuotas(Long groupId, Integer page, Integer size);
	PageResponse<GetMyGroupQuotasResponse> getMyGroupQuotas();
	void UpdateMemberQuotas(Long groupId, List<Long> targetUserIds, Integer newLimit);
}
