---
title: AAS plugin reference
weight: 70
---

# AAS plugin — Asset Administration Shell (IDTA)

`shepard-plugin-aas` ships IDTA AAS v3 compatibility as a drop-in plugin.
It maps **Collections → AAS Shells** and **DataObjects → Submodel references**,
provides a server self-description endpoint, an IDTA Submodel Template import
path, and an outbox-based sync to external AAS registries.

Plugin id: **`aas`** · Licence: Apache-2.0

---

## Endpoints

### AAS Shell listing

```
GET /v2/aas/shells
```

Returns all Shells the authenticated caller may read (one per Collection).
Supports `?cursor=` and `?limit=` for pagination.

**Response shape** (array):
```json
[
  {
    "id": "urn:shepard:collection:coll-abc123",
    "idShort": "MyCollection",
    "assetInformation": {
      "assetKind": "Instance",
      "globalAssetId": "urn:shepard:asset:coll-abc123"
    },
    "description": [{ "language": "en", "text": "My collection description" }],
    "submodels": []
  }
]
```

### Single Shell

```
GET /v2/aas/shells/{aasId}
```

`{aasId}` may be the bare Collection `appId` or the base64url-encoded full
IRI (`urn:shepard:collection:{appId}`). Returns 404 if the Collection is
unknown or the caller lacks read permission.

### Submodel references

```
GET /v2/aas/shells/{aasId}/submodels
```

Returns the top-level DataObjects attached to the Shell as Submodel references.
Each reference uses `type: "ExternalReference"` with a single key
`value: "urn:shepard:dataobject:{dataObjectAppId}"`.

### Server self-description

```
GET /v2/aas/.well-known/aas-server
```

Unauthenticated. Returns a capability document (AAS profile, enabled flag,
shell count, IDTA template names, registry registrations). No per-Shell
identifiers are exposed.

**Example response:**
```json
{
  "aas:enabled": true,
  "aas:apiProfile": "Submodel-Repository-Read-3.1",
  "aas:shellCount": 12,
  "aas:templates": ["IDTA Digital Nameplate", "IDTA Technical Data", "IDTA Time Series Data"],
  "aas:registrations": []
}
```

### IDTA template import (admin)

```
POST /v2/admin/aas/import-idta-templates
```

Requires `instance-admin` role. Idempotently imports the three bundled IDTA
Submodel Templates (Digital Nameplate v3.0, Technical Data v2.0, Time Series
Data v1.1). Skips templates whose body/description/tags are already identical
to the live record; bumps to a new version when the bundled copy differs.

**Response:**
```json
{
  "created": [ /* ShepardTemplate IOs for minted/updated templates */ ],
  "skipped": 2
}
```

### Registry outbox (admin)

```
GET  /v2/admin/aas/registrations
POST /v2/admin/aas/registrations/sync
```

`GET` lists all `:AasRegistration` outbox rows (one per shell/registry-url
pair). `POST /sync` triggers an on-demand retry of all `PENDING` or `FAILED`
rows. Both require `instance-admin` role.

**AasRegistration fields:**

| Field | Description |
|---|---|
| `appId` | Stable outbox row identifier (UUID v7). |
| `shellAppId` | `appId` of the Collection being registered. |
| `registryUrl` | Base URL of the external IDTA AAS Registry. |
| `status` | `PENDING`, `SYNCED`, or `FAILED`. |
| `lastAttemptAt` | Epoch millis of the most recent attempt; `null` until first try. |
| `errorMessage` | HTTP status or exception from the last failed attempt. |
| `createdAt` / `updatedAt` | Epoch millis timestamps. |

---

## Configuration

All keys are deploy-time-only (see install guide for runtime toggle plans).

| Key | Default | Description |
|---|---|---|
| `shepard.aas.registry.url` | (none) | Base URL of the external IDTA AAS Registry to push descriptors to. Omit to disable registry sync. |
| `shepard.aas.registry.api-key` | (none) | Bearer token for the registry (omit for open registries). |
| `shepard.aas.base-url` | (none) | Public base URL of this shepard instance, embedded in registry Shell descriptors. |
| `shepard.plugins.aas.enabled` | `true` | Runtime toggle — set to `false` to disable the plugin without removing its JAR. |

---

## Neo4j entity

`:AasRegistration` — outbox node. Unique constraint on `appId`
(V46 migration, ships in backend resources). One node per
(Collection, registry-url) pair, tracking registration state.

---

## See also

- `docs/help/aas-quickstart.md` — casual-user tasks
- `docs/install/aas-plugin.md` — operator install guide
- `aidocs/integrations/52-aas-backend-integration.md` — design rationale
- `aidocs/plugins/69-aas-plugin-extraction-design.md` — extraction design
