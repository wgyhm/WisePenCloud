package com.oriole.wisepen.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.WalletTransferTokenRequest;
import com.oriole.wisepen.user.api.enums.*;
import com.oriole.wisepen.common.core.domain.enums.GroupType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.req.GroupMemberTokenLimitUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.GroupMemberTokenDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.WalletDetailResponse;
import com.oriole.wisepen.user.api.domain.dto.res.WalletTransactionRecordResponse;
import com.oriole.wisepen.user.api.domain.mq.TokenConsumptionMessage;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.domain.entity.*;
import com.oriole.wisepen.user.event.GroupTokenConsumeEvent;
import com.oriole.wisepen.user.exception.GroupErrorCode;
import com.oriole.wisepen.user.exception.UserErrorCode;
import com.oriole.wisepen.user.mapper.*;
import com.oriole.wisepen.user.service.IWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements IWalletService {

    private final ApplicationEventPublisher eventPublisher;

    private final UserServiceImpl userService;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserWalletsMapper userWalletsMapper;
    private final TokenTransactionRecordMapper tokenTransactionRecordMapper;
    private final TokenVoucherMapper tokenVoucherMapper;

    private final RedisCacheManager redisCacheManager;

    @Override
    // 消费Token
    public void consumptionToken(TokenConsumptionMessage message) {
        LambdaQueryWrapper<TokenTransactionRecordEntity> wrapper = new LambdaQueryWrapper<TokenTransactionRecordEntity>().eq(
                TokenTransactionRecordEntity::getTraceId, message.getTraceId());
        // 避免重复处理业务
        if (tokenTransactionRecordMapper.selectOne(wrapper) != null) return;

        Long userId = message.getUserId();
        Long groupId = message.getGroupId();

        Integer tokenBill = message.getUsageTokens() * message.getModelType().getRatio();
        if (groupId != null) {
            tokenBill = this.updateGroupMemberTokenUsed(groupId, userId, message.getTraceId(), tokenBill,
                    message.getModelType().getDesc()); // 从组成员侧扣除
        }
        if (tokenBill > 0) { // 即个人账单，或组内未能支付全部账单
            this.updateUserTokenUsed(userId, message.getTraceId(), tokenBill,
                    message.getModelType().getDesc()); // 从个人侧扣除
        }
    }

    @Override
    // 改变小组 Token 余额
    public void changeGroupTokenBalance(Long groupId, Long operator, Integer changedToken, TokenTransactionType type, String Meta) {
        GroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);
        }

        if (GroupType.NORMAL_GROUP.equals(group.getGroupType())) {
            throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
        }

        LambdaUpdateWrapper<GroupEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GroupEntity::getGroupId, groupId)
                .setSql("token_balance = token_balance + " + changedToken);

        // 记录日志
        TokenTransactionRecordEntity record = TokenTransactionRecordEntity.builder()
                .traceId(IdUtil.randomUUID())
                .payerId(groupId).payerType(TokenPayerType.GROUP)
                .tokenCount(changedToken)
                .tokenTransactionType(type).operatorId(operator).meta(Meta).build();
        tokenTransactionRecordMapper.insert(record);

        groupMapper.update(null, wrapper);
        if (group.getTokenBalance() + changedToken > 0) {
            redisCacheManager.unblockGroupChat(groupId);
        } else {
            redisCacheManager.blockGroupChat(groupId);
        }
    }

    @Override
    // 改变个人 Token 余额
    public void changeUserTokenBalance(Long userId, Long operator, Integer changedToken, TokenTransactionType type, String Meta) {
        UserWalletEntity walletEntity = userWalletsMapper.selectById(userId);
        if (walletEntity == null) {
            throw new ServiceException(UserErrorCode.USER_NOT_EXIST);
        }

        LambdaUpdateWrapper<UserWalletEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserWalletEntity::getUserId, userId)
                .setSql("token_balance = token_balance + " + changedToken);

        // 记录日志
        TokenTransactionRecordEntity record = TokenTransactionRecordEntity.builder()
                .traceId(IdUtil.randomUUID())
                .payerId(userId).payerType(TokenPayerType.USER)
                .tokenCount(changedToken)
                .tokenTransactionType(type).operatorId(operator).meta(Meta).build();
        tokenTransactionRecordMapper.insert(record);

        userWalletsMapper.update(null, wrapper);
        if (walletEntity.getTokenBalance() + changedToken > 0) {
            redisCacheManager.unblockUserChat(userId);
        } else {
            redisCacheManager.blockUserChat(userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // 更新组成员 Token 配额
    public void updateGroupMemberTokenLimit(GroupMemberTokenLimitUpdateRequest req) {
        GroupEntity group = groupMapper.selectById(req.getGroupId());
        if (group == null) throw new ServiceException(GroupErrorCode.GROUP_NOT_EXIST);

        if (GroupType.NORMAL_GROUP.equals(group.getGroupType())) {
            throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
        }

        // 批量更新额度，并防降额击穿
        LambdaUpdateWrapper<GroupMemberEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
                .in(GroupMemberEntity::getUserId, req.getTargetUserIds())
                // 新给定的上限，绝对不能小于该用户已经用掉的额度
                .le(GroupMemberEntity::getTokenUsed, req.getNewTokenLimit())
                .set(GroupMemberEntity::getTokenLimit, req.getNewTokenLimit());

        int rows = groupMemberMapper.update(null, wrapper);

        if (rows == 0) {
            throw new ServiceException(GroupErrorCode.LIMIT_CANNOT_BE_LOWER_THAN_USED);
        }

        LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMemberEntity::getGroupId, req.getGroupId())
                .in(GroupMemberEntity::getUserId, req.getTargetUserIds())
                // 必须严格小于 (<)！如果等于，说明额度依然是满的，不能解封
                .lt(GroupMemberEntity::getTokenUsed, req.getNewTokenLimit());

        List<GroupMemberEntity> validMembersToUnblock = groupMemberMapper.selectList(queryWrapper);

        validMembersToUnblock.forEach(member ->
                redisCacheManager.unblockGroupMemberChat(req.getGroupId(), member.getUserId())
        );
    }

    @Override
    // 更新组 Token 用量
    public void updateGroupTokenUsed(Long groupId, Integer usedToken) {
        LambdaUpdateWrapper<GroupEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(GroupEntity::getGroupId, groupId)
                .setSql("token_used = token_used + " + usedToken)
                .setSql("token_balance = token_balance - " + usedToken);

        groupMapper.update(null, wrapper);

        GroupEntity group = groupMapper.selectById(groupId);
        if (group != null && group.getTokenBalance() <= 0) {
            redisCacheManager.blockGroupChat(groupId);
            log.warn("群组 {} 余额已欠费透支，当前余额: {}，已触发熔断", groupId, group.getTokenBalance());
        }
    }

    @EventListener
    public void handleGroupTokenConsumeEvent(GroupTokenConsumeEvent event) {
        this.updateGroupTokenUsed(event.getGroupId(), event.getUsedToken());
    }


    @Override
    // 更新组成员 Token 用量
    public Integer updateGroupMemberTokenUsed(Long groupId, Long userId, Long traceId, Integer tokenBill, String BillMeta) {
        // 查询组成员最新额度消耗情况
        LambdaQueryWrapper<GroupMemberEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMemberEntity::getGroupId, groupId)
                .eq(GroupMemberEntity::getUserId, userId);
        GroupMemberEntity member = groupMemberMapper.selectOne(queryWrapper);

        Integer groupTokenBalance = member.getTokenLimit()  - member.getTokenUsed();
        int overageTokenBill = 0;
        // 如果当前余额不足以支付订单
        if (groupTokenBalance < tokenBill){
            overageTokenBill  = tokenBill - groupTokenBalance; // 超支部分，转个人支付
            tokenBill = groupTokenBalance; // 扣除所有余量
            // 触发组成员熔断
            redisCacheManager.blockGroupMemberChat(groupId, userId);
            log.warn("用户 {} 在群组 {} 的个人配额已触发熔断", userId, groupId);
        }

        // 联动扣除群组大盘资金池
        eventPublisher.publishEvent(new GroupTokenConsumeEvent(this, groupId, tokenBill));

        // 累计组成员的组内用量
        UpdateWrapper<GroupMemberEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("group_id", groupId)
                .eq("user_id", userId)
                .setSql("token_used = token_used + " + tokenBill);
        groupMemberMapper.update(null, wrapper);

        // 记录交易
        TokenTransactionRecordEntity record = TokenTransactionRecordEntity.builder()
                .traceId(traceId.toString())
                .payerId(groupId).payerType(TokenPayerType.GROUP)
                .tokenCount(tokenBill).tokenTransactionType(TokenTransactionType.SPEND)
                .operatorId(userId)
                .meta(BillMeta).build();
        tokenTransactionRecordMapper.insert(record);
        return overageTokenBill;
    }

    @Override
    // 更新个人 Token 用量
    public void updateUserTokenUsed(Long userId, Long traceId, Integer tokenBill, String BillMeta) {
        // 个人允许小额透支，因此不事先检查余量
        UpdateWrapper<UserWalletEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("user_id", userId)
                .setSql("token_used = token_used + " + tokenBill)
                .setSql("token_balance = token_balance - " + tokenBill);

        userWalletsMapper.update(null, wrapper);

        UserWalletEntity walletEntity = userWalletsMapper.selectById(userId);
        if (walletEntity != null && walletEntity.getTokenBalance() <= 0) {
            redisCacheManager.blockUserChat(userId);
            log.warn("用户 {} 余额已欠费透支，当前余额: {}，已触发熔断", userId, walletEntity.getTokenBalance());
        }

        // 记录交易
        TokenTransactionRecordEntity record = TokenTransactionRecordEntity.builder()
                .traceId(traceId.toString())
                .payerId(userId).payerType(TokenPayerType.USER)
                .tokenCount(tokenBill).tokenTransactionType(TokenTransactionType.SPEND)
                .operatorId(userId)
                .meta(BillMeta).build();
        tokenTransactionRecordMapper.insert(record);
    }

    @Override
    public void transferTokenBetweenGroupAndUser(Long userId, WalletTransferTokenRequest req){
        this.transferTokenBetweenGroupAndUser(userId, req.getGroupId(), req.getTokenCount(), req.getTokenTransferType());
    }

    @Override
    public void transferTokenBetweenGroupAndUser(Long userId, Long groupId, Integer tokenCount, TokenTransferType tokenTransferType) {
        GroupEntity groupEntity = groupMapper.selectById(groupId);
        UserWalletEntity userWalletEntity = userWalletsMapper.selectById(userId);

        if (GroupType.ADVANCED_GROUP != groupEntity.getGroupType()) {
            throw new ServiceException(GroupErrorCode.GROUP_HAS_NO_QUOTA);
        }

        switch (tokenTransferType) {
            case GROUP_INFLOW:
                if (userWalletEntity.getTokenBalance() < tokenCount) throw new ServiceException(GroupErrorCode.LIMIT_CANNOT_BE_LOWER_THAN_USED);
                this.changeUserTokenBalance(userId, userId, -tokenCount, TokenTransactionType.TRANSFER_OUT, null);
                this.changeGroupTokenBalance(groupId, userId, tokenCount, TokenTransactionType.TRANSFER_IN, null);
                break;
            case USER_INFLOW:
                if (groupEntity.getTokenBalance() < tokenCount) throw new ServiceException(GroupErrorCode.LIMIT_CANNOT_BE_LOWER_THAN_USED);
                this.changeUserTokenBalance(userId, userId, tokenCount, TokenTransactionType.TRANSFER_IN, null);
                this.changeGroupTokenBalance(groupId, userId, -tokenCount, TokenTransactionType.TRANSFER_OUT, null);
                break;
        }
    }

    public void redeemVoucher(Long userId, String voucherCode) {
        LambdaQueryWrapper<TokenVoucherEntity> wrapper =
                new LambdaQueryWrapper<TokenVoucherEntity>().eq(TokenVoucherEntity::getCode, voucherCode);
        TokenVoucherEntity voucher = tokenVoucherMapper.selectOne(wrapper);

        // 兑换券不存在
        if (voucher==null) throw new ServiceException(GroupErrorCode.VOUCHER_NOT_EXIST);
        // 兑换券已被使用
        if (voucher.getStatus() == VoucherStatus.USED) throw new ServiceException(GroupErrorCode.VOUCHER_IS_USED);
        // 兑换券已过期
        if (voucher.getExpireTime() != null && !new Date().before(voucher.getExpireTime())) throw new ServiceException(GroupErrorCode.VOUCHER_IS_EXPIRED);

        String voucherCodeMasked = "****-****-****-" + voucherCode.substring(voucherCode.length() - 4);
        // 先消费 Voucher
        int row = tokenVoucherMapper.update(
                null,
                new LambdaUpdateWrapper<TokenVoucherEntity>()
                        .eq(TokenVoucherEntity::getVoucherId, voucher.getVoucherId())
                        .eq(TokenVoucherEntity::getStatus, VoucherStatus.UNUSED)
                        .set(TokenVoucherEntity::getStatus, VoucherStatus.USED)
        );
        if (row == 0) {
            // 未能成功消费，可能已被使用
            throw new ServiceException(GroupErrorCode.VOUCHER_IS_USED);
        }
        // 执行充值
        this.changeUserTokenBalance(userId, userId, voucher.getAmount(), TokenTransactionType.REFILL, voucherCodeMasked);
    }

    @Override
    public PageResult<WalletTransactionRecordResponse> listTransactions(
            TokenPayerType payerType,
            Long payerId,
            TokenTransactionType tokenTransactionType,
            Integer page, Integer size
    ) {
        Page<TokenTransactionRecordEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TokenTransactionRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TokenTransactionRecordEntity::getPayerId, payerId)
                .eq(TokenTransactionRecordEntity::getPayerType, payerType)
                .eq(tokenTransactionType != null, TokenTransactionRecordEntity::getTokenTransactionType, tokenTransactionType)
                .orderByDesc(TokenTransactionRecordEntity::getCreateTime);

        IPage<TokenTransactionRecordEntity> transactionPage = tokenTransactionRecordMapper.selectPage(pageParam, wrapper);

        PageResult<WalletTransactionRecordResponse> pageResult = new PageResult<>(transactionPage.getTotal(), page, size);
        if (CollectionUtils.isEmpty(transactionPage.getRecords())) {
            return pageResult;
        }

        // 提取所有不为空的 operatorId，去重收集到 Set 中
        Set<Long> operatorIds = transactionPage.getRecords().stream()
                .map(TokenTransactionRecordEntity::getOperatorId)
                .filter(Objects::nonNull) // 防御性编程：过滤掉为 null 的 ID
                .collect(Collectors.toSet());

        // 批量查询用户信息
        Map<Long, UserDisplayBase> operatorInfoMap = operatorIds.isEmpty() ?
                Collections.emptyMap() :
                userService.getUserDisplayInfoByIds(operatorIds);

        // 遍历组装返回值
        List<WalletTransactionRecordResponse> records = transactionPage.getRecords().stream()
                .map(record -> {
                    WalletTransactionRecordResponse response = BeanUtil.copyProperties(record, WalletTransactionRecordResponse.class);
                    if (record.getOperatorId() != null) {
                        response.setOperatorDisplay(operatorInfoMap.get(record.getOperatorId()));
                    }
                    return response;
                })
                .collect(Collectors.toList());
        pageResult.addAll(records);
        return pageResult;
    }


    @Override
    public WalletDetailResponse getUserWalletInfo(Long userId) {
        UserWalletEntity userWallet = userWalletsMapper.selectById(userId);
        return BeanUtil.copyProperties(userWallet, WalletDetailResponse.class);
    }

    @Override
    public PageResult<GroupMemberTokenDetailResponse> getAllGroupTokenInfoByUserId(Long userId, Integer page, Integer size) {
        Page<GroupMemberEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<GroupMemberEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMemberEntity::getUserId, userId)
                .orderByAsc(GroupMemberEntity::getRole)
                .orderByDesc(GroupMemberEntity::getJoinTime);
        IPage<GroupMemberEntity> memberPage = groupMemberMapper.selectPage(pageParam, wrapper);

        List<Long> groupIds = memberPage.getRecords().stream()
                .map(GroupMemberEntity::getGroupId)
                .collect(Collectors.toList());
        PageResult<GroupMemberTokenDetailResponse> pageResult = new PageResult<>(memberPage.getTotal(), page, size);
        if (groupIds.isEmpty()) {
            return pageResult;
        }

        Map<Long, GroupEntity> groupEntityMap = groupMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(GroupEntity::getGroupId, group -> group));

        List<GroupMemberTokenDetailResponse> records = memberPage.getRecords().stream().map(memberEntity -> {
            GroupMemberTokenDetailResponse resp = BeanUtil.copyProperties(memberEntity, GroupMemberTokenDetailResponse.class);
            resp.setGroupDisplayBase(groupEntityMap.get(memberEntity.getGroupId()));
            return resp;
        }).collect(Collectors.toList());

        pageResult.addAll(records);
        return pageResult;
    }
}