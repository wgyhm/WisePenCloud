package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.BusinessType;
import com.oriole.wisepen.common.log.annotation.Log;
import com.oriole.wisepen.common.security.annotation.CheckRole;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;
import com.oriole.wisepen.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@CheckRole(IdentityType.ADMIN)
public class AdminUserController {

    private final IUserService userService;

    @GetMapping("/getUserList")
    @Log(title = "管理员查询用户列表", businessType = BusinessType.SELECT)
    public R<PageResult<UserEntity>> getUserList(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam String keyword,
            @RequestParam Status status,
            @RequestParam IdentityType identityType
    ) {
        return R.ok(userService.getUserListAdmin(page, size, keyword, status, identityType));
    }

    @GetMapping("/getUserInfo")
    @Log(title = "管理员获取用户详情", businessType = BusinessType.SELECT)
    public R<UserProfileEntity> getUserInfo(@RequestParam("userId") Long userId) {
        return R.ok(userService.getUserDetailInfoAdmin(userId));
    }

    @PostMapping("/changeUserProfile")
    @Log(title = "管理员更新用户资料", businessType = BusinessType.UPDATE)
    public R<Void> updateUserProfile(@RequestBody UserProfileAdminUpdateRequest req) {
        userService.updateProfileAdmin(req);
        return R.ok();
    }

    @PostMapping("/changeUserInfo")
    @Log(title = "管理员更新用户信息", businessType = BusinessType.UPDATE)
    public R<Void> updateUserInfo(@RequestBody UserInfoAdminUpdateRequest req) {
        userService.updateUserInfoAdmin(req);
        return R.ok();
    }

    @PostMapping("/resetPassword")
    @Log(title = "管理员重置用户密码", businessType = BusinessType.UPDATE)
    public R<Void> resetPassword(@RequestBody AuthPwdAdminResetRequest req) {
        userService.resetPasswordAdmin(req);
        return R.ok();
    }
}
