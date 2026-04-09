package com.oriole.wisepen.file.storage.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StsTokenDTO implements Serializable {
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String bucket;
    private String region;
    private LocalDateTime expiration;
}