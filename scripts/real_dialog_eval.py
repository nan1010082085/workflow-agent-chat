#!/usr/bin/env python3
"""
澄语真实对话评测：用业务多轮对话打磨助手，不做 TAG 探针。

输出：每轮 user/assistant 全文 + 简易质量标注（人工标准的自动化弱检查）。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Callable

BASE = os.environ.get("WAC_BASE", "https://pyflow.icu/workflow-agent-chat/api/chat")
USER = os.environ.get("WAC_USER", "admin")
PASSWORD = os.environ.get("WAC_PASSWORD", "admin123456")

# 空总结/模板废话 —— 真实对话里出现即判劣
BAD_PATTERNS = [
    r"没有可总结的执行结果",
    r"目前没有收到您需要执行的具体任务",
    r"尚未产生可总结",
    r"请提供您想要完成的具体事项",
    r"当前对话中还没有具体的任务",
]


class Client:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.token: str | None = None

    def req(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        timeout: float = 180,
    ) -> tuple[int, Any]:
        data = None if body is None else json.dumps(body).encode()
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        r = urllib.request.Request(
            self.base + path, data=data, headers=headers, method=method
        )
        try:
            with urllib.request.urlopen(r, timeout=timeout) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                payload = json.loads(raw) if raw else {"raw": raw}
            except Exception:
                payload = {"raw": raw}
            return e.code, payload

    def login(self) -> None:
        code, body = self.req(
            "POST", "/auth/login", {"username": USER, "password": PASSWORD}
        )
        if code != 200 or not body or not body.get("accessToken"):
            raise RuntimeError(f"login failed: {code} {body}")
        self.token = body["accessToken"]


def poll_run(client: Client, run_id: str, rounds: int = 90) -> dict:
    last: dict = {}
    waiting_streak = 0
    for _ in range(rounds):
        code, run = client.req("GET", f"/runs/{run_id}")
        if code != 200 or not isinstance(run, dict):
            raise RuntimeError(f"getRun {code} {run}")
        last = run
        st = run.get("status")
        if st in ("COMPLETED", "FAILED", "CANCELLED"):
            return run
        if st == "WAITING_INPUT":
            waiting_streak += 1
            if waiting_streak >= 1:
                return run
        else:
            waiting_streak = 0
        time.sleep(2)
    return last


def resume_until_done(client: Client, run_id: str, max_hitl: int = 3) -> dict:
    run = poll_run(client, run_id)
    for _ in range(max_hitl):
        if run.get("status") != "WAITING_INPUT":
            return run
        code, _ = client.req(
            "POST",
            f"/runs/{run_id}/resume",
            {
                "action": "approve",
                "payload": json.dumps(
                    {"note": "评测自动确认：需求可按当前理解继续"},
                    ensure_ascii=False,
                ),
            },
        )
        if code not in (200, 404, 503):
            raise RuntimeError(f"resume {code}")
        # 离开 waiting 后再看是否新一轮 HITL
        time.sleep(2)
        for __ in range(60):
            code, run = client.req("GET", f"/runs/{run_id}")
            if code != 200:
                raise RuntimeError(f"getRun {code}")
            st = run.get("status")
            if st in ("COMPLETED", "FAILED", "CANCELLED"):
                return run
            if st == "WAITING_INPUT":
                break
            time.sleep(2)
        else:
            return run
    return run


def last_assistant(client: Client, session_id: str) -> dict | None:
    code, msgs = client.req("GET", f"/sessions/{session_id}/messages")
    if code != 200 or not isinstance(msgs, list):
        return None
    for m in reversed(msgs):
        if m.get("role") == "assistant":
            return m
    return None


@dataclass
class TurnJudgement:
    round: int
    user: str
    assistant: str
    status: str
    exec_id: str | None
    message: dict = field(default_factory=dict)
    notes: list[str] = field(default_factory=list)
    ok: bool = True


def judge_text(text: str, checks: list[Callable[[str], str | None]]) -> list[str]:
    notes: list[str] = []
    for pat in BAD_PATTERNS:
        if re.search(pat, text or ""):
            notes.append(f"BAD_TEMPLATE:{pat}")
    for fn in checks:
        n = fn(text or "")
        if n:
            notes.append(n)
    if not (text or "").strip():
        notes.append("EMPTY_REPLY")
    if (text or "").strip() in ('{"text":""}', "{'text':''}"):
        notes.append("EMPTY_JSON_SHELL")
    return notes


def run_agent_dialog(
    client: Client,
    agent: dict,
    title: str,
    turns: list[tuple[str, list[Callable[[str], str | None]]]],
) -> dict:
    code, session = client.req(
        "POST",
        "/sessions",
        {
            "title": title,
            "agentId": agent["id"],
            "agentName": agent.get("name") or agent.get("slug"),
        },
    )
    if code != 200:
        raise RuntimeError(f"create session {code} {session}")
    sid = session["id"]
    results: list[TurnJudgement] = []

    for i, (user_text, checks) in enumerate(turns, start=1):
        print(f"\n--- [{agent.get('slug')}] R{i} ---")
        print(f"USER: {user_text}")
        code, sent = client.req(
            "POST",
            f"/sessions/{sid}/messages",
            {"agentId": agent["id"], "content": user_text},
            timeout=90,
        )
        if code != 200:
            raise RuntimeError(f"send {code} {sent}")
        run = resume_until_done(client, sent["runId"])
        asst = last_assistant(client, sid) or {}
        content = asst.get("content") or ""
        print(f"STATUS: {run.get('status')} exec={sent.get('runtimeExecutionId')}")
        print(f"ASST ({len(content)} chars): {content[:800]}{'…' if len(content)>800 else ''}")
        notes = judge_text(content, checks)
        if run.get("status") != "COMPLETED":
            notes.append(f"BAD_STATUS:{run.get('status')}")
        # message 结构观察
        for key in ("thinking", "tip", "toolCalls", "attachments", "documentSummaries"):
            if asst.get(key):
                notes.append(f"HAS_FIELD:{key}")
        j = TurnJudgement(
            round=i,
            user=user_text,
            assistant=content,
            status=run.get("status") or "?",
            exec_id=sent.get("runtimeExecutionId"),
            message={
                k: asst.get(k)
                for k in (
                    "id",
                    "status",
                    "thinking",
                    "tip",
                    "toolCalls",
                    "attachments",
                    "documentSummaries",
                )
                if asst.get(k) is not None
            },
            notes=notes,
            ok=not any(
                n.startswith("BAD_")
                or n.startswith("MISSING")
                or n.startswith("TOO_")
                or n.startswith("NOT_")
                or n.startswith("EMPTY_")
                or n == "EMPTY_REPLY"
                for n in notes
            ),
        )
        results.append(j)
        print("JUDGE:", "PASS" if j.ok else "FAIL", notes)

    return {
        "slug": agent.get("slug"),
        "sessionId": sid,
        "turns": [j.__dict__ for j in results],
        "passed": sum(1 for j in results if j.ok),
        "failed": sum(1 for j in results if not j.ok),
    }


def run_model_dialog(client: Client, model_id: str, title: str, turns: list[str]) -> dict:
    code, session = client.req("POST", "/sessions", {"title": title})
    if code != 200:
        raise RuntimeError(f"create {code}")
    sid = session["id"]
    out = []
    for i, user_text in enumerate(turns, start=1):
        print(f"\n--- [model] R{i} ---")
        print(f"USER: {user_text}")
        code, r = client.req(
            "POST",
            f"/sessions/{sid}/completions",
            {"modelId": model_id, "content": user_text},
            timeout=180,
        )
        content = (r.get("content") if isinstance(r, dict) else "") or ""
        print(f"ASST: {content[:800]}{'…' if len(content)>800 else ''}")
        notes = judge_text(content, [])
        if code != 200:
            notes.append(f"HTTP:{code}")
        ok = code == 200 and not any(n.startswith("BAD_") or n == "EMPTY_REPLY" for n in notes)
        out.append(
            {
                "round": i,
                "user": user_text,
                "assistant": content,
                "thinking": (r.get("thinking") if isinstance(r, dict) else None),
                "notes": notes,
                "ok": ok,
            }
        )
        print("JUDGE:", "PASS" if ok else "FAIL", notes)
    return {
        "slug": "model",
        "sessionId": sid,
        "turns": out,
        "passed": sum(1 for t in out if t["ok"]),
        "failed": sum(1 for t in out if not t["ok"]),
    }


def must_contain(*needles: str) -> Callable[[str], str | None]:
    def _fn(text: str) -> str | None:
        missing = [n for n in needles if n.lower() not in text.lower()]
        return f"MISSING:{','.join(missing)}" if missing else None

    return _fn


def must_look_like_email() -> Callable[[str], str | None]:
    def _fn(text: str) -> str | None:
        if len(text) < 80:
            return "TOO_SHORT_FOR_EMAIL"
        # 至少像一封信：称呼或落款或催款相关
        if not re.search(r"尊敬|您好|催|款|此致|谢谢|顺祝", text):
            return "NOT_EMAIL_LIKE"
        return None

    return _fn


def must_have_structure(*headers: str) -> Callable[[str], str | None]:
    def _fn(text: str) -> str | None:
        miss = [h for h in headers if h not in text]
        return f"MISSING_SECTION:{','.join(miss)}" if miss else None

    return _fn


SCENARIOS: dict[str, Callable[[Client, dict, str], dict]] = {}


def scenario_intelligent(client: Client, agents_by_slug: dict, model_id: str) -> dict:
    agent = agents_by_slug["gui-intelligent-assistant"]
    return run_agent_dialog(
        client,
        agent,
        "[真实评测] 智能助手-表单流程问答",
        [
            (
                "我们团队刚开始用 Schema 平台。请用通俗中文解释：表单（Schema）和流程（Flow）分别解决什么问题？"
                "各举一个审批场景的例子，控制在 200 字以内。",
                [must_contain("表单", "流程")],
            ),
            (
                "基于你上一轮的解释：如果我要做「请假申请」，应该先建表单还是先建流程？为什么？给出三步落地建议。",
                [must_contain("表单", "流程")],
            ),
            (
                "把刚才的建议改成给新人的检查清单，用 markdown 有序列表，不要开场寒暄。",
                [must_contain("1.", "2.")],
            ),
        ],
    )


def scenario_parity(client: Client, agents_by_slug: dict, model_id: str) -> dict:
    agent = agents_by_slug["gui-chat-parity-assistant"]
    return run_agent_dialog(
        client,
        agent,
        "[真实评测] chat-parity-催款邮件",
        [
            (
                "请写一封中文催款邮件：收件人是「华腾贸易 财务部」，我方是「澄语科技」，"
                "未付发票 INV-2026-0812，金额 ¥28,600，原定付款日 2026-08-01，请在 5 个工作日内付款。"
                "语气正式但不生硬，直接给可复制正文。",
                [must_look_like_email(), must_contain("INV-2026-0812", "28,600")],
            ),
            (
                "把上一封改得更简短，保留发票号和金额，去掉客套，并加一句：逾期将暂停后续发货。",
                [must_contain("INV-2026-0812", "暂停")],
            ),
            (
                "再给一个更强硬但仍合规的版本，只要正文，不要解释你的修改思路。",
                [must_contain("INV-2026-0812")],
            ),
        ],
    )


def scenario_dashboard(client: Client, agents_by_slug: dict, model_id: str) -> dict:
    agent = agents_by_slug["gui-dashboard-assist"]
    return run_agent_dialog(
        client,
        agent,
        "[真实评测] 仪表盘-电商周报",
        [
            (
                "我是电商运营负责人，要做「周销售复盘看板」给管理层。"
                "请推荐 6～8 个核心指标，并说明每个指标适合的图表类型（折线/柱状/漏斗等）和为什么。",
                [must_contain("指标", "图")],
            ),
            (
                "在上一轮基础上，按「概览 / 转化 / 商品 / 异常」四块给出看板布局建议，用 markdown 二级标题。",
                [must_have_structure("概览", "转化")],
            ),
            (
                "针对「异常」那一块，列出 3 条可执行的数据告警规则（条件 + 通知对象），不要重复介绍看板。",
                [must_contain("告警")],
            ),
        ],
    )


def scenario_model(client: Client, agents_by_slug: dict, model_id: str) -> dict:
    return run_model_dialog(
        client,
        model_id,
        "[真实评测] 模型-产品说明改写",
        [
            "把下面这段改成面向业务用户的产品介绍（120 字内）："
            "Workflow Agent Chat 通过 BFF 调用已发布 workflow，并在会话中展示处理状态与确认。",
            "再改成三条卖点 bullet，每条不超过 20 字。",
            "用表格对比「通用模型对话」和「任务助手」的适用场景，两列三行即可。",
        ],
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--only",
        default="intelligent,parity,dashboard,model",
        help="comma: intelligent,parity,dashboard,model",
    )
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()

    marker = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    print(f"=== real dialog eval marker={marker} ===")
    client = Client(BASE)
    client.login()

    code, models = client.req("GET", "/models")
    model_id = ""
    if code == 200 and isinstance(models, dict):
        model_id = models.get("defaultModelId") or (models.get("items") or [{}])[0].get(
            "id", ""
        )

    code, agents = client.req("GET", "/agents")
    if code != 200 or not isinstance(agents, list):
        raise RuntimeError(f"agents {code}")
    by_slug = {a.get("slug"): a for a in agents}

    wanted = {x.strip() for x in args.only.split(",") if x.strip()}
    runners = {
        "intelligent": scenario_intelligent,
        "parity": scenario_parity,
        "dashboard": scenario_dashboard,
        "model": scenario_model,
    }

    reports = []
    for key, fn in runners.items():
        if key not in wanted:
            continue
        if key != "model" and (
            (key == "intelligent" and "gui-intelligent-assistant" not in by_slug)
            or (key == "parity" and "gui-chat-parity-assistant" not in by_slug)
            or (key == "dashboard" and "gui-dashboard-assist" not in by_slug)
        ):
            print(f"SKIP {key}: agent missing")
            continue
        print(f"\n========== SCENARIO {key} ==========")
        reports.append(fn(client, by_slug, model_id))

    total_p = sum(r["passed"] for r in reports)
    total_f = sum(r["failed"] for r in reports)
    out = {
        "marker": marker,
        "base": BASE,
        "passed": total_p,
        "failed": total_f,
        "scenarios": reports,
    }
    path = args.json_out or f"/tmp/wac_real_dialog_{marker}.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)

    print("\n=== REAL DIALOG SUMMARY ===")
    for r in reports:
        print(f"[{r['slug']}] passed={r['passed']} failed={r['failed']} session={r['sessionId']}")
        for t in r["turns"]:
            flag = "PASS" if t["ok"] else "FAIL"
            notes = t.get("notes") or []
            print(f"  R{t['round']} {flag} notes={notes}")
    print(f"wrote {path} turns_passed={total_p} turns_failed={total_f}")
    return 1 if total_f else 0


if __name__ == "__main__":
    sys.exit(main())
