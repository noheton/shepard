# Dev Experience + Storage-Backend Plugin System — Design

**Scope.** Two coupled concerns landed in the same design doc because
they pull on the same lever:

1. A **plugin system for storage backends** so a new payload kind
   (HDF5, future tabular store, future "X") drops in without
   touching the backend's core surfaces.
2. **Dev experience** improvements — fixture infra, codegen, error
   messages, dev-mode bootstrap — that the plugin system makes
   meaningful (and that, conversely, the plugin system needs to
   not regress).

**Status.** PL1a + PL1b + PL1c shipped (2026-05-16).
**Snapshot date.** 2026-05-16.
**Originating items.** User request: "improve dev experience; large
ask design a plugin system to add data backends systematically;
migrate current feature flags as plugins." Couples to:
`aidocs/45` (FileStorage interface — narrower, file-only),
`aidocs/35` (HSDS sidecar pattern — first opportunity for the new
SPI), `aidocs/16` row A3/A3c (existing feature-toggle mechanism
this design migrates), `aidocs/40 §3` (sTC's Sources→Bridges→Sinks
shape — informs but is **not** in scope per user clarification).

---

## 1. Why now, what's painful

### 1.0 The casual-user north star (read first)

shepard's audience skews **casual** — researchers who use the tool
once a month between experiments, not data engineers who live in
the API. Every dev-experience improvement and every plugin shape
in this doc is judged against the same question: **does this make
the casual-user path easier?**

Concrete mappings:

- Plugin SPI → fewer "I have to go ask the maintainers to add my
  data type" friction events.
- Snap dashboards (`aidocs/43 §5.8`) → casual users get inline
  charts without writing code.
- BI/dashboarding integrations (§4.8 below) → casual users who
  *do* want a deeper dashboard get Grafana / Superset out of the
  box rather than building bespoke web UIs.
- `make dev` (DX4) → first-time evaluators don't bounce off a
  multi-step setup.
- Codegen archetype (DX3) → power users who *do* extend get a
  one-line scaffold instead of a 12-file PR-archeology session.

If a proposed change in this doc doesn't trace back to "smoother
for casual users" or "faster for power users," it doesn't ship.

### 1.1 Today's reality for "add a new payload kind"

Compare what L2a touched (one mixin, one DAO seam) with what
shipping HDF5 (`aidocs/35` A5 series) actually requires:

- New `HdfContainer` + `HdfReference` Neo4j entities + `HasAppId`
  mixin instances.
- New IO classes (`HdfContainerIO`, `HdfReferenceIO`).
- New DAOs (`HdfContainerDAO`, `HdfReferenceDAO`).
- New services (`HdfContainerService`, `HdfReferenceService`,
  the `HsdsClient` to talk to the sidecar).
- New JAX-RS resources (`/v2/hdf-containers/...`,
  `/v2/hdf-references/...`).
- New OpenAPI-generated client classes downstream
  (`clients/python`, `clients/typescript`, `clients/java`).
- New permission-graph wiring (HDF references fall under the same
  ACL model).
- New search-projection rules.
- New RO-Crate exporter contribution.
- New migration (`V13__Add_appId_constraint_HdfContainer_HdfReference.cypher`).
- New tracker rows (`aidocs/16`, `aidocs/34`, `aidocs/44`).
- New tests across all of the above.

Some of this is irreducible (the data **is** new); much of it is
**boilerplate that follows an established pattern** every existing
payload kind already conforms to. **The pattern is not
abstracted** — every kind ships hand-rolled DAOs / services /
resources, with no shared seam beyond `BasicReference`.

The cost compounds when an external author wants to add a payload
kind. Today: fork shepard, modify ~12 files across the repo, get
the change accepted by maintainers. With a plugin SPI: write one
JAR, drop it in, restart.

### 1.2 Today's reality for dev iteration

Concrete pain points (from `aidocs/16` work, agent transcripts,
and the pre-existing baseline failure trail):

