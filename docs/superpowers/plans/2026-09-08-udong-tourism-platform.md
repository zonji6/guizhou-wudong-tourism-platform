# 贵州乌冬文旅综合服务平台实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在三天内交付本机可运行、用于答辩的贵州乌冬文旅“衣食住行 + 社区 + 管理后台 + 可追踪 AI 助手”三端原型。

**架构：** 原生微信小程序和 Vue PC/后台调用 Java Spring Boot 的确定性文旅服务；Python FastAPI 承载 LangGraph、DeepSeek、RAG 和 WebSocket。Java 是 MySQL 的唯一业务写入口；Python 通过 Java 内部 API 查询资源与创建待确认预约，Redis Stack 只保存向量检索与短时状态。

**技术栈：** 原生微信小程序、Vue 3/Vite、Java 21/Spring Boot 3、Python 3.11/FastAPI、LangGraph、DeepSeek、百炼 text-embedding-v4、MySQL 8、Redis Stack、WebSocket、LangSmith、Docker Compose。

---

## 预置条件与文件结构

执行前先确认 Java 21、Maven、Node.js 20+、Python 3.11+、Docker Desktop 和微信开发者工具。Key 仅写入本地 `.env`。

~~~text
乌冬/
├─ infra/docker-compose.yml
├─ scripts/start-demo.ps1
├─ tourism-service/       # Java 业务服务
├─ agent-service/         # Python AI 服务
├─ web/                   # Vue PC 门户与后台
└─ miniprogram/           # 原生微信小程序
~~~

### 任务 1：初始化版本控制和本机依赖

**文件：**
- 创建：`.gitignore`
- 创建：`.env.example`
- 创建：`infra/docker-compose.yml`
- 创建：`scripts/verify-infra.ps1`

- [ ] **步骤 1：确认运行时**

运行：

~~~powershell
java -version
mvn -version
node -v
python --version
docker version
~~~

预期：Java 21、Node 20+、Python 3.11+，Docker 客户端和服务端均可用。任一项缺失时停止并修复环境。

- [ ] **步骤 2：初始化 Git 和忽略规则**

运行：

~~~powershell
git init
~~~

创建 `.gitignore`：

~~~gitignore
.env
**/.env
**/target/
**/node_modules/
**/dist/
**/.venv/
**/__pycache__/
*.pyc
~~~

创建 `.env.example`：

~~~dotenv
DEEPSEEK_API_KEY=
DASHSCOPE_API_KEY=
LANGSMITH_TRACING=false
LANGSMITH_API_KEY=
LANGSMITH_PROJECT=udong-tourism-demo
JAVA_SERVICE_BASE_URL=http://127.0.0.1:8080
~~~

- [ ] **步骤 3：添加最小依赖容器和失败验证**

创建 `infra/docker-compose.yml`：

~~~yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: udong_tourism
    ports: ["3306:3306"]
  redis:
    image: redis/redis-stack-server:latest
    ports: ["6379:6379"]
~~~

创建 `scripts/verify-infra.ps1`：

~~~powershell
docker compose -f infra/docker-compose.yml exec -T mysql mysqladmin ping -h localhost -uroot -proot
if ($LASTEXITCODE -ne 0) { exit 1 }
$reply = docker compose -f infra/docker-compose.yml exec -T redis redis-cli ping
if ($LASTEXITCODE -ne 0 -or $reply[-1] -ne 'PONG') { exit 1 }
~~~

运行：

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-infra.ps1
~~~

预期：容器未启动时 FAIL。

- [ ] **步骤 4：启动并验证依赖**

运行：

~~~powershell
docker compose -f infra/docker-compose.yml up -d
powershell -ExecutionPolicy Bypass -File scripts/verify-infra.ps1
~~~

预期：退出码为 0，MySQL 返回 `mysqld is alive`，Redis 返回 `PONG`。

- [ ] **步骤 5：Commit**

~~~powershell
git add .gitignore .env.example infra scripts
git commit -m "chore: 初始化乌东文旅本地依赖"
~~~

### 任务 2：建立 Java 文旅数据与订单闭环

**文件：**
- 创建：`tourism-service/pom.xml`
- 创建：`tourism-service/src/main/resources/application.yml`
- 创建：`tourism-service/src/main/resources/db/migration/V1__tourism_schema.sql`
- 创建：`tourism-service/src/main/java/com/udong/tourism/booking/BookingService.java`
- 创建：`tourism-service/src/main/java/com/udong/tourism/booking/BookingController.java`
- 创建：`tourism-service/src/test/java/com/udong/tourism/booking/BookingServiceTest.java`

