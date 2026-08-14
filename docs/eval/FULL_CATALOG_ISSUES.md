# 全量评测问题清单与修复记录

> 2026-08-14｜15 助手 × 5 轮 + 3 模型 × 5 轮 + 附件矩阵（txt/pdf/png/docx/xlsx）

## 汇总

| 批次 | 结果 |
|------|------|
| 全量助手首跑 | 68/75 PASS（后半 JWT 过期 401） |
| 补跑 smart-form / dashboard | 9/10 PASS（1 次 RUNNING 超时空回复） |
| 普通 LLM（Flash / Mimo / Pro） | **15/15 PASS** |
| 附件矩阵 | **5/5 PASS**（上传 + 回显 + invoke） |

**有效结论：** 15 个可用助手均已至少跑通多轮；模型与多格式附件链路可用。

## 发现的问题与处理

### 1. Catalog `supportedInputs` 全是 `text`（高）

- **现象：** Composer 隐藏上传；文档助手无法在 UI 选文件  
- **根因：** 平台 `/invoke/catalog` 硬编码 `supportedInputs: ['text']`  
- **修复：** 按 `publishedGraph` 节点推断 file/image，并以 slug 兜底（含 `multi-doc`）；澄语 `agentStore` 再兜底；助手中心展示「图片」能力标签  
- **复验：** 文档类已返回 `['text','file']` / `['text','file','image']`

### 2. PDF / Office 预览弱（高）

- **现象：** 仅图片有弹层；PDF/docx/xlsx 只有外链  
- **修复：** `MessageAttachmentList` — PDF iframe 预览；Word/Excel 类型徽章 + 打开/下载；MIME 猜测补齐 docx/xlsx  
- **状态：** 已合入并部署

### 3. 长跑评测 JWT 过期（中）

- **现象：** ~1h 后 SESSION/上传 401，误伤 dashboard / 模型 / 附件  
- **修复：** `full_catalog_eval.py` 401 自动重登重试  

### 4. 智能表单检索偶发 RUNNING 超时（中）

- **现象：** R1 轮询结束仍 RUNNING + 空回复  
- **处理：** poll 上限 90→120；其余 4/5 轮 PASS  
- **后续：** 盯平台该助手耗时 / 超时配置

### 5. 评测脚本模型会话创建（已修）

- 勿传非法 `modelId` 建会话；改为空会话 + `/completions`

## 附件矩阵（复验 PASS）

| 文件 | 结果 |
|------|------|
| note.txt | PASS |
| contract.pdf | PASS |
| scan.png | PASS |
| clause.docx | PASS |
| amount.xlsx | PASS |

## 产物

- `scripts/full_catalog_eval.py`
- `docs/eval/full_catalog_latest.json` / `.log`
- `docs/eval/full_catalog_rerun_agents.json`
- 本文件

## 建议下一轮

1. 把 `rounds` 拉到 8–10 做稳定性门禁（带 401 重登）  
2. UI E2E：Composer 上传按钮可见性 + PDF 弹层  
3. Office 正文抽取（docx/xlsx excerpt）进 document-parse，而不只是传文件流  
