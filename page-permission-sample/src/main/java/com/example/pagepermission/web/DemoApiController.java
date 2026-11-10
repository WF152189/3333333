package com.example.pagepermission.web;

import com.example.pagepermission.annotation.Operation;
import com.example.pagepermission.annotation.AccessMode;
import com.example.pagepermission.annotation.PagePermission;
import com.example.pagepermission.annotation.PagePermissionGroup;
import com.example.pagepermission.annotation.PagePermissionItem;
import com.example.pagepermission.annotation.PagePermissionRef;
import com.example.pagepermission.annotation.PagePermissionRefItem;
import com.example.pagepermission.core.PageIdParser;
import com.example.pagepermission.security.RsaPageTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class DemoApiController {
    private static final Set<String> MENU_PAGES = Set.of("11001", "12341", "22001");
    private final RsaPageTokenService tokenService;
    private final PageIdParser pageIdParser;

    public DemoApiController(RsaPageTokenService tokenService, PageIdParser pageIdParser) {
        this.tokenService = tokenService;
        this.pageIdParser = pageIdParser;
    }

    @PostMapping("/page-auth")
    public ResponseEntity<Map<String, String>> authenticate(
            @RequestBody PageAuthRequest request, HttpServletResponse response) {
        // 只有折叠菜单可直接进入的父页面使用该API；子页面不调用页面认证API。
        // 折りたたみメニューから直接遷移できる親ページだけがこのAPIを使用します。
        // 子ページからページ認証APIは呼び出しません。
        pageIdParser.groupId(request.pageId());
        if (!MENU_PAGES.contains(request.pageId())) {
            return ResponseEntity.status(403).body(Map.of("message", "menu page denied"));
        }
        String token = tokenService.issueInitial(request.pageId());
        response.setHeader(PageTokenRequestReader.HEADER, token);
        return ResponseEntity.ok(Map.of("message", "parent page authenticated", "pageId", request.pageId()));
    }

    @PostMapping("/business/b")
    // A1 -> B1时不会触发Angular父路由Guard，而是在此业务API上完成跨页面认证。
    // A1からB1への遷移ではAngularの親ルートGuardを実行せず、この業務APIで画面間認証を行います。
    @PagePermission(groups = @PagePermissionGroup(
            mode = AccessMode.OPERATION, codeField = "code",
            items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1", "2"})))
    @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"))
    public Map<String, String> businessB(@RequestBody BusinessRequest request) {
        return Map.of("message", "B controller returned normally", "code", request.code());
    }

    @PostMapping("/business/c")
    @PagePermission(groups = @PagePermissionGroup(
            mode = AccessMode.OPERATION, codeField = "code",
            items = @PagePermissionItem(pageId = "23451", action = Operation.QUERY, codes = {"1"})))
    @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = "12341", targetPageId = "23451"))
    public Map<String, String> businessC(@RequestBody BusinessRequest request) {
        return Map.of("message", "C controller returned normally", "code", request.code());
    }

    @PostMapping("/business/shared")
    // 混合示例：一个PAGE规则与一组N:1动作页面共用同一个Controller方法。
    // 混在例：1つのPAGEルールとN対1の操作ページ群が同じControllerメソッドを共有します。
    @PagePermission(groups = {
            @PagePermissionGroup(mode = AccessMode.PAGE, pageId = "56781"),
            @PagePermissionGroup(mode = AccessMode.OPERATION, codeField = "code", items = {
                    @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1", "2", "3"}),
                    @PagePermissionItem(pageId = "12342", action = Operation.CREATE, codes = {"4", "5", "6"}),
                    @PagePermissionItem(pageId = "12343", action = Operation.UPDATE, codes = {"7", "8", "9"}),
                    @PagePermissionItem(pageId = "12344", action = Operation.DELETE, codes = {"10", "11", "12"})
            })})
    @PagePermissionRef(items = {
            // 目标56781允许多个来源页面调用；单一来源也可省略数组的大括号。
            // 遷移先56781は複数の遷移元ページから呼び出せます。遷移元が1件なら配列の波括弧を省略できます。
            // PAGE调用中的code只是普通业务字段，不参与权限判断。
            // PAGE呼び出しのcodeは通常の業務項目であり、権限判定には使用しません。
            @PagePermissionRefItem(sourcePageIds = {"22001", "22002"}, targetPageId = "56781"),
            @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"),
            @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12342"),
            @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12343"),
            @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12344")
    })
    public Map<String, String> sharedBusiness(@RequestBody BusinessRequest request) {
        return Map.of("message", "shared controller returned normally",
                "code", request.code() == null ? "" : request.code());
    }

    @PostMapping("/business/fail")
    // 用于验证Controller异常时AOP绝不能签发目标JWT。
    // Controllerで例外が発生した場合に、AOPが遷移先JWTを発行しないことを検証するためのAPIです。
    @PagePermission(groups = @PagePermissionGroup(
            mode = AccessMode.OPERATION, codeField = "code",
            items = @PagePermissionItem(pageId = "12341", action = Operation.QUERY, codes = {"1"})))
    @PagePermissionRef(items = @PagePermissionRefItem(sourcePageIds = "11001", targetPageId = "12341"))
    public Map<String, String> failingBusiness(@RequestBody BusinessRequest request) {
        throw new IllegalStateException("simulated controller failure");
    }

    @PostMapping("/business/public")
    // 未标注@PagePermission的API不进入页面权限AOP，用于验证公开API的旁路行为。
    // @PagePermissionを付与しないAPIはページ権限AOPの対象外となり、公開APIのバイパス動作を検証します。
    public Map<String, String> publicBusiness() {
        return Map.of("message", "public controller returned normally");
    }

    public record PageAuthRequest(String pageId) {}
    public record BusinessRequest(String code) {}
}
