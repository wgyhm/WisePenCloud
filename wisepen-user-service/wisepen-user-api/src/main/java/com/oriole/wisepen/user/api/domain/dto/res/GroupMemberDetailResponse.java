package com.oriole.wisepen.user.api.domain.dto.res;

import com.oriole.wisepen.user.api.domain.base.GroupMemberBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupMemberDetailResponse extends GroupMemberBase {
	private Long groupId; // 组ID
	private Long memberId; // 成员 UID
	private UserDisplayBase memberInfo; // 成员信息
}
