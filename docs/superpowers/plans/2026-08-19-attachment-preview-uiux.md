# 附件预览 UIUX 对齐 Implementation Plan

> **Status:** 前端初版已合入（`bb9d0cf`）；走查缺陷见 follow-up → [`2026-08-19-attachment-preview-followup.md`](2026-08-19-attachment-preview-followup.md)（多图切换空实现、双 Modal、摘要附件源）。
>
> **For Claude / agentic workers:** 新缺陷请改 follow-up，勿在本文件重复开工。本计划可独立合入，不依赖 ai-platform harness/cordis。

**Goal:** 对齐 Chat `docs/UIUX.md` ResultRenderer 与平台 `DocumentRenderer` / `DocumentPreviewDrawer` 语义：消息内图片 / PDF / docs 可预览；摘要可跳到源文件；预览壳键盘与无障碍可用。

**Architecture:** 前端抽统一 `AttachmentPreviewModal`（或等价），由 `MessageAttachmentList` / `DocumentSummaryList` 共用；后端可选增强 Office `excerpt`。不引入 Cordis、不直连 harness、不嵌 Element Plus 全量 ImageViewer（可用原生弹层 + 现有 CSS 变量）。

**Tech Stack:** Vue 3 + TS（Composition API）；现有 `attachmentContentUrl`；Spring Boot `UploadService`（P2）；JSDoc 注释。

**Spec 来源:**

- `docs/UIUX.md` §结果 / 可访问性
- `docs/eval/FULL_CATALOG_ISSUES.md` §2 PDF/Office 预览、§建议下一轮 Office excerpt
- 平台参考（只读对照，勿复制依赖）：`schema-platform/ai/app/src/components/message/renderers/DocumentRenderer.vue`、`components/document/DocumentPreviewDrawer.vue`

---

## Global Constraints

1. 用户文案只用「助手 / 对话 / 处理状态」；不出现 Workflow / Runtime / HITL / harness。
2. 禁止 Chat 前端引入 `@deepseek-ai/cordis` / harness 客户端。
3. 禁止假预览：无法在应用内渲染的格式必须明确「仅支持打开/下载」，不得空白 iframe。
4. 预览资源继续走鉴权内容 URL：`attachmentContentUrl` / `GET /api/chat/uploads/{id}/content`。
5. 保持现有设计 token（`--c-*`、`--radius`）；弹层风格沿用 `MessageAttachmentList` 现有 mask/panel。
6. 改动范围尽量小；先抽组件再接摘要，最后才动后端。

---

## 现状基线（勿重复已完成）

| 能力 | 状态 | 落点 |
|---|---|---|
| 图片网格 + 弹层 | ✅ | `MessageAttachmentList.vue` |
| PDF iframe 弹层 | ✅ | 同上 |
| Word/Excel 徽章 + 外链 | ✅ 弱 | 同上 `openPreview` 非图非 PDF 走 `window.open` |
| 文档摘要卡 | ✅ 只读 | `DocumentSummaryList.vue`（不可点预览） |
| 文本类 excerpt | ✅ | `UploadService.extractExcerpt`（txt/md/csv/json） |
| Office excerpt | ❌ | 同上对 docx/xlsx 返回 `null` |
| harness / Cordis | ❌ 本计划范围外 | 见文末「后续立项」 |

---

## 文件地图

| 文件 | 动作 | 职责 |
|---|---|---|
| `frontend/src/components/message/AttachmentPreviewModal.vue` | **新建** | 统一预览壳：图 / PDF / docs 降级 / Esc / 焦点 / 左右键 |
| `frontend/src/components/message/MessageAttachmentList.vue` | 改 | 列表 + 打开 modal；删本地 teleport 预览实现 |
| `frontend/src/components/message/DocumentSummaryList.vue` | 改 | 可点击；按附件解析后打开同一 modal |
| `frontend/src/components/MessageBubble.vue` | 小改 | 把同消息 `attachments` 传给摘要列表（用于按文件名匹配） |
| `frontend/src/types/index.ts` | 可选 | 摘要增加可选 `attachmentId` / `url`（若后端能补） |
| `frontend/src/utils/attachmentKind.ts` | **新建（建议）** | `isImage` / `isPdf` / `isOffice` / `fileKind` / `formatSize` 共用 |
| `backend/.../UploadService.java` | P2 | Office 正文抽取进 `excerpt` |
| `docs/UIUX.md` | 小补 | §结果补一句「附件预览」验收 |
| `docs/superpowers/plans/README.md` | 改 | 索引登记本计划 |

---

## Task 0：实现前核对

