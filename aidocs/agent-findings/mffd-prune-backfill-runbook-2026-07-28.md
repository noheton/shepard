---
stage: feature-defined
last-stage-change: 2026-07-28
author: neo4j-ops-runbook-agent
task: MFFD-GRAPH-PRUNE, ACTIVITY-SUPERNODE-BACKFILL, CHILD-APPID-BACKFILL
---

# MFFD prune + two supernode backfills — operator runbook (2026-07-28)

**PLAN ONLY. This runbook executed ZERO mutations.** Every number below is a
read-only substrate-direct measurement or a `PROFILE` (read-only) run against
live Neo4j 5.26.26 Community on `infrastructure-neo4j-1`. APOC 5.26.26 present.

The three mutations were deferred out of the 2026-07-19 deploy after the naive
`MATCH…MERGE` agent-edge backfill hung backend startup for 7 min / 0 commits
(O(degree)/row MERGE on the ~3.3M-degree `:User` supernode). This runbook is the
tuned, batched, offline replacement.

Deliverable ops files (cited throughout):
- `scripts/ops/backfill-agent-acted-in-month-2026-07-28.cypher` (additive)
- `scripts/ops/prune-mffd-tombstones-2026-07-28.cypher` (destructive)
- `scripts/ops/BackfillShepardFileAppId.java` (additive; JVM-minted v7)

---

## What I found

### Ground truth (measured 2026-07-28, not the weeks-old RESUME estimates)

| Quantity | RESUME estimate | **Measured 2026-07-28** |
|---|---|---|
| Soft-deleted DataObjects (`deleted=true`) | ~3178 | **3,185** (live: 10,443) |
| References under dead DOs | — | **8,108** (all carry appId) |
| SemanticAnnotations to prune (subjectAppId ∈ dead-DO ∪ dead-ref appIds) | ~1088 "otvis" | **26,445** (11,400 DO-subject + 15,045 ref-subject) |
| Activities total | — | **3,332,648** |
| Activities missing agent edge **but backfillable** (have `WAS_ASSOCIATED_WITH`) | ~2.87M | **3,008,314** |
| Activities with agent edge already (write-path) | — | **313,567** |
| `:ShepardFile` with NULL appId | ~567k | **567,658** (155,762 already set) |

Soft-deleted DOs by collection:

| Collection | appId | dead DOs |
|---|---|---|
| mffd-bridge-welding | `019ed455-6781-755e-87dd-eb3f2f3dbba3` | 2,077 |
| mffd-ndt-thermography | `019ed455-6866-71f1-b0bf-0f83a3e3aaa9` | 1,101 |
| mffd-stringer-welding | `019edb10-c107-7473-ae28-ffc592aba860` | 5 |
| CASCADE-GATE-fixA (scratch) | `019f7e28-5342-7ece-acc3-07cd5b18bdc7` | 1 |
| PERF4-1784640815384 (scratch) | `019f84e1-c121-7afc-9b5f-e9e467f65c44` | 1 |

### The single most important finding: the snapshot target in the task is wrong-scope

The task's `POST /v2/collections/019f4bf2-176f-7f4c-b3e2-5de837bf20af/snapshots`
resolves to **`MFFD-Dropbox`**, which contains **0** soft-deleted DataObjects
(verified). Snapshotting it protects *nothing this prune deletes*. The tombstones
live in the three `mffd-*` collections above. **The prune's snapshot prerequisite
must target those three, not MFFD-Dropbox** (details under Sequence).

### Prune safety verified (read-only), not asserted

| Check | Query intent | Result | Meaning |
|---|---|---|---|
| CHECK 1 | live DO `-[has_successor\|has_child]-` dead DO | **0** | no live lineage severed by `DETACH DELETE` of a dead DO |
| CHECK 2 | live DO whose `typedPredecessorsJson` CONTAINS a dead appId | **0** | predecessors are a JSON *property* (index `dataobject_typed_predecessors_json`), which `DETACH DELETE` cannot clean — 0 means no dangling refs are created |
| CHECK 3 | annotations-to-prune also subject of a *live* entity | **0** | `subjectAppId` is 1:1; no live annotation is collateral |
| Version | dead DOs sharing the per-collection HEAD `:Version` | **3,185 / 3,185** | all share HEAD → **never delete the Version node**; `DETACH DELETE` the DO/ref only |

