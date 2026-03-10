package com.oriole.wisepen.user.api.domain.dto.req;

import com.oriole.wisepen.user.api.constant.UserValidationMsg;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class AuthPwdResetVerifyRequest implements Serializable {

    @NotBlank(message = UserValidationMsg.CAMPUS_NO_EMPTY)
    private String campusNo; // 学工号

    private String code; // 验证码 (预留)

    private String uuid; // 唯一标识 (预留，用于验证码校验)
}