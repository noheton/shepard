---
stage: concept
last-stage-change: 2026-07-28
---

# Bridgewelding giveup + semanticRepositories 500 — triage (2026-07-28)

Session under triage: `tapelaying-20260710b` (LOCAL mode, completed 2026-07-21 04:20 UTC,
258,670 files, 0 `[err]`). Two tail anomalies triaged substrate-direct against Neo4j
(`infrastructure-neo4j-1`). Backend is healthy now; the blip was transient.

## What I found

### Anomaly 1 — the `[bridgewelding]` reconnect giveup: NO data gap

**Numeric id 996614 resolves to the tapelaying dest collection itself.**

```
MATCH (c:Collection) WHERE id(c)=996614 RETURN c.name, c.appId, c.deleted;
→ "MFFD-Dropbox", "019f4bf2-176f-7f4c-b3e2-5de837bf20af", FALSE
```

That appId is exactly the tapelaying dest given in the brief. So the `[bridgewelding]`
step POSTed into the **same** collection as tapelaying — it is **not** a second-collection
ingest. It is a hardcoded no-op leftover chain step (see below).

**MFFD-Dropbox live DataObjects (16 total):**

```
MATCH (c:Collection)-[:has_dataobject]->(d:DataObject)
WHERE id(c)=996614 AND d.deleted=false RETURN d.name, d.appId ...
```

| DataObject | count | refs |
|---|---|---|
| `Tapelaying-tapelaying-20260710b` (appId `019f7186-…dca2`) | 1 | **258,728 SingletonFileReference** |
| `Bridgewelding-tapelaying-20260710b` | **12** | **0 each** |
| `WikiDump-…`, `ImportScripts`, `WarmupProbe-…` | 1 each | scaffolding |

The Tapelaying DO holds 258,728 live `SingletonFileReference`s (≥ the 258,670 uploaded
files — the surplus is per-track `Timeseries.csv` + `metadata.json`). **Tapelaying is
complete.** (Supporting fact, not the focus.)

**The 12 Bridgewelding DOs are all empty (0 refs) and are duplicate-on-timeout residue.**
Their `createdAt` timestamps span `1784610044335 → 1784610811603` = **767 s across 12 DOs**,
~50–100 s apart — i.e. they were all created inside the *single* 900 s reconnect window,
not across 12 resume passes. Proof by code + asymmetry:

- `ensure_dest_do` (`mffd-import-v15.py:2859`) is **find-or-create by name**
  (`find_data_object` first, then create). A resume pass therefore *finds* the existing DO
  — which is why there is exactly **1** Tapelaying DO. If resume passes duplicated, Tapelaying
  would be duplicated too. It is not.
- The 12 duplicates come from a layer *below* `ensure_dest_do`: the HTTP reconnect wrapper
  `_request_with_retry` (`mffd-import-v15.py:2700-2747`). On a `requests.exceptions.Timeout`
  it catches the exception and **blindly re-issues the same `POST /collections/996614/dataObjects`**
  (line 2701) with no idempotency key and without re-running find-or-create. Under the
  backend's write-contention load (the `:User` supernode edge-writes, RESUME.md 2026-07-20),
  each POST *reached the server and created a DO*, but the response exceeded `timeout=60s`
  → ReadTimeout → retry → another DO. After 12 attempts the 900 s deadline (`:2666`,
  `:2738`) tripped → `giving up …after 12 attempts` → `_post` returns `None` → `ensure_dest_do`
  returns `None` → `run_local_mode` `continue`s (`:4753`). This matches the log line-for-line.

