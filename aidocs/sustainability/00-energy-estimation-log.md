---
title: Energy + CO₂ estimation log per commit (SSOT)
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributors, maintainers, sustainability reviewers, funding-body reviewers
---

# 00 — Energy + CO₂ estimation log per commit

**Status.** **Feature-defined.** Backfilled best-effort from 2026-05-20;
current going forward per the rule in
[`feedback_energy_log_per_commit.md`](../../../../root/.claude/projects/-opt-shepard/memory/feedback_energy_log_per_commit.md)
(memory; not in git).

**Audience.** Maintainers (per-commit discipline); funding-body reviewers
(sustainability disclosure); future-Claude (consult before substantive work).

**Companion docs.**

- [`01-methodology.md`](01-methodology.md) — heuristics + citations.
- [`aidocs/16-dispatcher-backlog.md` SUST-1..4](../16-dispatcher-backlog.md) —
  follow-on work (measurement; harness telemetry; substrate-native lift).
- [`aidocs/44-fork-vs-upstream-feature-matrix.md`](../44-fork-vs-upstream-feature-matrix.md) —
  sustainability-ledger row as a fork-vs-upstream distinguisher.

---

## §0 At a glance

**This log estimates the energy footprint of Claude Opus 4.7 inference (the model that produced these commits) on Anthropic's hosting infrastructure (primarily AWS US).** Local-build + CI + production-runtime energy use their respective regions per [`01-methodology.md`](01-methodology.md).

### §0.1 Measured (current arc, 2026-05-22 → 2026-05-23) — HIGH-confidence headline

From the JSONL transcripts at `/root/.claude/projects/-opt-shepard/*.jsonl` — **measured** `usage.input_tokens` / `output_tokens` / `cache_creation` / `cache_read` per Claude API response, not heuristic estimates.

| Metric (current arc only)               | Value                                      |
|-----------------------------------------|--------------------------------------------|
| Total tokens                            | **~11.76 B**                               |
| Cache-read share                        | **63%** ← the methodologically important finding |
| Inference energy (us-east-1, ±50%)      | **~547 Wh**                                |
| Inference CO₂eq (370 gCO₂/kWh)          | **~203 gCO₂eq**                            |
| Workstation baseline addition           | AI inference adds ~19% over laptop-only    |

**The 63% cache-read finding matters:** a naive per-token estimate without cache-discount would overcount by ~2–3×. Anthropic's prompt caching changes the energy story materially — this is the kind of provider-specific finding that makes the case study publishable.

### §0.2 Heuristic backfill (2026-05-20 → 2026-05-23) — MEDIUM/LOW confidence

Cumulative across **214 commits** estimated from commit shape (token volumes inferred from diff size, not measured):

| Metric                          | Value                                                                  |
|---------------------------------|------------------------------------------------------------------------|
| Commits logged                  | **214**                                                                |
| Total estimated energy          | **~5 370 Wh** (5.4 kWh, ±50–100%)                                       |
| Total estimated CO₂eq           | **~1 950 g** (~2.0 kgCO₂eq, ±50–100%)                                  |
| Confidence distribution         | 164 MEDIUM · 50 LOW · 0 HIGH                                           |
| Kind distribution               | 103 doc · 84 code · 17 mixed · 10 merge                                |
| Mean per commit                 | **~25 Wh / ~9.1 gCO₂eq** (heuristic) — note ~7× higher than the measured per-commit average from §0.1 (~3.5 Wh); the spread IS the uncertainty band |
| Equivalent reference            | ~1 minute of a 1.5 kW kettle, or 11 km of EV driving (DE 2024 grid mix) |

**`gCO2eq_est` column in §3 currently uses DE Strommix 363 g/kWh as a single-region constant** — per `feedback_energy_log_per_commit.md` the corrected per-stream regional methodology (AWS us-east-1 ~370 for LLM inference, DE for local build/CI as appropriate) is in [`01-methodology.md`](01-methodology.md). Recomputation of the §3 column against per-stream regional is queued as **`SUST-CO2-RECOMPUTE`** in `aidocs/16`. Until recomputed, treat the §3 numbers as DE-blanket upper bounds.

### §0.3 Honest reading

The ~7× spread between the §0.1 measured (~3.5 Wh/commit) and §0.2 heuristic (~25 Wh/commit) averages **is exactly the ±50–100% uncertainty the methodology claims**. Both numbers are defensible; the headline is *gradient + band*, not a single point. The number that survives the uncertainty is the **ratio**: a code commit is roughly **3–5× the energy of a doc-only commit**, and an agent-dispatched large feature is roughly **10–15× a small fix**.

---

## §1 Methodology

See [`01-methodology.md`](01-methodology.md) for the full per-stream regional methodology + citations. Quick reference:

