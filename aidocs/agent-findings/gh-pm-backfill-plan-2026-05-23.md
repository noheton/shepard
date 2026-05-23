---
stage: deployed
last-stage-change: 2026-05-23
generator: scripts/gh-pm-backfill-execute.py (one-shot, 2026-05-23)
companion: aidocs/agent-findings/gh-pm-adoption-synthesis-2026-05-23.md
---

# GH-PM5 backfill — plan + execution log (2026-05-23)

> 🤖 **BACKFILL artefact** — this entire document is the audit trail of
> a synthetic-but-disclosed backfill operation. The Issues + Milestones
> referenced here were created retroactively as part of GH-PM5 adoption.
> Per `feedback_no_synthetic_provenance.md` in agent memory: retroactive
> artefacts MUST carry per-artefact transparency markers. The plan IS the
> generation-rule artefact; the execution log IS the dataset. The
> snapshot chain (plan → milestones → issues → closed-or-open) IS the
> provenance.

## Summary

| Metric | Value |
|---|---|
| Walked aidocs/16 rows | 524 |
| FILE rows (gate-passed) | see §3 tally |
| SKIP rows (gate-failed) | see §3 tally |
| Milestones created | 4 (`v6.0.0-rc.1`, `v6.0.0-rc.2`, `v6.0.0`, `v6.x backlog`) |
| Source persona findings | 5 |
| Execution mode | clearly-synthetic-artefact under transparency markers — no per-Issue human approval |

## §1 — The 4-gate filter (from `aidocs/strategy/85 §3`)

File an Issue ONLY when at least one of these gates is true:

1. **External-contributor-visible** — work an outside contributor could
   pick up; needs public-by-default + threaded discussion.
2. **Security disclosure** — file privately via security-finding
   template. (Backfill: security-flavoured shipped work is filed PUBLIC
   under marker since the CVE has shipped; no embargo at backfill time.)
3. **Bug with clear repro** — customer-facing defect that benefits from
   public threaded discussion + `Closes #N` linkage.
4. **In-flight agent execution** — agent is currently dispatched against
   the row; matching Issue is the public in-flight ledger.

**Backfill heuristics applied per row:**

- **FILE** if the row ships:
  - New admin-visible endpoint or feature toggle
  - New plugin module or plugin SPI
  - New REST resource shelf (`/v2/...`)
  - User-visible UI feature (researcher workflow change)
  - Security fix that shipped
  - New ontology / SHACL / semantic surface
  - Migration script (operator-visible)
  - New CLI command
- **SKIP** if the row is:
  - Pure internal refactor with no admin/user-visible surface
  - Pure docs work already covered by GH-PM1
  - `parked` / `superseded` / `decommissioned`
  - Status not advanced beyond `concept` / `idea` (no shippable artefact)
  - Pure design-doc work without code change (status: `design done` only)

## §2 — Milestone routing

| Milestone | Range | Bundles |
|---|---|---|
| `v6.0.0-rc.1 — post-MFFD-import bundle` | done rows shipped 2026-05-01 → 2026-05-23 | Garage S3 (FS1b/d/i), smart warmup (IMPORT-W*), PROV1*, BIB-1, ORIGIN-1, DOC-STAGE1, GH-PM1, V1COMPAT.0, MFFD-* |
| `v6.0.0-rc.2 — substrate split + SHACL-1` | (forward-looking) | substrate-split rows, SHACL-1, post-rc.1 queued items |
| `v6.0.0 — stable` | (no synthetic mark; forward-looking) | rc.1 + rc.2 cumulative |
| `v6.x — backlog (no milestone yet)` | done rows pre-2026-05-01 + all queued + blocked | Everything else under §3 FILE classification |

**Note on rc.2:** rc.2 carries the BACKFILL marker on its description
because the milestone itself was created retroactively today; the
backlog assignment of rows to it is administrative until those rows
ship.

## §3 — Per-row classification + execution log

Execution log is appended to this document in §4 by
`scripts/gh-pm-backfill-execute.py` at runtime. Each entry:

```
<aidocs-id>  <decision>  <issue-#-or-skip-reason>  <milestone>  <closed-or-open>
```

See §4 below for the actual log.

## §4 — Execution log (filled at runtime by the script)

See bottom of this document.

## §5 — Two-step refinement (load-bearing for the case study)

This backfill is the first concrete instance of the
`feedback_no_synthetic_provenance.md` refinement:

- **Beat 1.** AI proposed mass-backfill of Issues from aidocs/16.
- **Beat 2.** User flagged forgery — backfilled Issues without
  disclosure are wire-shape-identical to real-time Issues; future
  readers cannot distinguish them.
- **Beat 3.** Joint refinement: backfill IS allowed, but every
  artefact carries an in-body transparency marker. The marker IS the
  gate; per-Issue human approval is replaced by per-Issue per-artefact
  disclosure.

**This artefact is the proof of refinement.** A future reader scanning
the Issues tab finds `🤖 BACKFILL` in every retroactive Issue body and
knows the work shipped before the Issue existed. The chain is honest.

## §6 — Anti-pattern

Per `aidocs/strategy/85 §15 #7`:

> **Filing backfilled Issues without a BACKFILL disclosure marker —
> silent forgery of the audit trail.**

This document is the discipline-of-process artefact for that
anti-pattern. Every Issue filed in §4 carries the marker.

## §7 — Companion files

- Synthesis: `aidocs/agent-findings/gh-pm-adoption-synthesis-2026-05-23.md`
- Policy (deployed): `aidocs/strategy/85-github-project-management-policies.md`
- Backlog: `aidocs/16-dispatcher-backlog.md`
- Admin ledger: `aidocs/34-upstream-upgrade-path.md` — `GH-PM-ADOPT` row
- Memory: `/root/.claude/projects/-opt-shepard/memory/feedback_no_synthetic_provenance.md`

---

## §4 (continued) — Execution log

(Appended by `scripts/gh-pm-backfill-execute.py` on 2026-05-23.)

