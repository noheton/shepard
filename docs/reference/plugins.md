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

## Bundled plugins

The published backend image bakes four plugins into
`/deployments/plugins/` by default. They activate automatically
via the `with-plugins` Maven profile; operators don't need to
copy any JAR by hand.

| Plugin id | Module | Purpose | Source |
|---|---|---|---|
| `unhide` | `plugins/unhide/` (`shepard-plugin-unhide`) | Helmholtz Unhide publish feed — `GET /v2/unhide/feed.jsonld` schema.org + metadata4ing JSON-LD for the HKG / Unhide harvester. Admin-configurable runtime: `:UnhideConfig` singleton + `/v2/admin/unhide/config` + `shepard-admin unhide ...` CLI parity. | UH1a–UH1c |
| `kip` | `plugins/kip/` (`shepard-plugin-kip`) | HMC Kernel Information Profile resolver — `GET /v2/.well-known/kip/{pid-suffix}` returns the public JSON-LD KIP record per `aidocs/66 §3.2`. | KIP1g |
| `minter-local` | `plugins/minter-local/` (`shepard-plugin-minter-local`) | Default `Minter` plugin — mints local-instance versioned PIDs of the form `shepard:<instance.id>:<kind>:<appId>:v<n>`. Replaces the pre-KIP1h in-core `MockMinter`. Optional: set `shepard.publish.minter=none` for resolver-only deployments. See [Publish and PIDs](/reference/publish-and-pids/). | KIP1h |
| `minter-datacite` | `plugins/minter-datacite/` (`shepard-plugin-minter-datacite`) | DataCite Fabrica DOI minter — mints real DOIs against DataCite (test or production). Activated by `shepard.publish.minter=datacite` once credentials are configured. Admin-configurable runtime: `:DataciteMinterConfig` singleton + `/v2/admin/minters/datacite/...` + `shepard-admin minters datacite ...` CLI parity. AES-GCM credential cipher; versioned-PID chain via DataCite `IsNewVersionOf` / `HasVersion`. See [DataCite DOI minter](/reference/minter-datacite/). | KIP1d |

To opt out of a bundled plugin without removing the JAR: set
`shepard.plugins.<id>.enabled=false` in `application.properties`
and restart (or `shepard-admin plugins disable <id>` for an
in-memory override until the next restart).

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

The runtime override is **persisted** to the
`:PluginRuntimeOverride` Neo4j table (since PM1e). Once you've
flipped a plugin, the new state survives a backend restart
without any `application.properties` edit — the registry seeds
its in-memory cache from the table on startup. Flipping back to
the deploy-time default deletes the row, keeping the table
sparse (it only ever carries rows that differ from the install
default).

Toggling a plugin off doesn't unload its classes; the plugin
stays present and its REST resources continue to mount, but
each plugin is expected to gate its own behaviour on
`isEnabled(id)` (or its own `:<Feature>Config.enabled` per the
A3b runtime-knob pattern — UH1a does both: the master
`shepard.plugins.unhide.enabled` toggle AND the runtime
`:UnhideConfig.enabled` knob).

## Admin REST API

PM1b ships the runtime SPI registry's REST surface. Both
endpoints are `@RolesAllowed("instance-admin")`.

### `GET /v2/admin/plugins`

Lists every discovered plugin — including `DISABLED` + `FAILED`
rows — in registry insertion order.

```bash
curl -H "X-API-KEY: $TOKEN" https://shepard.example.com/v2/admin/plugins
```

```json
{
  "plugins": [
    {
      "id": "unhide",
      "version": "1.0.0",
      "shepardCompatibility": ">=5.2.0,<6",
      "state": "ENABLED",
      "enabled": true,
      "sourcePath": "/deployments/plugins/shepard-plugin-unhide-1.0.0.jar",
      "registeredAt": "2026-05-13T05:00:00.000+00:00"
    },
    {
      "id": "hdf-hsds",
      "version": "0.5.0",
      "shepardCompatibility": ">=5.0.0,<6",
      "state": "DISABLED",
      "enabled": false,
      "sourcePath": "/deployments/plugins/shepard-plugin-hdf-hsds-0.5.0.jar",
      "registeredAt": "2026-05-13T05:00:00.041+00:00"
    }
  ]
}
```

The `enabled` field reflects the current effective toggle
(runtime override wins; fall through to
`shepard.plugins.<id>.enabled`). `state` is the lifecycle
outcome at last `onRegister` invocation. `sourcePath` is
`null` for build-classpath plugins (ADR-0024 Phase 1 shape).

### `PATCH /v2/admin/plugins/{id}`

RFC 7396 merge-patch. The only patchable field is `enabled`:

```bash
curl -X PATCH \
  -H "X-API-KEY: $TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled": false}' \
  https://shepard.example.com/v2/admin/plugins/unhide
```

