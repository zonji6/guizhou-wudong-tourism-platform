# tourism-api-v3-draft-r3

**接口修订：** tourism-api-v3-draft-r3

**鉴权帧修订：** wudong-ws-auth-v1

**适用端：** Vue Web、原生微信小程序、Java 业务服务、Python AI 服务

**运行边界：** 本机浏览器、微信开发者工具；Web 为 http://127.0.0.1:5174

**契约状态：** 本文件在已独立验收的 B1 身份、匿名会话与内部访问边界，以及已独立验收的 B2 目录、订单、草稿、回执与旧记录投影上，继续冻结 B3 的知识发布、内部检索、精确候选解析与脱敏运行摘要；不表示代码、配置、迁移、Redis、数据库、代理或运行验收已经完成。

## 1. 范围与规范用语

本修订冻结以下 B1 内容，B2 不改变其授权与安全语义：

- 平台游客账号与后台管理账号的注册、登录、刷新、退出和本人资料。
- RS256 访问 JWT、固定不透明刷新令牌、Redis 登录状态及其原子失效关系。
- Web Cookie、小程序显式凭据、CSRF、Origin、CORS 和本机代理边界。
- 匿名凭据、匿名会话、7 天浏览器载体与最近有效交互起 24 小时的服务端有效期。
- Java 与 Python 的双向服务凭据、用户或匿名证明和方法／路由白名单。
- AI WebSocket 的首帧鉴权、持续有效性检查、拒绝和关闭语义。
- v1／v2 身份方式的停用与保留规则。

本修订同时冻结以下 B2 内容：

- 公开目录、同店菜单、三类核价、三类正式订单、社区与水彩示意地图。
- 已保存行程、食宿草稿、草稿提交、版本条件与调用方数据投影。
- 持久操作槽、规范摘要、成功回执、主动核对及不可变 `NOT_APPLIED` 终态。
- v1／v2 既有目录、预约、订单、社区与知识记录的前向投影和迁移边界。

本修订追加冻结以下 B3 Java 共享边界：

- 知识工作草稿、来源主档、固定生效快照与持久发布任务三表语义。
- 管理端保存、发布、人工重发、下架和状态查询，以及发布请求键、规范摘要、失败指纹、统一锁序和迟到结果拒绝。
- `knowledge-build-inputs`、`active-builds`、`eligibility` 与 Java 关键词检索的严格 DTO 和真实 `READY` 条件。
- 行程、餐食草稿、住宿草稿三类精确候选解析，以及不可变版本、归属、服务器期限、基础版本和联系人保留边界。
- `run-summaries` 的脱敏白名单、去重、乱序和终态围栏。

“必须”“仅可”“不得”是规范要求。未在本文列出的字段一律视为未知字段并拒绝。本文不定义面向客户端的 AI 卡片、业务事件或检查点结构；它们由独立的 `assistant-card-v3` 契约冻结，不能向旧 `2.0` 结构随意加键。本文件第 27 节只冻结 Java 为保存三类业务成果而调用的精确候选解析载荷。

客户端提供的 accountId、userId、visitorId、role、purpose、sid、threadId 或其他标识都不能单独构成授权。Java 从已验证的访问凭据取得账号身份；Python 从已验证的访问凭据和 Redis 关系取得会话身份。

## 2. 共同传输约定

### 2.1 版本与内容类型

- 所有 v3 REST 请求必须携带 X-Wudong-Contract: tourism-api-v3-draft-r3。
- CSRF 获取端点也必须携带该请求头，以便在切换期拒绝旧客户端。
- 所有 JSON 请求必须使用 Content-Type: application/json；字符集为 UTF-8。
- 所有 v3 REST 响应必须携带：
  - X-Wudong-Contract: tourism-api-v3-draft-r3
  - X-Request-Id: 服务端生成的规范 UUID
  - Cache-Control: no-store
- 请求不得通过查询参数、URL、Referer 或重定向携带密码、访问令牌、刷新令牌、匿名凭据或服务凭据。
- 服务端不得跟随会转发任何凭据的重定向。

缺少或不匹配的契约修订返回 HTTP 400 与 CONTRACT_INCOMPATIBLE，不尝试 v1／v2 解析，也不回退旧入口。

### 2.2 固定响应信封

所有字段始终出现。成功时 message、code、details 均为 null：

~~~json
{
  "success": true,
  "data": {},
  "message": null,
  "code": null,
  "details": null
}
~~~

失败时 data 为 null；details 只能使用与 code 对应的封闭对象或 null：

~~~json
{
  "success": false,
  "data": null,
  "message": "可直接展示的中文说明",
  "code": "STABLE_ERROR_CODE",
  "details": null
}
~~~

不得在响应中返回堆栈、SQL、文件路径、内部 URL、密钥内容、凭据摘要、原始上游异常或是否存在他人资源。系统时间使用 UTC RFC 3339 秒精度字符串，例如 2026-09-10T01:02:03Z；JWT 时间声明使用 NumericDate 秒。

### 2.3 严格 JSON

- 请求对象缺省与 null 不等价；只有本文明确允许 null 的字段才可传 null。
- 未知字段、重复 JSON 键、错误类型、非有限数值和未知枚举均返回 VALIDATION_FAILED。
- 字符串不静默截断；错误信封不得回显密码或凭据原值。
- UUID 必须是小写、带连字符的规范字符串。

## 3. 身份、用途与凭据

### 3.1 账号与用途

账号角色只有：