| Pain | What happens | What's missing |
|---|---|---|
| **Quarkus Dev Services patchwork** | Postgres/Mongo Dev Services auto-provision; Neo4j and InfluxDB don't (no first-party Quarkus extension). The `*QuarkusTest` classes hardcode `127.0.0.1:5433` and fail without manual `docker compose -f infrastructure-local/...` setup. | A unified test-resource that brings up every backend shepard needs, including Neo4j + Influx, via testcontainers. |
| **Test-fixture sprawl** | Each `*ServiceTest` rebuilds the same mock graph — Mockito boilerplate or hand-rolled fixtures. | A shared `ShepardTestFixtures` helper exposing typical Collection / DataObject / Reference graphs as one-line factories. |
| **Codegen for new payload kinds** | None today — every kind is hand-implemented. | A `mvn shepard:scaffold-payload-kind --name=foo` archetype that drops the 12 files in §1.1 from a template (post-plugin SPI). |
| **Error-message diagnosability** | Errors return stack traces or terse strings (`aidocs/07` H4 — RFC 7807 missing). | Already designed; gating on H4 + P4. |
| **Slow first-run on a fresh checkout** | `mvn verify` first time downloads Maven cache (acceptable) + tries to talk to non-existent Postgres + fails. | One-line "make me a working dev env" command — `make dev` or `shepard-admin init --profile=dev`. |
| **"Where do I add my code?" navigation** | The 12-files-per-payload-kind sprawl makes the answer hard for newcomers. | The plugin SPI itself, plus `aidocs/47` §2 documenting where each artifact lives. |
| **Feature toggles live in three places** | `application.properties` (build), `@ConditionalOnFeature` (runtime), `infrastructure/docker-compose.yml` profiles (compose). | Single source of truth: a feature is a plugin; enabling = JAR-on-classpath + config flip. |

These pain points cluster around **"adding new things"** — the
plugin SPI is the structural answer.

---

## 2. Storage-backend plugin SPI

### 2.1 What a plugin is

A **storage-backend plugin** is a Quarkus extension (or a plain
Maven JAR for the lighter shape) that contributes:

- A new **payload kind** (entity + reference type).
- Its **persistence backend** (the actual storage — Mongo / S3 /
  TimescaleDB / PostGIS / HSDS / new).
- Its **REST surface** under `/v2/<kind-name>-references/...`.
- Its **search-projection** contribution.
- Its **RO-Crate exporter** contribution.
- Its **migration** scripts (Neo4j + relational, if any).
- Its **OpenAPI** schema (auto-emitted via JAX-RS scanning).
- Optionally a **client-side stub** for the generated clients
  (`clients/python` etc.).

A plugin **is not** a way to change the auth model, the search-API
shape, or the entity-graph fundamentals — those stay in core. The
SPI is deliberately **payload-kind shaped**, not "extend anything."

### 2.2 The SPI

Single Java interface set under
`backend/src/main/java/de/dlr/shepard/spi/payload/`:

```java
package de.dlr.shepard.spi.payload;

public interface PayloadKind {
  /** Kind discriminator: "file" / "structured" / "timeseries" /
   *  "spatial" / "hdf" / "table" / etc. Used in URL paths and
   *  in the SnapshotEntry / search projection. Must be
   *  [a-z][a-z0-9-]*. */
  String name();

  /** Is this kind enabled at runtime? Plugins may be on the
   *  classpath but disabled via config (`shepard.payload.<name>.enabled`).
   *  Mirrors aidocs/16 A3 ConditionalOnFeature pattern. */
  boolean enabled();

  /** Reference entity type. Must extend BasicReference and
   *  carry HasAppId from L2a. */
  Class<? extends BasicReference> referenceClass();

  /** Container entity type. May be Void if the kind doesn't have
   *  containers (rare; today only timeseries-style kinds have them
   *  decoupled from the reference). */
  Class<?> containerClass();

  /** Backend handle — the actual storage. Each plugin returns its
   *  own implementation. */
  PayloadStorage storage();

  /** Search projection — contributes per-kind WHERE clauses to the
   *  unified search builder. Optional; default no-op. */
  default void contributeToSearch(SearchContext ctx) {}

  /** RO-Crate emitter — adds the kind's manifest entries during
   *  export. Optional; default no-op (the kind ships as opaque file
   *  references). */
  default void contributeToCrate(CrateBuilder builder, BasicReference ref) {}

  /** Migration descriptor — Neo4j Cypher + optional SQL paths the
   *  plugin ships. The MigrationsRunner picks them up via the same
   *  mechanism as core migrations. */
  default List<MigrationDescriptor> migrations() { return List.of(); }
}
```

