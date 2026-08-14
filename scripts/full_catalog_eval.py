#!/usr/bin/env python3
"""
全量智能体 + 普通 LLM 循环评测。

- 每个已发布助手至少 1 轮业务场景，再循环 --rounds 次（默认 5）
- 全部可用模型各跑 --rounds 次
- 附件矩阵：txt / pdf / png / docx / xlsx 上传与回显检查
- 产物：docs/eval/full_catalog_latest.json + ISSUES.md 片段
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import struct
import sys
import time
import urllib.error
import urllib.request
import zipfile
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
    r"\{\s*\"text\"\s*:\s*\"\"\s*\}",
    r"未指定上传文件流",
    r"未指定图片上传流",
    r"\$input\.file",
]

# slug → 评测提示（覆盖全部保留助手）
PROMPTS: dict[str, str] = {
    "gui-intelligent-assistant": "用不超过 120 字说明：表单和流程在 Schema 平台分别解决什么问题？",
    "gui-chat-parity-assistant": "请写一封简短催款邮件：发票 INV-2026-0814，金额 ¥8600，3 个工作日内付款。只要正文。",
    "gui-dashboard-assist": "为电商周报推荐 5 个核心指标，每个注明适合的图表类型，用 markdown 列表。",
    "gui-document-summary": "请总结以下会议纪要，给出议题/结论/待办：\n\n周会 2026-08-14\n议题：附件预览与全量评测\n决议：本周完成全助手循环评测\n待办：张三补 PDF 预览；李四补 xlsx 联调\n",
    "gui-doc-image-recognition": "下面是发票 OCR 文本，请结构化提取金额、日期、销方：\n发票金额：¥553.00\n开票日期：2026-08-10\n销方：中国铁路12306\n购方：澄语科技\n",
    "gui-contract-extract": "从下列合同提取甲乙方、金额、违约责任：\n甲方澄语科技，乙方华腾贸易，金额120000元，逾期超15日违约金20%。",
    "gui-contract-risk-tag": "请标注下列合同风险并给出等级：\n总金额120000，违约金20%，争议提交甲方所在地法院。",
    "gui-resume-screening": "筛选下列简历是否适合 Java 后端：\n张三，本科，2年经验，Java/Spring/MySQL/Redis，做过订单服务重构。",
    "gui-expense-audit": "审核报销单：申请人李四，差旅639元，餐补超标80元，请给出合规结论。",
    "gui-multi-doc-compare": "对比两版条款差异：\nA：金额120000，违约金20%\nB：金额100000，违约金10%\n列出冲突点。",
    "gui-feedback-analysis": "分析客户反馈情感与主题：\n1) 审批太慢\n2) 界面清爽好用\n3) 导出经常失败\n给出可执行建议。",
    "gui-cs-kb-reply": "客户问：如何重置表单发布权限？请生成一条专业简洁的客服回复草稿。",
    "gui-memory-assistant": "记住：我偏好中文、简洁列表输出。然后用该偏好回答：什么是 HITL？",
    "gui-smart-form-search": "帮我找适合「请假申请」的表单字段建议，列出 5 个核心字段。",
    "gui-requirement-gated-build": "我想做员工请假审批，请先澄清 2 个关键问题再给落地步骤。",
}

MODEL_PROMPT = "用三句话介绍你自己，并回答：1+1等于几？只输出中文。"


class Client:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.token: str | None = None

    def req(self, method: str, path: str, body: dict | None = None, timeout: float = 180, _retry: bool = True):
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
                payload = json.loads(raw) if raw else {"raw": raw}
            except Exception:
                payload = {"raw": raw}
            if e.code == 401 and _retry and path != "/auth/login":
                try:
                    self.login()
                    return self.req(method, path, body, timeout=timeout, _retry=False)
                except Exception:
                    return e.code, payload
            return e.code, payload

    def login(self) -> None:
        code, body = self.req("POST", "/auth/login", {"username": USER, "password": PASSWORD}, _retry=False)
        if code != 200 or not body or not body.get("accessToken"):
            raise RuntimeError(f"login failed: {code} {body}")
        self.token = body["accessToken"]

    def upload(self, filename: str, content: bytes, content_type: str, session_id: str | None = None):
        def _do():
            boundary = f"----wac{int(time.time()*1000)}"
            parts: list[bytes] = []
            if session_id:
                parts.append(
                    f"--{boundary}\r\nContent-Disposition: form-data; name=\"sessionId\"\r\n\r\n{session_id}\r\n".encode()
                )
            parts.append(
                (
                    f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
                    f"Content-Type: {content_type}\r\n\r\n"
                ).encode()
                + content
                + b"\r\n"
            )
            parts.append(f"--{boundary}--\r\n".encode())
            body = b"".join(parts)
            headers = {
                "Content-Type": f"multipart/form-data; boundary={boundary}",
                "Authorization": f"Bearer {self.token}",
            }
            r = urllib.request.Request(self.base + "/uploads", data=body, headers=headers, method="POST")
            try:
                with urllib.request.urlopen(r, timeout=60) as resp:
                    return resp.status, json.loads(resp.read().decode())
            except urllib.error.HTTPError as e:
                return e.code, e.read().decode(errors="replace")

        code, payload = _do()
        if code == 401:
            self.login()
            code, payload = _do()
        return code, payload


def poll(client: Client, run_id: str, rounds: int = 120) -> dict:
    last: dict = {}
    for _ in range(rounds):
        code, run = client.req("GET", f"/runs/{run_id}")
        if code != 200 or not isinstance(run, dict):
            return {"status": "ERROR", "error": run}
        last = run
        st = run.get("status")
        if st in ("COMPLETED", "FAILED", "CANCELLED", "WAITING_INPUT"):
            return run
        time.sleep(2)
    return last


def last_assistant(client: Client, sid: str) -> dict:
    code, msgs = client.req("GET", f"/sessions/{sid}/messages")
    if code != 200:
        return {}
    items = msgs if isinstance(msgs, list) else (msgs or {}).get("items") or []
    for m in reversed(items):
        if m.get("role") == "assistant":
            return m
    return {}


def judge(text: str, min_len: int = 20) -> list[str]:
    notes: list[str] = []
    t = text or ""
    if not t.strip():
        notes.append("EMPTY_REPLY")
    elif len(t.strip()) < min_len:
        notes.append(f"TOO_SHORT:{len(t.strip())}")
    for pat in BAD:
        if re.search(pat, t, re.I):
            notes.append(f"BAD:{pat}")
    if re.search(r'"approved"\s*:\s*true', t) and len(t) < 280:
        notes.append("HITL_APPROVE_ECHO")
    return notes


def minimal_pdf() -> bytes:
    return b"""%PDF-1.1