Returns the updated row in the same shape as the list entry.

Error responses use RFC 7807 `application/problem+json`:

- **404 `/problems/plugin.not-found`** — unknown plugin id.
- **400 `/problems/plugin.config.read-only-field`** — caller
  named a field other than `enabled` in the patch body.
- **403** — caller lacks the `instance-admin` role.

The flip is **persisted** synchronously within the request
(PM1e). The backend writes a row to the `:PluginRuntimeOverride`
Neo4j table; the override survives across restart. Resetting a
plugin to its deploy-time default deletes the row instead of
upserting it, keeping the table sparse.

PROV1a's `ProvenanceCaptureFilter` records each PATCH as an
`:Activity` row (`targetKind=PluginEntry`) so the audit trail
can be filtered to "who flipped which plugin when". The
persisted `:PluginRuntimeOverride` row also carries
`updatedBy` for fast lookups without scanning the activity log:

```cypher
MATCH (o:PluginRuntimeOverride)
RETURN o.pluginId, o.enabled, o.updatedAt, o.updatedBy
ORDER BY o.updatedAt DESC
```

## `shepard-admin plugins` subcommand

The CLI mirrors the REST surface 1:1.

```bash
$ shepard-admin plugins list
ID        VERSION  STATE     ENABLED  SOURCE
unhide    1.0.0    ENABLED   true     /deployments/plugins/shepard-plugin-unhide-1.0.0.jar
hdf-hsds  0.5.0    DISABLED  false    /deployments/plugins/shepard-plugin-hdf-hsds-0.5.0.jar
```

```bash
$ shepard-admin plugins list --output=json
{
  "plugins" : [
    { "id" : "unhide", "version" : "1.0.0", "state" : "ENABLED", ... }
  ]
}
```

```bash
$ shepard-admin plugins disable unhide
Plugin 'unhide' disabled — state=ENABLED (override persisted; survives restart)

$ shepard-admin plugins enable unhide
Plugin 'unhide' enabled — state=ENABLED (override persisted; survives restart)
```

Shared flags (per the L1 baseline): `--url <base>`,
`--api-key <key>`, `--output={human,json}`. Default `--url` is
`$SHEPARD_ADMIN_URL` or `http://localhost:8080`; default
`--api-key` is `$SHEPARD_ADMIN_API_KEY`.

Exit codes: `0` on success, `1` on HTTP / auth / IO error
(stderr carries a one-line message; `--verbose` surfaces the
stack), `2` on unexpected runtime exceptions.

## CLI extensibility (third-party `shepard-admin` subcommands)

A plugin JAR can contribute its own `shepard-admin` subcommands
via the **`AdminCliCommandProvider` SPI** (PM1d). The shape mirrors
the backend's `PluginManifest` SPI from PM1a: ServiceLoader-based
discovery, same plugin directory as the backend, same fail-soft
posture per plugin.

### What an operator needs to know

`shepard-admin` walks the same plugin directory the backend
uses — `$SHEPARD_PLUGINS_DIR` (or the `shepard.plugins.dir`
JVM system property), defaulting to `/deployments/plugins` in
the container image and `cli/plugins` for local development.
Each JAR in that directory is opened in a child class loader,
ServiceLoader-scanned for `AdminCliCommandProvider`
implementations, and each provider's `@Command` class is
registered as a top-level subcommand on `shepard-admin`.

```bash
$ ls /deployments/plugins/
shepard-plugin-unhide-1.0.0-SNAPSHOT.jar
shepard-plugin-acme-foo-0.3.0.jar

$ shepard-admin --help
Usage: shepard-admin [-hV] [COMMAND]
Commands:
  features    ...
  health      ...
  migrations  ...
  plugins     ...
  semantic    ...
  unhide      Manage the Helmholtz Unhide publish plugin ...
  acme-foo    ...                                          # contributed by the acme-foo plugin
```

The same JAR usually carries both sides — a `PluginManifest` for
the backend (REST resources, CDI beans, payload-kind factories)
AND an `AdminCliCommandProvider` for the CLI (Picocli
subcommands). Operators install once: `cp …jar
/deployments/plugins/`.

If a plugin's CLI side is broken (e.g. it points at a class with
no public no-arg constructor), the bootstrap logs a one-line
WARN to stderr and skips that subcommand — every other subcommand
still works. The CLI never crashes on a plugin defect.

### What a plugin author needs to do

Three small artefacts inside your plugin module:

