package com.oriole.wisepen.user.api.constant;

public interface GroupValidationMsg {
    /**
     * 群组基础信息相关
     */
    String GROUP_ID_NOT_NULL = "群组ID不能为空";
    String GROUP_NAME_NOT_BLANK = "群组名称不能为空";
    String GROUP_TYPE_NOT_NULL = "群组类型不能为空";
    String GROUP_DESCRIPTION_NOT_BLANK = "群组描述不能为空";

    /**
     * 群组操作与成员相关
     */
    String INVITE_CODE_NOT_BLANK = "邀请码不能为空";
    String TARGET_USER_ID_NOT_NULL = "目标用户ID不能为空";
    String TARGET_USER_IDS_NOT_NULL = "目标用户列表不能为空";
    String ROLE_NOT_NULL = "成员角色不能为空";
    String TOKEN_LIMIT_NOT_NULL = "TOKEN限额不能为空";
}
