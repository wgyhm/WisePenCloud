package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.user.api.constant.GroupValidationMsg;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class GroupIdentityBase implements Serializable {
    @NotNull(message = GroupValidationMsg.GROUP_ID_NOT_NULL)
    private Long groupId;
}