**There was nothing to lose.** DATA_DIR for this session is
`/mnt/pve/unas/dump/dataset/cube3-export/mffd-export/ts-export`. It contains **only**
`tapelaying/` (8,459 track dirs) + `manifest.json` — **no `bridgewelding/` subdir**. In
LOCAL mode `run_local_mode`'s chain is hardcoded `[("tapelaying", None), ("bridgewelding",
"tapelaying")]` (`:4733-4736`); the bridgewelding step calls `file_list(DATA_DIR/"bridgewelding")`
which returns `[]` for a non-existent dir. So even had the DO created cleanly, the step would
have printed `no local files` and uploaded zero refs. The giveup dropped **zero** payload.

The real bridge-welding data (cube coll 163811 → 1031 DOs / 3930 file / 1031 struct refs,
W3, merged `87a7492f4`) landed in the separate `mffd-bridge-welding` collection (nid 215,
appId `019ed455-6781-755e-87dd-eb3f2f3dbba3`) and is untouched by this run.

### Anomaly 2 — `POST /semanticRepositories` 500: reproducible upstream bug

**Root cause chain (fully traced, not guessed):**

1. Script POSTs `{name:"mffd-migration-tapelaying-20260710b", type:"SPARQL",
   endpoint:"https://noheton.org/mffd/migration/tapelaying-20260710b"}`
   (`mffd-import-v15.py:6994-6997`, also `:4076-4079`). `noheton.org` is a deliberate
   placeholder ("namespace-stable but not required to resolve", `:2003`).
2. `SemanticRepositoryService.validateRepository` (`SemanticRepositoryService.java:89`):
   for non-INTERNAL types it builds `new URL(endpoint)` (passes — well-formed) then
   `connectorFactory.getRepositoryService(SPARQL, endpoint)` → `new SparqlConnector(endpoint)`
   → `healthCheck()`.
3. `SparqlConnector.healthCheck()` (`SparqlConnector.java:38`) fires an `ASK` GET at the
   endpoint. **`noheton.org` does not resolve** (verified: `curl` → `Could not resolve host`,
   `getent hosts noheton.org` → empty). The JAX-RS client throws `UnknownHostException`
   wrapped as `jakarta.ws.rs.ProcessingException`.
4. `request()` (`SparqlConnector.java:104`) catches `ProcessingException`, logs, and returns
   **`null`**.
5. `healthCheck()` passes that `null` straight into `parseJson(null)` (`:47`) →
   `mapper.readTree(null)`.
6. **Jackson 2.22 `ObjectMapper.readTree(String)` calls `_assertNotNull("content", content)`,
   which throws `IllegalArgumentException("argument \"content\" is null")`** on null input
   (contract present since the 2.8→2.9 refactor, jackson-databind #2211). `parseJson`'s
   `try/catch` only catches `JsonProcessingException`, so this `IllegalArgumentException`
   (a `RuntimeException`) escapes → unmapped → generic 500 with `IllegalArgumentException`
   + reference `886abd3a`. **Exactly the log.**

This is **100% reproducible** for any SPARQL repo whose endpoint host is unresolvable /
connection-refused / times out at transport level. The code is longstanding upstream
(`git log SparqlConnector.java` → only `76b86899c` folder move + `666ae9b60` Quarkus bump).
The intended behaviour was a 400 "Invalid endpoint" — the null-guard gap turns it into a 500.

**Blast radius — every MFFD `mffd-migration-*` repo is missing:**

```
MATCH (s:SemanticRepository) RETURN s.name, s.type, s.deleted;
→ "Built-in Semantic Store (n10s)"  INTERNAL  FALSE
→ "prov-o"                          INTERNAL  FALSE
```

Only **two** SemanticRepository nodes exist instance-wide (incl. soft-deleted). **Zero
`mffd-migration-*` repos exist** — for tapelaying-20260710b or *any* prior MFFD session.
Both surviving repos are `INTERNAL`, which short-circuits `validateRepository` (`:93`,
returns before the health check) — that is why `prov-o` succeeds and every SPARQL
`mffd-migration-*` fails. Sessions that ran the migration script and are therefore missing
their repo cover the whole MFFD family (collections that exist today: nids 199, 209, 215,
221, 227, 233, 483751, 994228, 996614 — `mffd-afp-tapelaying`, `mffd-bridge-welding`,
`mffd-spot-welding`, `mffd-ndt-thermography`, `mffd-cell`, `mffd-stringer-welding`,
`MFFD-Dropbox`, plus the RDK/URDF + project collections).

**Practical data impact is small.** The `mffd-migration-*` repo was only a holder for opaque
source-DO URN valueIRIs used by the *v1-style* `add_semantic_annotation` path, and the whole
block is gated `if prov_repo_id and migration_repo_id and fair2r_repo_id` (`:4085`) /
`if prov_repo_id and migration_repo_id` (`:6998`) — a `None` migration repo skips those
provenance writes entirely. But it is **gated behind `MFFD_CREATE_SEMANTIC_REPOS=1`**
(`:4071`, off by default), and in LOCAL mode the only annotation stage is snapshot-lineage,
which additionally needs a fired snapshot appId (`pre/post_snap_app_id`, null here — snapshots
are human-fired). The modern SEMA-V6 annotation store is **decoupled** from this mechanism and
is intact: **70,103 `:SemanticAnnotation` nodes** exist (31,155 on file refs, 1,045 on
timeseries), storing `propertyIRI`/`valueName`/`sourceMode` inline with **no** edge to any
`:SemanticRepository`. So the 500 cost the *migration-URN* provenance subset (largely a no-op
for this LOCAL session), not the platform's annotation graph.

## Opportunities

- **One-line backend fix kills a whole 500 class.** Guarding `parseJson` against null turns
  every unreachable-SPARQL-endpoint into the intended 400, not a 500 — benefits any operator
  who ever types a wrong SPARQL URL, not just this ingest.
- **An idempotency key on `create_data_object`** (or a re-find on Timeout) would have produced
  1 Bridgewelding DO instead of 12, and hardens *every* MFFD create against the backend's
  known write-contention latency.
- **The bridgewelding chain step is dead weight in LOCAL tapelaying sessions** — gating it on
  `(DATA_DIR/step_key).is_dir()` before `ensure_dest_do` would stop minting empty step DOs.

## Ideas

- Make the migration repo `INTERNAL` (endpoint ignored) instead of `SPARQL` — it is only an
  IRI-namespace holder, never actually queried over SPARQL. This matches the working `prov-o`
  pattern and sidesteps the health check entirely (proper fix on the *caller* side).
- Backend: return `false` from `SparqlConnector.healthCheck()`/`getTerm()` when `request()`
  yields null, so a dead endpoint degrades to 400 (fail-soft, matches the "registries are
  fail-soft" principle in CLAUDE.md).
- Adopt an `Idempotency-Key` header (draft-ietf-httpapi-idempotency-key) on the create
  endpoints so the reconnect wrapper can retry POSTs safely — the Stripe pattern.

## Real-world impact

- **No re-ingest of bridgewelding needed** — there is no bridgewelding payload in this session
  and the giveup dropped nothing. The only residue is 12 empty, identically-named DOs cluttering
  MFFD-Dropbox (a cosmetic / graph-hygiene issue, `MFFD-GRAPH-PRUNE`).
- The semrepo 500 leaves the per-session migration provenance repo absent, but the auditable
  SEMA-V6 annotation graph (70k triples) is intact. FAIR-wise, the *primary* data + provenance
  Activity chain are preserved; the missing piece is the optional source-URN cross-reference
  repo that was off-by-default anyway.

## Gaps & blockers

- **Opposing lens — "this IS a real gap, re-ingest it":** the strongest form of the argument is
  "a step gave up after 12 attempts → some bridgewelding DOs never got their files." Refuted on
  three independent grounds: (1) `DATA_DIR` has no `bridgewelding/` subdir, so the step's file
  set is empty by construction; (2) all 12 DOs have **0 refs** and the giveup fired at
  `ensure_dest_do` *before* the file loop is even reached (`:4745` → `None` → `:4753 continue`);
  (3) the source truth for bridgewelding (coll 163811, 1031 DOs) already landed in a *different*
  collection via W3. There is no expected-vs-actual deficit to close.
- **Opposing lens — "harmless relic, ignore it":** mostly right, but not *fully* harmless — it
  minted 12 duplicate live DOs and it masks a real non-idempotent-retry bug that will recreate
  duplicates on the *next* transient blip during any real create. So: no re-ingest, but do prune
  + do file the retry bug.
- Could not run the JVM to execute a live repro of the 500; the chain is proven by code-read +
  Jackson contract + confirmed DNS non-resolution rather than an in-process stack trace.

## What surprised me

- The numeric `996614` in the alarming log line is **the tapelaying collection itself**, not a
  separate bridgewelding collection — the whole "did bridgewelding lose data?" scare dissolves
  once the id is resolved.
- The retry logic's failure mode is inverted from intuition: "giving up after 12 attempts"
  didn't *lose* writes, it *created 12 extra* ones. A ReadTimeout is not proof the write failed.
- The semantic-repo subsystem the script leans on (`/semanticRepositories` + valueIRI + repo_id)
  is essentially vestigial next to the 70k-node SEMA-V6 store, which needs no external repo at all.

---

## VERDICT

1. **Is bridgewelding complete?** — **N/A, and nothing is missing.** The `[bridgewelding]`
   step is a hardcoded no-op leftover in a LOCAL *tapelaying* session (no `bridgewelding/`
   data dir). The giveup dropped **zero** payload. Tapelaying itself is **complete**
   (258,728 refs ≥ 258,670 files). The only residue is **12 empty duplicate DOs** created by
   a non-idempotent POST-retry-on-timeout.

2. **Is the semrepo 500 a bug to file?** — **Yes, two bugs.**
   - **`SEMREPO-HEALTHCHECK-NPE-500`** (backend, upstream-inherited, reproducible): unreachable
     SPARQL endpoint → `request()` returns null → `parseJson(null)` → Jackson
     `IllegalArgumentException` → unmapped 500 instead of the intended 400.
   - **`MFFD-POST-RETRY-NONIDEMPOTENT`** (importer): `_request_with_retry` re-issues POSTs on
     Timeout with no idempotency key, creating duplicate DOs.

3. **Exact remediation (plan only — no mutations executed):**

   **a. Prune the 12 empty Bridgewelding dupes** (soft-delete per `MFFD-GRAPH-PRUNE`; keep at
   most one skeleton, or delete all 12 since the step carries no data). Verify-then-delete via v2:
   ```
   # verify all 12 are still zero-ref, then soft-delete via the v2 API (appId-addressed):
   #   for each appId in the 12 →  DELETE /v2/dataObjects/{appId}
   # appIds: 019f830c-…fec1b, 019f830d-…c83e, 019f830f-…964d, 019f8310-…d9c0,
   #         019f8311-…0b60, 019f8312-…4947, 019f8313-…1caf2, 019f8314-…fbd7,
   #         019f8315-…f899, 019f8316-…19bf, 019f8317-…a227, 019f8317-…f1a0
   ```
   No file re-ingest, no resume sweep. (If a resume were ever wanted for a *real* second
   collection, it would be `mffd-runner.sh` resume-only with the state file — but that is not
   applicable here.)

   **b. Fix the semrepo 500 — backend proper fix (preferred):** null-guard `parseJson` in
   `SparqlConnector.java` so a dead endpoint yields the intended 400:
   ```java
   private Optional<JsonNode> parseJson(String string) {
     if (string == null || string.isBlank()) return Optional.empty();
     ...
   }
   ```
   (Ship with a unit test that asserts `createRepository` on an unresolvable SPARQL endpoint
   returns 400, not 500 — per CLAUDE.md "add tests in the same PR" + upstream-tracker row in
   `aidocs/34`.)

   **c. Script workaround (immediate, no backend deploy):** change the two `get_or_create_semantic_repo`
   calls (`mffd-import-v15.py:4076` and `:6994`) for `mffd-migration-*` from `type_="SPARQL"`
   to `type_="INTERNAL"`. INTERNAL short-circuits `validateRepository` (no health check), so the
   repo node is created and the provenance block un-gates — matching the working `prov-o` path.

   **d. Harden the retry (fixes root of anomaly 1):** add an `Idempotency-Key` header to
   `create_data_object` (or re-run find-or-create on Timeout) in `_request_with_retry`.

### External sources
- Jackson `readTree` null/empty-input contract change (2.8→2.9 unification; modern
  `_assertNotNull` throws `IllegalArgumentException` on explicit null):
  https://github.com/FasterXML/jackson-databind/issues/2211
- POST non-idempotency + duplicate-on-timeout, and the idempotency-key remedy:
  RFC 7231 §4.2.2 (POST/PATCH not idempotent); IETF Idempotency-Key header draft
  https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header-07 ;
  Stripe idempotent requests https://docs.stripe.com/api/idempotent_requests
