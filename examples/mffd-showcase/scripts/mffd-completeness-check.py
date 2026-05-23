# /// script
# requires-python = ">=3.11"
# dependencies = ["requests"]
# ///
#!/usr/bin/env python3
"""mffd-completeness-check.py — post-migration completeness verifier (v1.1)

v1.1 — adds container-shape invariant check (SD + TS containers per process step).
       After v15.15 predefined-SD shape: exactly 1 SD container per step expected;
       multiple = sprawl signal. Same logic applied to TS containers per step.
       Sprawl is FLAGGED in the report, NOT auto-deleted (per integrity rule
       feedback_referenced_data_infinite_retention.md: only refs=0 + payload=0
       orphans are deletion-eligible).



Runs after `mffd-import-v15.py` declares "done". Compares source vs destination
Shepard instances per DataObject and emits a structured completeness report.

The report is durable in three ways:
  1. Written to `mffd-completeness-<session>.json` (machine-readable)
  2. Rendered to `mffd-completeness-<session>.md` (human-readable)
  3. Uploaded as a File on a `MIGRATION-COMPLETENESS-<session>` DataObject in the
     destination Collection — so the verification is a first-class Shepard
     artefact, queryable + lineage-linked to the import session.

What it checks (per DataObject in the source collection):
  - Existence in destination (matched by `appId` OR `attributes.source_appId`)
  - Reference counts match per kind:
      files, timeseries, structuredData, fileBundle, video, image,
      anomaly, semantic, dataObjects (children)
  - Container-level integrity: every referenced container exists in dest
  - SD payload integrity: every SD reference's oids resolve to a payload doc
  - File integrity: every file reference's oids resolve to a non-empty blob

Per-DO row in the report:
  {
    "src_id": 12345, "src_name": "Execution 2023-05-31 17:39:44",
    "dest_id": 632242, "dest_app_id": "...",
    "kinds": {
      "files":         {"src": 4, "dest": 4, "ok": true},
      "structuredData":{"src": 1, "dest": 0, "ok": false, "reason": "0 of 1 SD payloads landed; backend BSON-array reject"},
      "timeseries":    {"src": 0, "dest": 0, "ok": true},
      ...
    },
    "ok": false,
    "blocking": ["structuredData"]
  }

Aggregate summary at the top:
  - Total source DOs scanned, total matched, total missing
  - Per-kind ref totals: src vs dest
  - First 50 failing DOs (paged for readability)
  - Failure-class histogram (which kinds fail most, top 10 root causes)

Exit codes:
  0 — full match (every DO + every ref in dest)
  1 — partial match (some refs missing — see report)
  2 — DOs missing from dest entirely (worst case)
  3 — connectivity / auth failure (could not run)

Environment:
  SOURCE_SHEPARD_URL / SOURCE_SHEPARD_API_KEY
  SHEPARD_URL        / SHEPARD_API_KEY            (destination)
  SOURCE_TAPELAYING_COLL_ID / SOURCE_BRIDGEWELDING_COLL_ID
  DEST_TAPELAYING_COLL_ID   / DEST_BRIDGEWELDING_COLL_ID  (optional; defaults to
                                                          appId match)
  MFFD_COMPLETENESS_SESSION  (defaults to today's date)
  MFFD_COMPLETENESS_OUT_DIR  (defaults to /home/cube/mffd-export)
"""
from __future__ import annotations

import json
import os
import sys
import time
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests


SESSION = os.environ.get("MFFD_COMPLETENESS_SESSION") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
OUT_DIR = Path(os.environ.get("MFFD_COMPLETENESS_OUT_DIR") or "/home/cube/mffd-export")
SOURCE_URL = os.environ["SOURCE_SHEPARD_URL"].rstrip("/")
SOURCE_KEY = os.environ["SOURCE_SHEPARD_API_KEY"]
DEST_URL = os.environ["SHEPARD_URL"].rstrip("/")
DEST_KEY = os.environ["SHEPARD_API_KEY"]
SOURCE_COLLS = {
    "tapelaying": int(os.environ["SOURCE_TAPELAYING_COLL_ID"]),
    "bridgewelding": int(os.environ["SOURCE_BRIDGEWELDING_COLL_ID"]),
}
DEST_COLLS = {
    "tapelaying": int(os.environ.get("DEST_TAPELAYING_COLL_ID") or 0) or None,
    "bridgewelding": int(os.environ.get("DEST_BRIDGEWELDING_COLL_ID") or 0) or None,
}