The Version finding is the load-bearing landmine (`feedback_never_raw_delete_shared_version`):
one `:Version {isHEADVersion:true}` per collection is shared by the Collection +
every DataObject + every Reference. `DETACH DELETE d` / `DETACH DELETE r` removes
only that node's *own* `has_version` edge; the shared HEAD survives. A raw
`DELETE v` would 404 the entire collection.

### Write-path shapes I aligned to

- Agent edge: `ActivityDAO.AGENT_ACTED_IN_MONTH_CYPHER` = `MATCH (u:User{username})
  MATCH (a:Activity{appId}) WHERE NOT (a)<-[:agent_acted_in_month]-(u) CREATE …`.
  `ym` = `String.format("%04d%02d", utcYear, utcMonth)` from `a.startedAtMillis`.
  My backfill reproduces this exactly via `apoc.temporal.format(datetime({epochMillis:…}),'yyyyMM')`.
- appId: `AppIdGenerator.next()` = `com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch()`
  → RFC 9562 UUID **v7**. Neo4j `randomUUID()` / `apoc.create.uuid()` are **v4**
  only (APOC 5.26 confirmed) → the mint **cannot** be pure Cypher.

### PROFILE evidence — O(1)/row vs the O(degree) trap

```
SAFE agent-edge (Activity-side guard, read-only proxy for CREATE):
  200  candidate rows -> 1,208 DbHits  (~6/row)
  2,000 candidate rows -> 12,008 DbHits (~6/row)     => LINEAR, O(1)/row  ✓

TRAP (07-19 shape) — cost of expanding from the :User side:
  ONE service :User already has 313,560 outgoing agent_acted_in_month edges
  -> 313,628 DbHits to scan them ONCE. A MERGE that expands from :User pays
     that PER ROW, and grows to ~3M -> O(degree)/row.               ✗

PRUNE traversal per dead DO (refs + annos):  200 DOs -> 4,504 DbHits (~22/row) ✓
PRUNE annotation delete via subjectAppId idx: 200 DOs ->   795 DbHits (~4/row) ✓
appId NULL-scan (label scan):                5,000    -> 10,001 DbHits (~2/row) ✓
```

The safe form's linearity (1,208→12,008 as rows 200→2,000) is the proof the task
asks for. The 313,628-DbHits single-node scan is the smoking gun for why 07-19
hung.

---

## Opportunities

### Why the safe agent-edge form escapes the supernode-lock trap

Three independent properties, all necessary:

1. **Drive from the bounded side.** Iterate `MATCH (a:Activity)-[:WAS_ASSOCIATED_WITH]->(u:User)`
   — the guard `NOT (a)<-[:agent_acted_in_month]-(u)` checks `a`'s *incoming*
   agent-edge degree (≤1) = O(1). The 07-19 form checked `u`'s *outgoing* degree
   (millions) = O(degree). [Neo4j Cypher `MERGE` locking][merge], [dense-node
   concurrent access][concurrent].
2. **`CREATE`, never `MERGE`.** `CREATE` does not scan `u`'s relationship chain
   to test existence; the Activity-side `WHERE NOT` guard supplies idempotency
   instead. `MERGE (u)-[…]->(a)` on a dense `u` re-scans + double-locks per row
   ([Neo4j KB: diagnose locking][locking]).
3. **`CALL {} IN TRANSACTIONS OF 10000 ROWS`** ([Cypher manual][callintx]) so each
   batch commits and *releases* the `:User` exclusive lock, bounding heap; combined
   with an **offline/paused** window the per-row `:User` lock is *uncontended*
   (single writer) — no deadlock. Under the live 8-worker ingest the same edge
   write deadlocks (AGENT-EDGE-DEADLOCK), which is exactly why this is offline.

