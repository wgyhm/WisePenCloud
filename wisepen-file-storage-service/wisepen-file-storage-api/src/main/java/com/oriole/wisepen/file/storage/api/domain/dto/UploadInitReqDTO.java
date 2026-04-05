package com.oriole.wisepen.file.storage.api.domain.dto;

import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UploadInitReqDTO implements Serializable {
    private String md5;
    private String extension;
    private StorageSceneEnum scene;
    private String bizTag;
    private Long configId;
    private Long expectedSize;
}