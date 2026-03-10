package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.common.security.exception.PermissionErrorCode;
import com.oriole.wisepen.common.security.exception.PermissionException;
import com.oriole.wisepen.user.api.domain.dto.req.GroupCreateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupDeleteRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberJoinRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupItemInfoResponse;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.service.GroupMemberService;
import com.oriole.wisepen.user.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
@Validated
@CheckLogin
public class GroupController {

	private final GroupService groupService;

	@PostMapping("/joinGroup")
	public R<Void> joinGroup(@RequestBody @Valid GroupMemberJoinRequest req) {
		groupService.joinGroup(req, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRoleMap().keySet());
		// SecurityContextHolder.getGroupRoleMap().keySet()防止用户重复加群
		return R.ok();
	}

	@PostMapping("/addGroup")
	public R<Void> createGroup(@RequestBody @Valid GroupCreateRequest req) {

		IdentityType userIdentityType= SecurityContextHolder.getIdentityType();
		if (req.getGroupType() == GroupType.ADVANCED_GROUP && userIdentityType == IdentityType.STUDENT) {
			throw new PermissionException(PermissionErrorCode.IDENTITY_UNAUTHORIZED);
		}

		if (req.getGroupType()==GroupType.MARKET_GROUP && userIdentityType != IdentityType.ADMIN) {
			throw new PermissionException(PermissionErrorCode.IDENTITY_UNAUTHORIZED);
		}

		groupService.createGroup(req, SecurityContextHolder.getUserId());
		return R.ok();
	}

	@PostMapping("/changeGroup")
	public R<Void> updateGroup(@RequestBody @Valid GroupUpdateRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER, GroupRoleType.ADMIN);
		groupService.updateGroup(req);
		return R.ok();
	}

	@PostMapping("/removeGroup")
	public R<Void> deleteGroup(@RequestBody @Valid GroupDeleteRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
		groupService.deleteGroup(req);
		return R.ok();
	}

	@GetMapping("/list")
	public R<PageResult<GroupItemInfoResponse>> listGroups(
			@RequestParam GroupRoleType groupRoleType,
			@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "20") int size
	) {
		return R.ok(groupService.listGroups(SecurityContextHolder.getUserId(), groupRoleType, page, size));
	}

	@GetMapping("/getGroupBaseInfo")
	public R<GroupItemInfoResponse> getGroupBaseInfo(@RequestParam("groupId") Long groupId) {
		return R.ok(groupService.getGroupBaseInfoById(groupId));
	}

	@GetMapping("/getGroupDetailInfo")
	public R<GroupDetailInfoResponse> getGroupDetailInfo(@RequestParam("groupId") Long groupId) {
		SecurityContextHolder.assertGroupRole(groupId, GroupRoleType.OWNER, GroupRoleType.ADMIN);
		return R.ok(groupService.getGroupDetailInfoById(groupId));
	}
}