package com.example.pagepermission.security;

import com.example.pagepermission.core.PagePermissionError;
import com.example.pagepermission.core.PagePermissionException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RsaPageTokenServiceTest {
    private final Instant issuedAt = Instant.parse("2026-11-02T00:00:00Z");

    @Test
    void validTokenPreservesPageIdAndStandardClaims() {
        RsaPageTokenService service = serviceAt(issuedAt);
        var claims = service.verify(service.issueInitial("12341"));

        assertEquals("12341", claims.pageId());
        assertEquals("demo-user", claims.userId());
        assertEquals("demo-session", claims.sessionId());
    }

    @Test
    void issuedTargetTokenChangesPageIdAndJtiButKeepsSession() {
        RsaPageTokenService service = serviceAt(issuedAt);
        var source = service.verify(service.issueInitial("11001"));
        var target = service.verify(service.issue(source, "12342"));

        assertEquals("12342", target.pageId());
        assertNotEquals(source.tokenId(), target.tokenId());
        assertEquals(source.sessionId(), target.sessionId());
        assertEquals(source.userId(), target.userId());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(issuedAt).issueInitial("11001");
        PagePermissionException ex = assertThrows(
                PagePermissionException.class,
                () -> serviceAt(issuedAt.plusSeconds(301)).verify(token));
        assertEquals(PagePermissionError.PAGE_TOKEN_INVALID, ex.error());
    }

    @Test
    void malformedTokenIsRejected() {
        PagePermissionException ex = assertThrows(
                PagePermissionException.class,
                () -> serviceAt(issuedAt).verify("not-a-jwt"));
        assertEquals(PagePermissionError.PAGE_TOKEN_INVALID, ex.error());
    }

    @Test
    void tokenWithWrongIssuerIsRejectedEvenWhenSignatureIsValid() {
        RsaPageTokenService service = serviceAt(issuedAt);
        var foreignClaims = new com.example.pagepermission.core.PageClaims(
                "foreign-issuer", "page-api", "user", "session", "jti", "11001");
        String token = service.issue(foreignClaims, "12341");

        PagePermissionException ex = assertThrows(PagePermissionException.class, () -> service.verify(token));
        assertEquals(PagePermissionError.PAGE_TOKEN_INVALID, ex.error());
    }

    @Test
    void tokenWithWrongAudienceIsRejectedEvenWhenSignatureIsValid() {
        RsaPageTokenService service = serviceAt(issuedAt);
        var foreignClaims = new com.example.pagepermission.core.PageClaims(
                "page-permission-demo", "foreign-api", "user", "session", "jti", "11001");
        String token = service.issue(foreignClaims, "12341");

        PagePermissionException ex = assertThrows(PagePermissionException.class, () -> service.verify(token));
        assertEquals(PagePermissionError.PAGE_TOKEN_INVALID, ex.error());
    }

    @Test
    void tokenExpiresExactlyAtExpirationBoundary() {
        String token = serviceAt(issuedAt).issueInitial("11001");
        PagePermissionException ex = assertThrows(
                PagePermissionException.class,
                () -> serviceAt(issuedAt.plusSeconds(300)).verify(token));
        assertEquals(PagePermissionError.PAGE_TOKEN_INVALID, ex.error());
    }

    private RsaPageTokenService serviceAt(Instant instant) {
        return new RsaPageTokenService(Clock.fixed(instant, ZoneOffset.UTC));
    }
}
