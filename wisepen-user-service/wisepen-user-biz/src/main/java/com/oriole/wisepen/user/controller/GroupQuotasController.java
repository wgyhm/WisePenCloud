package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.GetGroupQuotasInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.GetMyGroupQuotasResponse;
import com.oriole.wisepen.user.api.domain.dto.SetGroupQuotasRequest;
import com.oriole.wisepen.user.service.GroupQuotasService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/group/quotas")
@RequiredArgsConstructor
@Validated
public class GroupQuotasController {
	private final GroupQuotasService groupQuotasService;

	@CheckLogin
	@PostMapping("/set")
	public R<Void> setGroupQuotas(@Valid @RequestBody SetGroupQuotasRequest setGroupQuotasRequest) {
		groupQuotasService.updateMembersQuotas(
				setGroupQuotasRequest.getGroupId(),
				setGroupQuotasRequest.getTargetUserIds(),
				setGroupQuotasRequest.getNewLimit());
		return R.ok();
	}

	@CheckLogin
	@GetMapping("/quota-by-user")
	public R<PageResult<GetMyGroupQuotasResponse>> getMyGroupQuotas(
			@RequestParam("page") @NotNull @Min(1) Integer page,
			@RequestParam("size") @NotNull @Min(1) Integer size) {
		return R.ok(groupQuotasService.getMyGroupQuotas(page, size));
	}

	@CheckLogin
	@GetMapping("/group-info")
	public R<GetGroupQuotasInfoResponse> getGroupInfo(@RequestParam("groupId") @NotNull Long groupId) {
		return R.ok(groupQuotasService.getGroupQuotas(groupId));
	}
}
