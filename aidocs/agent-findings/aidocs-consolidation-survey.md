---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# aidocs consolidation survey — single source of truth enforcement

**Date:** 2026-05-22  
**Scope:** Audit of all 174 canonical docs (excl. archive) across 188 total.  
**Purpose:** Identify duplicates, overlapping concepts, orphaned findings, numeric collisions, and design-vs-current ambiguities. Provide consolidation manifest.  
**Standing rule:** One canonical doc per semantic concept; all others REDIRECT or ARCHIVE.

---

## 1. Inventory snapshot

**Total docs surveyed:** 174 live + 14 archived = 188.

| Folder | Count | Notes |
|--------|-------|-------|
| Root (aidocs/) | 11 | Ledgers (34, 42, 44), ecosystem (40), pipelines (97), thesis (98), UI annoyances (100), dispatcher backlog (16), handover, case study, roadmap, index |
| platform/ | 26 | Architecture (11, 19, 20, 23–26, 29–30, 32, 47, 56, 63), SPI (47, 68, 69, 85, 86, 91), v1-compat (103, 103a), migrations (25, 87, A3c), MCP (30, 88), plugin designs (68, 69, 86) |
| data/ | 19 | Timeseries (12, 68, 69), spatial (81–86), storage (35, 37, 41, 45, 46, 50, 53), orchestration (50), CAD/CPACS (78, 79), file ops (88) |
| semantics/ | 8 | Search (13), semantic (14, 43), repository (48), ontology (65), SHACL/shapes (95, 96, 98) |
| workflows/ | 8 | Provenance (30, 55, 64), templates (39, 54), RO-Crate (31), git (38), user profile (36) |
| integrations/ | 15 | Ecosystem (40), backends (52, 60–70, 72, 77, 80–84, 92) |
| ops/ | 13 | Admin (22, 27, 28), UX/frontend (33, 49, 58, 59, 75, 77), testing (75, 77), collection model (87), UI (85, 86) |
| strategy/ | 7 | Competitors (70), fork adoption (71), stakeholders (73, 74, 82), funding (75), users (76) |
| ux/ | 5 | Landing page (73), session refresh (74), chart (76), basic mode (78), mode matrix (82) |
| agent-findings/ | 57 | 10 cited in canonical docs; **47 orphaned (0 refs)** |
| plugins/, input/ | 2 | AAS extraction (69), input config |
| archive/ | 14 | Pre-2026-05 status snapshots, cleanup plans |

---

## 2. Numeric-prefix collision analysis

### Collisions with distinct topics (keep all, folder context separates)

**82 (4 docs, 4 topics):**
- data/82: spatial-perf-evaluation (PostGIS tuning)
- integrations/82: confluence-import (wiki importer)
- strategy/82: zlp-augsburg-stakeholder (DLR stakeholder brief)
- ux/82: basic-vs-advanced-mode-matrix (feature gating reference)

**Decision: KEEP.** Folders provide clear context.

**86 (3 docs, 3 topics):**
- data/86: scene-drive-and-replay (DR1 — digital twin drive)
- ops/86: ui-changelog (living ledger)
- platform/86: ai-plugin-design (shepard-plugin-ai)

**Decision: KEEP.** Distinct topics; folder separates them.

**85 (3 docs, 3 topics):**
- data/85: coordinate-frame-tree (CST1 — coordinate systems)
- ops/85: ui-overhaul-design (UI redesign critique)
- platform/85: openapi-llm-readable (API doc standard)

**Decision: KEEP.** Distinct topics; folder context separates.

**69 (3 docs, 3 topics):**
- data/69: timeseries-upstream-migration (schema migration)
- platform/69: runtime-plugin-cdi (CDI deferred)
- plugins/69: aas-plugin-extraction-design (AAS plugin)

**Decision: KEEP.** Distinct topics and folders.

### Collisions with one superseded doc

**68 (3 docs, 1 superseded):**
- data/68: timeseries-data-model-tuning (CANONICAL)
- platform/68-plugin-vs-core-overview: architecture frame (CANONICAL)
- platform/68-v2-baseline-v1-compat-layer: **SUPERSEDED by platform/103/#103a** (2026-05-22)

**Decision: ARCHIVE platform/68-v2-baseline-v1-compat-layer.** Keep data/68 and platform/68-plugin-vs-core-overview (different topics).

**88, 87, 84, 83, 81, 78, 77, 76, 75, 74, 73, 71, 70, 41, 40, 30, 103:**
All within a single folder or are distinct cross-folder designs. No consolidation needed.

