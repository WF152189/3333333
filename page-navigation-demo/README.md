# 页面权限跳转 Demo

这是一个可直接运行的 Angular Demo，用来验证当前确定的页面跳转与页面授权 JWT 更新规则。认证请求会实际发送到同级目录中的 Spring Boot 项目 `page-permission-sample`。

## 运行

先在一个终端启动 Spring Boot：

```powershell
cd ..\page-permission-sample
mvn spring-boot:run
```

再在另一个终端启动 Angular：

```powershell
cd ..\page-navigation-demo
npm install
npm start
```

然后访问 <http://localhost:4200/>。

生产构建验证：

```powershell
npm run build
```

## 路由规则

- 父页面 `/a`、`/b` 配置 `parentPageGuard`。进入前由 Guard 调用页面认证 API，并保存新的页面授权 JWT。
- 子页面采用扁平路由配置：`/a/a1`、`/a/a2`、`/b/b1`、`/b/b2`、`/c/c1`。
- 子页面不配置 Guard，也不会因为 URL 中含有父路径而执行父页面 Guard。
- 同组子页面继承相同 `pageId`，跳转时不更新 JWT。
- 跨组子页面跳转在目标页面初始化时调用目标业务 API，由 API 层验证 `PagePermissionRef`、`pageId`、`code` 和 `action`；Controller 正常返回后更新 JWT。

## 建议测试顺序

1. 点击“父页面 A”：Guard 调用页面认证 API，JWT 变为 `11001`。
2. 进入 A1，再进入 A2：不调用 Guard，JWT 保持 `11001`。
3. 从 A1 进入“B父页面”：B 父路由 Guard 执行，JWT 变为 `12341`。
4. 点击“重置Demo”，重新进入 A → A1 → B1：不调用 B 父路由 Guard，由 B 业务 API 验证 `11001 -> 12341`，成功后更新 JWT。
5. 从 B1 进入 C1：C 业务 API 使用 JWT(B) 验证 `12341 -> 23451`，成功后更新 JWT。

右侧“执行日志”会逐步显示 Guard、API、AOP 和 JWT 的处理结果。

## 关键文件

- `src/app/app.routes.ts`：父页面 Guard 与扁平子页面路由配置。
- `src/app/parent-page.guard.ts`：父页面进入前的认证处理。
- `src/app/mock-permission.service.ts`：调用 Spring Boot 页面认证 API 和业务 API，并保存响应头返回的 JWT。
- `src/app/demo-state.service.ts`：页面授权 JWT 与执行日志状态。
- `src/app/pages.ts`：各场景页面及跳转按钮。