Plus a smaller `PayloadStorage` interface for the actual byte / row /
document handling — generalises `aidocs/45 FileStorage`:

```java
public interface PayloadStorage {
  /** Write a payload; return a stable handle the plugin understands.
   *  The handle is opaque to core. */
  PayloadHandle write(String containerOid, PayloadWriteRequest req);

  /** Read by handle. Streaming; core never buffers the full payload. */
  InputStream read(String containerOid, PayloadHandle h);

  /** Presigned URL if the backend supports direct-from-storage reads
   *  (S3, HSDS). Empty Optional means "core proxies the bytes." */
  Optional<URL> presignedRead(String containerOid, PayloadHandle h,
                              Duration ttl);

  /** Same for upload. */
  Optional<URL> presignedWrite(String containerOid, String name,
                                Duration ttl);

  /** Lifecycle. */
  String createContainer();
  void deleteContainer(String oid);
  void delete(String containerOid, PayloadHandle h);

  /** Health. Per-backend up/down + per-backend version. Surfaces
   *  through the existing /healthz once A1b (shipped) wires it. */
  HealthStatus health();
}
```

### 2.3 Discovery

A plugin registers via CDI:

```java
@ApplicationScoped
public class HdfPayloadKind implements PayloadKind { /* ... */ }
```

Quarkus's CDI scanner finds all beans implementing `PayloadKind` at
build time. A central `PayloadKindRegistry` injects `Instance<PayloadKind>`,
filters to those with `enabled() == true`, and exposes per-name
lookup.

**Build-time vs runtime.** Plugins ship as Quarkus extensions or
plain JARs on the classpath; presence is a build-time decision.
Whether an enabled-classpath plugin is *active* is a runtime
decision via `shepard.payload.<name>.enabled` (default `true`
when the plugin is on the classpath, but operator-overridable).
This matches the A3 (`@ConditionalOnFeature`) pattern shipped on
this fork.

The build-time-vs-runtime split is deliberate: full-runtime plugin
loading (drop a JAR into a `plugins/` directory of a running
shepard, no rebuild) is **out of scope** — Quarkus's build-time
augmentation philosophy makes it impractical, and the operator
benefit doesn't justify the engineering cost (rebuild + restart is
fine for a research-data platform).

### 2.4 What stays in core (the closed surface)

Plugins **cannot**:

- Change the auth model (`PermissionsService`, JWT/API-key path).
- Modify entity-graph traversal rules (predecessors, parent/child).
- Replace the search-API contract (only contribute *to* it).
- Mutate other plugins' data.
- Override the RO-Crate manifest schema (only contribute manifest
  entries).
- Add new top-level URL paths outside `/v2/<kind-name>-references/`
  and `/v2/<kind-name>-containers/`.

Each constraint is a **safety rail**; without them, plugins become
"shepard 2.0" forks-in-disguise.

### 2.5 Where the boilerplate goes

Once the SPI lands, **scaffolding generates the kind's 12 files**:

```
mvn de.dlr.shepard:shepard-archetype:generate \
    -DgroupId=de.dlr.example \
    -DartifactId=shepard-plugin-myformat \
    -DkindName=myformat
```

Generates a complete plugin Maven module with:

- `MyformatPayloadKind.java` (CDI bean, the SPI implementation)
- `MyformatContainer.java` + `MyformatReference.java` Neo4j entities
- `MyformatContainerIO.java` + `MyformatReferenceIO.java`
- `MyformatStorage.java` (the backend stub — author fills in)
- `MyformatResource.java` JAX-RS at `/v2/myformat-references/{appId}/...`
- `V20__Add_appId_constraint_Myformat.cypher`
- `MyformatPayloadKindTest.java` (test fixture skeleton)
- `pom.xml` declaring the dependency on `shepard-spi`

**That's the dev-experience leverage.** Adding a new payload kind
goes from "rummage through 12 places" to "fill in `MyformatStorage`."

---

## 3. Migration of existing feature flags as plugins

Today's storage-bound feature toggles map to plugins:

