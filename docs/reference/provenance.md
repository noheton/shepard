---
layout: default
title: Provenance / activity log (reference)
permalink: /reference/provenance/
audience: user
---
# Provenance reference

shepard captures a W3C **PROV-O** provenance trail for every
mutation and (opt-in) read against entities in its graph. The log
is exposed under `/v2/provenance/*` and rendered through one of
four content types depending on the request's `Accept` header.

> **Casual users** — for the "what's been happening in this
> Collection?" task, see the
> [Monitor Collection activity](../help/monitor-collection-activity.md)
> page in the help track. This page is the wire-shape deep-dive.

## What gets captured

A `:Activity` Neo4j node lands for every authenticated 2xx response
on the mutating verbs (`POST` / `PUT` / `PATCH` / `DELETE`) — plus,
when the operator flips `shepard.provenance.capture-reads=true`,
also on `GET`. Fields:

| Field | Meaning |
|---|---|
| `appId` | UUID-v7 stable identifier |
| `actionKind` | One of `CREATE` / `READ` / `UPDATE` / `DELETE` / `EXECUTE` |
| `targetKind` | OGM label of the touched entity (`Collection`, `DataObject`, …) |
| `targetAppId` | `appId` of the touched entity (or OGM-id-as-string for legacy rows) |
| `agentUsername` | The acting user (always set — every captured row runs in an authenticated context) |
| `summary` | Short human-readable description (e.g. `"POST /v2/dataobjects"`) |
| `startedAtMillis` / `endedAtMillis` | Server-side timestamps in epoch millis |
| `method` / `path` / `status` | HTTP context (debug-only; not part of PROV-O semantics) |
| `originInstance` | Per-deployment stamp from `shepard.instance.id` (used by Edge → Central sync) |

Captures are best-effort observability — they never block the
request and a capture failure never propagates to the user.

## Read endpoints

| Endpoint | Returns |
|---|---|
| `GET /v2/provenance/activities` | Filterable list (by agent / targetKind / targetAppId / time window) |
| `GET /v2/provenance/entity/{appId}` | Activities touching a single entity |
| `GET /v2/provenance/count` | Row count under the same filter set (for dashboard tiles) |
| `GET /v2/provenance/stats?scope=collection&id=...` | Aggregated stats: totals, sparkline buckets, action-kind histogram, cumulative integral |

Permissions: casual users see only their own rows. The
`?scope=collection` stats endpoint is gated on **Read** permission
against the target Collection (admins bypass; missing Collection
→ 404; lacking Read → 403). Instance-admins see everyone's rows
across every endpoint.

## Content types

Four output shapes on the same endpoints, dispatched by the
request's `Accept` header.

### 1. Plain JSON (default)

```
GET /v2/provenance/activities
Accept: application/json
```

Returns a JSON array of `Activity` objects matching the field
table above. This is the shape the per-Collection dashboard
consumes; it's also the right shape for a quick `curl | jq` look.

```json
[
  {
    "appId": "0192a4f3-7d8a-7000-8c4b-9f1e2a3b4c5d",
    "actionKind": "CREATE",
    "targetKind": "Collection",
    "targetAppId": "0192a3...",
    "agentUsername": "alice",
    "summary": "POST /v2/dataobjects",
    "startedAtMillis": 1700000000000,
    "endedAtMillis": 1700000000500
  }
]
```

### 2. W3C PROV-JSON

```
GET /v2/provenance/activities
Accept: application/prov+json
```

The W3C PROV-JSON Submission shape — blocks for `prefix`,
`activity`, `agent`, `entity`, `wasAssociatedWith`, `used`,
`wasGeneratedBy`. Suitable for tools that already speak
PROV-JSON (some RO-Crate generators, OpenLineage adapters).

```json
{
  "prefix": {
    "prov": "http://www.w3.org/ns/prov#",
    "shepard": "https://noheton.github.io/shepard/prov#"
  },
  "activity": {
    "shepard:activity/<appId>": {
      "prov:type": "shepard:CREATE",
      "prov:startTime": "2023-11-14T22:13:20Z"
    }
  },
  "agent": { "shepard:agent/alice": { "prov:type": "prov:Person" } },
  "wasAssociatedWith": {
    "_:wa0": { "prov:activity": "shepard:activity/<appId>", "prov:agent": "shepard:agent/alice" }
  }
}
```

