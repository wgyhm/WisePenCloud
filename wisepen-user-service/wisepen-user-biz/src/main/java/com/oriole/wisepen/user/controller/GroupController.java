package com.oriole.wisepen.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.dto.UserInfoDTO;
import com.oriole.wisepen.user.domain.dto.GroupQueryResp;
import com.oriole.wisepen.user.domain.dto.PageResp;
import com.oriole.wisepen.user.domain.entity.Group;
import com.oriole.wisepen.user.service.GroupMemberService;
import com.oriole.wisepen.user.service.GroupService;
import com.oriole.wisepen.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/group")
@RequiredArgsConstructor
public class GroupController {

	private final GroupService groupService;
	private final GroupMemberService groupMemberService;

	@SaCheckLogin
	@PostMapping("/new")
	public R<?> createGroup(
			@RequestParam("groupName") String groupName,
			@RequestParam("groupType") int groupType,
			@RequestParam("description") String description,
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
			@RequestParam("groupId") Long groupId,
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
	public R<?> deleteGroup(@RequestParam("groupId")  Long groupId) {
		groupService.deleteGroup(groupId);
		return R.ok();
	}

	@SaCheckLogin
	@GetMapping("/info")
	public R<PageResp<GroupQueryResp>> getInfo(
			@RequestParam("relationType") int relationType,
			@RequestParam("page") int page,
			@RequestParam("size") int size
	) {
		Long userId = SecurityContextHolder.getUserId();
		return R.ok(groupService.getGroupIdsByUserIdAndType(userId,relationType,page,size));
	}
}


