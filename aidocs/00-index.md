# aidocs — Reading Order & Index

This directory holds the AI-assisted analysis and design notes for
shepard. Files are number-prefixed in suggested reading order.

The corpus has three sections:

- **A. Situation reports** (01–10) — what is in the repo today, what
  is open on GitLab, what is mirrored on GitHub, where the gaps are.
- **B. Synthesis** (11) — phased plan that draws on A.
- **C. Forward-looking design notes** (12–14) — proposals for the
  next-major-cycle work, each readable on its own.

If you are new, start with **01** for the lay of the land, then jump
to **11** for the plan, then dip into A or C as needed.

---

## A. Situation reports

| # | File | Purpose | Audience |
|---|---|---|---|
| 01 | [`01-repo-overview.md`](01-repo-overview.md) | Top-level scope, layout, goals, constraints, tech stack | Anyone new to the repo |
| 02 | [`02-cluster-map.md`](02-cluster-map.md) | Cluster view of related issues / MRs / files; epics; cross-cutting concerns | Maintainers planning work |
| 03 | [`03-issues-status.md`](03-issues-status.md) | Per-issue gauge across all 166 open GitLab items (effort / complexity / value / staleness / implementation status) | Triage / planning |
| 04 | [`04-reconciliation.md`](04-reconciliation.md) | GitHub mirror vs GitLab authoritative — sync state, gaps, mirror artifacts | Mirror operators |
| 05 | [`05-dependency-report.md`](05-dependency-report.md) | Dependency landscape (Renovate, version pins, upgrade pressure) | Renovate / supply-chain |
| 06 | [`06-code-quality.md`](06-code-quality.md) | TODO / FIXME inventory, weak tests, dead code | Code-quality cleanup |
| 07 | [`07-security-issues.md`](07-security-issues.md) | Critical (C1-C5), High (H1-H8), Medium (M1-M12), Low findings | Security work |
| 08 | [`08-first-issues.md`](08-first-issues.md) | Small, fresh, high-value issues to ship first | New contributors / Phase 2 |
| 09 | [`09-ready-to-close.md`](09-ready-to-close.md) | Items ready for closure with confidence levels and draft comments | Maintainer pass |
| 10 | [`10-cleanup-plan.md`](10-cleanup-plan.md) | Original GitHub-mirror cleanup plan | Reference |

## B. Synthesis

| # | File | Purpose |
|---|---|---|
| 11 | [`11-implementation-plan.md`](11-implementation-plan.md) | Phased plan (Phase 0 housekeeping → Phase 6 backlog closure). The single document the team should drive against. |
| 15 | [`15-phase-0-status.md`](15-phase-0-status.md) | Progress note for Phase 0 housekeeping (what was done, what remains, research-closure check) |
| 16 | [`16-dispatcher-backlog.md`](16-dispatcher-backlog.md) | Working backlog of items extracted from `input/input_raw.md`, with scope filter, parked items, open decisions, and per-round dispatch log |
| 17 | [`17-startup-wait-audit.md`](17-startup-wait-audit.md) | Audit of Mongo / Flyway / JDBC startup wait/retry semantics against the Neo4j 60s ceiling from A1 (configuration-only follow-up) |

## C. Forward-looking design notes

12–14 are designed as a triplet; read in order. Each builds on the
identifier-discipline groundwork laid in §11.B of the perf doc. 18–22
are subsequent design / research / strategic notes.