### 3. PROV-O JSON-LD

```
GET /v2/provenance/activities
Accept: application/ld+json
```

W3C PROV-O serialised as JSON-LD. The `@context` is **embedded
inline** — the consuming tool needs no network round-trip to
resolve namespaces. Pipes directly into Apache Jena, RDF4J,
Stardog, or any other JSON-LD-aware loader.

```json
{
  "@context": {
    "prov": "http://www.w3.org/ns/prov#",
    "shepard": "https://noheton.github.io/shepard/prov#",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  },
  "@graph": [
    {
      "@id": "shepard:agent/alice",
      "@type": ["prov:Agent", "prov:Person"],
      "shepard:username": "alice"
    },
    {
      "@id": "shepard:entity/<targetAppId>",
      "@type": ["prov:Entity"],
      "shepard:kind": "Collection"
    },
    {
      "@id": "shepard:activity/<appId>",
      "@type": ["prov:Activity"],
      "prov:startedAtTime": { "@type": "xsd:dateTime", "@value": "2023-11-14T22:13:20Z" },
      "prov:endedAtTime":   { "@type": "xsd:dateTime", "@value": "2023-11-14T22:13:20.500Z" },
      "prov:type": "shepard:CREATE",
      "prov:wasAssociatedWith": { "@id": "shepard:agent/alice" },
      "prov:generated": { "@id": "shepard:entity/<targetAppId>" }
    }
  ]
}
```

Field-level mapping rules:

| shepard `Activity` field | PROV-O term |
|---|---|
| `actionKind` | `prov:type` (as `shepard:<KIND>`) |
| `agentUsername` | `prov:wasAssociatedWith` → `shepard:agent/<username>` |
| `startedAtMillis` / `endedAtMillis` | `prov:startedAtTime` / `prov:endedAtTime` (typed `xsd:dateTime`) |
| `targetAppId` (when `actionKind=READ`) | `prov:used` → `shepard:entity/<appId>` |
| `targetAppId` (other action kinds) | `prov:generated` → `shepard:entity/<appId>` |

### 4. metadata4ing (m4i) JSON-LD

```
GET /v2/provenance/activities
Accept: application/ld+json; profile="https://w3id.org/nfdi4ing/metadata4ing/"
```

(Short form: `profile=metadata4ing`.)

The NFDI4Ing **metadata4ing** flavour — engineering-research
subtypes on top of PROV-O. Activities are **dual-typed** so a
PROV-O-only reader still parses the document. The `m4i` namespace
is added to the `@context` alongside `prov` / `shepard` / `xsd`.

```json
{
  "@context": {
    "prov": "http://www.w3.org/ns/prov#",
    "shepard": "https://noheton.github.io/shepard/prov#",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "m4i": "http://w3id.org/nfdi4ing/metadata4ing#"
  },
  "@graph": [
    {
      "@id": "shepard:agent/alice",
      "@type": ["m4i:Person", "prov:Agent", "prov:Person"],
      "shepard:username": "alice"
    },
    {
      "@id": "shepard:entity/<targetAppId>",
      "@type": ["m4i:InvestigatedObject", "prov:Entity"],
      "shepard:kind": "Collection"
    },
    {
      "@id": "shepard:activity/<appId>",
      "@type": ["m4i:ProcessingStep", "prov:Activity"],
      "prov:startedAtTime": { "@type": "xsd:dateTime", "@value": "2023-11-14T22:13:20Z" },
      "prov:type": "shepard:CREATE",
      "m4i:hasMethod": "shepard:method/CREATE",
      "prov:wasAssociatedWith": { "@id": "shepard:agent/alice" },
      "prov:generated": { "@id": "shepard:entity/<targetAppId>" },
      "m4i:hasOutput":  { "@id": "shepard:entity/<targetAppId>" }
    }
  ]
}
```

Additional m4i field mappings (in addition to all PROV-O fields,
which are kept):

| shepard concept | m4i term |
|---|---|
| `:Activity` | `m4i:ProcessingStep` (subclass of `prov:Activity`) |
| Acting user | `m4i:Person` (subclass of `prov:Agent`) |
| Target entity | `m4i:InvestigatedObject` (subclass of `prov:Entity`) |
| `actionKind` (READ) | `m4i:hasInput` (parallel to `prov:used`) |
| `actionKind` (CREATE/UPDATE/DELETE/EXECUTE) | `m4i:hasOutput` (parallel to `prov:generated`) |
| `actionKind` (pragmatic verb-as-method) | `m4i:hasMethod` (as `shepard:method/<KIND>`) |

