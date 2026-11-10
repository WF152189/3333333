package com.example.pagepermission.core;

import com.example.pagepermission.annotation.Operation;
import com.example.pagepermission.annotation.AccessMode;
import com.example.pagepermission.annotation.PagePermission;
import com.example.pagepermission.annotation.PagePermissionGroup;
import com.example.pagepermission.annotation.PagePermissionItem;
import com.example.pagepermission.annotation.PagePermissionRef;
import com.example.pagepermission.annotation.PagePermissionRefItem;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 页面权限规则解析器，也是认证逻辑的核心组件。
 * ページ権限ルールを解決する、認証・認可ロジックの中核コンポーネントです。
 *
 * <p>本类不负责验证JWT签名；JWT签名已经由 {@code PagePermissionAspect} 调用
 * {@code PageTokenService} 完成验证。本类只使用可信JWT中的 {@code pageId}，结合
 * Controller方法上的 {@code @PagePermission}/{@code @PagePermissionRef} 和本次请求的
 * {@code code}，决定：</p>
 * <ol>
 *   <li>本次调用是当前页面调用，还是从其他页面发起的跨页面调用；</li>
 *   <li>应该匹配PAGE（1:1）规则还是OPERATION（N:1）规则；</li>
 *   <li>请求最终代表哪个目标pageId及哪个操作；</li>
 *   <li>跨页面调用是否具有合法、单向的页面关联。</li>
 * </ol>
 *
 * <p>JWT署名の検証自体は行いません。検証済みJWTのpageId、Controllerメソッドに付与された
 * {@code @PagePermission}/{@code @PagePermissionRef}、およびリクエストのcodeを組み合わせ、
 * 同一画面呼び出しか画面間呼び出しか、PAGE・OPERATIONのどちらを適用するか、最終的な
 * 遷移先pageIdと操作、ならびに有効な一方向の画面関連が存在するかを判定します。</p>
 */
public final class PermissionResolver {
    private final PageIdParser pageIdParser;
    private final OperationCodeResolver codeResolver;

    public PermissionResolver(PageIdParser pageIdParser, OperationCodeResolver codeResolver) {
        this.pageIdParser = pageIdParser;
        this.codeResolver = codeResolver;
    }

    public PermissionContext resolve(
            ProceedingJoinPoint joinPoint,
            PageClaims claims,
            PagePermission permission,
            PagePermissionRef reference) {

        // sourcePageId只能来自已经验签的页面授权JWT，不能信任前端额外提交的pageId。
        // sourcePageIdには署名検証済みJWTの値のみを使用し、フロントエンドが別途送信した値は信頼しません。
        String sourcePageId = claims.pageId();

        // 先检查当前JWT的pageId是否已经属于此API的任意一条规则。
        // 找得到表示“当前页面调用本页面有权访问的API”；找不到才可能是跨页面调用。
        // 現在のJWTのpageIdが、このAPIのいずれかのルールに含まれるかを先に確認します。
        // 含まれる場合は同一画面からの呼び出し、含まれない場合は画面間呼び出しの候補です。
        PagePermissionGroup matchedGroup = findGroup(permission, sourcePageId).orElse(null);
        // 当前页面调用：不需要验证PagePermissionRef，也不会签发新的页面JWT。
        // 跨页面调用：必须验证Ref，并在Controller成功后把JWT切换为目标pageId。
        // 同一画面呼び出しではRef検証とJWT再発行は不要です。
        // 画面間呼び出しではRef検証が必須で、Controller正常終了後にJWTを遷移先pageIdへ切り替えます。
        boolean relatedCall = matchedGroup == null;

        // 跨页面时，不能让客户端直接声明目标pageId。
        // 目标必须同时满足：存在source->target的Ref、目标属于当前API规则，并且能够唯一确定：
        // 唯一Ref目标直接采用；多个OPERATION目标才使用具体code值继续筛选。
        // 画面間呼び出しでは、クライアントに遷移先pageIdを直接指定させません。
        // RefとAPI権限ルールから候補を絞り、複数のOPERATION候補がある場合だけcode値を使用して、
        // 一意の遷移先pageIdを決定します。
        String targetPageId = relatedCall
                ? resolveRelatedTarget(joinPoint, sourcePageId, permission, reference)
                : sourcePageId;
        PagePermissionGroup group = relatedCall
                ? findGroup(permission, targetPageId).orElseThrow()
                : matchedGroup;

        // PAGE表示页面与API是1:1关系。pageId已经在规则匹配阶段确定，
        // 这种模式没有操作code，也不需要进行action校验。
        // PAGEは画面とAPIが1対1のモードです。操作codeおよびactionの検証は行いません。
        if (group.mode() == AccessMode.PAGE) {
            return new PermissionContext(
                    claims, sourcePageId, targetPageId, relatedCall,
                    group, null, null, null);
        }

        final String resolvedTargetPageId = targetPageId;
        PagePermissionItem item = Arrays.stream(group.items())
                .filter(it -> it.pageId().equals(resolvedTargetPageId))
                .findFirst()
                .orElseThrow(() -> new PagePermissionException(
                        PagePermissionError.PAGE_PERMISSION_NOT_FOUND,
                        "No permission item for page " + resolvedTargetPageId));

        // OPERATION表示多个动作页面共用一个API。此时code不仅是业务参数，还是权限判定依据。
        // code必须属于最终目标pageId对应的item，不能借用同一规则组内其他动作页面的code。
        // OPERATIONは複数の操作ページが同一APIを共有するモードです。
        // codeは業務値であると同時に権限判定値であり、遷移先pageIdに対応するitemのcodeである必要があります。
        String code = codeResolver.resolve(joinPoint, group.codeField());
        if (Arrays.stream(item.codes()).noneMatch(code::equals)) {
            throw new PagePermissionException(
                    PagePermissionError.OPERATION_CODE_INVALID,
                    "Operation code is not allowed: " + code);
        }

        // pageId最后一位编码了动作类型。即使code合法，也必须再次验证该动作与注解action一致，
        // 防止错误配置把查询页面配置成新增、修改或删除权限。
        // pageIdの末尾1桁は操作種別を表します。codeが正しくても、注解のactionとの一致を検証し、
        // 設定ミスによって参照画面へ登録・更新・削除権限が付与されることを防止します。
        Operation pageOperation = pageIdParser.operation(resolvedTargetPageId);
        if (pageOperation != item.action()) {
            throw new PagePermissionException(
                    PagePermissionError.OPERATION_MISMATCH,
                    "pageId action and configured action differ");
        }

        return new PermissionContext(
                claims,
                sourcePageId,
                resolvedTargetPageId,
                relatedCall,
                group,
                item,
                code,
                item.action());
    }

