# TASK-007 r5 独立静态验收记录

- task_id：`TASK-007`
- dispatch_id：`dispatch-TASK-007-r5`
- 基线：`dd2c17670a65e3e174a6eabe3c53d9642decc927`
- 制品：`e10f759c1ea30ebb66eba8d6124339e18b3db22c`
- 证据指纹：`sha256:8d91b7b377bde0fd928f31e2e4564a73bf014ac5a3082767a390f692ef3ba344`
- tests：SKIPPED
- build：SKIPPED
- runtime：NOT_EXECUTED

| 锁定项 | 结论 | 独立静态证据 |
| --- | --- | --- |
| 制品、范围与指纹 | PASS | `dd2c176..e10f759` 为祖先区间，包含 `1aa587e`、`e10f759` 两个连续提交，`git diff --name-only` 仅列出 5 个路径，均在 r5 写范围；`TASK-007.yaml` 的制品与指纹精确匹配；`git diff --check` 无输出。 |
| 动态 reduced-motion 状态收束 | PASS | `ScrollGate.vue` 与 `WudongJourney.vue` 都创建 `MediaQueryList`，在挂载时订阅 `change`、卸载时移除监听，并从 `event.matches` 重读状态；进入 reduce 时分别调用受状态保护的 `finishOpen()`、`finishFogTransition()`。初始 reduce 在进入 opening/passing 后立即收束，正常模式仍由对应动画结束事件收束。 |
| 开卷事件绑定 | PASS | 开卷的 `animationend` 监听已绑定到实际动画元素 `.scroll-gate__panel--left/right`，不再将子元素事件交给带 `.self` 的父元素过滤；首次到达的事件收束状态，后续事件由状态守卫安全忽略。 |
| Web 社区图片对与稳定回退 | PASS | `App.vue` 以字符串稳定 ID 查找 `posts` 回退项；只有 `coverUrl` 与 `coverAlt` 同时存在时采用 API 图，否则保留策展封面或非空 `乌东社区分享配图暂缺`。 |
| 小程序服务未知项与图片语义 | PASS | `normalizeService()` 保留 API `category`，为无同 ID 回退项提供 `categoryLabels` 与非空 `乌东{类别}实景暂缺`；API 图仍要求 `imageUrl/imageAlt` 成对。基线与制品的六项预置服务 `imageAlt` 文本未变。 |
| 小程序社区图片对 | PASS | `normalizePost()` 以稳定 ID 合并，只有 `coverUrl/coverAlt` 成对才使用 API 图；未知且无图项目回退为非空 `乌东社区分享配图暂缺`，`community.js` 复用该规范化函数。 |
| 未越界 | PASS | 变更未触及 CSS、预约、后台裸图或任务 5/8 工作台路径；未引入测试、构建、服务或外部调用。 |

结论：PASS。以上为制品文本静态验收；未执行运行时验证。
