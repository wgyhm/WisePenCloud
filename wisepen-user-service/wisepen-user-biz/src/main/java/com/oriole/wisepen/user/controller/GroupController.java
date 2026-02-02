package com.oriole.wisepen.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.GroupQueryResp;
import com.oriole.wisepen.user.api.domain.dto.PageResp;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.service.GroupMemberService;
import com.oriole.wisepen.user.service.GroupService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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

	@SaCheckLogin
	@PostMapping("/new")
	public R<?> createGroup(
			@RequestParam("groupName") @NonNull String groupName,
			@RequestParam("groupType") @NotNull Integer groupType,
			@RequestParam("description") @NonNull String description,
			@RequestParam(value="coverUrl",required = false) String coverUrl
	) {
		Group group = new Group();
		group.setName(groupName);
		group.setType(groupType);
		group.setDescription(description);
		group.setCoverUrl(coverUrl);
		group.setOwnerId(SecurityContextHolder.getUserId());
		groupService.createGroup(group);
		Long userId = SecurityContextHolder.getUserId();
		groupMemberService.becomeGroupOwner(userId,group.getId());
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/edit")
	public R<?> updateGroup(
			@RequestParam("groupId") @NotNull Long groupId,
			@RequestParam(value="groupName",required = false) String groupName,
			@RequestParam(value="description",required = false) String description,
			@RequestParam(value="coverUrl",required = false) String coverUrl
	){
		Group group = new Group();
		group.setId(groupId);
		group.setName(groupName);
		group.setDescription(description);
		group.setCoverUrl(coverUrl);
		groupService.updateGroup(group);
		return R.ok();
	}

	@SaCheckLogin
	@PostMapping("/delete")
	public R<?> deleteGroup(@RequestParam("groupId") @NonNull Long groupId) {
		groupService.deleteGroup(groupId);
		return R.ok();
	}

	@SaCheckLogin
	@GetMapping("/info")
	public R<PageResp<GroupQueryResp>> getInfo(
			@RequestParam("relationType") @NonNull Integer relationType,
			@RequestParam("page") @NonNull @Min(1) Integer page,
			@RequestParam("size") @NonNull @Min(1) Integer size
	) {
		Long userId = SecurityContextHolder.getUserId();
		return R.ok(groupService.getGroupIds(userId,relationType,page,size));
	}
}


