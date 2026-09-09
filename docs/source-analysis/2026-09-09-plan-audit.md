# 贵州乌东五模块与后端计划核对

审阅日期：2026-09-09。范围：两份后续实现计划、对应规格及主目录和 `frontend-redesign` 工作区的代码抽查。本文只记录设计依据、实现差距和后续衔接，不授权本轮全面实施后端。

当前推进顺序按用户最新要求执行：先落地浅色水彩旅行手账视觉，再将既有前后端规划与原始资料综合成新的设计、开发文档。旧规格中深绿主背景、沉浸摄影等视觉约定需要在新文档中明确覆盖；未经本轮改变的产品功能与技术边界继续保留。

## 1. 阅读依据与证据限制

已完整阅读：

- [五模块前端实现计划](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/docs/superpowers/plans/2026-09-09-wudong-five-module-frontend.md)。
- [后端领域与 AI 服务实现计划](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/docs/superpowers/plans/2026-09-09-wudong-backend-domain.md)。
- [五模块前端设计规格](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/docs/superpowers/specs/2026-09-09-wudong-five-module-frontend-design.md)。
- [后端领域与 AI 服务设计规格](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/docs/superpowers/specs/2026-09-09-wudong-backend-domain-design.md)。
- [此前前端重设计规格](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/docs/superpowers/specs/2026-09-08-wudong-frontend-redesign-design.md)及 [现有 v1 API 契约](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/docs/contracts/tourism-api-v1.md)。

静态快照：主目录 HEAD 为 `e83170f`，前端工作区 HEAD 为 `c4ea6d7`；两处均有未提交文件，尤其前端首页视觉正有并行修改。结论适用于本次读到的文件，不代表运行服务采用相同版本。

本次没有启动服务、执行测试或构建，没有查询数据库、外部模型或地图服务。因此“存在代码”“未见代码”“仍为占位”是静态判断，不能转换成运行通过、真实数据有效或独立验收结论。没有读取密钥。

课程 PDF 的对比依据是主代理提供的五页阅读摘要，本审阅没有独立读取 PDF 或 DOCX 正文。DOCX 事实、页码与可使用素材仍以主代理资料分析为准。

## 2. 已确认的产品模块与交互边界

产品名称为“贵州乌东”。五模块是商品、食、住、行、社区；后台是运营端，不是第六个游客模块。`山、水、寨、茶、人` 是视觉叙事维度，不直接变成业务类别或数据库表。

已确认 Web 一级结构为“贵州乌东｜逛乌东｜乌东向导｜预约 / 我的｜运营后台”。“逛乌东”是独立总览，依次展示茶旅引导、五张主题卡、一条演示推荐、继续行程/进入向导。小程序维持原生五栏“首页｜逛乌东｜向导｜社区｜我的”，详情、订单确认等作为二级页；既有 `resources`、`community` 页面路径不必为了改名而迁移。

| 模块 | 已确认的可操作内容 | 本期边界 |
| --- | --- | --- |
| 商品 | 分类/价格筛选、商品详情、数量/取货点/联系人核对、现场自提订单 | 不配送、不支付，不承诺实时库存；茶与伴手礼、苗绣文创均可作为商品内容 |
| 食 | 餐食/茶点/体验筛选、详情、到店时间/人数/联系人、到店订单 | 不做外卖配送、实时餐位或评价系统 |
| 住 | 日期/人数/房型筛选、住宿主体与房型详情、入住日期/人数/联系人、住宿预约 | 不做实时房态或支付；当前字段只有入住日期，不能自行宣传支持多晚库存核算 |
| 行 | 出发地/日期/人数、地图接入位置、路线建议、地点资料、社区路线攻略引用 | 是独立模块；没有已确认的第四类行程订单、门票交易或交通售票 |
| 社区 | 演示身份发布正文/标签，普通动态与路线攻略筛选，按最新浏览 | 路线攻略与行页共享同一内容；不做照片上传、评论、点赞、收藏、举报或审核流程 |

三个交易页面沿用“列表 → 详情 → 独立确认页 → 我的订单”，字段与状态分开。AI 推荐入口也必须经过确认页。我的订单规格包含“全部、商品、食、住、行程”；其中“行程”应与先前单设备、单会话的已加入行程衔接，不意味着新增行程订单表。

后台是工具页面：三类订单处理、商家与目录维护、地点、知识与攻略、AI 脱敏摘要。目录采用新增、查看、编辑、上架/下架或归档，订单引用过的数据不提供常规物理删除。当前没有商家账号或生产账户体系。

