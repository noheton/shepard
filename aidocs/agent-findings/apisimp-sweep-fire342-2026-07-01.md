---
stage: deployed
last-stage-change: 2026-07-01
---

# APISIMP Sweep — 2026-07-01 (fire-342)

**Scope:** Full scan of `backend/src/main/java/de/dlr/shepard/v2/` (95 REST resource files) against the standard APISIMP checklist. Frozen `/shepard/api/` v1 surface excluded throughout.

**Baseline:** fire-341 merged `APISIMP-MISSING-OPERATIONID` (PR #2213, 129 operationIds across 60 files). All named APISIMP rows are merged, blocked, or deferred. This sweep was run because `APISIMP-PERMISSION-AUDIT-NEO4J-ID` (the last queued row) remains blocked on L2 migration clean confirmation.

**Axes confirmed clean (no new findings):**

| Axis | Status |
|---|---|
| Numeric Neo4j id leaks (`Long`/`long` in `@PathParam`/`@QueryParam`/IO) | ✅ clean — one known exception tracked as `APISIMP-PERMISSION-AUDIT-NEO4J-ID` |
| Forbidden `@Path(Constants.SHEPARD_API + ...)` in v2 namespace | ✅ clean — zero occurrences |
| Per-kind path fragmentation vs. `?kind=` unification | ✅ clean — references and containers fully unified |
| Pagination param naming (`page` / `pageSize`) | ✅ clean — one `?size=` on thumbnail endpoint is pixel dimension (intentional, per fire-341) |
| RFC 7807 `ProblemJson` envelope on 4xx/5xx | ⚠️ **2 new exceptions** — see F2, F3 below |
| `PagedResponseIO<T>` envelope on list endpoints | ⚠️ **2 new bare-list returns** — see F4, F5 below |
| `@Tag` fragmentation | ✅ clean — all F1–F4 tag rows shipped (fires 335–340) |
| `@Operation(operationId=...)` coverage | ✅ clean — all 129 missing ids added (fire-341, PR #2213) |
| Bespoke admin `*ConfigRest` outside registry | ✅ clean — `AdminConfigRest` is the single registry entry |
| `@APIResponse(responseCode = "401")` documentation | ⚠️ **10 authenticated resources missing 401 doc** — see F1 below |

---

## MINOR (5)

### F1 — `@APIResponse(responseCode = "401")` missing from 10 authenticated v2 REST resources

**Slug:** `APISIMP-MISSING-401-RESPONSES` | **Size:** XS

10 v2 REST resources are protected by `@RolesAllowed("instance-admin")` or `@Authenticated` but declare no `@APIResponse(responseCode = "401")` entry. The actual Quarkus/Resteasy behavior is correct (401 returned for unauthenticated requests) but the generated OpenAPI spec omits the 401 response code entirely, misleading API consumers and SDK generators that assume undocumented response codes never occur.

This is the same class of gap that `APISIMP-MISSING-OPERATIONID` fixed for operationIds — a spec completeness issue, not a wire-behavior bug.

| File | Path | Auth mechanism |
|---|---|---|
| `v2/admin/config/resources/AdminConfigRest.java` | `GET|PATCH /v2/admin/config/...` | `@RolesAllowed("instance-admin")` |
| `v2/admin/ledger/resources/LedgerAnchorRest.java` | `GET /v2/admin/ledger` | `@RolesAllowed("instance-admin")` |
| `v2/admin/mffd/resources/MffdProcessChainMappingRest.java` | `GET|PATCH /v2/admin/mffd/...` | `@RolesAllowed("instance-admin")` |
| `v2/admin/files/resources/FileMigrationRest.java` | `POST /v2/admin/files/migrate` | `@RolesAllowed("instance-admin")` |
| `v2/admin/storage/resources/StorageAdminRest.java` | `GET /v2/admin/storage/...` | `@RolesAllowed("instance-admin")` |
| `v2/admin/resources/AdminFeaturesRest.java` | `GET|PATCH /v2/admin/features/...` | `@Authenticated` |
| `v2/admin/resources/InstanceAdminRest.java` | `GET|POST|DELETE /v2/admin/...` | `@RolesAllowed("instance-admin")` |
| `v2/admin/notifications/resources/NotificationAdminRest.java` | `GET|POST|DELETE /v2/admin/notifications/...` | `@RolesAllowed("instance-admin")` |
| `v2/admin/notifications/resources/NotificationTransportRest.java` | `GET|POST|PATCH|DELETE /v2/admin/notifications/transports/...` | `@RolesAllowed("instance-admin")` |
| `v2/vocabularies/resources/PersonalVocabularyRest.java` | `GET|POST|DELETE /v2/vocabularies/personal/...` | `@Authenticated` |

**Fix:** Add `@APIResponse(responseCode = "401", description = "Request is not authenticated.")` to each protected method in these files (or at class level where all methods are uniformly authenticated). Zero wire shape change — annotation-only, same scope as fire-341 operationId pass.

**AC:** All 10 files have `@APIResponse(responseCode = "401", ...)` on every authenticated method; generated OpenAPI spec documents 401 for all protected endpoints; `mvn verify -pl backend` green.

---

### F2 — `CollectionExportUrlRest` throws JAX-RS exceptions bypassing RFC 7807 envelope

**Slug:** `APISIMP-EXPORT-URL-EXCEPTION-SHAPE` | **Size:** XS

`CollectionExportUrlRest` (at `GET /v2/collections/{appId}/export-url`) throws `InternalServerErrorException` (lines 151, 162) and `ServiceUnavailableException` (lines 141, 166) from the Jakarta WS RS API. The Resteasy default exception mapper produces a non-`application/problem+json` body for these, bypassing the RFC 7807 envelope established by `APISIMP-ERROR-ENVELOPE-UNIFY`. The class already has a `problem()` helper at line 180–183; the throw sites just need to be converted to `return problem(...)`.

**File + lines:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionExportUrlRest.java:141,151,162,166`

**Fix:** Replace each `throw new InternalServerErrorException(...)` / `throw new ServiceUnavailableException(...)` with `return problem(PT_..., "...", Response.Status.INTERNAL_SERVER_ERROR/SERVICE_UNAVAILABLE, ...)`. Define two `PT_` constants. No logic change.

**AC:** All error paths on `CollectionExportUrlRest` return `Content-Type: application/problem+json`; `mvn verify -pl backend` green.

---

### F3 — `DataObjectV2Rest.listDataObjects()` throws bare `RuntimeException` on serialization failure

**Slug:** `APISIMP-DATAOBJECT-LIST-RUNTIME-EX` | **Size:** XS

`DataObjectV2Rest.java:334` throws `new RuntimeException("Failed to serialise DataObject list response", e)` when JSON serialization of the `GET /v2/data-objects` response fails. This is the only non-exception-mapper error path in that class. The Resteasy default mapper for `RuntimeException` produces a non-ProblemJson 500, bypassing RFC 7807. `ProblemJson` is already imported in the file.

**File + line:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/resources/DataObjectV2Rest.java:334`

**Fix:** Replace the `throw new RuntimeException(...)` with a structured `Response.serverError().type("application/problem+json").entity(new ProblemJson(...)).build()` return. Log the exception at `ERROR` level before returning.

**AC:** JSON serialization failure on `GET /v2/data-objects` returns `Content-Type: application/problem+json` with status 500; `mvn verify -pl backend` green.

---

### F4 — `SemanticAdminRest` throws `IllegalArgumentException` bypassing RFC 7807

**Slug:** `APISIMP-SEMANTIC-ADMIN-ILLEGAL-ARG` | **Size:** XS

`SemanticAdminRest.java:557` throws `new IllegalArgumentException("metadata part is required")` and `:568` throws `new IllegalArgumentException(ex.getMessage(), ex)` during multipart ontology upload validation. The JAX-RS default mapper for `IllegalArgumentException` produces a `400 Bad Request` with a plain-text or non-ProblemJson body. Affects `POST /v2/admin/semantic/ontologies`.

**File + lines:** `backend/src/main/java/de/dlr/shepard/v2/admin/semantic/SemanticAdminRest.java:557,568`

**Fix:** Add or reuse a private `problem()` helper following the pattern established in other v2 REST resources. Replace both `throw new IllegalArgumentException(...)` with `return problem(PT_BAD_REQUEST, "Bad Request", Response.Status.BAD_REQUEST, message)`. Define a `PT_BAD_REQUEST = "/problems/semantic-admin.bad-request"` constant.

**AC:** Bad multipart upload to `/v2/admin/semantic/ontologies` returns `Content-Type: application/problem+json` 400; `mvn verify -pl backend` green.

---

### F5 — `SemanticTermSearchRest` returns bare `List<>` inconsistent with `PagedResponseIO` envelope

**Slug:** `APISIMP-TERM-SEARCH-BARE-LIST` | **Size:** XS

`GET /v2/semantic/terms/search` accepts `@QueryParam("pageSize")` but returns a bare `List<TermSuggestionIO>` JSON array (`SemanticTermSearchRest.java:232`). Every other paginated list endpoint on the v2 surface returns `PagedResponseIO<T>` with `{items, total, page, pageSize}`. Callers who inspect the Content-Type or response shape for consistency with other v2 endpoints will encounter a structurally different response.

**File + line:** `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/SemanticTermSearchRest.java:232`

**Fix:** Wrap `results` in `new PagedResponseIO<>(results, results.size(), 0, effectivePageSize)`. Update the `@APIResponse` schema annotation from `SchemaType.ARRAY` to `implementation = PagedResponseIO.class`. `PagedResponseIO` is already imported throughout the v2 layer.

**AC:** `GET /v2/semantic/terms/search` returns `{items:[...], total:N, page:0, pageSize:M}`; `@APIResponse` schema updated; `mvn verify -pl backend` green.

---

## MAJOR (1)

### F6 — `CollectionIO.defaultFileContainerId` leaks numeric Neo4j id on `POST|PATCH /v2/collections`

**Slug:** `APISIMP-COLLECTION-CREATE-LONG-INPUT` | **Size:** M

`CollectionIO.java:34` declares `private Long defaultFileContainerId = null;` — a raw Neo4j node id — as an accepted input field on both `POST /v2/collections` (create) and `PATCH /v2/collections/{appId}` (update). The generated OpenAPI schema for these endpoints includes `"defaultFileContainerId": {"type": "integer", "format": "int64"}`. The operation description at `CollectionV2Rest.java:250` explicitly calls this a "legacy long id." Response IOs suppress the field via `@JsonIgnoreProperties`, so it does not leak on GET — only the input is affected.

This is a MAJOR APISIMP violation: the v2 surface must accept only `appId` (UUID v7) strings as entity identifiers. The current field forces callers to know the internal Neo4j node id of the file container they want to link.

**Files:**
- `backend/src/main/java/de/dlr/shepard/context/collection/io/CollectionIO.java:34`
- `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionV2Rest.java:250,279,354`
- `backend/src/main/java/de/dlr/shepard/context/collection/services/CollectionService.java:89-90,236-237`

**Fix:** Create a `CreateCollectionV2IO` / `UpdateCollectionV2IO` record that replaces `defaultFileContainerId: Long` with `defaultFileContainerAppId: String`. In `CollectionService.create()` / `update()`, resolve the appId to the Neo4j entity via a new `FileContainerService.findByAppId(String)` method. Remove the legacy field from the v2 input schema; log in `aidocs/34` as an additive breaking change to the `/v2/collections` input shape (pre-existing field that no external caller should be using given the "legacy" label in the operation description).

**AC:** `POST /v2/collections` accepts `defaultFileContainerAppId` (string) in the request body; `defaultFileContainerId` (Long) no longer appears in the OpenAPI input schema; `CollectionService` resolves by appId; `mvn verify -pl backend` green.

---

## Not filed (ruled out)

- **`?size=` thumbnail param** (`ContainersV2Rest.getThumbnail()`) — pixel dimension, not pagination size. Explicitly ruled out in fire-341 sweep ("intentionally named `size`, fixed from `sizeParam` in APISIMP-THUMBNAIL-SIZE-PARAM-NAME, fire-114. Not a finding.").
- **`CollectionDQRRest.evaluate()` bare list** — `POST /v2/collections/{appId}/dqr/evaluate` returns `List<DQRResultIO>`. This is an action endpoint, not a paginated list GET. The bare array shape is arguably correct for an evaluate-and-return-all semantics. Deferred pending a separate DQR UX review.
- **`JupyterConfigPublicRest`** — intentional public (non-admin) endpoint; not a bespoke admin config class.
- **`AdminFeaturesRest` at `/v2/admin/features`** — runtime in-JVM feature toggles, correctly separate from the persisted `:*Config` shapes in `AdminConfigRest`. Not a consolidation target.

---

## Summary

| Slug | Severity | Size | Status |
|---|---|---|---|
| APISIMP-MISSING-401-RESPONSES | MINOR | XS | ⏳ queued |
| APISIMP-EXPORT-URL-EXCEPTION-SHAPE | MINOR | XS | ⏳ queued |
| APISIMP-DATAOBJECT-LIST-RUNTIME-EX | MINOR | XS | ⏳ queued |
| APISIMP-SEMANTIC-ADMIN-ILLEGAL-ARG | MINOR | XS | ⏳ queued |
| APISIMP-TERM-SEARCH-BARE-LIST | MINOR | XS | ⏳ queued |
| APISIMP-COLLECTION-CREATE-LONG-INPUT | MAJOR | M | ⏳ queued |

**5 new XS rows + 1 MAJOR row filed.** Smallest dispatchable slice next fire: `APISIMP-MISSING-401-RESPONSES` or `APISIMP-TERM-SEARCH-BARE-LIST` (both XS, annotation/1-line changes, no migration, no frontend change).
