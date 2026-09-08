# 贵州乌东前端重设计实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在三天答辩原型范围内，把现有通用页面改造成“点击开卷 → 山水寨茶人 → 沿溪入寨 → 一叶同行”的贵州乌东 Web 体验，并完成工具优先微信小程序和诚实可操作的 AI 对话工作台。

**架构：** 保留现有 Vue 3 hash 路由、原生微信小程序、Java 业务服务和 Python LangGraph 服务。先统一本地图片、展示名称与 `AgentCard` 契约；Python 通过 LangGraph 自定义流事件向两端发送真实阶段，通过进程内 checkpointer 保留当前线程上下文；Web 与小程序分别实现适合自身终端的界面，不新增跨端框架。

**技术栈：** Vue 3、Vite、原生微信小程序、Python 3.11、FastAPI、LangGraph 1.1、DeepSeek OpenAI 兼容 API、Java 21、Spring Boot、MySQL、Redis Stack、WebSocket。

---

## 实施约束

- 本计划服从项目的“开发优先”规则：不创建自动化测试文件，不运行单元、集成、全量或回归测试。
- 每项任务只包含代码实施、变更范围核对和独立提交。用户可见验收由文末清单完成。
- 本机照片可用于原型，但来源、地点、版权或肖像许可未补齐前不得进入公开 Git 仓库。
- 当前 `.gitignore` 已有与本计划无关的未提交内容；任务 1 必须只暂存新增的三条素材规则，不得把既有 `.superpowers/` 行顺带提交。
- “山、水、寨、茶、人”只是首页叙事，不新增后端分类、数据表或 CMS。
- 不引入 Vue Router、动画库、WebGL、跨端框架、状态库、登录、支付、地图、天气或完整收藏系统。
- 旧目录名与内部标识 `wudong`、`udong` 不迁移；所有游客可见中文统一为“贵州乌东”。

## 技术依据

