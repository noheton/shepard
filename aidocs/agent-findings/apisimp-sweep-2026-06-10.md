---
stage: concept
last-stage-change: 2026-06-10
---

# APISIMP — v2 surface simplification sweep (2026-06-10)

API-minimalist audit of the `/v2` REST surface (113 distinct `@Path` shapes
across `backend/.../v2/**` + `plugins/*/.../v2/**`). Goal: minimalist core /
extensible plugin — unify per-kind endpoints, kill numeric-id leaks, dissolve
bespoke surfaces a generic one already supersedes. RESEARCH + BACKLOG ONLY; no
production Java/Vue touched.

Grounded against the **Microsoft REST API Guidelines / Azure API-design best
practices** (learn.microsoft.com/azure/architecture/best-practices/api-design):
three of its named rules back the core findings — *"Avoid creating APIs that
mirror the internal structure of a database / expose internal implementation
details"* (the numeric-id + OGM-id leaks), *consistent `limit`/`offset`
pagination with defaults* (the page/pageSize/size/limit mess), and *"child
entities reached from the root via a collection sub-resource"* vs a polymorphic
filter (the annotation-surface tension). Zalando RESTful API Guidelines
(rule 248 "consistent error model" / Problem JSON) and Google AIP-158
(consistent pagination across a surface) corroborate.

## What I found

### Priority 1 — Semantic-annotation surfaces

The generic polymorphic surface `de.dlr.shepard.v2.annotations.resources.SemanticAnnotationV2Rest`
at `/v2/annotations` (SEMA-V6-004) is full-featured and appId-keyed: list (with
`subjectAppId`/`subjectKind`/`predicateIri`/`vocabId` filters + `page`/`pageSize`),
text-`find`, get-by-appId, Turtle export, create, merge-patch update, delete with
policy, RFC-7807 Problem JSON throughout, `:Activity` capture + skip-capture
handoff. It is the single canonical surface.

Six bespoke `extends SemanticAnnotationRest` resources persist. **Critically,
the operator's brief mis-scoped three of them** — they sit on the **frozen
`/shepard/api/` v1 surface**, NOT `/v2/`, so they are OUT of scope for this
fork-owned simplification (CLAUDE.md: never change v1 wire shape):

| Resource | Path | In scope? |
|---|---|---|
| `CollectionSemanticAnnotationRest` (`context/semantic/endpoints`) | `Constants.SHEPARD_API + /collections/{id}/semanticAnnotations` | **NO — frozen v1** |
| `DataObjectSemanticAnnotationRest` (`context/semantic/endpoints`) | `Constants.SHEPARD_API + …` (numeric) | **NO — frozen v1** |
| `BasicReferenceSemanticAnnotationRest` (`context/semantic/endpoints`) | `Constants.SHEPARD_API + …` (numeric) | **NO — frozen v1** |
| `FileContainerSemanticAnnotationRest` (`v2/filecontainer/resources`) | `/v2/file-containers/{containerId}/annotations` | **YES — bespoke /v2** |
| `TimeseriesContainerSemanticAnnotationRest` (`v2/timeseriescontainer/resources`) | `/v2/timeseries-containers/{containerId}/annotations` | **YES — bespoke /v2** |
| `StructuredDataContainerSemanticAnnotationRest` (`v2/structureddatacontainer/resources`) | `/v2/structured-data-containers/{containerId}/annotations` | **YES — bespoke /v2** |

