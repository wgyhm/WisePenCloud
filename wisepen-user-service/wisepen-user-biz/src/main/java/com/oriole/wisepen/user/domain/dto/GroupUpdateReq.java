package com.oriole.wisepen.user.domain.dto;

import lombok.Data;

@Data
public class GroupUpdateReq {
	private Long id;
	private String name;
	private String description;
	private String coverUrl;
}
