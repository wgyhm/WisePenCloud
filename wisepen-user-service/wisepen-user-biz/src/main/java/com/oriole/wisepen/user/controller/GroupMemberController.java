package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.ChangeType;
import com.oriole.wisepen.common.core.domain.enums.ConsumerType;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberExchangeTokenRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberKickRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberRoleUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberQuitRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberRedeemVoucherRequest;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberTokenLimitUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberGetGroupTokenResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberGetTokenResponse;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberGetTransactionsResponse;
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
	) {
		SecurityContextHolder.assertInGroup(groupId);
		return R.ok(groupMemberService.getGroupMemberList(groupId, page, size));
	}

	@GetMapping("/getMyGroupMemberInfo")
	public R<GroupMemberDetailResponse> getMyGroupMemberInfo(@RequestParam("groupId") Long groupId) {
		return R.ok(groupMemberService.getGroupMemberInfoByUserId(groupId, SecurityContextHolder.getUserId()));
	}

	@GetMapping("/getMyRole")
	public R<GroupRoleType> getMyRole(@RequestParam("groupId") Long groupId) {
		return R.ok(SecurityContextHolder.getGroupRole(groupId));
	}

	@PostMapping("/changeTokenLimit")
	public R<Void> changeTokenLimit(@RequestBody @Valid GroupMemberTokenLimitUpdateRequest req) {
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
		groupMemberService.updateGroupMemberTokenLimit(req);
		return R.ok();
	}

	@GetMapping("/getGroupToken")
	public R<GroupMemberGetTokenResponse> getGroupToken(@RequestParam("groupId") Long groupId) {
		SecurityContextHolder.assertInGroup(groupId);
		return R.ok(groupMemberService.getGroupToken(SecurityContextHolder.getUserId(), groupId));
	}

	@GetMapping("/getAllGroupToken")
	public R<PageResult<GroupMemberGetGroupTokenResponse>> getAllGroupToken(
			@RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
			@RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size){
		return R.ok(groupMemberService.getAllGroupToken(SecurityContextHolder.getUserId(), page, size));
	}

	@GetMapping("/getWalletInfo")
	public R<GroupMemberGetTokenResponse> getWalletInfo(
			@RequestParam("targetType") Integer targetType,
			@RequestParam("targetId") Long targetId
	) {
		ConsumerType consumerType = parseConsumerType(targetType);
		assertTargetAccess(consumerType, targetId);
		return R.ok(groupMemberService.getWalletInfo(consumerType, targetId));
	}

	@PostMapping("/redeemVoucher")
	public R<Void> redeemVoucher(@RequestBody @Valid GroupMemberRedeemVoucherRequest req) {
		ConsumerType consumerType = parseConsumerType(req.getTargetType());
		assertTargetAccess(consumerType, req.getTargetId());
		groupMemberService.redeemVoucher(consumerType, req.getTargetId(), req.getCode());
		return R.ok();
	}

	@GetMapping("/getTransactions")
	public R<PageResult<GroupMemberGetTransactionsResponse>> getTransactions(
			@RequestParam("targetType") Integer targetType,
			@RequestParam("targetId") Long targetId,
			@RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
			@RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size,
			@RequestParam("type") Integer type
	) {
		ConsumerType consumerType = parseConsumerType(targetType);
		assertTargetAccess(consumerType, targetId);
		return R.ok(groupMemberService.getTransactions(consumerType, targetId, page, size, parseChangeType(type)));
	}

	@PostMapping("/giveTokenToOwner")
	public R<Void> giveTokenToOwner(@RequestBody @Valid GroupMemberExchangeTokenRequest req) {
		Long userId = SecurityContextHolder.getUserId();
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
		groupMemberService.exchangeTokenToOwner(userId, req.getGroupId(), req.getAmount());
		return R.ok();
	}

	@PostMapping("/giveTokenToGroup")
	public R<Void> giveTokenToGroup(@RequestBody @Valid GroupMemberExchangeTokenRequest req) {
		Long userId = SecurityContextHolder.getUserId();
		SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
		groupMemberService.exchangeTokenToGroup(userId, req.getGroupId(), req.getAmount());
		return R.ok();
	}


	private ConsumerType parseConsumerType(Integer type) {
		ConsumerType consumerType = ConsumerType.getByCode(type);
		if (consumerType == null) {
			throw new ServiceException(ResultCode.PARAM_ERROR);
		}
		return consumerType;
	}

	private void assertTargetAccess(ConsumerType targetType, Long targetId) {
		if (targetType == ConsumerType.USER) {
			SecurityContextHolder.assertUserId(targetId);
			return;
		}
		if (targetType == ConsumerType.GROUP) {
			SecurityContextHolder.assertGroupRole(targetId,GroupRoleType.OWNER);
			return;
		}
		throw new ServiceException(ResultCode.PARAM_ERROR);
	}

	private ChangeType parseChangeType(Integer type) {
		ChangeType changeType = ChangeType.getByCode(type);
		if (changeType == null) {
			throw new ServiceException(ResultCode.PARAM_ERROR);
		}
		return changeType;
	}

}
