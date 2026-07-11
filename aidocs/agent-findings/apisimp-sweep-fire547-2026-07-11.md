---
stage: deployed
last-stage-change: 2026-07-11
---

# APISIMP Sweep — fire-547 (2026-07-11)

Triggered by: context-window recovery mid-fire; APISIMP-PROV-CURSOR-PAGECAP (filed
fire-545) was the dispatchable queued slice missed by the earlier session. Sweep run
to identify any further residual sprawl after fire-546/547 X-Total-Count campaign.

Last shipped before this sweep: `APISIMP-XTOTALCOUNT-LIST-DO-2` (PR #2483, fire-547)
and `APISIMP-NAME-ALIAS-RETIRE` (PR #2481, fire-547).

## Scope

Scanned: `backend/src/main/java/de/dlr/shepard/v2/**/*Rest.java` (94 active files) +
`de.dlr.shepard.v2/**/*IO.java`. Focus areas: `@Deprecated` IO fields, `@PathParam`
impl-detail leaks, unpaginated list returns, stale doc strings.

---

## Finding 1 — APISIMP-TYPED-PRED-NEO4J-ID-DROP (XS) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/TypedPredecessorSummaryIO.java:28-38`

**What's wrong:** The record carries a `@Deprecated @JsonIgnore long predecessorId` component
that is never serialized to the wire (`@JsonIgnore`) and has `@Schema(hidden=true)`. It
is pure dead code — the fire-545 sweep noted its existence but did not file a removal row.
Removing it shrinks the record, removes a confusing hidden field from IDE completion, and
eliminates the last numeric Neo4j id from the `TypedPredecessorSummaryIO` surface.

```java
// TypedPredecessorSummaryIO.java:28-38
@Deprecated
@JsonIgnore
@Schema(readOnly = true, deprecated = true, hidden = true,
  description = "Deprecated — join on predecessorAppId (UUID v7) instead. …")
long predecessorId,   // ← dead weight; @JsonIgnore means it is never on the wire
```

**Fix:** Delete the four-line `@Deprecated @JsonIgnore @Schema(…) long predecessorId`
component from the record. No callers to update (the field is `@JsonIgnore`; no existing
Cypher or query passes this field).

**Size:** XS (single-file, no migration)

---

## Finding 2 — APISIMP-ANNOT-LEGACY-FIELDS-DROP (S) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/v2/annotations/io/AnnotationIO.java:88-106`

**What's wrong:** `AnnotationIO` carries four `@Deprecated` backward-compat aliases for
pre-SEMA-V6 callers:

```java
@Deprecated private String propertyName;   // alias for predicateLabel
@Deprecated private String propertyIri;    // alias for predicateIri
@Deprecated private String valueName;      // alias for objectLiteral
@Deprecated private String valueIri;       // alias for objectIri
```

These ARE serialized to the wire (no `@JsonIgnore`). They exist so that pre-v6 callers
reading `GET /v2/annotations/...` still see the old names. A frontend caller audit is
required before removal — any composable reading `propertyName` / `valueName` / etc. must
migrate to the canonical v6 names first.

**Fix:** (a) Audit all frontend callers of annotation responses for pre-v6 field names.
(b) Once confirmed clear, remove the four fields from `AnnotationIO` and its constructor.
Add a test asserting the four deprecated field names are absent from the serialized JSON.

**Size:** S (one backend class + constructor + frontend caller audit + test)

---

## Finding 3 — APISIMP-OID-PATHPARAM-REPLACE (M) — NEW

**Files:**
- `backend/src/main/java/de/dlr/shepard/v2/containers/resources/ContainersV2Rest.java:1453, 1553`
- `backend/src/main/java/de/dlr/shepard/v2/filecontainer/io/PresignedUploadUrlIO.java:26`
- `backend/src/main/java/de/dlr/shepard/v2/filecontainer/io/UploadCommitIO.java:16`

**What's wrong:** Two endpoints expose a MongoDB ObjectId as a URL path segment:

```
GET /v2/containers/{appId}/files/{oid}/thumbnail
GET /v2/containers/{appId}/files/{oid}/download-url
```

`oid` is a storage-impl detail (MongoDB GridFS / Garage object key) that leaks directly
into the public URL. Additionally, `PresignedUploadUrlIO.oid` and `UploadCommitIO.oid`
in the presigned-upload workflow also expose this token. The path and response field name
`oid` couples callers to the storage substrate.

The fix requires adding a stable `fileAppId` (UUID v7) to the `ShepardFile` entity that
the frontend and any API consumer can use as a stable addressing handle, replacing the
storage-internal `oid` token. This is a non-trivial schema change requiring a migration.

**Fix:** (a) Add `fileAppId UUID` column to the structured-data files table (Mongo doc or
S3 metadata — wherever `ShepardFile` records are kept). (b) Update the two `@Path` segments
from `{oid}` to `{fileAppId}`. (c) Rename `PresignedUploadUrlIO.oid` → `fileId` and
`UploadCommitIO.oid` → `fileId`. (d) Migrate old records. (e) Update frontend callers.

**Size:** M (schema migration + handler changes + frontend + 2 IO classes)

---

## Finding 4 — APISIMP-XTOTALCOUNT-DOC-CLEANUP (S) — NEW

**Files:** 17 v2 REST resource files (see below).

**What's wrong:** After the `APISIMP-XTOTALCOUNT-DUAL-EMIT-DROP` (fire-546) and
`APISIMP-XTOTALCOUNT-LIST-DO-2` (fire-547) campaigns fully removed the `X-Total-Count`
response header from every v2 endpoint, 17 files still contain `@APIResponse` description
strings that reference the header as still being emitted ("kept during deprecation window,
APISIMP-PAGINATION-ENVELOPE"). This is now misleading: callers reading the OpenAPI spec
will see "Header X-Total-Count = total count before paging" but the header is gone.

Files with stale doc strings:
`CollectionWatchersRest.java`, `CollectionV2Rest.java`, `CollectionContainersRest.java`,
`ContainersV2Rest.java`, `FlatPublicationsRest.java`, `NotificationRest.java`,
`CollectionDQRRest.java`, `ProjectsRest.java`, `ReferencesV2Rest.java`,
`ReferenceAnnotationRest.java`, `ShepardTemplateRest.java`, `VocabularyBrowseRest.java`,
`PersonalVocabularyRest.java`, `CollectionLabJournalEntriesRest.java`,
`OntologyGitSourceRest.java`, `CollectionWatchesRest.java`, `DataObjectDAO.java`.

**Fix:** Remove all `X-Total-Count` mentions from `@APIResponse(description = ...)` and
`@Operation(description = ...)` strings in these 17 files. Replace with a note that
`PagedResponseIO.total` carries the count.

**Size:** S (mechanical string replacement in 17 files, no logic change)

---

## What was NOT found

- Numeric Neo4j `Long` leaks in v2 PathParams or response bodies: only the known blocked
  row `APISIMP-PERMISSION-AUDIT-NEO4J-ID` (line 3764 in aidocs/16) remains.
- Unpaginated list endpoints returning bare `List<T>`: `ContainersV2Rest.listVersions`
  and `getLinkedDataObjects` wrap in `PagedResponseIO` but serve all items in one page.
  Both are bounded in practice (versions per file, DataObjects per container), so not filed.
- Bespoke `*ConfigRest` admin classes: 0 (all migrated to generic registry).
- `@Path(Constants.SHEPARD_API + ...)` in v2 package: 0 violations.
- The pre-existing queued row `APISIMP-PROV-CURSOR-PAGECAP` (filed fire-545) is
  dispatchable and will be the next slice dispatched in fire-547 (after this sweep commit).

## New rows filed

| Row | Size | Status |
|-----|------|--------|
| APISIMP-TYPED-PRED-NEO4J-ID-DROP | XS | ⏳ queued |
| APISIMP-ANNOT-LEGACY-FIELDS-DROP | S | ⏳ queued |
| APISIMP-OID-PATHPARAM-REPLACE | M | ⏳ queued |
| APISIMP-XTOTALCOUNT-DOC-CLEANUP | S | ⏳ queued |

## Next sweep trigger

After APISIMP-XTOTALCOUNT-DOC-CLEANUP ships: check for any remaining v2-surface
description strings that reference removed features. Expectation: zero new findings.