### Collision: 40-ecosystem (same number, overlapping topic)

- **root/40-ecosystem.md** (2026-05-21, Status: Living) — CANONICAL ecosystem ledger
- **integrations/40-ecosystem.md** (2026-05-08, Status: Concept) — SPW+sTC integration design

**Analysis:** Root/40 is a living ledger of repos, standards, and planned plugins. integrations/40 is a forward-looking design for integrating the shepard-process-wizard and timeseries-collector. They overlap on "ecosystem" but address different questions: "what is the ecosystem" vs. "how do we integrate these two tools."

**Decision: MERGE integrations/40 into root/40 as a new §, or extract to workflows/40-spw-stc-integration-design.md.** Then REDIRECT integrations/40 → root/40 with brief intro pointing to the workflow doc.

### Collision: 98 (same number, different topics)

- **root/98-thesis-perspective.md** (2026-05-21) — Fork adoption narrative
- **semantics/98-shapes-views-and-process-model.md** (2026-05-?) — MFFD shapes canonical

**Analysis:** These are NOT duplicates. Root/98 is a meta-document about the fork as a thesis project. semantics/98 (per task spec) is the canonical reference for how shapes map to views. No semantic overlap.

**Decision: KEEP both.** Root/98 is a unique document; semantics/98 is a canonical technical reference. No action needed.

---

## 3. Current-state vs. design-state classification

### LIVE (kept current with features)
- root/34: upstream-upgrade-path (Status: **Living document**)
- root/42: vision (Status: **Live**)
- root/44: fork-vs-upstream-feature-matrix (Status: **Living**)
- ops/86: ui-changelog (Status: **Living ledger**)
- semantics/95: shacl-templates-and-individuals (Status: **Live reference**)
- semantics/98: shapes-views-and-process-model (Status: **Live reference**)
- workflows/64: provenance-architecture (Status: **Live reference**)

### DESIGN (not yet shipped)
Most design docs clearly state Status: Design, Concept, or Proposal. Examples:
- workflows/39: "Status: Design. No code or migration shipped."
- platform/86: "Status: Designed — 2026-05-20"
- integrations/80: "Status: design — ready for slice planning"

### HYBRID (partly shipped, partly future)
- platform/88: "Design; landed in Phase 1; Phase 2 pending"
- semantics/95: "Live reference; roadmap includes future slices"

### STALE (superseded)
- platform/68-v2-baseline-v1-compat-layer: **"SUPERSEDED by aidocs/platform/103-v1-compat-plugin-extraction.md + #103a"** (2026-05-22)

### Clarity assessment
**No docs present design as current state without disclaimer.** All sampled design docs have explicit Status fields. Best practice is observed across the board.

---

## 4. Agent-findings orphan analysis

**Total agent-findings docs:** 57  
**Cited in canonical docs:** 10  
**Orphaned (0 inbound references):** 47

### Orphaned session findings (candidates for bulk archive)

**All *-debate docs** (cross-agent debate snapshots):
- analytics-ai-debate, api-scrutinizer-debate, data-ontologist-debate, ecosystem-advocate-debate, manufacturing-quality-debate, research-data-manager-debate, strategy-advisor-debate, ux-auditor-debate, persona-debate-round

**All *-proposals docs** (feature lists extracted from debates):
- analytics-ai-proposals, api-scrutinizer-proposals, data-ontologist-proposals, ecosystem-advocate-proposals, manufacturing-quality-proposals, research-data-manager-proposals, strategy-advisor-proposals, ux-auditor-proposals

**Domain-specific audits** (not rolled up):
- database-antipatterns-audit, database-schema-research-multi-db
- easa-ai-compliance (note: easa-ai-regulatory-positioning IS cited; easa-data-management IS cited)
- industrial-robotics-ontology-audit
- quarkus-ecosystem, quarkus-mcp-kadi-gaps
- timescaledb-schema-research

**Point-in-time assessments:**
- mffd-paper-198366, performance-baseline-2026-05-21, v1-compat-live-validation
- shacl-changeover-non-ts, test-cleanup-2026-05-22
- trace3d-spike, plugin-registry-trust

**All persona-* docs EXCEPT persona-review-***:
- persona-digital-native, persona-reluctant-senior, ai-in-science-policy-alignment

### Cited/canonical agent-findings (keep)

