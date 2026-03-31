package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import com.oriole.wisepen.resource.enums.ResourceAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupResConfigResponse {
    private String groupId;
    private FileOrganizationLogic fileOrgLogic;
    private List<ResourceAction> defaultMemberActions;
}
