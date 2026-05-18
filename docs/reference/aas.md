# AAS — Asset Administration Shell

shepard exposes an IDTA AAS v3 compliant read surface at `/v2/aas/...`.
Every **Collection** maps to an **AssetAdministrationShell**; every top-level
**DataObject** maps to a **Submodel reference**.

## Endpoints

### Shell listing

```
GET /v2/aas/shells
```

Returns all Shells the authenticated caller may read, one per Collection.
Supports `?cursor=` and `?limit=` for pagination.

**Response shape** (array):
```json
[
  {
    "id": "urn:shepard:collection:{appId}",
    "idShort": "MyCollection",
    "assetInformation": {
      "assetKind": "Instance",
      "globalAssetId": "urn:shepard:asset:{appId}"
    },
    "description": [{ "language": "en", "text": "Human description" }],
    "submodels": []
  }
]
```

### Single Shell

```
GET /v2/aas/shells/{aasId}
```

`{aasId}` may be either:
- A base64url-encoded Shell IRI: `urn:shepard:collection:{appId}` — per IDTA-01002-3-2 §4.3
- A bare Collection `appId`

Returns 404 when the Shell does not exist or the caller lacks read access (404-on-no-read discipline per `aidocs/52 §7`).

**Response**: same `AasShell` shape as the listing but with `submodels` populated.

### Submodel references

```
GET /v2/aas/shells/{aasId}/submodels
```

Returns one `ExternalReference` per top-level DataObject of the Collection:
```json
[
  {
    "type": "ExternalReference",
    "keys": [
      { "type": "Submodel", "value": "urn:shepard:dataobject:{appId}" }
    ]
  }
]
```

### Well-known self-description

```
GET /v2/aas/.well-known/aas-server
```

Unauthenticated capability document. Reports `enabled`, `aasApiProfile`,
`endpoints`, `supportedSubmodelTemplates`, `shellCount`, and
`registryRegistrations`.

## Submodel Templates

shepard ships three bundled IDTA Submodel Templates. Run the import command
once after install (idempotent — safe to repeat):

```bash
shepard-admin aas import-idta-templates
```

Or via the REST API:
```
POST /v2/admin/aas/import-idta-templates
```
_(requires instance-admin role)_

**Response**:
```json
{
  "created": [
    { "appId": "...", "name": "IDTA Digital Nameplate", "version": 1 }
  ],
  "skipped": 0
}
```

### Included templates

| Name | IDTA spec | Semantic ID | Mandatory elements |
|------|-----------|-------------|-------------------|
| IDTA Digital Nameplate | IDTA-02006 v3.0 | `https://admin-shell.io/IDTA-02006/3/0` | ManufacturerName, ManufacturerProductDesignation, YearOfConstruction |
| IDTA Technical Data | IDTA-02003 v2.0 | `https://admin-shell.io/IDTA-02003/2/0` | GeneralInformation, TechnicalProperties |
| IDTA Time Series Data | IDTA-02008 v1.1 | `https://admin-shell.io/IDTA-02008/1/1` | Metadata, Segments |

Templates appear in `GET /v2/templates?templateKind=AAS_SUBMODEL_TEMPLATE` and
in the `/.well-known/aas-server` `supportedSubmodelTemplates` list after import.

## Registry sync

shepard can push Shell descriptors to an external IDTA AAS Registry:

| Config key | Default | Description |
|---|---|---|
| `shepard.aas.enabled` | `false` | Master toggle for AAS capability advertisement |
| `shepard.aas.api-profile` | `Submodel-Repository-Read-3.1` | IDTA API profile advertised in the well-known doc |
| `shepard.aas.registry.url` | — | External IDTA registry URL (optional) |
| `shepard.aas.registry.api-key` | — | API key for the registry (optional) |
| `shepard.aas.base-url` | — | Public base URL of this shepard instance advertised in Shell descriptors |

Registry registrations status:
```
GET /v2/admin/aas/registrations
POST /v2/admin/aas/registrations/sync
```

## IRI conventions

| Entity | IRI |
|--------|-----|
| Shell | `urn:shepard:collection:{appId}` |
| Asset | `urn:shepard:asset:{appId}` |
| Submodel (DataObject) | `urn:shepard:dataobject:{appId}` |