未核验的价格、库存、地点、图片、路线和时间持续标为演示；社区路线必须保留“个人经验”语义。地图未配置时显示明确状态和地点资料，不把示意底图、轨迹、时长或班次写成实时导航。

## 3. 后端领域、接口、状态与 AI 权限

### 3.1 技术和事实边界

继续使用 Vue 3/Vite、原生微信小程序、Java 21/Spring Boot/JdbcTemplate/Flyway/MySQL、Python 3.11/FastAPI/LangGraph，以及原方案的 DeepSeek、RAG、Redis、WebSocket、LangSmith 和云端 Embedding。不能因后端计划的简短技术栈列表未逐一列出 Redis 等组件，就把它们视为取消。

Java 是目录、订单、社区与知识的唯一业务写入口。Python 通过内部 API 读取公开业务资料；AI 允许的业务写能力仅限餐食、住宿的待确认提案/待确认单，不能生成商品订单、正式订单、修改状态、写目录或发布社区内容。游客确认与住宿方确认需要使用不同语义，见第 6 节。

### 3.2 领域实体与关系

| 实体 | 主要关系/字段 |
| --- | --- |
| `merchant` | 名称、描述、联系方式、发布状态；供商品、餐食、住宿主体引用 |
| `product` | 商家、名称、描述、价格、取货点、标签、图片、演示与上架状态 |
| `food_item` | 商家、名称、描述、价格、到店时段说明、标签、图片、演示与上架状态 |
| `stay_property`、`room_type` | 商家 → 住宿主体 → 房型；房型含容量、价格、说明、图片、演示与上架状态 |
| `place` | 名称、类别、描述、经纬度、标签、图片、演示与发布状态 |
| `product_order`、`food_order`、`stay_booking` | 三类订单分别引用商品、餐食、房型，保存访客归属及各自业务字段 |
| `community_post` | 访客、正文、标签、服务端生成的演示展示名、内容类型、演示状态、时间；路线攻略追加标题、节点摘要/顺序和地点引用 |
| `knowledge_document` | 继续承接资料与来源；离线资料映射后才形成可检索公开内容 |

### 3.3 已确认接口族

- 游客目录：`GET /api/products`、`/api/products/{id}`、`/api/foods`、`/api/foods/{id}`、`/api/stays`、`/api/stays/{id}`、`/api/places`。
- 社区：`GET /api/posts?type=&tag=` 与 `POST /api/posts`。
- 提交及本人订单：`POST /api/product-orders`、`/api/food-orders`、`/api/stay-bookings`；`GET /api/me/product-orders`、`/api/me/food-orders`、`/api/me/stay-bookings`。
- 后台：`/api/admin/merchants`、`products`、`foods`、`stays`、`room-types`、`places`、`orders/*/status`、`knowledge-documents`。
- 内部：公开目录、地点、知识、路线攻略、受当前访客/线程范围约束的订单查询、餐食/住宿待确认单创建；具体 v2 路径尚待契约落定。

`X-Visitor-Id` 采用本机随机持久化 UUID；订单和社区写接口拒绝缺失或无效标识。我的订单按该标识限定读取，不能按手机号查单或向游客返回其他人的联系人。该标识是本机演示归属凭据，不应在文档中写成正式登录认证或跨设备账户。

### 3.4 订单状态

| 类型 | 游客在确认页提交后的初态 | 已确认状态集合 |
| --- | --- | --- |
| 商品 | 待取货 | 待取货、已取货、已取消 |
| 餐食 | 待到店 | 待到店、已完成、已取消 |
| 住宿 | 待确认 | 待确认、已确认、已完成、已取消 |

状态只能由 Java 按类型白名单推进，不能跨类型或跳跃。旧 `Booking` 的 `PROCESSING` 不是三类订单共有状态。规格已确定状态集合和主要顺序，但尚未逐项列出全部转换邻接、取消起点和终态处理；应在 v2 契约中精确表达，不能仅验证“目标状态属于某集合”。

### 3.5 AI 会话和响应

同一工作台复用 `threadId`；持久化检查点应保存确认条件、摘要、上一版结果和可公开工具结果，重启后恢复失败必须明确提示。固定页面动作直接查询 Java；自然语言负责行程、知识和推荐。卡片只引用实际存在的公开目标及来源标题，无可靠目标 ID 时隐藏详情/确认动作。

