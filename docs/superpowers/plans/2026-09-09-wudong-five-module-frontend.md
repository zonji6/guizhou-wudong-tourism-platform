# 贵州乌东五模块前端实现计划

**状态：** 用户已确认（2026-09-09）

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在已批准的沉浸首页与乌东向导之外，实现“逛乌东”总览、商品、食、住、行、社区、三类确认订单、我的订单和运营后台。

**架构：** Web 保留 Vue hash 路由，小程序保留原生页面；两端通过同一领域 API 与本机 `visitorId` 读取数据。当前 `2026-09-08-wudong-frontend-redesign.md` 正在并行实施，本计划只能在其页面拆分完成并合并后执行，不能并发修改 `App.vue`、首页或向导工作台。

**技术栈：** Vue 3、Vite、原生微信小程序、现有 Java REST API、地图服务商 SDK（待用户提供）。

---

## 实施约束

- 不新增或运行自动化测试；每任务以用户可见手工验收与 API 契约核对作为检查。
- 所有未核验资料、地图数据、价格、库存与图片显示演示标识。
- 不实现支付、配送、正式登录、评论、点赞、图片上传或商家端账号。

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `web/src/services/api.js` | 注入 `X-Visitor-Id`、按领域调用 API、统一演示降级错误。 |
| `web/src/composables/useVisitorId.js` | 生成并持久化随机匿名访客标识。 |
| `web/src/pages/ExplorePage.vue` | “逛乌东”五模块总览。 |
| `web/src/pages/CatalogPage.vue` | 商品、食、住通用列表与筛选壳。 |
| `web/src/pages/CatalogDetailPage.vue` | 分类型详情与唯一主操作。 |
| `web/src/pages/TravelPage.vue` | 地图、路线表单、路线攻略引用及服务商降级。 |
| `web/src/pages/CommunityPage.vue` | 文字动态发布、标签筛选、路线攻略卡。 |
| `web/src/pages/OrderConfirmPage.vue`、`MyPage.vue` | 三类确认页与当前访客订单。 |
| `web/src/pages/AdminPage.vue` | 分领域目录维护与三类订单处理。 |
| `miniprogram/utils/api.js`、`miniprogram/app.js` | 访客标识、领域 API 与共享状态。 |
| `miniprogram/pages/*` | 对应主栏与二级页的单列布局。 |

### 任务 1：建立匿名访客与领域 API 边界

**文件：** 修改 `web/src/services/api.js`、`miniprogram/utils/api.js`、`miniprogram/app.js`；创建 `web/src/composables/useVisitorId.js`。

- [ ] 生成 UUID 格式的 `visitorId`，Web 保存于 `localStorage`，小程序保存于 `wx` storage；不得使用手机号、设备硬件 ID 或固定演示编号。
- [ ] 每个订单/社区写请求发送 `X-Visitor-Id`；我的订单只调用 `/api/me/*`，不拼接手机号查询参数。
- [ ] 定义领域调用：商品、食、住、地点、动态、三类订单与后台资源；失败时返回“服务暂不可用”，不把本地假数据冒充为服务成功。
- [ ] 手工检查：刷新浏览器或重启小程序后 `visitorId` 不变；不在界面或控制台显示其值。

### 任务 2：实现 Web 逛乌东、商品、食、住与确认闭环

**文件：** 创建 `web/src/pages/ExplorePage.vue`、`CatalogPage.vue`、`CatalogDetailPage.vue`、`OrderConfirmPage.vue`、`MyPage.vue`；修改已拆分后的路由壳与公共样式。

- [ ] 总览固定为茶旅引导、五张模块卡、一条演示推荐和继续行程入口；首页快捷卡及顶部“逛乌东”复用相同路由目标。
- [ ] `CatalogPage` 按 `product`、`food`、`stay` 映射标题、筛选字段及数据源；卡片只显示对应价格、标签、演示标识和详情入口。
- [ ] 详情页按类型只显示一个主动作：商品数量/取货点后下单，餐食到店时间/人数后下单，住宿日期/人数后预约。
- [ ] 确认页在 POST 前列出资源、字段和联系人；成功后跳转我的订单，不提供支付或跨访客订单查看。
- [ ] 手工验收：商品、餐食、住宿各完成一条“列表→详情→确认→我的订单”路径。

### 任务 3：实现 Web 行与社区的共享路线攻略

**文件：** 创建 `web/src/pages/TravelPage.vue`；替换/修改 `web/src/pages/CommunityPage.vue`。

- [ ] 行页采用“出发地/日期/人数→地图→路线卡→路线攻略引用”顺序；未配置地图服务商时显示未配置状态和地点列表，不渲染伪造地图。
- [ ] 将地图 provider 封装在页面局部适配层，读取环境变量与 Java `places`；不能把密钥写入前端仓库。
- [ ] 社区只允许演示身份发布正文、标签和可选路线攻略节点；发布后读取 API 返回的同一篇动态。
- [ ] 行页引用 `postType=ROUTE_GUIDE` 的社区卡，点击进入原动态；所有卡显示个人经验/演示提示。
- [ ] 手工验收：发布路线攻略后，可在社区筛选与行页引用区看到同一标题。

### 任务 4：实现 Web 我的订单和运营后台

**文件：** 修改 `web/src/pages/MyPage.vue`、`web/src/pages/AdminPage.vue`。

- [ ] 我的订单按商品、食、住、行程筛选，按后端返回的独立状态展示说明。
- [ ] 后台分为订单、商家/目录、地点、知识/攻略与 AI 摘要；商品、食、住只显示其合法状态转换按钮。
- [ ] 目录表单调用后台新增/编辑/上架或归档接口；不提供物理删除按钮。
- [ ] 手工验收：后台将三类订单分别推进到允许的下一状态，游客端刷新后状态一致。

### 任务 5：实现小程序等价入口与二级页

**文件：** 修改 `miniprogram/app.json`、`pages/resources/*`、`pages/detail/*`、`pages/booking/*`、`pages/orders/*`、`pages/community/*`；创建或调整商品、食、住、行、我的页面。

- [ ] “逛乌东”主栏使用五模块卡；商品、食、住、行、社区按原生二级页打开。
- [ ] 列表单列化，筛选使用横滑标签/底部层；详情页在底部固定唯一主操作。
- [ ] 行页按表单、全宽地图、路线卡布局；社区保持文字发布和路线筛选。
- [ ] 我的页面只读取当前匿名访客订单；确认页字段与 Web 同构。
- [ ] 手工验收：五个模块均可从小程序主栏进入并返回，订单确认不绕过字段核对。

### 任务 6：前端收束与用户验收

**文件：** 修改 `README.md`、`docs/答辩演示流程.md`（仅实际行为变化处）。

- [ ] 全局搜索游客文案，确保使用“贵州乌东”，不将“游乌东”“寨里”作为独立一级模块名称。
- [ ] 逐项按前端规格执行手工验收：模块入口、三类订单、社区路线攻略、地图未配置降级、我的订单与后台状态。
- [ ] 仅暂存本计划直接涉及的文件后提交；提交前确认不包含 `.superpowers/`、原始素材或并行会话的文件。
