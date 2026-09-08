# 贵州乌冬文旅“衣食住行”综合服务平台设计规格

**状态：** 待用户书面审阅
**目标：** 在三天内交付用于项目答辩的本机可操作原型。
**产品范围：** 贵州乌冬文旅的衣食住行、社区分享、平台管理，以及可追踪的 AI 文旅助手。

## 1. 产品目标与边界

平台服务于游客与运营人员，而不是复刻阿里商旅的差旅业务。阿里商旅案例只作为技术方法参考：混合栈分层、快慢车道、状态化多智能体、RAG 与全链路观测。

答辩时，游客应能在 PC 网页端或微信小程序完成“浏览贵州乌冬资源 → 向 AI 助手提出需求 → 获得带来源的推荐或行程 → 提交预约 → 查看订单”的闭环；运营人员应能在管理后台管理资源、内容和订单，并查看 AI 执行的脱敏轨迹。

本期不包含真实支付、微信正式登录、真实库存扣减、评价体系、即时聊天、地图导航、对象存储、生产级权限体系、生产级多实例部署或真实游客上线。

## 2. 终端与功能范围

| 终端 | 定位 | 本期能力 |
| --- | --- | --- |
| 原生微信小程序 | 移动游客端 | 首页、衣食住行资源、资源详情、社区、AI 助手、预约、我的订单 |
| Vue PC 网页端 | 桌面游客端 | 首页、资源浏览、攻略/社区、AI 助手、预约入口 |
| Vue Web 管理后台 | 运营端 | 商家和服务、社区内容、攻略知识、订单、AI 运行轨迹 |

PC 网页端与小程序共享资源、订单、知识和 AI API，但各自适配页面布局；管理后台不与游客端混用。

## 3. 技术选型

| 层级 | 已确定选型 | 责任 |
| --- | --- | --- |
| 小程序 | 原生微信小程序 | 移动游客体验与答辩扫码/模拟器展示 |
| PC 与后台 | Vue 3 + Vite | PC 文旅门户与管理后台，使用同一工程、不同路由与布局 |
| 确定性业务服务 | Java Spring Boot | 资源、社区、攻略、预约订单、后台业务规则与 MySQL 写入 |
| AI 服务 | Python + FastAPI | LangGraph、DeepSeek、RAG、WebSocket、LangSmith 埋点 |
| Agent 编排 | LangGraph `StateGraph` | 主控、条件路由、专业节点、状态持久化与错误回退 |
| 生成模型 | DeepSeek API | 意图理解、行程生成、知识回答、结构化工具参数 |
| 向量化 | 阿里云百炼 `text-embedding-v4` | 贵州乌冬知识文本的云端 Embedding；后续由用户创建 Key |
| 业务数据 | MySQL | 商家、服务、社区、订单、知识原文与索引状态的唯一事实来源 |
| AI 检索与短时状态 | Redis Stack | 向量索引、RAG 检索、短时会话与任务状态；不保存业务事实 |
| 实时传输 | WebSocket | 小程序和网页接收 Agent 节点进度与最终卡片 |
| 开发期观测 | LangSmith | 追踪模型、RAG、工具和 LangGraph 节点；不向管理后台暴露完整输入输出 |

## 4. 服务边界与数据流

```text
原生微信小程序 / Vue PC 门户 / Vue 管理后台
                  |                 |
                  |------ REST -----|----> Java Spring Boot
                  |                         资源、社区、订单、后台
                  |                         MySQL 唯一业务写入口
                  |
                  |---- WebSocket --------> Python FastAPI
                                            LangGraph、DeepSeek、RAG
                                            通过内部 HTTP 调用 Java 工具 API
                                               |              |
                                      Redis Stack       LangSmith（开发期）
                                      RAG/短时状态      Trace
```

Python 服务不得直接写入订单、商家、社区等业务表。Agent 只能调用 Java 提供的受控工具：查询资源、查询知识原文、创建待确认预约、查询订单。正式订单仅在用户通过小程序或 PC 确认后由 Java 服务创建。

## 5. LangGraph 设计

### 5.1 状态

每次会话使用稳定的 `thread_id`。图状态至少包含：用户输入、消息摘要、提取的日期/人数/预算/偏好、当前意图、路由结果、检索结果、工具结果、待确认预约、最终卡片、任务事件和错误信息。

### 5.2 节点

| 节点 | 职责 | 输出 |
| --- | --- | --- |
| 快车道路由 | 处理页面按钮和固定动作 | 指定工具或固定业务路径 |
| 意图识别/主控 | 处理自由文本，补全条件，决定下一节点 | `route`、结构化条件、追问或下一步 |
| 行程规划 Agent | 基于资源工具生成日程和服务推荐 | 行程与推荐卡片 JSON |
| 知识问答 Agent | 检索贵州乌冬知识并基于来源回答 | 答案、来源列表、置信说明 |
| 预约协助 Agent | 基于已选服务生成待确认预约 | 待确认预约 JSON；不创建正式订单 |
| 错误回退 | 处理模型、检索或工具失败 | 可理解的降级提示与可继续操作 |

快车道用于“推荐住宿”“查看我的订单”等明确页面动作，不调用大模型。慢车道用于“帮一家三口安排两天一夜苗寨体验”等自然语言需求，经过主控和专业节点。模型返回必须符合 JSON 契约；后端校验后才转换为小程序或网页卡片。

