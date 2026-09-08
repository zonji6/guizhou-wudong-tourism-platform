# TASK-007 独立静态验收记录

- task_id：`TASK-007`
- dispatch_id：`dispatch-TASK-007-r2`
- 基线：`b10ca9f52787d13d9d839eabef4ba96e42ee87ae`
- 制品：`46e0a2e7e76771793875eaa9df8f09989646c347`
- 证据指纹：`sha256:761eab11c85ee4f4af8be5408bce2640aab839237e5a8f7e6e58ab8eda958793`
- tests：SKIPPED
- build：SKIPPED
- runtime：NOT_EXECUTED

| 验收项 | 结论 | 静态证据 |
| --- | --- | --- |
| 范围与差异 | PASS | `git diff --name-status b10ca9f..46e0a2e7` 的 32 个路径均落在 TASK-007 r2 写范围；`git diff --check` 无输出。 |
| 游客可见文案统一 | FAIL | `git grep '乌冬' 46e0a2e7 -- web miniprogram` 仍命中 `miniprogram/pages/assistant/*`、`pages/detail/detail.wxml`、`pages/orders/orders.wxml`。 |
| 稳定 UUID 与演示边界 | PASS | Web 与小程序 `utils/data demo` 使用相同 UUID；演示资源均带 `demoData` 或可见演示标识；本任务未接入自动加载的 `demoAssistantCard`。 |
| 首次开卷状态机 | FAIL | `ScrollGate.vue` 的 `@animationend.self="finishOpen"` 绑定在 `.scroll-gate`，但 `style.css` 只给 `.scroll-gate__panel--left/right` 定义 `open-left/open-right` 动画。子元素冒泡事件被 `.self` 排除，`finishOpen()` 不会执行，`gateState` 停在 `opening`，不会写入 sessionStorage 或发出 `opened`。 |
| 五联、探景与动作 | PASS | `scenes.js` 固定 mountain/water/village/tea/people；寨窄联为 `village`、展开为 `village-aerial`，茶为 `tea`；`SceneExplorer` 具备关闭、遮罩关闭与动作事件。 |
| 雾、叶片与减少动态 | PASS | 雾只由 `WudongJourney` 的 IntersectionObserver 触发，动画结束才发出 `journey-entered`；叶片仅据此显示；减少动态仍保留非零 `.18s` 动画时长。 |
| SafeImage、路由与模板 | PASS | `SafeImage` 在空源或 `error` 时输出带 `role=img`、`aria-label` 的占位；`App.vue` 仅以 `HomePage` 替换首页并保留资源、详情、社区、助手、预约和后台 hash 路由。 |
| 小程序五栏与工具首页 | FAIL | 首页、资源、社区、我的分别设置 0/1/3/4 选中态，资源分类会一次性清空且异步回包按当前 active 重筛；资源/社区有语义占位。但 `custom-tab-bar` 切换只调用 `switchTab`，既有 `pages/assistant/assistant.js` 没有 `onShow()` 设置 `selected:2`，向导 tab 的选中态不正确。 |
| 指纹绑定 | PASS | `.codex/multi-agent/tasks/TASK-007.yaml` 的 `artifact_revision` 与 `evidence_fingerprint` 精确匹配锁定制品与给定指纹。 |

结论：REJECT。未执行测试、构建、服务、浏览器或微信开发者工具。
