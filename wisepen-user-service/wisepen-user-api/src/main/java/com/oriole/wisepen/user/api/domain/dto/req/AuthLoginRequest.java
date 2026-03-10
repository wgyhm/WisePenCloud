package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class AuthLoginRequest implements Serializable {

    @NotBlank(message = UserValidationMsg.USERNAME_EMPTY)
    private String account; // 用户名或学工号

    @NotBlank(message = UserValidationMsg.PASSWORD_EMPTY)
    private String password;

    private String code; // 验证码 (预留)

    private String uuid; // 唯一标识 (预留，用于验证码校验)
}