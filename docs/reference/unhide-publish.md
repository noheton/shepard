---
layout: default
title: Unhide publish (reference)
permalink: /reference/unhide-publish/
---

# Helmholtz Unhide publish plugin

The **Unhide publish plugin** (`shepard-plugin-unhide`) exposes
your shepard instance's Collections to the
[Helmholtz Knowledge Graph (HKG / Unhide)](https://unhide.helmholtz-metadaten.de/)
via a daily-harvested JSON-LD feed. Unhide is **harvest-pull**:
shepard never runs code on Unhide's side; it just emits a feed at
a stable URL, and Unhide's crawler reads it on its own schedule.

This is the first plugin of the fork's
[plugin-first architecture](https://github.com/noheton/shepard/blob/main/aidocs/47-plugin-architecture.md).

## What gets published

For every non-deleted Collection on this shepard instance, the
feed emits a single JSON-LD entry shaped as
`schema:Dataset` + `m4i:Dataset` (NFDI4Ing's `metadata4ing`
extension of W3C PROV-O). Each entry carries:

| JSON-LD field | Source on the Collection |
|---|---|
| `@id` | `https://<your-shepard>/v2/collections/<appId>` |
| `@type` | `["schema:Dataset", "m4i:Dataset"]` |
| `name` | Collection `name` |
| `description` | Collection `description` |
| `dateCreated` | Collection `createdAt` |
| `dateModified` | Collection `updatedAt` |
| `creator` | `schema:Person` built from User's `displayName` / `firstName lastName` / `username`; `@id` = `https://orcid.org/<ORCID>` when an ORCID is on file |
| `m4i:isAbout` | Placeholder `[]` in Phase 1; populated by UH1b once the PROV1h m4i render endpoint ships |

`license` is currently omitted (the data model doesn't carry a
per-Collection licence yet; UH1d wires it through `:CollectionProperties`).

## Endpoints

### `GET /v2/unhide/feed.jsonld`

The harvester endpoint. Cursor-paginated:

```
GET /v2/unhide/feed.jsonld?page=0&page-size=100
```

- `page` — zero-based page index; defaults to `0`.
- `page-size` — defaults to `100`, capped at `1000`.

The page size is deploy-time-tuned via the
`shepard.unhide.feed.page-size` config key (a buffer-sizing knob,
not runtime-mutable — see the
[CLAUDE.md "Buffer sizes / page sizes" exception](https://github.com/noheton/shepard/blob/main/CLAUDE.md#always-surface-operator-knobs-in-the-admin-config)).

#### Response shape

```json
{
  "@context": [
    "https://schema.org/",
    "https://w3id.org/nfdi4ing/metadata4ing/",
    {
      "shepard": "https://shepard.dlr.de/types/",
      "dcat": "http://www.w3.org/ns/dcat#",
      "m4i": "https://w3id.org/nfdi4ing/metadata4ing/"
    }
  ],
  "@graph": [
    {
      "@id": "https://shepard.example.dlr.de/v2/collections/01HF...",
      "@type": ["schema:Dataset", "m4i:Dataset"],
      "name": "Cyclic-fatigue test campaign 2026-Q1",
      "description": "...",
      "dateCreated": "2026-01-15T10:23:00Z",
      "dateModified": "2026-02-01T12:00:00Z",
      "creator": {
        "@type": "schema:Person",
        "@id": "https://orcid.org/0000-0002-1825-0097",
        "name": "Alice Researcher"
      },
      "m4i:isAbout": []
    }
  ],
  "_meta": {
    "page": 0,
    "pageSize": 100,
    "totalEntries": 42,
    "totalPages": 1,
    "generatedAt": "2026-05-13T05:11:00Z",
    "contactEmail": "ops@example.dlr.de"
  }
}
```

#### Status codes

| Status | When |
|---|---|
| `200 OK` | Feed page returned. |
| `401 Unauthorized` | `feedPublic=false` AND missing / wrong `X-API-KEY` header. RFC 7807 body, type `unhide.harvest-key.absent`. |
| `503 Service Unavailable` | `:UnhideConfig.enabled=false`. RFC 7807 body, type `unhide.feed.disabled`. Operator opts in via `shepard-admin unhide enable`. |

### `GET /v2/admin/unhide/config`

Returns the current `:UnhideConfig` singleton.
**`@RolesAllowed("instance-admin")`-gated.**

```json
{
  "enabled": true,
  "feedPublic": false,
  "contactEmail": "ops@example.dlr.de",
  "harvestApiKeyMintedAt": "2026-05-13T05:00:00Z",
  "harvestApiKeyFingerprint": "01234567"
}
```

The **harvest API key hash is never returned**. The
`harvestApiKeyFingerprint` is the first 8 hex chars of the
SHA-256 — enough to confirm "yes, this is the key I just minted"
without exposing material that would help reverse the plaintext.

### `PATCH /v2/admin/unhide/config`

RFC 7396 merge-patch. **`@RolesAllowed("instance-admin")`-gated.**
Patchable fields:

- `enabled` — `boolean`
- `feedPublic` — `boolean`
- `contactEmail` — `string` (explicit `null` clears the field)

Touching `harvestApiKeyHash` directly returns
**400 RFC 7807 `unhide.config.read-only-field`**. Use the rotate
endpoint instead.

```bash
curl -X PATCH \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled":true,"contactEmail":"ops@example.dlr.de"}' \
  https://shepard.example.dlr.de/v2/admin/unhide/config
```

### `POST /v2/admin/unhide/harvest-key/rotate`

Mints a fresh UUID v4 harvest API key, stores its SHA-256 hash on
the singleton, and **returns the plaintext exactly once**. There is
no second chance — save it now.

**Security details (see the [CLAUDE.md plaintext-handling rule](https://github.com/noheton/shepard/blob/main/CLAUDE.md)):**

- The plaintext is never logged. INFO logs carry only the first-8
  fingerprint.
- The plaintext is never stored. Only the SHA-256 hex lives in Neo4j.
- The plaintext never enters `:Activity`. PROV1a's
  `ProvenanceCaptureFilter` captures request path + method + status
  only, never response bodies.

```json
{
  "harvestApiKey": "11111111-2222-4333-8444-555555555555",
  "fingerprint": "01234567",
  "mintedAt": "2026-05-13T05:00:00Z",
  "warning": "This is the only time this harvest API key is shown. Save it now; if lost, use POST /v2/admin/unhide/harvest-key/rotate to mint a new one."
}
```

### `POST /v2/admin/unhide/harvest-key/revoke`

Clears `:UnhideConfig.harvestApiKeyHash`. When `feedPublic=false`
the feed becomes inaccessible until a fresh key is minted. Returns
the post-revoke config in the same shape as `GET /config`.

`DELETE /v2/admin/unhide/harvest-key` is wired as an equivalent
REST-purist verb for the same operation.

## CLI parity

```text
shepard-admin unhide status
shepard-admin unhide enable
shepard-admin unhide disable
shepard-admin unhide set-feed-public <true|false>
shepard-admin unhide set-contact-email <email>      # empty arg ⇒ clear
shepard-admin unhide rotate-harvest-key
shepard-admin unhide revoke-harvest-key
```

All commands honour `--output={human,json}` plus the L1 baseline
flags (`--url`, `--api-key`, `--verbose`). The
`rotate-harvest-key` command emits the plaintext on `stdout` with
the warning + fingerprint on `stderr` — pipe through a secret
manager:

```bash
shepard-admin unhide rotate-harvest-key \
  | gopass insert -m shepard/unhide-harvest-key
```

## Configuration

Four install-time defaults in `application.properties`. The first
three seed `:UnhideConfig` on first start; once an operator
PATCHes the runtime config the deploy-time value becomes a fallback
only used on a fresh DB.

| Key | Default | Purpose | Runtime-mutable? |
|---|---|---|---|
| `shepard.unhide.enabled` | `false` | Seeds the master toggle | Yes — via `PATCH /v2/admin/unhide/config` or `shepard-admin unhide enable` |
| `shepard.unhide.feed.public` | `false` | Seeds the feed-visibility flag | Yes |
| `shepard.unhide.contact-email` | (empty) | Seeds the contact email | Yes |
| `shepard.unhide.feed.page-size` | `100` | Cursor page size, capped at `1000` | **No** — deploy-time only (buffer-sizing exception) |

## Auth model

The feed endpoint's access predicate is **runtime-mutable** —
which is why `:UnhideConfig` is a singleton with an admin REST
surface, not a deploy-time-only config. Three states:

1. **`enabled=false`** → 503 `unhide.feed.disabled`. No auth attempted.
2. **`enabled=true` AND `feedPublic=true`** → 200, no auth required.
3. **`enabled=true` AND `feedPublic=false`** → 200 only when the
   request carries `X-API-KEY: <plaintext>` whose SHA-256 hex matches
   `:UnhideConfig.harvestApiKeyHash`. Constant-time compare guards
   against timing attacks.

> **Phase 1 simplification.** `aidocs/67 §5.1` mentions a
> private-feed fallback for instance-admin callers (so an admin can
> inspect the private feed without a harvest key). Phase 1 omits
> this — the harvest key is the sole non-public auth path. The
> admin inspection flow is "mint a key, curl with it", which
> matches how `shepard-admin unhide rotate-harvest-key` works. The
> instance-admin fallback can graft on in UH1b if operator feedback
> wants it.

## Registering with Unhide

shepard never runs code on Unhide's infrastructure. To get
harvested:

- **Self-service**: visit
  [`unhide.helmholtz-metadaten.de/dataprovider/register`](https://unhide.helmholtz-metadaten.de/dataprovider/register),
  submit your shepard's feed URL plus a contact, and wait for the
  HMC team to approve.
- **HMC outreach**: HMC's data-provider liaison reaches out to
  Helmholtz centres directly when a public-facing shepard install
  becomes visible.

shepard's job is to emit a stable feed at
`https://<your-shepard>/v2/unhide/feed.jsonld` — discovery is
operator-side.

## Data flow

```
Operator → shepard-admin unhide enable + set-contact-email + rotate-harvest-key
       ↓
shepard exposes /v2/unhide/feed.jsonld
       ↓
Unhide harvester (daily cron, X-API-KEY-authenticated when feedPublic=false)
  → GET /v2/unhide/feed.jsonld?page=…
       ↓
Unhide's inward-mappings extract schema.org + m4i terms
       ↓
Triples land in the Helmholtz KG (Virtuoso + QLever)
       ↓
External researcher → SPARQL on Unhide → finds this Collection
       ↓
Click landing page → arrives at shepard's /v2/collections/{appId}
       ↓
Read-permission check → 200 (if public/auth'd) or 401
```

## Cross-references

- **Casual-task page**:
  [`docs/help/publish-to-helmholtz-unhide.md`](../help/publish-to-helmholtz-unhide.md)
- **Design**:
  [`aidocs/67-unhide-publish-plugin.md`](https://github.com/noheton/shepard/blob/main/aidocs/67-unhide-publish-plugin.md)
- **Plugin distribution**:
  [`aidocs/63` ADR-0023](https://github.com/noheton/shepard/blob/main/aidocs/63-architecture-decision-log.md#adr-0023)
- **PROV1a's role**:
  [`aidocs/55` provenance capture](https://github.com/noheton/shepard/blob/main/aidocs/55-provenance.md)
  — why the harvest-key plaintext never enters `:Activity`.
