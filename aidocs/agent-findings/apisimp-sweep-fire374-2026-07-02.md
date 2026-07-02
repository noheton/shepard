---
stage: fragment
last-stage-change: 2026-07-02
---

# APISIMP REST Surface Sweep — fire-374 (2026-07-02)

Axes scanned: plugin REST files for soft-clamp pagination vs JSR-380 annotations.
Scope: `plugins/aas/src/main/java/**/*.java` + other plugin REST files.

Context: prior sweeps completed the core v2 REST surface. This fire extends the
sweep to plugin REST pagination correctness. All named APISIMP rows are either
shipped, blocked, or operator-deferred; no named row is dispatchable.

---

## Axis 1 — JSR-380 vs soft-clamp on paginated list endpoints (1 finding)

### F1 — APISIMP-AAS-SHELLS-SOFT-CAPS

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | XS |
| **Files** | `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java` (2 methods), `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/admin/resources/AasRegistrationAdminRest.java` (1 method) |
| **Endpoints** | `GET /v2/aas/shells`, `GET /v2/aas/shells/{aasId}/submodels`, `GET /v2/admin/aas/registrations` |

All three list endpoints clamp their `page`/`pageSize` params with `Math.max(page, 0)` /
`Math.min(Math.max(pageSize, 1), MAX)` instead of JSR-380 `@PositiveOrZero` /
`@Min(1)` / `@Max(200)` annotations. This means:

- `page=-1` silently returns page 0 with HTTP 200 instead of 400.
- `pageSize=0` silently returns pageSize 1 with HTTP 200 instead of 400.
- `pageSize=99999` silently returns pageSize 200 with HTTP 200 instead of 400.

This is inconsistent with every other paginated list endpoint in the v2 surface
(e.g. `LabJournalHistoryRest`, `CollectionLabJournalEntriesRest`, snapshot manifest/
pinned-DO endpoints shipped by APISIMP-SNAPSHOT-MANIFEST-FAKE-PAGED and
APISIMP-SNAPSHOT-PINNED-DO-UNCAPPED), which return 400 for invalid values via
JSR-380. The OpenAPI schema annotations also miss the numeric constraint ranges.

The pagination itself is real (DAO-level `listAll(page, size)` + `countAll()`), so
this is a pure parameter-validation inconsistency.

**Fix:** In each of the three list methods:
1. Add `@PositiveOrZero` to the `page` `@QueryParam`.
2. Add `@Min(1) @Max(200)` to the `pageSize` `@QueryParam`.
3. Remove the `int safePage = Math.max(page, 0);` and `int safeSize = Math.min(Math.max(pageSize, 1), 200);` lines.
4. Replace `safePage`/`safeSize` usages in the method body with the raw `page`/`pageSize` params.
5. Add `import jakarta.validation.constraints.{Max,Min,PositiveOrZero};` (available — backend carries `quarkus-hibernate-validator`; spatiotemporal plugin uses the same imports).

**Acceptance criteria:**
- `GET /v2/aas/shells?page=-1` → 400.
- `GET /v2/aas/shells?pageSize=0` → 400.
- `GET /v2/aas/shells?pageSize=201` → 400.
- `GET /v2/aas/shells?page=0&pageSize=50` → 200 with paged shell list.
- Same three error cases for `/submodels` and `/admin/aas/registrations`.
- `mvn verify -pl plugins/aas` green.

---

## Other plugin REST files surveyed

| File | Finding |
|---|---|
| `HdfAdminRest.java` | No list endpoint — single POST action only. Clean. |
| `KipResolverRest.java` | No pagination; single-result resolver. Clean. |
| `MeCredentialsRest.java` | Returns `List<GitCredentialIO>` unbounded; per-user set is naturally bounded (practical max ~10). Intentional. |
| `WikiWriterRest.java`, `WikiWriterTombstoneRest.java` | No list endpoints. Clean. |
| `VideoStreamReferenceV2Rest.java` | No paginated list. Clean. |
| `LegacyV1StatsAdminRest.java` | `?topN` clamping documented + intentional. Clean. |
| `AasShellsRest.java` | See F1 above. |
| `AasRegistrationAdminRest.java` | See F1 above. |
| `SpatialDataPointRest.java`, `SpatialDataReferenceRest.java` | Frozen v1 surface — not in scope. |
| `UnhideAdminRest.java`, `UnhideFeedRest.java` | No list endpoints needing pagination. Clean. |
| `DataciteAdminRest.java`, `EpicAdminRest.java` | Credential endpoints; no list. Clean. |
| `SpatialPromoteRest.java` | Action endpoint; no list. Clean. |

---

## Summary

| ID | Size | Status |
|----|------|--------|
| APISIMP-AAS-SHELLS-SOFT-CAPS | XS | ⏳ queued |

Plugin REST surface is clean except for the AAS soft-cap pattern (F1).
All core v2 REST findings from prior sweeps are either shipped or in-flight.
