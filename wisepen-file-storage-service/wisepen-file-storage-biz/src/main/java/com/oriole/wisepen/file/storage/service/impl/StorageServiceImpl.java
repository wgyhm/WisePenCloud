package com.oriole.wisepen.file.storage.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.base.StorageRecordBase;
import com.oriole.wisepen.file.storage.api.domain.base.UploadUrlBase;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitReqDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import com.oriole.wisepen.file.storage.api.enums.StorageSceneEnum;
import com.oriole.wisepen.file.storage.api.enums.StorageStatusEnum;
import com.oriole.wisepen.file.storage.config.StorageProperties;
import com.oriole.wisepen.file.storage.domain.entity.StorageRecordEntity;
import com.oriole.wisepen.file.storage.exception.StorageErrorCode;
import com.oriole.wisepen.file.storage.mapper.StorageRecordMapper;
import com.oriole.wisepen.file.storage.mq.KafkaStorageEventPublisher;
import com.oriole.wisepen.file.storage.service.IStorageService;
import com.oriole.wisepen.file.storage.strategy.StorageManager;
import com.oriole.wisepen.file.storage.strategy.StorageProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements IStorageService {

    private final StorageManager storageManager;
    private final StorageRecordMapper storageRecordMapper;
    private final KafkaStorageEventPublisher eventPublisher;
    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadInitRespDTO initUpload(UploadInitReqDTO req) {
        StorageProvider provider = storageManager.getProvider(req.getConfigId());

        // 查询库中是否存在同 MD5 且同配置区的文件 (秒传判定)
        StorageRecordEntity existRecord = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getMd5, req.getMd5())
                        .eq(StorageRecordEntity::getConfigId, provider.getConfigId())
                        .last("LIMIT 1")
        );

        String newObjectKey = buildObjectKey(req.getScene().getPrefix(), req.getBizTag(), req.getExtension());
        String domain = provider.getDomain();

        if (existRecord != null) {
            log.info("触发同源秒传: md5={}, 原文件={}, 新文件={}", req.getMd5(), existRecord.getObjectKey(), newObjectKey);
            // 触发 OSS 内部物理克隆
            try {
                provider.copyObject(existRecord.getObjectKey(), newObjectKey);
            }  catch (Exception e) {
                // 秒传异常，获取直传 PUT URL
                return this.getPutUrl(req.getScene(), newObjectKey, provider);
            }

            StorageRecordEntity newRecord = BeanUtil.copyProperties(existRecord, StorageRecordEntity.class,
                    "fileId", "createTime", "objectKey");
            newRecord.setObjectKey(newObjectKey);
            storageRecordMapper.insert(newRecord);

            FileUploadedMessage fileUploadedMessage = BeanUtil.copyProperties(newRecord, FileUploadedMessage.class);
            fileUploadedMessage.setDomain(provider.getDomain());
            fileUploadedMessage.setFlashUploaded(true);
            // 发送 Kafka 事件
            eventPublisher.publishFileUploadedEvent(fileUploadedMessage);

            return UploadInitRespDTO.builder()
                    .flashUploaded(true)
                    .domain(domain)
                    .objectKey(newObjectKey)
                    .build();
        }

        // 没命中秒传，获取直传 PUT URL
        return this.getPutUrl(req.getScene(), newObjectKey, provider);
    }

    private UploadInitRespDTO getPutUrl(StorageSceneEnum scene, String newObjectKey, StorageProvider provider) {
        StorageRecordEntity newRecord = StorageRecordEntity.builder()
                .scene(scene).objectKey(newObjectKey).status(StorageStatusEnum.UPLOADING).configId(provider.getConfigId())
                .build();
        storageRecordMapper.insert(newRecord);
        UploadUrlBase uploadUrl = provider.generateUploadTicket(newObjectKey, storageProperties.getDefaultTicketDuration(), storageProperties.getApiDomain());

        UploadInitRespDTO dto = UploadInitRespDTO.builder()
                .flashUploaded(false).objectKey(newObjectKey).domain(provider.getDomain())
                .build();
        BeanUtil.copyProperties(uploadUrl, dto, CopyOptions.create().ignoreNullValue());
        return dto;
    }

    @Override
    public StorageRecordDTO uploadSmallFileProxy(MultipartFile file, StorageSceneEnum scene, String bizTag) {
        if (file.getSize() > storageProperties.getMaxSmallFileSize()) {
            throw new ServiceException(StorageErrorCode.FILE_SIZE_EXCEEDED);
        }

        String extension = FileUtil.extName(file.getOriginalFilename()).toLowerCase();

        String objectKey = buildObjectKey(scene.getPrefix(), bizTag, extension);
        StorageProvider provider = storageManager.getPrimaryProvider();
        provider.uploadSmallFile(file, objectKey);

        StorageRecordEntity newRecord = StorageRecordEntity.builder()
                .scene(scene).objectKey(objectKey).size(file.getSize()).configId(provider.getConfigId()).status(StorageStatusEnum.AVAILABLE)
                .build();
        storageRecordMapper.insert(newRecord);

        StorageRecordDTO dto = BeanUtil.copyProperties(newRecord, StorageRecordDTO.class);
        dto.setDomain(provider.getDomain());
        return dto;
    }

    @Override
    public String getDownloadUrl(String objectKey, Long durationSeconds) {
        StorageRecordEntity record = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getObjectKey, objectKey)
                        .ne(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
                        .last("LIMIT 1")
        );
        if (record == null) {
            throw new ServiceException(StorageErrorCode.RECORD_NOT_FOUND);
        }
        if (StorageStatusEnum.UPLOADING.equals(record.getStatus())) {
            StorageRecordDTO storageRecordDTO = this.compensateStatus(record);
            if (storageRecordDTO == null) {
                throw new ServiceException(StorageErrorCode.RECORD_NOT_FOUND);
            }
        }

        StorageProvider provider = storageManager.getProvider(record.getConfigId());
        return provider.generateDownloadUrl(objectKey, durationSeconds);
    }

    @Override
    public void deleteFiles(List<String> objectKeys) {
        int updateCount = storageRecordMapper.update(null,
                Wrappers.<StorageRecordEntity>lambdaUpdate()
                        .set(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
                        .in(StorageRecordEntity::getObjectKey, objectKeys)
                        .ne(StorageRecordEntity::getStatus, StorageStatusEnum.DELETED)
        );

        if (updateCount == 0) {
            throw new ServiceException(StorageErrorCode.RECORD_NOT_FOUND);
        }
        log.info("执行批量软删除成功，影响行数: {}, objectKeys: {}", updateCount, objectKeys);
    }

    @Override
    public StsTokenDTO getStsToken(StorageSceneEnum scene, String bizTag, Long configId, Long durationSeconds) {
        String cleanBizTag = StrUtil.isNotBlank(bizTag)
                ? bizTag.replaceAll("^/+", "").replaceAll("/+$", "")
                : "";
        String pathPrefix = scene.getPrefix();
        if (StrUtil.isNotBlank(cleanBizTag)) {
            pathPrefix += "/" + cleanBizTag;
        }
        pathPrefix += "/*"; // 加上通配符，授予该目录及子目录下的读取权限
        // 如果业务方不指定 configId，StorageManager 会自动降级使用 PrimaryProvider
        StorageProvider provider = storageManager.getProvider(configId);
        return provider.getStsToken(pathPrefix, durationSeconds);
    }

    @Override
    public void handleUploadCallback(HttpServletRequest request, String rawBody) {
        // 解析参数
        Map<String, String> paramMap = HttpUtil.decodeParamMap(rawBody, CharsetUtil.CHARSET_UTF_8);
        String objectKey = paramMap.get("objectKey");
        String md5 = paramMap.get("md5");
        Long size = paramMap.containsKey("size") ? Long.parseLong(paramMap.get("size")) : null;

        StorageRecordEntity record = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getObjectKey, objectKey)
                        .last("LIMIT 1")
        );

        StorageProvider provider = storageManager.getProvider(record.getConfigId());

        if (!provider.verifyCallbackSignature(request, rawBody)) {
            throw new ServiceException(StorageErrorCode.CALLBACK_SIGNATURE_INVALID);
        }

        if (StorageStatusEnum.AVAILABLE.equals(record.getStatus())) {
            log.info("文件已处理，忽略重复回调: {}", objectKey);
            return;
        }

        record.setMd5(md5);
        record.setSize(size);
        record.setStatus(StorageStatusEnum.AVAILABLE);
        storageRecordMapper.updateById(record);

        FileUploadedMessage fileUploadedMessage = BeanUtil.copyProperties(record, FileUploadedMessage.class);
        fileUploadedMessage.setDomain(provider.getDomain());
        fileUploadedMessage.setFlashUploaded(false); // 非秒传

        eventPublisher.publishFileUploadedEvent(fileUploadedMessage);
        log.info("云端直传文件回调落库: {}/{}", provider.getDomain(), objectKey);
    }

    private String buildObjectKey(String scenePrefix, String bizTag, String extension) {
        String cleanBizTag = StrUtil.isNotBlank(bizTag)
                ? bizTag.replaceAll("^/+", "").replaceAll("/+$", "")
                : DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
        String fileName = IdUtil.fastSimpleUUID() + "." + extension;

        // 场景后缀 + 时间戳 / 业务Tag + 文件名
        // 例如 public/images/user/2026/1/1/XXXX.png private/images/note/XXXX/XXXX.png
        return Stream.of(scenePrefix, cleanBizTag, fileName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("/"));
    }

    @Override
    public StorageRecordDTO getFileRecord(String objectKey) {

        StorageRecordEntity record = storageRecordMapper.selectOne(
                Wrappers.<StorageRecordEntity>lambdaQuery()
                        .eq(StorageRecordEntity::getObjectKey, objectKey)
                        .last("LIMIT 1")
        );

        if (record == null || StorageStatusEnum.DELETED.equals(record.getStatus())) {
            return null;
        }

        StorageProvider provider = storageManager.getProvider(record.getConfigId());
        if (StorageStatusEnum.AVAILABLE.equals(record.getStatus())) {
            StorageRecordDTO dto = BeanUtil.copyProperties(record, StorageRecordDTO.class);
            dto.setDomain(provider.getDomain());
            return dto;
        }
        return this.compensateStatus(record);
    }

    @Override
    public StorageRecordDTO compensateStatus(StorageRecordEntity record) {
        StorageProvider provider = storageManager.getProvider(record.getConfigId());
        log.warn("触发自愈补偿, 正在请求云厂商验证: {}/{}", provider.getDomain(), record.getObjectKey());

        StorageRecordBase cloudRecord = provider.getObjectMetadata(record.getObjectKey());
        if (cloudRecord != null) {
            // 云端找到说明回调丢失，进行数据补偿
            BeanUtil.copyProperties(cloudRecord, record);
            record.setStatus(StorageStatusEnum.AVAILABLE);
            storageRecordMapper.updateById(record);

            StorageRecordDTO dto = BeanUtil.copyProperties(record, StorageRecordDTO.class);
            dto.setDomain(provider.getDomain());

            // 补发 Kafka 消息
            FileUploadedMessage fileUploadedMessage = BeanUtil.copyProperties(record, FileUploadedMessage.class);
            fileUploadedMessage.setDomain(provider.getDomain());
            fileUploadedMessage.setFlashUploaded(false);

            eventPublisher.publishFileUploadedEvent(fileUploadedMessage);

            log.info("文件状态已更新, 本地处于 AVAILABLE: {}/{}", provider.getDomain(), record.getObjectKey());
            return dto;
        }
        return null;
    }
}