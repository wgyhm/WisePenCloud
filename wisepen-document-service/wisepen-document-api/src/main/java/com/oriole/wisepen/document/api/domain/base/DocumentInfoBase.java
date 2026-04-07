package com.oriole.wisepen.document.api.domain.base;

import com.oriole.wisepen.document.api.enums.DocumentStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfoBase {
    private DocumentUploadMeta uploadMeta;
    private DocumentStatus documentStatus;
    private Integer maxPreviewPages;
}
