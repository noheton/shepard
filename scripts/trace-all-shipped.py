#!/usr/bin/env python3
# trace-all-shipped.py — run trace-feature.sh over every shipped/done aidocs/16 row.
#
# Writes results to aidocs/agent-findings/traceability-audit-YYYY-MM-DD.md.
# Usage: python3 scripts/trace-all-shipped.py
# Relates to: GH-PM-ENH-Q3-1 (aidocs/16-dispatcher-backlog.md)

import subprocess, pathlib, datetime, sys

repo = pathlib.Path(__file__).resolve().parent.parent
backlog = repo / "aidocs" / "16-dispatcher-backlog.md"
trace_sh = repo / "scripts" / "trace-feature.sh"
today = datetime.date.today().isoformat()
out_path = repo / "aidocs" / "agent-findings" / f"traceability-audit-{today}.md"

ids = [
    cols[1].strip()
    for line in backlog.read_text().splitlines()
    if line.startswith("|")
    for cols in [[c for c in line.split("|")]]
    if len(cols) >= 7
    and any(s in cols[5] for s in ("done", "shipped"))
    and cols[1].strip() not in ("ID", "")
]

sections = [f"# Traceability audit — {today}\n\nShipped rows found: {len(ids)}\n"]
for fid in ids:
    result = subprocess.run(["bash", str(trace_sh), fid], capture_output=True, text=True)
    sections.append(f"\n## {fid}\n\n```\n{result.stdout.strip()}\n```\n")

out_path.write_text("\n".join(sections))
print(f"Written: {out_path}")
