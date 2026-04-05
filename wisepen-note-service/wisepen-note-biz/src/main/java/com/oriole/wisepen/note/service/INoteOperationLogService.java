package com.oriole.wisepen.note.service;

import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.note.api.domain.dto.res.NoteOperationLogResponse;
import com.oriole.wisepen.note.api.domain.mq.NoteOperationLogMessage;
import org.springframework.data.domain.Page;

import java.util.List;

public interface INoteOperationLogService {

    void batchSave(NoteOperationLogMessage message);

    PageResult<NoteOperationLogResponse> listOperationLogs(String resourceId, int page, int size);

    void deleteAllOpLogsByResourceIds(List<String> resourceId);
}