| Today's toggle | Plugin shape after migration |
|---|---|
| `shepard.spatial-data.enabled` (A3c) | `shepard-plugin-spatial` — plugin with `name() = "spatial"`, PostGIS as backend (shipped 2026-05-16). The compose `spatial` profile remains for the PostGIS sidecar; the Java code lives in the plugin module. |
| (forthcoming) `shepard.hdf.enabled` per `aidocs/35` | `shepard-plugin-hdf-hsds` — plugin with HSDS sidecar; ships the auth-bridge from `aidocs/35 §5`. |
| (forthcoming) `shepard.files.storage` per `aidocs/45` | **Two file plugins co-existing as first-class supported backends** — `shepard-plugin-file-gridfs` (default) + `shepard-plugin-file-s3` — selected via `shepard.payload.file.backend` runtime config. Per user direction (2026-05-12): GridFS is **not deprecated** — both plugins are supported indefinitely; the operator picks per-install based on workload size + presigned-URL needs (see `aidocs/45 §3.2`). The `PayloadKind` registry permits one-active-per-name; the two file plugins share the kind name `"file"` but differ in backend. |
| `quarkus.versioning.feature.enabled` (existing) | **Stays a non-plugin toggle.** Versioning is entity-graph behaviour, not a payload backend. Out of scope for this SPI. |
| `shepard.migrations.mode-enabled` (existing) | **Stays a non-plugin toggle.** Same reason. |

**Core payload kinds (file / structured / timeseries / spatial) all
become plugins** in the long run, but the migration is **gradual**:

1. Land the SPI alongside today's hand-rolled kinds (no behaviour
   change).
2. Refactor one kind at a time to conform to the SPI — start with
   `spatial` (already feature-toggled, smallest surface) as the
   pilot.
3. Once one kind ships as a plugin, the pattern is set; the other
   three follow.
4. Three new kinds land **as plugins from day 1**: HDF5/HSDS (A5),
   Git (G1, `aidocs/38`), Templates' future per-kind extensions.

**Backwards compatibility.** The `/shepard/api/...` paths on each
kind are byte-frozen per `CLAUDE.md`. The plugin migration is
internal — no client-visible change.

---

## 4. Other dev-experience improvements

These ride alongside the plugin work; some unblock the SPI, some
are independent.

### 4.1 Unified test-resource (DX1)

A single `@WithTestResource(ShepardTestStack.class)` annotation that
spins up every backend a `@QuarkusTest` needs (Postgres + Mongo +
Neo4j + Influx + Mock OIDC) via testcontainers. Replaces the
fragile per-test setup that today fails when run without the
`infrastructure-local` compose stack.

Resolves the pre-existing baseline failure (the
`DataObjectSearchServiceQuarkusTest` that ate a Postgres-not-up
error in the L2b/C5 agent runs).

### 4.2 Shared fixture helpers (DX2)

`ShepardTestFixtures.fired Run()`, `ShepardTestFixtures.collection()`,
etc. — typed builders that produce graphs matching the LUMEN
showcase shape. Closes the Mockito-boilerplate sprawl in `*ServiceTest`.

### 4.3 Codegen archetype (DX3)

The `mvn shepard:scaffold-payload-kind` archetype from §2.5. Lands
with the plugin SPI.

### 4.4 Single-command dev bootstrap (DX4)

Wraps `shepard-admin init` (`aidocs/22 §4.11`) + `docker compose up
-d` + a smoke-test into `make dev`. Idempotent. Makes
"clone-and-run" a one-line story.

### 4.5 Better Quarkus dev-mode story (DX5)

- Hot-reload for the frontend (already works) **plus** the OpenAPI
  spec — today regenerating clients requires a rebuild + republish.
  Auto-refresh `clients/<lang>` on backend signature change.
- Automatic Neo4j Dev Service via the third-party
  `quarkus-neo4j-devservices` extension; same shape as
  `quarkus-jdbc-postgresql`'s Dev Service.

### 4.6 RFC 7807 errors everywhere (DX6)

Already designed (`aidocs/16` H4); reiterate as part of dev-ex
because every "what does this 500 mean?" debugging session is a
DX paper-cut.

### 4.7 Feature-toggle inventory + introspection (DX7)

A new `GET /v2/admin/features` endpoint + matching `shepard-admin
features list` (`aidocs/22 §4.6`) that returns **every** feature
toggle and its source (build-time / runtime config / plugin
classpath presence). Today's three-places-to-look becomes one
read.

### 4.8 BI / dashboarding integrations (DX8)

