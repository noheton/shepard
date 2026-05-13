---
layout: default
title: Publish and PIDs
permalink: /reference/publish-and-pids/
---

# Publish and PIDs

shepard publishes entities by minting persistent identifiers (PIDs)
through the HMC Kernel Information Profile (KIP) integration
designed in [`aidocs/66`](https://github.com/noheton/shepard/blob/main/aidocs/66-hmc-kip-integration.md).
The KIP1a baseline (this page) ships the `Minter` SPI seam, an
in-core `MockMinter` for casual installs, the publish + resolver
REST endpoints, and the `:Publication` Neo4j entity. Production
adapters (ePIC handles, DataCite DOIs) ship as drop-in plugin JARs
in KIP1c / KIP1d per the [plugin-distribution
ADR-0023](https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md#adr-0023).

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
                                   └─────────────────┘
```

`:Publication` rows are **append-only** by KIP convention. The
most-recent row attached to an entity is the "current" Publication;
a force re-mint attaches a fresh row alongside, never replacing
the old one.

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
- **Forced re-mint.** `?force=true` mints a fresh PID and attaches
  it as an additional `:Publication` row.

Response body (`application/json`):

```json
{
  "appId": "01HF6N3R-pub-row",
  "pid": "mock:shepard:data-objects:01HF...:1747000000000",
  "mintedAt": "2026-05-13T08:11:00Z",
  "minterId": "mock",
  "resolverUrl": "https://<shepard-host>/v2/.well-known/kip/<pid>",
  "publishedBy": "<username>",
  "entityKind": "data-objects",
  "entityAppId": "01HF..."
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
  JAX-RS `{suffix:.+}`. Both Mock-shaped PIDs (colon-separated) and
  Handle/DOI-shaped PIDs (slash-separated) work verbatim — no URL
  encoding required.
- **Visibility.** The KIP record itself is findability metadata —
  type, landing-page URL, timestamps, rights-holder — never entity
  payload. The `landingPage` URL it points at *is* permission-gated;
  an anonymous client gets the KIP record but hits 401 on the
  landing page unless they're authorised. This mirrors the
  `aas-server` `.well-known` posture.

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
    "license": null
  }
}
```

## Configuration

| Key | Default | Notes |
|---|---|---|
| `shepard.publish.minter` | `mock` | Active `Minter` adapter selected at startup. Deploy-time-only — switching the PID provider is a re-bootstrap decision (the "cluster identity / topology" exception in `CLAUDE.md`). |

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

- **`MockMinter`** (`id="mock"`, ships in core) — synthetic Handle-
  shaped PIDs: `mock:shepard:<kind>:<appId>:<epoch-millis>`. Never
  throws. Default for fresh installs and any development environment
  where calling an external service would be wrong.
- **`epic` plugin** (KIP1c, queued) — real Handles via the
  PID Consortium / ePIC handle service. Drops into
  `backend/plugins/` as `shepard-plugin-minter-epic-*.jar`.
- **`datacite` plugin** (KIP1d, queued) — DOIs via the DataCite
  REST API. Same drop-in shape.

Plugins are discovered via `java.util.ServiceLoader<Minter>` per
ADR-0023. The `MinterRegistry` fails startup if the configured
`shepard.publish.minter=<id>` has no matching bean — operators see
the misconfiguration at boot, not mid-request.

## Errors

All non-200 responses ship `application/problem+json` per RFC 7807.

| `type` | Status | When |
|---|---|---|
| `publish.kind.unsupported` | 404 | URL segment isn't in the `PublishableKindRegistry`. |
| `publish.entity.wrong-kind` | 404 | The appId exists but is a different label than the URL segment claims. |
| `publish.minter.failed` | 500 | Active Minter threw `MinterException`. `detail` carries the operator-readable message. |
| `kip.pid.not-found` | 404 | No `:Publication` at this shepard has the requested PID. |

Bare 401 / 403 / 404 (no problem body) on the publish endpoint mean
authentication / permission failure / unknown appId, identical to
the rest of shepard's `/v2/` shelf.

## Migrations

| Version | What it adds | Idempotent? |
|---|---|---|
| `V29__Add_appId_constraint_Publication.cypher` | V11-shape uniqueness constraint on the new `:Publication` label. | Yes — `CREATE CONSTRAINT IF NOT EXISTS`. |

No rollback file ships; an admin who needs to undo runs
`DROP CONSTRAINT appId_unique_Publication IF EXISTS` and (if
desired) `MATCH (p:Publication) DETACH DELETE p`. Future KIP slices
that mutate the `:Publication` shape ship dedicated rollback files
per the CLAUDE.md "comfort over cleverness" rule.

## What's next

- **KIP1c (ePIC plugin)** — real Handles, queued.
- **KIP1d (DataCite plugin)** — DOIs, queued.
- **KIP1e (Vue Publish button)** — UI surface + licence picker, **shipped** (see [Publishing from the UI](#publishing-from-the-ui) above).
- **KIP1f (unpublish / retire)** — open question on retire-vs-tombstone semantics; KIP records are append-only by the HMC spec so a hard delete is the wrong shape.

See `aidocs/66` (design) and `aidocs/16` KIP1 rows (live status).
