package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_group_member_quotas")
public class GroupMemberQuotas {
	@TableId(type = IdType.INPUT) // 关键：id不是自增，必须手动填入 member.id
	private long id;

	private Integer quotaUsed;

	private Integer quotaLimit;

	private LocalDateTime updateTime;
}
