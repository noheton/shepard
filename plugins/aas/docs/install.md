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
| `shepard.plugins.aas.enabled` | `true` | Runtime toggle. `false` disables all AAS endpoints without removing the JAR. |
| `shepard.aas.registry.url` | (none) | External IDTA AAS Registry base URL. Omit to disable registry sync. |
| `shepard.aas.registry.api-key` | (none) | Bearer token for the registry. Omit for open registries. |
| `shepard.aas.base-url` | (none) | Public URL of this shepard instance, embedded in Shell descriptor endpoints. |

All keys are deploy-time-only. A runtime-mutable `:AasPluginConfig` endpoint
(`/v2/admin/aas/config`) is tracked as a follow-up (`AAS1-plugin-runtime`).

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
