package com.example.pagepermission.aop;

import com.example.pagepermission.annotation.*;
import com.example.pagepermission.core.*;
import com.example.pagepermission.spi.PageTokenResponseWriter;
import com.example.pagepermission.spi.PageTokenService;
import com.example.pagepermission.web.PageTokenRequestReader;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PagePermissionAspectTest {
    private FakeTokenService tokenService;
    private FakeResponseWriter responseWriter;
    private PagePermissionAspect aspect;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PageTokenRequestReader.HEADER, "old-token");
        tokenService = new FakeTokenService();
        responseWriter = new FakeResponseWriter();
        aspect = new PagePermissionAspect(
                new PageTokenRequestReader(request), tokenService,
                new PermissionResolver(new PageIdParser(), new OperationCodeResolver()), responseWriter);
    }

    @Test
    void samePageCallDoesNotIssueNewToken() throws Throwable {
        Method method = Fixture.class.getMethod("normal", Map.class);
        tokenService.verifiedClaims = claims("12341");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        assertEquals("ok", aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertTrue(state.proceeded);
        assertNull(tokenService.issuedTargetPageId);
        assertNull(responseWriter.token);
    }

    @Test
    void relatedChildPageCallIssuesTargetTokenAfterNormalReturn() throws Throwable {
        Method method = Fixture.class.getMethod("related", Map.class);
        tokenService.verifiedClaims = claims("11001");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        assertEquals("ok", aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertEquals("12341", tokenService.issuedTargetPageId);
        assertEquals("target-token", responseWriter.token);
    }

    @Test
    void anySourceListedForOneTargetCanCallIt() throws Throwable {
        Method method = Fixture.class.getMethod("multiSourceRelated", Map.class);
        tokenService.verifiedClaims = claims("22001");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class));
        assertTrue(state.proceeded);
        assertEquals("12341", tokenService.issuedTargetPageId);
    }

    @Test
    void sourceNotListedForResolvedTargetIsDenied() throws Throwable {
        Method method = Fixture.class.getMethod("multiSourceRelated", Map.class);
        tokenService.verifiedClaims = claims("33001");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        PagePermissionException ex = assertThrows(PagePermissionException.class,
                () -> aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertEquals(PagePermissionError.PAGE_REFERENCE_DENIED, ex.error());
        assertFalse(state.proceeded);
    }

    @Test
    void missingReferenceDeniesCrossPageCall() throws Throwable {
        Method method = Fixture.class.getMethod("normal", Map.class);
        tokenService.verifiedClaims = claims("11001");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        PagePermissionException ex = assertThrows(PagePermissionException.class,
                () -> aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertEquals(PagePermissionError.PAGE_REFERENCE_DENIED, ex.error());
        assertFalse(state.proceeded);
    }

    @Test
    void controllerExceptionDoesNotIssueNewToken() throws Throwable {
        Method method = Fixture.class.getMethod("related", Map.class);
        tokenService.verifiedClaims = claims("11001");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), null,
                new IllegalStateException("failed"));

        assertThrows(IllegalStateException.class,
                () -> aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertNull(tokenService.issuedTargetPageId);
        assertNull(responseWriter.token);
    }

    @Test
    void tokenIssueFailureIsReportedAfterControllerReturn() throws Throwable {
        Method method = Fixture.class.getMethod("related", Map.class);
        tokenService.verifiedClaims = claims("11001");
        tokenService.issueFailure = new IllegalStateException("issue failed");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        PagePermissionException ex = assertThrows(PagePermissionException.class,
                () -> aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertEquals(PagePermissionError.PAGE_TOKEN_ISSUE_FAILED, ex.error());
        assertTrue(state.proceeded);
        assertNull(responseWriter.token);
    }

    @Test
    void responseWriteFailureIsReportedAfterControllerReturn() throws Throwable {
        Method method = Fixture.class.getMethod("related", Map.class);
        tokenService.verifiedClaims = claims("11001");
        responseWriter.failure = new IllegalStateException("header failed");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        PagePermissionException ex = assertThrows(PagePermissionException.class,
                () -> aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertEquals(PagePermissionError.PAGE_TOKEN_ISSUE_FAILED, ex.error());
        assertTrue(state.proceeded);
    }

    @Test
    void overlappingCodesAcrossReferencedTargetsAreRejectedAsAmbiguous() throws Throwable {
        Method method = Fixture.class.getMethod("ambiguous", Map.class);
        tokenService.verifiedClaims = claims("11001");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        PagePermissionException ex = assertThrows(PagePermissionException.class,
                () -> aspect.authorize(state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertEquals(PagePermissionError.PAGE_REFERENCE_AMBIGUOUS, ex.error());
        assertFalse(state.proceeded);
    }

    @Test
    void uniqueRelatedPageTargetIgnoresCodeField() throws Throwable {
        Method method = Fixture.class.getMethod("relatedPageWithCode", Map.class);
        tokenService.verifiedClaims = claims("22001");
        JoinPointState state = joinPoint(method, Map.of("code", "1"), "ok", null);

        assertEquals("ok", aspect.authorize(
                state.joinPoint, method.getAnnotation(PagePermission.class)));
        assertTrue(state.proceeded);
        assertEquals("56781", tokenService.issuedTargetPageId);
    }

    private PageClaims claims(String pageId) {
        return new PageClaims("iss", "aud", "u1", "s1", "j1", pageId);
    }

    private JoinPointState joinPoint(Method method, Object argument, Object result, RuntimeException failure) {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{MethodSignature.class},
                (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getName" -> method.getName();
                    case "getDeclaringType" -> method.getDeclaringClass();
                    case "getDeclaringTypeName" -> method.getDeclaringClass().getName();
                    case "getReturnType" -> method.getReturnType();
                    case "getParameterTypes" -> method.getParameterTypes();
                    case "getParameterNames" -> java.util.Arrays.stream(method.getParameters())
                            .map(java.lang.reflect.Parameter::getName).toArray(String[]::new);
                    case "getModifiers" -> method.getModifiers();
                    case "toShortString", "toLongString", "toString" -> method.toGenericString();
                    default -> null;
                });
        JoinPointState state = new JoinPointState();
        state.joinPoint = (ProceedingJoinPoint) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "getSignature" -> signature;
                    case "getArgs" -> new Object[]{argument};
                    case "proceed" -> {
                        state.proceeded = true;
                        if (failure != null) throw failure;
                        yield result;
                    }
                    case "toShortString", "toLongString", "toString" -> method.toGenericString();
                    default -> null;
                });
        return state;
    }

    private static final class JoinPointState {
        private ProceedingJoinPoint joinPoint;
        private boolean proceeded;
    }

    private static final class FakeTokenService implements PageTokenService {
        private PageClaims verifiedClaims;
        private String issuedTargetPageId;
        private RuntimeException issueFailure;

        public PageClaims verify(String token) { return verifiedClaims; }
        public String issue(PageClaims sourceClaims, String targetPageId) {
            if (issueFailure != null) throw issueFailure;
            issuedTargetPageId = targetPageId;
            return "target-token";
        }
    }

    private static final class FakeResponseWriter implements PageTokenResponseWriter {
        private String token;
        private RuntimeException failure;
        public void write(String token) {
            if (failure != null) throw failure;
            this.token = token;
        }
    }

    static final class Fixture {
        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, codeField = "code",
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1", "2"})))
        public String normal(Map<String, String> request) { return "ok"; }

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, codeField = "code",
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1", "2"})))
        @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"))
        public String related(Map<String, String> request) { return "ok"; }

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, codeField = "code",
                items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1", "2"})))
        @PagePermissionRef(items = @PagePermissionRefItem(
                sourcePageIds = {"11001", "22001"}, targetPageId = "12341"))
        public String multiSourceRelated(Map<String, String> request) { return "ok"; }

        @PagePermission(groups = @PagePermissionGroup(mode = AccessMode.OPERATION, codeField = "code", items = {
                @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = "1"),
                @PagePermissionItem(pageId = "12342", action = Operation.CREATE, codes = "1")}))
        @PagePermissionRef(items = {
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"),
                @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12342")})
        public String ambiguous(Map<String, String> request) { return "ok"; }

        @PagePermission(groups = {
                @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"),
                @PagePermissionGroup(mode = AccessMode.OPERATION, codeField = "code",
                        items = @PagePermissionItem(
                                pageId = "12341", action = Operation.QUERY, codes = "1"))
        })
        @PagePermissionRef(items = @PagePermissionRefItem(
                sourcePageIds = "22001", targetPageId = "56781"))
        public String relatedPageWithCode(Map<String, String> request) { return "ok"; }
    }
}