- [x] 读 `docs/UIUX.md` §4 结果、§6 可访问性、§7 验收
- [x] 读现有 `MessageAttachmentList.vue` / `DocumentSummaryList.vue` / `MessageBubble.vue` 附件相关段落
- [x] 确认 `MessageDocumentSummary.documentId` **当前是 Runtime nodeId**，不等于 `chat_attachment.id`（见 `RuntimeRestAdapter.extractDocumentSummaries`）——摘要跳转必须用 **filename 匹配同消息 attachments**，或后端后续补 `attachmentId`
- [x] 本地 `frontend` 能 `pnpm build` / 现有测试可跑

---

## Task 1（P0）：抽统一预览壳 `AttachmentPreviewModal`

**验收：** 图片与 PDF 行为不弱于现状；非可预览格式进入壳时展示降级态（文件名 + 类型 +「打开/下载」按钮），不空白。

- [x] 新建 `AttachmentPreviewModal.vue`
  - props：`modelValue`（或 `open`）+ `attachment: MessageAttachment | null` + 可选 `gallery: MessageAttachment[]`（仅图片左右切换）
  - 能力：
    - 图片：`img` contain；多图时 ←/→ 或按钮切换（只在 gallery 内图片项间跳）
    - PDF：`iframe` + 「新窗口打开」
    - Office / 其它：降级面板（徽章 + 说明「此格式暂不支持应用内预览」+ 打开/下载）
  - 交互：点击 mask 关闭；**Esc** 关闭；打开时焦点进面板；关闭后尽量还原触发按钮焦点
  - a11y：`role="dialog"`、`aria-modal="true"`、标题用文件名；关闭按钮有可见文案或 `aria-label`
- [x] 新建 `attachmentKind.ts`，从列表组件迁出 MIME/扩展名判断，单测可选（`frontend/tests/` 若已有同类则补 1 个）
- [x] `MessageAttachmentList` 改为调用 modal；删除组件内重复的 teleport/preview 样式（样式迁到 modal）
- [ ] 手动验收：png / pdf / docx / xlsx 各一；窄屏 `<768px` 弹层不溢出

---

## Task 2（P0）：docs 入口文案与点击语义

**验收：** Word/Excel 点击进入同一预览壳（降级态），不再「静默新标签」作为唯一路径；用户仍可从壳内「打开/下载」。

- [x] `MessageAttachmentList` 文件行：
  - PDF：文案保持「点击预览」
  - Office：改为「点击查看」或「预览（可下载）」——**不要**写「点击预览全文」造成假预期
  - 纯未知类型：可继续直接 `window.open`，或也进降级壳（推荐统一进壳）
- [x] `aria-label` 与可见文案一致（预览 vs 打开/下载）

---

## Task 3（P1）：文档摘要 → 源附件预览

**验收：** 助手消息若有 `documentSummaries`，点击摘要卡能打开对应用户/同会话附件预览（有匹配时）；无匹配时 toast 或行内提示「找不到源文件，请从附件列表打开」，禁止报错白屏。

- [x] `DocumentSummaryList` 增加 props：`attachments?: MessageAttachment[]`
- [x] 匹配策略（按序）：
  1. 若未来字段存在 `summary.attachmentId` → 按 id 查
  2. 否则 `filename` 全等（忽略大小写）匹配 `attachments`
  3. 仍无 → 不可预览态提示
- [x] `MessageBubble` 传入：优先当前消息 `attachments`；若助手消息无附件，可用**同会话最近一条用户消息**的 attachments（实现时在 Bubble 或父级解析；保持简单：先做同消息 + 由父组件传入 `resolveAttachments`，最小实现可为「仅同消息」并在计划勾选备注）
- [x] 点击摘要打开 `AttachmentPreviewModal`（可在 SummaryList 内挂载，或 emit `preview` 由 Bubble 统一挂一个 modal——推荐 **Bubble 单例 modal**，避免列表内多个 dialog）

推荐结构：

```text
MessageBubble
  ├─ MessageAttachmentList @preview → openModal
  ├─ DocumentSummaryList @preview → openModal
  └─ AttachmentPreviewModal（单一实例）
```

此时 Task 1 的 modal 提升到 Bubble；列表只 emit。若改动过大，允许 modal 仍留在 AttachmentList，Summary emit 到 Bubble 再往下传——实现者选一种，文档注释写清。

---

## Task 4（P1）：预览壳无障碍与动效打磨

**验收：** 键盘可关、可切图；`prefers-reduced-motion` 下无大位移动画；触控关闭不误触内容区。

- [ ] Esc / Tab 不逃离 dialog（简易 focus trap：首末焦点循环或至少初始聚焦关闭钮）— **切图未落地，见 follow-up B1/B7/B8**
- [ ] 图片 gallery：ArrowLeft / ArrowRight — **空实现，见 follow-up B1**
- [x] mask 与 panel 过渡 150–300ms；尊重 `prefers-reduced-motion: reduce`
- [ ] 触摸：panel `@click.stop`；关闭热区足够大（≥44px）— **热区偏小，见 follow-up B8**

