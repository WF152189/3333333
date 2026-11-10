package com.example.pagepermission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 声明业务API允许哪些页面调用。
 * 实际认证由 {@code PagePermissionAspect} 统一执行，Controller无需重复编写权限代码。
 * 業務APIを呼び出せるページを宣言します。
 * 実際の認証・認可は {@code PagePermissionAspect} が一元的に実行します。
 */
public @interface PagePermission {
    /**
     * 一个API可以组合多个PAGE或OPERATION认证规则，但不再重复配置groupId。
     * 1つのAPIに複数のPAGE・OPERATIONルールを組み合わせられます。groupIdの重複設定は不要です。
     */
    PagePermissionGroup[] groups();
}
