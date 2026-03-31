package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.oriole.wisepen.common.core.domain.PageResult;
import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.common.core.domain.enums.list.SortDirectionEnum;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.ResourceConstants;
import com.oriole.wisepen.resource.domain.GroupAcl;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.dto.*;
import com.oriole.wisepen.resource.domain.dto.req.ResourceRenameRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceUpdateActionPermissionRequest;
import com.oriole.wisepen.resource.domain.dto.req.ResourceUpdateTagsRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceItemResponse;
import com.oriole.wisepen.resource.domain.entity.GroupResConfigEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.domain.entity.TagEntity;
import com.oriole.wisepen.resource.enums.ResourceAccessRole;
import com.oriole.wisepen.resource.enums.ResourceAction;
import com.oriole.wisepen.resource.enums.ResourceSortBy;
import com.oriole.wisepen.resource.enums.VisibilityMode;
import com.oriole.wisepen.resource.exception.ResPermissionErrorCode;
import com.oriole.wisepen.resource.repository.CustomResourceItemRepository;
import com.oriole.wisepen.resource.repository.GroupResConfigRepository;
import com.oriole.wisepen.resource.repository.ResourceItemRepository;
import com.oriole.wisepen.resource.repository.TagRepository;
import com.oriole.wisepen.resource.enums.FileOrganizationLogic;
import com.oriole.wisepen.resource.service.IAclEventPublisher;
import com.oriole.wisepen.resource.service.IGroupResService;
import com.oriole.wisepen.resource.service.IResourceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements IResourceService {

    private static final String RESOURCE_TRASH_COLLECTION = "wisepen_resource_trash";

    private final TagRepository tagRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final CustomResourceItemRepository customResourceItemRepository;
    private final GroupResConfigRepository groupResConfigRepository;
    private final IAclEventPublisher aclEventPublisher;
    private final MongoTemplate mongoTemplate;
    private final IGroupResService groupResService;

    @Override
    public void assertResourceOwner(String resourceId, String userId) {
        ResourceItemEntity entity = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));
        if (!userId.equals(entity.getOwnerId())) {
            throw new ServiceException(ResPermissionErrorCode.RESOURCE_PERMISSION_DENIED);
        }
    }

    @Override
    public void renameResource(ResourceRenameRequest req) {
        ResourceItemEntity entity = resourceItemRepository.findById(req.getResourceId())
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));

        entity.setResourceName(req.getNewName());
        entity.setUpdateTime(new Date());
        resourceItemRepository.save(entity);
    }

    @Override
    public void updateResourceTags(ResourceUpdateTagsRequest req) {
        String resourceId = req.getResourceId();
        String groupId = req.getGroupId();
        List<String> tagIds = req.getTagIds();

        ResourceItemEntity entity = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));

        entity.setUpdateTime(new Date());
        List<GroupTagBind> groupBinds = entity.getGroupBinds();

        if (groupBinds == null) {
            groupBinds = new ArrayList<>();
            entity.setGroupBinds(groupBinds);
        }

        if (tagIds == null || tagIds.isEmpty()) {
            // 本次操作清空了该组所有标签，从列表中移除该组
            groupBinds.removeIf(bind -> bind.getGroupId().equals(groupId));
        } else {
            // 执行常规的覆盖 (Update)/新增 (Upsert) 操作

            // 检查Tag是否存在
            List<TagEntity> validTags = (List<TagEntity>) tagRepository.findAllById(req.getTagIds());
            if (validTags.size() != req.getTagIds().size()) {
                throw new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND); // 包含无效的标签ID
            }
            boolean allBelongToGroup = validTags.stream().allMatch(tag -> groupId.equals(tag.getGroupId()));
            if (!allBelongToGroup) {
                throw new ServiceException(ResPermissionErrorCode.TAG_NOT_FOUND); // 包含无效的标签ID（实际上是跨空间越权挂载，但不返回真实原因）
            }

            // FOLDER 模式：同一小组内每个资源至多挂载一个标签
            if (!groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
                FileOrganizationLogic logic = groupResService.getFileOrgLogic(groupId);
                if (FileOrganizationLogic.FOLDER == logic && tagIds.size() > 1) {
                    throw new ServiceException(ResPermissionErrorCode.FOLDER_MODE_ONLY_ONE_TAG);
                }
            }

            // 寻找该实体中是否已经存在当前 groupId 的绑定记录
            boolean groupFound = false;
            for (GroupTagBind groupBind : groupBinds) {
                if (groupBind.getGroupId().equals(groupId)) {
                    // 找到对应的组，直接覆盖该组的 tagIds
                    groupBind.setTagIds(tagIds);
                    groupFound = true;
                    break;
                }
            }
            // 如果之前这个资源没有在这个组下绑过标签，则新增一个组绑定对象
            if (!groupFound) {
                entity.getGroupBinds().add(GroupTagBind.builder().groupId(groupId).tagIds(tagIds).build());
            }
        }
        resourceItemRepository.save(entity);
        if (groupId != null && groupId.startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
            return; // 个人Tag变更不需要重新计算Acl
        }
        this.calculateResourceAcl(entity.getResourceId());
    }

    @Override
    public void updateResourceActionPermission(ResourceUpdateActionPermissionRequest req){
        ResourceItemEntity entity = resourceItemRepository.findById(req.getResourceId())
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));

        // 前端传 null 代表清空覆盖规则，走默认群组标签规则 (下同)
        if (req.getOverrideGrantedActions() != null) {
            entity.setOverrideGrantedActionsMask(ResourceAction.actionsToPermissionCode(req.getOverrideGrantedActions()));
        } else {
            entity.setOverrideGrantedActionsMask(null);
        }

        if (req.getSpecifiedUsersGrantedActions() != null) {
            Map<String, Integer> specifiedMaskMap = new HashMap<>();
            req.getSpecifiedUsersGrantedActions().forEach((uid, actionsList) -> {
                specifiedMaskMap.put(uid, ResourceAction.actionsToPermissionCode(actionsList));
            });
            entity.setSpecifiedUsersGrantedActionsMask(specifiedMaskMap);
        } else {
            entity.setSpecifiedUsersGrantedActionsMask(null);
        }

        entity.setUpdateTime(new Date());
        resourceItemRepository.save(entity);
    }

    @Override
    public ResourceItemResponse getResourceInfo(String resourceId, String userId, Map<Long, GroupRoleType> groupRoles) {
        ResourceItemEntity entity = resourceItemRepository.findById(resourceId)
                .orElseThrow(() -> new ServiceException(ResPermissionErrorCode.RESOURCE_NOT_FOUND));

        // 预计算 ACL 快速鉴权 (拦截非法越权访问)
        boolean canView = false;

        // 如果用户被资源级的“指定用户特权”单独授权了 VIEW 动作
        Integer userMask = entity.getSpecifiedUsersGrantedActionsMask() == null ? null: entity.getSpecifiedUsersGrantedActionsMask().get(userId);

        if (userId.equals(entity.getOwnerId())) {
            // 所有者直接放行
            canView = true;
        } else if (userMask != null && ResourceAction.hasAction(userMask, ResourceAction.VIEW)) {
            // 如果用户被资源级的“指定用户特权”单独授权了 VIEW 动作
            canView = true;
        } else if (groupRoles != null && !groupRoles.isEmpty() && entity.getComputedAcls() != null) {
            // 遍历预计算的 ACL 列表
            for (GroupAcl acl : entity.getComputedAcls()) {
                Long groupId = Long.valueOf(acl.getGroupId());
                if (!groupRoles.containsKey(groupId)) continue;

                GroupRoleType userRole = groupRoles.get(groupId);
                // 群组管理员/所有者直接放行
                if (userRole == GroupRoleType.ADMIN || userRole == GroupRoleType.OWNER) {
                    canView = true;
                    break;
                }

                // 校验基础可见性
                boolean hasReadAuth = (acl.getVisibilityMode() == VisibilityMode.ALL ||
                        (acl.getVisibilityMode() == VisibilityMode.WHITELIST && acl.getSpecifiedUsers().contains(userId)) ||
                        (acl.getVisibilityMode() == VisibilityMode.BLACKLIST && !acl.getSpecifiedUsers().contains(userId)));

                if (hasReadAuth) {
                    canView = true;
                    break;
                }
            }
        }

        // 彻底无权限，抛出异常阻断
        if (!canView) {
            throw new ServiceException(ResPermissionErrorCode.RESOURCE_PERMISSION_DENIED);
        }

        // 组装响应数据
        ResourceItemResponse resp = new ResourceItemResponse();
        BeanUtil.copyProperties(entity, resp);

        // 处理标签回显
        List<String> allTagIds = extractRelevantTagIds(entity, null);
        Map<String, String> tagMap = new HashMap<>();
        if (!allTagIds.isEmpty()) {
            Iterable<TagEntity> tagEntities = tagRepository.findAllById(allTagIds);
            for (TagEntity tag : tagEntities) {
                tagMap.put(tag.getTagId(), tag.getTagName());
            }
        }
        resp.setCurrentTags(tagMap);

        if (userId.equals(entity.getOwnerId())) {
            // 处理权限掩码解包
            if (entity.getOverrideGrantedActionsMask() != null) {
                resp.setOverrideGrantedActions(ResourceAction.permissionCodeToActions(entity.getOverrideGrantedActionsMask()));
            }

            if (entity.getSpecifiedUsersGrantedActionsMask() != null) {
                Map<String, List<ResourceAction>> userActionsMap = new HashMap<>();
                entity.getSpecifiedUsersGrantedActionsMask().forEach((uid, mask) -> {
                    userActionsMap.put(uid, ResourceAction.permissionCodeToActions(mask));
                });
                resp.setSpecifiedUsersGrantedActions(userActionsMap);
            }
        }
        return resp;
    }

    @Override
    public PageResult<ResourceItemResponse> listResources(String currentUserId,
                                                          String groupId, GroupRoleType userGroupRole,
                                                          List<String> tagIds, QueryLogicEnum tagQueryLogicMode,
                                                          String resourceType, int page, int size,
                                                          ResourceSortBy sortBy, SortDirectionEnum sortDir) {

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(sortDir.toSpringDirection(), sortBy.getDbField()));

        Page<ResourceItemEntity> entityPage = customResourceItemRepository.findAccessibleResources(
                currentUserId, groupId, userGroupRole, tagIds, tagQueryLogicMode, resourceType, pageable);

        // 批量获取当前页用到的所有 Tag 名称
        Set<String> allTagIdsToFetch = new HashSet<>();
        Map<String, List<String>> resourceTagIdsMap = new HashMap<>(); // 缓存 ResourceId -> TagIds

        for (ResourceItemEntity entity : entityPage.getContent()) {
            List<String> extractedTagIds = extractRelevantTagIds(entity, groupId);
            resourceTagIdsMap.put(entity.getResourceId(), extractedTagIds);
            allTagIdsToFetch.addAll(extractedTagIds);
        }

        Map<String, String> tagIdNameMap = new HashMap<>();
        if (!allTagIdsToFetch.isEmpty()) {
            Iterable<TagEntity> tagEntities = tagRepository.findAllById(allTagIdsToFetch);
            for (TagEntity tag : tagEntities) {
                tagIdNameMap.put(tag.getTagId(), tag.getTagName());
            }
        }

        List<ResourceItemResponse> responses = entityPage.getContent().stream().map(entity -> {
            ResourceItemResponse resp = new ResourceItemResponse();
            BeanUtil.copyProperties(entity, resp);

            List<String> myTagIds = resourceTagIdsMap.get(entity.getResourceId());

            // 直接转为 Map<String, String>
            Map<String, String> tagMap = new HashMap<>();
            if (myTagIds != null) {
                for (String id : myTagIds) {
                    tagMap.put(id, tagIdNameMap.getOrDefault(id, "未知标签"));
                }
            }
            resp.setCurrentTags(tagMap);

            return resp;
        }).collect(Collectors.toList());

        PageResult<ResourceItemResponse> pageResult = new PageResult<>(entityPage.getTotalElements(), page, size);
        pageResult.addAll(responses);
        return pageResult;
    }

    // 辅助方法：提取资源的 TagId 供前端回显
    private List<String> extractRelevantTagIds(ResourceItemEntity entity, String groupId) {
        if (entity.getGroupBinds() == null) {
            return Collections.emptyList();
        }

        if (StringUtils.hasText(groupId)) {
            // 如果处于某个特定的小组内，只关心该文件在这个小组下挂了什么标签
            return entity.getGroupBinds().stream()
                    .filter(bind -> bind.getGroupId().equals(groupId))
                    .findFirst()
                    .map(GroupTagBind::getTagIds)
                    .orElse(Collections.emptyList());
        } else {
            // 否则提取该文件在所有空间下挂载的全部标签，并去重
            return entity.getGroupBinds().stream()
                    .map(GroupTagBind::getTagIds)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    @Override
    public String createResourceItem(ResourceCreateReqDTO dto) {
        ResourceItemEntity entity = new ResourceItemEntity();
        BeanUtil.copyProperties(dto, entity);

        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());

        resourceItemRepository.save(entity);
        return entity.getResourceId();
    }

    @Override
    public void softRemoveResourceItem(String resourceId) {
        resourceItemRepository.findById(resourceId).ifPresent(entity -> {
            // 插入到回收站集合中
            mongoTemplate.save(entity, RESOURCE_TRASH_COLLECTION);
            resourceItemRepository.deleteById(resourceId);
        });
    }

    @Override
    public void updateResourceAttributes(ResourceUpdateReqDTO dto) {
        resourceItemRepository.findById(dto.getResourceId()).ifPresent(entity -> {
            BeanUtil.copyProperties(dto, entity, CopyOptions.create().ignoreNullValue());
            entity.setUpdateTime(new Date());
            resourceItemRepository.save(entity);
        });
    }

    @Override
    public void afterTagNodeChanged(List<String> changedTagIds) {
        if (changedTagIds == null || changedTagIds.isEmpty())
            return;

        // 查询所有涉及的资源绑定记录
        List<ResourceItemEntity> affectedBinds = resourceItemRepository.findByTagIdsIn(changedTagIds);

        // 循环触发权限预计算
        // 个人Tag不能设置权限，无法触发afterTagNodeChanged，因此无需特意排除
        for (ResourceItemEntity bind : affectedBinds) {
            aclEventPublisher.publishRecalculateEvent(bind.getResourceId(), "TAG_CHANGED");
        }
    }

    @Override
    public void afterTagNodeDeleted(List<String> deletedTagIds, Boolean isPersonalTag) {
        if (deletedTagIds == null || deletedTagIds.isEmpty()) return;

        // 查询所有涉及的资源绑定记录
        List<ResourceItemEntity> affectedBinds = resourceItemRepository.findByTagIdsIn(deletedTagIds);

        for (ResourceItemEntity entity : affectedBinds) {
            if (entity.getGroupBinds() != null) {
                Iterator<GroupTagBind> iterator = entity.getGroupBinds().iterator();
                while (iterator.hasNext()) {
                    GroupTagBind groupBind = iterator.next();

                    // 移除掉已经被删除的 Tag ID
                    if (groupBind.getTagIds() != null) {
                        groupBind.getTagIds().removeAll(deletedTagIds);
                    }

                    // 如果移除后，该组下没有任何 Tag 了，清理空组
                    if (groupBind.getTagIds() == null || groupBind.getTagIds().isEmpty()) {
                        iterator.remove();
                    }
                }
            }
            resourceItemRepository.save(entity);
            if (isPersonalTag) {
                continue; // 个人Tag变更不需要重新计算Acl
            }
            aclEventPublisher.publishRecalculateEvent(entity.getResourceId(), "TAG_DELETED");
        }
    }

    @Override
    public void calculateResourceAcl(String resourceId) {
        // 获取资源绑定记录
        ResourceItemEntity bindEntity = resourceItemRepository.findByResourceId(resourceId)
                .orElse(null);

        List<GroupAcl> finalGroupAcls = new ArrayList<>();

        if (bindEntity != null && bindEntity.getGroupBinds() != null && !bindEntity.getGroupBinds().isEmpty()) {
            for (GroupTagBind groupBind : bindEntity.getGroupBinds()) {
                if (groupBind.getTagIds() == null || groupBind.getTagIds().isEmpty()) {
                    continue;
                }
                if (groupBind.getGroupId().startsWith(ResourceConstants.PERSONAL_GROUP_PREFIX)) {
                    continue; // 个人Tag不参与计算Acl
                }
                // 提取首标并查询
                String primaryTagId = groupBind.getTagIds().getFirst();
                TagEntity primaryTag = tagRepository.findById(primaryTagId).orElse(null);

                if (primaryTag == null) continue;

                // 双维度溯源：可见性和动作权限分别从最近的有配置祖先节点捕获
                // 加载当前组身份基线
                Integer defaultActions = groupResConfigRepository.findByGroupId(groupBind.getGroupId())
                        .map(GroupResConfigEntity::getDefaultMemberActionsMask)
                        .orElse(ResourceAction.DEFAULT_MEMBER_ACTIONS);
                ResolvedTagPermission resolved = resolveTagPermission(primaryTag, defaultActions);

                GroupAcl acl = GroupAcl.builder().groupId(groupBind.getGroupId()).build();
                BeanUtil.copyProperties(resolved, acl);
                finalGroupAcls.add(acl);
            }
        }

        Query query = new Query(Criteria.where("_id").is(resourceId));
        Update update = new Update()
                .set("computedAcls", finalGroupAcls)
                .set("updateTime", new Date());
        mongoTemplate.updateFirst(query, update, ResourceItemEntity.class);
    }

    @Override
    public ResourceCheckPermissionResDTO checkPermission(ResourceCheckPermissionReqDTO dto) {
        // 如果资源不存在（或已进入回收站），直接拒绝
        ResourceItemEntity entity = resourceItemRepository.findById(dto.getResourceId()).orElse(null);
        if (entity == null) {
            return new ResourceCheckPermissionResDTO(ResourceAccessRole.NONE);
        }
        // 资源所有者有全部权限
        if (dto.getUserId().equals(entity.getOwnerId())) {
            return new ResourceCheckPermissionResDTO(ResourceAccessRole.OWNER, null,
                    ResourceAction.permissionCodeToActions(ResourceAction.ALL_ACTIONS));
        }
        // 提前提取用户定向特权掩码
        Integer userMask = entity.getSpecifiedUsersGrantedActionsMask() == null ? null :
                entity.getSpecifiedUsersGrantedActionsMask().get(dto.getUserId());
        // 判断是否缺乏群组上下文（用户不在任何组 或 资源不在任何组）
        boolean noGroupContext = (dto.getGroupRoles() == null || dto.getGroupRoles().isEmpty()) ||
                (entity.getGroupBinds() == null || entity.getGroupBinds().isEmpty());
        // 如果既没有群组上下文，也没有被单独赋予特权，直接拒绝
        if (noGroupContext && userMask == null) {
            return new ResourceCheckPermissionResDTO(ResourceAccessRole.NONE);
        }
        ResourceAccessRole resourceAccessRole = ResourceAccessRole.NONE;
        Integer actionsMask = 0;
        Set<String> permissionSources = new HashSet<>();

        // 计算群组权限 (如果有群组上下文的话)
        if (!noGroupContext) {
            // 遍历资源绑定的所有组
            for (GroupTagBind groupBind : entity.getGroupBinds()) {
                Long groupId = Long.valueOf(groupBind.getGroupId());

                if (!dto.getGroupRoles().containsKey(groupId)) { // 用户不在该组，跳过
                    continue;
                }
                GroupRoleType userRoleInThisGroup = dto.getGroupRoles().get(groupId);

                // 用户是组管理员/拥有者，有全部权限
                if (userRoleInThisGroup == GroupRoleType.ADMIN || userRoleInThisGroup == GroupRoleType.OWNER) {
                    return new ResourceCheckPermissionResDTO(ResourceAccessRole.GROUP_ADMIN,
                            new HashSet<>(Collections.singleton(groupBind.getGroupId())),
                            ResourceAction.permissionCodeToActions(ResourceAction.ALL_ACTIONS));
                }

                // 提取首标并查询
                String primaryTagId = groupBind.getTagIds().getFirst();
                // 查询首标
                TagEntity primaryTag = tagRepository.findById(primaryTagId).orElse(null);

                // 可见性和动作权限分别从最近的有配置祖先节点捕获
                // 加载当前组身份基线
                Integer defaultActions = groupResConfigRepository.findByGroupId(groupBind.getGroupId())
                        .map(GroupResConfigEntity::getDefaultMemberActionsMask)
                        .orElse(ResourceAction.DEFAULT_MEMBER_ACTIONS);

                ResolvedTagPermission resolved = resolveTagPermission(primaryTag, defaultActions);

                // 判断是否有当前组的阅读权限
                boolean hasReadAuth = (resolved.visibilityMode == VisibilityMode.ALL ||
                        (resolved.visibilityMode == VisibilityMode.WHITELIST && resolved.specifiedUsers.contains(dto.getUserId())) ||
                        (resolved.visibilityMode == VisibilityMode.BLACKLIST && !resolved.specifiedUsers.contains(dto.getUserId())));

                if (hasReadAuth) {
                    // 只要有一个组能看，基础身份就是 Member
                    if (resourceAccessRole == ResourceAccessRole.NONE) {
                        resourceAccessRole = ResourceAccessRole.GROUP_MEMBER;
                    }
                    permissionSources.add(groupBind.getGroupId());
                    // 应用标签策略
                    actionsMask |= resolved.grantedActionsMask;
                }
            }
        }

        // 如果群组层面没有权限，且个人特权里没有 VIEW (阅读) 权限，则彻底拒绝
        if (resourceAccessRole == ResourceAccessRole.NONE && (userMask == null || !ResourceAction.hasAction(userMask, ResourceAction.VIEW))) {
            return new ResourceCheckPermissionResDTO(ResourceAccessRole.NONE);
        }

        // 应用资源级权限覆盖策略
        // 优先级：定向用户特权 (userMask) > 全局覆盖 (Override) > 群组策略 (actionsMask)
        actionsMask = entity.getOverrideGrantedActionsMask() == null ? actionsMask : entity.getOverrideGrantedActionsMask();

        if (userMask != null) {
            resourceAccessRole = ResourceAccessRole.OWNER_SPECIFIED;
            actionsMask = userMask;
            permissionSources.clear();
        }

        return new ResourceCheckPermissionResDTO(resourceAccessRole, permissionSources, ResourceAction.permissionCodeToActions(actionsMask));
    }

    /**
     * 标签权限溯源的聚合结果
     * 可见性（visibilityMode + specifiedUsers）和动作权限（grantedActions）
     * 是两个独立维度，可能来自标签树中不同层级的祖先节点
     */
    @Data
    private static class ResolvedTagPermission {
        VisibilityMode visibilityMode;
        List<String> specifiedUsers = Collections.emptyList();
        Integer grantedActionsMask;

        boolean isVisibilityResolved() {
            return visibilityMode != null;
        }

        boolean isActionsResolved() {
            return grantedActionsMask != null;
        }

        boolean isFullyResolved() {
            return isVisibilityResolved() && isActionsResolved();
        }
    }

    /**
     * 单次向上遍历标签树，分别捕获可见性和动作权限两个维度最近的非空配置
     */
    private ResolvedTagPermission resolveTagPermission(TagEntity node, Integer defaultActions) {
        ResolvedTagPermission result = new ResolvedTagPermission();

        // 先尝试从当前节点捕获
        capturePermission(result, node);

        // 如果当前节点未能完全解析，且存在祖先节点，则向上溯源
        if (!result.isFullyResolved()) {

            List<String> ancestors = node.getAncestors();
            if (ancestors != null && !ancestors.isEmpty()) {

                // 批量查询祖先节点
                Iterable<TagEntity> ancestorEntities = tagRepository.findAllById(ancestors);

                Map<String, TagEntity> ancestorMap = new HashMap<>();
                for (TagEntity entity : ancestorEntities) {
                    ancestorMap.put(entity.getTagId(), entity); // 建立TagID与实体的映射
                }

                for (int i = ancestors.size() - 1; i >= 0; i--) { // 从 ancestors 列表的最后一个元素开始往回遍历到第一个元素
                    TagEntity ancestorNode = ancestorMap.get(ancestors.get(i));
                    if (ancestorNode != null) {
                        capturePermission(result, ancestorNode);
                        if (result.isFullyResolved())
                            break; // 如果均捕获到，直接返回
                    }
                }
            }
        }

        // 无论是根节点本身没配权限，还是遍历完所有祖先都没找到配置，都会在这里被安全拦截
        // 如果未解析到动作权限，则使用默认动作权限
        if (!result.isActionsResolved()) {
            result.grantedActionsMask = defaultActions;
        }
        // 如果未解析到可见性，则使用默认可见性（ALL）
        if (!result.isVisibilityResolved()) {
            result.visibilityMode = VisibilityMode.ALL;
        }

        return result;
    }

    /**
     * 将节点的权限配置按维度填入聚合结果，仅填充尚未解析的维度
     */
    private void capturePermission(ResolvedTagPermission result, TagEntity node) {
        if (!result.isVisibilityResolved() && node.getVisibilityMode() != null) {
            result.visibilityMode = node.getVisibilityMode();
            result.specifiedUsers = node.getSpecifiedUsers() != null ? node.getSpecifiedUsers()
                    : Collections.emptyList();
        }
        if (!result.isActionsResolved() && (node.getGrantedActionsMask() != null)) {
            result.grantedActionsMask = node.getGrantedActionsMask();
        }
    }
}