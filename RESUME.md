# RESUME — current worklog

**Updated:** 2026-07-17 by claude-opus-4-8 with operator fkrebs@nucli.de
**Active arc:** Post-dispatcher reconcile. The hourly cloud dispatcher (APISIMP sweep, ran through fire-653 on 2026-07-17) is now **OFF**. July produced ~320 commits of APISIMP surface cleanup + UIRULE conversions + Quarkus 3.37 migration + MP4-video + URDF picker. Reconcile in flight: TIFF PR #2456 rebase+gates, PR #2626 review, ~520 stale APISIMP remote branch prune. Next big thread: MFFD tapelaying TPS ingest (`593529286` decoded the source structure).
**Status:** Historic sections below (2026-05-28 header era) are ARCHIVE — see dated sections from 2026-06-17 onward for current MFFD state.

---

## Immediate next action

**BLOCKED — backend rebuild keeps hanging in Jandex infinite loop.** Two consecutive attempts (PIDs 354722, 500244) ran 40+ min and 5h respectively, both stuck at `org.jboss.jandex.CompositeIndex.getClassByName` inside `quarkus-arc AnnotationsTransformer.apply()`. The Makefile comment at the `build-backend` target attributes this to stale class files and prescribes `mvn clean` — but `rm -rf backend/target/` + `mvn clean package` reproduces the hang. Real root cause unclear; needs targeted investigation (Jandex index corruption in `~/.m2`? specific class with circular annotation? recent plugin commit introduced the trigger?).

**Backend in production stays at the 2026-05-26 image** until this is resolved. TS-AXIS-AUTO (`/v2/timeseries-containers/{id}/channels/spatial-roles`) and TS-SEMANTIC-REST (`POST .../channels/{shepardId}/annotations`) are in code (`483282896`, `babb5c8f6`) but NOT live.

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
| Q7 / #145 fix | branch `145-fileref-truncation-fix`; findings `aidocs/agent-findings/q7-fileref-parser-bug-2026-05-28.md` | **code-only fix shipped 2026-05-28** — two compounding bugs in `download_file_ref`: (1) worker call site dropped `oid=fref.oid` so bare `/payload` returned JSON metadata uploaded as binary; (2) `iter_content()` silent truncation never verified against `Content-Length`. 10-case regression in `test_fileref_truncation.py`. Validation deferred to next v16 import (no live cube JWT + 661923 was wiped today). |

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

## 2026-06-17 — Full instance reset COMPLETE + MFFD scaffolding seeded
- Reset done: all substrates wiped (Neo4j fresh, TS 0 rows, mongo/garage fresh), Keycloak preserved. Backend+frontend healthy.
- Fixed 3 fresh-DB boot bugs (commit on main, rebased d1df38676): pgcrypto/pg_stat_statements extension privilege (init script pre-creates as superuser); V1.17.0 refresh_continuous_aggregate-in-txn (sidecar .sql.conf executeInTransaction=false); stale SHEPARD_PERMISSIONS_DEFAULT_OWNER unset. Runbook 13 corrected (host bind mounts, not named volumes).
- Auth bootstrap: admin user (username=7eead942..., appId 019ed455-...) materialized via Keycloak password grant (admin/admin-demo, scope=openid profile email). Granted instance-admin via :HAS_ROLE Cypher. Minted import key -> /root/.claude/uploads/mffd-import-key-2026-06-17.txt (X-API-KEY, instance-admin, verified 200).
- v2 base is /v2/... (root-path /), NOT /shepard/api/v2/... (that 404s).
- MFFD scaffolding seeded (seed-mffd-collections.py, ONLY mffd, no other examples):
  - Project "MFFD Upper Shell — Project" = 019ed455-62cd-75b5-951e-b837ffdace16
  - mffd-afp-tapelaying  = 019ed455-66f4-7aea-8cb3-5c0b34a737df
  - mffd-bridge-welding  = 019ed455-6781-755e-87dd-eb3f2f3dbba3   (W3 target)
  - mffd-spot-welding    = 019ed455-67f7-7725-bf2d-7cd1b67aca9f
  - mffd-ndt-thermography= 019ed455-6866-71f1-b0bf-0f83a3e3aaa9
  - mffd-cell            = 019ed455-68d9-7e00-8aa0-0191e99fc117
  - UserGroups: mffd-afp-team, mffd-welding-team, mffd-ndt-team, mffd-cell-team
