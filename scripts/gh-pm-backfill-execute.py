#!/usr/bin/env python3
"""GH-PM5 backfill executor — 2026-05-23.

Walks aidocs/16-dispatcher-backlog.md rows, applies the 4-gate filter,
files Issues via `gh issue create` with the §8.1 BACKFILL marker, assigns
labels + milestones, closes rows whose status is `done`, and appends
the execution log to gh-pm-backfill-plan-2026-05-23.md §4.
"""

import json
import re
import subprocess
import sys
import time
from pathlib import Path
from datetime import datetime

REPO = "noheton/shepard"
TODAY = "2026-05-23"
BACKLOG = Path("/opt/shepard/aidocs/16-dispatcher-backlog.md")
LOG = Path("/opt/shepard/aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md")

# Milestone numbers (live, just created)
MS_RC1 = 25
MS_RC2 = 26
MS_V6 = 27
MS_BACKLOG = 28

SLEEP = 0.4  # between API calls

# ----- 4-gate classifier rules -----

# Row IDs / prefixes that map to rc.1 (shipped 2026-05-01 → 2026-05-23)
RC1_PREFIXES = {
    "FS1", "PROV1", "BIB", "ORIGIN", "DOC-STAGE", "GH-PM", "GH-INFRA",
    "V1COMPAT", "IMPORT-W", "IMPORT-FIX", "OBS-MFFD", "MCPGW", "AAS1",
    "VID1", "FB1a-fileSize", "TM1a", "MFFD", "WAAPI",
}

# Row IDs that explicitly should FILE because admin/user-visible
EXPLICIT_FILE_PREFIXES = {
    "A0", "A1", "A3", "A4", "A5", "P1", "P2", "P3", "P4", "P10", "P14",
    "P16", "P17", "P18", "P21", "P23",
    "L1", "L2", "L5", "L6", "L8",
    "C3", "C5", "H4",
    "U1", "U3-coupled",
    "J1", "G1", "T1", "PR1", "V2", "AI1", "PV1", "PL1", "PM1",
    "DX1", "DX2", "DX6", "DX7", "N1", "D1", "EXP1", "R1", "R2",
    "F3", "F4", "F5", "F8",
    "PROV1", "MNT1", "V2S1", "CG1", "QW", "UI1", "UI2", "UI3", "UI4",
    "UI5", "UI6", "UI8", "UI9", "UI12", "CP1", "ONT1", "CAD1", "CPACS1",
    "RCE1", "SB1", "PC1", "DT1", "REF1", "AI1q", "AI1r", "CC1",
    "TS56", "SWEEP",
}

# Skip prefixes (parked / superseded / generally noise)
SKIP_PREFIXES = {
    "FOCUS-",  # in-flight focus capture, not work items
}

def parse_aidocs16():
    """Yield row dicts from aidocs/16-dispatcher-backlog.md."""
    text = BACKLOG.read_text()
    lines = text.split("\n")
    rows = []
    in_table = False
    header = []
    section = ""
    for ln_no, line in enumerate(lines, 1):
        sec_match = re.match(r'^###\s+(.+)$', line)
        if sec_match:
            section = sec_match.group(1)
            continue
        if line.startswith("| ID |") or line.startswith("|ID|"):
            in_table = True
            header = [c.strip().lower() for c in line.split("|")[1:-1]]
            continue
        if in_table and re.match(r'^\|\s*-+', line):
            continue
        if in_table and not line.startswith("|"):
            in_table = False
            header = []
            continue
        if in_table and line.startswith("|"):
            cells = [c.strip() for c in line.split("|")[1:-1]]
            if len(cells) < 4:
                continue
            m = re.match(r'^([A-Z][A-Za-z0-9.\-+_]*)\s*$', cells[0])
            if not m:
                continue
            row_id = m.group(1)
            try:
                status_idx = header.index('status')
            except ValueError:
                continue
            status = cells[status_idx] if status_idx < len(cells) else ""
            size = cells[status_idx - 1] if status_idx >= 1 else ""
            slice_txt = cells[1] if len(cells) > 1 else ""
            notes = cells[-1] if cells else ""
            rows.append({
                "id": row_id,
                "section": section,
                "slice": slice_txt,
                "size": size,
                "status_raw": status,
                "notes": notes,
                "line": ln_no,
            })
    return rows


