# 附件预览走查修复 Implementation Plan

> **Status: ARCHIVED · DONE（2026-08-20）** — B1 接线 `v-model:attachment` 已落地。
>
> **For Claude / agentic workers:** 严格按任务勾选落地。本计划修复 `2026-08-19-attachment-preview-uiux` 合入后走查发现的问题，**不要重做**已可用的降级壳 / PDF iframe / Esc / UIUX 文档。

**Goal:** 让多图切换与摘要→附件预览在真实对话中可用；收敛为 Bubble 单例 Modal；清掉空实现与死代码。

**来源:** 2026-08-19 走查（commit `bb9d0cf` 之后）

**Tech Stack:** Vue 3 + TS；现有 `AttachmentPreviewModal` / `MessageBubble` / `MessageAttachmentList` / `DocumentSummaryList`。

---

## Global Constraints

1. 不引入 Cordis / harness / Element Plus ImageViewer。
2. 不假预览 Office（降级壳保持现状）。
3. 用户文案仍用「助手 / 对话 / 处理状态」。
4. 改动范围限前端预览链路；Task 5 Office excerpt 仍不在本计划。
5. 代码注释用 JSDoc。

---

## 问题清单（走查）

| ID | 严重度 | 现象 | 根因落点 | 状态 |
|---|---|---|---|---|
| B1 | **P0** | 多图 ←/→ 与按钮无效果 | Modal emit 后 Bubble 未接 `v-model:attachment`（走查残留） | ✅ 已修复（2026-08-20：`v-model:attachment`） |
| B2 | **P0** | 计划写「Bubble 单例」，实际双壳 | `MessageAttachmentList` 与 `MessageBubble` 各挂一个 Modal | ✅ 已修复 |
| B3 | **P0** | 摘要点不开真实附件 | Summary 只拿当前助手消息 `attachments`；用户文件在上一条 user 消息 | ✅ 已修复 |
| B4 | 中 | 计数显示如 `3 / 2` | `currentIndex` 用全量 gallery，分母用图片数 | ✅ 已修复 |
| B5 | 中 | `[img, pdf, img]` 切不到第二张 | `hasPrev/hasNext` 只看相邻项是否图片，不跳过非图 | ✅ 已修复 |
| B6 | 低 | 死代码 / 未用 import | List `closePreview`；Summary 未用 import；Modal `onMounted` 残留 | ✅ 已修复（2026-08-20 清 `onMounted`） |
| B7 | 低 | 每条消息常驻 keydown | Modal `onMounted` 绑 `document`，应按打开态挂卸 | ✅ 已修复 |
| B8 | 低 | 关闭钮热区偏小 | `.preview-action-btn` 高度 &lt; 44px | ✅ 已修复 |
| D1 | 文档 | 原计划 Task4 / 验收 #1#4#6 误勾 ✅ | `2026-08-19-attachment-preview-uiux.md` | ✅ 已修复 |

---

## 文件地图

| 文件 | 动作 |
|---|---|
| `frontend/src/components/message/AttachmentPreviewModal.vue` | 修切图、图集索引、键盘挂卸、热区 |
| `frontend/src/components/message/MessageAttachmentList.vue` | 去掉内嵌 Modal；改为 `emit('preview', att)` |
| `frontend/src/components/message/DocumentSummaryList.vue` | 清未用 import；匹配逻辑可保持，附件源由父传入 |
| `frontend/src/components/MessageBubble.vue` | 单例 Modal；附件列表 + 摘要共用；解析可预览附件池 |
| `frontend/src/components/MessageList.vue`（或 Bubble 的 props） | 若 Bubble 需「向前找 user 附件」，由 List 传入 `sessionMessages` 或 `resolveAttachments` |
| `docs/superpowers/plans/2026-08-19-attachment-preview-uiux.md` | 更正误勾；链到本计划 |
| `docs/superpowers/plans/README.md` | 登记本计划；修正「Task1-4 已完成」表述 |

---

## Task 0：核对

- [x] 读本计划问题表 B1–B8
- [x] 打开上述文件确认现状仍与走查一致（空 `goPrev`、双 Modal、Summary 仅 `message.attachments`）
- [x] `frontend` 能 build

---

## Task 1（P0）：修好图集切换 — B1 / B4 / B5

**验收：** 同消息 ≥2 张图时，按钮与 ArrowLeft/ArrowRight 能切换；夹杂 PDF 时仍能在图片间跳；计数为「当前图序 / 图片总数」（从 1 起）。

推荐实现（选一种，写清）：

**方案 A（推荐）：** Modal 增加

```ts
emit('update:attachment', MessageAttachment)
// 或
emit('navigate', MessageAttachment)
```

父组件更新 `previewAttachment`。

**方案 B：** Modal 内 `active = ref(attachment)`，`watch(() => props.attachment)` 同步；切图只改 `active`，关闭时复位。

无论 A/B：