- [ ] **步骤 1：编写失败的确认预约测试**

~~~java
@Test
void confirmedBookingIsPersistedAndQueryable() {
    var request = new CreateBookingRequest(1L, "2026-10-01", 3, "演示游客", "13800000000");
    var created = bookingService.createConfirmed(request);
    assertThat(created.status()).isEqualTo("CONFIRMED");
    assertThat(bookingService.findById(created.id()).serviceId()).isEqualTo(1L);
}
~~~

- [ ] **步骤 2：运行测试确认失败**

~~~powershell
cd tourism-service
mvn test -Dtest=BookingServiceTest
~~~

预期：FAIL，因为 DTO、服务和迁移尚未创建。

- [ ] **步骤 3：实现最少领域表、初始数据与 API**

迁移必须创建 `merchant`、`service_item`、`community_post`、`knowledge_document`、`knowledge_chunk`、`booking`、`agent_run`、`agent_event`。插入至少六项贵州乌冬演示服务：民宿、酸汤鱼、苗岭茶旅、米酒体验、香包手作、两日游套餐。

实现：

~~~java
public record BookingView(Long id, Long serviceId, String status, Integer people, String visitDate) {}
public record CreateBookingRequest(Long serviceId, String visitDate, Integer people,
                                   String contactName, String contactPhone) {}
~~~

`POST /api/bookings` 写入 `CONFIRMED`；`GET /api/bookings/{id}` 查询。拒绝不存在的服务和少于一人的预约。

- [ ] **步骤 4：运行测试验证通过**

~~~powershell
mvn test -Dtest=BookingServiceTest
~~~

预期：PASS。

- [ ] **步骤 5：Commit**

~~~powershell
git add tourism-service
git commit -m "feat: 增加乌东资源与预约订单服务"
~~~

### 任务 3：提供 Java 资源、内容和受控 Agent 工具

**文件：**
- 创建：`tourism-service/src/main/java/com/udong/tourism/catalog/ServiceItemController.java`
- 创建：`tourism-service/src/main/java/com/udong/tourism/content/ContentController.java`
- 创建：`tourism-service/src/main/java/com/udong/tourism/agent/InternalAgentController.java`
- 创建：`tourism-service/src/test/java/com/udong/tourism/agent/InternalAgentControllerTest.java`

- [ ] **步骤 1：编写失败的工具边界测试**

~~~java
@Test
void agentCanCreateOnlyPendingConfirmation() {
    var result = internalAgent.createPending(new PendingBookingRequest(1L, "2026-10-01", 2));
    assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
}
~~~

- [ ] **步骤 2：运行测试确认失败**

~~~powershell
mvn test -Dtest=InternalAgentControllerTest
~~~

预期：FAIL，因为内部工具接口不存在。

- [ ] **步骤 3：实现 REST 与内部工具契约**

~~~text
GET  /api/services?category=stay|food|travel|culture
GET  /api/services/{id}
GET  /api/posts
GET  /api/knowledge-documents
POST /api/admin/knowledge-documents
GET  /internal/agent/services/search?keywords=&tags=
POST /internal/agent/pending-bookings
GET  /internal/agent/bookings/{id}
~~~

`pending-bookings` 仅写入 `PENDING_CONFIRMATION`；公开的 `POST /api/bookings/{id}/confirm` 只允许将该状态变更为 `CONFIRMED`。任务 2 的 `POST /api/bookings` 代表用户已经在页面确认，因此可直接创建 `CONFIRMED`。知识发布后调用 Python `POST /internal/index/knowledge/{documentId}`；索引失败时保存 `index_status=FAILED`。

- [ ] **步骤 4：运行测试验证通过并提交**

~~~powershell
mvn test -Dtest=InternalAgentControllerTest
git add tourism-service
git commit -m "feat: 提供内容和受控 Agent 工具接口"
~~~

预期：测试 PASS，Agent 接口不能创建正式订单。

### 任务 4：定义 Python Agent 状态与安全卡片

**文件：**
- 创建：`agent-service/pyproject.toml`
- 创建：`agent-service/app/config.py`
- 创建：`agent-service/app/contracts.py`
- 创建：`agent-service/app/graph/state.py`
- 创建：`agent-service/tests/test_card_contracts.py`

- [ ] **步骤 1：编写失败的卡片契约测试**

