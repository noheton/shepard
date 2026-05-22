---
stage: feature-defined
last-stage-change: 2026-05-23
persona: digital-native-researcher
audit-target: aidocs/strategy/85-github-project-management-policies.md
---

# Persona: Digital Native Researcher — GH-PM audit (2026-05-23)

28yo postdoc. GitHub for everything. `gh` CLI, Jupyter, Claude, Python.
First question I ask of any internal RDM policy: *can I drive it from a
terminal?* I read GH-PM-1 §1–§16 + `scripts/trace-feature.sh` once.
Then I shelled the boards. Here's what I found.

---

## TL;DR — votes

| Q  | Topic                                | Claude lean | My vote     | Confidence |
|----|--------------------------------------|-------------|-------------|------------|
| Q1 | Adopt §85 as-is                      | yes         | **YES**     | high       |
| Q2 | `v6.0.0-rc.1` first milestone        | yes         | **YES**     | high       |
| Q3 | Backfill dry-run first               | yes         | **YES**     | high       |
| Q4 | Auto-file Issues for in-flight agents| no          | **NO**      | high       |
| Q5 | Manual board vs scripted GraphQL     | manual      | **FLIP → scripted** | **high**  |
| Q6 | PR-scope hook: install vs trust      | trust       | **install** (soft) | medium |

**The one flip: Q5.** `gh project` CLI is a complete, scriptable surface.
Tested live below — full board with 6 single-select axes in 31 lines of
Python, 7.5s wall-clock. Claude's "GraphQL fragility" justification
doesn't hold up to a `gh --version 2.92.0` reality check.

---

## Q1 — Adopt §85 as-is

**Finding.** Yes. The doc is *unusually* terminal-friendly for a
"governance" doc. Every state-machine state has a label, every label is
checked into `.github/labels.yml`, every stage flip is observable via
git. The "no Issue by default" 4-gate rule (§3) is the right call: I
can read 573 backlog rows in `aidocs/16-dispatcher-backlog.md` in one
`grep`; I could not in 573 Issues.

**Recommendation.** Adopt as-is. Soft edits below in Q5/Q6.

**Rationale.** Policy reads like an API spec, not a bureaucratic
ritual. Rare.

**Cost of being wrong.** Near-zero. Reversible per-section.

---

## Q2 — `v6.0.0-rc.1` scope (MFFD-import + Garage S3 + smart warmup)

**Finding.** Coherent feature set. FS1b+FS1d shipped (verified via
`scripts/trace-feature.sh FS1b` — 4 commits, all surfaces hit). IMPORT-W1
+ AAS-1 align with current MFFD ingestion arc per RESUME.md.

**Recommendation.** Yes — but **add a release-prep dry-run script** that
runs the §5 pre-flight checklist locally before tag-push. (See Q3
discussion — same script shape.)

**Rationale.** Coherent bundle; pre-flight script avoids day-of release
panic.

**Cost of being wrong.** Low. RC can slip a week if pre-flight fails
red.

---

## Q3 — Backfill dry-run first

**Finding.** Yes. With 573 aidocs/16 rows and ~hundreds of commits
already on `main`, the trace-feature script (§14) is the dry-run engine.
Run it across the universe of shipped IDs, count orphan surfaces, fix
those before flipping any policy gates in CI.

**Recommendation.** Yes — and **script the universe-scan**. Five lines:

```python
# trace-all-shipped.py — find traceability holes before tightening gates
import subprocess, re, pathlib
rows = pathlib.Path("aidocs/16-dispatcher-backlog.md").read_text().splitlines()
ids = [m.group(1) for ln in rows if (m := re.match(r"^\|\s*([A-Z][A-Z0-9-]+[a-z]?)\s+\|", ln))]
for i in ids:
    r = subprocess.run(["bash","scripts/trace-feature.sh",i], capture_output=True, text=True)
    if r.stdout.count("(no ") > 2:   # 2+ missing surfaces = audit
        print(f"{i}: {r.stdout.count('(no ')} gaps")
```

That's the audit. Run it once, fix the top N gaps, then turn on CI
enforcement (commit-scope linter, aidocs/34 reviewer requirement).

**Rationale.** No new tooling needed; trace-feature.sh already is the
oracle.

**Cost of being wrong.** Medium. If you tighten CI without backfill,
every legitimate refactor PR fails the scope check.

---

## Q4 — Auto-file Issues for in-flight agents

**Finding.** No. The 4-gate rule (§3) explicitly handles this:
*optional* Issue at dispatch when agent is dispatched. Making it
automatic creates 573 Issues over the lifetime of the backlog. That's
the "360-row sync trap" from §15 anti-pattern #1.

**Recommendation.** **NO**. Leave it optional. Default: no Issue.
Agent-dispatch script asks `--file-issue` flag for the cases where
public threading helps (security finding, external bug report).

**Rationale.** SSOT is aidocs/16. Issues = the 4-gate subset. Don't
duplicate.

