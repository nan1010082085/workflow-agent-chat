#!/usr/bin/env python3
"""
梳理并取消发布平台上无效 / 半搭建的 Agent Workflow。

判定规则（任一命中 → unpublish，status=archived）：
1. slug/name/desc 含：演示、demo、空白、试用、待服务端、可再接
2. slug 为空，或 slug 以 workflow- 开头的试用项
3. 图节点过少（≤3）且仅为 blank/模板骨架
4. 入口仅为 webhook/schedule，且无 manual/chat 友好入口（澄语不可用）
5. 行业模板纯 LLM stub 且 description 过短 / 无真实工具边

保留白名单（产品向，可对话落地）：
- gui-intelligent-assistant
- gui-chat-parity-assistant
- gui-dashboard-assist
- gui-document-summary
- gui-doc-image-recognition
- gui-contract-extract
- gui-contract-risk-tag
- gui-cs-kb-reply
- gui-memory-assistant
- gui-smart-form-search
- gui-requirement-gated-build
- gui-resume-screening
- gui-expense-audit
- gui-multi-doc-compare
- gui-feedback-analysis

用法（在装有 pymongo 的服务器上，读取 schema-platform .env）：
  python3 curate_platform_agents.py [--apply]
默认 dry-run。
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Any

from pymongo import MongoClient
from bson import ObjectId

KEEP_SLUGS = {
    "gui-intelligent-assistant",
    "gui-chat-parity-assistant",
    "gui-dashboard-assist",
    "gui-document-summary",
    "gui-doc-image-recognition",
    "gui-contract-extract",
    "gui-contract-risk-tag",
    "gui-cs-kb-reply",
    "gui-memory-assistant",
    "gui-smart-form-search",
    "gui-requirement-gated-build",
    "gui-resume-screening",
    "gui-expense-audit",
    "gui-multi-doc-compare",
    "gui-feedback-analysis",
}

DEMO_RE = re.compile(
    r"(演示|demo|空白|试用|待服务端|可再接|从零开始|模板骨架|vertical.?检索演示|流程控制演示|代码执行演示|定时触发演示)",
    re.I,
)

CHAT_TRIGGERS = {"manual-trigger", "chat-trigger", "start", "trigger"}
WEBHOOK_TRIGGERS = {"webhook-trigger", "schedule-trigger"}


def load_uri(env_path: Path) -> str:
    for line in env_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("MONGODB_URI="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit(f"MONGODB_URI missing in {env_path}")


def node_types(graph: dict | None) -> list[str]:
    if not graph or not isinstance(graph, dict):
        return []
    nodes = graph.get("nodes") or []
    out = []
    for n in nodes:
        if isinstance(n, dict):
            t = n.get("type") or ""
            if t:
                out.append(str(t))
    return out


def reasons_to_drop(doc: dict[str, Any]) -> list[str]:
    """
    澄语产品白名单：不在 KEEP_SLUGS 一律归档。
    额外标注原因，便于审计。
    """
    slug = (doc.get("slug") or "").strip()
    name = doc.get("name") or ""
    desc = doc.get("description") or ""
    graph = doc.get("publishedGraph") or doc.get("draftGraph") or {}
    types = node_types(graph if isinstance(graph, dict) else {})
    reasons: list[str] = []

    if slug in KEEP_SLUGS:
        return []

    reasons.append("not_in_chengyu_whitelist")
    if not slug:
        reasons.append("empty_slug")
    if slug.startswith("workflow-"):
        reasons.append("trial_slug")
    if DEMO_RE.search(f"{slug} {name} {desc}"):
        reasons.append("demo_or_half_copy")
    if slug.endswith("-demo") or "-demo" in slug:
        reasons.append("demo_slug")

    has_chatish = any(t in CHAT_TRIGGERS or "manual" in t or t == "input" for t in types)
    only_webhookish = types and any(t in WEBHOOK_TRIGGERS for t in types) and not has_chatish
    if only_webhookish:
        reasons.append("webhook_only_not_chat")
    if "blank" in slug or "空白" in name:
        reasons.append("blank_skeleton")
    if any(x in slug for x in ("video-promo", "image-text", "ppt-generation", "rag-ingest")):
        reasons.append("half_wired_generation")

    return reasons


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument(
        "--env",
        default="/home/ubuntu/schema-platform/.env",
    )
    args = ap.parse_args()

    uri = load_uri(Path(args.env))
    client = MongoClient(uri, serverSelectionTimeoutMS=10000)
    db = client.get_default_database()
    # mongoose 默认复数小写
    col = None
    for name in ("agentworkflows", "agent_workflows", "agentWorkflows"):
        if name in db.list_collection_names():
            col = db[name]
            break
    if col is None:
        # 模糊匹配
        for name in db.list_collection_names():
            if "agentworkflow" in name.lower().replace("_", ""):
                col = db[name]
                break
    if col is None:
        raise SystemExit(f"agent workflow collection not found in {db.name}: {db.list_collection_names()}")

    print(f"db={db.name} collection={col.name}")
    published = list(col.find({"status": "published"}))
    print(f"published_count={len(published)}")

    keep, drop = [], []
    missing_keep = sorted(KEEP_SLUGS - {(d.get("slug") or "").strip() for d in published})
    for doc in published:
        slug = (doc.get("slug") or "").strip()
        rs = reasons_to_drop(doc)
        types = node_types(doc.get("publishedGraph") or {})
        row = {
            "id": str(doc["_id"]),
            "slug": slug,
            "name": doc.get("name"),
            "nodes": len(types),
            "types": types[:12],
            "reasons": rs,
        }
        if not rs:
            keep.append(row)
        else:
            drop.append(row)
    if missing_keep:
        print(f"WARN whitelist_missing_published={missing_keep}")

    print("\n=== KEEP ===")
    for r in sorted(keep, key=lambda x: x["slug"] or ""):
        print(f"+ {r['slug'] or '-'}\t{r['name']}\tnodes={r['nodes']}\t{r['reasons']}")

    print("\n=== DROP (archive) ===")
    for r in sorted(drop, key=lambda x: x["slug"] or ""):
        print(f"- {r['slug'] or '-'}\t{r['name']}\tnodes={r['nodes']}\treasons={r['reasons']}")

    print(f"\nsummary keep={len(keep)} drop={len(drop)} apply={args.apply}")

    if not args.apply:
        print("dry-run only; pass --apply to archive")
        return 0

    ids = [ObjectId(r["id"]) for r in drop]
    if not ids:
        print("nothing to archive")
        return 0
    res = col.update_many(
        {"_id": {"$in": ids}, "status": "published"},
        {"$set": {"status": "archived"}},
    )
    print(f"archived matched={res.matched_count} modified={res.modified_count}")
    still = col.count_documents({"status": "published"})
    print(f"published_remaining={still}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
