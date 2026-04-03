package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.TagEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends MongoRepository<TagEntity, String> {

    // 获取某个用户组下的特定 Tag (通过ID)
    Optional<TagEntity> findByGroupIdAndTagId(String groupId, String tagId);

    // 获取某个用户组下的特定 Tags (通过IDs)
    List<TagEntity> findByGroupIdAndTagIdIn(String groupId, List<String> tagIds);

    // 获取某个用户组下的所有 Tag
    List<TagEntity> findByGroupId(String groupId);

    // 查询某个节点的所有子孙节点
    List<TagEntity> findByGroupIdAndAncestorsContaining(String groupId, String ancestorId);

    // 删除某个节点的所有子孙节点
    void deleteByGroupIdAndAncestorsContaining(String groupId, String ancestorId);

    // 删除某个用户组下的所有 Tag（小组解散时使用）
    void deleteByGroupId(String groupId);

    // 用于同级重名校验
    Optional<TagEntity> findByGroupIdAndParentIdAndTagName(String groupId, String parentId, String tagName);
}