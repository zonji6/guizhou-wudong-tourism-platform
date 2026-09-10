# assistant-card-v3-draft-r1

**Assistant 接口修订：** <code>assistant-card-v3-draft-r1</code>

**卡片版本：** <code>3.0</code>

**匹配 Java 契约：** <code>tourism-api-v3-draft-r3</code>

**冻结依赖制品：** <code>uncommitted_worktree_sha256:02fcb348b3244ad5b884dfda15043edcbde7831fd4d0b57e55edb7372a9c1b0d</code>

**运行边界：** 本机答辩演示；Java 是账号、目录、知识公开状态、已保存行程、食宿草稿和订单的唯一业务事实写入口。

**实现状态：** 本文只冻结 Assistant Card v3 的卡片、过程事件、恢复状态、候选、checkpoint、运行围栏、知识核验与隐私边界。本文存在不表示 Python、Java、Web、小程序、Redis Saver、Redis Search、Embedding 或 LangSmith 已接线、已集成或已运行验证。

## 1. 版本矩阵与规范用语

| 边界 | 固定版本 | 说明 |
| --- | --- | --- |
| Java REST、内部 REST、WebSocket 鉴权帧 | <code>tourism-api-v3-draft-r3</code> | 请求头和鉴权帧中的 Java <code>contractVersion</code> |
| WebSocket 鉴权 | <code>wudong-ws-auth-v1</code> | 由 Java v3 第 9 节冻结，本文不重定义 |
| Assistant 业务帧、卡片、事件、恢复状态 | <code>assistant-card-v3-draft-r1</code> | 字段名为 <code>assistantContractVersion</code> |
| Assistant 事件 | <code>assistant-event-v3-draft-r1</code> | 字段名为 <code>eventVersion</code> |
| Assistant 恢复状态 | <code>assistant-session-state-v3-draft-r1</code> | 字段名为 <code>stateVersion</code> |
| Assistant checkpoint | <code>assistant-checkpoint-v3-draft-r1</code> | 字段名为 <code>checkpointVersion</code> |
| 用户卡片 | <code>3.0</code> | 字段名为 <code>cardVersion</code> |

Assistant 业务帧同时携带：

- <code>assistantContractVersion="assistant-card-v3-draft-r1"</code>；
- <code>tourismContractVersion="tourism-api-v3-draft-r3"</code>。

二者任一不匹配都返回稳定错误 <code>CONTRACT_INCOMPATIBLE</code>，不得只替换版本字符串后继续解析 v2 字段。四端必须成组切换；本文不提供 v2 到 v3 的宽松兼容解析。

本文中的“必须”“不得”“仅”均为强制要求。“建议”只用于尚须实施期能力验证的具体依赖解析版本，不削弱 DTO、状态机、隐私或原子性要求。

## 2. 共同传输与严格模型规则

### 2.1 严格 JSON

- Python 使用 Pydantic v2 严格判别联合；所有对象 <code>extra="forbid"</code>，API JSON 使用 <code>camelCase</code>。
- 未列字段、重复 JSON 键、错误类型、未知枚举、非法 <code>null</code>、把布尔值当整数以及把数字写成字符串都拒绝。
- 所有列出的字段都必须出现；只有类型明确写为 <code>| null</code> 的字段可为 <code>null</code>。数组必须出现，空集合写 <code>[]</code>，不得写 <code>null</code>。
- <code>Version</code>、<code>Revision</code>、<code>candidateVersion</code>、<code>resourceVersion</code>、<code>sequence</code> 均为从 1 开始的 JSON 正整数。
- UUID 为小写、带连字符的规范字符串。
- UTC 时间使用秒精度 RFC 3339，例如 <code>2026-09-10T01:02:03Z</code>；本地日期为真实 <code>YYYY-MM-DD</code>；本地日期时间为 <code>Asia/Shanghai</code> 语义的 <code>YYYY-MM-DDTHH:mm:ss</code>，不得带时区后缀或小数秒。
- <code>Sha256Digest</code> 固定为 <code>sha256:</code> 加 64 位小写十六进制。
- 所有模型先严格验证，再做确定性递归白名单和文本清洗，最后用同一严格模型重新验证。输出统一使用 JSON 模式和别名，不能直接发送模型字典、异常对象或工具结果。

### 2.2 共同枚举

| 名称 | 固定值 |
| --- | --- |
| <code>TargetType</code> | <code>PRODUCT|FOOD|STAY|PLACE|ROUTE_GUIDE</code> |
| 知识 <code>RetrievalMode</code> | <code>KEYWORD_DEMO|VECTOR</code> |
| Assistant／运行摘要检索模式 | <code>NONE|KEYWORD_DEMO|VECTOR</code> |
| <code>AgentType</code> | <code>KNOWLEDGE_GUIDE|SERVICE_RECOMMENDER|ITINERARY_PLANNER</code> |
| <code>CandidateType</code> | <code>ITINERARY|FOOD_DRAFT|STAY_DRAFT</code> |
| <code>CandidateAction</code> | <code>CREATE|UPDATE</code> |
| <code>SourceOwnerKind</code> | <code>USER|ANONYMOUS</code> |
| <code>RunState</code> | <code>RUNNING|CANCEL_REQUESTED|STOPPED|INTERRUPTED|COMPLETED|FAILED</code> |

知识 <code>RetrievalMode</code> 与运行摘要检索模式是两个不同类型，不能为了复用枚举而让 Java 知识 DTO 接受 <code>NONE</code>。

## 3. 公共基础 DTO

### 3.1 <code>PublicTarget</code>

~~~json
{
  "targetType": "PLACE",
  "targetId": "00000000-0000-0000-0000-000000000000",
  "targetName": "公开地点名称"
}
~~~

三个字段都必填。目标必须来自 Java 当前公开投影；<code>STAY</code> 的 <code>targetId</code> 永远是 <code>roomTypeId</code>。模型叙述中没有可靠平台 ID 的内容不能伪造此对象。

### 3.2 <code>MerchantItemsCondition</code> 与 <code>ConfirmedConditionsV3</code>

<code>MerchantItemsCondition</code> 固定为：

