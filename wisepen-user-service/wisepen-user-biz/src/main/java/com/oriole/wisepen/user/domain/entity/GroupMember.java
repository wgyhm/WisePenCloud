package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_group_member")
public class GroupMember implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 组-成员ID (雪花算法) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 组ID */
    private Long groupId;

    /** 用户ID */
    private Long userId;

    private Integer role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinTime;
}