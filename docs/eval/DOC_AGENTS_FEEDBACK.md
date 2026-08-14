# 文档 / 图片 / PDF 助手评测反馈

> 2026-08-14｜生产走查 + 平台/澄语联修后复跑  
> 同日结果：**12/12 PASS**

## 评测范围

| 助手 | 场景 |
|------|------|
| 文档摘要 | 会议纪要粘贴 + txt 附件 |
| 合同条款提取 | 合同正文 / txt / 最小 PDF |
| 合同风险标注 | 合同正文 + HITL 确认 |
| 简历筛选 | 简历粘贴 + txt |
| 报销单审核 | 报销单正文 + HITL |
| 多文档对比 | 两版合同差异 |
| 文档/图片识别 | OCR 文本粘贴；PNG 真图 |

脚本：`scripts/doc_agent_eval.py`  
产物：`docs/eval/latest_doc_agents.json` / `.log`

## 结果（最新）

**12 PASS / 0 FAIL**

| 结果 | 说明 |
|------|------|
| PASS ×10 | 粘贴正文、txt 附件路径 |
| PASS ×2 | PDF 合同提取 + PNG vision |

Work 三助手（问答 / parity / 仪表盘）同日 `real_dialog_eval`：**9/9 PASS**。

## 本轮根因与修复

### 1. 粘贴无附件 → `$input.file` 硬失败（平台）

文档图首节点多为 `document-parse`（stream）。Chat 仅发 `message` 时生产报：

> 未指定上传文件流（$input.file）…

**修复：**

- `resolveMessageAsTextFile`：无文件时把足够长的 `message` 合成 `paste.txt`
- `document-parse` stream 路径回退该合成文件
- `vision-analyze`：无图或非图片时，把粘贴 OCR 正文当 `text-fallback` description
- 澄语 `ChatService.withPasteFallbackFile`：无附件时同步写入 invoke `input.file`（双保险）

### 2. HITL 批准后气泡只剩 approve JSON（平台 + Chat）

合同风险标注批准后，末节点输出变成 `{approved,comment,...}`。

**修复：**

- resume 时 `lastOutput` 恢复为 waiting 前的上游分析（`waitingRecord.input.lastOutput`）
- Chat `RuntimeRestAdapter` 跳过 HITL 批准回声，并从成功节点取正文
- 前端 `humanizeRuntimeError`：技术错误 / 批准回声转用户文案

### 3. 评测判定

- 「决议」别名增加「结论 / 建议」，避免摘要用词不同误杀

## Chat message 优化

- 缺文件 / 非图片 / 批准回声 → 可读 Markdown 引导
- 部署：`deploy/deploy.sh`（backend + frontend 已重建）；nginx `-t` 有无关重复 location 告警，健康检查仍 OK

## 约定

- 问答/搭建：`python3 scripts/real_dialog_eval.py --only intelligent,parity,dashboard`
- 文档族：`python3 scripts/doc_agent_eval.py`
- 平台热修路径：`/home/ubuntu/schema-platform/server/ai/services/`（有 `.bak.*`）

## 下一步（可选）

1. Catalog `supportedInputs` 按图声明 file/image，Composer 能力条诚实展示上传
2. 平台热修补进 schema-platform 正式发版，减少 PM2 热文件漂移
3. 扫描件 PDF / 复杂版式专项样例
