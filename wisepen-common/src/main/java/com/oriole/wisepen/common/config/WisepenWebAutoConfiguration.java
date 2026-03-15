package com.oriole.wisepen.common.config;

import com.oriole.wisepen.common.web.interceptor.FeignRequestInterceptor;
import com.oriole.wisepen.common.web.interceptor.HeaderInterceptor;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Configuration
// 显式扫描 common 包，把 SaPermissionImpl, GlobalExceptionHandler 等扫进去
@ComponentScan(basePackages = "com.oriole.wisepen.common")
public class WisepenWebAutoConfiguration implements WebMvcConfigurer {

    @Value("${wisepen.security.from-source:APISIX-wX0iR6tY}")
    private String fromSource;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册头部拦截器，拦截所有路径
        registry.addInterceptor(new HeaderInterceptor(fromSource))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login", "/auth/register",
                        "/external/**",
                        "/v3/api-docs/**",     // 放行 OpenAPI 的 JSON 接口
                        "/swagger-ui/**",      // 放行 Swagger 的 UI 静态资源
                        "/swagger-ui.html"     // 放行 Swagger 的 UI 入口
               );
    }

    @Bean
    public RequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor(fromSource);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 拦截所有接口
                // 允许跨域的源，Spring Boot 2.4+ 推荐使用 allowedOriginPatterns
                .allowedOriginPatterns("*")
                // 允许的方法，注意必须包含 OPTIONS（用于预检请求）和 HEAD（用于 pdf.js 探测）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                // 允许前端携带的自定义请求头（如你的 X-From-Source 或 Authorization）
                .allowedHeaders("*")
                // 暴露特定的响应头给前端 JS (pdf.js 强依赖这三个头)
                .exposedHeaders("Accept-Ranges", "Content-Range", "Content-Length")
                // 允许前端携带 Cookie 等凭证信息
                .allowCredentials(true)
                // 预检请求的缓存时间（秒），避免频繁发送 OPTIONS 请求
                .maxAge(3600);
    }
}