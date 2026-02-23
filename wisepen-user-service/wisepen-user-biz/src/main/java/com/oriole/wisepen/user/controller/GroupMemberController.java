package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.*;
import com.oriole.wisepen.user.service.GroupMemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group/member")
@RequiredArgsConstructor
@Validated
public class GroupMemberController {

	private final GroupMemberService groupMemberService;

	@CheckLogin
	@PostMapping("/join")
	public R<Void> joinGroup(@RequestBody @Valid JoinGroupRequest req) {
		Long userId = Long.valueOf(SecurityContextHolder.getUserId());
		groupMemberService.joinGroup(userId, req.getInviteCode());
		return R.ok();
	}

	@CheckLogin
	@PostMapping("/quit")
	public R<Void> quitGroup(@RequestBody @Valid QuitGroupRequest req) {
		Long userId = Long.valueOf(SecurityContextHolder.getUserId());
		groupMemberService.leaveGroup(userId, req.getGroupId());
		return R.ok();
	}

	@CheckLogin
	@PostMapping("/kick")
	public R<Void> kickGroup(@RequestBody @Valid KickGroupRequest req) {
		Long userId = Long.valueOf(SecurityContextHolder.getUserId());
		groupMemberService.kickGroupMembers(userId, req.getGroupId(), req.getTargetUserIds());
		return R.ok();
	}

	@CheckLogin
	@PostMapping("/update-role")
	public R<Void> updateRole(@RequestBody @Valid UpdateRoleRequest req) {
		groupMemberService.updateGroupMemberRole(req.getGroupId(), req.getTargetUserId(), req.getRole());
		return R.ok();
	}


	@CheckLogin
	@GetMapping("/info")
	public R<PageResponse<MemberListQueryResponse>> getGroupMember(
			@RequestParam("groupId") @NotNull Long groupId,
			@RequestParam("page") @NonNull @Min(1) Integer page,
			@RequestParam("size") @NonNull @Min(1) Integer size
	){
		return R.ok(groupMemberService.getMemberList(groupId,page,size));
	}
}