| # | File | Topic | Status |
|---|---|---|---|
| 12 | [`12-timescaledb-performance-analysis.md`](12-timescaledb-performance-analysis.md) | Schema, ingest, query, JDBC, ops; **§11** covers streaming the read path end-to-end and aligning the timeseries `id` (used by semantic annotations) with the 5-tuple (used by data endpoints) | Active — V1.8.0 schema fixes merged; sprint-1 mitigations open |
| 13 | [`13-search-improvements.md`](13-search-improvements.md) | One unified search endpoint replacing today's five; richer query syntax (predicate JSON + fulltext + raw escape hatches: SPARQL/SQL); searching by semantic annotation; cursor pagination + streaming | Proposal |
| 14 | [`14-semantic-improvements.md`](14-semantic-improvements.md) | Generalising semantic annotations to file / structured / spatial payloads; label vs IRI discipline; search-as-you-type for terms; triplestore (n10s on Neo4j → GraphDB / RDF4J) for SPARQL, reasoning, KG export, FAIR | Proposal |
| 18 | [`18-pagination-inventory.md`](18-pagination-inventory.md) | Inventory of every list-returning REST endpoint with current pagination state, row-count risk, frontend usage, and a sized rollout plan; convention recommendation that diverges from `13-search-improvements.md`'s cursor proposal for the existing list surface | Research |
| 19 | [`19-architecture-feedback.md`](19-architecture-feedback.md) | Critical architectural review: where shepard is strong vs fragile; cross-cutting concerns; risks for the proposed `13` / `14` work; 6-month recommendation list mapped to backlog IDs in `16`; "deliberately don't do" list. Ground-truthed against post-Round-3 code state | Review |
| 20 | [`20-epic-roadmap.md`](20-epic-roadmap.md) | Strategic catalogue of 14 candidate epics across foundations, search/semantics, data types, performance, UX/ecosystem, KG interfaces — each with goal, scope, dependencies, T-shirt size, and a map to backlog IDs in `16`. Includes a Mermaid dependency graph and a two-track 6-month plan | Roadmap |
| 21 | [`21-user-interest-gauge.md`](21-user-interest-gauge.md) | Gauge of repo-internal demand signals (open issues, design notes, Slack/Mattermost in `input_raw.md`) for HDF5/HSDS, tabular/relational storage, and knowledge-graph interfaces. Recommends a lightweight survey + interview plan to convert unknowns into evidence | Research |
| 22 | [`22-admin-cli-draft.md`](22-admin-cli-draft.md) | Candidate-function design for a future `shepard-admin` CLI: goals/non-goals, auth model (blocked on A0), per-command catalogue (features/health/migrations/cleanup/cache/apikey/import-export), framework recommendation (Java + Picocli), distribution, 4-phase rollout, open maintainer decisions | Draft |
| 23 | [`23-api-critique.md`](23-api-critique.md) | API critique: clunkiness pain points (resource model leaks storage, path/query-param non-uniformity, 40+ endpoints in one Rest, five search endpoints, permission-semantics-leak, error envelope, pagination, auth headers, Python boilerplate); per-slice paradigm fit (SQL-over-HTTP for bulk timeseries reads, S3-presigned for blobs, SSE for change-feeds); client generation (stay openapi-generator + pin + 30-LoC `shepard-py`/`shepard-ts` convenience layer) and schema source-of-truth (stay annotation-generated OpenAPI; OpenAPI 3.1 once tooling stabilises). Spawns IDs P5–P20 | Review |
| 24 | [`24-permission-system-review.md`](24-permission-system-review.md) | Permission system review and room for development: per-entity discretionary access model in Neo4j (`(:Permissions)`-sibling pattern); fragilities ranked by blast-radius (C3 fail-open default, path-segment-switch dispatch, missing admin role, no group sharing, cache-key blindness, no audit trail, cross-DB consistency under degraded Neo4j); sized evolutions F1–F7 + L8 unpacking; verdicts on policy-as-code (no, but design F1 to allow drop-in) and row-level security in data DBs (no for v1) | Review |
| 25 | [`25-neo4j-id-migration-design.md`](25-neo4j-id-migration-design.md) | Migration plan from deprecated `id()` (and `elementId()`) to application-generated IDs (UUID v7 chosen). 5-phase rollout (instrument → additive `appId` → backfill → read-path switch → `/v2` native → drop legacy) with Cypher-injection / cache-key / path-segment-switch interlocks. Spawns sub-IDs L2a–L2e | Design |
| 26 | [`26-crud-consistency.md`](26-crud-consistency.md) | CRUD consistency table for the REST surface: 29 resources × 153 endpoints (85 GET / 33 POST / 10 PUT / 25 DELETE / 0 PATCH); only 28% have full CRUD; inconsistencies inventory (no PATCH anywhere, composite 5-tuple key, permissions sub-resource lottery, by-X endpoint fragmentation, four per-kind annotation rests); top-5 cleanup wins mapped to A2 / P5 / P9 | Inventory |
| 27 | [`27-convenience-clients-design.md`](27-convenience-clients-design.md) | `shepard-py` and `shepard-ts` convenience layer (backlog P16) — concrete `Client` shape, 14-line vs 3-line worked example, honest interrogation of "no new dependencies" (true with `[pandas]` / `[excel]` extras) and "150 LoC" (achievable; tests ~equal), pagination iterator, three workflow helpers (`to_pandas`, `to_excel`, `ro_crate`) with pre-P10 vs post-P10 shapes, TypeScript counterpart with browser-vs-Node tree-shaking notes, maintenance story tied to P17b, 4-phase rollout | Design |
| 28 | [`28-paradigms-and-clients-synthesis.md`](28-paradigms-and-clients-synthesis.md) | Integrated synthesis of `aidocs/23 §4` (paradigms) + `§5` (client generation): four surfaces (REST core + SQL side door for bulk reads + S3-presigned for blobs + SSE for change-feeds), one schema source, one generator pipeline, one convenience wrapper per language as the multiplexer. Maintenance-cost ledger fits the budget; 12-month critical path alternates foundations / user-value tracks. Spawns P22 (SSE proxy-compat test) + P23 (presign-vs-cache TTL invariant) | Synthesis |

### Dependency graph among C-section docs

```
12 §11.B (id alignment)
   ↓
13 (cross-store search) ──► 14 (annotation model)
   ↓                          ↓
13 §6 cross-store planner ──► 14 §5 triplestore
```

---

## Snapshot date and provenance

All files snapshot 2026-05-04 unless noted in their own header. Sources
of truth:

- GitLab `gitlab.com/dlr-shepard/shepard` for issues / MRs (authoritative).
- GitHub `github.com/noheton/shepard` for the mirror.
- Code refs are `file_path:line_number` against `develop` HEAD at the
  snapshot date.

Subsequent updates should preserve the numeric prefixes. New
forward-looking docs go at 15+; new situation reports renumber by
inserting and updating cross-references.