1. **A Picocli `@Command` class.** Same shape as
   `UnhideCommand` — a no-op parent runnable that lists nested
   verb commands via `subcommands = {…}`.

   ```java
   package com.example.shepard.foo.cli;

   import picocli.CommandLine.Command;

   @Command(
     name = "foo",
     description = "Manage the acme-foo extension.",
     subcommands = { FooStatusCommand.class, FooRefreshCommand.class }
   )
   public final class FooCommand implements Runnable {
     @Override public void run() { /* picocli prints the usage banner */ }
   }
   ```

   Each verb command extends `de.dlr.shepard.cli.AbstractCommand`
   so it gets the shared HTTP client / config / output-format /
   error-envelope wiring for free.

2. **An `AdminCliCommandProvider` implementation.**

   ```java
   package com.example.shepard.foo.cli;

   import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;

   public final class FooAdminCliCommandProvider implements AdminCliCommandProvider {
     @Override public Class<?> commandClass() { return FooCommand.class; }
   }
   ```

3. **The `META-INF/services/` pointer.**

   ```
   # plugins/foo/src/main/resources/META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider
   com.example.shepard.foo.cli.FooAdminCliCommandProvider
   ```

In your plugin's `pom.xml`, depend on the CLI module with
`<scope>provided</scope>` (same as the backend dependency) so
the CLI's own classes — including Picocli — are not bundled
into the plugin JAR:

```xml
<dependency>
  <groupId>de.dlr.shepard</groupId>
  <artifactId>shepard-admin</artifactId>
  <version>${shepard.version}</version>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>info.picocli</groupId>
  <artifactId>picocli</artifactId>
  <version>4.7.6</version>
  <scope>provided</scope>
</dependency>
```

To reuse the CLI's `StubBackend` / `CliRunner` test fixtures in
your plugin's own subcommand tests, pull the CLI's `tests`
classifier JAR:

```xml
<dependency>
  <groupId>de.dlr.shepard</groupId>
  <artifactId>shepard-admin</artifactId>
  <version>${shepard.version}</version>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```

That's the entire contract. Build the JAR, drop it into
`/deployments/plugins/`, restart shepard, run
`shepard-admin foo --help` — your subcommand is there. The
`unhide` plugin under `plugins/unhide/` is the canonical example;
its `UnhideAdminCliCommandProvider` is exactly the shape above.

### Discovery hardening

- **Path traversal.** Each JAR's real path must lie inside the
  resolved plugin directory's real path. Symlinks pointing
  outside the directory get rejected with a WARN and skipped.
- **Subcommand name collisions.** If a plugin tries to register a
  subcommand named identically to an existing one (a core verb
  or a previously-registered plugin), the bootstrap logs a
  one-line WARN to stderr and skips the second registration —
  the first wins.
- **Broken providers.** A provider that throws from
  `commandClass()`, returns `null`, returns a class without a
  public no-arg constructor, or returns a class without a
  `@Command` annotation gets a WARN and is skipped.
- **No service file.** A JAR that carries only the backend
  `PluginManifest` (no `AdminCliCommandProvider`) is silently
  ignored — not every plugin has CLI verbs, and that's fine.

## Signing + compatibility enforcement

PM1b2 adds two operator gates the registry runs at discovery
time. Both are **default-safe** (the gates match the pre-PM1b2
behaviour so an upgrade is zero-touch); set the keys below to opt
in to stricter enforcement.

### JAR signature verification

`shepard.plugins.signing.required=true` makes the registry refuse
to load any JAR that isn't signed by a trusted publisher. The
default is `false` (the JARs we ship today are unsigned;
operators upgrade in place without breaking the plugin chain).

Five keys drive the gate:

| Key | Default | Meaning |
|---|---|---|
| `shepard.plugins.signing.required` | `false` | When `true`, unsigned / untrusted JARs land in `FAILED` state and don't have their lifecycle hooks invoked. |
| `shepard.plugins.signing.truststore.path` | empty | Filesystem path to a JKS or PKCS12 truststore. Each certificate alias becomes a trust anchor. |
| `shepard.plugins.signing.truststore.password` | empty | Password for the truststore (empty = no password). |
| `shepard.plugins.signing.trust-anchors` | empty | CSV of alternative trust-anchor identifiers (reserved for forward compat with PEM-on-disk anchors; the truststore path takes precedence). |
| `shepard.plugins.compatibility.strict` | `true` | Enforce the plugin's `shepardCompatibility()` semver range against the running shepard version. |

#### Setting up a signed-plugins instance

1. **Mint a publisher cert** (or reuse one your build pipeline
   already has):

   ```bash
   keytool -genkeypair -alias my-publisher \
     -keyalg RSA -keysize 2048 -validity 3650 \
     -dname "CN=My Publisher, O=My Org" \
     -keystore my-keystore.jks \
     -storepass changeit -keypass changeit
   ```

2. **Sign each plugin JAR** before dropping it into
   `/deployments/plugins/`:

   ```bash
   jarsigner -keystore my-keystore.jks \
     -storepass changeit \
     shepard-plugin-foo-1.0.0.jar my-publisher
   ```