1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj
2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj
3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources<< /Font<< /F1 5 0 R >> >> >>endobj
4 0 obj<< /Length 55 >>stream
BT /F1 12 Tf 20 100 Td (Amount CNY 120000) Tj ET
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


def minimal_png() -> bytes:
    import zlib

    w, h = 32, 32
    raw = b"".join(b"\x00" + bytes([200, 80, 60]) * w for _ in range(h))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def minimal_docx() -> bytes:
    """最小 OOXML docx（可被上传白名单接受）。"""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        )
        z.writestr(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""",
        )
        z.writestr(
            "word/document.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body><w:p><w:r><w:t>合同金额人民币120000元，违约金20%。</w:t></w:r></w:p></w:body>
</w:document>""",
        )
    return buf.getvalue()


def minimal_xlsx() -> bytes:
    """最小 OOXML xlsx。"""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""",
        )
        z.writestr(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""",
        )
        z.writestr(
            "xl/workbook.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
</workbook>""",
        )
        z.writestr(
            "xl/_rels/workbook.xml.rels",
            """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""",
        )
        z.writestr(
            "xl/worksheets/sheet1.xml",
            """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1"><c r="A1" t="inlineStr"><is><t>amount</t></is></c><c r="B1"><v>120000</v></c></row>
  </sheetData>
</worksheet>""",
        )
    return buf.getvalue()


def run_agent_once(client: Client, agent: dict, prompt: str, round_i: int) -> dict:
    slug = agent.get("slug") or "?"
    code, session = client.req(
        "POST",
        "/sessions",
        {
            "title": f"[全量评测] {slug} R{round_i}",
            "agentId": agent["id"],
            "agentName": agent.get("name"),
        },
    )
    if code != 200:
        return {"slug": slug, "ok": False, "notes": [f"SESSION:{code}"], "round": round_i}
    sid = session["id"]
    code, sent = client.req(
        "POST",
        f"/sessions/{sid}/messages",
        {"agentId": agent["id"], "content": prompt},
        timeout=120,
    )
    if code != 200:
        return {
            "slug": slug,
            "ok": False,
            "notes": [f"SEND:{code}:{sent}"],
            "round": round_i,
            "sessionId": sid,
        }
    run = poll(client, sent["runId"])
    if run.get("status") == "WAITING_INPUT":
        client.req(
            "POST",
            f"/runs/{sent['runId']}/resume",
            {"action": "approve", "payload": "确认继续"},
        )
        run = poll(client, sent["runId"])
    asst = last_assistant(client, sid)
    text = asst.get("content") or ""
    notes = judge(text)
    st = run.get("status") or "?"
    if st not in ("COMPLETED", "WAITING_INPUT"):
        notes.append(f"BAD_STATUS:{st}")
        if st == "FAILED":
            notes.append(f"FAILED:{(run.get('errorMessage') or '')[:160]}")
    # 附件回显字段健全性（若有）
    atts = asst.get("attachments") or []
    if atts and not isinstance(atts, list):
        notes.append("BAD_ATTACHMENTS_SHAPE")
    ok = not any(
        n.startswith(("EMPTY", "TOO_SHORT", "BAD:", "BAD_STATUS", "FAILED", "SEND", "HITL", "SESSION"))
        for n in notes
    )
    print(f"[{slug}] R{round_i} {st} {'PASS' if ok else 'FAIL'} notes={notes}")
    return {
        "slug": slug,
        "name": agent.get("name"),
        "round": round_i,
        "sessionId": sid,
        "status": st,
        "ok": ok,
        "notes": notes,
        "contentPreview": (text[:400] + ("…" if len(text) > 400 else "")),
        "supportedInputs": agent.get("supportedInputs") or [],
    }


def run_model_once(client: Client, model: dict, round_i: int) -> dict:
    mid = model.get("id") or model.get("modelId")
    name = model.get("name") or model.get("model") or mid
    code, session = client.req(
        "POST",
        "/sessions",
        {"title": f"[模型评测] {name} R{round_i}"},
    )
    if code != 200:
        return {
            "modelId": mid,
            "name": name,
            "ok": False,
            "notes": [f"SESSION:{code}"],
            "round": round_i,
        }
    sid = session["id"]
    code, result = client.req(
        "POST",
        f"/sessions/{sid}/completions",
        {"modelId": mid, "content": MODEL_PROMPT},
        timeout=180,
    )
    text = ""
    notes: list[str] = []
    if code != 200:
        notes.append(f"COMPLETE:{code}:{result}")
    else:
        if isinstance(result, dict):
            text = result.get("content") or ""
            run_id = result.get("runId")
            if run_id:
                run = poll(client, run_id)
                asst = last_assistant(client, sid)
                text = asst.get("content") or text or ""
                st = run.get("status")
                if st not in ("COMPLETED", None):
                    notes.append(f"BAD_STATUS:{st}")
            elif not text:
                # 同步返回
                asst = last_assistant(client, sid)
                text = asst.get("content") or text or ""
        notes.extend(judge(text, min_len=8))
    ok = not notes
    print(f"[model:{name}] R{round_i} {'PASS' if ok else 'FAIL'} notes={notes}")
    return {
        "modelId": mid,
        "name": name,
        "round": round_i,
        "sessionId": sid,
        "ok": ok,
        "notes": notes,
        "contentPreview": (text[:300] + ("…" if len(text) > 300 else "")),
    }


def run_attachment_matrix(client: Client, agents: list[dict]) -> list[dict]:
    """上传多格式附件并检查元数据回显；对文档助手走一轮 invoke。"""
    target = next((a for a in agents if a.get("slug") == "gui-contract-extract"), agents[0])
    fixtures = [
        ("note.txt", b"Contract amount 120000, penalty 20%.\n", "text/plain"),
        ("contract.pdf", minimal_pdf(), "application/pdf"),
        ("scan.png", minimal_png(), "image/png"),
        ("clause.docx", minimal_docx(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        ("amount.xlsx", minimal_xlsx(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ]
    results = []
    code, session = client.req(
        "POST",
        "/sessions",
        {
            "title": "[附件矩阵] multi-format",
            "agentId": target["id"],
            "agentName": target.get("name"),
        },
    )
    if code != 200:
        return [{"ok": False, "notes": [f"SESSION:{code}"]}]
    sid = session["id"]
    for fname, blob, ctype in fixtures:
        ucode, up = client.upload(fname, blob, ctype, sid)
        item: dict[str, Any] = {
            "filename": fname,
            "contentType": ctype,
            "uploadCode": ucode,
            "ok": False,
            "notes": [],
        }
        if ucode != 200 or not isinstance(up, dict):
            item["notes"].append(f"UPLOAD_FAIL:{ucode}:{up}")
            results.append(item)
            print(f"[attach:{fname}] UPLOAD FAIL {ucode}")
            continue
        # 校验回显字段
        for key in ("id", "filename", "mimetype", "size"):
            if key == "filename" and up.get("filename") != fname and up.get("originalFilename") != fname:
                # 兼容字段名
                if not (up.get("filename") or up.get("originalFilename")):
                    item["notes"].append("MISSING_FILENAME")
            if key == "id" and not up.get("id"):
                item["notes"].append("MISSING_ID")
            if key == "size" and not (up.get("size") or up.get("sizeBytes")):
                item["notes"].append("MISSING_SIZE")
        mime = up.get("mimetype") or up.get("contentType") or ""
        if fname.endswith(".png") and not str(mime).startswith("image/"):
            item["notes"].append(f"BAD_MIME:{mime}")
        if fname.endswith(".pdf") and "pdf" not in str(mime).lower():
            item["notes"].append(f"BAD_MIME:{mime}")
        # 绑定到消息并检查 messages 回显
        code, sent = client.req(
            "POST",
            f"/sessions/{sid}/messages",
            {
                "agentId": target["id"],
                "content": f"请处理附件 {fname}",
                "attachmentIds": [up["id"]],
            },
            timeout=120,
        )
        if code != 200:
            item["notes"].append(f"SEND:{code}")
        else:
            run = poll(client, sent["runId"])
            if run.get("status") == "WAITING_INPUT":
                client.req(
                    "POST",
                    f"/runs/{sent['runId']}/resume",
                    {"action": "approve", "payload": "确认继续"},
                )
                run = poll(client, sent["runId"])
            # 用户消息附件回显
            code_m, msgs = client.req("GET", f"/sessions/{sid}/messages")
            items = msgs if isinstance(msgs, list) else (msgs or {}).get("items") or []
            user_atts = []
            for m in items:
                if m.get("role") == "user" and m.get("attachments"):
                    user_atts = m["attachments"]
            if not user_atts:
                item["notes"].append("NO_ATTACHMENT_ON_USER_MESSAGE")
            else:
                names = [a.get("filename") for a in user_atts]
                if fname not in names:
                    item["notes"].append(f"FILENAME_MISMATCH:{names}")
            st = run.get("status")
            if st == "FAILED":
                item["notes"].append(f"INVOKE_FAILED:{(run.get('errorMessage') or '')[:120]}")
            elif st not in ("COMPLETED", "WAITING_INPUT"):
                item["notes"].append(f"BAD_STATUS:{st}")
            asst = last_assistant(client, sid)
            text = asst.get("content") or ""
            item["notes"].extend(judge(text, min_len=10))
            item["contentPreview"] = text[:240]
            item["status"] = st
        item["ok"] = not item["notes"]
        print(f"[attach:{fname}] {'PASS' if item['ok'] else 'FAIL'} notes={item['notes']}")
        results.append(item)
    return results


def collect_catalog_issues(agents: list[dict]) -> list[dict]:
    issues = []
    for a in agents:
        inputs = a.get("supportedInputs") or []
        slug = a.get("slug") or ""
        needs_file = any(
            k in slug
            for k in (
                "document",
                "contract",
                "resume",
                "expense",
                "doc-image",
                "multi-doc",
                "image",
            )
        )
        if needs_file and inputs == ["text"]:
            issues.append(
                {
                    "type": "catalog_supportedInputs",
                    "slug": slug,
                    "severity": "文档/图片类助手 Catalog 仅声明 text，Composer 会隐藏上传按钮",
                    "severity": "high",
                }
            )
    return issues


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rounds", type=int, default=5, help="每个助手/模型循环次数（5-10）")
    ap.add_argument("--json-out", default="docs/eval/full_catalog_latest.json")
    ap.add_argument("--skip-attachments", action="store_true")
    ap.add_argument("--only-slug", default="", help="逗号分隔，仅测指定 slug")
    args = ap.parse_args()
    rounds = max(1, min(10, args.rounds))

    client = Client(BASE)
    client.login()
    code, agents = client.req("GET", "/agents")
    if code != 200 or not isinstance(agents, list):
        raise SystemExit(f"agents failed: {code} {agents}")
    code, models_body = client.req("GET", "/models")
    models: list[dict] = []
    if code == 200:
        if isinstance(models_body, list):
            models = models_body
        elif isinstance(models_body, dict):
            models = models_body.get("items") or models_body.get("data") or []

    only = {s.strip() for s in args.only_slug.split(",") if s.strip()}
    if only:
        agents = [a for a in agents if a.get("slug") in only]

    marker = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    print(f"=== full catalog eval marker={marker} agents={len(agents)} models={len(models)} rounds={rounds} ===")

    agent_results: list[dict] = []
    for agent in agents:
        slug = agent.get("slug") or ""
        prompt = PROMPTS.get(slug) or f"请用中文简要说明你能帮我做什么，并给一个具体示例。（助手：{agent.get('name')}）"
        for i in range(1, rounds + 1):
            try:
                agent_results.append(run_agent_once(client, agent, prompt, i))
            except Exception as e:
                agent_results.append(
                    {"slug": slug, "round": i, "ok": False, "notes": [f"EXC:{e}"]}
                )
                print(f"[{slug}] R{i} EXC {e}")

    model_results: list[dict] = []
    for model in models:
        for i in range(1, rounds + 1):
            try:
                model_results.append(run_model_once(client, model, i))
            except Exception as e:
                model_results.append(
                    {
                        "modelId": model.get("id"),
                        "round": i,
                        "ok": False,
                        "notes": [f"EXC:{e}"],
                    }
                )

    attach_results: list[dict] = []
    if not args.skip_attachments:
        attach_results = run_attachment_matrix(client, agents)

    catalog_issues = collect_catalog_issues(agents)

    # 汇总问题
    issues: list[dict] = list(catalog_issues)
    for r in agent_results:
        if not r.get("ok"):
            issues.append(
                {
                    "type": "agent_dialog",
                    "slug": r.get("slug"),
                    "round": r.get("round"),
                    "notes": r.get("notes"),
                    "preview": r.get("contentPreview"),
                    "severity": "high" if any(str(n).startswith(("FAILED", "BAD:", "EMPTY")) for n in (r.get("notes") or [])) else "medium",
                }
            )
    for r in model_results:
        if not r.get("ok"):
            issues.append(
                {
                    "type": "model_dialog",
                    "modelId": r.get("modelId"),
                    "name": r.get("name"),
                    "round": r.get("round"),
                    "notes": r.get("notes"),
                    "severity": "high",
                }
            )
    for r in attach_results:
        if not r.get("ok"):
            issues.append(
                {
                    "type": "attachment",
                    "filename": r.get("filename"),
                    "notes": r.get("notes"),
                    "severity": "high",
                }
            )

    # UX 静态问题（代码级已知）
    issues.append(
        {
            "type": "ux_static",
            "severity": "high",
            "finding": "MessageAttachmentList 仅图片有预览弹层；PDF/docx/xlsx 只有外链下载，无类型徽章与 PDF iframe 预览",
        }
    )

    summary = {
        "marker": marker,
        "rounds": rounds,
        "agents": len(agents),
        "models": len(models),
        "agent_passed": sum(1 for r in agent_results if r.get("ok")),
        "agent_failed": sum(1 for r in agent_results if not r.get("ok")),
        "model_passed": sum(1 for r in model_results if r.get("ok")),
        "model_failed": sum(1 for r in model_results if not r.get("ok")),
        "attach_passed": sum(1 for r in attach_results if r.get("ok")),
        "attach_failed": sum(1 for r in attach_results if not r.get("ok")),
        "issues": issues,
        "agent_results": agent_results,
        "model_results": model_results,
        "attach_results": attach_results,
    }
    out = Path(args.json_out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    md = Path("docs/eval/FULL_CATALOG_ISSUES.md")
    lines = [
        f"# 全量评测问题清单",
        "",
        f"> marker `{marker}` · rounds={rounds} · agents={len(agents)} · models={len(models)}",
        "",
        f"- 助手回合：{summary['agent_passed']} PASS / {summary['agent_failed']} FAIL",
        f"- 模型回合：{summary['model_passed']} PASS / {summary['model_failed']} FAIL",
        f"- 附件矩阵：{summary['attach_passed']} PASS / {summary['attach_failed']} FAIL",
        "",
        "## Issues",
        "",
    ]
    for i, iss in enumerate(issues, 1):
        lines.append(f"### {i}. [{iss.get('severity','?')}] {iss.get('type')}")
        for k, v in iss.items():
            if k in ("type", "severity"):
                continue
            lines.append(f"- **{k}**: `{v}`")
        lines.append("")
    md.write_text("\n".join(lines), encoding="utf-8")

    print("\n=== FULL CATALOG SUMMARY ===")
    print(f"agents {summary['agent_passed']}/{summary['agent_passed']+summary['agent_failed']}")
    print(f"models {summary['model_passed']}/{summary['model_passed']+summary['model_failed']}")
    print(f"attach {summary['attach_passed']}/{summary['attach_passed']+summary['attach_failed']}")
    print(f"issues={len(issues)} wrote {out} {md}")
    return 1 if (summary["agent_failed"] or summary["model_failed"] or summary["attach_failed"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
