---
title: Install AAS plugin
---

# Installing `shepard-plugin-aas`

The AAS plugin is bundled with the default shepherd backend image (built with
the `with-plugins` Maven profile, which is active by default). For most
operators no installation step is needed.

---

## Default install (bundled image)

The plugin JAR is already in `/deployments/plugins/` in the standard
`shepard-backend-patched` image. On first startup:

1. `PluginRegistry` discovers `shepard-plugin-aas-*.jar` and registers the
   plugin as **ENABLED** (`shepard.plugins.aas.enabled=true` default).
2. `AasRegistryOutboxService` seeds `PENDING` registration rows for all
   Collections if `shepard.aas.registry.url` is set.
3. Verify with `GET /v2/admin/plugins` → look for `"id": "aas"`, `"state": "ENABLED"`.

Or via CLI:
```
shepard-admin plugins list
```

---

## Custom image without the plugin

Operators who build their own backend image with `-DnoPlugins` must manually
add the plugin JAR:

```
cp shepard-plugin-aas-6.0.0-SNAPSHOT.jar /deployments/plugins/
```

Then restart. The backend's Quarkus CDI scanner discovers the plugin's
`@ApplicationScoped` beans on startup (no rebuild required — the `with-plugins`
classpath entries in `application.properties` handle bean indexing for the
standard image; drop-in operators rely on the filesystem scan).

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.aas.enabled` | `true` | Plugin-level toggle. `false` disables all AAS endpoints without removing the JAR. |
| `shepard.aas.enabled` | `false` | Deploy-time seed for the `:AasConfig.enabled` runtime field. Seeded once on first start; subsequent runtime changes use `PATCH /v2/admin/aas/config`. |
| `shepard.aas.registry.url` | (none) | Deploy-time seed for the registry URL. Overridden at runtime via `PATCH /v2/admin/aas/config` with `{"registryUrl": "..."}`. |
| `shepard.aas.registry.api-key` | (none) | Deploy-time seed for the registry Bearer token. Overridden at runtime via `PATCH /v2/admin/aas/config` with `{"registryApiKey": "..."}`. |
| `shepard.aas.base-url` | (none) | Deploy-time seed for the public Shepard base URL. Overridden at runtime via `PATCH /v2/admin/aas/config` with `{"baseUrl": "..."}`. |

**Runtime precedence:** the `:AasConfig` Neo4j singleton (seeded on first
startup from the deploy-time defaults above) wins over deploy-time properties.
Use `GET /v2/admin/aas/config` to read the live runtime value; use
`PATCH /v2/admin/aas/config` to mutate fields without restarting.

---

## Admin runtime config (AAS1l)

The AAS plugin exposes a runtime-mutable config singleton following the
UH1a / N1c2 operator-knob pattern.

### Read the current config

```http
GET /v2/admin/aas/config
Authorization: Bearer <instance-admin token>
```

Example response:

```json
{
  "enabled": true,
  "registryUrl": "https://registry.example.dlr.de",
  "apiKeyPresent": true,
  "baseUrl": "https://shepard.example.dlr.de"
}
```

Note: `apiKeyPresent` is a boolean — the raw registry API key is never
returned. To check or rotate the key, `PATCH` with `{"registryApiKey": "new-key"}`.

### Update config at runtime (RFC 7396 merge-patch)

```http
PATCH /v2/admin/aas/config
Authorization: Bearer <instance-admin token>
Content-Type: application/json

{
  "enabled": true,
  "registryUrl": "https://registry.example.dlr.de",
  "baseUrl": "https://shepard.example.dlr.de"
}
```

All fields are optional (RFC 7396 semantics — absent = leave unchanged).
Setting a string field to `null` clears it. Setting `"registryApiKey": null`
revokes the stored key (open registries need no auth).

### CLI parity

```bash
# Read config
shepard-admin aas config status

# Enable the AAS integration
shepard-admin aas config enable

# Set registry URL
shepard-admin aas config set-registry-url https://registry.example.dlr.de

# Set base URL
shepard-admin aas config set-base-url https://shepard.example.dlr.de

# Set registry API key
shepard-admin aas config set-registry-api-key <token>

# Revoke registry API key
shepard-admin aas config revoke-registry-api-key
```

All CLI commands support `--output={human,json}`, `--url`, and `--api-key`
flags per the L1 baseline CLI pattern.

### `:AasConfig` Neo4j fields

| Field | Type | Description |
|---|---|---|
| `appId` | UUID v7 (unique) | Singleton identifier; constrained by V88 migration. |
| `enabled` | boolean | Master toggle for the AAS integration. |
| `registryUrl` | String (nullable) | IDTA AAS Registry base URL. |
| `registryApiKey` | String (nullable) | Bearer token for the registry (never returned via GET). |
| `baseUrl` | String (nullable) | Public Shepard URL for shell descriptor endpoints. |

---

## Neo4j migration

V46 (`V46__Add_appId_constraint_AasRegistration.cypher`) runs automatically on
backend startup via `MigrationsRunner`. It adds a unique constraint on
`:AasRegistration.appId`. The migration is **idempotent** and safe to re-run.

Note: this migration lives in the backend resources (not the plugin JAR) because
the Docker runner reads migrations from `/deployments/neo4j/migrations/` which
is populated from backend resources only.

---

## Disabling the plugin

```
# In application.properties or environment:
shepard.plugins.aas.enabled=false
```

Or at runtime via the admin REST:
```http
PATCH /v2/admin/plugins/aas
{"enabled": false}
```

AAS endpoints return 404 when the plugin is disabled. The `:AasRegistration`
outbox rows are preserved; registry sync stops.

---

## Known pitfalls

- **Registry connection timeouts**: The AAS registry sync fires at startup via
  a virtual thread. If the registry is slow or down, the startup log shows
  `FAILED` registration rows — the backend still starts. Use
  `POST /v2/admin/aas/registrations/sync` to retry after the registry is back.
- **base-url not set**: Without `shepard.aas.base-url`, Shell descriptors sent
  to the registry have `null` endpoint hrefs. Set the public URL before enabling
  registry sync.

---

## Verify the install

```
GET /v2/aas/.well-known/aas-server
```

Returns `{"aas:enabled": true, ...}` if the plugin is active. No auth required.