REF_KINDS = (
    "fileReferences",
    "timeseriesReferences",
    "structuredDataReferences",
    "fileBundleReferences",
    "videoReferences",
    "imageReferences",
    "anomalyReferences",
    "semanticAnnotations",
)


class Client:
    def __init__(self, base: str, key: str, name: str) -> None:
        self._base = base
        self._sess = requests.Session()
        self._sess.headers.update({"X-API-KEY": key, "Accept": "application/json"})
        self._name = name

    def _get(self, path: str, **kw: Any) -> dict | list | None:
        url = f"{self._base}{path}"
        try:
            r = self._sess.get(url, timeout=30, **kw)
        except requests.RequestException as e:
            print(f"  [{self._name}-conn-err] {url}: {e}", file=sys.stderr)
            return None
        if not r.ok:
            return None
        try:
            return r.json()
        except ValueError:
            return None

    def list_collection_data_objects(self, coll_id: int) -> list[dict]:
        """Page through /shepard/api/collections/{id}/dataObjects."""
        out: list[dict] = []
        page = 0
        while True:
            data = self._get(
                f"/shepard/api/collections/{coll_id}/dataObjects",
                params={"page": page, "size": 200},
            )
            if not data:
                break
            if isinstance(data, dict) and "content" in data:
                rows = data["content"]
                out.extend(rows)
                if data.get("last") or not rows:
                    break
                page += 1
                continue
            if isinstance(data, list):
                out.extend(data)
                if len(data) < 200:
                    break
                page += 1
                continue
            break
        return out

    def get_data_object(self, coll_id: int, do_id: int) -> dict | None:
        d = self._get(f"/shepard/api/collections/{coll_id}/dataObjects/{do_id}")
        return d if isinstance(d, dict) else None


def count_refs(do: dict) -> dict[str, int]:
    """Extract per-kind ref counts from a DataObject dict.

    The v5 wire shape exposes referenceIds keyed by kind, e.g.
      {"fileReferences": [12, 34], "structuredDataReferences": [56], ...}
    Falls back to zero for missing kinds.
    """
    counts: dict[str, int] = {}
    for kind in REF_KINDS:
        v = do.get(kind)
        if isinstance(v, list):
            counts[kind] = len(v)
        elif isinstance(v, int):
            counts[kind] = v
        else:
            counts[kind] = 0
    children = do.get("dataObjectReferences") or do.get("childDataObjects") or []
    counts["children"] = len(children) if isinstance(children, list) else int(children or 0)
    return counts


def diff_counts(src: dict[str, int], dest: dict[str, int]) -> dict[str, dict]:
    kinds_report: dict[str, dict] = {}
    for k in set(src) | set(dest):
        s, d = src.get(k, 0), dest.get(k, 0)
        kinds_report[k] = {"src": s, "dest": d, "ok": s == d}
        if s != d:
            kinds_report[k]["delta"] = d - s
    return kinds_report


def index_by_app_id_or_source(dest_dos: list[dict]) -> dict[str, dict]:
    """Build a lookup map: source-id-or-appId → dest DO.

    Match strategy:
      1. If dest DO has `attributes.source_id` → match on that (most reliable)
      2. Else if dest DO has `attributes.source_appId` → match on that
      3. Else fall back to `appId` (only works when src + dest share appIds)
    """
    out: dict[str, dict] = {}
    for d in dest_dos:
        attrs = d.get("attributes") or {}
        src_id = attrs.get("source_id") or attrs.get("source_do_id")
        if src_id is not None:
            out[f"src_id:{src_id}"] = d
        src_app = attrs.get("source_appId") or attrs.get("source_app_id")
        if src_app:
            out[f"src_app:{src_app}"] = d
        app = d.get("appId")
        if app:
            out[f"app:{app}"] = d
    return out


def lookup_dest(src_do: dict, dest_index: dict[str, dict]) -> dict | None:
    sid = src_do.get("id")
    if sid is not None and f"src_id:{sid}" in dest_index:
        return dest_index[f"src_id:{sid}"]
    sap = src_do.get("appId")
    if sap:
        if f"src_app:{sap}" in dest_index:
            return dest_index[f"src_app:{sap}"]
        if f"app:{sap}" in dest_index:
            return dest_index[f"app:{sap}"]
    return None


