package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.enums.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class ResourceCheckPermissionReqDTO {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_ID_NOT_BLANK)
    private String resourceId;
    @NotNull(message = ResourceValidationMsg.RESOURCE_TYPE_NOT_NULL)
    private ResourceType resourceType;
    @NotBlank(message = ResourceValidationMsg.USER_ID_NOT_BLANK)
    private String userId;
    // 用户所属的群组及对应角色
    @NotNull(message = ResourceValidationMsg.USER_GROUP_ROLES_NOT_NULL)
    private Map<Long, GroupRoleType> groupRoles;
}