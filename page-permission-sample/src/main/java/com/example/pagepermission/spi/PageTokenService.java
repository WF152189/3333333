package com.example.pagepermission.spi;

import com.example.pagepermission.core.PageClaims;

public interface PageTokenService {
    PageClaims verify(String token);
    String issue(PageClaims sourceClaims, String targetPageId);
}
