package com.oriole.wisepen.user.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.json.JSONUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.exception.UserErrorCode;
import com.oriole.wisepen.user.api.domain.dto.LoginRequest;
import com.oriole.wisepen.user.domain.entity.User;
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
    private final GroupService groupService;

    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_SESSION_PREFIX = "wisepen:user:auth:session:";
    private static final String REDIS_USER_SESSION_PREFIX = "wisepen:user:auth:user:";
    private static final long SESSION_TIMEOUT_DAYS = 7;

    public String login(LoginRequest loginRequest) {
        String account = loginRequest.getAccount();

        // 查询用户信息 (包含密码密文)
        User user = userService.getUserCoreInfoByAccount(account);

        // 账号不存�?
        if (user==null){
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }

        // 校验账号状�?
        if (user.getStatus()== Status.BANNED) {
            throw new ServiceException(UserErrorCode.USER_LOCKED);
        }

        // 校验密码
        if (!BCrypt.checkpw(loginRequest.getPassword(), user.getPassword())) {
            throw new ServiceException(UserErrorCode.USER_PASSWORD_ERROR);
        }

        Map<String, Integer> groupRoleMap = groupService.getGroupRoleMapByUserId(user.getId());

        // 构建 Session 上下文数�?
        Map<String, Object> sessionData = new HashMap<>();
        String userId = user.getId().toString();
        sessionData.put("userId", userId);
        sessionData.put("identityType", user.getIdentityType().getCode());
        sessionData.put("groupRoleMap", groupRoleMap);

        String userSessionKey = REDIS_USER_SESSION_PREFIX + userId;
        String existingSessionId = stringRedisTemplate.opsForValue().get(userSessionKey);
        if (StrUtil.isNotBlank(existingSessionId)) {
            String existingRedisKey = REDIS_SESSION_PREFIX + existingSessionId;
            if (stringRedisTemplate.hasKey(existingRedisKey)) {
                stringRedisTemplate.opsForValue().set(
                        existingRedisKey,
                        JSONUtil.toJsonStr(sessionData),
                        SESSION_TIMEOUT_DAYS,
                        TimeUnit.DAYS
                );
                stringRedisTemplate.expire(userSessionKey, SESSION_TIMEOUT_DAYS, TimeUnit.DAYS);
                log.info("用户登录成功: account={}, id={}, sessionId={}", account, userId, existingSessionId);
                return existingSessionId;
            }
            stringRedisTemplate.delete(userSessionKey);
        }

        String sessionId = IdUtil.fastSimpleUUID();
        String redisKey = REDIS_SESSION_PREFIX + sessionId;
        // 存入 Redis
        stringRedisTemplate.opsForValue().set(
                redisKey,
                JSONUtil.toJsonStr(sessionData),
                SESSION_TIMEOUT_DAYS,
                TimeUnit.DAYS
        );
        stringRedisTemplate.opsForValue().set(
                userSessionKey,
                sessionId,
                SESSION_TIMEOUT_DAYS,
                TimeUnit.DAYS
        );

        log.info("用户登录成功: account={}, id={}, groupRoleMap={}", account, user.getId(), groupRoleMap);
        return sessionId;
    }

    /**
     * 注销
     */
    public void logout(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return;
        }
        String redisKey = REDIS_SESSION_PREFIX + sessionId;
        String sessionJson = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isNotBlank(sessionJson)) {
            try {
                String parsedUserId = JSONUtil.parseObj(sessionJson).getStr("userId");
                if (StrUtil.isNotBlank(parsedUserId)) {
                    stringRedisTemplate.delete(REDIS_USER_SESSION_PREFIX + parsedUserId);
                }
            } catch (Exception e) {
                log.warn("Failed to parse session json: sessionId={}", sessionId, e);
            }
        }
        // 直接从 Redis 中物理删除该 Session
        Boolean deleted = stringRedisTemplate.delete(redisKey);
        if (deleted) {
            log.info("用户主动注销成功，已清理 Redis 会话: sessionId={}", sessionId);
        } else {
            log.warn("用户注销时会话已不存在或已过 sessionId={}", sessionId);
        }
    }
}