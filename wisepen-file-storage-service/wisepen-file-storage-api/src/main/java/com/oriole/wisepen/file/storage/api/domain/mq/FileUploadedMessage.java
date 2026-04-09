package com.oriole.wisepen.file.storage.api.domain.mq;

import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class FileUploadedMessage extends StorageRecordDTO implements Serializable {
    private StorageSceneEnum scene;
    private Boolean flashUploaded;
}