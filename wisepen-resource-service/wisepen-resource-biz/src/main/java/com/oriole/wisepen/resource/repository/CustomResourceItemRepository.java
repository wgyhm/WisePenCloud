package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.common.core.domain.enums.GroupRoleType;
import com.oriole.wisepen.common.core.domain.enums.list.QueryLogicEnum;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.ResourceAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class CustomResourceItemRepository {

    private final MongoTemplate mongoTemplate;

    public CustomResourceItemRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // 核心分页查询
    public Page<ResourceItemEntity> findAccessibleResources(
            String userId, String groupId, GroupRoleType userGroupRole, List<String> tagIds, List<String> excludeTrashId, QueryLogicEnum tagQueryLogicMode,
            String resourceType, Pageable pageable) {

        Criteria criteria = new Criteria();

        if (excludeTrashId != null && !excludeTrashId.isEmpty()) {
            // MongoDB 语法：排除 groupBinds 数组中，groupId 匹配 且 tagIds 中包含回收站黑名单中任意一个 ID 的记录
            criteria.and("groupBinds").not().elemMatch(
                    Criteria.where("groupId").is(groupId).and("tagIds").in(excludeTrashId)
            );
        }

        // 资源类型过滤
        if (StringUtils.hasText(resourceType)) {
            criteria.and("resourceType").is(resourceType);
        }

        if (!StringUtils.hasText(groupId)) { // 个人检索
            // 个人模式不传 groupId，只要是自己创建的文件都属于个人空间
            criteria.and("ownerId").is(userId);
            if (tagIds != null && !tagIds.isEmpty()) {
                if (tagQueryLogicMode == QueryLogicEnum.AND) {
                    criteria.and("groupBinds.tagIds").all(tagIds);
                } else {
                    criteria.and("groupBinds.tagIds").in(tagIds);
                }
            }

        } else { // 小组检索
            Criteria groupBindCriteria = Criteria.where("groupId").is(groupId);
            if (tagIds != null && !tagIds.isEmpty()) {
                if (tagQueryLogicMode == QueryLogicEnum.AND) {
                    groupBindCriteria.and("tagIds").all(tagIds);
                } else {
                    groupBindCriteria.and("tagIds").in(tagIds);
                }
            }
            criteria.and("groupBinds").elemMatch(groupBindCriteria);
            if (userGroupRole != GroupRoleType.ADMIN && userGroupRole != GroupRoleType.OWNER) {
                int discoverCode = ResourceAction.DISCOVER.getCode();
                String aclPrefix = "computedGroupAcls." + groupId;
                Criteria aclCriteria = new Criteria().orOperator(
                        // 情况 A: 自己的文件 (免检)
                        Criteria.where("ownerId").is(userId),
                        // 情况 B: 资源级的定向用户特权 (包含 DISCOVER)
                        Criteria.where("specifiedUsersGrantedActionsMask." + userId).bits().allSet(discoverCode),
                        // 情况 C: 用户被分配了专属掩码，且掩码中包含 DISCOVER
                        Criteria.where(aclPrefix + ".userMasks." + userId).bits().allSet(discoverCode),
                        // 情况 D: 用户没有专属掩码，检查该组的 baseMask 是否包含 DISCOVER
                        new Criteria().andOperator(
                                Criteria.where(aclPrefix + ".userMasks." + userId).exists(false),
                                Criteria.where(aclPrefix + ".baseMask").bits().allSet(discoverCode)
                        )
                );
                criteria = new Criteria().andOperator(criteria, aclCriteria);
            }
        }

        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, ResourceItemEntity.class);

        query.with(pageable);
        List<ResourceItemEntity> list = mongoTemplate.find(query, ResourceItemEntity.class);

        return new PageImpl<>(list, pageable, total);
    }
}