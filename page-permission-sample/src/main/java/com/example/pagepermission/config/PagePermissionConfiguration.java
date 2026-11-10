package com.example.pagepermission.config;

import com.example.pagepermission.aop.PagePermissionAspect;
import com.example.pagepermission.core.OperationCodeResolver;
import com.example.pagepermission.core.PageIdParser;
import com.example.pagepermission.core.PermissionResolver;
import com.example.pagepermission.spi.PageTokenResponseWriter;
import com.example.pagepermission.spi.PageTokenService;
import com.example.pagepermission.web.PageTokenRequestReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;

@Configuration
public class PagePermissionConfiguration {
    @Bean
    PageIdParser pageIdParser() {
        return new PageIdParser();
    }

    @Bean
    OperationCodeResolver operationCodeResolver() {
        return new OperationCodeResolver();
    }

    @Bean
    PermissionResolver permissionResolver(
            PageIdParser parser,
            OperationCodeResolver codeResolver) {
        return new PermissionResolver(parser, codeResolver);
    }

    @Bean
    PagePermissionAspect pagePermissionAspect(
            PageTokenRequestReader tokenReader,
            PageTokenService tokenService,
            PermissionResolver permissionResolver,
            PageTokenResponseWriter responseWriter) {
        return new PagePermissionAspect(
                tokenReader, tokenService, permissionResolver, responseWriter);
    }

    @Bean
    PermissionAnnotationValidator permissionAnnotationValidator(
            ApplicationContext applicationContext,
            PageIdParser pageIdParser) {
        return new PermissionAnnotationValidator(applicationContext, pageIdParser);
    }
}