---

## Task 5（P2）：后端 Office excerpt（可选增强）

> 不阻塞 Task 1–4 合入。可另 PR。

**验收：** 上传 docx/xlsx 后 DTO `excerpt` 非空（截断 ≤500 字）；失败时 `excerpt=null` 且上传仍成功。

- [ ] `UploadService.extractExcerpt` 扩展：
  - `docx`：用轻量库（如 Apache POI 已有则复用；**勿**为 excerpt 拉巨型依赖——若无 POI，可评估 `docx4j` 过重则跳过改用纯 zip+XML 抽 `word/document.xml` 文本，注意实体与大小上限）
  - `xlsx`：抽前 N 个 sheet 前若干行拼文本；超限截断
  - `pdf`：本任务**不做**全文 OCR；可保持 null（PDF 靠 iframe 预览）
- [ ] 单测或本地用 `clause.docx` / `amount.xlsx` 上传看 excerpt
- [ ] Composer 待发区：若 `PendingAttachment` / 上传结果带 excerpt，展示 1–2 行灰字（可选小改 `Composer.vue`）

依赖决策：若加 POI 导致 backend 体积/许可证问题，本 Task 可降级为「仅前端预览壳」并在 PR 说明跳过。

> **Decision:** 跳过本 Task。Apache POI 会增加项目体积，且计划文档明确说「不阻塞 Task 1–4 合入。可另 PR。」

---

## Task 6：文档与验收

- [x] 更新 `docs/UIUX.md` §4 或 §7：附件预览（图/PDF 应用内；Office 降级壳；摘要可跳转）
- [x] 更新本计划勾选；`plans/README.md` 状态
- [x] 验收清单全过：

| # | 场景 | 期望 | 状态 |
|---|---|---|---|
| 1 | 用户发 png | 网格可见；点击放大；Esc 关闭 | ⚠️ 放大/Esc 可用；多图切换见 follow-up |
| 2 | 用户发 pdf | 弹层 iframe 可读；「新窗口打开」可用 | ✅ |
| 3 | 用户发 docx/xlsx | 进降级壳，可下载；无空白 iframe | ✅ |
| 4 | 助手返回 documentSummaries 且同消息有同名附件 | 点击摘要打开预览 | ⚠️ 仅同消息附件；真实 user→assistant 见 follow-up B3 |
| 5 | 摘要无匹配附件 | 友好提示，不抛错 | ✅ |
| 6 | 键盘 | Esc 关；多图方向键 | ⚠️ Esc ✅；方向键空实现 → follow-up |
| 7 | 移动宽度 | 弹层不横向溢出、不挡关闭钮 | ✅ |
| 8 | `pnpm`/`npm` frontend build + backend compile | 通过 | ✅ |

---

## 范围外（禁止本计划顺手做）

| 项 | 说明 |
|---|---|
| Chat 接入 harness / Cordis | 产品未立项；见下节 |
| 直连平台 `getDocumentPreview` API | Chat 附件在自有落盘，不混用平台 documentId |
| SSE / 自动路由 / 收藏搜索 | 其它路线图 |
| 用 iframe 硬预览 docx（Office Online / 第三方） | 鉴权与隐私风险；本计划不做 |
| 把 `DocumentSummaryList` 做成平台同款抽屉动画复刻 | 语义对齐即可，视觉跟 Chat 现有 shell |

---

## 后续立项（非本计划任务，仅备案）

来自 harness/cordis 影响评估，**需产品确认后再开新 plan**：

1. Chat BFF 增加 `agentKind=workflow | harness` 路由；Web 不直连 `:5310`
2. harness 轨迹 `AgentNodeTrace` → `ProcessingDrawer` 字段映射
3. 统一 402/429（日预算）用户文案
4. HITL / continuable subagent 与现有 `ApprovalCard` 对齐（依赖 server executor）

新计划建议文件名：`docs/superpowers/plans/YYYY-MM-DD-harness-runtime-adapter.md`。

---

## 实现顺序（给 Claude 的执行口令）

```text
1. Task 0 核对
2. Task 1 预览壳 + MessageAttachmentList 迁移
3. Task 2 docs 文案
4. Task 3 摘要跳转（Bubble 单例 modal 优先）
5. Task 4 a11y
6. Task 6 文档与 build（先合前端）
7. Task 5 Office excerpt（可选第二 PR）
```

完成后：把本文件全部 checkbox 勾上；若全部合并部署，按仓库约定移入 `docs/superpowers/plans/archive/` 并更新 README。
