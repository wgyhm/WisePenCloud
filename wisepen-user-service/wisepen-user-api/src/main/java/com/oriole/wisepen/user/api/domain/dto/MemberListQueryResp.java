package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberListQueryResp {
	//其实是 userid
	private Long userId;
	private String realname;
	private String nickname;
	private Integer role;
	private LocalDateTime joinTime;
}
