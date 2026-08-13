# 澄语智能体评测反馈（落地版）

> 生成时间：2026-08-13  
> 范围：平台已发布 Agent 精简 + 核心三助手真实多轮评测

## 1. 平台侧已落地的运行时迭代（热修，非演示探针）

这些改动在 **Schema Platform `server/ai` 节点运行时**，解决的是「对话断了 / 空总结 / 失忆 / 空 JSON」：

| 模块 | 问题 | 改动 |
|------|------|------|
| `expert.js` | 专家吃到上游路由 JSON | 优先 `ctx.input.message` |
| `intentRouter.js` | 意图路由读错输入 | 同上 |
| `summarizer.js` + `runtime/summarizer.js` | 空任务链输出「无可总结」盖住真实回复 | 透传专家回复；注入对话历史 |
| `llm.js` | 多轮失忆；空 `{text:""}` | 默认带 history；空内容 → `nodeFailure` |
| `agentLoop.js` | 循环内不落历史 | 写入 `conversationHistory` |
| HITL resume（此前热修） | resume 冲掉用户 message | 合并 approve payload 与原 message |

说明：以上多数仍是 **生产热补丁**（有 `.bak.*`），尚未全部回灌到 schema-platform 主仓发版流程。

## 2. 本次：剔除无效 / 半搭建智能体

- **原发布**：51  
- **归档**：36（演示、空白、试用、webhook-only、半接线生成、行业 LLM stub、空 slug）  
- **保留（澄语产品白名单）**：15  

保留：

1. 智能助手问答  
2. 智能助手 v2  
3. 仪表盘助手  
4. 文档摘要  
5. 文档 / 图片识别  
6. 合同条款提取  
7. 合同风险标注  
8. 客服知识库回复  
9. 记忆增强助手  
10. 智能表单检索  
11. 需求门控构建  
12. 简历筛选  
13. 报销单审核  
14. 多文档对比  
15. 客户反馈分析  

脚本：`scripts/curate_platform_agents.py`（`--apply` 归档）、`scripts/polish_kept_agents.py`（中文名/描述 + 仪表盘 prompt）。

## 3. 真实多轮评测结果（有反馈，可迭代）

命令：

```bash
python3 scripts/real_dialog_eval.py --only intelligent,parity,dashboard \
  --json-out docs/eval/latest_real_dialog.json
```

| 助手 | 轮次 | 结果 | 反馈 |
|------|------|------|------|
| 智能助手问答 | 3 | **全过** | 能讲清表单/流程差异，多轮承接 OK |
| 智能助手 v2 | 3 | **全过** | 催款邮件多轮改写 OK；需继续盯 HITL 模糊话术 |
| 仪表盘助手 | 3 | **全过** | 指标/布局/告警均落地；中文名与 prompt 已打磨 |

原始日志：`docs/eval/latest_real_dialog.log`  
原始 JSON：`docs/eval/latest_real_dialog.json`

## 4. 下一轮智能体迭代清单（按优先级）

1. **智能助手 v2 HITL**：模糊输入「帮我弄一下」——本轮复测已变为正常澄清问句（无「系统路由」误判）；后续继续压缩 confirmQuestions 长度与字段重复。  
2. **平台热修补进主仓**：把 expert/intent/summarizer/llm/agentLoop 变更合入 schema-platform 源码与发布，避免 PM2 热文件漂移。  
3. **保留的 12 个非核心助手**：各补 1 组真实业务多轮（合同/报销/简历/客服），不过关再改图或下架。  
4. **澄语侧**：Catalog 可增加可选 allowlist（双保险），避免平台误重新发布演示工作流又灌进助手列表。

## 5. 对「测试没有反馈、没有智能体迭代」的澄清

此前部分测试停在 **连通性/空白修复**，没有把 FAIL 原因写回「改哪个节点 / 哪段 prompt」。  
从本轮起约定：

- 评测产物必须进 `docs/eval/`  
- FAIL → 写进本反馈表 → 改平台图或节点 → 再跑同一 scenario  
- 演示/半搭建不进澄语 Catalog
