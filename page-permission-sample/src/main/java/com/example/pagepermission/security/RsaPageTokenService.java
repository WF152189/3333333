package com.example.pagepermission.security;

import com.example.pagepermission.core.PageClaims;
import com.example.pagepermission.core.PagePermissionError;
import com.example.pagepermission.core.PagePermissionException;
import com.example.pagepermission.spi.PageTokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
/**
 * Demo用的RS256页面JWT实现；正式环境应从KeyStore或密钥管理服务读取RSA密钥。
 * デモ用のRS256ページJWT実装です。本番環境ではKeyStoreまたは鍵管理サービスからRSA鍵を取得してください。
 */
public class RsaPageTokenService implements PageTokenService {
    private static final String ISSUER = "page-permission-demo";
    private static final String AUDIENCE = "page-api";
    // 仅用于本地Demo：进程启动时生成RSA密钥对。生产环境必须使用持久化并可轮换的密钥。
    // ローカルデモ専用：起動時にRSA鍵ペアを生成します。本番環境では永続化・ローテーション可能な鍵を使用してください。
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final RSAPrivateKey SIGNING_KEY = (RSAPrivateKey) KEY_PAIR.getPrivate();
    private static final RSAPublicKey VERIFICATION_KEY = (RSAPublicKey) KEY_PAIR.getPublic();
    private final Clock clock;

    public RsaPageTokenService() {
        this(Clock.systemUTC());
    }

    RsaPageTokenService(Clock clock) {
        this.clock = clock;
    }

    public String issueInitial(String pageId) {
        // 父页面Guard认证成功后创建该登录会话的第一枚页面JWT。
        // 親ページのGuard認証成功後、このログインセッションで最初のページJWTを生成します。
        PageClaims initial = new PageClaims(
                ISSUER, AUDIENCE, "demo-user", "demo-session",
                UUID.randomUUID().toString(), pageId);
        return encode(initial);
    }

    @Override
    public PageClaims verify(String token) {
        try {
            // Nimbus负责JWT结构解析、算法固定及RSA公钥验签。
            // NimbusでJWTを解析し、アルゴリズム固定とRSA公開鍵による署名検証を行います。
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("algorithm");
            }
            if (!jwt.verify(new RSASSAVerifier(VERIFICATION_KEY))) {
                throw new IllegalArgumentException("signature");
            }

            var body = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            Date expiration = body.getExpirationTime();
            if (expiration == null || !now.isBefore(expiration.toInstant())) {
                throw new IllegalArgumentException("expired");
            }
            Date issuedAt = body.getIssueTime();
            if (issuedAt == null || issuedAt.toInstant().isAfter(now)) {
                throw new IllegalArgumentException("issued-at");
            }
            List<String> audience = body.getAudience();
            if (!ISSUER.equals(body.getIssuer()) || audience.size() != 1 || !AUDIENCE.equals(audience.get(0))) {
                throw new IllegalArgumentException("issuer or audience");
            }
            PageClaims claims = new PageClaims(
                    required(body.getIssuer(), "iss"), required(audience.get(0), "aud"),
                    required(body.getSubject(), "sub"), required(body.getStringClaim("sid"), "sid"),
                    required(body.getJWTID(), "jti"), required(body.getStringClaim("pageId"), "pageId"));
            return claims;
        } catch (PagePermissionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PagePermissionException(PagePermissionError.PAGE_TOKEN_INVALID, "Invalid page JWT", ex);
        }
    }

    @Override
    public String issue(PageClaims sourceClaims, String targetPageId) {
        // 跨页面时只更新pageId和jti，用户及登录会话保持不变。
        // 画面間遷移ではpageIdとjtiだけを更新し、ユーザーおよびログインセッションは維持します。
        return encode(new PageClaims(
                sourceClaims.issuer(), sourceClaims.audience(), sourceClaims.userId(),
                sourceClaims.sessionId(), UUID.randomUUID().toString(), targetPageId));
    }

    private String encode(PageClaims claims) {
        try {
            Instant now = clock.instant();
            // 旧JWT不维护服务端失效列表，因此通过五分钟短有效期控制风险窗口。
            // 旧JWTのサーバー側失効リストは管理せず、5分間の短い有効期限でリスク期間を制限します。
            // JJWT负责标准Claims组装、Base64URL编码及RS256私钥签发。
            // JJWTで標準Claimsの設定、Base64URLエンコード、RS256秘密鍵署名を行います。
            return Jwts.builder()
                    .issuer(claims.issuer())
                    .audience().add(claims.audience()).and()
                    .subject(claims.userId())
                    .claim("sid", claims.sessionId())
                    .id(claims.tokenId())
                    .claim("pageId", claims.pageId())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusSeconds(300)))
                    .signWith(SIGNING_KEY, Jwts.SIG.RS256)
                    .compact();
        } catch (Exception ex) {
            throw new PagePermissionException(PagePermissionError.PAGE_TOKEN_ISSUE_FAILED, "Cannot issue page JWT", ex);
        }
    }

    private String required(String value, String claimName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(claimName);
        return value;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
