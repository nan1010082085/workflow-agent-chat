#!/usr/bin/env python3
"""
文档 / 图片 / PDF / 合同 / 简历 / 报销 助手真实评测。

覆盖此前被忽略的文档类助手；同时探测：
1) 纯文本粘贴正文
2) Chat 附件上传（txt/md 有摘录；pdf/png 看平台是否吃得下）
3) 入口是否为 webhook-only（澄语不可用则记 STRUCTURAL_FAIL）
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
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

BASE = os.environ.get("WAC_BASE", "https://pyflow.icu/workflow-agent-chat/api/chat")
USER = os.environ.get("WAC_USER", "admin")
PASSWORD = os.environ.get("WAC_PASSWORD", "admin123456")

BAD = [
    r"没有可总结的执行结果",
    r"目前没有收到您需要执行的具体任务",
    r"尚未产生可总结",
    r"Workflow not found",
    r"document-parse",
    r"undefined",
    r"\{\s*\"text\"\s*:\s*\"\"\s*\}",
]

CONTRACT_TXT = """服务合同

甲方：澄语科技有限公司
乙方：华腾贸易有限公司
签订日期：2026-08-01
合同编号：HT-2026-0813

一、服务内容
乙方为甲方提供企业软件实施服务，周期 90 天。

二、合同金额
总金额人民币壹拾贰万元整（¥120,000），分两期支付：
1. 签约后 5 个工作日内支付 40%
2. 验收通过后 10 个工作日内支付 60%

三、违约责任
任何一方逾期付款或交付超过 15 日，守约方有权解除合同并要求违约金为合同总额的 20%。

四、保密
双方对商业秘密保密，期限为合同终止后 3 年。

五、争议解决
提交甲方所在地人民法院诉讼解决。
"""

RESUME_TXT = """张三
电话：13800001111 | 邮箱：zhangsan@example.com
求职意向：Java 后端工程师

教育背景
2018-2022 某某大学 计算机科学 本科

工作经历
2022-至今 某互联网公司 后端开发
- 负责订单服务重构，QPS 提升 40%
- 熟悉 Spring Boot / MySQL / Redis / Kafka

