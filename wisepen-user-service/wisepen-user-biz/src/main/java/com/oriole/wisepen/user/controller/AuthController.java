package com.oriole.wisepen.user.controller;

import cn.hutool.core.util.StrUtil;
import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.req.AuthLoginRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthRegisterRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetVerifyRequest;
import com.oriole.wisepen.user.service.AuthService;
import com.oriole.wisepen.user.service.IUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static com.oriole.wisepen.common.core.constant.SecurityConstants.AUTHORIZATION_TOKEN;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final IUserService userService;

    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody AuthLoginRequest loginRequest, HttpServletResponse response) {
        String sessionId = authService.login(loginRequest);

        Cookie cookie = buildAuthCookie(sessionId, 7 * 24 * 60 * 60);
        response.addCookie(cookie);
        // 返回 sessionId，以备加入请求头中
        return R.ok(sessionId);
    }

    @CheckLogin
    @PostMapping("/logout")
    public R<Void> logout(HttpServletResponse response) {
        String sessionId = SecurityContextHolder.getUserAuthToken();

        if (StrUtil.isNotBlank(sessionId)) {
            authService.logout(sessionId, SecurityContextHolder.getUserId());
        }

        // 创建一个同名、同路径的空 Cookie
        Cookie clearCookie = buildAuthCookie(null, 0); // Max-Age=0 会强制浏览器立刻彻底删除该 Cookie
        response.addCookie(clearCookie);
        return R.ok();
    }

    private Cookie buildAuthCookie (String value, Integer maxAge) {
        Cookie cookie = new Cookie(AUTHORIZATION_TOKEN, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true); // 严禁前端 JS 读取，防 XSS
        // cookie.setSecure(true); // HTTPS 务必开启此项
        cookie.setMaxAge(maxAge); // 7天
        return cookie;
    }

    @PostMapping("/register")
    public R<String> register(@Valid @RequestBody AuthRegisterRequest req) {
        userService.register(req);
        return R.ok();
    }

    @PostMapping("/sendResetMail")
    public R<Void> sendResetMail(@Valid @RequestBody AuthPwdResetVerifyRequest req) {
        userService.sendResetMail(req);
        return R.ok();
    }

    @PostMapping("/resetPassword")
    public R<Void> resetPassword(@Valid @RequestBody AuthPwdResetRequest req) {
        userService.resetPassword(req);
        return R.ok();
    }

}