The casual-user path to "look at my data with charts" is the snap
dashboards feature (`aidocs/43 §5.8` — chat-driven, in-shepard,
LLM-assisted). For users who want **a richer BI experience** —
multiple linked panes, time-range pickers, alerting, public
sharing — shepard should integrate cleanly with established BI
tools rather than reinvent them. Three integration shapes,
ranked by leverage:

| Tool | Fit | Integration shape |
|---|---|---|
| **Grafana** | Best for **timeseries** (the canonical Grafana use case). Operators already know it; the existing `monitoring` profile in `infrastructure/docker-compose.yml` already ships it for prom/jvm dashboards. | Ship a **shepard data source plugin** for Grafana that exposes shepard's timeseries via the existing `/timeseriesContainers/.../payload` endpoint (or `/v2/...` post-L2c). Plus a small "create dashboard from this DataObject" button in the shepard UI that POSTs a Grafana dashboard JSON. |
| **Apache Superset** | Best for **structured / tabular data + SQL** — exactly what the `POST /sql/timeseries` design (`aidocs/29` P10) unblocks. Superset speaks SQL natively; once P10 ships, Superset → shepard via Superset's "SQLAlchemy URI" config is one line. | Document a Superset connection recipe in `docs/deploy.md`. Optional: a Superset dashboard template that demos the LUMEN showcase data. |
| **Metabase** | Mid-fit alternative to Superset; lighter to deploy; same SQL-over-HTTP requirement. | Same as Superset — a recipe, not a deep integration. |

**Why this matters dev-experience-wise.** Reinventing dashboarding
in shepard is a multi-quarter slog (the `aidocs/43 §5.8` snap
dashboards are deliberately scoped to chat-driven *one-off*
analyses, not full BI). Integrating with tools researchers
already know cuts that scope to a thin adapter.

**The "SQL win"** specifically: `aidocs/29` P10's
`POST /sql/timeseries` endpoint is what makes Superset / Metabase
viable. P10 is gated on C5 (now shipped) — so the path is open.
Document the P10-Superset recipe in the same doc that ships P10,
to prevent the BI integration becoming a separate effort.

Out of scope: shepard ships *no* BI tool itself. Tableau /
PowerBI integrations follow the same shape (SQL adapter); add to
the recipe list as users ask.

---

## 5. Phasing

Spans the **DX series** (dev-ex pain points) and **PL series**
(plugin layer). Land in this order:

| ID | Slice | Size | Gate |
|---|---|---|---|
| **DX1** | Unified `ShepardTestStack` test-resource (Postgres + Mongo + Neo4j + Influx + mock OIDC). | M | None. Unblocks `*QuarkusTest` reliability. |
| **PL1a** ✓ | `PayloadKind` SPI interface in `backend/.../spi/payload/PayloadKind.java`. `NeoConnector` uses `ServiceLoader` to discover entity packages from PayloadKind impls. Shipped 2026-05-16. | M | None |
| **PL1b** ✓ | `shepard-plugin-spatial` — pilot migration of the `spatial` payload kind to a plugin. `SpatialPayloadKind` (ServiceLoader POJO) + `SpatialPluginManifest` (PluginManifest SPI). Behaviour-identical; profile-bound compose service unchanged. Shipped 2026-05-16. | M | PL1a |
| **DX3** | `mvn shepard:scaffold-payload-kind` archetype. | M | PL1a |
| **PL1c** ✓ | `shepard-plugin-hdf5` — extraction of HDF5/HSDS payload kind (A5a+A5b) to drop-in plugin JAR. `HdfPayloadKind` (ServiceLoader POJO) registers `de.dlr.shepard.data.hdf.entities`; fixes latent OGM-gap bug. `HdfPluginManifest` (PluginManifest SPI). `shepard.plugins.hdf5.enabled=false` default. 9 production files + 10 unit tests moved. Shipped 2026-05-16. | M | PL1a |
| **PL1d** | G1 (Git) per `aidocs/38` lands as a plugin from day 1. | M | PL1a + DX3 + `aidocs/38` |
| **DX2** | `ShepardTestFixtures` helper. | S | None |
| **DX5** | `quarkus-neo4j-devservices` integration; OpenAPI hot-reload story. | M | None |
| **DX4** | `make dev` single-command bootstrap. | S | DX1 + `aidocs/22` |
| **DX7** | `GET /v2/admin/features` introspection endpoint + CLI integration. | S | None |
| **PL1e** | (deferred) Refactor `file` payload kind to the SPI; lands the FS1 (`aidocs/45`) GridFS/S3 plugin split | L | PL1a + FS1 series |
| **PL1f** | (deferred) Refactor `structured` payload kind to the SPI. | M | PL1a |
| **PL1g** | (deferred) Refactor `timeseries` payload kind to the SPI. | L | PL1a + careful design — timeseries has more entanglement (`AnnotatableTimeseries`, hypertable specifics). |
| **DX6** | RFC 7807 errors (already designed as H4); reiterate as dev-ex. | M | H4 / `aidocs/16` |

