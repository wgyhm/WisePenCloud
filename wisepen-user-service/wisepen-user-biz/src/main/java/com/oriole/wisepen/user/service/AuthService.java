package com.oriole.wisepen.user.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.cache.RedisCacheManager;
import com.oriole.wisepen.user.exception.UserErrorCode;
import com.oriole.wisepen.user.api.domain.dto.req.AuthLoginRequest;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final GroupMemberService groupMemberService;
    private final RedisCacheManager redisCacheManager;

    public String login(AuthLoginRequest loginRequest) {
        String account = loginRequest.getAccount();

        // 查询用户信息 (包含密码密文)
        UserEntity user = userService.getUserCoreInfoByAccount(account);

        // 账号不存在
        if (user==null){
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }

        // 校验账号状态
        if (user.getStatus()== Status.BANNED) {
            throw new ServiceException(UserErrorCode.USER_LOCKED);
        }

        // 校验密码
        if (!BCrypt.checkpw(loginRequest.getPassword(), user.getPassword())) {
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }

        Map<String, Integer> groupRoleMap = groupMemberService.getGroupRoleMapByUserId(user.getUserId());

        String sessionId = redisCacheManager.setSession(user.getUserId(), user.getIdentityType(), groupRoleMap);
        log.info("用户登录成功: sessionId={}, account={}, userId={}, groupRoleMap={}", sessionId, account, user.getUserId(), groupRoleMap);
        return sessionId;
    }

    /**
     * 注销
     */
    public void logout(String sessionId, Long userId) {
        if (StrUtil.isBlank(sessionId)) {
            return;
        }
        redisCacheManager.deleteSession(sessionId, userId);
        log.info("用户注销成功: sessionId={}, userId={}", sessionId, userId);
    }
}