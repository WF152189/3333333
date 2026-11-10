package com.example.pagepermission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 声明从其他页面进入当前API所代表目标页面的合法关联。
 * 他ページから、現在のAPIが表す遷移先ページへ遷移できる正当な関連を宣言します。
 */
public @interface PagePermissionRef {
    PagePermissionRefItem[] items();
}
