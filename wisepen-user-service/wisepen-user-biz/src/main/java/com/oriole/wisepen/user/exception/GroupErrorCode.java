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
	GROUP_HAS_NO_QUOTA(2006,"当前组不能配置配额"),
	VOUCHER_NOT_EXIST(2007,"点卡不存在"),
	VOUCHER_IS_USED(2008,"点卡不可用或已用过"),
	VOUCHER_IS_EXPIRED(2009,"点卡已过期");

	// ..暂无

	private final Integer code;
	private final String msg;
}