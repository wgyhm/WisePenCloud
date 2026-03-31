package com.oriole.wisepen.resource.domain;

import com.oriole.wisepen.resource.enums.VisibilityMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAcl {
    private String groupId;
    private VisibilityMode visibilityMode;
    private List<String> specifiedUsers;
    private Integer grantedActionsMask;
}