# AAS → plugin extraction — Design (AAS1-plugin)

**Scope.** Forward-looking design note for extracting the Asset
Administration Shell (AAS) feature surface from the backend tree
into a stand-alone plugin (`plugins/aas/` → `shepard-plugin-aas`)
following the PM1a / ADR-0023 plugin-first heuristic spelled out
in `CLAUDE.md §"Always: think plugin-first for new features"`.

**Status.** Design, not started. AAS landed in the backend tree
before the plugin SPI matured; the user's direction (2026-05-18)
is to move it out:

> "aas should be moved to a seperate plugin soon. dont forget
> plugin documentation (description, whats it for, how it works,
> endpoints...)"

**Snapshot date.** 2026-05-18.

**Companion docs.** Sits next to `aidocs/platform/47` (plugin
system), the existing `plugins/unhide/`, `plugins/file-s3/`,
`plugins/minter-datacite/` plugins, and the future
`docs/reference/aas-plugin.md` + `docs/help/aas-plugin-quickstart.md`
+ `docs/install/aas-plugin.md` per CLAUDE.md's "plugins ship their
own documentation" rule.

---

## 1. Why extract

The PM1a plugin-first heuristic says:

> "New integrations (Helmholtz Unhide harvest feed, git host
> adapters, **AAS registry sync**, …) → plugin shape. They have
> their own release cadence, their own dependency tree, and their
> own failure modes; isolating them from core is the structural
> fix."

AAS lives in the backend tree today because it was designed
before this rule was codified. Three concrete reasons to move it:

1. **Release cadence.** The IDTA AAS specification iterates
   independently of shepard. A new IDTA submodel template should
   ship as a plugin update, not a backend release.
2. **Dependency tree.** AAS pulls in IDTA's AAS4J client
   libraries, the AAS-server self-description models, and
   (when AAS_REGISTRY is enabled) the registry-client HTTP
   surface. None of this code is needed when AAS is disabled.
   Backing it out lets `shepard-backend` deployments that don't
   use AAS stay lean.
3. **Failure isolation.** The AAS registry sync is the most
   network-coupled service in the backend — it talks to an
   external AAS registry whose availability is outside DLR's
   control. A connection-pool exhaustion in the registry client
   currently can starve other backend services. As a plugin with
   its own scoping, failures are bounded.

---

## 2. What's in scope

Source tree to move:

```
backend/src/main/java/de/dlr/shepard/aas/                        →  plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/
  entities/AasRegistration.java                                   →  entities/AasRegistration.java
  services/AasServerSelfDescriptionService.java                   →  services/...
  services/AasShellMappingService.java                            →  services/...
  services/AasIdtaTemplateImportService.java                      →  services/...
  services/AasRegistryOutboxService.java                          →  services/...
  services/AasRegistryClient.java                                 →  services/...
  daos/AasRegistrationDAO.java                                    →  daos/...
backend/src/main/java/de/dlr/shepard/v2/aas/                     →  plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/
  io/Aas*IO.java                                                  →  io/...
  resources/AasShellsRest.java                                    →  resources/...
  resources/AasWellKnownRest.java                                 →  resources/...
  resources/AasAdminRest.java                                     →  resources/...
backend/src/main/java/de/dlr/shepard/v2/admin/aas/               →  plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/admin/
  resources/AasRegistrationAdminRest.java                         →  resources/...
  io/AasRegistrationIO.java                                       →  io/...
  io/AasSyncResultIO.java                                         →  io/...
```

Plus the matching migrations (`backend/src/main/resources/neo4j/migrations/V46__Add_appId_constraint_AasRegistration.cypher`)
and tests under `backend/src/test/java/.../v2/admin/aas/`.

---

## 3. What stays in-core

Per the existing plugin-extraction pattern (UH1a unhide, KIP1h
minter-local) some interfaces stay in-tree so every plugin can
compile against them:

- **Permissions surface** (`PermissionsService`, `JWTFilter`) —
  the AAS endpoints inherit standard shepard auth.
