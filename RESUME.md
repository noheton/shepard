# RESUME — current worklog

**Updated:** 2026-05-28 ~16:35 UTC by claude-opus-4-7 with operator fkrebs@nucli.de
**Active arc:** **POST-RESET** — full-instance reset (runbook 13) executed on nuclide; stack rebuilt from scratch with Keycloak users + admin singletons preserved. LUMEN + MFFD synthetic re-seed pending operator long-lived API key (needs flo to sign in once + mint). Backend image stays at 2026-05-26 cut (post-#207 Permissions-seed fix); Jandex hang investigation remains deferred.
**Status:** Wipe footprint: Neo4j 317 MB → 8 KB; Mongo 949 MB → empty; TimescaleDB 1.8 GB → empty; anon postgres + garage_data + garage_meta volumes destroyed. Pre-reset counts snapshot at `/tmp/preset-20260528-181423/`: 17 Collections / 17,324 DataObjects / 22,126 ShepardFiles / 22,336 BasicRefs / 238,525 SemanticAnnotations / 328,618 Activities / 379,771 Resources / 5 Users. Post-reset: 0 of each EXCEPT 1 User (flo bootstrapped pre-startup) + V49 preseed (Vocabulary×10, Resource×10, OntologyAlignment×12) + admin singletons (LegacyV1Config, SemanticConfig, InstanceRorConfig, SqlTimeseriesConfig, BootstrapState, _ShepardMigrationContext, SemanticRepository, Role). Public reachability green: shepard.nuclide.systems 200, shepard-api.nuclide.systems/healthz/ready 200, shepard-auth realm 200, /shepard/doc/openapi/v2.json 200.

Two real upstream-of-upgrader bugs uncovered during the reset and shipped on `main` (`1508a50a4` + `b2d40b525`):
1. `V20__Add_appId_constraint_GitCredential.cypher` used SQL `--` comments — Cypher rejects them. Every fork-fresh-init was failing on this. Fixed to `//`.
2. `infrastructure/docker-compose.yml` `timescaledb.command:` was missing `-c timescaledb.enable_chunk_skipping=on`. Flyway `V1.8.0__optimize_timeseries_performance.sql` calls the feature and was failing on every fresh init.

Confluence wiki seed (task #137) directive remains in force — "dont seed wiki content with mfffd - we try to integrate on structure". 112 wiki DOs went away with the reset (which was the intended cleanup). Structural integration is filed as MFFD-WIKI-STRUCT umbrella (sub-rows A-E).

---

## Immediate next action

**For the operator (flo) — Phase 5 of runbook 13 (re-seed) is blocked on a long-lived API key:**

1. Sign in once via Keycloak at https://shepard.nuclide.systems/ — confirms OIDC roundtrip; populates flo's User node email/firstName/lastName from the JWT.
2. Mint a long-lived API key in `/me` profile UI; export as `APIKEY=<token>`.
3. Re-seed LUMEN + MFFD synthetic (Phase 5.1 + 5.2):
   ```bash
   cd /opt/shepard/examples/lumen-showcase && HOST=https://shepard-api.nuclide.systems APIKEY=$APIKEY python3 seed.py
   cd /opt/shepard/examples/mffd-showcase  && HOST=https://shepard-api.nuclide.systems APIKEY=$APIKEY python3 seed.py
   ```
4. Phase 5.3 (MFFD-Dropbox real-data ingest from cube3) needs a fresh DLR cube JWT before it can resume; v16 import script will recreate the dest tree.

**Backend Jandex hang investigation remains deferred** — running image (cut 2026-05-26) survives the reset. Filing the Quarkus issue with the jstack remains a TODO; revisit when the next backend feature needs a rebuild (TS-AXIS-VERIFY #236 still waits on it).

**Cube-side (Track A) — RUNNING:**
- AFP tapelaying export (cube3 → ts-export/) tmux session
- **NEW:** Bridge-welding export (cube3 coll 163811 → ts-export-bridgewelding/) tmux session `mffd-fw-export`; uses `mffd-ts-export.py` with `SKIP_TAPELAYING=1 INCLUDE_BRIDGEWELDING=1`. Wrapper `mffd-fw-export.py` shipped in `bb8c99bcd`.

**After backend rebuild is figured out:**
1. TS-AXIS-VERIFY (#236): re-run `annotate_spatial_roles` for container 987749 (`lbr`, `afp-s1`); verify `/spatial-roles` returns non-null roles; confirm Trace3D dialog auto-populates.
2. Track A: task #145 — fileRef parser bug for 8462 MFFD-Dropbox DOs (still needs fresh DLR JWT).
3. Delete the 112 wiki DOs in collection 661923 (snapshot first per PRE-MUT-SNAP).

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
