---
layout: default
title: Plugin Development (reference)
permalink: /reference/plugin-development/
audience: plugin-author
---

# Writing a shepard plugin

This page is the **developer reference** for writing a new `shepard-plugin-*`
module. It covers the SPI interfaces, the Maven build sequence, how to register
your code, how to add admin-configurable runtime config, and the test and
documentation requirements every plugin must meet before shipping.

For the operator-facing companion (how to install and manage existing plugins),
see [Plugins (reference)]({{ '/reference/plugins/' | relative_url }}).
For the design rationale, see [aidocs/47]({{ '/aidocs/47-dev-experience-and-plugin-system' | relative_url }}).

---

## 1. What is a plugin?

A shepard plugin is a Maven JAR module (`shepard-plugin-<id>-<version>.jar`)
that extends the platform with new payload kinds, storage adapters, identifier
minters, analytics detectors, or external integrations — without touching core.
Plugins ship alongside the backend image (bundled in-tree, or as a drop-in JAR
in `/deployments/plugins/`) and are discovered at startup by two complementary
mechanisms: Java `ServiceLoader` for low-level SPI hooks (OGM entity packages,
manifest registration), and Quarkus's build-time CDI scanner for the plugin's
actual beans (services, REST resources, DAOs).

The **plugin-first heuristic in CLAUDE.md** means new capabilities belong in a
plugin unless they touch the auth perimeter, the identity graph primitives, or
the SPI infrastructure itself. Plugins have their own release cadence, their own
dependency tree, and their own failure modes. They also own their documentation:
every plugin ships `docs/reference.md`, `docs/quickstart.md`, and `docs/install.md`
under `plugins/<id>/docs/` (see [§7](#7-plugin-documentation) below).

---

## 2. Plugin types (SPI interfaces)

A plugin can implement one or more of these interfaces, all in the `backend`
compile-time classpath:

| Role | Interface | Package | Discovery | Concrete example |
|---|---|---|---|---|
| **Manifest** | `PluginManifest` | `de.dlr.shepard.plugin` | `META-INF/services/de.dlr.shepard.plugin.PluginManifest` + CDI | Every plugin |
| **Payload-kind entity hook** | `PayloadKind` | `de.dlr.shepard.spi.payload` | `META-INF/services/de.dlr.shepard.spi.payload.PayloadKind` | `SpatialPayloadKind`, `GitPayloadKind`, `AiPayloadKind` |
| **File storage adapter** | `FileStorage` | `de.dlr.shepard.storage` | CDI `@ApplicationScoped` | `S3FileStorage` in `shepard-plugin-file-s3` |
| **PID minter** | `Minter` | `de.dlr.shepard.publish.minter` | CDI `@ApplicationScoped` | `LocalMinter` in `shepard-plugin-minter-local` |
| **LLM provider** | `LlmProvider` | `de.dlr.shepard.spi.ai` | CDI (optional, guarded by `Instance<LlmProvider>`) | `LlmProviderImpl` in `shepard-plugin-ai` |
| **Timeseries analytics** | `TimeseriesAnalytics` | `de.dlr.shepard.spi.analytics` | CDI | `MADDetector` in `shepard-plugin-analytics-ts` |
| **Git host adapter** | `GitAdapter` | `de.dlr.shepard.context.references.git.adapters` | CDI, picked by `GitAdapterRegistry.supports(host)` | `GitLabRestClient` in `shepard-plugin-git` |

The two mechanisms serve different phases of startup:

- **`META-INF/services/` (ServiceLoader)** runs inside `NeoConnector.connect()`
  and `PluginRegistry.discover()`, which fire before CDI is available.
  Use it for: the manifest declaration, and registering Neo4j-OGM entity packages.
- **CDI `@ApplicationScoped`** is picked up by Quarkus's build-time scanner.
  Use it for: everything that does real work — REST resources, services, DAOs,
  storage adapters, minters, analytics detectors.

Most plugins need both: a `PluginManifest` (ServiceLoader) so the registry
tracks them, and `@ApplicationScoped` beans for their behaviour.

---

## 3. Getting started

### 3a. Create the Maven module

Create `plugins/<id>/pom.xml` following the pattern of an existing plugin (e.g.
`plugins/spatial/pom.xml` or `plugins/minter-local/pom.xml`). Key points:

```xml
<groupId>de.dlr.shepard.plugins</groupId>
<artifactId>shepard-plugin-<id></artifactId>
<version>${revision}</version>
<packaging>jar</packaging>
```

The `backend` artefact is a **`provided`** dependency — your plugin sees core
types at compile time but does not bundle them in its JAR. Add the backend dep
with an exclusion block for every other plugin to break circular resolution:

```xml
<dependency>
  <groupId>de.dlr.shepard</groupId>
  <artifactId>backend</artifactId>
  <version>${revision}</version>
  <scope>provided</scope>
  <exclusions>
    <!-- exclude all other in-tree plugin artefacts to break circular dep -->
    <exclusion>
      <groupId>de.dlr.shepard.plugins</groupId>
      <artifactId>shepard-plugin-unhide</artifactId>
    </exclusion>
    <!-- ... repeat for every other plugin ... -->
  </exclusions>
</dependency>
```

Add Quarkus runtime types (`quarkus-arc`, `quarkus-rest`, `quarkus-rest-jackson`,
etc.) as `provided` — the backend image carries them. Only add `compile`-scoped
deps for libraries the backend image does **not** already provide.

Then declare your plugin in the backend's `with-plugins` Maven profile
(`backend/pom.xml`) so it lands on the build classpath:

```xml
<dependency>
  <groupId>de.dlr.shepard.plugins</groupId>
  <artifactId>shepard-plugin-<id></artifactId>
  <version>${revision}</version>
</dependency>
```

### 3b. Implement the SPI interfaces

At minimum, implement `PluginManifest`. If your plugin introduces Neo4j-OGM
entity classes, also implement `PayloadKind`. For storage adapters, minters,
analytics detectors, or LLM providers, implement the matching interface listed
in §2.

See §4 for a minimal `PluginManifest` example and §5 for `PayloadKind`.

### 3c. Register via META-INF/services

For `PluginManifest`, create:

```
src/main/resources/META-INF/services/de.dlr.shepard.plugin.PluginManifest
```

with a single line naming your implementation class:

```
de.dlr.shepard.plugins.<id>.<Id>PluginManifest
```

If you also implement `PayloadKind`, create a parallel file:

```
src/main/resources/META-INF/services/de.dlr.shepard.spi.payload.PayloadKind
```

with its implementation class.

CDI beans (`@ApplicationScoped`) need no `META-INF/services` entry — Quarkus
discovers them at build time via classpath scanning.

### 3d. The two-pass build sequence

Because the backend's `with-plugins` profile declares your plugin as a
compile-time dependency, and your plugin declares the backend as a
`provided` dependency, you must bootstrap the local Maven repo in two
passes:

```bash
# Pass 1: build backend WITHOUT plugins so your plugin can compile
# against it. Install installs backend.jar into the local repo.
cd /opt/shepard/backend
mvn -DnoPlugins -DskipTests -Dquarkus.build.skip=true install

# Pass 2: build your plugin (backend.jar is now in the local repo)
cd /opt/shepard/plugins/<id>
mvn -DskipTests install

# Pass 3: build the full backend with your plugin on the classpath
cd /opt/shepard/backend
mvn package
```

CI follows the same sequence in `backend-ci.yml` and `build-images.yml`.

### 3e. Compose profiles for sidecars

If your plugin needs an external service (S3 backend, PostGIS, a message
broker), declare it via `PluginManifest.sidecars()` using the `SidecarSpec`
record (PM1f). The `SidecarsAssembler` renders a compose snippet the operator
pastes into their `docker-compose.override.yml`. Do **not** hand-edit the main
`infrastructure/docker-compose.yml` to add your sidecar — operators discover
sidecar requirements through `GET /v2/admin/plugins` and the assembler output.

For plugins that have no external dependencies (most minters, most analytics
detectors), override nothing — the `sidecars()` default returns `List.of()`.

For the canonical worked example of a sidecar declaration, see
`FileS3PluginManifest.sidecars()` in `plugins/file-s3/`.

---

## 4. A minimal PluginManifest

Every plugin ships exactly one class implementing `PluginManifest`. The
interface lives at `de.dlr.shepard.plugin.PluginManifest`. Required methods:
`id()`, `version()`, `shepardCompatibility()`. All other methods have default
implementations that return empty strings, empty Optionals, or no-ops.

```java
package de.dlr.shepard.plugins.example;

import de.dlr.shepard.plugin.PluginContext;
import de.dlr.shepard.plugin.PluginManifest;
import io.quarkus.logging.Log;
import java.net.URI;
import java.util.Optional;

/**
 * ExamplePlugin — illustrative minimal PluginManifest.
 *
 * Discovered by PluginRegistry at startup via
 * META-INF/services/de.dlr.shepard.plugin.PluginManifest.
 */
public final class ExamplePluginManifest implements PluginManifest {

  @Override
  public String id() {
    // Lowercase, hyphen-separated. Must match:
    //   shepard.plugins.<id>.enabled  (config key)
    //   shepard-plugin-<id>           (Maven artifactId)
    return "example";
  }

  @Override
  public String version() {
    return "1.0.0-SNAPSHOT";
  }

  @Override
  public String shepardCompatibility() {
    // Semver range. Not enforced in Phase 1 but surfaced in admin REST.
    return ">=6.0.0-SNAPSHOT,<7";
  }

  @Override
  public String title() {
    return "Example Plugin";
  }

  @Override
  public String description() {
    return "Illustrative plugin — replace with operator-facing description.";
  }

  @Override
  public Optional<URI> repositoryUrl() {
    return Optional.of(URI.create("https://github.com/your-org/shepard-plugin-example"));
  }

  @Override
  public String licence() {
    return "Apache-2.0";
  }

  @Override
  public void onRegister(PluginContext ctx) {
    Log.infof("example: plugin v%s active (id=%s, compat=%s)",
      version(), id(), shepardCompatibility());
  }
}
```

Checklist:
- `id()` matches the `shepard.plugins.<id>.enabled` config key.
- `version()` is a semver string.
- `shepardCompatibility()` targets `>=6.0.0-SNAPSHOT,<7` for this fork.
- `licence()` is the SPDX identifier.
- The `META-INF/services/de.dlr.shepard.plugin.PluginManifest` file names
  this class exactly.
- `onRegister` and `onUnregister` do not throw — a thrown exception flips
  the plugin to `FAILED` state in `PluginRegistry`.

---

## 5. Registering Neo4j-OGM entity packages (PayloadKind)

If your plugin introduces `@NodeEntity` or `@RelationshipEntity` classes,
Neo4j-OGM must know about them before CDI starts. The `PayloadKind` SPI
covers this:

```java
package de.dlr.shepard.plugins.example;

import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

/**
 * Registers the example plugin's Neo4j-OGM entity packages.
 * Plain POJO — NOT a CDI bean. Discovered via ServiceLoader
 * in NeoConnector.connect(), which runs before CDI is up.
 */
public final class ExamplePayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "example";
  }

  @Override
  public List<String> entityPackages() {
    // One entry per package that contains @NodeEntity or
    // @RelationshipEntity classes.
    return List.of("de.dlr.shepard.plugins.example.entities");
  }
}
```

Create the matching ServiceLoader file at:

```
src/main/resources/META-INF/services/de.dlr.shepard.spi.payload.PayloadKind
```

with content:

```
de.dlr.shepard.plugins.example.ExamplePayloadKind
```

Plugins that introduce **no Neo4j-OGM entities** (e.g. a pure Minter plugin
or an analytics-only plugin) do not need `PayloadKind` at all.

---

## 6. Admin-configurable runtime config

Any plugin knob an operator legitimately needs to flip at runtime (endpoint
URL, feature toggle, threshold) must follow the `:*Config` pattern from
CLAUDE.md ("Always: surface operator knobs in the admin config"):

1. **A single `:YourConfig` Neo4j entity** carrying the mutable fields.
   Annotate with `@NodeEntity`, `@HasAppId`, single-instance per the A3b
   pattern. The entity's package must be included in your `PayloadKind.entityPackages()`.

2. **A service** (`YourConfigService`) that reads and writes the entity.

3. **`GET /v2/admin/<id>/config`** returns the current config shape.

4. **`PATCH /v2/admin/<id>/config`** (RFC 7396 merge-patch, `@RolesAllowed("instance-admin")`)
   updates it at runtime.

5. **Deploy-time default** in `application.properties` seeds the singleton
   on first start. The deploy-time value is the fallback; an admin flip via
   PATCH wins at runtime.

For worked examples see:
- `plugins/ai/` — `AiCapabilityConfig` entity + `/v2/admin/ai/capabilities/{capability}`
- `plugins/unhide/` — `UnhideConfig` entity + `/v2/admin/unhide/config`
- `plugins/minter-datacite/` — `DataciteMinterConfig` + `/v2/admin/minters/datacite/...`

Knobs that are legitimately deploy-time-only (cluster topology, pre-startup
ordering invariants, pure buffer sizes) stay in `application.properties` and
do **not** need this pattern.

---

## 7. Testing

### Unit tests

Unit tests live in `plugins/<id>/src/test/java/`. Every plugin ships at minimum
a manifest smoke test verifying:

- `id()` matches the expected string (and therefore the `shepard.plugins.<id>.enabled`
  config key).
- `licence()` is non-blank and an SPDX identifier.
- `repositoryUrl()` is present.
- `version()` is non-blank.
- `shepardCompatibility()` targets the fork-6 range.
- `onRegister()` and `onUnregister()` are no-op safe (must not throw).

```java
class ExamplePluginManifestTest {

  @Test
  void manifest_id_matches_enable_config_key() {
    assertThat(new ExamplePluginManifest().id()).isEqualTo("example");
  }

  @Test
  void manifest_declares_apache_2_0_licence() {
    assertThat(new ExamplePluginManifest().licence()).isEqualTo("Apache-2.0");
  }

  @Test
  void on_register_is_no_op_safe() {
    var ctx = Mockito.mock(PluginContext.class);
    assertThatCode(() -> new ExamplePluginManifest().onRegister(ctx))
      .doesNotThrowAnyException();
  }
}
```

See `plugins/analytics-ts/src/test/java/de/dlr/shepard/plugins/analyticsts/AnalyticsTsPluginManifestTest.java`
and `plugins/importer/src/test/java/de/dlr/shepard/plugins/importer/ImporterPluginManifestTest.java`
for the established pattern.

### Integration tests

Plugins that expose REST endpoints ship `@QuarkusTest` resource tests with
Mockito-mocked dependencies. Because Quarkus CDI scanning is build-time,
your plugin's beans are automatically on the test classpath when the backend
runs `mvn test` with the `with-plugins` profile (which is the default).

See `plugins/video/src/test/java/de/dlr/shepard/v2/video/resources/VideoStreamReferenceV2RestTest.java`
for a REST resource test pattern, and
`plugins/unhide/src/test/java/de/dlr/shepard/plugins/unhide/services/UnhideConfigServiceTest.java`
for a service-layer test with a live Neo4j testcontainer.

### Coverage gate

New plugin code targets ≥ 70% line coverage per the `min-coverage-changed-files`
CI rule (`backend-ci.yml`). The core JaCoCo bundle floor (≥ 60% line / ≥ 60%
branch) applies to the combined build. Do not add new exclusions to
`<excludes>` to dodge the gate.

---

## 8. Plugin documentation requirements

Every plugin that ships REST endpoints or config keys must carry three doc files
at `plugins/<id>/docs/`:

| File | Audience | Contents |
|---|---|---|
| `reference.md` | Power user / operator | Every REST endpoint, Neo4j entity, config key, admin CLI command — with worked request/response examples. |
| `quickstart.md` | Researcher / casual user | "How do I do X with this plugin?" — answers in two clicks, no installation knowledge assumed. |
| `install.md` | Operator | Prerequisites, compose-profile changes or sidecar declarations, config keys and defaults, migration steps, healthcheck endpoint, known pitfalls. |

Pure infrastructure plugins (no user-visible payload kind or endpoint) need a
`reference.md` only. Plugins behind a feature flag that is off by default need
an `install.md`; `reference.md` and `quickstart.md` can land in the same PR
as the flag-enable.

Existing examples:
- `plugins/spatial/docs/` — storage-backed payload kind with a PostGIS sidecar.
- `plugins/minter-datacite/docs/` — integration plugin with external credentials.
- `plugins/hdf5/docs/` — payload kind with an HSDS sidecar.

The main `docs/reference/plugins.md` links to each plugin's install page under
the "Bundled plugins" table. Add your entry there in the same PR.

---

## 9. Checklist for a new plugin PR

Before marking a plugin PR ready for review:

- [ ] `plugins/<id>/pom.xml` declares `backend` as `provided` with full
  exclusions.
- [ ] Backend `with-plugins` profile in `backend/pom.xml` declares the new dep.
- [ ] `PluginManifest` implementation present with non-blank `id()`, `version()`,
  `shepardCompatibility()`, `licence()`.
- [ ] `META-INF/services/de.dlr.shepard.plugin.PluginManifest` names the class.
- [ ] If Neo4j-OGM entities present: `PayloadKind` implementation +
  `META-INF/services/de.dlr.shepard.spi.payload.PayloadKind`.
- [ ] All new REST endpoints land under `/v2/` (not `/shepard/api/`).
- [ ] Any runtime-tunable knob uses the `:*Config` + `PATCH /v2/admin/<id>/config`
  pattern.
- [ ] If sidecar needed: `sidecars()` override in manifest (not a hand-edited compose file).
- [ ] Manifest smoke test + any service/REST unit tests at ≥ 70% line coverage.
- [ ] `plugins/<id>/docs/{reference,quickstart,install}.md` present.
- [ ] Bundled-plugin row added to `docs/reference/plugins.md`.
- [ ] `aidocs/34` tracker row (upstream-upgrade impact or "internal, none").
- [ ] `aidocs/44` feature matrix row flipped to matching status.
- [ ] `aidocs/42` vision update if the plugin is user-visible.

---

## See also

- [Plugins (operator reference)]({{ '/reference/plugins/' | relative_url }}) — installation, enable/disable, troubleshooting.
- [Sidecars SPI]({{ '/reference/sidecars/' | relative_url }}) — declaring external service dependencies.
- [aidocs/47]({{ '/aidocs/47-dev-experience-and-plugin-system' | relative_url }}) — design rationale, SPI evolution plan.
- [ADR-0023]({{ '/aidocs/63-architecture-decision-log#adr-0023' | relative_url }}) — plugin distribution decision.
- [Admin-configurable runtime config pattern]({{ '/reference/admin-cli/' | relative_url }}) — `:*Config` entity + PATCH endpoint shape.