def norm_status(s):
    s = s.lower()
    s = re.sub(r'\*+', '', s)
    s = s.replace('`', '')
    s = re.sub(r'\(.*?\)', '', s)
    s = re.sub(r'—.*', '', s)
    s = s.strip()
    if 'parked' in s or 'superseded' in s or 'decommiss' in s:
        return 'parked'
    if 'done' in s or 'shipped' in s:
        return 'done'
    if 'design done' in s or 'designed' in s:
        return 'design-done'
    if 'in-progress' in s or 'in progress' in s:
        return 'in-progress'
    if 'blocked' in s:
        return 'blocked'
    if 'queued' in s or 'feature-defined' in s:
        return 'queued'
    return 'other'


def is_rc1(row):
    """True if row belongs in v6.0.0-rc.1 bundle."""
    rid = row['id']
    for p in RC1_PREFIXES:
        if rid.startswith(p):
            return True
    return False


def classify(row):
    """Return (decision, rationale, milestone)."""
    rid = row['id']
    ns = norm_status(row['status_raw'])
    section = row['section'].lower()

    # Skip parked/superseded
    if ns == 'parked':
        return ("SKIP", "parked/superseded/decommissioned", None)

    # Skip FOCUS-* in-flight capture
    for sp in SKIP_PREFIXES:
        if rid.startswith(sp):
            return ("SKIP", f"prefix-skip: {sp} (focus-capture, not a work item)", None)

    # GH-PM5 itself: filed by §85 §3 gate 4 (in-flight policy)
    # File design-done items only if they're admin-visible epics
    file_ok = False
    rationale = ""

    for p in EXPLICIT_FILE_PREFIXES | RC1_PREFIXES:
        if rid.startswith(p):
            file_ok = True
            rationale = f"gate 1 (external-visible): row prefix {p} is admin/user-surface"
            break

    if not file_ok:
        return ("SKIP", "no gate-1 surface (internal refactor / docs-only)", None)

    # Milestone routing
    if is_rc1(row) and ns == 'done':
        return ("FILE", rationale, MS_RC1)
    elif ns == 'done':
        return ("FILE", rationale, MS_BACKLOG)
    elif ns in ('queued', 'blocked', 'design-done', 'in-progress'):
        return ("FILE", rationale, MS_BACKLOG)
    else:
        return ("FILE", rationale + " (ambiguous status)", MS_BACKLOG)


# ----- Conventional-commits type inference -----

def infer_type(row):
    ns = norm_status(row['status_raw'])
    sec = row['section'].lower()
    slice_lo = row['slice'].lower()
    if 'security' in sec or 'security' in slice_lo:
        return 'fix'
    if 'docs' in slice_lo[:80] or row['id'].startswith('DOC') or row['id'].startswith('GH-PM') or row['id'].startswith('GH-INFRA'):
        return 'docs'
    if 'bug' in slice_lo[:50] or 'fix' in slice_lo[:30]:
        return 'fix'
    if 'refactor' in slice_lo[:50] or row['id'].startswith('SWEEP'):
        return 'refactor'
    if ns in ('design-done',):
        return 'docs'  # design-only work
    return 'feat'


def infer_area_label(row):
    rid = row['id']
    sec = row['section'].lower()
    slice_lo = row['slice'].lower()
    if rid.startswith(('FS', 'A5', 'P1', 'P2', 'P3', 'P4', 'L2', 'C5', 'H4', 'PROV', 'V2')):
        return 'area:backend'
    if rid.startswith(('UI', 'QW', 'CC')):
        return 'area:frontend'
    if rid.startswith(('PL1', 'PV1', 'PM1', 'VID1', 'CAD', 'CPACS', 'DT', 'PC1', 'SB1', 'AAS', 'EXP', 'D1', 'CG', 'AI1', 'N1')):
        return 'area:plugins'
    if rid.startswith(('GH-INFRA', 'GH-PM', 'OBS', 'IMPORT')):
        return 'area:infra'
    if rid.startswith(('DOC-STAGE', 'BIB', 'ORIGIN', 'TERM')):
        return 'area:docs'
    if rid.startswith(('ONT', 'SB', 'SWEEP', 'M4I', 'IOT', 'PROV')):
        return 'area:semantics'
    if rid.startswith(('MCPGW', 'MCP')):
        return 'area:mcp'
    if rid.startswith(('VIS',)):
        return 'area:vis'
    if rid.startswith(('AI',)):
        return 'area:ai'
    if rid.startswith(('CG',)):
        return 'area:clients'
    if 'security' in sec or 'security' in slice_lo[:80]:
        return 'area:security'
    return 'area:backend'