| role | 登录 purpose | 可用范围 |
| --- | --- | --- |
| USER | USER | 公共读取、/api/me/**、登录用户 AI |
| ADMIN | ADMIN | 独立后台；不能用于 /api/me/** 或 Python AI |

游客自助注册只能创建 USER。ADMIN 只能由第 5.9 节的本机交互式初始化入口创建。开发者与运营者使用不同账号，但都只有 ADMIN 这一档业务权限。

同一浏览器的 USER 与 ADMIN 使用不同登录通道和不同 Cookie，可以同时存在。不同浏览器配置、不同小程序安装实例或不同设备形成不同独立登录；退出只撤销发起退出的那一个 sid。

### 3.2 用户名、昵称与密码

- username 输入只允许 3～32 个 ASCII 字母、数字或下划线，正则为 ^[A-Za-z0-9_]{3,32}$。
- 服务端使用 Locale.ROOT 转为小写后保存和唯一比较；不得做其他 Unicode 归一化，也不得静默去除首尾空白。
- nickname 省略或为 null 时取规范化 username；显式空字符串无效。非空昵称去除首尾空白后为 1～40 个 Unicode 码点。
- password 不去空白、不做 Unicode 归一化；至少 8 个 Unicode 码点，UTF-8 编码后最多 72 字节。
- 密码使用 Spring Security DelegatingPasswordEncoder，持久值必须带 {bcrypt} 前缀；bcrypt cost 固定为 12。
- 超过 72 个 UTF-8 字节必须在编码前拒绝，不得截断。

密码编码超限使用固定错误：

~~~json
{
  "success": false,
  "data": null,
  "message": "密码编码后不能超过 72 字节，请缩短后重试。",
  "code": "PASSWORD_ENCODING_LIMIT_EXCEEDED",
  "details": {
    "kind": "password_encoding_limit",
    "encoding": "UTF-8",
    "maxBytes": 72
  }
}
~~~

登录失败统一返回 INVALID_CREDENTIALS，不区分账号不存在、密码错误或角色不匹配。

### 3.3 访问 JWT

访问令牌必须满足：

| 项 | 固定值或规则 |
| --- | --- |
| JOSE alg | RS256，其他算法一律拒绝 |
| RSA 密钥 | 至少 2048 位 |
| 私钥编码 | Java 外置 PKCS#8 PEM |
| 公钥编码 | Java／Python 各自从受信外置 SPKI PEM 文件读取 |
| typ | JWT |
| kid | 必须存在，并等于本机配置的唯一 JWT_KEY_ID |
| iss | wudong-java-local |
| iat、nbf、exp、session_exp | 必须存在；nbf 等于 iat；不允许时钟宽限 |
| sub | 账号 UUID |
| sid | 独立登录 UUID |
| purpose | USER 或 ADMIN |
| role | USER 或 ADMIN，必须与 purpose 对应 |
| aud | USER 固定为 ["wudong-java","wudong-ai"]；ADMIN 固定为 ["wudong-java"] |

exp = min(iat + 900 秒, session_exp)。USER 的 session_exp 不晚于本次登录签发起 604800 秒；ADMIN 不晚于 28800 秒。访问令牌过期不延长登录绝对期限。

Java 是唯一签发者并保管私钥。Python 只接受配置文件指定的 kid、公钥、issuer、audience=wudong-ai 和 purpose=USER。Java／Python 都不得从 token 中的 jku、jwk、x5u、x5c 或任何客户端地址动态取钥，不得降级为 HS256 或无签名解析。

所有受保护 HTTP 请求使用：

~~~text
Authorization: Bearer <access-token>
~~~

Web 和小程序只把访问令牌放在运行内存。访问令牌不得进入 Cookie、本地持久存储、URL、模型输入、检查点或日志。

### 3.4 固定刷新令牌

- 每次独立登录生成 32 个密码学安全随机字节，使用无填充 Base64url 编码，得到 43 字符不透明 refreshToken。
- refreshToken 不是 JWT，不编码账号、sid、期限或用途。
- 同一 sid 有效期间固定复用原 refreshToken。成功刷新只签发新访问 JWT，不轮换、不回显、不重新设置刷新 Cookie，也不延长 session_exp 或 Redis TTL。
- Redis 仅保存 SHA-256(refreshToken 的 ASCII 字节) 的小写十六进制摘要；不得保存原值。
- 摘要不是客户端凭据，接口不得接受摘要替代原 refreshToken。

### 3.5 浏览器登录通道与迟到 Cookie

Web USER、Web ADMIN 与每个 mini 安装实例各有独立登录通道。通道由 surface、purpose、不具授权能力的随机 laneId、单调 authGeneration 和当前 sid 组成。surface 固定为 WEB 或 MINI；laneId 只能用于并发协调，不能读取账号资料或替代 CSRF／登录凭据。

刷新 Cookie 使用按 sid 隔离的名称：

- USER：WD_WEB_REFRESH_<sid32>
- ADMIN：WD_ADMIN_REFRESH_<sid32>

sid32 是 sid 去掉连字符后的 32 位小写十六进制字符串。刷新时，服务端先从 lane 找到当前 sid，再只读取该 sid 对应的 Cookie；其他同前缀 Cookie均为失效残留，不能参与候选选择。

所有会改变浏览器登录通道的登录、换号和退出请求必须携带 expectedAuthGeneration。Redis 原子比较成功后才递增 generation 并改变 currentSid；不匹配返回 AUTH_GENERATION_CONFLICT，且不改账号、Redis、Cookie 或响应内存状态。

新登录原子替换同一 lane 的旧 sid，并将旧 sid 标为 REVOKED。新旧 refresh 使用不同 Cookie 名，因此旧响应即使迟到，也只能重新写入已撤销 sid 的独立 Cookie，不能覆盖新 sid 的 Cookie。刷新成功不发送 Set-Cookie。退出只删除目标 sid 的 Cookie；旧退出响应不能删除另一 sid 的 Cookie。

客户端还必须以 authRequestId 和返回的 authGeneration 绑定内存状态。丢弃迟到 JSON 响应只是第二层保护，不能代替上述服务端 lane、独立 Cookie 名和 Redis 原子比较。

小程序使用持久的随机 clientInstanceId 作为 laneId，并保存 authGeneration；它们不是凭据。登录／刷新响应必须回显 authRequestId 和 authGeneration，小程序只接纳当前请求。新登录在 Redis 中原子替换该 clientInstanceId 的旧 sid；响应体没有浏览器自动 Cookie，因此迟到响应不得更新本地 access、refresh、账号或 generation。

### 3.6 Redis 登录记录与原子关系

Redis 逻辑记录固定为：

~~~text
auth:lane:{surface}:{purpose}:{laneId}
  generation, currentSid

auth:login:{sid}
  accountId, surface, purpose, role, laneId, issuedAt, absoluteExpiresAt, refreshDigest

auth:refresh:{purpose}:{refreshDigest}
  sid

auth:terminal:sid:{sid}
  state=REVOKED|EXPIRED, terminalAt, absoluteExpiresAt

auth:terminal:refresh:{purpose}:{refreshDigest}
  state=REVOKED|EXPIRED, terminalAt, absoluteExpiresAt
~~~

absoluteExpiresAt 是授权的逻辑截止点。为在截止后区分 EXPIRED 与未知凭据，live login 与 refresh 索引的物理 TTL 固定到 absoluteExpiresAt 后 24 小时；任何脚本都必须先比较 absoluteExpiresAt，过期记录即使尚未物理删除也绝不能通过认证。首次观察到过期时将其原子转换为 EXPIRED 终态。终态标记只保留状态、时间和不可逆摘要，并在同一物理截止点删除；不得包含原 refreshToken。

下列操作必须以单个 Redis Lua 脚本或等价的服务端原子事务完成，不能使用“先 GET、后普通 SET”的窗口：

1. 登录／换号：比较 lane generation；创建 login 与 refresh 索引；更新 lane；若有旧 currentSid，同时删除旧 live 记录并写 REVOKED 终态。
2. 刷新：按 surface、lane currentSid、Cookie／正文摘要、purpose、laneId 和 absoluteExpiresAt 一次性核对；过期时写 EXPIRED 终态并删除 live 记录；成功时不得修改任何 TTL。
3. 退出：核对当前 sid、purpose、laneId 和 generation；写 REVOKED 终态，删除 live login 与 refresh 索引，递增 lane generation 并清空 currentSid。
4. 受保护请求：JWT 验签后核对 login 仍存在且 sub、purpose、role、session_exp 与记录一致，再按记录中的 surface、purpose、laneId 核对 lane currentSid 仍等于该 sid；Redis 不可达时不得放行。

刷新在原子核对后、JWT 签发前与退出相撞时，可能返回一个随后已无效的 accessToken；这不会复活登录，因为每个受保护请求和 WebSocket 输出仍须再次核对 Redis。退出不得创建或恢复任何 login 键。

状态对外区分：

- JWT 或 session_exp 已到期：AUTH_EXPIRED。
- 终态为 REVOKED，或 Redis 可达但 sid 已失效：SESSION_REVOKED。
- Redis 连接、超时或状态无法确认：AUTH_STATE_UNAVAILABLE。
- 从未存在或无法匹配的 refreshToken：INVALID_REFRESH_TOKEN。

## 4. Cookie、CSRF、Origin 与 CORS

### 4.1 Cookie 属性

| Cookie | Path | HttpOnly | SameSite | local-demo Secure | Max-Age |
| --- | --- | --- | --- | --- | --- |
| WD_WEB_LANE | /api/auth/web | 是 | Lax | false | 604800 |
| WD_ADMIN_LANE | /api/admin/auth | 是 | Lax | false | 28800 |
| WD_WEB_REFRESH_<sid32> | /api/auth/web | 是 | Lax | false | 不晚于 USER session_exp |
| WD_ADMIN_REFRESH_<sid32> | /api/admin/auth | 是 | Lax | false | 不晚于 ADMIN session_exp |
| WD_ANON_LANE | / | 是 | Lax | false | 604800 |
| WD_ANON_<threadId32> | / | 是 | Lax | false | 604800 |
| WD_XSRF_WEB | / | 否 | Lax | false | 当前浏览器会话 |
| WD_XSRF_ADMIN | / | 否 | Lax | false | 当前浏览器会话 |
| WD_XSRF_ANON | / | 否 | Lax | false | 当前浏览器会话 |

所有 Cookie 都省略 Domain。Secure=false 只允许 PUBLIC_WEB_ORIGIN 精确为 http://127.0.0.1:5174 且服务绑定回环的 local-demo；其他环境必须 HTTPS、Secure=true 并另行评审，不得把本表直接用于公网。

Cookie Path 不是安全边界。Bearer-only 业务路由不得从任何 Cookie 回退认证。登录刷新 Cookie不得被代理转发给 Python。

### 4.2 CSRF

除下表三个 CSRF 获取端点外，浏览器中所有创建或修改 Cookie、使用 refresh Cookie、使用匿名 Cookie 证明写操作的请求，都必须同时满足：

1. Origin 头精确等于 http://127.0.0.1:5174；缺失、空字符串、localhost、其他端口或其他源均拒绝。
2. 对应的可读 XSRF Cookie 存在。
3. X-Wudong-CSRF 请求头与 Cookie 值恒定时间比较相等。
4. Content-Type 为 application/json。

端点：

| 方法与路由 | 结果 |
| --- | --- |
| GET /api/auth/web/csrf | 设置或复用 WD_WEB_LANE，轮换 WD_XSRF_WEB，返回 csrfToken 与 authGeneration |
| GET /api/admin/auth/csrf | 设置或复用 WD_ADMIN_LANE，轮换 WD_XSRF_ADMIN，返回 csrfToken 与 authGeneration |
| GET /api/anonymous/web/csrf | 设置或复用 WD_ANON_LANE，轮换 WD_XSRF_ANON，返回 csrfToken 与 anonymousGeneration |

这三个 GET 仍须精确 Origin、X-Wudong-Contract 和 no-store，但在尚无 token 时不要求 X-Wudong-CSRF，也不要求 JSON Content-Type。客户端必须串行完成首次 lane／CSRF 引导后再发认证或匿名写请求，不得并发创建多个初始 lane。

CSRF token 是 32 个随机字节的无填充 Base64url 字符串。只有上述 CSRF 获取端点设置或轮换 XSRF Cookie；登录、刷新和退出响应不得设置或清除 XSRF Cookie，以免迟到认证响应改变新登录的 CSRF 状态。CSRF token 不得作为身份或匿名访问凭据。

USER／ADMIN 的 CSRF 响应 data 固定为 {"csrfToken":"<token>","authGeneration":0}；匿名响应固定为 {"csrfToken":"<token>","anonymousGeneration":0}。两个 generation 都是非负整数。

小程序不依赖 Cookie，不使用上述 CSRF token；它必须提交本文规定的 access、refresh 或 anonymousCredential。缺少 Origin 不构成小程序授权。

### 4.3 Web Origin 与 CORS

Java 对 /api/** 的浏览器 CORS 仅允许：

~~~text
Access-Control-Allow-Origin: http://127.0.0.1:5174
Access-Control-Allow-Credentials: true
Vary: Origin
~~~

允许方法为 GET、POST、PUT、PATCH、OPTIONS。允许请求头为 Authorization、Content-Type、Idempotency-Key、X-Wudong-Contract、X-Wudong-CSRF。浏览器不得提交只供 mini／内部调用的 X-Wudong-Anonymous-Proof。暴露响应头为 Retry-After、X-Request-Id、X-Wudong-Contract。

不得使用通配 Origin，不允许 http://localhost:5174、5173 或任意缺失／空 Origin 冒充浏览器源。OPTIONS 只处理预检，不绕过实际请求的认证、CSRF 或契约版本。

微信开发者工具不是浏览器 Cookie 通道。其显式凭据 HTTP／WebSocket 请求可以没有 Origin；若带 Origin，只允许 https://servicewechat.com。无论 Origin 是否存在，都必须完成相应凭据校验。

### 4.4 同源代理

Web 只访问 127.0.0.1:5174：

| 对外路径 | 上游 | Cookie 规则 |
| --- | --- | --- |
| /api/** | http://127.0.0.1:8080 | 原样传给 Java；Java 按具体路由取用 |
| /ai/ws/user | ws://127.0.0.1:8000/ws/web/user | 删除全部 Cookie |
| /ai/ws/anonymous | ws://127.0.0.1:8000/ws/web/anonymous | 只转发 WD_ANON_LANE 与 WD_ANON_<threadId32> |

代理不得暴露或转发 /internal/**，不得注入任何服务凭据，不得信任客户端 X-Forwarded-* 决定来源。代理必须保留原始 Origin，changeOrigin=false；代理与 Python 都必须检查该值。用于匿名 WebSocket 的 Cookie 白名单按 Cookie 名解析，不能用原始字符串包含判断。

Java、Python 与 Web 开发服务都只监听 127.0.0.1；本修订不允许 0.0.0.0、局域网、公网隧道或外部回调。

## 5. 账号 REST

### 5.1 公共 DTO

AccountView：

~~~json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "username": "wudong_user",
  "nickname": "乌东游客",
  "role": "USER"
}
~~~

AccessSession：

~~~json
{
  "authRequestId": "00000000-0000-0000-0000-000000000000",
  "authGeneration": 1,
  "sessionId": "00000000-0000-0000-0000-000000000000",
  "purpose": "USER",
  "account": {
    "id": "00000000-0000-0000-0000-000000000000",
    "username": "wudong_user",
    "nickname": "乌东游客",
    "role": "USER"
  },
  "tokenType": "Bearer",
  "accessToken": "<access-token>",
  "accessExpiresAt": "2026-09-10T01:17:03Z",
  "loginExpiresAt": "2026-09-17T01:02:03Z"
}
~~~

只有 mini 登录响应在上述对象顶层额外包含 refreshToken；Web／ADMIN 响应禁止该字段。刷新响应不包含 refreshToken。

### 5.2 注册

POST /api/auth/web/register 和 POST /api/auth/mini/register：

~~~json
{
  "username": "Wudong_User",
  "password": "<user-entered-password>",
  "nickname": "乌东游客"
}
~~~

nickname 可省略或为 null；其他字段必填且不得为 null。成功返回 HTTP 201：

~~~json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "username": "wudong_user",
  "nickname": "乌东游客",
  "role": "USER",
  "nextAction": "LOGIN"
}
~~~

注册不自动登录、不设置 refresh Cookie、不创建 sid。Web 注册要求精确 Origin、WD_XSRF_WEB 和 X-Wudong-CSRF；mini 注册不使用 Cookie。用户名冲突返回 409 USERNAME_ALREADY_EXISTS。

### 5.3 Web USER 登录

POST /api/auth/web/login，要求 WD_WEB_LANE、WD_XSRF_WEB、Origin 和 CSRF：

~~~json
{
  "username": "wudong_user",
  "password": "<user-entered-password>",
  "authRequestId": "00000000-0000-0000-0000-000000000000",
  "expectedAuthGeneration": 0
}
~~~

成功返回 AccessSession，role/purpose 为 USER，并设置新 sid 对应的 WD_WEB_REFRESH_<sid32>。同 lane 旧 sid 在 Redis 中原子撤销。服务端同时用原值重新设置 WD_WEB_LANE，使其载体期限覆盖新的 USER 登录绝对期限；不得更换 laneId，也不得在响应体返回 refreshToken。

### 5.4 mini USER 登录

POST /api/auth/mini/login：

~~~json
{
  "username": "wudong_user",
  "password": "<user-entered-password>",
  "clientInstanceId": "00000000-0000-0000-0000-000000000000",
  "authRequestId": "00000000-0000-0000-0000-000000000000",
  "expectedAuthGeneration": 0
}
~~~

成功返回 AccessSession，并额外返回 refreshToken。小程序只在本地保存 clientInstanceId、authGeneration 和 refreshToken；accessToken 只在内存。响应不设置登录 Cookie。

### 5.5 ADMIN 登录

POST /api/admin/auth/login 与 Web USER 登录形状相同，使用 WD_ADMIN_LANE、WD_XSRF_ADMIN 和独立 authGeneration。账号必须已有 ADMIN 角色。成功返回 role/purpose=ADMIN，并设置 WD_ADMIN_REFRESH_<sid32>，同时用原值重新设置 WD_ADMIN_LANE，使其载体期限覆盖新的 ADMIN 登录绝对期限；不得更换 laneId，响应体不返回 refreshToken。

### 5.6 刷新

| 路由 | 请求 |
| --- | --- |
| POST /api/auth/web/refresh | Cookie＋Origin＋CSRF；body 为 authRequestId、expectedAuthGeneration |
| POST /api/admin/auth/refresh | ADMIN Cookie＋Origin＋CSRF；body 同上 |
| POST /api/auth/mini/refresh | body 见下；不读 Cookie |

Web／ADMIN body：

~~~json
{
  "authRequestId": "00000000-0000-0000-0000-000000000000",
  "expectedAuthGeneration": 1
}
~~~

mini 请求：

~~~json
{
  "clientInstanceId": "00000000-0000-0000-0000-000000000000",
  "authRequestId": "00000000-0000-0000-0000-000000000000",
  "expectedAuthGeneration": 1,
  "refreshToken": "<opaque-refresh-token>"
}
~~~

成功返回 HTTP 200 的 AccessSession；authGeneration 不变，sessionId 与 loginExpiresAt 不变，只更新 accessToken 与 accessExpiresAt。Web 刷新不得发送 Set-Cookie。

### 5.7 退出

所有退出都需要当前有效的 Authorization Bearer。若 access 已过期但本次登录仍有效，客户端先调用对应刷新端点，再用新 access 调用退出。

| 路由 | 请求 |
| --- | --- |
| POST /api/auth/web/logout | lane Cookie＋Origin＋CSRF；body 为 authRequestId、expectedAuthGeneration |
| POST /api/admin/auth/logout | ADMIN lane Cookie＋Origin＋CSRF；body 同上 |
| POST /api/auth/mini/logout | body 为 clientInstanceId、authRequestId、expectedAuthGeneration |

Web／ADMIN logout body 与第 5.6 节 Web refresh body 相同。退出不要求 refresh Cookie仍存在；有效 Bearer 和 lane 记录足以定位并撤销当前 sid。

成功原子撤销当前 sid，返回：

~~~json
{
  "authRequestId": "00000000-0000-0000-0000-000000000000",
  "authGeneration": 2,
  "sessionId": "00000000-0000-0000-0000-000000000000",
  "revocationState": "REVOKED",
  "revokedAt": "2026-09-10T01:10:00Z"
}
~~~

只有 Redis 已确认撤销后，Web 才删除目标 refresh Cookie。Redis 不可达返回 AUTH_STATE_UNAVAILABLE，不发送删除 Cookie，客户端不得显示“已从服务端退出”。网络无响应属于未知结果；客户端可以清除当前页面内存，但必须明确这不等于服务端撤销。

### 5.8 本人资料

- GET /api/me/profile：需要 purpose=USER，返回 AccountView。
- GET /api/admin/auth/profile：需要 purpose=ADMIN 与 role=ADMIN，返回 AccountView。

无论请求是否携带 visitorId、userId 或 accountId，服务端都只使用 JWT sub。USER 与 ADMIN 入口不互相兜底。

### 5.9 后台账号初始化

后台账号没有 HTTP 创建、注册、找回、重置或授权入口。唯一初始化方式是开发者显式启动 Java 非 Web CLI 模式：

~~~text
java -jar tourism-service.jar --spring.main.web-application-type=none --wudong.admin-init=true
~~~

CLI 依次读取 username、可选 nickname，并使用 Console.readPassword 隐藏读取 password。无交互控制台时以 ADMIN_INIT_INTERACTIVE_CONSOLE_REQUIRED 失败，不降级到命令参数、环境变量或标准明文输入。

CLI 复用第 3.2 节校验和 PasswordEncoder，只创建 ADMIN。同名账号无论原角色为何都以 ADMIN_USERNAME_EXISTS 停止，不覆盖密码、不提权。日常服务启动不得执行初始化。输出只含稳定结果码与规范化 username，不打印密码、哈希或凭据。

## 6. 匿名会话

### 6.1 凭据与 Redis 记录

anonymousCredential 与 refreshToken 使用相同的 32 随机字节、无填充 Base64url 规则，但二者分别生成、用途隔离且不可互换。Redis 仅保存 SHA-256 摘要：

~~~text
anon:lane:{surface}:{laneId}
  generation, currentThreadId

anon:session:{threadId}
  credentialDigest, surface, laneId, ownerKind=ANONYMOUS,
  createdAt, lastInteractionAt, expiresAt

anon:credential:{credentialDigest}
  threadId

anon:accepted-runs:{threadId}
  已接受且已用于续期的 runId 集合

anon:terminal:{credentialDigest}
  state=EXPIRED|REVOKED, terminalAt, threadId
~~~

threadId 和 credential 由 Java 生成；客户端不得指定。expiresAt 是匿名授权的逻辑截止点。为区分失效凭据与随机无效值，session 与 credential 索引的物理 TTL 固定到 expiresAt 后 7 天；任何读取都先比较 expiresAt，过期元数据不得恢复上下文。首次观察到过期时原子写 EXPIRED 终态。lane 与终态也至少保留到该物理截止点；终态不得保存原凭据。候选／检查点的可访问期限仍止于 expiresAt，保留鉴权元数据不延长会话。匿名凭据不进入账号、JWT、业务表、模型、检查点或日志。

### 6.2 创建

POST /api/anonymous/web/sessions 要求 WD_ANON_LANE、WD_XSRF_ANON、Origin 和 CSRF：

~~~json
{
  "anonymousRequestId": "00000000-0000-0000-0000-000000000000",
  "expectedAnonymousGeneration": 0
}
~~~

成功原子递增 lane generation、建立新 thread 和凭据，撤销该 lane 的旧匿名 session，设置 WD_ANON_<threadId32>，但不在响应体返回凭据：

~~~json
{
  "anonymousRequestId": "00000000-0000-0000-0000-000000000000",
  "threadId": "00000000-0000-0000-0000-000000000000",
  "mode": "ANONYMOUS",
  "anonymousGeneration": 1,
  "sessionExpiresAt": "2026-09-11T01:02:03Z",
  "carrierExpiresAt": "2026-09-17T01:02:03Z"
}
~~~

POST /api/anonymous/mini/sessions：

~~~json
{
  "clientInstanceId": "00000000-0000-0000-0000-000000000000",
  "anonymousRequestId": "00000000-0000-0000-0000-000000000000",
  "expectedAnonymousGeneration": 0
}
~~~

成功响应额外包含 anonymousCredential，且不设置 Cookie。小程序本地保存原凭据；持有凭据不表示会话仍有效。

新建 session 的初始 expiresAt 为 Java 当前时间加 24 小时。创建本身不是一次 AI 有效交互，不额外续期。

### 6.3 有效交互与滑动 24 小时

只有同时满足以下条件的 generate 才是“有效交互”：

1. 用户或匿名证明已验证。
2. 输入与协议已验证。
3. 限频和同 thread 单运行占用检查通过。
4. Python 已生成唯一 runId，并实际接受该次生成。

Python 随后调用第 8.3 节的 POST /internal/agent/anonymous/interactions。Java 使用自身当前时间，在单个 Redis 原子操作中再次验证 credential 摘要、surface、thread 关联和现有 expiresAt；首次见到该 runId 时写入 accepted-runs、更新 lastInteractionAt，并把 expiresAt 设置为当前时间加 86400 秒。该操作同时更新 session、credential 索引与 accepted-runs 的 TTL；鉴权元数据继续按第 6.1 节保留到逻辑期限后 7 天。相同 runId 的重试只返回原结果，不再次延长。

握手、心跳、读取预览、session_state、Cookie 同步、内部候选读取、失败输入、被限频请求和重复运行拒绝都不得续期。会话已经到期时不得因交互调用重建；返回 ANONYMOUS_EXPIRED，用户只能显式创建新 session。

### 6.4 Web Cookie 同步

POST /api/anonymous/web/cookie-sync 要求当前匿名 lane、当前 WD_ANON_<threadId32>、Origin 与 CSRF：

~~~json
{
  "anonymousRequestId": "00000000-0000-0000-0000-000000000000",
  "expectedAnonymousGeneration": 1
}
~~~

Java 只验证并用原值重新设置 WD_ANON_LANE 与同一个动态匿名 Cookie，使两个浏览器载体都从同步响应时起保留 7 天；不得更换 laneId、凭据或 threadId，不得修改 lastInteractionAt、expiresAt 或逻辑会话期限。无记录或已过期返回 ANONYMOUS_EXPIRED；已撤销返回 ANONYMOUS_REVOKED；两者都不创建新凭据。

动态 Cookie 名与 anonymousGeneration 避免迟到同步响应覆盖后来创建的匿名 session：旧响应只能写回旧 thread 的 Cookie，lane currentThreadId 仍指向新 thread，Java／Python 都不得选择旧 Cookie。

### 6.5 匿名凭据的使用

- Web 匿名 HTTP：Java 从 WD_ANON_LANE 和当前动态匿名 Cookie取凭据。
- Web 匿名 WebSocket：代理只转发匿名 lane 与动态匿名 Cookie，Python按 Redis lane currentThreadId 选择当前凭据。
- mini 匿名 HTTP：X-Wudong-Anonymous-Proof: <anonymousCredential>。
- mini 匿名 WebSocket：首帧显式携带 anonymousCredential。

匿名凭据只能访问其 thread 的公开 AI 上下文与未采用候选，不得读取任何账号的行程、草稿、订单、联系人或后台数据。登录不会升级匿名凭据、改变 owner 或把 24 小时改成 30 天。

后续采用匿名预览时：

- Web 必须同时提交 USER Bearer、当前匿名 Cookie、Origin 和 WD_XSRF_ANON。
- mini 必须同时提交 USER Bearer 和 X-Wudong-Anonymous-Proof。
- Java 先按账号／操作防重规则核对已成功结果；仅对尚未成功的新采用验证匿名 session。
- 对匿名 session 的读取和采用不续期。

候选字段和业务保存由第 16、27 节冻结，本节只冻结证明载体。

## 7. 受保护路由的授权

### 7.1 Java

| 路由范围 | 必要授权 |
| --- | --- |
| 公共 GET | 无账号凭据；仍须契约版本和输入校验 |
| /api/me/** | USER JWT、Redis active sid、资源 account_id=JWT sub |
| /api/admin/**（除 auth） | ADMIN JWT、Redis active sid、role=ADMIN |
| /api/anonymous/web/** | 当前匿名 lane／Cookie；写 Cookie时另需 Origin＋CSRF |
| /api/anonymous/mini/** | 显式 anonymousCredential 或创建请求规定的 mini lane |
| /internal/** | 第 8 节的方向服务凭据与逐路由附加证明 |

v3 不接受 X-Visitor-Id、X-Admin-Token、手机号、threadId 或请求体 userId 作为授权。任何 v3 请求只要出现 X-Visitor-Id 或 X-Admin-Token 就返回 CONTRACT_INCOMPATIBLE，即使同时带有有效 JWT，也不得混合解释。

### 7.2 Python

Python 登录用户能力必须同时满足：

1. RS256 JWT 完整校验成功，aud 包含 wudong-ai，purpose/role=USER。
2. Redis auth:login:{sid} 存在，且 sub、sid、purpose、role、session_exp 与记录一致；记录指向的 surface／purpose／lane currentSid 仍为该 sid。
3. 当前 thread 明确属于该 accountId。
4. 每次私人工具调用继续携带原 USER 证明，并由 Java 再验归属。

服务凭据、threadId、已显示页面或已读 checkpoint 都不能替代任一条件。Redis 不可用时拒绝私有读取和输出。

## 8. Java／Python 内部访问

### 8.1 双向服务凭据

两方向分别生成独立的 32 随机字节无填充 Base64url 凭据：

- JAVA_TO_AI_CREDENTIAL_FILE：Java 出站持有，Python 入站校验。
- AI_TO_JAVA_CREDENTIAL_FILE：Python 出站持有，Java 入站校验。

文件必须位于仓库外的绝对路径，内容只含一行凭据，不能使用默认值。调用方发送：

~~~text
X-Wudong-Service-Credential: <direction-specific-credential>
~~~

接收方以恒定时间比较。两值必须不同。缺配置返回 503 INTERNAL_AUTH_UNAVAILABLE；缺失、错误或方向不匹配统一返回 403 INTERNAL_FORBIDDEN，不披露哪个条件失败。

内部服务只绑定 127.0.0.1。回环地址只是附加限制，不是身份；不得因 remoteAddr、Host、X-Forwarded-* 或 internal 路径跳过服务凭据。内部路由不提供 CORS，也不得由 Web 代理暴露。

服务凭据只证明调用服务，不证明最终用户、匿名 session、资源归属、候选版本或知识任务资格。固定服务凭据本身不提供请求签名、防重放、传输加密或进程隔离；本修订只允许回环本机，跨机器部署必须另行评审。

### 8.2 附加证明头

需要用户身份时：

~~~text
X-Wudong-User-Proof: Bearer <current-user-access-jwt>
~~~

接收方必须重新执行 JWT、Redis、purpose=USER 与资源归属校验，不能接受 accountId/userId 替代。

需要匿名访问时：

~~~text
X-Wudong-Anonymous-Proof: <anonymousCredential>
~~~

接收方必须重新计算摘要并核对 thread、有效期和访问关系。用户证明与匿名证明可同时出现，但用途分别校验，不能互相替代。

### 8.3 Python → Java 白名单

必须使用 AI_TO_JAVA 凭据。除表中路由外全部拒绝：

| 方法与路由 | 附加证明与权限 |
| --- | --- |
| GET /internal/agent/products/search | 仅公开、已发布投影；服务凭据 |
| GET /internal/agent/foods/search | 同上 |
| GET /internal/agent/stays/search | 同上 |
| GET /internal/agent/places/search | 同上 |
| GET /internal/agent/route-guides/search | 同上 |
| GET /internal/agent/knowledge/search | 仅当前生效知识；服务凭据 |
| GET /internal/agent/itineraries/{id} | USER proof；本人无联系人规划投影 |
| GET /internal/agent/food-drafts/{id} | USER proof；本人无联系人、无私人备注规划投影 |
| GET /internal/agent/stay-drafts/{id} | USER proof；本人无联系人、无私人备注规划投影 |
| GET /internal/agent/orders/status?threadId= | USER proof；SQL 同时限定 account_id 与 source_thread_id |
| POST /internal/agent/anonymous/interactions | ANONYMOUS proof；body 仅 threadId、runId |
| GET /internal/agent/knowledge/active-builds?configHash= | 服务凭据；只读当前公开且匹配配置的标识 |
| POST /internal/agent/knowledge/eligibility | 服务凭据；严格 documentId/buildId 批量核验 |
| GET /internal/agent/knowledge-build-inputs/{taskId} | 服务凭据；仅有效 RUNNING 任务固定输入 |
| POST /internal/agent/run-summaries | 服务凭据；严格脱敏白名单 |

该方向没有账号、目录、社区、订单、草稿或知识发布业务写权限。anonymous/interactions 只更新匿名访问元数据；run-summaries 只写脱敏运行摘要，它们都不是业务事实写入口。

anonymous/interactions 请求与响应：

~~~json
{
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": "00000000-0000-0000-0000-000000000000"
}
~~~

~~~json
{
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": "00000000-0000-0000-0000-000000000000",
  "lastInteractionAt": "2026-09-10T01:02:03Z",
  "sessionExpiresAt": "2026-09-11T01:02:03Z",
  "replayed": false
}
~~~

### 8.4 Java → Python 白名单

必须使用 JAVA_TO_AI 凭据：

| 方法与路由 | 附加证明与权限 |
| --- | --- |
| POST /internal/assistant/candidates/resolve | USER proof；匿名来源候选另需 ANONYMOUS proof；精确 thread/candidate/version |
| POST /internal/knowledge-builds/{taskId} | 服务凭据；Python 仍向 Java 核验固定任务输入与 RUNNING 资格 |

该方向不能让 Java 用服务凭据绕过候选归属或让 Python直接写 MySQL。公开采用请求中的 `candidateRef` 由第 16 节冻结；`/internal/assistant/candidates/resolve` 的精确请求／响应由第 27 节冻结，知识构建请求由第 25 节冻结。

### 8.5 凭据保管、替换与日志

- 凭据文件、JWT 私钥和密码不得进入 Git、配置样例值、模型输入、检查点、业务表、事件正文、异常或日志。
- 日志必须整体脱敏 Authorization、Cookie、Set-Cookie、X-Wudong-Service-Credential、X-Wudong-User-Proof、X-Wudong-Anonymous-Proof，以及 auth／refresh／anonymous 请求体。
- 允许记录 requestId、稳定错误码、方法、受控路由模板、耗时和服务方向；不得记录原始路径参数中的私人标识。
- 服务凭据不自动轮换。替换时暂停该方向调用，在两端一致替换外置文件并重启或受控重载后恢复；不保留永久双密钥宽限。

## 9. AI WebSocket 鉴权

### 9.1 路由与 Origin

| 客户端 | Python 路由 | Origin |
| --- | --- | --- |
| Web USER | /ws/web/user，经 /ai/ws/user 代理 | 必须精确为 http://127.0.0.1:5174 |
| Web ANONYMOUS | /ws/web/anonymous，经 /ai/ws/anonymous 代理 | 同上 |
| mini USER | /ws/mini/user | 可缺失；出现时必须为 https://servicewechat.com |
| mini ANONYMOUS | /ws/mini/anonymous | 同上 |

Web Origin 缺失或为空直接拒绝。mini 的 Origin 规则只区分开发工具表面，不形成授权；首帧凭据仍是必要条件。

### 9.2 首帧

连接建立后 10 秒内只接受一个严格 auth 对象。USER：

~~~json
{
  "type": "auth",
  "authVersion": "wudong-ws-auth-v1",
  "contractVersion": "tourism-api-v3-draft-r3",
  "mode": "USER",
  "accessToken": "<access-token>"
}
~~~

Web ANONYMOUS：

~~~json
{
  "type": "auth",
  "authVersion": "wudong-ws-auth-v1",
  "contractVersion": "tourism-api-v3-draft-r3",
  "mode": "ANONYMOUS_WEB"
}
~~~

凭据只来自已白名单转发的匿名 Cookie。mini ANONYMOUS：

~~~json
{
  "type": "auth",
  "authVersion": "wudong-ws-auth-v1",
  "contractVersion": "tourism-api-v3-draft-r3",
  "mode": "ANONYMOUS_MINI",
  "anonymousCredential": "<anonymous-credential>"
}
~~~

路由、mode 与凭据载体必须匹配。USER 帧不得包含 refreshToken、anonymousCredential、userId 或 threadId。ANONYMOUS_WEB 不得含任何凭据字段；ANONYMOUS_MINI 不得含 accessToken。

认证成功：

~~~json
{
  "type": "auth_ok",
  "authVersion": "wudong-ws-auth-v1",
  "contractVersion": "tourism-api-v3-draft-r3",
  "connectionId": "00000000-0000-0000-0000-000000000000",
  "mode": "USER",
  "authExpiresAt": "2026-09-10T01:17:03Z"
}
~~~

匿名成功 mode 为 ANONYMOUS，authExpiresAt 为当前匿名 expiresAt。auth_ok 之前不得接受业务消息、读取私有 checkpoint、调用模型或工具。

### 9.3 失败与关闭

失败时最多发送一次脱敏 auth_failed，随后立即关闭：

~~~json
{
  "type": "auth_failed",
  "authVersion": "wudong-ws-auth-v1",
  "contractVersion": "tourism-api-v3-draft-r3",
  "code": "AUTH_EXPIRED",
  "message": "登录已过期，请重新登录。",
  "retryable": false
}
~~~

| close code | 场景 |
| --- | --- |
| 4401 | AUTH_REQUIRED、AUTH_EXPIRED、SESSION_REVOKED、ANONYMOUS_EXPIRED、ANONYMOUS_REVOKED |
| 4403 | ORIGIN_REJECTED、FORBIDDEN、AUTH_MODE_MISMATCH、CONTRACT_INCOMPATIBLE |
| 4408 | 10 秒未收到完整合法 auth 帧 |
| 4429 | RATE_LIMITED |
| 4503 | AUTH_STATE_UNAVAILABLE 或服务端认证配置不可用 |

关闭 reason 只使用稳定 code，最长 123 个 UTF-8 字节，不放令牌、异常或用户内容。retryable 只有 AUTH_STATE_UNAVAILABLE 与 RATE_LIMITED 为 true；RATE_LIMITED 的失败帧另含整数 retryAfterSeconds，其他失败帧禁止该字段。未知首帧、业务帧先于 auth、额外字段、错误版本都先返回固定失败再关闭；解析失败时可直接关闭 4403，不回显原帧。

### 9.4 持续有效性

首帧成功不是永久授权。Python 必须在以下时点重新核对 JWT／匿名期限及 Redis：

- 每条业务输入前。
- 每次私人工具调用前。
- 每个可能公开用户内容的输出事件前。
- 空闲连接每 5 秒一次。
- 访问 JWT exp 或匿名 expiresAt 到达时立即停止受保护输出并关闭。

失效 USER 不得降级为 ANONYMOUS。连接内不允许更新 accessToken；有效独立登录只能向 Java 刷新后创建新连接并重新首帧鉴权。重连不自动重发 AI 生成、保存或业务写入。凭据与 auth 帧不得写入业务事件、模型、检查点或日志。

运行取消、迟到输出围栏和业务事件版本由独立的 `assistant-card-v3` 契约冻结；本节只要求一旦身份失效，旧连接不得继续输入或输出。第 28 节仅冻结 Java 接收的脱敏运行摘要，不以摘要代替输出围栏。

## 10. 配置与依赖

### 10.1 本机配置

| 配置 | 固定值／规则 | 所有者 |
| --- | --- | --- |
| SERVER_ADDRESS | 127.0.0.1 | Java |
| SERVER_PORT | 8080 | Java |
| AI_SERVER_ADDRESS | 127.0.0.1 | Python |
| AI_SERVER_PORT | 8000 | Python |
| PUBLIC_WEB_ORIGIN | http://127.0.0.1:5174 | Java、Python、代理 |
| COOKIE_SECURE | false，仅 local-demo | Java |
| ACCESS_TTL_SECONDS | 900 | Java、Python |
| TOURIST_LOGIN_TTL_SECONDS | 604800 | Java |
| ADMIN_LOGIN_TTL_SECONDS | 28800 | Java |
| ANON_IDLE_TTL_SECONDS | 86400 | Java、Python |
| ANON_COOKIE_MAX_AGE_SECONDS | 604800 | Java |
| JWT_ALGORITHM | RS256 | Java、Python |
| JWT_ISSUER | wudong-java-local | Java、Python |
| JWT_KEY_ID | 非秘密固定标识，不得空 | Java、Python |
| JWT_PRIVATE_KEY_FILE | 仓库外绝对 PKCS#8 PEM 路径 | 仅 Java |
| JWT_PUBLIC_KEY_FILE | 仓库外绝对 SPKI PEM 路径 | Java、Python 各自配置 |
| JAVA_TO_AI_CREDENTIAL_FILE | 仓库外绝对路径 | Java 出站、Python 入站 |
| AI_TO_JAVA_CREDENTIAL_FILE | 仓库外绝对路径 | Python 出站、Java 入站 |
| WS_AUTH_TIMEOUT_SECONDS | 10 | Python |
| AUTH_RECHECK_SECONDS | 5 | Python |
| WRITE_LOCK_WAIT_SECONDS | 3 | Java；仅操作槽／业务事务局部锁等待上限 |
| OPERATION_RECEIPT_RETENTION | NO_AUTO_CLEANUP | Java；首版本机操作槽及终态不自动清理 |

密钥或凭据配置缺失时不得使用默认值或降级认证。公开只读能力可以继续运行；相关登录、私有、匿名或内部能力返回对应 503。配置文件只记录外置路径，不记录实际值。

### 10.2 兼容依赖

| 运行端 | 依赖约束 |
| --- | --- |
| Java | Java 21、Spring Boot 3.4.2；由该 BOM 管理 spring-boot-starter-security、spring-boot-starter-oauth2-resource-server、spring-boot-starter-data-redis，不单独覆盖 Spring Security／JOSE／Lettuce 版本 |
| 密码 | Spring Security PasswordEncoder；DelegatingPasswordEncoder＋bcrypt cost 12 |
| Python | Python >=3.11,<3.14；PyJWT[crypto] >=2.10,<3；redis >=5,<7；只允许显式 algorithms=["RS256"] |
| Redis 服务 | 固定 Redis 7.4 系列，不使用 latest 标签；认证 DB 与 AI checkpoint 键空间分前缀 |
| Web | Vue 3／Vite 6；不新增持久令牌库，代理按第 4.4 节配置 |

后续实现必须在各自依赖清单中锁定实际解析版本并验证上述能力；不得为了满足库默认行为改变本契约。本文不安装依赖，也不声称这些依赖已存在或已运行。

## 11. 错误目录

| HTTP | code | 固定语义 |
| --- | --- | --- |
| 400 | VALIDATION_FAILED | 严格字段、类型、格式或 null 规则不符 |
| 400 | CONTRACT_INCOMPATIBLE | 请求或 WS 契约版本不匹配 |
| 400 | PASSWORD_ENCODING_LIMIT_EXCEEDED | 密码 UTF-8 超过 72 字节 |
| 401 | INVALID_CREDENTIALS | 登录账号、密码或入口角色不匹配 |
| 401 | AUTH_REQUIRED | 缺少访问证明 |
| 401 | AUTH_EXPIRED | JWT、独立登录或 refresh 已到绝对期限 |
| 401 | SESSION_REVOKED | sid 已撤销或被同 lane 新登录替换 |
| 401 | INVALID_REFRESH_TOKEN | refresh 格式、摘要或 lane 关系不匹配 |
| 401 | ANONYMOUS_REQUIRED | 缺少匿名证明 |
| 401 | ANONYMOUS_EXPIRED | 匿名 session 已过期 |
| 401 | ANONYMOUS_REVOKED | 匿名 session 被同 lane 新会话替换或明确撤销 |
| 403 | FORBIDDEN | 有效身份不具备该用途／角色／资源权限 |
| 403 | AUTH_MODE_MISMATCH | WebSocket 路由、mode 与凭据载体不匹配 |
| 403 | CSRF_REJECTED | CSRF Cookie 与请求头缺失或不匹配 |
| 403 | ORIGIN_REJECTED | Origin 不在对应表面精确名单 |
| 403 | INTERNAL_FORBIDDEN | 内部方向凭据、回环或路由名单不符 |
| 409 | USERNAME_ALREADY_EXISTS | 规范化 username 已存在 |
| 409 | AUTH_GENERATION_CONFLICT | expected generation 已过时 |
| 429 | RATE_LIMITED | 认证／匿名生成限频；返回 Retry-After |
| 503 | AUTH_STATE_UNAVAILABLE | Redis 登录／匿名状态无法确认 |
| 503 | AUTH_CONFIGURATION_UNAVAILABLE | JWT、PasswordEncoder 或必要认证配置不可用 |
| 503 | INTERNAL_AUTH_UNAVAILABLE | 双向服务凭据未正确配置 |
| 410 | LEGACY_ENDPOINT_DISABLED | 新链切换后旧身份或旧处理器已停用 |

AUTH_GENERATION_CONFLICT 的 details 固定为：

~~~json
{
  "kind": "auth_generation_conflict",
  "currentGeneration": 2
}
~~~

除该对象和第 3.2 节的 password_encoding_limit 外，身份错误 details 为 null，避免泄露账号、sid、thread 或终态内部记录。

## 12. v1／v2 兼容与切换

### 12.1 不兼容矩阵

| 边界 | v1／v2 | v3 |
| --- | --- | --- |
| 游客归属 | X-Visitor-Id | USER JWT＋Redis sid＋account_id |
| 后台 | X-Admin-Token | 独立 ADMIN JWT＋Redis sid |
| 内部调用 | 仅回环 | 回环＋方向服务凭据＋逐路由附加证明 |
| 匿名 AI | 客户端或旧会话标识 | Java 随机凭据＋Redis 摘要／thread 关系 |
| Web refresh | 无 | HttpOnly、按 sid 隔离的固定 refresh Cookie |
| mini refresh | 无 | 本地原 refreshToken，只在 refresh 请求体提交 |
| WebSocket | 无严格首帧身份 | wudong-ws-auth-v1 严格首帧＋持续 Redis 检查 |

旧文件 tourism-api-v1.md 与 tourism-api-v2.md 保持原样，只作为历史依据。v3 不是向 v2 原位加字段，消费者不得忽略未知字段或自动回退。

### 12.2 切换规则

在 Java、Python、Web 与小程序的 B1 消费者都匹配本契约前，不得宣称新身份链可演示。统一切换后：

- X-Visitor-Id 与 X-Admin-Token 不再授权任何新链路。
- /api/services、/api/bookings、/internal/agent/pending-bookings 及旧无鉴权 AI 入口不得成为旁路；若保留路由壳，只返回 410 LEGACY_ENDPOINT_DISABLED。
- 旧未认证 WebSocket 或错误 authVersion 以 4403 CONTRACT_INCOMPATIBLE 关闭。
- 不删除旧源码、契约、数据库记录或检查点。
- 不把旧 visitorId 记录自动归属给账号，不用手机号、昵称或相同设备推断归属。
- 旧记录的合法账号投影、字段缺失与前向迁移由第 18 节冻结；本文不操作数据。
- 不兼容旧对话／候选保留原数据和原期限，仅在先验证访问权后提示重新开始；具体 checkpoint 版本由独立的 `assistant-card-v3` 契约冻结。

密钥、公钥与双向服务凭据必须在仓库外准备好，四端配置一致后才能启用切换。本文不生成密钥、不修改配置、不迁移数据、不启动服务。

## 13. B1 完成边界

| TASK-023 验收项 | 本文落点 |
| --- | --- |
| JWT RS256、外置可信密钥、访问／刷新期限 | 第 3.3～3.4、10 节 |
| 固定 refresh、Redis 状态、刷新／退出原子关系 | 第 3.5～3.6、5.6～5.7 |
| Web Cookie 与 mini 显式载体 | 第 4、5 节 |
| 匿名 Cookie 7 天、服务端滑动 24 小时、读取／同步不续期 | 第 6 节 |
| 用户、匿名、双向服务凭据及证明路由 | 第 7～8 节 |
| WebSocket 首帧、拒绝、关闭与持续失效 | 第 9 节 |
| CORS、Cookie、CSRF、5174 同源与回环 | 第 4、10 节 |
| 编码溢出错误信封、版本化字段和迁移兼容 | 第 2、3.2、11～12 节 |
| 秘密不得记录或进入客户端／模型／检查点 | 第 3、8.5、9 节 |

TASK-023 的 B1 冻结输入已由 Coordinator 记录为 VERIFIED；其被 B2 接续前的文件 SHA-256 为 `676e3153b200d421526ae59c4e2919d24c12a9d52a90057fc649a6b7da1dae9b8`。第 14～18 节只追加 B2 业务定义，第 20～30 节只追加 B3 Java 共享定义；两者都不削弱本节任何 B1 身份、匿名、内部访问、Cookie、CSRF、Origin、CORS 或 WebSocket 规则。本文没有授予任何业务代码、配置、迁移、测试、运行、数据库、提交、推送或集成权限。

## 14. B2 共同业务类型、目录、地图与社区

### 14.1 B2 共同表示

第 2 节的版本头、严格 JSON、固定信封、时间和错误脱敏规则适用于本节以后全部路由。公共读取不需要账号，但仍须 `X-Wudong-Contract`；个人写入和读取必须使用第 7.1 节的 USER 授权，后台路由必须使用 ADMIN 授权。B2 不接受 `X-Visitor-Id`、`X-Admin-Token` 或请求体中的 `accountId`。

业务类型固定如下：

| 类型 | 表示与校验 |
| --- | --- |
| `MoneyAmount` | 十进制字符串，正则 `^(0|[1-9][0-9]{0,9})\.[0-9]{2}$`；币种仅为 `CNY`，不得用 JSON 浮点数作为订单事实 |
| `CatalogVersion` | 从 1 开始的正整数；任何会改变公开名称、归属、履约条件、容量、演示价或发布状态的变更递增 1 |
| `ResourceVersion` | 从 1 开始的正整数；每次成功保存或提交草稿递增 1 |
| `localDate` | 严格 `YYYY-MM-DD`，真实日历日期 |
| `localDateTime` | 严格 `YYYY-MM-DDTHH:mm:ss`，语义固定为 `Asia/Shanghai`，不接受时区后缀或小数秒 |
| `tags` | `string[]`；空集合为 `[]`，不得为 `null` 或 CSV；元素去除首尾空白后不得为空，重复元素拒绝 |

目录价使用两个互不冒充的对象：

~~~json
{
  "referencePrice": {
    "amount": "88.00",
    "currency": "CNY",
    "unit": "ITEM",
    "sourceTitle": "已核验的站内来源标题"
  },
  "demoPrice": {
    "amount": "68.00",
    "currency": "CNY",
    "unit": "ITEM",
    "simulationNote": "本机演示价格，不代表真实经营报价"
  }
}
~~~

`referencePrice` 与 `demoPrice` 均可为 `null`，但二者不能互相补值。`referencePrice.sourceTitle` 只允许已核验且可公开的站内来源标题，不返回原始 URL。订单核价只使用非空且 `amount>0.00` 的 `demoPrice`；没有演示价时返回 `409 PRICE_UNAVAILABLE`，不得把 `referencePrice`、缺失值、零或前端金额改作订单价。`unit` 按资源固定为商品 `ITEM`、餐食 `PORTION`、房型 `ROOM_NIGHT`。

所有公开目录对象都有 `demoData:boolean`、`verificationStatus:UNVERIFIED|VERIFIED`、`catalogStatus:PUBLISHED`、`version:CatalogVersion`。`demoData=true` 不等于资料、坐标、授权或价格已经核验。公共端只返回自身及全部必需父资源均为 `PUBLISHED` 的对象；下架或归档对象在详情、报价与新订单中统一不可见。

### 14.2 公开目录对象

公开投影的字段集合固定如下，未列字段不得出现：

| 类型 | 字段 |
| --- | --- |
| `FoodMerchant` | `id`、`name`、`description`、`tags`、`imageUrl:string|null`、`demoData`、`verificationStatus`、`catalogStatus`、`version` |
| `Product` | `id`、`merchantId`、`merchantName`、`name`、`description`、`referencePrice:null|ReferencePrice`、`demoPrice:null|DemoPrice`、`pickupPoint`、`tags`、`imageUrl:string|null`、`orderable`、`demoData`、`verificationStatus`、`catalogStatus`、`version` |
| `FoodItem` | `id`、`merchantId`、`merchantName`、`name`、`description`、`itemType:DISH|DRINK|SET`、`referencePrice:null|ReferencePrice`、`demoPrice:null|DemoPrice`、`visitTimeText:string|null`、`tags`、`imageUrl:string|null`、`orderable`、`demoData`、`verificationStatus`、`catalogStatus`、`version` |
| `StayProperty` | `id`、`merchantId`、`merchantName`、`name`、`description`、`locationText:string|null`、`tags`、`imageUrl:string|null`、`demoData`、`verificationStatus`、`catalogStatus`、`version`、`roomTypes:RoomType[]` |
| `RoomType` | `id`、`stayPropertyId`、`stayPropertyName`、`name`、`description`、`maxGuestsPerRoom:integer`、`referencePrice:null|ReferencePrice`、`demoPrice:null|DemoPrice`、`imageUrl:string|null`、`orderable`、`demoData`、`verificationStatus`、`catalogStatus`、`version` |
| `Place` | `id`、`name`、`category`、`description`、`tags`、`imageUrl:string|null`、`schematicPosition:null|SchematicPosition`、`demoData`、`verificationStatus`、`catalogStatus`、`version` |

`orderable` 只有在资源与必需父资源均已发布，且 `demoPrice` 非空、`amount>0.00`、`currency=CNY`、`unit` 与资源固定的 `ITEM|PORTION|ROOM_NIGHT` 单位匹配时才为 `true`。它不表示库存、餐位或房态可用。`maxGuestsPerRoom` 是每间房的演示容量正整数；没有容量时该房型不得进入住宿报价或新预约，不能按无限容量处理。

`imageUrl` 只允许获准公开的资源 URL，不返回本机路径、对象存储私有键或临时签名参数。商家联系方式不在任何公共目录或 Python 目录投影中出现。

### 14.3 公开目录路由

| 方法与路由 | 查询参数 | `data` |
| --- | --- | --- |
| `GET /api/products` | `categoryTag`、可重复 `tag` 可选 | `Product[]` |
| `GET /api/products/{id}` | 无 | `Product` |
| `GET /api/food-merchants` | 可重复 `tag` 可选 | 至少有一个已发布餐食项的 `FoodMerchant[]` |
| `GET /api/food-merchants/{id}` | 无 | `FoodMerchant` |
| `GET /api/foods` | `merchantId` 必填；`categoryTag`、可重复 `tag` 可选 | 该店 `FoodItem[]` |
| `GET /api/foods/{id}` | 无 | `FoodItem` |
| `GET /api/stays` | `peopleCount`、`roomTypeId`、可重复 `tag` 可选 | `StayProperty[]`，内含匹配的已发布房型 |
| `GET /api/stays/{id}` | 无 | `StayProperty`，内含已发布房型 |
| `GET /api/room-types/{id}` | 无 | `RoomType` |
| `GET /api/places` | `category`、可重复 `tag` 可选 | `MapPlacesResult` |
| `GET /api/places/{id}` | 无 | `Place` |

`merchantId` 必须是当前公开餐食店铺的规范 UUID；不存在、未发布或不具公开餐食项时返回 `404 RESOURCE_NOT_FOUND`。每张餐食篮只能使用同一次 `/api/foods?merchantId=...` 对应的一家店。`categoryTag` 仍是受控界面类别到既有 `tags` 元素的映射，不新增类别列；它与每个 `tag` 同时出现时必须全部匹配。

住宿 `peopleCount` 只以 `maxGuestsPerRoom` 作目录提示过滤，不代表实时房态；实际提交容量按 `maxGuestsPerRoom * roomCount` 重新校验。日期、餐食到店时间、价格区间、库存、餐位、房态和班次都不是列表查询条件。列表无结果返回 `200` 与空数组；详情不存在或不可见返回 `404 RESOURCE_NOT_FOUND`。

### 14.4 后台目录边界

既有六组后台资源路由在本修订中继续使用：`/api/admin/merchants`、`products`、`foods`、`stays`、`room-types`、`places`，各自有集合 `GET/POST`、单项 `GET/PATCH` 和专用 `PATCH .../{id}/catalog-status`。没有物理删除路由。

六类在线 `POST` 的请求白名单固定如下；“必填”字段必须出现且不得为 `null`，“可选”字段可省略并按括号内默认值规范化，“可空”字段可显式传 `null`：

| 资源 | 必填 | 可选／可空 |
| --- | --- | --- |
| merchant | `name`、`description` | `contactPhone:null|string`、`tags:[]|string[]`、`imageUrl:null|string` |
| product | `merchantId`、`name`、`description`、`pickupPoint` | `demoPrice:null|DemoPrice`、`tags:[]|string[]`、`imageUrl:null|string` |
| food | `merchantId`、`name`、`description`、`itemType:DISH|DRINK|SET` | `demoPrice:null|DemoPrice`、`visitTimeText:null|string`、`tags:[]|string[]`、`imageUrl:null|string` |
| stay | `merchantId`、`name`、`description` | `locationText:null|string`、`tags:[]|string[]`、`imageUrl:null|string` |
| room-type | `stayPropertyId`、`name`、`description`、`maxGuestsPerRoom` | `demoPrice:null|DemoPrice`、`imageUrl:null|string` |
| place | `name`、`category`、`description` | `tags:[]|string[]`、`imageUrl:null|string`、`schematicPosition:null|SchematicPosition` |

`DemoPrice` 必须完整包含 `amount`、`currency=CNY`、资源固定 `unit` 和 `simulationNote`；不得部分 PATCH 内部字段，改价时整体替换对象。`merchantId`、`stayPropertyId` 和所有字符串／数组／坐标继续遵守第 14.1～14.2 节。管理响应为相应完整目录对象；merchant 管理投影额外含 `contactPhone`，其他响应不返回该电话。

普通 `PATCH` 请求必须含 `expectedVersion`，并至少含下表一个可编辑字段；省略字段保持不变，列外字段拒绝：

| 资源 | 可编辑字段 |
| --- | --- |
| merchant | `name`、`description`、`contactPhone`、`tags`、`imageUrl` |
| product | `name`、`description`、`pickupPoint`、`demoPrice`、`tags`、`imageUrl` |
| food | `name`、`description`、`itemType`、`demoPrice`、`visitTimeText`、`tags`、`imageUrl` |
| stay | `name`、`description`、`locationText`、`tags`、`imageUrl` |
| room-type | `name`、`description`、`maxGuestsPerRoom`、`demoPrice`、`imageUrl` |
| place | `name`、`category`、`description`、`tags`、`imageUrl`、`schematicPosition` |

在线 `POST` 固定生成 `demoData=true`、`verificationStatus=UNVERIFIED`、`catalogStatus=UNPUBLISHED`、`version=1`；请求不得写这四个字段或 `id`、时间。普通 `PATCH` 必须携带 `expectedVersion`，省略的可编辑字段保持不变，至少修改一个业务字段；关系字段 `merchantId`、`stayPropertyId` 不可通过普通编辑换绑。状态请求固定为：

~~~json
{
  "expectedVersion": 3,
  "catalogStatus": "PUBLISHED"
}
~~~

状态只允许 `PUBLISHED|UNPUBLISHED|ARCHIVED`，并以 `id + expectedVersion` 条件更新；不匹配返回 `409 VERSION_CONFLICT`。被订单或草稿引用的资料只下架／归档，不物理删除。在线后台只能写演示价及其说明，不得把资料改为 `VERIFIED` 或写 `referencePrice`；后两项只允许后续受控、可追溯的离线核验导入。地点真实经纬度即使已在受控记录中存在，也不由本修订的公共地图返回。

### 14.5 水彩示意地图

`SchematicPosition` 固定为：

~~~json
{
  "x": "0.2750",
  "y": "0.6400"
}
~~~

`x`、`y` 是四位小数的字符串且范围为 `0.0000`～`1.0000`，只表示水彩画布内相对位置，不是经纬度、像素或比例尺。`MapPlacesResult` 固定为：

~~~json
{
  "mapMode": "SCHEMATIC",
  "navigationAvailable": false,
  "scale": "NOT_TO_SCALE",
  "notice": "水彩示意图，仅用于展示地点顺序，不提供实时导航、精确距离或预计时长。",
  "places": []
}
~~~

公开地点可以在没有真实地图服务时正常展示；只有平台中当前已发布、具有真实 `id` 的 `Place` 才能成为结构化节点。个人行程或路线攻略按其 `sequence` 顺序连接同时具有 `schematicPosition` 的地点；缺失位置的节点保留文字但不绘制，不移动其他节点，也不生成替代 ID。任何调用方不得从示意坐标计算或展示经纬度、道路、精确里程、ETA、实时交通、班次、导航或安全承诺。

### 14.6 社区 DTO 与兼容读取

新投稿只有两个严格请求变体。`MOMENT`：

~~~json
{
  "postType": "MOMENT",
  "content": "清晨沿溪散步的个人记录。",
  "tags": ["村寨生活"]
}
~~~

`ROUTE_GUIDE`：

~~~json
{
  "postType": "ROUTE_GUIDE",
  "title": "半日慢行",
  "content": "个人经验，仅供演示参考。",
  "tags": ["路线攻略"],
  "routeSummary": "按示意节点依次游览",
  "routeNodes": [
    {
      "sequence": 1,
      "placeId": "00000000-0000-0000-0000-000000000000",
      "note": "从公开地点开始"
    }
  ]
}
~~~

- 新投稿 `content` 去除首尾空白后为 1～2,000 个 Unicode 码点；`title` 为 1～80 个码点；最多 5 个不同标签，每个为 1～20 个码点。
- `MOMENT` 请求不得出现 `title`、`routeSummary` 或 `routeNodes`，即使值为 `null` 也拒绝。
- `ROUTE_GUIDE` 的 `routeSummary` 为 1～300 个码点，`routeNodes` 为 1～12 项；`sequence` 必须无重复且严格为 `1..N`，请求数组也必须按该顺序排列。
- 新路线节点的 `placeId` 必填且不得为 `null`，必须引用提交时当前已发布的地点；`note` 可为 `null`，非空时最多 300 个码点。请求不接受 `placeName`，响应从地点记录生成名称快照。
- 无平台公开地点 ID 的地点只能写入 `content`，不得伪造 UUID、复用其他地点 ID 或把名称当 ID。作者名从 USER 账号昵称生成，请求不接受作者、发布状态、图片、HTML 或 `accountId`。

公开 `CommunityPost` 字段为 `id`、`postType`、`title:string|null`、`content`、`authorName`、`coverUrl:string|null`、`tags`、`routeSummary:string|null`、`routeNodes:RouteNode[]`、`demoData`、`legacyData`、`publishedAt`。`RouteNode` 字段为 `sequence`、`placeId:UUID|null`、`placeName`、`note:string|null`、`drawable:boolean`。新投稿固定 `coverUrl=null`、`demoData=true`、`legacyData=false`，由服务端设置 `publishedAt`；新 `MOMENT` 的 `routeNodes=[]`，新 `ROUTE_GUIDE` 的节点均有非空 `placeId`，但地点后来下架或缺少示意位置时 `drawable=false`。

| 方法与路由 | 规则 | `data` |
| --- | --- | --- |
| `GET /api/posts?type=&tag=` | 只读已发布内容；`type=MOMENT|ROUTE_GUIDE`；按 `publishedAt`、`id` 降序 | `CommunityPost[]` |
| `GET /api/posts/{id}` | 已发布内容 | `CommunityPost` |
| `POST /api/posts` | USER；严格使用上述二选一请求 | 新 `CommunityPost`，HTTP 201 |

既有社区记录不重新套用新投稿长度规则：读取时不得截断正文、标题、标签、摘要或节点，也不得为缺失 `placeId` 补造公开地点。旧节点没有合法公开地点 ID 时保留原 `placeName` 和原顺序，输出 `placeId=null`、`drawable=false`、`legacyData=true`；旧记录的重复或断裂序号按持久原值稳定读取，不静默重排。旧 `coverUrl` 原值继续留存，只有既有公开授权仍可确认时才在只读投影返回，否则返回 `null`，不得用其他图片替代。该兼容只读投影不允许以旧形状创建或修改新投稿。本期仍无点赞、评论、收藏、上传、举报、审核或删除功能。

## 15. 三类核价与正式订单

### 15.1 核价请求

核价是 USER Bearer 保护的只读计算型 POST，不使用 `Idempotency-Key`，不保存临时订单、不占库存／餐位／房态，也不承诺随后可提交。三个固定路由和严格请求如下：

`POST /api/order-quotes/product`

~~~json
{
  "productId": "00000000-0000-0000-0000-000000000000",
  "quantity": 2,
  "pickupPoint": "公开目录中的现场取货点"
}
~~~

`POST /api/order-quotes/food`

~~~json
{
  "merchantId": "00000000-0000-0000-0000-000000000000",
  "items": [
    {
      "foodItemId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ],
  "visitAt": "2026-09-12T18:30:00",
  "peopleCount": 2
}
~~~

`POST /api/order-quotes/stay`

~~~json
{
  "roomTypeId": "00000000-0000-0000-0000-000000000000",
  "checkInDate": "2026-09-12",
  "checkOutDate": "2026-09-14",
  "roomCount": 1,
  "peopleCount": 2
}
~~~

数量、份数、人数与房间数均为正整数。餐食 `items` 为 1～50 项，数组顺序是正式订单明细顺序；相同 `foodItemId` 出现两次返回 `400 DUPLICATE_FOOD_ITEM`，服务端不得静默合并。所有餐食必须属于 `merchantId` 指定的同一家已发布店铺且均已发布，否则整单拒绝，不拆单、不部分报价。

住宿 `checkOutDate` 必须晚于 `checkInDate`，`nights` 由 Java 以两个本地日期之差计算且至少为 1。`peopleCount <= maxGuestsPerRoom * roomCount`；乘法先以足够宽的整数检查溢出。该校验只是演示容量条件，不是实时房态检查。

商品 `pickupPoint` 必须逐字等于当前公开目录值。餐食只表示到店餐食／茶点体验，住宿只表示入住预约，商品只表示现场自提；三个请求均不接受联系人、备注、客户端金额、折扣、支付、配送、库存、餐位或房态字段。

### 15.2 `OrderQuote` 与计算口径

成功 `data` 是按 `quoteType` 判别的严格 `OrderQuote`：

~~~json
{
  "quoteType": "FOOD",
  "currency": "CNY",
  "pricingKind": "DEMO",
  "lines": [
    {
      "sequence": 1,
      "resourceType": "FOOD",
      "resourceId": "00000000-0000-0000-0000-000000000000",
      "resourceName": "餐食演示项",
      "catalogVersion": 3,
      "unit": "PORTION",
      "unitAmount": "28.00",
      "quantity": 2,
      "lineAmount": "56.00",
      "demoData": true
    }
  ],
  "calculation": {
    "kind": "FOOD",
    "merchantId": "00000000-0000-0000-0000-000000000000",
    "visitAt": "2026-09-12T18:30:00",
    "peopleCount": 2
  },
  "totalAmount": "56.00",
  "quoteFingerprint": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "quotedAt": "2026-09-10T01:02:03Z",
  "locksInventory": false,
  "holdsCapacity": false,
  "notice": "本机模拟核价，不代表真实报价、库存、餐位或房态。"
}
~~~

`lines` 的共同字段固定为示例所列字段。商品只有一行，`resourceType=PRODUCT`、`unit=ITEM`、`quantity` 为商品数量；餐食逐项一行，`resourceType=FOOD`、`unit=PORTION`，顺序与请求一致；住宿只有一行，`resourceType=STAY`、`unit=ROOM_NIGHT`、`quantity=roomCount*nights`。

`calculation` 是下列严格判别联合，其他字段不得出现：

| `kind` | 字段 |
| --- | --- |
| `PRODUCT` | `kind`、`productId`、`quantity`、`pickupPoint` |
| `FOOD` | `kind`、`merchantId`、`visitAt`、`peopleCount` |
| `STAY` | `kind`、`roomTypeId`、`checkInDate`、`checkOutDate`、`nights`、`roomCount`、`peopleCount`、`maxGuestsPerRoom`、`totalCapacity` |

所有乘法逐行使用 Java `BigDecimal` 和整数数量，按两位小数精确计算；中间结果不得用二进制浮点，合计为行金额精确相加。商品为 `ITEM 单价 * quantity`，餐食为各 `PORTION 单价 * quantity` 后求和，住宿为 `ROOM_NIGHT 单价 * nights * roomCount`。没有对应单位的非空演示价时返回 `409 PRICE_UNIT_UNSUPPORTED`，不得猜测换算。

一次核价必须在同一个数据库一致性读取中取得全部父资源、项目、版本和演示价；不得把不同时点的多次无约束读取拼成一个指纹。该只读核价不持久化结果或占用业务资源，正式提交仍按下一节重新锁定并计算。

### 15.3 报价规范指纹与改价

`quoteFingerprint` 是 `sha256:` 加 64 位小写十六进制。Java 先完成严格 DTO 校验，再构造固定材料：

~~~json
{
  "contractVersion": "tourism-api-v3-draft-r3",
  "quoteType": "FOOD",
  "selection": {
    "merchantId": "00000000-0000-0000-0000-000000000000",
    "items": [
      {
        "foodItemId": "00000000-0000-0000-0000-000000000000",
        "quantity": 2
      }
    ],
    "visitAt": "2026-09-12T18:30:00",
    "peopleCount": 2
  },
  "priceInputs": [
    {
      "merchantId": "00000000-0000-0000-0000-000000000000",
      "merchantVersion": 2,
      "resourceId": "00000000-0000-0000-0000-000000000000",
      "catalogVersion": 3,
      "amount": "28.00",
      "currency": "CNY",
      "unit": "PORTION"
    }
  ]
}
~~~

三类 `selection` 分别与第 15.1 节对应请求完全相同；`priceInputs` 与报价行一一对应并保持相同顺序。商品和餐食项的输入除示例字段外固定包含 `merchantId`、`merchantVersion`；住宿输入固定包含 `merchantId`、`merchantVersion`、`stayPropertyId`、`stayPropertyVersion`，以便父资源改名、改状态或换履约条件时也要求重新确认。规范序列化固定为：对象键在每一级按 Unicode 码点升序；数组保持业务顺序；不输出无意义空白；UUID、日期、时间、金额和整数使用本文已规范化的字符串或十进制形式；JSON 字符串使用标准最短转义；所有列出的字段（包括可为 `null` 的字段）都输出。对所得 UTF-8 字节计算 SHA-256。`quotedAt`、账号、联系人、备注、凭据、请求 ID 和 `Idempotency-Key` 不进入报价指纹。

指纹只绑定选择条件及参与计价的当前目录版本／演示价，不是授权、签名、价格锁或库存凭据。正式提交必须在持久事务中按第 17.3 节先锁操作槽，再按 UUID 规范升序锁定涉及的商家、商品／餐食／住宿主体／房型行，重新校验发布状态、归属、履约条件、容量和演示价，并重新计算指纹；不得只比较提交前的一次内存结果。

若重新计算值与 `expectedQuoteFingerprint` 不同，该事务不创建订单，并在持有操作槽锁时把该旧操作可靠终结为第 17 节的 `NOT_APPLIED`，`terminalReason=QUOTE_CHANGED` 后提交。随后返回 `409 QUOTE_CHANGED`：

~~~json
{
  "success": false,
  "data": null,
  "message": "报价已变化，请核对当前金额后重新确认。",
  "code": "QUOTE_CHANGED",
  "details": {
    "kind": "quote_changed",
    "expectedQuoteFingerprint": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
    "currentQuote": {}
  }
}
~~~

`currentQuote` 是刚按当前目录计算的完整 `OrderQuote`。界面展示的“旧金额”只能来自客户端保留的、此前一次成功核价响应；它是历史展示值，不是本次服务端事实，也不得回传后被 Java 采信。若客户端已丢失旧报价，只显示“此前报价已变化”，不得编造旧金额。`currentQuote.totalAmount` 是“新金额”。用户核对新报价后必须发起新的主动提交：从页面仍保留的原选择重新构造完整请求正文，写入新报价指纹，并使用新的 `Idempotency-Key`；不得自动替用户改变商品、菜品、日期、人数、房数或联系人。旧 key 已永久绑定旧指纹和旧内容，不能装入新指纹或其他内容。

### 15.4 正式创建请求

以下路由都需要 USER 和规范 UUID `Idempotency-Key`。请求不得包含账号、状态、金额或订单 ID。

`POST /api/product-orders`：

~~~json
{
  "productId": "00000000-0000-0000-0000-000000000000",
  "quantity": 2,
  "pickupPoint": "公开目录中的现场取货点",
  "contactName": "演示联系人",
  "contactPhone": "00000000000",
  "note": null,
  "sourceThreadId": null,
  "expectedQuoteFingerprint": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
}
~~~

`POST /api/food-orders`：

~~~json
{
  "merchantId": "00000000-0000-0000-0000-000000000000",
  "items": [
    {
      "foodItemId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ],
  "visitAt": "2026-09-12T18:30:00",
  "peopleCount": 2,
  "contactName": "演示联系人",
  "contactPhone": "00000000000",
  "note": null,
  "sourceThreadId": null,
  "expectedQuoteFingerprint": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
}
~~~

`POST /api/stay-bookings`：

~~~json
{
  "roomTypeId": "00000000-0000-0000-0000-000000000000",
  "checkInDate": "2026-09-12",
  "checkOutDate": "2026-09-14",
  "roomCount": 1,
  "peopleCount": 2,
  "contactName": "演示联系人",
  "contactPhone": "00000000000",
  "note": null,
  "sourceThreadId": null,
  "expectedQuoteFingerprint": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
}
~~~

`contactName` 去除首尾空白后为 1～80 个 Unicode 码点。`contactPhone` 去除首尾空白后为 1～32 个字符，只允许 ASCII 数字、空格、`+`、`-`、`(`、`)`，且至少包含一个数字；这不冒充实名或真实号码核验。`note`、`sourceThreadId` 可省略或为 `null`；非空备注最多 500 个码点，非空 `sourceThreadId` 必须是规范 UUID。其他字段必填且不得为 `null`。`sourceThreadId` 只记录当前账号有权访问的来源会话；它不授权订单读取，无法核对时不得写入一个未经验证的值。

成功创建初态分别为 `PENDING_PICKUP`、`PENDING_VISIT`、`PENDING_CONFIRMATION`。原请求提交返回 HTTP 201 的 `WriteReceipt`，同 key、同规范摘要重试返回 HTTP 200 且 `replayed=true`；两者都只在订单和成功回执共同提交后返回。

### 15.5 正式订单与调用方投影

持久化订单的事实字段固定如下：

| 类型 | 持久化事实 |
| --- | --- |
| `ProductOrder` | `id`、`accountId`、商品 ID／名称／目录版本快照、数量、取货点、联系人、备注、演示单价／单位／合计快照、状态、`sourceThreadId:null|string`、`sourceDraftId:null`、`demoData`、创建／更新时间 |
| `FoodOrder` | `id`、`accountId`、店铺 ID／名称快照、到店时间、人数、联系人、备注、合计快照、状态、`sourceThreadId:null|string`、`sourceDraftId:null|UUID`、`demoData`、创建／更新时间；另有有序 `FoodOrderItem[]` |
| `FoodOrderItem` | `sequence`、餐食 ID／名称／类型／目录版本快照、单位、演示单价、份数、行金额、`demoData` |
| `StayBooking` | `id`、`accountId`、住宿主体及房型 ID／名称／目录版本快照、入住／离店日期、夜数、房间数、人数、下单时每间容量、联系人、备注、单位／演示单价／合计快照、状态、`sourceThreadId:null|string`、`sourceDraftId:null|UUID`、`demoData`、创建／更新时间 |

目录后续改名、改价、下架或归档不得改写订单快照。所有金额字段使用第 14.1 节的十进制字符串和 `currency=CNY`；本修订不增加支付状态。

本人 `ProductOrderView` 字段为：`id`、`orderType=PRODUCT`、`productId`、`productName`、`catalogVersionAtOrder`、`quantity`、`pickupPoint`、`unit`、`unitAmount`、`totalAmount`、`currency`、`note`、`status`、`demoData`、`createdAt`、`updatedAt`。

本人 `FoodOrderView` 字段为：`id`、`orderType=FOOD`、`merchantId`、`merchantName`、`items:FoodOrderItemView[]`、`visitAt`、`peopleCount`、`totalAmount`、`currency`、`note`、`status`、`demoData`、`createdAt`、`updatedAt`。`FoodOrderItemView` 为持久明细除内部主键外的全部快照字段。

本人 `StayBookingView` 字段为：`id`、`orderType=STAY`、`stayPropertyId`、`stayPropertyName`、`roomTypeId`、`roomTypeName`、`catalogVersionAtOrder`、`checkInDate`、`checkOutDate`、`nights`、`roomCount`、`peopleCount`、`maxGuestsPerRoomAtOrder`、`unit`、`unitAmount`、`totalAmount`、`currency`、`note`、`status`、`demoData`、`createdAt`、`updatedAt`。

三个本人投影均不得返回 `accountId`、`contactName`、`contactPhone`、`sourceThreadId`、`sourceDraftId` 或操作回执内部字段。本人路由固定为：

| 方法与路由 | `data` |
| --- | --- |
| `GET /api/me/product-orders`、`GET /api/me/product-orders/{id}` | 本人 `ProductOrderView[]`／单项 |
| `GET /api/me/food-orders`、`GET /api/me/food-orders/{id}` | 本人 `FoodOrderView[]`／单项 |
| `GET /api/me/stay-bookings`、`GET /api/me/stay-bookings/{id}` | 本人 `StayBookingView[]`／单项 |

查询必须在 SQL 中同时限定 `id` 与 `account_id=JWT sub`；不存在与不属于本人统一为 `404 RESOURCE_NOT_FOUND`。不得用联系人、手机号、昵称、`sourceThreadId` 或客户端账号标识查单。

ADMIN 投影在对应本人投影上增加 `accountId`、`contactName`、`contactPhone`、`sourceThreadId`、`sourceDraftId`，只用于既定履约。后台列表固定为 `/api/admin/orders/products|foods|stays`，可选查询参数为各自合法 `status` 和 `merchantId`；二者同时出现时均须匹配。管理员仍不能读取未提交食宿草稿。

Python 的 `OrderStatusSummary` 只允许 `orderType=FOOD|STAY`、`orderId`、`resourceId`、`resourceName`、`status`、`createdAt`、`updatedAt`。第 8.3 节内部查询必须同时以 USER proof 的 `account_id` 和已核验 `source_thread_id` 限定；不返回商品订单、明细、金额、联系人、备注、草稿、账号或来源线程字段。

### 15.6 状态与并发条件

合法状态机固定为：

~~~text
商品：PENDING_PICKUP -> PICKED_UP
                    -> CANCELLED

餐食：PENDING_VISIT -> COMPLETED
                   -> CANCELLED

住宿：PENDING_CONFIRMATION -> CONFIRMED -> COMPLETED
                         \-> CANCELLED
       CONFIRMED -----------------------> CANCELLED
~~~

游客取消路由为 `POST /api/me/product-orders/{id}/cancel`、`food-orders/{id}/cancel`、`stay-bookings/{id}/cancel`，请求只含 `expectedStatus`。它们分别只接受本人 `PENDING_PICKUP`、`PENDING_VISIT`、`PENDING_CONFIRMATION`，并执行 `UPDATE ... WHERE id=? AND account_id=? AND status=?`；影响行数不是 1 时重新按本人范围读取并返回 `409 INVALID_STATUS_TRANSITION` 或 `404 RESOURCE_NOT_FOUND`。游客不能取消已确认住宿。

后台状态路由沿用 `/api/admin/orders/products|foods|stays/{id}/status`，请求严格为：

~~~json
{
  "expectedStatus": "PENDING_VISIT",
  "status": "COMPLETED"
}
~~~

服务端按订单类型、当前状态和箭头校验，并以 `id + expectedStatus` 条件更新。跨类型值、跳跃、重复写终态和过时状态统一返回 `409 INVALID_STATUS_TRANSITION`；不得靠先读后无条件更新覆盖并发结果。创建接口是产生初态的唯一方式；AI、目录接口和草稿保存均不能直接写正式订单状态。本期没有支付、退款、配送、逐菜履约、取消审批、实时库存、餐位或房态状态。

## 16. 已保存行程与食宿草稿

### 16.1 `SavedItinerary`

已保存行程与 AI 对话／检查点分开，由 Java／MySQL 按账号持久化。`SavedItineraryView` 固定为 `id`、`version`、`content`、`demoData`、`createdAt`、`updatedAt`。`content` 固定为：

~~~json
{
  "title": "两日水彩慢游",
  "travelDate": null,
  "peopleCount": null,
  "days": [
    {
      "day": 1,
      "theme": "沿溪慢行",
      "stops": [
        {
          "sequence": 1,
          "targetType": "PLACE",
          "targetId": "00000000-0000-0000-0000-000000000000",
          "title": "公开地点名称",
          "note": null
        }
      ]
    }
  ]
}
~~~

`title` 为 1～80 个 Unicode 码点；`travelDate`、`peopleCount` 可为 `null`，非空人数为正整数。`days` 为 1～30 项，`day` 必须按数组严格为 `1..N`；每个 `stops` 最多 30 项，`sequence` 同样连续且与数组顺序一致。`theme`、`note` 可为 `null`，非空时分别最多 80／500 个码点。

`targetType` 与 `targetId` 必须同时为 `null`，或同时为 `PLACE|PRODUCT|FOOD|STAY|ROUTE_GUIDE` 和对应当前公开资源的规范 UUID；`STAY` 指房型。没有可靠平台 ID 的叙述项使用二者都为 `null` 并在 `title`／`note` 说明，不得伪造 ID。示意地图只连接 `targetType=PLACE` 且具有示意位置的 stops，遵守第 14.5 节。

路由固定为：

| 方法与路由 | 请求 | 成功 `data` |
| --- | --- | --- |
| `GET /api/me/itineraries`、`GET /api/me/itineraries/{id}` | 无 | 本人列表／单项 |
| `POST /api/me/itineraries` | `content` | HTTP 201 `WriteReceipt<SavedItineraryView>` |
| `PUT /api/me/itineraries/{id}` | `expectedVersion`、`content` | HTTP 200 `WriteReceipt<SavedItineraryView>` |
| `POST /api/me/itineraries/adoptions` | `candidateRef` | HTTP 201 `WriteReceipt<SavedItineraryView>` |
| `POST /api/me/itineraries/{id}/adoptions` | `candidateRef`、`expectedVersion` | HTTP 200 `WriteReceipt<SavedItineraryView>` |

四个写路由均需要第 17 节规定的 `Idempotency-Key`。`PUT` 是完整内容替换，不是字段 PATCH；必须以 `id + account_id + expectedVersion` 条件更新并递增版本。AI 更新还必须保证 `expectedVersion` 等于候选生成时绑定的基础行程版本，不能改填当前最新版绕过冲突。行程不因聊天到期或闲置自动删除，本修订不增加版本历史库、离线副本或自动合并。

### 16.2 食宿草稿内容和可空规则

`FoodDraftContent` 的字段全部在请求和响应中出现：

~~~json
{
  "merchantId": "00000000-0000-0000-0000-000000000000",
  "items": [
    {
      "foodItemId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ],
  "visitAt": null,
  "peopleCount": null,
  "contactName": null,
  "contactPhone": null,
  "note": null
}
~~~

`merchantId` 必填；`items` 为 1～50 个同店餐食项，规则与第 15.1 节相同。`visitAt`、`peopleCount`、`contactName`、`contactPhone`、`note` 均允许 `null`，因此未填日期／人数／联系人仍可保存；非空值按第 15 节格式与长度校验。空字符串不是 `null`，不得用空字符串表示未填。

`StayDraftContent` 同样要求所有字段出现：

~~~json
{
  "roomTypeId": "00000000-0000-0000-0000-000000000000",
  "checkInDate": null,
  "checkOutDate": null,
  "roomCount": null,
  "peopleCount": null,
  "contactName": null,
  "contactPhone": null,
  "note": null
}
~~~

`roomTypeId` 必填且必须引用正确类型的当前可见房型。两个日期、房间数、人数和两个联系人字段均可各自为 `null`；若两个日期都非空，保存时就必须满足离店晚于入住；若人数与房间数都非空，保存时就必须满足当前已知容量。目录后来变化不会删除草稿，但提交时必须按当前目录重新校验。备注可为 `null`。草稿保存允许上述明确缺项，不允许未知资源、错误资源类型、跨店餐食、重复餐食项、非正整数、非法日期或伪造 ID。

### 16.3 草稿状态与本人投影

`FoodDraft` 与 `StayDraft` 只有 `DRAFT`、`SUBMITTED` 两个状态。创建版本为 1；每次成功手动保存、采用 AI 更新或提交都递增 1。`SUBMITTED` 是只读终态，不得恢复为 `DRAFT`。

草稿持久元数据另含服务端写入的 `sourceThreadId:string|null`：AI 首次采用取已核验 `candidateRef.threadId`，AI 更新取本次已核验候选线程，手动保存保持原值，提交时复制到正式订单。客户端草稿正文不得设置或清空它；本人草稿投影、内部规划投影和后台均不返回该字段，它也不能作为授权。

本人详情是按 `state` 判别的严格联合：

- `DRAFT` 投影字段：`id`、`draftType=FOOD|STAY`、`version`、`state=DRAFT`、对应完整 `content`（包括可为空的 `contactName`、`contactPhone`）、`linkedOrder=null`、`demoData`、`createdAt`、`updatedAt`。
- `SUBMITTED` 投影字段：`id`、`draftType`、`version`、`state=SUBMITTED`、去除 `contactName`、`contactPhone` 后的规划内容、`linkedOrder:{orderType:FOOD|STAY,orderId}`、`demoData`、`createdAt`、`updatedAt`。已提交投影不得出现两个联系人键，即使值为 `null` 也不得出现。

本人列表使用同一联合投影；因此未提交草稿可在同账号 Web 与小程序回读联系人并继续填写，已提交草稿不会新增联系人回显。无归属与不存在统一返回 `404 RESOURCE_NOT_FOUND`。后台没有食宿私人草稿路由。

Python 内部规划投影只含 `id`、`draftType`、`version`、`state`、`merchantId/items/visitAt/peopleCount` 或 `roomTypeId/checkInDate/checkOutDate/roomCount/peopleCount`、`demoData`；永远不含 `contactName`、`contactPhone`、私人 `note`、`linkedOrder`、`sourceThreadId`、账号标识或回执。第 8.3 节的内部草稿读取仍须 USER proof、本人与资源归属，且不能用服务凭据遍历私人草稿。

### 16.4 草稿路由与保存语义

| 方法与路由 | 请求 | 结果 |
| --- | --- | --- |
| `GET /api/me/food-drafts`、`GET /api/me/food-drafts/{id}` | 无 | 本人联合投影列表／单项 |
| `GET /api/me/stay-drafts`、`GET /api/me/stay-drafts/{id}` | 无 | 本人联合投影列表／单项 |
| `POST /api/me/food-drafts/adoptions` | `candidateRef` | 创建 DRAFT，HTTP 201 回执 |
| `POST /api/me/stay-drafts/adoptions` | `candidateRef` | 创建 DRAFT，HTTP 201 回执 |
| `POST /api/me/food-drafts/{id}/adoptions` | `candidateRef`、`expectedVersion` | 更新 DRAFT，HTTP 200 回执 |
| `POST /api/me/stay-drafts/{id}/adoptions` | `candidateRef`、`expectedVersion` | 更新 DRAFT，HTTP 200 回执 |
| `PUT /api/me/food-drafts/{id}` | `expectedVersion`、完整 `content` | 手动保存，HTTP 200 回执 |
| `PUT /api/me/stay-drafts/{id}` | `expectedVersion`、完整 `content` | 手动保存，HTTP 200 回执 |

`candidateRef` 固定只含 `threadId:UUID`、`candidateId:UUID`、`candidateVersion:positive integer`；它不是授权。对尚未成功的新采用，Java 按第 8.4 节携带服务身份、当前 USER proof，并在匿名来源时携带原 ANONYMOUS proof，精确读取该版本。第 27 节冻结候选内容载荷；Java 还必须校验候选类型、资源 ID、同店／容量和基础版本后写业务表，不接受客户端回传的候选全文。

AI 首次采用生成的食宿草稿联系人固定为 `null`。AI 更新只可更新规划列；数据库写入必须列出规划列，不得对整行或完整 JSON 做候选覆盖，已有 `contact_name`、`contact_phone` 保持原值。候选缺联系人不是清空指令。候选查找超时返回 `503 CANDIDATE_LOOKUP_UNAVAILABLE`，不能当成候选过期、保存失败或成功；已成功原 key 的重试先返回成功回执，不因候选后来过期而失败。

手动 `PUT` 使用完整内容快照，允许本人明确把未提交草稿联系人字段设为 `null`；省略字段、部分 PATCH 或额外字段拒绝。更新 SQL 必须至少包含 `WHERE id=? AND account_id=? AND version=? AND state='DRAFT'`，影响行数不是 1 时重新在本人范围读取：版本不同返回 `409 VERSION_CONFLICT`，已提交返回 `409 DRAFT_ALREADY_SUBMITTED`，不存在或非本人返回 404。任何冲突都不得自动合并、改填新版本或覆盖。

### 16.5 草稿正式提交

固定路由：

~~~text
POST /api/me/food-drafts/{id}/submit
POST /api/me/stay-drafts/{id}/submit
~~~

二者需要 USER、`Idempotency-Key`，请求严格为：

~~~json
{
  "expectedVersion": 7,
  "expectedQuoteFingerprint": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
}
~~~

提交只读取 `expectedVersion` 对应的已保存草稿内容；请求不得夹带另一份 items、日期、人数、联系人、备注、金额或账号。页面上的最新联系人或规划改动尚未确认保存时不得提交。食宿完整性要求：

| 草稿 | 提交时非空条件 |
| --- | --- |
| FOOD | `merchantId`、非空且同店无重复的 `items`、`visitAt`、正整数 `peopleCount`、非空合法 `contactName` 与 `contactPhone` |
| STAY | `roomTypeId`、`checkInDate`、`checkOutDate`、正整数 `roomCount` 与 `peopleCount`、非空合法 `contactName` 与 `contactPhone`；离店晚于入住且人数不超过当前容量乘房数 |

缺项返回 `409 DRAFT_INCOMPLETE`，`details` 只含 `kind=draft_incomplete` 和按固定字段顺序排列的 `missingFields:string[]`，不回显联系人内容。资源下架、类型／归属错误、日期或容量不再合法按对应业务错误返回。提交前必须使用草稿的完整规划字段调用第 15 节同类核价；`expectedQuoteFingerprint` 不匹配时遵守 `QUOTE_CHANGED` 和新 key 规则，草稿保持 `DRAFT`。

提交成功的单个 MySQL 事务必须按第 17.3 节先锁操作槽，然后：

1. `SELECT ... FROM food_draft|stay_draft WHERE id=? AND account_id=? FOR UPDATE`，核对 `version=expectedVersion` 与 `state='DRAFT'`。
2. 按规范 UUID 升序锁定店铺、餐食项，或住宿主体、房型目录行；重新校验发布、同店、演示价、日期、容量和报价指纹。
3. 插入正式订单；餐食还按请求顺序插入全部明细及名称／类型／版本／单价／数量／行金额快照。
4. 执行 `UPDATE ... SET state='SUBMITTED', version=version+1, linked_order_id=?, submitted_at=? WHERE id=? AND account_id=? AND version=? AND state='DRAFT'`，影响行数必须为 1。
5. 把同一操作槽从 `RESERVED` 更新为 `SUCCEEDED`，记录订单结果与 `submittedDraft` 的新版本；影响行数必须为 1。
6. 仅在上述全部成功后提交；任一步失败则订单、餐食明细、草稿状态／关联与成功回执全部回滚。

`food_order.source_draft_id` 与 `stay_booking.source_draft_id` 各自允许 `null`，但非空值必须有唯一约束；直接确认页订单使用 `null`，草稿提交写对应草稿 ID。该约束是“一草稿一单”的数据库兜底，不以 `Idempotency-Key` 代替。另一 key 再提交同一草稿返回 `409 DRAFT_ALREADY_SUBMITTED`；只有请求者仍是本人时，`details` 才可包含已存在的 `linkedOrder`，不能冒充新建成功。

成功回执的主 `resource` 是本人无联系人的正式订单投影，`committedVersion=null`；`submittedDraft` 为 `{"id":"UUID","committedVersion":8,"state":"SUBMITTED"}`。订单版本与草稿版本不得混为一个字段。

### 16.6 自动保存与跨端读取

Web 与小程序在停止编辑约 800 毫秒后，对同一记录一次只发送一个手动 `PUT`；新编辑等待前一结果。该客户端串行不替代服务端版本和回执。失败、冲突或结果未知时进入暂停，保留页面内容；网络恢复、回到前台或刷新列表均不得自动恢复上传。用户点击“继续保存”时先走第 17.6 节主动 reconcile，确认结果后才用新 key 保存尚未保存的新内容。

进入页面、回到前台和用户手动刷新时通过 HTTP 按需读取；不轮询、不为个人业务增加 WebSocket 推送。存在未保存编辑、保存中或未知结果时，读取到新版只提示，不覆盖当前编辑、不解除暂停。离开页面有未保存内容时尽力提示；离开不撤销已发请求，强制关页或小程序回收不保证保全。本修订不增加本地长期副本、离线同步或完整修订历史。

## 17. 持久操作槽、防重与结果核对

### 17.1 适用操作与唯一作用域

下列 `operationType` 是封闭枚举，并由固定路由在服务端确定；客户端不能在请求体选择操作类型：

~~~text
CREATE_PRODUCT_ORDER
CREATE_FOOD_ORDER
CREATE_STAY_BOOKING
CREATE_ITINERARY
ADOPT_ITINERARY_CREATE
ADOPT_ITINERARY_UPDATE
SAVE_ITINERARY
ADOPT_FOOD_DRAFT_CREATE
ADOPT_FOOD_DRAFT_UPDATE
SAVE_FOOD_DRAFT
SUBMIT_FOOD_DRAFT
ADOPT_STAY_DRAFT_CREATE
ADOPT_STAY_DRAFT_UPDATE
SAVE_STAY_DRAFT
SUBMIT_STAY_DRAFT
~~~

这些创建、采用、提交与手动保存路由必须携带：

~~~text
Idempotency-Key: <canonical-lowercase-UUID>
~~~

唯一槽为 `(account_id, operation_type, request_key)`。`account_id` 只取当前已验证 USER JWT 的 `sub`，`operation_type` 只取匹配到的固定路由，`request_key` 只取请求头；三者都不能从业务正文覆盖。key 不是授权，不跨账号共享结果；同一 UUID 可在不同 `operationType` 形成不同槽，但客户端每次新的主动业务动作应生成新 key，同一次网络重试必须复用原 key 和原内容。

社区投稿、游客取消和后台状态迁移不属于本节回执范围；它们分别依赖普通创建结果或第 15.6 节的状态条件，本文不擅自把“继续保存”扩展为取消、发布或后台处理。知识发布使用第 24 节的独立 ADMIN 请求键与发布任务账本，不复用本节 USER 操作槽。

### 17.2 规范请求摘要

服务端通过严格 DTO 后构造：

~~~json
{
  "contractVersion": "tourism-api-v3-draft-r3",
  "accountId": "00000000-0000-0000-0000-000000000000",
  "operationType": "SAVE_FOOD_DRAFT",
  "requestKey": "00000000-0000-0000-0000-000000000000",
  "target": {
    "resourceType": "FOOD_DRAFT",
    "resourceId": "00000000-0000-0000-0000-000000000000"
  },
  "request": {
    "expectedVersion": 7,
    "content": {}
  }
}
~~~

`target.resourceId` 对新建操作固定为 `null`，对路径中已有资源固定为规范路径 ID。`request` 是对应严格请求的规范值：所有该 DTO 定义的字段都出现；只有对应 DTO 明确允许缺省的 `note`、`sourceThreadId` 才先规范成 `null`。`candidateRef`、`expectedVersion`、`expectedQuoteFingerprint`、联系人和完整业务内容按适用操作纳入。凭据、Cookie、JWT、CSRF、Origin、`X-Request-Id`、网络重试次数和客户端诊断字段不得进入。

序列化规则与第 15.3 节相同；数组保持业务顺序，尤其保留餐食明细、行程日程和 stops 顺序。文本只执行各 DTO 明确规定的去除首尾空白，不做大小写、Unicode 或内容改写。摘要为规范 UTF-8 字节的 `sha256:` 加 64 位小写十六进制。重复餐食、重复标签、未知字段和非法类型必须在计算前拒绝，不得通过“摘要相同”放行无效正文。

一旦槽创建，`request_digest` 永不改变。同账号、同操作、同 key 但摘要不同一律返回 `409 IDEMPOTENCY_CONFLICT`；不论前一业务事务成功、失败、回滚、仍未知或已 `NOT_APPLIED`，都不能用 UPSERT、重试或 reconcile 覆盖原摘要。报价变更后的新指纹会改变摘要，因此必须使用新 key。

### 17.3 槽记录与状态

MySQL 逻辑表 `operation_receipt` 至少冻结以下列；实际迁移编号不在本文分配：

| 列 | 规则 |
| --- | --- |
| `account_id`,`operation_type`,`request_key` | 联合唯一且非空 |
| `request_digest` | 非空规范 SHA-256；创建后不可修改 |
| `requested_resource_type`,`requested_resource_id` | 固定目标；新建 ID 未知时后者为 `null` |
| `state` | `RESERVED|SUCCEEDED|NOT_APPLIED` |
| `result_resource_type`,`result_resource_id` | 仅 `SUCCEEDED` 非空 |
| `committed_version` | 版本化主结果的原提交版本；正式订单为 `null` |
| `submitted_draft_id`,`submitted_draft_version` | 仅食宿草稿提交成功非空 |
| `terminal_reason` | 仅 `NOT_APPLIED` 的封闭非敏感原因码 |
| `reserved_at`,`committed_at`,`terminal_at` | UTC 服务器时间；按状态分别非空 |

`RESERVED` 是持久失败尝试指纹和竞争槽，不是“已受理”、成功或未执行证明。`SUCCEEDED` 与 `NOT_APPLIED` 都是不可变终态；所有状态更新必须带 `WHERE state='RESERVED' AND request_digest=?`，影响行数必须为 1。任何路径都不得把终态改回 `RESERVED`、把两种终态互换或覆盖结果 ID／版本。数据库唯一约束是并发裁决点，Redis 锁、JVM 锁和前端防连点都不能替代。

`terminal_reason` 只允许 `RECONCILED_BEFORE_APPLY`、`QUOTE_CHANGED`、`VERSION_CONFLICT`、`DRAFT_INCOMPLETE`、`RESOURCE_NOT_WRITABLE`。它说明服务端在同一槽锁下可靠确认该 key 没有提交业务变更，不保存错误原文或私人内容。

### 17.4 原写入 SQL 时序

所有适用写入分成两个明确阶段。阶段 A 只保留指纹，不是假成功：

1. 完成 B1 身份／用途校验、严格 JSON 校验并计算规范摘要；失败时不建槽。
2. 开启短事务，执行普通 `INSERT INTO operation_receipt (...,state,reserved_at) VALUES (...,'RESERVED',...)`。
3. 禁止 `INSERT IGNORE`、`REPLACE` 或会改写旧列的 `ON DUPLICATE KEY UPDATE`。插入成功即提交；联合唯一冲突只表示槽已存在，必须回滚该短事务且保持原行逐字不变，然后进入新的阶段 B 事务读取裁决。
4. 阶段 A 的插入或提交结果无法确认时，立即返回 `503 WRITE_RESULT_UNAVAILABLE`，不得继续业务写入，也不得推断没有槽。

因为阶段 A 已单独提交，即使阶段 B 的业务事务后来完全回滚，原 `request_digest` 仍以 `RESERVED` 保留。同 key 换内容会命中该指纹并被拒绝，闭合“失败回滚后无摘要可比”的缺口；这笔预留不等于成功回执，真正的 `SUCCEEDED` 仍必须与业务记录在同一事务提交。

阶段 B 的原写入固定顺序如下：

~~~sql
START TRANSACTION;

SELECT state, request_digest, result_resource_type, result_resource_id,
       committed_version, submitted_draft_id, submitted_draft_version
FROM operation_receipt
WHERE account_id = ? AND operation_type = ? AND request_key = ?
FOR UPDATE;
-- 先比较 request_digest，再按 state 分支。
~~~

- 无槽表示阶段 A 状态不可信，回滚并返回 `503 WRITE_RESULT_UNAVAILABLE`，不能直接执行业务 SQL。
- 摘要不同：回滚，返回 `409 IDEMPOTENCY_CONFLICT`。
- `SUCCEEDED`：不重做写入；在同一账号授权下读取当前资源投影，提交只读事务并返回原回执，`replayed=true`。
- `NOT_APPLIED`：不得执行任何业务 SQL；回滚或只读提交后返回 `409 OPERATION_NOT_APPLIED`。
- 只有 `RESERVED` 且摘要相同才继续，并始终持有该槽行锁直到提交或回滚。

后续锁序全局固定，跳过不存在的层级但不得颠倒：

1. 当前 `operation_receipt` 槽行。
2. 被更新的行程或食宿草稿目标行，使用 `id + account_id FOR UPDATE`。
3. 必需父目录行，再锁商品／餐食项／房型；同层多个 UUID 按规范字符串升序。核价创建用锁定读，后台目录更新也必须取得相同行锁。
4. 插入或条件更新业务主记录；餐食明细按请求顺序插入。
5. 草稿提交时条件更新草稿状态／版本／订单关联。
6. 最后执行成功终态更新：

~~~sql
UPDATE operation_receipt
SET state = 'SUCCEEDED', result_resource_type = ?, result_resource_id = ?,
    committed_version = ?, submitted_draft_id = ?, submitted_draft_version = ?,
    committed_at = ?, terminal_reason = NULL, terminal_at = NULL
WHERE account_id = ? AND operation_type = ? AND request_key = ?
  AND state = 'RESERVED' AND request_digest = ?;
~~~

只有影响行数为 1 才允许 `COMMIT`。业务记录、餐食明细、草稿提交关联和该成功终态处于同一阶段 B 事务；成功回执不得在事务外补写。任何 SQL 异常、约束冲突、死锁、连接中断或提交结果未知都不返回成功；事务回滚时阶段 A 的 `RESERVED` 指纹仍在，调用方进入结果核对。

若在任何业务 `INSERT/UPDATE` 之前，服务端已持有槽和全部相关资源行锁，并确定报价变化、版本冲突、草稿缺项或目标不可写，可以执行下列条件更新并提交一个可靠 `NOT_APPLIED`，同时返回对应业务错误：

~~~sql
UPDATE operation_receipt
SET state = 'NOT_APPLIED', terminal_reason = ?, terminal_at = ?
WHERE account_id = ? AND operation_type = ? AND request_key = ?
  AND state = 'RESERVED' AND request_digest = ?;
~~~

若已经尝试业务写入、事务是否回滚尚不确定，或无法取得所需锁，则禁止走该快捷终结；保持未知并返回 `503 WRITE_RESULT_UNAVAILABLE`。只有数据库明确提交上述终态后，结果查询才能报告 `NOT_APPLIED`。

### 17.5 成功回执与只读核对

`WriteReceipt<T>` 字段固定为：

~~~json
{
  "requestKey": "00000000-0000-0000-0000-000000000000",
  "operationType": "SAVE_FOOD_DRAFT",
  "resourceType": "FOOD_DRAFT",
  "resourceId": "00000000-0000-0000-0000-000000000000",
  "committedVersion": 8,
  "committedAt": "2026-09-10T01:02:03Z",
  "replayed": false,
  "resource": {},
  "submittedDraft": null
}
~~~

`resource` 是响应时当前调用者获准看到的投影，不是持久化的历史响应正文；重放时它可能已到更高版本或后来状态。`committedVersion` 始终是原操作提交的版本，不能用当前版本覆盖。订单主结果的 `committedVersion=null`；草稿提交按第 16.5 节另填 `submittedDraft`。

只有首次阶段 B 成功响应使用 `replayed=false`；同 key 成功重试、只读结果查询或 reconcile 返回既有成功时都使用 `replayed=true`。该布尔值不进入持久摘要，也不改变原提交时间和版本。

只读核对路由为 `GET /api/me/write-results/{operationType}/{requestKey}`。它需要 USER，且只查询 JWT sub 对应的唯一槽。成功 `data` 是下列判别联合：

- `outcome=SUCCEEDED`：`requestKey`、`operationType`、`receipt:WriteReceipt`。
- `outcome=NOT_APPLIED`：`requestKey`、`operationType`、`terminalReason`、`terminalAt`、`target:{resourceType,resourceId}`、`current:{version,state,editable,linkedOrder}|null`。
- `outcome=NOT_OBSERVED`：仅 `requestKey`、`operationType`、`outcome`。

槽为 `SUCCEEDED` 才返回 `SUCCEEDED`；槽为 `NOT_APPLIED` 才返回 `NOT_APPLIED`。槽为 `RESERVED` 或没有记录都返回 `NOT_OBSERVED`，不披露二者差异：原请求可能尚未到达、正在阶段 A／B、已回滚后等待重试，或其提交结果尚不可见。查询超时、死锁、连接失败或数据库不可确认返回 `503 WRITE_RESULT_UNAVAILABLE`，绝不能转换为 `NOT_OBSERVED` 或 `NOT_APPLIED`。只读 GET 不创建、终结或修改槽。

### 17.6 主动 reconcile 的竞争与迟到阻断

reconcile 仅用于用户在手动保存结果未知后点击“继续保存”，操作类型只允许 `SAVE_ITINERARY|SAVE_FOOD_DRAFT|SAVE_STAY_DRAFT`：

~~~text
POST /api/me/write-results/{operationType}/{requestKey}/reconcile
~~~

请求严格携带原目标、原基础版本和原完整内容：

~~~json
{
  "targetId": "00000000-0000-0000-0000-000000000000",
  "expectedVersion": 7,
  "content": {}
}
~~~

Java 必须按原 `PUT` 的路径目标与正文重新构造完全相同的规范摘要；客户端不能提供摘要。reconcile 先执行第 17.4 节阶段 A：槽不存在时以原摘要创建 `RESERVED`；槽存在时不改原行。随后开启自己的阶段 B，按与原写入完全相同的顺序先 `SELECT operation_receipt ... FOR UPDATE`，再锁本人目标资源行。

同一槽的竞争语义固定为：

1. 原写入已持有槽锁时，reconcile 等待其提交或回滚。原写入提交后看到 `SUCCEEDED`，reconcile 返回原成功回执；原写入回滚后看到的仍是阶段 A 的 `RESERVED`。
2. reconcile 先取得 `RESERVED` 槽锁时，在本人目标存在且摘要相同的前提下，把槽条件更新为 `NOT_APPLIED`、`terminalReason=RECONCILED_BEFORE_APPLY`，影响行数为 1 后提交。资源当前版本是否仍为原基础版只决定能否继续下一次保存，不改变该旧 key 已被可靠阻断的事实。
3. 原写入在 reconcile 提交后才进入阶段 B 时，首先读取到 `NOT_APPLIED`，必须在任何业务 `UPDATE` 前返回 `409 OPERATION_NOT_APPLIED`。终结后的旧请求不能更新资源，也不能把终态覆盖为成功。
4. 摘要不匹配返回 `409 IDEMPOTENCY_CONFLICT`；目标不存在或非本人返回 404，均不得创建可用于探测他人资源的终态。

锁等待上限固定为 3 秒。等待超时、死锁、数据库错误或 `NOT_APPLIED` 提交响应未知时返回 `503 WRITE_RESULT_UNAVAILABLE`，页面继续暂停；不能因为“看不到成功记录”就声称未执行。reconcile 不是撤销：它不回滚已经提交的保存，也不适用于采用候选、创建／提交订单、取消订单、社区投稿或知识发布。

reconcile 返回 `SUCCEEDED` 时，只有当前资源版本仍等于回执的 `committedVersion` 才能直接把它作为下一保存的基础；若更高则显示跨端冲突。返回已提交的 `NOT_APPLIED` 时，只有目标仍为 `expectedVersion` 且可编辑，页面才可用新的 key 保存当前未保存内容；否则保持内容并提示读取最新版。任何下一次保存都是新主动操作，不能复用已终结 key。

### 17.7 保留与隐私

首版本机不为 `RESERVED`、`SUCCEEDED` 或 `NOT_APPLIED` 槽配置自动清理任务，也不套用匿名 24 小时或登录对话 30 天 TTL。该决定仅适用于操作槽／回执，不扩展为所有日志、Cookie、登录、聊天或业务数据永久保留承诺。

槽只保存规范摘要、受控枚举、业务 ID、版本和时间，不保存原请求 JSON、联系人、备注、候选正文、JWT、刷新令牌、匿名凭据、Cookie、CSRF、服务凭据、请求头或异常原文。摘要不返回公共 API，也不得作为替代凭据。日志只允许记录 `X-Request-Id`、操作类型、稳定错误码、耗时和是否重放，不记录 key 与业务内容的组合明文。

## 18. 旧记录投影与前向迁移边界

### 18.1 归属与只读原则

旧数据保留不等于新账号已获授权。旧 `visitorId`／`visitor_id`、手机号、联系人、昵称、浏览器本地 UUID、设备或相同 `threadId` 都不得自动映射为 v3 `account_id`。只有记录本身已有可信 `account_id`，或后续经单独批准、可审计的归属流程形成明确关联，才是“合法归属”；本修订不创建自助认领、手机号查单、批量匹配或管理员代认领接口。

合法归属的旧成果必须保持可读，但始终只读，不伪装成字段完整的新订单／草稿，也不能通过编辑旧记录绕过新报价、版本、状态或身份规则。没有合法归属的私人记录原样保留在数据库，不出现在任何 `/api/me/**`、Python 或公共响应中；不存在与无权访问继续统一为 404。

旧记录投影使用固定公共字段：

~~~json
{
  "legacyRecordType": "FOOD_ORDER_V2",
  "legacyId": "00000000-0000-0000-0000-000000000000",
  "sourceSchema": "tourism-api-v2",
  "ownershipStatus": "ATTRIBUTED",
  "readOnly": true,
  "legacyData": true,
  "missingFields": ["merchantIdAtOrder", "merchantNameAtOrder", "items[0].itemTypeAtOrder", "items[0].catalogVersionAtOrder", "items[0].unit", "items[0].unitAmount", "items[0].quantity", "items[0].lineAmount", "items[0].demoData", "totalAmount", "currency"],
  "data": {
    "merchantIdAtOrder": null,
    "merchantNameAtOrder": null,
    "items": [
      {
        "sequence": 1,
        "foodItemId": "00000000-0000-0000-0000-000000000000",
        "foodItemName": "旧餐食项当前名称",
        "nameProvenance": "CURRENT_CATALOG",
        "itemTypeAtOrder": null,
        "catalogVersionAtOrder": null,
        "unit": null,
        "unitAmount": null,
        "quantity": null,
        "lineAmount": null,
        "demoData": null
      }
    ],
    "visitAt": "2026-09-12T18:30:00",
    "peopleCount": 2,
    "totalAmount": null,
    "currency": null,
    "note": "旧记录原备注",
    "status": "PENDING_VISIT",
    "demoData": true,
    "createdAt": "2026-09-10T01:02:03Z",
    "updatedAt": "2026-09-10T01:02:03Z"
  }
}
~~~

`legacyRecordType` 只允许 `LEGACY_BOOKING_V1|PRODUCT_ORDER_V2|FOOD_ORDER_V2|STAY_BOOKING_V2`；`sourceSchema` 必须与类型对应为 `tourism-api-v1` 或 `tourism-api-v2`，`ownershipStatus` 固定为 `ATTRIBUTED`。

`missingFields` 按本节各类型的固定顺序返回；缺失值在 `data` 对应位置显式为 `null`，不能填零、当前价格、估计日期、默认人数、默认房数或其他目录 ID。当前目录联表所得名称只能标成 `nameProvenance=CURRENT_CATALOG`，不得称为下单时快照。本人旧投影仍不返回联系人、`visitorId`、`accountId`、`sourceThreadId` 或回执内部字段。

合法归属旧记录通过 `GET /api/me/legacy-records` 读取；可选 `type` 只允许本节 `legacyRecordType` 枚举。该集合供“我的”与当前三类集合并列展示，不把旧记录塞入新建接口或新 DTO。无记录返回空数组。本修订不新增旧记录详情修改、提交、取消、导出或历史管理器。

### 18.2 已核实旧模型与字段去向

下表只依据现有 v1 契约、v2 契约以及当前 `OrderRequests`、`OrderViews`、`OrderService`、`OrderRepository` 可观察字段；“缺失”不得由实现者猜测补齐。

| 既有模型／字段 | 新链去向 | 冻结规则 |
| --- | --- | --- |
| v1 `service_resource`: `id,merchant_id,name,category,description,price,durationText,locationText,tags,merchantName,imageUrl,demoData` | 保留旧表及既有 `merchant_id` 外键关系；逐条候选映射到 merchant/product/food/stay/room/place/knowledge | 现存非空 `merchant_id` 不是缺失字段，迁移时必须原样保留并核对所指商家；`category` 仍不能单独证明目标类型。没有房型、容量、计价单位、来源核验或地点 ID 时不自动创建公开资源，旧 `price` 不自动成为已核验报价。 |
| v1 `booking`: `id,serviceId,serviceName,travelDate,peopleCount,contactName,contactPhone,note,status,source,threadId,createdAt` | `LEGACY_BOOKING_V1` 只读投影 | 缺订单类型、账号归属、数量／取货、餐食多明细、住宿离店／房数、金额快照和新状态语义；`PENDING_CONFIRMATION/CONFIRMED/PROCESSING/...` 不写回三类新状态机。没有可信账号归属时不向本人显示。 |
| v2 `merchant` 及 `Product/FoodItem/StayProperty/RoomType/Place` 对象字段 | 保留 ID 和父子关系，前向增加版本、价格来源／演示价、示意位置等列 | 原 `price` 没有独立计价单位与来源字段，先原样保留；只有逐条确认单位后，`demoData=true` 的非负值才可进入 `demoPrice` 并标明“旧演示价格”。未确认时 `demoPrice/referencePrice` 都为 `null`、不可下单；`demoData=false` 也不能在无可核验来源时自动成为 referencePrice。 |
| v2 `room_type.maxGuests` | 新 `maxGuestsPerRoom` 候选 | 只在确认旧字段确为每间房容量后原值映射；否则新字段缺失、房型不可报价，不按无限或总容量猜测。 |
| v2 `place.latitude/longitude` | 受控旧地理字段原样保留 | 不复制到 `schematicPosition`，不从手绘图推算，不由本修订公共地图返回；只有另行核验真实来源后才能进入未来真实地图。 |
| v2 `product_order`: `visitor_id,product_id,quantity,pickup_point,contact_name,contact_phone,note,status,source_thread_id,demo_data,created_at,updated_at`；名称来自当前 product 联表 | `PRODUCT_ORDER_V2` 只读投影 | 原数量、取货点、状态和时间可保留；`catalogVersionAtOrder`、单位价与合计为 `null`。当前联表名若存在标记 `CURRENT_CATALOG`，不伪称快照。`visitor_id` 不自动认领。 |
| v2 `food_order`: 单个 `food_item_id`、`visit_at,people_count`、联系人、备注、状态、线程、演示及时间；名称来自当前 food_item 联表 | `FOOD_ORDER_V2` 只读投影 | 可显示一个旧明细引用，但 `merchantIdAtOrder`、名称快照、类型快照、单位价、行金额和合计为 `null`；不能把一行旧单改写成已核价的新多菜订单。`visitor_id` 不自动认领。 |
| v2 `stay_booking`: `room_type_id,check_in_date,people_count`、联系人、备注、状态、线程、演示及时间；名称来自当前 room_type 联表 | `STAY_BOOKING_V2` 只读投影 | `checkOutDate`、`nights`、`roomCount`、下单时容量、单位价和合计均为 `null`；不得默认一间／一晚。`visitor_id` 不自动认领。 |
| v1 社区对象：`id,title,content,authorName,coverUrl,tags,publishedAt,demoData` | 保留原记录并通过第 14.6 节兼容读取 | 不套用新长度上限。只有当前持久行已有明确 `postType` 时使用其 `MOMENT|ROUTE_GUIDE`；源行仍无类型时先保留待核对，不凭缺少路线字段静默回填。旧封面按公开授权决定是否投影，不改变原存储。 |
| v2 `CommunityPost`: `title,content,authorName,tags,postType,routeSummary,routeNodes,demoData,publishedAt,visitorId,published` | 继续公共只读投影或新记录表的兼容行 | 按第 14.6 节保留原全文和原节点顺序；超出新投稿上限也不截断。空／失效 placeId 不补造，节点不可绘制。旧 `visitorId` 不赋予账号编辑权。 |
| v1/v2 `knowledge_document`: `id,title,content,tags,sourceType,published,indexStatus,updatedAt,demoData` 及旧直接发布语义 | 原记录与既有 `published` 值均保留，逐条进入第 22～24 节的工作草稿／来源／生效快照评审 | 旧 `published`、`indexStatus`、曾公开或有切块数都不能自动证明新知识已审核、生效或向量 READY；不得批量发布、批量向量化或把原 URL 暴露给游客。 |

四类旧私人投影的 `missingFields` 是下列固定有序数组；路径使用字段名和 `items[0].field` 形式。每个数组元素必须在该类型 `data` 中有同路径、固定为 `null` 的字段，且 `data` 不得再出现未列入数组的固定缺失 `null` 字段。来自旧源且本身允许为空的 `note`，以及当前联表可能缺失的名称，不属于固定缺字段，分别由原值和 `nameProvenance` 表达。

| `legacyRecordType` | 固定顺序 `missingFields` |
| --- | --- |
| `LEGACY_BOOKING_V1` | `orderType`、`status`、`productId`、`merchantIdAtOrder`、`foodItemId`、`stayPropertyIdAtOrder`、`roomTypeId`、`catalogVersionAtOrder`、`quantity`、`pickupPoint`、`visitAt`、`checkInDate`、`checkOutDate`、`nights`、`roomCount`、`maxGuestsPerRoomAtOrder`、`unit`、`unitAmount`、`totalAmount`、`currency`、`demoData`、`updatedAt` |
| `PRODUCT_ORDER_V2` | `catalogVersionAtOrder`、`unit`、`unitAmount`、`totalAmount`、`currency` |
| `FOOD_ORDER_V2` | `merchantIdAtOrder`、`merchantNameAtOrder`、`items[0].itemTypeAtOrder`、`items[0].catalogVersionAtOrder`、`items[0].unit`、`items[0].unitAmount`、`items[0].quantity`、`items[0].lineAmount`、`items[0].demoData`、`totalAmount`、`currency` |
| `STAY_BOOKING_V2` | `stayPropertyIdAtOrder`、`stayPropertyNameAtOrder`、`catalogVersionAtOrder`、`checkOutDate`、`nights`、`roomCount`、`maxGuestsPerRoomAtOrder`、`unit`、`unitAmount`、`totalAmount`、`currency` |

旧商品只读 `data` 固定为：`productId`、`productName:string|null`、`nameProvenance:CURRENT_CATALOG|MISSING`、`catalogVersionAtOrder:null`、`quantity`、`pickupPoint`、`unit:null`、`unitAmount:null`、`totalAmount:null`、`currency:null`、`note`、`status`、`demoData`、`createdAt`、`updatedAt`。

旧餐食只读 `data` 固定为：`merchantIdAtOrder:null`、`merchantNameAtOrder:null`、`items:[{sequence:1,foodItemId,foodItemName:string|null,nameProvenance:CURRENT_CATALOG|MISSING,itemTypeAtOrder:null,catalogVersionAtOrder:null,unit:null,unitAmount:null,quantity:null,lineAmount:null,demoData:null}]`、`visitAt`、`peopleCount`、`totalAmount:null`、`currency:null`、`note`、`status`、`demoData`、`createdAt`、`updatedAt`。旧模型没有份数，`quantity` 必须为 `null`，不能默认为 1。

旧住宿只读 `data` 固定为：`roomTypeId`、`roomTypeName:string|null`、`nameProvenance:CURRENT_CATALOG|MISSING`、`stayPropertyIdAtOrder:null`、`stayPropertyNameAtOrder:null`、`catalogVersionAtOrder:null`、`checkInDate`、`checkOutDate:null`、`nights:null`、`roomCount:null`、`peopleCount`、`maxGuestsPerRoomAtOrder:null`、`unit:null`、`unitAmount:null`、`totalAmount:null`、`currency:null`、`note`、`status`、`demoData`、`createdAt`、`updatedAt`。

v1 预约只读 `data` 固定为：`serviceId`、`serviceName`、`travelDate`、`peopleCount`、`note`、`legacyStatus`、`legacySource`、`createdAt`、`orderType:null`、`status:null`、`productId:null`、`merchantIdAtOrder:null`、`foodItemId:null`、`stayPropertyIdAtOrder:null`、`roomTypeId:null`、`catalogVersionAtOrder:null`、`quantity:null`、`pickupPoint:null`、`visitAt:null`、`checkInDate:null`、`checkOutDate:null`、`nights:null`、`roomCount:null`、`maxGuestsPerRoomAtOrder:null`、`unit:null`、`unitAmount:null`、`totalAmount:null`、`currency:null`、`demoData:null`、`updatedAt:null`。上述投影不返回旧联系人，但原值继续在受控数据库中按既有后台履约边界保留。

### 18.3 只允许的未来迁移顺序

本文不分配 Flyway 版本号，不创建或修改迁移文件。后续唯一 Java 写入人必须先核对目标分支实际已有 V1～V4 和待集成制品，再以新的、未占用的前向版本按以下顺序实施；不得修改已执行脚本：

1. 增加账号及新记录 `account_id` 归属结构；旧 `visitor_id` 原列保留，默认不建立账号关联。
2. 为目录增加 `CatalogVersion`、演示价／参考价分列、核验状态、餐食类型、每间容量和地点示意坐标；只做能证明语义的确定性转换。
3. 建立新餐食主单／明细与订单快照字段；为新住宿增加离店、房数、夜数、容量及金额快照；旧单保留可空兼容列或独立兼容读取，不强制补值。
4. 建立已保存行程、食宿草稿、`version/state/linked_order_id` 和订单非空 `source_draft_id` 唯一约束；不建 MySQL 临时 AI 候选表。
5. 建立第 17 节操作槽、联合唯一、状态约束与结果关联；上线任何受保护写入前先确保所有写路径都遵守同一锁序。
6. 前向适配社区节点与示意地图，只对新投稿执行新上限；旧内容继续兼容只读。
7. 按第 22～24 节单独建立知识工作草稿、来源、发布任务与生效快照；逐条审查旧知识，绝不按旧 `published/indexStatus` 批量认定。
8. Java、Python、Web 与小程序消费者均匹配 `tourism-api-v3-draft-r3` 及独立 `assistant-card-v3` 协议并完成独立验收后，才按统一切换清单停用旧入口壳；保留旧表、旧记录、旧源码和旧契约。

任何实际建表、ALTER、回填、归属关联、导入、发布、索引、删除、清理或数据读取验证都不在本文授权内。迁移失败不得通过重建库、删除旧数据或虚构默认值处理。

### 18.4 B2 错误目录追加

第 11 节继续有效，B2 新增固定错误：

| HTTP | code | 固定语义 |
| --- | --- | --- |
| 400 | `DUPLICATE_FOOD_ITEM` | 同一餐食请求重复 `foodItemId`，不得静默合并 |
| 409 | `CATALOG_NOT_PUBLISHED` | 目标或必需父资源未发布 |
| 409 | `PRICE_UNAVAILABLE` | 没有可用于演示订单的 `demoPrice` |
| 409 | `PRICE_UNIT_UNSUPPORTED` | 演示价单位与资源类型不符 |
| 409 | `INVALID_DATE_RANGE` | 离店不晚于入住或日期组合无效 |
| 409 | `CAPACITY_EXCEEDED` | 人数超过每间容量乘房间数；不表示实时房态 |
| 409 | `QUOTE_CHANGED` | 当前选择／目录版本／演示价与确认指纹不同；返回当前报价 |
| 409 | `VERSION_CONFLICT` | 本人资源当前版本不等于 `expectedVersion` |
| 409 | `DRAFT_INCOMPLETE` | 草稿可保存但尚不满足正式提交必填条件 |
| 409 | `DRAFT_ALREADY_SUBMITTED` | 草稿已是只读终态，不能保存、采用或再次提交 |
| 409 | `INVALID_STATUS_TRANSITION` | 订单类型、预期状态或目标迁移不合法 |
| 409 | `IDEMPOTENCY_CONFLICT` | 同账号、同操作、同 key 已绑定不同规范摘要 |
| 409 | `OPERATION_NOT_APPLIED` | 该 key 已有不可变未执行终态，旧写入被阻断 |
| 410 | `CANDIDATE_EXPIRED` | 在已验证访问范围内，尚未成功的新采用候选已到期 |
| 410 | `CANDIDATE_VERSION_UNAVAILABLE` | 精确候选版本不存在；不得改取最新版 |
| 503 | `CANDIDATE_LOOKUP_UNAVAILABLE` | 无法确认候选结果，不得误报过期或保存结果 |
| 503 | `WRITE_RESULT_UNAVAILABLE` | 槽、锁、事务或提交结果无法确认 |

`VERSION_CONFLICT.details` 只在调用者已经通过该资源授权时为 `{"kind":"resource_conflict","currentVersion":8,"editable":true}`。`DRAFT_ALREADY_SUBMITTED.details` 只在本人范围内为 `{"kind":"draft_already_submitted","linkedOrder":{"orderType":"FOOD","orderId":"UUID"}}`。`IDEMPOTENCY_CONFLICT.details=null`，不得返回旧摘要或旧内容。`OPERATION_NOT_APPLIED.details` 只含 `kind=operation_not_applied`、`terminalReason`、`terminalAt`。身份或归属未通过时优先返回 B1 的 401／403／404，不泄漏上述业务状态。

## 19. B2 完成边界

| TASK-024 验收项 | 本文落点 |
| --- | --- |
| 公开目录、按店菜单、多菜、住宿四字段 | 第 14.1～14.4、15.1～15.5 节 |
| MOMENT、sequence、旧内容、真实地点与水彩示意 | 第 14.5～14.6、18.2 节 |
| 三类报价、规范指纹、改价与新确认 | 第 15.1～15.4 节 |
| 三类订单 DTO、快照、状态与条件更新 | 第 15.4～15.6 节 |
| 食宿草稿可缺项、本人联系人、内部脱敏、AI 保留联系人 | 第 16.2～16.4 节 |
| 草稿版本、一草稿一单和提交同事务 | 第 16.5、17.4 节 |
| 账号／操作／key、失败指纹、成功回执与不可变终态 | 第 17.1～17.5 节 |
| 原写入／reconcile 锁序、未知边界和迟到阻断 | 第 17.4～17.6 节 |
| 回执保留和敏感原文禁止 | 第 17.7 节 |
| 实际旧模型、缺字段投影、不认领 visitorId、知识逐项核对 | 第 18.1～18.3 节 |

B2 仅冻结静态接口、DTO、状态、指纹和事务边界。它没有实现或验证 Java、MySQL、Redis、Vue、小程序、Python、AI、地图或迁移，也没有创建测试、运行构建／服务／数据库／浏览器／网络、修改 v1／v2、提交、推送或集成。第 20～30 节在不改变 B2 语义的前提下追加 B3 Java 共享协议；面向客户端的卡片、事件和 checkpoint 仍须由独立 `assistant-card-v3` 契约冻结并与本修订逐字段匹配，四端新链才能进入统一切换验收。

## 20. B3 Java 共享边界与共同值

### 20.1 版本分工

`tourism-api-v3-draft-r3` 是 Java 公共 REST、管理 REST、Java／Python 内部 REST 和 WebSocket 鉴权帧中的唯一 `contractVersion`。B1、B2 的请求、响应、鉴权、严格 JSON、错误脱敏和事务语义除版本字符串由 r2 前移到 r3 外保持不变；消费者不得只替换请求头而继续使用旧字段。

面向客户端的 AI 卡片、过程事件、恢复状态和 checkpoint 使用独立 `assistant-card-v3` 契约，并必须在其中声明它所匹配的 `tourism-api-v3-draft-r3`。本文件不预先定义该文件的版本号或六类卡片字段；第 27 节的候选解析是 Java 保存业务成果所需的服务间 DTO，不是可直接透传给客户端的卡片。

所有 B3 REST 对象继续遵守第 2.3 节：未列字段、重复键、错误类型、未知枚举和非法 `null` 一律 `VALIDATION_FAILED`。下列值在第 20～30 节统一使用：

| 名称 | 固定表示 |
| --- | --- |
| `Version`／`Revision`／`sequence` | 从 1 开始的 JSON 正整数，不接受字符串、零、小数或负数 |
| `Sha256Digest` | `sha256:` 加 64 位小写十六进制；摘要原文按第 15.3 节规范 JSON 序列化后取 UTF-8 SHA-256 |
| `KnowledgeVisibility` | `DRAFT|PUBLISHED|WITHDRAWN` |
| `KnowledgePublishStatus` | `RESERVED|PENDING|RUNNING|SUCCEEDED|FAILED|INVALIDATED` |
| `RetrievalMode` | `KEYWORD_DEMO|VECTOR` |
| `SourceReadStatus` | `UNREVIEWED|SEARCH_SNIPPET_ONLY|FULL_TEXT_REVIEWED|ARCHIVED_COPY_REVIEWED` |
| `CandidateType` | `ITINERARY|FOOD_DRAFT|STAY_DRAFT` |
| `CandidateAction` | `CREATE|UPDATE` |

所有期限判断都使用接收服务在取得相应数据库或 Redis 锁后的 UTC 当前时间。客户端、Java 调用方或 Python 调用方携带的时间都不能替代服务器期限核验。任何摘要只能用于一致性比较，不能作为凭据、授权、就绪证明或公开资格。

### 20.2 内容与隐私边界

- Java 是账号、目录、订单、草稿、知识公开状态与当前生效快照的唯一业务写入口。Python 不写这些 MySQL 事实，也不能返回 `PUBLISHED` 决定。
- 原始来源 URL 只存在于 ADMIN 来源主档投影。公开接口、Agent 检索、候选、运行摘要、checkpoint、模型输入与 LangSmith 不得含原 URL、本机路径、原始 Word、图片、凭据或联系人。
- 知识正文只从当前 `PUBLISHED` 的固定 `liveSnapshot` 产生。工作草稿、来源主档最新值、准备中的向量块和旧 build 都没有公开资格。
- `demoData=true` 始终随快照冻结并对外保留，不因保存、构建或发布自动变为真实核验资料。
- 第 25～28 节的内部接口只在第 8 节方向凭据、回环、方法／路由白名单和所需附加证明全部通过后处理正文；服务凭据不能替代任务、候选、thread、账号或匿名归属。

## 21. 知识内容、来源与投影 DTO

### 21.1 工作草稿内容

`KnowledgeDraftContent` 的全部字段必须出现：

~~~json
{
  "title": "乌东村寨知识条目",
  "content": "经管理员核读后整理的正文。",
  "tags": ["村寨生活"],
  "region": "乌东",
  "periodText": null,
  "evidenceCategory": "地方资料",
  "usageLimitations": ["仅用于本平台知识讲解"],
  "demoData": false,
  "sourceBindings": [
    {
      "sourceId": "00000000-0000-0000-0000-000000000000",
      "expectedSourceVersion": 3
    }
  ]
}
~~~

草稿允许尚未完整：`title`、`content`、`region`、`periodText`、`evidenceCategory` 可为 `null`；非空时去除首尾空白，`title` 为 1～120 个 Unicode 码点，`content` 为 1～30,000 个码点，其余三个文本各为 1～200 个码点。`tags` 与 `usageLimitations` 必须是数组，分别最多 20／10 项，每项去空白后为 1～80／300 个码点，重复项拒绝。`sourceBindings` 为 0～20 项，`sourceId` 不得重复。`demoData` 必须明确为布尔值，不能省略或为 `null`。

保存时 Java 按每个 `sourceId + expectedSourceVersion` 读取来源主档并生成第 21.2 节的固定来源快照。来源不存在、版本不符或已不可用于新草稿时，整次保存失败；不得悄悄换成来源最新版。草稿响应中的 `sourceBindings` 使用解析后的 `sourceSnapshots` 替代请求引用，不回显原 URL。

发起发布时，固定输入必须满足：`title`、`content`、`evidenceCategory` 非空，至少有一个来源快照，且每个来源快照的 `readStatus` 为 `FULL_TEXT_REVIEWED` 或 `ARCHIVED_COPY_REVIEWED`。`SEARCH_SNIPPET_ONLY` 不能作为核读全文证据。`region`、`periodText` 可以为 `null`，空标签与空使用限定仍以 `[]` 明确表示。未满足时发布任务可靠终结为 `FAILED / KNOWLEDGE_DRAFT_INCOMPLETE`，草稿仍可编辑。

### 21.2 来源主档与固定来源快照

ADMIN `KnowledgeSourceView` 字段固定为：

~~~json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "version": 3,
  "sourceKey": "S01",
  "title": "受控来源标题",
  "url": null,
  "publisher": null,
  "sourceKind": "地方资料",
  "publicationDateText": null,
  "readAt": "2026-09-10T01:02:03Z",
  "locator": "第 2 节",
  "readStatus": "FULL_TEXT_REVIEWED",
  "createdAt": "2026-09-10T01:02:03Z",
  "updatedAt": "2026-09-10T01:02:03Z"
}
~~~

`sourceKey`、`title`、`sourceKind`、`readStatus` 必填且不得为 `null`；`url`、`publisher`、`publicationDateText`、`readAt`、`locator` 可为 `null`。`sourceKey` 为 1～40 个 ASCII 字母、数字、`-`、`_`，服务端按原大小写保存并唯一比较。`title`、`sourceKind` 去空白后分别为 1～200、1～80 个 Unicode 码点；`publisher`、`publicationDateText`、`locator` 非空时分别为 1～200、1～80、1～300 个码点。`url` 非空时只能是长度不超过 2,048 的绝对 `http` 或 `https` URL；它仍不表示版权授权。`readAt` 只在 `FULL_TEXT_REVIEWED|ARCHIVED_COPY_REVIEWED` 时必填，在另两种状态时必须为 `null`。`version` 从 1 开始，任一业务字段成功修改后递增。

保存进草稿和发布快照的 `KnowledgeSourceSnapshot` 只含：

~~~json
{
  "sourceId": "00000000-0000-0000-0000-000000000000",
  "sourceVersion": 3,
  "sourceKey": "S01",
  "sourceTitle": "受控来源标题",
  "publisher": null,
  "sourceKind": "地方资料",
  "publicationDateText": null,
  "readAt": "2026-09-10T01:02:03Z",
  "locator": "第 2 节",
  "readStatus": "FULL_TEXT_REVIEWED"
}
~~~

该快照不含 `url`。来源主档之后修改不得改写已保存草稿引用、进行中任务或旧生效版；管理详情必须分别显示草稿快照、当前来源版本和生效快照，不能把当前主档值冒充已发布引用。

### 21.3 固定发布快照与公开投影

`KnowledgeSnapshot` 是持久任务输入和 `live_snapshot` 共用的严格对象：

~~~json
{
  "documentId": "00000000-0000-0000-0000-000000000000",
  "draftRevision": 7,
  "title": "乌东村寨知识条目",
  "content": "经管理员核读后整理的正文。",
  "tags": ["村寨生活"],
  "region": "乌东",
  "periodText": null,
  "evidenceCategory": "地方资料",
  "usageLimitations": ["仅用于本平台知识讲解"],
  "demoData": false,
  "sources": [
    {
      "sourceId": "00000000-0000-0000-0000-000000000000",
      "sourceVersion": 3,
      "sourceKey": "S01",
      "sourceTitle": "受控来源标题",
      "publisher": null,
      "sourceKind": "地方资料",
      "publicationDateText": null,
      "readAt": "2026-09-10T01:02:03Z",
      "locator": "第 2 节",
      "readStatus": "FULL_TEXT_REVIEWED"
    }
  ]
}
~~~

其中 `sources` 为保存草稿时生成的 `KnowledgeSourceSnapshot[]`，顺序与 `sourceBindings` 一致。`snapshotHash` 不放进自身；Java 对上述完整对象规范序列化后计算 `Sha256Digest`。同一任务从创建到终结只能引用这一份对象与摘要，不得在构建前重新读取草稿或重新拼接来源。

公开 `KnowledgePublicView` 固定为：

~~~json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "title": "乌东村寨知识条目",
  "content": "经管理员核读后整理的正文。",
  "tags": ["村寨生活"],
  "region": "乌东",
  "periodText": null,
  "evidenceCategory": "地方资料",
  "usageLimitations": ["仅用于本平台知识讲解"],
  "demoData": false,
  "references": [
    {
      "sourceTitle": "受控来源标题",
      "detailPath": "/api/knowledge-documents/00000000-0000-0000-0000-000000000000"
    }
  ],
  "publishedAt": "2026-09-10T01:02:03Z"
}
~~~

`references` 与快照来源同序，只公开 `sourceTitle` 和本站知识详情相对路径；不返回 source ID、来源 URL、publisher、locator、readStatus、任务摘要或 build 信息。所有公开列表、详情和 Agent 证据对象都从同一 `live_snapshot` 投影，不能联表取来源主档最新值覆盖它。

### 21.4 ADMIN 知识投影

`KnowledgeAdminView` 的字段固定为：`id`、`candidateCode:string|null`、`version`、`draftRevision`、`visibility`、`draft:KnowledgeDraftResolved`、`liveSnapshot:KnowledgeSnapshot|null`、`activeTaskId:UUID|null`、`currentTaskId:UUID|null`、`editable`、`createdAt`、`updatedAt`、`publishedAt:string|null`。`version` 即持久行的 `row_version`；`candidateCode` 非空时去空白后为 1～40 个 ASCII 字母、数字、`-`、`_`。`KnowledgeDraftResolved` 等于第 21.1 节对象，但用 `sourceSnapshots:KnowledgeSourceSnapshot[]` 取代 `sourceBindings`。

`editable` 只在 `currentTaskId=null` 时为 `true`。`PUBLISHED` 且存在当前任务表示旧 `liveSnapshot` 继续公开、新草稿正在准备；它不是两份同时生效的内容。`WITHDRAWN` 可保留 `liveSnapshot` 和 `activeTaskId` 供 ADMIN 审计，但公共与 Agent 路径必须当作不存在。

## 22. 三个持久知识对象

### 22.1 `knowledge_document`

逻辑列至少固定为：

| 列 | 规则 |
| --- | --- |
| `id`、`candidate_code` | `id` 为规范 UUID；`candidate_code` 可空，仅编辑定位，不作主键 |
| `title`、`content`、`tags`、`demo_data` | 现有业务列转为工作草稿；按第 21.1 节保存，不再直接作为公开内容 |
| `draft_meta` | 严格对象，只含 `region`、`periodText`、`evidenceCategory`、`usageLimitations`、`sourceSnapshots`；不得保存任意扩展键 |
| `draft_revision` | 正整数；草稿业务内容或来源绑定每次成功变化后递增 |
| `row_version` | 正整数；保存、接受发布、发布终结和下架每次成功状态变化后递增 |
| `visibility` | `DRAFT|PUBLISHED|WITHDRAWN`；唯一公开状态来源 |
| `live_snapshot`、`live_snapshot_hash` | 首次成功发布前均为 `null`；之后同时非空，普通草稿编辑不得改变 |
| `active_task_id` | 生成当前生效快照的 `SUCCEEDED` 任务；首次发布前为 `null` |
| `current_task_id` | 当前唯一 `PENDING|RUNNING` 任务；终结后清空 |
| 时间列 | `created_at`、`updated_at`、`published_at`；`published_at` 只在成功切换时更新 |

公开资格必须同时满足 `visibility=PUBLISHED`、`live_snapshot` 非空、`active_task_id` 指向 `SUCCEEDED` 任务且该任务的 `snapshot_hash` 与 `live_snapshot_hash` 相同。不得仅检查旧 `published` 或 `indexStatus`。

### 22.2 `knowledge_source`

逻辑列固定承载第 21.2 节 ADMIN 来源字段，以及服务端时间。`source_key` 唯一，`source_version` 为乐观并发条件。首版没有物理删除路由；尚不具发布资格的来源通过 `read_status=UNREVIEWED|SEARCH_SNIPPET_ONLY` 表达，仍可随未完成草稿保存，但发布完整性校验必须拒绝，且任何状态变化都不能改写已冻结快照。

### 22.3 `knowledge_publish_task`

逻辑列至少固定为：

| 列 | 规则 |
| --- | --- |
| `id`、`document_id`、`requested_by` | 全新任务 UUID、知识 UUID、从 ADMIN JWT `sub` 取得的请求者 |
| `request_key`、`request_hash` | 规范 UUID 与不可变规范摘要；`requested_by + request_key` 联合唯一 |
| `status` | `RESERVED|PENDING|RUNNING|SUCCEEDED|FAILED|INVALIDATED` |
| `draft_revision`、`input_snapshot`、`snapshot_hash` | `RESERVED` 或冻结前失败可为 `null`；从 `PENDING` 起同时非空且不可修改 |
| `retrieval_mode`、`index_config`、`config_hash` | 冻结前可为 `null`；从 `PENDING` 起同时非空且不可修改 |
| `created_at`、`started_at`、`deadline_at`、`finished_at` | `created_at/deadline_at` 创建即非空；其余按状态出现 |
| `build_receipt` | 仅 `SUCCEEDED` 非空，严格使用第 24.5、25.4 节对象 |
| `error_code`、`error_message` | 仅 `FAILED` 非空；固定非敏感码与可展示中文，不存底层异常 |

`INVALIDATED` 使用 `finished_at`，`error_code/error_message=null`；原因由状态本身表达。`RESERVED` 是已经持久化的请求键和失败指纹，不表示发布请求已受理。每次人工重发创建新任务和新 key；任何状态都不能改写 `request_hash`，所有终态不可恢复或互换。本机首版不自动清理任何发布任务、固定输入或结果，但该决定不扩展为所有日志或 Redis 孤立构建永久保留。

## 23. 来源、草稿与公开查询 REST

### 23.1 ADMIN 来源接口

| 方法与路由 | 请求 | 成功 `data` |
| --- | --- | --- |
| `GET /api/admin/knowledge-sources` | 无 | `KnowledgeSourceView[]`，按 `sourceKey,id` 升序 |
| `GET /api/admin/knowledge-sources/{id}` | 无 | 单个 `KnowledgeSourceView` |
| `POST /api/admin/knowledge-sources` | 除 `id/version/createdAt/updatedAt` 外的完整来源字段 | HTTP 201、版本 1 的 `KnowledgeSourceView` |
| `PUT /api/admin/knowledge-sources/{id}` | `expectedVersion` 加完整来源字段 | HTTP 200、版本递增后的 `KnowledgeSourceView` |

`PUT` 是完整替换，不是 PATCH；使用 `id + expectedVersion` 条件更新。冲突返回 `409 VERSION_CONFLICT`。来源保存不自动修改任何草稿、任务、生效快照或已构建索引，也不触发自动发布。

### 23.2 ADMIN 知识草稿接口

| 方法与路由 | 请求 | 成功 `data` |
| --- | --- | --- |
| `GET /api/admin/knowledge-documents` | 无 | `KnowledgeAdminView[]`，按 `updatedAt,id` 降序 |
| `GET /api/admin/knowledge-documents/{id}` | 无 | 单个 `KnowledgeAdminView` |
| `POST /api/admin/knowledge-documents` | `candidateCode:string|null`、`draft:KnowledgeDraftContent` | HTTP 201、`version=1,draftRevision=1,visibility=DRAFT` 的投影 |
| `PUT /api/admin/knowledge-documents/{id}/draft` | `expectedVersion`、`candidateCode:string|null`、`draft:KnowledgeDraftContent` | HTTP 200、两个版本按规则递增后的投影 |

创建与保存不使用 `Idempotency-Key`，也不公开内容。`PUT` 先锁知识行；`currentTaskId` 非空返回 `409 PUBLISH_IN_PROGRESS`。随后按规范 UUID 升序锁定全部绑定来源，比较 `expectedSourceVersion` 并生成快照；任一失败整次回滚。以规范字段逐项比较后，草稿正文、标签、元数据或来源快照实际变化时，`draft_revision` 与 `row_version` 各递增 1；只改 `candidateCode` 时仅 `row_version` 递增；全部值相同时返回当前投影且不写行、不改时间或版本。保存 `PUBLISHED|WITHDRAWN` 知识只改变工作草稿，绝不改变原 `visibility/live_snapshot/active_task_id/published_at`。

普通来源修改不锁知识行，也不自动改变草稿快照。发起发布时会重新比较草稿中每个来源快照的 `sourceVersion`；变化返回 `SOURCE_CHANGED`，要求管理员重新保存并确认，而不是静默替换。

### 23.3 公开知识查询

| 方法与路由 | 查询 | 成功 `data` |
| --- | --- | --- |
| `GET /api/knowledge-documents` | 可选 `keyword`、可重复 `tag` | `KnowledgePublicView[]` |
| `GET /api/knowledge-documents/{id}` | 无 | 单个 `KnowledgePublicView` |

`keyword` 去除首尾空白后为 1～200 个码点；每个 `tag` 遵守第 21.1 节标签规则，同时出现时必须全部匹配。列表只查询当前 `PUBLISHED` 的 `live_snapshot`，按 `publishedAt,id` 降序；无结果为 200 空数组。详情不存在、未发布或已下架统一为 `404 RESOURCE_NOT_FOUND`。该公共筛选不是 Agent 检索模式证明，不返回 rank、buildId、摘要、来源 URL 或工作草稿。

## 24. 知识发布、下架与任务查询

### 24.1 发布请求与规范指纹

`POST /api/admin/knowledge-documents/{id}/publications` 需要 ADMIN、规范 UUID `Idempotency-Key`，请求严格为：

~~~json
{
  "expectedVersion": 8
}
~~~

检索模式、模型、维度、切块和超时均由服务端配置决定，请求不得选择或覆盖。Java 在通过身份、路由、请求头和严格 DTO 校验后构造：

~~~json
{
  "contractVersion": "tourism-api-v3-draft-r3",
  "operationType": "PUBLISH_KNOWLEDGE",
  "accountId": "00000000-0000-0000-0000-000000000000",
  "requestKey": "00000000-0000-0000-0000-000000000000",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "expectedVersion": 8
}
~~~

`accountId` 只取 ADMIN JWT `sub`，`documentId` 只取规范路径，其他值取固定路由和已校验输入。对该对象按第 15.3 节规范序列化得到 `requestHash`。服务端配置、任务 ID、来源最新版、当前时间、凭据和请求 ID 不进入请求摘要；它们在首次有效处理时另行冻结。同一管理员、同一 key、不同 `requestHash` 永久返回 `409 IDEMPOTENCY_CONFLICT`，不能以失败、下架、超时或人工重发为由改写原摘要。

### 24.2 失败指纹与接受事务

发布分两个短事务，不跨 Python、Embedding 或 Redis 调用持有 MySQL 锁。

阶段 A 仅持久化请求指纹：

1. 普通 `INSERT` 新 `knowledge_publish_task`，写入全新 `id`、`document_id`、`requested_by`、`request_key`、`request_hash`、`status=RESERVED`、`created_at` 与 `deadline_at=created_at+180 秒`。
2. 禁止 `INSERT IGNORE`、`REPLACE` 或 `ON DUPLICATE KEY UPDATE`。插入成功单独提交；提交结果无法确认时返回 `503 PUBLISH_RESULT_UNAVAILABLE`，不得进入阶段 B 或启动构建。
3. 唯一冲突时原行逐字不变。先比较 `requested_by` 范围内的 `request_hash`；不同返回 `IDEMPOTENCY_CONFLICT`，相同则不再检查当前版本或编辑限制，而对任一任务状态返回 HTTP 200；`data` 只含 `task:KnowledgePublishTaskView` 与 `replayed=true`。失败详情由任务投影的 `error` 表达。重放不延长期限、不重新构建、不改变任务或知识。

阶段 B 只处理同一个 `RESERVED` 指纹：

1. 开启事务，按第 24.4 节先锁知识行，再按 UUID 升序锁定草稿引用的来源行，最后锁本任务以及现有当前任务行。
2. 比较任务仍为 `RESERVED`、摘要不变、`row_version=expectedVersion`、`current_task_id=null`，在取得锁后确认服务器当前时间未过 `deadline_at`，并核对第 21.1 节发布完整性及每个来源当前 `source_version` 仍等于草稿快照版本。
3. 读取当前服务端检索配置。`VECTOR` 缺少第 29.1 节任一必需外部参数时不得降级为关键词；任务可靠终结为 `FAILED / VECTOR_CONFIGURATION_UNAVAILABLE`。合法配置按第 25.1 节冻结并计算 `configHash`。
4. 任一可确定业务拒绝都在仍持锁的事务中把本任务从 `RESERVED` 条件更新为 `FAILED`，写固定 `error_code/error_message/finished_at` 后提交；知识行、草稿、生效快照与当前指针不变。只有该失败终态明确提交后，原请求才返回对应 4xx／503；提交未知统一返回 `PUBLISH_RESULT_UNAVAILABLE`。
5. 全部通过时，从已保存草稿快照构造一次 `KnowledgeSnapshot` 与 `snapshotHash`，把本任务条件更新为 `PENDING` 并冻结全部输入和配置；同时把知识 `current_task_id` 设为本任务、`row_version` 加 1。两个影响行数都必须为 1，随后同事务提交。

只有阶段 B 明确提交 `PENDING` 后，首次请求才返回 HTTP 202：

~~~json
{
  "taskId": "00000000-0000-0000-0000-000000000000",
  "status": "PENDING",
  "documentVersion": 9,
  "editable": false,
  "replayed": false
}
~~~

严格解析前失败不创建指纹；身份失败、未知字段、非法 key 或非法类型不能凭不完整输入占用发布 key。已可靠记录的业务失败即使没有改动知识，也永久保留原 key 与摘要。人工重试必须使用新 key；不能把旧失败任务恢复为 `PENDING`。

### 24.3 任务状态与任务投影

合法状态迁移只有：

~~~text
RESERVED -> PENDING -> RUNNING -> SUCCEEDED
    |           |          |----> FAILED
    |           |          \----> INVALIDATED
    |           |----> FAILED
    |           \----> INVALIDATED
    \----> FAILED
~~~

`INVALIDATED` 只用于任务曾取得或准备取得某条知识的当前资格、随后被下架明确作废的情况；尚未被知识接受的 `RESERVED` 业务拒绝使用 `FAILED`。任何终态不可改变。失败不自动重试；扫描 `PENDING` 是执行首次已受理任务，不是重发失败任务。

本修订不提供发布任务取消、恢复、回滚或删除端点。下架是立即撤销知识公开资格并使当前任务失效的业务操作，不是一个可把任务恢复或重新执行的“取消”按钮；任务列表也不是完整知识版本历史管理器。

ADMIN `KnowledgePublishTaskView` 所有字段固定出现：

~~~json
{
  "taskId": "00000000-0000-0000-0000-000000000000",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "status": "RUNNING",
  "accepted": true,
  "retrievalMode": "VECTOR",
  "draftRevision": 7,
  "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "createdAt": "2026-09-10T01:02:03Z",
  "startedAt": "2026-09-10T01:02:04Z",
  "deadlineAt": "2026-09-10T01:05:03Z",
  "finishedAt": null,
  "buildReceipt": null,
  "error": null
}
~~~

`accepted` 仅对 `PENDING|RUNNING|SUCCEEDED|FAILED|INVALIDATED` 且已冻结输入的任务为 `true`；冻结前 `FAILED` 和 `RESERVED` 为 `false`。`retrievalMode/draftRevision/snapshotHash/configHash` 在未冻结时均为 `null`。`startedAt` 仅从 `RUNNING` 起非空；`finishedAt` 仅终态非空。`buildReceipt` 仅 `SUCCEEDED` 非空。`error` 仅 `FAILED` 为 `{"code":"STABLE_CODE","message":"可展示中文"}`，不得含原始异常、内部 URL、模型响应、密钥、路径或正文。

发布任务 `error.code` 是封闭枚举：`RESOURCE_NOT_FOUND|VERSION_CONFLICT|PUBLISH_IN_PROGRESS|SOURCE_CHANGED|KNOWLEDGE_DRAFT_INCOMPLETE|VECTOR_CONFIGURATION_UNAVAILABLE|PUBLISH_RESERVATION_EXPIRED|BUILD_DEADLINE_EXCEEDED|SERVICE_RESTARTED|VECTOR_BUILD_NOT_READY|VECTOR_BUILD_UNAVAILABLE`。底层异常只映射到这些稳定码；是否能够可靠写入该失败终态仍遵守第 24.2、24.4 节，不能用内存异常覆盖未知提交结果。

任务查询固定为：

| 方法与路由 | 查询 | `data` |
| --- | --- | --- |
| `GET /api/admin/knowledge-publish-tasks` | 可选规范 UUID `documentId` | `KnowledgePublishTaskView[]`，按 `createdAt,taskId` 降序 |
| `GET /api/admin/knowledge-publish-tasks/{taskId}` | 无 | 单个 `KnowledgePublishTaskView` |

查询任务失败状态是 HTTP 200，不把业务失败伪装为查询接口故障；不存在为 `404 RESOURCE_NOT_FOUND`。不得虚构百分比、预计完成时间或“已写入向量”状态。

### 24.4 全局锁序、认领与终结

同一知识相关的草稿保存、发起发布、任务认领、任务完成、任务失败、超时、重启处理和下架，全局锁序固定为：

1. 先锁 `knowledge_document`；不存在时跳过该层，但不得先锁一个存在的任务再回头锁知识。
2. 需要核对来源时，按来源 UUID 规范字符串升序锁 `knowledge_source`。
3. 再按任务 UUID 规范字符串升序锁涉及的 `knowledge_publish_task`；通常为当前任务与本次任务。
4. 最后执行条件更新；所有任务更新都至少带原状态，所有知识更新都至少带原 `row_version` 或 `current_task_id` 条件。

阶段 A 的单行普通插入不读取或锁知识；它提交后，任何后续状态处理都必须遵守上述顺序。来源编辑只锁自身来源行且不再锁知识，避免形成反向锁序。锁等待、死锁、连接失败或提交未知均返回／记录 `PUBLISH_RESULT_UNAVAILABLE`，不得推断失败、成功或未执行。

执行器仅可认领 `PENDING`：先锁知识和任务，重新读取服务器时间，要求 `current_task_id=taskId`、任务输入完整且尚未到 `deadline_at`，再把任务条件更新为 `RUNNING` 并写 `started_at`。不满足时按明确原因终结，绝不调用 Python。

终结任务时再次先锁知识再锁任务。失败只有在任务仍是当前 `PENDING|RUNNING` 时才清空 `current_task_id` 并使知识 `row_version` 加 1；旧 `visibility/live_snapshot/active_task_id/published_at` 保持不变。若当前指针已变化，旧结果只允许确认原任务终态或保持已失效状态，不能清除新任务指针。

### 24.5 成功切换与关键词就绪凭据

`KEYWORD_DEMO` 不调用 Python 或 Embedding。执行器在 Java 内验证固定快照可由第 26.3 节真实查询路径读取，生成：

~~~json
{
  "receiptType": "KEYWORD_READY",
  "taskId": "00000000-0000-0000-0000-000000000000",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "status": "READY",
  "buildId": "00000000-0000-0000-0000-000000000000",
  "checkedAt": "2026-09-10T01:02:05Z"
}
~~~

`buildId` 必须等于 `taskId`。生成该凭据前，Java 对固定快照执行与第 26.3 节相同的字段投影、NFKC／大小写规范化和 token 处理，并确认发布必需字段能够构造查询记录；真正的关键词公开资格仍只在下述成功事务切换 `live_snapshot` 后产生。该凭据不是向量 READY。

无论关键词或向量，切换成功的短事务必须在取得知识行和任务行锁后同时满足：

1. `current_task_id=taskId`，任务仍为 `RUNNING`，任务的 `document_id/draft_revision/snapshot_hash/config_hash` 与固定输入及就绪凭据逐字段一致。
2. 取得锁后重新读取服务端时间，当前时间严格早于或等于 `deadline_at`；等待锁前的时间判断无效。
3. 当前知识的 `draft_revision` 仍等于任务冻结值。`KEYWORD_DEMO` 使用上述本地凭据；`VECTOR` 使用第 25.4 节真实 READY 凭据并复核模式、buildId 和计数。
4. 条件更新任务 `RUNNING -> SUCCEEDED`、写 `build_receipt/finished_at`；同一事务把任务 `input_snapshot` 复制为知识 `live_snapshot`，复制任务 `snapshot_hash`，设置 `active_task_id=taskId`、`visibility=PUBLISHED`、`current_task_id=null`、更新 `published_at` 并使 `row_version` 加 1。两个更新影响行数都必须为 1。

任一条件不满足都不得切换。事务提交未知不得返回成功；任务扫描和同 key 查询只能依据持久状态回答，不能依据内存中的 Python 响应补写成功。

### 24.6 下架、迟到结果、超时与重启

`POST /api/admin/knowledge-documents/{id}/unpublish` 需要 ADMIN，请求严格为：

~~~json
{
  "expectedVersion": 11
}
~~~

事务先锁知识，再锁 `current_task_id` 指向的任务。要求版本匹配且当前不是 `WITHDRAWN`；随后把 `visibility` 设为 `WITHDRAWN`，将当前 `PENDING|RUNNING` 任务条件更新为 `INVALIDATED` 并写 `finished_at`，清空 `current_task_id`，使 `row_version` 加 1。`live_snapshot/active_task_id` 可为 ADMIN 审计保留，但提交后立即失去所有公共、关键词、向量和候选新生成资格；不等待 Redis 物理删除。成功返回新的 `KnowledgeAdminView`。重复旧版本请求为 `VERSION_CONFLICT`，不能影响后来重新发布的版本。

Python 迟到 `READY`、HTTP 超时后的响应、旧 Java Future 或旧扫描器都必须重新走第 24.5 节锁内资格校验。任务不是当前、已到期、已失败、已失效或已成功时不得改变知识，即使摘要和计数看似匹配。重新发布总是新 taskId 和新 key，不复用旧 build。

到期扫描覆盖 `RESERVED|PENDING|RUNNING`。对每个候选先读取其 `document_id`，再在事务中按第 24.4 节锁知识和任务；取得锁后以服务端当前时间核对期限。`PENDING|RUNNING` 到期变为 `FAILED / BUILD_DEADLINE_EXCEEDED`，只在仍是当前任务时清指针并递增知识版本；`RESERVED` 到期变为冻结前 `FAILED / PUBLISH_RESERVATION_EXPIRED`，不改知识。

Java 每次启动时、接受新的知识管理写操作前及启动执行器前，同样处理遗留 `RESERVED|PENDING|RUNNING`：终结为 `FAILED / SERVICE_RESTARTED`，按当前指针条件解锁；不自动续算、重新调用 Python 或新建任务。管理员查看明确失败后用新 key 人工重发。关闭页面、断开浏览器或 WebSocket 不影响持久任务。

## 25. 构建输入、向量存储与真实 READY

### 25.1 严格 `IndexConfig`

`KEYWORD_DEMO` 配置固定为：

~~~json
{
  "retrievalMode": "KEYWORD_DEMO",
  "schemaVersion": "knowledge-keyword-v1",
  "matchingAlgorithm": "JAVA_LITERAL_TOKEN_V1",
  "maxResults": 10
}
~~~

`VECTOR` 配置固定字段为：

~~~json
{
  "retrievalMode": "VECTOR",
  "schemaVersion": "knowledge-vector-v1",
  "embeddingProvider": "configured-provider",
  "embeddingModel": "configured-model",
  "vectorDimension": 1024,
  "vectorDataType": "FLOAT32",
  "distanceMetric": "COSINE",
  "chunkAlgorithm": "UNICODE_CODEPOINT_V1",
  "chunkMaxCodePoints": 1000,
  "chunkOverlapCodePoints": 120,
  "embeddingBatchSize": 16,
  "maxResults": 10
}
~~~

`embeddingProvider`、`embeddingModel` 去空白后各为 1～120 个 ASCII 字符；`vectorDimension` 为 1～4,096；其三者必须由真实外部配置明确提供，没有默认或占位成功。其余 VECTOR 值固定如示例，不能由一次发布请求改写。端点、密钥、请求头和凭据不进入 `IndexConfig`。Java 对完整配置规范序列化计算 `configHash`；Python 只能接受自身当前配置计算出的同一摘要，不能忽略未知字段或以默认维度补齐。

### 25.2 Java → Python 构建请求

`POST /internal/knowledge-builds/{taskId}` 使用 `JAVA_TO_AI` 凭据，不使用用户 JWT。Java 只在任务已持久化为 `RUNNING` 后调用，请求严格为：

~~~json
{
  "taskId": "00000000-0000-0000-0000-000000000000",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "draftRevision": 7,
  "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "snapshot": {
    "documentId": "00000000-0000-0000-0000-000000000000",
    "draftRevision": 7,
    "title": "乌东村寨知识条目",
    "content": "经管理员核读后整理的正文。",
    "tags": ["村寨生活"],
    "region": "乌东",
    "periodText": null,
    "evidenceCategory": "地方资料",
    "usageLimitations": ["仅用于本平台知识讲解"],
    "demoData": false,
    "sources": [
      {
        "sourceId": "00000000-0000-0000-0000-000000000000",
        "sourceVersion": 3,
        "sourceKey": "S01",
        "sourceTitle": "受控来源标题",
        "publisher": null,
        "sourceKind": "地方资料",
        "publicationDateText": null,
        "readAt": "2026-09-10T01:02:03Z",
        "locator": "第 2 节",
        "readStatus": "FULL_TEXT_REVIEWED"
      }
    ]
  },
  "indexConfig": {
    "retrievalMode": "VECTOR",
    "schemaVersion": "knowledge-vector-v1",
    "embeddingProvider": "configured-provider",
    "embeddingModel": "configured-model",
    "vectorDimension": 1024,
    "vectorDataType": "FLOAT32",
    "distanceMetric": "COSINE",
    "chunkAlgorithm": "UNICODE_CODEPOINT_V1",
    "chunkMaxCodePoints": 1000,
    "chunkOverlapCodePoints": 120,
    "embeddingBatchSize": 16,
    "maxResults": 10
  },
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "deadlineAt": "2026-09-10T01:05:03Z"
}
~~~

`snapshot` 必须是完整 `KnowledgeSnapshot`，`indexConfig` 必须是第 25.1 节 VECTOR 变体，路径与正文 `taskId` 必须相同。Java 的 HTTP 连接超时为 5 秒，响应等待上限为 120 秒且不得超过调用时任务剩余期限；客户端库的自动重试、重定向和隐式重新提交全部关闭。网络、超时、非 2xx、解析失败或返回非 `READY` 都不能视为成功，由 Java 按第 24.4 节可靠终结任务或在结果未知时等待期限扫描，绝不降级为关键词成功。

### 25.3 Python 回读固定输入

Python 收到构建请求后，在切块、Embedding 或写 Redis 之前，必须用独立 `AI_TO_JAVA` 凭据调用 `GET /internal/agent/knowledge-build-inputs/{taskId}`。Java 取得知识和任务行锁后仅在下列条件全部成立时返回 HTTP 200：任务为当前 `RUNNING`、`document_id` 匹配、输入与配置均已冻结、服务器当前时间未过 `deadline_at`。`data` 固定为与第 25.2 节请求正文逐字段相同的对象。

任务不具资格统一返回 `409 BUILD_TASK_NOT_ELIGIBLE`，`details=null`；不存在与非当前不区分。Python 必须规范化并逐字段比较回读对象、路径和收到的构建请求，再自行重算 `snapshotHash/configHash`。任何差异返回 `409 BUILD_INPUT_MISMATCH`，且不得调用 Embedding、写 Redis 或返回 READY。服务身份只允许读取本次仍有效任务的固定输入，不能列举所有任务或读取草稿最新版。

### 25.4 Redis 构建结构与 READY 回执

VECTOR 每个任务使用隔离的不可公开构建空间；`configHashHex` 是去掉 `sha256:` 的 64 位摘要：

~~~text
索引名：wudong-knowledge-v3-{configHashHex}
manifest：wudong:knowledge:v3:{configHashHex}:{buildId}:manifest
chunk：wudong:knowledge:v3:{configHashHex}:{buildId}:chunk:{chunkId}
~~~

`buildId` 必须等于任务 UUID。manifest 至少包含 `taskId/documentId/buildId/snapshotHash/configHash/expectedChunkCount/schemaVersion/vectorDimension/status`。每个 chunk 至少包含 `documentId/buildId/chunkId/sequence/text/region/periodText/snapshotHash/configHash/vector`；`chunkId` 固定为对 `{documentId,buildId,sequence,text}` 规范对象计算的 `Sha256Digest`。`sequence` 连续为 `1..N`，正文非空；向量必须恰有 `vectorDimension` 个有限 FLOAT32 值。不得通过全局别名、覆盖旧 build 或修改 Java 指针使准备中构建生效。

只有下列检查全部通过才可返回：

~~~json
{
  "receiptType": "VECTOR_READY",
  "taskId": "00000000-0000-0000-0000-000000000000",
  "documentId": "00000000-0000-0000-0000-000000000000",
  "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "status": "READY",
  "buildId": "00000000-0000-0000-0000-000000000000",
  "expectedChunkCount": 4,
  "searchableChunkCount": 4,
  "checkedAt": "2026-09-10T01:04:30Z"
}
~~~

真实 READY 必须同时证明：固定输入产生非空完整切块集合；全部向量维度和值合法；每个预期 chunk 已在本 build 空间写入；使用实际生产查询索引并按 `documentId + buildId + configHash` 过滤可读回完全相同的 chunkId 集合；manifest 与任务字段一致；索引无阻止该配置查询的错误；回读和 `checkedAt` 均未超过 `deadlineAt`。Redis 全局计数、写命令成功、返回数量大于零、Embedding 响应成功或 manifest 自报 READY 均不能单独满足条件。

Python 在完成上述读取检查后才把 manifest 设为 READY 并返回严格回执。部分写入、孤立 build 或旧 build 永远没有 Java 公开资格；本修订不要求在失败路径物理删除它们，也不把发布任务“不自动清理”扩展为索引永久保留承诺。Java 收到回执后仍必须执行第 24.5 节锁内终验；Python READY 不是发布决定。

## 26. 当前 build、检索资格与双重依赖核验

### 26.1 `active-builds`

Python 进行 VECTOR 检索前调用：

~~~text
GET /internal/agent/knowledge/active-builds?configHash=<Sha256Digest>
~~~

使用 `AI_TO_JAVA` 凭据。`configHash` 必填且不得重复。成功 `data` 固定为：

~~~json
{
  "retrievalMode": "VECTOR",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "checkedAt": "2026-09-10T01:02:03Z",
  "builds": [
    {
      "documentId": "00000000-0000-0000-0000-000000000000",
      "buildId": "00000000-0000-0000-0000-000000000000",
      "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
    }
  ]
}
~~~

Java 只返回当前 `PUBLISHED` 且满足第 22.1 节公开资格、其 `active_task_id` 为 `SUCCEEDED / VECTOR`、任务 `config_hash` 精确匹配的行，按 `documentId` 升序。空集合是有效结果；配置不匹配不能返回其他维度 build。该列表只减少 Redis 旧构建进入候选的概率，不是后续资格证明，也不得缓存跨越一次生成。

### 26.2 `eligibility`

检索候选进入模型前，以及最终知识正文交付前，Python 都调用 `POST /internal/agent/knowledge/eligibility`，使用 `AI_TO_JAVA` 凭据，请求严格为：

~~~json
{
  "retrievalMode": "VECTOR",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "candidates": [
    {
      "documentId": "00000000-0000-0000-0000-000000000000",
      "buildId": "00000000-0000-0000-0000-000000000000"
    }
  ]
}
~~~

`candidates` 为 1～50 项，`documentId + buildId` 不得重复，数组顺序保留。`configHash` 必须是调用方当前严格配置摘要；`KEYWORD_DEMO` 使用第 25.1 节关键词配置摘要，`VECTOR` 使用向量摘要。成功 `data` 固定为：

~~~json
{
  "retrievalMode": "VECTOR",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "checkedAt": "2026-09-10T01:02:03Z",
  "results": [
    {
      "documentId": "00000000-0000-0000-0000-000000000000",
      "buildId": "00000000-0000-0000-0000-000000000000",
      "eligible": true,
      "evidence": {
        "documentId": "00000000-0000-0000-0000-000000000000",
        "buildId": "00000000-0000-0000-0000-000000000000",
        "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
        "title": "乌东村寨知识条目",
        "content": "经管理员核读后整理的正文。",
        "tags": ["村寨生活"],
        "region": "乌东",
        "periodText": null,
        "evidenceCategory": "地方资料",
        "usageLimitations": ["仅用于本平台知识讲解"],
        "demoData": false,
        "references": [
          {
            "sourceTitle": "受控来源标题",
            "detailPath": "/api/knowledge-documents/00000000-0000-0000-0000-000000000000"
          }
        ]
      }
    }
  ]
}
~~~

结果与请求同序且一一对应。`eligible=true` 时必须出现完整 `KnowledgeEvidence`；`eligible=false` 时必须省略 `evidence`，不能将其设为 `null` 或返回失效正文。`KnowledgeEvidence` 固定为：`documentId`、`buildId`、`snapshotHash`、`title`、`content`、`tags`、`region:string|null`、`periodText:string|null`、`evidenceCategory`、`usageLimitations`、`demoData`、`references:[{sourceTitle,detailPath}]`。它从当前 `live_snapshot` 投影，`references` 与第 21.3 节完全相同。

两种模式都必须核对当前 `visibility`、`active_task_id=buildId`、任务 `SUCCEEDED`、snapshot hash 和生效快照。VECTOR 还要求任务 `retrieval_mode=VECTOR` 且任务 `config_hash` 与请求一致；KEYWORD_DEMO 核对当前 Java 关键词配置摘要，不要求生效任务当初也以关键词构建，因此成功向量发布的同一生效快照仍可被明确关键词模式查询。已下架、换版、配置不符或未知 pair 只返回 `eligible=false`。数据库或资格状态不可确认时整个调用返回 `503 KNOWLEDGE_ELIGIBILITY_UNAVAILABLE`，不能把不可确认伪装为 false 或返回部分正文。

### 26.3 Java 关键词检索

`GET /internal/agent/knowledge/search?keywords=<text>&limit=<integer>` 使用 `AI_TO_JAVA` 凭据，只执行 `KEYWORD_DEMO`。`keywords` 去空白后为 1～200 个码点；`limit` 可省略，默认 10，范围 1～10。成功 `data` 固定为：

~~~json
{
  "retrievalMode": "KEYWORD_DEMO",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "checkedAt": "2026-09-10T01:02:03Z",
  "results": [
    {
      "rank": 1,
      "documentId": "00000000-0000-0000-0000-000000000000",
      "buildId": "00000000-0000-0000-0000-000000000000",
      "evidence": {
        "documentId": "00000000-0000-0000-0000-000000000000",
        "buildId": "00000000-0000-0000-0000-000000000000",
        "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
        "title": "乌东村寨知识条目",
        "content": "经管理员核读后整理的正文。",
        "tags": ["村寨生活"],
        "region": "乌东",
        "periodText": null,
        "evidenceCategory": "地方资料",
        "usageLimitations": ["仅用于本平台知识讲解"],
        "demoData": false,
        "references": [
          {
            "sourceTitle": "受控来源标题",
            "detailPath": "/api/knowledge-documents/00000000-0000-0000-0000-000000000000"
          }
        ]
      }
    }
  ]
}
~~~

`evidence` 是第 26.2 节完整对象，`rank` 连续为 `1..N`。`JAVA_LITERAL_TOKEN_V1` 先对查询及待检索字段执行 Unicode NFKC，再以 `Locale.ROOT` 转小写；查询按一个或多个 Unicode White_Space 切分并去除空项。结果必须命中至少一个不同 token；每个 token 在标题、任一完整标签、正文中出现分别计 3、2、1 分，同一字段内重复出现不重复计分，按总分降序、标题命中数降序、`published_at` 降序、`documentId` 升序稳定排列。Java 只检索当前 `live_snapshot` 的标题、正文和标签，不得查询工作草稿、来源主档、旧快照或 Redis；不返回相关度小数。无结果返回空数组。该响应明确标记演示关键词模式；VECTOR 失败或未配置时，调用方只有主动选择该路由才是关键词结果，不能把一次向量请求的失败改写为此成功。

### 26.4 入模前与交付前核验

一次生成的完整知识依赖集合固定为去重后的 `KnowledgeDependency[]`：每项只含 `documentId`、`buildId`、`snapshotHash`，按 `documentId,buildId` 升序。VECTOR 的 Redis 命中必须先与本轮 `active-builds` 按这三个字段精确相交，再以全部候选调用 eligibility；只有 `eligible=true` 返回的 Java `evidence` 可进入模型，Redis chunk 正文不得直接作为可信正文。KEYWORD_DEMO 直接使用 Java search evidence，但仍记录同样的依赖集合。

在任何最终卡片正文对用户可见前，Python 必须以本轮同一 `retrievalMode/configHash` 对整个依赖集合再次调用 eligibility，并逐项比较 `snapshotHash`。只核验模型最终列出的来源、只核验某一张卡或只删除来源链接都不合格。任一项失效、换版、摘要变化或调用不可确认时，丢弃全部尚未交付的知识派生正文，输出独立卡片契约规定的“知识已更新／暂不可核验”状态；不得继续拼装、部分交付或回退旧 chunk。

阶段中只允许发送不含知识正文和来源内容的处理进度。checkpoint 中保存的片段、先前卡片、run summary 或 LangSmith 记录都不是新的公开授权；恢复或下一轮必须重新核验。最终可见引用只含来源标题与本站详情路径。已经交付的历史内容不承诺撤回，但不得因历史可见而在新输出中继续使用失效知识。

## 27. 三类精确候选解析

### 27.1 请求、证明与基础版本

Java 为第 16 节任一尚未成功的新采用调用 `POST /internal/assistant/candidates/resolve`。请求使用 `JAVA_TO_AI` 凭据和当前 `X-Wudong-User-Proof`；候选来自匿名 thread 时还必须携带当前 `X-Wudong-Anonymous-Proof`。请求严格为：

~~~json
{
  "candidateRef": {
    "threadId": "00000000-0000-0000-0000-000000000000",
    "candidateId": "00000000-0000-0000-0000-000000000000",
    "candidateVersion": 2
  },
  "expectedCandidateType": "FOOD_DRAFT",
  "expectedAction": "UPDATE",
  "expectedBase": {
    "resourceType": "FOOD_DRAFT",
    "resourceId": "00000000-0000-0000-0000-000000000000",
    "resourceVersion": 7
  }
}
~~~

`CREATE` 时 `expectedBase` 必须明确为 `null`；`UPDATE` 时必须为上述对象，且 `resourceType` 与候选类型一一对应为 `ITINERARY|FOOD_DRAFT|STAY_DRAFT`。Java 从采用路由确定 expected 类型与动作，从路径和请求 `expectedVersion` 构造 expected base；客户端不能在公共采用请求中提供这些内部字段。

Python 先验证服务方向，再验证 USER JWT、Redis sid、thread 与账号归属。USER thread 必须属于 JWT sub；ANONYMOUS thread 必须同时由有效匿名证明精确授权，USER proof 只证明采用结果写入哪个账号，不能替代匿名访问。任一证明失败都不得查询候选。服务凭据、threadId、candidateId 或已展示过的卡片都不能单独授权解析。

Java 的候选解析 HTTP 连接／总等待上限分别为 2／5 秒，且关闭自动重试和重定向。超时或结果不可确认统一返回第 27.4 节的 `CANDIDATE_LOOKUP_UNAVAILABLE`；同一用户操作只有在调用方复用原 `Idempotency-Key` 与原 candidateRef 时才能主动重试，不能换候选版本或推断未保存。

### 27.2 不可变候选信封

成功 `data` 的共同字段固定为：

~~~json
{
  "candidateRef": {
    "threadId": "00000000-0000-0000-0000-000000000000",
    "candidateId": "00000000-0000-0000-0000-000000000000",
    "candidateVersion": 2
  },
  "candidateType": "FOOD_DRAFT",
  "adoptionAction": "UPDATE",
  "baseResource": {
    "resourceType": "FOOD_DRAFT",
    "resourceId": "00000000-0000-0000-0000-000000000000",
    "resourceVersion": 7
  },
  "sourceOwnerKind": "USER",
  "createdAt": "2026-09-10T01:00:00Z",
  "expiresAt": "2026-09-11T01:00:00Z",
  "candidateDigest": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "knowledgeContext": null,
  "knowledgeDependencies": [],
  "payload": {
    "merchantId": "00000000-0000-0000-0000-000000000000",
    "items": [
      {
        "foodItemId": "00000000-0000-0000-0000-000000000000",
        "quantity": 2
      }
    ],
    "visitAt": null,
    "peopleCount": null
  },
  "resolvedAt": "2026-09-10T01:02:03Z"
}
~~~

`sourceOwnerKind` 只允许 `USER|ANONYMOUS`。`CREATE` 的 `baseResource=null`，`UPDATE` 必须非空。`knowledgeContext` 为 `null`，或严格对象 `{"retrievalMode":"KEYWORD_DEMO|VECTOR","configHash":"Sha256Digest"}`；仅当 `knowledgeDependencies=[]` 时它才为 `null`，依赖非空时必须非空。`knowledgeDependencies` 使用第 26.4 节对象，按固定顺序保存。`resolvedAt` 是本次查询时间，不改变候选。

`candidateDigest` 的规范材料固定包含 `contractVersion`、完整 `candidateRef`、`candidateType`、`adoptionAction`、`baseResource`、`sourceOwnerKind`、`createdAt`、`expiresAt`、`knowledgeContext`、`knowledgeDependencies` 和完整 `payload`，不包含 `resolvedAt`。Python 持久化每个 `candidateId + candidateVersion` 的完整材料与摘要，创建后不得原位更新；修订候选必须写新正整数版本和完整新载荷，不能以补丁覆盖旧版本。摘要是完整性凭据而非授权或签名，Java 仍须重算并比较。

界面可按独立卡片契约展示“当前完整方案”和“上一完整方案”，但服务端不能因已有候选数量超过 2 就提前删除仍在期限内、已发给客户端的精确版本。候选不形成供用户浏览的完整历史管理器；所有已发 candidateRef 只保留到各自服务器 `expiresAt`。过期由 Python 在读取候选记录后使用服务器时间判断，不采信客户端时间，也不因读取、重连或解析续期。

### 27.3 三类 `payload`

`candidateType=ITINERARY` 的 `payload` 必须是第 16.1 节完整 `SavedItinerary.content`，字段只含 `title`、`travelDate`、`peopleCount`、`days` 及其严格子字段。Java 仍重新核对每个结构化 target 当前类型和公开资格；无可靠平台 ID 的叙述项必须按第 16.1 节使用两个 `null`。

`candidateType=FOOD_DRAFT` 的 `payload` 固定为纯规划字段：

~~~json
{
  "merchantId": "00000000-0000-0000-0000-000000000000",
  "items": [
    {
      "foodItemId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ],
  "visitAt": null,
  "peopleCount": null
}
~~~

`candidateType=STAY_DRAFT` 的 `payload` 固定为：

~~~json
{
  "roomTypeId": "00000000-0000-0000-0000-000000000000",
  "checkInDate": null,
  "checkOutDate": null,
  "roomCount": null,
  "peopleCount": null
}
~~~

两类草稿候选都禁止 `contactName`、`contactPhone`、`note`、账号、金额、状态、sourceThreadId 和任意自由扩展键。CREATE 时 Java 用候选规划列创建草稿，并固定 `contactName=null`、`contactPhone=null`、`note=null`。UPDATE 时 SQL 只列出上述规划列；现有联系人和私人备注逐列保持原值，即使它们当前为 `null`，也不得用完整 JSON 替换或把候选缺字段解释为清空。公共采用请求仍不能回传候选 payload。

### 27.4 解析、知识和 Java 写入前核验

Python 必须精确查找三个 ID 组成的候选版本，不得在版本缺失时改取最新版本、上一版或重新生成。随后逐字段要求类型、动作、base 与请求完全相同，服务器当前时间不晚于 `expiresAt`，并重新计算候选摘要。候选有知识依赖时，在返回前使用已冻结的 `knowledgeContext.retrievalMode/configHash` 按第 26.4 节重新核验完整依赖集合；任一变化或不可确认都不返回 payload。

Java 收到成功响应后，必须在任何业务写事务前严格解析、重算摘要，并再次用 Java 服务器当前时间检查 `expiresAt`。响应的 candidateRef、类型、动作和 base 必须与请求逐字段相同。然后按第 17 节锁住原操作槽和目标资源，重新校验目标本人归属、数据库当前版本、目录发布状态、同店、日期与容量；候选解析成功不保证业务保存成功，也不能绕过 `expectedVersion`。

错误边界固定为：在已验证访问范围内，期限已过返回 `410 CANDIDATE_EXPIRED`；精确版本不存在返回 `410 CANDIDATE_VERSION_UNAVAILABLE`；类型、动作、base、摘要或严格 payload 不符返回 `409 CANDIDATE_PAYLOAD_INVALID`；基础资源与采用路由不符返回 `409 CANDIDATE_BASE_MISMATCH`；Python、Redis、依赖核验或结果状态不可确认返回 `503 CANDIDATE_LOOKUP_UNAVAILABLE`。这些响应不回显候选正文、内部所有者、摘要材料或存在于其他 thread 的信息。

同 key 已 `SUCCEEDED` 的采用重试仍按第 17.4 节先返回原成功回执，不重新解析候选，因此候选后来过期、下架或被清理不反转已提交业务结果。同 key 尚未成功时不得把 lookup 不可用记录为成功或 `NOT_APPLIED`；调用方保持结果未知边界。

## 28. 脱敏运行摘要、去重与乱序

### 28.1 严格请求白名单

Python 使用 `AI_TO_JAVA` 凭据调用 `POST /internal/agent/run-summaries`。该端点不是订单、草稿、候选、知识或账号写入口，也不接受浏览器调用。请求所有字段固定为：

~~~json
{
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": "00000000-0000-0000-0000-000000000000",
  "summarySequence": 4,
  "runState": "COMPLETED",
  "agentType": "ITINERARY_PLANNER",
  "retrievalMode": "KEYWORD_DEMO",
  "startedAt": "2026-09-10T01:00:00Z",
  "finishedAt": "2026-09-10T01:02:00Z",
  "knowledgeDependencies": [],
  "candidateRefs": [],
  "errorCode": null
}
~~~

`runState` 只允许 `RUNNING|CANCEL_REQUESTED|STOPPED|INTERRUPTED|COMPLETED|FAILED`；`agentType` 只允许 `KNOWLEDGE_GUIDE|SERVICE_RECOMMENDER|ITINERARY_PLANNER`；该处 `retrievalMode` 只允许 `NONE|KEYWORD_DEMO|VECTOR`。`knowledgeDependencies` 为第 26.4 节对象，最多 50 项且不得重复；`candidateRefs` 为第 16.4 节引用，最多 20 项且不得重复，且每一项的 `threadId` 必须等于顶层 `threadId`。两个数组都按调用方本轮确定性顺序保存，不包含正文。`retrievalMode=NONE` 时知识依赖必须为 `[]`；另两种模式允许因无命中而为空。

`RUNNING|CANCEL_REQUESTED` 必须 `finishedAt=null,errorCode=null`。`COMPLETED` 必须 `finishedAt` 非空且 `errorCode=null`。`STOPPED` 必须 `finishedAt` 非空且 `errorCode=CANCELLED_BY_USER`。`INTERRUPTED|FAILED` 必须 `finishedAt` 和 `errorCode` 非空；`errorCode` 只能是 1～64 位 ASCII 大写字母、数字和下划线组成的稳定码，不得放异常消息、供应商正文或用户内容。`finishedAt` 不早于 `startedAt`。

请求明确禁止自由文本 summary、prompt、用户输入、模型输出、工具参数／结果、知识正文、来源标题或 URL、联系人、备注、账号 ID、JWT、Cookie、匿名凭据、服务凭据、路径、堆栈和原始异常。`threadId/runId` 仅供 Java 内部关联，普通日志不得记录二者与 candidateRef 的组合。LangSmith 的更窄白名单只允许 `runState`、`agentType`、`retrievalMode`、依赖数量、候选数量和稳定 `errorCode`；不得发送 threadId、runId、任何 ID 数组或本请求的完整 JSON，原文自动追踪默认关闭。

### 28.2 运行状态语义

- `RUNNING` 表示当前 run 已被实际接受并仍可能产生工具调用或输出。
- `CANCEL_REQUESTED` 只表示收到取消意图，不表示执行、工具、持久化或输出已经停止。
- `STOPPED` 只在执行器确认不再产生该 run 的模型、工具、checkpoint 或用户输出，且独立卡片契约的原子 run 围栏已关闭后写入。
- `INTERRUPTED` 表示连接、进程或依赖中断且本 run 不会继续；有限重连只能重新认证并读取状态，不能恢复同一 run 生成。
- `COMPLETED` 只在最终结果及当前指针已经按独立卡片契约可靠提交后写入。
- `FAILED` 是其他明确终结。摘要状态不能替代 checkpoint 提交、当前 run 指针或迟到输出围栏的持久证据。

允许的非终态前移为 `RUNNING -> CANCEL_REQUESTED`。`RUNNING|CANCEL_REQUESTED` 可终结为 `STOPPED|INTERRUPTED|COMPLETED|FAILED`，因为完成与取消可能竞争；四个终态不可再改变。新摘要不能让状态倒退，也不能以更大 sequence 复活终态。

### 28.3 摘要指纹、去重和乱序处理

Java 对完整严格请求按第 15.3 节规范序列化，计算 `summaryDigest`；凭据、请求头、接收时间和 `X-Request-Id` 不进入摘要。持久唯一键为 `(thread_id,run_id)`，当前行至少保存 `last_sequence`、`last_digest`、`run_state`、白名单字段与服务端 `received_at`。

同一数据库事务按唯一键锁行并处理：

1. 没有记录：任何正 `summarySequence` 均可作为当前已知最新摘要插入；不要求等待缺失的较小序号。
2. 新 sequence 小于 `last_sequence`：返回 `STALE_IGNORED`，不改任何列；迟到状态、候选和依赖都不能覆盖新值。
3. 新 sequence 等于 `last_sequence` 且摘要相同：返回 `REPLAYED`，不改任何列。
4. 新 sequence 等于 `last_sequence` 但摘要不同：返回 `409 RUN_SUMMARY_SEQUENCE_CONFLICT`，不得采用任一新字段。
5. 新 sequence 大于 `last_sequence`：仅在当前非终态且状态迁移合法时，以 `WHERE last_sequence < ?` 条件整体替换白名单快照；影响行数必须为 1。当前已终态返回 `409 RUN_SUMMARY_TERMINAL`。

sequence 可以有空洞；服务器不等待丢失的中间摘要。并发 sequence 5 与 6 由行锁和条件更新裁决，最终只能保留较大者；随后到达的较小值为 `STALE_IGNORED`。提交结果未知返回 `503 RUN_SUMMARY_UNAVAILABLE`，不得声称 APPLIED 或通过重试覆盖未知行。

成功 `data` 固定为：

~~~json
{
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": "00000000-0000-0000-0000-000000000000",
  "receivedSequence": 4,
  "storedSequence": 4,
  "storedState": "COMPLETED",
  "outcome": "APPLIED",
  "receivedAt": "2026-09-10T01:02:03Z"
}
~~~

`outcome` 只允许 `APPLIED|REPLAYED|STALE_IGNORED`。STALE 时 `storedSequence/storedState` 返回当前较新值；`receivedAt` 是本次 Java 接收时间，不改写原摘要业务时间。响应不返回 digest 或任一正文。该接口不续 USER 登录、匿名会话、thread、候选或知识期限，也不用于判断内容已经对用户交付。

## 29. B3 配置、错误与兼容

### 29.1 有限本机配置

| 配置 | 固定值／规则 | 缺失影响 |
| --- | --- | --- |
| `KNOWLEDGE_RETRIEVAL_MODE` | 默认 `KEYWORD_DEMO`；启用 VECTOR 必须显式改为 `VECTOR` | 缺失仍明确为关键词演示，不冒充向量 |
| `KNOWLEDGE_PUBLISH_DEADLINE_SECONDS` | `180` | 不允许无期限任务 |
| `KNOWLEDGE_BUILD_CONNECT_TIMEOUT_SECONDS` | `5` | 不允许库默认无限连接 |
| `KNOWLEDGE_BUILD_RESPONSE_TIMEOUT_SECONDS` | `120`，且每次不超过任务剩余期限 | 不允许库默认无限读取 |
| `KNOWLEDGE_BUILD_CONCURRENCY` | `1` | 本机单实例有界执行器 |
| `KNOWLEDGE_TASK_SCAN_INTERVAL_SECONDS` | `5` | 仅扫描已受理 PENDING 与到期任务，不自动重试失败 |
| `KNOWLEDGE_SEARCH_MAX_RESULTS` | `10` | 请求同样受第 26.3 节 10 的硬上限 |
| `CANDIDATE_RESOLVE_CONNECT_TIMEOUT_SECONDS` | `2` | Java 候选解析有限连接等待 |
| `CANDIDATE_RESOLVE_TIMEOUT_SECONDS` | `5` | Java 候选解析有限总等待 |
| `KNOWLEDGE_TASK_RETENTION` | `NO_AUTO_CLEANUP` | 不删除本机任务账本、固定输入或结果 |
| `EMBEDDING_PROVIDER` | VECTOR 必填，无默认 | 只阻塞 VECTOR 新构建 |
| `EMBEDDING_MODEL` | VECTOR 必填，无默认 | 同上 |
| `EMBEDDING_VECTOR_DIMENSION` | VECTOR 必填，1～4,096，无默认 | 同上 |
| `EMBEDDING_ENDPOINT` | VECTOR 必填的服务端绝对 HTTPS 地址，无默认 | 同上；不进入任务正文或模型 |
| `EMBEDDING_CREDENTIAL_FILE` | VECTOR 必填的仓库外绝对路径，无默认 | 同上；密钥不得进入 Git 或请求快照 |

VECTOR 还必须锁定与第 25.4 节索引、FLOAT32 和 COSINE 兼容的 Redis Search 模块实际版本；现有 Redis 基础版本或浮动镜像不能自动证明向量能力可用。任何外部参数或兼容模块缺失时，身份、目录、三类订单、草稿、三个 Agent 的无知识能力、现有公开快照与明确 `KEYWORD_DEMO` 查询继续可用；只有 VECTOR 发布／检索返回对应不可用错误。系统不得偷偷调用第三方 Marketplace、换供应商、改维度或把关键词结果标成 VECTOR。

### 29.2 B3 错误目录追加

第 11、18.4、27.4 节继续有效，B3 新增：

| HTTP | code | 固定语义 |
| --- | --- | --- |
| 409 | `PUBLISH_IN_PROGRESS` | 当前知识已有 PENDING／RUNNING 任务，禁止保存或重复发起新任务 |
| 409 | `SOURCE_CHANGED` | 草稿绑定的来源版本已变化，须重新保存并确认 |
| 409 | `KNOWLEDGE_DRAFT_INCOMPLETE` | 草稿可保存但不满足发布完整性 |
| 409 | `KNOWLEDGE_ALREADY_WITHDRAWN` | 当前知识已经下架，旧版本请求不能重复改变状态 |
| 409 | `BUILD_TASK_NOT_ELIGIBLE` | build-inputs 请求的任务不是当前有效 RUNNING 任务 |
| 409 | `BUILD_INPUT_MISMATCH` | Python 收到的构建输入与 Java 回读固定输入不一致 |
| 409 | `RETRIEVAL_MODE_MISMATCH` | 调用的检索路由与明确模式／配置不匹配 |
| 409 | `CANDIDATE_PAYLOAD_INVALID` | 精确候选类型、动作、摘要或严格载荷不一致 |
| 409 | `CANDIDATE_BASE_MISMATCH` | 候选绑定基础资源与采用目标不一致 |
| 409 | `RUN_SUMMARY_SEQUENCE_CONFLICT` | 同 run 同 sequence 已绑定不同脱敏摘要 |
| 409 | `RUN_SUMMARY_TERMINAL` | 试图以更大 sequence 改写已终结 run 摘要 |
| 503 | `VECTOR_CONFIGURATION_UNAVAILABLE` | VECTOR 必需外部参数或兼容索引能力缺失 |
| 503 | `PUBLISH_RESULT_UNAVAILABLE` | 发布指纹、锁、任务事务或提交结果无法确认 |
| 503 | `VECTOR_BUILD_NOT_READY` | 构建或真实查询路径未满足全部 READY 条件 |
| 503 | `KNOWLEDGE_ELIGIBILITY_UNAVAILABLE` | Java 无法确认当前知识资格，不得返回正文或 false |
| 503 | `RUN_SUMMARY_UNAVAILABLE` | 摘要锁、写入或提交结果无法确认 |

`SOURCE_CHANGED.details` 只含 `{"kind":"source_changed","sourceIds":["UUID"]}`，ID 按升序且只在 ADMIN 已获知识访问权后返回。`KNOWLEDGE_DRAFT_INCOMPLETE.details` 只含 `kind=knowledge_draft_incomplete` 与按 `title,content,evidenceCategory,sources,sourceReadStatus` 固定顺序排列的 `missingFields`。`PUBLISH_IN_PROGRESS.details` 只含 `kind=publish_in_progress`、`taskId` 和 `status`。其他新增错误 `details=null`，除非本节明确列出；不得返回摘要、配置秘密、候选正文、Redis 键、内部 URL 或原始异常。

### 29.3 旧链与版本兼容

- `tourism-api-v1.md`、`tourism-api-v2.md` 和 r2 前置制品保持原样。旧 `published/indexStatus` 不映射为 r3 READY，旧知识逐条进入草稿评审。
- r3 的 `X-Wudong-Contract`、WS `contractVersion`、报价指纹和操作摘要都使用 `tourism-api-v3-draft-r3`；混用 r2 字符串返回 `CONTRACT_INCOMPATIBLE`，不得部分解析。
- 已存在的 B1、B2 数据与接口语义不因新增知识任务或候选解析而放宽。ADMIN 服务凭据不能访问 USER 私人成果；Python 仍不能写 MySQL 业务事实。
- 旧候选和 checkpoint 只有在独立卡片契约能验证其确切版本、归属和原服务器期限时才可只读提示；不得转换成 r3 candidateRef、延长期限或自动采用。
- 统一切换需要 Java、Python、Web、小程序与 `assistant-card-v3` 严格消费者全部独立验收。本契约文本存在、任务状态为 IMPLEMENTED 或某端自测通过都不是集成或运行证据。

## 30. B3 Java 共享完成边界

| TASK-026 验收项 | 本文落点 |
| --- | --- |
| 知识三表、保存不公开、固定快照、发布／下架／查询 | 第 21～23、24.5～24.6 节 |
| ADMIN 发布 key、规范摘要、失败指纹、全局锁序、迟到拒绝 | 第 24.1～24.6 节 |
| build-inputs、构建配置、Redis 隔离结构和真实 READY | 第 25 节 |
| active-builds、eligibility、Java 关键词 search、入模与交付双核验 | 第 26 节 |
| 行程／餐食草稿／住宿草稿三类不可变候选、归属、版本、期限与联系人保留 | 第 27 节 |
| run-summaries 脱敏白名单、同序去重、乱序忽略与终态拒绝 | 第 28 节 |
| 有限超时／并发、向量缺参局部阻塞、错误和旧链兼容 | 第 29 节 |
| B1／B2 不漂移 | 第 20.1、29.3 节及原第 1～19 节 |

批准计划 B3 的逐项对应为：

| 批准计划 B3 项 | 本任务对应与边界 |
| --- | --- |
| API／卡片／事件／checkpoint 版本匹配、候选引用、基础版本、采用动作、来源标题与站内详情 | 第 20.1 节冻结 API 与独立卡片契约的版本分工；第 21.3、26、27 节冻结 Java 需要的来源与候选字段。六类客户端卡片、事件和 checkpoint 由独立任务冻结，本文件不越权代写。 |
| 当前／上一完整方案、非完整历史、版本／归属／服务器期限 | 第 27.1～27.2 节；精确已发版本在各自期限内不按数量提前删除，但不提供完整历史管理。 |
| RUNNING、取消请求、确实停止、中断、完成与原子 run 围栏 | 第 28.2～28.3 节冻结 Java 脱敏摘要语义及终态拒绝；实际 checkpoint／当前指针原子围栏仍由独立卡片契约冻结，摘要不替代该证据。 |
| 知识三表、固定快照、服务认证、taskId／摘要／期限、真实 READY、关键词与向量分离 | 第 20.2、21～25 节。 |
| 入模前与最终交付前完整依赖核验，阶段正文与观测脱敏 | 第 26.4、28.1 节。 |
| 有限超时、并发、索引结构、维度、任务不自动清理、缺参只阻塞 vector | 第 25.1～25.4、29.1 节。 |

知识存储接口规格的逐项对应为：

| 知识规格主题 | 本文落点 |
| --- | --- |
| 方案 C：Java 持久任务、后台执行、Python 构建、Java 决定生效 | 第 24～25 节 |
| `knowledge_document`、`knowledge_source`、`knowledge_publish_task` 与固定来源／生效快照 | 第 21～22 节 |
| 保存、来源版本、发布、失败保留旧版、下架和人工重发 | 第 23～24 节 |
| 统一知识行→来源行→任务行锁序、唯一任务指针、期限、重启和迟到结果 | 第 24.2～24.6 节 |
| Java→Python 构建、Python→Java 固定输入／当前 build／批量资格 | 第 25.2～26.2 节 |
| Redis 独立 build、真实查询回读和 READY 条件 | 第 25.4 节 |
| Java 生效快照关键词检索、向量候选双核验和最终正文交付 | 第 26.2～26.4 节 |
| 本机任务不自动清理、旧知识逐条评审、不执行迁移或索引 | 第 22.3、29.1、29.3 节及第 30 节完成声明 |

TASK-024 经独立验收的输入文件 SHA-256 为 `0af5c2457c232822644a25e0685146023929038cb7c146f398d3c1b2fcfc7e81`；本修订从该制品接续，只修改版本匹配文字并追加 B3 Java 共享定义。B1／B2 的业务、安全、隐私、事务和旧记录边界继续有效。

TASK-026 只冻结静态接口、DTO、状态、摘要、锁序、资格与失败边界。它没有实现或验证 Java、Python、MySQL、Redis、Embedding、Vue、小程序、卡片、事件或 checkpoint，没有运行测试、构建、服务、数据库、浏览器或网络，没有修改其他契约、代码、配置、迁移或控制文件，也没有提交、推送或集成。后续实现必须分别提供代码存在、端到端运行和独立验收证据，不能把本文件或某个 `READY` 示例当成真实运行结果。
