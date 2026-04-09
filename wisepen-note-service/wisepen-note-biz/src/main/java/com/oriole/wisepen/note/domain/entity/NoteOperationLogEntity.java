package com.oriole.wisepen.note.domain.entity;

import com.oriole.wisepen.note.api.domain.base.NoteOperationLogBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "note_operation_logs")
@CompoundIndex(name = "idx_resource_timestamp", def = "{'resourceId': 1, 'timestamp': -1}")
public class NoteOperationLogEntity extends NoteOperationLogBase {
    @Id
    private String id;
    private String resourceId;
}
