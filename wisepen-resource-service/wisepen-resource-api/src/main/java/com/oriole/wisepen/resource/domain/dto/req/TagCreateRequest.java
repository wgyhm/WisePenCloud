package com.oriole.wisepen.resource.domain.dto.req;

import com.oriole.wisepen.resource.constant.ResourceValidationMsg;
import com.oriole.wisepen.resource.domain.base.TagSpaceBase;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.VisibilityMode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class TagCreateRequest extends TagSpaceBase {
    private String parentId;

    @NotBlank(message = ResourceValidationMsg.TAG_NAME_NOT_BLANK)
    private String tagName;

    private String tagDesc;

    private VisibilityMode visibilityMode;
    private List<String> specifiedUsers;
    private List<ResourceAction> grantedActions;
}