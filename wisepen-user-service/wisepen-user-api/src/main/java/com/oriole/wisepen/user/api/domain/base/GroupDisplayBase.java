package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.GroupType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
public class GroupDisplayBase implements Serializable {
    private String groupName;
    private String groupDesc;
    private String groupCoverUrl;
    private GroupType groupType;
}
