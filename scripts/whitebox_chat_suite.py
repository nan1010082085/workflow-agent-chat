#!/usr/bin/env python3
"""
澄语白盒持续测试：Chat → 模型 / Chat → 智能体(workflow)。
用法：
  python3 scripts/whitebox_chat_suite.py
  python3 scripts/whitebox_chat_suite.py --rounds 5 --agents 4
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

BASE = os.environ.get(
    "WAC_BASE",
    "https://pyflow.icu/workflow-agent-chat/api/chat",
)
USER = os.environ.get("WAC_USER", "admin")
PASSWORD = os.environ.get("WAC_PASSWORD", "admin123456")


@dataclass
class CaseResult:
    name: str
    ok: bool
    detail: str = ""
    meta: dict[str, Any] = field(default_factory=dict)


class Client:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.token: str | None = None

    def req(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        timeout: float = 120,
        auth: bool = True,
    ) -> tuple[int, Any]:
        data = None if body is None else json.dumps(body).encode()
        headers = {"Content-Type": "application/json"}
        if auth and self.token:
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

    def login(self, username: str, password: str) -> None:
        code, body = self.req(
            "POST",
            "/auth/login",
            {"username": username, "password": password},
            auth=False,
        )
        if code != 200 or not body or not body.get("accessToken"):
            raise RuntimeError(f"login failed: {code} {body}")
        self.token = body["accessToken"]


def poll_run_until(
    client: Client,
    run_id: str,
    *,
    rounds: int = 90,
    interval: float = 2.0,
    stop_waiting_after: int | None = None,
) -> dict:
    """轮询 run；stop_waiting_after>0 时，连续若干次仍 WAITING 才视为新一轮 HITL。"""
    last: dict = {}
    waiting_streak = 0
    for _ in range(rounds):
        code, run = client.req("GET", f"/runs/{run_id}")
        if code != 200 or not isinstance(run, dict):
            raise RuntimeError(f"getRun failed: {code} {run}")
        last = run
        st = run.get("status")
        if st in ("COMPLETED", "FAILED", "CANCELLED"):
            return run
        if st == "WAITING_INPUT":
            waiting_streak += 1
            if stop_waiting_after is None or waiting_streak >= stop_waiting_after:
                return run
        else:
            waiting_streak = 0
        time.sleep(interval)
    return last


def poll_run(client: Client, run_id: str, rounds: int = 90, interval: float = 2.0) -> dict:
    return poll_run_until(client, run_id, rounds=rounds, interval=interval, stop_waiting_after=1)


def resume_if_waiting(client: Client, run_id: str, result: dict) -> dict:
    """HITL：可多轮 waiting；每次 approve 后先等离开 WAITING，避免重复 resume 触发 404。"""
    run = poll_run(client, run_id)
    for _ in range(4):
        if run.get("status") != "WAITING_INPUT":
            return run
        last_err: Any = None
        advanced = False
        for attempt in range(5):
            code, resumed = client.req(
                "POST",
                f"/runs/{run_id}/resume",
                {"action": "approve", "payload": "白盒自动确认"},
            )
            if code == 200:
                last_err = None
                break
            last_err = (code, resumed)
            if code in (502, 503, 504, 404):
                time.sleep(2 * (attempt + 1))
                peeked = poll_run_until(
                    client, run_id, rounds=5, interval=1.5, stop_waiting_after=1
                )
                if peeked.get("status") != "WAITING_INPUT":
                    # 已离开 waiting（重复 resume / 平台已推进）
                    last_err = None
                    run = peeked
                    advanced = True
                    break
                continue
            raise RuntimeError(f"resume failed {code} {resumed}")
        if advanced:
            continue
        if last_err is not None:
            raise RuntimeError(f"resume failed {last_err[0]} {last_err[1]}")
        # resume 后至少跑几轮，连续 WAITING 才当作新一轮 HITL
        run = poll_run_until(client, run_id, stop_waiting_after=3)
    return run


def last_assistant(client: Client, session_id: str) -> dict | None:
    code, msgs = client.req("GET", f"/sessions/{session_id}/messages")
    if code != 200 or not isinstance(msgs, list):
        return None
    assistants = [m for m in msgs if m.get("role") == "assistant"]
    return assistants[-1] if assistants else None


def pick_agents(agents: list[dict], n: int) -> list[dict]:
    """优先选对话型助手；排除明显依赖 schema/表单 ID 的演示流。"""
    prefer = [
        "gui-intelligent-assistant",
        "gui-chat-parity-assistant",
        "gui-dashboard-assist",
        "gui-kb-faq",
        "gui-blank",
        "gui-smart-form-search",
    ]
    skip = {
        "gui-form-query-demo",  # 需 schemaId
        "gui-http-notify",
        "gui-scheduled-report",
        "gui-code-execute-demo",
        "gui-multimodal-llm-analyze",
        "gui-smart-suggestions",  # 结构化 JSON 输出，非对话复述
        "gui-kb-faq",  # 依赖知识库/FAQ 配置
    }
    by_slug = {
        a.get("slug"): a
        for a in agents
        if a.get("published", True) and a.get("slug") not in skip
    }
    picked: list[dict] = []
    for slug in prefer:
        if slug in by_slug and by_slug[slug] not in picked:
            picked.append(by_slug[slug])
        if len(picked) >= n:
            return picked
    for a in agents:
        if a.get("slug") in skip:
            continue
        if a not in picked:
            picked.append(a)
        if len(picked) >= n:
            break
    return picked


def test_auth(client: Client) -> CaseResult:
    code, me = client.req("GET", "/auth/me")
    ok = code == 200 and isinstance(me, dict) and bool(me.get("id") or me.get("username"))
    return CaseResult("auth.me", ok, f"http={code}", {"me": me})


def test_model_multiturn(client: Client, model_id: str, rounds: int, marker: str) -> CaseResult:
    code, session = client.req(
        "POST",
        "/sessions",
        {"title": f"[白盒-模型] {marker}"},
    )
    if code != 200:
        return CaseResult("model.multiturn", False, f"create session {code} {session}")
    sid = session["id"]
    secret = f"TAG-{marker}"
    # R1 remember
    code, r1 = client.req(
        "POST",
        f"/sessions/{sid}/completions",
        {
            "modelId": model_id,
            "content": f"[{marker}][R1] 请记住会话标记词：{secret}。只回复「已记住」。",
        },
        timeout=180,
    )
    if code != 200:
        return CaseResult("model.multiturn", False, f"R1 {code} {r1}", {"sessionId": sid})
    # R2 recall via sync completions (backend injects history)
    code, r2 = client.req(
        "POST",
        f"/sessions/{sid}/completions",
        {
            "modelId": model_id,
            "content": f"[{marker}][R2] 请原样复述你记住的会话标记词（完整字符串）。看不到就说看不到。",
        },
        timeout=180,
    )
    if code != 200:
        return CaseResult("model.multiturn", False, f"R2 {code} {r2}", {"sessionId": sid})
    content = (r2.get("content") or "") if isinstance(r2, dict) else ""
    # Extra rounds smoke
    extras_ok = True
    for i in range(3, rounds + 1):
        code, rx = client.req(
            "POST",
            f"/sessions/{sid}/completions",
            {
                "modelId": model_id,
                "content": f"[{marker}][R{i}] 用一句话确认你仍记得会话标记词前缀 TAG-。",
            },
            timeout=180,
        )
        if code != 200 or not (isinstance(rx, dict) and (rx.get("content") or "").strip()):
            extras_ok = False
            break
    recalled = secret in content
    ok = recalled and extras_ok and r1.get("status") == "COMPLETED"
    return CaseResult(
        "model.multiturn",
        ok,
        f"recalled={recalled} extras={extras_ok} r2={content[:160]}",
        {"sessionId": sid, "r1": r1, "r2": r2},
    )


def test_agent_multiturn(
    client: Client, agent: dict, rounds: int, marker: str
) -> CaseResult:
    slug = agent.get("slug") or "?"
    name = f"agent.multiturn.{slug}"
    code, session = client.req(
        "POST",
        "/sessions",
        {
            "title": f"[白盒-助手] {slug} {marker}",
            "agentId": agent["id"],
            "agentName": agent.get("name") or slug,
        },
    )
    if code != 200:
        return CaseResult(name, False, f"create {code} {session}")
    sid = session["id"]
    secret = f"TAG-{slug}-{marker}"

    def send(round_no: int, text: str) -> tuple[dict, dict]:
        code, result = client.req(
            "POST",
            f"/sessions/{sid}/messages",
            {"agentId": agent["id"], "content": text},
            timeout=90,
        )
        if code != 200:
            raise RuntimeError(f"send R{round_no} {code} {result}")
        run = resume_if_waiting(client, result["runId"], result)
        asst = last_assistant(client, sid) or {}
        return result, {"run": run, "assistant": asst}

    try:
        msg1 = f"[{marker}][R1] 请记住会话标记词：{secret}。只用一句话确认已记住。"
        r1, d1 = send(1, msg1)
        if d1["run"].get("status") != "COMPLETED":
            return CaseResult(
                name,
                False,
                f"R1 status={d1['run'].get('status')}",
                {"sessionId": sid, "exec": r1.get("runtimeExecutionId")},
            )

        msg2 = (
            f"[{marker}][R2] 请原样复述上一轮会话标记词（完整 TAG- 字符串）。"
            "如果看不到历史就明确说看不到。"
        )
        r2, d2 = send(2, msg2)
        a2 = (d2["assistant"].get("content") or "")
        recalled = secret in a2
        if d2["run"].get("status") != "COMPLETED":
            return CaseResult(
                name,
                False,
                f"R2 status={d2['run'].get('status')}",
                {"sessionId": sid, "exec": r2.get("runtimeExecutionId")},
            )

        extras_ok = True
        last_exec = r2.get("runtimeExecutionId")
        for i in range(3, rounds + 1):
            msg = f"[{marker}][R{i}] 用一句话回答：你还记得会话标记词吗？记得就说「仍记得」。"
            ri, di = send(i, msg)
            last_exec = ri.get("runtimeExecutionId")
            if di["run"].get("status") not in ("COMPLETED", "WAITING_INPUT"):
                extras_ok = False
                break
            if di["run"].get("status") == "COMPLETED" and not (
                di["assistant"].get("content") or ""
            ).strip():
                extras_ok = False
                break

        # isolation: completions on agent session must 409
        code_neg, _ = client.req(
            "POST",
            f"/sessions/{sid}/completions",
            {"modelId": "x", "content": "should fail"},
        )
        isolation_ok = code_neg == 409

        ok = recalled and extras_ok and isolation_ok
        return CaseResult(
            name,
            ok,
            f"recalled={recalled} extras={extras_ok} isolation409={isolation_ok} a2={a2[:180]}",
            {
                "sessionId": sid,
                "slug": slug,
                "execR1": r1.get("runtimeExecutionId"),
                "execR2": r2.get("runtimeExecutionId"),
                "lastExec": last_exec,
            },
        )
    except Exception as e:
        return CaseResult(name, False, str(e), {"sessionId": sid, "slug": slug})


def test_session_switch(client: Client, model_id: str, marker: str) -> CaseResult:
    """两会话各自一条消息，交叉拉 messages 不应串台。"""
    code_a, sa = client.req("POST", "/sessions", {"title": f"[白盒-A] {marker}"})
    code_b, sb = client.req("POST", "/sessions", {"title": f"[白盒-B] {marker}"})
    if code_a != 200 or code_b != 200:
        return CaseResult("session.switch", False, f"create {code_a}/{code_b}")
    ta, tb = f"TOKEN-A-{marker}", f"TOKEN-B-{marker}"
    client.req(
        "POST",
        f"/sessions/{sa['id']}/completions",
        {"modelId": model_id, "content": f"请只回复：{ta}"},
        timeout=180,
    )
    client.req(
        "POST",
        f"/sessions/{sb['id']}/completions",
        {"modelId": model_id, "content": f"请只回复：{tb}"},
        timeout=180,
    )
    _, ma = client.req("GET", f"/sessions/{sa['id']}/messages")
    _, mb = client.req("GET", f"/sessions/{sb['id']}/messages")
    ca = " ".join(m.get("content") or "" for m in (ma or []))
    cb = " ".join(m.get("content") or "" for m in (mb or []))
    ok = ta in ca and tb in cb and tb not in ca and ta not in cb
    return CaseResult(
        "session.switch",
        ok,
        f"A_has_A={ta in ca} B_has_B={tb in cb} crossA={tb in ca} crossB={ta in cb}",
        {"sessionA": sa["id"], "sessionB": sb["id"]},
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rounds", type=int, default=4, help="每条链路最少轮次")
    parser.add_argument("--agents", type=int, default=3, help="测试助手数量")
    parser.add_argument("--json-out", default="")
    args = parser.parse_args()

    marker = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    print(f"=== WAC whitebox marker={marker} base={BASE} ===")
    client = Client(BASE)
    client.login(USER, PASSWORD)

    results: list[CaseResult] = []
    results.append(test_auth(client))

    code, models = client.req("GET", "/models")
    if code != 200:
        results.append(CaseResult("models.list", False, f"{code} {models}"))
        model_id = ""
    else:
        model_id = models.get("defaultModelId") or (models.get("items") or [{}])[0].get("id")
        results.append(CaseResult("models.list", bool(model_id), f"default={model_id}"))

    code, agents = client.req("GET", "/agents")
    if code != 200 or not isinstance(agents, list) or not agents:
        results.append(CaseResult("agents.list", False, f"{code}"))
        agents = []
    else:
        results.append(CaseResult("agents.list", True, f"count={len(agents)}"))

    if model_id:
        results.append(test_model_multiturn(client, model_id, max(args.rounds, 3), marker))
        results.append(test_session_switch(client, model_id, marker))

    for agent in pick_agents(agents, args.agents):
        print(f"-- agent {agent.get('slug')} --")
        results.append(test_agent_multiturn(client, agent, max(args.rounds, 3), marker))

    failed = [r for r in results if not r.ok]
    print("\n=== SUMMARY ===")
    for r in results:
        flag = "PASS" if r.ok else "FAIL"
        print(f"[{flag}] {r.name}: {r.detail}")
        if r.meta.get("sessionId") or r.meta.get("execR2"):
            print(f"       meta={ {k: r.meta.get(k) for k in ('sessionId','slug','execR1','execR2','lastExec','sessionA','sessionB') if k in r.meta} }")

    out = {
        "marker": marker,
        "base": BASE,
        "passed": len(results) - len(failed),
        "failed": len(failed),
        "cases": [
            {"name": r.name, "ok": r.ok, "detail": r.detail, "meta": r.meta} for r in results
        ],
    }
    path = args.json_out or f"/tmp/wac_whitebox_{marker}.json"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"\nwrote {path} passed={out['passed']} failed={out['failed']}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