def status_to_label(ns):
    return {
        'done': 'status:done',
        'in-progress': 'status:in-progress',
        'blocked': 'status:blocked',
        'queued': 'status:queued',
        'design-done': 'status:queued',
        'other': 'status:queued',
    }.get(ns, 'status:queued')


def status_to_stage(ns):
    return {
        'done': 'stage:deployed',
        'in-progress': 'stage:feedback-implemented',
        'blocked': 'stage:feature-defined',
        'queued': 'stage:feature-defined',
        'design-done': 'stage:feature-defined',
        'other': 'stage:feature-defined',
    }.get(ns, 'stage:feature-defined')


def size_to_effort(size):
    s = size.upper().replace('*', '').strip()
    if s in ('S', 'XS'):
        return 'effort:S'
    if s in ('M',):
        return 'effort:M'
    if s in ('L',):
        return 'effort:L'
    if s in ('XL',):
        return 'effort:XL'
    if '–' in s or '-' in s:
        # ranges like S-M, M-L
        parts = re.split(r'[–-]', s)
        return f'effort:{parts[-1].strip()}' if parts[-1].strip() in ('S','M','L','XL') else 'effort:M'
    return 'effort:M'


def extract_commit_hash(row):
    """Search notes + status_raw for a commit hash."""
    for fld in (row.get('notes', ''), row.get('status_raw', '')):
        m = re.search(r'\b([0-9a-f]{7,40})\b', fld.lower())
        if m:
            return m.group(1)
    return None


def build_issue_body(row, ns, milestone_label):
    commit = extract_commit_hash(row) or '(see aidocs/16 row)'
    persona_hint = ""
    if 'GH-PM' in row['id']:
        persona_hint = "; persona findings: aidocs/agent-findings/persona-*-gh-pm-2026-05-23.md"
    body = f"""🤖 **BACKFILL** — created retroactively {TODAY} as part of GH-PM5 adoption.
Original work: commit `{commit}`, status `{ns}`. Audit trail: `aidocs/16-dispatcher-backlog.md` row `{row['id']}` (line {row['line']}, section "{row['section']}"){persona_hint}.

This Issue is a clearly-synthetic artefact (like a dataset row). The discussion thread does not represent contemporaneous review; the work shipped before this Issue existed. See `feedback_no_synthetic_provenance.md` for the principle, and `aidocs/agent-findings/gh-pm-backfill-plan-2026-05-23.md` for the execution audit trail.

Marker mandated by `aidocs/strategy/85 §8.1`. Anti-pattern guard: `aidocs/strategy/85 §15 #7` (filing without this marker = silent forgery).

---

**Slice (from aidocs/16):**

{row['slice']}

**Size:** {row['size']}
**Status:** {row['status_raw']}
**Section:** {row['section']}

**Notes (from aidocs/16):**

{row['notes']}

---

**Traceability:** run `scripts/trace-feature.sh {row['id']}` from repo root for the 9-surface trace.
**Milestone:** `{milestone_label}` (synthetic-backfill bucket).
"""
    if ns == 'done':
        commit_short = commit if commit and isinstance(commit, str) and len(commit) >= 7 else '(see aidocs/16)'
        body += f"\nClosed by commit `{commit_short}` on {TODAY} (backfill — work shipped before Issue existed).\n"
    return body


def build_title(row):
    """Conventional-Commits-style title."""
    t = infer_type(row)
    # Subject from slice — strip markdown bold, truncate to ~70 chars
    subject = row['slice']
    subject = re.sub(r'\*+', '', subject)
    subject = re.sub(r'`([^`]+)`', r'\1', subject)
    # Take first sentence
    subject = subject.split('.')[0].strip()
    subject = subject.split('—')[0].strip() if '—' in subject and len(subject.split('—')[0]) > 20 else subject
    if len(subject) > 80:
        subject = subject[:77] + '...'
    return f"{t}({row['id']}): {subject}"


def run(args, capture=True):
    """Run a subprocess. Raises on failure."""
    res = subprocess.run(args, capture_output=capture, text=True)
    if res.returncode != 0:
        sys.stderr.write(f"FAIL: {args}\nstdout: {res.stdout}\nstderr: {res.stderr}\n")
        raise RuntimeError(f"subprocess failed: {args}")
    return res.stdout.strip()


