package com.oriole.wisepen.resource.domain.dto;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.enums.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceCreateReqDTO {
    @NotBlank(message = ResourceValidationMsg.RESOURCE_NAME_NOT_BLANK)
    private String resourceName;
    @NotNull(message = ResourceValidationMsg.RESOURCE_TYPE_NOT_NULL)
    private ResourceType resourceType;
    @NotBlank(message = ResourceValidationMsg.OWNER_ID_NOT_BLANK)
    private String ownerId;

    private String pathTagId;

    private String preview;        // 初始预览图
    private Long size;             // 初始大小/字数
}