package com.example.pagepermission.config;

import com.example.pagepermission.annotation.PagePermission;
import com.example.pagepermission.annotation.AccessMode;
import com.example.pagepermission.annotation.PagePermissionGroup;
import com.example.pagepermission.annotation.PagePermissionItem;
import com.example.pagepermission.annotation.PagePermissionRef;
import com.example.pagepermission.annotation.PagePermissionRefItem;
import com.example.pagepermission.core.PageIdParser;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PermissionAnnotationValidator implements SmartInitializingSingleton {
    private final ApplicationContext applicationContext;
    private final PageIdParser pageIdParser;

    public PermissionAnnotationValidator(
            ApplicationContext applicationContext,
            PageIdParser pageIdParser) {
        this.applicationContext = applicationContext;
        this.pageIdParser = pageIdParser;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // Spring Bean全部创建后扫描Controller方法，让错误配置在启动阶段直接失败。
        // すべてのSpring Bean生成後にControllerメソッドを走査し、設定不備を起動時に検出します。
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (RuntimeException ignored) {
                continue;
            }
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            for (Method method : targetClass.getDeclaredMethods()) {
                PagePermission permission = method.getAnnotation(PagePermission.class);
                PagePermissionRef reference = method.getAnnotation(PagePermissionRef.class);
                if (permission != null) {
                    validate(method, permission, reference);
                } else if (reference != null) {
                    fail(method, "@PagePermissionRef requires @PagePermission");
                }
            }
        }
    }

    void validate(Method method, PagePermission permission, PagePermissionRef reference) {
        if (permission.groups().length == 0) {
            fail(method, "groups must not be empty");
        }
        Set<String> targetPages = new HashSet<>();
        // Ref校验时需要反查目标页面允许的codes。
        // Ref検証では、遷移先ページに許可されたcodeを逆引きする必要があります。
        Map<String, PagePermissionItem> itemsByPage = new HashMap<>();
        Map<String, AccessMode> modeByPage = new HashMap<>();
        for (PagePermissionGroup group : permission.groups()) {
            if (group.mode() == AccessMode.PAGE) {
                if (group.pageId().isBlank() || group.items().length != 0) {
                    fail(method, "PAGE requires exactly one pageId and no items");
                }
                validatePageId(method, group.pageId(), "PAGE pageId");
                if (!targetPages.add(group.pageId())) {
                    fail(method, "duplicate pageId: " + group.pageId());
                }
                modeByPage.put(group.pageId(), AccessMode.PAGE);
                continue;
            }

            if (!group.pageId().isBlank() || group.items().length == 0 || group.codeField().isBlank()) {
                fail(method, "OPERATION requires codeField and items, and must not configure pageId");
            }
            for (PagePermissionItem item : group.items()) {
                validatePageId(method, item.pageId(), "OPERATION pageId");
                if (!targetPages.add(item.pageId())) {
                    fail(method, "duplicate pageId: " + item.pageId());
                }
                if (pageIdParser.operation(item.pageId()) != item.action()) {
                    fail(method, "pageId action differs from item.action: " + item.pageId());
                }
                if (item.codes().length == 0
                        || Arrays.stream(item.codes()).anyMatch(String::isBlank)
                        || new HashSet<>(Arrays.asList(item.codes())).size() != item.codes().length) {
                    fail(method, "codes must be non-empty and unique for " + item.pageId());
                }
                itemsByPage.put(item.pageId(), item);
                modeByPage.put(item.pageId(), AccessMode.OPERATION);
            }
        }

        if (reference != null) {
            if (reference.items().length == 0) {
                fail(method, "Ref items must not be empty");
            }
            Set<String> refs = new HashSet<>();
            // 同一来源页面关联多个N:1目标时，code必须互斥，否则无法唯一确定目标pageId。
            // 同じ遷移元から複数のN対1遷移先を参照する場合、遷移先pageIdを一意に決定できるよう
            // codeが重複しないことを検証します。
            Map<String, Set<String>> codesBySource = new HashMap<>();
            Set<String> pageModeSources = new HashSet<>();
            Set<String> operationModeSources = new HashSet<>();
            for (PagePermissionRefItem item : reference.items()) {
                validatePageId(method, item.targetPageId(), "Ref targetPageId");
                if (item.sourcePageIds().length == 0
                        || Arrays.stream(item.sourcePageIds()).anyMatch(String::isBlank)
                        || new HashSet<>(Arrays.asList(item.sourcePageIds())).size()
                        != item.sourcePageIds().length) {
                    fail(method, "sourcePageIds must be non-empty and unique for target: "
                            + item.targetPageId());
                }
                Arrays.stream(item.sourcePageIds())
                        .forEach(source -> validatePageId(method, source, "Ref sourcePageId"));
                if (!targetPages.contains(item.targetPageId())) {
                    fail(method, "Ref target must exist in @PagePermission: " + item.targetPageId());
                }
                for (String sourcePageId : item.sourcePageIds()) {
                    if (!refs.add(sourcePageId + "->" + item.targetPageId())) {
                        fail(method, "duplicate Ref: " + sourcePageId + "->" + item.targetPageId());
                    }
                    if (modeByPage.get(item.targetPageId()) == AccessMode.PAGE) {
                        if (pageModeSources.contains(sourcePageId)
                                || operationModeSources.contains(sourcePageId)) {
                            fail(method, "One source cannot reference PAGE and other targets: "
                                    + sourcePageId);
                        }
                        pageModeSources.add(sourcePageId);
                        continue;
                    }
                    if (pageModeSources.contains(sourcePageId)) {
                        fail(method, "One source cannot reference PAGE and other targets: "
                                + sourcePageId);
                    }
                    operationModeSources.add(sourcePageId);
                    Set<String> sourceCodes = codesBySource.computeIfAbsent(
                            sourcePageId, ignored -> new HashSet<>());
                    for (String code : itemsByPage.get(item.targetPageId()).codes()) {
                        if (!sourceCodes.add(code)) {
                            fail(method, "Ref targets for one source must have unique codes: "
                                    + sourcePageId + ", code=" + code);
                        }
                    }
                }
            }
        }
    }

    private void fail(Method method, String message) {
        throw new IllegalStateException(method.toGenericString() + ": " + message);
    }

    private void validatePageId(Method method, String pageId, String field) {
        try {
            pageIdParser.groupId(pageId);
        } catch (RuntimeException ex) {
            // 注解中的非法pageId属于启动配置错误，不应作为运行时权限异常暴露。
            // 注解に設定された不正なpageIdは起動時設定エラーとして扱い、実行時権限例外として公開しません。
            throw new IllegalStateException(
                    method.toGenericString() + ": invalid " + field + ": " + pageId,
                    ex);
        }
    }
}
