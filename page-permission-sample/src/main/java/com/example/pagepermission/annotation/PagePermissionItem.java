package com.example.pagepermission.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
/**
 * 一个父页面（其子页面继承同一pageId）在当前API上的权限。
 * 親ページと、そのpageIdを継承する子ページが現在のAPIに対して持つ権限です。
 */
public @interface PagePermissionItem {
    /**
     * 全系统唯一的五位pageId：前四位为组，最后一位为动作。
     * システム全体で一意となる5桁のpageIdです。先頭4桁はグループ、末尾1桁は操作を表します。
     */
    String pageId();
    /**
     * 必须与pageId最后一位代表的动作一致。
     * pageIdの末尾1桁が表す操作と一致する必要があります。
     */
    Operation action();
    /**
     * 该页面调用当前API时允许使用的业务code。
     * このページから現在のAPIを呼び出す際に使用可能な業務codeです。
     */
    String[] codes();
}
