# 93 — MFFD real-data import (v15) requirements

**Status:** Requirements locked 2026-05-22 · ready for v15 implementation
**Supersedes:** `aidocs/integrations/92` §3 (single-shot script approach)
**Companion:** `aidocs/agent-findings/api-scrutinizer-v14-import.md` (20-bug catalogue from v14),
`aidocs/agent-findings/data-ontologist-prov-o-v15.md` (PROV-O turtle design)

## 0. Scope — what this is

A single-script (Python + uv) cross-instance importer that lifts the **real MFFD process data** from DLR cube3 (`backend.bt-au-cube3.intra.dlr.de`) into nuclide.systems (`shepard-api.nuclide.systems`), preserving the full 12-step DAG topology + lineage edges + all three payload kinds + activity provenance.

**Out of scope:** synthetic seed data, on-disk export replay, anything LUMEN-side.

## 1. Source — LIVE DLR cube3 API (corrected 2026-05-22 — third pass)

**Important pivot:** the local on-disk export at `examples/mffd-showcase/raw-data/mffd-data/` is **shape reference only — incomplete dataset**:

- File payloads: present (~5.5 GB tapelaying)
- TS payloads: **all 4627 `ts-*.csv` files are 0 bytes** (exporter v1.2 wrote placeholders only)
- Structured payloads: present
- Metadata + lineage: present

Without TS payloads we cannot do Trace3D thermal-trail rendering, ODIX channel analysis, or any other showcase demonstration that requires actual channel data. **The on-disk drop is useful for understanding the export shape; it is NOT the dataset for the real import.**

**Real source remains the LIVE DLR cube3 API:**

- **Tapelaying:** `https://backend.bt-au-cube3.intra.dlr.de/shepard/api/collections/48297` — 5012 DataObjects (28 PlyGroup + 77 Ply + 3985 Track + scaffold), 23,328 file refs, 4627 TS refs (with payloads accessible via `/export`)
- **Bridgewelding:** `https://backend.bt-au-cube3.intra.dlr.de/shepard/api/collections/163811` — 3371 DOs, structured data refs (StepMetaProcessStep, StepMetaProcessExecution)
- **Network:** DLR intranet only — script runs on cube@bt-au-cube-mig (the bridge host that reaches both DLR intranet AND nuclide.systems)
- **Auth:** kreb_fl JWT (14h TTL; v15 detects 401 and pauses for re-mint without losing progress) + Flo Researcher JWT (long-lived dest auth)

**TS payload access via live API:**

```
GET /timeseriesReferences/{refId}/export?csv_format=COLUMN  → real CSV bytes (NOT WIDE — v5.4.0 enum is {ROW, COLUMN})
```

v15's Bug H fix (`COLUMN` not `WIDE`) is what unlocks live TS access. The metadata-only fallback is no longer needed because the real source has the data.

**Labor split for v15:**
- **You (cube@bt-au-cube-mig)** run v15 — only host with both DLR intranet + nuclide reachability
- **Me (dev box)** prepares dest (Garage activation, container creation, sanity probes) and patches v15 itself

## 2. Dest — nuclide.systems with Garage S3

- **Collection:** MFFD-Dropbox (id 515365, appId `019e4e56-ca63-76f3-9bf0-6681f7fe6d56`) — fresh after cleanup
- **File storage:** **Garage** sidecar via `shepard-plugin-file-s3` (FS1b/c/d shipped; sidecar activation per §9)
- **TS storage:** TimescaleDB hypertables (PgBouncer connection pooling)
- **Structured data:** MongoDB collections (one per StructuredDataContainer)
- **Metadata + lineage:** Neo4j (operational; v1-compat surface)
- **Auth:** flo@demo.shepard.local JWT (long-lived)

## 3. Structure to preserve — non-negotiable

```
Tapelaying:                                        Bridgewelding:
  PlyGroup-N → Ply-K → Track-NNN (Run XXXXX)         Frame-N → AF_NN
                                                              → Execution-YYYY-MM-DD
                                                              → ProcessData
                                                                (StepMetaProcessStep,
                                                                 StepMetaProcessExecution)
```

