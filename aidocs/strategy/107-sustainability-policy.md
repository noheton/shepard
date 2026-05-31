---
title: Sustainability policy — energy and CO₂ disclosure
stage: deployed
last-stage-change: 2026-05-31
audience: contributors, funding-body reviewers
---

# 107 — Sustainability policy: energy and CO₂ disclosure

## § Purpose

This policy exists for three reasons:

1. **EU and Helmholtz funding mandates.** EU Horizon Europe and Clean Aviation JU
   grant agreements increasingly require a Data Management Plan section on the
   digital carbon footprint of the toolchain used in the funded project. Helmholtz
   Association's sustainability strategy targets carbon-neutrality by 2035;
   deployments of Shepard at Helmholtz institutes are part of that accounting.
   This policy provides the language a PI or data steward needs to fill those sections
   honestly.

2. **Honest engineering.** Shepard is AI-assisted in its development — Claude Opus /
   Sonnet inference runs on Anthropic's infrastructure for every agent-driven commit.
   That inference energy is real. Tracking it per-commit is the same discipline we
   apply to code quality: measure, then improve. We do not pretend the energy is free
   because a provider holds a renewable energy certificate.

3. **Reproducibility of the energy claim.** Any third party should be able to follow
   the methodology, re-derive the headline numbers from the published log, and arrive
   at the same order-of-magnitude figure. If they cannot, our numbers are wrong.

---

## § What we track

**Per-commit energy log.**
`aidocs/sustainability/00-energy-estimation-log.md` is the append-only SSOT ledger.
Every substantive commit records four energy streams: LLM inference (Anthropic infra,
primarily AWS us-east-1), local build (dev box, DE grid), CI runner (GitHub Actions,
Azure East US), and production-runtime delta (Hetzner Falkenstein, DE grid).

**Methodology.**
`aidocs/sustainability/01-methodology.md` fixes the citation set, per-stream regional
carbon-intensity factors, per-commit heuristics, and the confidence-tag taxonomy
(HIGH / MEDIUM / LOW). It is the document anyone adding a log row must read first.

**Key discipline from the methodology:**

- Each energy stream uses the carbon intensity of its *physical region*, not a
  global average. AWS us-east-1 is ~290 gCO₂eq/kWh (PJM grid, Virginia); the
  DE grid is ~380 gCO₂eq/kWh (Umweltbundesamt Strommix 2024).
- Uncertainty bands are stated per row. The inference column carries ±50% because
  Anthropic does not publish per-token energy. Reporting the band is not optional —
  claiming a precise number without it would be wrong.
- Track the **gradient, not the absolute**. A session with 30 commits has roughly
  10× the inference energy of a session with 3 commits. That ratio survives the
  uncertainty; a single point estimate does not.

---

## § Claims we make and don't make

**We make these claims:**

- Shepard's AI-assisted development arc (2026-05-20 → 2026-05-23, measured) consumed
  approximately **547 Wh** of LLM inference energy, emitting approximately
  **203 gCO₂eq**, at the AWS us-east-1 conservative baseline (see §0.1 of the log
  for the full sensitivity panel).
- Over the full backfilled development history (214 commits, heuristic), total
  estimated energy is **~5 370 Wh** (~5.4 kWh), total estimated CO₂eq is **~2.0 kg**,
  with ±50–100% uncertainty on the heuristic rows.
- Prompt caching reduces the effective inference energy by approximately **2–3×**
  versus a naive per-token estimate. The 63% cache-read share in the current arc is
  the key finding: a provider-specific efficiency that a naive open-source estimate
  would miss.
- Production runtime (Hetzner Falkenstein, DE grid) adds ~380 gCO₂eq/kWh for
  continuous service operation; per-commit attribution is zero unless a commit
  measurably changes the steady-state draw.

**We do not make these claims:**

- **"Net zero development."** Anthropic's AWS infrastructure and Hetzner's German
  data centres hold renewable energy certificates (RECs). RECs represent annual
  matched renewables — they do **not** mean the marginal kilowatt-hour consumed
  at the moment of inference was carbon-free. We apply the DE-grid and PJM-grid
  intensities without the REC discount. This is the position stated in §7 of the
  methodology (the "honest companion" section) and it is non-negotiable.
- **Precise per-commit CO₂eq without a band.** Every log row carries an explicit
  confidence tag and band. A table that shows `9.8 gCO₂eq` with no uncertainty
  qualifier is the bug, not the feature.
- **Comparison to a "green AI" benchmark.** We are not claiming Shepard's AI use
  is low-carbon relative to alternatives. We are claiming we measure it honestly
  and do not hide it.

---

## § The honest numbers — at a glance

From `aidocs/sustainability/00-energy-estimation-log.md §0`:

| Metric | Value | Confidence |
|---|---|---|
| Current arc tokens (measured) | ~11.76 B | HIGH |
| Cache-read share | 63% | HIGH |
| Current arc inference energy | ~547 Wh | HIGH (±25%) |
| Current arc inference CO₂eq | ~203 gCO₂eq | HIGH (±25%) |
| Backfill total energy (214 commits) | ~5 370 Wh | LOW (±50–100%) |
| Backfill total CO₂eq | ~2.0 kgCO₂eq | LOW (±50–100%) |
| Mean per commit (heuristic) | ~25 Wh / ~9.1 gCO₂eq | LOW |
| Mean per commit (measured arc) | ~3.5 Wh | HIGH |

The ~7× spread between measured and heuristic per-commit averages **is exactly the
uncertainty the methodology claims**. Both numbers are defensible; the headline is
the gradient and the band, not a point estimate.

---

## § Follow-on work

These backlog rows in `aidocs/16-dispatcher-backlog.md` extend this policy:

- **SUST-BACKFILL1** — complete the heuristic backfill of the full git history.
- **SUST-TOKEN-LEDGER** — per-commit token-count capture from the Claude Code
  transcript to tighten the inference column from ±50% to ±25%.
- **SUST-SUBSTRATE-LIFT** — once `:Activity` carries `shepard:energyWh` and
  `shepard:co2eqGrams`, retire the markdown log and replace it with a Cypher query
  over the provenance graph. The methodology doc stays as the human-readable companion.
- **SUST-CO2-RECOMPUTE** — recompute the backfill rows against the per-stream
  regional methodology (v1) once the TOKEN-LEDGER is available.
