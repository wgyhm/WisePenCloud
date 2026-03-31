package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.resource.enums.ResourceAction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
public class ResourceCheckPermissionResDTO {

    // 用户是以什么身份/角色访问这个资源的
    private ResourceAccessRole resourceAccessRole;

    // 这个身份是哪里赋予的
    private Set<String> permissionSources;

    // 可用动作有哪些
    private List<ResourceAction> allowedActions;

    public ResourceCheckPermissionResDTO(ResourceAccessRole resourceAccessRole) {
        this.resourceAccessRole = resourceAccessRole;
    }
}
