---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP Sweep — fire-470 · 2026-07-08

**Scope:** v2 REST surface — dead `SchemaType` imports, remaining `SchemaType.ARRAY` annotation vs. wire-shape mismatches.

**Method:** `grep -rn "SchemaType" backend/src/main/java/de/dlr/shepard/v2/` cross-referenced with actual `return Response.ok(...)` types in each file.

---

## §A1 — APISIMP-DEAD-SCHEMATYPE-IMPORTS (XS, ⏳ queued)

**Finding:** Eight v2 REST resources import `org.eclipse.microprofile.openapi.annotations.enums.SchemaType` but have zero body references to `SchemaType.*`. The import is dead code left over from earlier APISIMP refactors that migrated annotations to `PagedResponseIO` or removed `SchemaType.ARRAY` usage.

| File | Import line | Body uses |
|------|-------------|-----------|
| `v2/watches/resources/CollectionWatchesRest.java` | 29 | 0 — list endpoint uses `@Schema(implementation = PagedResponseIO.class)` |
| `v2/labjournal/resources/CollectionLabJournalEntriesRest.java` | 32 | 0 |
| `v2/notifications/resources/NotificationRest.java` | 29 | 0 |
| `v2/notifications/transport/resources/NotificationTransportRest.java` | 29 | 0 |
| `v2/project/resources/ProjectsRest.java` | 26 | 0 |
| `v2/admin/semantic/OntologyGitSourceRest.java` | 38 | 0 |
| `v2/bundle/resources/BundleGroupsV2Rest.java` | 48 | 0 |
| `v2/collection/resources/CollectionV2Rest.java` | 49 | 0 |

**Files legitimately keeping SchemaType (verified, not touched):**
- `NotebookRest.java:112` — `SchemaType.ARRAY` for a bare `List<NotebookReferenceIO>` return (correct)
- `AdminUserGitCredentialRest.java:219` — `SchemaType.ARRAY` for a bounded bare list (correct, intentional per APISIMP-GIT-CRED-BARE-LIST ⛔ parked)
- `AdminConfigRest.java:81` — `SchemaType.ARRAY` for bare `List<ConfigFeatureIO>` (correct)
- `OntologyAlignmentRest.java:92` — `SchemaType.ARRAY` for bare list return (correct)
- `DataObjectV2Rest.java:195` — documented deviation, tracked separately (APISIMP-PAGINATION-ENVELOPE)
- `DataObjectBatchV2Rest.java:143` — `SchemaType.ARRAY` in `@RequestBody`, not `@APIResponse` (correct)
- `ImportDiagnosticsV2Rest.java:120,181` — bare arrays for event/run summaries (correct)
- `ContainersV2Rest.java:1454` — `SchemaType.STRING` for binary thumbnail (correct)
- `LabJournalRenderRest.java:97` — `SchemaType.STRING` for HTML response (correct)
- `DmpSnippetV2Rest.java:117` — `SchemaType.STRING` for Markdown response (correct)

**Fix:** Remove the unused `import` line from each of the 8 files. No runtime change; no migration needed. Tracked as `APISIMP-DEAD-SCHEMATYPE-IMPORTS` in `aidocs/16`.

---

## Summary

| ID | Severity | Status |
|----|----------|--------|
| APISIMP-DEAD-SCHEMATYPE-IMPORTS | XS | ⏳ queued → dispatched fire-470 |

Previous fire-469/470 PRs merged before this sweep:
- PR #2396 (fire-469): `APISIMP-SSE-SILENT-403-404` ✅
- PR #2397 (fire-470): `APISIMP-USERGROUP-LIST-SCHEMA-MISMATCH` ✅
