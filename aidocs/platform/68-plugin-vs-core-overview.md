# 68 — Plugin-vs-core architecture overview

**Status.** Discussion draft.
**Snapshot date.** 2026-05-13.
**Audience.** Maintainer + contributors. The frame for "what's
core, what's a plugin, what should move."

**Originating items.** User direction 2026-05-13 ("write an
overview over plugins and core features. we will discuss") +
CLAUDE.md's new "always think plugin-first" rule. Couples to
`aidocs/47` (PayloadKind / PayloadStorage SPI), `aidocs/45`
(FileStorage SPI), `aidocs/38` (Git integration / `GitAdapter`),
`aidocs/57` (client generators / `Minter`-shape adapters),
`aidocs/66`/`67` (KIP + Unhide).

---

## 1. The frame

Three buckets:

| Bucket | What lives there | Reversibility of choice |
|---|---|---|
| **Core** | Things every shepard install runs. Auth, permissions, the entity graph, the SPI registry. Removing core breaks the product. | hard / one-way |
| **Plugin-by-interface** | Implementations swapped behind an in-tree interface. Different installs choose different plugins; some installs run none. The interface stays in core. | moderate — interface is durable, implementation isn't |
| **Plugin-by-package** | Standalone modules (`shepard-plugin-*`) consumed via discovery / a registry. Operators pick which to deploy. | easy — install / uninstall doesn't touch core |

The slippery shapes are at the **core ↔ plugin-by-interface**
boundary (the interface is part of core but does the implementation
need to live in-tree?) and at the **plugin-by-interface ↔
plugin-by-package** boundary (when does a swappable adapter
graduate to a separate module?).

## 2. Current state — what's where

### 2.1 Identifiably core

| Surface | Why core |
|---|---|
| `auth/*` — JWT filter, API-key auth, OIDC integration | Security perimeter; pluggable auth is a different product. |
| `permission/*` — `PermissionsService`, `Roles`, `:HAS_ROLE` graph | Permission enforcement is the only enforcement point; replicating into plugins multiplies trust boundaries. |
| `Collection` / `DataObject` / `User` / `BasicEntity` / `appId` (L2 chain) | The graph shape every plugin compiles against. |
| `:Activity` (PROV1) capture filter | Cross-cutting audit; capturing in-tree means every endpoint contributes automatically. |
| `MigrationsRunner`, the V## migration system | Startup-ordering invariants; plugins can declare migrations but the runner is core. |
| The SPI registry itself (`PayloadKindRegistry`, future `MinterRegistry`, future `AdapterRegistry` family) | Discovery is core; the things discovered are plugins. |

These shouldn't move. The CLAUDE.md "plugin-first" rule explicitly
exempts them.

### 2.2 Plugin-by-interface (interface in core, swappable implementations)

| Interface | Implementations on / planned | Status |
|---|---|---|
| **`GitAdapter`** (`context/references/git/adapters/`) | GitLab (G1b ✓), GitHub + Gitea (G1d ✓), Codeberg/Forgejo via host-CSV widener | shipped; the seam is durable |
| **`Minter`** (`aidocs/66 §5`) | mock (default), ePIC (Handle), DataCite (DOI) | designed (KIP1a) — interface lands in core; ePIC + DataCite are operator-selectable |
| **`GitAdapter` for non-git protocols?** | (hypothetical) — out of scope today | n/a |
| **`SemanticConnector`** (`context/semantic/`) | Internal n10s (N1a ✓), external SPARQL (existing), JSKOS / SKOSMOS (existing) | shipped pre-fork; the seam is durable |
| **`FileStorage`** (`aidocs/45` FS1 design) | GridFS (existing, in-tree), S3/MinIO (FS1b designed) | designed; interface introduction is in-tree |
| **`PayloadKind` + `PayloadStorage`** (`aidocs/47 §2`) | file, structured, spatial, timeseries, HDF5 (planned plugin), video (planned plugin), git (could be) | designed; not yet introduced as the kind-discovery seam |

The interface-in-core pattern is the right shape for adapters
where the **failure mode of a missing plugin is "feature off"**
rather than "shepard won't start."

### 2.3 Plugin-by-package (separate `shepard-plugin-*` modules)

Currently shipping:

- **None.** All "plugin-by-package" candidates today are
  designed-not-yet-shipped.

Designed / planned:

| Plugin | Source aidoc | Status |
|---|---|---|
| `shepard-plugin-hdf-hsds` | `aidocs/35` (A5 series) | partially shipped in-tree as `data/hdf` (A5a ✓ + A5b ✓); should be **extracted to a plugin** when PayloadKind SPI (`aidocs/47` PL1a) lands |
| `shepard-plugin-video` | `aidocs/53 §2` (VID1 series) | designed; ships as a plugin from day one |
| `shepard-plugin-unhide` | `aidocs/67` | designed; ships as a plugin (UH1a) |
| `shepard-plugin-aas` | `aidocs/52` (AAS1 series) | designed; could be plugin or in-tree per `aidocs/52` open question |
| `shepard-plugin-ref-dbpedia-databus` | `aidocs/58 §7` | designed; explicitly plugin (REF1) |
| `shepard-plugin-file-gridfs` + `shepard-plugin-file-s3` | `aidocs/45 §6` (FS1a/b) | designed; current GridFS is in-tree and would split |
| `shepard-plugin-spatial-postgis` | `aidocs/47 PL1b` | designed; current spatial is in-tree and would split |

### 2.4 In-tree today, **plugin candidates per the new rule**

These are the items where the new CLAUDE.md "plugin-first" rule
would have flagged the design. Most landed before the rule, so
the question is whether to **extract** them later:

| Feature | Currently | Plugin extraction cost | Recommended |
|---|---|---|---|
| **HDF5 / HSDS** (`data/hdf` + V25 + REST) | in-tree (A5a/b shipped) | M — move package, pin SPI version, extract config | **extract on PL1a's heels** — already smelly with HSDS sidecar |
| **Internal semantic repository (n10s)** | in-tree (N1a/b/c shipped) | M — depends on `SemanticConnector` becoming a published SPI | **leave in-tree until external n10s clients want the SPI** — single-impl seams aren't worth the split |
| **Spatial / PostGIS** | in-tree (legacy upstream) | M — already designed (PL1b) | **wait for PL1a** — natural extraction point |
| **File / GridFS** | in-tree (legacy upstream) | L — biggest blast radius | **wait for FS1a** — needs the FileStorage SPI to land first |
| **Timeseries / Timescale** | in-tree (legacy upstream) | L — heaviest entanglement (continuous aggregates, hypertable specifics) | **stays in-tree** for the foreseeable; the SPI shape isn't well-formed yet |
| **Templates (T1)** | in-tree (T1a-d shipped) | S — small package, clean boundary | **could split** if templates become a community contribution surface; otherwise leave |
| **PROV1 capture** | in-tree | L — would need to swap the capture filter per-install | **stays in-tree** — see §2.1 (cross-cutting audit) |
| **Lab journal + rendering (J1)** | in-tree | M — markdown render is a `MarkdownRenderer` class, the editor UI is Vue | **leave** — single domain shape |

### 2.5 Frontend

Frontend (`frontend/`) is one Nuxt app, no plugin model today.
Component-level plugins (e.g. drop-in viewers per payload kind)
would be a substantial design — not in scope today, but worth
noting as a future axis. The closest current shape is the per-
DataObject "pane" pattern (FileBundleReferences pane, Git
references pane, etc.) — each pane is one Vue component bound
to one backend feature. **Plugin-shaped frontend** would mean
those panes registering themselves at runtime from a manifest.

## 3. The plugin-discovery shape

Designed in `aidocs/47 §2.5`, not yet wired:

- `shepard.plugins.<plugin-id>.enabled=true/false` config keys
- A `PluginManifest` that each plugin emits at startup
- The `PayloadKindRegistry` reads the manifests and binds
  factories
- Plugins discovered via SPI service-loader (Java's
  `ServiceLoader` pattern over the Quarkus uber-jar shape) or
  via separate `@QuarkusBuildItem` registration

The **registry interfaces are core**; the plugin packages live
outside. `aidocs/47` calls this DX3.

The point at which "in-tree" becomes "plugin-by-package" is when
the discovery seam lands — until then, plugins-by-interface are
just well-structured in-tree code.

## 4. Boundaries — where things tend to leak

Recurring patterns that show up as design debt:

| Leak | Example | Fix |
|---|---|---|
| **Plugin needs a Cypher migration** | HDF5 wants its own V## constraint | Plugins declare migrations under `<plugin>/src/main/resources/neo4j/migrations/`; the runner discovers across the classpath. Already works post-A1e (every classpath migration runs). |
| **Plugin needs new IO shapes on a core endpoint** | Video extends `BasicEntityIO` with `videoOffsetMs` | Use `OutputProfile` (V2S1) to lift the kind-specific shape into a profile parameter, not a hardcoded field. |
| **Plugin needs to fire `:Activity` events** | Unhide harvest counts as an activity? | Plugin calls the existing `ProvenanceService.recordServiceActivity(...)` — the service is core, the call site is the plugin. |
| **Plugin needs config that survives restart** | UH1a's `enabled` toggle | Per the new "admin-configurable" rule: `:*Config` singleton, admin REST, CLI parity. |
| **Plugin needs operator-readable docs** | Each plugin's installation runbook | One `docs/reference/plugins/<plugin-id>.md` per plugin; index in `aidocs/49 §2.2`. |

## 5. Open questions for discussion

1. **Should the HDF5/HSDS work (A5 series) be extracted to a
   plugin now, or wait for PL1a?** A5a/b shipped in-tree; the
   extraction is moderate cost and would be cleaner before A5c
   (HdfReference) compounds the in-tree footprint.

2. **Should we land PayloadKind SPI (PL1a) before the next new
   payload kind?** Currently new payload kinds (HDF, video, git)
   each invent their own shape. PL1a is a one-time cost that
   pays back per kind.

3. **Plugin-by-package distribution.** **Resolved 2026-05-13:
   JAR drop-in via `ServiceLoader`** — see `aidocs/63` ADR-0023.
   Each plugin is a standalone Maven module producing a JAR
   carrying `META-INF/services/de.dlr.shepard.plugin.PluginManifest`;
   the backend bootstrap walks `backend/plugins/*.jar` after
   `MigrationsRunner.apply()` and registers each plugin's CDI
   beans / REST resources / payload-kind factories. Operator
   install path: `cp shepard-plugin-foo-X.Y.Z.jar backend/plugins/`
   + restart. Uninstall: `rm` + restart. The two rejected shapes
   (compose-side sidecar; forked Dockerfile per install) are
   documented in ADR-0023's "Alternatives considered" — neither
   matches the plugin-first rule's "install/uninstall without
   rebuild" expectation. UH1a (in flight) is the first module
   under the new shape; the `PluginManifest` SPI itself lands
   alongside it.

4. **Where does the convenience-wrapper (`shepard-py` /
   `shepard-ts`, `aidocs/27`) sit?** Currently a separate
   layer on top of generated clients, lives in `clients/` and
   `clients-v2/`. **Plugin-shaped?** Probably not — it's a
   user-facing library, not a shepard-side feature. Leave as is.

5. **Frontend plugins.** Worth a design doc? Or punt until a
   real third-party Vue pane emerges?

## 6. Recommended path forward

In the spirit of "decisions over indecisions, with explicit
reversibility":

1. **Land PL1a (PayloadKind SPI) next** after the current open
   queue clears. It's the gate item for half the remaining
   plugin candidates.
2. **Extract HDF5/HSDS to `shepard-plugin-hdf-hsds`** as the
   first plugin-by-package; it's the cleanest test of the SPI
   shape because the sidecar already isolates the failure mode.
3. **Ship VID1 as a plugin from day one** (new payload kind;
   matches the rule).
4. **Land UH1a as a plugin from day one** (external integration;
   matches the rule). `aidocs/67` already designs it this way.
5. **Leave existing in-tree code in-tree** until the SPI is
   stable enough that extraction is mechanical.

The plugin-first rule applies to **new** features. Retroactively
plugin-ifying every in-tree feature is a different (much bigger)
exercise that should only happen when there's evidence of
real third-party demand.

## 7. Cross-references

- `aidocs/47` PayloadKind / PayloadStorage SPI — the gate item.
- `aidocs/45` FS1 — FileStorage SPI; precedes FS1a/b split.
- `aidocs/38` Git integration — `GitAdapter` interface example.
- `aidocs/57` Client generators — `Minter`-shape adapters; ADR-0022.
- `aidocs/66` KIP — `Minter` interface seam.
- `aidocs/67` Unhide — first plugin-from-day-one external integration.
- `aidocs/52` AAS — open question whether plugin or in-tree.
- `CLAUDE.md §"Always: think plugin-first for new features"` — the
  rule this overview elaborates.
- `CLAUDE.md §"Always: surface operator knobs in the admin config"` —
  the runtime-knob durability rule.
