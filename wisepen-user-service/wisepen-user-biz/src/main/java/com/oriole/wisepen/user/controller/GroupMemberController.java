package com.oriole.wisepen.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.MemberListQueryResp;
import com.oriole.wisepen.user.api.domain.dto.PageResp;
import com.oriole.wisepen.user.service.GroupMemberService;
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

	@SaCheckLogin
	@PostMapping("/join")
	public R<Void> joinGroup(@RequestParam("inviteCode") @NotNull String inviteCode){
		Long userId= SecurityContextHolder.getUserId();
		groupMemberService.joinGroup(userId,inviteCode);
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/quit")
	public R<Void> quitGroup(@RequestParam("groupId") @NotNull Long groupId){
		Long userId= SecurityContextHolder.getUserId();
		groupMemberService.leaveGroup(userId,groupId);
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/kick")
	public R<Void> kickGroup(
			@RequestParam("groupId") @NotNull Long groupId,
			@RequestParam("targetUserId") @NotNull Long targetUserId
	){
		Long userId= SecurityContextHolder.getUserId();
		groupMemberService.kickGroupMember(userId,groupId,targetUserId);
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/update-role")
	public R<Void> updateRole(
			@RequestParam("groupId") @NotNull Long groupId,
			@RequestParam("targetUserId") @NotNull Long targetUserId,
			@RequestParam("role") @NotNull int role
	){
		groupMemberService.updateGroupMemberRole(groupId,targetUserId,role);
		return R.ok();
	}

	@SaCheckLogin
	@GetMapping("/info")
	public R<PageResp<MemberListQueryResp>> getGroupMember(
			@RequestParam("groupId") @NotNull Long groupId,
			@RequestParam("page") @NonNull @Min(1) Integer page,
			@RequestParam("size") @NonNull @Min(1) Integer size
	){
		return R.ok(groupMemberService.getMemberList(groupId,page,size));
	}
}
