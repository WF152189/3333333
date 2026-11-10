package com.example.pagepermission.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 同一个API中的一组认证规则。这里的“Group”是注解结构上的规则容器，不是groupId。
 * 同一API内の認証ルールをまとめるコンテナです。ここでのGroupはgroupIdを意味しません。
 *
 * <p>PAGE模式用于页面与API为1:1的情况，只配置一个pageId；OPERATION模式用于多个
 * 动作页面共用同一个API的N:1情况，配置codeField和一个或多个动作item。同一个
 * {@code @PagePermission}可以同时包含两种模式，使共享API兼容1:1与N:1调用。</p>
 *
 * <p>PAGEモードは画面とAPIが1対1の場合に使用し、pageIdを1件だけ設定します。
 * OPERATIONモードは複数の操作画面が同一APIを共有するN対1の場合に使用し、codeFieldと
 * 1件以上のitemを設定します。両モードを同じAPIに併記することも可能です。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PagePermissionGroup {
    /**
     * 规则模式；决定认证时是否需要读取code及验证action。
     * ルールモードです。codeの読込みとaction検証が必要かどうかを決定します。
     */
    AccessMode mode() default AccessMode.PAGE;

    /**
     * PAGE模式唯一允许的页面ID；OPERATION模式必须留空。
     * PAGEモードで許可する唯一のpageIdです。OPERATIONモードでは空にします。
     */
    String pageId() default "";

    /**
     * OPERATION模式从Controller参数、Map或DTO中读取操作code时使用的字段名。
     * OPERATIONモードでController引数、Map、DTOからcodeを取得するためのフィールド名です。
     */
    String codeField() default "code";

    /**
     * OPERATION模式的动作页面规则；PAGE模式必须为空。
     * OPERATIONモードの操作ページルールです。PAGEモードでは空にします。
     */
    PagePermissionItem[] items() default {};
}