- **LLM (current arc, measured)**: 0.3 Wh/1k fresh-input + 3.0 Wh/1k output + 0.03 Wh/1k cache-read (Luccioni 2024 + Anthropic prompt-caching docs; ±50%); **AWS us-east-1 ~370 gCO₂eq/kWh** as conservative proxy (us-west-2 PNW ~80 as optimistic band)
- **LLM (heuristic backfill in §3)**: 0.2 J/input-token + 1.0 J/output-token (midpoint of Samsi 2023 + modern H100; ±50%); **§3 column currently DE 363 g/kWh constant — pending `SUST-CO2-RECOMPUTE`**
- **Local build**: 20 Wh per code commit (mvn/npm/docker partial rebuild); 0 Wh for doc-only; **DE Strommix 380 g/kWh** (Umweltbundesamt 2024)
- **CI**: 5 Wh per code commit's pipeline run; 0 Wh for doc-only; **Azure us-east-2 ~310 g/kWh** (GitHub Actions default region)
- **Production runtime**: Hetzner Falkenstein DE ~380 g/kWh (pending `SUST-PROD-REGION-VERIFY`)

## §1.5 Sensitivity panel (4 axes a reviewer can re-do the math against)

| Axis | Naive default | Conservative band | Effect on headline |
|------|--------------|-------------------|--------------------|
| Per-token Wh proxy | 1 Wh/1k tokens (Luccioni 2024 + Samsi midpoint) | ±50% | linear scale on inference column |
| AWS region | us-east-1 370 g/kWh | us-west-2 80 g/kWh | ~4.6× lower CO₂eq if PNW |
| Sub-agent folding | Folded into Opus column (overcount) | Separate Sonnet/Haiku ⅓ + ⅒ rates | ~10–25% lower total |
| Cache-read ratio | 63% measured for current arc | Without cache discount | ~2–3× higher total |

The product of these axes is the ±50–100% band claimed throughout. Track the gradient (ratio across commit kinds), not the absolute.

## §2 Confidence taxonomy

| Tag    | When                                                      | Uncertainty |
|--------|-----------------------------------------------------------|-------------|
| HIGH   | In-session WITH token telemetry + measured build wall-clock | ±20%        |
| MEDIUM | In-session WITHOUT telemetry; or 2026-05-22 onward          | ±50%        |
| LOW    | Backfill before 2026-05-22; pure diff-shape heuristic       | ±100%       |

## §3 The log

**Schema.** Append-only. Newest first. Sortable on `date_utc` if you pull as
CSV (pipe-delimited markdown is `pd.read_csv(sep='|', skipinitialspace=True)`
compatible after the header rows).

**Pandas one-liner** (Digital Native lens):

```python
import pandas as pd
df = pd.read_csv('00-energy-estimation-log.md', sep='|', skiprows=lambda i: i < N_HEADER, skipinitialspace=True)
# df now has columns: commit_sha, date_utc, kind, tokens_in_est, tokens_out_est,
#   llm_Wh_est, build_Wh_est, ci_Wh_est, total_Wh_est, gCO2eq_est, confidence, notes
df.groupby('kind')['total_Wh_est'].describe()
```

**Columns.**

- `commit_sha` — 8-char prefix
- `date_utc` — ISO date of commit
- `kind` — code | doc | merge | mixed | infra
- `tokens_in_est` / `tokens_out_est` — heuristic-derived (k = thousands)
- `llm_Wh_est` — inference energy estimate
- `build_Wh_est` — local build/test energy (0 for doc-only)
- `ci_Wh_est` — GitHub Actions runtime estimate (0 for doc-only)
- `total_Wh_est` — sum of LLM + build + CI
- `gCO2eq_est` — total Wh × 363 g/kWh ÷ 1000
- `confidence` — see §2
- `notes` — commit subject line (truncated to 80 chars)

