package com.oriole.wisepen.note.api.domain.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NoteOperationLogBase {
    private String userId;
    private String operationType;
    private String contentSummary;
    private LocalDateTime timestamp;
    /** 合并的原子操作数（颗粒度合并时 >1） */
    private Integer mergedCount;
    /** BlockNote 树状突变详情 */
    private Object details;
}
