# 内容解析与用户接话 Implementation Plan

> **Status:** `ARCHIVED` · `DONE` · 2026-08-13  
> **Do not re-implement.** 已落地并部署；后续相关需求开新计划，勿在此文件续写任务。  
> **落地分支：** `cursor/agent-plus-tooltip`（含 `f7986e6` 等）

**Goal:** 对齐 `ai/app` 的消息拆段契约，升级正文渲染与结果展示，并让 Composer 按助手能力诚实接话（含文件能力未开放提示）。

**Architecture:** 解析仍集中在 `textParser.ts`；展示拆成轻量 part 渲染（text/code/artifact/document summary），不引入完整 RendererRegistry。Composer 由 `supportedInputs` 驱动能力条，文件上传仅占位不伪造闭环。

**Tech Stack:** Vue 3 + Pinia；`marked` + `dompurify`（对齐 ai/app TextRenderer）；Element Plus tooltip。

**Spec:** `docs/PRD.md` P0/P1、`docs/UIUX.md` ResultRenderer、`docs/TASKS.md` O-06（文件不进 MVP，但能力提示要诚实）

## Global Constraints

- 用户文案只用「助手 / 对话 / 处理状态 / 需要确认 / 处理信息」
- 前端不持有 Runtime 密钥；附件上传完整闭环留 P1
- 不复制 Schema/Flow 编辑器卡片，只做消费端预览

---

## 文件地图

| 文件 | 职责 |
|---|---|
| `docs/superpowers/plans/archive/2026-08-13-content-parse-and-composer.md` | 本计划（已归档） |
| `frontend/src/utils/textParser.ts` | 拆段 + Markdown（升级） |
| `frontend/src/components/message/MessageParts.vue` | 按 part 渲染 |
| `frontend/src/components/message/DocumentSummaryList.vue` | 文档摘要卡 |
| `frontend/src/components/MessageBubble.vue` | 外壳 + 接 MessageParts |
| `frontend/src/components/Composer.vue` | 能力条 + 文件占位 |
| `frontend/src/views/WorkspaceView.vue` | 传入 supportedInputs |
| `frontend/package.json` | marked / dompurify |

---

### Task 1：对齐拆段契约

- [x] 对齐 `ai/app`：`<schema>` 后冗余总结过滤；围栏语言支持 `word:` 形式
- [x] 保留现有单行围栏与空白段规范化

### Task 2：Markdown 升级

- [x] 安装 `marked` + `dompurify`（+ `@types/dompurify` 如需）
- [x] `renderMarkdown` 改为 marked.parse + DOMPurify.sanitize

### Task 3：结果渲染组件

- [x] `MessageParts`：text / code / artifact 分区展示
- [x] `DocumentSummaryList`：有 `documentSummaries` 时展示文件名+摘要
- [x] `MessageBubble` 接入上述组件

### Task 4：用户接话（Composer）

- [x] Composer 接收 `supportedInputs`
- [x] 展示能力提示（文本 / 文件即将开放 / 需要确认）
- [x] 含 `file` 时显示禁用上传按钮 + tooltip「文件能力即将开放」
- [x] WorkspaceView 传入当前助手能力
- [x] 文本区与工具栏分离（避免多行内容被底部控件遮挡）

### Task 5：验收

- [x] `tsx` 脚本覆盖 split + markdown 样例
- [x] 前端 `pnpm build` 通过

---

## 范围外（未纳入本计划 / 仍按产品路线图）

| 项 | 状态 |
|---|---|
| 真实文件/图片上传闭环（PRD P1 / TASKS O-06） | 未做，仅诚实占位 |
| ai/app 级 Artifact 可交互编辑、Schema/Flow 卡片 | 未做 |
| 产品总计划 Phase 0–4 全量勾选 | 见 `docs/DEVELOPMENT_PLAN.md` / `docs/TASKS.md`，非本计划范围 |
