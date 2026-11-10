package com.example.pagepermission.web;

import com.example.pagepermission.core.PagePermissionError;
import com.example.pagepermission.core.PagePermissionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class PageTokenRequestReader {
    public static final String HEADER = "X-Page-Authorization";

    private final HttpServletRequest request;

    public PageTokenRequestReader(HttpServletRequest request) {
        this.request = request;
    }

    public String read() {
        // 页面JWT与登录JWT分离，使用独立请求头，避免两种认证职责混在一起。
        // ページJWTとログインJWTを分離し、専用ヘッダーを使用して認証責務の混在を防ぎます。
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            throw new PagePermissionException(
                    PagePermissionError.PAGE_TOKEN_REQUIRED,
                    "Missing " + HEADER + " header");
        }
        // 同时兼容“Bearer token”和直接传token两种客户端写法。
        // 「Bearer token」形式とtokenを直接指定する形式の両方に対応します。
        return value.startsWith("Bearer ") ? value.substring(7) : value;
    }
}
