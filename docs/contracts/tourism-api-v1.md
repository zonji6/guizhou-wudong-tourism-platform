# tourism-api-v1

**版本：** `v1`
**适用端：** Vue PC 门户、Vue 管理后台、微信小程序、Python AI 服务
**基础地址：** `http://127.0.0.1:8080`（本机演示）

所有响应均为：

```json
{"success": true, "data": {}, "message": null}
```

失败时 HTTP 状态为 `400` 或 `404`，并返回 `{"success":false,"data":null,"message":"可展示的中文说明"}`。资源、社区、知识资料中的 `demoData: true` 表示演示数据。

## 游客端 REST

| 方法与路由 | 用途 | 请求 | `data` 核心字段 |
| --- | --- | --- | --- |
| `GET /api/services?category=` | 服务列表 | `category` 可选：`stay`、`food`、`travel`、`culture` | 服务数组：`id`、`name`、`category`、`description`、`price`、`durationText`、`locationText`、`tags`、`merchantName`、`imageUrl`、`demoData` |
| `GET /api/services/{id}` | 服务详情 | 路径 `id` | 单个服务对象 |
| `GET /api/posts` | 社区内容 | 无 | 动态数组：`id`、`title`、`content`、`authorName`、`coverUrl`、`tags`、`publishedAt`、`demoData` |
| `GET /api/knowledge-documents?keywords=` | 已发布知识资料 | `keywords` 可选 | 文档数组：`id`、`title`、`content`、`tags`、`sourceType`、`indexStatus`、`updatedAt`、`demoData` |
| `POST /api/bookings` | 游客提交待确认预约 | 见下 | 状态为 `PENDING_CONFIRMATION` 的预约 |
| `POST /api/bookings/{id}/confirm` | 游客页面确认预约 | 无 | 状态变为 `CONFIRMED` 的预约 |
| `GET /api/bookings/{id}` | 查询预约 | 路径 `id` | 预约对象 |

`POST /api/bookings` 请求：

```json
{
  "serviceId": "10000000-0000-0000-0000-000000000001",
  "travelDate": "2026-09-12",
  "peopleCount": 2,
  "contactName": "演示游客",
  "contactPhone": "13800000000",
  "note": "可选备注"
}
```

预约响应字段：`id`、`serviceId`、`serviceName`、`travelDate`、`peopleCount`、`contactName`、`contactPhone`、`note`、`status`、`source`、`threadId`、`createdAt`。`status` 流转仅允许：

```text
PENDING_CONFIRMATION --游客页面确认--> CONFIRMED --运营后台--> PROCESSING | COMPLETED | CANCELLED
```

客户端不能把状态写成 `CONFIRMED`；只有确认路由具备此权限。

## 管理后台 REST

| 方法与路由 | 用途 | 请求 |
| --- | --- | --- |
| `POST /api/admin/knowledge-documents` | 新增演示知识文档 | `{"title":"...","content":"...","tags":"茶旅,苗寨"}`，返回文档，初始 `indexStatus=PENDING` |
| `PATCH /api/admin/bookings/{id}/status` | 更新已确认预约的运营状态 | `{"status":"PROCESSING"}`；仅 `PROCESSING`、`COMPLETED`、`CANCELLED` |

## Python Agent 内部工具 API

这些路由仅供本机 Python AI 服务调用，Java 以回环地址限制其访问。前端不得调用 `/internal/**`，也不得把任何 Agent 请求直接发送到该服务。Agent 不能调用确认接口，不能写入商家、服务、社区或知识业务事实。

| 方法与路由 | 用途 | 请求/响应 |
| --- | --- | --- |
| `GET /internal/agent/services/search?keywords=&tags=` | 按自然语言关键词或标签查服务 | 返回与游客服务列表相同的服务数组 |
| `GET /internal/agent/knowledge/search?keywords=` | 读取已发布知识原文 | 返回知识文档数组，供 Python 切块、向量检索和来源展示 |
| `POST /internal/agent/pending-bookings` | 创建 Agent 提议的待确认预约 | 与游客预约字段相同，额外必填 `threadId`；永远返回 `PENDING_CONFIRMATION`、`source=AGENT` |
| `GET /internal/agent/bookings/{id}` | Agent 查询预约状态 | 返回预约对象 |

Agent 创建待确认预约示例：

```json
{
  "serviceId": "10000000-0000-0000-0000-000000000001",
  "travelDate": "2026-09-12",
  "peopleCount": 2,
  "contactName": "演示游客",
  "contactPhone": "13800000000",
  "note": "来自 AI 规划卡片",
  "threadId": "demo-tea-trip-001"
}
```

## 接入约束

- 价格、文案、图片均可能是演示数据；端侧应展示“演示数据”标识。
- Python 只读服务/知识数据，或创建 `PENDING_CONFIRMATION`；页面最终确认仍通过游客确认路由完成。
- 日期使用 `YYYY-MM-DD`，金额使用 JSON number，时间使用 ISO-8601 本地时间字符串。
- Vue 的 `5173` 端口已启用本机 CORS；小程序开发工具使用本地调试域名模式。
