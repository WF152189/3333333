package com.example.pagepermission.core;

public record PageClaims(
        String issuer,
        String audience,
        String userId,
        String sessionId,
        String tokenId,
        String pageId) {
}