def create_issue(row, ns, milestone_title, milestone_label):
    title = build_title(row)
    body = build_issue_body(row, ns, milestone_label)
    labels = [
        infer_area_label(row),
        'meta:backfill',
        status_to_label(ns),
        status_to_stage(ns),
        size_to_effort(row['size']),
    ]
    # type label: map cc-type → label vocabulary
    t = infer_type(row)
    type_label_map = {
        'feat': 'type:feature',
        'fix': 'type:bug',
        'docs': 'type:docs',
        'refactor': 'type:refactor',
        'chore': 'type:chore',
        'test': 'type:test',
    }
    labels.append(type_label_map.get(t, 'type:feature'))
    labels.append('severity:minor')  # default; can be overridden by editor

    # gh issue create
    cmd = [
        'gh', 'issue', 'create',
        '--title', title,
        '--body', body,
        '--milestone', milestone_title,
    ]
    for lab in labels:
        cmd += ['--label', lab]

    url = run(cmd)
    # url looks like https://github.com/noheton/shepard/issues/677
    m = re.search(r'/issues/(\d+)$', url)
    if not m:
        raise RuntimeError(f"could not parse Issue # from: {url}")
    issue_num = int(m.group(1))

    # Close if done
    if ns == 'done':
        time.sleep(SLEEP)
        run(['gh', 'issue', 'close', str(issue_num), '--reason', 'completed'])

    return issue_num, title, labels


def main():
    rows = parse_aidocs16()
    # dedupe rows by id (keep first occurrence)
    seen_ids = set()
    deduped = []
    for r in rows:
        if r['id'] in seen_ids:
            continue
        seen_ids.add(r['id'])
        deduped.append(r)
    rows = deduped

    log_lines = ["", "### Execution log (filled at runtime)", ""]
    log_lines.append("| aidocs ID | Decision | Issue # | Status | Milestone | Title (truncated) | Rationale |")
    log_lines.append("|---|---|---|---|---|---|---|")

    filed = 0
    skipped = 0
    closed = 0
    fail_count = 0
    started = time.time()

    rc1_count = rc2_count = backlog_count = 0

    for i, row in enumerate(rows):
        decision, rationale, milestone = classify(row)
        if decision == "SKIP":
            log_lines.append(f"| {row['id']} | SKIP | — | {row['status_raw'][:25]} | — | — | {rationale} |")
            skipped += 1
            continue

        ns = norm_status(row['status_raw'])
        ms_label = {MS_RC1: 'v6.0.0-rc.1', MS_RC2: 'v6.0.0-rc.2', MS_V6: 'v6.0.0', MS_BACKLOG: 'v6.x-backlog'}[milestone]
        ms_title = {
            MS_RC1: 'v6.0.0-rc.1 — post-MFFD-import bundle',
            MS_RC2: 'v6.0.0-rc.2 — substrate split + SHACL-1',
            MS_V6: 'v6.0.0 — stable',
            MS_BACKLOG: 'v6.x — backlog (no milestone yet)',
        }[milestone]

        try:
            issue_num, title, labels = create_issue(row, ns, ms_title, ms_label)
            closed_marker = "closed" if ns == 'done' else "open"
            if ns == 'done':
                closed += 1
            if milestone == MS_RC1:
                rc1_count += 1
            else:
                backlog_count += 1
            log_lines.append(f"| {row['id']} | FILE | #{issue_num} | {closed_marker} | {ms_label} | {title[:80]} | {rationale} |")
            filed += 1
            sys.stdout.write(f"[{filed:3d}] #{issue_num} ({closed_marker}) {row['id']} → {ms_label}\n")
            sys.stdout.flush()
            time.sleep(SLEEP)
        except RuntimeError as e:
            fail_count += 1
            log_lines.append(f"| {row['id']} | FILE-FAIL | — | error | {ms_label} | {build_title(row)[:80]} | {e!s} |")
            sys.stderr.write(f"FAIL at {row['id']}: {e}\n")
            if fail_count >= 3:
                sys.stderr.write("3 failures — pausing per task discipline.\n")
                break

    elapsed = time.time() - started
    summary = f"""

**Execution summary (real-time):**

- Walked rows: {len(rows)}
- FILE: {filed}
- SKIP: {skipped}
- FAIL: {fail_count}
- Closed (was `done`): {closed}
- rc.1: {rc1_count}; backlog: {backlog_count}; rc.2: {rc2_count}
- Wall clock: {elapsed:.1f}s
- Sleep between calls: {SLEEP}s

"""
    # Append to plan doc
    existing = LOG.read_text()
    LOG.write_text(existing + summary + "\n".join(log_lines) + "\n")
    print(summary)


if __name__ == "__main__":
    main()
