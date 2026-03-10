package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberKickRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberRoleUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberQuitRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberDetailResponse;
import com.oriole.wisepen.user.service.GroupMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group/member")
@RequiredArgsConstructor
@Validated
@CheckLogin
public class GroupMemberController {

	private final GroupMemberService groupMemberService;

	@PostMapping("/quit")
	public R<Void> quitGroup(@RequestBody @Valid GroupMemberQuitRequest req) {
		SecurityContextHolder.assertInGroup(req.getGroupId()); // 用户退群必须先在群中
		groupMemberService.quitGroup(req, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRole(req.getGroupId()));
		return R.ok();
	}

	@PostMapping("/kick")
	public R<Void> kickGroupMembers(@RequestBody @Valid GroupMemberKickRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER, GroupRoleType.ADMIN);
		groupMemberService.kickGroupMembers(req, SecurityContextHolder.getUserId(), SecurityContextHolder.getGroupRole(req.getGroupId()));
		return R.ok();
	}

	@PostMapping("/changeRole")
	public R<Void> changeRole(@RequestBody @Valid GroupMemberRoleUpdateRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER); //必须是所有者才能修改成员权限
		groupMemberService.updateGroupMemberRole(req, SecurityContextHolder.getUserId());
		return R.ok();
	}

	@GetMapping("/list")
	public R<PageResult<GroupMemberDetailResponse>> listGroupMembers(
			@RequestParam("groupId") Long groupId,
			@RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
			@RequestParam(value = "size", defaultValue = "20") @Min(1) int size
	){
		SecurityContextHolder.assertInGroup(groupId);
		return R.ok(groupMemberService.getGroupMemberList(groupId, page, size));
	}

	@GetMapping("/getMyGroupMemberInfo")
	public R<GroupMemberDetailResponse> getMyGroupMemberInfo(@RequestParam("groupId") Long groupId){
		return R.ok(groupMemberService.getGroupMemberInfoByUserId(groupId, SecurityContextHolder.getUserId()));
	}

	@GetMapping("/getMyRole")
	public R<GroupRoleType> getMyRole(@RequestParam("groupId") Long groupId){
		return R.ok(SecurityContextHolder.getGroupRole(groupId));
	}
}
