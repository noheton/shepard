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



**Execution summary (real-time):**

- Walked rows: 499
- FILE: 331
- SKIP: 168
- FAIL: 0
- Closed (was `done`): 186
- rc.1: 38; backlog: 293; rc.2: 0
- Wall clock: 840.9s
- Sleep between calls: 0.4s


### Execution log (filled at runtime)

| aidocs ID | Decision | Issue # | Status | Milestone | Title (truncated) | Rationale |
|---|---|---|---|---|---|---|
| A0 | FILE | #1180 | closed | v6.x-backlog | fix(A0): Admin role mechanism: instance-admin role tier, populate JWTPrincipal | gate 1 (external-visible): row prefix A0 is admin/user-surface |
| C3 | FILE | #1181 | closed | v6.x-backlog | fix(C3): Remove the full-access fallback in PermissionsService | gate 1 (external-visible): row prefix C3 is admin/user-surface |
| C5 | FILE | #1182 | closed | v6.x-backlog | fix(C5): Replace string-concatenated Cypher query construction in Neo4jQueryBuil | gate 1 (external-visible): row prefix C5 is admin/user-surface |
| C5b | FILE | #1183 | closed | v6.x-backlog | fix(C5b): Apply C5's parameter-binding pattern to the second-wave id()= / ID()=  | gate 1 (external-visible): row prefix C5 is admin/user-surface |
| H4 | FILE | #1184 | closed | v6.x-backlog | fix(H4): Surface RFC 7807-shape error responses (existing high-finding from 07) | gate 1 (external-visible): row prefix H4 is admin/user-surface |
| A1 | FILE | #1185 | closed | v6.x-backlog | feat(A1): Async DB init: bounded timeout + exponential backoff in MigrationsRunn | gate 1 (external-visible): row prefix A1 is admin/user-surface |
| A1b | FILE | #1186 | closed | v6.x-backlog | feat(A1b): Health checks: distinguish startup readiness vs runtime; per-DB statu | gate 1 (external-visible): row prefix A1 is admin/user-surface |
| A1c | FILE | #1187 | closed | v6.x-backlog | feat(A1c): Async DB init: graceful degradation when optional DBs (PostGIS) unava | gate 1 (external-visible): row prefix A1 is admin/user-surface |
| A1f | FILE | #1188 | closed | v6.x-backlog | feat(A1f): Automated DB recovery scheduler on top of DbHealthState | gate 1 (external-visible): row prefix A1 is admin/user-surface |
| A1d | FILE | #1189 | closed | v6.x-backlog | feat(A1d): Audit MongoDB / Flyway / Quarkus JDBC startup wait/retry semantics | gate 1 (external-visible): row prefix A1 is admin/user-surface |
| A1e | FILE | #1190 | closed | v6.x-backlog | feat(A1e): MigrationsRunner | gate 1 (external-visible): row prefix A1 is admin/user-surface |
| A2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| A3 | FILE | #1191 | closed | v6.x-backlog | feat(A3): Runtime feature toggles via CDI @Produces + @ConditionalOnFeature | gate 1 (external-visible): row prefix A3 is admin/user-surface |
| A3b | FILE | #1192 | closed | v6.x-backlog | feat(A3b): /admin/features endpoint to view/modify runtime toggles | gate 1 (external-visible): row prefix A3 is admin/user-surface |
| A3c | FILE | #1193 | closed | v6.x-backlog | feat(A3c): Namespace split: catalog/migrate shepard | gate 1 (external-visible): row prefix A3 is admin/user-surface |
| A4 | FILE | #1194 | closed | v6.x-backlog | feat(A4): Permission cache: TTL/LRU (Caffeine), user+entity keying | gate 1 (external-visible): row prefix A4 is admin/user-surface |
| A4b | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| A4c | FILE | #1195 | closed | v6.x-backlog | feat(A4c): Permission cache warming on StartupEvent for top-N entities | gate 1 (external-visible): row prefix A4 is admin/user-surface |
| A4d | FILE | #1196 | closed | v6.x-backlog | feat(A4d): Enable Micrometer metrics on permissions-service-cache | gate 1 (external-visible): row prefix A4 is admin/user-surface |
| P1 | FILE | #1197 | closed | v6.x-backlog | feat(P1): Parallelize DB connection checks (CompletableFuture / virtual threads) | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P2 | FILE | #1198 | closed | v6.x-backlog | feat(P2): Batch permission checks: checkPermissionsBatch(List<Long>) | gate 1 (external-visible): row prefix P2 is admin/user-surface |
| P2b | FILE | #1199 | open | v6.x-backlog | feat(P2b): TimescaleDB continuous aggregates / materialized views | gate 1 (external-visible): row prefix P2 is admin/user-surface |
| P3 | FILE | #1200 | closed | v6.x-backlog | feat(P3): Migration progress monitoring endpoint | gate 1 (external-visible): row prefix P3 is admin/user-surface |
| P3b | FILE | #1201 | open | v6.x-backlog | feat(P3b): Wire the external timescale-migration-preparation image to write migr | gate 1 (external-visible): row prefix P3 is admin/user-surface |
| P3c | FILE | #1202 | closed | v6.x-backlog | feat(P3c): Tighten authorisation on /temp/migrations/ (was: always-allowed for a | gate 1 (external-visible): row prefix P3 is admin/user-surface |
| P4 | FILE | #1203 | closed | v6.x-backlog | feat(P4): API versioning routing scaffolding | gate 1 (external-visible): row prefix P4 is admin/user-surface |
| P4b | FILE | #1204 | closed | v6.x-backlog | feat(P4b): OpenAPI client tree-shaking / code splitting | gate 1 (external-visible): row prefix P4 is admin/user-surface |
| P4c | FILE | #1205 | closed | v6.x-backlog | feat(P4c): OpenAPI emission — split into per-shelf documents (/shepard/doc/opena | gate 1 (external-visible): row prefix P4 is admin/user-surface |
| L1 | FILE | #1206 | closed | v6.x-backlog | feat(L1): Admin CLI: cleanup of data marked for deletion, import/export of colle | gate 1 (external-visible): row prefix L1 is admin/user-surface |
| L2 | FILE | #1207 | closed | v6.x-backlog | feat(L2): Neo4J: stop using deprecated id() function, migrate to application-gen | gate 1 (external-visible): row prefix L2 is admin/user-surface |
| L2a | FILE | #1208 | closed | v6.x-backlog | feat(L2a): Phase 1: additive appId property + unique constraint, mixin on entity | gate 1 (external-visible): row prefix L2 is admin/user-surface |
| L2b | FILE | #1209 | closed | v6.x-backlog | feat(L2b): Phase 2: Cypher backfill appId = randomUUID(), idempotent | gate 1 (external-visible): row prefix L2 is admin/user-surface |
| L2c | FILE | #1210 | closed | v6.x-backlog | feat(L2c): Phase 3: switch every Cypher to WHERE e | gate 1 (external-visible): row prefix L2 is admin/user-surface |
| L2d | FILE | #1211 | open | v6.x-backlog | feat(L2d): Phase 4: /v2/ exposes appId natively | gate 1 (external-visible): row prefix L2 is admin/user-surface (ambiguous status) |
| L2e | FILE | #1212 | open | v6.x-backlog | feat(L2e): Phase 5: drop legacy /v1/ long-id paths, flip permissions-service-cac | gate 1 (external-visible): row prefix L2 is admin/user-surface |
| L3 | SKIP | — | superseded | — | — | parked/superseded/decommissioned |
| L4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| L5 | FILE | #1213 | closed | v6.x-backlog | feat(L5): Semi-permanent API keys with expiry | gate 1 (external-visible): row prefix L5 is admin/user-surface |
| L6 | FILE | #1214 | closed | v6.x-backlog | feat(L6): Output control: pagination on more endpoints | gate 1 (external-visible): row prefix L6 is admin/user-surface |
| L7 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| L8 | FILE | #1215 | closed | v6.x-backlog | feat(L8): Review permissions model | gate 1 (external-visible): row prefix L8 is admin/user-surface |
| A5 | FILE | #1216 | closed | v6.x-backlog | feat(A5): HDF5/HSDS support — umbrella (epic E7) | gate 1 (external-visible): row prefix A5 is admin/user-surface |
| A5a | FILE | #1217 | closed | v6.x-backlog | feat(A5a): Phase 1: HSDS sidecar + HdfContainer create/read/delete + Neo4j model | gate 1 (external-visible): row prefix A5 is admin/user-surface |
| A5b | FILE | #1218 | closed | v6.x-backlog | feat(A5b): Phase 2: Permission bridge | gate 1 (external-visible): row prefix A5 is admin/user-surface |
| A5c | FILE | #1219 | open | v6.x-backlog | feat(A5c): Phase 3: HdfReference (per-DataObject anchor at a specific dataset pa | gate 1 (external-visible): row prefix A5 is admin/user-surface |
| A5d | FILE | #1220 | closed | v6.x-backlog | feat(A5d): Phase 4: Download-original-file fallback (GET /v2/hdf-containers/{app | gate 1 (external-visible): row prefix A5 is admin/user-surface |
| A5e | FILE | #1221 | open | v6.x-backlog | feat(A5e): Phase 5: Auth bridge | gate 1 (external-visible): row prefix A5 is admin/user-surface |
| U1 | FILE | #1222 | closed | v6.x-backlog | feat(U1): User profile + account settings | gate 1 (external-visible): row prefix U1 is admin/user-surface |
| U1a | FILE | #1223 | closed | v6.x-backlog | feat(U1a): Phase 1: User | gate 1 (external-visible): row prefix U1 is admin/user-surface |
| U1b | FILE | #1224 | closed | v6.x-backlog | feat(U1b): Phase 2: User | gate 1 (external-visible): row prefix U1 is admin/user-surface |
| U1c | FILE | #1225 | open | v6.x-backlog | feat(U1c): Phase 3: Frontend split | gate 1 (external-visible): row prefix U1 is admin/user-surface (ambiguous status) |
| U1d | FILE | #1226 | closed | v6.x-backlog | feat(U1d): Phase 4: Preferences pane in /me: theme, language, timeZone, dateForm | gate 1 (external-visible): row prefix U1 is admin/user-surface |
| U1e | FILE | #1227 | closed | v6.x-backlog | feat(U1e): Phase 5: Avatar — shepard-uploaded path (PUT/DELETE /users/me/avatar) | gate 1 (external-visible): row prefix U1 is admin/user-surface |
| U2-coupled | SKIP | — | gated on aidocs/38 | — | — | no gate-1 surface (internal refactor / docs-only) |
| U1f | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| U3-coupled | FILE | #1228 | closed | v6.x-backlog | feat(U3-coupled): editor | gate 1 (external-visible): row prefix U3-coupled is admin/user-surface |
| U1d-ext | FILE | #1229 | closed | v6.x-backlog | feat(U1d-ext): ui | gate 1 (external-visible): row prefix U1 is admin/user-surface |
| J1 | FILE | #1230 | closed | v6.x-backlog | feat(J1): Lab journal v2 + Jupyter integration | gate 1 (external-visible): row prefix J1 is admin/user-surface |
| J1a | FILE | #1231 | closed | v6.x-backlog | feat(J1a): Markdown body interpretation (CommonMark + GFM, sanitised) + GET /v2/ | gate 1 (external-visible): row prefix J1 is admin/user-surface |
| J1b | FILE | #1232 | closed | v6.x-backlog | feat(J1b): Inline | gate 1 (external-visible): row prefix J1 is admin/user-surface |
| J1c | FILE | #1233 | closed | v6.x-backlog | docs(J1c): "Open in Jupyter" deep link consuming editor | gate 1 (external-visible): row prefix J1 is admin/user-surface |
| J1d | FILE | #1234 | closed | v6.x-backlog | feat(J1d): Edit history via append-only LabJournalEntryRevision sibling node | gate 1 (external-visible): row prefix J1 is admin/user-surface |
| J1e | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| J1f | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| G1 | FILE | #1235 | closed | v6.x-backlog | feat(G1): Git integration — umbrella | gate 1 (external-visible): row prefix G1 is admin/user-surface |
| G1a | FILE | #1236 | closed | v6.x-backlog | feat(G1a): GitReference (mode a, loose link) + Neo4j model + V19__Add_appId_cons | gate 1 (external-visible): row prefix G1 is admin/user-surface |
| G1b | FILE | #1237 | closed | v6.x-backlog | feat(G1b): Mode (b) tracked-artifact + GitLabRestClient adapter | gate 1 (external-visible): row prefix G1 is admin/user-surface |
| G1c | FILE | #1238 | closed | v6.x-backlog | feat(G1c): Mode (c) pinned snapshot + RO-Crate SoftwareSourceCode integration | gate 1 (external-visible): row prefix G1 is admin/user-surface |
| G1d | FILE | #1239 | closed | v6.x-backlog | feat(G1d): GitHub + Gitea per-host adapters | gate 1 (external-visible): row prefix G1 is admin/user-surface |
| G1e | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| G1f | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| T1 | FILE | #1240 | closed | v6.x-backlog | feat(T1): Templates — umbrella (replaces L3) | gate 1 (external-visible): row prefix T1 is admin/user-surface |
| T1b | FILE | #1241 | open | v6.x-backlog | feat(T1b): AttributeSpec model (required / type / enum / default / description)  | gate 1 (external-visible): row prefix T1 is admin/user-surface (ambiguous status) |
| T1c | FILE | #1242 | open | v6.x-backlog | feat(T1c): FileSlot model (required / allowedMimeTypes / description) | gate 1 (external-visible): row prefix T1 is admin/user-surface (ambiguous status) |
| T1d | FILE | #1243 | open | v6.x-backlog | feat(T1d): Per-Collection allow-list | gate 1 (external-visible): row prefix T1 is admin/user-surface (ambiguous status) |
| T1e | FILE | #1244 | closed | v6.x-backlog | feat(T1e): Instantiation flow — POST /v2/collections/{collectionAppId}/data-obje | gate 1 (external-visible): row prefix T1 is admin/user-surface |
| T1e2 | FILE | #1245 | closed | v6.x-backlog | feat(T1e2): Frontend template-driven creation | gate 1 (external-visible): row prefix T1 is admin/user-surface |
| T1f | FILE | #1246 | closed | v6.x-backlog | feat(T1f): YAML import/export admin tools (POST /v2/templates/import, GET /v2/te | gate 1 (external-visible): row prefix T1 is admin/user-surface |
| T1g | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| T1h | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| PR1 | FILE | #1247 | closed | v6.x-backlog | feat(PR1): Process design + runtime in shepard | gate 1 (external-visible): row prefix PR1 is admin/user-surface |
| PR1a | FILE | #1248 | open | v6.x-backlog | feat(PR1a): ProcessDefinition model + JSON processSpec blob + CRUD /v2/processes | gate 1 (external-visible): row prefix PR1 is admin/user-surface (ambiguous status) |
| PR1b | FILE | #1249 | open | v6.x-backlog | feat(PR1b): ProcessRun runtime: start a run, advance steps, persist progress | gate 1 (external-visible): row prefix PR1 is admin/user-surface (ambiguous status) |
| PR1c | FILE | #1250 | open | v6.x-backlog | feat(PR1c): SPW XML importer (POST /v2/processes/import) | gate 1 (external-visible): row prefix PR1 is admin/user-surface (ambiguous status) |
| PR1d | FILE | #1251 | open | v6.x-backlog | feat(PR1d): Conditional / parallel flow control | gate 1 (external-visible): row prefix PR1 is admin/user-surface (ambiguous status) |
| PR1e | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| V2 | FILE | #1252 | closed | v6.x-backlog | feat(V2): Snapshots — umbrella (point-in-time freeze on top of today's Version m | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| V2a | FILE | #1253 | closed | v6.x-backlog | feat(V2a): revision: long field on VersionableEntity + write-side increment in G | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| V2b | FILE | #1254 | closed | v6.x-backlog | feat(V2b): Snapshot + SnapshotEntry model + POST /v2/collections/{appId}/snapsho | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| V2c | FILE | #1255 | closed | v6.x-backlog | feat(V2c): Snapshot-pinned read path (GET /v2/collections/{appId}?snapshot={snap | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| V2d | FILE | #1256 | closed | v6.x-backlog | feat(V2d): RO-Crate export against a snapshot | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| V2e | FILE | #1257 | closed | v6.x-backlog | feat(V2e): Snapshot diff tool (GET /v2/snapshots/{a}/diff/{b}) | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| V2f | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| AI1 | FILE | #1258 | closed | v6.x-backlog | feat(AI1): AI opportunities — umbrella (traditional ML + LLM integration) | gate 1 (external-visible): row prefix AI1 is admin/user-surface |
| AI1a | FILE | #1259 | open | v6.x-backlog | feat(AI1a): AI plumbing slice | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1b | FILE | #1260 | closed | v6.x-backlog | feat(AI1b): Anomaly detection: POST /v2/timeseries-references/{refAppId}/detect- | gate 1 (external-visible): row prefix AI1 is admin/user-surface |
| AI1c | FILE | #1261 | closed | v6.x-backlog | feat(AI1c): Channel-quality scoring: background job emits qualityScore attribute | gate 1 (external-visible): row prefix AI1 is admin/user-surface |
| AI1d | FILE | #1262 | open | v6.x-backlog | feat(AI1d): Embedding-based similarity + GET /v2/data-objects/{appId}/similar | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1e | FILE | #1263 | open | v6.x-backlog | feat(AI1e): Snap dashboards (§5 | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1f | FILE | #1264 | open | v6.x-backlog | feat(AI1f): Natural-language search (§5 | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1g | FILE | #1265 | open | v6.x-backlog | feat(AI1g): Lab journal authoring assist: ghost-text completion in the editor, a | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1h | FILE | #1266 | open | v6.x-backlog | feat(AI1h): Semantic annotation suggestion: POST /v2/semantic-annotations/sugges | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1i | FILE | #1267 | open | v6.x-backlog | feat(AI1i): Auto-summarisation: per-DataObject summary attribute (debounced rebu | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1j | FILE | #1268 | open | v6.x-backlog | feat(AI1j): RO-Crate description generation: ?aiAssist=true on export; operator  | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1k | FILE | #1269 | open | v6.x-backlog | feat(AI1k): Conversational lineage: chat interface walking the lineage graph | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1l | FILE | #1270 | open | v6.x-backlog | feat(AI1l): Notebook scaffolding: "Open in Jupyter with starter notebook" button | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1m | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| AI1n | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| AI1o | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| AI1p | FILE | #1271 | open | v6.x-backlog | feat(AI1p): Per-provider OpenAPI nuances (Azure deployment URL paths, Anthropic  | gate 1 (external-visible): row prefix AI1 is admin/user-surface (ambiguous status) |
| AI1r | FILE | #1272 | closed | v6.x-backlog | feat(AI1r): shepard-experiment | gate 1 (external-visible): row prefix AI1 is admin/user-surface |
| FS1 | FILE | #1273 | closed | v6.0.0-rc.1 | feat(FS1): File storage backend pluggability (GridFS → S3 evaluation) | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1a | FILE | #1274 | closed | v6.0.0-rc.1 | feat(FS1a): FileStorage SPI extracted into core (de | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1b | FILE | #1275 | closed | v6.0.0-rc.1 | feat(FS1b): shepard-plugin-file-s3 plugin using AWS SDK v2 + endpoint-override c | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1c | FILE | #1276 | closed | v6.0.0-rc.1 | feat(FS1c): Presigned-URL /v2/ endpoints (POST /v2/files/{containerAppId}/upload | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1d | FILE | #1277 | closed | v6.0.0-rc.1 | feat(FS1d): Garage sidecar in infrastructure/docker-compose | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1e1 | FILE | #1278 | closed | v6.0.0-rc.1 | feat(FS1e1): shepard-admin files migrate CLI command (big-bang mode) + POST /v2/ | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1e2 | FILE | #1279 | closed | v6.0.0-rc.1 | feat(FS1e2): Background continuous-sweep mode + progress-via-P3 pattern for FS1e | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1f | FILE | #1280 | closed | v6.0.0-rc.1 | feat(FS1f): Frontend update — large-file uploads use /v2/upload-url presigned pa | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1g | FILE | #1281 | closed | v6.0.0-rc.1 | docs(FS1g): RO-Crate export delivery (aidocs/31 §O3) returns presigned URLs when | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| FS1h | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| FS1i | FILE | #1282 | closed | v6.0.0-rc.1 | feat(FS1i): ADR-0024 implementation: swap infrastructure-local/docker-compose | gate 1 (external-visible): row prefix FS1 is admin/user-surface |
| PV1 | FILE | #1283 | closed | v6.x-backlog | feat(PV1): Payload versioning — umbrella | gate 1 (external-visible): row prefix PV1 is admin/user-surface |
| PV1a | FILE | #1284 | closed | v6.x-backlog | feat(PV1a): :PayloadVersion standalone Neo4j node recording SHA-256, fileOid, si | gate 1 (external-visible): row prefix PV1 is admin/user-surface |
| PV1b | FILE | #1285 | closed | v6.x-backlog | feat(PV1b): Same shape applied to StructuredDataReference | gate 1 (external-visible): row prefix PV1 is admin/user-surface |
| PV1c | FILE | #1286 | open | v6.x-backlog | feat(PV1c): Same shape applied to SpatialDataReference (PostGIS version_id colum | gate 1 (external-visible): row prefix PV1 is admin/user-surface (ambiguous status) |
| PV1d | FILE | #1287 | open | v6.x-backlog | feat(PV1d): TimeseriesReference re-ingest flow + version-aware reads | gate 1 (external-visible): row prefix PV1 is admin/user-surface (ambiguous status) |
| PV1e | FILE | #1288 | open | v6.x-backlog | feat(PV1e): V2 snapshot extension | gate 1 (external-visible): row prefix PV1 is admin/user-surface (ambiguous status) |
| PV1f | FILE | #1289 | open | v6.x-backlog | feat(PV1f): RO-Crate export pins payloadVersion automatically when ?snapshot= is | gate 1 (external-visible): row prefix PV1 is admin/user-surface (ambiguous status) |
| PV1g | FILE | #1290 | open | v6.x-backlog | feat(PV1g): Per-Collection retention policy (Collection | gate 1 (external-visible): row prefix PV1 is admin/user-surface (ambiguous status) |
| PV1h | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| PL1 | FILE | #1291 | closed | v6.x-backlog | feat(PL1): Storage-backend plugin SPI | gate 1 (external-visible): row prefix PL1 is admin/user-surface |
| PL1a | FILE | #1292 | closed | v6.x-backlog | feat(PL1a): PayloadKind + PayloadStorage SPI interfaces in backend/ | gate 1 (external-visible): row prefix PL1 is admin/user-surface |
| PL1b | FILE | #1293 | open | v6.x-backlog | feat(PL1b): Pilot migration: shepard-plugin-spatial-postgis | gate 1 (external-visible): row prefix PL1 is admin/user-surface (ambiguous status) |
| PL1c | FILE | #1294 | open | v6.x-backlog | docs(PL1c): A5a (HDF5/HSDS, aidocs/35) ships as a plugin from day 1 | gate 1 (external-visible): row prefix PL1 is admin/user-surface (ambiguous status) |
| PL1d | FILE | #1295 | open | v6.x-backlog | docs(PL1d): G1a (Git, aidocs/38) ships as a plugin from day 1 | gate 1 (external-visible): row prefix PL1 is admin/user-surface (ambiguous status) |
| PL1e | SKIP | — | parked (post FS1) | — | — | parked/superseded/decommissioned |
| PL1f | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| PL1g | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| PM1 | FILE | #1296 | closed | v6.x-backlog | feat(PM1): Plugin manifest SPI + lifecycle (umbrella) | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| PM1a | FILE | #1297 | closed | v6.x-backlog | feat(PM1a): PluginManifest SPI + PluginRegistry (@ApplicationScoped, observes St | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| PM1b | FILE | #1298 | closed | v6.x-backlog | feat(PM1b): Admin REST + CLI parity for the PM1a PluginRegistry: GET /v2/admin/p | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| PM1b2 | FILE | #1299 | closed | v6.x-backlog | feat(PM1b2): JarSignatureVerifier for untrusted JARs (four shepard | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| PM1b3 | FILE | #1300 | open | v6.x-backlog | feat(PM1b3): True runtime CDI integration | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| PM1c | FILE | #1301 | closed | v6.x-backlog | feat(PM1c): Enrich PluginManifest SPI with admin-visible metadata (title(), desc | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| PM1d | FILE | #1302 | closed | v6.x-backlog | feat(PM1d): CLI extensibility SPI (de | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| PM1e | FILE | #1303 | closed | v6.x-backlog | feat(PM1e): Persist runtime overrides (PluginRuntimeOverride Neo4j entity, HasAp | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| DX1 | FILE | #1304 | closed | v6.x-backlog | feat(DX1): Unified ShepardTestStack test-resource (Postgres + Mongo + Neo4j + mo | gate 1 (external-visible): row prefix DX1 is admin/user-surface |
| DX2 | FILE | #1305 | closed | v6.x-backlog | fix(DX2): ShepardTestFixtures shared helpers (typed builders for Collection / Da | gate 1 (external-visible): row prefix DX2 is admin/user-surface |
| DX3 | SKIP | — | gated on PL1a | — | — | no gate-1 surface (internal refactor / docs-only) |
| DX4 | SKIP | — | gated on DX1 + `aidocs/22 | — | — | no gate-1 surface (internal refactor / docs-only) |
| DX5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DX6 | FILE | #1306 | closed | v6.x-backlog | feat(DX6): RFC 7807 errors everywhere (== existing H4) | gate 1 (external-visible): row prefix DX6 is admin/user-surface |
| DX7 | FILE | #1307 | closed | v6.x-backlog | feat(DX7): GET /v2/admin/features + shepard-admin features list showing every to | gate 1 (external-visible): row prefix DX7 is admin/user-surface |
| DX8 | SKIP | — | gated on `aidocs/29` P10a | — | — | no gate-1 surface (internal refactor / docs-only) |
| IX1 | SKIP | — | **design sketch** | — | — | no gate-1 surface (internal refactor / docs-only) |
| N1 | FILE | #1308 | closed | v6.x-backlog | feat(N1): Internal semantic repository via neosemantics | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1a | FILE | #1309 | closed | v6.x-backlog | feat(N1a): n10s plugin in Neo4j compose service; SemanticRepositoryType | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1b | FILE | #1310 | closed | v6.x-backlog | feat(N1b): Pre-seeded common ontologies (PROV-O / Dublin Core / schema | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1c | FILE | #1311 | closed | v6.x-backlog | docs(N1c): shepard-admin semantic refresh-ontologies CLI per aidocs/22 §4 | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1c2 | FILE | #1312 | closed | v6.x-backlog | feat(N1c2): Admin-configurable ontology preseed | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1d | FILE | #1313 | open | v6.x-backlog | feat(N1d): ~~LUMEN seed integration~~ | gate 1 (external-visible): row prefix N1 is admin/user-surface (ambiguous status) |
| N1e | FILE | #1314 | closed | v6.x-backlog | feat(N1e): Frontend annotation picker shows pre-seeded ontology terms by default | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1f | FILE | #1315 | closed | v6.x-backlog | feat(N1f): (optional) /v2/semantic/{repoAppId}/sparql proxy endpoint that wraps  | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1g | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| D1 | FILE | #1316 | closed | v6.x-backlog | docs(D1): In-app user docs — umbrella | gate 1 (external-visible): row prefix D1 is admin/user-surface |
| D1a | FILE | #1317 | closed | v6.x-backlog | docs(D1a): Frontend /help route + HelpFrame | gate 1 (external-visible): row prefix D1 is admin/user-surface |
| D1b | FILE | #1318 | open | v6.x-backlog | feat(D1b): Playwright spec + | gate 1 (external-visible): row prefix D1 is admin/user-surface (ambiguous status) |
| D1c | FILE | #1319 | open | v6.x-backlog | docs(D1c): Task-shaped help pages | gate 1 (external-visible): row prefix D1 is admin/user-surface (ambiguous status) |
| D1d | FILE | #1320 | open | v6.x-backlog | docs(D1d): Version stamping — docs/_site/version | gate 1 (external-visible): row prefix D1 is admin/user-surface (ambiguous status) |
| D1e | FILE | #1321 | open | v6.x-backlog | feat(D1e): Per-page "Was this helpful?" telemetry | gate 1 (external-visible): row prefix D1 is admin/user-surface (ambiguous status) |
| D1f | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| D1g | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| EXP1 | FILE | #1322 | closed | v6.x-backlog | feat(EXP1): Experiment orchestration | gate 1 (external-visible): row prefix EXP1 is admin/user-surface |
| EXP1a | FILE | #1323 | open | v6.x-backlog | feat(EXP1a): Coordinator service skeleton | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1b | FILE | #1324 | open | v6.x-backlog | feat(EXP1b): OPC/UA trigger subscription | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1c | FILE | #1325 | open | v6.x-backlog | feat(EXP1c): sTC sink integration | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1d | FILE | #1326 | open | v6.x-backlog | feat(EXP1d): Pre-seed mode — recipe declares full graph upfront | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1e | FILE | #1327 | open | v6.x-backlog | feat(EXP1e): Post-process mode — staging-bucket walk + ingest endpoint | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1f | FILE | #1328 | open | v6.x-backlog | feat(EXP1f): Checkpoint + V2 snapshot integration | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1g | FILE | #1329 | open | v6.x-backlog | feat(EXP1g): Restart-whole + restart-at-step | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1h | FILE | #1330 | open | v6.x-backlog | feat(EXP1h): KUKA OPC/UA trigger integration | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1i | FILE | #1331 | open | v6.x-backlog | feat(EXP1i): KUKA RSI telemetry routing via sTC's RSI source | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1j | FILE | #1332 | open | v6.x-backlog | feat(EXP1j): Operator UI — web, embedded in shepard's frontend /experiments rout | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1k | FILE | #1333 | open | v6.x-backlog | feat(EXP1k): Recipe storage in shepard's __templates Collection with templateKin | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1l | FILE | #1334 | open | v6.x-backlog | feat(EXP1l): Modbus / REST source integration via sTC i9 | gate 1 (external-visible): row prefix EXP1 is admin/user-surface (ambiguous status) |
| EXP1m | SKIP | — | parked until safety revie | — | — | parked/superseded/decommissioned |
| EXP1n | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| S1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| S2 | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| R1 | FILE | #1335 | closed | v6.x-backlog | feat(R1): Databus + MOSS federation layer (shepard-plugin-databus) | gate 1 (external-visible): row prefix R1 is admin/user-surface |
| R2 | FILE | #1336 | closed | v6.x-backlog | feat(R2): Per-payload selective RO-Crate export (refinement of existing export) | gate 1 (external-visible): row prefix R2 is admin/user-surface |
| R2b | FILE | #1337 | closed | v6.x-backlog | feat(R2b): File- and column-level selection on payloads (per-FileReference oid i | gate 1 (external-visible): row prefix R2 is admin/user-surface |
| R2c | FILE | #1338 | closed | v6.x-backlog | feat(R2c): Per-payload metadata-field redaction (e | gate 1 (external-visible): row prefix R2 is admin/user-surface |
| R2d | FILE | #1339 | closed | v6.x-backlog | feat(R2d): Emit permissions / versions / subscriptions / semantic-annotations do | gate 1 (external-visible): row prefix R2 is admin/user-surface |
| R2d2 | FILE | #1340 | closed | v6.x-backlog | feat(R2d2): Emit per-entity subscriptions in the RO-Crate export | gate 1 (external-visible): row prefix R2 is admin/user-surface |
| R3 | SKIP | — | **needs decision** | — | — | no gate-1 surface (internal refactor / docs-only) |
| R4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| R5 | SKIP | — | **needs decision** | — | — | no gate-1 surface (internal refactor / docs-only) |
| R6 | SKIP | — | **needs decision** | — | — | no gate-1 surface (internal refactor / docs-only) |
| R8 | SKIP | — | **needs decision** | — | — | no gate-1 surface (internal refactor / docs-only) |
| R9 | SKIP | — | **needs decision** | — | — | no gate-1 surface (internal refactor / docs-only) |
| P5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| P6 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| P7 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| P8 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| P9 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| P10 | FILE | #1341 | closed | v6.x-backlog | feat(P10): POST /sql/timeseries curated SQL-over-HTTP for bulk reads | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P10a | FILE | #1342 | open | v6.x-backlog | feat(P10a): Phase 1: SqlQuerySpec + SqlQueryCompiler + JSON-only endpoint behind | gate 1 (external-visible): row prefix P1 is admin/user-surface (ambiguous status) |
| P10b | FILE | #1343 | open | v6.x-backlog | feat(P10b): Phase 2: text/csv + application/x-ndjson content negotiation; stream | gate 1 (external-visible): row prefix P1 is admin/user-surface (ambiguous status) |
| P10c | FILE | #1344 | open | v6.x-backlog | feat(P10c): Phase 3: feature flag default-on; :SqlTimeseriesConfig + GET/PATCH / | gate 1 (external-visible): row prefix P1 is admin/user-surface (ambiguous status) |
| P11 | FILE | #1345 | open | v6.x-backlog | feat(P11): Apache Arrow Flight / DuckDB read endpoint for analytical workloads | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P12 | FILE | #1346 | open | v6.x-backlog | feat(P12): S3-presigned URLs for File and StructuredData payloads | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P13 | FILE | #1347 | open | v6.x-backlog | feat(P13): SSE change-feed (GET /collections/{id}/events) | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P14 | FILE | #1348 | closed | v6.x-backlog | feat(P14): NDJSON streaming ingest for high-throughput timeseries imports | gate 1 (external-visible): row prefix P14 is admin/user-surface |
| P15 | FILE | #1349 | open | v6.x-backlog | feat(P15): Migrate spec to OpenAPI 3 | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P16 | FILE | #1350 | closed | v6.x-backlog | feat(P16): shepard-py and shepard-ts convenience layers | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P17 | FILE | #1351 | closed | v6.x-backlog | feat(P17): Pin openapi-generator-cli version across languages, add Microsoft Kio | gate 1 (external-visible): row prefix P17 is admin/user-surface |
| P17b | FILE | #1352 | closed | v6.x-backlog | feat(P17b): CI lint: every IO class has @Schema(name=…) | gate 1 (external-visible): row prefix P17 is admin/user-surface |
| P18 | FILE | #1353 | closed | v6.x-backlog | feat(P18): RFC 7807 error envelope (application/problem+json) | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P19 | FILE | #1354 | open | v6.x-backlog | feat(P19): Cursor pagination on the unified search; offset elsewhere stays for n | gate 1 (external-visible): row prefix P1 is admin/user-surface |
| P20 | FILE | #1355 | open | v6.x-backlog | feat(P20): Reactive (Mutiny) migration for the timeseries read path as the first | gate 1 (external-visible): row prefix P2 is admin/user-surface |
| P21 | FILE | #1356 | closed | v6.x-backlog | feat(P21): Introduce PATCH for partial-update endpoints (currently every update  | gate 1 (external-visible): row prefix P2 is admin/user-surface |
| P21x | FILE | #1357 | open | v6.x-backlog | feat(P21x): Move APPLICATION_MERGE_PATCH_JSON constant from per-Rest declaration | gate 1 (external-visible): row prefix P2 is admin/user-surface (ambiguous status) |
| P21-References-prereq | FILE | #1358 | open | v6.x-backlog | feat(P21-References-prereq): Architectural finding: the ReferenceRest family (Ba | gate 1 (external-visible): row prefix P2 is admin/user-surface (ambiguous status) |
| P21-File-prereq | FILE | #1359 | open | v6.x-backlog | feat(P21-File-prereq): Architectural finding (P21-File agent stop-and-report): t | gate 1 (external-visible): row prefix P2 is admin/user-surface (ambiguous status) |
| P22 | FILE | #1360 | open | v6.x-backlog | feat(P22): SSE proxy-compatibility integration test | gate 1 (external-visible): row prefix P2 is admin/user-surface |
| P23 | FILE | #1361 | closed | v6.x-backlog | feat(P23): Presign-vs-cache TTL invariant validator | gate 1 (external-visible): row prefix P23 is admin/user-surface |
| F1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| F2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| F3 | FILE | #1362 | closed | v6.x-backlog | feat(F3): Permission audit log (Postgres table) for grants/revokes | gate 1 (external-visible): row prefix F3 is admin/user-surface |
| F4 | FILE | #1363 | closed | v6.x-backlog | feat(F4): Versioned cache invalidation key: (entityId, AccessType, userSub, jwtI | gate 1 (external-visible): row prefix F4 is admin/user-surface |
| F5 | FILE | #1364 | closed | v6.x-backlog | feat(F5): Explicit fail-closed invariant when Neo4j is degraded | gate 1 (external-visible): row prefix F5 is admin/user-surface |
| F6 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| F7 | SKIP | — | parked — conditions for u | — | — | parked/superseded/decommissioned |
| F8 | FILE | #1365 | closed | v6.x-backlog | feat(F8): Configurable OIDC roles-claim path (shepard | gate 1 (external-visible): row prefix F8 is admin/user-surface |
| A4e | FILE | #1366 | open | v6.x-backlog | feat(A4e): Convert PermissionsServiceCacheMetricsTest to a HealthzIT-style integ | gate 1 (external-visible): row prefix A4 is admin/user-surface (ambiguous status) |
| P2c | FILE | #1367 | open | v6.x-backlog | feat(P2c): filterAllowedUsers(entityId, AccessType, Collection<String> usernames | gate 1 (external-visible): row prefix P2 is admin/user-surface (ambiguous status) |
| TM1a | FILE | #1368 | closed | v6.0.0-rc.1 | feat(TM1a): Time-reference model on TimeseriesReference | gate 1 (external-visible): row prefix TM1a is admin/user-surface |
| AAS1 | FILE | #1369 | closed | v6.0.0-rc.1 | feat(AAS1): AAS backend integration | gate 1 (external-visible): row prefix AAS1 is admin/user-surface |
| AAS1a | FILE | #1370 | closed | v6.0.0-rc.1 | feat(AAS1a): GET /v2/aas/shells — minimal IDTA AAS v3 Shell listing: one Shell p | gate 1 (external-visible): row prefix AAS1 is admin/user-surface |
| AAS1-reg | FILE | #1371 | closed | v6.0.0-rc.1 | feat(AAS1-reg): Outbound registration at an external IDTA AAS Registry (BaSyx /  | gate 1 (external-visible): row prefix AAS1 is admin/user-surface |
| AAS1-well-known | FILE | #1372 | closed | v6.0.0-rc.1 | feat(AAS1-well-known): GET /v2/aas/ | gate 1 (external-visible): row prefix AAS1 is admin/user-surface |
| AAS1-fed | FILE | #1373 | closed | v6.0.0-rc.1 | feat(AAS1-fed): Parent-repository federation (shepard | gate 1 (external-visible): row prefix AAS1 is admin/user-surface |
| AAS1-mdns | FILE | #1374 | closed | v6.0.0-rc.1 | docs(AAS1-mdns): mDNS / DNS-SD opportunistic LAN discovery via JmDNS | gate 1 (external-visible): row prefix AAS1 is admin/user-surface |
| AAS1-edc | FILE | #1375 | open | v6.x-backlog | feat(AAS1-edc): (parked) Eclipse Dataspace Connector publish | gate 1 (external-visible): row prefix AAS1 is admin/user-surface (ambiguous status) |
| FB1 | SKIP | — | **design done; FR1a + FR1 | — | — | no gate-1 surface (internal refactor / docs-only) |
| FB1a-fileSize | FILE | #1376 | closed | v6.0.0-rc.1 | feat(FB1a-fileSize): fileSize Long field on :ShepardFile (Mongo + Neo4j round-tr | gate 1 (external-visible): row prefix FB1a-fileSize is admin/user-surface |
| VID1 | FILE | #1377 | closed | v6.0.0-rc.1 | feat(VID1): Video payload kind — dedicated PayloadStorage plugin | gate 1 (external-visible): row prefix VID1 is admin/user-surface |
| VID1a | FILE | #1378 | closed | v6.0.0-rc.1 | feat(VID1a): VideoStreamReference upload + ffprobe metadata extraction | gate 1 (external-visible): row prefix VID1 is admin/user-surface |
| T1c-instantiate | FILE | #1379 | closed | v6.x-backlog | feat(T1c-instantiate): POST /v2/collections/{appId}/templates/from/{templateAppI | gate 1 (external-visible): row prefix T1 is admin/user-surface |
| PROV1 | FILE | #1380 | closed | v6.0.0-rc.1 | feat(PROV1): PROV-O provenance + activity dashboard | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1a | FILE | #1381 | closed | v6.0.0-rc.1 | feat(PROV1a): :Activity Neo4j entity + ActivityDAO + ProvenanceService + Provena | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1b | FILE | #1382 | closed | v6.0.0-rc.1 | feat(PROV1b): Per-entity provenance trail | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1c | FILE | #1383 | open | v6.x-backlog | feat(PROV1c): On-demand stats aggregation + `GET /v2/provenance/stats?scope=inst | gate 1 (external-visible): row prefix PROV1 is admin/user-surface (ambiguous status) |
| PROV1c2 | SKIP | — | parked | — | — | parked/superseded/decommissioned |
| PROV1c-acl | FILE | #1384 | closed | v6.0.0-rc.1 | feat(PROV1c-acl): Per-Collection Read-permission gate on GET /v2/provenance/stat | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1-content-stats | FILE | #1385 | closed | v6.0.0-rc.1 | feat(PROV1-content-stats): contentCensus field on /v2/provenance/stats | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1-content-stats-2 | FILE | #1386 | closed | v6.0.0-rc.1 | feat(PROV1-content-stats-2): byteTotals field on /v2/provenance/stats | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1d | FILE | #1387 | closed | v6.0.0-rc.1 | feat(PROV1d): Frontend per-Collection sparkline dashboard (Vue + vanilla SVG, no | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1e | FILE | #1388 | open | v6.x-backlog | feat(PROV1e): Instance-admin all-instance dashboard | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1f | FILE | #1389 | closed | v6.0.0-rc.1 | feat(PROV1f): Nightly retention TTL job (shepard | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1g | FILE | #1390 | closed | v6.0.0-rc.1 | feat(PROV1g): PROV-N JSON export (Accept: application/prov+json; W3C PROV-JSON s | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1h | FILE | #1391 | closed | v6.0.0-rc.1 | feat(PROV1h): metadata4ing (m4i) content-negotiation on /v2/provenance/{activiti | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| MNT1 | FILE | #1392 | closed | v6.x-backlog | feat(MNT1): shepard mount as a network drive | gate 1 (external-visible): row prefix MNT1 is admin/user-surface |
| V2S1 | FILE | #1393 | closed | v6.x-backlog | feat(V2S1): v2 API simplification + output profiles + MCP-friendly OpenAPI | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| V2S1a | FILE | #1394 | open | v6.x-backlog | feat(V2S1a): OutputProfile enum + OutputProfileResolver request-scoped bean + Ou | gate 1 (external-visible): row prefix V2 is admin/user-surface (ambiguous status) |
| V2S1b | FILE | #1395 | open | v6.x-backlog | feat(V2S1b): Apply the profile shape to every other /v2/ IO record | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| CG1 | FILE | #1396 | closed | v6.x-backlog | feat(CG1): OpenAPI client generators | gate 1 (external-visible): row prefix CG1 is admin/user-surface |
| CG1b | FILE | #1397 | closed | v6.x-backlog | feat(CG1b): OpenAPI Generator legacy maintenance | gate 1 (external-visible): row prefix CG1 is admin/user-surface |
| UI1 | FILE | #1398 | closed | v6.x-backlog | feat(UI1): Lefthand-tree drag-and-drop (move default, copy on modifier) | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| UI2 | FILE | #1399 | closed | v6.x-backlog | feat(UI2): Navigable Collection graph view (cytoscape | gate 1 (external-visible): row prefix UI2 is admin/user-surface |
| UI3 | FILE | #1400 | closed | v6.x-backlog | feat(UI3): @-mention autocomplete for internal entity citations | gate 1 (external-visible): row prefix UI3 is admin/user-surface |
| QW1 | FILE | #1401 | closed | v6.x-backlog | feat(QW1): Global search bar in HeaderBar | gate 1 (external-visible): row prefix QW is admin/user-surface |
| QW2 | FILE | #1402 | closed | v6.x-backlog | feat(QW2): Sidebar data-object filter | gate 1 (external-visible): row prefix QW is admin/user-surface |
| QW3 | FILE | #1403 | closed | v6.x-backlog | feat(QW3): JupyterHub URL in user profile pane | gate 1 (external-visible): row prefix QW is admin/user-surface |
| QW4 | FILE | #1404 | closed | v6.x-backlog | feat(QW4): Git credentials shortcut in GitReferencesPane | gate 1 (external-visible): row prefix QW is admin/user-surface |
| QW5 | FILE | #1405 | closed | v6.x-backlog | feat(QW5): Publish deep-link tooltip | gate 1 (external-visible): row prefix QW is admin/user-surface |
| QW6 | FILE | #1406 | closed | v6.x-backlog | feat(QW6): Admin metrics card — surface GET /v2/admin/metrics-summary in the /ad | gate 1 (external-visible): row prefix QW is admin/user-surface |
| UI4 | FILE | #1407 | closed | v6.x-backlog | feat(UI4): Snapshots UI — create/list/delete snapshots per collection; manifest  | gate 1 (external-visible): row prefix UI4 is admin/user-surface |
| UI5 | FILE | #1408 | closed | v6.x-backlog | feat(UI5): Templates browser + instantiation | gate 1 (external-visible): row prefix UI5 is admin/user-surface |
| UI6 | FILE | #1409 | closed | v6.x-backlog | feat(UI6): Video reference inline viewer | gate 1 (external-visible): row prefix UI6 is admin/user-surface |
| UI7 | SKIP | — | queued (PV1a) | — | — | no gate-1 surface (internal refactor / docs-only) |
| UI8 | FILE | #1410 | closed | v6.x-backlog | feat(UI8): RO-Crate export download button | gate 1 (external-visible): row prefix UI8 is admin/user-surface |
| UI9 | FILE | #1411 | closed | v6.x-backlog | feat(UI9): Snapshot diff viewer | gate 1 (external-visible): row prefix UI9 is admin/user-surface |
| UI10 | FILE | #1412 | open | v6.x-backlog | feat(UI10): Inline attach-to-data-object from container view | gate 1 (external-visible): row prefix UI1 is admin/user-surface (ambiguous status) |
| UI11 | FILE | #1413 | open | v6.x-backlog | feat(UI11): Unified publish/Unhide status panel | gate 1 (external-visible): row prefix UI1 is admin/user-surface (ambiguous status) |
| UI13 | FILE | #1414 | open | v6.x-backlog | feat(UI13): DLR brand theming — apply DLR visual identity (brand colors, logo, t | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| TS56 | FILE | #1415 | closed | v6.x-backlog | feat(TS56): Replace Apache ECharts with uPlot in all timeseries chart components | gate 1 (external-visible): row prefix TS56 is admin/user-surface |
| UI12 | FILE | #1416 | closed | v6.x-backlog | feat(UI12): Mobile + tablet responsive audit | gate 1 (external-visible): row prefix UI12 is admin/user-surface |
| CP1a | FILE | #1417 | closed | v6.x-backlog | feat(CP1a): CollectionProperties Neo4j entity + CollectionPropertiesDAO (findByC | gate 1 (external-visible): row prefix CP1 is admin/user-surface |
| CP1b | FILE | #1418 | closed | v6.x-backlog | feat(CP1b): GET /v2/collections/{appId}/properties (read; requires Read on paren | gate 1 (external-visible): row prefix CP1 is admin/user-surface |
| CP1c | FILE | #1419 | open | v6.x-backlog | feat(CP1c): Wire webdavVisible into the MNT1 WebDAV mount path; Collection becom | gate 1 (external-visible): row prefix CP1 is admin/user-surface |
| ONT1 | FILE | #1420 | closed | v6.x-backlog | feat(ONT1): Pre-seed bundle additions (umbrella) | gate 1 (external-visible): row prefix ONT1 is admin/user-surface |
| ONT1b | FILE | #1421 | closed | v6.x-backlog | feat(ONT1b): Add metadata4ing (NFDI4Ing) v1 | gate 1 (external-visible): row prefix ONT1 is admin/user-surface |
| CAD1 | FILE | #1422 | closed | v6.x-backlog | feat(CAD1): 3D Geometry & FEM Annotator (shepard-plugin-cad) | gate 1 (external-visible): row prefix CAD1 is admin/user-surface |
| CPACS1 | FILE | #1423 | closed | v6.x-backlog | feat(CPACS1): CPACS Annotator (shepard-plugin-cpacs) | gate 1 (external-visible): row prefix CPACS1 is admin/user-surface |
| RCE1 | FILE | #1424 | closed | v6.x-backlog | feat(RCE1): RCE Integration with Provenance Tracking (shepard-plugin-rce) | gate 1 (external-visible): row prefix RCE1 is admin/user-surface |
| MTX1 | SKIP | — | **design pending** | — | — | no gate-1 surface (internal refactor / docs-only) |
| SB1 | FILE | #1425 | closed | v6.x-backlog | feat(SB1): Spatial Data Binding | gate 1 (external-visible): row prefix SB1 is admin/user-surface |
| PC1+SB2 | FILE | #1426 | closed | v6.x-backlog | feat(PC1+SB2): Point cloud integration + live overlay modalities | gate 1 (external-visible): row prefix PC1 is admin/user-surface |
| IL1 | SKIP | — | **design sketch** | — | — | no gate-1 surface (internal refactor / docs-only) |
| DT1 | FILE | #1427 | closed | v6.x-backlog | feat(DT1): Live Digital Twin — moving-frame annotations, composite scene, WebSoc | gate 1 (external-visible): row prefix DT1 is admin/user-surface |
| TM1 | SKIP | — | S | — | — | no gate-1 surface (internal refactor / docs-only) |
| VID2 | SKIP | — | **design sketch** | — | — | no gate-1 surface (internal refactor / docs-only) |
| REF1 | FILE | #1428 | closed | v6.x-backlog | feat(REF1): DBpedia Databus rich-reference plugin (preview / description / title | gate 1 (external-visible): row prefix REF1 is admin/user-surface |
| AI1q | FILE | #1429 | closed | v6.x-backlog | feat(AI1q): query_knowledge_graph SPARQL tool for Lumen | gate 1 (external-visible): row prefix AI1q is admin/user-surface |
| BIZ1 | SKIP | — | **design pending decision | — | — | no gate-1 surface (internal refactor / docs-only) |
| CC1a | FILE | #1430 | closed | v6.x-backlog | feat(CC1a): Collapsible "Containers" section at the bottom of the collection sid | gate 1 (external-visible): row prefix CC1 is admin/user-surface |
| CC1b | FILE | #1431 | closed | v6.x-backlog | feat(CC1b): "Referenced by" expansion panel on each container detail page (File, | gate 1 (external-visible): row prefix CC1 is admin/user-surface |
| CC1c | FILE | #1432 | closed | v6.x-backlog | feat(CC1c): Default file-container name pre-filled as "<Collection name> | gate 1 (external-visible): row prefix CC1 is admin/user-surface |
| MCPGW-SYNC1 | FILE | #1433 | open | v6.x-backlog | feat(MCPGW-SYNC1): Move existing SyncService into shepard-plugin-mcp; the MCP ga | gate 1 (external-visible): row prefix MCPGW is admin/user-surface |
| MCPGW-SYNC2 | FILE | #1434 | open | v6.x-backlog | feat(MCPGW-SYNC2): Config UI for sync: list of services + their sync state + add | gate 1 (external-visible): row prefix MCPGW is admin/user-surface |
| MCPGW-SYNC3 | FILE | #1435 | open | v6.x-backlog | feat(MCPGW-SYNC3): "Additional services on lobe require explicit confirmation" | gate 1 (external-visible): row prefix MCPGW is admin/user-surface |
| ID-MIG1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| ID-MIG2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| ID-MIG3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| ID-MIG4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| ID-MIG5 | SKIP | — | gated | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-OPT1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-OPT2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-OPT3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-OPT4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-OPT5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-OPT6 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-INV1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-BP1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-AP1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-AP2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DB-DOC1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| TOOL-SM1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPORT-NS1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPORT-NS2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPORT-SR1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPORT-DBG1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPORT-Q7-VERIFY | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| TERM1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| TERM2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| TERM3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IOT1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IOT2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IOT3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IOT4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPORT-W1 | FILE | #1436 | closed | v6.0.0-rc.1 | feat(IMPORT-W1): v15 | gate 1 (external-visible): row prefix IMPORT-W is admin/user-surface |
| IMPORT-W2 | FILE | #1437 | closed | v6.0.0-rc.1 | feat(IMPORT-W2): Unexpected-reply abort + structured diagnostic report | gate 1 (external-visible): row prefix IMPORT-W is admin/user-surface |
| IMPORT-W3 | FILE | #1438 | closed | v6.0.0-rc.1 | feat(IMPORT-W3): OpenAPI-driven wire-shape comparator helper (Python) | gate 1 (external-visible): row prefix IMPORT-W is admin/user-surface |
| TRACE-A | SKIP | — | in-progress | — | — | no gate-1 surface (internal refactor / docs-only) |
| TRACE-B | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| TRACE-C | SKIP | — | blocked | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-S1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-T1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-V1 | SKIP | — | `arrows` \ | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-X1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-C1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-F1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-S2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| VIS-S3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| M4I-a | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| M4I-b | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| M4I-c | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| M4I-d | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| M4I-e | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| M4I-f | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| DOC-STAGE1 | FILE | #1439 | closed | v6.0.0-rc.1 | docs(DOC-STAGE1): Taxonomy doc (aidocs/00-doc-stages | gate 1 (external-visible): row prefix DOC-STAGE is admin/user-surface |
| DOC-STAGE2 | FILE | #1440 | open | v6.x-backlog | docs(DOC-STAGE2): Pre-commit hook + CI step that runs python3 scripts/regenerate | gate 1 (external-visible): row prefix DOC-STAGE is admin/user-surface |
| DOC-STAGE3 | FILE | #1441 | open | v6.x-backlog | docs(DOC-STAGE3): Per-stage filter view in the Pages site | gate 1 (external-visible): row prefix DOC-STAGE is admin/user-surface |
| API1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMP1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMP2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPL3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| MFG5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| EXP1o | FILE | #1442 | open | v6.x-backlog | refactor(EXP1o): Refactor ExportService payload-kind dispatch to a StrategyPatte | gate 1 (external-visible): row prefix EXP1 is admin/user-surface |
| J1g | FILE | #1443 | open | v6.x-backlog | refactor(J1g): Refactor LabJournalEntryRest endpoint functions into the LabJourn | gate 1 (external-visible): row prefix J1 is admin/user-surface |
| PERF5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| PERM1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| SD1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UI14 | FILE | #1444 | open | v6.x-backlog | feat(UI14): "Shared with me" section on PersonalDigest | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| UI15 | FILE | #1445 | open | v6.x-backlog | feat(UI15): Design-system pass on StepperDialog disabled-state colour | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| V1C1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| V1C2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| AAS1l | FILE | #1446 | open | v6.x-backlog | feat(AAS1l): AAS plugin admin-runtime config endpoint (/v2/admin/aas/config) + C | gate 1 (external-visible): row prefix AAS1 is admin/user-surface |
| G1h | FILE | #1447 | open | v6.x-backlog | feat(G1h): Short-circuit redundant resolveRef calls in GitHub + GitLab adapters  | gate 1 (external-visible): row prefix G1 is admin/user-surface |
| IMPL1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPL2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPL4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMPL5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| VID1c | FILE | #1448 | open | v6.x-backlog | feat(VID1c): video plugin admin-runtime config (/v2/admin/video/config) + CLI pa | gate 1 (external-visible): row prefix VID1 is admin/user-surface |
| PM1g | FILE | #1449 | open | v6.x-backlog | feat(PM1g): Promote the PluginContext | gate 1 (external-visible): row prefix PM1 is admin/user-surface |
| UH1f | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| AI1s | FILE | #1450 | open | v6.x-backlog | feat(AI1s): Migrate AI1c channel-quality scoring from a Neo4j primitive attribut | gate 1 (external-visible): row prefix AI1 is admin/user-surface |
| PROV1i | FILE | #1451 | open | v6.x-backlog | feat(PROV1i): SnapshotService | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1j | FILE | #1452 | open | v6.x-backlog | feat(PROV1j): Backend consumes X-AI-Agent header + surfaces _provenanceMode on v | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1k | FILE | #1453 | open | v6.x-backlog | feat(PROV1k): Typed-predecessor rework heuristics | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| PROV1l | FILE | #1454 | open | v6.x-backlog | feat(PROV1l): Agent-side opt-out of identity inclusion in provenance capture (GD | gate 1 (external-visible): row prefix PROV1 is admin/user-surface |
| IMP-W4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| IMP-W5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| LDGR1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| SHACL2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| MFFD-INFLUX1 | FILE | #1455 | open | v6.x-backlog | feat(MFFD-INFLUX1): InfluxDB → Shepard timeseries export bridge for the MFFD v5  | gate 1 (external-visible): row prefix MFFD is admin/user-surface |
| KIP1i | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| KIP1j | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| KIP1k | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| KIP1l | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR5 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR6 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR7 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| FAIR8 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-SEARCH1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-BULK1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-COMP1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| PERF6 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| PERF7 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| PERF8 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-PAGE1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| PERF9 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| PERF10 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-PROV1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-PIN1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-ANNO1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-AM1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UX-SUPER1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| UI16 | FILE | #1456 | open | v6.x-backlog | feat(UI16): Extract shared useLineageGraph(nodes, edges) composable; merge colou | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| UX-SEARCH2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| API2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| API3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| MFG1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| MFG2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| MFG3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| MFG4 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| V2g | FILE | #1457 | open | v6.x-backlog | feat(V2g): DataObject-granularity snapshot retrieval | gate 1 (external-visible): row prefix V2 is admin/user-surface |
| N1h | FILE | #1458 | open | v6.x-backlog | feat(N1h): Numeric semantic annotations | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1i | FILE | #1459 | open | v6.x-backlog | feat(N1i): Per-file / per-document annotation surface (sub-container granularity | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| N1j | FILE | #1460 | open | v6.x-backlog | feat(N1j): Causal edge type between command-DO and TimeseriesAnnotation | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| MAT1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| N1k | FILE | #1461 | open | v6.x-backlog | feat(N1k): Add CHAMEO + SSN/SOSA + IEC 61360 / ECLASS bundles to OntologySeedSer | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| T1i | FILE | #1462 | open | v6.x-backlog | feat(T1i): Built-in EquipmentItem ShepardTemplate | gate 1 (external-visible): row prefix T1 is admin/user-surface |
| UI17 | FILE | #1463 | open | v6.x-backlog | feat(UI17): Lot-lineage query panel | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| UI18 | FILE | #1464 | open | v6.x-backlog | feat(UI18): Mandatory unit picker in AddChannelDialog (QUDT browser) | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| UI19 | FILE | #1465 | open | v6.x-backlog | feat(UI19): skos:related sidebar in AddAnnotationDialog | gate 1 (external-visible): row prefix UI1 is admin/user-surface |
| N1l | FILE | #1466 | open | v6.x-backlog | feat(N1l): Opt-in admin job to refresh stale SemanticAnnotation | gate 1 (external-visible): row prefix N1 is admin/user-surface |
| AI1t | FILE | #1467 | open | v6.x-backlog | feat(AI1t): PDF / document auto-annotation quick-win using AI plugin TEXT capabi | gate 1 (external-visible): row prefix AI1 is admin/user-surface |
| AI1u | FILE | #1468 | open | v6.x-backlog | feat(AI1u): pgvector embeddingVector column on DataObject + nightly backfill job | gate 1 (external-visible): row prefix AI1 is admin/user-surface |
| TEST1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| SHACL3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| WATCH2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| J1h | FILE | #1469 | open | v6.x-backlog | feat(J1h): JupyterHub-integration open questions | gate 1 (external-visible): row prefix J1 is admin/user-surface |
| OR1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| EASA1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| EU-MR1 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| L9 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| L10 | FILE | #1470 | open | v6.x-backlog | feat(L10): Backend follow-up: configurable IdP-claim path for admin-CLI auth (cu | gate 1 (external-visible): row prefix L1 is admin/user-surface |
| CC1d | FILE | #1471 | open | v6.x-backlog | feat(CC1d): "Link to existing container" one-click option in the DataObject add  | gate 1 (external-visible): row prefix CC1 is admin/user-surface |
| CC1e | FILE | #1472 | open | v6.x-backlog | feat(CC1e): Container list "linked from" breadcrumb column | gate 1 (external-visible): row prefix CC1 is admin/user-surface |
| CC2 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| CC3 | SKIP | — | queued | — | — | no gate-1 surface (internal refactor / docs-only) |
| GH-INFRA1 | FILE | #1473 | closed | v6.0.0-rc.1 | docs(GH-INFRA1): Templates + CODEOWNERS + Dependabot + canonical labels + releas | gate 1 (external-visible): row prefix GH-INFRA is admin/user-surface |
| GH-INFRA2 | FILE | #1474 | open | v6.x-backlog | docs(GH-INFRA2): Manually create the Projects v2 board following docs/ops/github | gate 1 (external-visible): row prefix GH-INFRA is admin/user-surface |
| GH-INFRA3 | FILE | #1475 | open | v6.x-backlog | docs(GH-INFRA3): Cut v6 | gate 1 (external-visible): row prefix GH-INFRA is admin/user-surface |
| GH-INFRA4 | FILE | #1476 | open | v6.x-backlog | docs(GH-INFRA4): aidocs/16 ↔ GitHub Issues sync tooling | gate 1 (external-visible): row prefix GH-INFRA is admin/user-surface |
| GH-INFRA5 | FILE | #1477 | open | v6.x-backlog | docs(GH-INFRA5): GitHub Environments for nuclide | gate 1 (external-visible): row prefix GH-INFRA is admin/user-surface |
| GH-PM1 | FILE | #1478 | closed | v6.0.0-rc.1 | docs(GH-PM1): The policy doc itself + scripts/trace-feature | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM2 | FILE | #1479 | open | v6.x-backlog | docs(GH-PM2): Wire scripts/build-traceability-index | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM3 | FILE | #1480 | open | v6.x-backlog | docs(GH-PM3): Pre-commit hook ( | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM4 | FILE | #1481 | open | v6.x-backlog | docs(GH-PM4): Nightly drift check: cron-driven gh issue list --search "in:title  | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| FOCUS-2026-05-23-2 | SKIP | — | queued | — | — | prefix-skip: FOCUS- (focus-capture, not a work item) |
| FOCUS-2026-05-23-1 | SKIP | — | queued | — | — | prefix-skip: FOCUS- (focus-capture, not a work item) |
| GH-PM-DATASET | FILE | #1482 | open | v6.x-backlog | docs(GH-PM-DATASET): Capture the GH-PM5 backfill batch INTO Shepard as a synthet | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM5 | FILE | #1483 | closed | v6.0.0-rc.1 | docs(GH-PM5): Backfill from aidocs/16 ↔ Issues + Milestones + Releases on policy | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-RDM-1 | FILE | #1484 | open | v6.x-backlog | docs(GH-PM-ENH-RDM-1): Extend scripts/trace-feature | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-RDM-2 | FILE | #1485 | open | v6.x-backlog | docs(GH-PM-ENH-RDM-2): Add aidocs/data/00-model-inventory | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-RDM-3 | FILE | #1486 | open | v6.x-backlog | docs(GH-PM-ENH-RDM-3): Per-row Personas: column on aidocs/16 rows linking person | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-RDM-4 | FILE | #1487 | open | v6.x-backlog | docs(GH-PM-ENH-RDM-4): Extend §12 release-notes step 4 to cite persona findings  | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-RDM-5 | FILE | #1488 | open | v6.x-backlog | docs(GH-PM-ENH-RDM-5): Add commit: field to CITATION | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-RDM-6 | FILE | #1489 | open | v6.x-backlog | docs(GH-PM-ENH-RDM-6): RO-Crate manifest at release-tag boundary for any release | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-1 | FILE | #1490 | open | v6.x-backlog | docs(GH-PM-ENH-API-1): §2 mapping table → CI MUST fail on | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-2 | FILE | #1491 | open | v6.x-backlog | docs(GH-PM-ENH-API-2): §3 gate #4 → tighten to MUST NOT auto-file for purely-int | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-3 | FILE | #1492 | open | v6.x-backlog | docs(GH-PM-ENH-API-3): §4 → CI MUST reject milestone moves until source mileston | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-4 | FILE | #1493 | open | v6.x-backlog | docs(GH-PM-ENH-API-4): §5 #4 → release pipeline MUST abort when build-traceabili | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-5 | FILE | #1494 | open | v6.x-backlog | docs(GH-PM-ENH-API-5): §6 PR-template checkbox-presence CI lint | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-6 | FILE | #1495 | open | v6.x-backlog | docs(GH-PM-ENH-API-6): §7 Pin scope grammar ^[A-Z][A-Za-z0-9-](\+[A-Z][A-Za-z0-9 | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-7 | FILE | #1496 | open | v6.x-backlog | docs(GH-PM-ENH-API-7): §8 Issue-close webhook MUST verify aidocs/16 Status == do | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-8 | FILE | #1497 | open | v6.x-backlog | docs(GH-PM-ENH-API-8): §9 board setting MUST disallow manual card additions; aut | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-9 | FILE | #1498 | open | v6.x-backlog | docs(GH-PM-ENH-API-9): §10 PR-template enforces one-label-per-axis via dropdown  | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-10 | FILE | #1499 | open | v6.x-backlog | fix(GH-PM-ENH-API-10): §13 issue-template security-finding | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-11 | FILE | #1500 | open | v6.x-backlog | docs(GH-PM-ENH-API-11): §14 trace-feature | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-12 | FILE | #1501 | open | v6.x-backlog | docs(GH-PM-ENH-API-12): §15 anti-pattern #4 → CI MUST reject PR titles whose sub | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-13 | FILE | #1502 | open | v6.x-backlog | docs(GH-PM-ENH-API-13): Add trace-feature | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-API-14 | FILE | #1503 | open | v6.x-backlog | docs(GH-PM-ENH-API-14): Promote aidocs/platform/106 from stage: idea to stage: f | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-STRAT-1 | FILE | #1504 | open | v6.x-backlog | docs(GH-PM-ENH-STRAT-1): v6 | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-STRAT-2 | FILE | #1505 | open | v6.x-backlog | docs(GH-PM-ENH-STRAT-2): Tripwire — when monthly external PR authors hit 3 (not  | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-Q5-1 | FILE | #1506 | open | v6.x-backlog | docs(GH-PM-ENH-Q5-1): scripts/bootstrap-gh-project | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| GH-PM-ENH-Q3-1 | FILE | #1507 | open | v6.x-backlog | docs(GH-PM-ENH-Q3-1): scripts/trace-all-shipped | gate 1 (external-visible): row prefix GH-PM is admin/user-surface |
| OBS-MFFD1 | FILE | #1508 | closed | v6.0.0-rc.1 | feat(OBS-MFFD1): scripts/mffd-import-stats-collector | gate 1 (external-visible): row prefix OBS-MFFD is admin/user-surface |
| OBS-MFFD2 | FILE | #1509 | open | v6.x-backlog | feat(OBS-MFFD2): Generalise the collector to ANY import job: lift the MFFD-speci | gate 1 (external-visible): row prefix OBS-MFFD is admin/user-surface |
| OBS-MFFD3 | FILE | #1510 | open | v6.x-backlog | feat(OBS-MFFD3): Dashboard view summarising all in-flight imports: list every Im | gate 1 (external-visible): row prefix OBS-MFFD is admin/user-surface |
