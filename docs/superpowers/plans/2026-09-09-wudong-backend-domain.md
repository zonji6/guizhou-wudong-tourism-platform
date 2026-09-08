# 贵州乌东后端领域与 AI 服务实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 建立分领域目录、三类订单、轻量社区/路线攻略、匿名访客边界和可恢复的 LangGraph AI 服务。

**架构：** Java 是目录、订单和社区的唯一写入口；Python 通过内部 API 读取资料并只创建餐食/住宿待确认单。地图底图与路径服务由用户提供服务商、访问方式和已核验坐标后接入。

**技术栈：** Java 21、Spring Boot、JdbcTemplate、Flyway、MySQL、Python 3.11、FastAPI、LangGraph。

---

## 实施约束

- 不新增或运行自动化测试；每次变更执行最小编译/启动或用户指定的手工 API 验收。
- 每约十小时报告进度，不以此删除已确认功能。
- 原始资料由用户后续提供，离线解析为可追溯演示初始化数据；不是公开导入 API。

## 文件结构

| 文件 | 职责 |
| --- | --- |
| `tourism-service/src/main/resources/db/migration/V4__five_domain_catalogs.sql` | 分领域目录、三类订单、访客归属及社区路线攻略迁移。 |
| `tourism-service/.../domain/*.java` | 各目录、订单、地点与动态记录。 |
| `tourism-service/.../repository/TourismRepository.java` | 参数化查询与领域写入。 |
| `tourism-service/.../service/TourismService.java` | 状态机、访客边界和业务校验。 |
| `tourism-service/.../api/*Controller.java` | 游客、后台和内部 API。 |
| `docs/contracts/tourism-api-v2.md` | 新领域公开/内部契约。 |
| `agent-service/app/graph/*` | 会话检查点、路由和受控节点。 |
| `agent-service/app/tools/tourism_client.py` | Java 内部 API 客户端。 |
| `agent-service/app/contracts.py`、`streaming.py` | 卡片与脱敏事件契约。 |

### 任务 1：建立 Java 分领域迁移和记录类型

**文件：** 创建 `V4__five_domain_catalogs.sql`；创建/修改领域记录与请求 DTO。

- [ ] 新建商品、餐食、住宿主体/房型、地点、商品订单、餐食订单、住宿预约表；每表保存演示标识、发布/归档状态及必要外键。
- [ ] 为三类订单分别定义枚举状态与请求记录，不复用现有 `Booking` 字段或状态字符串。
- [ ] 为 `community_post` 增加 `visitor_id`、内容类型和路线摘要/节点字段；保留既有历史动态的演示标识。
- [ ] 新增迁移只向前演进，不编辑已执行 V1/V2；对现有数据提供明确的默认演示值。

### 任务 2：实现 Java 目录、地点和后台维护 API

**文件：** 修改/创建 `TourismRepository.java`、`TourismService.java`、`PublicTourismController.java`、`AdminController.java` 及请求 DTO。

- [ ] 游客端实现商品、餐食、住宿/房型、地点的已发布列表与详情查询；筛选字段只覆盖前端规格定义的类别、日期/人数或标签。
- [ ] 后台实现商家和各目录的新增、读取、编辑、上架/下架/归档；所有 SQL 使用绑定参数。
- [ ] 地点响应只返回可公开名称、类别、坐标、描述、标签和演示状态；不返回地图密钥或服务商配置。
- [ ] 更新 `docs/contracts/tourism-api-v2.md`，列明请求字段、响应、访客限制及演示语义。

### 任务 3：实现三类订单与匿名访客边界

**文件：** 修改 Java repository/service/controller；创建各订单请求与响应记录。

- [ ] 从 `X-Visitor-Id` 读取并格式校验匿名标识；订单和社区写接口拒绝缺失或无效标识。
- [ ] 商品订单校验数量和取货点，初始为待取货；餐食订单校验到店时间与人数，初始为待到店；住宿预约校验日期与人数，初始为待确认。
- [ ] `/api/me/*` 仅按当前访客读取订单；后台读取订单时不向游客端回传联系人信息。
- [ ] 状态更新逐类型白名单校验，拒绝跳跃和不属于该订单的状态。

### 任务 4：实现社区、路线攻略与受控内部查询

**文件：** 修改 Java repository/service/controller；修改 `InternalAgentController.java`。

- [ ] 游客动态发布只接受正文、标签、内容类型和路线节点；作者展示名由服务端生成演示身份，不接收联系人或自由身份字段。
- [ ] 支持按 `postType`、标签查询，并让路线攻略返回可复用节点摘要。
- [ ] 内部 API 只向 Python 暴露已发布目录、地点、知识与路线攻略；补充餐食/住宿待确认单创建接口。
- [ ] 维持回环访问限制，拒绝前端访问 `/internal/**`。

### 任务 5：扩展 Python 受控工具、会话检查点与图

**文件：** 修改 `agent-service/app/graph/state.py`、`builder.py`、`nodes.py`、`streaming.py`、`contracts.py`、`tools/tourism_client.py`。

- [ ] 为 `threadId` 配置持久化 LangGraph checkpointer，保存确认条件、上一版结果和可公开的工具摘要；恢复失败时明确返回新会话/降级状态。
- [ ] 固定页面动作直接使用 Java 查询；自然语言节点按行程、知识、商品/餐食/住宿推荐和路线建议路由。
- [ ] 检索节点读取目录、地点、知识和路线攻略，卡片只引用存在的公开 ID 与来源标题。
- [ ] 仅餐食/住宿节点可调用内部待确认单接口；商品节点只产生推荐和详情跳转数据。
- [ ] 流事件只输出用户可理解阶段和脱敏摘要；不输出提示词、思维链、联系人或内部参数。

### 任务 6：地图适配、原始资料准备与收束

**文件：** 创建地图配置适配文件与离线资料映射脚本（在用户交付资料后确定格式）；修改 `README.md`、`docs/项目进度.md`、API 契约。

- [ ] 用户提供地图服务商与访问方式后，将配置仅放入本机环境变量；Java/Python 读取可公开路线结果，前端不保存密钥。
- [ ] 地图不可用时返回明确未配置/演示状态，保持地点、订单、社区与 AI 其他能力可用。
- [ ] 用户提供原始资料后，先离线去重、标记来源/核验状态，再生成演示初始化迁移或受控导入脚本；不自动公开原始文件或未授权图片。
- [ ] 按后端规格进行手工 API 验收：三类订单状态、访客隔离、社区路线攻略、AI 待确认餐食/住宿、地图降级和后台脱敏摘要。
- [ ] 仅暂存本计划直接涉及文件后提交；不得包含 `.superpowers/`、原始数据、密钥或并行前端改动。
