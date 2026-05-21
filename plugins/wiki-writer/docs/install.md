---
title: Install — Wiki Writer plugin
weight: 97
---

# Installing shepard-plugin-wiki-writer

## Prerequisites

- Shepard ≥ 6.0.0-SNAPSHOT (this fork).
- `shepard-plugin-ai` deployed and configured with the TEXT capability.
  The wiki-writer will start without it but returns 503 on all requests.

## Deployment

`shepard-plugin-wiki-writer` ships in the default backend image alongside the
other bundled plugins. Enable it via environment variable or the admin panel.

```yaml
# docker-compose.override.yml — enable both AI and wiki-writer
services:
  backend:
    environment:
      SHEPARD_PLUGINS_AI_ENABLED: "true"
      SHEPARD_PLUGINS_WIKI_WRITER_ENABLED: "true"
      # AI plugin endpoint config (TEXT slot)
      SHEPARD_AI_TEXT_ENDPOINT_URL: "https://api.openai.com/v1"
      SHEPARD_AI_TEXT_MODEL: "gpt-4o-mini"
      SHEPARD_AI_TEXT_API_KEY: "sk-..."
      SHEPARD_AI_TEXT_ENABLED: "true"
```

## Config keys

The wiki-writer has no plugin-specific config keys in v0. All behaviour is
controlled via the AI plugin's `AiCapabilityConfig` for the TEXT slot.

| Env var | application.properties key | Default | Notes |
|---|---|---|---|
| `SHEPARD_PLUGINS_WIKI_WRITER_ENABLED` | `shepard.plugins.wiki-writer.enabled` | `false` | Master enable toggle. |

## No Neo4j migration

The wiki-writer creates no new Neo4j entities or constraints. It writes
`LabJournalEntry` nodes via the existing `LabJournalEntryService` (already
present in the backend core).

## Build from source

```bash
# 1. Install backend (makes backend classes available as a local Maven artifact)
cd /opt/shepard/backend
./mvnw -DnoPlugins -DskipTests -Dquarkus.build.skip=true install

# 2. Build the AI plugin first (wiki-writer soft-depends on it at runtime)
cd /opt/shepard/plugins/ai
mvn -DskipTests install

# 3. Build the wiki-writer plugin
cd /opt/shepard/plugins/wiki-writer
mvn -DskipTests install

# 4. Build backend image (includes both plugins on classpath)
cd /opt/shepard/backend
./mvnw package -Dquarkus.container-image.build=true
```

## Verify deployment

```bash
# Check that the endpoint is registered (should return 401, not 404)
curl -I https://<your-instance>/v2/collections/x/data-objects/x/wiki-write

# Check that the plugin is enabled (admin only)
curl -H "Authorization: Bearer <admin-token>" \
  https://<your-instance>/v2/admin/plugins/wiki-writer
```

## Known pitfalls

- **503 on all requests.** Means either `shepard-plugin-ai` is not deployed,
  or the TEXT capability is not configured/enabled. Check
  `GET /v2/admin/ai/capabilities/TEXT` for `enabled: true` and non-empty
  `endpointUrl` + `model`.
- **Empty generated entries.** The LLM returned an empty response — check the
  LLM endpoint logs and verify the model supports the chat completions API.
- **Slow responses.** `maxTokens=1024` on a slow local Ollama instance can take
  10–30 seconds. Consider using `maxTokens=512` for quicker drafts or switching
  to a faster remote model.
