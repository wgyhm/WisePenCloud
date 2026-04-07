package com.oriole.wisepen.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文档服务启动类
 */
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
@EnableFeignClients(basePackages = "com.oriole.wisepen")
@EnableMongoAuditing
public class DocumentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentApplication.class, args);
    }
}
