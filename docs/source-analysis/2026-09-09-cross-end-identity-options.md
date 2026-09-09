# Web 与原生微信小程序身份选择

日期：2026-09-09。本文是方案调研，不代表选型批准或接入完成。

## 当前项目基础

本轮静态读取了 `.worktrees/frontend-redesign` 下的当前前端分支代码：Web 的 `visitorIdentity.js` 在浏览器存储随机游客 ID，`tourismApi.js` 通过 `X-Visitor-Id` 请求个人数据；小程序的 `app.js`、`utils/api.js` 仍是本地演示状态，没有对应的统一身份接入。该分支的 Java 工程使用 Spring Boot 3.4.2，尚未声明 Spring Security 或 Redis 会话依赖。上述是代码现状，不是运行验证。

因此，互通需要两端经服务端验证后归属于同一内部用户，不能只共用数据库或让所有人使用固定游客 ID。两端可各持独立会话，访问同一用户的行程、草稿和订单；历史游客数据是否迁移或合并须另行确定。

## 已核实与限制

- 腾讯云官方示例展示：小程序 `wx.login` 取得 code，服务端使用小程序 AppID、AppSecret 调用 `jscode2session`，取得 openid 后建立业务登录态；密钥放服务端。[CloudBase 示例](https://docs.cloudbase.net/recipes/add-auth-wechat-miniprogram)
- 官方 OneID 文档说明可以用 openid/unionid 关联本地用户，并要求获取 UnionID 前将小程序绑定到微信开放平台。[登录流程](https://cloud.tencent.com/document/product/1441/68677)、[准备工作](https://cloud.tencent.com/document/product/1441/60595)。这里只引用流程，不建议因此引入 OneID。
- 开发者工具的跳过域名检查不保证普通真机预览可用；真机开发调试另有开关。[腾讯云常见问题](https://cloud.tencent.com/document/product/1081/49916)、[调试说明](https://cloud.tencent.com/document/product/1137/39909)。工程判断：手机的 localhost 指手机自身，两端必须能访问同一后端；模拟器成功不能证明手机连通。

本次下列微信原始页面均返回不可打开，不能完整核实当前细则：

- [小程序登录](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html)
- [code2Session](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html)
- [UnionID 机制](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/union-id.html)
- [网站应用微信登录](https://developers.weixin.qq.com/doc/oplatform/Website_App/WeChat_Login/Wechat_Login.html)
- [小程序网络](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)

待原文复核：openid 的应用范围、同一开放平台账号下 UnionID 的跨应用一致性及具体返回条件；网站应用审核、微信登录能力与回调域配置条件。不将小程序 AppID 当作网站应用凭据，也不假定 UnionID 必定返回。项目建模建议以内部用户 ID 归属数据、以 `(appid, openid)` 关联微信身份。

## 三种方向：以下均为设计推断

| 方向 | 本机演示价值 | 前提与代价 |
| --- | --- | --- |
| 轻量用户名密码 | Web、小程序登录同一账号后读写同一份数据 | 自建账号和会话；用户需记密码 |
| 一次性配对码 | 将另一端接入已持有的数据身份 | 自建短时、单次票据及确认流程；已有身份或设备丢失后的恢复需另定 |
| 微信生态身份 | 小程序以微信身份登录，可进一步确认 Web 登录 | 需要可用的小程序接入条件、服务端凭据与共同后端；联网及平台条件需核实 |

微信方向还分两条路：官方“网站应用微信扫码登录”使用独立的网站应用接入流程，仅有小程序 AppID 不证明它已可用；“小程序先登录，再确认我们生成的 Web 一次性二维码”是自建配对，可由后端把 Web 会话关联到同一内部用户，不以网站应用审核作为该设计的前提，也不要求依赖 UnionID。后者仍需可用的小程序 AppID、服务端 AppSecret、双方后端连通，以及待设计验证的扫码入口和确认流程。

自建二维码只携带短时随机挑战，不放用户凭据；拟由小程序内的扫码入口读取并明确确认，不能提前承诺普通微信相机扫码即可直接打开。挑战须单次使用并绑定发起的浏览器会话，确认后为浏览器建立独立登录态，不把小程序凭据转交给浏览器。

## 与现有技术栈的衔接建议

无论选择哪种身份入口，建议仍由 Java 管理内部用户与访问权限，MySQL 保存用户归属和业务数据，Redis 可保存短期会话及配对挑战。Spring Session 官方提供 Redis 支持的 REST 会话及请求头会话传递示例，说明小程序接入不意味着必须选 JWT；会话还是 JWT 留待选型，不能照搬文档最新版本到现有 Spring Boot 工程。[Spring Session REST 官方说明](https://docs.spring.io/spring-session/reference/guides/java-rest.html)

初步倾向：若用户已有可用的小程序接入条件，优先讨论“微信身份 + 小程序确认 Web 登录”，减少额外密码；否则轻量账号更便于本机演示及清除客户端缓存后找回数据。纯匿名配对不作为长期找回身份的首选，因为两端凭据都丢失后需要额外恢复机制。以上是针对本项目的设计判断，未锁定方案、表结构或接口。

本次未查看凭据、未登录平台后台、未调用登录接口，主体资格、费用和备案要求均未作确认；未修改业务代码或运行测试。
