package com.oriole.wisepen.note.api.domain.dto.res;

import com.oriole.wisepen.note.api.domain.base.NoteVersionBase;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class NoteVersionListResponse extends NoteVersionBase {
    private String id;
}
