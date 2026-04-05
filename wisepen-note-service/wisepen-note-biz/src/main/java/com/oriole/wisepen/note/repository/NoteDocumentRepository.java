package com.oriole.wisepen.note.repository;

import com.oriole.wisepen.note.domain.entity.NoteInfoEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteDocumentRepository extends MongoRepository<NoteInfoEntity, String> {

    Optional<NoteInfoEntity> findByResourceId(String resourceId);

    void deleteByResourceIdIn(List<String> resourceIds);
}
