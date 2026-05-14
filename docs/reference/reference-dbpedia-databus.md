# DBpedia Databus References

The `shepard-plugin-reference-dbpedia-databus` plugin adds a typed
reference that points at a [DBpedia Databus](https://databus.dbpedia.org)
artifact. When a researcher attaches a Databus reference to a DataObject,
shepard fetches the artifact's JSON-LD metadata and caches the title,
abstract, version, licence, and distribution list in the graph.

---

## Prerequisites

- The plugin JAR is on the classpath (included in the default image build).
- An instance admin must **enable** the integration at runtime.

---

## Admin setup

### 1. Check the current config

```
shepard-admin references dbpedia-databus status
```

or via REST:

```http
GET /v2/admin/references/dbpedia-databus/config
Authorization: X-API-Key <admin-key>
```

### 2. Enable the plugin

```
shepard-admin references dbpedia-databus enable
```

or:

```http
PATCH /v2/admin/references/dbpedia-databus/config
Content-Type: application/json

{ "enabled": true }
```

### 3. Set the default Databus base URL (optional)

The default is `https://databus.dbpedia.org`. For a private instance:

```
shepard-admin references dbpedia-databus set-base-url https://my-databus.example.org
```

or:

```http
PATCH /v2/admin/references/dbpedia-databus/config
Content-Type: application/json

{
  "defaultEndpoint": "https://my-databus.example.org",
  "allowedHosts": ["my-databus.example.org"]
}
```

> **Important:** the `allowedHosts` list is the security allowlist. Only
> artifact URIs whose hostname is in this list are fetched. The default
> contains `databus.dbpedia.org`; add your private host if needed.

### 4. Configure OAuth (optional, for private instances)

If your Databus instance requires OAuth client-credentials auth:

```http
PATCH /v2/admin/references/dbpedia-databus/config
Content-Type: application/json

{
  "authMode": "oauth-client-credentials",
  "oauthTokenUrl": "https://auth.example.org/token",
  "oauthClientId": "shepard-client"
}
```

Then set the client secret (write-once, never returned):

```http
POST /v2/admin/references/dbpedia-databus/credential
Content-Type: application/json

{ "clientSecret": "<your-secret>" }
```

The response shows a fingerprint (first 8 hex chars of SHA-256) so you can
confirm the secret was stored without exposing the plaintext.

To clear a stored secret:

```http
DELETE /v2/admin/references/dbpedia-databus/credential
```

### 5. Test connectivity

```
shepard-admin references dbpedia-databus test-connection
```

or:

```http
POST /v2/admin/references/dbpedia-databus/test-connection
```

Response:

```json
{ "reachable": true, "statusCode": 200, "latencyMs": 42, "reason": null }
```

---

## Researcher workflow

### Attach a Databus reference to a DataObject

```http
POST /v2/data-objects/{dataObjectAppId}/dbpedia-databus-references
Content-Type: application/json

{
  "artifactUri": "https://databus.dbpedia.org/dbpedia/mappings/geo-coordinates-wikidata",
  "apiKey": null
}
```

The optional `apiKey` field is used as-is on the first metadata fetch (for
per-user private Databus access). It is **never persisted**.

Response (201):

```json
{
  "appId": "01HZ...",
  "artifactUri": "https://databus.dbpedia.org/dbpedia/mappings/geo-coordinates-wikidata",
  "cachedTitle": "DBpedia Geo Coordinates — Wikidata",
  "cachedAbstract": "...",
  "cachedVersion": "2024.01.01",
  "cachedLicence": "CC-BY-SA-3.0",
  "cachedModifiedAt": "2024-01-01T00:00:00Z",
  "cacheFetchedAt": "2026-05-14T...",
  "cacheStatus": "fresh"
}
```

### List references

```http
GET /v2/data-objects/{dataObjectAppId}/dbpedia-databus-references
```

### Get one reference

```http
GET /v2/data-objects/{dataObjectAppId}/dbpedia-databus-references/{referenceAppId}
```

### Refresh the metadata preview

```http
GET /v2/data-objects/{dataObjectAppId}/dbpedia-databus-references/{referenceAppId}/preview
```

Returns a `DbpediaDatabusPreview` shape with `available`, `title`, `description`,
`version`, `licence`, `distributions`, `cacheFetchedAt`, `cacheStatus`.

When `available=false`, the `reason` field distinguishes:

| reason | cause |
|---|---|
| `disabled` | Plugin not enabled by admin |
| `host-not-allowed` | Artifact host not in `allowedHosts` |
| `invalid-uri` | URI is blank or not a valid http(s) URL |
| `auth.failed` | OAuth exchange failed or returned 401/403 |
| `fetch-failed` | Network / 5xx error |
| `parse-failed` | JSON-LD parse error |

### Delete a reference

```http
DELETE /v2/data-objects/{dataObjectAppId}/dbpedia-databus-references/{referenceAppId}
```

---

## Configuration reference

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `false` | Master toggle. |
| `defaultEndpoint` | string | `https://databus.dbpedia.org` | Base URL probed by `test-connection`. |
| `allowedHosts` | list | `["databus.dbpedia.org"]` | Hostname allowlist for artifact URIs. |
| `cacheTtlSeconds` | long | `86400` | Preview cache TTL (min 60). |
| `authMode` | string | `none` | `none` or `oauth-client-credentials`. |
| `oauthTokenUrl` | string | `null` | Token endpoint for OAuth CC flow. |
| `oauthClientId` | string | `null` | Client ID for OAuth CC flow. |

Deploy-time install defaults (override before first start):

```properties
shepard.references.dbpedia-databus.enabled=false
shepard.references.dbpedia-databus.default-endpoint=https://databus.dbpedia.org
shepard.references.dbpedia-databus.allowed-hosts=databus.dbpedia.org
shepard.references.dbpedia-databus.cache-ttl=PT24H
shepard.references.dbpedia-databus.auth-mode=none
```

---

## Neo4j migrations

| File | What it does |
|---|---|
| `V37__Add_appId_constraint_DbpediaDatabusReference.cypher` | Uniqueness on `:DbpediaDatabusReference.appId` |
| `V38__Add_appId_constraint_DbpediaDatabusConfig.cypher` | Uniqueness on `:DbpediaDatabusConfig.appId` (singleton) |

Both migrations are idempotent (`IF NOT EXISTS`) and fail-fast (abort startup
on error per the `MigrationsRunner` behaviour).
