package com.oriole.wisepen.file.storage.strategy.aliyun;

import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.sts20150401.Client;
import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.file.storage.api.domain.base.StorageRecordBase;
import com.oriole.wisepen.file.storage.api.domain.base.UploadUrlBase;
import com.oriole.wisepen.file.storage.api.domain.dto.StorageRecordDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.StsTokenDTO;
import com.oriole.wisepen.file.storage.api.domain.dto.UploadInitRespDTO;
import com.oriole.wisepen.file.storage.domain.entity.StorageConfigEntity;
import com.oriole.wisepen.file.storage.exception.StorageErrorCode;
import com.oriole.wisepen.file.storage.strategy.StorageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Scanner;

@Slf4j
public class AliyunOssProvider implements StorageProvider {

    private final StorageConfigEntity config;
    private final OSS ossClient;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 构造器注入配置并初始化连接
    public AliyunOssProvider(StorageConfigEntity config) {
        this.config = config;
        this.ossClient = new OSSClientBuilder().build(
                config.getEndpoint(),
                config.getAccessKeyId(),
                config.getAccessKeySecret()
        );
        log.info("Aliyun OSS Client initialized for ConfigId: [{}] - {}", config.getId(), config.getName());
    }

    @Override
    public Long getConfigId() {
        return config.getId();
    }

    @Override
    public String getDomain() {
        return config.getDomain();
    }

    @Override
    public UploadUrlBase generateUploadTicket(String objectKey, long durationSeconds, String apiDomain) {
        Date expirationDate = Date.from(Instant.now().plusSeconds(durationSeconds));
        String callbackUrl = apiDomain.replaceAll("/+$", "") + "/external/storage/callback/upload";

        try {
            String callbackBody = "objectKey=${object}&size=${size}&md5=${etag}";
            Map<String, String> outerPolicyMap = Map.of(
                    "callbackUrl", callbackUrl,
                    "callbackBody", callbackBody,
                    "callbackBodyType", "application/x-www-form-urlencoded"
            );
            String callbackJson = OBJECT_MAPPER.writeValueAsString(outerPolicyMap);
            String callbackBase64 = BinaryUtil.toBase64String(callbackJson.getBytes(StandardCharsets.UTF_8));

            // 生成 PUT 方法的预签名 URL，供前端直传
            GeneratePresignedUrlRequest request =
                    new com.aliyun.oss.model.GeneratePresignedUrlRequest(config.getBucketName(), objectKey, HttpMethod.PUT);
            request.setExpiration(expirationDate);
            request.setContentType("application/octet-stream");
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("x-oss-callback", callbackBase64);
            request.setHeaders(headers);

            URL url = ossClient.generatePresignedUrl(request);

            return UploadInitRespDTO.builder()
                    .putUrl(url.toString())
                    .callbackHeader(callbackBase64)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("构建 OSS 回调策略 JSON 失败", e);
            throw new ServiceException(StorageErrorCode.CALLBACK_POLICY_GENERATE_FAILED);
        }
    }

    @Override
    public String generateDownloadUrl(String objectKey, long durationSeconds) {
        try {
            Date expirationDate = Date.from(Instant.now().plusSeconds(durationSeconds));
            // 生成 GET 方法的预签名 URL，供私有文件下载
            URL url = ossClient.generatePresignedUrl(config.getBucketName(), objectKey, expirationDate, HttpMethod.GET);
            return url.toString();
        } catch (Exception e) {
            log.error("OSS Generate Presigned Url 失败: {}", objectKey, e);
            throw new ServiceException(StorageErrorCode.FILE_DOWNLOAD_ERROR);
        }
    }

    @Override
    public void copyObject(String sourceKey, String targetKey) {
        try {
            // 阿里云内部物理拷贝，实现极速秒传，不消耗服务器出入网带宽
            ossClient.copyObject(config.getBucketName(), sourceKey, config.getBucketName(), targetKey);
        } catch (Exception e) {
            log.error("OSS CopyObject 失败: source={}, target={}", sourceKey, targetKey, e);
            throw new ServiceException(StorageErrorCode.FILE_COPY_ERROR);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            ossClient.deleteObject(config.getBucketName(), objectKey);
        } catch (Exception e) {
            log.error("OSS DeleteObject 失败: {}", objectKey, e);
            throw new ServiceException(StorageErrorCode.FILE_DELETE_ERROR);
        }
    }

