---
stage: fragment
last-stage-change: 2026-07-02
---

# APISIMP REST Surface Sweep — fire-355 (2026-07-02)

Axes scanned: 8 (per-kind sprawl · bespoke admin config · numeric-id leaks · pagination
inconsistency · unused response fields · shapes/render supersession · v1-path in v2 package ·
endpoint redundancy). Scope: `backend/src/main/java/de/dlr/shepard/v2/**/*.java`,
`plugins/*/src/main/java/**/*.java`, `frontend/composables/`.

---

## Axis 1 — Per-kind endpoints not unified under `?kind=`

**Clean.** `GitReferenceRest` and `VideoStreamReferenceV2Rest` are tombstone-only classes
that unconditionally return 410 Gone. No active CRUD surfaces remain under per-kind
`/v2/data-objects/{id}/<kind>-references` paths.

---

## Axis 2 — Bespoke admin config GET/PATCH surfaces outside ConfigRegistry

**3 findings.**

### F1 — APISIMP-PLUGINS-ADMIN-BESPOKE

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | S |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/admin/plugins/PluginsAdminRest.java` |
| **Lines** | 77 (`@Path("/v2/admin/plugins")`), 94 (`@GET`), 120 (`@PATCH`) |

`PluginsAdminRest` exposes `GET /v2/admin/plugins` (list all plugins with enabled state)
and `PATCH /v2/admin/plugins/{id}` (enable/disable one plugin), backed by `PluginRegistry`
and persisted to `PluginRuntimeOverride`. This is a second admin-config GET/PATCH surface
running in parallel to the generic `ConfigRegistry` at `/v2/admin/config/{feature}`. An
OpenAPI consumer sees no discoverable reason why plugins live at `/v2/admin/plugins` while
other mutable instance config lives at `/v2/admin/config/{feature}`.

**Fix (option a):** Implement a `PluginConfigDescriptor` wrapping `PluginRegistry`,
register in `ConfigRegistry`, route `GET /v2/admin/config/plugins` and
`PATCH /v2/admin/config/plugins` through the standard registry, tombstone
`/v2/admin/plugins` → 410.

**Fix (option b — minimal):** Add a Javadoc cross-reference on `PluginsAdminRest`
explaining why it cannot merge into `ConfigRegistry` (the per-plugin `{id}` path segment
requires a different URL shape than the `/{feature}` singleton).

---

### F2 — APISIMP-FEATURES-ADMIN-BESPOKE

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | S |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/admin/resources/AdminFeaturesRest.java` |
| **Lines** | 32 (`@Path("/v2/admin/features")`), 48 (`@GET`), 71 (`@PATCH`) |

`AdminFeaturesRest` exposes `GET /v2/admin/features` (list runtime feature toggles) and
`PATCH /v2/admin/features/{name}` (flip one toggle), backed by `FeatureToggleRegistry`.
Changes are explicitly **transient** — effective in the running JVM but not persisted
across restarts. This creates a third GET/PATCH admin surface alongside
`/v2/admin/config/{feature}` (persisted) and `/v2/admin/plugins` (persisted). The
transience is the stated reason for separation, but that distinction is invisible at the
URL level.

**Fix (option a):** Rename to `/v2/admin/runtime-toggles/{name}` (the word "runtime"
signals transience) and add an `x-transient: true` OpenAPI extension to the PATCH response.

**Fix (option b — minimal):** Implement a `FeatureToggleConfigDescriptor`, fold into
`ConfigRegistry` with a clearly documented `"transient": true` flag in the shape JSON so
consumers know PATCH `/v2/admin/config/features` reverts on restart.

At minimum: add a Javadoc cross-reference on `AdminFeaturesRest` → `AdminConfigRest`
explaining the intentional split.

---

### F3 — APISIMP-INSTANCE-REGISTRY-BESPOKE ✅ DONE (fire-355 2026-07-02)

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/admin/instance/resources/InstanceRegistryRest.java` |
| **Lines** | 53 (`@Path("/v2/admin/instances")`), 63 (`@GET @PermitAll`), 84 (`@PATCH @RolesAllowed`) |

Asymmetric auth split: `GET /v2/admin/instances` is `@PermitAll` (public, no token
required) while `PATCH /v2/admin/instances` is `@RolesAllowed(INSTANCE_ADMIN_ROLE)`. A
public-GET at a path under `/v2/admin/` is misleading — `/admin/` conventionally implies
operator-only access. This is a fourth parallel GET/PATCH admin-config surface with no
cross-reference to the others.

**Fix:** Move the public GET to `/v2/instance` (non-admin path) and keep the PATCH at
`/v2/admin/instance`, or register the writable portion via `ConfigRegistry` at
`/v2/admin/config/instance`. The public read can remain as a sister endpoint since
`JupyterConfigPublicRest` establishes the same pattern.

---

## Axis 3 — Numeric Neo4j ids leaking to wire

**Clean.** `TypedPredecessorSummaryIO.predecessorId` is `@Deprecated @JsonIgnore` — not
serialized. `DataObjectDetailV2IO` and `DataObjectListItemV2IO` both carry
`@JsonIgnoreProperties({"id", "collectionId", "referenceIds", ...})`. No new leaks beyond
the previously filed `APISIMP-PERMISSION-AUDIT-NEO4J-ID` (blocked on L2 migration).

---

## Axis 4 — Inconsistent / broken pagination

**2 findings.**

### F4 — APISIMP-TERM-SEARCH-NO-PAGE ✅ DONE (PR #2228 merged fire-355 2026-07-02)

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/semantic/resources/SemanticTermSearchRest.java` |
| **Line** | 209 (`@QueryParam("pageSize") @DefaultValue("20") int pageSize`) |

