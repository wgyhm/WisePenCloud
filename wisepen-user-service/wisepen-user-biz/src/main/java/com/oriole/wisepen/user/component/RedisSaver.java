package com.oriole.wisepen.user.component;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisSaver {
	private final StringRedisTemplate stringRedisTemplate;

	private static final String REDIS_SESSION_PREFIX = "wisepen:user:auth:session:";
	private static final String REDIS_USER_SESSION_PREFIX = "wisepen:user:auth:user:";
	private static final long SESSION_TIMEOUT_DAYS = 7;

	public String getSessionIdByUserId(String userId) {
		if (StrUtil.isBlank(userId)) {
			return null;
		}
		String sessionId = stringRedisTemplate.opsForValue().get(REDIS_USER_SESSION_PREFIX + userId);
		if (StrUtil.isBlank(sessionId)) {
			return null;
		}
		String redisKey = REDIS_SESSION_PREFIX + sessionId;
		if (!stringRedisTemplate.hasKey(redisKey)) {
			return null;
		}
		return sessionId;
	}

	public void updateGroupRoleMap(Long userId, Map<String, Integer> groupRoleMap) {
		String sessionId=getSessionIdByUserId(userId.toString());
		if (sessionId==null) {
			return;
		}
		if (StrUtil.isBlank(sessionId)) {
			return;
		}
		String normalizedSessionId = StrUtil.trim(sessionId);
		if ((normalizedSessionId.startsWith("\"") && normalizedSessionId.endsWith("\""))
				|| (normalizedSessionId.startsWith("'") && normalizedSessionId.endsWith("'"))) {
			normalizedSessionId = normalizedSessionId.substring(1, normalizedSessionId.length() - 1);
		}
		String redisKey = REDIS_SESSION_PREFIX + normalizedSessionId;
		String sessionJson = stringRedisTemplate.opsForValue().get(redisKey);
		if (StrUtil.isBlank(sessionJson)) {
			return;
		}
		Long ttlSeconds = stringRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
		String updatedJson = JSONUtil.parseObj(sessionJson)
				.set("groupRoleMap", groupRoleMap)
				.toString();
		if (ttlSeconds == null || ttlSeconds <= 0) {
			stringRedisTemplate.opsForValue().set(redisKey, updatedJson, SESSION_TIMEOUT_DAYS, TimeUnit.DAYS);
		} else {
			stringRedisTemplate.opsForValue().set(redisKey, updatedJson, ttlSeconds, TimeUnit.SECONDS);
		}
	}
}
