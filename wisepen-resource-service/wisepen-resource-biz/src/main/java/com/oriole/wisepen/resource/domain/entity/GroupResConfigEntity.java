package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "wisepen_group_res_config")
public class GroupResConfigEntity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String groupId;

    private FileOrganizationLogic fileOrgLogic;

    /** 该小组 MEMBER 角色的默认权限掩码，null 时回退为 ResourceActionEnum.DEFAULT_MEMBER_ACTIONS */
    private Integer defaultMemberActionsMask;

    private Date updateTime;

    /** 小组解散时间，非 null 表示已解散；定时任务据此判断是否到期硬删*/
    private Date dissolvedAt;
}
