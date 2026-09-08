# MEDIA-PIPELINE-001 静态审查记录

- 审查制品：`b10ca9f52787d13d9d839eabef4ba96e42ee87ae`
- 基线制品：`6407585ccb5b239ec011face9b9cd32e0d5dd6a5`
- 锁定计划：`docs/superpowers/plans/2026-09-08-wudong-frontend-redesign.md`（任务 1）
- 审查方式：仅执行 Git 提交、差异、提交树和文本静态检查；未运行脚本、测试或构建。
- tests：SKIPPED（任务限制）

| 项目 | 结论 | 静态证据 |
| --- | --- | --- |
| 1. 变更范围 | PASS | `git diff --name-status 6407585 b10ca9f` 仅列出 `.gitignore`、`scripts/prepare-local-media.ps1`、`web/src/data/scenes.js`、`miniprogram/utils/media.js`。 |
| 2. 忽略规则 | PASS | `git show b10ca9f:.gitignore` 显示仅新增 `resours/贵州乌东图片/`、`web/public/images/wudong-local/`、`miniprogram/assets/wudong-local/` 三行；提交差异未包含 `.superpowers/`。 |
| 3. 未提交原图或派生图 | PASS | `git ls-tree -r --name-only b10ca9f` 不包含 `resours/`、`web/public/images/wudong-local/` 或 `miniprogram/assets/wudong-local/` 下的文件。 |
| 4. 派生脚本 | PASS | `scripts/prepare-local-media.ps1` 静态映射包含 `乌东苗寨.jpg`、`采茶.png`；源目录为 `resours/贵州乌东图片`，输出仅为两个派生目录；缩放比取最长边比例，缺失源文件抛错，未写入源目录。 |
| 5. 场景与小程序清单 | PASS | `web/src/data/scenes.js` 顺序为 mountain/water/village/tea/people；寨窄联为 `village`、展开为 `village-aerial`，茶为 `tea`；`miniprogram/utils/media.js` 的八项键与脚本 Target 一致。 |

结论：PASS。审查对象为锁定提交 `b10ca9f`；当前工作区的后续未提交状态未用于替代该制品结论。