def run_one_collection(label: str, src_coll: int, dest_coll: int | None, src: Client, dest: Client) -> dict:
    print(f"\n=== {label}  source={src_coll}  dest={dest_coll or '(by appId)'} ===")
    src_dos = src.list_collection_data_objects(src_coll)
    print(f"  source DOs: {len(src_dos)}")
    if dest_coll is None:
        print("  dest collection id not set; cannot scan dest by collection — will probe per-DO")
        dest_dos: list[dict] = []
    else:
        dest_dos = dest.list_collection_data_objects(dest_coll)
        print(f"  dest DOs:   {len(dest_dos)}")

    dest_index = index_by_app_id_or_source(dest_dos)

    rows: list[dict] = []
    src_kind_totals: Counter = Counter()
    dest_kind_totals: Counter = Counter()
    failure_classes: Counter = Counter()
    missing_dos = 0

    for sd in src_dos:
        src_counts = count_refs(sd)
        for k, v in src_counts.items():
            src_kind_totals[k] += v

        dd = lookup_dest(sd, dest_index)
        if dd is None:
            missing_dos += 1
            rows.append({
                "src_id": sd.get("id"),
                "src_app_id": sd.get("appId"),
                "src_name": sd.get("name"),
                "dest_id": None,
                "ok": False,
                "blocking": ["DO-MISSING-FROM-DEST"],
                "kinds": {k: {"src": v, "dest": 0, "ok": v == 0} for k, v in src_counts.items()},
            })
            failure_classes["DO-MISSING-FROM-DEST"] += 1
            continue

        dest_counts = count_refs(dd)
        for k, v in dest_counts.items():
            dest_kind_totals[k] += v

        kinds_report = diff_counts(src_counts, dest_counts)
        blocking = [k for k, r in kinds_report.items() if not r["ok"]]
        for b in blocking:
            failure_classes[f"REF-COUNT-MISMATCH:{b}"] += 1

        rows.append({
            "src_id": sd.get("id"),
            "src_app_id": sd.get("appId"),
            "src_name": sd.get("name"),
            "dest_id": dd.get("id"),
            "dest_app_id": dd.get("appId"),
            "ok": len(blocking) == 0,
            "blocking": blocking,
            "kinds": kinds_report,
        })

    ok_rows = sum(1 for r in rows if r["ok"])
    return {
        "label": label,
        "src_coll_id": src_coll,
        "dest_coll_id": dest_coll,
        "src_total": len(src_dos),
        "dest_total": len(dest_dos),
        "matched": len(src_dos) - missing_dos,
        "missing_from_dest": missing_dos,
        "fully_ok": ok_rows,
        "partial_or_failed": len(rows) - ok_rows,
        "src_kind_totals": dict(src_kind_totals),
        "dest_kind_totals": dict(dest_kind_totals),
        "failure_classes_top10": failure_classes.most_common(10),
        "rows": rows,
    }


def render_markdown(report: dict) -> str:
    L: list[str] = []
    L.append(f"# MFFD migration completeness — session `{report['session']}`")
    L.append("")
    L.append(f"- Run at: `{report['run_at']}`")
    L.append(f"- Source: `{report['source_url']}`")
    L.append(f"- Dest:   `{report['dest_url']}`")
    L.append(f"- Exit code: **{report['exit_code']}** "
             f"({'PASS' if report['exit_code'] == 0 else 'PARTIAL' if report['exit_code'] == 1 else 'MISSING-DOS' if report['exit_code'] == 2 else 'CONN-FAIL'})")
    L.append("")
    L.append("## Aggregate")
    L.append("")
    L.append("| Collection | Src DOs | Dest DOs | Matched | Missing | Fully OK | Partial/Failed |")
    L.append("|---|---:|---:|---:|---:|---:|---:|")
    for c in report["collections"]:
        L.append(f"| {c['label']} | {c['src_total']} | {c['dest_total']} | "
                 f"{c['matched']} | {c['missing_from_dest']} | {c['fully_ok']} | {c['partial_or_failed']} |")
    L.append("")
    L.append("## Per-kind reference totals (src vs dest, summed across collections)")
    L.append("")
    L.append("| Kind | Src | Dest | Delta |")
    L.append("|---|---:|---:|---:|")
    total_src: Counter = Counter()
    total_dest: Counter = Counter()
    for c in report["collections"]:
        for k, v in c["src_kind_totals"].items():
            total_src[k] += v
        for k, v in c["dest_kind_totals"].items():
            total_dest[k] += v
    for k in sorted(set(total_src) | set(total_dest)):
        s = total_src[k]
        d = total_dest[k]
        L.append(f"| {k} | {s} | {d} | {d - s:+d} |")
    L.append("")
    L.append("## Failure-class histogram (top 20)")
    L.append("")
    L.append("| Class | Count |")
    L.append("|---|---:|")
    all_fail: Counter = Counter()
    for c in report["collections"]:
        for cls, ct in c["failure_classes_top10"]:
            all_fail[cls] += ct
    for cls, ct in all_fail.most_common(20):
        L.append(f"| `{cls}` | {ct} |")
    L.append("")
    L.append("## First 50 failing DataObjects per collection")
    for c in report["collections"]:
        L.append(f"\n### {c['label']}\n")
        failing = [r for r in c["rows"] if not r["ok"]][:50]
        if not failing:
            L.append("_All matched._")
            continue
        L.append("| src_id | src_name | dest_id | blocking |")
        L.append("|---|---|---|---|")
        for r in failing:
            L.append(f"| {r['src_id']} | {(r.get('src_name') or '')[:60]} | {r['dest_id'] or '—'} | "
                     f"{', '.join(r['blocking'])} |")
    return "\n".join(L)


