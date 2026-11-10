package com.example.pagepermission.config;

import com.example.pagepermission.web.PageTokenRequestReader;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type", PageTokenRequestReader.HEADER)
                .exposedHeaders(PageTokenRequestReader.HEADER);
    }
}
