#!/usr/bin/env python3
# bootstrap-gh-project.py — idempotent bootstrapper for the shepard GitHub Projects v2 board.
#
# NOTE: docs/ops/github-projects-board-setup.md recommends manual setup (API churn,
# one-time work). This script is the automation alternative for operators who prefer
# a scripted, repeatable path. It produces the same board structure as the runbook.
#
# Usage:   python3 scripts/bootstrap-gh-project.py
# Prereq:  gh CLI authenticated (gh auth login). Requires project write on noheton org.
# chmod +x scripts/bootstrap-gh-project.py
#
# Relates to: GH-PM-ENH-Q5-1 (aidocs/strategy/85-github-project-management-policies.md §9)

import json, subprocess, sys

OWNER = "noheton"
TITLE = "shepard backlog (active)"
DESCRIPTION = (
    "Active work surface for github.com/noheton/shepard. "
    "The full backlog SSOT is aidocs/16-dispatcher-backlog.md; "
    "this board tracks issues currently in flight."
)

# Field definitions from task spec (Effort: XS/S/M/L per task; docs use S/M/L/XL).
FIELDS = [
    ("Severity", "SINGLE_SELECT", ["critical", "major", "minor", "nice-to-have"]),
    ("Area",     "SINGLE_SELECT", ["backend", "frontend", "plugins", "docs", "ops", "security"]),
    ("Effort",   "SINGLE_SELECT", ["XS", "S", "M", "L"]),  # task: XS/S/M/L; docs §9: S/M/L/XL
    ("Stage",    "TEXT",          []),
    ("Aidocs ID","TEXT",          []),
]

def gh(*args):
    """Run a gh CLI command and return parsed JSON output."""
    r = subprocess.run(["gh"] + list(args), capture_output=True, text=True)
    if r.returncode != 0:
        print(f"ERROR: gh {' '.join(args)}\n{r.stderr}", file=sys.stderr)
        sys.exit(1)
    return json.loads(r.stdout) if r.stdout.strip() else {}

print("Checking for existing project…")
projects = gh("project", "list", "--owner", OWNER, "--format", "json", "--limit", "100")
for p in projects.get("projects", []):
    if p.get("title") == TITLE:
        print(f"Project '{TITLE}' already exists (#{p['number']}). Nothing to do.")
        sys.exit(0)

print(f"Creating project '{TITLE}'…")
result = gh("project", "create", "--owner", OWNER, "--title", TITLE)
project_id = result.get("id") or result.get("projectId")
number = result.get("number")
print(f"  Created project #{number} (id={project_id})")

print("Adding custom fields…")
for name, ftype, options in FIELDS:
    args = ["project", "field-create", str(number), "--owner", OWNER,
            "--name", name, "--data-type", ftype]
    if ftype == "SINGLE_SELECT":
        args += ["--single-select-options", ",".join(options)]
    gh(*args)
    print(f"  Field '{name}' ({ftype}) created")

print(f"\nBoard ready: https://github.com/orgs/{OWNER}/projects/{number}")
print("Next: connect noheton/shepard via Settings > Manage access, then enable auto-add workflow.")
print("Saved views (Active sprint, Open bugs, External-facing, Plugin work, Security) must be")
print("created manually in the Projects UI — the API does not expose view creation cleanly.")