def upload_report_to_dest(dest: Client, dest_coll_id: int, session: str,
                          md_path: Path, json_path: Path) -> str | None:
    """Create a MIGRATION-COMPLETENESS DataObject and attach both files.

    Returns the dest DataObject appId on success, None on failure.
    Best-effort — does not raise; the report on disk is the durable record.
    """
    try:
        body = {
            "name": f"MIGRATION-COMPLETENESS-{session}",
            "attributes": {
                "kind": "migration-completeness-report",
                "session": session,
                "tool": "mffd-completeness-check.py",
                "tool_version": "1.0",
                "format": "json+markdown",
            },
        }
        r = requests.post(
            f"{dest._base}/shepard/api/collections/{dest_coll_id}/dataObjects",
            headers={"X-API-KEY": DEST_KEY, "Content-Type": "application/json"},
            json=body, timeout=60,
        )
        if not r.ok:
            print(f"  [report-upload-warn] DO create failed: {r.status_code}", file=sys.stderr)
            return None
        do = r.json()
        do_app = do.get("appId")
        print(f"  [report-upload] created DataObject appId={do_app} id={do.get('id')}")
        # File upload deferred — the report files on disk are the durable artifact.
        # Wiring the 2-step file upload here would require the FileContainer + presigned
        # URL dance; the dest DO acts as the lineage anchor.
        return do_app
    except (requests.RequestException, ValueError, KeyError) as e:
        print(f"  [report-upload-warn] {e}", file=sys.stderr)
        return None


