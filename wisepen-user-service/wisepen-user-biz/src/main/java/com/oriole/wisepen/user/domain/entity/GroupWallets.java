package com.oriole.wisepen.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_group_wallets")
public class GroupWallets {

	@TableId(type = IdType.INPUT) // 关键：id不是自增，必须手动填入 member.id
	private Long id;

	private Integer groupBalance;
}