- **dlr-ontology-catalog.md** (8 refs) — cited in semantics/95, 96, 40-ecosystem
- **ecosystem-advocate.md** (2 refs) — cited in root/40
- **easa-ai-regulatory-positioning.md** (2 refs) — cited in root/40
- **ecosystem-tools.md** (1 ref) — cited in root/40
- **persona-review-ontologist.md** through **persona-review-ime-aqe.md** (7 docs, each cited in semantics/98)

### Recommendation

**ARCHIVE all 47 orphaned findings** to `archive/agent-findings-sessions-2026-05/`. These are session snapshots; if their content is valuable, fold them into the canonical docs that ARE referenced. This unclutters the active agent-findings/ folder and creates a clear signal: "This folder contains only findings that feed the canonical docs."

---

## 5. Duplicate-or-overlapping cluster analysis

### Provenance lineage (3 docs, intentional progression)

| Doc | Date | Status | Role |
|-----|------|--------|------|
| workflows/30 | 2026-05-07 | Proposal | Forward-looking exploration (ancestor) |
| workflows/55 | 2026-05-12 | Concept design | Design (current) |
| workflows/64 | 2026-05-13 | Live reference | Shipped state + roadmap |

**Relationship:** 30 → 55 → 64 is an intentional lineage (explore → design → ship). Not duplicates.

**Decision: KEEP all three.** Cross-reference: 30 and 55 should note "this design shipped; see workflows/64." 64 should cite "design in workflows/55, exploration in workflows/30."

### Templates progression (2 docs, two different shapes)

| Doc | Date | Status | Describes |
|-----|------|--------|-----------|
| workflows/39 | 2026-05-08 | Design | v1: DataObjects in `__templates` Collection |
| workflows/54 | 2026-05-12 | Concept | v2: ShepardTemplate entity (future) |

**Relationship:** 39 is the shipped shape; 54 is the planned refactor. Not duplicates; intentional evolution.

**Decision: KEEP both.** Cross-reference: 39 should note "future refactor in workflows/54." 54 should note "replaces the DataObject-based shape in workflows/39."

### Plugin system cluster (multiple refs to SPI, architecture, implementations)

| Doc | Folder | Role |
|-----|--------|------|
| platform/47 | platform | **CANONICAL SPI reference** (DevEx + PayloadKind SPI) |
| platform/68 | platform | Architecture frame (plugin-vs-core) |
| platform/69 | platform | Specific item (CDI, deferred) |
| platform/30 | platform | MCP plugin design |
| platform/86 | platform | AI plugin design |
| data/69 | data | AAS plugin extraction |
| plugins/69 | plugins | AAS plugin extraction (duplicate?) |

**Analysis:** platform/47 is the canonical SPI reference. 68 and 69 are architecture/implementation docs that reference 47. 30, 86, etc. are specific plugin designs. Check: are `platform/69-runtime-plugin-cdi.md` and `plugins/69-aas-plugin-extraction-design.md` the same document? **No — different names, different topics (CDI vs. AAS).**

**Decision: KEEP all.** Consider brief note in platform/47 pointing to "specific plugins: platform/30 (MCP), platform/86 (AI), plugins/69 (AAS), etc."

### Ecosystem (40) — root vs. integrations

Already analyzed above (§2). **Decision: MERGE integrations/40 into root/40 or workflows/.**

### UI design cluster

| Doc | Folder | Role |
|-----|--------|------|
| ops/85 | ops | UI overhaul — critique + opportunities |
| ops/86 | ops | UI changelog — living ledger |
| data/86 | data | Scene drive — 3D digital twin UI (feature-specific) |
| platform/85 | platform | OpenAPI doc standard (not UI-specific) |

**Analysis:** ops/85 and ops/86 are related (both UI-specific) but not duplicates (design vs. ledger). data/86 is a feature design that includes a UI component. platform/85 is API documentation (not UI).

**Decision: KEEP all.** ops/85 and ops/86 could cross-reference each other ("UI redesign in ops/85; changelog in ops/86").

### SHACL + ontology cluster

| Doc | Folder | Role |
|-----|--------|------|
| semantics/95 | semantics | SHACL templates, individuals, ontology-driven data model |
| semantics/96 | semantics | Upper-ontology alignment (BFO + IOF + IAO) |
| semantics/98 | semantics | Shapes, views, and MFFD process model |

**Relationship:** 95 is "how we model data (SHACL)." 96 is "what ontologies we align to (BFO/IOF/IAO)." 98 is "how shapes map to views (MFFD)." Not duplicates; complementary.

**Decision: KEEP all.** Add cross-reference in 98: "Substrate: see semantics/95 (SHACL) + semantics/96 (ontology alignment)."