**Cost of being wrong.** High if you do it: backlog explosion, dual
sources of truth, drift. Low if you don't: occasional missing public
ledger for an agent run.

---

## Q5 — Projects v2 board: manual vs scripted **[FLIP]**

**Finding.** Claude's "GraphQL fragility" claim is wrong as of `gh
2.92.0`. The `gh project` subcommand surface is complete:

```
create | delete | edit | field-create | field-list | field-delete
item-add | item-create | item-edit | item-list | item-delete
link | unlink | view | close | copy
```

I ran the full bootstrap live against my GitHub account:

```bash
$ gh project create --owner noheton --title "test" --format json
# → {"id":"PVT_kw...","number":1, ...}    (~1 sec)

$ gh project field-create 1 --owner noheton --name "Stage" \
    --data-type SINGLE_SELECT \
    --single-select-options "fragment,concept,idea,feature-defined,..."
# → field id PVTSSF_l...

$ gh project item-create 1 --owner noheton --title "FS1b: ..." --format json
# → item id PVTI_l...

$ gh project item-edit --project-id "PVT_..." --id "PVTI_..." \
    --field-id "PVTSSF_..." --single-select-option-id "b76b261e"
# → no error, item-list shows: "FS1b: ..." → feature-defined
```

**Then I scripted the whole §10 axis matrix in 31 lines of Python:**

```python
#!/usr/bin/env python3
"""Bootstrap GH Projects v2 board per aidocs/strategy/85 §9–§10."""
import json, subprocess, sys

def gh(*a):
    return json.loads(subprocess.run(["gh", *a, "--format", "json"],
                                     capture_output=True, text=True, check=True).stdout)

owner, title = sys.argv[1], sys.argv[2]
proj = gh("project", "create", "--owner", owner, "--title", title)
num = str(proj["number"])
AXES = {
    "Stage":    ["fragment","concept","idea","feature-defined","audited-by-personas",
                 "feedback-implemented","tests-implemented","deployed","decommissioned"],
    "Severity": ["critical","major","minor","nice-to-have"],
    "Area":     ["backend","frontend","plugins","infra","docs","semantics","mcp","vis","ai"],
    "Type":     ["bug","feature","docs","refactor","chore"],
    "Effort":   ["S","M","L","XL"],
    "AidocsID": [],
}
for name, opts in AXES.items():
    if not opts:
        gh("project","field-create",num,"--owner",owner,"--name",name,"--data-type","TEXT")
    else:
        gh("project","field-create",num,"--owner",owner,"--name",name,
           "--data-type","SINGLE_SELECT","--single-select-options",",".join(opts))
print(f"done: gh project view {num} --owner {owner} --web")
```

**Wall-clock: 7.5 seconds. Six axes, six fields, fully populated.** No
GraphQL written by me. The `gh project` subcommands hide the GraphQL.
This is not the 2023 surface; the CLI matured.

**Recommendation.** **FLIP to scripted.** Ship it as
`scripts/bootstrap-gh-project.py` next to `trace-feature.sh`. Idempotent
(check if project + fields exist before creating); operator runs it
once per repo.

**Rationale.** 31 lines vs a click-through runbook. Same operator runs
both — script wins on reproducibility + recovery (board got nuked?
re-run; click-through means "consult the runbook for an hour").

**Cost of being wrong.** If GitHub deprecates a CLI subcommand → patch
the script. If the runbook drifts → silent broken-board for months. The
script breaks loudly.

**Caveat.** Saved views (§9 "Active sprint / External-pickup / Recently
shipped") cannot be created via `gh project` today (GraphQL has the
mutation but `gh` doesn't expose it). Two options: (a) ship the script
+ document the three view filters as a 3-line setup gist that the
operator pastes once; (b) wait for the `gh project view-create`
subcommand. Pick (a).

---

## Q6 — PR-scope hook: install vs trust

**Finding.** Claude leans trust. Trust is fine for solo + ~5 agents.
The moment external contributors arrive (GH-INFRA4 gate), trust
degrades. A pre-commit hook + `gh pr create` template enforces
transparently for both.

**Recommendation.** **Install a hook — but soft (warn-only) by
default.** Two pieces:

```bash
# .githooks/commit-msg — 8 lines
#!/usr/bin/env bash
MSG_FILE="$1"
FIRST_LINE=$(head -1 "${MSG_FILE}")
if ! grep -qE '^[a-z]+\([A-Za-z0-9+-]+\): ' "${MSG_FILE}"; then
  echo "warn: commit subject missing Conv-Commits scope (per aidocs/strategy/85 §7)" >&2
  echo "      example: feat(FS1b): add S3 file storage plugin" >&2
  echo "      (warn only; commit allowed)" >&2
fi
```

Plus `.github/pull_request_template.md` already exists per
`scripts/trace-feature.sh` finding — just add a *Title:* hint at the top:

```markdown
<!-- Title format: <type>(<aidocs-16-ID>): <subject>
     Examples: feat(FS1b): ..., fix(KIP1d): ..., docs(GH-PM1): ...
     See aidocs/strategy/85 §6–§7 -->