> **Batching structure matters (subtle, load-bearing).** The driving `MATCH` must
> sit **before** the `CALL`, so the subquery receives ~3M input rows and commits
> every 10,000. If the `MATCH` were **inside** the `CALL`, the subquery would get
> one implicit row → run **once** → all ~3M CREATEs in a **single transaction** =
> the exact non-streaming, 0-commits-for-minutes shape of the 07-19 hang. The
> shipped `.cypher` uses `MATCH … WITH … CALL { WITH … CREATE … } IN TRANSACTIONS`.
> (The prune's `DETACH DELETE` steps use the same corrected structure; there it is
> benign — each step is ≤15k rows — but kept batched for a truthful claim.)

The APOC twin (`apoc.periodic.iterate`, [docs][apoc]) is equivalent **only with
`parallel:false`** — `parallel:true` would run N threads each exclusive-locking
the one `:User` → the deadlock storm again.

### The prune is completeness-correct by `subjectAppId`, not by edge

38,015 FileReference-subject annotations exist but only 31,155 `has_annotation`
edges from references — an edge-only traversal would *miss* ref-subject annos that
lack the edge. Driving deletion by the indexed `subjectAppId` (union of dead-DO
appIds and dead-ref appIds) is both complete (`feedback_completeness_nonnegotiable`)
and O(1)/row (`SemanticAnnotation_subjectAppId_idx`, 795 DbHits/200).

### appId backfill can preserve monotonic sort trivially

v7 embeds a ms timestamp; minting at backfill-time (matching `AppIdGenerator.next()`
semantics — appId marks *assignment* time) keeps the whole column one UUID version
and sortable. I considered seeding v7 from each file's `createdAt` (truthful
embedded timestamp) but rejected it: it diverges from the write-path (which mints
at storeFile-time) and buys nothing the `oid`/`createdAt` properties don't already
give. Keep it simple: current-instant v7.

---

## Ideas

- **Promote the appId backfill to `shepard-admin files backfill-appid`.** The JVM
  mint requirement makes a CLI command the natural home (reuses `AppIdGenerator`
  directly, no classpath wiring). File `CHILD-APPID-CLI` in aidocs/16. The ops
  `.java` is the interim.
- **Bake the "pause → snapshot → export → prune/backfill → verify" window into a
  single `scripts/ops/run-maintenance-window.sh` wrapper** with a dry-run mode that
  prints all counts first. Turns three careful manual runs into one reviewable log.
- **Add a `ProvenanceConfig.agentMonthEdgeEnabled` runtime flag** (per the admin
  runtime-knob rule) so the agent edge can be disabled during heavy concurrent
  ingest without a redeploy — the clean fix for AGENT-EDGE-DEADLOCK that
  PROV-ASYNC-WRITE will supersede.
- **Regression fixture for the migration ledger:** a testcontainer that seeds a
  dead-DO subtree + a mini `:User` fan-in and asserts (a) prune leaves the HEAD
  Version, (b) backfill is O(1)/row (assert DbHits/row bound). Tracks the aidocs/34
  test obligation for all three rows.

---

## Real-world impact

- **Prune** removes 3,185 tombstones + 8,108 dead refs + 26,445 orphan
  annotations. The orphan annotations are the real cost: they pollute every
  cross-cutting annotation query (the FAIR-R1 "one Cypher query" promise) and
  inflate provenance counts. The DIN EN 9100 lineage story rests on live
  Predecessor/Successor chains — CHECK 1/2 prove the prune does not touch them.
- **Agent-edge backfill** makes "who did what, when" (agent+month) answerable by a
  bounded `ym`-indexed rel-scan instead of walking a 3.3M-degree supernode — the
  EU AI Act Art. 50 / "audit trail is a graph" query surface, retroactively
  complete back to the first Activity.
- **appId backfill** closes the last v4-legacy hole: 567,658 `:ShepardFile` blobs
  become addressable by the single cross-substrate `shepardId`, so file payloads
  are reachable/annotatable by appId like every other entity.

---

## Gaps & blockers

1. **Snapshot scope mismatch (BLOCKER for the prune as written).** The provided
   appId is MFFD-Dropbox (0 tombstones). The three real collections must be
   snapshotted instead. **Not yet fired.**