- **Core entity types referenced by AAS** (`Collection`,
  `DataObject`) — read-only from the plugin's perspective. The
  AAS plugin's `AasShellMappingService` reads DataObjects via
  the shared `DataObjectService`; it doesn't reach into the
  entity graph directly.
- **`AppIdGenerator`** — UUID v7 minting stays in-core.
- **`InstanceRorConfigService`** — if the AAS server-self-
  description echoes the running organisation, it reads from the
  in-core service (consistent with all other org-identity reads).
- **Provenance capture** (`ProvenanceCaptureFilter`) — runs at
  the JAX-RS layer, applies to plugin endpoints the same as
  in-core ones.

---

## 4. Plugin manifest

```java
public final class AasPluginManifest implements PluginManifest {
  public static final String ID = "aas";
  public String id()              { return ID; }
  public String displayName()     { return "Asset Administration Shell"; }
  public String version()         { return "6.0.0-SNAPSHOT"; }
  public String compatibility()   { return ">=6.0.0-SNAPSHOT,<7"; }
  public List<Class<?>> registerablePayloadKinds() { return List.of(); }
  public List<Class<?>> registerableMinters()      { return List.of(); }
  public List<Class<?>> registerableFileStorages() { return List.of(); }
}
```

The plugin doesn't introduce a new PayloadKind / Minter / FileStorage —
its surface is REST endpoints + scheduled outbox sync. Both are picked
up by Quarkus automatically once the plugin JAR is on the classpath
(per the PM1a indexing path).

`META-INF/services/de.dlr.shepard.plugin.PluginManifest` line:

```
de.dlr.shepard.plugins.aas.AasPluginManifest
```

---

## 5. Maven module

`plugins/aas/pom.xml`:

- `<parent>` = the same root `shepard-plugins-parent` used by
  `unhide`, `minter-local`, etc.
- `<dependencies>`:
  - `de.dlr.shepard:backend` with `<scope>provided</scope>` — the
    SPI seam.
  - IDTA `org.eclipse.digitaltwin.aas4j:dataformat-json` (and the
    `model` sibling).
  - The AAS registry client (currently shipped via the
    `aas-registry-client` direct dependency in `backend/pom.xml` —
    moves here entirely).
- `<build>` reuses the standard plugin shade config (lift from
  `plugins/unhide/pom.xml`).

Backend `pom.xml` changes:

- Drop the three direct IDTA / AAS dependencies — they live in
  the plugin now.
- Drop the AAS registry-client dependency.
- Add the new plugin as a `<dependency>` under the
  `with-plugins` profile (same shape as the unhide / minter-local /
  file-s3 entries).
- Add `quarkus.index-dependency.shepard-plugin-aas.group-id =
  de.dlr.shepard.plugins` / `artifact-id = shepard-plugin-aas`
  so Quarkus's build-time CDI scanner picks the `@ApplicationScoped`
  beans.

Tracker in `aidocs/34` gets an "AAS1-plugin" row noting the
package move, with **AWARE** status — same shape as the
KIP1h / minter-local move.

---

## 6. Feature toggle + admin config

A per-plugin runtime toggle is already free via PM1a
(`shepard.plugins.aas.enabled`, default `true`).

The existing AAS admin-config is `application.properties` keyed.
Per CLAUDE.md's "Always: surface operator knobs" rule, the
extraction should also lift the registry-sync settings into a
runtime-mutable `:AasPluginConfig` singleton + matching
`/v2/admin/aas/config` PATCH endpoint + `shepard-admin aas
{status, enable, disable, set-registry-url, …}` CLI parity.
Track this as a follow-up `AAS1-plugin-runtime` row — not
gating the package-move landing.

---

## 7. Three-page plugin docs (CLAUDE.md requirement)

Per the "Always: plugins ship their own documentation" rule, this
plugin ships three docs in the same PR as the first feature
landing (or as a follow-up if the package-move PR is purely
structural):

