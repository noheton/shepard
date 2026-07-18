---
stage: fragment
last-stage-change: 2026-07-18
author: db-cross-cutting-auditor
task: DB-AP2
supersedes-partial: DB-AP1 finding #8 (:Timeseries 198 vs channel_metadata 136), finding #9 (storageBackend NULL)
---

# DB-AP2 — Cross-Cutting Substrate Audit — 2026-07-18

Sibling to DB-AP1 (`db-antipattern-hunt-2026-07-18.md`, per-substrate). This pass
hunts patterns that **span substrates** — dual/triple source-of-truth,
substrate-mismatch, schema-evolution gaps that cross the Neo4j/Postgres/Mongo/S3
boundary, and cross-substrate `appId` consistency. All queries substrate-direct,
**read-only**, `LIMIT`/count-bounded, run *during* the live MFFD tapelaying ingest.
No writes, no locks, no full scans.

**Substrates reached direct:** Neo4j 5.26 (`infrastructure-neo4j-1`), TimescaleDB/
PG16 (`infrastructure-timescaledb-1`, via `shepard`/`shepard_secret`), MongoDB 8.0.4
(`infrastructure-mongodb-1`), Garage v1.0.1 (`shepard-garage`).

---

## Severity-ranked summary

| # | Severity | Pattern (cross-cutting class) | Substrates | Concrete site + counts | One-line fix |
|---|----------|------------------------------|------------|------------------------|--------------|
| 1 | **MAJOR** | **Backfill-without-write-path-fix** (identity regression) | Neo4j + migration ledger | `V82`/`V12` backfilled channel/file `appId`; live shows **0 / 198** `:Timeseries` and **0 / 505,759** `:ShepardFile` with `appId` — *after* V82 is recorded applied in `__Neo4jMigration`. V82's own comment falsely claims "new rows receive UUID-v7 appIds from the Minter service on creation." | Fix the write path (mint on child construction), *then* re-backfill. Add a range-index-backed degree/NULL watch so the regression is caught at ingest, not at audit. |
| 2 | **MAJOR** | **OGM cascade-save bypasses `appId` minting** (shared root cause of #1) | Neo4j | `GenericDAO.createOrUpdate(T entity)` mints `appId` only on the *top-level* entity (line ~125-127) then `session.save(entity, DEPTH_ENTITY)` cascades children un-minted. `:Timeseries` created via `ref.addTimeseries(new ReferencedTimeseriesNodeEntity(ts))`; `:ShepardFile` via `new ShepardFile(oid,…)` attached to a `FileContainer`. Neither child ever transits the minting branch. | Mint `appId` at child-entity construction (constructor or a pre-save `@PostLoad`-style walk), or add a `wireAppIds(entity)` depth-walk in `createOrUpdate` before `session.save`. |
| 3 | **MAJOR** | **Triple representation of the 5-tuple** (best-effort dual-write, no atomic boundary) | Postgres + Neo4j (×2) | The channel 5-tuple lives in **three** places: PG `channel_metadata` (198), Neo4j `:Timeseries` node properties (198), and Neo4j `:SemanticAnnotation` nodes under `:AnnotatableTimeseries` (198). `TimeseriesSemanticDualWriteService` mirrors PG→Neo4j **best-effort** (`catch (Throwable)` → WARN, "Postgres write unaffected"; TODO(TS-SEMANTIC-02) flags hot-path risk for bulk MFFD ingest). No shared transaction, no reconciliation job. | Adopt the outbox/CDC shape: single atomic write to the authoritative substrate (PG), derive Neo4j annotations from a durable outbox, not a fire-and-forget mirror. Add a nightly reconciliation count. |
| 4 | **MAJOR** | **No cross-substrate join key for a timeseries channel** | Postgres ↔ Neo4j | Neo4j `:Timeseries` has `appId = NULL` (all 198), so the *only* correlation to PG `timeseries.shepard_id` is 5-tuple string matching. Worse, there are **two** Neo4j nodes per channel — `:Timeseries` (`appId` NULL) and `:AnnotatableTimeseries` (`appId = shepard_id`) — and **they are not linked to each other**. Traversing reference→annotations requires a PG round-trip. | Set `:Timeseries.appId = timeseries.shepard_id` on write (closes both #1 and the split-brain); or link `:Timeseries -[:HAS_ANNOTATABLE]-> :AnnotatableTimeseries`. |
| 5 | **MINOR→MAJOR** | **Channel identity is UUID v4, not the mandated v7** | Postgres → Neo4j | `timeseries.shepard_id` is minted by PG `gen_random_uuid()` = **v4** (version nibble = 4, confirmed on 3 rows). That v4 is propagated verbatim into `:AnnotatableTimeseries.appId` and `SemanticAnnotation.subjectAppId`. `V82` backfill also mints v4 (`randomUUID()`, comment admits it). Every other entity uses v7 via `AppIdGenerator.next()`. | Accept v4 as a documented exception for TS channels, OR mint v7 app-side and let PG mirror it. Either way stop asserting "new rows get v7" — they get v4 (or NULL). |
| 6 | **MINOR** | **189 GiB of S3 bytes keyed by a legacy Mongo ObjectId** | Neo4j → Garage/Mongo | `:ShepardFile.oid` values are 24-hex Mongo ObjectIds (e.g. `6a3249110aa57b3dd9cc60fd`); Garage object keys derive from `oid`. With `appId = NULL` (#1) and `storageBackend = NULL` (AP1 #9), the **entire byte layer is threaded by a Mongo-native id** with no `shepardId` and no per-file backend stamp anywhere. | Stamp `appId` + `storageBackend` per file (FS1e sweep); treat `oid` as an internal locator, not the cross-substrate identity. |
| 7 | **INFO** (reconciliation) | **`:User` dual identity** — reconciles the AP1↔NEO-AUDIT id discrepancy | Neo4j | `:User` @Id = `username` (Keycloak sub; for admin the UUID-looking `7eead942-…`) **plus** a separate `appId` (v7 `019ed452-…`). Same node. DB-AP1 cited the username; the `NEO-AUDIT-…-ACTIVITY-SUPERNODE` backlog row cited the appId and asked for reconciliation — this is it. | None (working as designed). Record which id crosses which boundary: permission edges + `SHEPARD_PERMISSIONS_DEFAULT_OWNER` key on `username`; `appId` is the wire `shepardId`. |

**Verified clean / two-views-of-one-truth (do not re-flag):**
- **5-tuple sets are byte-identical across substrates right now** — `comm` diff of 198 Neo4j `:Timeseries` tuples vs 198 PG `channel_metadata` tuples: **0 divergent, 198 common**. No *active* drift; the risk in #3/#4 is structural, not realised.
- **AP1's "198 vs 136" was ingest lag, not drift.** Live now reads `:Timeseries` 198 = `channel_metadata` 198 = core `timeseries` 198 = distinct `shepard_id` 198. The dual-write *caught up*; the 136 was a mid-ingest lag window (which is itself the exact interval a reader gets an inconsistent answer — a smaller MINOR sub-note under #3).
- **PG↔Neo4j soft-FK by appId is consistent.** `permission_audit_log`: 9,280/9,280 rows carry both `app_id` and `entity_app_id`; a sampled `entity_app_id` (`019f7185-…`) resolves to a real Neo4j node (`:StructuredDataContainer:BasicEntity:BasicContainer`). The cross-substrate reference is a soft-FK (no enforced constraint) but the values are sound.
- **`timeseries.shepard_id` self-heals via column DEFAULT.** Because PG lets you attach `DEFAULT gen_random_uuid()`, new PG rows *never* NULL — the exact opposite of the Neo4j `appId` gap, which has no substrate-level default and so depends entirely on the (broken) app write-path. Good contrast, not a finding.

---

## What I found

**The headline corrects AP1.** DB-AP1 concluded "the 5-tuple debt is largely paid
off" because it inspected *one* table (PG core `timeseries`) and found the 5-tuple
correctly evicted to `channel_metadata`. The **cross-substrate view is the opposite
conclusion**: the 5-tuple is evicted from core PG `timeseries` but is **alive in
three places** — PG `channel_metadata`, Neo4j `:Timeseries` node properties, and
Neo4j `:SemanticAnnotation` nodes. Eviction from one table is not eviction from the
system.

**The `appId`-NULL story is a live regression, not legacy debt (findings #1, #2).**
Migrations `V12__Backfill_appId` and `V82__mint_timeseries_appids` are both recorded
applied in `__Neo4jMigration`. V82 backfilled 596 NULL `:Timeseries.appId` → 0 on
2026-05-24 and its header comment asserts *"New Timeseries rows receive UUID-v7
appIds from the Minter service on creation."* Yet the live graph shows **0 of 198
`:Timeseries` and 0 of 505,759 `:ShepardFile` carry an `appId`** — every one is NULL.
The current channel/file population was created *after* V82 (a `--reset`/re-ingest),
through the OGM cascade write-path, which never mints child-node appIds. The
migration fixed the snapshot; the write path re-manufactures the NULLs. This is
**exactly the shape of the `providerId` bug that `V79`/`V34` already documented**
("V34 introduced providerId… but only covered rows that pre-dated V34; every
ShepardFile written between V34 and the write-path stamp had providerId = NULL") —
except `providerId` got a *real write-path fix* (`Objects.requireNonNull` in
`FileContainerService.createFile`), whereas `appId` on these child nodes did not. The
same institution learned the lesson for one field and re-committed the error on
another.

**Root cause is shared and structural (#2).** `GenericDAO.createOrUpdate(T entity)`
mints `appId` only for the single `entity` argument, then delegates to OGM
`session.save(entity, DEPTH_ENTITY)`, which cascade-persists child nodes without
re-entering the minting branch. Any `HasAppId` node that is only ever saved *as a
cascaded child of a parent* — `:Timeseries` (added to a `TimeseriesReference`),
`:ShepardFile` (attached to a `FileContainer`) — is born with `appId = NULL`. This is
one bug with two large blast radii (198 + 505,759), not two bugs.

**Timeseries channel identity is a three-body problem (#3, #4, #5).** One logical
channel is represented by: (a) PG `timeseries` row (`shepard_id` UUID **v4** + FK to
`channel_metadata` 5-tuple), (b) Neo4j `:Timeseries` node (5-tuple props, `appId`
NULL), (c) Neo4j `:AnnotatableTimeseries` node (`appId = shepard_id`) carrying one
`:SemanticAnnotation` per 5-tuple field. (b) and (c) are both Neo4j, both "the
channel", and **not linked to each other**; (b) has no id that reaches (a). The
`TsChannelResolver` docstring even declares *"The Neo4j side is uninvolved — channels
live entirely in Postgres/Timescale"* — yet (b) holds the 5-tuple and is the target
of the AP1 finding-#2 `has_payload` fan-in supernode (8,262 refs). A node that claims
to be uninvolved is in fact the densest channel node in the graph.

**The byte layer has no shepardId (#6).** 189 GiB across 436k Garage objects is keyed
by `:ShepardFile.oid`, a legacy Mongo ObjectId. With `appId` and `storageBackend`
both NULL, nothing on the file entity is a `shepardId` and nothing records which
backend holds the bytes — resolution rides entirely on the global `activeStorage()`.

## Opportunities

- **One fix closes four findings.** Minting `:Timeseries.appId = timeseries.shepard_id`
  at write time (fixing #2) simultaneously: gives the node a shepardId (#1), creates
  the missing PG↔Neo4j join key (#4), and lets the two Neo4j nodes be joined on a
  shared id (#4 split-brain). Do it in the write path first, then one re-backfill
  migration mops up the current NULLs.
- **Replace the best-effort mirror with an outbox (#3).** `TimeseriesSemanticDualWriteService`
  is a textbook dual-write. The durable fix is the transactional-outbox / CDC shape:
  commit the PG channel row + an outbox row atomically, derive the Neo4j annotations
  from the outbox. Until then, ship a nightly reconciliation count (`:Timeseries` vs
  `channel_metadata` vs `:AnnotatableTimeseries`) so a lag/failure surfaces on a
  dashboard, not in an audit six weeks later.
- **A cross-substrate `appId` NULL-watch.** A cheap per-label `count(n) - count(n.appId)`
  sweep (all `HasAppId` labels) would have caught #1 the moment the write-path
  regressed. Pair it with the AP1 degree-watch.

## Ideas

- **Constraint that bites:** Neo4j uniqueness constraints permit NULL, so
  `appId_unique_Timeseries` is "satisfied but meaningless" (V82's words). An
  `appId IS NOT NULL` **existence constraint** (Neo4j 5 enterprise) on the addressable
  labels would fail-fast the write-path regression instead of silently accreting NULLs.
- **Migration lint rule:** a backfill migration (`SET x.field = …`) that touches a
  field with no corresponding write-path guard is a code smell. A CI check that pairs
  every `V##__backfill_*` with a test asserting the *write path* also populates the
  field would have blocked both the `providerId` and the `appId` regressions.
- **Assert the ID scheme in one place.** A test that samples every substrate's
  "shepardId-bearing" column and asserts UUID **v7** would have caught #5 (the PG
  `gen_random_uuid()` v4 leak) at build time.

## Real-world impact

- **MFFD ingest is manufacturing the debt right now.** Every channel and file the live
  tapelaying ingest writes lands with `appId = NULL`. A future consumer that addresses
  a channel by `shepardId` (the MCP tools, the `/v2/` appId contract, any FAIR-R1
  cross-substrate query) cannot reach these 198 channels or 505k files by their
  mandated identity — only by 5-tuple or `oid`. This is a growing `TS-CORE-SCHEMA-01`-
  style migration liability, exactly the failure the "every entity carries a shepardId"
  rule exists to prevent.
- **A single Neo4j hiccup during bulk ingest = silent, permanent divergence.** #3's
  best-effort mirror catches and swallows Neo4j failures while the PG write commits.
  Under the "don't parallelize heavy ingests → pool exhaustion → 504" failure mode
  already in project memory, a Neo4j write can fail transiently — and the channel's
  semantic annotations are then permanently absent with only a WARN in the log. The
  data model has no way to detect this after the fact (no shared txn, no reconciler).
- **Provenance/annotation queries can't cheaply cross the seam.** Because `:Timeseries`
  (the reference payload node) has no id reaching PG or the `:AnnotatableTimeseries`
  sibling, "show me the annotations on the channel this reference points to" is a
  three-substrate dance, not one traversal — undercutting the "audit trail is a graph"
  promise for timeseries.

## Gaps & blockers

- **Write-path fix needs a backend rebuild**, which per RESUME is gated on the
  Jandex-build issue — same constraint as `NEO-AUDIT-…-ACTIVITY-SUPERNODE`. The
  re-backfill migration and the reconciliation job can land independently.
- **Could not confirm whether `:ShepardFile.appId` NULL breaks the
  `/v2/containers/{appId}/payload/{fileAppId}/…` endpoints** (which take a `fileAppId`)
  vs. the `SingletonFileReference` layer (52,996, all `appId`) being the real
  addressable surface. Left as a follow-up for DB-OPT4 / the FS owners — the empirical
  NULL is confirmed; the wire-impact needs a functional probe not run under the
  read-only ingest constraint.
- **No enforced cross-substrate FK exists** (by design — polyglot). The `permission_audit_log`
  soft-FK is consistent *today*; nothing prevents a future orphan. Out of scope to fix
  here; noted for the reconciliation-job idea.

## What surprised me

- **The fork already solved this exact bug once and re-introduced it.** `V79`'s comment
  is a near-perfect post-mortem of the `providerId` backfill-without-write-path-fix —
  and the `appId` gap on the *same entity class* (`:ShepardFile`) is the identical
  mistake, unfixed. The lesson was documented but not generalised into a guard.
- **A node that documents itself as "uninvolved" is the graph's densest channel node.**
  `TsChannelResolver` says Neo4j is uninvolved in channel identity; the `:Timeseries`
  node holds the full 5-tuple *and* is the 8,262-edge `has_payload` fan-in supernode
  from AP1. The comment and the data disagree.
- **The AP1 "198 vs 136 drift" evaporated.** It was a lag window, not a standing
  inconsistency — a useful reminder that a point-in-time count taken mid-ingest can
  read as "drift" when it is really "eventual consistency mid-flight." The structural
  risk (#3) is real; that particular number was not.
- **Postgres quietly does the right thing that Neo4j structurally cannot.** `shepard_id`
  self-heals via a column `DEFAULT`; Neo4j has no equivalent, so app-layer minting is
  load-bearing — and load-bearing code that only runs on the top-level `createOrUpdate`
  argument silently skips every cascaded child.

---

## Devil's-advocate notes (per finding)

1. **Backfill regression (MAJOR):** *Is NULL appId on an internal node actually harmful?*
   Only if something addresses these nodes by `appId`. Today most channel access is
   5-tuple/`shepard_id`-keyed, so the pain is deferred — but the `/v2/` contract and MCP
   tools are appId-native, and the ingest is minting the debt at 500k+ scale. MAJOR
   because it's actively growing and violates a load-bearing invariant, not because it's
   breaking a query *today*.
2. **Cascade minting gap (MAJOR):** *Maybe child nodes are deliberately identity-less
   payload.* Plausible for `:Timeseries` (a shared, deduped-by-5-tuple node) — but both
   implement `HasAppId` with an `@Property("appId")`, and V82 spent a migration trying to
   populate it, so the *intent* is clearly that they carry one. The bug is the write path,
   not the intent.
3. **Triple 5-tuple / dual-write (MAJOR, currently 0 drift):** *These are two views of one
   truth, not an inconsistency.* True **right now** (0 divergent tuples). But the mirror is
   explicitly best-effort and non-transactional, so the guarantee is "consistent until the
   first swallowed Neo4j failure." Severity is about the *unguarded* structure and the
   documented bulk-ingest risk, not a realised drift.
4. **No join key (MAJOR):** *5-tuple matching works.* It does, but it's O(string) and
   breaks the instant any substrate's 5-tuple is corrected out of band (then a "corrected"
   reference mints a *second* `:Timeseries` node, splitting the channel). A shared id is the
   cheap structural fix.
5. **v4 vs v7 (MINOR→MAJOR):** *Both are unique UUIDs; who cares about the version nibble?*
   The v7 mandate exists for time-sortable locality (index/range-scan friendliness) and a
   single ID scheme across substrates. A v4 among v7s is a contract deviation, not a
   correctness bug — MINOR, leaning MAJOR only because it's the cross-substrate *identity*
   primitive and the migration comment misrepresents it.
6. **oid-keyed bytes (MINOR):** The global `activeStorage()` resolves fine until a provider
   flip; `oid` is a perfectly good internal locator. The smell is purely the *absence* of a
   shepardId/backend stamp as belt-and-braces — the FS1e sweep is the intended net.
7. **User dual identity (INFO):** Working as designed — `username` is the natural OIDC
   upsert key; `appId` is the wire id. Included only to close the reconciliation the backlog
   row asked for.

## Sources

- Dual-write hazard + outbox/CDC: [Confluent — The Dual-Write Problem](https://www.confluent.io/blog/dual-write-problem/);
  [Thorben Janssen — Dual Writes: The Unknown Cause of Data Inconsistencies](https://thorben-janssen.com/dual-writes/);
  [AWS Prescriptive Guidance — Transactional Outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html).
  Directly models `TimeseriesSemanticDualWriteService` (finding #3): two independent
  systems, no atomic boundary, best-effort mirror → silent inconsistency; fix is
  single-atomic-write + outbox/CDC derivation.
- Schema evolution — expand→migrate→contract: [LogRocket — Migrate a DB schema at scale](https://blog.logrocket.com/how-to-migrate-a-database-schema-at-scale/);
  [Bytebase — How to Handle Schema Change](https://www.bytebase.com/blog/how-to-handle-database-schema-change/).
  The safe order is *fix write-path (expand) → backfill (migrate) → contract*; the
  Shepard `appId`/`providerId` regressions inverted it (backfill without expand), which
  is precisely what the pattern prevents (findings #1, #2).
- PostgreSQL column-add semantics: [PG16 — Adding a Column / Default Values](https://www.postgresql.org/docs/16/ddl-alter.html#ddl-alter-adding-a-column)
  (pg-aiguide). Confirms new rows get NULL unless a `DEFAULT` is attached — why PG
  `shepard_id` self-heals (column default) and Neo4j `appId` cannot (no substrate
  default; app-layer minting is load-bearing) — the contrast under finding #5 / "clean".
