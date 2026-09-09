# 乌东首页苗纹护卷与单屏探景实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将首页入口升级为富有乌东气质的苗纹护卷，改善五联实景的留白与光影，并让五联探景在桌面端一屏内完整呈现。

**架构：** 保留现有 Vue 状态和素材数据，仅调整三个首页组件的语义结构，并在现有全局样式末尾增加一组高优先级首页样式。动画继续依赖 CSS，不引入新依赖。

**技术栈：** Vue 3、CSS、Vite

---

## 文件结构

- 修改 `web/src/components/home/ScrollGate.vue`：提供卷轴木轴、织锦边、文化纹理和中心题签的语义层。
- 修改 `web/src/components/home/FiveSceneHero.vue`：增加五联外框、场景副题和按场景区分的样式标识。
- 修改 `web/src/components/home/SceneExplorer.vue`：拆分图片题签与短章信息区，限制桌面内容量。
- 修改 `web/src/style.css`：实现卷轴细节、五联留白与光影、单屏探景和响应式降级。

### 任务 1：丰富苗纹护卷

- [x] **步骤 1：补充卷轴语义层**

在 `ScrollGate.vue` 中保留原有点击与动画结束逻辑，为左右面板增加木轴、织锦边、山水线和银饰光点子元素；为中心区增加章节眉题。

- [x] **步骤 2：实现卷轴视觉和开启动画**

在 `style.css` 中使用渐变、伪元素和 CSS 图形建立木构、靛青织边、水波与银饰微光；确保 `.is-opening` 和减少动态效果仍沿用现有状态。

- [x] **步骤 3：在浏览器检查入口**

打开 `http://127.0.0.1:5174/`，清除当前标签页的 `sessionStorage` 后刷新。预期：入口无雾，必须点击打开；卷轴两侧细节清楚但不压过中央题字。

### 任务 2：重排五联实景

- [x] **步骤 1：补充画框与题签结构**

在 `FiveSceneHero.vue` 中添加外层画框、顶部题签和场景副题映射，继续使用 `scene.id` 与现有图片数据。

- [x] **步骤 2：实现留白、比例与五种光影**

在 `style.css` 中将桌面五联改为带最大宽度、左右纸页留白和 6–8px 窄缝的网格；中心寨景使用较宽列；通过 `data-scene` 分别添加冷雾光、水面反光、暖日、叶影和暖暮光。

- [x] **步骤 3：保留移动横向浏览**

在 `max-width: 760px` 下继续使用横向吸附卡片，去掉桌面木框的厚重边距，确保画面焦点和按钮可操作。

### 任务 3：压缩探景为单屏短章

- [x] **步骤 1：拆分图像区和信息区**

在 `SceneExplorer.vue` 中增加 `.scene-explorer__media`、`.scene-explorer__caption` 和 `.scene-explorer__copy`，保留关闭、遮罩、Escape 和主行动事件。

- [x] **步骤 2：限制桌面尺寸和溢出**

在 `style.css` 中将桌面探景限制为 `min(92vw, 1500px)` × `min(78vh, 720px)`，图片/信息采用 58/42 分栏；信息区禁止纵向滚动，故事最多显示三行。

- [x] **步骤 3：保留小屏可读性**

在小屏下使用图上文下的紧凑布局，图片高度受限，关闭按钮始终可见。

### 任务 4：范围自检与交付

- [x] **步骤 1：检查变更范围**

运行 `git diff -- web/src/components/home/ScrollGate.vue web/src/components/home/FiveSceneHero.vue web/src/components/home/SceneExplorer.vue web/src/style.css`。预期：只涉及已确认的三个界面，不修改素材、路由、后端或测试文件。

- [x] **步骤 2：提供人工验收入口**

保持 Vite 端口 `5174` 可访问，并给出四项可见检查：入口细节、点击开卷、五联留白与光影、探景无滚动条。

> 本计划遵循项目约定，不新增自动化测试，不运行全量、回归或测试矩阵。
