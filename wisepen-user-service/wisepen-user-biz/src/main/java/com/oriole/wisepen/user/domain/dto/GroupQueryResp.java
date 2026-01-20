package com.oriole.wisepen.user.domain.dto;

import lombok.Data;

@Data
public class GroupQueryResp {
	private Long id;
	private String name;
	private Long ownerId;
	private String description;
	private int type;
	private String coverUrl;
	private String inviteCode;
	private int memberCount;
}
