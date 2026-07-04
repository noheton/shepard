---
stage: fragment
last-stage-change: 2026-06-28
---

# APISIMP — v2 surface simplification sweep (2026-06-28, fire-277)

Incremental audit of the `/v2` REST surface after fire-277 merged
`APISIMP-USERGROUP-LIST-ENVELOPE` (PR #2142). This pass targeted list endpoints
not yet covered by the pagination-envelope wave, focussing on the template,
collection-template, and bundle subsystems.

Scope: `backend/.../v2/**` — REST resources only; no plugin surfaces.
Research-and-backlog only; no production Java/Vue touched.

## What I found

### Finding 1 — `GET /v2/templates` returns unbounded list

`ShepardTemplateRest.list()` (`v2/template/resources/ShepardTemplateRest.java:86-96`)
accepts `?kind` and `?includeRetired` query params but returns a plain
`List<ShepardTemplateIO>` via `Response.ok(rows).build()` — no `PagedResponseIO`
envelope, no `page`/`pageSize` params, no `total`. Every other list endpoint in the
v2 surface now uses the standard `PagedResponseIO<T>` shape (shipped via the
APISIMP-PAGINATION-ENVELOPE wave, fire-236). Instances grow with each template
admin adds; at large scale (hundreds of templates) this becomes an uncapped memory
hit. The picker UI (`GET /v2/templates`) will exhaust the whole table on every load.

**Filed as `APISIMP-TEMPLATES-LIST-UNBOUNDED` (XS). Dispatched this fire.**

### Finding 2 — `GET /v2/collections/{appId}/templates/allowed` unbounded

`CollectionTemplatesRest.listAllowed()` (`v2/template/resources/CollectionTemplatesRest.java:82-98`)
returns a plain `List<ShepardTemplateIO>` for the allowed-template curation set.
The curation set is expected to be small for any one collection, but carries no
server cap and grows with each `PUT /v2/collections/{appId}/templates/allowed` call.
`ShepardTemplateDAO.listAllowedForCollection()` returns an unbounded cypher result.
Fix requires a `countAllowedForCollection(String collectionAppId)` DAO method and
wrapping the REST response in `PagedResponseIO<ShepardTemplateIO>`.

**Filed as `APISIMP-UNBOUNDED-COLLECTION-TEMPLATES-ALLOWED` (S).**

### Finding 3 — `GET /v2/collections/{appId}/templates/used` unbounded

`CollectionTemplatesRest.listUsed()` (`v2/template/resources/CollectionTemplatesRest.java:100-116`)
returns a plain `List<ShepardTemplateIO>` for the used-template provenance set.
Includes retired templates (correct, per copy-on-write semantics) but carries no
server cap; a long-lived collection used from many templates accumulates an
unbounded set. `ShepardTemplateDAO.listUsedByCollection()` is also unbounded.
Fix: `countUsedByCollection(String collectionAppId)` DAO method + `PagedResponseIO`
wrapper.

**Filed as `APISIMP-UNBOUNDED-COLLECTION-TEMPLATES-USED` (S).**

### Finding 4 — `PUT /v2/collections/{appId}/templates/allowed` response unbounded

`CollectionTemplatesRest.setAllowed()` (`v2/template/resources/CollectionTemplatesRest.java:118-144`)
returns the updated allowed-set as a plain `List<ShepardTemplateIO>` after a PUT.
The client just posted the full id list, so it knows the new set — the response body
is redundant and unbounded. Consistent with Finding 2 (same DAO call).
Fix: return `PagedResponseIO<ShepardTemplateIO>` or an empty 204 response (the
simpler shape; client can re-fetch with `GET .../allowed` if it needs the full set).

**Filed as `APISIMP-COLLECTION-TEMPLATES-PUT-UNBOUNDED` (XS).**

### Finding 5 — `GET /v2/bundles/{bundleAppId}/groups` unbounded

`FileBundleReferenceRest.listGroups()` (`v2/bundle/resources/FileBundleReferenceRest.java:187-198`)
returns a plain `List<FileGroupIO>` via `Response.ok(rows).build()` — no
`PagedResponseIO` envelope. Bundles are expected to hold bounded file-group sets but
carry no server cap. The DAO's `findGroupsByBundleAppId` is also unbounded.
Fix: `countGroupsByBundleAppId(String bundleAppId)` DAO method + `PagedResponseIO`
wrapper on the REST response; add `page`/`pageSize` query params (defaults 0/50).

**Filed as `APISIMP-UNBOUNDED-FILEGROUPS` (S).**

### Finding 6 — `GET /v2/provenance/activities` time-cursor (documented intentional)

`ProvenanceRest.listActivities()` (`v2/provenance/resources/ProvenanceRest.java:104-137`)
has a `@QueryParam("pageSize")` but returns a raw `List<ActivityIO>`. The method
comment explicitly documents this as **time-cursor pagination** (`since`/`until`
epoch ms) not `PagedResponseIO` pagination: "The `?page=` parameter is not
supported and is silently ignored." This is an intentional design choice —
`COUNT(*)` on the Activity table is expensive at provenance scale, and the
time-cursor is the right bounded-query primitive here. **Not a standard pagination
gap.** Filed for audit completeness and to prevent future sweep re-detection.

**Filed as `APISIMP-UNBOUNDED-PROVENANCE-ACTIVITIES` (N/A — intentional design,
no fix needed).**

### Finding 7 — `GET /v2/provenance/entities/{entityAppId}/activities` time-cursor (documented intentional)

`ProvenanceRest.listEntityActivities()` (`v2/provenance/resources/ProvenanceRest.java:252-275`)
uses the same time-cursor pagination as Finding 6. Comment explicitly states: "Uses
time-cursor pagination (since/until epoch ms)." Intentional; `COUNT(*)` scoped to
an entity is also expensive for high-frequency actors. Filed for audit completeness.

**Filed as `APISIMP-UNBOUNDED-PROVENANCE-ENTITY-ACTIVITIES` (N/A — intentional
design, no fix needed).**

## Dispatch

Smallest finding with clean boundaries:
**`APISIMP-TEMPLATES-LIST-UNBOUNDED` (XS)** — dispatched this fire.

- `ShepardTemplateDAO`: add `count(String, boolean): long` + 4-arg
  `list(String, boolean, int, int): List<ShepardTemplate>` overload (2-arg stays for
  `ShapesApplicableRest`, `MappingsMcpTools`, `TemplatePortabilityRest`).
- `ShepardTemplateRest.list()`: add `page`/`pageSize` params (defaults 0/50, cap 200);
  return `PagedResponseIO<ShepardTemplateIO>`.
- `ShepardTemplateRestTest`: update 3 existing tests + add 3 new tests.

## Cross-references

- `aidocs/16` rows: `APISIMP-TEMPLATES-LIST-UNBOUNDED`, `APISIMP-UNBOUNDED-COLLECTION-TEMPLATES-ALLOWED`,
  `APISIMP-UNBOUNDED-COLLECTION-TEMPLATES-USED`, `APISIMP-COLLECTION-TEMPLATES-PUT-UNBOUNDED`,
  `APISIMP-UNBOUNDED-FILEGROUPS`, `APISIMP-UNBOUNDED-PROVENANCE-ACTIVITIES`,
  `APISIMP-UNBOUNDED-PROVENANCE-ENTITY-ACTIVITIES`
- Files: `backend/src/main/java/de/dlr/shepard/v2/template/resources/ShepardTemplateRest.java:86-96`,
  `backend/src/main/java/de/dlr/shepard/template/daos/ShepardTemplateDAO.java:34-48`,
  `backend/src/main/java/de/dlr/shepard/v2/template/resources/CollectionTemplatesRest.java:82-144`,
  `backend/src/main/java/de/dlr/shepard/v2/bundle/resources/FileBundleReferenceRest.java:187-198`,
  `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java:104-137,252-275`
