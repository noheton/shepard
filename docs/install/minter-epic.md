# Operator Install Guide: ePIC Handle Service Minter

Plugin: `shepard-plugin-minter-epic`  
Requires: shepard 6.0.0-SNAPSHOT+, ePIC Handle Service API access

## Prerequisites

- Access to a B2HANDLE-compatible ePIC Handle Service REST API.
- An ePIC prefix allocated by your consortium (e.g. `21.T11148` for Helmholtz).
- Credentials (typically `user:password` or an API token).

## Build

The plugin is included in the default `with-plugins` Maven profile.
No extra build step is needed for standard shepard images.

To build standalone:

```
cd backend && mvn -B -DnoPlugins -DskipTests -Dquarkus.build.skip=true install
cd cli && mvn -B -DnoPlugins -DskipTests install
cd plugins/minter-epic && mvn -B install
cd backend && mvn -B package
```

## Configuration

### application.properties defaults (optional)

```properties
# Activate the ePIC minter as the default minter:
shepard.publish.minter=epic

# Install-time defaults for the :EpicMinterConfig singleton:
shepard.minters.epic.enabled=false
shepard.minters.epic.api-base-url=https://handle.argo.grnet.gr/api
shepard.minters.epic.handle-prefix=21.T11148
```

Credentials are **never** set in `application.properties` — use the admin REST endpoint
or CLI to set them at runtime (security posture: avoids gitleaks false-positives and
prevents credentials appearing in container env vars).

### Runtime configuration

```bash
# Set the API URL
shepard-admin minters epic set-api-url https://handle.argo.grnet.gr/api

# Set the handle prefix
shepard-admin minters epic set-prefix 21.T11148

# Set the credential (prompts for input, no shell history)
shepard-admin minters epic set-credential

# Verify connectivity
shepard-admin minters epic test-connection

# Enable
shepard-admin minters epic enable
```

## :EpicMinterConfig fields

| Field            | Default  | Description                                              |
|------------------|----------|----------------------------------------------------------|
| `enabled`        | `false`  | Master toggle; must be `true` for mint to proceed        |
| `apiBaseUrl`     | (empty)  | B2HANDLE REST API base URL                               |
| `handlePrefix`   | (empty)  | ePIC consortium prefix                                   |
| `credentialKey`  | (empty)  | AES-GCM cipher of credential — set via REST/CLI only     |
| `credentialHash` | (empty)  | SHA-256 hex fingerprint — read-only via GET /config      |

## Neo4j migration

V45 (`V45__Add_appId_constraint_EpicMinterConfig.cypher`) adds a unique constraint on
`:EpicMinterConfig.appId`. This runs automatically on backend startup via `MigrationsRunner`.

## Plugin toggle

```properties
# In application.properties or via admin REST:
shepard.plugins.minter-epic.enabled=true
```

Or via admin REST: `PATCH /v2/admin/plugins/minter-epic/enabled`.

## Healthcheck

`POST /v2/admin/minters/epic/test-connection` — returns `reachable=true` when the
configured ePIC API responds with a 2xx-3xx status.

## Rollback

To disable the minter at runtime: `shepard-admin minters epic disable`.

To remove the credential: `shepard-admin minters epic clear-credential`.

The V45 constraint migration is non-destructive and has no rollback requirement.

## Known pitfalls

- If the ePIC API requires HTTPS client certificates, inject them via JVM truststore
  settings (`JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=...`).
- `credentialSet=false` but `enabled=true` causes every mint to fail with
  `publish.minter.failed`; run `set-credential` before enabling.
- If `shepard.instance.id` changes after credentials are set, the AES-GCM decryption
  will fail (key-mismatch). Run `clear-credential` then `set-credential` again.
