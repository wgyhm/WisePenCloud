package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.core.constant.CommonConstants;
import com.oriole.wisepen.common.core.constant.SecurityConstants;
import com.oriole.wisepen.common.core.context.GrayContextHolder;
import com.oriole.wisepen.common.web.interceptor.FeignRequestInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableFeignClients(basePackages = "com.oriole.wisepen")
public class FeignConfiguration {

    // 读取配置中的安全密钥
    @Value("${wisepen.security.from-source:APISIX-wX0iR6tY}")
    private String fromSource;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new FeignRequestInterceptor(fromSource);
    }
}