技能
Java, Spring Cloud, MySQL, Redis, Docker
"""

EXPENSE_TXT = """报销单
申请人：李四
部门：市场部
日期：2026-08-10
项目：客户拜访交通费
明细：
- 高铁票 北京-上海 ¥553
- 出租车 ¥86
合计：¥639
附件：发票号码 044001234567
备注：无招待餐费
"""

MEETING_TXT = """周会纪要（2026-08-12）
出席：产品、研发、设计
议题：
1. 澄语 HITL 拒绝后无后续 —— 已排期修复
2. 文档助手评测覆盖不足 —— 本周补齐
决议：
- 本周五前上线拒绝后续引导
- 文档类助手改为对话可用入口
待办：
- 张三：改 webhook 入口
- 李四：补 PDF 解析联调
"""


class Client:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.token: str | None = None

    def req(self, method: str, path: str, body: dict | None = None, timeout: float = 180):
        data = None if body is None else json.dumps(body).encode()
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        r = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(r, timeout=timeout) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw) if raw else {"raw": raw}
            except Exception:
                return e.code, {"raw": raw}

    def upload(self, filename: str, content: bytes, content_type: str, session_id: str | None = None):
        boundary = "----wacboundary7"
        fields: list[bytes] = []
        if session_id:
            fields.append(
                (
                    f"--{boundary}\r\n"
                    f'Content-Disposition: form-data; name="sessionId"\r\n\r\n'
                    f"{session_id}\r\n"
                ).encode()
            )
        fields.append(
            (
                f"--{boundary}\r\n"
                f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                f"Content-Type: {content_type}\r\n\r\n"
            ).encode()
            + content
            + b"\r\n"
        )
        fields.append(f"--{boundary}--\r\n".encode())
        body = b"".join(fields)
        headers = {
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Authorization": f"Bearer {self.token}",
        }
        r = urllib.request.Request(self.base + "/uploads", data=body, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(r, timeout=60) as resp:
                return resp.status, json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, {"raw": raw}

    def login(self):
        code, body = self.req("POST", "/auth/login", {"username": USER, "password": PASSWORD})
        if code != 200:
            raise RuntimeError(f"login {code} {body}")
        self.token = body["accessToken"]


def poll(client: Client, run_id: str, rounds: int = 60) -> dict:
    last: dict = {}
    for _ in range(rounds):
        code, run = client.req("GET", f"/runs/{run_id}")
        if code == 200 and isinstance(run, dict):
            last = run
            st = run.get("status")
            if st in ("WAITING_INPUT", "COMPLETED", "FAILED", "CANCELLED"):
                return run
        time.sleep(2)
    return last


def last_assistant(client: Client, sid: str) -> dict:
    code, msgs = client.req("GET", f"/sessions/{sid}/messages")
    if code != 200 or not isinstance(msgs, list):
        return {}
    for m in reversed(msgs):
        if m.get("role") == "assistant":
            return m
    return {}


def judge(text: str, needles: list[str], min_len: int = 40) -> list[str]:
    notes: list[str] = []
    t = text or ""
    if not t.strip():
        notes.append("EMPTY_REPLY")
    if len(t.strip()) < min_len:
        notes.append(f"TOO_SHORT:{len(t.strip())}")
    for pat in BAD:
        if re.search(pat, t, re.I):
            notes.append(f"BAD:{pat}")
    # 英文 structured JSON 也算命中
    aliases = {
        "差异": ["差异", "difference", "differences", "conflict"],
        "违约": ["违约", "违约金", "penalty"],
        "风险": ["风险", "risk"],
        "合规": ["合规", "compliant", "不合规", "异常"],
        "分": ["分", "评分", "score"],
        "待办": ["待办", "todo", "行动"],
        "决议": ["决议", "决定", "decision"],
    }
    miss = []
    for n in needles:
        opts = aliases.get(n, [n])
        if not any(o.lower() in t.lower() for o in opts):
            miss.append(n)
    if miss:
        notes.append("MISSING:" + ",".join(miss))
    # 仅当回复像「缺文件」失败壳时标记（避免正文提到 webhook 误杀）
    if re.search(
        r"(未指定上传文件流|没有收到文档|缺少文档\s*[：:]|请上传文件|documentId\s*缺失|Workflow not found)",
        t,
        re.I,
    ):
        notes.append("NEEDS_DOC_OR_WEBHOOK")
    return notes


def run_case(
    client: Client,
    agent: dict,
    title: str,
    content: str,
    needles: list[str],
    attachment_ids: list[str] | None = None,
    approve_if_waiting: bool = True,
) -> dict:
    code, session = client.req(
        "POST",
        "/sessions",
        {"title": title, "agentId": agent["id"], "agentName": agent.get("name")},
    )
    if code != 200:
        return {"slug": agent.get("slug"), "ok": False, "notes": [f"SESSION:{code}"], "content": ""}
    sid = session["id"]
    body: dict[str, Any] = {"agentId": agent["id"], "content": content}
    if attachment_ids:
        body["attachmentIds"] = attachment_ids
    code, sent = client.req("POST", f"/sessions/{sid}/messages", body, timeout=120)
    if code != 200:
        return {
            "slug": agent.get("slug"),
            "ok": False,
            "notes": [f"SEND:{code}:{sent}"],
            "content": "",
            "sessionId": sid,
        }
    run = poll(client, sent["runId"])
    # HITL：先 approve 看最终结果（带空 comment）
    if approve_if_waiting and run.get("status") == "WAITING_INPUT":
        client.req("POST", f"/runs/{sent['runId']}/resume", {"action": "approve", "payload": "确认继续"})
        run = poll(client, sent["runId"])
    asst = last_assistant(client, sid)
    text = asst.get("content") or ""
    notes = judge(text, needles)
    st = run.get("status") or "?"
    if st not in ("COMPLETED", "WAITING_INPUT"):
        notes.append(f"BAD_STATUS:{st}")
    if st == "FAILED":
        notes.append(f"FAILED:{(run.get('errorMessage') or run.get('error') or '')[:120]}")
    ok = not any(
        n.startswith(("EMPTY", "TOO_SHORT", "BAD:", "MISSING", "BAD_STATUS", "FAILED", "SEND", "NEEDS_DOC"))
        for n in notes
    )
    print(f"\n--- {agent.get('slug')} ---")
    print(f"STATUS {st} notes={notes} ok={ok}")
    print((text[:500] + ("…" if len(text) > 500 else "")) or "(empty)")
    return {
        "slug": agent.get("slug"),
        "name": agent.get("name"),
        "sessionId": sid,
        "status": st,
        "ok": ok,
        "notes": notes,
        "content": text,
        "mode": "attachment" if attachment_ids else "paste",
    }


def minimal_pdf_bytes() -> bytes:
    # 极简 PDF，含可抽取文本「合同金额120000」
    return b"""%PDF-1.1