---

## 6. Consolidation manifest (actionable output)

| # | Doc Path | Action | Reason | Target |
|---|----------|--------|--------|--------|
| 1 | root/40-ecosystem.md | **KEEP** | Living ledger; canonical ecosystem reference | self |
| 2 | integrations/40-ecosystem.md | **MERGE→REDIRECT** | SPW+sTC integration design overlaps topic but not scope; fold into root/40 §2 or create workflows/40-spw-stc-integration-design.md; then REDIRECT | workflows/40-spw-stc-integration-design.md |
| 3 | platform/68-v2-baseline-v1-compat-layer.md | **ARCHIVE** | Explicitly marked SUPERSEDED by platform/103/#103a (2026-05-22) | archive/platform/68-v2-baseline-v1-compat-layer.md |
| 4–8 | workflows/30, 55, 64 | **KEEP** | Provenance lineage (explore → design → shipped); not duplicates; add cross-refs | self |
| 9–10 | workflows/39, 54 | **KEEP** | Templates progression (v1 DataObjects → v2 ShepardTemplate); not duplicates; add cross-refs | self |
| 11–15 | platform/47, 68, 69, 30, 86; plugins/69 | **KEEP** | Plugin system cluster (SPI, architecture, implementations); not duplicates; add cluster note to platform/47 | self |
| 16–20 | semantics/95, 96, 98 | **KEEP** | SHACL+ontology cluster (not duplicates); add cross-refs in 98 | self |
| 21–24 | ops/85, 86; data/86; platform/85 | **KEEP** | UI cluster (design + ledger + feature-specific + API doc); not duplicates | self |
| 25 | root/98-thesis-perspective.md | **KEEP** | Fork adoption narrative; not a duplicate of semantics/98 | self |
| 26–27 | data/82, 86, 85, 88, 87, etc. (all numeric collisions in different folders) | **KEEP** | Folder context disambiguates; distinct topics | self |
| 28–74 | agent-findings/*.md (47 orphaned: all *-debate, *-proposals, audits, assessments except cited ones) | **ARCHIVE** | Session snapshots; 0 inbound references; if valuable, fold into canonical docs | archive/agent-findings-sessions-2026-05/ |
| 75–81 | agent-findings/dlr-ontology-catalog.md, ecosystem-advocate.md, easa-ai-regulatory-positioning.md, ecosystem-tools.md, persona-review-*.md | **KEEP** | Cited in canonical docs (8, 2, 2, 1, 7+ refs respectively) | agent-findings/ |

---

## 7. Single-source-of-truth principle — durable rule

This survey enforces: **one canonical doc per semantic concept.** Duplicates REDIRECT or ARCHIVE. To sustain this principle going forward:

1. **CLAUDE.md rule (developer prompt):** Before writing a new aidocs design, search existing aidocs for the topic. If a related doc exists, fold into it or cross-reference. Don't create a new doc with a new number.

2. **Session output routing (MEMORY.md):** When agent-findings docs are created, flag: "Can this fold into a canonical doc, or is it a snapshot for archive?" Mark snapshots with `<!-- ARCHIVE: session-YYYY-MM-DD -->` for quarterly bulk cleanup.

3. **aidocs/00-index.md expansion:** Add a "Cross-References & Lineages" section:
   - "Provenance: see workflows/30 (exploration) → workflows/55 (design) → workflows/64 (shipped reference)"
   - "Templates: see workflows/39 (v1 DataObjects) + workflows/54 (future ShepardTemplate)"
   - "Plugin SPI: see platform/47 (canonical) + platform/68 (architecture frame) + specific designs (platform/30, 86; plugins/69)"

4. **Live ledger discipline:** aidocs/34 (upstream-upgrade-path), aidocs/44 (feature-matrix) are the authoritative feature+upgrade sources. All design docs should *reference* these, not replace them. When a design ships: (a) update the matrix; (b) archive or redirect the design doc.

5. **Quarterly audit:** Run this survey quarterly. Archive any agent-findings with 0 inbound refs accumulated during the quarter. Target: keep agent-findings/ to <20 docs (only cited findings + current sessions).

This prevents the accretion of overlapping docs that plagued the codebase in May 2026. Readers can now confidently follow the canonical chain for any topic.

---

**Survey completed:** 2026-05-22  
**Next steps:** Implement actions in consolidation manifest; fold orphaned findings into canonical docs or archive; add cross-refs per cluster analysis.