The `m4i:hasMethod` mapping is a pragmatic placeholder — the HTTP
verb isn't a real method in the engineering-research sense, but it
makes the document parseable as a typed event by an m4i-aware
consumer. A future slice may wire `m4i:hasMethod` to an actual
template-defined process step (post-T1, see `aidocs/54`).

### Profile negotiation rules

- No `Accept` header, or `Accept: application/json` (or any
  superset that doesn't mention `application/ld+json` /
  `application/prov+json`) → plain JSON.
- `Accept: application/prov+json` → W3C PROV-JSON.
- `Accept: application/ld+json` (no `profile=` parameter) →
  PROV-O JSON-LD.
- `Accept: application/ld+json; profile="https://w3id.org/nfdi4ing/metadata4ing/"`
  (or `; profile=metadata4ing` short form) → m4i JSON-LD.
- `Accept: application/ld+json; profile=<unknown>` → **406 Not
  Acceptable** with an RFC 7807 `application/problem+json` body
  whose `type` is
  `https://noheton.github.io/shepard/errors/provenance.unsupported-profile`
  and whose `detail` cites the supported values. The response is
  always plain `application/json` (not `ld+json`) so a JSON-LD
  parser doesn't try to ingest the error body.

The negotiation is **additive** — every existing client (the
in-app sparkline composable, the casual `curl | jq` user, the
`shepard-py` SDK) sees no wire change. The new media types are
opt-in per request.

## /count under JSON-LD

```
GET /v2/provenance/count
Accept: application/ld+json
```

Returns a thin wrapper carrying the integer as `shepard:numberOfActivities`
typed `xsd:nonNegativeInteger` under the same `@context` family:

```json
{
  "@context": {
    "prov": "http://www.w3.org/ns/prov#",
    "shepard": "https://noheton.github.io/shepard/prov#",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  },
  "shepard:numberOfActivities": {
    "@type": "xsd:nonNegativeInteger",
    "@value": "42"
  }
}
```

The plain JSON variant (no `Accept: ld+json`) keeps returning
`{ "count": 42 }` for backwards compatibility.

## Filtering parameters

All three list endpoints accept the same query parameters:

| Parameter | Meaning |
|---|---|
| `agent` | Restrict to a specific username. Casual users may only pass their own username; admins may pass any. |
| `targetKind` | Restrict to a specific entity kind (e.g. `Collection`, `DataObject`). |
| `targetAppId` | Restrict to a specific target entity. |
| `since` | Inclusive lower bound on `startedAt` (millis since epoch). |
| `until` | Inclusive upper bound on `startedAt` (millis since epoch). |
| `limit` | Max rows. Defaults to 100; capped at 1000. Paginate via narrower time windows. |

## Retention

`:Activity` rows are pruned by a nightly job (`@Scheduled` cron
`0 42 3 * * ?`). The retention window is operator-configurable
via `shepard.provenance.retention-days=730` (two years; negative
value = keep forever). When the dashboard reports "No recorded
activity in the selected window" it may be that older rows were
pruned — the operator can flip the retention to your needs.

## Caveats

- **Read capture is opt-in.** Without
  `shepard.provenance.capture-reads=true`, the log only reflects
  mutations.
- **Request bodies + headers are never echoed.** The on-row
  `summary` is server-generated from the method + target metadata.
- **`:Activity` is the *casual* audit trail.** F3's tamper-evident
  security audit trail (`aidocs/24 §3.7`) carries the
  who-gained-what permission diff that doesn't fit the
  PROV-O-shaped activity log.
- **JSON-LD emission only.** shepard renders JSON-LD; it doesn't
  ship a JSON-LD parser dependency. Round-tripping a JSON-LD body
  back into shepard (e.g. for replay) isn't on the v1 roadmap.

## Design references

- `aidocs/55-provenance-and-activity-overhaul.md` — the PROV1
  design doc (capture filter, dashboard, retention).
- `aidocs/64-provenance-architecture.md` §3.2 — the PROV1h /
  m4i content-negotiation design.
- `aidocs/63-architecture-decision-log.md` ADR-0004 — why
  PROV-O over OpenLineage.
