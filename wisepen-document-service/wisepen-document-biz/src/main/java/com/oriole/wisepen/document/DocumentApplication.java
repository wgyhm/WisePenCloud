package com.oriole.wisepen.document;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文档服务启动类
 */
@EnableScheduling
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.oriole.wisepen.document.mapper")
@EnableFeignClients(basePackages = "com.oriole.wisepen")
public class DocumentApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentApplication.class, args);
    }
}
