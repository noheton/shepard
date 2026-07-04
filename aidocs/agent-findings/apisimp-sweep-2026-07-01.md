---
stage: deployed
last-stage-change: 2026-07-01
---

# APISIMP sweep — 2026-07-01 (fire-338)

Scope: `backend/src/main/java/de/dlr/shepard/v2/**` — all 89 v2 `*Rest.java` files.
Frozen `/shepard/api/` v1 surface excluded throughout.

## Already swept (APISIMP section in aidocs/16)

The following categories are confirmed clean or already fully filed:

| Category | Status |
|---|---|
| Numeric Neo4j ID leaks (`Long`/`long` in `@PathParam`/`@QueryParam`) | ✅ fully swept — zero leaks in v2 |
| RFC-7807 error-envelope unification (plain-string 4xx responses) | ✅ fully swept — all v2 resources use `problem+json` |
| Pagination param naming (`?page=` / `?pageSize=`) | ✅ fully swept (APISIMP-PAGINATION-UNIFY series + APISIMP-COLLECTION-LIST-USES-SIZE-NOT-PAGESIZE) |
| `PagedResponseIO` envelope for list endpoints | ✅ substantially swept — APISIMP-PAGINATION-ENVELOPE PR #2102–2105 + residual fixes |
| `@Tag` sub-tag consolidation (Collection, Semantic, Timeseries, Misc) | ✅ F1–F4 all shipped or in-flight (PR #2208–2211) |

## New findings

### F1 — APISIMP-MISSING-OPERATIONID (S)

**What:** 30+ HTTP endpoint methods across 20+ v2 REST resources lack
`@Operation(operationId="...")` annotations. The OpenAPI generator auto-derives
names when the annotation is absent, producing fragile, implementation-coupled
identifiers like `getV2AdminConfig_0` that break generated TypeScript clients,
monitoring dashboards, and API documentation tools keyed on stable operationId
strings.

**Confirmed examples:**
- `AdminConfigRest.java:72` — `GET /v2/admin/config` (lists all feature toggles):
  the sibling method at line 93 has `@Operation(operationId="getFeatureConfig")`
  but `listFeatures()` at line 72 does not.
- `CollectionDQRRest.java:109,147` — `POST /v2/collections/{appId}/dqr` (assign)
  and `DELETE /v2/collections/{appId}/dqr/{dqrAppId}` (remove) both missing.
- `ReferencesV2Rest.java:305,338,370` — `PUT /v2/references/{appId}/content`,
  `GET /v2/references/{appId}/content`, `GET /v2/references` (list) all missing.
- `SqlTimeseriesRest.java`, `LabJournalRenderRest.java`, `NotebookRest.java`,
  `IndependenceProofRest.java` — each a single-method resource with zero `operationId`.

**Scale:** ~150 v2 endpoints total (89 resource classes × avg 1.7 HTTP methods);
scan shows ~30 methods affected across ~20 classes.

**AC:** Every `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH` method in
`de.dlr.shepard.v2.*` carries a sibling `@Operation(operationId="descriptiveName")`
following the established naming convention (`list*`, `create*`, `get*`,
`update*`, `patch*`, `delete*`). Generated OpenAPI spec contains human-readable
operationIds for all v2 endpoints. `mvn verify -pl backend` green.

**First-refs:**
- `backend/src/main/java/de/dlr/shepard/v2/admin/config/resources/AdminConfigRest.java:72`
- `backend/src/main/java/de/dlr/shepard/v2/quality/resources/CollectionDQRRest.java:109,147`
- `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java:305,338,370`
- `backend/src/main/java/de/dlr/shepard/v2/sql/resources/SqlTimeseriesRest.java`
- `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/LabJournalRenderRest.java`

---

### F2 — APISIMP-TAG-INCONSISTENCY-OUTLIERS (XS)

**What:** 3 resources use non-canonical `@Tag` values that remain after the
F1–F4 consolidation sweep:

| Resource | Current tag | Target tag | Rationale |
|---|---|---|---|
| `DmpSnippetV2Rest.java:61` | `"FAIR DMP snippet"` | `"Export"` | DMP is a form of structured export; collapses with `RepExportV2Rest` and `CollectionExportUrlRest` |
| `InstanceCapabilitiesRest.java:41` | `"Instance identity"` | `"Instance"` | Two resources (capabilities + identity) share the same "Instance identity" tag; shorten to singular for cleaner grouping |
| `InstanceIdentityRest.java:42` | `"Instance identity"` | `"Instance"` | See above |

Pure `@Tag` annotation changes; no wire shape change, no migration.

**AC:** All 89 v2 resources use a canonical tag name. Zero outlier tags in
`v2/**`. OpenAPI spec shows `"Export"` and `"Instance"` sections (no
`"FAIR DMP snippet"` or `"Instance identity"`). `mvn verify -pl backend` green.

**First-refs:**
- `backend/src/main/java/de/dlr/shepard/v2/fair/resources/DmpSnippetV2Rest.java:61`
- `backend/src/main/java/de/dlr/shepard/v2/instance/InstanceCapabilitiesRest.java:41`
- `backend/src/main/java/de/dlr/shepard/v2/instance/InstanceIdentityRest.java:42`

---

## Not filed (residual / known-good)

- **Thumbnail `?size=` param** (`ContainersV2Rest`) — this is image pixel
  dimension, not pagination size. Already documented per
  APISIMP-CONTAINERS-THUMBNAIL-SIZE-PARAM (merged PR #2025). Not a naming
  inconsistency.
- **`@Parameter` coverage** — substantially swept across multiple fires
  (fire-114, fire-137, fire-139, fire-140, …); remaining gaps are low-value
  cosmetic OpenAPI issues, not filed as new APISIMP rows this fire.
