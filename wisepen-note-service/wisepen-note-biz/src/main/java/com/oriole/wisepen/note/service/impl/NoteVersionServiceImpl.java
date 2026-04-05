package com.oriole.wisepen.note.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSnapshotResponse;
import com.oriole.wisepen.note.api.domain.dto.res.NoteVersionListResponse;
import com.oriole.wisepen.note.api.domain.mq.NoteSnapshotMessage;
import com.oriole.wisepen.note.domain.entity.NoteInfoEntity;
import com.oriole.wisepen.note.domain.entity.NoteVersionEntity;
import com.oriole.wisepen.note.api.domain.enums.VersionType;
import com.oriole.wisepen.note.exception.NoteErrorCode;
import com.oriole.wisepen.note.repository.NoteDocumentRepository;
import com.oriole.wisepen.note.repository.NoteVersionRepository;
import com.oriole.wisepen.note.service.INoteVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.Binary;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteVersionServiceImpl implements INoteVersionService {

    private final NoteVersionRepository noteVersionRepository;
    private final NoteDocumentRepository noteDocumentRepository;

    @Override
    public void createVersion(NoteSnapshotMessage msg) {
        List<Long> updatedBy = msg.getUpdatedBy() != null ? msg.getUpdatedBy().stream().map(Long::valueOf).toList() : null;
        NoteVersionEntity noteVersionEntity = NoteVersionEntity.builder()
                .type(VersionType.valueOf(msg.getType()))
                .data(new Binary(Base64.getDecoder().decode(msg.getData())))
                .createdAt(new Date())
                .createdBy(updatedBy)
                .build();
        BeanUtils.copyProperties(msg, noteVersionEntity, "type","data");
        noteVersionRepository.save(noteVersionEntity);

        updateNoteDocumentMetadata(msg, updatedBy);
    }

    private void updateNoteDocumentMetadata(NoteSnapshotMessage msg, List<Long> currentRoundAuthors) {
        noteDocumentRepository.findByResourceId(msg.getResourceId()).ifPresent(noteInfo -> {
            // 更新最后修改时间
            noteInfo.setLastUpdatedAt(new Date());
            if (currentRoundAuthors != null && !currentRoundAuthors.isEmpty()) {
                // 将现有的作者和当前的作者合并，利用 CollUtil.distinct 自动去重
                List<Long> existing = noteInfo.getAuthors() == null ? CollUtil.newArrayList() : noteInfo.getAuthors();
                existing.addAll(currentRoundAuthors);
                noteInfo.setAuthors(CollUtil.distinct(existing));
            }
            // 如果是 FULL 快照，顺便更新纯文本供 ES/全文检索使用
            if (VersionType.FULL.name().equals(msg.getType()) && msg.getPlainText() != null) {
                noteInfo.setPlainText(msg.getPlainText());
            }
            noteDocumentRepository.save(noteInfo);
        });
    }

    @Override
    public NoteSnapshotResponse getLatestVersion(String resourceId) {
        NoteInfoEntity noteInfoEntity = noteDocumentRepository.findByResourceId(resourceId)
                .orElseThrow(() -> new ServiceException(NoteErrorCode.NOTE_NOT_FOUND));
        // 获取最后一个Full版本
        Optional<NoteVersionEntity> latestFullVersionEntity = noteVersionRepository
                .findFirstByResourceIdAndTypeOrderByVersionDesc(resourceId, VersionType.FULL);
        // 完整版本的版本号
        Long latestFullVersion = latestFullVersionEntity.map(NoteVersionEntity::getVersion).orElse(0L);
        // 获取在该 Full 版本之后的所有 DELTA 版本
        List<NoteVersionEntity> deltaVersionEntityList = noteVersionRepository
                .findByResourceIdAndVersionGreaterThanAndTypeOrderByVersionAsc(resourceId, latestFullVersion, VersionType.DELTA);
        // 处理增量数据
        List<String> deltasBase64 = deltaVersionEntityList.stream()
                .map(e -> Base64.getEncoder().encodeToString(e.getData().getData()))
                .toList();
        // 获取当前版本号
        Long currentVersion = latestFullVersion;
        if (!deltaVersionEntityList.isEmpty()) {
            currentVersion = deltaVersionEntityList.getLast().getVersion();
        }
        // 处理全量数据
        String fullSnapshotBase64 = latestFullVersionEntity
                .map(e -> Base64.getEncoder().encodeToString(e.getData().getData()))
                .orElse(null);
        return NoteSnapshotResponse.builder()
                .resourceId(resourceId)
                .fullSnapshot(fullSnapshotBase64)
                .version(currentVersion)
                .deltas(deltasBase64.isEmpty() ? null : deltasBase64)
                .build();
    }

    @Override
    public void deleteAllVersionsByResourceIds(List<String> resourceIds) {
        noteVersionRepository.deleteByResourceIdIn(resourceIds);
    }

    @Override
    public PageResult<NoteVersionListResponse> listVersions(String resourceId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<NoteVersionEntity> entityPage = noteVersionRepository.findByResourceIdOrderByVersionDesc(resourceId, pageable);

        PageResult<NoteVersionListResponse> pageResult = new PageResult<>(entityPage.getTotalElements(), page, size);

        List<NoteVersionListResponse> responses = entityPage.getContent().stream().map(entity -> {
            NoteVersionListResponse response = new NoteVersionListResponse();
            BeanUtils.copyProperties(entity, response);
            return response;
        }).toList();

        pageResult.addAll(responses);
        return pageResult;
    }
}