So the in-scope dissolution is **3 `/v2/` container-SA resources**, all
`Long containerId`-keyed (numeric Neo4j id leak — `getContainer(containerId)`
takes the raw OGM id). They are functionally a strict subset of
`/v2/annotations` (subjectKind `FileContainer`/`TimeseriesContainer`/
`StructuredDataContainer` + the container's appId).

**The legacy `SemanticAnnotationIO` they return leaks DB internals** that the
new `AnnotationIO` does not: `private Long id` (Neo4j node id),
`propertyRepositoryId` + `valueRepositoryId` (OGM repository longs,
`= -1` sentinel). This is the exact Microsoft "don't mirror the DB schema /
don't expose internal implementation details" anti-pattern. `AnnotationIO`
(SEMA-V6) carries `appId` + the predicate/object triple with no node-id or
repository-id leak. Nothing the legacy IO carries is *missing* from the new IO
except the numeric ids — which is the point of removing them.

**Callers (grepped frontend/, e2e/, examples/, clients/, MCP):**
- The 3 bespoke `/v2/` container-SA paths: **one** live caller —
  `e2e/api/tests/test_v2_features.py:35` probes
  `GET /v2/timeseries-containers/{cid}/annotations` (tolerant: accepts non-2xx).
  No frontend, no MCP, no seed, no backend-test caller. The FE
  `useTimeseriesContainerAnnotations.ts` composable hits
  `/temporal-annotations` (a DIFFERENT feature — see boundary below), not
  `/annotations`. So the dissolution is effectively a backend-delete + one
  e2e-probe repoint; no FE migration needed.
- `/v2/annotations` (the keeper): FE `composables/semantic/useAnnotations.ts`,
  `components/semantic/AnnotationChip.vue`, `AnnotationDialog.vue`.

**The KEEP boundary (NOT container-SA, must stay distinct):**
- `TimeseriesChannelAnnotationRest` —
  `/v2/timeseries-containers/{containerId}/channels/{channelShepardId}/annotations`
  (per-channel annotations; distinct feature, NOT a container-SA).
- `TimeseriesContainerTemporalAnnotationRest` —
  `/v2/timeseries-containers/{containerId}/temporal-annotations`
  (time-window annotations on the container's timeline; the live FE feature).
- `TimeseriesAnnotationRest` —
  `/v2/timeseries-references/{refAppId}/annotations` (TA1a: appId-keyed
  start/end-Ns windowed annotations on a `:TimeseriesReference`; the lumen seed
  calls this, `seed.py:2253`). This one is **already appId-keyed** and is its
  own typed feature (anomaly-window labels with `aiGenerated`/`confidence`) —
  NOT a SA-CONT and NOT a numeric-id leak; keep.

### Priority 2 — File-container thumbnails (TH1a)

`ThumbnailRest` (`v2/filecontainer/resources/ThumbnailRest.java`):
`GET /v2/file-containers/{containerAppId}/payload/{oid}/thumbnail?size=`.
**Already appId-keyed** (`containerAppId` String + `oid` String) — no numeric-id
leak. It rides the file-container *payload* sub-resource, which is the natural
home (the thumbnail of a payload object). It is genuinely file-kind-specific
(renders raster/text payloads to PNG via `ThumbnailProvider` SPI). It is NOT a
candidate for `/v2/shapes/render` (render is template/shape-rooted; thumbnails
are a payload-derived raster with a fixed size enum, no shape). **Verdict:
KEEP as-is.** The only cosmetic nit: `size` is silently normalised to 400 on
any invalid value rather than 400-ing — the `@APIResponse(400)` is documented
but never returned. Minor; not worth a row alone.

### Priority 3 — HdfContainer (v2)

Per §7a of `aidocs/platform/191`, the bespoke `HdfContainerRest`
(`/v2/hdf-containers/{appId}` + `/file`) was **already DISSOLVED 2026-06-10**
(A7-HDF-UNIFY): base CRUD now flows through `/v2/containers?kind=hdf` via the
shipped `HdfContainerKindHandler`, and the load-bearing raw `/file` download
moved onto the generic `GET /v2/containers/{appId}/file` resolver. The only
remaining hdf5-plugin `/v2/` path is `HdfAdminRest`
(`POST /v2/admin/hdf/rebuild-acls`) — a genuinely kind-specific operator
escape-hatch (re-asserts HSDS domain ACLs from shepard's permission graph;
idempotent; `@EndpointDisabled` on `shepard.hdf.enabled`). It is appId-keyed in
its result body (per-container `appId` in `errors[]`), takes no numeric path
param, and has **no generic-surface equivalent** (admin config-registry covers
config, not an ACL-rebuild job). **Verdict: no hdf duplication remains; KEEP
`HdfAdminRest`.**

### Priority 4 — Payload versionings (PV1a/PV1b)

Two resources:
- `PayloadVersionRest` — `GET /v2/file-containers/{containerAppId}/files/{originalName}/versions`
- `StructuredDataPayloadVersionRest` — `GET /v2/structured-data-containers/{containerAppId}/files/{originalName}/versions`

Both **already appId-keyed** (`containerAppId` String) — no numeric leak. Both
are read-only `versions` sub-resources, identical shape, differing only in the
container kind. This is **per-kind sprawl**: two near-identical resources that a
`/v2/containers/{appId}/files/{name}/versions` generic (dispatched through a
`ContainerKindHandler.listVersions(...)` default → 415 for kinds with no
file-payload versioning) would collapse to one — the exact shape the shipped
`ContainerFileDownload` / `ContainerKindHandler.downloadFile` generic used for
A7-HDF-UNIFY. Low caller count (no FE caller found; PV is backend-tested).
**Verdict: unify-under-generic candidate (S), low risk.**

### Whole-surface sweep — additional smells

**(a) Numeric-id leak island — the whole `/v2/timeseries-containers/{containerId}/…`
cluster.** Five resources take `@PathParam Long containerId` (the raw Neo4j OGM
id, consumed directly as `getContainer(containerId)`):
- `TimeseriesContainerChannelsRest` (`/channels`, the big one — list/ingest/…)
- `TimeseriesChannelAnnotationRest` (`/channels/{channelShepardId}/annotations`)
- `TimeseriesContainerTemporalAnnotationRest` (`/temporal-annotations`)
- `TimeseriesContainerChartViewRest` (`/chart-view`)
- `TimeseriesContainerSemanticAnnotationRest` (`/annotations` — the P1 dissolve)

This is the single largest numeric-id-leak surface left in `/v2/`. It
contradicts the appId-keyed contract directly (`ContainersV2Rest` keys on
`{appId}`; these key on `{containerId}` Long). It also blocks the FE container
routes from going appId-native (`CONTAINER-V2-ROUTE` in aidocs/16 is the FE
half of the same debt). The `channelShepardId` path param in
`TimeseriesChannelAnnotationRest` is a second numeric leak in the same resource.

**(b) Pagination inconsistency.** Across v2 list endpoints, four different
param vocabularies coexist: `page`+`pageSize` (annotations), `page`+`size`
(channels/others), bare `limit` (6 endpoints), `limit`+`offset`-style. Defaults
vary (`50`, `100`, `200`, `20`). Microsoft/Zalando/AIP all prescribe ONE
vocabulary with a capped default. Recommend standardising on `page`+`pageSize`
(already the SEMA-V6 + most-common choice) with a documented max.

**(c) Error-envelope inconsistency.** ~10 v2 resources return plain-string
`.entity("startNs is required")` bodies on 4xx (e.g. `TimeseriesAnnotationRest`,
`ContainersV2Rest`, `FileBundleReferenceRest`, the importer cluster) while the
newer surfaces (`SemanticAnnotationV2Rest`, `HdfAdminRest`) return RFC-7807
`application/problem+json`. A client can't parse errors uniformly. Recommend
converging on Problem JSON (Zalando rule 248).

**(d) Forbidden additions.** Grepped `@Path(Constants.SHEPARD_API + …)` newly
added under `/v2/**` and `plugins/**/v2/**`: **zero**. The three SHEPARD_API
SA resources are the long-standing frozen v1 surface, not new additions. Clean.

## Opportunities

Prioritised by effort×value. Each: simplification / benefit / risk+callers /
opposing-view.

1. **Dissolve the 3 bespoke `/v2/` container-SA resources onto `/v2/annotations`.**
   *Benefit:* removes 3 resources + the numeric `Long containerId` leak + the
   DB-internal-id-leaking `SemanticAnnotationIO` from the `/v2/` surface; one
   canonical annotation surface (the CLAUDE.md "one canonical store" promise on
   the wire too). *Risk/callers:* exactly one caller (the tolerant e2e probe);
   repoint it to `GET /v2/annotations?subjectAppId={containerAppId}&subjectKind=TimeseriesContainer`.
   No FE/MCP/seed impact. *Opposing view:* the bespoke path expresses the
   parent→child relationship as a sub-resource (Microsoft's "child entities via
   collection sub-resource" is legitimate); the polymorphic filter is slightly
   less RESTful-pure. But the duplication cost + numeric leak + IO leak
   outweigh purity here, and `/v2/annotations?subjectAppId=` gives the same
   scoping.

2. **Kill the numeric `Long containerId` leak across the timeseries-container
   cluster** (5 resources) by re-keying on `{containerAppId}` + resolving the
   OGM id in the service layer. *Benefit:* closes the largest remaining
   numeric-id-leak surface in `/v2/`; unblocks appId-native container routes
   (`CONTAINER-V2-ROUTE`). *Risk/callers:* FE timeseries panes call these; a
   path-param type change is a wire break, but `/v2/` is pre-production (no
   back-compat owed). Must land FE + backend in lockstep. *Opposing view:*
   larger blast radius than P1; the numeric id is *internally* convenient
   (avoids one appId→OGM resolve per call). But that's exactly the leak the
   appId contract forbids — convenience inside the service is fine, on the wire
   it is not.

3. **Unify the two payload-version resources** under
   `GET /v2/containers/{appId}/files/{name}/versions` via a
   `ContainerKindHandler.listVersions` default (415 for non-file kinds).
   *Benefit:* −1 resource, mirrors the shipped A7-HDF-UNIFY generic-file
   pattern; future kinds inherit versioning for free. *Risk/callers:* no FE
   caller; backend-tested only. Low. *Opposing view:* only two kinds today; a
   generic for N=2 is mild over-abstraction — but the handler SPI already
   exists, so the marginal cost is near-zero and the precedent is set.

4. **Standardise pagination** on `page`+`pageSize` (capped default) across all
   v2 list endpoints. *Benefit:* one client-side pager works everywhere;
   matches Microsoft/AIP guidance. *Risk/callers:* renaming `size`→`pageSize` /
   `limit`→`pageSize` is a wire change per endpoint; pre-prod so allowed, but
   touches many call sites. Medium-spread, low-depth. *Opposing view:* `limit`
   is the Microsoft-preferred name (their examples use `limit`/`offset`); one
   could standardise on `limit`/`offset` instead. Either is fine — the value is
   in picking ONE; `page`/`pageSize` already has the most adopters here.

5. **Converge error envelopes on RFC-7807 Problem JSON** for the ~10
   plain-string-4xx resources. *Benefit:* uniform machine-parseable errors
   (Zalando 248). *Risk/callers:* clients that string-match error bodies break;
   none found doing so. Low-medium. *Opposing view:* plain strings are simpler
   for a human curling the API; but the surface already standardised on Problem
   JSON in its newest resources, so consistency wins.

## Ideas

- A `ContainerKindHandler` is already the convergence point for
  download (A7-HDF-UNIFY) — extending it with `listVersions` (P3) and a future
  `listAnnotations` makes "everything about a container by appId" one generic
  resource with kind-dispatched optional capabilities. The timeseries-container
  cluster's channels/chart-view are the genuinely-TS-specific residue that
  legitimately stays bespoke (they have no analogue in file/structured kinds) —
  but even those should re-key on `{appId}`.
- A lightweight `PaginatedResponse<T>` envelope (`{items, page, pageSize,
  total?}`) would let P4 also fix the bare-array responses that hide whether
  more pages exist (several list endpoints return a raw JSON array with no
  cursor/total — AIP-158 flags this).
- The legacy `SemanticAnnotationIO`'s `propertyRepositoryId`/`valueRepositoryId`
  `-1` sentinels are a tell that the v1 semantic-repository model leaks through;
  when the three frozen-v1 SA resources are eventually sunset (out of this
  pass), that IO dies with them.

## Real-world impact

- A Python/Claude client reaching annotations on a TimeseriesContainer today
  must know to use `/v2/timeseries-containers/{numeric-id}/annotations` (and
  first resolve the numeric id) instead of the uniform
  `/v2/annotations?subjectAppId=…&subjectKind=…` — the same MCP-class confusion
  (`referenceIds` vs `appId`) the API-scrutinizer brief calls out. P1 removes
  the trap.
- The timeseries-container numeric-id leak is the backend root cause of the
  operator-surfaced `/collections/367014 → "Couldn't load the DataObject tree"`
  numeric-route class (V2-LINKS / CONTAINER-V2-ROUTE): as long as the backend
  *accepts* a numeric container id, the FE has an excuse to route on it. P2
  removes the excuse.
- Inconsistent pagination + error shapes are the silent tax on every
  generated-client and every notebook author — exactly the "chatty / surprising
  API" Microsoft warns degrades adoption.

## Gaps & blockers

- **P2 needs a coordinated FE+backend PR** (wire break). It pairs with the
  already-filed `CONTAINER-V2-ROUTE` / `V2-SWEEP-003-CONTAINER-API-MIGRATION`
  rows — sequence P2 with those, not independently.
- **`RESEED-FIND-MISC (a)`** already notes a perm asymmetry on
  `GET /v2/annotations?subjectAppId=<Collection>` (403s to owner). Repointing
  container-SA callers onto `/v2/annotations` (P1) is safe (containers walk
  DataObject→Collection perms, the working path), but the Collection-subject
  perm bug must be fixed before any *Collection*-SA repoint — out of P1 scope,
  noted so the two don't collide.
- I did NOT exhaustively diff every one of the 113 `@Path` IO bodies for
  unused-field verbosity (time-boxed); the `SemanticAnnotationIO` node-id leak
  was the clearest instance. A follow-up IO-field-usage sweep is worthwhile.

## What surprised me

- **Three of the operator's six named SA resources are frozen v1, not `/v2/`.**
  The brief listed `CollectionSemanticAnnotationRest` /
  `DataObjectSemanticAnnotationRest` / `BasicReferenceSemanticAnnotationRest`
  as dissolution targets, but they sit on `@Path(Constants.SHEPARD_API + …)` —
  the byte-frozen upstream surface. Dissolving them would break third-party
  upstream clients. The real in-scope dissolution is half the size (3, not 6).
- **The legacy SA IO leaks Neo4j node ids AND OGM repository ids** in its
  response body (`id`, `propertyRepositoryId`, `valueRepositoryId`) — a
  cleaner-cut DB-schema leak than I expected; the new `AnnotationIO` quietly
  fixed it, so the dissolution also removes a real abstraction leak from the
  wire, not just a duplicate path.
- **Thumbnails, payload-versions, and HdfAdmin are already appId-clean.** The
  brief flagged them as suspects; only the payload-version pair has a (mild)
  per-kind-sprawl issue. The genuine leak is concentrated entirely in the
  timeseries-container cluster — one well-defined island, not a scattered mess.
- **HDF duplication is already gone** (A7-HDF-UNIFY, 2026-06-10). The brief's
  "verify no bespoke `/v2/hdf/*` CRUD duplicates the unified surface" resolves
  to: confirmed, only the ACL-rebuild escape-hatch remains, and it's
  legitimately kind-specific.

## Cross-references
- `aidocs/platform/191-v2-surface-convergence.md` (V2CONV SSOT; §7a residual survey; §7/A7-HDF-UNIFY)
- CLAUDE.md: evolve-in-new-namespace; v1-frozen; appId-keyed v2; one-canonical-annotation-store; plugin-first
- `aidocs/16` rows: `CONTAINER-V2-ROUTE`, `ANNOT-V2`, `RESEED-FIND-MISC`, V2CONV-A* family
- Microsoft Azure API-design best practices (pagination `limit`/`offset`; "don't mirror the DB schema"); Zalando RESTful API Guidelines rule 248 (Problem JSON); Google AIP-158 (consistent pagination)
