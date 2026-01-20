package com.oriole.wisepen.common.core.domain.enums;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode implements IErrorCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 系统级
    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数错误"),

    // --- 认证/权限 (严格对应 Sa-Token 异常) ---
    NOT_LOGIN(401, "账号未登录"),             // 对应 NotLoginException
    NO_PERMISSION(403, "没有权限访问该资源"),   // 对应 NotPermissionException
    NO_ROLE(403, "没有角色权限访问该资源"),     // 对应 NotRoleException

    // 用户/认证模块 (1000-1999)
    USER_NOT_EXIST(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "账号或密码错误"),
    USER_LOCKED(1003, "账号已被冻结"),

    // 业务模块 (2000-2999)
    GROUP_IS_EXISTED(2001,"该组已存在"),
    GROUP_NOT_EXIST(2002,"该组不存在"),
    PAGE_NOT_EXIST(2003,"该页不存在"),
    MEMBER_NOT_IN_GROUP(2004,"该成员不在该组中"),
    MEMBER_IS_OWNER(2005,"组长不能退出小组"),
    PERMISSION_IS_LOWER(2006,"权限不够"),
    MEMBER_NOT_EXSIT(2007,"该组没有成员");
    // ..暂无

    private final Integer code;
    private final String msg;
}