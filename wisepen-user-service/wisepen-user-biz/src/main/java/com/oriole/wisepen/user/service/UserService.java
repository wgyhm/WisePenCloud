package com.oriole.wisepen.user.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.req.*;
import com.oriole.wisepen.user.api.domain.dto.res.UserDetailInfoResponse;
import com.oriole.wisepen.user.api.enums.Status;
import com.oriole.wisepen.user.domain.entity.UserEntity;
import com.oriole.wisepen.user.domain.entity.UserProfileEntity;

import java.util.Map;
import java.util.Set;

public interface UserService {

    // 根据账号获取用户信息
    UserEntity getUserCoreInfoByAccount(String account);
    // 根据 userId 获取用户详细信息
    UserDetailInfoResponse getUserInfoById(Long userId);
    // 根据 userId 列表获取用户展示信息
    Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds);

    // 注册
    void register(AuthRegisterRequest req);

    // 发送重置密码邮件
    void sendResetMail(AuthPwdResetVerifyRequest req);
    // 重置密码
    void resetPassword(AuthPwdResetRequest req);

    // 更新用户信息
    void updateUserInfo(Long userId, UserInfoUpdateRequest req);
    // 更新用户Profile信息
    void updateProfile(Long userId, UserProfileUpdateRequest req);

    // 仅限管理员使用
    void resetPasswordAdmin(AuthPwdAdminResetRequest req);
    void updateUserInfoAdmin(UserInfoAdminUpdateRequest req);
    void updateProfileAdmin(UserProfileAdminUpdateRequest req);
    PageResult<UserEntity> getUserListAdmin(int page, int size, String keyword, Status status, IdentityType identityType);
    UserProfileEntity getUserDetailInfoAdmin(Long userId);
}