- [x] 计算 `imageGallery = gallery.filter(isImage)`（无 gallery 时用当前图单元素组）
- [x] `imageIndex` 在 `imageGallery` 上找当前 id
- [x] `goPrev` / `goNext` / 方向键真正切换
- [x] 计数：`imageIndex + 1` / `imageGallery.length`
- [x] 删掉空注释块

---

## Task 2（P0）：Bubble 单例 Modal — B2

**目标结构：**

```text
MessageBubble
  ├─ MessageAttachmentList @preview="openPreview"
  ├─ DocumentSummaryList   @preview="openPreview"
  └─ AttachmentPreviewModal（唯一）
       v-model + :attachment + :gallery="imagePool"
```

- [x] `MessageAttachmentList` 删除内嵌 `AttachmentPreviewModal` 与本地 `previewOpen` 状态；点击改为 `emit('preview', att)`
- [x] `MessageBubble`：`openPreview(att)` 设置 `previewAttachment` + `previewOpen`；`:gallery` 传图集；**必须** `v-model:attachment` 承接切图（2026-08-20 补齐）
- [x] 确认同一气泡不会出现两个 dialog

---

## Task 3（P0）：摘要匹配真实附件源 — B3

**验收：** 用户消息上传 `contract.pdf` → 助手返回同名 `documentSummaries` → 点击摘要能打开预览（降级或 PDF 壳）。

- [x] 在 `MessageList`（或等价父级）向每个 `MessageBubble` 传入：
  - `sessionMessages: Message[]`，或
  - 预计算的 `previewAttachments: MessageAttachment[]`
- [x] 解析规则（按序）：
  1. 当前消息 `attachments`
  2. 若不足：在会话列表中**向前**找最近一条 `role===user'` 且含附件的消息，合并（同 id 去重）
  3. Summary `findAttachment`：先可选 `attachmentId`，再 filename 忽略大小写
- [x] `DocumentSummaryList` 的 `:attachments` 使用上述合并池，不再只用助手消息自身附件
- [x] 无匹配时仍显示「无对应附件，请从附件列表打开」，不抛错

> 若改 MessageList props 成本高：允许 Bubble `inject` / 由 Workspace 经 List 透传；禁止为匹配去打新 API。

---

## Task 4（低）：键盘生命周期与热区 — B7 / B8

- [x] 仅在 `modelValue === true` 时 `addEventListener('keydown')`，关闭或 `onUnmounted` 时移除
- [x] 关闭 / 「打开原图」等操作钮 `min-height` / `min-width` ≥ 44px（可用 padding 补齐）
- [x] gallery 左右钮保持 ≥ 36px（移动已有），桌面尽量 ≥ 40px

---

## Task 5（低）：清理 — B6

- [x] 删除未使用函数 / import / computed
- [x] `vue-tsc` / build 无新增告警（项目既有 chunk 体积警告可忽略）

---

## Task 6：文档回写 — D1

- [x] 在 `2026-08-19-attachment-preview-uiux.md`：
  - Task 4 与验收表 #1 / #4 / #6 改为未完成或加注「见 follow-up 计划」
  - 文首加一行：`Status: 前端初版已合入；走查缺陷见 2026-08-19-attachment-preview-followup.md`
- [x] 更新本计划勾选与 `plans/README.md`
- [x] 验收表全过后再考虑把**两个**附件预览计划一并 archive（或只 archive 本 follow-up）

---

## 验收清单

| # | 场景 | 期望 | 状态 |
|---|---|---|---|
| 1 | 同消息 2+ 张 png | 打开后按钮与方向键可切换；计数正确 | ✅（含 2026-08-20 Bubble `v-model:attachment`） |
| 2 | 同消息 img + pdf + img | 两张图之间可切换，不被 PDF 挡住 | ✅ |
| 3 | user 上传 pdf，assistant 摘要同名 | 点摘要打开预览壳 | ✅ |
| 4 | 摘要文件名对不上 | 提示「无对应附件…」，无白屏 | ✅ |
| 5 | 任意预览 | 仅一个 dialog；Esc 关闭；焦点可回触发钮 | ✅ |
| 6 | 打开多个历史气泡后反复预览 | 无残留 keydown 异常（关一个不影响另一条） | ✅ |
| 7 | `npm run build`（frontend） | 通过 | ✅ |

---

## 范围外

| 项 | 说明 |
|---|---|
| 后端 Office excerpt | 仍另 PR |
| harness / Cordis | 未立项 |
| Office 应用内真预览 | 不做 |
| 跨会话附件匹配 | 不做 |

---

## 执行口令

```text
1. Task 0
2. Task 1 切图（先修 Modal）
3. Task 2 单例（List emit + Bubble 唯一 Modal）
4. Task 3 附件池（List/Bubble 向前合并 user 附件）
5. Task 4–5 收尾
6. Task 6 文档
7. 跑验收表 + build
```
