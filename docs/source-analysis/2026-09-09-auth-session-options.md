# 登录会话方案比较

2026-09-09。用户已确认首版采用 Spring Security + Spring Session Redis；下文保留比较依据，不代表功能已经实现。讨论前提：Vue Web、原生微信小程序、Java 业务层、已选 Redis；现有 Spring Boot 3.4.2 / Java 21。

| 方案 | 适配与代价 |
| --- | --- |
| Spring Security + Spring Session Redis | 登录状态放在服务端；撤销会话后，后续请求无法继续凭该会话访问。受保护访问依赖 Redis 的可用性与容量。Spring Session 3.4 的 REST 示例支持 `X-Auth-Token` 请求头，服务端会话并非只能通过网页 Cookie 传输。[Spring 官方示例](https://docs.spring.io/spring-session/reference/3.4/guides/java-rest.html) |
| Spring Security + JWT access/refresh | 签名与声明允许接收方验证 JWT；短期 access token 配合 refresh token 可续期，但需管理刷新、过期和撤销。若要求退出后立即拒绝尚未到期的 access token，需要撤销名单等额外状态；只撤销 refresh token 不足以让既有 access token 立即失效。[JWT 标准](https://www.rfc-editor.org/rfc/rfc7519.html)、[OWASP 撤销说明](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html) |

拟议传输方式：Web 使用 `HttpOnly`、`Secure` Cookie，并配置适合部署域名的 `SameSite`；小程序显式携带会话请求头。HttpOnly 只能限制脚本读取 Cookie，不能替代 CSRF 防护。[OWASP 会话安全](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#cookies)

Cookie 与 header 双入口的解析规则仍待设计；不能因有小程序入口就全局关闭 CSRF。Web 写操作与登录、退出后的 CSRF token 更新应按实际 Spring Security 版本处理；当前文档中的新 API 不能直接视为 Boot 3.4.2 可用。[Spring CSRF 指南](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

若选 JWT，建议将 refresh token 轮换、重用检测列入设计；这里借鉴 RFC 的刷新令牌保护原则，不据此引入 OAuth 授权服务器。[RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)

本项目设计判断：优先选服务端会话。Java 统一鉴权且已有 Redis，集中管理登录与退出更贴合当前规模；采用 JWT 不构成天然更安全的理由。依赖须按现有 Boot 版本配套核对，不直接采用 Session 4.x 示例。

这里“已有 Redis”指已选入项目技术栈，不代表会话已接通或运行可用。账号与业务数据仍由 MySQL 持久化，Redis 会话只管理登录状态；两端拟各持独立会话并映射同一内部用户。本机 HTTP 演示的 Cookie 属性、同源代理与真机访问路径须另行设计，不能据此承诺现有地址直接可用；正式部署的安全传输要求不以演示配置替代。

已确认选择：Spring Security + Spring Session Redis；JWT access/refresh 不纳入首版。Cookie/header 具体传输、会话期限、退出范围及密码找回仍待细化。用户同时要求后台仅向开发和运营人员开放，游客顶栏右侧改为“我的”；具体后台权限分档与管理账号产生方式继续讨论，不把普通登录成功等同于拥有管理权限。

核对范围：官方文档与标准；未运行测试、构建或业务接口，未修改业务代码。