### 5.3 可见进度

客户端只展示任务状态，例如“正在理解需求”“正在检索贵州乌冬资料”“正在匹配民宿与体验”“方案已生成”。不展示模型的原始思维链。后台仅展示节点名称、工具摘要、耗时、状态、知识来源和最终结果摘要。

## 6. RAG 知识库设计

MySQL 中的 `knowledge_document` 保存原始攻略、村寨文化、茶旅、民宿、餐饮、文创、交通与活动资料；`knowledge_chunk` 保存切块文本、文档来源、标签、版本和索引状态。

知识发布流程：

```text
管理后台发布知识
→ Java 写入 MySQL
→ 通知 Python 索引服务
→ 文本按约 500 个中文字符切块，约 80 个字符重叠
→ 百炼 text-embedding-v4 生成 1024 维向量
→ Redis Stack 写入向量、来源、标签和版本
```

检索流程：用户问题向量化后，从 Redis Stack 使用标签过滤检索 Top 4 块，再由 DeepSeek 根据检索内容组织回答。回答卡片必须携带来源标题；未检索到可信资料时明确说明，不能编造事实。

本期不使用重排序模型、图片/视频多模态检索或托管知识库服务。

## 7. API 与事件契约

### 7.1 Java REST API

- 游客资源：分类、列表、详情、套餐查询。
- 社区与攻略：列表、详情、后台增删改。
- 订单：提交用户确认后的预约、订单查询、后台状态更新。
- Agent 工具：供 Python 内部调用的资源查询、套餐匹配、待确认预约和订单查询接口。

### 7.2 Agent WebSocket

客户端向 Python AI 服务发送 `thread_id`、用户文本或页面动作、可选的当前资源标识。服务端依次发送：`task_started`、`node_started`、`tool_finished`、`card_ready`、`completed` 或 `failed`。

最终 `card_ready` 只允许下列可渲染卡片：行程、资源推荐、知识回答、追问、待确认预约、错误提示。卡片不包含任意 HTML 或未校验模型字段。

## 8. 观测与隐私

LangSmith 项目用于开发期完整 Trace；启用条件为配置 `LANGSMITH_TRACING`、`LANGSMITH_API_KEY` 和项目名称。使用 DeepSeek 的调用、RAG 检索和 Java 工具均需成为同一根 Trace 的子节点。

LangSmith 只在本机开发/答辩环境启用。联系人、手机号等个人信息不得发送到 LangSmith；后台轨迹页使用脱敏后的摘要数据。DeepSeek Key、百炼 Key 与 LangSmith Key 均通过本地环境变量配置，绝不提交到仓库或小程序代码。

## 9. 本机演示运行方式

Docker Compose 仅运行 MySQL 与 Redis Stack。Java Spring Boot、Python FastAPI 和 Vue 应用在本机通过 PowerShell 启动脚本分别运行，以便快速定位启动或联调错误。微信开发者工具在关闭本地调试域名校验后连接本机 API；不配置公网域名、HTTPS、反向代理、容器化应用镜像或真机访问。

## 10. 三天交付与验收

| 时间 | 目标 |
| --- | --- |
| 第 1 天 | 基础数据、MySQL/Redis Stack、Java 资源与订单 API、Python LangGraph 主链路、PC 门户骨架 |
| 第 2 天 | 原生小程序、Vue 管理后台、RAG 索引与问答、预约确认闭环、WebSocket 进度 |
| 第 3 天 | LangSmith 与本地轨迹页、社区、三端联调、答辩文案和主链路冒烟验证 |

验收场景：

1. PC 和小程序均能浏览同一批贵州乌冬资源与社区内容。
2. 自由文本需求经过 LangGraph，生成带日程、资源推荐和知识来源的行程卡片。
3. 点击固定推荐操作走快车道并直接返回资源卡片。
4. 用户确认后创建预约；Java 写入 MySQL；后台能查看并更新订单状态。
5. 后台能查看与该会话对应的脱敏 Agent 节点、工具、RAG 来源、耗时和最终状态。
6. RAG、模型或工具发生故障时，客户端收到明确降级提示，且既有订单和资源数据不受影响。

自动验证只保留两项：Agent 结构化卡片契约与确认预约后订单可查询。其余按上述六个场景进行人工冒烟验证。

## 11. 未决外部依赖

- 用户后续创建阿里云百炼 API Key，用于 `text-embedding-v4`。
- 用户后续创建 LangSmith API Key，用于开发期追踪。
- DeepSeek API Key 已由用户持有，实施时仅在本机环境变量中配置。

## 12. 技术参考

- LangGraph Graph API：<https://langchain-ai.github.io/langgraph/how-tos/state-reducers/>
- DeepSeek OpenAI 兼容 API：<https://api-docs.deepseek.com/>
- 阿里云百炼 text-embedding-v4：<https://help.aliyun.com/zh/model-studio/text-embedding-v4>
- Redis Stack 向量检索：<https://redis.io/docs/latest/develop/ai/search-and-query/vectors/>
- LangSmith 追踪 LangGraph：<https://docs.langchain.com/langsmith/trace-with-langgraph>
