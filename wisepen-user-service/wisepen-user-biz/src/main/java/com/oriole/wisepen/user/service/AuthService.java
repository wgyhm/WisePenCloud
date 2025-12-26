package com.oriole.wisepen.user.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.SaLoginConfig;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.oriole.wisepen.common.core.domain.enums.ResultCode;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.user.api.domain.dto.LoginBody;
import com.oriole.wisepen.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final GroupService groupService;

    /**
     * 登录逻辑
     */
    public void login(LoginBody loginBody) {
        String username = loginBody.getUsername();
        String password = loginBody.getPassword();

        // 查询用户信息 (包含密码密文)
        User user = userService.getUserCoreInfoByUsername(username);

        // 账号不存在
        if (user == null) {
            throw new ServiceException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 校验账号状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new ServiceException(ResultCode.USER_LOCKED);
        }

        // 校验密码 (BCrypt)
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new ServiceException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 获取该用户所属的所有 Group ID
        List<Long> groupIds = groupService.getGroupIdsByUserId(user.getId());
        String groupIdsStr = StrUtil.join(",", groupIds); // 转成字符串

        // 获取 Sa-Token 配置的有效期 (单位是秒)
        long timeout = SaManager.getConfig().getTimeout();
        // 计算过期时间戳 (当前时间秒数 + 有效期秒数)
        long expTime = System.currentTimeMillis() / 1000 + timeout;

        // Sa-Token 登录
        // 这里我们将 IdentityType 和 GroupIds 一起注入 Token Session，这样网关层 (APISIX) 就能读到这些数据并透传了
        StpUtil.login(user.getId(),
                SaLoginConfig.setExtra("identityType", user.getIdentityType().getCode())
                        .setExtra("groupIds", groupIdsStr)
                        .setExtra("key", "wisepen-app")
                        .setExtra("exp", expTime)
        );
        log.info("用户登录成功: username={}, id={}, groups={}", username, user.getId(), groupIdsStr);
    }

    /**
     * 注销
     */
    public void logout() {
        StpUtil.logout();
    }
}