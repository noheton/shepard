---
title: v5 cross-instance quirks
parent: Reference
---

# v5 cross-instance quirks

> **Audience.** Operators and client developers integrating against the
> `/shepard/api/...` (upstream-frozen) surface. If you're using a client
> generated against the upstream OpenAPI spec, this page documents the
> places where this fork's wire shape differs from a clean upstream
> `gitlab.com/dlr-shepard/shepard 5.2.0` deployment.
>
> **Posture.** Per `CLAUDE.md §"API-version policy"`, `/shepard/api/...`
> stays **byte-compatible** with upstream 5.2.0. Every cross-instance
> quirk on this page is **drift** — either an additive field with a
> documented upgrade-ledger row in `aidocs/34-upstream-upgrade-path.md`
> (clean upgrade path, low impact), or a bug to track separately.
>
> **How this page stays honest.** Every quirk below is backed by a
> fixture file in `backend/src/test/resources/fixtures/v5/`. The
> `V5WireFidelityTest` superclass runs them on every CI build — a quirk
> with no fixture is a quirk we don't enforce, which is the bug.

## Additive fields on the v1 surface (per-row, traceable)

The fork main returns a small number of additional fields on
`/shepard/api/...` responses. All of them are **additive**: an
upstream-generated client using Jackson with default lenient
deserialisation (`@JsonIgnoreProperties(ignoreUnknown=true)`) keeps
working. A strict-schema client (rare) may need its schema relaxed.

| Field            | On shape(s)                                              | Source           | Tracker row |
|------------------|----------------------------------------------------------|------------------|-------------|
| `appId`          | every `BasicEntityIO` subclass                           | L2a (mint UUID)  | `aidocs/34` L2a |
| `revision`       | every `VersionableEntity` subclass (`Collection`, `DataObject`, `*Reference`) | V2a (write counter) | `aidocs/34` V2a |
| `heroImageUrl`   | `CollectionIO` — `@JsonInclude(NON_NULL)`, absent when unset (task #131; pre-fix the field leaked to `/shepard/api/` as `"heroImageUrl": null`) | Feature B | `aidocs/34` Feature B — fixed in task #131 |
| `license`, `accessRights` | every `AbstractDataObjectIO` subclass (`Collection`, `DataObject`) — `@JsonInclude(NON_NULL)`, absent when unset | FAIR-1 | `aidocs/34` FAIR-1 |
| `status`         | every `AbstractDataObjectIO` subclass — `@JsonInclude(NON_NULL)`, absent when unset | UX status field | (pre-snapshot) |
| `orcid`, `displayName` | `UserIO` — `@JsonInclude(NON_NULL)`, absent when unset (task #131 v5 wire-fidelity audit). `effectiveDisplayName` is always populated (fallback chain); `appId` is always minted on user creation. | U1a / U1b | `aidocs/34` U1a, U1b, #131 |
| `qualityScore`, `lastScoredAt`, `timeReference`, `wallClockOffset`, `wallClockOffsetSource` | `TimeseriesReferenceIO` — all `@JsonInclude(NON_NULL)`, absent when unset (task #131 v5 wire-fidelity audit) | AI1c + TM1 | `aidocs/34` AI1c, TM1, #131 |

## Subtractive / renamed / re-typed fields

> **None as of the snapshot date.** This is the section to populate
> when a quirk gets caught that materially breaks an upstream client —
> e.g. an enum value renamed, a numeric field returning a string, a
> required field being omitted in some path. If one of those is
> introduced and approved, the matching fixture in `fixtures/v5/`
> gets regenerated and a row lands here in the same PR.

## How the corpus catches drift

The fixtures define the contract. Every fixture pinned in
`backend/src/test/resources/fixtures/v5/` is exhaustive over its
endpoint's response shape (key set is checked strict — extras and
missing keys both fail the test). The `V5JsonNormalizer` tolerates
the well-known dynamic-mint fields (`id`, `appId`, `createdAt`,
`createdBy`, `updatedAt`, `updatedBy`, plus their array variants and
the per-IO additions in `V5WireFidelityTest.DEFAULT_DYNAMIC_FIELDS`).
Everything else is byte-strict.

If a future PR adds a sixth additive field to `UserIO`, or renames
`fileContainerId` to `fileContainerAppId`, or returns the
`permissionType` as `"READ_WRITE"` instead of `"READ_AND_WRITE"`, the
matching test in `de.dlr.shepard.integrationtests.wirefidelity` fails
on `mvn verify` — the operator never sees the broken wire.

## Endpoint coverage (snapshot)

| Entity kind                | Endpoints covered                | Fixtures |
|----------------------------|----------------------------------|----------|
| Collections                | `POST /collections`, `GET /collections/{id}` | 2 |
| DataObjects                | `POST .../dataObjects`, `GET .../dataObjects/{id}` | 2 |
| FileContainers             | `POST /fileContainers`           | 1 |
| TimeseriesContainers       | `POST /timeseriesContainers`     | 1 |
| StructuredDataContainers   | `POST /structuredDataContainers` | 1 |
| StructuredDataReferences   | `POST .../structuredDataReferences` | 1 |
| Users                      | `GET /users/{username}`          | 1 |
| Permissions                | `GET /collections/{id}/permissions` | 1 |

**Gaps deliberately left for follow-up:** `FileReference` (needs
multipart upload), `TimeseriesReference` (needs Timescale row insertion +
extensive payload setup), `Subscription`, `UserGroup`, `SemanticAnnotation`,
the `/shepard/api/collections/{id}/export` shape, the `/v2/` surface
(out of scope per task; `/v2/` is intentionally evolving). Adding any of
these is a 1-test, 1-fixture exercise per the "Adding a fixture" recipe
in `V5WireFidelityTest`'s javadoc.

## Running the corpus

```bash
# Standard CI run: assert mode (the default)
cd backend
./mvnw verify -Dit.test='*V5WireFidelityIT'

# Re-record after an approved wire change
./mvnw verify -Dit.test='*V5WireFidelityIT' -Dshepard.fixtures.record=true

# Single entity kind
./mvnw verify -Dit.test=CollectionV5WireFidelityIT
```

Re-recording is a wire-contract change. Cross-reference the change in
the same PR's `aidocs/34-upstream-upgrade-path.md` row and call it out
loudly in the PR description so the delta is reviewer-visible.
