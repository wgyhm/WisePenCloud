package com.oriole.wisepen.resource.controller;

import com.oriole.wisepen.common.core.context.SecurityContextHolder;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.resource.domain.dto.req.GroupResConfigUpdateRequest;
import com.oriole.wisepen.resource.domain.dto.res.GroupResConfigResponse;
import com.oriole.wisepen.resource.service.IGroupResService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小组资源配置", description = "小组文件组织模式（Folder / Tag）的查询与设置")
@RestController
@RequestMapping("/resource/groupConfig")
@RequiredArgsConstructor
@CheckLogin
public class GroupResConfigController {

    private final IGroupResService groupResService;

    @Operation(summary = "获取小组资源配置", description = "小组成员可查询，查不到时默认返回 FOLDER 模式")
    @GetMapping("/getConfig")
    public R<GroupResConfigResponse> getConfig(@RequestParam("groupId") String groupId) {
        SecurityContextHolder.assertInGroup(Long.parseLong(groupId));
        return R.ok(groupResService.getGroupResConfig(groupId));
    }

    @Operation(summary = "设置小组资源配置", description = "仅小组 OWNER 或 ADMIN 可操作，首次调用时创建记录")
    @PostMapping("/changeConfig")
    public R<Void> upsertConfig(@RequestBody GroupResConfigUpdateRequest req) {
        SecurityContextHolder.assertGroupRole(Long.parseLong(req.getGroupId()), GroupRoleType.OWNER, GroupRoleType.ADMIN);
        groupResService.upsertGroupResConfig(req);
        return R.ok();
    }
}
