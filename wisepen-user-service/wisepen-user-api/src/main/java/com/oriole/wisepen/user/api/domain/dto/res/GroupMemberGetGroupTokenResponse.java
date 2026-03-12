package com.oriole.wisepen.user.api.domain.dto.res;


import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class GroupMemberGetGroupTokenResponse extends GroupDisplayBase {
	Integer tokenUsed;
	Integer tokenBalance;
}
