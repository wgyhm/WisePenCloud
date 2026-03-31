package com.oriole.wisepen.resource.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum ResourceAction {
    VIEW(1 << 0),               // 1: 在线阅读
    EDIT(1 << 1),               // 2: 协同编辑
    DOWNLOAD_WATERMARK(1 << 2), // 4: 导出/下载带水印
    DOWNLOAD_ORIGINAL(1 << 3);  // 8: 下载源文件

    public static final int ALL_ACTIONS = (1 << values().length) - 1;
    public static final int DEFAULT_MEMBER_ACTIONS = VIEW.code | DOWNLOAD_WATERMARK.code; // 7

    private final int code;

    // 将权限掩码解析为枚举列表
    public static List<ResourceAction> permissionCodeToActions(int permissionCode) {
        return Arrays.stream(values())
                // 使用按位与(&)判断是否包含该权限
                .filter(action -> (permissionCode & action.code) != 0)
                .collect(Collectors.toList());
    }

    // 将枚举列表合并为一个权限掩码
    public static int actionsToPermissionCode(List<ResourceAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return 0;
        }
        return actions.stream()
                .mapToInt(ResourceAction::getCode)
                // 使用按位或(|)合并所有权限
                .reduce(0, (a, b) -> a | b);
    }

    // 快速校验是否拥有某个指定权限
    public static boolean hasAction(int currentPermissionCode, ResourceAction targetAction) {
        return (currentPermissionCode & targetAction.code) != 0;
    }
}
