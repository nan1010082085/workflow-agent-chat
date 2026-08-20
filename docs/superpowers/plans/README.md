# Superpowers Plans 索引

> 活跃计划只放在本目录；**已完成计划移入 `archive/`，避免重复开工。**

## 约定

1. 新建计划：`docs/superpowers/plans/YYYY-MM-DD-<feature>.md`
2. 全部任务勾选且已合并/部署后：移到 `archive/`，标题加 `Status: ARCHIVED · DONE`
3. 开新需求前先查本索引与 `archive/`，勿重复已落地范围
4. 产品级未完成项仍以 `docs/PRD.md` / `docs/DEVELOPMENT_PLAN.md` / `docs/TASKS.md` 为准

## 活跃计划

> 当前无活跃计划。所有计划已完成并归档。

> **Status Update（2026-08-20）：** 所有计划已完成并归档。

## 已归档

| 计划 | 完成日 | 摘要 | 勿重复做 |
|---|---|---|---|
| [内容展示优化](archive/2026-08-13-content-display-optimization.md) | 2026-08-20 | Result 可读性、处理信息抽屉、附件预览 | JSON 美化、ProcessingDrawer、思考/步骤折叠 |
| [文件上传、附件展示与限流](archive/2026-08-13-upload-attachments-ratelimit.md) | 2026-08-20 | 上传落盘 `~/payflow/agentChat`、消息附件展示、租户+用户限流 | UploadService、限流 Filter、Composer 上传 |
| [附件预览走查修复](archive/2026-08-19-attachment-preview-followup.md) | 2026-08-20 | 切图接线、单例 Modal、摘要匹配 user 附件、a11y | 空 `goPrev`、双 Modal、仅同消息摘要 |
| [附件预览 UIUX 对齐](archive/2026-08-19-attachment-preview-uiux.md) | 2026-08-20 | 统一预览壳 / Office 降级 / 计划骨架 | 勿再开同范围初版；缺陷已在 follow-up 收口 |
| [内容解析与用户接话](archive/2026-08-13-content-parse-and-composer.md) | 2026-08-13 | marked/DOMPurify、MessageParts、Composer 能力条与布局 | 拆段/Markdown 升级、能力占位、工具栏分离 |

## 仍开放（产品路线图，非本目录计划）

- SSE、自动路由等 → `docs/DEVELOPMENT_PLAN.md` Phase 3+
- 搜索/收藏等 O-06 其余子项
