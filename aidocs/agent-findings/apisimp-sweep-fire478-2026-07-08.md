---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP sweep — fire-478 (2026-07-08)

Scope: full v2 REST surface; focus on OpenAPI annotation completeness, shared
utility extraction, and schema consistency, following the fire-477 X-Total-Count
sweep that closed all bare-list findings. Picks up where fire-477 left off.

## Context — fire-477 row close-out

| Row | Status after fire-478 |
|---|---|
| APISIMP-ADMIN-CONFIG-NO-XCOUNT (F1) | ✅ merged (fire-478, PR #2409, SHA 8642a17d) |

## Findings

### F1 — APISIMP-PROBLEM-DEDUP (M) 🔄 queued

`de.dlr.shepard.v2.*.resources.*Rest.java` — 76 v2 REST classes each define
an identical private `static Response problem(String type, String title,
Response.Status status, String detail)` three-liner. No shared utility exists
at `de.dlr.shepard.v2.common`. Representative: `CollectionContainersRest.java:115`,
`DataObjectV2Rest.java:974`, `CollectionWatchersRest.java:195`.

Fix: create `de.dlr.shepard.v2.common.ProblemResponse.java` with a single
`public static Response problem(...)` method, then replace each copy with a
static import. Touches 76 files — batch PR.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionContainersRest.java:115`

---

### F2 — APISIMP-XCOUNT-OPENAPI-HEADER (M) 🔄 queued

`backend/src/main/java/de/dlr/shepard/v2/**/*Rest.java` — 37 list endpoints
emit `X-Total-Count` at runtime but declare it only in the `description` string
of `@APIResponse(responseCode="200")`, not in a formal
`headers = @Header(name="X-Total-Count", schema=@Schema(type=SchemaType.INTEGER))`
block. Only `AdminConfigRest` declares it properly (PR #2409, fire-478).
Generated OpenAPI clients never see the header in the spec; pagination is
invisible to tooling.

Fix: add `headers = @Header(...)` to the existing `@APIResponse(responseCode="200")`
block for each of the 37 endpoints. The import
`org.eclipse.microprofile.openapi.annotations.headers.Header` is already present
in `AdminConfigRest` as the template.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/collection/resources/CollectionContainersRest.java:76`

---

### F3 — APISIMP-TSCHANNEL-JSONIGNORE (M) 🚫 duplicate/blocked

`backend/src/main/java/de/dlr/shepard/v2/timeseriescontainer/io/TimeseriesChannelV2IO.java:38,49`
— `int id` (Postgres serial) and `long containerId` (Postgres serial FK) are
annotated `@Schema(deprecated=true)` but lack `@JsonIgnore`. Both fields still
serialize to every `GET /v2/timeseries-containers/{appId}/channels` response.

Finding is a re-observation of existing tracked rows:
- `APISIMP-TSCHANNEL-INT-ID-DEPRECATE` — for `int id`
- `APISIMP-TSCHANNEL-CONTAINER-ID` — for `long containerId`

Both are BLOCKED on TS-IDb/c Postgres migration (TS-CORE-SCHEMA-01).
No new row filed — duplicate.

---

### F4 — APISIMP-TPLREST-TAG-PAGECAP (XS) 🔄 in-flight (fire-478)

`backend/src/main/java/de/dlr/shepard/v2/template/resources/ShepardTemplateRest.java:308`
— `GET /v2/templates/tags` declares `@Max(500)` while `GET /v2/templates` on the
same class uses `@Max(200)`. The tags endpoint returns `List<String>` (lighter
than full template objects), but the divergent cap contradicts the predominant
`@Max(200)` standard across 32 other v2 list endpoints and is undocumented.

Fix: change `@Max(500)` to `@Max(200)` on line 308 and update the description
strings that say "1–500" to say "1–200". One-liner.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/template/resources/ShepardTemplateRest.java:308`

---

### F5 — APISIMP-PERM-AUDIT-DEPRECATED (XS) 🔄 in-flight (fire-478)

`backend/src/main/java/de/dlr/shepard/v2/admin/io/PermissionAuditEntryIO.java:20`
— `Long neo4jNodeId` exposes a raw Neo4j internal ID on the v2 wire. The `@Schema`
annotation documents the field as a "triage handle when appId is null (pre-migration
rows)" but omits `deprecated = true`, signalling no intent to remove it once the L2
migration completes. Callers may treat it as stable.

Fix: add `deprecated = true` to the existing `@Schema` and note it will be removed
once all rows carry a non-null `appId`. One-liner.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/admin/io/PermissionAuditEntryIO.java:20`

---

## Batch PR: `APISIMP-ANNO-MINOR-BATCH-1` (fire-478)

F4 + F5 implemented as standalone PR `APISIMP-ANNO-MINOR-BATCH-1`:
- `ShepardTemplateRest.java:308` `@Max(500)` → `@Max(200)` + description update
- `PermissionAuditEntryIO.java:20` `@Schema` gets `deprecated = true`

F1 (`APISIMP-PROBLEM-DEDUP`) and F2 (`APISIMP-XCOUNT-OPENAPI-HEADER`) queued for
future fires (batch PRs touching 76 and 37 files respectively).