~~~python
def test_itinerary_card_requires_day_items_and_sources() -> None:
    card = ItineraryCard(
        type="itinerary", title="贵州乌冬两日游",
        days=[{"day": 1, "items": ["苗寨漫步"]}],
        sources=[{"title": "贵州乌冬茶旅攻略", "document_id": 1}],
    )
    assert card.type == "itinerary"

def test_card_rejects_unknown_type() -> None:
    with pytest.raises(ValidationError):
        AgentCard(type="raw_html", title="x")
~~~

- [ ] **步骤 2：运行测试确认失败**

~~~powershell
cd agent-service
python -m pytest tests/test_card_contracts.py -v
~~~

预期：FAIL，因为类型尚不存在。

- [ ] **步骤 3：实现状态与卡片白名单**

~~~python
class AgentState(TypedDict, total=False):
    thread_id: str
    user_text: str
    page_action: str | None
    intent: str
    extracted: dict[str, Any]
    retrieved_chunks: list[dict[str, Any]]
    tool_results: list[dict[str, Any]]
    card: dict[str, Any]
    events: list[dict[str, Any]]
    error: str | None
~~~

只允许 `itinerary`、`service_recommendation`、`knowledge_answer`、`clarifying_question`、`pending_booking` 和 `error`。配置仅从环境变量读取 Key。

- [ ] **步骤 4：运行测试验证通过并提交**

~~~powershell
python -m pytest tests/test_card_contracts.py -v
git add agent-service
git commit -m "feat: 增加 Agent 状态与安全卡片契约"
~~~

预期：PASS。

### 任务 5：实现 LangGraph 快慢车道与 Java 工具客户端

**文件：**
- 创建：`agent-service/app/tools/tourism_client.py`
- 创建：`agent-service/app/graph/nodes.py`
- 创建：`agent-service/app/graph/builder.py`
- 创建：`agent-service/tests/test_graph_routing.py`

- [ ] **步骤 1：编写失败的路由测试**

~~~python
def test_known_page_action_uses_fast_lane() -> None:
    assert route_request({"page_action": "recommend_stay", "user_text": ""}) == "service_search"

def test_free_text_uses_intent_node() -> None:
    assert route_request({"page_action": None, "user_text": "三个人国庆来贵州乌冬两天"}) == "intent"
~~~

- [ ] **步骤 2：运行测试确认失败**

~~~powershell
python -m pytest tests/test_graph_routing.py -v
~~~

预期：FAIL，因为 `route_request` 不存在。

- [ ] **步骤 3：实现固定状态图**

~~~text
START → route
route ──页面动作──→ service_search → compose_card → END
route ──自由文本──→ intent
intent ──信息不足──→ clarify → END
intent ──行程──────→ retrieve → itinerary → compose_card → END
intent ──知识──────→ retrieve → answer → compose_card → END
intent ──预约──────→ service_search → pending_booking → compose_card → END
~~~

`tourism_client.py` 通过 HTTP 调用 Java 内部接口。网络失败必须生成 `error` 卡片，不能由模型编造商家、价格或订单号。

- [ ] **步骤 4：运行测试验证通过并提交**

~~~powershell
python -m pytest tests/test_graph_routing.py -v
git add agent-service
git commit -m "feat: 实现文旅助手快慢车道和工具边界"
~~~

预期：PASS。

### 任务 6：实现 RAG 索引、检索和来源约束

**文件：**
- 创建：`agent-service/app/rag/embedding_client.py`
- 创建：`agent-service/app/rag/indexer.py`
- 创建：`agent-service/app/rag/retriever.py`
- 创建：`agent-service/tests/test_rag.py`

- [ ] **步骤 1：编写失败的 RAG 测试**

~~~python
def test_chunker_keeps_document_source_on_every_chunk() -> None:
    chunks = chunk_document(document_id=7, title="苗岭茶旅", text="茶" * 900)
    assert len(chunks) == 2
    assert {chunk.document_id for chunk in chunks} == {7}

def test_answer_without_retrieval_is_an_error_card() -> None:
    assert compose_grounded_answer([], "贵州乌冬有什么茶旅体验").type == "error"
~~~

- [ ] **步骤 2：运行测试确认失败**

~~~powershell
python -m pytest tests/test_rag.py -v
~~~

预期：FAIL，因为索引模块不存在。

- [ ] **步骤 3：实现受控索引和检索**

