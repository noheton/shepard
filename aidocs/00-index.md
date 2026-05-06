# aidocs — Reading Order & Index

This directory holds the AI-assisted analysis and design notes for
shepard. Each file has a stable numeric prefix (so refs like
`aidocs/16-dispatcher-backlog.md` stay valid across history); the
**chapter grouping below** is the recommended reading order for new
readers and the way related docs cluster topically.

The corpus is grouped into seven chapters:

- **A. Situation reports** (`01–10`) — what is in the repo today, what
  is open on GitLab, what is mirrored on GitHub, where the gaps are.
- **B. Plan & live work** (`11`, `15`, `16`) — the team's master plan,
  Phase-0 status, and the live dispatcher backlog with per-round logs.
- **C. Architecture & operations** (`12`, `17`, `19`, `20`) —
  performance, reliability, startup/health, and the epic roadmap.
- **D. API surface & clients** (`18`, `23`, `26`, `27`, `28`, `29`) —
  REST surface review, CRUD inventory, paradigms-per-slice, client
  generation, convenience layers, P10 (SQL-over-HTTP) design.
- **E. Search, semantics & knowledge graph** (`13`, `14`) —
  discoverability, annotation generalisation, triplestore path. (`30`
  provenance & lineage joins this chapter once it lands.)
- **F. Identity, auth, permissions & identifiers** (`24`, `25`) —
  the access model and the application-generated-ID migration.
- **G. Strategy & tooling** (`21`, `22`) — demand gauges and the
  admin-CLI design.

If you are new, start with **01** for the lay of the land, then jump
to **11** for the plan, then dip into the chapter that matches the
task at hand. **The single live ledger** is `16-dispatcher-backlog.md`;
every queued / done / blocked item lives there with cross-references
to the design doc that motivates it.

---

## Chapter A — Situation reports

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

## Chapter B — Plan & live work

The master plan and the live ledger that tracks delivery against it.

| # | File | Purpose |
|---|---|---|
| 11 | [`11-implementation-plan.md`](11-implementation-plan.md) | Phased plan (Phase 0 housekeeping → Phase 6 backlog closure). The single document the team should drive against. |
| 15 | [`15-phase-0-status.md`](15-phase-0-status.md) | Progress note for Phase 0 housekeeping (what was done, what remains, research-closure check). |
| 16 | [`16-dispatcher-backlog.md`](16-dispatcher-backlog.md) | **The live ledger.** Every backlog item from `input/input_raw.md` and from the design docs in C–G with status (queued / dispatched / done / blocked / parked), commit hashes, and per-round dispatch logs. |

## Chapter C — Architecture & operations

Performance, reliability, deployment, and the strategic roadmap.

| # | File | Topic | Status |
|---|---|---|---|
| 12 | [`12-timescaledb-performance-analysis.md`](12-timescaledb-performance-analysis.md) | Schema, ingest, query, JDBC, ops; **§11** covers streaming the read path end-to-end and aligning the timeseries `id` (used by semantic annotations) with the 5-tuple (used by data endpoints) | Active — V1.8.0 schema fixes merged; sprint-1 mitigations open |
| 17 | [`17-startup-wait-audit.md`](17-startup-wait-audit.md) | Audit of Mongo / Flyway / JDBC startup wait/retry semantics against the Neo4j 60s ceiling from A1 (configuration-only follow-up) | Audit |
| 19 | [`19-architecture-feedback.md`](19-architecture-feedback.md) | Critical architectural review: where shepard is strong vs fragile; cross-cutting concerns; risks for the proposed `13` / `14` work; 6-month recommendation list mapped to backlog IDs in `16`; "deliberately don't do" list. Ground-truthed against post-Round-3 code state | Review |
| 20 | [`20-epic-roadmap.md`](20-epic-roadmap.md) | Strategic catalogue of 14 candidate epics across foundations, search/semantics, data types, performance, UX/ecosystem, KG interfaces — each with goal, scope, dependencies, T-shirt size, and a map to backlog IDs in `16`. Includes a Mermaid dependency graph and a two-track 6-month plan | Roadmap |

