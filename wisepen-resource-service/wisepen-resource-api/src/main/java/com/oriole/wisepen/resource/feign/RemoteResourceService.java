package com.oriole.wisepen.resource.feign;

import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.oriole.wisepen.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 提供给其他微服务的权限 RPC 接口
 */
@Tag(name = "内部资源服务", description = "提供给其他微服务的权限与资源 Feign 接口")
@FeignClient(contextId = "remoteResourceService", value = "wisepen-resource-service")
public interface RemoteResourceService {

    @Operation(summary = "注册/创建资源", description = "注册用户资源")
    @PostMapping("/internal/resource/addRes")
    R<String> createResource(@RequestBody ResourceCreateReqDTO dto);

    @Operation(summary = "更新资源属性", description = "更新已有资源的大小等元信息")
    @PostMapping("/internal/resource/changeResAttr")
    R<Void> updateAttributes(@RequestBody ResourceUpdateReqDTO dto);

    @Operation(summary = "获取资源详细信息", description = "获取单个资源的详细信息，包括当前挂载的标签、资源覆盖权限及指定用户权限")
    @PostMapping("/internal/resource/getResourceInfo")
    R<ResourceItemResponse> getResourceInfo(@RequestBody ResourceInfoGetReqDTO dto);

    @Operation(summary = "检查资源权限", description = "校验用户对某资源是否有访问权限")
    @PostMapping("/internal/resource/checkResPermission")
    R<ResourceCheckPermissionResDTO> checkResPermission(@RequestBody ResourceCheckPermissionReqDTO dto);

    @Operation(summary = "解散小组", description = "软删除小组下的 Tag 树与资源配置，30 天后由定时任务彻底清理")
    @PostMapping("/internal/resource/dissolveGroup")
    R<Void> dissolveGroup(@RequestParam("groupId") Long groupId);

}