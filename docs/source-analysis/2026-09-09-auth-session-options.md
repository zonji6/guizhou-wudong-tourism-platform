# 登录会话方案比较

2026-09-09。用户最新选择首版采用 Spring Security + JWT，覆盖此前 Spring Security + Spring Session Redis 的选择；下文保留比较依据，不代表功能已经实现。讨论前提：Vue Web、原生微信小程序、Java 业务层、Python AI 服务、已选 Redis；现有 Spring Boot 3.4.2 / Java 21。

| 方案 | 适配与代价 |
| --- | --- |
| Spring Security + Spring Session Redis | 登录状态放在服务端；撤销会话后，后续请求无法继续凭该会话访问。受保护访问依赖 Redis 的可用性与容量。Spring Session 3.4 的 REST 示例支持 `X-Auth-Token` 请求头，服务端会话并非只能通过网页 Cookie 传输。[Spring 官方示例](https://docs.spring.io/spring-session/reference/3.4/guides/java-rest.html) |
| Spring Security + JWT access/refresh | 签名与声明允许接收方验证 JWT；短期 access token 配合 refresh token 可续期，但需管理刷新、过期和撤销。若要求退出后立即拒绝尚未到期的 access token，需要撤销名单等额外状态；只撤销 refresh token 不足以让既有 access token 立即失效。[JWT 标准](https://www.rfc-editor.org/rfc/rfc7519.html)、[OWASP 撤销说明](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html) |

此前讨论的 Web Cookie、小程序请求头方式只是候选，不是随 JWT 一同确定的传输方案。JWT 是令牌格式，浏览器的令牌保存位置、请求携带方式须另行设计。若使用 Cookie，应考虑 `HttpOnly`、`Secure` 和适合部署域名的 `SameSite`；HttpOnly 只能限制脚本读取 Cookie，不能替代 CSRF 防护。[OWASP 会话安全](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#cookies)

Cookie 与 header 双入口的解析规则仍待设计；不能因有小程序入口就全局关闭 CSRF。Web 写操作与登录、退出后的 CSRF token 更新应按实际 Spring Security 版本处理；当前文档中的新 API 不能直接视为 Boot 3.4.2 可用。[Spring CSRF 指南](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

短期 JWT access token 配合 refresh token 是后续候选方案，用户尚未确认是否采用双令牌、有效期或续期体验。若采用 refresh token，建议将轮换、重用检测列入设计；refresh token 不要求也使用 JWT。这里借鉴 RFC 的刷新令牌保护原则，不据此引入 OAuth 授权服务器。[RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)

此前优先服务端会话的理由是集中管理登录和退出，并复用已选 Redis；该倾向已被最新 JWT 决定替代。当前前端还直连 Python WebSocket，并非所有请求都经过 Java；JWT 为 Java 与 Python 各自校验身份提供了可行方向，但不意味着自动实现跨端互通、即时撤销或更高安全性。Spring Security 保留为 Java 认证与授权框架，其 JWT 校验支持不要求本项目额外建设 OAuth 授权服务器；依赖和配置须按现有 Boot 版本配套核对，不照搬最新文档 API。[Spring Security JWT 官方说明](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

这里“已有 Redis”指已选入项目技术栈，不代表认证已接通或运行可用。Redis 继续保留，但不再采用 Spring Session Redis 作为首版登录机制；是否用 Redis 保存刷新或撤销状态需随令牌生命周期设计确定。账号与业务数据仍由 MySQL 持久化，两端各持登录凭据并映射同一内部用户，不共享固定游客 ID。本机 HTTP 演示的 Cookie 属性、同源代理与真机访问路径须另行设计，不能据此承诺现有地址直接可用；正式部署的安全传输要求不以演示配置替代。

已确认选择：Spring Security + JWT。待细化项包括令牌签发与签名密钥管理、固定允许的签名算法、签发方和接收方校验、有效期、刷新、撤销、退出范围、Cookie/header 传输及密码找回。Java 与 Python 须按约定校验凭据，业务访问还须检查权限与数据归属，不能只解码 JWT 就信任其中字段。[JWT 标准](https://www.rfc-editor.org/rfc/rfc7519.html)

Python `/ws/assistant` 的身份接入仍待设计和实施：凭据传递方式、握手或首条消息鉴权的选择、由 Python 自验或 Java 代验、避免令牌进入 URL 或日志、已建立连接的过期与撤销处理，不能视为 JWT 选型自动解决。鉴权完成前不得处理受保护的业务消息；AI 访问私人行程和草稿须使用经校验的用户身份。Java 仍是业务写入边界，不能把游客 JWT 等同于内部服务访问凭据。

游客顶栏右侧为“我的”，后台仅向授权开发和运营人员开放的要求不变；普通自助注册不能取得管理权限。后台权限已确认仅区分游客与后台管理人员，开发者和运营者各用独立账号、共用一档既定后台业务权限；管理账号产生方式继续讨论，不把普通登录成功等同于拥有管理权限。

核对范围：官方文档与标准；未运行测试、构建或业务接口，未修改业务代码。
