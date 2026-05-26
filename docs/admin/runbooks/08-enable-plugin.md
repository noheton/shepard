---
layout: default
title: "Runbook — Enable or install a Shepard plugin"
description: "Two paths: (A) toggle an already-registered plugin on/off at runtime via the PluginRegistry API; (B) install a new plugin JAR that requires a backend classpath restart. Covers the PluginRuntimeOverride persistence model."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Enable or install a Shepard plugin

> **When to use this runbook**: You need to activate or deactivate a plugin in a
> running Shepard instance, or you need to install a new plugin JAR that is not
> yet on the classpath.

**Two paths**:
- **Path A — runtime toggle**: The plugin JAR is already on the classpath (it shipped
  with the backend image or was previously installed). Toggle it `enabled: true/false`
  via the REST API — no restart required.
- **Path B — new JAR install**: The plugin JAR does not yet exist on the classpath.
  You must copy the JAR into the plugins directory and restart the backend for Quarkus
  CDI to discover it.

---

## Prerequisites

- An instance-admin API key (`${INSTANCE_ADMIN_API_KEY}`).
- `${API_BASE}` — the Shepard API base URL.
- For Path B: the plugin JAR file and SSH access to the nuclide host.
- Compose working directory: `/opt/shepard/infrastructure/`.

---

## Path A — Runtime toggle (JAR already on classpath)

### A1. List all registered plugins

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/plugins" \
  | jq '.[] | {id, name, enabled, state}'
```

Expected: array of plugin descriptors. Note the `id` of the plugin to toggle.
Plugin `state` values include `ACTIVE`, `DISABLED`, `FAILED`.

```bash
export PLUGIN_ID="<plugin-id-from-above>"
```

### A2. Enable the plugin

```bash
# [operator-machine]
curl -fsS -X PATCH \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}' \
  "${API_BASE}/v2/admin/plugins/${PLUGIN_ID}" \
  | jq .
```

Expected: HTTP 200 with updated plugin descriptor showing `"enabled": true`.

The toggle is persisted as a `:PluginRuntimeOverride` node in Neo4j — it survives
backend restarts. Flipping back to the deploy-time default (`null` override) deletes
the override node.

### A3. Verify plugin is active

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/plugins/${PLUGIN_ID}" \
  | jq '{id, name, enabled, state}'
```

Expected: `"state": "ACTIVE"`, `"enabled": true`.

### A4. Smoke-test plugin endpoints

Each plugin registers its own endpoints. Check the plugin's
`plugins/<plugin-id>/docs/reference.md` for the specific paths to test.

For the built-in plugins, representative smoke tests:

```bash
# shepard-plugin-semantics (if enabled)
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/semantic/ontologies" | jq 'length'

# shepard-plugin-unhide (if enabled)
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/unhide/config" | jq .
```

---

## Path B — New JAR install (not yet on classpath)

### B1. Obtain the plugin JAR

The plugin JAR is typically published to GHCR or Maven Central as an artifact.
Download it to a known path:

```bash
# [nuclide]
curl -fL "https://github.com/noheton/shepard/releases/download/<version>/shepard-plugin-<name>-<version>.jar" \
  -o /opt/shepard/plugins/shepard-plugin-<name>-<version>.jar
echo "JAR downloaded: $(ls -lh /opt/shepard/plugins/shepard-plugin-<name>-<version>.jar)"
```

### B2. Verify the JAR digest

```bash
# [nuclide]
sha256sum /opt/shepard/plugins/shepard-plugin-<name>-<version>.jar
```

Compare against the digest published in the GitHub Release or build artifacts.

### B3. Place the JAR in the plugins directory

The backend image reads additional JARs from a directory mounted at
`/opt/quarkus/plugins/` (configured via `QUARKUS_CLASS_LOADING_PARENT_FIRST_ARTIFACTS`
or the compose volume). Confirm the mount point from `docker-compose.override.yml`:

```bash
# [nuclide]
docker inspect infrastructure-backend-1 \
  --format '{{range .Mounts}}{{if eq .Destination "/opt/quarkus/plugins"}}{{.Source}}{{end}}{{end}}'
```

If the mount is not configured, the plugin JAR approach requires a custom image build.
For the standard compose setup, copy the JAR to the plugins directory:

```bash
# [nuclide]
cp /opt/shepard/plugins/shepard-plugin-<name>-<version>.jar \
   /opt/shepard/plugins/active/
```

### B4. Restart the backend

Quarkus CDI discovers plugins at startup — a restart is required for new JARs:

```bash
# [nuclide]
cd /opt/shepard/infrastructure
docker compose restart backend
```

Watch startup logs for plugin registration:

```bash
# [nuclide]
docker compose logs -f --tail 100 backend 2>&1 | grep -i plugin | head -20
```

Expected: `PluginRegistry: registered plugin <name>` or similar.

### B5. Enable the plugin (Path A from here)

After restart, the new plugin will appear in `GET /v2/admin/plugins` with
`"state": "DISABLED"` (deploy-time default). Follow Path A steps A2–A4 to enable it.

---

## Disabling a plugin

```bash
# [operator-machine]
curl -fsS -X PATCH \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}' \
  "${API_BASE}/v2/admin/plugins/${PLUGIN_ID}" \
  | jq .
```

Expected: `"state": "DISABLED"`.

---

## Rollback

**Path A (runtime toggle)**: Re-toggle `enabled: false`. No restart needed.

**Path B (new JAR)**: Remove the JAR from the plugins directory and restart
the backend:

```bash
# [nuclide]
rm /opt/shepard/plugins/active/shepard-plugin-<name>-<version>.jar
cd /opt/shepard/infrastructure
docker compose restart backend
```

---

## End-state verification

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/plugins" \
  | jq '.[] | select(.id == "'${PLUGIN_ID}'") | {id, enabled, state}'
```

Expected: `"enabled": true`, `"state": "ACTIVE"`.

---

## Provenance

- Plugin admin REST: `backend/src/main/java/de/dlr/shepard/v2/admin/plugins/PluginsAdminRest.java`
- Plugin system design: `aidocs/platform/47-dev-experience-and-plugin-system.md`
- Feature toggle A3b: `aidocs/16-dispatcher-backlog.md` row A3b
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
