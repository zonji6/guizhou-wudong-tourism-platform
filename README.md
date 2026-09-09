# 贵州乌东文旅综合服务平台

面向项目答辩的本机可操作原型。平台以贵州乌东的山水、苗寨生活与茶旅体验为主线，连接资源浏览、乌东向导、行程加入、预约确认、寨里分享和后台管理。

## 2026-09-09 水彩视觉更新

本分支已接入浅色水彩旅行手账主题、开卷主画与溪岸插画，保留点击开卷、五景、单屏探景及自然动效。本机预览端口为 5174。本分支与 `main` 尚未合并；[最新整合设计](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/main/docs/superpowers/specs/2026-09-09-wudong-watercolor-content-design.md)和[下一阶段开发计划](https://github.com/zonji6/guizhou-wudong-tourism-platform/blob/main/docs/superpowers/plans/2026-09-09-wudong-integrated-development.md)保存在 `main`，其中新增业务规则尚待实施。

生成的水彩插画位于 `web/public/images/wudong-art/`，属于概念艺术，不是实景或地图。尚未确认公开使用许可的五景照片及派生图继续由 `.gitignore` 排除，不能用强制暂存绕过；本机现有图片不删除。新克隆需要按 `scripts/prepare-local-media.ps1` 准备允许使用的本地素材，不能把缺图占位理解为完整实景效果。

## 当前可演示体验

- Web 门户：首次点击开卷，依次看见“山、水、寨、茶、人”五联实景；继续下滑经过雾散、叶片与沿溪入寨叙事，进入“乌东向导”和游客服务页面。
- 微信小程序：采用工具优先的五栏结构——“首页｜游乌东｜向导｜寨里｜我的”，不照搬 Web 长卷。
- AI 工作台：保留同一会话的连续追问、真实事件阶段、结构化结果卡片与来源入口；只有后端配置 DeepSeek 后才是实时模型结果。
- 预约闭环：推荐项必须带业务服务返回的真实 `serviceId` 才能进入预约，并由用户在预约页完成二次确认。
- 运营后台：承接服务、订单与静态脱敏 AI 摘要样例的答辩展示；该样例不对应本次会话，也不是 LangSmith Trace。

## 演示边界

| 能力 | 当前口径 |
| --- | --- |
| DeepSeek | 配置有效 API Key 后才调用实时模型；未配置或调用失败时，页面只在用户主动选择后进入演示降级，不能把静态结果称为 DeepSeek 回答。 |
| 知识检索 | 云端 Embedding 服务尚未提供时，界面明确显示“关键词资料（演示）”；不能把关键词匹配称为向量 RAG。 |
| 运行摘要与 LangSmith | 当前后台仅展示静态脱敏摘要样例，不对应本次向导会话，也不是 LangSmith Trace；真实会话级摘要持久化与 LangSmith 接入属于后续“五领域 v2”。 |
| 图片素材 | 地点与公开授权尚未逐一核验的照片仅用于本机答辩演示，不作为可公开发布素材。 |
| 业务数据 | 商家、价格、库存等未核验内容均属于演示数据；不包含真实支付、正式微信登录或生产部署。 |

## 技术结构

- 原生微信小程序：移动游客端。
- Vue 3 + Vite：PC 门户与管理后台。
- Java Spring Boot：业务事实、资源、社区与预约订单服务。
- Python FastAPI + LangGraph：WebSocket 会话、AI 编排与卡片生成；当前 LangGraph 使用 `InMemorySaver`，关键词检索通过 Java API 完成。
- MySQL + Redis：MySQL 承载现有业务数据；Redis 是已声明的本机基础设施和后续持久化预留，当前不承担检索或会话存储。

后端“五领域 v2”仍在计划中，当前答辩应以仓库现有接口和演示边界为准，不把规划项表述为已交付能力。

## 文档入口

- [需求文档](docs/需求文档.md)
- [开发文档](docs/开发文档.md)
- [前端重设计规格](docs/superpowers/specs/2026-09-08-wudong-frontend-redesign-design.md)
- [答辩演示流程](docs/答辩演示流程.md)
- [项目进度](docs/项目进度.md)

## 当前验证状态

前端主链与端侧交互已经实现；其中 TASK-008 的会话与预约收紧改动仍处于 `VERIFYING`。本轮文档收口没有运行测试、构建、服务启动、浏览器验收或端到端验证，因此不能据此宣称项目已通过运行验收。

> API Key 只能保存在本机环境变量中，不得提交到仓库、写入小程序源码或出现在答辩截图中。
