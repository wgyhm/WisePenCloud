package com.oriole.wisepen.note.repository;

import com.oriole.wisepen.note.domain.entity.NoteOperationLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteOperationLogRepository extends MongoRepository<NoteOperationLogEntity, String> {

    Page<NoteOperationLogEntity> findByResourceIdOrderByTimestampDesc(String resourceId, Pageable pageable);

    void deleteByResourceIdIn(List<String> resourceIds);
}