Every `predecessorIds[]` link from the source must replicate on the dest. **Bug I in v14 was the structure-killer** — v15 fixes it by setting `predecessorIds` in the DataObject body at POST time, not via a non-existent `/predecessors/{id}` PUT.

**Note on "Q1 anomaly":** the ply-5 consolidation-force-drop + TCP temp-spike anomaly + NDT FAIL → Rework → NDT PASS chain described in `examples/mffd-showcase/README.md` and `seed.py` is a **synthetic narrative baked into the seed**, NOT a feature of the real cube3 data. Real DLR data is real production data — anomalies (if any) live wherever they actually live, not at a scripted location. The demo story shifts accordingly: the showcase demonstrates *the system finding anomalies in real data*, not *the system pointing at a pre-known one*. ODIX analysis runs against whatever's actually there. See updated §13.

## 4. Per-payload wire-shape requirements (8 bugs to fix)

Per `aidocs/agent-findings/api-scrutinizer-v14-import.md`:

| Bug | Kind | Fix |
|---|---|---|
| **D** | Files | Capture `fileOids[]` array; iterate over OIDs in `/payload/{oid}` for multi-file refs |
| **G** | TS | Reorder: container → CSV import → list channels → link (link required non-empty `timeseries[]`) |
| **H** | TS | `csv_format=COLUMN` (not `WIDE`; DLR v5.4.0 enum is `{ROW, COLUMN}` only) |
| **L** | TS | Drop `type:"TIMESERIES"` from create-container body (readOnly field) |
| **E** | Structured | Parse `StructuredDataPayload[]` wrapper; decode inner JSON string (payload field is JSON-encoded string, not object) — **CRITICAL: silent data corruption** |
| **B** | Structured | POST not PUT for `/structuredDataContainers/{id}/payload`; capture returned OID |
| **C** | Structured | Supply `structuredDataOids:[oid,...]` (non-empty) when creating StructuredDataReference |
| **I** | Metadata | Set `predecessorIds:[...]` in DataObject body at POST/PUT; remove the broken `/predecessors/{id}` call |

Additional cleanups (Bugs J, K, O, P, R from the Scrutinizer doc) — minor / dead-code removal.

## 5. Concurrency + resilience

```
Producer thread:    iterates source DOs → enqueues UploadTask(kind, src_*, dest_*) on bounded Queue(256)
Worker pool: 4×     pull tasks; per task: resilient_retry(download_src → upload_dest); post CompletionEvent
State writer: 1×    single writer, atomic .state.json (tmp+fsync+rename), every 100 events OR 30s
ETA publisher: 1×   PATCH collection.attributes.import_eta every 30s
Log publisher: 1×   re-upload log file to ImportScripts DO every 5min
Prov writer: 1×     emit semanticAnnotation triples per batch (every 100 DOs OR 5min)
```

**Retry policy** (`resilient_retry`):
- Transient (502, 503, conn-reset, conn-refused, timeout): exp backoff base 1s, max 60s, jitter
- Redeploy-class (sustained 502/503/conn-refused on dest): switch to long-wait mode, poll `/health` every 5s, **indefinite retry** (resume in-place without aborting; configurable cap for non-redeploy classes)
- 401 (JWT expired): pause workers, log "JWT expired; re-mint and POSIX-signal SIGCONT to resume" (operator-driven, not automatic — keeps the script offline-safe)
- 4xx (other): record as failure, continue queue
- 5xx (sustained, non-redeploy): record as failure, alert

