---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP Sweep — fire-472 · 2026-07-08

**Scope:** v2 REST surface — `@APIResponse(responseCode = "200")` annotations where
list endpoints use the item type in `@Schema(implementation = ...)` instead of
`PagedResponseIO.class`, while the method body returns `Response.ok(new PagedResponseIO<>(...))`.

**Method:** Cross-referenced `new PagedResponseIO<` instantiation sites against
`implementation = PagedResponseIO\.class` schema annotations in all v2 REST files;
identified files present in the former but absent in the latter; read each candidate
to confirm whether the 200 schema mismatch is real or a legitimate single-entity return.

---

## §A1 — APISIMP-VOCAB-BROWSE-SCHEMA-MISMATCH (XS, 🔄 in-flight)

**Finding:** `VocabularyBrowseRest` (`GET /v2/semantic/vocabularies` and siblings)
has three list endpoints whose 200 `@APIResponse` annotation declares the **item**
type in `@Schema(implementation = ...)` instead of `PagedResponseIO.class`, while
every method body returns `Response.ok(new PagedResponseIO<>(...))`.

| Endpoint | operationId | Line | Wrong schema |
|----------|-------------|------|--------------|
| `GET /v2/semantic/vocabularies` | `listVocabularies` | 104 | `VocabularyIO.class` |
| `GET /v2/semantic/vocabularies/{vocabId}/predicates` | `listPredicatesForVocabulary` | 147 | `PredicateIO.class` |
| `GET /v2/semantic/vocabularies/used-by/{entityAppId}` | `listVocabulariesUsedBy` | 205 | `VocabularyIO.class` |

All three also include `mediaType = MediaType.APPLICATION_JSON` in the `@Content`
annotation — redundant given the class-level `@Produces(MediaType.APPLICATION_JSON)`.

**Fix:** Change all three `@Schema` declarations to `implementation = PagedResponseIO.class`;
remove the redundant `mediaType` parameter from each `@Content`. Annotation-only; no
runtime behaviour affected. Tracked as `APISIMP-VOCAB-BROWSE-SCHEMA-MISMATCH` in
`aidocs/16` (row 4011), dispatched fire-472, PR #2400.

---

## Files verified correct (not touched)

**List endpoints returning `PagedResponseIO` with correct `PagedResponseIO.class` schema:**
- `ReferenceAnnotationRest.java` — 200 schema now correct after PR #2399 (fire-471)
- `ReferencesV2Rest.java` — correct
- `ContainersV2Rest.java` (`listVersions`, `getLinkedDataObjects`) — correct
- `InstanceAdminRest.java` (`permissionAudit`, `permissionAuditLog`) — correct
- `CollectionWatchesRest.java` (list) — correct
- `CollectionLabJournalEntriesRest.java` — correct
- `NotificationRest.java` — correct
- `NotificationTransportRest.java` — correct
- `ProjectsRest.java` — correct
- `OntologyGitSourceRest.java` — correct
- `BundleGroupsV2Rest.java` — correct
- `CollectionV2Rest.java` — correct
- `ProvenanceRest.java` (`listActivities`) — correct
- `PublicationsListRest.java` (`listPublications`) — correct
- `UserGroupV2Rest.java` — correct after PR #2397 (fire-470)
- All other v2 REST list files — verified in prior fire sweeps

**Non-list annotations (not findings):**
- `PersonalVocabularyRest.java:105` — 201 POST response for single `VocabularyIO` (correct)
- `ProjectsRest.java:122` — 200 GET for single `ProjectIO` (correct)
- `CollectionWatchesRest.java:150` — 201 POST response for single `WatchIO` (correct)

---

## Summary

| ID | Severity | Status |
|----|----------|--------|
| APISIMP-VOCAB-BROWSE-SCHEMA-MISMATCH | XS | 🔄 in-flight (fire-472, PR #2400) |

Previous fire-471 PRs:
- PR #2398 (fire-471): `APISIMP-DEAD-SCHEMATYPE-IMPORTS` ✅ merged
- PR #2399 (fire-471): `APISIMP-REFANNO-LIST-NO-SCHEMA` 🔄 CI running
