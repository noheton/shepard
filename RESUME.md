# RESUME — current worklog

**Updated:** 2026-05-27 ~00:35 UTC by claude-sonnet-4-6 with operator fkrebs@nucli.de
**Active arc:** MFFD showcase — Track A real data ingest pending redeploy; Track B synthetic seed DONE.
**Status:** Wave 16 complete (all pushed, commit fbdb73724). Backend Maven build in progress (PID 3652168, Quarkus augmentation phase, ~60min elapsed, still running at 100% CPU nice=5). Track A: AFP export running on cube3; framewelding today. Track B: **SEED COMPLETE** — Collection 987758 on nuclide has 16 DOs + 8 TS refs + 5 structured + 5 file refs (commit 4b4554bd7). Awaiting backend redeploy to activate Wave 14/15/16 changes. TPL2b+c+d are ALL DONE on main (7d9884ed7, 643d271dc, 065094e04).

---

## Immediate next action

**Waiting on:** Maven build PID 3652168 to complete → `make image-backend && cd infrastructure && docker compose up -d --force-recreate shepard-backend && make wait-for-health && make smoke`. Then run `make redeploy-frontend` to activate PERF9 channel virtualization.

**After redeploy:**
1. Verify Wave 16 live: UX-PROV1 ancestor chain panel on MFFD synthetic Collection 987758, PERF9 TS channel list, PERF5 batch N+1 fix
2. Track A: task #145 — fix fileRef parser bug for 8462 MFFD-Dropbox DOs (needs fresh DLR JWT from user-side)
3. task #146 — v1-compat Phase 1 follow-up (filter scope + DAO session timing + smoke gate)
4. task #137 — Mutate MFFD-Dropbox collection (Confluence zip → per-page DOs + TOC)

## Hot artefacts (verify before recommending)

| Artefact | ID | Notes |
|---|---|---|
| MFFD-Dropbox collection | 661923 / `019e55f3-75fb-7ef3-84fc-6238566b63ea` | dest on nuclide (v16 ingest target) |
| MFFD Synthetic Showcase | 987758 | 16 DOs, full process chain, seeded 2026-05-27 |
| ImportScripts DO | 515376 / `019e4e56-cc74-76ca-a811-9710d245e4c3` | self-uploaded by v14 |
| mffd-dropbox-import.py v14 | appId `019e50bd-9cbd-73d9-8f86-80f58930aaf1`, md5 `2c67de6ba38fbbc9a4842d7a5ac5b4dc`, 78262 bytes, FileRef id 578453 | latest version |
| MFFD-Dropbox cube source | tapelaying coll 48297, bridgewelding coll 163811 | on DLR cube3 |
| Source JWT (kreb_fl) | jti `9f6ffd2a-b720-4582-b016-8ec2662ef5f6`, iat 2026-05-22 18:38 UTC | churns every ~14h |
| Dest JWT (Flo Researcher) | jti `76c20c60-a35f-4024-87a6-1b54ddb371eb`, iat 2026-05-22 04:47 UTC | long-lived |
| MCP endpoint | `https://shepard.nuclide.systems/mcp/sse` | via Zoraxy virtual directory |

## Gotchas

- **DLR JWT churns every ~14h.** Mint fresh before each session if 401s. Source JWT in `project_mffd_api_keys.md`.
- **`/shepard/api/collections/.../fileReferences` may return [] when refs exist.** Use v2 paths (`/v2/data-objects/{appId}/files`) for ground truth. See `project_v5_list_visibility_bug.md`.
- **Warmup gate matches SESSION_ID exactly.** Use existing `import_ready` value or PATCH it to match next run.
- **v14 has three confirmed wire-shape bugs** (TS link order, structured wrong-method, multi-OID parser miss). Don't iterate v14 — fold into v15.
- **The MFFD raw export already lives on disk** at `examples/mffd-showcase/raw-data/mffd-data/` (10.9 GB, gitignored) — alternate source if cube3 dies.