~~~json
{
  "merchantId": "00000000-0000-0000-0000-000000000000",
  "items": [
    {
      "foodItemId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ]
}
~~~

<code>items</code> 为 1～50 项，<code>quantity</code> 为正整数，<code>foodItemId</code> 不得重复且都属于同一 <code>merchantId</code>。异常协议输入必须拒绝，不能静默合并重复菜品。

<code>ConfirmedConditionsV3</code> 的全部字段固定为：

~~~json
{
  "travelDate": null,
  "visitAt": null,
  "checkInDate": null,
  "checkOutDate": null,
  "roomCount": null,
  "peopleCount": null,
  "preferences": [],
  "selectedTargets": [],
  "merchantItems": null
}
~~~

- 日期、日期时间、房数和人数允许 <code>null</code>；非空房数和人数是正整数。
- <code>preferences</code> 最多 12 项，去首尾空白后每项非空且不得重复。
- <code>selectedTargets</code> 最多 12 项，以 <code>targetType+targetId</code> 去重。
- <code>merchantItems</code> 为 <code>null</code> 或上面的严格对象。
- 此对象只保存已经结构化并由用户确认的公开条件。联系人、电话、私人备注、原始话语、账号标识和凭据均不得进入。

### 3.3 <code>KnowledgeReference</code>

用户可见引用逐字段只含：

~~~json
{
  "sourceTitle": "受控来源标题",
  "detailPath": "/api/knowledge-documents/00000000-0000-0000-0000-000000000000"
}
~~~

<code>detailPath</code> 必须精确匹配本站路径 <code>/api/knowledge-documents/{canonical-lowercase-uuid}</code>。不得出现 <code>documentId</code>、<code>buildId</code>、<code>snapshotHash</code>、<code>configHash</code>、原始 URL、发布者、定位、来源类型、分数或知识正文。

最终引用由本轮已通过交付前核验的全部 Java <code>KnowledgeEvidence.references</code> 产生：先按内部 <code>KnowledgeDependency</code> 的 <code>documentId,buildId</code> 顺序，再按每个 evidence 的原始引用顺序，以 <code>sourceTitle+detailPath</code> 去重。不得只保留模型主动列出的来源。

### 3.4 金额展示

<code>ReferencePrice</code>：

~~~json
{
  "amount": "88.00",
  "currency": "CNY",
  "unit": "ITEM",
  "sourceTitle": "已核验的站内来源标题"
}
~~~

<code>DemoPrice</code>：

~~~json
{
  "amount": "68.00",
  "currency": "CNY",
  "unit": "ITEM",
  "simulationNote": "本机演示价格，不代表真实经营报价"
}
~~~

<code>amount</code> 匹配 <code>^(0|[1-9][0-9]{0,9})\.[0-9]{2}$</code>，不得用 JSON 浮点数。<code>currency</code> 只能为 <code>CNY</code>；单位按商品、餐食、住宿分别为 <code>ITEM|PORTION|ROOM_NIGHT</code>。两种价格都可为 <code>null</code>，但不能互相补值；它们是目录展示，不是订单核价、锁价、库存或房态事实。

### 3.5 <code>CandidateRef</code>、<code>BaseResource</code> 与公开候选元数据

<code>CandidateRef</code> 精确为：

~~~json
{
  "threadId": "00000000-0000-0000-0000-000000000000",
  "candidateId": "00000000-0000-0000-0000-000000000000",
  "candidateVersion": 2
}
~~~

<code>BaseResource</code> 精确为：

~~~json
{
  "resourceType": "FOOD_DRAFT",
  "resourceId": "00000000-0000-0000-0000-000000000000",
  "resourceVersion": 7
}
~~~

面向客户端的 <code>CandidateMeta</code> 精确为：

~~~json
{
  "candidateRef": {
    "threadId": "00000000-0000-0000-0000-000000000000",
    "candidateId": "00000000-0000-0000-0000-000000000000",
    "candidateVersion": 2
  },
  "candidateType": "FOOD_DRAFT",
  "adoptionAction": "UPDATE",
  "baseResource": {
    "resourceType": "FOOD_DRAFT",
    "resourceId": "00000000-0000-0000-0000-000000000000",
    "resourceVersion": 7
  },
  "createdAt": "2026-09-10T01:00:00Z",
  "expiresAt": "2026-09-11T01:00:00Z"
}
~~~

联合约束：

- <code>CREATE</code> 当且仅当 <code>baseResource=null</code>。
- <code>UPDATE</code> 当且仅当 <code>baseResource</code> 非空，且 <code>resourceType=candidateType</code>。
- <code>candidateVersion</code> 是候选不可变版本，<code>resourceVersion</code> 是 Java 基础资源版本，二者不得混用。
- 公开候选元数据不得出现内部 <code>sourceOwnerKind</code>、<code>candidateDigest</code>、<code>knowledgeContext</code>、<code>knowledgeDependencies</code> 或 <code>resolvedAt</code>。
- <code>CandidateRef</code>、线程 ID、已展示卡片和摘要都不是授权。

### 3.6 <code>CardActionV3</code> 严格联合

目标动作：

~~~json
{
  "action": "OPEN_DETAIL",
  "label": "查看详情",
  "targetType": "FOOD",
  "targetId": "00000000-0000-0000-0000-000000000000"
}
~~~

<code>action</code> 只允许 <code>OPEN_DETAIL|OPEN_MAP|OPEN_ROUTE_GUIDE</code>。<code>OPEN_MAP</code> 只允许具有公开示意位置的 <code>PLACE</code>，且界面必须标明“水彩示意、非等比例、非导航”；<code>OPEN_ROUTE_GUIDE</code> 只允许 <code>ROUTE_GUIDE</code>。

候选采用动作：

~~~json
{
  "action": "ADOPT_FOOD_DRAFT",
  "label": "保存为餐食草稿",
  "candidateRef": {
    "threadId": "00000000-0000-0000-0000-000000000000",
    "candidateId": "00000000-0000-0000-0000-000000000000",
    "candidateVersion": 2
  }
}
~~~

<code>action</code> 只允许 <code>SAVE_ITINERARY|ADOPT_FOOD_DRAFT|ADOPT_STAY_DRAFT</code>，并分别只匹配 <code>ITINERARY|FOOD_DRAFT|STAY_DRAFT</code>。

重试动作：

~~~json
{
  "action": "RETRY",
  "label": "重新尝试"
}
~~~

三个分支不得混入其他分支字段。<code>RETRY</code> 只代表用户主动发起新 run，绝不授权客户端自动重发。v3 不含 <code>OPEN_ORDER_CONFIRMATION</code> 或具有直接业务写入含义的 <code>ADD_TO_ITINERARY</code>。

采用动作必须先由用户明确确认并具备有效 USER 登录，然后只把精确 <code>candidateRef</code> 交给 Java 对应公开采用路由；客户端不回传候选全文。采用最多创建或更新已保存行程、餐食草稿或住宿草稿，绝不直接创建订单。Java 成功保存草稿后，用户仍须进入独立确认页核价、补齐联系人并正式提交。

## 4. Assistant Card v3

六类卡片共同字段全部必填：

~~~json
{
  "cardVersion": "3.0",
  "type": "knowledge_answer",
  "title": "乌东资料回答",
  "summary": "根据当前生效资料整理。",
  "references": [],
  "actions": [],
  "data": {}
}
~~~

| 字段 | 约束 |
| --- | --- |
| <code>cardVersion</code> | 固定 <code>"3.0"</code> |
| <code>type</code> | 仅第 4.1～4.6 节六个判别值 |
| <code>title</code> | 面向用户的 1～80 个 Unicode 码点文本 |
| <code>summary</code> | 面向用户的 0～800 个 Unicode 码点文本 |
| <code>references</code> | <code>KnowledgeReference[]</code>；无可靠知识引用时为 <code>[]</code> |
| <code>actions</code> | <code>CardActionV3[]</code>；无可靠 ID 或候选时为 <code>[]</code> |
| <code>data</code> | 按 <code>type</code> 选择的严格 DTO |

卡片不得携带内部知识依赖、模型原文流、工具输入输出、联系人、账号、凭据、业务写回执或自由扩展字段。<code>references</code> 仅是公开展示，不能替代内部完整知识依赖。

### 4.1 <code>type=itinerary</code>

<code>data</code> 精确为：

~~~json
{
  "content": {
    "title": "两日水彩慢游",
    "travelDate": null,
    "peopleCount": null,
    "days": [
      {
        "day": 1,
        "theme": "沿溪慢行",
        "stops": [
          {
            "sequence": 1,
            "targetType": "PLACE",
            "targetId": "00000000-0000-0000-0000-000000000000",
            "title": "公开地点名称",
            "note": null
          }
        ]
      }
    ]
  },
  "candidate": null,
  "retrievalMode": "NONE",
  "demoData": true,
  "notice": "服务时间、价格和可预约情况请以确认页及服务方最终确认结果为准。"
}
~~~

<code>content</code> 必须与 Java v3 <code>SavedItinerary.content</code> 逐字段一致：

- <code>title</code> 为 1～80 个 Unicode 码点。
- <code>travelDate</code>、<code>peopleCount</code> 可为 <code>null</code>；非空人数为正整数。
- <code>days</code> 为 1～30 项，<code>day</code> 严格等于数组序号 <code>1..N</code>。
- 每日 <code>theme</code> 为 <code>null</code> 或最多 80 个码点；<code>stops</code> 为 0～30 项。
- <code>sequence</code> 严格等于节点数组序号 <code>1..N</code>。
- <code>targetType</code> 与 <code>targetId</code> 必须同时为 <code>null</code>，或同时为可靠的公开目标类型与规范 UUID；<code>STAY</code> 指房型。
- 节点 <code>title</code> 必填；<code>note</code> 为 <code>null</code> 或最多 500 个码点。无可靠平台 ID 的叙述项用两个 <code>null</code>，不得伪造 ID。

<code>candidate</code> 为 <code>null</code> 或 <code>candidateType=ITINERARY</code> 的 <code>CandidateMeta</code>；非空时内部候选 <code>payload</code> 必须与 <code>content</code> 逐字段相同，并且 <code>actions</code> 必须恰有匹配同一引用的 <code>SAVE_ITINERARY</code>。候选为空时不得产生保存动作。

<code>demoData</code> 与检索模式独立：任一用于当前展示的目录或知识证据为演示数据时为 <code>true</code>。<code>retrievalMode=NONE</code> 时 <code>references=[]</code>；另两种模式的引用与内部依赖遵守第 10 节。

### 4.2 <code>type=service_recommendation</code>

<code>data</code> 精确为：

~~~json
{
  "items": [
    {
      "targetType": "FOOD",
      "targetId": "00000000-0000-0000-0000-000000000000",
      "targetName": "公开餐食名称",
      "summary": "来自当前公开目录的简要介绍。",
      "referencePrice": null,
      "demoPrice": {
        "amount": "68.00",
        "currency": "CNY",
        "unit": "PORTION",
        "simulationNote": "本机演示价格，不代表真实经营报价"
      },
      "tags": [],
      "demoData": true
    }
  ],
  "retrievalMode": "NONE",
  "notice": "目录价格仅供展示，正式提交前由 Java 重新核价。"
}
~~~

- <code>items</code> 为 1～20 项，每项必须绑定 Java 当前公开目录的真实 <code>targetType+targetId</code>；<code>STAY</code> 使用 <code>roomTypeId</code>。
- <code>summary</code> 是公开投影或经清洗的说明，不能编造地方事实、库存、餐位、房态、班次或联系方式。
- <code>referencePrice</code> 与 <code>demoPrice</code> 分别严格使用第 3.4 节对象；<code>PLACE|ROUTE_GUIDE</code> 二者都必须为 <code>null</code>。
- <code>tags</code> 来自当前公开投影；<code>demoData</code> 原样保留目录标识。
- 所有目标动作必须引用对应条目的同一真实 ID。商品只允许推荐或打开详情，不能生成商品候选或商品订单。
- <code>retrievalMode=NONE</code> 时 <code>references=[]</code>；使用知识增强时遵守第 10 节。

### 4.3 <code>type=knowledge_answer</code>

<code>data</code> 精确为：

~~~json
{
  "answer": "依据当前生效且重新核验的乌东资料整理出的完整回答。",
  "retrievalMode": "KEYWORD_DEMO",
  "demoData": false
}
~~~

- <code>answer</code> 为最终完整回答，不是 token 流或中间草稿。
- <code>retrievalMode</code> 只允许 <code>KEYWORD_DEMO|VECTOR</code>，不得为 <code>NONE</code>。
- 顶层 <code>references</code> 必须非空，并与第 10 节最终通过核验的完整依赖集合一致。
- <code>demoData</code> 在任一实际入模 evidence 的 <code>demoData=true</code> 时为 <code>true</code>；它与 <code>KEYWORD_DEMO</code> 是两条独立轴。
- 没有可靠来源时不得伪装资料回答，应产生稳定错误卡。任一依赖在交付前失效、换版、摘要变化或不可确认时，必须丢弃整份尚未交付的回答和引用，不能只删除链接或交付剩余段落。

### 4.4 <code>type=clarifying_question</code>

<code>data</code> 精确为：

~~~json
{
  "requiredFields": [
    "checkOutDate",
    "roomCount"
  ],
  "confirmedConditions": {
    "travelDate": null,
    "visitAt": null,
    "checkInDate": "2026-10-01",
    "checkOutDate": null,
    "roomCount": null,
    "peopleCount": 2,
    "preferences": [],
    "selectedTargets": [],
    "merchantItems": null
  }
}
~~~

<code>requiredFields</code> 为非空、不重复数组，只允许：

<code>travelDate|visitAt|checkInDate|checkOutDate|roomCount|peopleCount|preferences|target|merchantItems</code>。

追问只能询问缺失的公开规划条件，不补造默认日期、人数、房数、目标、联系人或私人备注。此类卡片固定 <code>references=[]</code>，且不得有候选采用动作。

### 4.5 <code>type=pending_booking</code>

为保持六类判别值，外层技术值保留 <code>pending_booking</code>；面向用户的标题、摘要和动作必须统一称“待采用方案”或“保存为草稿”，绝不能称作已预约、待支付、已下单或正式订单。

<code>data</code> 是由 <code>candidate.candidateType</code> 判别的严格联合，共同字段为：

~~~json
{
  "candidate": {
    "candidateRef": {
      "threadId": "00000000-0000-0000-0000-000000000000",
      "candidateId": "00000000-0000-0000-0000-000000000000",
      "candidateVersion": 1
    },
    "candidateType": "FOOD_DRAFT",
    "adoptionAction": "CREATE",
    "baseResource": null,
    "createdAt": "2026-09-10T01:00:00Z",
    "expiresAt": "2026-09-11T01:00:00Z"
  },
  "proposal": {
    "merchantId": "00000000-0000-0000-0000-000000000000",
    "items": [
      {
        "foodItemId": "00000000-0000-0000-0000-000000000000",
        "quantity": 2
      }
    ],
    "visitAt": null,
    "peopleCount": null
  },
  "retrievalMode": "NONE",
  "demoData": true,
  "notice": "采用后只保存为草稿，仍需在确认页核价、补齐联系人并正式提交。"
}
~~~

餐食分支要求 <code>candidateType=FOOD_DRAFT</code>，<code>proposal</code> 逐字段只含：

~~~json
{
  "merchantId": "00000000-0000-0000-0000-000000000000",
  "items": [
    {
      "foodItemId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ],
  "visitAt": null,
  "peopleCount": null
}
~~~

住宿分支要求 <code>candidateType=STAY_DRAFT</code>，<code>proposal</code> 逐字段只含：

~~~json
{
  "roomTypeId": "00000000-0000-0000-0000-000000000000",
  "checkInDate": null,
  "checkOutDate": null,
  "roomCount": null,
  "peopleCount": null
}
~~~

两个 <code>proposal</code> 必须与内部不可变候选 <code>payload</code> 逐字段相同。餐食 <code>items</code> 为 1～50 个不重复、同店条目，数量为正整数；食宿允许已列规划字段为 <code>null</code>，但不允许错误资源、跨店、重复条目或非法日期／数量。

两个分支都禁止 <code>contactName</code>、<code>contactPhone</code>、<code>note</code>、<code>sourceThreadId</code>、账号、金额、状态、订单 ID、目标名称、<code>requiresVisitorConfirmation</code> 和任意扩展键。公开名称只可放在卡片 <code>title/summary</code> 中作为展示，不能进入采用 payload。

<code>actions</code> 必须恰有与候选类型和同一引用匹配的 <code>ADOPT_FOOD_DRAFT</code> 或 <code>ADOPT_STAY_DRAFT</code>。<code>retrievalMode</code>、<code>references</code> 和 <code>demoData</code> 遵守第 10 节及目录事实。

### 4.6 <code>type=error</code>

<code>data</code> 精确为：

~~~json
{
  "code": "KNOWLEDGE_ELIGIBILITY_UNAVAILABLE",
  "retryable": true
}
~~~

<code>code</code> 为 1～64 位 ASCII 大写字母、数字和下划线组成的稳定公开码。中文安全说明放在共同 <code>title/summary</code>，不得放原始异常、供应商正文、路径、URL、工具参数或配置。<code>retryable=true</code> 只允许用户主动重试，不表示自动重发，也不能借此把 VECTOR 失败自动改写成关键词成功。错误卡固定 <code>references=[]</code> 且没有目标或候选动作；唯一可选动作是 <code>RETRY</code>。

## 5. 认证后的 Assistant 请求

Java v3 第 9 节的 <code>auth_ok</code> 之前不得处理以下任何业务帧。每条业务输入前都重新核对 JWT／匿名期限及 Redis；路由、身份模式、thread 归属不匹配时拒绝，不能因客户端持有 threadId 或页面缓存而放行。

### 5.1 <code>session_state_request</code>

~~~json
{
  "assistantContractVersion": "assistant-card-v3-draft-r1",
  "tourismContractVersion": "tourism-api-v3-draft-r3",
  "type": "session_state_request",
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": null,
  "lastEventSequence": 0
}
~~~

- <code>threadId</code> 为规范 UUID；只有用户明确“新建对话”时可为 <code>null</code>，此时由服务器生成并绑定当前 USER 或 ANONYMOUS 所有者，客户端不能指定 owner。
- 恢复或重连必须携带已有 <code>threadId</code>；<code>runId</code> 为 <code>null</code> 或当前已知 run。
- <code>lastEventSequence</code> 为非负整数，零表示客户端尚未接收运行事件。
- 此请求只恢复认证后的状态，不续会话、checkpoint、候选或知识期限，不自动生成、不补发业务写入，也不保证逐事件回放。

### 5.2 <code>generate</code>

~~~json
{
  "assistantContractVersion": "assistant-card-v3-draft-r1",
  "tourismContractVersion": "tourism-api-v3-draft-r3",
  "type": "generate",
  "clientRequestId": "00000000-0000-0000-0000-000000000000",
  "threadId": "00000000-0000-0000-0000-000000000000",
  "expectedCheckpointRevision": null,
  "userText": "想安排两天慢游。",
  "pageAction": null,
  "selectedTarget": null,
  "baseResource": null,
  "conditions": {
    "travelDate": null,
    "visitAt": null,
    "checkInDate": null,
    "checkOutDate": null,
    "roomCount": null,
    "peopleCount": null,
    "preferences": [],
    "selectedTargets": [],
    "merchantItems": null
  },
  "retrievalMode": "KEYWORD_DEMO"
}
~~~

- <code>clientRequestId</code> 是本 thread 的请求防重 UUID；同 ID 同规范摘要只返回原 run／状态，同 ID 异摘要返回 <code>RUN_REQUEST_CONFLICT</code>。
- <code>expectedCheckpointRevision</code> 为 <code>null</code> 或正整数；新会话没有 checkpoint 时为 <code>null</code>。不匹配时返回 <code>CHECKPOINT_SEQUENCE_CONFLICT</code>，不得自动套用最新版。
- <code>userText</code> 为 0～2,000 个 Unicode 码点的单轮瞬时输入。
- <code>pageAction</code> 为 <code>null</code> 或 <code>RECOMMEND_PRODUCT|RECOMMEND_FOOD|RECOMMEND_STAY|BROWSE_PLACES|BROWSE_ROUTE_GUIDES|SHOW_MY_ORDERS</code>。
- <code>selectedTarget</code> 为 <code>null</code> 或 <code>PublicTarget</code>。
- <code>baseResource</code> 为 <code>null</code> 或第 3.5 节对象，只表达用户希望更新哪一项本人行程／草稿；它不是授权。Python 必须用当前 USER proof 读取本人严格规划投影并核对 ID、类型、状态和版本后才可绑定 UPDATE 候选；ANONYMOUS 请求必须为 <code>null</code>。
- 去除首尾空白后的 <code>userText</code> 与 <code>pageAction</code> 至少一个有效。
- <code>retrievalMode</code> 明确固定本 run 的模式；<code>NONE</code> 不访问知识，另两种模式运行中不得改写。VECTOR 不可用时本 run 失败，不能静默降级。
- <code>agentType</code> 不由客户端提交。服务端按已校验页面动作和归一化意图选择三个固定 Agent 之一。

只有通过身份、严格请求、限频、会话期限、checkpoint 版本和单 run 占用检查后，才算接受 generate。只有这次接受可按服务器时间延长 USER 对话 30 天；ANONYMOUS 由 Java <code>POST /internal/agent/anonymous/interactions</code> 对同一 <code>threadId/runId</code> 原子确认并延长 24 小时。握手、状态读取、重连、取消、失败的重复请求和候选解析均不续期。

### 5.3 <code>cancel_run</code>

~~~json
{
  "assistantContractVersion": "assistant-card-v3-draft-r1",
  "tourismContractVersion": "tourism-api-v3-draft-r3",
  "type": "cancel_run",
  "cancelRequestId": "00000000-0000-0000-0000-000000000000",
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": "00000000-0000-0000-0000-000000000000"
}
~~~

取消必须核对当前身份、thread 归属和精确 run。首次有效取消只原子进入 <code>CANCEL_REQUESTED</code>；重复同请求返回相同状态。返回取消已请求绝不等于执行已经停止，且不会自动启动新 run。

## 6. 不可变候选与 Java 采用边界

### 6.1 内部知识类型

<code>KnowledgeDependency</code> 精确为：

~~~json
{
  "documentId": "00000000-0000-0000-0000-000000000000",
  "buildId": "00000000-0000-0000-0000-000000000000",
  "snapshotHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
}
~~~

一次 run 的依赖按 <code>documentId,buildId</code> 升序去重；Java run summary 最多 50 项。内部 <code>KnowledgeContext</code> 精确为：

~~~json
{
  "retrievalMode": "VECTOR",
  "configHash": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
}
~~~

候选的 <code>knowledgeDependencies=[]</code> 时 <code>knowledgeContext</code> 必须为 <code>null</code>；依赖非空时必须为上面的非空对象。公开引用不能反推或替代这些内部字段。

### 6.2 Java → Python 精确解析请求

<code>POST /internal/assistant/candidates/resolve</code> 使用 Java v3 的 <code>JAVA_TO_AI</code> 服务身份、当前 USER proof；匿名来源候选还必须有原 ANONYMOUS proof。严格请求为：

~~~json
{
  "candidateRef": {
    "threadId": "00000000-0000-0000-0000-000000000000",
    "candidateId": "00000000-0000-0000-0000-000000000000",
    "candidateVersion": 2
  },
  "expectedCandidateType": "FOOD_DRAFT",
  "expectedAction": "UPDATE",
  "expectedBase": {
    "resourceType": "FOOD_DRAFT",
    "resourceId": "00000000-0000-0000-0000-000000000000",
    "resourceVersion": 7
  }
}
~~~

<code>CREATE</code> 时 <code>expectedBase</code> 必须明确为 <code>null</code>；<code>UPDATE</code> 时必须非空，且类型与候选一致。Java 从采用路由、路径资源和请求 <code>expectedVersion</code> 构造 expected 字段，公共客户端不能提交它们。

Python 必须先验证服务方向，再验证 USER 登录、Redis sid、thread 与账号归属；匿名来源还须精确验证匿名证明。任一证明失败都不得查询候选。解析只能精确查找 <code>threadId+candidateId+candidateVersion</code>，不能取 latest、current、previous、其他版本或重新生成。

### 6.3 内部不可变候选信封

成功信封逐字段固定为：

~~~json
{
  "candidateRef": {
    "threadId": "00000000-0000-0000-0000-000000000000",
    "candidateId": "00000000-0000-0000-0000-000000000000",
    "candidateVersion": 2
  },
  "candidateType": "FOOD_DRAFT",
  "adoptionAction": "UPDATE",
  "baseResource": {
    "resourceType": "FOOD_DRAFT",
    "resourceId": "00000000-0000-0000-0000-000000000000",
    "resourceVersion": 7
  },
  "sourceOwnerKind": "USER",
  "createdAt": "2026-09-10T01:00:00Z",
  "expiresAt": "2026-09-11T01:00:00Z",
  "candidateDigest": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
  "knowledgeContext": null,
  "knowledgeDependencies": [],
  "payload": {
    "merchantId": "00000000-0000-0000-0000-000000000000",
    "items": [
      {
        "foodItemId": "00000000-0000-0000-0000-000000000000",
        "quantity": 2
      }
    ],
    "visitAt": null,
    "peopleCount": null
  },
  "resolvedAt": "2026-09-10T01:02:03Z"
}
~~~

三种 <code>payload</code> 分别为：

- <code>ITINERARY</code>：第 4.1 节完整 <code>content</code>。
- <code>FOOD_DRAFT</code>：第 4.5 节餐食 <code>proposal</code>。
- <code>STAY_DRAFT</code>：第 4.5 节住宿 <code>proposal</code>。

食宿 payload 禁止联系人、电话、<code>note</code>、账号、金额、状态、<code>sourceThreadId</code> 和任意额外键。CREATE 时 Java 固定新草稿联系人和私人备注为 <code>null</code>；UPDATE 时只更新上述规划列，逐列保留已有联系人和私人备注，不能把候选缺字段解释为清空。

<code>candidateDigest</code> 的规范材料包含 Java <code>contractVersion</code>、完整 <code>candidateRef</code>、<code>candidateType</code>、<code>adoptionAction</code>、<code>baseResource</code>、<code>sourceOwnerKind</code>、<code>createdAt</code>、<code>expiresAt</code>、<code>knowledgeContext</code>、<code>knowledgeDependencies</code> 和完整 <code>payload</code>，不含 <code>resolvedAt</code>。每个候选版本创建后不可原位修改；修订必须新增正整数版本和完整新载荷。

候选有知识依赖时，Python 在每次 resolve 返回 payload 前，必须按候选冻结的同一 <code>retrievalMode/configHash</code> 对完整依赖重新执行第 10 节核验。任何变化或不可确认都不返回 payload。摘要只是完整性检查，不是授权或签名；Java 仍须重算摘要、检查服务器期限、类型、动作、base、资源归属、目录状态、同店、日期、容量和资源版本。

### 6.4 候选期限与采用

- <code>expiresAt</code> 在候选创建时由 Python 服务器以当时会话期限冻结，并进入摘要；读取、重连、查看、选择和 resolve 都不续期。
- 登录不会把原匿名候选的期限改成 USER 30 天。需要新期限只能由用户主动生成新候选版本，不能修改旧信封。
- 服务端不得因为界面只显示两个方案就删除仍未到 <code>expiresAt</code> 的任何已发候选版本。
- 到期后不保证物理保留，但读取必须先按服务器时间判断；客户端时间不参与裁决。
- 候选过期或精确版本不存在时，用户只能主动重新生成，不能自动换版。

公共采用路由固定为：

| 候选 | CREATE | UPDATE |
| --- | --- | --- |
| <code>ITINERARY</code> | <code>POST /api/me/itineraries/adoptions</code>，body 仅 <code>candidateRef</code> | <code>POST /api/me/itineraries/{id}/adoptions</code>，body 为 <code>candidateRef,expectedVersion</code> |
| <code>FOOD_DRAFT</code> | <code>POST /api/me/food-drafts/adoptions</code>，body 仅 <code>candidateRef</code> | <code>POST /api/me/food-drafts/{id}/adoptions</code>，body 为 <code>candidateRef,expectedVersion</code> |
| <code>STAY_DRAFT</code> | <code>POST /api/me/stay-drafts/adoptions</code>，body 仅 <code>candidateRef</code> | <code>POST /api/me/stay-drafts/{id}/adoptions</code>，body 为 <code>candidateRef,expectedVersion</code> |

这些写入都由 Java v3 的 USER 授权、<code>Idempotency-Key</code>、业务版本和持久回执裁决。已成功同 key 重试先复用原成功回执，不因候选后来过期而反转；尚未成功的调用结果不可确认时不得伪装成功、失败或过期。

解析错误必须与 Java v3 对齐：

| HTTP | code | 语义 |
| --- | --- | --- |
| 410 | <code>CANDIDATE_EXPIRED</code> | 已验证范围内，服务器期限已过 |
| 410 | <code>CANDIDATE_VERSION_UNAVAILABLE</code> | 精确版本不存在 |
| 409 | <code>CANDIDATE_PAYLOAD_INVALID</code> | 类型、动作、摘要或严格 payload 不符 |
| 409 | <code>CANDIDATE_BASE_MISMATCH</code> | 基础资源与采用目标不符 |
| 503 | <code>CANDIDATE_LOOKUP_UNAVAILABLE</code> | Python、Redis、依赖核验或结果不可确认 |

响应不得回显候选正文、内部所有者、摘要材料或其他 thread 的存在性。

## 7. 当前／上一完整方案

### 7.1 内部 <code>SolutionSnapshot</code>

只有已原子提交的候选方案才可成为方案快照。内部对象的字段精确为：

| 字段 | 类型 |
| --- | --- |
| <code>solutionRevision</code> | 正整数 |
| <code>card</code> | 完整 <code>AssistantCardV3</code> |
| <code>candidateRef</code> | <code>CandidateRef</code> |
| <code>candidateType</code> | <code>CandidateType</code> |
| <code>adoptionAction</code> | <code>CandidateAction</code> |
| <code>baseResource</code> | <code>BaseResource|null</code> |
| <code>createdAt</code> | UTC 时间 |
| <code>expiresAt</code> | UTC 时间 |
| <code>knowledgeContext</code> | <code>KnowledgeContext|null</code> |
| <code>knowledgeDependencies</code> | <code>KnowledgeDependency[]</code> |

<code>card</code> 必须是带同一候选引用的完整 <code>itinerary</code> 或 <code>pending_booking</code> 卡片。内部知识字段只用于恢复和再次核验，不进入公共卡片。

新候选方案完成提交时，在同一个原子提交中执行：

1. 若新 <code>candidateRef</code> 与当前引用相同，视为重放，不旋转。
2. 否则原 <code>currentSolution</code> 移为 <code>previousSolution</code>。
3. 新完整方案成为 <code>currentSolution</code>。
4. 更早的界面槽位被淘汰，但其未过期候选记录仍保留到自身 <code>expiresAt</code>。

追问、普通推荐、知识回答、错误、无候选行程、阶段进度、取消、中断和失败都不得旋转方案槽。未通过最终 DTO、知识核验或原子提交的半成品永远不能成为当前／上一方案。

### 7.2 公共方案槽

恢复状态和终态事件中的 <code>currentSolution</code>、<code>previousSolution</code> 都使用：

~~~json
{
  "slot": "CURRENT",
  "status": "EMPTY",
  "solution": null,
  "code": null
}
~~~

<code>slot</code> 为 <code>CURRENT|PREVIOUS</code>；<code>status</code> 为：

- <code>EMPTY</code>：<code>solution=null,code=null</code>；
- <code>AVAILABLE</code>：<code>solution</code> 为完整卡片快照，<code>code=null</code>；
- <code>EXPIRED|VERSION_UNAVAILABLE|LOOKUP_UNAVAILABLE|INCOMPATIBLE</code>：<code>solution=null</code>，<code>code</code> 为对应稳定码。

恢复时必须重新检查 checkpoint 版本、访问归属、精确候选版本、服务器 <code>expiresAt</code>、摘要以及完整知识依赖；只有全部通过才可把卡片作为新输出返回。失败状态不能改取最新版，也不能用 checkpoint 旧正文绕过核验。

用户在界面切换 CURRENT／PREVIOUS 只是本地查看，不修改服务器指针、不延长期限。采用必须提交所查看卡片的精确 <code>candidateRef</code>，不得提交字符串 CURRENT／PREVIOUS 让服务端猜测。

## 8. 运行状态、事件与有限重连

### 8.1 状态机

Redis 中的 <code>threadKey+runId+fenceEpoch</code> 是运行唯一事实；WebSocket 连接、进程内 task、客户端界面和 Java <code>run-summaries</code> 都不能替代它。

- <code>RUNNING</code>：run 已实际接受，仍可能产生模型、工具、checkpoint 或输出。
- <code>CANCEL_REQUESTED</code>：取消意图已持久化，三道提交门已关闭；不表示执行已停。
- <code>STOPPED</code>：只用于用户明确取消，且执行器已证明模型任务、工具回调和 Saver 写任务全部静默；<code>errorCode=CANCELLED_BY_USER</code>。
- <code>INTERRUPTED</code>：连接、认证、进程或依赖中断，且已确认该 run 不再继续；必须有稳定 <code>errorCode</code>。
- <code>COMPLETED</code>：最终卡片、候选（若有）、checkpoint 指针、方案槽和终态已一次原子提交。
- <code>FAILED</code>：其他明确终结，必须有稳定 <code>errorCode</code>；不能掩盖未知提交结果。

允许的安全迁移为：

~~~text
RUNNING -> CANCEL_REQUESTED | STOPPED | INTERRUPTED | COMPLETED | FAILED
CANCEL_REQUESTED -> STOPPED | INTERRUPTED | FAILED
~~~

四个终态不可改变、倒退或复活。每个 thread 同时最多一个非终态 run；<code>CANCEL_REQUESTED</code> 仍占用运行槽，新 run 必须返回 <code>RUN_ALREADY_ACTIVE</code>。

完成与取消竞争由原子脚本裁决：完成先提交则为 <code>COMPLETED</code>，随后取消只返回已完成状态；取消先提交则关闭结果、事件和 checkpoint 门，迟到完成必须被拒绝。不得仅凭十秒等待、租约到期或本地发出 cancel 就写 <code>STOPPED</code> 或启动重叠 run。

### 8.2 事件共同字段

每个持久 run 事件共同字段为：

~~~json
{
  "assistantContractVersion": "assistant-card-v3-draft-r1",
  "tourismContractVersion": "tourism-api-v3-draft-r3",
  "eventVersion": "assistant-event-v3-draft-r1",
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": "00000000-0000-0000-0000-000000000000",
  "eventSequence": 1,
  "runState": "RUNNING",
  "emittedAt": "2026-09-10T01:00:00Z",
  "type": "run_started",
  "data": {}
}
~~~

<code>eventSequence</code> 在一个 run 内从 1 连续递增。服务端以原子比较 <code>lastEventSequence+1</code> 分配序号；同序号同摘要为重放，同序号异摘要为 <code>EVENT_SEQUENCE_CONFLICT</code>，小序号为 stale，大于下一序号为 <code>EVENT_SEQUENCE_GAP</code>。终态事件唯一且最后；终态后、epoch 已变化或 run 不匹配时，旧事件统一被 <code>RUN_FENCE_CLOSED</code> 拒绝。

### 8.3 严格事件联合

| <code>type</code> | <code>data</code> 精确字段 | 规则 |
| --- | --- | --- |
| <code>run_started</code> | <code>agentType,retrievalMode,startedAt,checkpointRevision</code> | 恒为序号 1；checkpoint revision 可为 null |
| <code>progress</code> | <code>stage,status,completedUnits,totalUnits</code> | 只允许固定阶段与计数 |
| <code>tool_summary</code> | <code>toolCategory,operationCode,finalStatus,itemCount,durationMs</code> | 仅稳定枚举、计数与耗时 |
| <code>cancel_requested</code> | <code>requestedAt,displayCode</code> | <code>displayCode=CANCEL_REQUESTED</code> |
| <code>card_ready</code> | <code>card,checkpointRevision</code> | 唯一可携带卡片正文的事件 |
| <code>completed</code> | <code>finishedAt,checkpointRevision,currentSolution,previousSolution</code> | 最终可靠提交后 |
| <code>stopped</code> | <code>finishedAt,errorCode,checkpointRevision,currentSolution,previousSolution</code> | error 固定为 CANCELLED_BY_USER |
| <code>interrupted</code> | <code>finishedAt,errorCode,checkpointRevision,currentSolution,previousSolution</code> | 明确本 run 不再继续 |
| <code>failed</code> | <code>finishedAt,errorCode,retryable,checkpointRevision,currentSolution,previousSolution</code> | 稳定错误，不含异常正文 |

<code>progress.stage</code> 只允许 <code>UNDERSTANDING|RETRIEVING|VERIFYING_KNOWLEDGE|PLANNING|PREPARING_RESULT|PERSISTING_RESULT</code>；<code>status</code> 只允许 <code>STARTED|COMPLETED|SKIPPED</code>。<code>completedUnits</code> 为非负整数；<code>totalUnits</code> 为正整数或 <code>null</code>。

<code>tool_summary.toolCategory</code> 只允许 <code>CATALOG|KNOWLEDGE|PERSONAL_PLANNING</code>；<code>operationCode</code> 和 <code>finalStatus</code> 只使用实施时冻结的稳定枚举，不能使用工具名、路由或自由文本；<code>itemCount</code>、<code>durationMs</code> 为非负整数。

阶段事件禁止用户原文、自由文本 message、知识正文、来源标题、引用、模型 token、提示词、工具参数／结果、内部 URL、路径或异常。身份和 Redis 还必须在每个可能公开输出前重新核验。

最终卡片提交时，原子脚本同时持久化并预留连续的 <code>card_ready</code> 与 <code>completed</code>；错误卡可与 <code>failed</code> 形成同样的原子事件对。服务端按序发送，客户端先缓冲 <code>card_ready</code>，收到紧随其后的匹配终态或通过状态恢复确认终态后才提升卡片。若无法可靠持久化事件对，不得发送卡片正文。

### 8.4 <code>session_state</code>

<code>session_state_request</code> 成功返回连接级快照，不占用 run 的 <code>eventSequence</code>：

~~~json
{
  "assistantContractVersion": "assistant-card-v3-draft-r1",
  "tourismContractVersion": "tourism-api-v3-draft-r3",
  "stateVersion": "assistant-session-state-v3-draft-r1",
  "type": "session_state",
  "threadId": "00000000-0000-0000-0000-000000000000",
  "logicalExpiresAt": "2026-10-10T01:00:00Z",
  "checkpointRevision": 3,
  "run": {
    "runId": "00000000-0000-0000-0000-000000000000",
    "runState": "CANCEL_REQUESTED",
    "lastEventSequence": 4,
    "startedAt": "2026-09-10T01:00:00Z",
    "cancelRequestedAt": "2026-09-10T01:01:00Z",
    "finishedAt": null,
    "errorCode": null
  },
  "canStartNewRun": false,
  "lastCardStatus": "EMPTY",
  "lastCard": null,
  "currentSolution": {
    "slot": "CURRENT",
    "status": "EMPTY",
    "solution": null,
    "code": null
  },
  "previousSolution": {
    "slot": "PREVIOUS",
    "status": "EMPTY",
    "solution": null,
    "code": null
  }
}
~~~

<code>checkpointRevision</code> 与 <code>run</code> 都可为 <code>null</code>。非空 run 的字段固定为示例中的七项，各可空时间／错误严格按第 8.1 节状态决定。<code>lastCardStatus</code> 只允许 <code>EMPTY|AVAILABLE|KNOWLEDGE_UPDATED|LOOKUP_UNAVAILABLE|INCOMPATIBLE</code>；仅 AVAILABLE 时 <code>lastCard</code> 为通过恢复期重新核验的完整卡片，其余状态为 <code>null</code>。<code>canStartNewRun</code> 只有无 <code>RUNNING|CANCEL_REQUESTED</code> 且当前身份、会话和 checkpoint 可确认时才为 <code>true</code>。读取故障不得伪装为没有记录或版本不兼容。

### 8.5 有限重连

- 断线后最多自动重连 3 次，固定退避 1／2／4 秒，整个自动重连窗口不超过 15 秒。
- 每个新连接都重新执行 Java v3 第 9 节首帧鉴权；<code>auth_ok</code> 后只发送一次已有 thread 的 <code>session_state_request</code>。
- 重连不自动重发 generate、cancel、候选采用、保存或订单请求，不恢复同一 run 生成，也不承诺补回所有事件。
- 若状态仍为 <code>RUNNING|CANCEL_REQUESTED</code>，界面保留已提交旧卡和阶段状态，禁止新生成；<code>CANCEL_REQUESTED</code> 文案只能是“正在确认停止”。
- USER access JWT 到期时，旧连接立即停止保护输出并关闭；只有同次 Java 登录仍有效时，客户端才可通过 Java HTTP 刷新后新建连接。连接内不得换证，refresh token 不交给 Python。
- 自动预算耗尽后只提供人工重连或重新登录。匿名过期、登录撤销、限频和未停止运行均不自动重试。

断线触发的是尽力取消。显式用户取消在执行体静默后终结为 <code>STOPPED/CANCELLED_BY_USER</code>；断线、认证失效或服务重启在确认不会继续后终结为对应 <code>INTERRUPTED</code>。若取消等待 10 秒仍不能确认静默，状态保持 <code>CANCEL_REQUESTED</code>，三道门继续关闭且运行槽继续占用。

## 9. Checkpoint、异步 Saver 与原子 run 围栏

### 9.1 <code>CheckpointEnvelopeV3</code>

checkpoint 是经过严格验证和隐私清洗的不可变完整快照：

~~~json
{
  "assistantContractVersion": "assistant-card-v3-draft-r1",
  "tourismContractVersion": "tourism-api-v3-draft-r3",
  "checkpointVersion": "assistant-checkpoint-v3-draft-r1",
  "checkpointRevision": 3,
  "parentCheckpointRevision": 2,
  "committedByRunId": "00000000-0000-0000-0000-000000000000",
  "committedByFenceEpoch": 3,
  "createdAt": "2026-09-10T01:02:03Z",
  "state": {
    "intent": "ITINERARY",
    "confirmedConditions": {
      "travelDate": null,
      "visitAt": null,
      "checkInDate": null,
      "checkOutDate": null,
      "roomCount": null,
      "peopleCount": null,
      "preferences": [],
      "selectedTargets": [],
      "merchantItems": null
    },
    "conversationSummary": "用户希望安排两天的乌东慢游。",
    "lastCommittedCard": null,
    "currentSolution": null,
    "previousSolution": null,
    "publicToolSummaries": [],
    "sessionStatus": "ACTIVE"
  },
  "checkpointDigest": "sha256:0000000000000000000000000000000000000000000000000000000000000000"
}
~~~

约束：

- <code>checkpointRevision</code> 在 thread 内从 1 单调递增；第 1 版的 <code>parentCheckpointRevision=null</code>，后续必须精确等于提交前当前 revision。
- <code>checkpointDigest</code> 对除自身外的完整规范对象计算；同 revision 同摘要可重放，同 revision 异摘要永不覆盖。
- <code>intent</code> 只允许 <code>UNKNOWN|KNOWLEDGE|SERVICE|ITINERARY|FOOD_DRAFT|STAY_DRAFT</code>。
- <code>conversationSummary</code> 为确定性脱敏、最多 1,000 个码点的结构化语义摘要，不是原始对话或 messages。
- <code>lastCommittedCard</code> 为 <code>null</code> 或内部记录：<code>cardRevision,card,knowledgeContext,knowledgeDependencies,committedAt</code>。只有通过终验并可靠提交的完整卡片可写入。
- 内部 <code>currentSolution/previousSolution</code> 为 <code>null</code> 或第 7.1 节 <code>SolutionSnapshot</code>。
- <code>publicToolSummaries</code> 最多 20 项，只使用第 8.3 节脱敏工具摘要字段。
- <code>sessionStatus</code> 只允许 <code>NEW|ACTIVE|WAITING_FOR_USER|DEGRADED</code>，它不是 run 围栏或授权事实。

<code>threadId</code> 不复制到 state 或 envelope；它只作为 Saver 配置和 Redis 访问元数据。唯一例外是严格 <code>CandidateRef.threadId</code>，因为它属于 Java v3 不可变候选引用。账号 ID、owner 摘要、JWT、匿名凭据及登录状态只存访问元数据，不进入模型 state。

### 9.2 单轮 <code>RunContextV3</code>

原始 <code>userText</code>、当前连接、认证结果、owner、runId、fenceEpoch、clientRequestId、页面动作、当前选择、知识 evidence 正文、工具调用对象、取消信号和 deadline 都是单轮运行数据，不属于 checkpoint。节点不得通过返回值、messages、工具摘要、卡片附加键或异常对象把它们隐藏写入 state。

中间 checkpoint 也执行同一严格白名单；不得以“还没最终输出”为由保存原始 evidence、模型草稿或用户原文。恢复后的新 run 可读取最后一个已可靠提交 checkpoint 的脱敏条件和摘要，但绝不恢复旧的已中断 run 的原节点、token 流或外部调用。

### 9.3 选定的 Saver 组合

v3 选择以下实现方向：

- Python <code>>=3.11,<3.14</code>；
- LangGraph <code>>=1.1,<2</code>；
- 维护方 <code>langgraph-checkpoint-redis</code> 的异步 Redis Saver／<code>AsyncRedisSaver</code> 能力；
- <code>redis.asyncio</code>，redis-py <code>>=5,<7</code>；
- 固定 Redis <code>7.4.x</code> 镜像并使用持久卷，不使用 <code>latest</code>。

实施时必须把实际解析出的 LangGraph、Redis checkpointer、redis-py、Redis 7.4 及其 Saver 所需 JSON／Search 模块版本锁入依赖和镜像；在同一组合上证明异步 put/get、初始化索引、TTL、事务／Lua 和重启恢复后才可启用。本文不虚构尚未解析的补丁版本。

必须用自有 <code>FencedAsyncRedisSaver</code> 包装维护方 Saver：Saver 只负责不可变 blob 与读取能力，当前指针、run 状态和迟到写回隔离由第 9.5 节 Lua／CAS 保证。禁止直接用 stock Saver 的 latest 查询绕过受控指针，禁止在 event loop 中使用同步 Redis 客户端，也禁止在 Redis 不可用时回退 <code>InMemorySaver</code> 并宣称持久恢复。

能力门分开处理：

| 能力门 | 故障只阻塞 | 不得扩大为 |
| --- | --- | --- |
| checkpoint Redis／异步 Saver | 对话恢复、生成、checkpoint 提交 | 关键词公开知识、Java 业务 REST 全停 |
| candidate Redis | 候选生成、精确解析和采用 | 已保存成果不可读 |
| Redis Search／vector 索引 | VECTOR 发布和检索 | KEYWORD_DEMO 不可用 |
| Embedding 配置／服务 | VECTOR 新构建和查询 | 三 Agent 无知识能力、身份和业务不可用 |

### 9.4 Redis 键与受控指针

<code>threadKey</code> 为规范 <code>threadId</code> 的 SHA-256，不是授权。一个 thread 的所有键使用相同 Redis Cluster hash tag <code>{threadKey}</code>：

~~~text
wd:a3:{threadKey}:meta
wd:a3:{threadKey}:run:{runId}
wd:a3:{threadKey}:request:{clientRequestId}
wd:a3:{threadKey}:checkpoint:{checkpointRevision}
wd:a3:{threadKey}:candidate:{candidateId}:{candidateVersion}
wd:a3:{threadKey}:candidate-index
wd:a3:{threadKey}:event:{runId}:{eventSequence}
~~~

- <code>meta</code> 保存 ownerKind／ownerDigest、logicalExpiresAt、fenceEpoch、currentRunId／state、三道 gate、当前 checkpoint revision／key／digest 及当前／上一方案指针。
- <code>run</code> 保存 epoch、请求摘要、状态、开始／取消／结束时间、稳定错误码、base/result checkpoint revision 及最后事件序号／摘要。
- <code>request</code> 只保存 runId 和规范请求摘要，不保存 userText。
- checkpoint 与 candidate 值是严格、清洗后的不可变对象；未获原子指针或 ready 标记的 prepared blob 不可读取为当前状态或可解析候选。
- Saver 自身 pending writes 必须携带 runId+epoch，并受同一 checkpoint gate。不得扫描物理最大 revision 当作 current。

逻辑期限由 meta 控制，读取不自动续 TTL。USER thread 只在 accepted generate 后更新为服务器当前时间加 30 天；匿名期限只采用 Java 原子交互回执。候选自身 <code>expiresAt</code> 创建后不变。对话到期不会删除 Java／MySQL 已保存行程、草稿或订单。

### 9.5 必须原子的操作

以下操作都在取得服务器时间后用 Lua 或等价单 Redis 原子 CAS 完成；“先读 runId，再普通写入”不合格。

1. <code>start_run</code>
   - 核对版本、owner、logicalExpiresAt、expected checkpoint revision 和当前无 <code>RUNNING|CANCEL_REQUESTED</code>。
   - 同 clientRequestId 同摘要返回原 run；同 ID 异摘要返回 <code>RUN_REQUEST_CONFLICT</code>。
   - 原子递增永不复用的 <code>fenceEpoch</code>，生成并绑定服务端 runId，打开 <code>resultGate/eventGate/checkpointGate</code>，写 <code>RUNNING</code> 与序号 1。

2. <code>append_event</code>
   - 同时比较 currentRunId、fenceEpoch、允许状态、eventGate 和期望下一序号。
   - 先可靠写事件及摘要，再允许 WebSocket 发送；旧 epoch、关闭 gate 或终态一律拒绝。

3. <code>commit_checkpoint</code>
   - 先以不可变键写严格 blob，回读并核对摘要，形成同 hash tag 的 ready receipt。
   - CAS 再比较 currentRunId、epoch、<code>checkpointGate=OPEN</code>、状态为 RUNNING、当前 revision 等于 parent、receipt 摘要一致，才推进 meta 当前指针。
   - CAS 失败的 prepared blob 永远不能被 latest 读取，可由短 TTL 后清理。

4. <code>request_cancel</code>
   - 仅把 RUNNING 原子改为 CANCEL_REQUESTED，同时关闭三道 gate 并写唯一 <code>cancel_requested</code> 事件。
   - 之后才向本机执行器发尽力取消；重复请求返回原状态，终态返回既有终态。

5. <code>commit_terminal_card</code>
   - 最终卡片、候选、checkpoint 均先严格验证、清洗、摘要、写 prepared blob 并回读核对。
   - 对含知识依赖的结果，必须在进入脚本前完成第 10 节交付前全量复核。
   - 脚本再次比较 currentRunId、epoch、RUNNING、三道 gate OPEN、parent revision、所有 ready receipt、候选版本／摘要／期限。
   - 对成功卡一次提交候选可见标记、checkpoint 指针、last card、方案旋转、COMPLETED、finishedAt、连续 <code>card_ready/completed</code> 事件并关闭 gate。
   - 对严格错误卡一次提交 last card、FAILED、errorCode、连续 <code>card_ready/failed</code> 事件；不创建候选、不旋转方案。
   - 任一条件失败则所有指针和终态均不改变。

6. <code>finish_without_card</code>
   - STOPPED、INTERRUPTED 或无法形成卡片的 FAILED 一次关闭 gate、写 finishedAt／errorCode 和唯一终态事件。
   - STOPPED 必须另有执行器 quiesced 证明；只到等待上限不能调用此分支。

终态后或新 epoch 后，旧 run 的模型结果、工具回调、事件、候选、checkpoint 和用户输出影响必须为零，并返回内部 outcome <code>REJECTED_STALE_RUN</code>。提交结果不可确认时不得用重试覆盖未知状态；先读取同一键的持久事实。

### 9.6 Java 脱敏运行摘要

本地 Redis 状态提交后，Python 才可调用 <code>POST /internal/agent/run-summaries</code>。严格请求逐字段为：

~~~json
{
  "threadId": "00000000-0000-0000-0000-000000000000",
  "runId": "00000000-0000-0000-0000-000000000000",
  "summarySequence": 4,
  "runState": "COMPLETED",
  "agentType": "ITINERARY_PLANNER",
  "retrievalMode": "KEYWORD_DEMO",
  "startedAt": "2026-09-10T01:00:00Z",
  "finishedAt": "2026-09-10T01:02:00Z",
  "knowledgeDependencies": [],
  "candidateRefs": [],
  "errorCode": null
}
~~~

<code>RUNNING|CANCEL_REQUESTED</code> 要求 <code>finishedAt=null,errorCode=null</code>；<code>COMPLETED</code> 要求结束时间非空且错误为空；<code>STOPPED</code> 要求结束时间非空且错误固定 <code>CANCELLED_BY_USER</code>；<code>INTERRUPTED|FAILED</code> 要求二者非空。candidateRefs 最多 20 项、不得重复且 threadId 必须匹配顶层；<code>retrievalMode=NONE</code> 时依赖必须为空。

Java summary 只允许在相应 Redis 状态已经提交后投影；调用失败不回滚本地完成状态，同一 sequence+digest 可有限重试。其 <code>APPLIED|REPLAYED|STALE_IGNORED</code> 结果不能反向驱动 Python 状态、替代 checkpoint／run 指针或证明客户端已收到内容。

## 10. 知识发布、检索与双核验

### 10.1 唯一事实与发布边界

<code>knowledge_document</code>、<code>knowledge_source</code>、<code>knowledge_publish_task</code> 及固定 <code>liveSnapshot</code> 都由 Java／MySQL 管理。Python 不创建或修改这些表，不决定 <code>PUBLISHED</code>，也不把知识草稿、来源主档最新值、准备中向量块或旧 build 当成公开知识。

Java → Python 构建只走 <code>POST /internal/knowledge-builds/{taskId}</code>；Python 在切块、Embedding 或写 Redis 前，必须用独立 <code>AI_TO_JAVA</code> 凭据调用 <code>GET /internal/agent/knowledge-build-inputs/{taskId}</code>，逐字段比较路径 taskId、固定输入、snapshotHash、IndexConfig、configHash 和 deadlineAt。任务不是当前有效 RUNNING 或任一字段不一致时，不调用 Embedding、不写 Redis、不返回 READY。

VECTOR 的 <code>buildId=taskId</code>。真实 READY 必须证明全部预期非空 chunk 已按本 build 隔离写入、向量维度及有限 FLOAT32 合法、生产查询索引按 <code>documentId+buildId+configHash</code> 可回读相同 chunkId 集合、manifest 一致且未过 deadline。写命令成功、全局计数、Embedding 成功或 manifest 自报 READY 都不能单独证明 READY。

### 10.2 固定检索配置

KEYWORD_DEMO 的 IndexConfig 固定为：

~~~json
{
  "retrievalMode": "KEYWORD_DEMO",
  "schemaVersion": "knowledge-keyword-v1",
  "matchingAlgorithm": "JAVA_LITERAL_TOKEN_V1",
  "maxResults": 10
}
~~~

VECTOR 的 IndexConfig 固定字段为：

~~~json
{
  "retrievalMode": "VECTOR",
  "schemaVersion": "knowledge-vector-v1",
  "embeddingProvider": "configured-provider",
  "embeddingModel": "configured-model",
  "vectorDimension": 1024,
  "vectorDataType": "FLOAT32",
  "distanceMetric": "COSINE",
  "chunkAlgorithm": "UNICODE_CODEPOINT_V1",
  "chunkMaxCodePoints": 1000,
  "chunkOverlapCodePoints": 120,
  "embeddingBatchSize": 16,
  "maxResults": 10
}
~~~

provider、model 和 1～4,096 的 dimension 必须由真实外部配置明确提供；endpoint 是服务端绝对 HTTPS 地址，credential file 是仓库外绝对路径。端点、密钥和请求头不进入 IndexConfig、模型、checkpoint 或日志。Python 必须重算并匹配 Java configHash，不能补默认维度、换供应商或忽略未知字段。

### 10.3 入模前核验

<code>VECTOR</code>：

1. 本 run 固定 mode/configHash，调用 <code>GET /internal/agent/knowledge/active-builds?configHash=...</code>，结果不得跨 run 缓存。
2. Redis 命中必须携带 <code>documentId,buildId,snapshotHash</code>，先与 active-builds 按三字段精确相交。
3. 对候选调用 <code>POST /internal/agent/knowledge/eligibility</code>；请求 candidate 逐项只含 <code>documentId,buildId</code>，不得多传 snapshotHash。
4. 只有 <code>eligible=true</code> 的完整 Java <code>KnowledgeEvidence</code> 可入模；<code>eligible=false</code> 时响应必须省略 evidence。Python 再比较 evidence 摘要与 Redis／active-builds 摘要。
5. Redis chunk 正文只参与召回，绝不能直接作为可信入模正文。

<code>KEYWORD_DEMO</code> 只调用 Java <code>GET /internal/agent/knowledge/search</code>，使用其当前 liveSnapshot 的完整 evidence；不调用 Redis Search 或 Embedding。响应的 mode、关键词 configHash 和 checkedAt 必须明确。VECTOR 失败不能在同一 run 中改用此路由；改用关键词必须由用户主动创建明确选择新模式的新 run。

Java <code>KnowledgeEvidence</code> 精确字段为 <code>documentId,buildId,snapshotHash,title,content,tags,region,periodText,evidenceCategory,usageLimitations,demoData,references</code>，仅 region／periodText 可为 null。Python 保存实际入模 evidence 的完整依赖三元组，不保存 evidence 正文到 checkpoint。

入模前部分 <code>eligible=false</code> 可剔除对应候选；若最终无 evidence，不能生成 knowledge_answer。eligibility 整体 503 属于不可确认，不能使用缓存或部分响应。

### 10.4 交付前全量复核

模型输出先停留在不可交付的运行缓冲区。在任何知识派生正文、引用或候选通过 <code>card_ready</code> 新近可见前，Python 必须：

1. 对本轮全部实际入模的去重 <code>KnowledgeDependency[]</code>，使用同一 <code>retrievalMode/configHash</code> 再次调用 eligibility。
2. 要求结果与请求同序一一对应、全部 eligible，并逐项比较 documentId、buildId、snapshotHash。
3. 以复核返回的 Java evidence 生成第 3.3 节引用并再次严格验证最终卡片。
4. 在同一当前 run 围栏内原子提交卡片、候选、checkpoint 和终态，之后才发送。

任一依赖失效、换版、摘要变化或调用不可确认时，丢弃全部尚未交付的知识派生正文、引用和候选；不得只核验模型列出的来源、只删链接、部分交付、回退旧 chunk 或从 checkpoint 恢复旧授权。已交付历史内容不承诺撤回，但下次显示、恢复、生成或候选 resolve 都须重新核验。

已知换版使用稳定错误 <code>KNOWLEDGE_UPDATED</code>；资格不可确认使用 <code>KNOWLEDGE_ELIGIBILITY_UNAVAILABLE</code>。<code>RETRIEVAL_MODE_MISMATCH</code>、<code>VECTOR_CONFIGURATION_UNAVAILABLE</code> 和 <code>VECTOR_BUILD_NOT_READY</code> 不得呈现为关键词成功。

### 10.5 有限参数与局部失败

| 配置 | 固定值／规则 |
| --- | --- |
| <code>AI_RATE_WINDOW_SECONDS / AI_RATE_REQUESTS</code> | 同账号或匿名会话 60 秒最多 6 次 accepted generate |
| <code>AI_THREAD_CONCURRENCY / AI_GLOBAL_CONCURRENCY</code> | 1／3 |
| <code>AI_RUN_DEADLINE_SECONDS</code> | 180 |
| <code>AI_CANCEL_WAIT_SECONDS</code> | 10；到时仍未静默则保持 CANCEL_REQUESTED |
| <code>AUTH_RECHECK_SECONDS</code> | 5；输入、私人工具和公开输出仍逐次核验 |
| <code>KNOWLEDGE_PUBLISH_DEADLINE_SECONDS</code> | 180 |
| <code>KNOWLEDGE_BUILD_CONNECT_TIMEOUT_SECONDS</code> | 5 |
| <code>KNOWLEDGE_BUILD_RESPONSE_TIMEOUT_SECONDS</code> | 120，且不超过任务剩余期限 |
| <code>KNOWLEDGE_BUILD_CONCURRENCY</code> | 1 |
| <code>KNOWLEDGE_SEARCH_MAX_RESULTS</code> | 10 |
| <code>EMBEDDING_HTTP_TIMEOUT_SECONDS</code> | 30，且不超过任务剩余期限 |

外部 Embedding 参数或兼容 Redis Search 模块缺失时，只关闭 VECTOR 新构建和检索；KEYWORD_DEMO、三个 Agent 的无知识能力、身份、目录、业务 REST、已保存成果和现有公开快照继续工作。不得调用第三方 Marketplace、自动换供应商、改维度或把关键词结果标成 VECTOR。

## 11. 隐私、日志与 LangSmith

### 11.1 卡片与 checkpoint 白名单

允许持久化的内容仅限：

- 版本、revision、固定状态、稳定错误码、计数与时间；
- 已确认的结构化公开条件和脱敏 conversationSummary；
- 已通过最终核验并可靠提交的严格 card；
- 当前／上一 <code>SolutionSnapshot</code>；
- 严格 CandidateRef、候选信封、KnowledgeContext 和 KnowledgeDependency；
- 第 8.3 节脱敏工具摘要。

禁止持久化、输出或追踪：

- 原始 userText、messages、提示词、思维链、模型 token、未核验模型草稿；
- KnowledgeEvidence 正文、Redis chunk／vector、原始工具输入输出或参数；
- 联系人、电话、私人备注、账号 ID、visitorId；
- JWT、Cookie、refresh token、匿名凭据、服务凭据、API key；
- 原始来源 URL、本机路径、仓库路径、私有对象键、临时签名参数；
- 原始异常、堆栈、供应商请求／响应或完整内部 DTO。

公开本站 <code>detailPath</code> 和严格 CandidateRef 是显式 DTO 例外，不得扩展成任意 URL 或任意 thread 字段。隐私清洗发现敏感字段时默认抛出不含原键和值的稳定错误；不得记录“被删除的原内容”。

### 11.2 LangSmith

LangSmith 默认关闭原文自动追踪。允许发送的逐 run 白名单只含：

~~~text
runState
agentType
retrievalMode
knowledgeDependencyCount
candidateCount
errorCode
~~~

不得发送 threadId、runId、任何 ID 数组、完整 run-summary JSON、正文、标题、引用、用户输入、提示词、工具内容、联系人、凭据或异常。观测写入失败不阻塞已经由本地围栏可靠提交的业务结果，但也不得放宽隐私或改写运行状态。

普通日志同样不得组合记录 threadId、runId 与 candidateRef。需要关联时只使用短期不可逆诊断摘要和稳定码；日志中不得输出 Redis 键、configHash、candidateDigest 或正文。

## 12. 稳定错误与恢复边界

Assistant 协议至少冻结下列稳定码：

| 范围 | code |
| --- | --- |
| 运行 | <code>RUN_ALREADY_ACTIVE</code>、<code>RUN_REQUEST_CONFLICT</code>、<code>RUN_NOT_FOUND</code>、<code>RUN_NOT_ACTIVE</code>、<code>RUN_STATUS_UNAVAILABLE</code>、<code>RUN_CANCEL_UNAVAILABLE</code>、<code>RUN_FENCE_CLOSED</code> |
| 事件 | <code>EVENT_SEQUENCE_GAP</code>、<code>EVENT_SEQUENCE_CONFLICT</code> |
| checkpoint | <code>CHECKPOINT_INCOMPATIBLE</code>、<code>CHECKPOINT_UNAVAILABLE</code>、<code>CHECKPOINT_SEQUENCE_CONFLICT</code> |
| 知识／向量 | <code>KNOWLEDGE_UPDATED</code>、<code>KNOWLEDGE_NO_ELIGIBLE_SOURCE</code>、<code>KNOWLEDGE_ELIGIBILITY_UNAVAILABLE</code>、<code>RETRIEVAL_MODE_MISMATCH</code>、<code>VECTOR_CONFIGURATION_UNAVAILABLE</code>、<code>VECTOR_BUILD_NOT_READY</code> |
| 候选 | <code>CANDIDATE_EXPIRED</code>、<code>CANDIDATE_VERSION_UNAVAILABLE</code>、<code>CANDIDATE_PAYLOAD_INVALID</code>、<code>CANDIDATE_BASE_MISMATCH</code>、<code>CANDIDATE_LOOKUP_UNAVAILABLE</code> |
| 明确中断 | <code>CLIENT_DISCONNECTED</code>、<code>AUTH_EXPIRED</code>、<code>SESSION_REVOKED</code>、<code>ANONYMOUS_EXPIRED</code>、<code>SERVICE_RESTARTED</code>、<code>DEPENDENCY_INTERRUPTED</code> |
| 明确失败 | <code>MODEL_UNAVAILABLE</code>、<code>TOOL_UNAVAILABLE</code>、<code>FINAL_VALIDATION_FAILED</code>、<code>CHECKPOINT_COMMIT_FAILED</code> |

Redis／身份／资格／提交不可确认都必须保持“不可确认”语义，不能改写为不存在、过期、未执行或成功。旧 checkpoint 只有在先验证访问权且确实识别出旧 schema 后才返回 <code>CHECKPOINT_INCOMPATIBLE</code>；不得迁移成 v3、清空、补期限或自动重发。无法证明归属、读取故障和到期分别处理。

Python 重启时先关闭所有旧进程内执行体，再对 Redis 中遗留 RUNNING／CANCEL_REQUESTED 做围栏核对；确认旧执行体不可能继续后才写 <code>INTERRUPTED/SERVICE_RESTARTED</code>。不自动续算旧 run。已经由 Java 成功保存的行程、草稿、订单和发布任务不因此撤销。

## 13. v2 → v3 不兼容清单

| v2 | v3 |
| --- | --- |
| <code>cardVersion="2.0"</code> | <code>"3.0"</code>，严格新消费者 |
| <code>PublicSource{title,documentId,sourceType,demoData}</code> | <code>KnowledgeReference{sourceTitle,detailPath}</code> |
| 行程 <code>items/timeText/summary</code>、1～7 天 | Java <code>content.days.stops</code>、连续 sequence、1～30 天 |
| 推荐 <code>price:number</code> | Java ReferencePrice／DemoPrice，金额为两位小数字符串 |
| 旧 <code>ConfirmedConditions</code> | 增加 checkOutDate、roomCount、merchantItems |
| 单餐品 <code>targetId</code> 提案 | 同店 merchantId＋多 items |
| <code>FOOD_ORDER|STAY_BOOKING</code> 候选语义 | <code>FOOD_DRAFT|STAY_DRAFT</code>，采用只保存草稿 |
| 食宿候选 note、sourceThreadId、targetName、联系人占位 | 全部删除；Java v3 食宿 payload 只含规划字段 |
| <code>OPEN_ORDER_CONFIRMATION</code>／直接加入 | 三个精确候选采用动作；公共请求只交 CandidateRef |
| 通用 currentCard／previousCard | last card 与 current／previous candidate solution 分离 |
| <code>progress.message</code>、带来源标题 tool summary | 固定 stage／码／计数，不提前泄漏内容 |
| DTO 校验后立即 card_ready | 完整知识复核＋原子 run 提交后才形成 card_ready／终态事件对 |
| <code>error.demoAvailable</code> | 删除；VECTOR 失败不得自动展示关键词成功 |
| <code>InMemorySaver</code> 可运行即视为恢复 | 异步 Redis Saver＋自有原子围栏；Redis 失败不降级 |

旧 candidate、checkpoint、事件和卡片不得转换成 v3 引用、延长期限或自动采用。Java、Python、Web 与原生小程序必须一起升级版本和严格解析器，不长期维护双业务链。

## 14. 契约完成边界

本文已经冻结：

- 六类卡片及各自严格 data；
- 三类不可变候选、基础资源版本和 Java 精确采用；
- 当前／上一完整方案的两槽展示与候选独立保留；
- RUNNING、CANCEL_REQUESTED、STOPPED、INTERRUPTED、COMPLETED、FAILED 及取消／完成竞争；
- 事件序列、最终卡片原子提交、有限重连和主动继续；
- checkpoint 版本、Redis 键、异步 Saver 组合和原子 run 围栏；
- Java 固定生效知识、入模前／交付前双核验及公开引用；
- LangSmith、日志、checkpoint 和事件隐私白名单；
- checkpoint Redis、candidate Redis、Redis Search 与 Embedding 的局部失败边界。

本文没有安装依赖、修改产品代码、迁移数据、启动服务、调用模型／Embedding、执行测试或提供集成证据。后续实现只有在严格消费者成组接线、依赖组合实际锁定并通过独立验收后，才可声明 v3 已运行。