## Chapter D — API surface & clients

The REST surface (today vs. evolved), client generation, and the
convenience-layer plan that hides the multi-paradigm picture from
researchers. **`28` is the integrated synthesis** of `23 §4` + `§5`;
`29` is the first concrete implementation design downstream of it.

| # | File | Topic | Status |
|---|---|---|---|
| 18 | [`18-pagination-inventory.md`](18-pagination-inventory.md) | Inventory of every list-returning REST endpoint with current pagination state, row-count risk, frontend usage, and a sized rollout plan; convention recommendation that diverges from `13`'s cursor proposal for the existing list surface | Research |
| 23 | [`23-api-critique.md`](23-api-critique.md) | API critique: clunkiness pain points, redundancies, per-slice paradigm fit (SQL-over-HTTP, S3-presigned, SSE), client generation, schema source-of-truth (stay annotation-generated OpenAPI). Spawns IDs P5–P20 | Review |
| 26 | [`26-crud-consistency.md`](26-crud-consistency.md) | CRUD consistency table: 29 resources × 153 endpoints (85 GET / 33 POST / 10 PUT / 25 DELETE / **0 PATCH**); 28% have full CRUD; inconsistencies inventory; top-5 cleanup wins mapped to A2 / P5 / P9 | Inventory |
| 27 | [`27-convenience-clients-design.md`](27-convenience-clients-design.md) | `shepard-py` and `shepard-ts` convenience layer (P16) — `Client` shape, 14-line vs 3-line worked example, "no new dependencies" interrogation (true with `[pandas]` / `[excel]` extras), pagination iterator, three workflow helpers (`to_pandas`, `to_excel`, `ro_crate`), TypeScript counterpart, 4-phase rollout | Design |
| 28 | [`28-paradigms-and-clients-synthesis.md`](28-paradigms-and-clients-synthesis.md) | **Integrated synthesis** of `23 §4` (paradigms) + `§5` (client generation): four surfaces (REST core + SQL side door for bulk reads + S3-presigned for blobs + SSE for change-feeds), one schema source, one generator, one wrapper per language as multiplexer. Maintenance-cost ledger fits the budget; 12-month critical path. Spawns P22 + P23 | Synthesis |
| 29 | [`29-p10-implementation-design.md`](29-p10-implementation-design.md) | P10 (`POST /sql/timeseries`) implementation design — JSON DSL request body, Cypher-first permission flow via `filterAllowedForUser` (post-P2), three-format content negotiation (CSV / JSON / NDJSON; Arrow deferred to P11), 1M-row + PT60S caps, file-level layout under `data/timeseries/sql/`, **gated on C5**. 3-phase rollout (P10a / P10b / P10c) | Design |

## Chapter E — Search, semantics & knowledge graph

