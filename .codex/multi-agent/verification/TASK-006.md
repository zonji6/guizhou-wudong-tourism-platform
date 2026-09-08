# TASK-006 独立静态验收记录

- task_id：`TASK-006`
- dispatch_id：`dispatch-TASK-006-r1`
- 基线：`b10ca9f52787d13d9d839eabef4ba96e42ee87ae`
- 制品：`d94dd05d7ca97857265fff58092761ecdec665df`
- 证据指纹：`sha256:0d2e348eabad269675a31813af072bbb1033dc912ba74657c814cca904dbbf9e`
- 审查方式：仅 Git 差异、提交树、制品与 tourism-api-v1 文本静态审阅。
- tests：SKIPPED
- build：SKIPPED
- runtime：NOT_EXECUTED

| 锁定项 | 结论 | 证据 |
| --- | --- | --- |
| 1. 范围 | PASS | `git diff --name-status b10ca9f..d94dd05` 的 15 项均在 `agent-service/**`；无产品端或控制面变更。 |
| 2. 图与线程 | PASS | `pyproject.toml` 固定 `langgraph>=1.1,<2.0`；`builder.py` 以 `@lru_cache(maxsize=1)` 缓存图并 `compile(checkpointer=InMemorySaver())`；`streaming.py` 将 `thread_id` 传入 `configurable`。 |
| 3. 真实流与终态 | PASS | `nodes.py` 从 `langgraph.config` 导入 `get_stream_writer`，且仅由节点调用；`run_assistant()` 使用 `astream(stream_mode=["custom", "updates"], version="v2")`，正常与异常路径各只输出一张 `card_ready`，并按卡片类型输出 `completed` 或 `failed`。 |
| 4. 字符串 ID 与别名 | PASS | `Source.document_id`、索引入口、chunker/indexer 均为 `str`；`AssistantRequest.service_id` 接收 `serviceId` 别名，`AgentState` 保持内部 `service_id`。 |
| 5. Java 检索与关键词降级 | PASS | `TourismClient` 仅调用契约中的 `/internal/agent/services/search`、`/internal/agent/knowledge/search` 与待确认预约内部路由；`retriever.py` 从 Java 知识资料返回 `retrieval_mode: keyword_demo`；模型输入仅包含这些资料片段及 Java 返回服务。 |
| 6. 模型输出约束与降级 | PASS | `deepseek_client.py` 强制 JSON 对象并验证完整行程；`sanitize_itinerary()` 仅保留 Java 服务集合中的 `serviceId`；模型、检索或服务异常产生明确 `error`/演示可用卡，未伪装实时成功。 |
| 7. 待确认预约与隐私 | PASS | `pending_booking()` 只经 `TourismClient.create_pending_booking()` 调用 `/internal/agent/pending-bookings`；返回卡片白名单未含联系人姓名或手机号，且无确认/正式订单路由调用。 |
| 8. 文案与敏感边界 | PASS | `git grep '乌冬' d94dd05 -- agent-service` 无匹配；变更未包含密钥值、真实数据或 Java/Web/小程序/文档/协作控制文件。 |
| 9. 差异与指纹绑定 | PASS | `git diff --check b10ca9f..d94dd05` 无输出；`.codex/multi-agent/tasks/TASK-006.yaml` 中的 `artifact_revision` 与 `implementation_evidence_fingerprint` 精确匹配锁定制品和给定指纹。 |

结论：PASS。未执行 Python、导入、测试、构建、服务、外部模型或网络请求。
