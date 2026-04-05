package com.oriole.wisepen.user.api.feign;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Tag(name = "内部用户服务", description = "提供给其他微服务的 Feign 接口")
@FeignClient(contextId = "remoteUserService", value = "wisepen-user-service")
public interface RemoteUserService {

    @Operation(summary = "获取用户展示信息", description = "获取指定用户（列表）的展示类信息")
    @GetMapping("/internal/user/getUserDisplayInfo")
    R<Map<Long, UserDisplayBase>> getUserDisplayInfo(@RequestParam("userId") List<Long> userIds);

    @Operation(summary = "获取小组展示信息", description = "获取指定小组（列表）的展示类信息")
    @GetMapping("/internal/group/getGroupDisplayInfo")
    R<Map<Long, GroupDisplayBase>> getGroupDisplayInfo(@RequestParam("groupId") List<Long> groupIds);

}