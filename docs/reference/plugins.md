---
layout: default
title: Plugins (reference)
permalink: /reference/plugins/
---

# shepard plugins reference

shepard's value grows from extension. New payload kinds (HDF5,
video, AAS submodels), new external integrations (Helmholtz
Unhide, git-host adapters, AAS registry sync, Databus catalogue),
and new identifier providers (ePIC, DataCite) all ship as
`shepard-plugin-*` JARs — drop-in modules that a shepard
operator installs without rebuilding the image.

This page is the **operator runbook**: what a `shepard-plugin-*`
JAR is, where to put it, how to enable / disable / inspect it,
and how the lifecycle interacts with the rest of the system.

The companion design document is
[ADR-0023]({{ '/aidocs/63-architecture-decision-log#adr-0023' | relative_url }})
(plugin distribution = drop-in JARs via `ServiceLoader`); the
contributor-facing matrix is in
[aidocs/47]({{ '/aidocs/47-dev-experience-and-plugin-system' | relative_url }}).

## What is a shepard plugin?

A shepard plugin is a JAR file (`shepard-plugin-<id>-<version>.jar`)
carrying:

1. **A `PluginManifest` implementation** —
   `de.dlr.shepard.plugin.PluginManifest` is the SPI interface
   in shepard core. Each plugin ships one class implementing it
   plus a `META-INF/services/de.dlr.shepard.plugin.PluginManifest`
   entry naming the class (standard Java `ServiceLoader` contract).
2. **The plugin's own CDI beans, REST resources, DAOs,
   entities** — every shepard-internal type. These get wired in
   by Quarkus's CDI scanner when the JAR is on the build
   classpath or (in PM1b+) the runtime classloader.

The first shipped plugin is `shepard-plugin-unhide` (UH1a — the
Helmholtz Unhide publish feed). Future plugins follow the same
shape: `shepard-plugin-hdf-hsds`, `shepard-plugin-video`,
`shepard-plugin-aas`, `shepard-plugin-minter-{epic,datacite}`,
`shepard-plugin-spatial-postgis`, etc.

## Install a plugin

The default drop-in directory inside the container is
`/deployments/plugins/`. The host-side mount-point is whatever
you mapped — typically a named volume in your compose recipe.

```bash
# Inside the container, or via a docker volume:
cp shepard-plugin-foo-1.2.3.jar /deployments/plugins/
# then restart the shepard backend so PluginRegistry picks it up
docker compose restart shepard-backend
```

Outside a container, the dev-loop equivalent is dropping the JAR
into `backend/plugins/` and restarting `mvn quarkus:dev`.

The directory is configurable per-install via the
`shepard.plugins.dir` deploy-time property (or
`SHEPARD_PLUGINS_DIR` env var). Default `backend/plugins`
relative to the JVM working directory; the image ships
`/deployments/plugins`.

## Inspect what's installed

CLI:

```bash
shepard-admin plugins list
# Plugin       Version          State      Source
# unhide       1.0.0-SNAPSHOT   ENABLED    build classpath
# hdf-hsds     0.5.0            ENABLED    /deployments/plugins/shepard-plugin-hdf-hsds-0.5.0.jar
```

REST (instance-admin scope):

```bash
curl -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.com/v2/admin/plugins
```

State machine:

| State        | Meaning                                              |
|--------------|------------------------------------------------------|
| `DISCOVERED` | The plugin's manifest was loaded; lifecycle pending. |
| `ENABLED`    | `onRegister(ctx)` returned cleanly; the plugin is live. |
| `DISABLED`   | `shepard.plugins.<id>.enabled=false` skipped the lifecycle. |
| `FAILED`     | `onRegister(ctx)` threw — see backend logs (`plugin.discovery.failed`). |

## Enable / disable a plugin

Two layers:

1. **Per-install default** — `shepard.plugins.<id>.enabled` in
   `application.properties` (or env var
   `SHEPARD_PLUGINS_<ID>_ENABLED`). Defaults to `true` when the
   JAR is present.
2. **Runtime override** — the admin REST or CLI flips the toggle
   without a restart:

```bash
shepard-admin plugins disable unhide
shepard-admin plugins enable unhide
```

The runtime override is **in-memory only**. It survives the
current backend process but not a restart; for persistence,
edit `application.properties` instead. (PM1c will persist
runtime overrides to the database; per-call admin endpoints
already track these via PROV1a's `:Activity` audit trail.)

Toggling a plugin off doesn't unload its classes; the plugin
stays present and its REST resources continue to mount, but
each plugin is expected to gate its own behaviour on
`isEnabled(id)` (or its own `:<Feature>Config.enabled` per the
A3b runtime-knob pattern — UH1a does both: the master
`shepard.plugins.unhide.enabled` toggle AND the runtime
`:UnhideConfig.enabled` knob).

## Uninstall a plugin

```bash
rm /deployments/plugins/shepard-plugin-foo-*.jar
docker compose restart shepard-backend
```

Uninstall is symmetric to install: remove the JAR, restart.
Data the plugin wrote (Neo4j nodes, Postgres tables, MongoDB
collections) is left intact — the operator chooses the
data-retention story, not the plugin lifecycle.

## Build a plugin from source

If you're a plugin developer (or a vendor shipping a custom
plugin), the source-to-JAR loop is two `mvn` invocations.
shepard core uses a `provided`-scope split that keeps the
plugin's transitive dependencies small.