3. **Export the publisher cert** and import it into the shepard
   truststore (or use the same keystore — JKS supports both
   key entries and trusted-cert entries):

   ```bash
   keytool -exportcert -alias my-publisher \
     -keystore my-keystore.jks -storepass changeit \
     -file my-publisher.cer
   keytool -importcert -alias my-publisher \
     -keystore /etc/shepard/truststore.jks \
     -storepass changeit -file my-publisher.cer
   ```

4. **Configure shepard** via `application.properties` (or the
   matching environment variables — Quarkus's MicroProfile
   Config picks both up):

   ```properties
   shepard.plugins.signing.required=true
   shepard.plugins.signing.truststore.path=/etc/shepard/truststore.jks
   shepard.plugins.signing.truststore.password=changeit
   ```

5. **Restart shepard**. `GET /v2/admin/plugins` now reports a
   `state=FAILED` row with `failureMessage=plugin.signature.unsigned`
   for any JAR that hasn't been signed by a trusted publisher.

The bundled `unhide`, `kip`, and `minter-local` plugins ship
**unsigned** today. Setting `signing.required=true` will fail
them unless you sign them yourself with `jarsigner` before
shepard starts. (The PM1b3 design — `aidocs/69` — explores how to
move the build-classpath plugins to a signed-by-default release
flow.)

### Compatibility (semver-range) enforcement

`PluginManifest.shepardCompatibility()` returns a semver range
the plugin claims to be compatible with (e.g. `">=5.2.0,<6"`).
PM1b2 enforces the range against the running shepard version
during discovery:

- A plugin whose manifest declares an incompatible range lands
  in `state=FAILED` with
  `failureMessage=plugin.compatibility.failed: requires shepard <range>, running <version>`.
- A plugin whose range string is malformed (a bug in the
  vendor's manifest) lands in `state=FAILED` with
  `failureMessage=plugin.compatibility.unparseable: <details>`.
- An empty / null range admits any version (matches PM1a
  posture — vendors who haven't filled the field in keep
  registering).

Default is `shepard.plugins.compatibility.strict=true`. Set
`false` to override the check (incompatible plugins still
register, with a loud `THIS IS A DANGER ZONE` WARN in the
log). The override is the escape valve for the "we forked
shepard 5.2 to 5.3 and vendor X hasn't refreshed their manifest
yet" case; flip it back to `true` as soon as the vendor publishes
a fix.

### Operator runbook

`shepard-admin plugins list` shows the new states in its `STATE`
column verbatim. To inspect why a plugin failed:

```bash
shepard-admin plugins list --output=json \
  | jq '.plugins[] | select(.state == "FAILED") | {id, failureMessage}'
```

Common outcomes:

| `failureMessage` prefix | Meaning |
|---|---|
| `plugin.signature.unsigned` | `signing.required=true` and the JAR has no signature. Sign it with `jarsigner` or set `signing.required=false`. |
| `plugin.signature.untrusted` | The JAR is signed, but the signer's cert isn't in the truststore. Import the cert via `keytool -importcert`. |
| `plugin.compatibility.failed` | The running shepard's version is outside the plugin's declared range. Upgrade the plugin, or set `compatibility.strict=false`. |
| `plugin.compatibility.unparseable` | The vendor's manifest carries a broken range string. Open an issue against the vendor; meanwhile, `compatibility.strict=false` re-admits the plugin. |
| `plugin.dependency.missing` | The plugin declares a sibling plugin in `dependencies()` that isn't in `/deployments/plugins/`. Install the sibling. |
| `plugin.dependency.version-mismatch` | The sibling is present but a version range isn't satisfied. Upgrade the sibling. |
| `plugin.dependency.cycle` | The dependency graph has a loop. Vendor fix — drop one of the cycle's edges. |

The `DEGRADED` state is a forward placeholder (PM1b2 ships the
state + lifecycle hook ahead of the PM1b3 design at `aidocs/69`
that will use it for runtime-only JAR detection). Today no
production code path puts a plugin into `DEGRADED` — the row
appears only in the unit-test fixture.

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

PM1b adds:

- **Admin REST + CLI parity** —
  `GET / PATCH /v2/admin/plugins` + `shepard-admin plugins
  {list, enable, disable}`. See the dedicated sections above.

Still queued for follow-up:

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
in `application.properties` or via a persisted runtime override.
Flip it back on with `shepard-admin plugins enable <id>` (which
DELETEs the `:PluginRuntimeOverride` row when the deploy-time
default is `true`) or edit `application.properties` and restart.
To inspect what overrides exist:

```cypher
MATCH (o:PluginRuntimeOverride)
RETURN o.pluginId, o.enabled, o.updatedBy, o.updatedAt
```

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
