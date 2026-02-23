package com.oriole.wisepen.user.api.domain.dto;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class MemberListQueryResponse implements Serializable {
	//其实是 userid
	private Long userId;
	private String realname;
	private String nickname;
	private GroupRoleType role;
	private LocalDateTime joinTime;
}