```bash
# 1. Install the shepard backend to your local Maven repo first
#    so the plugin's compile-time references resolve. The
#    -DnoPlugins flag skips the optional with-plugins profile so
#    we don't hit a build-time circular reference.
cd backend
mvn -DnoPlugins -DskipTests -Dquarkus.build.skip=true install

# 2. Build the plugin
cd ../plugins/foo
mvn -DskipTests install

# 3. Optional: rebuild the backend with the plugin on its classpath
#    (Quarkus's CDI scanner picks up @ApplicationScoped beans + @Path
#    resources from the JAR). The default profile (`with-plugins`)
#    pulls every in-tree plugin onto the build classpath.
cd ../../backend
mvn -DskipTests package

# 4. Deploy
cp plugins/foo/target/shepard-plugin-foo-*.jar /path/to/shepard/backend/plugins/
```

CI mirrors this sequence in `.github/workflows/backend-ci.yml`
(unit tests + coverage) and `.github/workflows/build-images.yml`
(GHCR image push). The image-build workflow stages all in-tree
plugin JARs into `backend/plugins-stage/`, which the Dockerfile
copies into `/deployments/plugins/` for operator visibility.

## What plugins can do

In PM1a (the baseline SPI), a plugin can:

- Track itself in the admin surface — `id()`, `version()`,
  `shepardCompatibility()` semver range.
- Run `onRegister(ctx)` / `onUnregister(ctx)` lifecycle hooks.
- Ship `@ApplicationScoped` CDI beans, `@Path` REST resources,
  `@NodeEntity` Neo4j entities, Flyway migrations, anything the
  in-tree shepard code can ship.
- Mount REST endpoints on the `/v2/` surface (NOT the upstream
  compat `/shepard/api/` shelf — that's frozen per CLAUDE.md).

In PM1b (next slice) it'll also gain:

- A proper runtime classloader hookup so plugins that aren't on
  the build classpath can still discover their CDI beans.
- A `JarSignatureVerifier` so JARs from untrusted sources can be
  rejected at discovery time.

Future expansion via the SPI registries in `aidocs/47 §2.5`:
- `PayloadKindRegistry` (HDF5, video, AAS submodels as
  first-class payload kinds).
- `MinterRegistry` (ePIC, DataCite handle/DOI minters).
- `FileStorageRegistry` (GridFS / S3 / Azure Blob / Ceph
  backends).
- `GitAdapterRegistry`, `SemanticConnectorRegistry`, etc.

Each grows incrementally — once an SPI surface exists, the
plugins that bind to it ship alongside the SPI.

## Plugin identity & compatibility

A plugin manifest exposes three identifying strings:

- **`id()`** — short, lowercase, dash-separated: `unhide`,
  `hdf-hsds`, `minter-epic`. This is the identifier used in the
  `shepard.plugins.<id>.enabled` config key, the admin REST /
  CLI surface, and log lines. Two plugins with the same id are
  rejected at discovery (second one marked `FAILED`).
- **`version()`** — semver string. Surfaced in
  `GET /v2/admin/plugins`; not enforced.
- **`shepardCompatibility()`** — semver range the plugin
  claims to be compatible with. PM1a logs it but doesn't
  enforce; PM1b will turn an incompatible plugin's lifecycle
  off + surface a clear admin-visible warning.

## Troubleshooting

**A plugin shows `FAILED` in the admin surface.**
Check the backend startup logs for the `plugin.discovery.failed`
WARN line — it carries the exception class + message that
`onRegister` threw. Fix the underlying defect (often a missing
config key the plugin needs, or a database constraint that
wasn't created); restart shepard.

**A plugin shows `DISCOVERED` but never `ENABLED`.**
The `shepard.plugins.<id>.enabled` toggle is `false` — either
in `application.properties` or via a stale runtime override.
Flip it back on with `shepard-admin plugins enable <id>` or
edit `application.properties` and restart.

**The plugin JAR is in `/deployments/plugins/` but doesn't
appear in `shepard-admin plugins list`.**
The JAR doesn't carry a valid
`META-INF/services/de.dlr.shepard.plugin.PluginManifest` entry,
or the entry names a class that's not on the classpath. Run
`unzip -l <plugin>.jar | grep META-INF/services/` to check the
service file is present; `jar tf <plugin>.jar | grep
PluginManifest` to check the implementation class is bundled.

**Two plugins claim the same id.**
The first one registered wins; the second is marked `FAILED`
with a "duplicate plugin id" message. Rename one of the
plugins (the `id()` method, not the JAR filename — the JAR
filename is irrelevant to discovery).

## See also

- [ADR-0023]({{ '/aidocs/63-architecture-decision-log#adr-0023' | relative_url }}) — plugin distribution decision (drop-in JARs via `ServiceLoader`).
- [aidocs/47]({{ '/aidocs/47-dev-experience-and-plugin-system' | relative_url }}) — the SPI design + future registries.
- [aidocs/68]({{ '/aidocs/68-plugin-vs-core-overview' | relative_url }}) — plugin-vs-core decision matrix.
- [Unhide publish feed]({{ '/reference/unhide-publish/' | relative_url }}) — the first shipped plugin (UH1a).