卡片类别继续是行程、服务推荐、知识回答、追问、待确认单、错误。事件只显示可理解阶段和脱敏摘要；禁止提示词、思维链、联系人、密钥、内部 URL、SQL、完整工具参数和原始异常。当前已有 v2 前端解析器，不能据此声称 Python 已输出同一版本。

## 4. 当前代码实际差距

| 证据位置 | 静态事实 | 对新文档的影响 |
| --- | --- | --- |
| [主目录 V1 迁移](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/tourism-service/src/main/resources/db/migration/V1__create_tourism_tables.sql#L9) | 仍为 `service_resource`、通用 `booking`、只读社区所需字段；没有三类目录/订单或 `visitor_id` | 五领域模型尚待实施 |
| [前端工作区迁移目录](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/tourism-service/src/main/resources/db/migration/V3__rename_wudong_display_copy.sql) | 比主目录多 V3 中文名修正，未见计划中的 V4 | V4 的基础版本需与真实工作区对齐，不能覆盖 V1/V2 或跳过已有迁移 |
| [公开控制器](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/tourism-service/src/main/java/com/guizhou/wudong/api/PublicTourismController.java#L28) | 仍为 `/api/services`、只读 `/api/posts`、`/api/bookings` 与确认/按 ID 查询；工作区对应控制器仍同形 | 未见领域 API、社区发布、`/api/me/*` 或请求访客校验 |
| [通用订单服务](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/e83170f/tourism-service/src/main/java/com/guizhou/wudong/service/TourismService.java#L86) | 后台只验证目标值属于 `PROCESSING/COMPLETED/CANCELLED`，没有校验当前订单状态；按 ID 查询/确认也没有访客归属 | 旧 v1 契约声称的已确认后运营处理，不能仅凭当前服务代码当作完整状态保障；本轮仅记录，后续领域任务处理 |
| [Web 领域客户端](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/services/tourismApi.js#L5)、[本机身份](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/services/visitorIdentity.js) | 已有三领域目录、订单、`/api/me/*`、访客 UUID 及请求头；失败会抛错 | 是前端接入代码，不是后端已支持的证据；与两处 Java 源码明显不齐 |
| [Web 路由](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/router/hashRoutes.js#L25) | `/resources` 直接进入商品目录，已有三类详情/确认路由；未见总览或行模块路由 | 逛乌东总览和独立行页尚未实现；不能把向导替代“行”当成计划完成 |
| [Web App](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/App.vue#L306) | 导航直接列商品/食/住/寨里/向导；行分类意图转向向导 | 与精简一级结构、逛乌东总览不一致；新设计需明确入口归位 |
| [Web 确认页](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/pages/BookingPage.vue#L121)、[我的订单](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/pages/MyOrdersPage.vue#L12) | 已有三类独立字段及提交后受理结果；成功后让用户点击“查看我的订单”；仅商品/食/住三个筛选 | 前端路径已部分实现，但未自动跳转，未有全部/行程筛选；实际提交成功未核验 |
| [Web 社区](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/pages/CommunityPage.vue) | 只显示动态卡，含演示点赞数字，没有发布框、类型筛选或路线攻略引用 | 不应写成社区投稿与路线共享已完成；静态数字不等于点赞功能 |
| [Web 后台](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/pages/AdminPage.vue#L106) | 目录明确只读，AI 摘要明确静态演示；App 的运营更新提示等待后续任务 | 未形成三类订单处理、目录 CRUD 或真实会话摘要闭环 |
| [小程序配置](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/miniprogram/app.json)、[请求工具](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/miniprogram/utils/api.js)、[预约页](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/miniprogram/pages/booking/booking.js#L197) | 已是原生五栏，但仍显示“游乌东/寨里”；请求无 `X-Visitor-Id`，订单仍用通用 `/api/bookings` | 五模块等价业务、小程序匿名访客隔离、三类订单仍待接入 |
| [LangGraph 构建](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/agent-service/app/graph/builder.py#L42) | 工作区使用缓存图与 `InMemorySaver`；主目录图没有 checkpointer | 有进程内上下文实现，没有重启持久化恢复证据 |
| [检索器](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/agent-service/app/rag/retriever.py#L12)、[Embedding 客户端](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/agent-service/app/rag/embedding_client.py#L14) | 检索是 Java 关键词资料并标 `keyword_demo`；`embed()` 最终仍抛待配置异常 | 云端 Embedding 与 Redis 向量检索未实现；不能称为向量 RAG 已接通 |
| [AI 节点](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/agent-service/app/graph/nodes.py)、[内部工具](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/agent-service/app/tools/tourism_client.py) | 有 DeepSeek 调用路径与旧通用预约创建路径；创建前检查服务存在，未限制服务类别只能食/住，未传访客归属 | 实时模型代码存在不代表本次调用成功；受控领域权限还需升级 |
| [前端 AI 协议](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/c4ea6d7/web/src/protocol/assistantProtocol.js#L226) | 已校验 `cardVersion=2.0`、食/住提案和用户确认动作；仍兼容 legacy | 协议接收能力领先 Python/Java 实现，不应再凭旧计划重新造一套解析器 |

另：主目录 Web 和小程序还保留创建/确认失败后本地演示订单回退。前端工作区 Web 正式三类订单已经采用失败显示错误的方向，因此不能把主目录旧回退直接复制进新业务流程。

## 5. 后续实施顺序与并行文件所有权建议

以下是新开发文档可采用的依赖与文件划分，不创建新任务，不改变现有 Agent 拓扑，也不代表本轮开始实施。

1. **完成当前视觉基线。** 首页、共用色彩/字体/卡片样式首先由当前视觉负责人收束；用户验收浅色水彩旅行手账的氛围与可见交互。
2. **形成新规格和契约。** 汇总旧规格有效项、资料事实/演示项、现有代码差距；锁定命名、领域 API、AI 提案语义和地图降级。先补齐文档事实，不把 PDF 的可选扩展自动并入。
3. **Java 业务基础。** 对齐 V3 基础后新增 V4 领域迁移；完成目录、访客归属、三类状态、社区与内部接口。已执行迁移只向前演进。
4. **前端接入与小程序等价。** Web 复用现有 `tourismApi`、`visitorIdentity` 和三类页面代码；补齐总览、行、社区、后台及小程序。Java 契约稳定后两端可以分别推进。
5. **Python 领域接入。** 在内部契约稳定后增加受控工具、持久化检查点和 v2 卡片输出；再接云 Embedding/向量检索和真实摘要。
6. **真实资料与地图。** 原始资料可提前离线解析、去重和标注来源；入库依赖领域模型，真实地图能力依赖用户提供服务商、访问方式和经核验坐标。未配置时保留明确降级。

| 工作域 | 单一写入所有者应覆盖的文件 | 并行限制 |
| --- | --- | --- |
| 当前视觉 | `web/src/components/home/*`、`web/src/pages/HomePage.vue`、`web/src/style.css` 及获准的视觉资产 | 当前已有未提交变更；其他域不得同时改首页、公共样式 |
| Web 接入 | `web/src/pages/*` 中业务页面、`composables/useCatalogBrowser.js`、`useVisitorOrders.js`、`services/tourismApi.js`、`visitorIdentity.js` | 复用已存在文件，避免再建功能相同的 `useVisitorId.js`；`App.vue` 和 `router/hashRoutes.js` 只由一个集成人员写入 |
| 小程序 | `miniprogram/pages/*`、`utils/api.js`、`app.js`、`app.json`、`custom-tab-bar/*` | 可与 Web 业务页并行；本机访客规则和领域字段必须先共用契约 |
| Java | `tourism-service/**` 和数据库迁移 | 当前 repository/service/controller 集中在少数文件，不宜按“商品/食/住”三代理同时修改；保持一个 Java 写入所有者，无需为并行而额外重构 |
| Python | `agent-service/app/graph/*`、`tools/tourism_client.py`、`contracts.py`、`streaming.py`、RAG 文件 | 与 Java 可在契约稳定后并行；共享的 AI 卡片协议与前端解析器需按版本协调 |
| 契约与集成文档 | `docs/contracts/tourism-api-v2.md`、新设计/开发文档、实际变更对应 README/演示说明 | 一个契约所有者统一字段与状态；其他实施者反馈差异，避免多份冲突定义 |

旧前端计划写的是“等首页重设计拆分完成并合并后执行”，当前页面拆分已在工作区存在，但未据此验证合并完成。后续应以实际选定基线为准，而不是重新执行已存在部分或在不同工作区各自补一套。

## 6. 文档冲突及需要补齐的决定

### 6.1 可按现有授权直接在文档中统一

- **视觉新旧冲突：** 本次浅色水彩旅行手账方向优先；五模块旧深绿色令牌不能继续被写成新视觉的强制要求。当前用户只明确改变视觉，不自动改变 Java/Python/原生小程序边界。
- **行模块独立：** 既有规格已明确独立“行”页；当前导向 AI 的临时入口属于实现差距，不能反向覆盖产品规则。
- **前端文件名差异：** 计划文件名是职责草图。现有 `ResourcesPage`、`ResourceDetailPage`、`BookingPage`、`MyOrdersPage` 可承接对应职责，不为字面符合计划而无必要重命名、重复建文件。
- **工期参考：** 旧三天原型、约十小时进度检查与 PDF 六天课程分别属于不同语境。后者不能改变用户开发周期，进度检查也不能成为删功能或新增测试的依据。

### 6.2 实现前需形成精确契约，当前无需阻塞视觉

- **AI 待确认语义：** 旧后端写的是创建待确认订单；现有 v2 前端是 `proposal`，Python 仍会写 legacy `booking`。需要明确提案是否落库、如何绑定访客/线程、独立确认页如何转换；住宿订单自身“待确认”表示等待住宿方确认，不能与游客尚未提交混为一谈。
- **房型详情：** Web 已请求 `/api/room-types/{id}`，规格只列公开住宿详情。v2 契约必须补充房型详情路径/响应，或明确等价读取方式。
- **社区字段：** 规格查询写 `type`，前端计划写 `postType=ROUTE_GUIDE`；路线标题在规格必需，但发布步骤未始终列出。应统一类型字段、路线标题和节点/地点引用格式。
- **状态邻接和订单归属：** 明确所有允许转换及访客查询条件；`sourceThreadId` 不是独立的访问授权。UUID 归属不等于正式用户认证。
- **地图访问配置：** 前端计划同时出现 SDK/环境变量与“前端不保存密钥”。需区分服务商明确允许公开、受来源限制的客户端标识与服务端秘密；不能把 `VITE_*` 当成秘密存储。等待具体服务商后制定接法。
- **后台访问边界：** 现有代码只有本机演示后台，不代表具备管理身份或生产权限。回环地址拦截也只能区分远端与本机请求，不能证明每个本机请求确实来自 Python。当前不扩大成账号工程；真实部署前必须另行定义管理与内部访问方式。

### 6.3 PDF 参考与已确认范围的差异

| PDF 摘要中的方向 | 与现有规格关系 | 新文档处理建议 |
| --- | --- | --- |
| 衣/非遗商品、商品故事/工艺、餐饮、住宿 | 与商品/食/住方向相容 | 可作目录文案与资料字段来源；示例价格、库存和营业时间仍需核验 |
| 线路详情、时间安排、人数、预订意向，交通/门票/活动组合 | 行页、路线卡相容；线路订单/门票交易超出现有三类订单 | 保留参考，不新增第四类订单，除非用户进一步采纳 |
| 社区照片投稿、点赞、评论 | 与已确认轻量文字社区的排除项冲突 | 不自动实施；将其列为可选后续能力 |
| 后台用户、权限、基础统计 | 目录/订单管理相容；账户权限和统计体系未获本期实现授权 | 写明演示后台现状；生产权限单列后续决定 |
| 订单意向/课程演示交易 | 可用来说明当前演示性质 | 不自动新增支付流程；三类订单继续按已确认字段和状态执行 |

本次收到的 PDF 摘要并没有足够证据确认“模拟支付”是明确需求，不能据此加入支付状态、收银页或退款流程。DOCX 若含更具体要求，主代理应逐条标明“资料建议/用户已确认/暂缓”，而不能只因资料出现就视为授权。

### 6.4 需要用户后续提供的事实资料

地图服务商与访问方式、可用坐标及来源；商品/餐食/住宿/地点台账；价格、取货点、到店时段、容量等核验状态；照片公开范围与来源许可；云 Embedding 具体端点、模型/维度与访问配置。它们影响真实能力，不影响本轮先完成视觉和资料映射文档。

## 7. 交付核对

本审阅只新增本文，未修改源计划、规格、代码、数据库、工作区结构或现有任务。没有执行测试、构建、服务启动或端到端验收。文档中的后续实施和文件所有权只是供主代理整合的建议。

新设计/开发文档交给用户前应便于核对：五模块名称及入口是否一致；三类订单是否都保持独立确认；行是否仍独立且明确地图未配置状态；社区是否仍为本期文字投稿；AI 提案与正式订单是否区分；浅色水彩视觉是否替代旧强制视觉条款；所有“已实现”是否有对应代码且与“已验收”分开。