Recommended order: **DX1 → PL1a → PL1b → DX3 → PL1c**. DX1 is the
unblocker; PL1a is the new abstraction; PL1b is the smallest pilot
that proves the SPI works on real code (spatial is feature-toggled
already and has the smallest surface); DX3 codifies the pattern;
PL1c lands the first net-new plugin (HDF5) from day 1.

---

## 6. Risks

- **SPI under-specified.** Designing an interface that fits four
  existing kinds + HDF5 + Git + future kinds is hard. Mitigation:
  PL1b (spatial as pilot) shakes out gaps before any other kind
  refactors. Don't lock the SPI as `1.0` until PL1c also conforms.
- **Plugin classpath conflicts.** Two plugins both offering a kind
  with the same `name()` → core errors at startup. Documented
  failure mode; clearer than silent override.
- **Migration churn for existing kinds.** PL1e (file refactor) is
  L-sized work that landed users won't notice. Risk: it ships,
  introduces a regression, the "no behaviour change" promise
  breaks. Mitigation: behaviour-pinning tests on the existing
  surface before the refactor.
- **Plugin author footguns.** Bad migrations from a plugin can
  corrupt the core graph. Mitigation: plugin migrations live in a
  separate Cypher migration namespace; `MigrationsRunner` (post-A1e
  fail-fast) aborts startup on conflict.
- **OpenAPI inflation.** Each plugin adds endpoints; the spec
  grows. Mitigation: tag-per-plugin in OpenAPI; clients can
  filter by tag at generation time.
- **Plugin authors expecting more than the SPI offers.** Will hit
  the §2.4 closed-surface constraints and want them lifted.
  Mitigation: the SPI grows by **adding** new optional methods
  (default no-op), never by removing constraints. RFC-style change
  process for SPI evolution after `1.0`.

---

## 7. What this is NOT

- **Not** a way to make shepard a generic-extension-platform —
  payload kinds only.
- **Not** a way to load JARs at runtime — build-time classpath
  decision.
- **Not** option (2) from the dispatch question (data-source
  *connector* plugins — OPC/UA / MQTT / Kafka). Those live
  outside shepard, in `shepard-timeseries-collector` or its
  successors. The SPI here is for **storage backends**, the
  thing that owns bytes inside shepard.
- **Not** a re-architecture of search, permissions, or graph
  traversal. Those stay closed surfaces.

---

## 8. Cross-references

- **aidocs:** `aidocs/16` (DX1-DX7 + PL1a-PL1g queueing entries
  follow this design), `aidocs/22 §4.6 / §4.6a` (admin-CLI feature
  toggle integration with PL1 plugins), `aidocs/34` (CONFIG-status
  rows when `shepard.payload.<name>.enabled` toggles ship; ZERO for
  internal SPI introduction; AWARE for first plugin migration),
  `aidocs/35` (HDF5/HSDS — first net-new plugin from day 1),
  `aidocs/38` (Git references — second net-new plugin),
  `aidocs/40 §3` (sTC's Sources/Sinks shape — adjacent but
  out-of-scope), `aidocs/44` (feature matrix — new SPI / DX rows),
  `aidocs/45` (FileStorage interface — narrower precursor; PL1e
  generalises it).
- **Backlog:** new **PL1** umbrella + sub-IDs in `aidocs/16`. New
  **DX1-DX7** rows for dev-experience improvements.
- **Standing rules:** plugin authors writing migrations must follow
  the `CLAUDE.md` migration rules (idempotent, fail-fast, tracker
  rows in `aidocs/34`/`aidocs/44`). Documented in the archetype
  template.
