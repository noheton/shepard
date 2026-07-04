---
stage: fragment
last-stage-change: 2026-06-17
---

# APISIMP sweep — 2026-06-17 (fire-89)

Routine: fire-89 found B8 (`FileBundleReferenceKindHandler`) blocked on PR #1966 merge.
Ran a full API-SIMPLIFICATION SWEEP of the live `/v2` surface as the dispatch alternative.

Surface scanned:
- `backend/src/main/java/de/dlr/shepard/v2/**` — all REST files (85+ classes)
- `plugins/*/src/main/java/**` — all plugin REST files

Status of queued PRs at scan time: #1966 (READY), #1967 (READY), #1968 (READY), #1970 (READY), #1969 (CLOSE).

---

## Finding 1 — CRITICAL: JAX-RS path collision on `/v2/references/{appId}/annotations`

**Severity:** CRITICAL  
**Size:** S  
**ID:** `APISIMP-ANNOTATION-SUBRESOURCE-COLLISION`

After APISIMP-VIDEO-ANNOT-PATH (fire-57, PR #1953) migrated `VideoAnnotationRest` from
`/v2/data-objects/{dataObjectAppId}/video-stream-references/{refAppId}/annotations` to
`/v2/references/{refAppId}/annotations`, **two JAX-RS resource classes now register identical
path templates** in the same deployed WAR:

| Class | Module | `@Path` |
|---|---|---|
| `TimeseriesAnnotationRest` | `backend` | `/v2/references/{appId}/annotations` |
| `VideoAnnotationRest` | `plugin-video` | `/v2/references/{refAppId}/annotations` |

Both expose the same HTTP verbs: `GET /`, `POST /`, `GET /{annId}`, `PATCH /{annId}`, `DELETE /{annId}`.
Path-parameter names (`{appId}` vs `{refAppId}`) are irrelevant for JAX-RS dispatch — the template
structure is identical.

Both plugins are bundled into the backend JAR (confirmed in `backend/pom.xml:1095`).

**Effect at runtime:** Quarkus/RESTEasy picks one class; the other silently never receives requests.
Given classpath ordering, `TimeseriesAnnotationRest` (core module, loaded before plugins) likely wins,
meaning **all `GET|POST|PATCH|DELETE /v2/references/{videoRefAppId}/annotations` calls return
timeseries-annotation 404 ("timeseries reference not found") instead of video annotation data.**

The existing unit tests (`VideoAnnotationRestTest`, `TimeseriesAnnotationRestTest`) bypass the JAX-RS
container by instantiating resource classes directly — they do NOT catch this collision.

**Fix options (preferred to least preferred):**
1. Introduce a `ReferenceAnnotationHandler` sub-SPI; create a single unified `ReferenceAnnotationRest`
   that resolves the reference kind and delegates: one class, one path, correct dispatch.
2. Move each annotation resource under a kind-discriminated sub-path:
   `/v2/references/{appId}/timeseries-annotations` and `/v2/references/{appId}/video-annotations`.
   Simpler, but breaks the consistent sub-resource pattern.

Option 1 aligns with the `ReferenceKindHandler` SPI pattern already established. The interface gains
a default `Optional<ReferenceAnnotationHandler> annotationHandler()` method returning `Optional.empty()`;
`FileReferenceKindHandler` and other non-annotatable kinds return empty; `TimeseriesReferenceKindHandler`
and `VideoStreamReferenceKindHandler` return their respective handler impls. A single
`ReferenceAnnotationRest` at `/v2/references/{appId}/annotations` resolves kind + delegates.

**First-refs:**
- `backend/src/main/java/de/dlr/shepard/v2/timeseries/resources/TimeseriesAnnotationRest.java:43`
- `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoAnnotationRest.java:43`
- `backend/src/main/java/de/dlr/shepard/v2/references/spi/ReferenceKindHandler.java`

**Backlog row:** `APISIMP-ANNOTATION-SUBRESOURCE-COLLISION`

---

## Finding 2 — MAJOR: `/v2/files/{appId}*` CRUD duplicates `/v2/references/{appId}` post-#1966

**Severity:** MAJOR  
**Size:** M  
**ID:** `APISIMP-FILE-PATH-RETIRE-2`

PR #1966 (READY) tombstones `POST /v2/files` (the multipart create endpoint) under the
`APISIMP-KIND-DISCRIMINATOR` row. After that merge, `FileReferenceV2Rest` (`@Path("/v2/files")`)
still exposes 5 endpoints that are now **fully duplicated** by `ReferencesV2Rest` via
`FileReferenceKindHandler`:

| Old path | Unified equivalent |
|---|---|
| `GET /v2/files/by-data-object/{dataObjectAppId}` | `GET /v2/references?kind=file&dataObjectAppId={id}` |
| `GET /v2/files/{appId}` | `GET /v2/references/{appId}` |
| `GET /v2/files/{appId}/content` | `GET /v2/references/{appId}/content` |
| `PATCH /v2/files/{appId}` | `PATCH /v2/references/{appId}` |
| `DELETE /v2/files/{appId}` | `DELETE /v2/references/{appId}` |

Confirmed: `ReferencesV2Rest.get()` calls `resolved.get().handler().toIO(...)` — the handler is
resolved by kind from the stored `FileReference` entity, so `GET /v2/references/{appId}` works
for all kinds including `file` with no special-casing. `FileReferenceKindHandler.uploadContent()` /
`FileReferenceKindHandler.downloadContent()` cover the binary GET/PUT too.

**Fix:** After #1966 merges, tombstone the remaining 5 endpoints in `FileReferenceV2Rest` with
HTTP 410 Gone + `Location` header pointing to the equivalent `/v2/references` path. Then
repoint the handful of frontend callers that still hit `/v2/files/...` directly (grep
`/v2/files` in `frontend/composables/` → ~3 call sites).

**First-refs:**
- `backend/src/main/java/de/dlr/shepard/v2/file/resources/FileReferenceV2Rest.java:208,269,309,413,466`
- `backend/src/main/java/de/dlr/shepard/v2/references/handlers/FileReferenceKindHandler.java`

**Blocked on:** PR #1966 merge (tombstones `POST /v2/files` first)

**Backlog row:** `APISIMP-FILE-PATH-RETIRE-2`

---

## Finding 3 — MINOR: Stale Javadoc in `ContainersV2Rest`

**Severity:** MINOR  
**Size:** XS  
**ID:** `APISIMP-STALE-COMMENTS`

`ContainersV2Rest.java` Javadoc still references the old per-kind path prefix
`/v2/timeseries-containers/{appId}/...` (lines 468, 515, 559, 601, 642, 694, 737, 785,
822, 870, 921, 958, 998, 1037, 1074) — paths that were retired by the APISIMP-TSCONT-APPID-KEY
/ CONT-NS-COLLAPSE series. These show up in generated OpenAPI descriptions and confuse
callers reading the spec.

**Fix:** Text-replace all occurrences of `/v2/timeseries-containers/{` → `/v2/containers/{`
in the `@Operation`/`@APIResponse` descriptions. XS change, no logic.

**First-ref:** `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:468`

**Backlog row:** `APISIMP-STALE-COMMENTS`

---

## What's NOT sprawl (verified correct)

| Surface | Verdict |
|---|---|
| `/v2/admin/semantic` (SemanticAdminRest) | Correct — operational actions (ontology CRUD) separate from config CRUD at `/v2/admin/config/semantic` |
| `/v2/admin/unhide` (UnhideAdminRest) | Correct — only harvest-key rotate/revoke operations; config at `UnhideConfigDescriptor` → `/v2/admin/config/unhide` |
| `/v2/jupyter/config` (JupyterConfigPublicRest) | Correct — public-readable endpoint (non-admin users need the JupyterHub URL); admin writes go to `/v2/admin/config/jupyter` |
| `/v2/sql/timeseries` (SqlTimeseriesRest) | Correct — single `POST` for DSL query execution; not a CRUD reference surface |
| `/v2/aas/` namespace | Correct — approved narrow plugin namespace per 191 §5 |
| Pagination: `page`+`pageSize` vs v1 `page`+`size` | Correct — two internally consistent vocabularies on two separate surfaces |
| `/v2/{kind}/{appId}/publications` | Correct — polymorphic publications list across entity kinds |

---

## New backlog rows filed

- `APISIMP-ANNOTATION-SUBRESOURCE-COLLISION` (CRITICAL, S) — see §Finding 1
- `APISIMP-FILE-PATH-RETIRE-2` (MAJOR, M) — see §Finding 2, blocked on #1966
- `APISIMP-STALE-COMMENTS` (MINOR, XS) — see §Finding 3

Dispatch order: 1 first (CRITICAL, unblocked), 2 second (MAJOR, blocked on #1966 merge), 3 third (XS cleanup).