Discoverability, annotation model generalisation, and the triplestore
path. Designed as a triplet (12 §11.B → 13 → 14) with explicit
dependencies — see graph below. *(`30` provenance & lineage joins
this chapter once it lands; lineage is queried via `13`'s unified
search and stored as PROV-O alongside `14`'s annotation triples.)*

| # | File | Topic | Status |
|---|---|---|---|
| 13 | [`13-search-improvements.md`](13-search-improvements.md) | One unified search endpoint replacing today's five; richer query syntax (predicate JSON + fulltext + raw escape hatches: SPARQL/SQL); searching by semantic annotation; cursor pagination + streaming | Proposal |
| 14 | [`14-semantic-improvements.md`](14-semantic-improvements.md) | Generalising semantic annotations to file / structured / spatial payloads; label vs IRI discipline; search-as-you-type for terms; triplestore (n10s on Neo4j → GraphDB / RDF4J) for SPARQL, reasoning, KG export, FAIR | Proposal |

### Dependency graph among Chapter E + `12 §11.B`

```
12 §11.B (id alignment)
   ↓
13 (cross-store search) ──► 14 (annotation model)
   ↓                          ↓
13 §6 cross-store planner ──► 14 §5 triplestore
```

## Chapter F — Identity, auth, permissions & identifiers

Who can do what, and how things are named. The Neo4j-id migration is
in this chapter (not Architecture) because identifier choice cuts
through the auth and permission caches more than through performance.

| # | File | Topic | Status |
|---|---|---|---|
| 24 | [`24-permission-system-review.md`](24-permission-system-review.md) | Per-entity discretionary access model in Neo4j; fragilities ranked by blast-radius (C3 fail-open default, path-segment-switch dispatch, missing admin role, no group sharing, cache-key blindness, no audit trail, cross-DB consistency under degraded Neo4j); sized evolutions F1–F7 + L8 unpacking; verdicts on policy-as-code (no, but design F1 to allow drop-in) and row-level security in data DBs (no for v1) | Review |
| 25 | [`25-neo4j-id-migration-design.md`](25-neo4j-id-migration-design.md) | Migration from deprecated `id()` (and `elementId()`) to **application-generated IDs** (UUID v7). 5-phase rollout with Cypher-injection / cache-key / path-segment-switch interlocks. **L2c is gated on C5**, **L2d is gated on P4 + H4**. Spawns L2a–L2e | Design |

## Chapter G — Strategy & tooling

Demand-side research and operator-facing tooling.

| # | File | Topic | Status |
|---|---|---|---|
| 21 | [`21-user-interest-gauge.md`](21-user-interest-gauge.md) | Gauge of repo-internal demand signals for HDF5/HSDS, tabular/relational storage, and knowledge-graph interfaces. Verdicts: **KG interfaces** strongest, **HDF5** low-medium with hard `h5py`/`h5pyd` compatibility constraint (per epic E7), **tabular** thin (defer). Recommends a lightweight survey + interview plan | Research |
| 22 | [`22-admin-cli-draft.md`](22-admin-cli-draft.md) | Candidate-function design for a future `shepard-admin` CLI: goals/non-goals, auth model (blocked on A0), per-command catalogue, framework recommendation (Java + Picocli), distribution, 4-phase rollout | Draft |

---

## Cross-chapter interlocks

A handful of items in `16-dispatcher-backlog.md` thread through several
chapters; record them here so a reader who picks up one chapter knows
where the rest of the story lives.

- **C5** (Cypher / SQL injection — string-concatenated query construction)
  is in Chapter B's backlog as an escalated security item. It **gates
  P7** (unified `/search/v2`, Chapter E + D) **and L2c** (Chapter F's
  read-path switch becomes a SQL-injection vector once entity ids
  become UUID strings). Therefore: fix C5 *before* either ships.
- **A0** (admin role mechanism) is in Chapter B's backlog as a needs-
  decision item. It unblocks **A3b** (Chapter D's `/admin/features`
  endpoint), **P3c** (Chapter D's `/temp/migrations/*` hardening),
  and **C3** (Chapter F's fail-open fallback fix needs a way to
  authorise legitimate admin paths).
- **L2** (Chapter F application-generated IDs) couples to **§11** of
  `12-timescaledb-performance-analysis.md` (Chapter C), to the cache-
  key shape from **A4** (Chapter F), and to the path-segment-switch
  fragility called out in **24 §3.2** (Chapter F) and L2's own §5
  (Chapter F).
- **P16** (convenience clients, Chapter D `27`) is the **multiplexer**
  that hides Chapter D's four-surface picture from end-users; Chapter D
  doesn't fully deliver on its user-friendliness promise without P16.

---

## Snapshot date and provenance

Files snapshot 2026-05-04 unless noted in their own header (rounds 1–4
docs are 2026-05-05). Sources of truth:

- GitLab `gitlab.com/dlr-shepard/shepard` for issues / MRs (authoritative).
- GitHub `github.com/noheton/shepard` for the mirror.
- Code refs are `file_path:line_number` against `develop` HEAD at the
  snapshot date.

Subsequent updates should preserve the numeric prefixes (links and
external references depend on them); only the **chapter assignment**
and the per-row description in this index change as the corpus grows.
New docs go at the next free integer; new situation reports renumber
by inserting and updating cross-references.
