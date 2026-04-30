package com.military.combat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA 静态资源显式映射：
 * 避免 /app 路由兜底时对 /app/assets/** 产生匹配歧义。
 */
@Configuration
public class SpaWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/assets/**")
                .addResourceLocations("classpath:/static/app/assets/");
    }
}
