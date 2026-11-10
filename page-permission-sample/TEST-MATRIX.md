# 页面权限认证测试总结与追加计划

最后执行日期：2026-11-03（Asia/Tokyo）

执行命令：

```text
mvn test
```

最新结果：

```text
Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

> 本文件只把实际执行成功的项目标记为“已通过”。旧版“64项通过”的记录已经失效，
> 当前结果对应 `sourcePageIds[] + targetPageId` 的最新注解结构。

## 1. 测试层次与数量

| 层次 | 测试类 | 执行数 | 状态 | 主要职责 |
|---|---|---:|---|---|
| 单体/AOP | `PagePermissionAspectTest` | 9 | 已通过 | AOP执行顺序、Ref、Controller执行及JWT更新时点 |
| 单体/配置 | `PermissionAnnotationValidatorTest` | 24 | 已通过 | 注解配置的启动校验 |
| 单体/解析 | `PageIdParserTest` | 11 | 已通过 | pageId格式、组和动作解析 |
| 单体/JWT | `RsaPageTokenServiceTest` | 7 | 已通过 | RS256 JWT签发、验签、过期、issuer和audience |
| Spring Boot结合测试 | `PagePermissionIntegrationTest` | 37 | 已通过 | MVC、AOP、Controller、JWT、CORS和共享API联动 |
| 合计 |  | **88** | **全部通过** | 0失败、0错误、0跳过 |

参数化测试的每组输入按一次测试执行计数，因此方法数量少于88。

## 2. 已通过的单体测试

### 2.1 AOP与权限解析

- 当前页面调用受保护API成功，Controller执行，但不签发新JWT。
- 存在Ref的跨页面调用成功，Controller正常返回后签发目标JWT。
- 同一目标配置多个 `sourcePageIds` 时，列表中的任意来源均可调用。
- JWT来源不在目标的 `sourcePageIds` 中时，以 `PAGE_REFERENCE_DENIED` 拒绝。
- 跨页面调用缺少Ref时，在Controller执行前拒绝。
- Controller抛出异常时不签发、不写入目标JWT。
- JWT签发失败时转换为 `PAGE_TOKEN_ISSUE_FAILED`。
- 响应头写入失败时转换为 `PAGE_TOKEN_ISSUE_FAILED`。
- 重复code导致多个目标同时匹配时，以 `PAGE_REFERENCE_AMBIGUOUS` 拒绝。

为避免Windows上的Mockito/JVM Attach问题，AOP测试使用显式Fake和JDK动态代理，测试运行不再依赖Mockito。

### 2.2 注解启动校验

- 合法的多item配置通过。
- `groups`为空时失败。
- pageId重复时失败。
- PAGE和OPERATION之间pageId重复时失败。
- pageId动作位与 `action`不一致时失败。
- item内code重复、为空或包含空字符串时失败。
- Ref目标不存在于 `@PagePermission`时失败。
- 相同 `source → target`重复声明时失败。
- 同一来源的多个OPERATION目标code重叠时失败。
- 不同来源可以使用相同code指向不同目标。
- PAGE未配置pageId时失败。
- PAGE配置items时失败。
- OPERATION配置pageId时失败。
- OPERATION没有items时失败。
- OPERATION的 `codeField`为空时失败。
- 同一来源关联多个无法区分的PAGE目标时失败。
- `sourcePageIds`为空、包含空字符串、包含重复值或格式非法时失败。
- `@PagePermissionRef.items`为空时失败。
- 只有 `@PagePermissionRef`、没有 `@PagePermission`时，启动扫描失败。
- 注解中的非法pageId统一转换为启动配置异常 `IllegalStateException`。

### 2.3 pageId解析

- 5位数字pageId正常解析组ID和QUERY/CREATE/UPDATE/DELETE动作。
- null、空字符串、非数字、长度不足和长度超出时拒绝。
- 末位不是已定义动作时拒绝。

### 2.4 JWT

- 合法JWT保留pageId、用户和会话claims。
- 跨页面JWT更新pageId和jti，保留用户与会话。
- 过期JWT拒绝。
- 在 `exp`边界时立即拒绝。
- 格式错误JWT拒绝。
- 即使签名正确，issuer错误也拒绝。
- 即使签名正确，audience错误也拒绝。

## 3. 已通过的Spring Boot结合测试

### 3.1 父页面与连续跳转

- 菜单父页面通过页面认证API取得真实JWT。
- A→B通过目标业务API认证并取得JWT(B)。
- B→C继续使用JWT(B)，成功取得JWT(C)。
- 同页面调用不重复签发JWT。
- 不存在Ref的跨页面调用返回HTTP 403。
- Controller异常返回HTTP 500且不返回目标JWT。

### 3.2 PAGE与OPERATION共享API

共享API同时配置：

| pageId | mode/action | codes |
|---|---|---|
| 56781 | PAGE | 不使用code |
| 12341 | QUERY | 1、2、3 |
| 12342 | CREATE | 4、5、6 |
| 12343 | UPDATE | 7、8、9 |
| 12344 | DELETE | 10、11、12 |

已通过：

- 先按JWT中的sourcePageId筛选Ref目标，不能用code是否存在来选择PAGE或OPERATION。
- source只关联一个PAGE目标时，即使请求携带code也选择该PAGE目标，并完全忽略code权限判断。
- source关联多个目标时，只允许全部为OPERATION，再使用code从中选择唯一目标。
- 同一source同时关联PAGE与其他目标时，启动校验失败。
- code 1～12分别唯一选择正确动作页面。
- 目标页面再次调用共享API时不更新JWT。
- 当前动作页面使用其他动作的code时拒绝。
- 当前OPERATION页面不传code时返回 `OPERATION_CODE_REQUIRED`。
- 非法code在Controller执行前拒绝。

### 3.3 `sourcePageIds`目标中心模型

- 11001、22001、22002均可调用同一目标56781。
- 不在来源列表中的33001调用56781时返回 `PAGE_REFERENCE_DENIED`。
- 先根据请求形态确定目标，再检查该目标的来源列表。
- 22001只关联PAGE目标56781；即使携带与OPERATION规则相同的code，也仍然访问56781。
- 认证失败时不返回新页面JWT。

### 3.4 Web与安全边界

- 缺少页面JWT时返回HTTP 403和 `PAGE_TOKEN_REQUIRED`。
- 篡改JWT时返回HTTP 403和 `PAGE_TOKEN_INVALID`。
- Bearer前缀和无前缀JWT均可读取。
- pageId为空、非数字或长度非法时拒绝。
- 格式正确但不是菜单父页面的pageId不能通过Guard认证API。
- 重复认证同一父页面会生成不同JWT。
- 旧JWT在5分钟有效期内仍可继续使用。
- Angular来源CORS预检允许自定义页面JWT请求头。
- 没有 `@PagePermission`的公开API不执行页面权限AOP：无JWT或伪造JWT都不影响调用，也不返回新JWT。

## 4. 测试过程中修复的问题

### 4.1 Windows测试挂起

原因：Mockito运行时需要JVM Attach，在当前Windows环境中无法打开Attach管道。

处理：

- AOP测试改用Fake和JDK动态代理。
- 从测试依赖中排除Mockito。
- 完整测试现在可以稳定结束。

### 4.2 非法注解pageId异常类型不一致

原因：启动校验直接向外抛出运行时 `PagePermissionException`。

处理：注解内非法pageId统一包装为 `IllegalStateException`，明确属于应用启动配置错误。

### 4.3 JWT issuer/audience未校验

原因：旧实现只检查签名和过期时间。

处理：新增固定issuer和audience校验，并补充有效签名但issuer/audience错误的反向测试。

## 5. 尚未执行、后续建议新增的测试

以下项目不计入88项成功结果。

### 5.1 需要先确定设计规则

- 已确定：PAGE模式不使用code进行权限判断；请求携带code时仍按PAGE处理。
- Controller业务成功但目标JWT签发/响应写入失败时，业务事务是否回滚。
- 页面JWT签发失败应使用独立的“响应写入失败”错误码，还是继续使用 `PAGE_TOKEN_ISSUE_FAILED`。
- 旧JWT不立即失效时，是否允许同一旧JWT同时发起A→B和A→C。

这些规则确定后才能写出唯一、稳定的期望断言。

### 5.2 后端建议追加

- JWT缺少sub、sid、jti或pageId的测试。
- JWT的iat位于未来、header算法不符合预期的测试。
- 数字、枚举和null类型code的解析测试。
- Controller具名参数、Map、DTO和record四种code读取方式的独立测试。
- 相同目标被拆分到多个RefItem且来源集合不重叠时的测试。
- 错误响应不得泄露允许的code、注解配置和服务器堆栈。
- 数据库事务与JWT签发失败的结合测试（需要实际持久化层后才能实现）。

### 5.3 Angular真实浏览器结合测试（2026-11-03）

本节使用Codex内置浏览器实际操作Angular页面，并连接真实Spring Boot进程执行，不是Mock浏览器测试。

| 场景 | 实测结果 | 判定 |
|---|---|---|
| 菜单进入父页面A | Guard调用页面认证API，签发pageId=11001的JWT | 通过 |
| A→A1 | 未调用父路由Guard，继承JWT(A) | 通过 |
| A1→A2 | 未调用父路由Guard，pageId与jti均不变 | 通过 |
| A1→B父页面 | 执行B父路由Guard，JWT更新为pageId=12341 | 通过 |
| A1→B1 | 未调用父路由Guard；业务API验证Ref/pageId/code/action后签发JWT(B) | 通过 |
| B1调用当前页面业务API | 认证成功且不重复签发JWT | 通过 |
| A1→B1→C1 | 依次使用JWT(A)、JWT(B)，最终签发pageId=23451 | 通过 |
| 无JWT调用B1业务API | 前端在发出请求前检测到无JWT并拒绝调用 | 通过 |
| 两个浏览器标签页 | 第二个标签页没有读取第一个标签页的sessionStorage JWT | 通过 |
| 地址栏直接输入 `/a/a1` | 可以显示静态页面；未获得页面JWT，不能据此调用受保护API | 通过（API认证范围） |
| 地址栏直接输入 `/b/b1` | 可以显示静态页面；业务操作因无JWT而被拒绝 | 通过（API认证范围） |
| 浏览器返回/前进 | 可以切换显示页面，但不会因此取得目标页面JWT或绕过API认证 | 通过（API认证范围） |

结论：本设计的验收重点是受保护API的访问认证，而不是禁止前端静态页面渲染。按照该范围，核心页面认证API、业务API、PagePermissionRef、pageId/code/action校验、AOP签发JWT及多标签隔离的浏览器结合测试均已通过。直接输入子页面URL或使用浏览器返回/前进，只能改变页面显示；没有合法页面JWT时仍不能成功调用受保护API。

尚未执行的浏览器场景：

- A1→B1认证失败后是否始终保留JWT(A)。
- 刷新已合法进入的子页面时的明确处理规则。
- 双击和快速连续跳转时，旧响应不能覆盖较新的JWT。

## 6. 完成标准

后端当前阶段的完成标准：

- `mvn test`稳定结束；
- 88项测试全部通过；
- 0失败、0错误、0跳过；
- `sourcePageIds[] + targetPageId`新模型已由单体测试和Spring Boot结合测试共同覆盖。

当前已执行的API认证相关浏览器结合测试全部通过。系统最终完成前仍建议补完第5.3节列出的失败路径、刷新和快速连续请求测试。