2. **Dest JWT expired.** The June key is dead; the snapshot `POST`s need a fresh
   JWT (RESUME "Hot artefacts").
3. **appId backfill needs a JVM host with the backend classpath** (or the CLI
   command). Pure-Cypher is impossible (v7). Documented in the `.java` header.
4. **Ingest must be paused/quiesced** for all three — the tapelaying ingest was
   ~74% at last RESUME; confirm it has completed/stopped (`touch /tmp/mffd-runner.stop`
   + SIGINT) before opening the window. The agent-edge write deadlocks under
   concurrency.
5. **Rollback of a `DETACH DELETE` cannot be a hand-written `V(N)_R__` twin, and a
   Shepard snapshot does NOT back it either.** A `:Snapshot` is a manifest of
   `(entityAppId, revision)` scalar pairs (`SnapshotEntry`) — it copies no node
   data, and `revision` is an in-place counter, so after a hard delete the manifest
   entry dangles. The snapshot is an audit **boundary marker**, not a data backup.
   The **primary** rollback is a full **offline `neo4j-admin database dump`** (taken
   in the paused window before deleting — Community has no online backup; stopping
   Neo4j also enforces the single-writer precondition). The **secondary** is a
   pre-delete `apoc.export.cypher` of the delete subgraph (STEP 0), which **must**
   return the surviving neighbour nodes (Collection, HEAD Version, ShepardFile) and
   the connecting relationships as explicit variables — otherwise the has_dataobject
   / has_version / has_payload edges (whose other endpoint survives) are dropped and
   a replay restores orphaned, versionless DataObjects.
6. **These do NOT belong in `MigrationsRunner`.** V121 correctly shipped index-only;
   V122 is a NOOP. All three live under `scripts/ops/` and run manually against a
   paused instance. Putting a multi-million-row backfill in the fail-fast startup
   runner is the exact 07-19 mistake.

### Opposing-lens arguments (argued before recommending)

**Prune — "leave the tombstones, they're harmless":** they carry `deleted=true`,
the v2 read path filters them, and keeping them preserves a delete audit trail.
*Rebuttal:* the 26,445 orphan annotations are NOT filtered by `deleted` — they are
live `:SemanticAnnotation` nodes whose subject is a corpse, so they leak into every
`subjectKind`/vocabulary aggregation and distort the annotation counts the FAIR
surface reports. The delete audit lives in `:Activity` (untouched), not in the
tombstone. **Net: prune, but only behind an offline dump + export.**

**Backfills — "requires full maintenance downtime" vs "do it online in a paused
window":** full downtime is the safe default but over-kills — reads are unaffected;
only *writes* to the `:User`/`:ShepardFile` nodes contend. *Resolution:* a
**paused-ingest window with the instance still serving reads** is sufficient. The
only hard requirement is a single writer (the ops script), which pausing the
ingest achieves. No user-facing downtime needed. (If the ops box cannot guarantee
the ingest is stopped, then take the stronger downtime — a deadlock mid-backfill
is a fail-fast abort that re-runs cleanly, so the risk is throughput, not
correctness.)

---

## What surprised me

- **The provided snapshot appId protects nothing.** MFFD-Dropbox holds 0 of the
  3,185 tombstones. Had the operator followed the task literally, the "safety"
  snapshot would have been theatre. Always resolve the snapshot scope against the
  actual delete set.
- **A single `:User` already carries 313,560 `agent_acted_in_month` edges** (from
  the 313k write-path Activities) — the supernode the index was meant to relieve is
  itself becoming a second fan-out. The index makes it *queryable*; it does not make
  the node less dense. PROV-ASYNC-WRITE is the real long-term fix.
