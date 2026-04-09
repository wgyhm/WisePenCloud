package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.req.WalletRedeemVoucherRequest;
import com.oriole.wisepen.user.api.domain.dto.req.WalletTransferTokenRequest;
import com.oriole.wisepen.user.api.domain.dto.res.WalletDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.WalletTransactionRecordResponse;
import com.oriole.wisepen.user.api.enums.TokenPayerType;
import com.oriole.wisepen.user.api.enums.TokenTransactionType;
import com.oriole.wisepen.user.service.IWalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/wallet/")
@RequiredArgsConstructor
@Validated
@CheckLogin
public class WalletController {

    private final IWalletService walletService;

    @GetMapping("/getUserWalletInfo")
    public R<WalletDetailResponse> getUserWalletInfo() {
        Long userId = SecurityContextHolder.getUserId();
        return R.ok(walletService.getUserWalletInfo(userId));
    }

    @GetMapping("/getGroupWalletInfo")
    public R<WalletDetailResponse> getGroupWalletInfo(@RequestParam(value = "groupId") Long groupId) {
        SecurityContextHolder.assertGroupRole(groupId, GroupRoleType.OWNER);
        return R.ok(walletService.getGroupWalletInfo(groupId));
    }

    @PostMapping("/redeemVoucher")
    public R<Void> redeemVoucher(@RequestBody WalletRedeemVoucherRequest req) {
        Long userId = SecurityContextHolder.getUserId();
        walletService.redeemVoucher(userId, req.getVoucherCode());
        return R.ok();
    }

    @PostMapping("/transferTokenBetweenGroupAndUser")
    public R<Void> transferTokenBetweenGroupAndUser(@RequestBody @Valid WalletTransferTokenRequest req) {
        Long userId = SecurityContextHolder.getUserId();
        SecurityContextHolder.assertGroupRole(req.getGroupId(), GroupRoleType.OWNER);
        walletService.transferTokenBetweenGroupAndUser(userId, req);
        return R.ok();
    }

    @GetMapping("/listTransactions")
    public R<PageResult<WalletTransactionRecordResponse>> listTransactions(
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "type", required = false) TokenTransactionType tokenTransactionType,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) Integer page,
            @RequestParam(value = "size", defaultValue = "20") @Min(1) Integer size
            ) {
        TokenPayerType payerType;
        Long payerId;
        if (groupId == null) {
            payerType = TokenPayerType.USER;
            payerId = SecurityContextHolder.getUserId();
        } else {
            payerType = TokenPayerType.GROUP;
            payerId = groupId;
            SecurityContextHolder.assertGroupRole(groupId, GroupRoleType.OWNER);
        }
        return R.ok(walletService.listTransactions(payerType, payerId, tokenTransactionType, page, size));
    }
}