`GET /v2/semantic/terms/search` accepts `?pageSize=` (default 20, capped at 50) but has
no `?page=` or cursor offset parameter. The response is now wrapped in `PagedResponseIO`
(per the merged APISIMP-TERM-SEARCH-BARE-LIST), so callers receive a `total` count field
implying further pages are navigable — but there is no mechanism to retrieve them.

**Fix:** Add `@QueryParam("page") @DefaultValue("0") int page` to the search method;
thread it through as a `SKIP $skip` parameter in `FULLTEXT_CYPHER` and `CONTAINS_CYPHER`
alongside the existing `LIMIT $pageSize`. The Neo4j session.query call already passes a
param map; add `"skip": (long)(page * pageSize)`. Update `PagedResponseIO` constructor
call to pass the correct page offset.

---

### F5 — APISIMP-PROV-CURSOR-INCONSISTENT

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | S |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java` |
| **Lines** | All three `@GET @Path("/activities")` variants (JSON / PROV-JSON / JSON-LD) |

`GET /v2/provenance/activities` uses `?since=` / `?until=` temporal cursor parameters
together with `?pageSize=` to page through the event stream. Every other paginated v2
list endpoint uses `?page=` (zero-based offset) together with `?pageSize=`. A client that
reads the OpenAPI spec, sees `pageSize`, and assumes a `?page=` sibling will silently
receive only the first window on every call. The cursor model is correct and intentional
for an event stream; the naming is what misleads.

**Fix:** Rename `?pageSize=` → `?limit=` on all three `/activities` method signatures.
Update the OpenAPI `@Parameter` description to explicitly say "cursor window size — use
`?since=` / `?until=` for navigation, not `?page=`". This removes the false
`PagedResponseIO` implication while preserving the intentional temporal cursor design.

---

## Axis 5 — Response fields no frontend caller reads

**1 finding.**

### F6 — APISIMP-PROVENANCE-MODE-UNUSED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **Files** | `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/DataObjectListItemV2IO.java:110` |
|           | `backend/src/main/java/de/dlr/shepard/v2/dataobject/io/DataObjectDetailV2IO.java:118` |

Both IO classes carry a `provenanceMode` field (values: `"human"` / `"ai"` /
`"collaborative"` / null). A project-wide grep of `frontend/composables/` and
`frontend/components/` finds **zero references** to the string `"provenanceMode"`. The
field is serialized on every DataObject list and detail response but no frontend composable
or component reads or renders it. It was introduced for EU AI Act Art. 50 compliance
(PROV1j) and is planned for future rendering.

**Fix (option a — full):** Add a `ProvenanceModeChip` or similar UI indicator in the
DataObject list row and detail header, which resolves the unused-field issue by creating
the consumer.

**Fix (option b — minimal, 1-line):** Add `@JsonInclude(NON_NULL)` to `provenanceMode`
on `DataObjectListItemV2IO` (it already has it on `DataObjectDetailV2IO:117`) so
null-mode objects — the vast majority — don't serialize the key at all, shrinking list
payloads.

---

## Axes 6, 7, 8 — Clean

- **Axis 6 (superseded by `/v2/shapes/render`):** `GET /v2/lab-journal/{appId}/render`
  renders Markdown to sanitised HTML; `POST /v2/shapes/render` applies SHACL
  `VIEW_RECIPE` projections. Completely different domains; no overlap.
- **Axis 7 (`@Path(Constants.SHEPARD_API + ...)` in `de.dlr.shepard.v2.*`):** Two
  spatiotemporal files using `Constants.SHEPARD_API` live in `de.dlr.shepard.context` and
  `de.dlr.shepard.data` packages — not `de.dlr.shepard.v2.*`. Clean.
- **Axis 8 (redundant endpoints):** The three `@GET @Path("/activities")` methods on
  `ProvenanceRest` are legitimate JAX-RS content negotiation (`application/json` vs
  `application/ld+json` vs `application/prov+json`). `CrossDoBulkDataRest`
  (`POST /v2/data-objects/cross-timeseries-bulk`) is deliberately different grain from
  the per-container bulk endpoint. No redundant operations found.

---

## Summary table

| ID | Axis | Severity | Size | Description |
|---|---|---|---|---|
| APISIMP-PLUGINS-ADMIN-BESPOKE | 2 | MINOR | S | `PluginsAdminRest` bespoke GET/PATCH at `/v2/admin/plugins` outside ConfigRegistry |
| APISIMP-FEATURES-ADMIN-BESPOKE | 2 | MINOR | S | `AdminFeaturesRest` transient-toggle surface outside ConfigRegistry |
| APISIMP-INSTANCE-REGISTRY-BESPOKE | 2 | MINOR | XS | Public `@GET` at `/v2/admin/instances` misleads auth posture |
| APISIMP-TERM-SEARCH-NO-PAGE | 4 | MINOR | XS | `GET /v2/semantic/terms/search` has `pageSize` but no `page=` offset |
| APISIMP-PROV-CURSOR-INCONSISTENT | 4 | MINOR | S | `?pageSize=` on cursor-based `/v2/provenance/activities` misleads offset clients |
| APISIMP-PROVENANCE-MODE-UNUSED | 5 | MINOR | XS | `provenanceMode` serialized on every DataObject response; zero FE callers |
