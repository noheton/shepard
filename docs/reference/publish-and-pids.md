---
layout: default
title: Publish and PIDs
permalink: /reference/publish-and-pids/
---

# Publish and PIDs

shepard publishes entities by minting persistent identifiers (PIDs)
through the HMC Kernel Information Profile (KIP) integration
designed in [`aidocs/66`](https://github.com/noheton/shepard/blob/main/aidocs/66-hmc-kip-integration.md).
The KIP1a baseline (this page) ships the `Minter` SPI seam, the
publish + resolver REST endpoints, and the `:Publication` Neo4j
entity. Post-KIP1h every minter — including the legitimate-default
`LocalMinter` — lives in a plugin per CLAUDE.md's plugin-first
heuristic #3. Production adapters (ePIC handles, DataCite DOIs) ship
as drop-in plugin JARs in KIP1c / KIP1d per the
[plugin-distribution ADR-0023](https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md#adr-0023).

For the casual-task expression see [Publish a DataObject or
Collection](/help/publish-data-object/).

## What gets published in KIP1a

| Entity kind | URL segment | KIP `digitalObjectType` |
|---|---|---|
| [DataObject](/reference/data-object/) | `data-objects` | `http://shepard.dlr.de/types/dlr:DataObject` |
| [Collection](/reference/collection/) | `collections` | `http://shepard.dlr.de/types/dlr:Collection` |

Future KIP slices add bundles, files, and lab-journal entries — the
`PublishableKind` enum + `PublishableKindRegistry` exists exactly so
adding a new kind doesn't change the URL shape.

## Shape

```
DataObject  ─(:HAS_PUBLICATION)─►  Publication
                                   ┌─────────────────┐
                                   │ appId           │
                                   │ pid (@Index)    │
                                   │ mintedAt        │
                                   │ minterId        │
                                   │ publishedBy     │
                                   │ entityKind      │
                                   │ entityAppId     │
                                   │ versionNumber   │
                                   └─────────────────┘
```

`:Publication` rows are **append-only** by KIP convention. The
most-recent row attached to an entity is the "current" Publication;
a force re-mint attaches a fresh row alongside, never replacing
the old one. KIP1h's versioned-PIDs Phase 1 stamps the row's
1-based `versionNumber` so the resolver can emit
`digitalObjectVersion: "v<n>"` without a fan-out query.

## Publishing from the UI

In addition to the REST surface below, the shepard web UI ships a
one-click **Publish button** on every [Collection](/reference/collection/)
and [DataObject](/reference/data-object/) detail pane (KIP1e
slice — see `frontend/components/context/publish/`).

The button only renders for users with **Writer** or **Manager**
permission on the entity (the same predicate that gates the
existing Edit / Add affordances on those panes — the backend still
enforces the permission check on the POST regardless of UI state).

Workflow:

1. Click **Publish** at the top of the entity detail pane.
2. Pick an SPDX licence in the modal that opens. The minimum-viable
   set covers the common shapes: `CC-BY-4.0`, `CC-BY-SA-4.0`,
   `CC0-1.0`, `MIT`, `Apache-2.0`, `LGPL-3.0`, `GPL-3.0`. A
   tracked-SPDX integration that draws from the canonical SPDX
   list is a follow-up slice.
3. Confirm. The modal explains the KIP append-only convention
   before the confirm CTA fires.
4. A snackbar surfaces the freshly-minted PID with two copy
   actions: **Copy resolver URL** (the public
   `/v2/.well-known/kip/...` URL HMC PID resolvers should dereference
   against) and **Copy PID** (the bare PID a researcher drops into
   a paper / dataset citation).

**Scope-down note (KIP1e).** The button reads "Publish" in all
states regardless of whether the entity already has a Publication
attached. Because the backend's POST is idempotent — a re-POST
without `?force=true` returns the existing row — clicking on an
already-published entity surfaces the same PID through the
snackbar. A follow-up slice will land an "existing-publication"
popover with the minted-at timestamp + the "Re-mint (force)"
affordance once a `GET /v2/{kind}/{appId}/publications` helper
endpoint exists (KIP1a's `BasicEntityIO` is frozen per
`CLAUDE.md`'s "frozen upstream classes" rule, so the publication
state isn't surfaced on the entity-fetch wire shape today).

**Licence wire-shape note.** The KIP1a `POST` endpoint does not
yet accept a request body — the licence sourced into the public
KIP record comes from the entity's metadata (`attributes.license`
if you've set one there). The KIP1e modal's licence drop-down is
**informational** today: the operator confirms intent, the publish
fires without a body, the licence stored on the entity stays
authoritative. The modal already emits the selected SPDX id on
its submit event, so when KIP1a grows a `licence` body field the
wire-up is a single prop forward.

## REST surface

### Publish an entity

```
POST /v2/{kind}/{appId}/publish[?force=true]
```

- **Auth.** Bearer JWT or `X-API-KEY`. Caller must hold **Writer**
  or **Manager** on the entity (per
  [`PermissionsService`](/reference/permissions/) — the
  `AccessType.Write` gate admits both).
- **Idempotency.** Second call without `?force=true` returns the
  existing most-recent Publication (200 OK, no fresh mint).
- **Forced re-mint.** `?force=true` bumps the entity's
  `versionNumber` and mints a new PID encoding the new version
  (e.g. `v1` → `v2`). The previous Publication row is preserved.

Response body (`application/json`):

```json
{
  "appId": "01HF6N3R-pub-row",
  "pid": "shepard:dlr.de/shepard-prod:data-objects:01HF...:v1",
  "mintedAt": "2026-05-13T08:11:00Z",
  "minterId": "local",
  "resolverUrl": "https://<shepard-host>/v2/.well-known/kip/<pid>",
  "publishedBy": "<username>",
  "entityKind": "data-objects",
  "entityAppId": "01HF...",
  "versionNumber": 1
}
```

### Resolve a PID (public)

```
GET /v2/.well-known/kip/{pid-suffix}
```

- **Auth.** None. The endpoint is registered in
  `PublicEndpointRegistry` as a prefix-matched public path; the
  next-character-must-be-slash rule guards against
  `kip-foo` shaped foot-guns.
- **Path matching.** `{pid-suffix}` is greedily matched via
  JAX-RS `{suffix:.+}`. Both `LocalMinter` PIDs (colon-separated)
  and Handle/DOI-shaped PIDs (slash-separated) work verbatim — no
  URL encoding required. Pre-KIP1h `mock:`-prefixed legacy PIDs
  also keep resolving (the resolver does an opaque `findByPid`).
- **Visibility.** The KIP record itself is findability metadata —
  type, landing-page URL, timestamps, rights-holder, version —
  never entity payload. The `landingPage` URL it points at *is*
  permission-gated; an anonymous client gets the KIP record but
  hits 401 on the landing page unless they're authorised. This
  mirrors the `aas-server` `.well-known` posture.

Response body (`application/json`, JSON-LD-flavoured):

```json
{
  "@context": "https://hmc.helmholtz.de/kip/v1",
  "id": "<PID>",
  "kernelInformationProfile": {
    "id": "<PID>",
    "landingPage": "https://<shepard-host>/v2/<kind>/<appId>",
    "digitalObjectType": "http://shepard.dlr.de/types/dlr:DataObject",
    "dateCreated": "2026-05-13T08:11:00Z",
    "dateModified": "2026-05-13T08:11:00Z",
    "rightsHolder": "<username>",
    "license": null,
    "digitalObjectVersion": "v1"
  }
}
```

## Configuration

| Key | Default | Notes |
|---|---|---|
| `shepard.publish.minter` | `local` | Active `Minter` adapter selected at startup. Deploy-time-only — switching the PID provider is a re-bootstrap decision (the "cluster identity / topology" exception in `CLAUDE.md`). Set to `none` (or blank) to disable the publish endpoint while keeping the KIP resolver online for pre-existing rows. |
| `shepard.instance.id` | `local` | Reused by `LocalMinter` as the `<instance.id>` segment of every locally-minted PID. Production deployments should set this to a namespaced value (e.g. `dlr.de/shepard-prod`) so PIDs minted by different instances don't collide. Operators on the `local` fallback see a startup WARN. |

Per-minter knobs (ePIC handle prefix, DataCite credentials) ship
with the matching plugin in KIP1c / KIP1d and use the standard
admin-runtime `:*Config` pattern from CLAUDE.md.

## The `Minter` SPI

```java
public interface Minter {
  String id();             // stable id used in shepard.publish.minter=<id>
  boolean isEnabled();     // adapter can self-report "not ready"
  MintResult mint(MintRequest req);
}
```

Post-KIP1h **every minter ships as a plugin** per CLAUDE.md
heuristic #3 ("SPIs in core, adapters in plugins").

- **`local` plugin** (`shepard-plugin-minter-local`, KIP1h, **the
  default for fresh installs**) — mints stable, versioned,
  local-instance PIDs of the form
  `shepard:<instance.id>:<kind>:<appId>:v<n>`. Never throws. No
  external service required. Renamed from the pre-KIP1h in-core
  `MockMinter`; "mock" misled operators into thinking it was a
  test-only fixture.
- **`epic` plugin** (`shepard-plugin-minter-epic`, KIP1c, queued)
  — real Handles via the PID Consortium / ePIC handle service.
  Drops into `backend/plugins/` as `shepard-plugin-minter-epic-*.jar`.
- **`datacite` plugin** (`shepard-plugin-minter-datacite`, KIP1d,
  queued) — DOIs via the DataCite REST API. Same drop-in shape.

### The optional-minter posture

Post-KIP1h the `MinterRegistry` is **optional** — operators who
haven't picked a PID provider yet (or who only want a resolver-only
deployment) boot cleanly with no active minter. The publish endpoint
returns 503 `publish.minter.not-installed` until either:

1. A minter plugin is installed on the classpath AND
   `shepard.publish.minter` is set to its id, **or**
2. (Explicit-disable) `shepard.publish.minter=none` is set — the
   sentinel value disables publish without uninstalling the plugin.

The KIP resolver (`/v2/.well-known/kip/{pid-suffix}`) keeps working
regardless — it reads pre-existing `:Publication` rows by PID.

## Versioned PIDs (Phase 1)

KIP1h shipped **versioned PIDs Phase 1**:

- The `LocalMinter` encodes a 1-based version segment in every PID:
  `shepard:<instance.id>:<kind>:<appId>:v<n>`. Same inputs → same
  PID (the format is stable across re-mints).
- The `:Publication` entity grows a `versionNumber: Integer`
  property. Pre-KIP1h rows are backfilled to `versionNumber=1` via
  `V31__Backfill_Publication_versionNumber.cypher` (idempotent).
- The `PublishService` computes the next version as
  `findLatestVersionNumber(appId) + 1` before minting, stamps it
  onto both the `MintRequest` and the persisted `Publication`.
- The `?force=true` re-mint **bumps the version** (v1 → v2 → v3)
  instead of stamping a fresh epoch-millis suffix. The previous
  Publication row stays — re-mint is additive per the KIP
  append-only convention.
- The `PublicationIO` response shape grows
  `versionNumber: int` (additive).
- The `KipRecordIO.KernelInformationProfile` body grows
  `digitalObjectVersion: "v<n>"` (additive; JSON-LD open-world
  semantics let pre-KIP1h clients ignore the new field).

Phase 2 (full `:EntityVersion` graph with `previousVersion` /
`nextVersion` edges) is queued as the ENT1 umbrella — the Phase-1
scalar persists as a stable read-side denormalisation.

## Errors

All non-200 responses ship `application/problem+json` per RFC 7807.

| `type` | Status | When |
|---|---|---|
| `publish.kind.unsupported` | 404 | URL segment isn't in the `PublishableKindRegistry`. |
| `publish.entity.wrong-kind` | 404 | The appId exists but is a different label than the URL segment claims. |
| `publish.minter.not-installed` | 503 | No active minter (KIP1h). The `detail` field guides the operator to install `plugins/minter-local/` (or another minter plugin) and set `shepard.publish.minter=<id>`. |
| `publish.minter.failed` | 500 | Active Minter threw `MinterException`. `detail` carries the operator-readable message. |
| `kip.pid.not-found` | 404 | No `:Publication` at this shepard has the requested PID. |

Bare 401 / 403 / 404 (no problem body) on the publish endpoint mean
authentication / permission failure / unknown appId, identical to
the rest of shepard's `/v2/` shelf.

## Migrations

| Version | What it adds | Idempotent? |
|---|---|---|
| `V29__Add_appId_constraint_Publication.cypher` | V11-shape uniqueness constraint on the new `:Publication` label. | Yes — `CREATE CONSTRAINT IF NOT EXISTS`. |
| `V31__Backfill_Publication_versionNumber.cypher` | KIP1h — stamps `versionNumber=1` on every pre-KIP1h `:Publication` row missing the property. | Yes — `WHERE p.versionNumber IS NULL` short-circuits re-runs. |

No rollback file ships for V29; an admin who needs to undo runs
`DROP CONSTRAINT appId_unique_Publication IF EXISTS` and (if
desired) `MATCH (p:Publication) DETACH DELETE p`. For V31, the
rollback is `MATCH (p:Publication) REMOVE p.versionNumber;`. Future
KIP slices that mutate the `:Publication` shape ship dedicated
rollback files per the CLAUDE.md "comfort over cleverness" rule.

## Plugin shape

Post-KIP1g + KIP1h (per CLAUDE.md's plugin-first heuristic #2
"external integrations → plugin shape" and heuristic #3 "SPIs in
core, adapters in plugins"), the resolver, the KIP record JSON-LD
shape, and every `Minter` implementation live in separate Maven
modules:

- **`plugins/kip/`** — `shepard-plugin-kip-${revision}.jar`.
  Carries `KipResolverRest` (the `GET /v2/.well-known/kip/{pid-suffix}`
  resource) and `KipRecordIO` (the `kernelInformationProfile`
  envelope JSON-LD shape per the Helmholtz HMC standard:
  `digitalObjectType`, `landingPage`, `dateCreated`, `dateModified`,
  `rightsHolder`, `license`, `digitalObjectVersion`).
- **`plugins/minter-local/`** —
  `shepard-plugin-minter-local-${revision}.jar`. Carries
  `LocalMinter` (the default minter; mints
  `shepard:<instance.id>:<kind>:<appId>:v<n>` PIDs). Operators who
  build their own backend image with `-DnoPlugins` lose the
  default minter and the publish endpoint returns 503 until a
  minter is wired.
- **In core (`backend/`)** — `Minter` SPI, `MintRequest`,
  `MintResult`, `MinterException`, `MinterNotInstalledException`,
  `MinterRegistry`, `:Publication` entity, `PublicationDAO`,
  `PublishableKindRegistry`, the generic
  `POST /v2/{kind}/{appId}/publish` orchestration in `PublishRest`,
  and the generic `PublicationIO` response shape. None of these
  depends on the HMC record format or any specific minter — they
  would work identically against an alternative findability
  protocol or PID provider shipping in a different plugin.

The `/v2/.well-known/kip/{pid-suffix}` endpoint path, the JSON-LD
response body's existing fields, and the RFC 7807 problem responses
are **byte-identical** to pre-KIP1g; KIP1h grew the response
additively with `versionNumber` (`PublicationIO`) and
`digitalObjectVersion` (`KipRecordIO`). Operators see no
wire-shape regression.

To opt out of the local minter (rare — operators who want only the
resolver, or who plan to mint via ePIC/DataCite once those plugins
ship): set `shepard.publish.minter=none` in `application.properties`
and restart. The KIP resolver keeps working against pre-existing
`:Publication` rows; the publish endpoint returns 503.

Distribution follows the ADR-0023 drop-in JAR shape: each plugin
JAR is baked into `/deployments/plugins/` in the published backend
image, and the backend's `with-plugins` Maven profile declares
both as `<dependency>` items so Quarkus's build-time CDI scanner
picks the `@Path` resources + `@ApplicationScoped` beans. See
[plugins.md](/reference/plugins/) for the operator runbook.

## What's next

- **KIP1c (ePIC plugin)** — real Handles, queued.
- **KIP1d (DataCite plugin)** — DOIs, queued.
- **KIP1e (Vue Publish button)** — UI surface + licence picker, **shipped** (see [Publishing from the UI](#publishing-from-the-ui) above).
- **KIP1f (unpublish / retire)** — open question on retire-vs-tombstone semantics; KIP records are append-only by the HMC spec so a hard delete is the wrong shape.
- **KIP1g (resolver as plugin)** — **shipped** (see [Plugin shape](#plugin-shape) above).
- **KIP1h (`LocalMinter` plugin + optional minter + versioned PIDs Phase 1)** — **shipped** (see [Versioned PIDs (Phase 1)](#versioned-pids-phase-1) and [The optional-minter posture](#the-optional-minter-posture) above).
- **ENT1 (versioning Phase 2 — `:EntityVersion` graph)** — queued; introduces first-class version nodes with `previousVersion` / `nextVersion` edges and `m4i:isNewVersionOf` semantics in the KIP record.

See `aidocs/66` (design) and `aidocs/16` KIP1 rows (live status).
