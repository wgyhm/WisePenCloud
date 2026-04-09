package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupItemInfoResponse extends GroupDisplayBase {
	private Long groupId;
	private Long ownerId;
	private UserDisplayBase ownerInfo;
	private Integer memberCount;
	private LocalDateTime createTime;
}