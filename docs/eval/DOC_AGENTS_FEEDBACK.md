# 文档 / 图片 / PDF 助手评测反馈

> 2026-08-13｜回应「为什么总评那几个助手」——本轮专门覆盖文档类。

## 评测范围（此前缺失，现已补）

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

## 结果（修复后）

**11 PASS / 1 FAIL**

| 结果 | 说明 |
|------|------|
| PASS ×11 | 粘贴正文与 txt 附件路径已通 |
| FAIL ×1 | `gui-doc-image-recognition` + 真 PNG：平台 vision 模型不支持 `image_url` |

## 发现的结构性问题（已落地修复）

1. **document-parse 只认 `$input.file`**  
   澄语只传 `message` → 全部文档助手直接失败。  
   - 平台：`documentParse` 无文件时回退把 `message` 当文本解析  
   - 澄语：invoke 把附件 Base64 写入 `input.file` / `input.files`

2. **HITL 批准后 end 吐出 approve JSON**  
   合同风险标注确认后正文变成 `{"approved":true...}`。  
   - 平台 resume：批准后保留上游 LLM 文本作为 `lastOutput`

3. **Catalog 谎称 supportedInputs=text**  
   实际文档助手依赖 parse/vision；需后续改 catalog 如实声明 `file`/`image`。

4. **真图 / 多模态**  
   仍缺可用的 vision 模型配置（`AI_VISION_OCR_MODEL` / 模型中心勾选 vision）。

## 对「反反复复评那几个」的纠正

此前默认 `real_dialog_eval --only intelligent,parity,dashboard,model` 确实偏了。  
约定：

- 问答/搭建：`intelligent,parity,dashboard`
- **文档族必须跑**：`python3 scripts/doc_agent_eval.py`
- 完整回归：两类都跑，FAIL 写入本反馈并改图/改运行时

## 下一步

1. 配置可用 vision 模型，让 PNG/JPG 识别过关  
2. Catalog `supportedInputs` 按图节点声明 file/image  
3. PDF 复杂版式（扫描件）专项样例集  
4. 将本脚本并入 CI/发布门禁（文档族不过不标「可用」）
