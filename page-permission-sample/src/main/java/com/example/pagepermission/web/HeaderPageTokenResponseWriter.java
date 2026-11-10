package com.example.pagepermission.web;

import com.example.pagepermission.spi.PageTokenResponseWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class HeaderPageTokenResponseWriter implements PageTokenResponseWriter {
    private final HttpServletResponse response;

    public HeaderPageTokenResponseWriter(HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public void write(String token) {
        // Angular读取该响应头并覆盖sessionStorage中的当前页面JWT。
        // Angularはこのレスポンスヘッダーを読み取り、sessionStorageの現在ページJWTを更新します。
        response.setHeader(PageTokenRequestReader.HEADER, token);
    }
}
