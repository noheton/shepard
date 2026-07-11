---
stage: deployed
last-stage-change: 2026-07-10
---

# APISIMP Sweep — Fire 534 (2026-07-10)

Triggered: 0 queued APISIMP rows remained after fire-533 merged
`APISIMP-BUNDLEGROUPS-PAGECAP` (PR #2467). This sweep scanned the live
`/v2` REST surface for residual path-param inconsistencies and pagination
cap violations.

## Scope

All JAX-RS resource files under
`backend/src/main/java/de/dlr/shepard/v2/` plus plugin REST resources
under `plugins/*/src/main/java/de/dlr/shepard/v2/`.

## Methodology

1. Grepped all `@PathParam` annotations for non-`appId` names.
2. Checked `@Max` annotations on `pageSize` / `limit`-style params against
   the 200 cap standard.
3. Verified multi-param endpoints for by-design exceptions (two-entity
   paths where both params can't both be named `appId`).

## Findings

### Filed this fire

| Row ID | File | Finding | Size |
|--------|------|---------|------|
| APISIMP-COLL-LABJOURNAL-PATHPARAM | `CollectionLabJournalEntriesRest.java` | `{collectionAppId}` → `{appId}` in class `@Path` and method param. Single-entity path. | XS |
| APISIMP-DATAOBJECT-V2REST-PATHPARAM | `DataObjectV2Rest.java` | `{dataObjectAppId}` → `{appId}` across ~20+ occurrences in multiple methods; `{collectionAppId}` review. | S |

### By-design exceptions (not filed)

- **Two-entity endpoints** (`/v2/collections/{collectionId}/dataobjects/{dataObjectId}/...`):
  when a path contains two entity segments both cannot be named `appId` (JAX-RS
  collision). These are handled case-by-case in their respective APISIMP rows.
- **Domain-semantic size params** (`maxPoints`, `maxItems`, `windowSeconds`):
  exempt from the 200 cap standard per `aidocs/16` policy note.

### Already clean

All other `/v2` resources reviewed this fire carried either `{appId}` already
or a by-design two-entity exception. No new pagination cap violations found
beyond the two rows filed.

## Rows Dispatched

- `APISIMP-COLL-LABJOURNAL-PATHPARAM` — dispatched this fire (PR opened)
- `APISIMP-DATAOBJECT-V2REST-PATHPARAM` — queued for fire-535

## Next Sweep

Trigger: when `APISIMP-DATAOBJECT-V2REST-PATHPARAM` and any subsequent rows
are exhausted. The sweep should also revisit plugin REST surfaces (git,
spatiotemporal, krl-interpreter) once their V2 resources stabilise.
