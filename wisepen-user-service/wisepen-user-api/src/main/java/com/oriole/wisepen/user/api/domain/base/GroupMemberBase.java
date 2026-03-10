package com.oriole.wisepen.user.api.domain.base;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Date;

@Data
@SuperBuilder
@NoArgsConstructor
public class GroupMemberBase implements Serializable {
    private GroupRoleType role; // 用户角色
    private Date joinTime; // 加入时间

    private Integer tokenLimit;
    private Integer tokenUsed;
}