1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj
2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj
3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources<< /Font<< /F1 5 0 R >> >> >>endobj
4 0 obj<< /Length 68 >>stream
BT /F1 12 Tf 20 100 Td (Contract amount CNY 120000) Tj ET
endstream
endobj
5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000266 00000 n 
0000000384 00000 n 
trailer<< /Size 6 /Root 1 0 R >>
startxref
457
%%EOF
"""


def minimal_png_bytes() -> bytes:
    # 1x1 PNG
    import base64
    return base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--json-out", default="docs/eval/latest_doc_agents.json")
    args = ap.parse_args()

    client = Client(BASE)
    client.login()
    code, agents = client.req("GET", "/agents")
    by = {a.get("slug"): a for a in agents}
    marker = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    results: list[dict] = []

    cases = [
        # slug, content, needles, approve_if_waiting, attach_name, attach_bytes, attach_ctype
        (
            "gui-document-summary",
            "请总结以下会议纪要，给出议题/决议/待办三部分：\n\n" + MEETING_TXT,
            ["待办", "决议"],
            False,
            "会议纪要.txt",
            MEETING_TXT.encode(),
            "text/plain",
        ),
        (
            "gui-contract-extract",
            "请从下面合同提取甲乙方、金额、付款节点、违约金与争议解决：\n\n" + CONTRACT_TXT,
            ["120", "违约", "澄语"],
            False,
            "服务合同.txt",
            CONTRACT_TXT.encode(),
            "text/plain",
        ),
        (
            "gui-contract-risk-tag",
            "请标注下面合同的风险条款并给出风险等级：\n\n" + CONTRACT_TXT,
            ["风险", "违约"],
            True,
            None,
            None,
            None,
        ),
        (
            "gui-resume-screening",
            "岗位要求：3年 Java 后端，熟悉 Spring/MySQL/Redis。请评估下面简历并打分：\n\n" + RESUME_TXT,
            ["张三", "分"],
            False,
            "resume.txt",
            RESUME_TXT.encode(),
            "text/plain",
        ),
        (
            "gui-expense-audit",
            "请审核下面报销单是否合规，列出异常项：\n\n" + EXPENSE_TXT,
            ["639", "合规"],
            True,
            None,
            None,
            None,
        ),
        (
            "gui-multi-doc-compare",
            "文档A：\n"
            + CONTRACT_TXT
            + "\n\n文档B：同样合同但违约金改为 10%，金额改为 10 万元。\n请对比差异并给合并建议。",
            ["差异", "违约"],
            False,
            None,
            None,
            None,
        ),
        (
            "gui-doc-image-recognition",
            "下面是一张报销发票的 OCR 文本，请结构化提取金额、日期、销方：\n"
            "发票代码 044001234567\n开票日期 2026-08-10\n金额 553.00\n销方 中国铁路12306\n购方 澄语科技",
            ["553", "发票"],
            False,
            None,
            None,
            None,
        ),
    ]

    for slug, content, needles, hitl, fname, fbytes, ctype in cases:
        agent = by.get(slug)
        if not agent:
            print(f"SKIP missing {slug}")
            results.append({"slug": slug, "ok": False, "notes": ["AGENT_MISSING"]})
            continue
        # 1) paste text
        results.append(
            run_case(
                client,
                agent,
                f"[文档评测] {agent.get('name')}-粘贴",
                content,
                needles,
                approve_if_waiting=bool(hitl),
            )
        )
        # 2) attachment if provided
        if fname and fbytes and ctype:
            code, session = client.req(
                "POST",
                "/sessions",
                {
                    "title": f"[文档评测] {agent.get('name')}-附件",
                    "agentId": agent["id"],
                    "agentName": agent.get("name"),
                },
            )
            if code == 200:
                sid = session["id"]
                ucode, up = client.upload(fname, fbytes, ctype, sid)
                if ucode == 200 and isinstance(up, dict) and up.get("id"):
                    results.append(
                        run_case(
                            client,
                            agent,
                            f"[文档评测] {agent.get('name')}-附件",
                            f"请处理附件《{fname}》，完成该助手的核心任务。",
                            needles,
                            attachment_ids=[up["id"]],
                            approve_if_waiting=bool(hitl),
                        )
                    )
                else:
                    results.append(
                        {
                            "slug": slug,
                            "ok": False,
                            "notes": [f"UPLOAD_FAIL:{ucode}:{up}"],
                            "mode": "attachment",
                        }
                    )

    # PDF / PNG 专项（合同提取 + 图片识别）
    for slug, fname, blob, ctype, needles in [
        (
            "gui-contract-extract",
            "contract.pdf",
            minimal_pdf_bytes(),
            "application/pdf",
            ["120"],
        ),
        (
            "gui-doc-image-recognition",
            "invoice.png",
            minimal_png_bytes(),
            "image/png",
            [],
        ),
    ]:
        agent = by.get(slug)
        if not agent:
            continue
        code, session = client.req(
            "POST",
            "/sessions",
            {
                "title": f"[文档评测] {slug}-binary",
                "agentId": agent["id"],
                "agentName": agent.get("name"),
            },
        )
        if code != 200:
            continue
        sid = session["id"]
        ucode, up = client.upload(fname, blob, ctype, sid)
        if ucode != 200 or not isinstance(up, dict):
            results.append({"slug": slug, "ok": False, "notes": [f"UPLOAD_FAIL:{ucode}"], "mode": fname})
            continue
        results.append(
            run_case(
                client,
                agent,
                f"[文档评测] {slug}-{fname}",
                f"请处理附件 {fname}。",
                needles,
                attachment_ids=[up["id"]],
            )
        )

    passed = sum(1 for r in results if r.get("ok"))
    failed = len(results) - passed
    out = {
        "marker": marker,
        "passed": passed,
        "failed": failed,
        "results": results,
        "insight": [
            "catalog 里 supportedInputs 全是 text，但文档助手图多依赖 document-parse",
            "若干助手入口仍是 webhook-trigger，澄语 invoke 可能拿不到可解析文档",
            "Chat 附件对 pdf/png 无文本摘录，需平台 document-parse/vision 真正吃文件",
        ],
    }
    path = Path(args.json_out)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n=== DOC AGENT SUMMARY ===")
    for r in results:
        print(
            f"[{r.get('slug')}] mode={r.get('mode')} "
            f"{'PASS' if r.get('ok') else 'FAIL'} notes={r.get('notes')}"
        )
    print(f"wrote {path} passed={passed} failed={failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
