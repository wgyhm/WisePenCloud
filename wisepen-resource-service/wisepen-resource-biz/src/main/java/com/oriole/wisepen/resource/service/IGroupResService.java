package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.resource.domain.dto.req.GroupResConfigUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.GroupResConfigResponse;
import com.oriole.wisepen.resource.enums.FileOrganizationLogic;

public interface IGroupResService {

    /**
     * 获取小组资源配置，查不到时默认返回 FOLDER 模式。
     */
    GroupResConfigResponse getGroupResConfig(String groupId);

    /**
     * 创建或更新小组资源配置（懒初始化，首次调用时写入记录）。
     */
    void upsertGroupResConfig(GroupResConfigUpdateRequest req);

    /**
     * 获取小组的文件组织模式，供内部校验使用（查不到默认 FOLDER）。
     */
    FileOrganizationLogic getFileOrgLogic(String groupId);

    /**
     * 小组解散时软删除
     */
    void softRemoveGroupResConfigByGroupId(String groupId);

    /**
     * 30 天后的硬删除，由定时任务调用
     */
    void hardRemoveGroupResConfigByGroupId(String groupId);
}
