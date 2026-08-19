# Superpowers Plans 索引

> 活跃计划只放在本目录；**已完成计划移入 `archive/`，避免重复开工。**

## 约定

1. 新建计划：`docs/superpowers/plans/YYYY-MM-DD-<feature>.md`
2. 全部任务勾选且已合并/部署后：移到 `archive/`，标题加 `Status: ARCHIVED · DONE`
3. 开新需求前先查本索引与 `archive/`，勿重复已落地范围
4. 产品级未完成项仍以 `docs/PRD.md` / `docs/DEVELOPMENT_PLAN.md` / `docs/TASKS.md` 为准

## 活跃计划

| 计划 | 摘要 |
|---|---|
| [附件预览 UIUX 对齐](2026-08-19-attachment-preview-uiux.md) | 统一预览壳；图/PDF/docs；摘要跳转；可选 Office excerpt（不含 harness） |
| [内容展示优化](2026-08-13-content-display-optimization.md) | Result 可读性、处理信息抽屉、附件预览 |
| [文件上传、附件展示与限流](2026-08-13-upload-attachments-ratelimit.md) | 上传落盘 `~/payflow/agentChat`、消息附件展示、租户+用户限流 |

> **Status Update:** 附件预览 UIUX 对齐计划的 Task 1-4 已完成（前端实现），Task 5 跳过（后端 Office excerpt），Task 6 文档已更新。可进行 build 验收。

## 已归档

| 计划 | 完成日 | 摘要 | 勿重复做 |
|---|---|---|---|
| [内容解析与用户接话](archive/2026-08-13-content-parse-and-composer.md) | 2026-08-13 | marked/DOMPurify、MessageParts、Composer 能力条与布局 | 拆段/Markdown 升级、能力占位、工具栏分离 |

## 仍开放（产品路线图，非本目录计划）

- SSE、自动路由等 → `docs/DEVELOPMENT_PLAN.md` Phase 3+
- 搜索/收藏等 O-06 其余子项
