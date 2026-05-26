---
stage: audited-by-personas
last-stage-change: 2026-05-26
---

# 161 — aidocs SSOT consolidation sweep

**Date:** 2026-05-26
**Task:** #161 — aidocs consolidation, single source of truth per concept.
**Prior work:** `aidocs/agent-findings/aidocs-consolidation-survey.md` (2026-05-22)
completed a full semantic-similarity audit across 174 docs and produced a
consolidation manifest. This sweep implements the "add cross-refs" actions from
that manifest that were not yet executed.

---

## What I found

### 1. Prior survey coverage

The 2026-05-22 survey already analyzed every cluster the task spec names. Key
findings that stand:

- **SHACL cluster (semantics/95 + 96 + 98):** Not duplicates — complementary.
  95 = substrate, 96 = ontology alignment, 98 = shapes-to-views mapping. Keep all.
  Survey recommended adding SSOT split table to 98; this was implemented *inside 98
  itself* at §13-cross-reference-and-SSOT-split (the `## 13. Cross-reference…` section
  already contains a full linkage table stating which doc is canonical per claim).

- **Timeseries migration cluster (platform/87 + data/68 + data/69 + aidocs/16 + aidocs/34):**
  Not duplicates — intentional role split. `platform/87` is the engineering design SSOT
  for the 5-tuple → appId migration. `data/68` is the schema-tuning rationale (CANONICAL for
  TimescaleDB optimisation decisions). `data/69` is the operator-facing runbook for
  upgrading from upstream 5.2.0. `aidocs/16` rows are backlog pointers. `aidocs/34` row is
  the admin upgrade ledger entry. This is exactly the correct division.

- **Provenance lineage (workflows/30 + 55 + 64):** Intentional lineage (explore → design
  → shipped). 30 = exploration proposal, 55 = concept design, 64 = live reference. Survey
  recommended adding forward cross-references from 30 and 55 to 64. **Not yet done.**

### 2. Clusters addressed in this sweep (5 edits)

| # | File | Action | Reason |
|---|------|--------|--------|
| 1 | `workflows/30-provenance-and-lineage-design.md` | Add SSOT banner | Stage `idea`; design shipped as `workflows/64`. No forward pointer existed. |
| 2 | `workflows/55-provenance-and-activity-overhaul.md` | Add SSOT banner | Stage `concept`; design shipped as `workflows/64`. No forward pointer existed. |
| 3 | `agent-findings/shacl-changeover-non-ts.md` | Add SSOT banner | Implementation log for PRs now absorbed into `semantics/95` (CANONICAL). No pointer existed. |
| 4 | `agent-findings/data-ontologist-prov-o-v15.md` | Add SSOT banner | PROV-O fragment design for v15 import; PROV-O architecture SSOT is `workflows/64`. No pointer existed. |
| 5 | `data/69-timeseries-upstream-migration.md` | Add SSOT banner | Operator runbook; engineering design SSOT for the planned V1.11.0 TIMESTAMPTZ migration is `platform/87`. Stage `fragment`. Already had a `data/68` companion note but not a `platform/87` pointer for the appId migration dimension. |

### 3. Clusters confirmed already-handled

| Cluster | Status |
|---------|--------|
| SHACL 95/96/98 — SSOT split | Already implemented inside `semantics/98 §13` |
| `platform/68-v2-baseline-v1-compat-layer` (superseded) | Already archived to `archive/platform/68-v2-baseline-v1-compat-layer.md` per `platform/103a` |
| `archive/platform/86-ai-plugin-design.md` | Already marked decommissioned (banner at top of archived file) |
| `semantics/100` SSOT self-reference | Already present at bottom of doc |
| `strategy/105` SSOT self-reference | Already present at bottom of doc |

### 4. Clusters intentionally not touched

- `aidocs/16` rows that mention timeseries/SHACL/provenance: these are backlog *pointers*,
  not design content. Per CLAUDE.md, `aidocs/16` is a pointer ledger, not the SSOT for
  design. Adding banners to `aidocs/16` rows would be wrong — they are already correct
  by their nature.
- `aidocs/34` upgrade-ledger rows: same reasoning as `aidocs/16`.
- `agent-findings/data-ontologist.md`: covers broad data-model audit across MFFD+PLUTO.
  It is a supplementary finding, not a duplicate of any single design doc. No banner
  needed; it references `semantics/95` where appropriate (line 145).
- `agent-findings/research-data-manager.md`: broad FAIR audit. Feeds multiple docs
  (semantics/100, integrations/66, integrations/67). Not a duplicate of any single SSOT.

---

## SSOT decisions (durable)

| Concept | Canonical doc | Supplementary docs |
|---------|---------------|--------------------|
| Provenance architecture (shipped) | `workflows/64-provenance-architecture.md` | `workflows/55` (design), `workflows/30` (exploration), `agent-findings/data-ontologist-prov-o-v15.md` (v15-specific fragment) |
| SHACL substrate + templates | `semantics/95-shacl-templates-and-individuals.md` | `agent-findings/shacl-changeover-non-ts.md` (implementation log) |
| Timeseries appId migration (engineering design) | `platform/87-timeseries-appid-migration.md` | `data/69` (operator runbook), `data/68` (schema tuning rationale) |
| TimescaleDB schema tuning | `data/68-timeseries-data-model-tuning.md` | `platform/87` §substrate, `data/69` §V1.11.0 planned |

---

## Files changed

1. `/opt/shepard/aidocs/workflows/30-provenance-and-lineage-design.md`
2. `/opt/shepard/aidocs/workflows/55-provenance-and-activity-overhaul.md`
3. `/opt/shepard/aidocs/agent-findings/shacl-changeover-non-ts.md`
4. `/opt/shepard/aidocs/agent-findings/data-ontologist-prov-o-v15.md`
5. `/opt/shepard/aidocs/data/69-timeseries-upstream-migration.md`
6. `/opt/shepard/aidocs/01-doc-stage-index.md` (regenerated)
