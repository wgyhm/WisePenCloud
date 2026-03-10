package com.oriole.wisepen.user.exception;

import com.oriole.wisepen.common.core.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GroupErrorCode implements IErrorCode {            // 对应 NotLoginException
	// 业务模块 (2000-2999)
	GROUP_NOT_EXIST(2001,"该组不存在"),
	TARGET_MEMBER_NOT_EXIST(2002,"操作的目标成员不存在"),
	MEMBER_IS_EXISTED(2003,"成员已存在于该组"),
	OWNER_QUIT_GROUP(2004,"组长不能退出小组"),
	LIMIT_CANNOT_BE_LOWER_THAN_USED(2005,"配额不能低于用量"),
	GROUP_HAS_NO_QUOTA(2006,"当前组不能配置配额");

	// ..暂无

	private final Integer code;
	private final String msg;
}