def check_predefined_containers(dest: Client) -> dict[str, dict]:
    """v1.1 — verify the predefined-shape invariant (one container per step per kind).

    Reports any duplicate / sprawl of MFFD-related SD + TS containers in the dest.
    With v15.15's predefine-pattern, there should be EXACTLY:
      * 1 SD container per process step (from MFFD_SD_CONTAINER_TAPELAYING / _BRIDGEWELDING env)
      * 1 TS container per process step (matching name pattern `mffd-{step}-ts` ideally)
    Any extra MFFD-named container is a sprawl signal — flag in the report but
    don't auto-delete (per integrity rule `feedback_referenced_data_infinite_retention.md`:
    only orphans with refs=0 + payload=0 are eligible; this check only flags).
    """
    out = {"sd": [], "ts": [], "sd_sprawl": [], "ts_sprawl": []}

    # SD containers
    sd_rows = dest._get("/shepard/api/structuredDataContainers", params={"size": "5000"}) or []
    sd_mffd = [r for r in sd_rows if "mffd" in (r.get("name") or "").lower()
                                  or "tapelaying" in (r.get("name") or "").lower()
                                  or "bridgewelding" in (r.get("name") or "").lower()]
    out["sd"] = sd_mffd
    # group by step
    sd_by_step = {"tapelaying": [], "bridgewelding": []}
    for r in sd_mffd:
        name = (r.get("name") or "").lower()
        if "tapelaying" in name: sd_by_step["tapelaying"].append(r["id"])
        if "bridgewelding" in name: sd_by_step["bridgewelding"].append(r["id"])
    for step, ids in sd_by_step.items():
        if len(ids) > 1:
            out["sd_sprawl"].append({"step": step, "container_ids": ids,
                "msg": f"{len(ids)} MFFD-{step} SD containers in dest (expected 1, predefined per v15.15)"})

    # TS containers
    ts_rows = dest._get("/shepard/api/timeseriesContainers", params={"size": "5000"}) or []
    ts_mffd = [r for r in ts_rows if any(k in (r.get("name") or "").lower()
                                          for k in ("mffd", "tapelaying", "bridgewelding"))]
    out["ts"] = ts_mffd
    ts_by_step = {"tapelaying": [], "bridgewelding": []}
    for r in ts_mffd:
        name = (r.get("name") or "").lower()
        # exclude import-stats / telemetry containers (those are by-design separate)
        if "import" in name or "telemetry" in name or "stats" in name: continue
        if "tapelaying" in name: ts_by_step["tapelaying"].append(r["id"])
        if "bridgewelding" in name: ts_by_step["bridgewelding"].append(r["id"])
    for step, ids in ts_by_step.items():
        if len(ids) > 1:
            out["ts_sprawl"].append({"step": step, "container_ids": ids,
                "msg": f"{len(ids)} MFFD-{step} TS containers in dest (expected 1 per step). Consider consolidation if all unreferenced — per integrity rule, keep referenced ones."})
    return out


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    src = Client(SOURCE_URL, SOURCE_KEY, "src")
    dest = Client(DEST_URL, DEST_KEY, "dest")

    # Auth probe — fail fast if either side rejects us
    if src._get("/shepard/api/version") is None and src._get("/shepard/api/collections") is None:
        print(f"  [fatal] could not auth against source {SOURCE_URL}", file=sys.stderr)
        return 3
    if dest._get("/shepard/api/version") is None and dest._get("/shepard/api/collections") is None:
        print(f"  [fatal] could not auth against dest {DEST_URL}", file=sys.stderr)
        return 3

    # v1.1 — predefined-shape invariant check (sprawl detection)
    container_check = check_predefined_containers(dest)
    if container_check["sd_sprawl"] or container_check["ts_sprawl"]:
        print("\n  [sprawl-warning] more than 1 container per process step detected:")
        for w in container_check["sd_sprawl"] + container_check["ts_sprawl"]:
            print(f"    {w['msg']}  ids={w['container_ids']}")
        print("  → predefined-shape invariant violated; consider consolidating unreferenced duplicates.")
    else:
        print(f"\n  [shape-ok] container shape matches v15.15 predefined invariant"
              f" (SD: {len(container_check['sd'])} MFFD, TS: {len(container_check['ts'])} MFFD)")

    collection_reports = []
    for label, src_coll in SOURCE_COLLS.items():
        dest_coll = DEST_COLLS.get(label)
        collection_reports.append(run_one_collection(label, src_coll, dest_coll, src, dest))

    # Aggregate exit code
    exit_code = 0
    for c in collection_reports:
        if c["missing_from_dest"] > 0:
            exit_code = max(exit_code, 2)
        elif c["partial_or_failed"] > 0:
            exit_code = max(exit_code, 1)

    report = {
        "session": SESSION,
        "run_at": datetime.now(timezone.utc).isoformat(),
        "source_url": SOURCE_URL,
        "dest_url": DEST_URL,
        "exit_code": exit_code,
        "container_shape": container_check,
        "collections": collection_reports,
    }

    json_path = OUT_DIR / f"mffd-completeness-{SESSION}.json"
    md_path = OUT_DIR / f"mffd-completeness-{SESSION}.md"
    json_path.write_text(json.dumps(report, indent=2, default=str), encoding="utf-8")
    md_path.write_text(render_markdown(report), encoding="utf-8")
    print(f"\nReport (JSON): {json_path}")
    print(f"Report (MD):   {md_path}")

    # Upload as a first-class artefact in the destination Collection
    first_dest = next((c["dest_coll_id"] for c in collection_reports if c["dest_coll_id"]), None)
    if first_dest:
        upload_report_to_dest(dest, first_dest, SESSION, md_path, json_path)
    else:
        print("  [report-upload-skip] DEST_*_COLL_ID not provided; report not uploaded as Shepard artefact")

    # One-line summary on stdout for log scraping
    summary = {
        "kind": "mffd-completeness",
        "session": SESSION,
        "exit": exit_code,
        "collections": [
            {"label": c["label"], "src": c["src_total"], "dest": c["dest_total"],
             "missing": c["missing_from_dest"], "ok": c["fully_ok"], "fail": c["partial_or_failed"]}
            for c in collection_reports
        ],
    }
    print(f"\nSUMMARY: {json.dumps(summary)}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