- **The "37,903 orphaned annotations" trap.** My first orphan definition ("no
  incoming `has_annotation` edge") returned 37,903 — but those are valid
  DataObject-/Collection-subject annotations that attach by `subjectAppId`
  *property*, not by edge (only References + AnnotatableTimeseries get the edge).
  Pruning by edge-orphan would have destroyed 31,034 live DataObject annotations.
  The correct orphan test is `subjectAppId ∈ dead set`. Measure the model before
  trusting an intuition about it.
- **A Shepard snapshot is not a delete-backup.** It is a manifest of
  `(entityAppId, revision)` scalar pointers with no data copy, and `revision` is
  in-place — so after a hard `DETACH DELETE` the manifest entry dangles and can
  restore nothing. I initially wrote the snapshot as the prune's *primary*
  rollback; it is only a boundary marker. On Community the real rollback is a full
  offline `neo4j-admin database dump`. Reading `SnapshotService`/`SnapshotEntry`
  before trusting the word "snapshot" caught this.
- **The naive "find dangling annotations" query (`NOT EXISTS { MATCH (e) WHERE
  e.appId = s.subjectAppId }`) timed out at 2 min** — O(N×M) with no index to seek
  the correlated `appId`. A perfect miniature of the 07-19 lesson: a "cleanup"
  query can itself be the O(degree) landmine. The indexed `subjectAppId ∈ collect(...)`
  form is O(1)/row instead.

---

## GO / NO-GO + exact operator sequence

**Prerequisite for ALL:** confirm the tapelaying ingest is stopped
(`touch /tmp/mffd-runner.stop`, SIGINT, verify no `mffd-import` process; the
`.mffd-import.lock` released). Mint a fresh dest JWT.

**Order:** PRUNE → APPID-BACKFILL → AGENT-EDGE-BACKFILL. Prune first (fewest edges,
snapshot-gated, shrinks the ShepardFile/Activity working set slightly). The two
backfills are order-independent; do appId first (fast, additive, low-risk) to
build confidence before the ~3M-edge agent backfill.

1. **PRUNE — `scripts/ops/prune-mffd-tombstones-2026-07-28.cypher`.**
   a. Fire a Shepard snapshot (boundary marker) on **all three** real collections
   (`019ed455-6781-…`, `019ed455-6866-…`, `019edb10-c107-…`) with the fresh JWT —
   **not** MFFD-Dropbox.
   b. **Primary rollback:** stop Neo4j → `neo4j-admin database dump neo4j` → copy the
   `.dump` off the container → start Neo4j. (This also enforces single-writer.)
   c. **Secondary rollback:** run STEP 0 (widened `apoc.export.cypher` of the delete
   subgraph incl. neighbour nodes); confirm the file is non-empty and copy it off.
   d. Run steps 1→3; confirm all four post-checks return 0 (esp. `liveDOsMissingHEAD = 0`).

2. **CHILD-APPID-BACKFILL — `scripts/ops/BackfillShepardFileAppId.java`** (or the
   `shepard-admin files backfill-appid` command). Run to `DONE, 0 remaining`.
   Verify: `MATCH (f:ShepardFile) WHERE f.appId IS NULL RETURN count(f)` → 0.

3. **ACTIVITY-SUPERNODE-BACKFILL — `scripts/ops/backfill-agent-acted-in-month-2026-07-28.cypher`.**
   Verify index present (preflight), run, confirm `remainingBackfillable = 0`.
   Monitor from a second shell: `MATCH ()-[r:agent_acted_in_month]->() RETURN count(r)`
   climbs 313,567 → ~3,321,881.

**GO/NO-GO:**
- **PRUNE — GO, conditional.** Safety checks all pass (0 live-lineage, 0 dangling
  predecessor JSON, 0 live-annotation conflict, HEAD Version preserved by
  DETACH-DELETE-node-only). **Condition:** take a full offline `neo4j-admin
  database dump` (primary rollback) + the widened `apoc.export.cypher` (secondary)
  first, and fire the boundary-marker snapshots on the three *correct* collections
  (not MFFD-Dropbox — it holds 0 tombstones). Without the dump, **NO-GO** — it is
  the one destructive, non-reconstructable mutation and the snapshot does not back it.
- **CHILD-APPID-BACKFILL — GO.** Additive, nullable-safe, convergent/idempotent,
  JVM-minted spec-correct v7. No rollback needed (documented). Only blocker is a
  JVM host with the backend classpath.
- **ACTIVITY-SUPERNODE-BACKFILL — GO.** Additive, idempotent, PROFILE-proven
  O(1)/row, matches the write-path shape exactly. **Condition:** offline/paused
  window (single writer) so the `:User` lock is uncontended — this is the sole
  reason the identical write online deadlocks.

[callintx]: https://neo4j.com/docs/cypher-manual/current/subqueries/subqueries-in-transactions/
[apoc]: https://neo4j.com/docs/apoc/current/overview/apoc.periodic/apoc.periodic.iterate/
[concurrent]: https://neo4j.com/docs/operations-manual/current/database-internals/concurrent-data-access/
[merge]: https://neo4j.com/docs/cypher-manual/current/clauses/merge/
[locking]: https://neo4j.com/developer/kb/diagnose-locking-issues/
[rfc9562]: https://www.rfc-editor.org/rfc/rfc9562.html

---

## EXECUTION RECORD — 2026-07-28 (appended after the operator approved the two additive backfills)

Operator chose the **two additive backfills only**; the destructive **PRUNE remains
NOT RUN** (still gated on an offline `neo4j-admin` dump + boundary snapshots on the
three tombstone-holding collections).

### ✅ CHILD-APPID-BACKFILL — done
`scripts/ops/BackfillShepardFileAppId.java`, compiled against the backend runtime
classpath (`mvn dependency:build-classpath`; note the class is in package
`de.dlr.shepard.ops`, and the neo4j driver needs its full transitive bolt-connection
set — a bare driver jar fails with `NoClassDefFoundError`).

- **567,658** `:ShepardFile` appIds minted, **< 45 s**, batch 5,000.
- Pre-verified `NodeByIdSeek` (3 db-hits / 3 rows) — the `MATCH (f:ShepardFile) WHERE
  id(f)=p.id` form was checked *before* the run precisely because a label-scan-per-row
  would have been catastrophic.
- Post-verified: **723,420** ShepardFiles, **all** with appId, **0** null; 200,000-row
  sample **all distinct** and **all version-7**.

### ✅ ACTIVITY-SUPERNODE-BACKFILL — done, but the runbook query was WRONG

**The plan in this document was not safe as written, and PROFILE caught it.**

This runbook asserted the driving query was "O(1)/row proven" and its inline comment
said it drives "from the BOUNDED Activity side". Neither was true of the actual Cypher:

```
MATCH (a:Activity)-[:WAS_ASSOCIATED_WITH]->(u:User)   -- single pattern
WHERE ... AND NOT (a)<-[:agent_acted_in_month]-(u)
```

The COST planner chose `NodeByLabelScan(u:User)` → `Expand(All)` over the `:User`
supernode's ~3.3M `WAS_ASSOCIATED_WITH` edges → `AntiSemiApply` + `Expand(Into)`.

| Form | db-hits @200 rows | @2,000 rows |
|---|---|---|
| Runbook (single pattern) | **2,183,547** | 2,197,047 |
| Corrected (split pattern) | **995** | 9,067 (~4.5/row, linear) |

A ~2.18M **fixed** cost — the same supernode-driven shape that hung backend startup on
07-19. Corrected in `21c6dc72a` by filtering Activities first, then expanding to the
user; the guard uses the anonymous `-()` form (verified **0** Activities have >1
`WAS_ASSOCIATED_WITH` user, so it is semantically identical and cheaper).

Executed with the corrected form:
- **3,008,314** edges created, **< 60 s**, `CALL {} IN TRANSACTIONS OF 10000`.
- Post-check remaining backfillable **0**; **0** activities with duplicate edges;
  `ym` values sane (202606–202607); total edges 3,321,896.
- Backend healthy throughout (401 in 23 ms after; container up, no restart).

### Lesson

`PROFILE` the *actual statement text you are about to run*, not the shape you intended
to write. A comment claiming a bounded drive is not a plan — the planner decides, and
on a supernode it will happily pick the expensive side. This is the second time in ten
days the same trap appeared (07-19 startup hang, and again here in the very script
written to avoid it).
