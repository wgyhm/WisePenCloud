package com.oriole.wisepen.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.user.domain.entity.GroupMember;
import com.oriole.wisepen.user.service.GroupMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group/member")
@RequiredArgsConstructor
public class GroupMemberController {

	private final GroupMemberService groupMemberService;

	@SaCheckLogin
	@PostMapping("/join")
	public void joinGroup(@RequestParam("inviteCode") String inviteCode){
		Long userId= SecurityContextHolder.getUserId();
		groupMemberService.joinGroup(userId,inviteCode);
	}
}
