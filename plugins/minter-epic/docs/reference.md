# ePIC Handle Service Minter — Reference

Plugin id: `minter-epic`  
SPI: `Minter` (KIP1a)  
Admin path: `/v2/admin/minters/epic/`  
CLI command group: `shepard-admin minters epic`

## Overview

`shepard-plugin-minter-epic` mints persistent handles via the ePIC Handle Service
(B2HANDLE-compatible REST API). It implements the in-core `Minter` SPI (KIP1a) and is
activated when an operator sets `shepard.publish.minter=epic`.

Each mint call issues a `PUT` to `<apiBaseUrl>/handles/<prefix>/<uuid>` with a JSON body
containing URL, optional name, and version records (B2HANDLE format). The returned PID is
`https://hdl.handle.net/<prefix>/<uuid>`.

Credentials are stored encrypted (AES-GCM keyed off the instance id) in the
`:EpicMinterConfig` Neo4j singleton; the plaintext is never logged or returned through REST.

## Neo4j entity

`:EpicMinterConfig` — singleton node, one per deployment.

| Field            | Type    | Description                                         |
|------------------|---------|-----------------------------------------------------|
| `appId`          | String  | UUID v7, unique constraint (V45 migration)          |
| `enabled`        | boolean | Master toggle (default `false`)                     |
| `apiBaseUrl`     | String  | ePIC REST API base URL                              |
| `handlePrefix`   | String  | ePIC-allocated prefix (e.g. `21.T11148`)            |
| `credentialKey`  | String  | AES-GCM ciphertext of the credential — never in REST|
| `credentialHash` | String  | SHA-256 hex — fingerprint only in REST              |
| `updatedAt`      | Long    | Epoch millis of last mutation                       |
| `updatedBy`      | String  | Username of last mutating operator                  |

## REST endpoints

All endpoints require the `instance-admin` role.

### `GET /v2/admin/minters/epic/config`

Returns the current `:EpicMinterConfig` singleton. Credential material is masked:
`credentialSet` (boolean) and `credentialFingerprint` (first 8 hex chars of SHA-256)
are surfaced instead of the raw cipher.

**Response 200:**

```json
{
  "enabled": false,
  "apiBaseUrl": "https://handle.argo.grnet.gr/api",
  "handlePrefix": "21.T11148",
  "credentialSet": true,
  "credentialFingerprint": "deadbeef",
  "updatedAt": "2026-05-17T10:00:00.000+00:00",
  "updatedBy": "alice"
}
```

### `PATCH /v2/admin/minters/epic/config`

RFC 7396 merge-patch. Patchable fields: `enabled`, `apiBaseUrl`, `handlePrefix`.
Credential fields are read-only via this path — use `POST .../credential`.

**Request body:**
```json
{"enabled": true, "handlePrefix": "21.T11148"}
```

**Response 200:** same shape as GET.  
**Response 400** (`application/problem+json`): read-only field touched.

### `POST /v2/admin/minters/epic/credential`

Set or rotate the ePIC credential. Body: `{"credential": "<plaintext>"}`.
The plaintext is encrypted with AES-GCM and stored; the response returns only the fingerprint.

**Response 200:**
```json
{"credentialSet": true, "fingerprint": "deadbeef"}
```

**Response 400:** empty / missing credential.

### `DELETE /v2/admin/minters/epic/credential`

Clear the stored credential. Subsequent mint calls will throw `publish.minter.failed`
until a new credential is set.

**Response 200:** same shape as `GET /config` with `credentialSet=false`.

### `POST /v2/admin/minters/epic/test-connection`

Probe the configured ePIC API URL. Reports reachability, status code, and latency.

**Response 200:**
```json
{
  "reachable": true,
  "statusCode": 200,
  "latencyMs": 42,
  "apiBaseUrl": "https://handle.argo.grnet.gr/api",
  "detail": null
}
```

## CLI commands

```
shepard-admin minters epic status          # print current config
shepard-admin minters epic enable          # set enabled=true
shepard-admin minters epic disable         # set enabled=false
shepard-admin minters epic set-api-url <url>    # set apiBaseUrl
shepard-admin minters epic set-prefix <prefix>  # set handlePrefix
shepard-admin minters epic set-credential  # set credential (stdin/tty)
shepard-admin minters epic clear-credential     # clear stored credential
shepard-admin minters epic test-connection      # probe the API
```

All commands accept `--url <backend>`, `--api-key <key>`, and `--output={human,json}`.

## Activation

Set `shepard.publish.minter=epic` in `application.properties` and configure the
credentials before enabling. The minter is disabled by default (`enabled=false`).

## Mint flow

1. Load `:EpicMinterConfig`; throw `MinterException` if disabled or missing required fields.
2. Generate a UUID v4 suffix.
3. Build a B2HANDLE JSON body: `[{"type":"URL","parsed_data":"<locatorUrl>"}, ...]`.
4. `PUT <apiBaseUrl>/handles/<prefix>/<suffix>` with `Authorization: Basic <base64(credential)>`.
5. On 200/201: return `MintResult(pid="https://hdl.handle.net/<prefix>/<suffix>", minterId="epic")`.
6. On 5xx or network error: retry once with 1s backoff.
7. On 4xx: throw `MinterException` with the HTTP status and truncated body.

## Cross-references

- Design: `aidocs/16` KIP1c
- Upgrade tracker: `aidocs/34` KIP1c row
- Feature matrix: `aidocs/44` KIP1c row
- Migration: `backend/src/main/resources/neo4j/migrations/V45__Add_appId_constraint_EpicMinterConfig.cypher`
- Install guide: `docs/install/minter-epic.md`
- Quickstart: `docs/help/minter-epic-quickstart.md`