## Decisions baked this session

- **Substrate split = end-state, not transition.** TPL4 dual-write is the bridge; the GOAL is the split (Neo4j keeps operational/identity only). See `feedback_shacl_single_source_of_truth.md`.
- **appId → shepardId rename** lands in tandem with substrate-split PRs, not as follow-up. See task #123 + `feedback_appid_to_shepardid.md`.
- **Trace3D = first concrete VIEW_RECIPE; library = TresJS** (Three.js Vue wrapper) per spike at `aidocs/agent-findings/trace3d-spike.md`. See task #142.
- **Plugin meta-ontology categories deferred.** See `project_plugin_categories.md`.
- **v15 is the LAST script iteration before plugin port.** PR-3 (`shepard-plugin-importer` `DLRv5Source`) productizes v15's lessons. See task #126 + #158.
- **Labor split:** I run nuclide-side, user runs DLR-cube-side. See `feedback_labor_split.md`.

## Substrate-split chain (active arc)

```
[domain artefact, low risk, you-led]
  #156 Predicate-mapping vocabulary table (~1d, parallel)

[backend, parallelisable, agent-delegable]
  #123 Java rename appId → shepardId
  #125 shepardId rename IO + FE + MCP
  #127 PR-2 n10s → Jena substrate

[the lift]
  #127 PR-5 attribute → annotation backfill

[runtime, small]
  #157 POST /v2/shapes/render + PROCESS_RECIPE/VIEW_RECIPE enum

[first VIEW_RECIPE consumer — TresJS chosen]
  #142 Trace3D AFP TCP thermal-trail (MFFD acceptance)

[the actual split]
  Post-deprecation V## migration: delete `attributes` field from V2
```

## Open background agents

1. **API Scrutinizer** ✅ done → `aidocs/agent-findings/api-scrutinizer-v14-import.md` — **20 bugs total** in v14
2. **Data Ontologist** ✅ done → `aidocs/agent-findings/data-ontologist-prov-o-v15.md`
3. **Cleanup preview** (bash `b403vjdxn`) — pagination stuck; needs smaller page size + retry
4. **aidocs consolidation survey** (Explore, in flight) → `aidocs/agent-findings/aidocs-consolidation-survey.md` — 188 docs, SSOT enforcement, numeric-collision resolution, current-vs-future tagging, orphan findings

## API Scrutinizer findings (load-bearing for v15)

