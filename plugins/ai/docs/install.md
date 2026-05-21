---
title: Install — AI plugin
weight: 92
---

# Installing shepard-plugin-ai

## Prerequisites

- Shepard ≥ 6.0.0-SNAPSHOT (this fork).
- Neo4j 5.x (already required by Shepard core).
- Java 21 build toolchain (if building from source).

## Deployment

`shepard-plugin-ai` ships as a standard plugin JAR. The default
`docker-compose.yml` already includes it in the backend image when the
`plugins` profile is active.

```yaml
# infrastructure/docker-compose.yml — already present
services:
  backend:
    profiles:
      - plugins
    environment:
      # Deploy-time defaults — override at runtime via PATCH /v2/admin/ai/capabilities/TEXT
      SHEPARD_AI_TEXT_ENDPOINT_URL: ""
      SHEPARD_AI_TEXT_MODEL: ""
      SHEPARD_AI_TEXT_API_KEY: ""
      SHEPARD_AI_TEXT_ENABLED: "false"
```

Set `SHEPARD_AI_TEXT_ENABLED=true` plus your endpoint URL, model, and API key
to activate the TEXT slot from deploy time. Runtime mutations via
`PATCH /v2/admin/ai/capabilities/TEXT` always win over deploy-time env vars.

## Config keys

All keys are optional. The runtime `:AiCapabilityConfig` node wins over
deploy-time env vars.

| Env var | application.properties key | Default | Notes |
|---|---|---|---|
| `SHEPARD_AI_TEXT_ENDPOINT_URL` | `shepard.ai.text.endpoint-url` | `""` | Base URL, no trailing `/`. |
| `SHEPARD_AI_TEXT_MODEL` | `shepard.ai.text.model` | `""` | Model identifier. |
| `SHEPARD_AI_TEXT_API_KEY` | `shepard.ai.text.api-key` | `""` | API key; leave blank for local/keyless endpoints. |
| `SHEPARD_AI_TEXT_ENABLED` | `shepard.ai.text.enabled` | `false` | Master toggle; safe default is off. |
| `SHEPARD_AI_FAST_TEXT_ENDPOINT_URL` | `shepard.ai.fast-text.endpoint-url` | `""` | Same pattern for the FAST_TEXT slot. |
| `SHEPARD_AI_FAST_TEXT_MODEL` | `shepard.ai.fast-text.model` | `""` | |
| `SHEPARD_AI_FAST_TEXT_ENABLED` | `shepard.ai.fast-text.enabled` | `false` | |

Other slots (`IMAGE_GEN`, `VISION`, `EMBEDDING`, `STRUCTURED`) have no deploy-time
defaults in v0 — configure them at runtime via the admin panel.

## Neo4j migration

`V58__AiCapabilityConfig_constraint.cypher` runs automatically on startup:

```cypher
CREATE CONSTRAINT AiCapabilityConfig_appId_unique IF NOT EXISTS
FOR (n:AiCapabilityConfig)
REQUIRE n.appId IS UNIQUE;
```

Idempotent. No rollback file needed — dropping the constraint is safe:
```cypher
DROP CONSTRAINT AiCapabilityConfig_appId_unique IF EXISTS;
```

## Build from source

```bash
# 1. Install backend (makes backend classes available as a local Maven artifact)
cd /opt/shepard/backend
./mvnw -DnoPlugins -DskipTests -Dquarkus.build.skip=true install

# 2. Build the plugin
cd /opt/shepard/plugins/ai
mvn -DskipTests install

# 3. Build backend image (includes plugin on classpath)
cd /opt/shepard/backend
./mvnw package -Dquarkus.container-image.build=true
```

## Healthcheck

`GET /q/health` — the standard Quarkus health endpoint. The plugin does not
register a separate health check in v0; capability availability is surfaced via
`GET /v2/admin/ai/capabilities` (returns `enabled: true/false` per slot).

To verify a slot is connected:
```bash
curl -H "Authorization: Bearer <admin-token>" \
  https://<your-instance>/v2/admin/ai/capabilities/TEXT
```

Expected response: `"enabled": true` with non-empty `endpointUrl` and `model`.

## Known pitfalls

- **`enabled: false` on first start.** All capability slots default to disabled.
  Either set `SHEPARD_AI_TEXT_ENABLED=true` or use the admin panel after first boot.
- **Blank endpoint URL.** `LlmProviderImpl.complete()` throws `LlmException` with a
  message pointing to the `PATCH` endpoint. Check logs for the capability name.
- **API key storage is plain-text in v0.** Encrypt the Neo4j volume at rest.
  Key-at-rest encryption is a v0b concern (tracked in aidocs/86 §3).
- **OTel issue #789.** With Quarkus MCP, downstream spans from within `LlmProviderImpl`
  are not attributed to the MCP tool span. This is a Quarkus upstream issue; no
  workaround in v0.
