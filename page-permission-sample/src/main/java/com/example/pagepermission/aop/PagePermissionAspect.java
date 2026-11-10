package com.example.pagepermission.aop;

import com.example.pagepermission.annotation.PagePermission;
import com.example.pagepermission.annotation.PagePermissionRef;
import com.example.pagepermission.core.PageClaims;
import com.example.pagepermission.core.PagePermissionError;
import com.example.pagepermission.core.PagePermissionException;
import com.example.pagepermission.core.PermissionContext;
import com.example.pagepermission.core.PermissionResolver;
import com.example.pagepermission.spi.PageTokenResponseWriter;
import com.example.pagepermission.spi.PageTokenService;
import com.example.pagepermission.web.PageTokenRequestReader;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
/**
 * 页面权限认证的统一AOP入口。
 * ページ権限認証を一元的に処理するAOPのエントリーポイントです。
 *
 * <p>仅拦截标注了 {@code @PagePermission} 的Controller方法；没有该注解的API不执行
 * 本套页面权限认证。认证顺序固定为：</p>
 * <ol>
 *   <li>读取并验证当前页面授权JWT，取得可信的source pageId；</li>
 *   <li>解析 {@code @PagePermissionRef}、PAGE/OPERATION规则、code和action；</li>
 *   <li>全部通过后才执行Controller；</li>
 *   <li>仅跨页面调用且Controller正常返回时，签发目标pageId的新JWT。</li>
 * </ol>
 *
 * <p>因此，认证失败、Controller抛出异常或响应JWT写入失败都不会被当作成功跳转。
 * 当前页面调用不会重复签发JWT；旧JWT也不会在服务端立即作废，而是由较短有效期控制风险。</p>
 *
 * <p>{@code @PagePermission} が付与されたControllerメソッドのみをインターセプトします。
 * 処理順序は、既存JWTの検証、ページ権限の解決、Controllerの実行、必要に応じた遷移先JWTの
 * 発行です。同一ページからの呼び出しではJWTを再発行せず、画面間呼び出しでControllerが
 * 正常終了した場合に限り、遷移先pageIdを格納したJWTを発行します。</p>
 */
public final class PagePermissionAspect {
    private final PageTokenRequestReader tokenReader;
    private final PageTokenService tokenService;
    private final PermissionResolver permissionResolver;
    private final PageTokenResponseWriter responseWriter;

    public PagePermissionAspect(
            PageTokenRequestReader tokenReader,
            PageTokenService tokenService,
            PermissionResolver permissionResolver,
            PageTokenResponseWriter responseWriter) {
        this.tokenReader = tokenReader;
        this.tokenService = tokenService;
        this.permissionResolver = permissionResolver;
        this.responseWriter = responseWriter;
    }

    @Around("@annotation(pagePermission)")
    public Object authorize(ProceedingJoinPoint joinPoint, PagePermission pagePermission) throws Throwable {
        // 第一步：读取并验签页面授权JWT。
        // 权限判断只信任后端签名后的claims.pageId；请求参数、URL或前端状态中的pageId
        // 都不能替代JWT中的pageId，否则客户端可以自行伪造来源页面。
        // 第1段階：ページ権限JWTを読み込み、署名を検証します。
        // 権限判定では署名済みclaims.pageIdのみを信頼し、リクエストパラメータやURL、
        // フロントエンド状態のpageIdは使用しません。
        String token = tokenReader.read();
        PageClaims claims = tokenService.verify(token);
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        // Ref只在跨页面调用时使用。同页面调用即使方法配置了Ref，也不会借此切换JWT。
        // Refは画面間呼び出しの場合のみ使用します。同一画面の呼び出しではJWTを切り替えません。
        PagePermissionRef reference = method.getAnnotation(PagePermissionRef.class);

        // 第二步：在业务代码执行前完成全部授权判断。
        // Resolver会区分当前页面/跨页面及PAGE/OPERATION模式，并保证目标pageId唯一。
        // 第2段階：業務処理の実行前にすべての認可判定を完了します。
        // Resolverは同一画面・画面間呼び出し、およびPAGE・OPERATIONモードを判別し、
        // 遷移先pageIdが一意になることを保証します。
        PermissionContext context = permissionResolver.resolve(
                joinPoint, claims, pagePermission, reference);

        // 第三步：只有认证成功才能进入Controller。
        // proceed()抛出任何异常时方法立即退出，因此不会执行下面的新JWT签发逻辑。
        // 第3段階：認証・認可に成功した場合のみControllerを実行します。
        // proceed()が例外を送出した場合、新しいJWTは発行されません。
        Object result = joinPoint.proceed();

        if (context.relatedCall()) {
            try {
                // 第四步：只有跨页面调用且Controller正常返回，才签发并写入目标页面JWT。
                // 前端收到响应后保存该JWT，后续目标页面调用便成为“当前页面调用”。
                // PAGE和OPERATION两种规则在这里没有区别，最终JWT都只保存目标pageId。
                // 第4段階：画面間呼び出しでControllerが正常終了した場合に限り、遷移先JWTを発行します。
                // フロントエンドはレスポンスからJWTを保存し、以降は遷移先ページからの呼び出しとして扱います。
                // PAGE・OPERATIONのどちらでも、JWTに保存する権限情報は遷移先pageIdのみです。
                String targetToken = tokenService.issue(claims, context.targetPageId());
                responseWriter.write(targetToken);
            } catch (PagePermissionException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new PagePermissionException(
                        PagePermissionError.PAGE_TOKEN_ISSUE_FAILED,
                        "Failed to issue the target page token",
                        ex);
            }
        }
        return result;
    }
}
