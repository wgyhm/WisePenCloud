package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.user.api.domain.dto.req.WalletTransferTokenRequest;
import com.oriole.wisepen.user.api.domain.dto.res.WalletDetailResponse;
import com.oriole.wisepen.user.api.enums.TokenPayerType;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberTokenLimitUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberTokenDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.WalletTransactionRecordResponse;
import com.oriole.wisepen.user.api.domain.mq.TokenConsumptionMessage;
import com.oriole.wisepen.user.api.enums.TokenTransactionType;
import com.oriole.wisepen.user.api.enums.TokenTransferType;

public interface IWalletService {

    // 消耗 Token
    void consumptionToken(TokenConsumptionMessage message);

    // 改变小组 Token 余额
    void changeGroupTokenBalance(Long groupId, Long operator, Integer changedToken, TokenTransactionType type, String Meta);
    // 改变个人 Token 余额
    void changeUserTokenBalance(Long groupId, Long operator, Integer changedToken, TokenTransactionType type, String Meta);
    // 更新组成员 Token 配额
    void updateGroupMemberTokenLimit(GroupMemberTokenLimitUpdateRequest req);

    // 更新组 Token 用量
    void updateGroupTokenUsed(Long groupId, Integer usedToken);
    // 更新组成员 Token 用量
    Integer updateGroupMemberTokenUsed(Long groupId, Long userId, Long traceId, Integer tokenBill, String BillMeta);
    // 更新个人 Token 用量
    void updateUserTokenUsed(Long userId, Long traceId, Integer tokenBill, String BillMeta);

    // 转移 Token（仅限小组所有者）
    void transferTokenBetweenGroupAndUser(Long userId, WalletTransferTokenRequest req);
    void transferTokenBetweenGroupAndUser(Long userId, Long groupId, Integer tokenCount, TokenTransferType tokenTransferType);
    // 核销点卡
    void redeemVoucher(Long userId, String voucherCode);

    //获取交易流水
    PageResult<WalletTransactionRecordResponse> listTransactions(
            TokenPayerType payerType,
            Long payerId,
            TokenTransactionType tokenTransactionType,
            Integer page, Integer size
    );

    // 获取个人钱包详情
    WalletDetailResponse getUserWalletInfo(Long userId);
    // 获取小组钱包详情
    WalletDetailResponse getGroupWalletInfo(Long groupId);

    // 批量获得用户所有小组的 Token 信息
    PageResult<GroupMemberTokenDetailResponse> getAllGroupTokenInfoByUserId(Long userId, Integer page, Integer size);
}
