package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.enums.ResPermissionLevelEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResourceCheckPermissionResDTO {
    private ResPermissionLevelEnum resPermissionLevel;
    private String permissionSource;

    public ResourceCheckPermissionResDTO(ResPermissionLevelEnum resPermissionLevel) {
        this.resPermissionLevel = resPermissionLevel;
    }
}