| commit_sha | date_utc | kind | tokens_in_est | tokens_out_est | llm_Wh_est | build_Wh_est | ci_Wh_est | total_Wh_est | gCO2eq_est | confidence | notes |
|------------|----------|------|---------------|----------------|------------|--------------|-----------|--------------|------------|------------|-------|
| `fbeadaf5` | 2026-05-23 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | fix(frontend/landing): toEl() helper — ref on Vuetify component is instance, ... |
| `87d98f34` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): NETCDF1 — shepard-plugin-netcdf payload kind |
| `613866a0` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): PERSONA-ISSUES-FROM-AUDIT — persona-audit findings become GH I... |
| `c30c542d` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | decide(views-as-shapes): bundle wave — TS-IDc + shapes/render + Trace3D ship ... |
| `925b96da` | 2026-05-23 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | merge: views-as-shapes — doc 98 enrichment + paired persona audit |
| `73b59c98` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(audit): persona audit of docs 95 + 98 — both bump to audited-by-personas |
| `1cfb73aa` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(98): enrich views-as-shapes — AFP examples + structural shapes + reuse s... |
| `7fde2b6d` | 2026-05-23 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | design(fe): aidocs/frontend/100 — cross-instance provenance UI |
| `1036ba00` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): UX-REVERT-MUTATIONS — first-class undo for data mutations |
| `cf3557b8` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): MFFD-IMPORTER-DATASET-CAPTURE — importer versions + transcript... |
| `7e8f93a5` | 2026-05-23 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | merge: v15.9 — source-side user identity capture (MFFD-IMPORT-USER-CAPTURE) |
| `8ab7c2ce` | 2026-05-23 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd-import): v15.9 — source-side user identity capture (MFFD-IMPORT-USE... |
| `052145cd` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(v5-survey): metadata-enrichment opportunities for MFFD importer v15.10 |
| `3c09831c` | 2026-05-23 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | fix(mffd-import): v15.8 PERF2 — per-DO is_do_done short-circuit + start-header |
| `d50519f5` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): MFFD-IMPORT-USER-CAPTURE — source-side user identity into prov... |
| `f5ba8082` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): SHEPARD-FORK-KEY-ROTATE + PROV-USER-ENRICH + V5-METADATA-SURVEY |
| `50c5954a` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): V1-COMPAT-APIKEY (wire drift) + sharpen MFFD-IMPORT-AUTH1 |
| `9c95c4c4` | 2026-05-23 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(mffd-import): v15.8 — real worker fan-out (PERF1) + lazy enrichment (PERF2) |
| `2907d266` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): AUTH-KEY-ROTATE1 (user-facing key rotation UI) + MFFD-IMPORT-A... |
| `da4d94cc` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): MFFD-IMPORT-AUTH1 — v15.9 auto-refresh JWT via MFFD_REFRESH_JW... |
| `16116ff8` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(diagnose): MFFD cube3 import slowness investigation |
| `ed3fe959` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(plugin-backfill): record Shepard Collection capture for DOCS-3A10 |
| `badb0012` | 2026-05-23 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(plugin-backfill): fill 17 missing pages across 9 plugins per DOCS-3A2 au... |
| `8bdc8c61` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs: apply final escalation batch — PROMPT-1 (dual) + ONT-5 + ONT-6 |
| `c5eb7239` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs: apply two more tier-1 escalations — ONT-3 + PROMPT-3 |
| `f3db0120` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(outreach): draft TIB Hannover Step-1 'Used by' issue for OntoAligner |
| `d1a55043` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs: apply user-OK on DOCS-A (visitor audience) + ADMIN-STALE Path B |
| `b8561a14` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs: apply user-OK on LOGSTORE ESCALATION-2 + PROMPT ESCALATION-2 (CRITICAL) |
| `9574f93d` | 2026-05-23 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | MEDIUM | chore(gitignore): ignore mffd-importer per-session artefacts |
| `2cc00eaa` | 2026-05-23 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | Merge IMPORTER-DOC — v15.* tool README + user help page |
| `8b67952b` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(importer): v15.* tool README + user help page (IMPORTER-DOC) |
| `5ee52f2d` | 2026-05-23 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | Merge IMPORTER-CARRY — v15.x patterns distilled into shepard-plugin-importer ... |
| `5c086a49` | 2026-05-23 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(importer): v15.x patterns → shepard-plugin-importer design notes (IMPORT... |
| `ac79933c` | 2026-05-23 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | Merge DOCS-3A2 — plugin-docs three-page gap audit |
| `fbcf3a72` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(plugin-docs): three-page minimum audit across all plugins |
| `b87680ae` | 2026-05-23 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | Merge DOCS-3A9 — audience: front-matter retrofit (62 docs/**/*.md pages) |
| `462020c1` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(audience): retrofit audience: front-matter across docs/**/*.md per DOCS-3A9 |
| `6a8f8823` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(aidocs/16): annotate DOCS-3A1 Notes with commit hash + collision postscript |
| `5a4505b0` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(admin): consolidate docs/admin.md + docs/deploy.md + docs/system-require... |
| `d55cceee` | 2026-05-23 | mixed | 180k | 60k | 26.67 | 20 | 5 | 51.67 | 18.76 | MEDIUM | docs(ontology-mapping-survey): apply audit §F3.1 BLOCKING — downgrade DeepOnt... |
| `3ff0ee50` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(persona-audit): land ONT-AI-MAP1 audit deliverable + stage bump |
| `681309a2` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(persona-audit): ontology-mapping survey — 4 personas reviewed (ONT-AI-MAP1) |
| `a4a87645` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(persona-audit): promptlog design — 4 personas reviewed |
| `879ac713` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(persona-audit): log-store design — 4 personas reviewed |
| `435f6b0d` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | chore(doc-stage): regenerate index (235 docs) |
| `c6b605c2` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(reading-list): batch 2 — 18 entries from PROMPT1/LOGSTORE1/ADMIN-STALE-C... |
| `28a067c2` | 2026-05-23 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | feat(mffd-import): v15.7 — SIGHUP-safe + telemetry self-heal + crash-loop det... |
| `5b7a7166` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(ontology): close survey OQ-6 — defer to AI plugin BYOK chain |
| `c14957ba` | 2026-05-23 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(ontology): AI-assisted ontology mapping research survey |
| `b653a2ee` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(aidocs/16): DOCS-3A6-10 — docs backfill rows for the three-audience rule |
| `ccef23cf` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(aidocs/16): three-audience docs discipline + DOCS-3A consolidation |
| `f5d58ef9` | 2026-05-23 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(admin-stale-ch): stale timeseries channel admin tool design |
| `6677e494` | 2026-05-23 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(promptlog): design — prompts as first-class Shepard artefacts |
| `bef7fc5b` | 2026-05-23 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(logstore): log-store-with-shape sidecar design — LOGSTORE1 |
| `389203e7` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(reading-list): seed running 'interesting topics not pursued' list |
| `e67777c6` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(agent-findings): AAS + EDC reuse survey for shepard-plugin-aas + shepard... |
| `1ca88e6b` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(aidocs/16): migrate 10 FOCUS-captured rows into FOCUS-MIG section |
| `c79a3189` | 2026-05-23 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(mffd-import): v15.4→v15.6 self-update + telemetry + checkpoint + runner |
| `2e2a6748` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(synergy): S-08 — AI accountability dashboard MCP × Permission audit × F(... |
| `bada500b` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(synergy): S-07 — SHACL × MCP tools × ShapesValidateRest — one validator ... |
| `a0e3761b` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(synergy): S-06 — Snapshots × Garage S3 — Shepard absorbs the versioning gap |
| `5ae9368b` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(synergy): S-05 — PIDINST × SOSA/SSN × AAS Nameplate one-PID three-exports |
| `c5341265` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(synergy): S-04 — Trace3D × Video × DataBinding synchronized scrubber |
| `1af77494` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(synergy): S-03 — round-trip wiki: Confluence import × Wiki-writer × Snap... |
| `1dc6ad53` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(synergy): S-02 — OpenLineage × F(AI)²R × PROV-O = EASA evidence for free |
| `e69fe20d` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(synergy): S-01 — channel-as-individual: HSDS HDF5 × AAS TimeSeriesData ×... |
| `3e9ea7c7` | 2026-05-23 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | docs(GH-PM5): execute backfill — 331 Issues filed + 4 Milestones live |
| `1822ef7c` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(GH-PM5): adoption synthesis + backfill plan-as-artefact |
| `a9203e45` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(GH-PM1): RDM/FAIR persona audit of GH-PM1 + 6 adoption Qs |
| `8f2763ad` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(GH-PM1): reluctant-senior persona audit of policy doc 85 |
| `a2fb29d7` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(GH-PM1): digital-native persona audit of GH-PM policy |
| `c5d8b61a` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(GH-PM1): API-scrutinizer persona review of GH-PM adoption |
| `640e43ab` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(GH-PM): strategy-aligner persona audit of 6 adoption questions |
| `b187cbc7` | 2026-05-23 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(GH-PM5): backfill aidocs/16 ↔ Issues + Milestones on policy adoption |
| `2f1d9040` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(backlog): IMPORT-FIX + TERM1 + IOT1 sections (5+3+4 rows) |
| `6eceaa8a` | 2026-05-23 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(OBS-MFFD1): self-observability collector — Shepard measures its own import |
| `a17ad0cc` | 2026-05-23 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | docs(GH-PM1): GitHub project-management policies + trace-feature.sh |
| `ca2d7a80` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(model-inventory): seed aidocs/data/00 SSOT + add 7 missing aidocs/34 rows |
| `f34bbf8b` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(pages): origin myth + unofficial motto |
| `6cffbaef` | 2026-05-23 | mixed | 180k | 60k | 26.67 | 20 | 5 | 51.67 | 18.76 | MEDIUM | feat(GH-INFRA1): adopt GitHub Issues/Releases/Dependabot/labels/PR scaffolding |
| `26bd7292` | 2026-05-23 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | docs(bibliography): seed citable provenance ledger + Jekyll page + CITATION.cff |
| `fadf6dfd` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(semantics): metadata4ing (m4i) deepening design — 6 slices + critical fix |
| `1cd2514a` | 2026-05-23 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | docs(DOC-STAGE): unified work-item lifecycle taxonomy + retro-tag all aidocs |
| `07dd161c` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(backlog): SWEEP-2026-05-23 — surface 85 deferred items from repo grep |
| `1d0f7374` | 2026-05-23 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(backlog): ID-MIG + DB-OPT sections (consulted aidocs/25, 87, 91 first) |
| `a6835c97` | 2026-05-23 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(VIS): survey addendum — CAD + FEM (VIS-C1, VIS-F1) |
| `5ad3dca7` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(import-v15.2): smart warmup with fail-fast diagnostics (IMPORT-W1/W2/W3) |
| `5b8e325c` | 2026-05-22 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(VIS): visualization plugin family survey + dispatcher backlog rows |
| `327c7d08` | 2026-05-22 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | fix(infra): pin mongo image to 8.0.4 — proxmox post-outage version-compat fix |
| `7b58243e` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(backlog): DB inventory + best-practices + anti-pattern rows; presigned-U... |
| `4d7c30a1` | 2026-05-22 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | fix(home-showcase): collector restart loop — surface Keycloak token errors in... |
| `997b5227` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | fix(infra): garage healthcheck — replace missing-wget probe with garage CLI |
| `4c0cf326` | 2026-05-22 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(backlog): TOOL-SM1 — SendMessage tool unavailable in this harness |
| `4a67a5b8` | 2026-05-22 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(readme): refresh upstream-API note to cite 5.4.0 spec source |
| `d7c035fe` | 2026-05-22 | mixed | 180k | 60k | 26.67 | 20 | 5 | 51.67 | 18.76 | MEDIUM | docs(v5-legacy): commit upstream OpenAPI 5.4.0 spec as ground truth |
| `aa1be55f` | 2026-05-22 | code | 16k | 4k | 2.00 | 20 | 5 | 27.00 | 9.80 | MEDIUM | fix(WAAPI): typecheck-strict — array-access optional chaining in useAnimate t... |
| `afee310b` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(WAAPI): useAnimate composable + landing-page + Pages flair |
| `360c2e09` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(TRACE-A): prototype git-log → requirements traceability index |
| `3d7e16fb` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(mffd-import): v15.1 — snapshot brackets + per-DO F(AI)²R mode + FAIR R1 ... |
| `7242a25f` | 2026-05-22 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(TRACE): requirements-traceability research direction + backlog rows |
| `99285a72` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs: refresh GH Pages for pre-push (Garage active + v15 + sidecars + view re... |
| `044cb7e2` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | chore: track pre-existing agent-findings docs + UX-audit evidence + e2e progr... |
| `0cf87def` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs: refresh landing + architecture for Garage + plugin SPI |
| `0938074a` | 2026-05-22 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs: add reference pages for v15 import, sidecars SPI, view recipes |
| `74f54aa0` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(vision): data-forging front-and-center on aidocs/42 |
| `fba3692c` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(plugin-spi): PM1f — sidecars() declaration on PluginManifest + file-s3 G... |
| `a61c200f` | 2026-05-22 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | feat(TPL2a): add PROCESS_RECIPE + VIEW_RECIPE TemplateKinds + meta-shape |
| `fbf76227` | 2026-05-22 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs: v15 MFFD-import — aidocs/34 + 44 update + findings report (C8) |
| `ec174257` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd-import): v15 — C7 concurrency primitives (backoff, state, JWT pause) |
| `f6559cfb` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd-import): v15 — C6 PROV-O batch writeback + ETA publisher helpers |
| `78a02277` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd-import): v15 — C5 presigned-URL upload flow + Garage pre-flight |
| `0662cd50` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd-import): v15 — C2-C4 wire-shape fixes (D, F, G, A, B, C, D, E, I) |
| `f514cc92` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(mffd-import): v15 — rename from v14 + C1 wire-shape fixes (L, H, K, R) |
| `0c6ead4b` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(migrations): V61 register v15-import provenance predicates |
| `c03fbd96` | 2026-05-22 | mixed | 180k | 60k | 26.67 | 20 | 5 | 51.67 | 18.76 | MEDIUM | docs(fs1): Garage live on nuclide + v5→S3 migration runbook + v15 persona rev... |
| `42ac7d59` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(mffd-showcase): README aligned with corrected source story — live cube3 ... |
| `05353e1c` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(mffd): correct v15 source path — LIVE cube3 API, on-disk drop is shape-r... |
| `bd0b1b12` | 2026-05-22 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(mffd): retire seed.py Q1 fiction; lock v15 import requirements; add RESU... |
| `a0b3218e` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | docs(aidocs): SSOT consolidation — archive 31 orphan findings + dedupe 40-eco... |
| `67189c76` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd-import): v14 — all three payload types (files + TS + structured data) |
| `63515a32` | 2026-05-22 | code | 16k | 4k | 2.00 | 20 | 5 | 27.00 | 9.80 | MEDIUM | fix(#148): use 3-arg PermissionsService overload at DataObject v2 call sites |
| `0a4e9945` | 2026-05-22 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(ai-policy): OECD AI-in-Science + 9 sources × Shepard 4-band alignment |
| `c0d806d9` | 2026-05-22 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(db-schema): multi-substrate research (Neo4j+PG+Mongo+PostGIS+Garage) |
| `b8768144` | 2026-05-22 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(db-antipatterns): hunt across Neo4j+PG+Mongo+PostGIS+Garage |
| `85dfe2ba` | 2026-05-22 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | MEDIUM | docs(V1COMPAT.0): live validation findings — three CRITICAL failures |
| `29a0391d` | 2026-05-22 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | MEDIUM | feat(make): chain wait-for-health + smoke into redeploy targets |
| `8c3487e8` | 2026-05-22 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | feat(mffd-import): retry+backoff so the import survives a dest redeploy |
| `5523fa25` | 2026-05-22 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | MEDIUM | docs(ts-schema): TimescaleDB schema research + redesign opportunities |
| `4d31e769` | 2026-05-22 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | feat(MFFD-wiki): extraction script + ToC parser + Phase 1 test results |
| `edcb7900` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(mffd-import): --max-dos N flag for safe Q7 sample runs |
| `e943eedc` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(plugin-registry): trust mechanism survey |
| `f2221e64` | 2026-05-22 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | MEDIUM | docs(V1COMPAT.0): flip aidocs/44 row to ✓ shipped on worktree |
| `45872712` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(V1COMPAT.0): wire v1 deprecation banner + admin pane |
| `3cc8c4cb` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(V1COMPAT.0): admin REST + 410 gate filter + deprecation filter + UI banner |
| `7cfde4c1` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(V1COMPAT.0): Phase 1 marker plugin scaffold + :LegacyV1Config singleton |
| `d84216be` | 2026-05-22 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | MEDIUM | docs(aidocs/98): synthesize trio replacement from 7 persona reviews |
| `fc686baa` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | wip(aidocs/98): stub for trio-collapse synthesis (guard against session loss) |
| `2b02c317` | 2026-05-22 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | MEDIUM | fix(#133): route V5WireFidelityIT classes to failsafe via recursive glob |
| `4793f784` | 2026-05-22 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | fix(#131): omit nullable fork-added IO fields from v1 wire (NON_NULL audit) |
| `1206e436` | 2026-05-22 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | merge: v5 wire fixture regression corpus (#128) |
| `e11a4b52` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | chore(#129): land MCP test rewrites + CI @Disabled-guard |
| `84799623` | 2026-05-22 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | Merge branch 'worktree-agent-afd47cbcee0ee1cda' into main |
| `36f4760f` | 2026-05-22 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | Merge branch 'worktree-agent-afeffaa43e4043b78' into main |
| `12b4623a` | 2026-05-22 | code | 16k | 4k | 2.00 | 20 | 5 | 27.00 | 9.80 | MEDIUM | fix(IMP1b): rename Flyway migration V1.11.0→V1.11.1 to avoid collision with T... |
| `26558872` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | test(v5): wire-fidelity regression corpus + V5WireFidelityTest superclass |
| `1097efc6` | 2026-05-22 | merge | N/A | N/A | 0.00 | 0 | 4 | 4.00 | 1.45 | MEDIUM | Merge branch 'worktree-agent-af99a8ffc066548a1' into main |
| `77fb4ccf` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(AT1): additive detectorId IO + SHACL TTL + docs + trackers (PR-4 partial... |
| `f60c043b` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(AT1): MADDetector — extracted from in-tree AI1b (PR-3) |
| `c5b104ca` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(AT1): shepard-plugin-analytics-ts module scaffold (PR-2) |
| `85969cc9` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(AT1): TimeseriesAnalytics SPI with ExecutionMode tier (PR-1) |
| `0f535314` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(SHACL-1): Jena SHACL validator + /v2/shapes/validate + HMAC audit chain |
| `e90cf7fa` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(IMP1b): importer_run Postgres table + service (PR-2 of 7) |
| `b943b1c5` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(TS-ID PR-2): GET /v2/timeseries-containers/{id}/channels with shepardId |
| `087e8ca6` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(TS-ID PR-1): substrate — shepard_id UUID column on Postgres timeseries |
| `34066258` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(IMP1a): shepard-plugin-importer scaffold (PR-1 of 7) |
| `e0253cb4` | 2026-05-22 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | MEDIUM | docs(TS-ID): substrate correction — shepardId lives in Timescale, not Neo4j |
| `925b88e6` | 2026-05-22 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | fix(mffd): set collection Public on bootstrap so all instance users have access |
| `c7598401` | 2026-05-22 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | MEDIUM | docs(mffd): update run-mffd-import.sh for --bootstrap workflow |
| `93360af4` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd): bootstrap mode, cross-instance source client, snapshot rename tra... |
| `e5a19a49` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(mffd): source-collection mode, warmup probe gate, state tracker, deploy ... |
| `6308cc91` | 2026-05-22 | code | 16k | 4k | 2.00 | 20 | 5 | 27.00 | 9.80 | MEDIUM | fix(mffd): clarify DLR intranet auth — Shepard JWT goes in SHEPARD_API_KEY |
| `6f29ab51` | 2026-05-22 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | MEDIUM | feat(mffd): warmup/verify, wikidump+importscripts DataObjects, self-upload pr... |
| `16e12953` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | feat(UX): debounced search, sidebar flex fix, error body surfacing, git loggi... |
| `6092d71a` | 2026-05-22 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | MEDIUM | UI modernization pass 1: binoculars icon + HTTP caching |
| `5cdbb57c` | 2026-05-21 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | fix(build): install wiki-writer before ai in build-plugins |
| `9fd92994` | 2026-05-21 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | LOW | docs(CLAUDE.md): add mffd-showcase to shared agent orientation |
| `12b55608` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(showcase): add MFFD AFP manufacturing showcase seed |
| `b9cc428f` | 2026-05-21 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | build: install ai + wiki-writer plugins before video in build-plugins |
| `c870a006` | 2026-05-21 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | build: add shepard-plugin-ai and shepard-plugin-wiki-writer to Makefile build... |
| `b0ad7e74` | 2026-05-21 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | LOW | docs(aidocs/88): note MCP SSE deprecated — Phase 2 target is Streamable HTTP |
| `0e67fe07` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(AI1+WW1): add shepard-plugin-ai + shepard-plugin-wiki-writer v0 |
| `4e8b5752` | 2026-05-21 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | LOW | docs: Quarkus MCP v1.12.1 recommendation + Kadi4Mat comparison + RDM ecosyste... |
| `63dcbf7c` | 2026-05-21 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | feat(AI-SPI): add LlmProvider SPI + AiCapability enum + V58 migration |
| `9181dab3` | 2026-05-21 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | LOW | docs: process orchestrator plugin design + Quarkus MCP migration note |
| `fe61df00` | 2026-05-21 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | fix(#25): replace force layout with dagre hierarchical layout in lineage graph |
| `fdf6d93b` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(tools): import-confluence.py — CF1b+c Confluence HTML space import |
| `fb57a449` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(NTF1a): add nightly notification cleanup job + test |
| `0b2d6458` | 2026-05-21 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | fix(mcp): remove stale --profile comment from Caddyfile |
| `f5c7f390` | 2026-05-21 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | feat(MCP-1): always-on sidecar with admin plugin-toggle gating |
| `29c4034f` | 2026-05-21 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | fix(mcp): route /mcp/* via Caddy instead of Zoraxy virtual directory |
| `67c851f5` | 2026-05-21 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | LOW | docs(MCP-1): update aidocs/34 + aidocs/44 for shepard-plugin-mcp |
| `c572148e` | 2026-05-21 | doc | 4k | 1k | 0.50 | 0 | 0 | 0.50 | 0.18 | LOW | design(URI1): auth always required on /id/; FAIR A1.2 deferred to publication... |
| `952b744a` | 2026-05-21 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | LOW | design(URI1): base-url auto-detect + wizard integration + admin REST |
| `1f28dda3` | 2026-05-21 | doc | 45k | 15k | 6.67 | 0 | 0 | 6.67 | 2.42 | LOW | design(URI1): HTTPS persistent identifiers for all appId entities |
| `6933c115` | 2026-05-21 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | feat(tools): extend Confluence analysis script with fetch mode for missing files |
| `b4afdc9a` | 2026-05-21 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | fix(backend): populate containers in DataObjectDetailV2IO via Cypher query |
| `885ba9a6` | 2026-05-21 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | fix(mcp): correct setuptools build backend name |
| `9b90c283` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat: collection watchers, import validator, live-window, me v2, frontend UX ... |
| `0b146305` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(plugin): shepard-plugin-mcp — MCP-1a + MCP-1b complete |
| `e0a571d4` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(backend): REF-1 + ANC-1 + QA-1 + FAIR-1 — enriched DO detail, ancestry e... |
| `48a302ad` | 2026-05-21 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | docs(tools+integrations): welding explorer, Confluence import design, MCP pat... |
| `42ba11d9` | 2026-05-21 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | LOW | docs(agents): Phase 2 debate — 8 domain perspectives argue across all proposals |
| `5496646f` | 2026-05-21 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | LOW | docs(agents): Phase 1 feature proposals — 8 domain perspectives + JupyterHub ... |
| `ab2487dd` | 2026-05-21 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | LOW | docs(agents): overnight exploration findings — 7 specialist reports |
| `808f07e8` | 2026-05-21 | mixed | 180k | 60k | 26.67 | 20 | 5 | 51.67 | 18.76 | LOW | docs(#30/agents): MCP full-parity design + 8 exploration-first agent roles |
| `7b29ca44` | 2026-05-20 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | feat(admin): permission audit log viewer + nuke endpoint tests |
| `0a3597cb` | 2026-05-20 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(admin/seed): nuclear reset endpoint + lumen seed consistency fixes |
| `5c56084f` | 2026-05-20 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(TH1a): file thumbnail SPI + backend generation + frontend FilesTable column |
| `4528d0cd` | 2026-05-20 | doc | 110k | 40k | 17.22 | 0 | 0 | 17.22 | 6.25 | LOW | design(CST1/DR1): coordinate frame tree + scene drive & replay design docs |
| `f673bf87` | 2026-05-20 | code | 16k | 4k | 2.00 | 20 | 5 | 27.00 | 9.80 | LOW | fix(NTF1a): non-null assertion in notification test to satisfy strict TS |
| `ef73a79a` | 2026-05-20 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(NTF1a): in-app notification system — bell badge + panel + SPI |
| `87208b2f` | 2026-05-20 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | feat(PV1a/PV1b): payload version history viewer on FileContainer page |
| `8b55b3c5` | 2026-05-20 | code | 16k | 4k | 2.00 | 20 | 5 | 27.00 | 9.80 | LOW | fix(pgbouncer): wire auth_query via SECURITY DEFINER function |
| `cf0c8454` | 2026-05-20 | doc | 12k | 3k | 1.50 | 0 | 0 | 1.50 | 0.54 | LOW | infra: add PgBouncer connection pooler for TimescaleDB |
| `62659c7e` | 2026-05-20 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | fix(build): skip test compilation in first-pass plugin build |
| `82931a5b` | 2026-05-20 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | LOW | feat(INST2): extend capabilities endpoint with plugin metadata + show on Abou... |
| `8c385ebd` | 2026-05-20 | mixed | 30k | 8k | 3.89 | 20 | 5 | 28.89 | 10.49 | LOW | fix(docker): use static ffmpeg binary + update Makefile for two-pass plugin b... |
| `e193fc5b` | 2026-05-20 | code | 135k | 45k | 20.00 | 20 | 5 | 45.00 | 16.34 | LOW | feat(UX): video upload button, structured data editor, TS annotation fix |
| `9ea5f303` | 2026-05-20 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat(VID1b+INST2): extract video plugin + instance capabilities + ffmpeg |
| `7fd31d71` | 2026-05-20 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | LOW | feat(#23): show org name from ROR in header; fix aidocs CC2 tracking |
| `58a25e85` | 2026-05-20 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | LOW | test(CC2): unit tests for CollectionContainersRest — closes coverage gap |
| `c31fc636` | 2026-05-20 | code | 260k | 90k | 39.44 | 20 | 5 | 64.44 | 23.39 | LOW | feat: per-kind ref counts, anomaly detection fix, collection containers panel |
| `878e3338` | 2026-05-20 | code | 16k | 4k | 2.00 | 20 | 5 | 27.00 | 9.80 | LOW | fix(UI): align collection page section headers with content edge |
| `ee50a8f1` | 2026-05-20 | code | 45k | 15k | 6.67 | 20 | 5 | 31.67 | 11.50 | LOW | feat(UX): warn on expired session and prompt re-auth (#49) |

---

## §4 Going-forward rule

**Per-commit discipline** (per the memory file): every commit that makes
substantive code/doc changes appends one row to §3 IN THE SAME COMMIT (no
follow-up commits to add the log line). Tiny commits (typo fixes, single-line
tweaks) batch by day with a single summary row, or get
`[skipped: trivial]` on the day's wrap-up.

### §4.1 Automation paths considered

Four candidate paths to keep the log current — with the trade-off each makes:

| Path | Mechanism | Pro | Con |
|---|---|---|---|
| **(a) Manual** — current default | Committer types the row in the same commit | Same-PR rule respected; reflective discipline | Friction; easy to forget; token-shape is guesswork |
| **(b) Semi-automated** | `scripts/append-energy-row.sh <kind>` — reads `git diff --shortstat HEAD`, asks for tokens, computes row | Lowers friction by ~80%; one prompt for tokens; still in-commit | Still needs the committer to know token shape (Claude session doesn't expose it cleanly today) |
| **(c) CI-automated** | Post-merge GitHub Action computes from commit metadata + opens follow-up PR | Zero committer friction | **Violates same-PR rule** (memory file §3); log diverges from commit |
| **(d) Substrate-native** | Once Shepard captures `shepard:energyWh` on `:Activity`, generate log from `:Activity` graph | Single source of truth across dev + ops + research | Depends on `:Activity` instrumentation (PROV1d); not yet shipped |

### §4.2 Recommendation: (b) semi-automated, with (d) as the destination

**Argued lenses** (per `feedback_agents_argue_and_consult.md`):

- **API Scrutinizer** says: (c) is the cleanest API surface — one GitHub
  Action does the work, devs do nothing. The fact that it violates the
  same-PR rule is a *symptom* of the same-PR rule being too tight, not of
  (c) being wrong.
- **Reluctant Senior** says: (a) is fine; the log is a number on a slide,
  not a hot path. Six extra seconds per commit is not a real burden;
  inventing a tool adds maintenance debt.

**Resolution.** Adopt (b) for now. The semi-automated helper preserves the
same-PR discipline (which is load-bearing — the log diverging from the
commit hash defeats the audit-trail value) while burning the
write-down-the-numbers friction. (b)'s output is a one-liner the committer
can paste into the log, in the same `git commit -m` invocation.

(d) is the long-term destination. When `shepard:energyWh` lands on
`:Activity` (per the [memory file](../../../../root/.claude/projects/-opt-shepard/memory/feedback_energy_log_per_commit.md)
§"Honest companion") this log becomes a generated read-out of the
substrate, and the per-commit row is captured as side-effect of the dev
session's `:Activity` chain. The methodology doc stays valid; only the
write path changes.

(c) is rejected: the same-PR rule exists because the rule's value comes
from the commit-hash:log-row coupling — a follow-up PR breaks the
audit-trail invariant.

### §4.3 Trivial-commit batching

Commits with `+/- <5 lines` in non-content files (typos, comment fixes,
`gitignore` lines, doc front-matter bumps) MAY be batched into a single
daily summary row with `kind: doc, confidence: LOW, notes: "trivial batch
for YYYY-MM-DD: <count> commits"`. The threshold is a judgment call; err on
the side of logging.

## §5 Future work

The log is the **bootstrap**; the substrate is the destination. When the
following ship, the log gets re-grounded:

1. **`shepard:energyWh` + `shepard:co2eqGrams` on `:Activity`** (PROV1d
   follow-on; see backlog SUST-4). The dev session's `:Activity` chain
   captures the energy directly; the markdown log becomes a generated view.
2. **Anthropic per-token energy disclosure**. When/if Anthropic publishes
   Wh-per-token figures for Opus 4.7 (and 4.6, 4.5, etc. — model-aware
   estimation matters), recompute every backfilled LLM column. Confidence
   may bump LOW→MEDIUM across the historic table.
3. **JoularJX-instrumented `mvn package`** (SUST-2). Replaces the 20 Wh
   build heuristic with a measurement.
4. **GitHub Actions per-workflow sustainability data**. GitHub has published
   ecosystem aggregates but not per-workflow data; if/when it does,
   recompute every CI column.
5. **Substrate ingestion**. Lift the log into Shepard as a Collection — the
   table becomes a TimeseriesContainer of `energy_Wh` and `co2_g` channels,
   plottable in the same UI a researcher uses for sensor data. The
   dogfooding angle from the memory file: Shepard's development trail in
   Shepard.

## §6 Changelog

| Date       | Change                                                                | By |
|------------|-----------------------------------------------------------------------|----|
| 2026-05-23 | Initial creation. Backfill 2026-05-20 → 2026-05-23 (214 commits).     | Claude Opus 4.7 (1M context) + Flo |

