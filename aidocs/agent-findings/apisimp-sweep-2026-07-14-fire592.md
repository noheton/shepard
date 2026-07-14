---
stage: concept
last-stage-change: 2026-07-14
---

# APISIMP Sweep — fire-592 (2026-07-14)

Continuation sweep following fire-591 merge of PR #2550 and dispatch of PR #2551.
Scans the remaining `java.util.Date` and bare epoch-ms `Long`/`long` fields on the
live `/v2` REST surface after PRs #2549–#2551 are fully absorbed.

---

## Context

PRs merged or in-flight entering this fire:

| PR | Rows covered | Status |
|---|---|---|
| #2549 (fire-589) | APISIMP-UNHIDE-CONFIG-DATE-TO-ISO + APISIMP-HARVEST-KEY-MINTED-DATE-TO-ISO | ✅ shipped |
| #2550 (fire-592) | APISIMP-PLUGIN-ENTRY-DATE-TO-ISO + APISIMP-LAB-JOURNAL-REVISION-DATE-TO-ISO | ✅ shipped SHA 70ef4fba |
| #2551 (fire-592) | APISIMP-AAS-REGISTRATION-LONG-TO-ISO + APISIMP-DATACITE-CONFIG-DATE-TO-ISO + APISIMP-EPIC-CONFIG-DATE-TO-ISO + APISIMP-LEGACY-V1-STATS-DATE-TO-ISO | 🔄 in-flight — healed this fire |

Scan method: `grep -rn "java.util.Date" backend/src/main/java/de/dlr/shepard/v2/ plugins/ --include="*.java" -l`
then cross-checked against existing backlog rows to exclude already-filed items.

---

## Findings

### Finding 1 — `LegacyV1ConfigIO.updatedAt` (v1-compat plugin) — **HIGH** (epoch-ms, no @JsonFormat)

