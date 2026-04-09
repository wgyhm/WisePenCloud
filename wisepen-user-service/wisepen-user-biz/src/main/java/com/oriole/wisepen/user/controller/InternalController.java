package com.oriole.wisepen.user.controller;

import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.user.api.domain.base.GroupDisplayBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import com.oriole.wisepen.user.service.IGroupService;
import com.oriole.wisepen.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController implements RemoteUserService {

    private final IUserService userService;
    private final IGroupService groupService;

    @GetMapping("/user/getUserDisplayInfo")
    public R<Map<Long, UserDisplayBase>> getUserDisplayInfo(List<Long> userIds) {
        return R.ok(userService.getUserDisplayInfoByIds(new HashSet<>(userIds)));
    }

    @GetMapping("/group/getGroupDisplayInfo")
    public R<Map<Long, GroupDisplayBase>> getGroupDisplayInfo(List<Long> groupIds) {
        return R.ok(groupService.getGroupDisplayInfoByIds(new HashSet<>(groupIds)));
    }
}