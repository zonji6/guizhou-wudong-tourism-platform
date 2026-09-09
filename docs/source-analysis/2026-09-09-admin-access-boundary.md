# 后台访问边界核对

2026-09-09。仅核对指定 `frontend-redesign` 工作树源码及一份官方文档；未读取秘密配置，未发业务请求、测试或构建，不代表运行环境验收。

用户已确认采用 Spring Security＋JWT；游客顶栏右侧使用“我的”，运营后台仅供开发和运营。以上为设计决定，尚未实现。

现有游客导航同时展示“我的订单”和“运营后台”，`App.vue` 第 319–320 行可见；同文件第 305–306 行已区分管理与游客页面布局。`router/hashRoutes.js` 第 7 行已有 `#/admin` 对应路由，但分开布局不等于完成授权。已确认保留同一项目，游客端与后台分别登录，不为入口隔离而增加一套后端；具体登录路由及凭据保存、传输方式仍待细化。

后台现有 `POST /api/admin/knowledge-documents` 和 `PATCH /api/admin/bookings/{id}/status`；控制器直接调用服务，未检查登录身份或角色。服务中的状态白名单属于业务校验。[控制器第 15、23–30 行][admin]、[服务第 47–51、86–92 行][service]。检索该工作树 Java 源码，未发现 `SecurityFilterChain` 或后台角色拦截，不能声称后台已受账号保护。

内部接口实际为 `/internal/agent/**`，未发现 `/api/internal/**` 映射。[内部控制器第 19 行][internal]。现有拦截器只对该路径检查请求来源是否为回环地址，否则返回 403；此处没有内部密钥校验，也不覆盖后台 API。[拦截器第 16、21–28 行][guard]。内部调用与后台人员登录应保持独立边界。

Spring 官方支持通过 `authorizeHttpRequests` 配合路径匹配和角色规则在服务器执行授权；本项目可据此对 `/api/admin/**` 要求管理权限，具体角色标识待选定。因此隐藏导航按钮只是入口调整，后续仍须保护后台 API。[官方说明][spring]

2026-09-10 补充确认：首版仅设“游客／后台管理人员”两档；开发者和运营者各用独立账号，共用一档既定后台业务权限，不共享登录账号，普通自助注册不获得后台权限。不细分开发与运营职责，不增加商户注册、动态权限编辑或独立微服务。后台账号已确认由开发通过初始化脚本创建，不开放后台自助注册，暂不开发人员管理和授权页面；账号凭据不写入公开仓库。该决定不关闭游客自助注册，也不批准密码找回、重置已有密码或每次启动覆盖现有账号；凭据注入方式继续讨论，本轮不实际创建账号；共用业务权限不包含账号授权管理、数据库、服务器、部署或密钥访问权限，也不替代 AI 内部接口的独立访问控制。

2026-09-10 已确认登录隔离：同一浏览器内游客端与后台分别维护登录状态。游客登录不自动登录后台，后台须以具有管理权限的账号单独登录；服务端不能只凭游客端登录成功就放行管理接口。两边分别续期、撤销和退出，互不影响，游客登录最长 7 天、后台登录最长 8 小时，刷新不重置各自绝对截止点。此处隔离登录会话，不新增两套用户库、两个项目或小程序后台；具体实现见后续认证设计。

[admin]: D:/AI_Project/Codex_project/乌冬/.worktrees/frontend-redesign/tourism-service/src/main/java/com/guizhou/wudong/api/AdminController.java:15
[service]: D:/AI_Project/Codex_project/乌冬/.worktrees/frontend-redesign/tourism-service/src/main/java/com/guizhou/wudong/service/TourismService.java:47
[internal]: D:/AI_Project/Codex_project/乌冬/.worktrees/frontend-redesign/tourism-service/src/main/java/com/guizhou/wudong/api/InternalAgentController.java:19
[guard]: D:/AI_Project/Codex_project/乌冬/.worktrees/frontend-redesign/tourism-service/src/main/java/com/guizhou/wudong/config/InternalAgentAccessConfiguration.java:16
[spring]: https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html#security-matchers