- **File:** `plugins/v1-compat/src/main/java/de/dlr/shepard/plugins/v1compat/io/LegacyV1ConfigIO.java:39,44`
- **Endpoint:** `GET /v2/admin/legacy/v1/config`
- **Current wire shape:** `"updatedAt": 1720955200000` (numeric epoch-ms)
- **Root cause:** Record component `Date updatedAt`; no `@JsonFormat` annotation; factory `from()` wraps `Long updatedAt` via `new Date(updated)`.
- **Fix:** Change to `String updatedAt`; factory: `updated == null ? null : Instant.ofEpochMilli(updated).toString()`.
- **Sibling row:** APISIMP-LEGACY-V1-STATS-DATE-TO-ISO (same plugin, in-flight PR #2551 fixes the stats IO; this fixes the config IO).
- **Filed:** APISIMP-LEGACY-V1-CONFIG-DATE-TO-ISO

### Finding 2 — `FeedEntryIO.dateCreated`/`dateModified` (unhide plugin) — **HIGH** (epoch-ms, no @JsonFormat, schema.org violation)

- **File:** `plugins/unhide/src/main/java/de/dlr/shepard/plugins/unhide/io/FeedEntryIO.java:81-82`
- **Endpoint:** `GET /v2/unhide/feed.jsonld` (JSON-LD feed)
- **Current wire shape:** `"dateCreated": 1700000000000` (numeric epoch-ms)
- **Root cause:** Record components `Date dateCreated`, `Date dateModified`; no `@JsonFormat` annotation; Jackson defaults to epoch-ms integers.
- **Schema.org violation:** schema.org `dateCreated`/`dateModified` vocabulary requires ISO 8601 dateTime strings. Numeric integers will fail JSON-LD processors and Helmholtz Unhide inward-mappings.
- **Fix:** Change both components to `String`; convert at call site in `UnhideFeedService` via `Instant.ofEpochMilli(...).toString()` with null-guard.
- **Filed:** APISIMP-FEED-ENTRY-DATE-TO-ISO

### Finding 3 — `NotebookReferenceIO.createdAt` (backend, lab-journal) — MEDIUM (has @JsonFormat(STRING), implicit format)

- **File:** `backend/src/main/java/de/dlr/shepard/v2/labjournal/io/NotebookReferenceIO.java:83`
- **Endpoint:** `GET /v2/lab-journal/{dataObjectAppId}/notebooks`
- **Current wire shape:** date string (format depends on ObjectMapper DateFormat config)
- **Root cause:** `private Date createdAt` with `@JsonFormat(shape=STRING)` at line 75. The format is implicit — not a numeric epoch-ms problem, but the `java.util.Date` type remains on the wire record.
- **Fix:** Change to `String`; convert in the factory/builder via `Instant.ofEpochMilli(...).toString()`.
- **Filed:** APISIMP-NOTEBOOK-REF-DATE-TO-ISO

### Finding 4 — `InstanceAdminGrantIO.grantedAt` (backend, admin) — MEDIUM (has @JsonFormat(STRING), implicit format)

- **File:** `backend/src/main/java/de/dlr/shepard/v2/admin/io/InstanceAdminGrantIO.java:55`
- **Endpoint:** `GET /v2/admin/instance-admins`
- **Current wire shape:** date string (format depends on ObjectMapper DateFormat config)
- **Root cause:** `private Date grantedAt` with `@JsonFormat(shape=STRING)` at line 48.
- **Fix:** Change to `String`; convert in `InstanceAdminService` via `Instant.ofEpochMilli(...).toString()` with null-guard.
- **Filed:** APISIMP-INSTANCE-ADMIN-GRANT-DATE-TO-ISO

### Finding 5 — `UserGroupV2IO.createdAt`/`updatedAt` (backend, users) — MEDIUM (has @JsonFormat(STRING), implicit format)

- **File:** `backend/src/main/java/de/dlr/shepard/v2/users/io/UserGroupV2IO.java:40,47`
- **Endpoint:** `GET /v2/user-groups`, `POST /v2/user-groups`, `PATCH /v2/user-groups/{appId}`
- **Current wire shape:** date strings (format implicit)
- **Root cause:** `private Date createdAt` (line 40) and `private Date updatedAt` (line 47), both with `@JsonFormat(shape=STRING)`.
- **Fix:** Change both to `String`; convert in constructor (lines 56–57, 60–61) via `Instant.ofEpochMilli(...).toString()` with null-guards.
- **Filed:** APISIMP-USER-GROUP-DATE-TO-ISO

### Finding 6 — `DataObjectSummaryIO.createdAt` (backend, dataobject) — MEDIUM (has @JsonFormat(STRING), implicit format)

- **File:** `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/DataObjectSummaryIO.java:40`
- **Endpoint:** `GET /v2/dataobjects/{appId}/predecessors`, `GET /v2/dataobjects/{appId}/successors`
- **Current wire shape:** date string (format implicit)
- **Root cause:** `private Date createdAt` with `@JsonFormat(shape=STRING)` at line 32; the PRED-V2-SHAPE javadoc says "ISO-8601 string on the wire" but the field type is still `Date`.
- **Fix:** Change to `String`; update constructor at line 58 via `Instant.ofEpochMilli(d.getCreatedAt().getTime()).toString()` with null-guard.
- **Filed:** APISIMP-DO-SUMMARY-DATE-TO-ISO

### Finding 7 — `GitCredentialIO.createdAt` (git plugin) — MEDIUM (has @JsonFormat(STRING), implicit format)

- **File:** `plugins/git/src/main/java/de/dlr/shepard/v2/users/io/GitCredentialIO.java:33`
- **Endpoint:** `GET /v2/users/me/git-credentials`
- **Current wire shape:** date string (format implicit)
- **Root cause:** `private Date createdAt` with `@JsonFormat(shape=STRING)` at line 31.
- **Fix:** Change to `String`; update constructor at line 40 via `Instant.ofEpochMilli(cred.getCreatedAt().getTime()).toString()` with null-guard.
- **Filed:** APISIMP-GIT-CREDENTIAL-DATE-TO-ISO

---

## Excluded / Not filed

| File | Reason |
|---|---|
| `backend/.../CollectionTimelineDAO.java` | DAO layer; uses `Date` internally for query construction, not on wire |
| `backend/.../AdminUserGitCredentialRest.java` | REST resource; reads `Date` from entity but delegates to `GitCredentialIO` (Finding 7) |
| `backend/.../InstanceAdminService.java` | Service layer; builds `InstanceAdminGrantIO` (Finding 4) |
| `backend/.../ImportStatsCollector.java` | Internal metrics; not serialised to API response |
| `plugins/unhide/.../UnhideConfig.java`, `plugins/unhide/.../UnhideFeedService.java` | Entity/service; the IO shape (Finding 2) is the wire layer |
| `plugins/unhide/.../UnhideFeedValidationTest.java`, `plugins/unhide/.../UnhideFeedServiceTest.java` | Test code; follow-on fix when Finding 2 ships |
| `plugins/minter-epic/.../EpicConfig.java` | CLI IO, not a v2 REST response body |
| `plugins/minter-datacite/.../DataciteConfig.java` | CLI IO, not a v2 REST response body |
| `plugins/spatiotemporal/...` test files | Test code for v1 frozen surface; not a v2 wire finding |
| `plugins/hdf5/...`, `plugins/video/...` | HDF5/video test files; no v2 wire impact |
| `plugins/v1-compat/.../LegacyV1StatsService.java` | Service layer; the IO is covered by PR #2551 |

---

## Summary

7 new APISIMP rows filed. Priority order for dispatch:

1. **APISIMP-FEED-ENTRY-DATE-TO-ISO** — schema.org violation; affects JSON-LD validity in the Helmholtz Unhide feed
2. **APISIMP-LEGACY-V1-CONFIG-DATE-TO-ISO** — no @JsonFormat; epoch-ms integers on the wire
3. **APISIMP-INSTANCE-ADMIN-GRANT-DATE-TO-ISO** — smallest footprint; single field, admin endpoint
4. **APISIMP-GIT-CREDENTIAL-DATE-TO-ISO** — single field, git plugin
5. **APISIMP-DO-SUMMARY-DATE-TO-ISO** — single field, predecessor/successor shape
6. **APISIMP-NOTEBOOK-REF-DATE-TO-ISO** — single field, lab-journal notebooks list
7. **APISIMP-USER-GROUP-DATE-TO-ISO** — two fields (createdAt + updatedAt), user-groups surface

All 7 are XS-sized; the first two (Finding 1+2) can batch into a single PR.
The @JsonFormat(STRING) findings (3–7) can batch into one further PR.
