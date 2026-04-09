package com.oriole.wisepen.note.api.domain.dto.res;

import com.oriole.wisepen.note.api.domain.base.NoteOperationLogBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NoteOperationLogResponse extends NoteOperationLogBase {
    private String id;
}
