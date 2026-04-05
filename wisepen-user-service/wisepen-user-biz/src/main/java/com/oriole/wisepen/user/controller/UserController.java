package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.user.api.domain.dto.req.UserInfoUpdateRequest;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.domain.dto.VerificationResultDTO;
import com.oriole.wisepen.user.api.domain.dto.req.UserProfileUpdateRequest;
import com.oriole.wisepen.user.api.enums.UserVerificationMode;
import com.oriole.wisepen.user.service.IUserService;
import com.oriole.wisepen.user.strategy.VerificationStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final VerificationStrategyFactory verificationStrategyFactory;

    @CheckLogin
    @GetMapping("/getUserInfo")
    @Log(title = "用户信息获取", businessType= BusinessType.SELECT, isSaveResponseData=false)
    public R<UserDetailInfoResponse> getUserInfo() {
        Long userId = SecurityContextHolder.getUserId();
        UserDetailInfoResponse userInfo = userService.getUserInfoById(userId);
        return R.ok(userInfo);
    }

    @CheckLogin
    @PutMapping("/changeUserProfile")
    @Log(title = "更新用户资料", businessType = BusinessType.UPDATE)
    public R<Void> updateUserProfile(@RequestBody UserProfileUpdateRequest dto) {
        userService.updateProfile(SecurityContextHolder.getUserId(), dto);
        return R.ok();
    }

    @CheckLogin
    @PutMapping("/changeUserInfo")
    @Log(title = "更新用户信息", businessType = BusinessType.UPDATE)
    public R<Void> updateUserInfo(@RequestBody UserInfoUpdateRequest dto) {
        userService.updateUserInfo(SecurityContextHolder.getUserId(), dto);
        return R.ok();
    }

    @CheckLogin
    @PostMapping("/verify/initiateEmailVerify")
    @Log(title = "发起邮箱验证", businessType = BusinessType.OTHER)
    public R<Void> initiateEmailVerify(@RequestParam("email") String email) {
        Map<String,Object> map = new HashMap<>();
        map.put("email", email);
        verificationStrategyFactory.getStrategy(UserVerificationMode.EDU_EMAIL)
                .initiate(SecurityContextHolder.getUserId(), map);
        return R.ok();
    }

    @GetMapping("/verify/checkEmailVerify")
    @Log(title = "邮箱验证回调", businessType = BusinessType.OTHER)
    public R<Void> checkEmailVerify(@RequestParam("token") String token) {
        Map<String,Object> map = new HashMap<>();
        map.put("token", token);
        verificationStrategyFactory.getStrategy(UserVerificationMode.EDU_EMAIL).verify(map);
        return R.ok();
    }

    @CheckLogin
    @PostMapping("/verify/initiateFudanUISVerify")
    @Log(title = "发起复旦UIS认证", businessType = BusinessType.OTHER)
    public R<Void> initiateFudanUISVerify(@RequestParam("uisAccount") String uisAccount, @RequestParam("uisPassword") String uisPassword) {
        Map<String,Object> map = new HashMap<>();
        map.put("uisAccount", uisAccount);
        map.put("uisPassword", uisPassword);
        verificationStrategyFactory.getStrategy(UserVerificationMode.FDU_UIS_SYS)
                .initiate(SecurityContextHolder.getUserId(), map);
        return R.ok();
    }

    @CheckLogin
    @GetMapping("/verify/checkFudanUISVerify")
    @Log(title = "检查复旦UIS认证状态", businessType = BusinessType.OTHER)
    public R<VerificationResultDTO> checkFudanUISVerify() {
        Map<String,Object> map = new HashMap<>();
        map.put("userId", SecurityContextHolder.getUserId());
        VerificationResultDTO dto = verificationStrategyFactory.getStrategy(UserVerificationMode.FDU_UIS_SYS).verify(map);
        return R.ok(dto);
    }
}