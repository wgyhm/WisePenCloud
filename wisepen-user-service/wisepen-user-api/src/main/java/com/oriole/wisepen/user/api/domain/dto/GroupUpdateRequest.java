package com.oriole.wisepen.user.api.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GroupUpdateRequest implements Serializable {
	private Long id;
	private String name;
	private String description;
	private String coverUrl;
}
