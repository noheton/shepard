---
stage: fragment
last-stage-change: 2026-07-01
---

# APISIMP Sweep — 2026-07-01 (fire-348)

**Scope:** Full scan of the `/v2/` REST surface for API simplification findings.
**Criteria applied:** per-kind path prefixes not unified under `?kind=`; bespoke
`*ConfigRest` classes outside the generic registry; numeric Neo4j id leaks in
`@PathParam`/`@QueryParam`/response bodies; pagination param inconsistencies;
redundant/superseded endpoints; forbidden `@Path(Constants.SHEPARD_API...)`;
missing `@RolesAllowed`; response field noise from OGM internals.

**Baseline:** fire-330 swept the same surface and declared it clean (2026-06-30).
fire-348 merge: PR #2219 (APISIMP-MISSING-401-RESPONSES-P2 — annotation-only).

## Findings

### Finding 1 — `PermissionsIO` deprecated numeric fields

**File:** `backend/src/main/java/de/dlr/shepard/auth/permission/io/PermissionsIO.java:19,34,36`

Fields `entityId`, `readerGroupIds`, `writerGroupIds` are still present in the
serialised response for `GET /v2/containers/{appId}/permissions` and
`/v2/user-groups/{groupAppId}/permissions`.

**No new action** — already tracked and shipped as `APISIMP-CONTAINERS-PERMS-IO-NUMERIC`
(fire-212/213, PR #2086). The fields are marked `@Schema(deprecated=true)` and
`@JsonProperty(access=READ_ONLY)` — present during the deprecation window before
L2e drops them. Removal is deferred to the L2e cutover per the existing row notes.

---

### All other criteria

| Criterion | Result |
|-----------|--------|
| Per-kind endpoints not unified | ✓ clean — all reference kinds under `?kind=` |
| Bespoke `*ConfigRest` outside registry | ✓ clean — `AdminConfigRest` is the only registry; `JupyterConfigPublicRest` is intentional |
| Numeric id leaks (`@PathParam`/`@QueryParam`) | ✓ clean — all path and query params use appId |
| Pagination naming | ✓ clean — `page`/`pageSize` consistent across all list endpoints |
| Redundant/superseded endpoints | ✓ clean — no functional duplication |
| `@Path(Constants.SHEPARD_API...)` in v2 package | ✓ clean — none found |
| Missing auth gates | ✓ clean — class-level `@Authenticated`/`@RolesAllowed` consistent |
| OGM internals in IO bodies | ✓ clean — `@JsonIgnoreProperties` pattern applied; only `PermissionsIO` exception tracked as above |

## Summary

**No new backlog rows.** The v2 surface is clean against all sweep criteria. The
one candidate finding (`PermissionsIO` deprecated fields) is already tracked in
`APISIMP-CONTAINERS-PERMS-IO-NUMERIC` (shipped) and will be removed at L2e.

Remaining open APISIMP items:
- `APISIMP-PERMISSION-AUDIT-NEO4J-ID` — blocked, awaiting L2 migration confirmation
- `APISIMP-CONTAINERS-PERMS-IO-NUMERIC` removal — deferred to L2e cutover
