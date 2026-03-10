package com.oriole.wisepen.user.service;

import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.domain.dto.*;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthPwdResetVerifyRequest;
import com.oriole.wisepen.user.api.domain.dto.req.AuthRegisterRequest;
import com.oriole.wisepen.user.domain.entity.UserEntity;

import java.util.Map;
import java.util.Set;

public interface UserService {
    UserEntity getUserCoreInfoByAccount(String account);
    UserInfoDTO getUserInfoById(Long userId);

    UserDisplayBase getUserDisplayInfoById(Long userId);
    Map<Long, UserDisplayBase> getUserDisplayInfoByIds(Set<Long> userIds);

    void register(AuthRegisterRequest req);
    void sendResetMail(AuthPwdResetVerifyRequest req);
    void resetPassword(AuthPwdResetRequest req);
}