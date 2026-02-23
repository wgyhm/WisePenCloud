package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.*;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.service.GroupMemberService;
import com.oriole.wisepen.user.service.GroupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
@Validated
public class GroupController {

	private final GroupService groupService;
	private final GroupMemberService groupMemberService;

	@CheckLogin
	@PostMapping("/new")
	public R<?> createGroup(@RequestBody @Valid CreateGroupRequest req) {

		Group group = new Group();
		group.setName(req.getGroupName());
		group.setType(req.getGroupType());
		group.setDescription(req.getDescription());
		group.setCoverUrl(req.getCoverUrl());

		Long userId = Long.valueOf(SecurityContextHolder.getUserId());

		group.setOwnerId(userId);
		groupService.createGroup(group);

		groupMemberService.becomeGroupOwner(userId, group.getId());

		return R.ok();
	}

	@CheckLogin
	@PostMapping("/edit")
	public R<?> updateGroup(@RequestBody @Valid UpdateGroupRequest req) {
		Group group = new Group();
		group.setId(req.getGroupId());
		group.setName(req.getGroupName());
		group.setDescription(req.getDescription());
		group.setCoverUrl(req.getCoverUrl());
		groupService.updateGroup(group);
		return R.ok();
	}

	@CheckLogin
	@PostMapping("/delete")
	public R<?> deleteGroup(@RequestBody @Valid DeleteGroupRequest req) {
		groupService.deleteGroup(req.getGroupId());
		return R.ok();
	}

	// GET 不动
	@CheckLogin
	@GetMapping("/info")
	public R<PageResponse<GroupQueryResponse>> getInfo(
			@RequestParam("relationType") @NonNull Integer relationType,
			@RequestParam("page") @NonNull @Min(1) Integer page,
			@RequestParam("size") @NonNull @Min(1) Integer size
	) {
		Long userId = Long.valueOf(SecurityContextHolder.getUserId());
		return R.ok(groupService.getGroupIds(userId, relationType, page, size));
	}
}