每块约 500 个中文字符，重叠约 80 字符；百炼 `text-embedding-v4` 固定 1024 维。Redis Stack 索引名为 `idx:udong:knowledge`，存储 `vector`、`document_id`、`title`、`content`、`category`、`tags`、`version`。

检索只返回带来源的 Top 4 块。DeepSeek 回答必须引用检索来源；无结果时返回“当前知识库未收录相关资料”，而非编造事实。

- [ ] **步骤 4：运行测试验证通过并验证 Redis 索引**

~~~powershell
python -m pytest tests/test_rag.py -v
~~~

预期：PASS。配置百炼 Key 后索引一篇示例攻略，Redis 查询结果含同一 `document_id`。

- [ ] **步骤 5：Commit**

~~~powershell
git add agent-service
git commit -m "feat: 接入乌东知识库 RAG"
~~~

### 任务 7：接入 DeepSeek、WebSocket 和 LangSmith

**文件：**
- 创建：`agent-service/app/main.py`
- 创建：`agent-service/app/llm/deepseek_client.py`
- 创建：`agent-service/app/streaming.py`
- 创建：`agent-service/app/observability.py`
- 创建：`agent-service/tests/test_websocket_events.py`

- [ ] **步骤 1：编写失败的事件序列测试**

~~~python
def test_agent_stream_emits_progress_before_card(test_client) -> None:
    events = list(test_client.stream_agent(thread_id="t-1", user_text="推荐民宿"))
    assert events[0]["type"] == "task_started"
    assert any(event["type"] == "node_started" for event in events)
    assert events[-1]["type"] in {"completed", "failed"}
~~~

- [ ] **步骤 2：运行测试确认失败**

~~~powershell
python -m pytest tests/test_websocket_events.py -v
~~~

预期：FAIL，因为 WebSocket 端点不存在。

- [ ] **步骤 3：实现流式事件和观测**

实现 `WS /ws/assistant`，事件顺序为 `task_started`、`node_started`、`tool_finished`、`card_ready`、`completed`，失败时为 `failed` 加安全错误卡片。

DeepSeek 使用 OpenAI 兼容客户端和 JSON 输出；模型结果必须先经过 Pydantic 校验。仅当 `LANGSMITH_TRACING=true` 且 Key 存在时启用 LangSmith。联系人、手机号在任何 LangSmith 埋点前删除或掩码。

- [ ] **步骤 4：运行测试验证通过并提交**

~~~powershell
python -m pytest tests/test_websocket_events.py -v
git add agent-service
git commit -m "feat: 增加助手实时进度和开发期追踪"
~~~

预期：PASS；配置 LangSmith Key 后一次请求有路由、检索、模型与工具的子 Trace。

### 任务 8：实现 Vue PC 门户与管理后台

**文件：**
- 创建：`web/package.json`
- 创建：`web/src/router/index.ts`
- 创建：`web/src/services/tourismApi.ts`
- 创建：`web/src/services/agentSocket.ts`
- 创建：`web/src/views/PublicHomeView.vue`
- 创建：`web/src/views/AssistantView.vue`
- 创建：`web/src/views/admin/ServiceAdminView.vue`
- 创建：`web/src/views/admin/BookingAdminView.vue`
- 创建：`web/src/views/admin/AgentRunView.vue`
- 创建：`web/src/services/agentSocket.spec.ts`

- [ ] **步骤 1：编写失败的 Agent 卡片映射测试**

~~~ts
it("maps card_ready to a renderable card", () => {
  expect(toAgentCard({ type: "card_ready", card: { type: "itinerary", title: "两日游" } }))
    .toEqual({ type: "itinerary", title: "两日游" })
})
~~~

- [ ] **步骤 2：运行测试确认失败**

~~~powershell
cd web
npm run test -- agentSocket.spec.ts
~~~

预期：FAIL，因为 Vue 工程和映射函数不存在。

- [ ] **步骤 3：实现公共和后台页面**

公共路由为 `/`、`/services`、`/services/:id`、`/community`、`/assistant`、`/bookings/:id`；后台路由为 `/admin`、`/admin/services`、`/admin/bookings`、`/admin/agent-runs`。

助手页只渲染固定事件和六种白名单卡片。后台轨迹页显示脱敏节点、工具摘要、耗时、来源和状态，不显示 Prompt、模型思维链或联系方式。

- [ ] **步骤 4：运行测试、构建并提交**

~~~powershell
npm run test -- agentSocket.spec.ts
npm run build
git add web
git commit -m "feat: 增加 PC 文旅门户和运营后台"
~~~