    @Override
    public void uploadSmallFile(MultipartFile file, String objectKey) {
        try (InputStream inputStream = file.getInputStream()) {
            // 代理直传，适合图床等轻量级场景
            ossClient.putObject(config.getBucketName(), objectKey, inputStream);
        } catch (Exception e) {
            log.error("OSS PutObject (SmallFile) 失败: {}", objectKey, e);
            throw new ServiceException(StorageErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    @Override
    public StsTokenDTO getStsToken(String pathPrefix, long durationSeconds) {
        try {
            // 初始化 STS Client
            Config stsConfig = new Config()
                    .setAccessKeyId(config.getAccessKeyId())
                    .setAccessKeySecret(config.getAccessKeySecret())
                    .setEndpoint("sts.aliyuncs.com");
            Client stsClient = new Client(stsConfig);

            // 将权限限制在当前 Bucket 的指定前缀下，只给 GetObject 权限
            String policy = """
                {
                    "Version": "1",
                    "Statement": [
                        {
                            "Effect": "Allow",
                            "Action": ["oss:GetObject"],
                            "Resource": ["acs:oss:*:*:%s/%s"]
                        }
                    ]
                }
                """.formatted(config.getBucketName(), pathPrefix);

            // 构建请求
            AssumeRoleRequest request = new AssumeRoleRequest()
                    .setRoleArn(config.getRoleArn()) // 从数据库动态获取
                    .setRoleSessionName("wisepen-" + IdUtil.fastSimpleUUID())
                    .setDurationSeconds(durationSeconds)
                    .setPolicy(policy);

            // 发起调用获取 Token
            AssumeRoleResponse response = stsClient.assumeRole(request);

            return StsTokenDTO.builder()
                    .accessKeyId(response.getBody().getCredentials().getAccessKeyId())
                    .accessKeySecret(response.getBody().getCredentials().getAccessKeySecret())
                    .securityToken(response.getBody().getCredentials().getSecurityToken())
                    .expiration(LocalDateTime.now().plusSeconds(durationSeconds))
                    .region(config.getRegion()).bucket(config.getBucketName())
                    .build();

        } catch (Exception e) {
            log.error("生成 STS Token 失败: pathPrefix={}, configId={}", pathPrefix, config.getId(), e);
            throw new ServiceException(StorageErrorCode.STS_TOKEN_GENERATE_FAILED);
        }
    }

    @Override
    public boolean verifyCallbackSignature(HttpServletRequest request, String rawBody) {
        // 阿里云 OSS 回调鉴权逻辑 (利用 RSA 校验 Authorization 头)
        try {
            String autorizationInput = request.getHeader("authorization");
            String pubKeyInput = request.getHeader("x-oss-pub-key-url");

            if (autorizationInput == null || pubKeyInput == null) {
                return false;
            }

            // 获取公钥并解码
            byte[] pubKeyBytes = BinaryUtil.fromBase64String(pubKeyInput);
            String pubKeyAddr = new String(pubKeyBytes);
            // 确保公钥确实来自阿里云
            if (!pubKeyAddr.startsWith("https://gosspublic.alicdn.com/")
                    && !pubKeyAddr.startsWith("http://gosspublic.alicdn.com/")) {
                return false;
            }
            String pubKeyStr = readNetworkStream(pubKeyAddr).replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\n", "");

            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(BinaryUtil.fromBase64String(pubKeyStr));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            // 拼对待签名字符串 (URI + QueryString + Body)
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String authStr = uri;
            if (queryString != null && !queryString.isEmpty()) {
                authStr += "?" + queryString;
            }
            authStr += "\n";
            authStr += rawBody;

            // 验证签名
            Signature signature = Signature.getInstance("MD5withRSA");
            signature.initVerify(publicKey);
            signature.update(authStr.getBytes());

            byte[] signBytes = BinaryUtil.fromBase64String(autorizationInput);
            return signature.verify(signBytes);

        } catch (Exception e) {
            log.error("OSS 回调签名验证异常", e);
            return false;
        }
    }

    @Override
    public StorageRecordBase getObjectMetadata(String objectKey) {
        try {
            // 阿里云的 getObjectMetadata 是一个轻量级的 HEAD 请求，不拉取文件主体
            ObjectMetadata metadata = ossClient.getObjectMetadata(config.getBucketName(), objectKey);

            StorageRecordBase record = new StorageRecordBase();
            record.setObjectKey(objectKey);
            record.setSize(metadata.getContentLength());
            // 阿里云通常在 ETag 中存储文件的 MD5 (大文件分片上传除外，这里做个兼容处理)
            String eTag = metadata.getETag();
            if (eTag != null) {
                record.setMd5(eTag.replace("\"", "").toLowerCase());
            }
            return record;
        } catch (OSSException e) {
            // 如果是 404 NoSuchKey，说明用户根本没上传，返回 null
            if ("NoSuchKey".equals(e.getErrorCode())) {
                return null;
            }
            log.error("查询 OSS 元数据失败: {}", objectKey, e);
            throw new ServiceException(StorageErrorCode.FILE_READ_ERROR);
        }
    }

    @Override
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    // --- 辅助方法 ---
    private String readNetworkStream(String url) throws Exception {
        try (Scanner scanner = new Scanner(new URL(url).openStream(), "UTF-8")) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private String readStream(InputStream is) throws Exception {
        try (Scanner scanner = new Scanner(is, "UTF-8")) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}