**Concurrency limits:**
- Source: max 4 parallel GETs against cube3 (avoid hammering DLR's instance)
- Dest backend: max 4 parallel POST/PATCH metadata calls (backend is the bottleneck, not S3)
- Dest Garage: presigned PUTs are direct — concurrency limited by network bandwidth + worker count

## 6. File upload — presigned-URL flow (NEW in v15)

v14: `POST /v2/files` multipart → backend reads body → writes via active storage adapter (gridfs today).
**v15: three-step against per-step FileContainer with `providerId=s3`:**

```
1. POST /v2/file-containers/{appId}/upload-url        body={fileName} → {uploadUrl, oid, expiresAt}
2. PUT  <uploadUrl> --data-binary @<file>            → direct to Garage; backend never sees bytes
3. POST /v2/file-containers/{appId}/upload-url/commit  body={oid, fileName} → 201 ShepardFile
4. POST /shepard/api/collections/{c}/dataObjects/{do}/fileReferences
        body={name, fileOids:[oid], fileContainerId} → 201 FileReference (the v5-compat surface for ref binding)
```

Step 2 is where the 5.5 GB tapelaying file payload + future TS-derived files go. **Backend memory stays cool.**

## 7. TS upload — live cube3 pull + corrected wire shapes

v15: live pull from cube3 + Bug-H-corrected csv_format:

```
1. GET https://backend.bt-au-cube3.intra.dlr.de/shepard/api/collections/{srcColl}/dataObjects/{srcDo}/timeseriesReferences/{srcRef}/export?csv_format=COLUMN
   → CSV bytes (the actual channel data, NOT a 0-byte placeholder)
2. POST /shepard/api/timeseriesContainers  body={name}            → {id} (no type field — readOnly per Bug L)
3. POST /shepard/api/timeseriesContainers/{id}/import  multipart   → 200 (CSV parsed → COPY to hypertable via PgBouncer)
4. GET .../timeseriesContainers/{id}/timeseries                   → [{measurement,device,location,symbolicName,field}, ...]
5. POST /shepard/api/collections/{c}/dataObjects/{do}/timeseriesReferences
        body={name, timeseriesContainerId, timeseries:[...listed channels]} → 201 TimeseriesReference
```

Concurrency: PgBouncer absorbs 4 parallel COPY operations on dest. Source-side: max 4 parallel GETs against cube3 to avoid hammering DLR's instance.

**Trace3D acceptance:** with real TS data flowing, the thermal-trail demo works end-to-end. No data-availability gap once import completes.

## 8. Structured data upload — wrapper-aware

```
1. GET /structuredDataReferences/{srcRefId}/payload          → StructuredDataPayload[] (array of wrappers!)
   for each wrapper:
     payload_object = json.loads(wrapper["payload"])         ← Bug E fix: payload is encoded STRING
2. POST /shepard/api/structuredDataContainers  body={name}    → {id}
3. POST /shepard/api/structuredDataContainers/{id}/payload    body=<payload_object> → {oid}  (Bug B: POST not PUT)
4. POST /shepard/api/collections/{c}/dataObjects/{do}/structuredDataReferences
        body={name, structuredDataContainerId, structuredDataOids:[oid,...]} → 201  (Bug C: non-empty OIDs required)
```

## 9. Garage sidecar activation — plugin-declared (NEW)

**Principle** (memory `feedback_plugins_declare_sidecars.md`): infrastructure dependencies belong in the owning plugin's manifest; the deploy assembles compose from active-plugin declarations. **Hand-edited compose overrides are forbidden.**

**Concrete plan:**

1. Extend `FileS3PluginManifest` with a `sidecars()` method returning a declarative spec (image, ports, volumes, env, healthcheck, post-init shell).
2. A small **bootstrap script** (`scripts/activate-plugin-sidecars.sh` or similar) reads active plugins via `GET /v2/admin/plugins`, extracts sidecar declarations, generates the compose override + bucket-bootstrap commands.
3. **The bootstrap script CAN BE BAKED INTO THE v15 IMPORT SCRIPT** as a pre-flight step (per user 2026-05-22: "can be baked into script") — v15 detects "S3 provider not active" via the presigned-URL probe, prints the activation runbook, exits cleanly.
4. Operator runs the bootstrap output once → restarts backend → re-runs v15 (with state file, resumes where it left off).

**Why bake into the script:** the operator gets one-paste deploy guidance pinned to the exact plugin version they're running. Future plugins follow the same shape — declare your sidecar, the script tells the operator how to stand it up.

**Sidecar declaration shape (proposed for `FileS3PluginManifest`):**

```java
public List<SidecarSpec> sidecars() {
    return List.of(
        SidecarSpec.builder()
            .id("garage")
            .image("dxflrs/garage:v1.0.1")
            .port(3900, "s3-api")
            .port(3902, "web-admin")
            .volume("garage_data", "/var/lib/garage")
            .env("GARAGE_RPC_SECRET", "{{generate:hex:64}}")
            .env("GARAGE_S3_API_BIND_ADDR", "0.0.0.0:3900")
            .healthcheck("CMD curl -f http://localhost:3900/health || exit 1", "30s", "10s", 3)
            .postInit(List.of(
                "/garage layout assign ${NODE_ID} -z dc1 -c 1G",
                "/garage layout apply --version 1",
                "/garage bucket create shepard-files",
                "/garage key new --name shepard-backend",
                "/garage bucket allow --read --write shepard-files --key shepard-backend"
            ))
            .backendEnvBinding(Map.of(
                "SHEPARD_FILES_S3_ENDPOINT", "http://${sidecar.host}:3900",
                "SHEPARD_FILES_S3_REGION", "garage-region",
                "SHEPARD_FILES_S3_PATH_STYLE", "true",
                "SHEPARD_FILES_S3_BUCKET", "shepard-files",
                "SHEPARD_FILES_S3_ACCESS_KEY_ID", "{{from:postInit.4.access_key_id}}",
                "SHEPARD_FILES_S3_SECRET_ACCESS_KEY", "{{from:postInit.4.secret_access_key}}"
            ))
            .build()
    );
}
```

This extension is a small new design step in the plugin SPI (`aidocs/platform/47`); the principle goes into task #143 (plugin meta-ontology) by adding a `shepard:hasSidecar` predicate.

## 10. Provenance — semanticAnnotations + X-AI-Agent (per task #160)

Per `aidocs/agent-findings/data-ontologist-prov-o-v15.md`: no public Turtle ingest endpoint exists; use existing typed-triple `POST /shepard/api/collections/{c}/dataObjects/{do}/semanticAnnotations` + `X-AI-Agent` HTTP header on every dest call.

Per batch (every 100 DOs OR 5min), v15 emits:

```
ent:do-<dest-appId> a prov:Entity ;
    prov:wasGeneratedBy act:<batch-uuid-v7> ;
    fair2r:verificationState verif:unverified .

act:<batch-uuid-v7> a fair2r:AuthoringPass ;
    prov:startedAtTime "<t0>" ; prov:endedAtTime "<t1>" ;
    prov:wasAssociatedWith agent:claude-opus-4-7 ;
    prov:wasAssociatedWith usr:fkrebs-at-nucli-de ;
    prov:used src:import-mffd-v15 ;
    shepard:filesUploaded <n> ; shepard:timeseriesImported <n> ; shepard:structuredPayloads <n> ;
    shepard:sourceInstance <https://backend.bt-au-cube3.intra.dlr.de> ;
    shepard:targetCollection coll:<dest-appId> .
```

**Blocker:** task #159 — Cypher migration to register `shepard:filesUploaded` / `timeseriesImported` / `structuredPayloads` / `batchSequence` / `throughputBytesPerSec` / `retryCount` / `sourceInstance` / `targetCollection` predicates + `role-executor` / `role-operator` individuals. v15 won't ship without this.

## 11. Observability

- **ETA attribute on dest collection (every 30s):**
  ```
  PATCH /shepard/api/collections/515365  body={"attributes":{
      "import_eta":"<iso8601>",
      "import_progress":"<n_done>/<n_total>",
      "import_throughput_dos_per_min":"<float>",
      "import_throughput_mb_per_min":"<float>",
      "import_last_heartbeat":"<iso8601>"
  }}
  ```
- **Log re-upload (every 5min):** upload `mffd-import-<session>.log` to ImportScripts DO as a new FileReference revision (via the presigned-URL flow on Garage container — keeps the proof-of-work durable).
- **Locally:** the state file `mffd-import-<session>.state.json` ledgers every completed payload; on restart, completed-files / completed-ts / completed-structured sets skip already-done work.

## 12. Out-of-scope for v15 (queued)

- **shepardId rename** (task #123/#125) — v15 uses 5-tuple TS identity (works against v5.4.0); migration to shepardId is a parallel arc.
- **Trace3D view** (task #142) — consumes the imported data; ships in the trace3d-views-as-shapes worktree.
- **shepard-plugin-importer port** (task #126 PR-3..PR-7) — v15 is the LAST script iteration; PR-3 extracts `DLRv5Source` from v15.
- **Substrate split full** (task #127) — domain attributes lift to SHACL graph asynchronously; v15 writes the v1-compat way.

## 13. Acceptance criteria

```
✓ Both tapelaying (5012 DOs) + bridgewelding (3371 DOs) on dest
✓ DAG topology preserved (every predecessorIds[] link verified — full chain reachable
  via predecessor walk from any leaf back to its PlyGroup root or Frame root)
✓ 23,328 file refs on dest with non-zero fileSize each
✓ 4627 TS refs on dest with non-empty timeseries[] channel arrays AND non-zero hypertable row count (real point data pulled live from cube3)
✓ Structured data refs (count from cube3 source) on dest with payloads matching by SHA-256
✓ TS channels present include the AFP robot TCP X/Y/Z + tcp_temperature + consolidation_force
  on each tapelaying Track DO with actual point data (renderer prerequisite for Trace3D — full
  thermal-trail demo works end-to-end)
✓ ImportScripts DO carries v15 script + log file + state file as FileReferences
✓ Collection.attributes.import_progress shows "8383/8383" + import_eta cleared at completion
✓ At least one fair2r:AuthoringPass Activity per batch visible in /v2/provenance/activities?targetAppId=...
✓ GET /v2/file-containers/<mffd-s3-container>/upload-url returns 200 (Garage active throughout)
```

**De-scoped (was incorrectly listed pre-correction):** locating a specific Q1 ply-5 anomaly — that's a `seed.py` synthetic; real data has its own (unknown to us) anomaly profile. Anomaly detection becomes ODIX's job *post-import*, not a pre-known import-acceptance check.

## 14. Sequencing — final

```
[1. YOU — deploy-side, nuclide]
   Garage activation via baked-in pre-flight (§9):
     • v15's pre-flight probes /v2/file-containers/{x}/upload-url
     • If 503 "gridfs", print the runbook + exit cleanly
     • Operator runs runbook, restarts backend
     • Re-runs v15; pre-flight passes, import proceeds

[2. ME — worktree, dev box]
   Patch the 8 wire-shape bugs (D, G, H, L, E, B, C, I)
   Implement 4-worker pool + resilient retry + redeploy-resilient long-wait
   Implement presigned-URL upload flow for files (against Garage container)
   Implement X-AI-Agent header + semanticAnnotation batch writeback
   Implement ETA attribute + log re-upload threads
   Add Cypher migration V61 for new shepard: predicates (task #159)
   Upload v15 to ImportScripts DO on nuclide (so cube can fetch it)

[3. YOU — cube@bt-au-cube-mig]
   Re-mint kreb_fl JWT (14h TTL) if last one expired
   Fetch v15 from nuclide ImportScripts DO (curl with -f and md5 check)
   Run v15 with both source + dest JWTs
   On JWT expiry: re-mint, SIGCONT the script (resumes from state file)
   On JWT validity: bytes flow direct cube3 → cube → nuclide Garage

[4. ME — verify, dev box]
   Acceptance criteria §13 against nuclide via REST + cypher-shell + psql probes
```

**Why cube and not dev box:** the dev box reaches nuclide but NOT DLR intranet (cube3). The cube reaches both. The on-disk drop at `examples/mffd-showcase/raw-data/` on the dev box is shape-reference only (no TS payloads); the real TS data lives in cube3's live API.

---

**Snapshot:** 2026-05-22 ~22:00 UTC by claude-opus-4-7 with operator fkrebs@nucli.de.
**Open questions:** none — all design decisions baked.