预期：测试 PASS、构建成功，四类页面无空白路由。

### 任务 9：实现原生微信小程序

**文件：**
- 创建：`miniprogram/app.json`
- 创建：`miniprogram/utils/config.ts`
- 创建：`miniprogram/utils/agentSocket.ts`
- 创建：`miniprogram/pages/home/index.*`
- 创建：`miniprogram/pages/services/index.*`
- 创建：`miniprogram/pages/service-detail/index.*`
- 创建：`miniprogram/pages/assistant/index.*`
- 创建：`miniprogram/pages/booking/index.*`
- 创建：`miniprogram/pages/orders/index.*`
- 创建：`miniprogram/pages/community/index.*`

- [ ] **步骤 1：先实现和验证纯事件文本函数**

~~~ts
export function progressText(type: string, node?: string): string {
  if (type === "node_started" && node === "retrieve") return "正在检索贵州乌冬资料"
  if (type === "card_ready") return "方案已生成"
  return "正在处理需求"
}
~~~

在微信开发者工具控制台运行 `progressText('node_started', 'retrieve')`，预期为“正在检索贵州乌冬资料”。

- [ ] **步骤 2：实现游客主链路**

资源页调用 Java 服务；详情页带入预约页；预约页必须展示服务、日期、人数和“确认预约”按钮，确认后调用 Java 订单 API。助手页连接 Python WebSocket，渲染进度和白名单卡片。社区页调用 Java 内容 API。

- [ ] **步骤 3：在微信开发者工具执行人工验收**

关闭本地调试域名校验，完成：浏览民宿 → 输入“国庆三人两日游” → 查看行程与来源 → 选择服务 → 确认预约 → 在“我的订单”看到状态。

预期：无原始 JSON、无模型思维链、无 API Key 暴露；依赖不可用时显示明确降级提示。

- [ ] **步骤 4：Commit**

~~~powershell
git add miniprogram
git commit -m "feat: 增加乌东文旅微信小程序"
~~~

### 任务 10：完成本机启动、跨服务冒烟和答辩流程

**文件：**
- 创建：`scripts/start-demo.ps1`
- 创建：`scripts/smoke-demo.ps1`
- 创建：`docs/答辩演示流程.md`

- [ ] **步骤 1：编写失败的服务冒烟脚本**

~~~powershell
$services = Invoke-RestMethod http://127.0.0.1:8080/api/services
if ($services.Count -lt 1) { throw "演示服务数据为空" }
~~~

- [ ] **步骤 2：运行脚本确认失败**

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/smoke-demo.ps1
~~~

预期：Java 未启动时 FAIL，并显示连接错误。

- [ ] **步骤 3：实现一键启动和五项自动冒烟**

`start-demo.ps1` 依次启动 Docker 依赖、Java、Python、Vue，并输出微信开发者工具本机 API 地址。

`smoke-demo.ps1` 必须验证：资源列表非空；Python 健康检查 200；快车道返回 `service_recommendation`；创建并确认预约后订单为 `CONFIRMED`；后台查询得到对应脱敏 `agent_run`。

- [ ] **步骤 4：运行全部自动检查**

~~~powershell
cd tourism-service; mvn test
cd ..\agent-service; python -m pytest -v
cd ..\web; npm run test; npm run build
cd ..; powershell -ExecutionPolicy Bypass -File scripts/smoke-demo.ps1
~~~

预期：全部 PASS。百炼或 LangSmith 未配置时必须显示“未配置”，但资源、订单、快车道和 UI 不能失败。

- [ ] **步骤 5：执行答辩前向验收并提交**

PC 展示资源和社区；小程序演示自然语言规划、进度、来源与预约；后台更新订单并展示轨迹；若配置了 LangSmith，再展示同一次会话的 Trace。

~~~powershell
git add scripts docs
git commit -m "docs: 增加乌东文旅答辩验收流程"
~~~

## 计划自检

- 三端与“衣食住行 + 社区 + 管理”由任务 2、3、8、9 覆盖。
- Java/Python 边界、单一业务写入口与待确认预约由任务 2、3、5 覆盖。
- LangGraph 快慢车道、结构化卡片、RAG、WebSocket 与 LangSmith 由任务 4 至 7 覆盖。
- 本机演示、故障降级、自动验证和人工前向验收由任务 1、10 覆盖。
- 所有 Key 均是环境变量，未配置不会伪造云端 RAG 或 Trace。