    private boolean containsPage(PagePermission permission, String pageId) {
        return findGroup(permission, pageId).isPresent();
    }

    private String resolveRelatedTarget(
            ProceedingJoinPoint joinPoint,
            String sourcePageId,
            PagePermission permission,
            PagePermissionRef reference) {
        if (reference == null) {
            denyReference(sourcePageId);
        }

        // 先收集目标确实声明在当前API权限中的Ref。每条Ref以目标页面为中心，
        // sourcePageIds表示“这个目标允许被谁调用”；Ref仍是单向的，允许A->B不会自动允许B->A。
        // まず、遷移先が現在のAPI権限ルールに存在するRefだけを収集します。
        // 各Refは遷移先を中心に定義し、sourcePageIdsは「誰から呼び出せるか」を表します。
        // Refは一方向であり、A→Bを許可してもB→Aは自動的には許可されません。
        List<PagePermissionRefItem> references = Arrays.stream(reference.items())
                .filter(it -> containsPage(permission, it.targetPageId()))
                .toList();
        if (references.isEmpty()) {
            denyReference(sourcePageId);
        }

        // 先用可信JWT中的sourcePageId筛选Ref。请求里即使始终包含code，也不能用“code是否存在”
        // 来决定PAGE或OPERATION；mode只能在目标页面确定后决定code是否参与认证。
        // まず、署名検証済みJWTのsourcePageIdでRefを絞り込みます。すべてのリクエストにcodeが
        // 含まれていても、codeの有無でPAGE／OPERATIONを選択してはいけません。
        List<PagePermissionRefItem> sourceReferences = references.stream()
                .filter(ref -> Arrays.asList(ref.sourcePageIds()).contains(sourcePageId))
                .toList();
        if (sourceReferences.isEmpty()) {
            denyReference(sourcePageId);
        }

        // 允许来源只关联一个目标时，目标已经由Ref唯一确定：
        // - PAGE：后续完全忽略code；
        // - OPERATION：后续再验证该目标Item对应的code与action。
        // 遷移元に対する遷移先が1件ならRefだけで一意に決定します。
        // PAGEではcodeを無視し、OPERATIONでは決定後にcodeとactionを検証します。
        if (sourceReferences.size() == 1) {
            return sourceReferences.get(0).targetPageId();
        }

        // 同一来源存在多个目标时，只允许这些目标全部为OPERATION。
        // PAGE与其他目标并存时，所有请求都带code的系统无法可靠判断要访问哪一个目标，
        // 该配置应由启动校验禁止；这里保留运行时防御。
        // 同一遷移元に複数の遷移先がある場合は、すべてOPERATIONでなければなりません。
        boolean containsNonOperationTarget = sourceReferences.stream()
                .map(ref -> findGroup(permission, ref.targetPageId()).orElseThrow())
                .anyMatch(group -> group.mode() != AccessMode.OPERATION);
        if (containsNonOperationTarget) {
            throw new PagePermissionException(
                    PagePermissionError.PAGE_REFERENCE_AMBIGUOUS,
                    "One source cannot reference PAGE and other targets: " + sourcePageId);
        }

        // 多个OPERATION目标之间才使用各自规则的codeField/codes来选择唯一目标。
        // 複数のOPERATION遷移先がある場合に限り、codeField/codesで遷移先を一意に決定します。
        List<String> matchedTargets = sourceReferences.stream()
                .filter(ref -> operationRequestMatchesTarget(
                        joinPoint, permission, ref.targetPageId()))
                .map(PagePermissionRefItem::targetPageId)
                .distinct()
                .toList();

        if (matchedTargets.isEmpty()) {
            boolean hasAnyOperationCode = sourceReferences.stream()
                    .map(ref -> findGroup(permission, ref.targetPageId()).orElseThrow())
                    .anyMatch(group -> codeResolver.resolveOptional(
                            joinPoint, group.codeField()).isPresent());
            if (!hasAnyOperationCode) {
                throw new PagePermissionException(
                        PagePermissionError.OPERATION_CODE_REQUIRED,
                        "Operation code is required to select a target page");
            }
            throw new PagePermissionException(
                    PagePermissionError.OPERATION_CODE_INVALID,
                    "Request does not select an allowed target page");
        }
        if (matchedTargets.size() > 1) {
            // 启动校验通常会提前拦截同一来源下的重复code；这里仍保留运行时防御，
            // 防止动态代理、未来配置来源变化或校验遗漏导致目标pageId不唯一。
            // 起動時検証で重複codeを検出しますが、防御的に実行時にも遷移先の一意性を確認します。
            throw new PagePermissionException(
                    PagePermissionError.PAGE_REFERENCE_AMBIGUOUS,
                    "Operation code selects multiple target pages: " + matchedTargets);
        }
        return matchedTargets.get(0);
    }