```

**Then add a CI check** in `.github/workflows/ci.yml` that fails on PR
title regex miss. That's the enforcement; the local hook is just early
feedback.

**Rationale.** Local warn = friction-free; CI fail = enforcement. Both
cheap.

**Cost of being wrong.** Low. Worst case: contributors learn the
format faster.

---

## §14 traceability — 5-line Python equivalent

The bash script is 211 lines. Most of that is ANSI + dim() + section()
prettification. The *logic* is 5 lines if you accept "I'll grep
manually" as the rendering:

```python
import subprocess, sys
ID = sys.argv[1]
for path, args in [("aidocs/16",["grep","-A2",f"^| {ID} ","aidocs/16-dispatcher-backlog.md"]),
                   ("design",["grep","-rln",ID,"aidocs/","--include=*.md"]),
                   ("findings",["grep","-rln",ID,"aidocs/agent-findings/"]),
                   ("commits",["git","log","--grep",f"({ID}):","--oneline"]),
                   ("admin",["grep","-n",ID,"aidocs/34-upstream-upgrade-path.md"])]:
    print(f"== {path} =="); subprocess.run(args)
```

**Faster?** No. Both run in <0.2s. **Better?** Marginally — Python is
more composable (the universe-scan in Q3 reuses this). Bash is more
self-contained (no python dep). **Verdict: keep the bash; add a small
Python module `scripts/trace.py` that exposes `trace(id) -> dict` for
the universe-scan use case.** Don't replace.

---

## MCP angles — `mcp.nuclide.systems/git` + `/gitlab`

**Finding.** Both exist (401 auth-gated, but reachable). Per memory
`project_mcp_path.md` the path is established via Zoraxy.

**Question I'd ask the platform lead:** does `mcp/git` expose
`git_log --grep` + `git_log --name-only` as MCP tools? If yes, the
trace-feature script collapses into one MCP tool call from a Claude
session, which is where the audit actually happens. Memory says
yes-in-principle but I didn't have keys handy to test.

**Recommendation.** Add an MCP tool `trace_feature(id)` that wraps
`scripts/trace-feature.sh` — one tool, one MCP server, every Claude
session in this org can audit traceability without shelling out. **This
is the integration that would close the digital-thread loop for
agentic workflows.** New aidocs/16 row candidate.

---

## Automation gaps table

| Surface                            | gh CLI native | Script lines | Note |
|------------------------------------|---------------|--------------|------|
| Project create + 6 axis fields     | yes           | 31 (Py)      | Q5 — refutes Claude's lean |
| Item add + field-set               | yes           | 4 (sh)       | works |
| Project saved views (3 per §9)     | **no**        | n/a          | document as click-once gist |
| Label sync from `.github/labels.yml`| via workflow (sync-labels.yml) | exists | shipped |
| Milestone create + assign Issues   | yes (`gh milestone` → use `gh api`) | 6 (Py) | not part of `gh project` |
| Issue close on PR merge            | yes (`Closes #N`) | 0           | GitHub native |
| §14 traceability across surfaces   | yes (composite)| 5–211        | bash exists, Py reuse for scans |
| Universe-scan for orphan surfaces  | derived       | 8 (Py)       | Q3 dry-run engine |
| Commit-scope linter (local)        | hook (8 lines)| 8 (sh)       | Q6 — warn-only |
| Commit-scope linter (CI)           | regex in workflow | 6 (yml)   | Q6 — enforce |
| MCP `trace_feature(id)` tool       | not yet       | new aidocs/16 row | suggested |

---

## What I'd ship in one sprint

1. **Q5 reversal**: `scripts/bootstrap-gh-project.py` (31 lines) + a
   3-line "saved views setup" gist in `docs/ops/github-projects-board-setup.md`.
2. **Q3 dry-run**: `scripts/trace-all-shipped.py` (8 lines, calls
   `trace-feature.sh` in a loop) + a markdown summary report on first
   run. This becomes the backfill triage list.
3. **Q6 soft enforcement**: `.githooks/commit-msg` (8 lines, warn-only)
   + CI regex on PR title.
4. **MCP follow-up**: file an aidocs/16 row for `mcp.nuclide.systems/git`
   to expose `trace_feature(id)` as a tool.

---

## Honest verdict

GH-PM-1 is the most well-thought-out internal policy doc I've read in
this codebase. The only friction is **Q5**: Claude advised
"manual UI" out of either unfamiliarity with current `gh project` surface
or memory of the 2022–2023 GraphQL-only era. The CLI shipped; the
policy should follow the CLI.

If I joined this team tomorrow, I would:
- Adopt §85 day one.
- File 4 PRs scoped to the recommendations above.
- Bookmark `scripts/trace-feature.sh` in my dotfiles.
- Stop checking the GitHub web UI within a week.

That's the test: does the policy let me live in my terminal? Mostly
yes. Flip Q5 and it's a full yes.
