# Page Permission Spring Boot Demo

这是页面权限注解与 AOP 的可运行 Spring Boot 后端，与 `../page-navigation-demo` 联调。

## 启动与测试

```powershell
mvn spring-boot:run
mvn test
```

服务地址为 `http://127.0.0.1:8080`。测试覆盖父页面认证、A→B→C 跨组 JWT 链、同页面不换 JWT、非法 code 拒绝以及 AOP 单元测试。

This module implements the business-API side of the page-permission design.

- Parent-page authentication remains an Angular Guard + page-authentication API concern.
- Child-page related calls are validated by `@PagePermissionRef` and `@PagePermission`.
- A new page token is issued only after a related target controller returns normally.
- Ordinary same-page API calls keep the current page token.

Integrate the module by providing a production `PageTokenService` implementation and,
if necessary, replacing `HeaderPageTokenResponseWriter` with the response abstraction
used by the host project.