**20 v14 bugs found (A–T).** Most critical:
- **Bug E (CRITICAL)** — `download_structured` mis-parses `StructuredDataPayload[]` (payload is JSON-encoded STRING inside wrapper, not the payload itself). **Every structured ref carries garbage end-to-end.**
- **Bug G** (compounds A) — `link_ts_to_do` called BEFORE `import_ts_csv`; even with correct body, channels are empty at link time. Whole step needs reordering: container → CSV imports → list channels → link.
- **Bug H** — `csv_format=WIDE` invalid on v5.4.0 source (enum is `{ROW, COLUMN}`). WIDE is fork-only. Source export fails or silently downgrades.
- **Bug I** — `_link_predecessor` PUTs to `/predecessors/{predId}` (doesn't exist). Predecessors set via `DataObject.predecessorIds[]` in body at POST/PUT.
- **Bug J** — v1 file-upload multipart→`/fileReferences` is wrong. Real v1 flow: `POST /fileContainers/{cid}/payload` → oid → JSON `POST /fileReferences` with `{name, fileOids:[oid], fileContainerId}`.
- **Bug L** — `create_ts_container` sends `type:"TIMESERIES"` (readOnly field).
- **Bugs O+P** — warmup probes send wrong-shape bodies (missing required fields); always 400 regardless of container state.
- **Bug K** — `/users/currentUser` endpoint doesn't exist (dead fallback).
- **Bug R** — only dest warmed up; source-side reachability fails late.

Full bug list + literal patches in the findings doc.

## Conflict resolved — n10s endpoint

**Data Ontologist proposed** `POST /v2/semantic/{repoAppId}/import` (Turtle body). **API Scrutinizer verified empirically** that endpoint doesn't exist. SparqlQueryValidator forbids writes; `/v2/provenance` is read-only.

**Verified by grep on /opt/shepard:**
- `/v2/semantic` → `SemanticSparqlRest` (read-only SPARQL proxy)
- `/v2/provenance` → `ProvenanceRest` (read-only — `@GET /activities` only)
- No `*Rest` class with POST + Turtle accept anywhere.

**v15 strategy (task #160):** use existing `POST /shepard/api/collections/{c}/dataObjects/{do}/semanticAnnotations` per-DO; emit prov:wasDerivedFrom + wasGeneratedBy + wasInformedBy as N typed annotations. Proper Turtle ingest = future fork task.

**Task #159** (Cypher migration for 8 new shepard: predicates) still applies — predicates lift to semanticAnnotation triples just as cleanly.

## Updated v15 BLOCKING dependencies

1. **#159** — Cypher migration adds 8 new `shepard:` predicates + role individuals  
2. **#160** — Use existing semanticAnnotations endpoint (no Turtle), accept v15 ships without `/v2/provenance/import` proper endpoint

Both can land in same PR-set with the script.

## Data Ontologist findings (load-bearing for v15)

- **n10s ingest endpoint:** `POST /v2/semantic/{repoAppId}/import` (Turtle body)
- **Agency:** AI `prov:actedOnBehalfOf` human — script is `fair2r:Source` + `prov:Plan`
- **IRI scheme:**
  - Activities/Sources/Users → instance-rooted (`https://shepard.nuclide.systems/id/activity/<uuid-v7>`) per `aidocs/platform/91`
  - AI agent → vendor namespace (`https://noheton.org/f-ai-r/agent/claude-opus-4-7`) — global, not per-instance
- **Pre-mint Activity UUIDs client-side** so retries are idempotent
- **Claim vs Entity:** typed DataObjects as `prov:Entity` + `wasGeneratedBy`; reserve `fair2r:Claim` for AI-interpreted facts
- **v15 always writes `verif:unverified`** — promotion happens downstream (shepard-plugin-ai for ai-confirmed, humans for human-confirmed via TPL9f widget)
- **Risk:** n10s `:Resource` label may not merge with `:DataObject` LPG nodes → duplicate shadows. Fallback: emit only the Activity + external IRIs; let PROV1a handle entity-side `wasGeneratedBy`

## v15 BLOCKING dependency — Cypher migration #159

8 new `shepard:` predicates + 2 role individuals need a migration BEFORE v15 ships:
`shepard:targetCollection`, `filesUploaded`, `timeseriesImported`, `structuredPayloads`, `batchSequence`, `throughputBytesPerSec`, `retryCount`, `sourceInstance` + role-executor, role-operator. See task #159.

## Open consolidation backlog

- #149 inspect dirty worktree a23a1610 (6 v2 REST files)
- #150 inspect dirty worktree a3a8672d (13 LIC1/heroImage files)
- #151 merge a02dc8eb (FS1e3 rollback)
- #152 merge a798dc76 (LIC1, closes #140)
- #153 merge a57fdb01 (V1COMPAT fixes, closes #146)
- #154 verify aa3378ed vs shipped #135
- #155 ✅ pruned 6 behind-only worktrees

## How to update this file

Update at every pivot:
- Active arc changes → rewrite "Active arc" line
- New hot artefact → add row to Hot artefacts table
- New decision → add to "Decisions baked"
- Agent completes → move from "Open background agents" to a summary line
- Session ending → save final state; next /resume reads this file

Author: claude-opus-4-7 on behalf of fkrebs@nucli.de. Format: live worklog, not append-only journal.
