package com.example.pagepermission.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
/**
 * 目标页面允许被哪些来源页面调用。
 * 一条规则只描述一个目标页面，避免多个来源与多个目标形成含义不清的笛卡尔积。
 * 遷移先ページを呼び出せる遷移元ページを宣言します。
 * 1つのルールでは遷移先を1ページだけ指定し、複数の遷移元・遷移先による曖昧な直積を防ぎます。
 */
public @interface PagePermissionRefItem {
    /**
     * 允许调用目标页面的来源pageId集合。
     * 运行时，已验签页面JWT中的pageId必须包含在该集合中。
     * 遷移先ページを呼び出せる遷移元pageIdの集合です。
     * 実行時には、署名検証済みJWTのpageIdがこの集合に含まれている必要があります。
     */
    String[] sourcePageIds();

    /**
     * 被调用的唯一目标pageId；Controller正常返回后，该值会写入新页面JWT。
     * 呼び出される唯一の遷移先pageIdです。Controller正常終了後、新しいページJWTに格納されます。
     */
    String targetPageId();
}
