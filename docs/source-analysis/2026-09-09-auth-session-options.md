# 登录会话方案比较

2026-09-09 建立比较，2026-09-10 补充确认双令牌机制。首版采用 Spring Security + 短期 JWT 访问令牌 + 刷新令牌，覆盖此前 Spring Security + Spring Session Redis 的选择；下文保留比较依据，不代表功能已经实现。讨论前提：Vue Web、原生微信小程序、Java 业务层、Python AI 服务、已选 Redis；现有 Spring Boot 3.4.2 / Java 21。

| 方案 | 适配与代价 |
| --- | --- |
| Spring Security + Spring Session Redis | 登录状态放在服务端；撤销会话后，后续请求无法继续凭该会话访问。受保护访问依赖 Redis 的可用性与容量。Spring Session 3.4 的 REST 示例支持 `X-Auth-Token` 请求头，服务端会话并非只能通过网页 Cookie 传输。[Spring 官方示例](https://docs.spring.io/spring-session/reference/3.4/guides/java-rest.html) |
| Spring Security + JWT access/refresh | 签名与声明允许接收方验证 JWT；短期 access token 配合 refresh token 可续期，但需管理刷新、过期和撤销。若要求退出后立即拒绝尚未到期的 access token，需要撤销名单等额外状态；只撤销 refresh token 不足以让既有 access token 立即失效。[JWT 标准](https://www.rfc-editor.org/rfc/rfc7519.html)、[OWASP 撤销说明](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html) |

此前讨论的 Web Cookie、小程序请求头方式只是候选，不是随 JWT 一同确定的传输方案。JWT 是令牌格式，浏览器的令牌保存位置、请求携带方式须另行设计。若使用 Cookie，应考虑 `HttpOnly`、`Secure` 和适合部署域名的 `SameSite`；HttpOnly 只能限制脚本读取 Cookie，不能替代 CSRF 防护。[OWASP 会话安全](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#cookies)

Cookie 与 header 双入口的解析规则仍待设计；不能因有小程序入口就全局关闭 CSRF。Web 写操作与登录、退出后的 CSRF token 更新应按实际 Spring Security 版本处理；当前文档中的新 API 不能直接视为 Boot 3.4.2 可用。[Spring CSRF 指南](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

用户已确认短期 JWT access token 配合 refresh token 的双令牌机制：访问令牌用于受保护业务请求，刷新令牌只用于换取新访问令牌，减少重复输入密码；不把刷新令牌用于普通业务接口。有效期和退出范围按下文已确认规则执行，客户端保存位置、闲置超时与退出后的失效机制尚未确认。刷新轮换与失效须纳入实现设计，重用检测、并发刷新处理和具体存储方案继续细化；refresh token 的具体格式仍待选择，不要求也使用 JWT。这里借鉴 RFC 的刷新令牌保护原则，不据此引入 OAuth 授权服务器或独立认证服务。[RFC 9700 §4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)

2026-09-10 用户确认以下本机演示有效期参数，不把它们表述为统一安全标准：

| 项目 | 已确认上限 |
| --- | --- |
| 单个 JWT 访问令牌 | 从签发起最长 15 分钟，且不能越过本次登录截止点 |
| 游客账号一次登录 | 从登录成功起最长 7 天 |
| 后台账号一次登录 | 从登录成功起最长 8 小时 |

上述 7 天和 8 小时是整次登录的绝对期限，不是账号有效期，也不是闲置时间。刷新和刷新令牌轮换均不重置起算点；临近期限签发的新访问令牌须缩短有效期，不能越过原截止点。达到绝对期限后不能继续刷新或凭该次登录的令牌访问受保护接口，须重新登录；访问令牌到期但仍可合法刷新时不要求用户每 15 分钟输入密码。退出后的凭据失效机制、闲置超时及 Python WebSocket 连接失效处理仍待设计，不因期限确认就声称既有长连接会自动断开。

2026-09-10 用户确认退出范围：点击“退出登录”只结束当前这次登录，共享该登录凭据的浏览器标签页一起退出，不连带退出小程序或其他独立登录。这里的范围是登录会话，不是仅当前页面，也不直接等同于整台设备。已保存的个人行程、草稿和订单保留，重新登录同一账号后仍可查看及执行其状态允许的操作；不因退出删除业务记录。此决定不自动新增“退出所有设备”、设备管理或后台与游客登录隔离功能；撤销生效时点、已签发访问令牌及现有 AI 连接如何失效仍待确定。

此前优先服务端会话的理由是集中管理登录和退出，并复用已选 Redis；该倾向已被最新 JWT 决定替代。当前前端还直连 Python WebSocket，并非所有请求都经过 Java；JWT 为 Java 与 Python 各自校验身份提供了可行方向，但不意味着自动实现跨端互通、即时撤销或更高安全性。Spring Security 保留为 Java 认证与授权框架，其 JWT 校验支持不要求本项目额外建设 OAuth 授权服务器；依赖和配置须按现有 Boot 版本配套核对，不照搬最新文档 API。[Spring Security JWT 官方说明](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

这里“已有 Redis”指已选入项目技术栈，不代表认证已接通或运行可用。Redis 继续保留，但不再采用 Spring Session Redis 作为首版登录机制；是否用 Redis 保存刷新或撤销状态需随令牌生命周期设计确定。账号与业务数据仍由 MySQL 持久化，两端各持登录凭据并映射同一内部用户，不共享固定游客 ID。本机 HTTP 演示的 Cookie 属性、同源代理与真机访问路径须另行设计，不能据此承诺现有地址直接可用；正式部署的安全传输要求不以演示配置替代。

已确认选择：Spring Security + 短期 JWT 访问令牌 + 刷新令牌，以及上述访问令牌与登录绝对期限、仅退出当前登录会话的范围。待细化项包括令牌签发与签名密钥管理、固定允许的签名算法、签发方和接收方校验、闲置超时、刷新实现、撤销及其生效时点、Cookie/header 传输及密码找回。上述确认不等于已确定后台登录隔离或 WebSocket 重连行为。Java 与 Python 须按约定校验凭据，业务访问还须检查权限与数据归属，不能只解码 JWT 就信任其中字段。[JWT 标准](https://www.rfc-editor.org/rfc/rfc7519.html)

Python `/ws/assistant` 的身份接入仍待设计和实施：凭据传递方式、握手或首条消息鉴权的选择、由 Python 自验或 Java 代验、避免令牌进入 URL 或日志、已建立连接的过期与撤销处理，不能视为 JWT 选型自动解决。鉴权完成前不得处理受保护的业务消息；AI 访问私人行程和草稿须使用经校验的用户身份。Java 仍是业务写入边界，不能把游客 JWT 等同于内部服务访问凭据。

游客顶栏右侧为“我的”，后台仅向授权开发和运营人员开放的要求不变；普通自助注册不能取得管理权限。后台权限已确认仅区分游客与后台管理人员，开发者和运营者各用独立账号、共用一档既定后台业务权限；后台账号已确认由开发通过初始化脚本创建，不开放后台自助注册或人员管理、授权页面，账号凭据不写入公开仓库；登录隔离与密码恢复细节仍待讨论，不把普通登录成功等同于拥有管理权限。

核对范围：官方文档与标准；未运行测试、构建或业务接口，未修改业务代码。