    private boolean operationRequestMatchesTarget(
            ProceedingJoinPoint joinPoint,
            PagePermission permission,
            String targetPageId) {
        PagePermissionGroup group = findGroup(permission, targetPageId).orElse(null);
        // Ref目标必须同时存在于@PagePermission中，否则不能成为候选目标。
        // Refの遷移先は@PagePermissionにも存在する必要があり、存在しない場合は候補外です。
        if (group == null) return false;

        // 此方法只负责在多个OPERATION候选之间按code选择目标。
        // PAGE目标由Ref唯一确定，code即使存在也不参与PAGE权限判断。
        // 本メソッドは複数のOPERATION候補をcodeで選択する場合にのみ使用します。
        // PAGEではcodeを権限判定に使用しません。
        if (group.mode() != AccessMode.OPERATION) return false;
        PagePermissionItem item = Arrays.stream(group.items())
                .filter(it -> it.pageId().equals(targetPageId))
                .findFirst()
                .orElse(null);
        if (item == null) return false;
        // OPERATION候选还必须同时满足code归属和动作一致性，避免只按Ref就切换JWT。
        // OPERATION候補ではcodeの所属とactionの一致も必須とし、RefだけでJWTが切り替わることを防ぎます。
        Optional<String> code = codeResolver.resolveOptional(joinPoint, group.codeField());
        return code.filter(value -> Arrays.asList(item.codes()).contains(value)).isPresent()
                && pageIdParser.operation(targetPageId) == item.action();
    }

    /**
     * 按pageId查找规则，不依赖显式groupId。
     * PAGE的pageId直接配置在组上；OPERATION的pageId配置在具体item上。
     * pageIdでルールを検索し、明示的なgroupIdには依存しません。
     * PAGEのpageIdはグループに、OPERATIONのpageIdは各itemに定義されます。
     */
    private Optional<PagePermissionGroup> findGroup(PagePermission permission, String pageId) {
        return Arrays.stream(permission.groups())
                .filter(group -> group.mode() == AccessMode.PAGE
                        ? group.pageId().equals(pageId)
                        : Arrays.stream(group.items()).anyMatch(item -> item.pageId().equals(pageId)))
                .findFirst();
    }

    private void denyReference(String sourcePageId) {
        throw new PagePermissionException(
                PagePermissionError.PAGE_REFERENCE_DENIED,
                "No page reference from source page " + sourcePageId);
    }
}