- BRIDGE WAVE (user picked "bridge first/validate"): source = LOCAL /mnt/pve/unas/dump/dataset/4-Brückenschweißen/{bridgewelding/,manifest.json}. manifest = {collections.bridgewelding:{id:163811, dos:{<1031 DOs>}}}; each DO has ts_refs/file_refs/structured_refs[{ref_id,ref_name,file}]; payloads in bridgewelding/<DO>/{structured,...}; 3.7GB total.
- BLOCKER/FINDING: README's `shepard-importer --source shepard-export-manifest` CLI is FICTIONAL. import-mffd.py = thermography-frame importer (no manifest replay); mffd-import-v15.py LOCAL MODE = 0-byte TS placeholders. The bridge manifest-replay importer must be BUILT (read manifest -> create DO per entry in mffd-bridge-welding -> recreate file/ts/structured refs + upload payloads -> annotate urn:shepard:source:provenance; completeness-non-negotiable retry-forever).

## 2026-06-17 — MFFD waves W3/W8a/W6 done (one-after-another)
- W3 bridge: 1031 DOs / 3930 file / 1031 struct refs. merged 87a7492f4.
- W8a spot-welding: 21 DOs / 21 file refs / 4260 svdx parser annos (20/21; 651MB file parsed 0 — parser edge case). merged 08c534891.
- W6 thermography: 744 DOs / 744 file refs / parser 744/744 / scope+layer+phase+status annos. merged d7854e942. Importer self-fixed 10 empty-payload uploads + 50 partial-anno backfills.
- GRAPH CRUFT (post-import prune needed, MFFD-GRAPH-PRUNE): iterative imports left 3178 soft-deleted DataObjects + orphan annotations (1088 otvis-file annos point at dead DOs). LIVE data is correct (deleted=false queries exact); tombstones are cruft. Prune after all waves.
- Backlog filed: P24 (v2 structured-data surface), P25 (bulk DO+ref create), F9 (reference-annotation 403 — confirmed PERMANENT not lag).
- Next: W2 tapelaying (TS-export, coll 48297, real timeseries — heaviest), then W5 cell (rdk→urdf), W8b stringer (247GB zip).

## 2026-06-17 — W2 pass B DONE; pass A pending; W5 done
- W5 cell: MFZ DO + MFZ.rdk FileReference + 10 rdk parser annos. merged f612b1cbf.
- W2 pass B (structure+lineage): 8457 Track/Ply DOs + 8400 predecessor (has_successor) + 8450 parent (has_child) edges, graph-verified, idempotent. merged d9235b885. Importer: examples/mffd-showcase/scripts/mffd-tapelaying-import.py. Lineage sub-pass SERIALIZED (workers=1) — parallel bidirectional-edge writes raced backend's successor-list validation (400); 5 manual serial retries cleared them; fix committed.
- W2 pass A (TPS->BrushTrace): NOT blocked by infra. spatiotemporal v6 plugin IS enabled (/v2/containers?kind=spatial -> 400=exists). Script's --pass A falsely STOPPED probing the DISABLED v1 path /shepard/api/spatialDataContainers (404). Needs rework to v2 kind=spatial + BrushTrace ingest. Backlog: W2-PASS-A-SPATIAL.
- Raw 355GB TPS payloads deferred (W2-TPS-RAW-1, disk-gated: 344GB free < 355GB).
- MFFD import status: W3 bridge ✓, W8a spot-welding ✓, W6 thermography ✓, W5 cell ✓, W2 pass B ✓. REMAINING: W2 pass A (spatial rework), W8b stringer (247GB zip, disk+parser). Wiki transform also pending.
- Graph cruft to prune post-import (MFFD-GRAPH-PRUNE): ~3178+ soft-deleted DOs + orphan annotations from iterative imports; live (deleted=false) data is correct.

