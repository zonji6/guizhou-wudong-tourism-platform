# 贵州乌东水彩插画资产记录

生成日期：2026-09-09。生成方式：Codex 内置图像生成工具；未使用项目 DeepSeek 密钥或其他命令行图像服务。

两张插画均为概念艺术，不作为实景照片、导航地图、服饰考据或特定商家商品图。用户提供的 PDF 用作水彩媒介参考，乌东实景与采茶照用于地点和生活参考；没有复制 PDF 的 Logo、课程图表和其他贵州地标。

## 资产与用途

| 资产 | 项目文件 | 用途 |
| --- | --- | --- |
| 开卷主画 | `.worktrees/frontend-redesign/web/public/images/wudong-art/scroll-watercolor.png` | 左右卷面使用同一全幅画；中央 HTML 标题；手动开卷 |
| 溪岸水彩 | `.worktrees/frontend-redesign/web/public/images/wudong-art/riverbank-watercolor.png` | 沿溪叙事与向导区装饰，不替换真实五联照片 |

两张图片均为 1536 × 1024 PNG。原始生成文件保留在本机生成目录，未删除：

- 开卷主画原始文件名：`exec-9df6e776-d0c3-412b-90ef-c72356a0ca61.png`。
- 溪岸水彩原始文件名：`exec-d6c10c5a-4cef-4ad3-b9bd-83c99153a12c.png`。

原件在本机图像生成目录保留；公开文档只记录文件名，不公开本机用户目录。

本轮本机演示已使用图片；未执行 Git 推送。生成结果仍需人工检查地域、服饰与权利边界，不能以“AI 生成”推定参考图权利问题自动消失。染布和银饰采用视觉意象，不解释纹样寓意。

## 开卷主画最终提示词

下方英文是实际生图输入记录，便于复现；文档说明与开发规范使用中文。

```text
Use case: illustration-story / style-transfer.
Asset type: a SINGLE wide panoramic watercolor painting to cover a tourism website's opening two-leaf scroll, landscape 3:2 composition, high resolution.
References: image 1 is STYLE ONLY (watercolor travel-journal palette, hand-painted paper atmosphere); ignore its logos, text, layout, unrelated Guizhou buildings. Image 2 shows Wudong's ACTUAL wooden houses and rounded densely forested Leigong mountains; use that locality and roof forms, ignore all lettering. Image 3 is clothing inspiration for a small fictional tea-picking woman, not a portrait copy.
Primary request: create an exquisite richly detailed yet airy pale-watercolor travel journal illustration celebrating Wudong Miao village in Guizhou, China. A peaceful real inhabited mountain valley, mountain streams linking everyday life. Delicate pencil line under translucent watercolor, authentic handmade travel sketchbook quality, granulation, blooming edges, warm off-white cold-pressed paper.
Composition: central upper/middle third (x36%-64%, y20%-62%) is OPEN calm warm ivory negative space for live HTML title and click button, without a hard white rectangle. Rich scenes wrap around the left/right sides and bottom like one continuous illustrated panorama. Upper left and upper right: layered broad rounded blue-green wooded slopes fading into warm paper, not pillar karst. Left middle: tea shrubs and a tiny tea picker with basket, black-indigo modest blouse, small warm embroidered details, neat bun. Right middle: hillside dark timber stilt houses with grey tiled roofs, stone steps, small simple timber covered bridge over a clear narrow creek. Bottom left: a woman in daily indigo clothing working beside a dye basin and hanging indigo cloth with very simple white stitch-like marks. Bottom right: tea cup, loose fresh tea sprig, finely observed small silver neck ring and bracelet as travel-journal still-life details, not a giant crown. A clear pale turquoise stream curves across lower half linking both halves, pebbles, moss, ferns, soft ochre earth. Tiny rice fields may be green only; keep season coherent. The whole composition feels quiet, fresh, warm, light-filled, alive; lots of painterly detail around sides, clear centre.
Palette: warm paper #FAF7EF, pale jade #A9C8AD, diluted lake blue #B7D5DF, muted teal #397F7A, indigo #36596D, small apricot ochre #C98C63. No large dark field.
Constraints: NO typography, Chinese characters, logo, watermark, frame, scroll rollers or UI. No fog covering the landscape. No pagodas, Dong drum towers, tall silver horn crowns, huge waterfalls, limestone needles, lantern city, photo collage, vector-flat art, childlike cartoon or generic fantasy. It is a conceptual travel illustration, NOT a map or historical reconstruction. Respectful people engaged in work, not exotic display.
```

参考图：PDF 第 1 页渲染图、现有 village-large.jpg、tea-large.jpg。生成前均已查看。

## 溪岸背景最终提示词

```text
Use case: illustration-story / style-transfer. Asset: a single wide watercolor scenic border/background for a Wudong travel-journal web page, 3:2 landscape.
Use attached painting ONLY as medium/palette reference; create a quieter complementary scene without its people, houses, tea cup or jewelry. Main subject: a delicate stream entering the lower foreground, curving gently between mossy pebbles and banks, tiny pale green ferns, tea sprigs, loose blue-green mountain silhouettes receding in upper left and right corners. Centre and most upper two-thirds of composition must remain nearly blank warm off-white cold pressed paper #FAF7EF. Rich detail is limited to the bottom fifth and bottom left/right corners, with watercolor edges dissolving gradually into bare paper. Genuine translucent pale jade and misty lake blue watercolor washes, nuanced granulation, confident delicate pencil lines; light, airy, fresh, slow and warm. Small ochre stones, muted teal creek. Far quieter and lighter than reference, ~75% negative paper space for overlaying website content. No text, logos, borders, UI, buildings, people, mythical shapes, dense full-page landscape, sharp geometric shapes. Not a geographical map. High-quality handmade travel sketchbook art.
```

参考图：已生成的开卷主画，仅延续媒介与配色，不复用其人物和器物。
