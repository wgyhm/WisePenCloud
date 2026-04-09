package com.oriole.wisepen.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.oriole.wisepen.common.web.interceptor.FeignRequestInterceptor;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

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

    private ObjectMapper feignObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        // 禁用将日期序列化为时间戳
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper;
    }

    @Bean
    public Encoder feignEncoder() {
        HttpMessageConverters converters = new HttpMessageConverters(
                new MappingJackson2HttpMessageConverter(feignObjectMapper())
        );
        ObjectFactory<HttpMessageConverters> factory = () -> converters;
        return new SpringEncoder(factory);
    }

    @Bean
    public Decoder feignDecoder() {
        HttpMessageConverters converters = new HttpMessageConverters(
                new MappingJackson2HttpMessageConverter(feignObjectMapper())
        );
        ObjectFactory<HttpMessageConverters> factory = () -> converters;
        return new ResponseEntityDecoder(new SpringDecoder(factory));
    }
}