## 2026-06-17 — MFFD demonstrator users + wiki + username-claim + perms (RECOVERED from stash@{1} on 2026-07-17 — was never committed)
- Wiki->lab-journal: 218 entries across 6 pages -> mffd collections; 2 :MirroredUser authors (dede_di, bran_lr). Fixes: wiki_common v1-id fallback (v2 dropped numeric id), bounded 403-retry (perm lag), PROJECT_APPID default. committed.
- 25 real Keycloak logins (all wiki DLR authors, e.g. vist_mi=Vistein Michael), shared pw "demo", user role, verified login. Realm 6->31 users.
- username-claim DEFAULT flipped to preferred_username (was sub-split/UUID). committed 9f379d2bb + aidocs/34 BREAKING note. Re-keyed 25 demo :User nodes UUID->login-name (admin left as 7eead942 — import key auths as that sub). Verified: vist_mi logs in as "vist_mi", no dup.
- 4 MFFD teams populated (mffd-afp 8 / welding 6 / ndt 5 / cell 6 = 25); membership by node-edge so survives re-key.
- ORPHAN PERMISSIONS crisis: redeploy re-ran startup guard -> 27323 BasicEntity lacked :has_permissions (importers, esp v1 paths, don't seed Permissions; V14 backfill only runs once, ran on empty DB during reset). Fix: re-set SHEPARD_PERMISSIONS_DEFAULT_OWNER=7eead942 (override, committed f2e57cfc5) to clear boot guard, then manual Cypher backfill of all 16013 orphans -> PublicReadable + owned_by admin (legacyBackfill='pubread-2026-06-17'). 0 orphans remain. Demo users now read all MFFD content; admin owns write.
- KNOWN: importers not seeding Permissions is a real bug (file backlog). USERNAME=UUID was instance-wide (now fixed for new users via claim flip; existing admin/flo still UUID).

## 2026-07-17 — dispatcher OFF; repo reconcile session
- Hourly cloud dispatcher (APISIMP sweep) ran through fire-653 (2026-07-17) and is now OFF per operator. July: ~320 commits (APISIMP surface cleanup, UIRULE dropdown conversions, Quarkus 3.27→3.37 + JUnit 6, MP4→video promotion, URDF searchable picker, tapelaying TPS source decode `593529286`).
- Reconciled: committed stray ops diff (pgbouncer pool 20→30 + mffd script exec bits, `50f53bf1e`); rebased main onto remote (5 dispatcher merges had landed after switch-off).
- Dispatched (in flight): (a) TIFF PR #2456 rebase+gates agent; (b) stale-branch janitor — ~520 APISIMP remote branches classified against PR ledger, PR-merged ones pruned, report → `aidocs/agent-findings/branch-reconcile-2026-07-17.md`; (c) PR #2626 (APISIMP-DQR-EVAL-INMEM, last dispatcher fire) review+gate agent.
- Operator backlog filings this session: UIBUG-RECENT-COLLECTIONS-GETTIME (pager updatedAt.getTime crash — generated-client Date-vs-ISO-string drift); MD-RENDER-UMBRELLA (journal + descriptions render markdown, not HTML).
- Stale open PRs needing triage: #1774 (Playwright auth fixture), #1773 (PROV read-capture flip), #1762 (bulk semantic-annotation REST), #1701 (backlog flip docs) — all May-era; plus 7 dependabot bumps.
- Stashes: dropped stash@{0} (stale screenshot churn) + stash@{1} (salvaged above). KEPT stash@{2} (svdx/thermography parser WIP, 530+ lines — verify against merged W6/W8a commits before dropping) + stash@{3} (pre-m4i on dead branch).
- NEXT after reconcile: MFFD tapelaying TPS ingest per `593529286` scripted-ingest path (W2 pass A spatial rework family); then MFFD-GRAPH-PRUNE.
