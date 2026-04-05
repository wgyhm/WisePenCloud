package com.oriole.wisepen.file.storage.controller;

import cn.hutool.core.io.FileUtil;
import com.oriole.wisepen.common.core.domain.R;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.security.annotation.CheckLogin;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.exception.StorageErrorCode;
import com.oriole.wisepen.file.storage.service.IStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.EnumSet;

import static com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum.*;

@RestController
@RequestMapping("/storage/")
@RequiredArgsConstructor
@CheckLogin
public class StorageController {

    private final IStorageService storageService;

    /**
     * 图床代理上传
     * @param file    图片文件
     * @param scene   业务场景
     * @param bizTag  业务隔离标识
     */
    @PostMapping("/imageUpload")
    public R<StorageRecordDTO> uploadImageProxy(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "scene", defaultValue = "true") StorageSceneEnum scene,
            @RequestParam(value = "bizTag", required = false) String bizTag) {

        String extension = FileUtil.extName(file.getOriginalFilename()).toLowerCase();
        if (!Arrays.asList("jpg", "jpeg", "png", "gif", "webp").contains(extension)) {
            throw new ServiceException(StorageErrorCode.FILE_TYPE_UNSUPPORTED);
        }
        if (!EnumSet.of(PUBLIC_IMAGE_FOR_USER, PUBLIC_IMAGE_FOR_GROUP, PRIVATE_IMAGE_FOR_NOTE).contains(scene)) {
            throw new ServiceException(StorageErrorCode.SCENE_TYPE_UNSUPPORTED);
        }
        StorageRecordDTO record = storageService.uploadSmallFileProxy(file, scene, bizTag);
        return R.ok(record);
    }
}