1. **`docs/reference/aas-plugin.md`** — full reference. Covers:
   - The AAS Server self-description endpoint (`GET /v2/aas/server-self-description`)
   - The AAS Shells API (`GET/POST /v2/aas/shells`,
     `GET/PUT/DELETE /v2/aas/shells/{aasId}`)
   - The AAS registration outbox (config keys, retry policy,
     scheduler interval)
   - The IDTA submodel template import path (`POST /v2/aas/idta-import`)
   - The :AasRegistration Neo4j entity shape + V46 migration
2. **`docs/help/aas-quickstart.md`** — casual-task page:
   "Register this shepard with an AAS registry", "Look up a
   DataObject by its AAS shell id", "Import an IDTA submodel
   template", each answered in two clicks.
3. **`docs/install/aas-plugin.md`** — operator install guide:
   - Add `shepard-plugin-aas` JAR to `/deployments/plugins/`
   - Set `shepard.aas.registry-url` (or runtime equivalent)
   - Restart
   - Verify with `shepard-admin plugins list` → AAS row shows
     `ENABLED`
   - Known pitfall: AAS registry connection-pool sizing under load

The main `docs/index.md` and `docs/user-guide.md` get a "Plugins"
sub-link pointing at each plugin's three pages. Pattern lift from
the existing `docs/reference/unhide-publish.md` /
`docs/help/publish-to-helmholtz-unhide.md`.

---

## 8. Migration steps (operator-facing)

For operators currently running a backend with AAS enabled:

1. **No action required for the demo install** — the
   `with-plugins` Maven profile is on by default and includes the
   new `shepard-plugin-aas` JAR. After upgrading the backend
   image, AAS endpoints continue to respond exactly as before.
2. **Operators who built their own backend image without the
   `with-plugins` profile** — they must explicitly add the
   `shepard-plugin-aas` JAR to `/deployments/plugins/`. Otherwise
   the AAS endpoints will 404 after the upgrade. Surface this in
   the `aidocs/34` ledger row as **AWARE** with a clear note.
3. **Third-party AAS extensions** — none known today. If any
   exist, they need to declare `>=6.0.0-SNAPSHOT,<7` compatibility
   like all other plugins post-V6.

---

## 9. Validation

- The existing AAS test suite (under
  `backend/src/test/java/.../v2/admin/aas/` + the matching shell /
  registry-client tests) moves wholesale into
  `plugins/aas/src/test/`. Coverage parity should hold; the JaCoCo
  bundle floor is per-module so the backend's floor likely rises
  slightly post-extraction.
- The integration smoke is a docker-compose `up -d` against the
  default `with-plugins` build: AAS endpoints respond, server-self-
  description payload is intact, the registry-sync scheduler runs.
- The `infrastructure/smoke-test.sh` gains an AAS check if it
  doesn't have one (the smoke test today doesn't exercise AAS
  endpoints; consider adding `GET /v2/aas/server-self-description`
  → 401 to detect breakage).

---

## 10. Risks

- **Package-rename PR is large** (~18 files moving). Mechanical
  but easy to mis-merge. Mitigate by landing it as a no-op
  refactor PR with zero behaviour change — separate from any
  feature work on AAS.
- **CDI bean discovery edge cases** — Quarkus's build-time CDI
  scanner sometimes misses beans in plugin JARs when the
  `quarkus.index-dependency.*` properties aren't set. The
  pattern from UH1a / minter-local works; copy it verbatim.
- **OpenAPI doc drift** — the plugin's REST endpoints must keep
  surfacing in the main OpenAPI document. The PM1a pattern
  already handles this (the plugin JAR is on the build
  classpath); verify post-move.

---

## 11. Out of scope

- **Wire path renames.** The `/v2/aas/...` paths stay where they
  are. The plugin's REST classes pick up the same `@Path`
  annotations; only their Java package moves.
- **AAS feature additions.** Submodel deduplication, AAS-registry
  filter expressions, IDTA v3 template support — all tracked as
  separate roadmap items, none gated on this extraction.
- **Cross-plugin coordination.** If a future minter-aas plugin
  wants to publish AAS shells as PIDs (KIP-style), that's a new
  plugin depending on this one — no contract change here.
