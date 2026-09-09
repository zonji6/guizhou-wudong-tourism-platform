# 乌东首页入寨长卷与自然动效实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把首页卷帘、雾气、溪流和叶片整合为一段连续的乌东入寨体验。

**架构：** 保留现有 Vue 事件和数据流，使用语义化装饰节点与内联 SVG 提供可控图形；样式继续集中在现有 `style.css` 的首页覆盖区，不引入图片生成资产、动画库或新依赖。三个任务顺序执行，避免同时编辑全局样式造成冲突。

**技术栈：** Vue 3、CSS、SVG、IntersectionObserver、Vite

---

## 文件结构

- 修改 `web/src/components/home/ScrollGate.vue`：构建左右连续长卷与中央叶形银坠锁扣。
- 修改 `web/src/components/home/WudongJourney.vue`：渲染三层雾、S 形溪流、章节锚点和自然装饰。
- 修改 `web/src/components/home/LeafGuide.vue`：渲染叶片、叶脉、水纹和可见标签。
- 修改 `web/src/pages/HomePage.vue`：侦测向导区域并传递叶片的靠近状态。
- 修改 `web/src/style.css`：实现上述视觉、响应式和减少动态效果降级。

### 任务 1：将卷帘扩展为乌东入寨长卷

**文件：**
- 修改：`web/src/components/home/ScrollGate.vue`
- 修改：`web/src/style.css`

- [x] **步骤 1：增加长卷图形结构**

在左右面板中增加 `.scroll-gate__story`，内部使用装饰性 SVG 绘制相连山脊、溪流、茶枝、风雨桥、染布人物侧影和抽象银饰；所有图形容器设置 `aria-hidden="true"`。中央按钮内部增加 `.scroll-gate__seal`，由叶形、银坠和按钮文字组成。

- [x] **步骤 2：编排开卷动作**

在 `style.css` 中让锁扣点击后先产生一圈水纹，再让图形随面板向左右轻拉伸并退出；保留 `@animationend.self`、约 1.5 秒总时长以及 180ms reduced-motion 降级。

- [x] **步骤 3：检查实现范围**

运行 `git diff --check -- web/src/components/home/ScrollGate.vue web/src/style.css`。预期：无空白错误，仅可能出现 Windows 换行提示。

### 任务 2：实现三层雾和沿溪入寨路径

**文件：**
- 修改：`web/src/components/home/WudongJourney.vue`
- 修改：`web/src/style.css`

- [x] **步骤 1：拆分三层雾**

将单一 `.journey__fog` 改为包含远、中、近三层的容器，每层独立动画但只由容器触发一次 `finishFogTransition`；保持 `pointer-events:none` 与现有 IntersectionObserver 状态机。

- [x] **步骤 2：增加溪流和自然背景**

在旅程容器中加入装饰性 SVG：宽水面路径、内侧亮线、三处涟漪和连接服务入口的支流。为章节输出 `data-chapter` 和索引变量，使照片与文字沿 S 形河道错位布局。

- [x] **步骤 3：将服务入口变成溪边停靠点**

保留原按钮、路径和点击事件，把列表改为石形底座、短支线与题签；移动端将 S 形溪流退化为左侧纵向水线。

- [x] **步骤 4：检查实现范围**

运行 `git diff --check -- web/src/components/home/WudongJourney.vue web/src/style.css`。预期：无空白错误，不运行测试或构建。

### 任务 3：补齐一叶同行和水纹反馈

**文件：**
- 修改：`web/src/components/home/LeafGuide.vue`
- 修改：`web/src/pages/HomePage.vue`
- 修改：`web/src/style.css`

- [x] **步骤 1：重构叶片按钮结构**

使用内联 SVG 绘制茶叶轮廓与叶脉，增加两层 `.leaf-guide__ripple` 和 `.leaf-guide__label`；保留原生按钮、`aria-label` 和 `open` 事件。

- [x] **步骤 2：传递靠近向导状态**

在 `HomePage.vue` 中用一个轻量 IntersectionObserver 观察 `assistantAnchor`，将布尔值传给 LeafGuide 的 `near-assistant` 属性；组件卸载时断开观察。

- [x] **步骤 3：实现漂落、停驻和水纹**

叶片首次出现时沿短弧漂落约 1.2 秒，停驻后低频浮动；hover/focus 触发两圈水纹，near-assistant 时缩小和降透明度。reduced-motion 下只淡入，不漂落、不浮动、不扩散。

- [x] **步骤 4：检查实现范围**

运行 `git diff --check -- web/src/components/home/LeafGuide.vue web/src/pages/HomePage.vue web/src/style.css`。预期：无空白错误，不运行测试或构建。

### 任务 4：浏览器人工验收与交付

**文件：**
- 检查：以上五个实现文件

- [x] **步骤 1：执行整体差异检查**

运行 `git diff --check` 与 `git diff --name-only`。预期：本轮新增实现只涉及五个指定文件；设计与计划文档单独记录。

- [x] **步骤 2：在 5174 端口进行桌面验收**

清除当前标签页 `sessionStorage`，依次观察闭合长卷、点击开卷、五联下滑雾层、沿溪章节、叶片悬停与点击。预期：六项用户可见验收均成立。

- [x] **步骤 3：交付用户确认**

保持 `http://127.0.0.1:5174/` 可访问并切换到新版页面；明确区分已完成实现、浏览器人工检查和按用户要求跳过的测试/构建。

> 本计划服从项目开发约定：不新增自动化测试，不运行测试、构建或 lint。
