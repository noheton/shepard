---
stage: deployed
last-stage-change: 2026-07-12
---

# APISIMP Sweep — fire-568 background agent (2026-07-12)

Triggered by: background sweep agent `afaa4a081abc0789a` ran concurrently with the
manual fire-568 scan. Scanned `de.dlr.shepard.v2/**/*IO.java` and related v2 REST
resource doc strings for residual deprecation annotation gaps and stale references.

Last shipped before this sweep:
- `APISIMP-PROV-STATS-ENTITYID-RENAME` (fire-568, PR #2505, sha `ecef299`)
- `APISIMP-LJE-ENTRY-ID-SUPPRESS` (fire-568, PR #2506, in-flight)

---

## Finding 1 — APISIMP-SEMA-ANNOT-ID-DEPRECATE (XS) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/context/semantic/io/SemanticAnnotationIO.java:17-18`

**What's wrong:** `SemanticAnnotationIO` carries `@Schema(readOnly = true, required = true) private Long id;` — the Neo4j OGM node ID of the annotation, wire-visible on every annotation response. `appId` (UUID v7) already exists at line 21. The `id` field has no `@Deprecated` Java annotation and `@Schema` lacks `deprecated = true`. Callers reading the OpenAPI spec see `id` as a first-class non-deprecated field when it is structurally equivalent to `LabJournalEntryIO.id` — the field being deprecated in fire-568 PR #2506.

Note: `id` is also returned by `getUniqueId()` (line 95) which implements `HasId` — an internal interface. The `getUniqueId()` usage is internal Java; the wire deprecation is separate.

**Fix:** Add `@Deprecated` Java annotation and `deprecated = true` + description to `@Schema` on `private Long id` at line 17–18:

```java
@Deprecated
@Schema(readOnly = true, required = true, deprecated = true,
    description = "DEPRECATED — numeric Neo4j OGM node ID. Use appId (UUID v7) instead.")
private Long id;
```

**Size:** XS (single annotation change, one file, no wire break)

---

## Finding 2 — APISIMP-SEMA-ANNOT-NAME-SCHEMA-DEPRECATED (XS) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/context/semantic/io/SemanticAnnotationIO.java:23-25`

**What's wrong:** `name` has Java `@Deprecated` (line 23) but `@Schema(readOnly = true, required = true)` (line 24) lacks `deprecated = true`. Client tooling that reads the OpenAPI/SmallRye spec sees `name` as non-deprecated even though the Java annotation says otherwise. This is the same gap that `propertyRepositoryId` and `valueRepositoryId` (lines 55–63) already correctly handle: both carry both Java `@Deprecated` and `@Schema(deprecated = true, …)`.

**Fix:** Add `deprecated = true` and a description to `@Schema` on `name`:

```java
@Deprecated
@Schema(readOnly = true, required = true, deprecated = true,
    description = "DEPRECATED — pre-SEMA-V6 display name. Use propertyName instead.")
private String name;
```

**Size:** XS (single `@Schema` attribute change, one file, no wire break)

---

## Finding 3 — APISIMP-LJE-COLL-DOCSTRING-DEPRECATED-FIELD (XS) — NEW

**File:** `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/CollectionLabJournalEntriesRest.java:52-60, 88-90`

**What's wrong:** Two stale references to the numeric `dataObjectId` and the frozen v1 surface:

1. Class Javadoc (lines 52–60) describes the N+1 hot bug fix by citing:
   - `GET /shepard/api/labJournalEntries?dataObjectId=N` (v1 path, deprecated)
   - "each entry already carries `dataObjectId` (the DataObject's shepardId — same numeric space as `GET /shepard/api/dataObjects/{id}`)": this conflates `dataObjectId` (numeric) with `dataObjectAppId` (UUID v7, the current field name in `LabJournalEntryIO`).

2. `@Operation` description (line 89): "Each entry carries its `dataObjectId`, so the frontend can group client-side" — should reference `dataObjectAppId` (the UUID v7 field on `LabJournalEntryIO` since fire-558).

**Fix:**
- In the class Javadoc: replace `GET /shepard/api/labJournalEntries?dataObjectId=N` with `GET /v2/collections/{appId}/lab-journal-entries`; replace "carries `dataObjectId` (the DataObject's shepardId — same numeric space as `GET /shepard/api/dataObjects/{id}`)" with "carries `dataObjectAppId` (UUID v7 of the parent DataObject)".
- In the `@Operation` description: replace "`dataObjectId`" with "`dataObjectAppId`".

**Size:** XS (doc-string only, one file, no logic change)

---

## What was NOT found

- `SemanticAnnotationIO` — `propertyRepositoryId` and `valueRepositoryId` already carry both Java `@Deprecated` and `@Schema(deprecated = true)`. No gap.
- No new `@PathParam Long` leaks in v2 IO classes beyond the two filed in fire-568 manual scan.
- No unpaginated bare-array list endpoints in the scanned IO files.
- No `@Path(Constants.SHEPARD_API + ...)` in v2 package.

## New rows filed

| Row | Size | Status |
|-----|------|--------|
| APISIMP-SEMA-ANNOT-ID-DEPRECATE | XS | ⏳ queued |
| APISIMP-SEMA-ANNOT-NAME-SCHEMA-DEPRECATED | XS | ⏳ queued |
| APISIMP-LJE-COLL-DOCSTRING-DEPRECATED-FIELD | XS | ⏳ queued |

## Next sweep trigger

After `APISIMP-LJE-ENTRY-ID-SUPPRESS` (PR #2506) and the three rows above ship:
check `SemanticAnnotationIO` once more for any remaining wire-visible Neo4j numeric
ids without `@Deprecated`. Expectation: zero new findings.