- LangGraph 使用 `astream(..., stream_mode=["custom", "updates"], version="v2")`，依据 [LangGraph Streaming 官方文档](https://docs.langchain.com/oss/python/langgraph/streaming)。
- 当前进程多轮状态使用 `InMemorySaver`，并在调用配置中传 `configurable.thread_id`，依据 [LangGraph Persistence 官方文档](https://docs.langchain.com/oss/python/langgraph/persistence)。
- DeepSeek 结构化行程使用 `response_format={"type":"json_object"}`，同时在提示中明确 JSON 结构并设置 `max_tokens`，依据 [DeepSeek JSON Output 官方文档](https://api-docs.deepseek.com/guides/json_mode/)。
- DeepSeek 默认模型及示例环境变量统一为 `deepseek-v4-flash`，依据 [DeepSeek Models & Pricing 官方模型列表](https://api-docs.deepseek.com/quick_start/pricing/)。

## 为什么使用一份计划

Web、小程序和 Python 属于不同运行端，但都依赖同一个 `AgentCard`、同一组服务 UUID、同一套演示/实时状态语义。拆成互不相干的计划会产生卡片字段、阶段名称和服务动作漂移，因此本计划把共享契约放在前面；任务 4～5 的 Web 工作与任务 6～8 的小程序工作可在共享契约完成后并行执行。

## 文件结构与职责

### 创建

| 文件 | 单一职责 |
| --- | --- |
| `scripts/prepare-local-media.ps1` | 从用户素材目录生成 Web 与小程序的本机 JPEG 派生图，不提交图片 |
| `tourism-service/src/main/resources/db/migration/V3__rename_wudong_display_copy.sql` | 修正已执行数据库中的“乌冬”展示数据 |
| `web/src/data/scenes.js` | 五联、探景与下滑章节的唯一图片/焦点/行动清单 |
| `web/src/components/common/SafeImage.vue` | 统一实景加载、懒加载与带语义的破图占位 |
| `web/src/pages/HomePage.vue` | 编排 Web 开卷、五联、探景、下滑故事、叶片和内嵌向导 |
| `web/src/components/home/ScrollGate.vue` | 当前浏览器会话的点击开卷状态与动效降级 |
| `web/src/components/home/FiveSceneHero.vue` | 五联布局、键盘操作、轻舒展和破图占位 |
| `web/src/components/home/SceneExplorer.vue` | 近全屏场景故事层和单一行动 |
| `web/src/components/home/WudongJourney.vue` | 雾转场、沿溪故事和衣食住行节点 |
| `web/src/components/home/LeafGuide.vue` | 雾散后出现的“一叶同行”入口 |
| `web/src/composables/useAssistantSession.js` | Web 稳定线程、WebSocket、阶段映射、卡片去重和演示模式 |
| `web/src/components/assistant/AssistantWorkspace.vue` | Web 对话、四阶段、六类卡片、来源和服务动作 |
| `web/src/pages/ResourcesPage.vue` | Web 资源筛选与服务卡片 |
| `web/src/pages/ResourceDetailPage.vue` | Web 单项服务详情与预约入口 |
| `web/src/pages/CommunityPage.vue` | Web 寨里内容列表 |
| `web/src/pages/BookingPage.vue` | Web 待确认预约与游客二次确认 |
| `web/src/pages/AdminPage.vue` | Web 订单、内容和脱敏运行摘要后台 |
| `miniprogram/utils/media.js` | 小程序本地图片路径和场景语义 |
| `miniprogram/utils/assistant.js` | 小程序卡片规范化、阶段映射和稳定线程帮助函数 |
| `miniprogram/custom-tab-bar/index.{json,js,wxml,wxss}` | 五栏自定义底部导航与中央叶片按钮 |
| `miniprogram/pages/profile/profile.{json,js,wxml,wxss}` | 演示身份、当前行程和预约订单入口 |

### 修改

| 文件 | 修改职责 |
| --- | --- |
| `.gitignore` | 忽略源照片和两端本机派生图片目录 |
| `tourism-service/src/main/resources/application.yml` | 修正服务展示名 |
| `agent-service/pyproject.toml` | 固定支持流式 v2 的 LangGraph 主版本 |
| `agent-service/.env.example` | 与代码默认值统一为当前 DeepSeek 快速模型标识 |
| `agent-service/app/config.py` | 设置可覆盖的 DeepSeek 快速模型默认值 |
| `agent-service/app/contracts.py` | 将知识文档 ID 修正为字符串并保持六类卡片白名单 |
| `agent-service/app/graph/state.py` | 增加可累积消息和实际服务结果 |
| `agent-service/app/graph/builder.py` | 编译一次带进程内 checkpointer 的图 |
| `agent-service/app/graph/nodes.py` | 发真实阶段事件、调用服务、生成同构卡片 |
| `agent-service/app/llm/deepseek_client.py` | 用 JSON 输出生成受服务与来源约束的行程 |
| `agent-service/app/rag/chunker.py` | 将文档 ID 改为字符串 |
| `agent-service/app/rag/indexer.py` | 将文档 ID 改为字符串 |
| `agent-service/app/rag/retriever.py` | 云端向量不可用时使用 Java 关键词资料并标演示模式 |
| `agent-service/app/streaming.py` | 直接转发 LangGraph custom/updates 流，只发一次完整卡片 |
| `agent-service/app/tools/tourism_client.py` | 增加知识搜索并统一服务动作字段 |
| `agent-service/app/main.py` | 修正产品名和字符串文档 ID |
| `agent-service/app/__init__.py` | 修正产品展示名 |
| `web/index.html` | 修正浏览器标题 |
| `web/src/App.vue` | 变为路由、共享业务状态和页面壳层，接入新首页/工作台 |
| `web/src/data/demo.js` | 使用稳定 UUID、本地图片、正确名称与 `AgentCard` 演示结构 |
| `web/src/style.css` | 移除在线字体和通用 Hero，应用品牌令牌与公共页面样式 |
| `miniprogram/app.js` | 增加分类意图、当前行程和稳定助手会话状态 |
| `miniprogram/app.json` | 配置五个主 tab、profile 和正确产品名 |
| `miniprogram/app.wxss` | 应用品牌令牌和底部安全区 |
| `miniprogram/pages/index/*` | 改为工具优先首页和 `switchTab` 导航 |
| `miniprogram/pages/resources/*` | 接收一次性分类意图、修正图片/名称和 tab 状态 |
| `miniprogram/pages/community/*` | 修正品牌文案、图片和 tab 状态 |
| `miniprogram/pages/assistant/*` | 改造成多轮对话工作台并删除定时伪进度 |
| `miniprogram/pages/detail/detail.js` | 使用字符串服务 ID，不再默认错误资源 |
| `miniprogram/pages/detail/detail.wxml` | 修正品牌名和无服务时的操作状态 |
| `miniprogram/pages/booking/booking.js` | 使用字符串服务 ID并保留二次确认 |
| `miniprogram/pages/orders/orders.wxml` | 修正“我的乌东行程”文案 |
| `README.md`、`docs/答辩演示流程.md`、`docs/项目进度.md` | 更新正确名称、视觉主链和真实/演示能力说明 |

### 本期明确不改

- `web/package.json`、`web/vite.config.js`、`web/src/main.js`、`web/src/services/api.js`
- Java Controller、领域记录、预约状态机和已有 API 路由
- MySQL 表结构与内部包名
- 云端 Embedding 的供应商实现；接口未提供前不得假装已完成向量 RAG

## 任务依赖

~~~text
任务 1 素材与语义清单 ───────→ 任务 4 Web 首页、任务 7 小程序首页
任务 2 名称与稳定业务 ID ────→ 任务 4、5、6、7、8
任务 3 AI 完整主链 ──────────→ 任务 5 Web 工作台、任务 8 小程序工作台
任务 4 ─→ 任务 5；任务 6 ─→ 任务 7 ─→ 任务 8
任务 5 + 任务 8 ─────────────→ 任务 9 收束
~~~

### 任务 1：建立安全的本机素材管线与场景清单

**文件：**
- 修改：`.gitignore`
- 创建：`scripts/prepare-local-media.ps1`
- 创建：`web/src/data/scenes.js`
- 创建：`miniprogram/utils/media.js`

- [ ] **步骤 1：阻止未授权照片进入公开仓库**

在 `.gitignore` 保留现有规则，并追加：

~~~gitignore
resours/贵州乌东图片/
web/public/images/wudong-local/
miniprogram/assets/wudong-local/
~~~

- [ ] **步骤 2：实现无外部依赖的本机派生图脚本**

`scripts/prepare-local-media.ps1` 使用 `System.Drawing`，映射固定为：

~~~powershell
$media = @(
    @{ Source = '天光乍雾山.jpg'; Target = 'mountain' },
    @{ Source = '风雨桥.jpg'; Target = 'water' },
    @{ Source = '乌东苗寨.jpg'; Target = 'village' },
    @{ Source = '村寨全景航拍.png'; Target = 'village-aerial' },
    @{ Source = '采茶.png'; Target = 'tea' },
    @{ Source = '老幼相宜——人间幸福.jpg'; Target = 'people' },
    @{ Source = '溪涧穿石.jpg'; Target = 'creek' },
    @{ Source = '田野务农.jpg'; Target = 'labor' }
)

$sourceRoot = Join-Path $PSScriptRoot '..\resours\贵州乌东图片'
$webRoot = Join-Path $PSScriptRoot '..\web\public\images\wudong-local'
$miniRoot = Join-Path $PSScriptRoot '..\miniprogram\assets\wudong-local'
~~~

脚本内的图片导出实现固定为：

~~~powershell
Add-Type -AssemblyName System.Drawing

function Get-JpegEncoder {
    return [Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
        Where-Object { $_.MimeType -eq 'image/jpeg' } |
        Select-Object -First 1
}

function Export-Jpeg {
    param(
        [string]$Source,
        [string]$Target,
        [int]$MaxSide,
        [long]$Quality
    )
    if (-not (Test-Path -LiteralPath $Source)) {
        throw "未找到素材：$Source"
    }
    $sourceImage = [Drawing.Image]::FromFile($Source)
    try {
        $scale = [Math]::Min(1.0, $MaxSide / [Math]::Max($sourceImage.Width, $sourceImage.Height))
        $width = [Math]::Max(1, [int][Math]::Round($sourceImage.Width * $scale))
        $height = [Math]::Max(1, [int][Math]::Round($sourceImage.Height * $scale))
        $bitmap = [Drawing.Bitmap]::new($width, $height)
        try {
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.DrawImage($sourceImage, 0, 0, $width, $height)
            } finally {
                $graphics.Dispose()
            }
            $encoderParameters = [Drawing.Imaging.EncoderParameters]::new(1)
            try {
                $encoderParameters.Param[0] = [Drawing.Imaging.EncoderParameter]::new(
                    [Drawing.Imaging.Encoder]::Quality,
                    $Quality
                )
                $bitmap.Save($Target, (Get-JpegEncoder), $encoderParameters)
            } finally {
                $encoderParameters.Dispose()
            }
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $sourceImage.Dispose()
    }
}

New-Item -ItemType Directory -Force -Path $webRoot, $miniRoot | Out-Null
foreach ($item in $media) {
    $source = Join-Path $sourceRoot $item.Source
    Export-Jpeg $source (Join-Path $webRoot ($item.Target + '-thumb.jpg')) 960 76
    Export-Jpeg $source (Join-Path $webRoot ($item.Target + '-large.jpg')) 1800 82
    Export-Jpeg $source (Join-Path $miniRoot ($item.Target + '.jpg')) 960 74
}
~~~

脚本保持宽高比、拒绝缺失源文件并创建输出目录，且不得覆盖 `resours` 原图。

- [ ] **步骤 3：生成本机派生图片**

运行：

~~~powershell
powershell -ExecutionPolicy Bypass -File scripts/prepare-local-media.ps1
~~~

预期：Web 生成 8 张 `-thumb.jpg` 与 8 张 `-large.jpg`，小程序生成 8 张 `.jpg`，共 24 张派生图；这些文件保持被 Git 忽略。三天原型不新增 WebP/AVIF 编码依赖，Web 以压缩 JPEG 兼容格式交付，公开部署前再补现代格式。

- [ ] **步骤 4：写入 Web 场景清单**

`web/src/data/scenes.js` 使用以下稳定结构；五联顺序不得由模板再次定义：

~~~js
const localThumb = name => `/images/wudong-local/${name}-thumb.jpg`
const localLarge = name => `/images/wudong-local/${name}-large.jpg`

export const scenes = [
  { id: 'mountain', label: '山', src: localThumb('mountain'), expandedAsset: localLarge('mountain'), alt: '云雾覆盖的层叠山岭与山路行人', expandedAlt: '云雾覆盖的层叠山岭与山路行人', desktopFocus: '62% 55%', mobileFocus: '68% 58%', sourceStatus: 'local-review', story: '从湿润山色进入乌东的慢行节奏。', action: { label: '查看山野路线', path: '/resources', category: 'travel' } },
  { id: 'water', label: '水', src: localThumb('water'), expandedAsset: localLarge('water'), alt: '风雨桥、溪水与雾中村寨', expandedAlt: '风雨桥、溪水与雾中村寨', desktopFocus: '50% 55%', mobileFocus: '50% 58%', sourceStatus: 'local-review', story: '桥与水把山路接进村寨日常。', action: { label: '开始沿溪漫游', path: '/resources', category: 'travel' } },
  { id: 'village', label: '寨', src: localThumb('village'), expandedAsset: localLarge('village-aerial'), alt: '标有乌东苗寨名称的村寨全景', expandedAlt: '山谷中沿溪分布的木楼、道路与田地', desktopFocus: '50% 58%', mobileFocus: '50% 62%', sourceStatus: 'local-review', story: '沿木楼之间的路径理解一寨一谷。', action: { label: '查看村寨导览', path: '/resources', category: 'culture' } },
  { id: 'tea', label: '茶', src: localThumb('tea'), expandedAsset: localLarge('tea'), alt: '茶园里正在采茶的人', expandedAlt: '茶园里正在采茶的人', desktopFocus: '49% 50%', mobileFocus: '47% 52%', sourceStatus: 'portrait-review', story: '从采茶、制茶到递出一盏热茶。', action: { label: '进入茶旅体验', path: '/resources', category: 'culture' } },
  { id: 'people', label: '人', src: localThumb('people'), expandedAsset: localLarge('people'), alt: '门前相视而笑的一老一幼', expandedAlt: '门前相视而笑的一老一幼', desktopFocus: '61% 48%', mobileFocus: '60% 48%', sourceStatus: 'portrait-review', story: '劳作、待客与笑声组成寨里的温度。', action: { label: '看寨里人物故事', path: '/community' } }
]

export const journeySections = [
  { id: 'creek', eyebrow: '沿溪入寨', title: '水声把路引向木楼', body: '这是页面叙事名称，不宣称为已核验官方线路。', image: localLarge('creek'), alt: '石间溪流与林木', sourceStatus: 'local-review', tone: 'cool' },
  { id: 'village', eyebrow: '一寨一谷', title: '屋舍顺着山谷生长', body: '从航拍关系进入可浏览的村寨服务。', image: localLarge('village-aerial'), alt: '山谷中沿溪分布的木楼与田地', sourceStatus: 'local-review', tone: 'neutral' },
  { id: 'people', eyebrow: '人在寨中', title: '山静，人也热烈', body: '以劳作与相聚呈现温度，不使用标签化人物描述。', image: localLarge('labor'), alt: '田野里劳作的人', sourceStatus: 'portrait-review', tone: 'warm' }
]
~~~

- [ ] **步骤 5：写入小程序图片清单**

`miniprogram/utils/media.js` 只暴露小程序包内派生图：

~~~js
const asset = name => `/assets/wudong-local/${name}.jpg`

const media = {
  mountain: asset('mountain'), water: asset('water'), village: asset('village'),
  villageAerial: asset('village-aerial'), tea: asset('tea'), people: asset('people'),
  creek: asset('creek'), labor: asset('labor')
}

module.exports = { media }
~~~

- [ ] **步骤 6：提交素材管线，不提交图片**

~~~powershell
git add scripts/prepare-local-media.ps1 web/src/data/scenes.js miniprogram/utils/media.js
git add -p .gitignore
git diff --cached -- .gitignore
git commit -m "feat: 建立乌东本机视觉素材管线"
~~~

在 `git add -p` 中只接受 `resours/贵州乌东图片/`、`web/public/images/wudong-local/`、`miniprogram/assets/wudong-local/` 三行；若它们与 `.superpowers/` 被合并在同一 hunk，选择编辑 hunk，只保留三条素材规则。提交前的 cached diff 不得出现 `.superpowers/`。

### 任务 2：统一“乌东”展示数据和真实服务标识

**文件：**
- 创建：`tourism-service/src/main/resources/db/migration/V3__rename_wudong_display_copy.sql`
- 修改：`tourism-service/src/main/resources/application.yml`
- 修改：`web/src/data/demo.js`
- 修改：`miniprogram/utils/demo.js`
- 修改：`web/index.html`

- [ ] **步骤 1：为已执行数据库增加向前迁移**

`V3__rename_wudong_display_copy.sql` 不修改已发布的 V2，而是更新所有会返回客户端的字段：

~~~sql
UPDATE merchant
SET name = REPLACE(name, '乌冬', '乌东'),
    description = REPLACE(description, '乌冬', '乌东');

UPDATE service_resource
SET name = REPLACE(name, '乌冬', '乌东'),
    description = REPLACE(description, '乌冬', '乌东'),
    location_text = REPLACE(location_text, '乌冬', '乌东');

UPDATE community_post
SET title = REPLACE(title, '乌冬', '乌东'),
    content = REPLACE(content, '乌冬', '乌东'),
    author_name = REPLACE(author_name, '乌冬', '乌东');

UPDATE knowledge_document
SET title = REPLACE(title, '乌冬', '乌东'),
    content = REPLACE(content, '乌冬', '乌东');
~~~

同时把 `application.yml` 的 `info.app` 改为 `贵州乌东文旅演示服务`，把游客可见的 `web/index.html` 标题改为品牌名 `贵州乌东`。

- [ ] **步骤 2：让前端演示资源使用 Java 的稳定 UUID**

两端 `demo.js` 均使用 V2 已有的六个 UUID，不再使用数字 `1..6`。规范化函数必须兼容 Java 的逗号标签与空图片：

~~~js
const normalizeTags = value => Array.isArray(value)
  ? value
  : String(value || '').split(',').map(item => item.trim()).filter(Boolean)

const fallbackById = Object.fromEntries(services.map(item => [String(item.id), item]))

export function normalizeService(item) {
  const fallback = fallbackById[String(item.id)] || {}
  const hasApiImagePair = Boolean(item.imageUrl && item.imageAlt)
  return {
    ...fallback,
    ...item,
    id: String(item.id),
    title: item.name || item.title || fallback.title,
    intro: item.description || item.intro || fallback.intro,
    image: hasApiImagePair ? item.imageUrl : (fallback.image || ''),
    imageAlt: hasApiImagePair ? item.imageAlt : (fallback.imageAlt || '服务实景暂缺'),
    tags: normalizeTags(item.tags),
    demoData: item.demoData !== false
  }
}
~~~

小程序使用相同函数体并通过 `module.exports = { services, posts, normalizeService }` 暴露；页面不再各自维护不同版本的 `normalize()`。Java 当前没有 `imageAlt` 字段，因此 API 图与替代文本不能组成一对时不直接展示未知画面，继续使用本机策展图片及其对应 `imageAlt`，或进入“服务实景暂缺”色块。

两端同时定义同构、只能由用户主动启用的演示卡：

~~~js
const demoAssistantCard = {
  type: 'itinerary',
  title: '贵州乌东苗族文化茶旅 · 两日建议',
  summary: '这是一份用于答辩操作的静态示例，请以服务方确认结果为准。',
  data: {
    demoMode: true,
    retrievalMode: 'keyword_demo',
    days: [
      { day: 1, theme: '从茶叶走进村寨', items: [
        { time: '上午', title: '苗族文化茶旅半日体验', summary: '示例节点', serviceId: '10000000-0000-0000-0000-000000000001', demoData: true },
        { time: '傍晚', title: '苗家长桌宴体验', summary: '示例节点', serviceId: '10000000-0000-0000-0000-000000000004', demoData: true }
      ] },
      { day: 2, theme: '在寨里慢下来', items: [
        { time: '上午', title: '苗绣香囊手作', summary: '示例节点', serviceId: '10000000-0000-0000-0000-000000000005', demoData: true }
      ] }
    ],
    notice: '服务时间、价格与可预约情况请以服务方确认结果为准。'
  },
  sources: [{ title: '贵州乌东苗族文化茶旅体验说明', document_id: '30000000-0000-0000-0000-000000000001' }]
}
~~~

Web 通过 `export` 暴露，小程序把它加入 `module.exports`；不得把此卡放进自动断线回调。

资源 `id` 依次为 `10000000-0000-0000-0000-000000000001` 至 `10000000-0000-0000-0000-000000000006`。只有前两项茶旅服务使用语义吻合的采茶图：Web 为 `/images/wudong-local/tea-thumb.jpg`，小程序为 `/assets/wudong-local/tea.jpg`，两端 `imageAlt` 固定为“身着民族服饰的姑娘们在山间茶园俯身采茶”。住宿、餐食、苗绣手作和接驳没有对应实景，`image` 保持空值，`imageAlt` 分别写“住宿服务实景暂缺”“餐食服务实景暂缺”“苗绣手作服务实景暂缺”“接驳服务实景暂缺”，由资源卡显示带类别名称的品牌色占位；不得拿村寨全景、人物肖像、务农或溪流照片冒充服务现场。所有未核验时长、价格和服务文案保留 `demoData: true`。社区第一条茶事分享复用同一采茶图与同一 `coverAlt`；第二条长桌宴分享没有对应实景，`cover` 保持空值并用 `coverAlt: '寨里分享配图暂缺'`。社区卡正常态读取 `coverAlt`，不从标题临时猜测画面。

- [ ] **步骤 3：提交名称与数据契约修正**

~~~powershell
git add tourism-service/src/main/resources/db/migration/V3__rename_wudong_display_copy.sql tourism-service/src/main/resources/application.yml web/index.html web/src/data/demo.js miniprogram/utils/demo.js
git commit -m "fix: 统一贵州乌东展示名称与服务标识"
~~~

### 任务 3：原子接通 LangGraph 会话、DeepSeek、检索与服务动作

**文件：**
- 修改：`agent-service/pyproject.toml`
- 修改：`agent-service/.env.example`
- 修改：`agent-service/app/contracts.py`
- 修改：`agent-service/app/graph/state.py`
- 修改：`agent-service/app/graph/builder.py`
- 修改：`agent-service/app/streaming.py`
- 修改：`agent-service/app/rag/chunker.py`
- 修改：`agent-service/app/rag/indexer.py`
- 修改：`agent-service/app/main.py`
- 修改：`agent-service/app/config.py`
- 修改：`agent-service/app/__init__.py`
- 修改：`agent-service/app/tools/tourism_client.py`
- 修改：`agent-service/app/rag/retriever.py`
- 修改：`agent-service/app/llm/deepseek_client.py`
- 修改：`agent-service/app/graph/nodes.py`

- [ ] **步骤 1：固定 LangGraph 流式接口主版本并统一文档 ID**

将依赖改为 `langgraph>=1.1,<2.0`，并把包描述修正为“贵州乌东文旅 AI 编排服务”。`Source.document_id`、`KnowledgeIndexer.index(document_id, ...)`、`chunk_document(document_id, ...)` 和 `/internal/index/knowledge/{document_id}` 的 `document_id` 全部使用 `str`，与 Java 36 位 ID 保持一致；`main.py` 的 FastAPI 标题同步改为“贵州乌东文旅 AI 服务”。

`AgentCard` 顶层契约保持：

~~~python
class Source(BaseModel):
    title: str
    document_id: str | None = None

class AgentCard(BaseModel):
    type: CardType
    title: str
    summary: str = ""
    data: dict[str, Any] = Field(default_factory=dict)
    sources: list[Source] = Field(default_factory=list)

class AssistantRequest(BaseModel):
    thread_id: str = "demo-session"
    user_text: str = ""
    page_action: str | None = None
    service_id: str | None = Field(default=None, alias="serviceId")
    people: int | None = None
    travel_date: str | None = Field(default=None, alias="travelDate")
    contact_name: str | None = Field(default=None, alias="contactName")
    contact_phone: str | None = Field(default=None, alias="contactPhone")
~~~

前端结构化预约动作使用 `serviceId`；Pydantic 接收该别名后，`request.model_dump()` 仍以字段名输出 `service_id`，正好写入 `AgentState.service_id`。因此卡片动作契约和图内部状态各自保持既定命名，不做临时键转换。

- [ ] **步骤 2：让当前线程状态可累积**

`graph/state.py` 增加消息 reducer 和服务结果：

~~~python
from operator import add
from typing import Annotated, Any, TypedDict

class AgentState(TypedDict, total=False):
    thread_id: str
    user_text: str
    messages: Annotated[list[dict[str, str]], add]
    page_action: str | None
    service_id: str | None
    people: int | None
    travel_date: str | None
    contact_name: str | None
    contact_phone: str | None
    intent: str
    retrieved_chunks: list[dict[str, Any]]
    services: list[dict[str, Any]]
    card: dict[str, Any]
    error: str | None
~~~

- [ ] **步骤 3：只编译一次带进程内 checkpointer 的图**

`graph/builder.py` 保留现有节点和边，只替换编译出口：

~~~python
from functools import lru_cache
from langgraph.checkpoint.memory import InMemorySaver

@lru_cache(maxsize=1)
def get_graph():
    return build_graph()

def build_graph():
    graph = StateGraph(AgentState)
    graph.add_node("fast_lane", fast_lane)
    graph.add_node("intent", identify_intent)
    graph.add_node("clarify", clarify)
    graph.add_node("retrieve", retrieve)
    graph.add_node("itinerary", itinerary)
    graph.add_node("knowledge", knowledge_answer)
    graph.add_node("pending_booking", pending_booking)
    graph.add_conditional_edges(START, route_request, {"fast": "fast_lane", "slow": "intent"})
    graph.add_edge("fast_lane", END)
    graph.add_conditional_edges(
        "intent",
        decide_intent,
        {"clarify": "clarify", "itinerary": "retrieve", "knowledge": "retrieve", "booking": "pending_booking"},
    )
    graph.add_conditional_edges("retrieve", lambda state: state.get("intent"), {"itinerary": "itinerary", "knowledge": "knowledge"})
    graph.add_edge("clarify", END)
    graph.add_edge("itinerary", END)
    graph.add_edge("knowledge", END)
    graph.add_edge("pending_booking", END)
    return graph.compile(checkpointer=InMemorySaver())
~~~

此状态只在当前 Python 进程内保留，服务重启即清空；页面不得宣称跨设备或永久保存。

- [ ] **步骤 4：直接流出真实 custom 事件与最终卡片**

`streaming.py` 删除节点累积事件回放和重复 `card_ready`，实现唯一出口；图正常结束但最终卡本身为 `error` 时也必须发 `failed`，不得把业务错误标成完成：

~~~python
from app.graph.builder import get_graph

async def run_assistant(payload: dict[str, Any]) -> AsyncIterator[dict[str, Any]]:
    thread_id = str(payload.get("thread_id") or "demo-session")
    config = {"configurable": {"thread_id": thread_id}}
    graph_input = {
        **payload,
        "thread_id": thread_id,
        "messages": [{"role": "user", "content": str(payload.get("user_text") or "")}],
    }
    final_card: dict[str, Any] | None = None
    yield {"type": "task_started", "thread_id": thread_id}
    try:
        async for part in get_graph().astream(
            graph_input,
            config=config,
            stream_mode=["custom", "updates"],
            version="v2",
        ):
            if part["type"] == "custom":
                yield part["data"]
            elif part["type"] == "updates":
                for update in part["data"].values():
                    if isinstance(update, dict) and update.get("card"):
                        final_card = update["card"]
        card = AgentCard.model_validate(final_card or {"type": "error", "title": "助手暂不可用"})
        yield {"type": "card_ready", "card": card.model_dump()}
        yield {"type": "failed" if card.type == "error" else "completed"}
    except Exception:
        fallback = AgentCard(
            type="error",
            title="助手暂不可用",
            summary="本机 AI 服务暂未准备好，请稍后重试。",
            data={"demoAvailable": True},
        )
        yield {"type": "card_ready", "card": fallback.model_dump()}
        yield {"type": "failed"}
~~~

同时把原有 `from app.graph.builder import build_graph` 替换为上面的 `get_graph` 导入，不能只改调用名。

- [ ] **步骤 5：增加真实知识搜索与服务卡映射**

`TourismClient` 增加：

~~~python
async def search_knowledge(self, keywords: str = "") -> list[dict[str, Any]]:
    async with httpx.AsyncClient(timeout=5) as client:
        response = await client.get(
            f"{self.base_url}/internal/agent/knowledge/search",
            params={"keywords": keywords},
        )
        response.raise_for_status()
        return response.json().get("data", [])

async def create_pending_booking(
    self,
    service_id: str,
    travel_date: str,
    people: int,
    contact_name: str,
    contact_phone: str,
    thread_id: str,
) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=5) as client:
        response = await client.post(
            f"{self.base_url}/internal/agent/pending-bookings",
            json={
                "serviceId": service_id,
                "travelDate": travel_date,
                "peopleCount": people,
                "contactName": contact_name,
                "contactPhone": contact_phone,
                "note": "来自乌东向导的待确认预约",
                "threadId": thread_id,
            },
        )
        response.raise_for_status()
        return response.json().get("data", {})

def service_card_item(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "serviceId": str(item["id"]),
        "title": item.get("name", "乌东体验"),
        "summary": item.get("description", ""),
        "price": item.get("price"),
        "demoData": item.get("demoData", True),
    }
~~~

- [ ] **步骤 6：为尚未提供的云端 Embedding 设置诚实降级**

当前云端 Embedding 接口尚未提供，所以 `KnowledgeRetriever.retrieve()` 只执行真实的 Java 关键词资料读取，并明确标记方式：

~~~python
from app.tools.tourism_client import TourismClient

QUERY_TERMS = (
    "苗族文化", "两天一夜", "长桌宴", "苗绣", "民宿",
    "茶旅", "茶园", "采茶", "制茶", "品茶", "乌东",
)

class KnowledgeRetriever:
    async def retrieve(self, query: str, top_k: int = 4) -> list[dict[str, Any]]:
        terms = [term for term in QUERY_TERMS if term in query][:4] or ["乌东"]
        documents_by_id: dict[str, dict[str, Any]] = {}
        client = TourismClient()
        for term in terms:
            for item in await client.search_knowledge(term):
                documents_by_id.setdefault(str(item["id"]), item)
        documents = list(documents_by_id.values())[:top_k]
        return [
            {
                "document_id": str(item["id"]),
                "title": item["title"],
                "content": item["content"],
                "retrieval_mode": "keyword_demo",
            }
            for item in documents[:top_k]
        ]
~~~

Java 当前对单个 `keywords` 参数执行连续子串匹配，因此不得把整句提问直接传入；上面的有限领域词表逐词请求并按文档 ID 去重，保证默认“贵州乌东苗族文化和茶旅”问题能够命中已发布资料，同时不引入新分词依赖。返回空数组时，知识回答使用 `error` 卡；有结果时界面显示“关键词资料（演示）”，不得显示“向量 RAG 已完成”。`main.py` 的健康信息将 Embedding 标为 `pending_endpoint`，直到用户提供真实接口。

- [ ] **步骤 7：实现受约束的 DeepSeek JSON 行程生成**

`config.py` 默认模型改为 `deepseek-v4-flash`，仍允许 `DEEPSEEK_MODEL` 覆盖；`.env.example` 的 `DEEPSEEK_MODEL` 同步改为 `deepseek-v4-flash`，避免示例配置反向覆盖代码默认值。`get_deepseek_client()` 缺少 Key 时的异常文案改为“未配置 DEEPSEEK_API_KEY”，删除“已使用本地确定性演示编排”的错误声明。`deepseek_client.py` 增加：

~~~python
import json
from typing import Any

from app.tools.tourism_client import service_card_item

class DeepSeekUnavailable(RuntimeError):
    pass

async def compose_itinerary(
    user_text: str,
    messages: list[dict[str, str]],
    services: list[dict[str, Any]],
    sources: list[dict[str, Any]],
) -> dict[str, Any]:
    client = get_deepseek_client()
    allowed_services = [service_card_item(item) for item in services]
    prompt = {
        "request": user_text,
        "recentMessages": messages[-8:],
        "allowedServices": allowed_services,
        "sources": [{"title": item["title"], "content": item["content"]} for item in sources],
        "outputSchema": {
            "title": "string",
            "summary": "string",
            "days": [{"day": 1, "theme": "string", "items": [{"time": "string", "title": "string", "summary": "string", "serviceId": "allowed id or null"}]}],
        },
    }
    response = await client.chat.completions.create(
        model=get_settings().deepseek_model,
        messages=[
            {"role": "system", "content": "只依据输入资料生成贵州乌东行程建议，以 json 输出；不得编造服务、库存、开放时段或价格。"},
            {"role": "user", "content": json.dumps(prompt, ensure_ascii=False)},
        ],
        response_format={"type": "json_object"},
        max_tokens=1600,
    )
    content = response.choices[0].message.content
    if not content or not content.strip():
        raise DeepSeekUnavailable("DeepSeek 返回了空内容")
    try:
        parsed = json.loads(content)
    except (TypeError, json.JSONDecodeError) as exc:
        raise DeepSeekUnavailable("DeepSeek 返回了无效 JSON") from exc
    if not isinstance(parsed, dict):
        raise DeepSeekUnavailable("DeepSeek JSON 顶层不是对象")

    title = str(parsed.get("title") or "").strip()
    summary = str(parsed.get("summary") or "").strip()
    retrieval_mode = next(
        (item.get("retrieval_mode") for item in sources if item.get("retrieval_mode")),
        None,
    )
    data = sanitize_itinerary(parsed, services, retrieval_mode)
    has_items = any(day.get("items") for day in data["days"])
    if not title or not summary or not data["days"] or not has_items:
        raise DeepSeekUnavailable("DeepSeek 行程结构不完整")
    return {"title": title, "summary": summary, "data": data}
~~~

解析后调用以下收口函数，只保留 `allowedServices` 中真实存在的 `serviceId`：

~~~python
def sanitize_itinerary(
    payload: dict[str, Any],
    services: list[dict[str, Any]],
    retrieval_mode: str | None,
) -> dict[str, Any]:
    allowed_by_id = {str(item["id"]): item for item in services}
    days: list[dict[str, Any]] = []
    raw_days = payload.get("days")
    if not isinstance(raw_days, list):
        raw_days = []
    for raw_day in raw_days[:3]:
        if not isinstance(raw_day, dict):
            continue
        items: list[dict[str, Any]] = []
        raw_items = raw_day.get("items")
        if not isinstance(raw_items, list):
            raw_items = []
        for raw_item in raw_items[:6]:
            if not isinstance(raw_item, dict):
                continue
            item = {
                "time": str(raw_item.get("time", "")),
                "title": str(raw_item.get("title", "乌东行程节点")),
                "summary": str(raw_item.get("summary", "")),
                "demoData": True,
            }
            service_id = str(raw_item.get("serviceId") or "")
            if service_id in allowed_by_id:
                item["serviceId"] = service_id
                item["demoData"] = allowed_by_id[service_id].get("demoData", True)
            items.append(item)
        try:
            day_number = int(raw_day.get("day"))
        except (TypeError, ValueError):
            day_number = len(days) + 1
        days.append({"day": day_number, "theme": str(raw_day.get("theme") or "慢游乌东"), "items": items})
    result = {"days": days, "notice": "服务时间、价格与可预约情况请以服务方确认结果为准。"}
    if retrieval_mode:
        result["retrievalMode"] = retrieval_mode
    return result
~~~

`itinerary()` 先把实际检索结果映射为 `Source`，再用返回值构造卡片：

~~~python
card_sources = [
    Source(title=item["title"], document_id=str(item["document_id"]))
    for item in chunks
]
card = AgentCard(
    type="itinerary",
    title=generated["title"],
    summary=generated["summary"],
    data=generated["data"],
    sources=card_sources,
)
~~~

未配置 Key、空内容、JSON 不合法或必要字段为空时，`compose_itinerary()` 统一抛出 `DeepSeekUnavailable`。图节点必须先返回 `error` 卡并使流终态为 `failed`，不能自动把本地演示行程当成一次已完成的模型结果。任务 2 的同构 `demoAgentCard` 只在用户看到错误后主动点击“查看演示结果”时由前端加载，并持续带 `data.demoMode=true`；Python 端不得引用或自动返回前端静态演示卡。

- [ ] **步骤 8：从节点内部发出真实阶段事件**

`nodes.py` 删除旧的 `event()` 累积器以及节点返回值中的 `events` 字段。事件帮助函数必须在图节点执行上下文中显式取得 writer，不能引用未定义的全局变量：

~~~python
from langgraph.config import get_stream_writer

from app.contracts import AgentCard, Source
from app.llm.deepseek_client import DeepSeekUnavailable, compose_itinerary
from app.rag.retriever import KnowledgeRetriever
from app.tools.tourism_client import TourismClient, service_card_item

def write_event(type_: str, **payload: Any) -> None:
    writer = get_stream_writer()
    writer({"type": type_, **payload})
~~~

`write_event()` 只允许在正在执行的图节点函数体内调用，不得在模块顶层调用。各函数内的调用固定为：

| 节点函数 | 函数体内调用 |
| --- | --- |
| `identify_intent()` | `write_event("node_started", node="intent", message="正在理解你的出行需求")` |
| `retrieve()` 开始 | `write_event("node_started", node="retrieve", message="正在查找乌东资料")` |
| `retrieve()` 取得结果后 | `write_event("tool_finished", tool="knowledge_retrieval", source_count=len(chunks))` |
| `itinerary()` 查询服务前 | `write_event("node_started", node="service_search", message="正在匹配平台服务")` |
| `itinerary()` 查询服务后 | `write_event("tool_finished", tool="service_search", service_count=len(services))` |
| `itinerary()` 调用模型前 | `write_event("node_started", node="itinerary", message="正在生成行程建议")` |

`itinerary()` 的固定顺序为：读取检索片段 → 调用 `search_services()` → 发出真实服务完成事件 → 调用 DeepSeek → 成功生成行程卡或失败生成错误卡。`data.days[].items[]` 使用对象而非字符串；只有真实服务才带 `serviceId`。

DeepSeek 调用落实为以下唯一成功/失败分支；成功分支使用步骤 7 已给出的 `card_sources` 和 `generated` 构造行程卡。不得再保留没有调用落点的 `fallback_itinerary()`：

~~~python
try:
    generated = await compose_itinerary(
        str(state.get("user_text") or ""),
        state.get("messages", []),
        services,
        chunks,
    )
    card_sources = [
        Source(title=item["title"], document_id=str(item["document_id"]))
        for item in chunks
    ]
    card = AgentCard(
        type="itinerary",
        title=generated["title"],
        summary=generated["summary"],
        data=generated["data"],
        sources=card_sources,
    )
except DeepSeekUnavailable:
    card = AgentCard(
        type="error",
        title="暂时无法生成行程",
        summary="模型当前不可用，你可以稍后重试或主动查看演示结果。",
        data={"demoAvailable": True},
    )
~~~

`fast_lane()` 的 `data.services[]` 使用 `service_card_item()`，并把快捷动作关键词修正为真实种子数据能命中的连续子串：

~~~python
async def fast_lane(state: dict[str, Any]) -> dict[str, Any]:
    action = state.get("page_action") or "recommend"
    keyword_map = {
        "recommend_stay": "民宿",
        "recommend_food": "长桌宴",
        "recommend_culture": "茶旅",
        "recommend_travel": "接驳",
    }
    try:
        write_event("node_started", node="service_search", message="正在匹配平台服务")
        services = await TourismClient().search_services(keyword_map.get(action, ""))
        write_event("tool_finished", tool="service_search", service_count=len(services))
        if not services:
            card = AgentCard(type="error", title="暂未找到匹配服务", summary="可以先浏览游乌东资源列表。")
        else:
            card = AgentCard(
                type="service_recommendation",
                title="为你推荐贵州乌东体验",
                summary="以下为平台服务，请进入详情核对演示数据与预约信息。",
                data={"services": [service_card_item(item) for item in services[:4]]},
            )
    except Exception:
        card = AgentCard(type="error", title="服务暂不可用", summary="资源服务尚未启动，请稍后再试。")
    return {"card": card.model_dump(), "messages": [{"role": "assistant", "content": card.summary}]}
~~~

结果为空时返回 `error` 卡，不能返回空的“推荐成功”。`knowledge_answer()` 的 `sources` 只来自实际检索结果，并且只有当检索结果确实包含 `retrieval_mode` 时，才把它映射为卡片的 `data.retrievalMode`。零来源的知识问答返回 `error` 卡；零来源但有服务信息的行程可以继续生成，却不能显示“关键词资料（演示）”。模型生成但未绑定真实 `serviceId` 的行程节点保留 `demoData=true`；绑定服务的节点才用该服务的 `demoData` 覆盖，避免把无台账内容显示成已核验实时服务。

~~~python
retrieval_mode = next(
    (item.get("retrieval_mode") for item in chunks if item.get("retrieval_mode")),
    None,
)
knowledge_data = {"retrievalMode": retrieval_mode} if retrieval_mode else {}
~~~

`pending_booking()` 只有在 `service_id`、`travel_date`、`people`、`contact_name`、`contact_phone` 全部由结构化请求明确提供时，才调用上面的 Java 接口；缺任一字段就返回 `clarifying_question`，引导用户进入独立预约确认页补全，绝不补默认人数或演示联系人。Java 返回后只把 `id`、`serviceId`、`serviceName`、`travelDate`、`peopleCount`、`status` 和从对应服务继承的 `demoData` 放入 `data.booking`，联系人字段不回显到游客卡片。这样不修改 Java 强校验，也不存在硬编码联系人。

~~~python
async def pending_booking(state: dict[str, Any]) -> dict[str, Any]:
    required = (
        state.get("service_id"), state.get("travel_date"), state.get("people"),
        state.get("contact_name"), state.get("contact_phone"),
    )
    if not all(required):
        card = AgentCard(
            type="clarifying_question",
            title="请先补全预约信息",
            summary="请在预约确认页核对服务、日期、人数和联系人，再提交待确认预约。",
            data={"serviceId": state.get("service_id")} if state.get("service_id") else {},
        )
    else:
        try:
            client = TourismClient()
            write_event("node_started", node="service_search", message="正在核对预约服务")
            services = await client.search_services()
            selected_service = next(
                (item for item in services if str(item["id"]) == str(state["service_id"])),
                None,
            )
            write_event("tool_finished", tool="service_search", service_count=len(services))
            if selected_service is None:
                raise ValueError("预约服务不存在")
            booking = await client.create_pending_booking(
                str(state["service_id"]), str(state["travel_date"]), int(state["people"]),
                str(state["contact_name"]), str(state["contact_phone"]),
                str(state.get("thread_id") or "demo-session"),
            )
            safe_booking = {
                key: booking.get(key)
                for key in ("id", "serviceId", "serviceName", "travelDate", "peopleCount", "status")
            }
            safe_booking["demoData"] = selected_service.get("demoData", True)
            card = AgentCard(
                type="pending_booking",
                title="待确认预约",
                summary="请在页面再次确认后生成正式订单。",
                data={"booking": safe_booking},
            )
        except Exception:
            card = AgentCard(type="error", title="暂时无法创建预约", summary="预约服务暂不可用，请稍后再试。")
    return {
        "card": card.model_dump(),
        "messages": [{"role": "assistant", "content": card.summary}],
    }
~~~

预约卡的 `data.booking.demoData` 因此来自真实服务资源的 `demoData`，不是前端猜测。所有节点返回卡片时追加一条可见 assistant 摘要消息，但不保存提示词或思维链。

- [ ] **步骤 9：原子提交完整 AI 主链**

~~~powershell
git add agent-service/pyproject.toml agent-service/.env.example agent-service/app/contracts.py agent-service/app/graph/state.py agent-service/app/graph/builder.py agent-service/app/streaming.py agent-service/app/rag/chunker.py agent-service/app/rag/indexer.py agent-service/app/main.py agent-service/app/config.py agent-service/app/__init__.py agent-service/app/tools/tourism_client.py agent-service/app/rag/retriever.py agent-service/app/llm/deepseek_client.py agent-service/app/graph/nodes.py
git commit -m "feat: 接通乌东向导完整 AI 主链"
~~~

此任务中的图状态、节点事件、DeepSeek 输出和流式出口必须在同一提交内落地，避免中间提交出现契约已变但节点仍返回旧结构的不可运行状态。

### 任务 4：实现 Web 开卷、五联与沿溪首页

**文件：**
- 创建：`web/src/components/common/SafeImage.vue`
- 创建：`web/src/components/home/ScrollGate.vue`
- 创建：`web/src/components/home/FiveSceneHero.vue`
- 创建：`web/src/components/home/SceneExplorer.vue`
- 创建：`web/src/components/home/WudongJourney.vue`
- 创建：`web/src/components/home/LeafGuide.vue`
- 创建：`web/src/pages/HomePage.vue`
- 修改：`web/src/style.css`

- [ ] **步骤 1：落地全局品牌令牌与焦点样式**

删除 Google Fonts `@import` 和旧 Unsplash Hero，`style.css` 以以下全局基础开头：

~~~css
:root {
  --mountain: #173f36;
  --stream: #86a69d;
  --paper: #f2efe5;
  --wood: #684c3a;
  --fire: #c6734d;
  --tea-gold: #d8cb86;
  --ink: #1f302b;
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
body { margin: 0; color: var(--ink); background: var(--paper); font-family: "Microsoft YaHei", "PingFang SC", sans-serif; }
h1, h2, h3 { font-family: "Noto Serif SC", "Source Han Serif SC", STSong, SimSun, serif; }
button:focus-visible, a:focus-visible { outline: 3px solid var(--tea-gold); outline-offset: 3px; }
@media (prefers-reduced-motion: reduce) {
  html { scroll-behavior: auto; }
  *, *::before, *::after { animation-duration: 0.18s !important; animation-iteration-count: 1 !important; transition-duration: 0.18s !important; }
}
~~~

所有仅用于卷轴木纹、山脊、溪线、苗绣纹样、雾层和叶片外形的 DOM 都设置 `aria-hidden="true"`；能用 `::before/::after` 完成的装饰优先用伪元素。`LeafGuide` 的外层仍是真实按钮，提供 `aria-label="打开乌东向导"`，只把按钮内部叶片图形隐藏。任何纯装饰层都不得进入键盘焦点顺序。

- [ ] **步骤 2：实现必须点击的会话开卷**

`ScrollGate.vue` 的公开接口固定为：

~~~js
const emit = defineEmits(['opened'])
const KEY = 'wudong:scroll-opened'
const gateState = ref(sessionStorage.getItem(KEY) === '1' ? 'open' : 'closed')
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches

function openScroll() {
  if (gateState.value !== 'closed') return
  gateState.value = 'opening'
}

function finishOpen() {
  if (gateState.value !== 'opening') return
  sessionStorage.setItem(KEY, '1')
  gateState.value = 'open'
  emit('opened')
}
~~~

模板在 `gateState!=='open'` 时保持挂载，中央只放真实 `<button>`，主文案“点击这里”、副文案“开启画卷”；点击只切到 `opening`，由开卷根元素自身的 `animationend`（使用 `.self`，防止子元素动画冒泡）调用 `finishOpen()` 后才卸载。门面无雾；左右挡板包含 CSS 山脊、溪线、茶叶和木纹。正常时总动画约 1.4 秒，减少动态效果时约 180 ms，两种模式都必须触发同一个完成出口。

- [ ] **步骤 3：实现五联与图片失败状态**

先创建 `SafeImage.vue`，公开接口固定为：

~~~js
const props = defineProps({
  src: String,
  alt: { type: String, required: true },
  label: { type: String, default: '乌东实景' },
  loading: { type: String, default: 'eager' },
  objectPosition: { type: String, default: '50% 50%' }
})
const failed = ref(!props.src)
watch(() => props.src, value => { failed.value = !value })
function markFailed() { failed.value = true }
~~~

正常时渲染 `<img :src :alt :loading decoding="async" @error="markFailed">`；失败或 `src` 为空时不保留 `<img>`，改渲染场景色块 `<div role="img" :aria-label="alt"><strong>{{ label }}</strong><span>{{ alt }}</span></div>`。因此本机图片缺失也不会出现破图图标。

`FiveSceneHero.vue`：

~~~js
const props = defineProps({ scenes: { type: Array, required: true } })
const emit = defineEmits(['open-scene'])
const activeId = ref('')
const failed = reactive(new Set())

function openScene(scene) { emit('open-scene', scene) }
function markImageFailed(id) { failed.add(id) }
~~~

每联必须是等宽的 `<button class="scene-panel">` 并使用 `SafeImage`；鼠标/键盘聚焦时只让内部 `.scene-panel__visual` 在自身 `overflow: hidden` 容器内用 `transform: scale(1.035)` 轻微舒展，并用文字层 `opacity` 加深反馈。不得对 `flex-grow`、宽度或裁切尺寸做过渡，其他四联始终完整可见。桌面为五等联，移动端为 `scroll-snap-type: x mandatory` 的“一张主卡 + 相邻露边”。图片失败时由 `SafeImage` 显示场景字、`alt` 和品牌色块，不出现破图图标。

每联绑定 `style="--scene-index: index"`，开卷后以 `animation-delay: calc(var(--scene-index) * 80ms)` 渐显，避免为五张图分别写定时器。

`HomePage.vue` 挂载时只预载五个 `scene.src` 缩略图，不预载 `expandedAsset`：

~~~js
onMounted(() => {
  scenes.forEach(scene => {
    const preload = new Image()
    preload.src = scene.src
  })
})
~~~

- [ ] **步骤 4：实现近全屏探景层**

`SceneExplorer.vue` 使用原生 `<dialog>`，公开接口为：

~~~js
const dialogRef = ref(null)
const props = defineProps({ scene: Object })
const emit = defineEmits(['close', 'action'])
function closeExplorer() {
  if (dialogRef.value?.open) dialogRef.value.close()
  emit('close')
}
function handleDialogClick(event) {
  if (event.target === dialogRef.value) closeExplorer()
}
function runSceneAction() { emit('action', props.scene.action) }
~~~

根节点写成 `<dialog ref="dialogRef" @click="handleDialogClick">`。组件挂载且 `scene` 存在时在 `nextTick()` 调用 `showModal()`；`cancel` 使用 `@cancel.prevent="closeExplorer"`，遮罩点击只在 `event.target === dialogRef` 时触发，关闭按钮也走 `closeExplorer()`，确保先执行原生 `dialog.close()` 再通知父级。`HomePage` 用 `v-if="selectedScene"` 挂载探景层并在 `close` 后清空选择；组件卸载前若仍为 `open` 再调用一次 `dialog.close()`，避免留下空的 top-layer 模态层。展开图只在 `scene` 存在时挂载为 `SafeImage`，使用 `scene.expandedAsset`、`scene.expandedAlt` 和 `loading="lazy"`，不能沿用窄联 `alt` 猜测另一张图的内容，也不能随五联提前请求大图；失败时显示当前场景名称和替代文本。桌面内容约 `92vw × 88vh`，移动端全屏。寨景保留原图“乌东苗寨”字，不叠第二个大标题。

- [ ] **步骤 5：实现下滑雾层、沿溪章节和叶片出现信号**

`WudongJourney.vue` 在边界进入视口时只触发一次：

~~~js
const emit = defineEmits(['journey-entered', 'navigate', 'open-service'])
const fogState = ref('idle')
function enterJourney() {
  if (fogState.value !== 'idle') return
  fogState.value = 'passing'
}
function finishFogTransition() {
  if (fogState.value !== 'passing') return
  fogState.value = 'cleared'
  emit('journey-entered')
}
~~~

用 `IntersectionObserver` 调用 `enterJourney()`，并由雾层的 `animationend` 调用 `finishFogTransition()`；叶片因此只在雾散后出现。雾层固定 `pointer-events: none`，只改变 `opacity/transform`，不劫持滚动。三个 `journeySections` 均使用 `SafeImage loading="lazy"`，只在接近视口时请求 `-large.jpg`；失败时显示段落标题和替代文本。茶垄线转成溪流路径，三个段落依次出现；衣食住行节点统一发出 `emit('navigate', { path: '/resources', category })`。

`LeafGuide.vue` 只接收 `visible` 并发出 `open`。正常态呼吸位移不超过 3 px；减少动态效果时静止。五联阶段 `visible=false`，`journey-entered` 后才为 true。

- [ ] **步骤 6：用首页编排页连接场景**

`HomePage.vue` 的接口固定为：

~~~js
defineProps({ services: Array, categories: Array })
const emit = defineEmits(['gate-change', 'navigate', 'open-service'])
const selectedScene = ref(null)
const leafVisible = ref(false)
const assistantAnchor = ref(null)
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

function selectScene(scene) { selectedScene.value = scene }
function closeScene() { selectedScene.value = null }
function handleSceneAction(action) { emit('navigate', action) }
function handleJourneyEntered() { leafVisible.value = true }
function handleGateOpened() { emit('gate-change', true) }
function scrollToAssistant() { assistantAnchor.value?.scrollIntoView({ behavior: reducedMotion ? 'auto' : 'smooth' }) }
~~~

页面顺序严格为 ScrollGate → FiveSceneHero → SceneExplorer → WudongJourney → 衣食住行 → `assistant` 命名插槽；插槽外层元素绑定 `ref="assistantAnchor"`，任务 5 将 `AssistantWorkspace` 放入该插槽。`LeafGuide.open` 只调用 `scrollToAssistant()`，顶栏“乌东向导”才切换独立 `#/assistant` 路由。标题固定“山静 · 人暖”和“走进雷公山半山的乌东苗寨”。

- [ ] **步骤 7：提交 Web 沉浸首页**

~~~powershell
git add web/src/components/common/SafeImage.vue web/src/components/home web/src/pages/HomePage.vue web/src/style.css
git commit -m "feat: 实现贵州乌东开卷五联首页"
~~~

### 任务 5：实现 Web AI 工作台并接回现有业务页

**文件：**
- 创建：`web/src/composables/useAssistantSession.js`
- 创建：`web/src/components/assistant/AssistantWorkspace.vue`
- 创建：`web/src/pages/ResourcesPage.vue`
- 创建：`web/src/pages/ResourceDetailPage.vue`
- 创建：`web/src/pages/CommunityPage.vue`
- 创建：`web/src/pages/BookingPage.vue`
- 创建：`web/src/pages/AdminPage.vue`
- 修改：`web/src/App.vue`
- 修改：`web/src/style.css`

- [ ] **步骤 1：实现模块级单线程会话**

`useAssistantSession.js` 在模块顶层只创建一次状态：

~~~js
const threadId = ref(`web-${globalThis.crypto?.randomUUID?.() || Date.now()}`)
const messages = ref([])
const stages = reactive([
  { id: 'understanding', label: '需求理解', state: 'idle' },
  { id: 'knowledge', label: '知识检索', state: 'idle' },
  { id: 'service', label: '服务协同', state: 'idle' },
  { id: 'itinerary', label: '行程生成', state: 'idle' }
])
const activeCard = ref(null)
const previousCard = ref(null)
const mode = ref('live')
const isBusy = ref(false)
let requestSeq = 0
let activeSocket = null

function resetStages() {
  stages.forEach(stage => { stage.state = 'idle' })
}

function setStage(id, state) {
  const stage = stages.find(item => item.id === id)
  if (stage) stage.state = state
}

function finishStageIfActive(id) {
  const stage = stages.find(item => item.id === id)
  if (stage?.state === 'active') stage.state = 'done'
}

let acceptedCardSeq = -1
let acceptedCardFingerprint = ''
function acceptCard(card, seq) {
  const fingerprint = JSON.stringify(card)
  if (seq === acceptedCardSeq && fingerprint === acceptedCardFingerprint) return
  if (activeCard.value) previousCard.value = activeCard.value
  activeCard.value = card
  acceptedCardSeq = seq
  acceptedCardFingerprint = fingerprint
  messages.value.push({ role: 'assistant', content: card.summary || card.title })
}
~~~

`sendMessage(text)` 先执行 `const seq = ++requestSeq` 使旧连接失效，再取出、清空并关闭原 `activeSocket`，随后调用 `resetStages()`、复用 `threadId`、追加用户消息、设置 `isBusy=true` 并建立新的 WebSocket；新连接立即赋给 `activeSocket`。这样旧连接的 `close` 即使同步到达也只能走旧序号关闭分支，不会短暂污染新界面。旧序号事件不改当前界面；仅同一 `requestSeq` 内完全相同的重复卡才去重，不得跨轮吞掉用户重复提问得到的相同结果。路由切换不重置对话或卡片，但每轮阶段必须从 `idle` 重来。每次连接都必须在该次 `completed`、`failed`、`error` 或非主动 `close` 后收束，不能让 `isBusy` 常驻或泄漏服务端保持的连接。

- [ ] **步骤 2：只按真实事件映射四阶段**

映射函数固定为：

~~~js
function closeAssistantSocket(socket) {
  if (socket?.readyState === WebSocket.OPEN) socket.close()
  else if (socket?.readyState === WebSocket.CONNECTING) {
    socket.addEventListener('open', () => socket.close(), { once: true })
  }
}

function settleRequest(seq, socket, outcome) {
  if (seq !== requestSeq) {
    closeAssistantSocket(socket)
    return
  }
  stages.forEach(stage => {
    if (stage.state === 'active') stage.state = outcome === 'completed' ? 'done' : 'failed'
    else if (stage.state === 'idle') stage.state = 'skipped'
  })
  isBusy.value = false
  if (outcome === 'failed') mode.value = 'error'
  closeAssistantSocket(socket)
  if (activeSocket === socket) activeSocket = null
}

function handleAssistantEvent(event, seq, socket) {
  if (seq !== requestSeq) {
    closeAssistantSocket(socket)
    return
  }
  if (event.type === 'node_started' && ['route', 'intent'].includes(event.node)) setStage('understanding', 'active')
  if (event.type === 'node_started' && ['retrieve', 'service_search', 'itinerary'].includes(event.node)) finishStageIfActive('understanding')
  if (event.type === 'node_started' && event.node === 'retrieve') setStage('knowledge', 'active')
  if (event.type === 'node_started' && event.node === 'service_search') setStage('service', 'active')
  if (event.type === 'node_started' && event.node === 'itinerary') setStage('itinerary', 'active')
  if (event.type === 'tool_finished' && event.tool === 'knowledge_retrieval') setStage('knowledge', 'done')
  if (event.type === 'tool_finished' && event.tool === 'service_search') setStage('service', 'done')
  if (event.type === 'card_ready' && event.card?.type) {
    mode.value = event.card.type === 'error' ? 'error' : event.card.data?.demoMode ? 'demo' : 'live'
    acceptCard(event.card, seq)
  }
  if (event.type === 'completed') settleRequest(seq, socket, 'completed')
  if (event.type === 'failed') settleRequest(seq, socket, 'failed')
}
~~~

`sendMessage()` 绑定 `socket.onerror` 和 `socket.onclose`：仅当 `seq===requestSeq && isBusy.value` 时写入带 `data.demoAvailable=true` 的传输错误卡并调用 `settleRequest(seq, socket, 'failed')`；主动收束后的 `close` 因 `isBusy=false` 不再重复报错。旧序号事件仍会关闭它携带的旧连接，只是不写界面状态。收到终态时，当前活动阶段按成功或失败收束，尚未发生的中间阶段标为 `skipped`，不能标完成。小程序复用同一终态规则，保存 `wx.connectSocket()` 返回的 `SocketTask`，并调用该实例的 `socketTask.close()`，不得使用全局 `wx.closeSocket`。删除现有三个 `setTimeout` 伪进度。模型失败卡和断线卡都只显示错误与“查看演示结果”按钮；按钮仅在 `currentCard.type==='error' && currentCard.data.demoAvailable===true` 时渲染。只有用户点击后才以同一轮新版本接收任务 2 的 `demoAssistantCard`、把 `mode` 设为 `demo`，并持续显示“演示模式”；不得在错误回调中自动加载。

- [ ] **步骤 3：按真实 `AgentCard` 渲染工作台**

`AssistantWorkspace.vue` 只读取：

~~~js
const CARD_TYPES = new Set(['itinerary', 'service_recommendation', 'knowledge_answer', 'clarifying_question', 'pending_booking', 'error'])

function normalizeCard(card) {
  return CARD_TYPES.has(card?.type)
    ? { type: card.type, title: card.title, summary: card.summary || '', data: card.data || {}, sources: Array.isArray(card.sources) ? card.sources : [] }
    : { type: 'error', title: '内容暂不可展示', summary: '向导返回了不支持的卡片类型。', data: {}, sources: [] }
}

const sourcesOpen = ref(false)
const sourceCount = computed(() => activeCard.value?.sources?.length || 0)
watch(activeCard, () => { sourcesOpen.value = false })
function toggleSources() { sourcesOpen.value = !sourcesOpen.value }
~~~

行程读取 `data.days`；服务推荐读取 `data.services`；待确认预约读取 `data.booking`。当 `sourceCount>0` 时默认只显示按钮“参考了 N 条乌东资料”，按钮绑定 `aria-expanded="sourcesOpen"`；点击后才展开列表，列表只显示 `sources[].title`，无来源时整个入口不渲染。`mode==='demo'` 持续显示“演示模式”；行程逐项读取 `data.days[].items[].demoData`，服务推荐逐项读取 `data.services[].demoData`，预约读取 `data.booking.demoData` 并显示“演示数据”；`data.retrievalMode==='keyword_demo'` 显示“关键词资料（演示）”。游客端不显示英文 `type`、模型名、提示词、工具参数或思维链。服务行的详情/加入/预约按钮只在存在真实 `serviceId` 时出现，并分别发出 `open-service`、`join-service`、`book-service`；普通服务行的预约载荷是 `{ serviceId }`，`pending_booking` 卡的“继续确认”载荷是 `{ serviceId: data.booking.serviceId, pendingBooking: data.booking }`。

当 `previousCard` 存在时，在当前卡下方渲染默认折叠的“上一版”区域，只显示上一版标题与摘要；展开后可查看完整结构，但不保留旧卡的详情、加入或预约按钮，避免用户误操作过期建议。新请求开始时继续显示当前卡，只有新卡被 `acceptCard()` 接收后才把旧卡移入“上一版”。

组件接口固定为：

~~~js
defineProps({ embedded: Boolean, joinedServiceIds: Array })
defineEmits(['open-service', 'join-service', 'book-service'])
~~~

- [ ] **步骤 4：把既有业务模板移入薄页面**

创建五个页面组件并只通过 props/emits 通信：

~~~js
// ResourcesPage.vue
defineProps({ services: Array, categories: Array, activeCategory: String })
defineEmits(['filter', 'open-service'])

// ResourceDetailPage.vue
defineProps({ service: Object, categoryLabel: String })
defineEmits(['book'])

// CommunityPage.vue
defineProps({ posts: Array })

// BookingPage.vue
defineProps({ service: Object, booking: Object, pending: Object, done: Boolean, latestOrder: Object, loading: Boolean, loadError: String, actionError: String })
defineEmits(['update-booking', 'submit', 'confirm', 'open-admin'])

// AdminPage.vue
defineProps({ tab: String, orders: Array, services: Array, actionError: String })
defineEmits(['change-tab', 'update-order', 'return-home'])
~~~

这些组件只搬运现有资源、详情、社区、预约和后台 DOM，不增加新功能；后台不添加卷轴、雾或叶片动画。资源列表、资源详情、社区、预约摘要和后台缩略图统一使用 `SafeImage`：服务图片的 `alt` 固定读取任务 2 归一化后的 `service.imageAlt`，社区封面读取 `post.coverAlt`；标题只作为色块 `label`，不能用服务名或帖子标题冒充画面描述。`src` 为空或加载失败时显示品牌色块，不出现破图。列表使用 `loading="lazy"`，当前详情主图使用 `loading="eager"`。

- [ ] **步骤 5：让 `App.vue` 只负责壳层和共享业务状态**

保留现有 hash 路由、资源、社区、预约、订单和后台函数；删除旧首页模板与旧 AI 计时器。新增：

~~~js
const homeOpened = ref(sessionStorage.getItem('wudong:scroll-opened') === '1')
const joinedServiceIds = ref([])
const booking = ref({ date: '', people: 2, contact: '', phone: '' })
const bookingLoading = ref(false)
const bookingLoadError = ref('')
const bookingActionError = ref('')
const adminActionError = ref('')

function normalizeBooking(item, fallbackDemoData) {
  const demoData = typeof item?.demoData === 'boolean'
    ? item.demoData
    : (typeof fallbackDemoData === 'boolean' ? fallbackDemoData : true)
  return {
    id: String(item.id),
    serviceId: String(item.serviceId || ''),
    name: item.serviceName || item.name || '乌东体验',
    date: item.travelDate || item.date || '',
    people: Number(item.peopleCount || item.people || 0),
    contact: item.contactName || item.contact || '',
    phone: item.contactPhone || item.phone || '',
    status: item.status,
    demoData
  }
}

async function openServiceById(serviceId) {
  const fallback = serviceList.value.find(item => String(item.id) === String(serviceId))
  if (!fallback) return
  selected.value = fallback
  go(`/resource/${serviceId}`)
  try { selected.value = normalizeService(await request(`/api/services/${serviceId}`)) } catch (_) {}
}

function navigateIntent({ path, category }) {
  if (category) activeCategory.value = category
  go(path)
}

async function bookServiceFromAssistant(payload) {
  const serviceId = String(payload?.serviceId || '')
  const item = serviceList.value.find(value => String(value.id) === serviceId)
  if (!item) return
  selected.value = item
  bookingDone.value = false
  bookingLoadError.value = ''
  pendingBooking.value = null
  bookingLoading.value = Boolean(payload?.pendingBooking?.id)
  go('/booking')
  if (!payload?.pendingBooking?.id) {
    bookingLoading.value = false
    return
  }
  try {
    const fullBooking = await request(`/api/bookings/${payload.pendingBooking.id}`)
    pendingBooking.value = normalizeBooking(fullBooking, payload.pendingBooking.demoData)
    booking.value = {
      date: fullBooking.travelDate,
      people: fullBooking.peopleCount,
      contact: fullBooking.contactName,
      phone: fullBooking.contactPhone
    }
  } catch (_) {
    bookingLoadError.value = '无法读取这笔待确认预约，请返回后重试。'
  } finally {
    bookingLoading.value = false
  }
}

function joinServiceById(serviceId) {
  const id = String(serviceId)
  if (!joinedServiceIds.value.includes(id)) joinedServiceIds.value.push(id)
}
~~~

首页路由渲染 `HomePage`，并由 `App.vue` 在命名插槽中直接放入内嵌 `AssistantWorkspace`；助手路由渲染非内嵌版本。两处共享同一个 composable 会话与 `joinedServiceIds`。事件链只经过一层：`AssistantWorkspace` 直接向 `App.vue` 发出 `open-service`、`join-service`、`book-service`，不要求 `HomePage` 转发助手事件。模板连接固定为：

~~~vue
<HomePage
  :services="serviceList"
  :categories="categories"
  @gate-change="homeOpened = $event"
  @navigate="navigateIntent"
  @open-service="openServiceById"
>
  <template #assistant>
    <AssistantWorkspace
      :embedded="true"
      :joined-service-ids="joinedServiceIds"
      @open-service="openServiceById"
      @join-service="joinServiceById"
      @book-service="bookServiceFromAssistant"
    />
  </template>
</HomePage>
~~~

非首页的 `AssistantWorkspace` 同样把 `book-service` 交给 `bookServiceFromAssistant`。`App.vue` 把 `bookingLoading` 和 `bookingLoadError` 传给 `BookingPage`；读取已有 ID 期间只显示“正在读取待确认预约”，`pending` 仍为 `null`，不得出现确认按钮。GET 成功后才显示日期、人数、联系人和电话，并只允许调用现有确认接口；读取失败时显示错误与返回按钮，不得退回“新建预约”。只有普通 `{ serviceId }` 载荷才显示可编辑表单并首次 POST 创建 `PENDING_CONFIRMATION`。新建和确认接口返回的 Java `serviceName/travelDate/peopleCount` 都必须先经 `normalizeBooking()` 映射成页面和订单使用的 `name/date/people`，再写入 `pendingBooking` 或 `orders`，不能把原始响应直接塞给旧模板。首页 `gate-change` 更新 `homeOpened`，顶栏仅在非首页或开卷完成后显示。顶栏固定“贵州乌东 / 游乌东 / 寨里 / 乌东向导 / 运营后台”；主 CTA 固定“为我安排乌东之行”。资源、社区、详情、预约、后台中的“乌冬”全部修正。

- [ ] **步骤 6：删除业务写操作的静默伪成功**

`App.vue` 的三个业务写操作必须遵守同一边界：

- `submitBooking()` 的 POST 失败时只设置 `bookingActionError`，`pendingBooking` 保持 `null`；删除 `WD${Date.now()}` 演示预约回退。
- 普通新建预约的 POST 必须使用 `booking.date`、`booking.people`、`booking.contact`、`booking.phone` 四个表单值，不得保留硬编码电话号码。
- `confirmBooking()` 失败时只设置 `bookingActionError`，不得把本地状态改成 `CONFIRMED`，也不得加入订单列表。
- `updateOrder()` 失败时只设置 `adminActionError`，不得在本地推进到 `PROCESSING` 或 `COMPLETED`。

`BookingPage` 和 `AdminPage` 原位显示对应错误；再次点击操作时先清空旧错误。静态初始订单可继续以 `demoData=true` 展示，但任何失败的写操作都不能自动伪装成功，也不自动切换演示模式。

- [ ] **步骤 7：提交 Web 工作台与壳层**

~~~powershell
git add web/src/composables/useAssistantSession.js web/src/components/assistant/AssistantWorkspace.vue web/src/pages web/src/App.vue web/src/style.css
git commit -m "feat: 实现乌东向导 Web 对话工作台"
~~~

### 任务 6：建立微信小程序五栏导航与最小“我的”

**文件：**
- 修改：`miniprogram/app.js`
- 修改：`miniprogram/app.json`
- 修改：`miniprogram/app.wxss`
- 创建：`miniprogram/custom-tab-bar/index.json`
- 创建：`miniprogram/custom-tab-bar/index.js`
- 创建：`miniprogram/custom-tab-bar/index.wxml`
- 创建：`miniprogram/custom-tab-bar/index.wxss`
- 创建：`miniprogram/pages/profile/profile.json`
- 创建：`miniprogram/pages/profile/profile.js`
- 创建：`miniprogram/pages/profile/profile.wxml`
- 创建：`miniprogram/pages/profile/profile.wxss`

- [ ] **步骤 1：配置五个主栏与共享状态**

`app.json` 的主栏固定：

~~~json
{
  "pages": [
    "pages/index/index", "pages/resources/resources", "pages/assistant/assistant",
    "pages/community/community", "pages/profile/profile", "pages/detail/detail",
    "pages/booking/booking", "pages/orders/orders"
  ],
  "tabBar": {
    "custom": true,
    "color": "#718077",
    "selectedColor": "#173f36",
    "backgroundColor": "#f2efe5",
    "borderStyle": "white",
    "list": [
      { "pagePath": "pages/index/index", "text": "首页" },
      { "pagePath": "pages/resources/resources", "text": "游乌东" },
      { "pagePath": "pages/assistant/assistant", "text": "向导" },
      { "pagePath": "pages/community/community", "text": "寨里" },
      { "pagePath": "pages/profile/profile", "text": "我的" }
    ]
  }
}
~~~

保留现有 `window/style` 配置，只把游客可见标题改为品牌名“贵州乌东”。`custom-tab-bar/index.json` 必须明确声明组件：

~~~json
{ "component": true }
~~~

`app.js` 增加：

~~~js
resourceCategory: null,
currentItinerary: null,
pendingBooking: null,
assistantSession: { threadId: `mini-${Date.now()}`, messages: [], currentCard: null, previousCard: null }
~~~

`app.wxss` 的全局基础固定为：

~~~css
page { min-height:100%; padding-bottom:calc(132rpx + env(safe-area-inset-bottom)); color:#1f302b; background:#f2efe5; font-family:"Microsoft YaHei", sans-serif; }
.page { padding:28rpx; }
.eyebrow { color:#c6734d; font-size:22rpx; letter-spacing:3rpx; }
.title { color:#173f36; font-size:42rpx; font-weight:700; }
.demo { color:#875235; background:#f5e7cf; font-size:20rpx; padding:4rpx 10rpx; border-radius:999rpx; }
.primary { color:#fff; background:#173f36; border-radius:999rpx; padding:20rpx 28rpx; text-align:center; }
~~~

- [ ] **步骤 2：实现唯一的中央叶片导航**

`custom-tab-bar/index.js`：

~~~js
Component({
  data: {
    selected: 0,
    list: [
      { pagePath: '/pages/index/index', text: '首页' },
      { pagePath: '/pages/resources/resources', text: '游乌东' },
      { pagePath: '/pages/assistant/assistant', text: '向导', guide: true },
      { pagePath: '/pages/community/community', text: '寨里' },
      { pagePath: '/pages/profile/profile', text: '我的' }
    ]
  },
  methods: {
    switchTab(event) {
      const index = Number(event.currentTarget.dataset.index)
      wx.switchTab({ url: this.data.list[index].pagePath })
    }
  }
})
~~~

`custom-tab-bar/index.wxml` 必须把循环下标写入点击元素，并把 `selected` 绑定到可见选中态：

~~~xml
<view class="custom-tab-bar">
  <view
    wx:for="{{list}}"
    wx:key="pagePath"
    class="tab-item {{selected === index ? 'is-selected' : ''}} {{item.guide ? 'guide-leaf' : ''}}"
    data-index="{{index}}"
    bindtap="switchTab"
  >
    <view wx:if="{{item.guide}}" class="leaf-mark" aria-hidden="true"></view>
    <text>{{item.text}}</text>
  </view>
</view>
~~~

WXML 共渲染五项，第三项单独使用 `.guide-leaf`；WXSS 固定底部并包含 `env(safe-area-inset-bottom)`，同时为 `.is-selected` 提供深绿文字与叶脉标记。小程序任何页面都不再创建全局悬浮叶片。

- [ ] **步骤 3：实现最小“我的”页**

`profile.js` 在 `onShow` 先执行 `this.getTabBar()?.setData({ selected: 4 })`，再同步 `globalData.currentItinerary` 和订单；WXML 只显示“演示体验官”、当前行程、预约订单入口。没有行程时显示“还没有加入行程”；不出现登录、头像上传、收藏、积分或设置。

- [ ] **步骤 4：提交小程序导航骨架**

~~~powershell
git add miniprogram/app.js miniprogram/app.json miniprogram/app.wxss miniprogram/custom-tab-bar miniprogram/pages/profile
git commit -m "feat: 增加乌东小程序五栏导航"
~~~

### 任务 7：实现小程序工具优先首页与主栏切换

**文件：**
- 修改：`miniprogram/pages/index/index.js`
- 修改：`miniprogram/pages/index/index.json`
- 修改：`miniprogram/pages/index/index.wxml`
- 修改：`miniprogram/pages/index/index.wxss`
- 修改：`miniprogram/pages/resources/resources.js`
- 修改：`miniprogram/pages/resources/resources.json`
- 修改：`miniprogram/pages/resources/resources.wxml`
- 修改：`miniprogram/pages/resources/resources.wxss`
- 修改：`miniprogram/pages/community/community.js`
- 修改：`miniprogram/pages/community/community.json`
- 修改：`miniprogram/pages/community/community.wxml`
- 修改：`miniprogram/pages/community/community.wxss`

- [ ] **步骤 1：将首页主入口改为 `switchTab`**

`index.js`：

~~~js
onShow() {
  this.getTabBar()?.setData({ selected: 0 })
  const app = getApp()
  this.setData({ currentItinerary: app.globalData.currentItinerary })
},
toResources(event) {
  getApp().globalData.resourceCategory = event.currentTarget.dataset.category || 'all'
  wx.switchTab({ url: '/pages/resources/resources' })
},
toAssistant() { wx.switchTab({ url: '/pages/assistant/assistant' }) },
toCommunity() { wx.switchTab({ url: '/pages/community/community' }) }
~~~

五个主栏之间全部使用 `switchTab`；资源详情、预约和订单属于非 tab 页面，继续使用 `navigateTo`。

页面级 JSON 不得覆盖回旧品牌：`pages/index/index.json` 固定 `navigationBarTitleText: "贵州乌东"`，`pages/resources/resources.json` 固定为 `"游乌东"`，`pages/community/community.json` 保持 `"寨里分享"`。

- [ ] **步骤 2：重排首页为操作优先**

首页依次显示：紧凑 `village` 实景与“山静 · 人暖” → “今天，想怎么游乌东？” → 茶旅/食宿/路线/导览四入口 → 当前行程或“让向导为我安排” → 两项精选服务。`index.js` 维护 `heroImageFailed`，品牌图绑定 `binderror`；为空或失败时以 `role="img" aria-label="乌东苗寨实景暂不可用"` 的深绿占位替换，不让大图或破图压过操作入口。

- [ ] **步骤 3：让资源页读取一次性分类意图**

`resources.js` 使用模块级完整列表，并在异步请求结束后按当前分类重新筛选，避免首次进入时 `onShow` 的分类结果被 `onLoad` 后到的请求覆盖：

~~~js
const { services, normalizeService } = require('../../utils/demo')
const { request } = require('../../utils/api')

const categories = [
  { id: 'all', name: '全部' },
  { id: 'culture', name: '苗韵茶旅' },
  { id: 'stay', name: '暖居民宿' },
  { id: 'food', name: '寨味餐食' },
  { id: 'travel', name: '山野出行' }
]
let allServices = services

Page({
  data: { active: 'all', services, categories },
  onLoad() {
    request('/api/services').then(items => {
      allServices = Array.isArray(items) && items.length ? items.map(normalizeService) : services
      this.applyFilter(this.data.active)
    }).catch(() => {
      allServices = services
      this.applyFilter(this.data.active)
    })
  },
  onShow() {
    this.getTabBar()?.setData({ selected: 1 })
    const app = getApp()
    const requested = app.globalData.resourceCategory
    app.globalData.resourceCategory = null
    const category = requested || this.data.active
    if (requested) this.setData({ active: requested })
    this.applyFilter(category)
  },
  applyFilter(category) {
    const visible = category === 'all' ? allServices : allServices.filter(item => item.category === category)
    this.setData({ active: category, services: visible })
  },
  filter(event) {
    this.applyFilter(event.currentTarget.dataset.id)
  },
  toDetail(event) {
    wx.navigateTo({ url: `/pages/detail/detail?id=${encodeURIComponent(event.currentTarget.dataset.id)}` })
  }
})
~~~

`resources.js` 和 `community.js` 都维护 `imageErrors: {}`，列表图片设置 `data-id="{{item.id}}" binderror="imageError"`；处理器复制当前对象并执行 `this.setData({ imageErrors: { ...this.data.imageErrors, [id]: true } })` 记录失败。WXML 只在图片路径存在且未失败时渲染 `<image>`，否则渲染带项目名称和替代说明的 `role="img"` 场景色块。`community.js` 的 `onShow` 设置 `selected: 3`。两页只修正“乌东”文案、本地图片和空状态，不改业务 API。

- [ ] **步骤 4：提交工具首页与浏览主栏**

~~~powershell
git add miniprogram/pages/index miniprogram/pages/resources miniprogram/pages/community
git commit -m "feat: 实现乌东小程序工具优先首页"
~~~

### 任务 8：实现小程序 AI 工作台、加入行程和真实预约动作

**文件：**
- 创建：`miniprogram/utils/assistant.js`
- 修改：`miniprogram/pages/assistant/assistant.js`
- 修改：`miniprogram/pages/assistant/assistant.json`
- 修改：`miniprogram/pages/assistant/assistant.wxml`
- 修改：`miniprogram/pages/assistant/assistant.wxss`
- 修改：`miniprogram/pages/detail/detail.js`
- 修改：`miniprogram/pages/detail/detail.wxml`
- 修改：`miniprogram/pages/booking/booking.js`
- 修改：`miniprogram/pages/booking/booking.wxml`
- 修改：`miniprogram/pages/orders/orders.wxml`

- [ ] **步骤 1：集中卡片与阶段规范化**

`utils/assistant.js`：

~~~js
const CARD_TYPES = ['itinerary', 'service_recommendation', 'knowledge_answer', 'clarifying_question', 'pending_booking', 'error']
const createPhases = () => [
  { key: 'understanding', label: '需求理解', state: 'idle' },
  { key: 'knowledge', label: '知识检索', state: 'idle' },
  { key: 'service', label: '服务协同', state: 'idle' },
  { key: 'itinerary', label: '行程生成', state: 'idle' }
]

function normalizeCard(card) {
  if (!card || !CARD_TYPES.includes(card.type)) return { type: 'error', title: '内容暂不可展示', summary: '向导返回了不支持的卡片类型。', data: {}, sources: [] }
  return { type: card.type, title: card.title, summary: card.summary || '', data: card.data || {}, sources: Array.isArray(card.sources) ? card.sources : [] }
}

module.exports = { CARD_TYPES, createPhases, normalizeCard }
~~~

- [ ] **步骤 2：保留同一线程、对话和上一版结果**

`assistant.js` 首次进入时复用 `getApp().globalData.assistantSession.threadId`。`data` 固定包含：

~~~js
{
  input: '我想体验贵州乌东的苗族文化和茶旅',
  messages: [], phases: createPhases(), currentCard: null, previousCard: null,
  isBusy: false, mode: 'live', requestSeq: 0, sourcesOpen: false
}
~~~

`pages/assistant/assistant.json` 固定 `navigationBarTitleText: "乌东向导"`，不得保留“乌冬 AI 旅伴”。`onShow()` 必须执行 `this.getTabBar()?.setData({ selected: 2 })`；至此五个 tab 页的下标固定为首页 0、游乌东 1、向导 2、寨里 3、我的 4。`ask()` 每轮先用 `createPhases()` 重置四阶段，再追加用户消息但不清空旧卡；卡片到达后先把旧卡移入 `previousCard`。WXML 在当前卡下方显示默认折叠的“上一版”，只读展示上一版标题、摘要和展开内容，不渲染旧卡操作按钮。每次请求记录局部 `seq`，旧 WebSocket 的消息不得覆盖新结果；卡片去重键必须包含 `seq`，只去除同一请求的重复事件，跨轮相同结果仍正常接收。错误卡立即把模式设为 `error`；只有非错误卡才能进入 `live` 或用户主动选择的 `demo`。删除所有 `setTimeout` 进度和静默静态成功。

- [ ] **步骤 3：映射真实事件并渲染六类卡片**

阶段映射与 Web 完全相同：`route`/`intent` 开始时激活“需求理解”，`retrieve`、`service_search` 或 `itinerary` 中第一个开始时仅将仍处于 `active` 的“需求理解”置为完成；之后再按对应节点激活知识、服务或行程阶段。快速通道未产生 `route`/`intent` 时，“需求理解”保持 `idle`，终态统一标为 `skipped`，不得伪造完成。WXML 使用 `card.summary`、`card.data.days`、`card.data.services`、`card.data.booking` 和 `card.sources[].title`；英文卡片类型不显示。新卡到达时设置 `sourcesOpen=false`；仅当 `card.sources.length>0` 时显示“参考了 {{card.sources.length}} 条乌东资料”，点击 `toggleSources()` 后才展开标题列表，无来源时不渲染入口。模型失败或断线时使用带 `data.demoAvailable=true` 的错误卡；只有用户点击“查看演示结果”后才加载任务 2 的同构 `demoAssistantCard`，并持续显示“演示模式”，错误回调本身不得自动加载。“演示数据”不读取不存在的卡片级 `data.demoData`：行程逐项检查 `card.data.days[].items[].demoData`，服务推荐逐项检查 `card.data.services[].demoData`，预约只检查 `card.data.booking.demoData`。当 `card.data.retrievalMode==='keyword_demo'` 时，两端都显示“关键词资料（演示）”；字段不存在时不显示该标签。

工作台先从 `card.data.days[].items[]` 计算带非空 `serviceId` 的 `joinableItems`；只有至少一个真实服务节点时才渲染“加入行程”。`addItinerary()` 写入 `globalData.currentItinerary` 时保留标题和天分组，但过滤掉所有缺少 `serviceId` 的节点，不能把纯模型文本变成已加入服务；写入后调用 `wx.switchTab({ url: '/pages/profile/profile' })`。`openService()` 与 `bookService()` 同样必须读取点击项的 `serviceId`；没有 ID 时不渲染按钮。`bookService()` 的分流固定为：

~~~js
bookService(event) {
  const card = this.data.currentCard
  const existing = card?.type === 'pending_booking' ? card.data?.booking : null
  const serviceId = String(existing?.serviceId || event.currentTarget.dataset.serviceId || '')
  if (!serviceId) return
  const app = getApp()
  app.globalData.pendingBooking = existing?.id ? existing : null
  wx.navigateTo({ url: `/pages/booking/booking?id=${encodeURIComponent(serviceId)}` })
}
~~~

- [ ] **步骤 4：修正 UUID 与预约二次确认**

`detail.js` 按字符串查找：

~~~js
const fallback = services.find(item => String(item.id) === String(options.id)) || null
~~~

无资源时显示不可操作空态，不能默认第一项。`detail.js` 和 `booking.js` 各维护单图 `imageFailed` 并绑定 `binderror`；正常图片使用资源数据的 `imageAlt`，图片缺失或失败时显示对应类别“服务实景暂缺”的 `role="img"` 色块，不用服务名冒充画面描述，也不出现破图。`booking.js` 同样按字符串查找，并保留一个统一响应映射：

~~~js
function toOrder(item, fallbackDemoData) {
  const demoData = typeof item?.demoData === 'boolean'
    ? item.demoData
    : (typeof fallbackDemoData === 'boolean' ? fallbackDemoData : true)
  return {
    id: String(item.id), serviceId: String(item.serviceId || ''),
    name: item.serviceName || item.name || '乌东体验',
    date: item.travelDate || item.date || '',
    people: Number(item.peopleCount || item.people || 0),
    contact: item.contactName || item.contact || '',
    phone: item.contactPhone || item.phone || '',
    status: item.status, demoData
  }
}
~~~

`onLoad` 最先读取、随后清空一次性的 `getApp().globalData.pendingBooking`：

- 若存在 `pendingBooking.id`，先设置 `pendingLoading=true` 且保持 `pending=null`，调用 `GET /api/bookings/{id}`；只有成功后才填入日期、人数、联系人、电话，并通过 `toOrder(fullBooking, pendingBooking.demoData)` 写入 `pending` 后开放“确认这笔预约”。随后调用现有 `/api/bookings/{id}/confirm`，绝不再次 POST。
- 若已有 ID 读取失败，设置 `pendingLoadError` 并只显示“返回重试”，绝不降级为新建。
- 若不存在已有 ID，才按 `options.id` 读取服务，显示日期、人数、联系人、电话表单；用户首次提交时以这四个输入值调用 `POST /api/bookings` 创建一笔 `PENDING_CONFIRMATION`，不得保留硬编码电话号码，随后在同页确认。

`booking.wxml` 在 `pendingLoading` 时只显示“正在读取待确认预约”；其后用 `wx:if="{{item && !pendingLoadError}}"` 包住对应表单或已有预约确认区，并在 `wx:else` 显示“未找到可预约服务”或 `pendingLoadError` 与返回按钮。确认按钮只在 GET 成功并写入 `pending` 后出现。`orders.wxml` 标题改为“我的乌东行程”。

新建 POST、已有预约 GET 与确认 POST 的成功响应都必须先经过 `toOrder()`，再写入 `pending` 或 `globalData.orders`，保证 Java 的 `serviceName/travelDate/peopleCount` 不会让“我的”页出现空标题、空日期或空人数。同时删除 `booking.js` 的两条静默伪成功：POST 失败只设置 `actionError='暂时无法创建预约，请稍后重试。'`，不得生成 `WD${Date.now()}`；确认请求失败只设置错误，不能调用 `finish({...status:'CONFIRMED'})`。`booking.wxml` 原位显示 `actionError`，重试前清空；已有 `demoData=true` 订单可以展示，但失败动作不能被伪装为成功。

- [ ] **步骤 5：提交小程序 AI 与预约动作**

~~~powershell
git add miniprogram/utils/assistant.js miniprogram/pages/assistant miniprogram/pages/detail miniprogram/pages/booking/booking.js miniprogram/pages/booking/booking.wxml miniprogram/pages/orders/orders.wxml
git commit -m "feat: 实现乌东小程序对话工作台"
~~~

### 任务 9：更新答辩说明并交给用户验收

**文件：**
- 修改：`README.md`
- 修改：`docs/答辩演示流程.md`
- 修改：`docs/项目进度.md`

- [ ] **步骤 1：更新项目入口和能力边界**

README 与进度文档写明：正确名称为“贵州乌东”；Web 主链为开卷/五联/雾/沿溪/向导；小程序是五栏工具型；DeepSeek 配置后才称实时模型；云端 Embedding 未提供时显示“关键词资料（演示）”；照片未授权前只在本机使用。

- [ ] **步骤 2：更新六分钟答辩顺序**

`docs/答辩演示流程.md` 固定为：点击开卷 → 五联探景 → 下滑雾散与叶片出现 → 乌东向导连续追问 → 从真实 `serviceId` 进入预约二次确认 → 小程序五栏与“我的” → 后台订单与脱敏摘要。演示模式必须主动说明，不把静态数据称为 DeepSeek 或向量 RAG 结果。

- [ ] **步骤 3：核对最终变更范围并提交文档**

~~~powershell
git status --short
git diff --name-only HEAD
git add README.md docs/答辩演示流程.md docs/项目进度.md
git commit -m "docs: 更新贵州乌东前端答辩流程"
~~~

预期：源照片与两端派生图不会出现在待提交列表；不删除用户已有的其他未提交文件。

## 用户可见验收清单

实现代理不创建或运行测试。开发提交完成后，交由用户按以下行为验收：

1. 新浏览器会话必须点击“点击这里”才开卷；门面无雾，同一会话返回不重复。
2. 五联顺序是山、水、寨、茶、人；寨使用《乌东苗寨》，茶使用《采茶》，探景可关闭并保留滚动位置。
3. 雾只在下滑边界短暂出现且不拦滚动；“一叶同行”只在雾散后出现。
4. Web 资源、详情、社区、预约、订单和管理后台仍可进入。
5. Web 与小程序同一工作台连续追问时 `thread_id` 不变，旧结果显示“上一版”。
6. 四阶段只响应真实 WebSocket 事件；未发生的服务协同显示跳过，不做计时伪动画。
7. 行程读取 `data.days`，来源读取 `sources[].title`；没有真实 `serviceId` 时没有详情或预约按钮。
8. 用户主动选择静态回退后，页面持续显示“演示模式”；断线不会伪装生成成功。
9. 小程序底栏固定“首页｜游乌东｜向导｜寨里｜我的”，中央叶片没有重复悬浮入口。
10. “加入行程”后在“我的”立即可见；预约仍需独立页面二次确认。
11. 所有游客可见文案为“贵州乌东”；内部目录与英文标识不做无关迁移。
12. 未授权原图与本机派生图没有进入公开 Git 提交。
13. 有来源时默认显示“参考了 N 条乌东资料”并可展开标题；无来源时不显示检索标签。
14. AI 已创建待确认预约时，确认页读取并确认同一个预约 ID，不产生第二笔订单；读取失败不伪造成功。
15. 任何预约确认或后台状态写入失败时原状态保持不变；缺少对应服务实景的卡片显示类别占位，不使用错配照片。

## 计划自检索引

- 规格第 1～3 节：任务 1、2、4、5、7 覆盖命名、素材、色彩、字体和唯一视觉签名。
- 规格第 4～5 节：任务 4 覆盖点击开卷、五联、探景、雾与叶片。
- 规格第 6 节：任务 3、5、8 覆盖稳定线程、真实事件、六类卡片、来源、动作和演示降级。
- 规格第 7 节：任务 5 保留游客页与后台闭环，只做品牌修正。
- 规格第 8 节：任务 6～8 覆盖五栏、工具首页、最小“我的”和移动工作台。
- 规格第 9～11 节：任务 1、4～8 覆盖组件边界、素材元数据、可访问性、性能和本地资源。
- 规格第 12～14 节：所有任务服从三天范围；文末只列用户验收，不增加自动化测试工作。
