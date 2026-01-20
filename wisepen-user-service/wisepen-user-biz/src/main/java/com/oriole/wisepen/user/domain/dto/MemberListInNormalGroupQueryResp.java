package com.oriole.wisepen.user.domain.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberListInNormalGroupQueryResp {
	private Long userId;
	private String nickName;
	private Integer role;
	private LocalDateTime joinTime;
}
