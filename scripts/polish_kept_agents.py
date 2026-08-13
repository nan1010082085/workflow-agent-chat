#!/usr/bin/env python3
"""
对澄语保留的已发布智能体做落地打磨：中文名称/描述、关键 systemPrompt。
默认 --apply。
"""
from __future__ import annotations

from pathlib import Path
from datetime import datetime, timezone
from pymongo import MongoClient

META = {
    "gui-intelligent-assistant": {
        "name": "智能助手问答",
        "description": "检索知识库后用中文回答产品/表单/流程问题，适合日常答疑。",
    },
    "gui-chat-parity-assistant": {
        "name": "智能助手 v2",
        "description": "意图路由 → 需求分析 → 确认 → 任务规划 → 专家协作 → 摘要，适合复杂搭建诉求。",
    },
    "gui-dashboard-assist": {
        "name": "仪表盘助手",
        "description": "根据业务场景推荐指标、图表类型、布局分区与配色，输出可落地的看板方案。",
    },
    "gui-document-summary": {
        "name": "文档摘要",
        "description": "解析文档并生成结构化中文摘要，突出要点与待办。",
    },
    "gui-doc-image-recognition": {
        "name": "文档 / 图片识别",
        "description": "识别上传图片或文档内容，提取关键字段与可读摘要。",
    },
    "gui-contract-extract": {
        "name": "合同条款提取",
        "description": "解析合同文档，结构化提取关键条款、金额、期限与风险点。",
    },
    "gui-contract-risk-tag": {
        "name": "合同风险标注",
        "description": "标注合同风险等级与关键条款，人工确认后输出终稿。",
    },
    "gui-cs-kb-reply": {
        "name": "客服知识库回复",
        "description": "检索知识库并生成客服回复草稿，语气专业简洁。",
    },
    "gui-memory-assistant": {
        "name": "记忆增强助手",
        "description": "结合长程记忆个性化回答，并自动沉淀新的偏好与事实。",
    },
    "gui-smart-form-search": {
        "name": "智能表单检索",
        "description": "按字段与组件类型检索已有表单，给出匹配结果与选用建议。",
    },
    "gui-requirement-gated-build": {
        "name": "需求门控构建",
        "description": "先澄清需求再规划任务，经确认后联动编辑器/流程专家落地。",
    },
    "gui-resume-screening": {
        "name": "简历筛选",
        "description": "解析简历并对照岗位要求评分，输出录用建议与理由。",
    },
    "gui-expense-audit": {
        "name": "报销单审核",
        "description": "核对报销凭证金额与合规性，列出异常项与处理建议。",
    },
    "gui-multi-doc-compare": {
        "name": "多文档对比",
        "description": "对比多份文档差异与一致性，给出合并建议。",
    },
    "gui-feedback-analysis": {
        "name": "客户反馈分析",
        "description": "对客户反馈做情感与主题分析，输出可执行汇总。",
    },
}

DASHBOARD_SYSTEM = """你是大屏/仪表盘设计顾问。根据用户业务场景，给出可落地的指标、图表、布局与配色建议。

## 输出格式（Markdown）

## 推荐指标与图表
- 列出 6～8 个核心指标，每个写清：指标含义、图表类型、为什么适合

## 布局建议
- 用二级标题按业务分区（如概览 / 转化 / 商品 / 异常）

## 配色方案
- 给出色板（含 hex）与主次用途

## 告警与下一步
- 若用户问到异常/告警，给出可执行规则；否则给 2～3 条落地步骤

## 规则
- 全程中文，具体可执行，避免空泛
- 多轮时承接上文，不要重复整套开场
- 需求不清时做合理假设并标明"""

DASHBOARD_PROMPT = "用户仪表盘需求：\n{{$input.message}}\n\n请给出完整、可落地的设计建议。"

PARITY_ANALYZER_HINT = (
    "你是需求分析助手。只针对用户真实业务诉求提问。"
    "禁止把系统路由、专家 JSON、内部节点输出当成用户需求。"
    "用户说「帮我弄一下」这类模糊话时，用短问题澄清：要做表单/流程/看板/查询中的哪一类，业务对象是什么。"
)


def load_uri(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("MONGODB_URI="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit("no uri")


def patch_graph(graph: dict | None, slug: str) -> bool:
    if not isinstance(graph, dict):
        return False
    changed = False
    for node in graph.get("nodes") or []:
        if not isinstance(node, dict):
            continue
        data = node.setdefault("data", {})
        ntype = node.get("type")
        if slug == "gui-dashboard-assist" and ntype == "llm":
            if data.get("systemPrompt") != DASHBOARD_SYSTEM:
                data["systemPrompt"] = DASHBOARD_SYSTEM
                changed = True
            if data.get("prompt") != DASHBOARD_PROMPT:
                data["prompt"] = DASHBOARD_PROMPT
                changed = True
            data["useConversationHistory"] = True
            data["label"] = "仪表盘分析与推荐"
            data["label"] = "仪表盘分析与推荐"
            changed = True
            if node.get("id") == "trigger-1" or ntype == "manual-trigger":
                pass
        if slug == "gui-dashboard-assist" and ntype == "manual-trigger":
            if data.get("label") != "描述看板需求":
                data["label"] = "描述看板需求"
                changed = True
        if slug == "gui-dashboard-assist" and ntype == "end":
            if data.get("label") != "结束":
                data["label"] = "结束"
                changed = True
        # chat-parity / requirement-gated：强化需求分析节点文案
        if slug in {"gui-chat-parity-assistant", "gui-requirement-gated-build"}:
            label = str(data.get("label") or "")
            if ntype in {"requirement-analyzer", "llm"} and (
                "需求" in label or ntype == "requirement-analyzer"
            ):
                sp = str(data.get("systemPrompt") or "")
                if "禁止把系统路由" not in sp:
                    data["systemPrompt"] = (sp + "\n\n" + PARITY_ANALYZER_HINT).strip()
                    changed = True
    return changed


def main() -> None:
    uri = load_uri(Path("/home/ubuntu/schema-platform/.env"))
    col = MongoClient(uri)["schema-form"]["agentworkflows"]
    now = datetime.now(timezone.utc)
    for slug, meta in META.items():
        doc = col.find_one({"slug": slug, "status": "published"})
        if not doc:
            print("MISS", slug)
            continue
        upd = {
            "name": meta["name"],
            "description": meta["description"],
            "updatedAt": now,
        }
        pub = doc.get("publishedGraph")
        draft = doc.get("draftGraph")
        if patch_graph(pub, slug):
            upd["publishedGraph"] = pub
        if patch_graph(draft, slug):
            upd["draftGraph"] = draft
        col.update_one({"_id": doc["_id"]}, {"$set": upd})
        print("OK", slug, "->", meta["name"])


if __name__ == "__main__":
    main()
