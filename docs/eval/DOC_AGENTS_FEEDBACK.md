# 文档 / 图片 / PDF 助手评测反馈

> 2026-08-13｜回应「为什么总评那几个助手」——本轮专门覆盖文档类。  
> 同日复跑：配好 vision 后 **12/12 PASS**。

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
| PASS ×11 | 粘贴正文、txt/PDF 附件路径 |
| PASS ×1 | `gui-doc-image-recognition` + PNG：走 `mimo-v2.5` vision，识别为纯色图并完成结构化输出 |

## 本轮为跑绿做的配置

1. **去掉 DeepSeek 误标的 vision**（`deepseek-v4-flash` 实际不支持 `image_url`）
2. **Mimo 密钥**：生产 Provider/ModelConfig 换成可用的 `mimo-proxy` 密钥；`AI_VISION_OCR_MODEL=mimo-v2.5`
3. **评测 PNG**：由无效 1×1 改为合法 64×64 色块（部分 vision API 会拒收坏图）

## 此前已落地的结构性修复

1. **document-parse 回退 message**；澄语 invoke 附件写入 `input.file`
2. **HITL 批准保留上游 LLM 文本**
3. Catalog `supportedInputs` 仍多为 `text`，待按图节点声明 file/image

## 约定

- 问答/搭建：`intelligent,parity,dashboard`
- **文档族**：`python3 scripts/doc_agent_eval.py`
- 完整回归：两类都跑

## 下一步（可选）

1. Catalog `supportedInputs` 按图节点声明 file/image  
2. 扫描件 PDF / 复杂版式专项样例集  
3. 文档族并入发布门禁
