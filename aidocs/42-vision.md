# shepard — Vision (for researchers)

**Status.** **Live.** Kept current as features land. The doc you'd
hand a researcher who asks "what is shepard, and should I use it?"

**Snapshot date.** 2026-05-08.
**Audience.** Researchers, group leads, principal investigators.
Not operators (they have `docs/admin.md`), not developers (they have
the rest of `aidocs/`).
**Standing rule.** Per `CLAUDE.md`, this doc updates in the same PR
as any user-visible feature change. A stale vision is worse than no
vision.

---

## In one paragraph

shepard is a **research-data platform** for the kinds of mixed,
heterogeneous datasets a real DLR experiment produces — sensor
timeseries, CAD files, lab-journal notes, photos, geometry,
structured run-logs, semantic annotations — all under one
permission-aware, exportable umbrella. You put your campaign in,
your collaborators see what they're allowed to see, and when it's
time to publish you get a citable RO-Crate ZIP back. It is not a
files-and-folders share, and it is not a database; it is the
**data context** layer in between.

## Who it's for

**Casual users come first.** Most shepard researchers touch the
tool once a month between experiments — they're not data engineers
who live in the API. Every feature on the roadmap is judged
against "does this make the casual-user path easier?" (the
explicit framing rule in `aidocs/47 §1.0`). Snap dashboards
(`aidocs/43 §5.8`), templates (`aidocs/39`), the LUMEN-inspired
showcase notebook, and the deploy guides under `docs/` are all
shaped by this constraint.

Specifically:

- A **PI** running a multi-month experiment campaign who wants the
  raw data, the analysis notes, and the publication artifact to all
  trace back to the same provenance graph.
- A **research engineer** who has timeseries from OPC/UA or MQTT and
  wants to attach them to a logical "test run" alongside the test
  report and the operator's debrief.
- A **postdoc** who wants to find the dataset their colleague
  produced last quarter without filename-scavenging.
- A **data steward** who has to ship an RO-Crate to a journal and
  doesn't want to assemble it by hand.

Power users (people who *do* extend shepard) come second; the
plugin SPI (`aidocs/47 §2`) keeps their path painless without
distorting the casual-user defaults.

It is **less obviously useful** for a single-author short-term
project where a git repo plus three CSV files would do. The
overhead of shepard is amortised across collaboration and
re-discovery.

## What's in the box (today)

shepard organises everything into five primitives:

| Primitive | What it represents | Example |
|---|---|---|
| **Collection** | A campaign, project, or topic — the top-level grouping | "LUMEN-Inspired Hot-Fire Test Campaign — Q3 2024" |
| **DataObject** | A logical thing inside a Collection, freely nestable, with attributes | "TR-004 — fired run with anomaly" |
| **Reference** | A pointer from a DataObject to a payload of one of five kinds | "`tr-004-sensors`" — TimeseriesReference |
| **Annotation** | A semantic tag from an ontology attached to anything | `phase = ramp_up`, `severity = HIGH` |
| **Lab journal entry** | A CommonMark + GFM markdown note attached to anything. **J1a shipped**: `GET /v2/lab-journal/{appId}/render` returns sanitised HTML. **J1b shipped**: `GET /v2/lab-journal/{dataObjectAppId}/notebooks` lists `.ipynb` file references. **J1d shipped**: `GET /v2/lab-journal/{entryAppId}/history` returns append-only revision history — researchers can recover earlier versions of their notes. Plain-text entries render unchanged as `<p>` elements. | "Vibration spike on fuel-turbopump observed at t=8s..." |

Plus payload kinds (the things References point at):

- **TimeseriesReference** → time-stamped numeric data, stored in
  TimescaleDB. Each reference can carry **timeseries annotations**
  (TA1a shipped): labelled intervals or point events (e.g. "fuel
  ramp", "combustion instability onset") attached to a reference via
  `/v2/timeseries-references/{appId}/annotations`. Annotations carry
  `startNs` / `endNs` / `label` (required), plus optional
  `description`, `confidence`, and an `aiGenerated` flag ready for
  the TA1c anomaly-detection hook.
- **FileReference (singleton)** → a single file attached to a
  DataObject (one PDF, one CSV result, one photo). Bytes live in a
  shared `_shepard_files` MongoDB / GridFS namespace — no per-Reference
  Mongo collection overhead. New `/v2/files/{appId}` surface with
  `POST`, `GET`, `GET /content` (HTTP-range capable), `PATCH`, `DELETE`
  (FR1b shipped, see `aidocs/53 §1.8`). Distinct from `FileBundleReference`
  below — the casual upload UI (FR1b-ui, queued) dispatches by drag count
  (1 file → singleton, ≥ 2 → bundle).
- **FileBundleReference** → a bag of files (CAD, PDFs, photos,
  source code, camera frame dumps), optionally organised into one or
  more **FileGroup** sub-Reference sub-nodes for "these N files
  belong to one logical sub-run" structure (FR1a shipped, see
  `aidocs/53 §1`). Stored per-bundle in MongoDB / GridFS. The upstream API
  surface keeps the legacy `FileReference` name for byte-for-byte
  wire compatibility; the `/v2/bundles/...` shelf reads with the new
  name. The upstream `/shepard/api/...fileReferences/...` surface
  projects both singleton and bundle types onto the legacy flat-files
  wire shape; writes there always create bundles (singletons are
  `/v2/`-only).
- **StructuredDataReference** → JSON documents, stored in MongoDB
  (run-logs, configs, metadata bundles).
- **SpatialDataReference** → geo / spatial geometry, stored in
  PostGIS (optional feature toggle).
- **HDF5 (via HSDS sidecar — opt-in)** *(A5a shipped: `HdfContainer`
  create/read/delete + opt-in `hdf` compose profile + HTTP Basic
  Phase 1 auth — see `aidocs/35`)* → HDF5 containers backed by the
  HSDS sidecar. The per-DataObject reference, the byte-identical
  download fallback, the permission bridge, and the
  shepard-API-key-to-`h5pyd`-bearer-token relay are queued (A5b–A5e).
- **Git reference** *(loose, tracked (GitLab + GitHub + Gitea) shipped via
  G1a/G1b/G1d; pinned planned via G1c; UI in-flight)* →
  pinned git commit + path, for analysis code provenance.

## The cross-cutting features

These work the same way across every primitive:

- **Permissions.** Every entity has owner / readers / writers /
  managers. PUBLIC is one toggle away. Group-based permissions
  designed (`aidocs/24` F2) but not yet shipped.
- **Lineage / predecessors.** Every DataObject can point at one or
  more predecessors. The "TR-006 was the bearing-replaced re-test
  of TR-004" relationship is a graph edge, traversable in both
  directions.
- **Versioning + snapshots.** Today: `Version` is a marker.
  *(Coming with V2; design done in `aidocs/41`)*: snapshots make
  point-in-time, immutable, reproducible reads first-class — your
  RO-Crate export against a snapshot is byte-reproducible.
- **Search.** Across all entities and attributes; semantic-annotation
  search lights up additionally for ontology terms. Improvements
  in flight (`aidocs/13`).
- **Semantic-annotation repositories — internal *or* external.**
  Reference an ontology term by IRI; resolve labels through one of
  three connector types. **External** SPARQL endpoints (the upstream
  path) for users who run their own triple store. **Internal**
  (`aidocs/48`, N1a shipped) — the neosemantics plugin runs inside
  shepard's existing Neo4j, so a fresh `docker compose up` is the
  whole setup for "I want to annotate `vibration_max` with the QUDT
  IRI for `g rms`." **Pre-seeded common ontologies** (PROV-O,
  Dublin Core, schema.org, FOAF, QUDT, OM-2, W3C Time, GeoSPARQL,
  OBO Relation Ontology, NFDI4Ing metadata4ing) ship with the
  install (N1b + ONT1a + ONT1b, shipped) — ten SHA-256-pinned
  Turtle bundles imported into the n10s repository on startup, so
  the casual annotation flow finds resolvable IRIs out of the box.
  The Relation Ontology supplies the cross-cutting relations
  researchers want for process-graph edges (`part_of`, `has_part`,
  `derives_from`, `participates_in`, `has_input`, `has_output`).
  metadata4ing is the NFDI4Ing engineering-research extension of
  PROV-O — `m4i:ProcessingStep`, `m4i:Method`, `m4i:Tool`,
  `m4i:InvestigatedObject`, `m4i:NumericalVariable` + QUDT units —
  layering domain-specific provenance terms on top of the PROV-O
  baseline. **Admins can extend the semantic vocabulary at runtime**
  (N1c2, shipped): the pre-seeded bundles can be flipped on/off
  per-instance, and lab-specific TTL ontologies can be uploaded
  through the admin API or `shepard-admin semantic ontologies
  upload` — no rebuild, no restart for the upload itself, joins the
  seed loop on the next start. PROV-O + Relation Ontology stay
  required (admin disable refused with RFC 7807 `semantic.bundle.required`)
  so the audit-trail interop never silently breaks.
- **Plugin extensibility** (`aidocs/47`, PM1a shipped). Every
  shepard install can grow `shepard-plugin-*` JARs without
  rebuilding the image. Drop a JAR into `backend/plugins/`
  (or `/deployments/plugins/` in containers), restart — the
  backend's `PluginRegistry` walks the directory at startup,
  loads each plugin via Java's `ServiceLoader`, and runs the
  `onRegister` lifecycle hook after database migrations apply.
  The same per-plugin runtime toggle (`shepard.plugins.<id>.enabled`)
  flips a plugin off without removing the JAR. UH1a (Helmholtz
  Unhide publish) is the first plugin under the new shape; HDF5
  / video / AAS / KIP minters / FS GridFS+S3 split / DBpedia
  rich-reference all queue behind it following the same pattern
  per ADR-0023.
- **Subscriptions.** URL-pattern webhooks fire on entity changes
  for downstream pipeline glue.
- **RO-Crate export.** Selective (`aidocs/31`) — choose which
  payload kinds, redact specific fields, optionally include
  permissions / annotations / versions. Reproducible (post-V2d).
- **API keys** with optional `validUntil` expiry (L5, shipped) —
  you can hand a campaign-lead key to a sub-team without forever
  granting access.
- **Activity dashboard + PROV-O provenance trail + metadata4ing (m4i)
  content-negotiation** (`aidocs/55`, `aidocs/64`, PROV1 series —
  PROV1a/b/c/d/f/g/h shipped). Every mutation generates
  a W3C PROV-O `:Activity` node (`prov:Agent` + `prov:wasAssociatedWith`);
  the per-Collection sparkline ("how busy is this Collection?")
  renders inline on the Collection page with totals, distinct-agent
  count, action-kind histogram, and a cumulative-integral overlay.
  Time-range picker (7d / 30d / 90d / 1y). Reads opt-in via
  `shepard.provenance.capture-reads`. **Three export shapes** on
  `/v2/provenance/{activities,entity/{appId},count}` via content
  negotiation: plain JSON (default), PROV-N JSON (`Accept:
  application/prov+json`), PROV-O JSON-LD (`Accept:
  application/ld+json`), or the **NFDI4Ing metadata4ing flavour**
  (`Accept: application/ld+json; profile=metadata4ing`) with
  `m4i:ProcessingStep` / `m4i:InvestigatedObject` / `m4i:Person`
  dual-typed alongside the PROV-O parents — feeds straight into a
  SPARQL store or an RO-Crate manifest. 2-year retention default.
  Instance-admin dashboard (PROV1e) queued behind A0's admin shell.
- **HMC Kernel Information Profile publication** (`aidocs/66`,
  KIP1a + KIP1e + KIP1f + KIP1g + KIP1h shipped). A one-click **Publish
  button** sits at the top of every Collection and DataObject
  pane in the web UI (KIP1e): a researcher with Writer / Manager
  permission clicks it, picks an SPDX licence, confirms, and the
  freshly-minted PID comes back in a snackbar with a one-click
  "Copy resolver URL" action. Under the hood:
  `POST /v2/data-objects/{appId}/publish` (or
  `/v2/collections/{appId}/publish`) mints a PID and attaches a
  `:Publication` row. Dereference via the public, unauthenticated
  `GET /v2/.well-known/kip/{pid-suffix}` resolver — any HMC PID
  resolver can consume the small JSON-LD-flavoured KIP record
  (digital-object type, landing-page URL, created/modified
  timestamps, rights-holder, version). **Local PIDs are now
  versioned** (KIP1h): the default `LocalMinter` plugin mints
  stable `shepard:<instance.id>:<kind>:<appId>:v<n>` PIDs — same
  inputs return the same PID, a forced re-mint via `?force=true`
  bumps the version. **Minters are now optional plugins**
  (KIP1h): the legitimate default ships as
  `shepard-plugin-minter-local`. **Real DOIs via DataCite (KIP1d)**
  ship as `shepard-plugin-minter-datacite` — an operator with a
  DataCite Member contract configures the plugin
  (`shepard-admin minters datacite set-prefix 10.5072 / set-repository-id /
  set-password / enable`), flips `shepard.publish.minter=datacite`,
  and subsequent `POST .../publish` calls mint real DOIs against
  Fabrica test (default) or production — with `IsNewVersionOf` /
  `HasVersion` chain links so re-publishes form a citable
  version trail in DataCite Commons. ePIC handle service (KIP1c)
  ships as a sibling plugin JAR per ADR-0023.
  Operators who want a resolver-only deployment (no PID
  minting, but the public `/v2/.well-known/kip/...` resolver
  keeps working against existing rows) set
  `shepard.publish.minter=none`. Publication records are
  append-only by KIP convention — every Publication tracks its
  `versionNumber`, the most recent is "current." **Retirement
  (KIP1f)**: a researcher with Writer / Manager permission can call
  `DELETE /v2/{kind}/{appId}/publish` to mark the most-recent
  Publication as `digitalObjectMutability: "retired"` — the row is
  preserved (PIDs are permanent) but the mutability marker signals
  that the object is no longer the operator's active intent.
  Idempotent — a second DELETE returns 204.
- **Publish to the Helmholtz Knowledge Graph (Unhide)** (`aidocs/67`,
  UH1a + UH1b + UH1c shipped). A first-class shepard plugin
  (`shepard-plugin-unhide`) exposes
  `GET /v2/unhide/feed.jsonld` — a schema.org + metadata4ing
  JSON-LD feed the HKG / Unhide harvester pulls daily. Every
  Collection on the instance becomes a `schema:Dataset` +
  `m4i:Dataset` entry with creator (firstName/lastName/displayName
  + ORCID `@id`), dates, and a back-link to shepard. Each entry
  also carries **machine-readable provenance** (UH1b) — a
  `m4i:hasProcessingStep` array inlining the most-recent
  metadata4ing `ProcessingStep` nodes targeting the Collection,
  rendered via the same PROV1h pipeline that powers
  `/v2/provenance/*` — and a **KIP citation** (UH1c) when the
  Collection has been published via KIP1a, surfacing the PID
  under `schema:identifier` + a public resolver URL under
  `schema:url` so a harvester can chain straight into the
  `.well-known/kip` record. Admin-configurable at runtime — flip
  the master toggle, the feed-public flag, or rotate the harvest
  API key via `shepard-admin unhide ...` without restarting.
  Default off so a fresh install never accidentally publishes;
  an operator opts in once their `contactEmail` and harvest key
  are in place. Refinements (per-Collection toggle UI, SHACL
  diagnostic) queued as UH1d–UH1e.

## How you actually use it

Five typical entry points:

1. **The web UI.** Browse, search, edit. Add lab-journal entries.
   Configure your profile (post-U1c). Spin up a process run
   (post-PR1b). The **API Docs** link in the nav bar (or the button
   on the landing page) opens the live Swagger UI at
   `/shepard/api/q/swagger-ui` — useful for trying endpoints
   interactively without leaving the browser.
2. **The Python client.** `pip install shepard-client`. Open a
   notebook, walk a Collection, run analysis, write results back.
   The `examples/seed-showcase/notebooks/` are the canonical
   pattern.
3. **`h5pyd` against shepard's HSDS sidecar** *(post-A5)*. Existing
   `h5py.File(...)` analysis code keeps working — just point at
   shepard's HSDS endpoint.
4. **shepard-timeseries-collector.** Configure once; it pushes
   OPC/UA / MQTT / KUKA RSI telemetry into shepard timeseries
   containers continuously. See `aidocs/40 §3` for the improvement
   roadmap.
5. **shepard-process-wizard, transitioning to in-shepard processes**
   *(post-PR1b)*. Model a campaign as a sequence of templated steps;
   step through it; every step's output is captured automatically.

## What shepard is not

- **Not a PLM.** No CAD revision graph, no BOM management, no
  approval workflows beyond the templates feature's required-fields
  mechanism. Aras / Teamcenter do PLM; shepard talks to PLM via
  references.
- **Not a HPC scheduler.** No job queue, no compute. Run your
  analysis where you already do; attach the result to shepard.
- **Not a code repository.** Code lives in git; shepard tracks the
  link to the right commit (post-G1). Don't paste source files into
  StructuredData blobs.
- **Not a permissions-light publishing platform.** Every entity has
  ACLs; if you want anonymous global reads, you set a Collection
  PUBLIC explicitly.
- **Not a real-time / OLTP system.** shepard reads happily; bulk
  writes have caps (`aidocs/29` P10 design quotes them). It's a
  research-data store, not a trading system.

## Where it's going

The four things on the near horizon, in priority order:

1. **Snap dashboards — chat-driven analysis** (`aidocs/43 §5.8`,
   AI1e). The killer feature. Open the chat, ask "show me vibration
   vs RPM for the last 7 fired runs and flag any outliers," get an
   inline publication-quality chart in seconds. Powered by the
   user's own LLM key (BYOK) or an admin-configured fallback —
   shepard ships zero models, only the plumbing.
2. **HDF5 / HSDS as a payload kind** (`aidocs/35`, A5 series).
   `h5pyd` parity for existing analysis code; gated on L2c.
3. **Templates + processes** (`aidocs/39` + `aidocs/40 §2`, T1 +
   PR1 series). Standardise how DataObjects get created; bring
   process design + runtime into shepard core.
4. **User profile + ORCID** (`aidocs/36`, U1 series). ~~Closes #29~~
   **U1a shipped** — ORCID field live with ISO 7064 checksum; ProfilePane
   edit dialog in-flight. RO-Crate exports now cite authors when the
   field is set. `displayName` override (U1b) in review.

Mid-horizon:

- **Snapshots** (`aidocs/41`, V2 series). Reproducible reads, deep
  exports, lineage diff.
- **Git artifact tracking** (`aidocs/38`, G1 series). Commit-SHA
  provenance in RO-Crate exports.
- **Lab journal + Jupyter** (`aidocs/37`, J1 series). **J1a shipped** —
  `GET /v2/lab-journal/{appId}/render` delivers sanitised CommonMark + GFM
  HTML. **J1b shipped** — `GET /v2/lab-journal/{dataObjectAppId}/notebooks`
  lists `.ipynb` file refs for inline render. **J1d shipped** — append-only
  edit history: `GET /v2/lab-journal/{entryAppId}/history` returns all prior
  versions of a note so researchers can recover earlier drafts. Queued next:
  "Open in Jupyter" deep link (J1c).
- **Unified search + pagination** (`aidocs/13`, P-series).
- **Provenance / lineage** (`aidocs/30`). OpenLineage-shape events
  across the pipeline.
- **Other AI features** (`aidocs/43`). **TA1a shipped** — timeseries
  annotations (interval + point) with `aiGenerated` flag. **AI1b
  shipped** — rolling-median MAD anomaly detector at
  `POST /v2/timeseries-references/{refAppId}/detect-anomalies`:
  pure Java, no ML library, LLM-independent; configurable rolling
  window (default 51) and Z-score threshold k (default 6.0);
  optional `createAnnotations=true` persists one `TimeseriesAnnotation`
  per contiguous anomaly run. Queued: semantic-annotation
  suggestion; lab-journal authoring assist; auto-summarisation;
  natural-language search. All AI inference BYOK / admin-fallback
  gated.
- **S3-compatible file storage** (`aidocs/45`, FS series). The
  **`FileStorage` SPI seam is shipped** (FS1a): an in-tree interface
  + the in-core GridFS default adapter, with `shepard.storage.provider`
  as the deploy-time switch. The S3-compatible adapter
  (`shepard-plugin-file-s3`, FS1b) drops into `plugins/storage-s3/`
  next — any S3-compatible endpoint (AWS S3, Cloudflare R2,
  Backblaze B2, Wasabi, Garage, SeaweedFS, Ceph RGW, MinIO).
  Presigned URLs (FS1c) unblock direct large-file uploads +
  downloads + RO-Crate ZIP delivery; closes the long-standing
  issue #27. GridFS stays a first-class supported backend
  indefinitely per the user direction (2026-05-12) — not a
  deprecation path.
- **Internal semantic repository — pre-seeded ontologies + UX**
  (`aidocs/48`, N1 series). The internal repo itself is **shipped**
  (N1a): the neosemantics plugin runs inside shepard's existing
  Neo4j, with `SemanticRepositoryType.INTERNAL` as the new
  connector type. Pre-seeded common ontologies (PROV-O / Dublin
  Core / schema.org / FOAF / QUDT / OM-2 / W3C Time / GeoSPARQL)
  are also **shipped** (N1b) — eight SHA-256-pinned Turtle bundles
  imported via `n10s.rdf.import.inline` on startup; see "what's in
  the box" above. What's still ahead: a `shepard-admin semantic
  refresh-ontologies` CLI (N1c) to swap the bundled stubs for the
  full canonical Turtle; LUMEN seed integration with real ontology
  IRIs (N1d); annotation-picker auto-complete (N1e). Closes the
  casual-user "I shouldn't need to deploy a SPARQL endpoint to
  annotate `g rms`" friction.
- **Storage-backend plugin SPI + dev-experience** (`aidocs/47`,
  PL1 + DX series). New payload kinds drop in as plugins instead
  of 12-file PR sprawls; existing storage-bound feature flags
  (spatial, hdf, files-s3) migrate to plugins; codegen archetype
  + `make dev` + unified test-resource make the casual + power
  user paths both faster. **Foundation shipped** as PM1a: the
  `PluginManifest` SPI (`de.dlr.shepard.plugin.PluginManifest`)
  + drop-in JAR discovery (`backend/plugins/*.jar` walked at
  startup via `ServiceLoader`) + per-plugin `shepard.plugins.<id>.enabled`
  toggle. UH1a is the first plugin under the new shape; HDF5
  (PL1c), Git references (PL1d), FS GridFS+S3 split (FS1), ePIC
  / DataCite minters (KIP1c/d), DBpedia rich-reference (REF1a),
  video (VID1a), AAS submodels (AAS1) all queue behind it
  following the same drop-in JAR pattern per ADR-0023.
- **In-app user docs** (`aidocs/49`, D1 series). The Nuxt UI grows
  a `/help` route serving the same `docs/*.md` content as the
  Pages site, with screenshots auto-captured by Playwright against
  a locally-booted compose stack. Casual users get task-shaped help
  (upload-data / share-collection / export-rocrate / process-step)
  without leaving the app.
- **Experiment orchestration** (`aidocs/50`, EXP1 series). A new
  `shepard-experiment-coordinator` service drives manufacturing-
  style experiments end-to-end (PLC / SPS / KUKA robot / OPC/UA /
  KUKA RSI), auto-materialises the shepard graph, and supports
  pre-seed / JIT / post-process timing strategies plus
  checkpoint-based restart. Builds on T1 templates + PR1 processes
  + sTC telemetry + V2 snapshots.
- **Instance-Admin role** (`aidocs/51`, A0 + C3 + F8 — backend
  shipped). Single role tier (`instance-admin`); dual-source role
  check (IdP claim OR Neo4j `:HAS_ROLE` edge); bootstrap-token
  mechanism for first-admin; `/v2/admin/...` REST surface.
- **Instance-admin activity dashboard** (`aidocs/55`, PROV1e —
  per-Collection casual-user dashboard already shipped under PROV1d
  in §"What's in the box"). All-instance sparkline + dormant-
  Collections panel + top-active-Collections leaderboard at
  `/admin/dashboard`. Gated on A0's instance-admin role.
- **Video as a first-class payload** (`aidocs/53`, VID1 series).
  **VID1a shipped**: `VideoStreamReference` entity with multipart
  upload (`POST /v2/data-objects/{appId}/video-stream-references`),
  ffprobe metadata extraction (`durationSeconds`, `width`, `height`,
  `frameRate`, `videoCodec`, `audioCodec`), and `wallClockTimestamp`
  as the TM1 temporal anchor. Still ahead: HLS segmentation, live
  ingest via `shepard-video-collector` / MediaMTX sidecar,
  video-time + wall-clock navigation, frame extraction (VID1d).
- **`FileReference` → `FileBundleReference` + `FileGroup` rename**
  (`aidocs/53`, FR1 series — **FR1a + FR1b shipped**. FR1a: V21
  migration adds the second label + default group; `/v2/bundles/{appId}`
  shelf surfaces the new shape; upstream wire stays frozen. FR1b:
  singleton `FileReference` re-introduced with the
  `:SingletonFileReference` label and shared `_shepard_files`
  Mongo namespace; new `/v2/files/{appId}/{,content}` shelf with
  HTTP-range support; V23 opt-in carve-out migration gated by
  `shepard.migration.split-singletons.enabled`).
  FR1c/FR1d cover snapshot + RO-Crate.
- **Templates as a first-class admin entity** (`aidocs/54`, T1
  revised). `:ShepardTemplate` Neo4j entity in an admin-only
  subgraph (replaces the `__templates` hack); JSON DSL bodies,
  copy-on-write versioning, admin-gated CRUD at `/v2/templates`.
- **v2 API simplification + output profiles + MCP-friendly
  OpenAPI** (`aidocs/56`, V2S1 series). Flat
  `/v2/dataobjects/{appId}` single-entity paths;
  `?profile=metadata|relations|all` projections;
  `x-mcp-tool-name` + `x-mcp-side-effects` extensions so a later
  MCP-server can be generated from the spec.
- **AAS backend integration** (`aidocs/52`, AAS1 series). Explore
  whether shepard can act as an Asset Administration Shell
  (Plattform Industrie 4.0) repository backend; v1 = adapter shim
  at `/v2/aas/...`; conformance targets IDTA Nameplate +
  TechnicalData + TimeSeriesData first.
- **UI + graph ergonomics cluster** (`aidocs/58`, UI1 / UI2 / UI3
  / CP1 / ONT1 / REF1 / GR1 / BIZ1 series). Lefthand-tree
  drag-and-drop; navigable Collection graph view (cytoscape.js);
  `@`-mention autocomplete; `:CollectionProperties` properties-
  node (CP1a/CP1b shipped); RO ontology added to the pre-seed
  bundle (ONT1a shipped); metadata4ing (NFDI4Ing) added to the
  pre-seed bundle as the engineering-research extension of PROV-O
  (ONT1b shipped — LUMEN seed citation queued under ONT1c, frontend
  ontology-picker queued under ONT1d); DBpedia Databus rich-reference
  plugin; **GraphRAG** on shepard via native Neo4j 5.13+ vector index.
- **OpenAPI client-generator pick** (`aidocs/57`, CG1 series).
  Kiota for the `/v2/` shelf, OpenAPI Generator retained for the
  byte-frozen `/shepard/api/...` shelf; Hey API as a TS-only
  tactical secondary for the Nuxt frontend.

Long-horizon (deliberately deferred):

- Federation across shepard instances (`aidocs/16` X3, parked).
- Live-kernel notebooks (`aidocs/37` J1f, parked).
- shepard as a git host (`aidocs/38 §10`, explicit non-goal).

## Why this fork exists

`gitlab.com/dlr-shepard/shepard` is the upstream. `noheton/shepard`
is a development fork that picks up the backlog faster: ID-migration
phasing (L2 series), bigger search/permissions investments,
HDF5/HSDS integration, the upgrade-tracker discipline. Per
`CLAUDE.md` the upstream API surface stays byte-frozen; everything
this fork adds lives at `/v2/` so an admin upgrading from upstream
has a low-friction path (see `aidocs/34`).

## Where to go next

- **Operator** ("how do I run it?") → `docs/admin.md`,
  `docs/deploy.md`.
- **API user** ("how do I write a client?") → the OpenAPI at
  `<host>/shepard/doc/openapi.json`; generated clients on
  `gitlab.com/groups/dlr-shepard/-/packages`.
- **Showcase walk** ("show me a real example") →
  `examples/seed-showcase/` + `examples/seed-showcase/notebooks/anomaly-analysis.ipynb`.
- **Per-feature design** → the `aidocs/00-index.md` table of
  contents; each row's link is a self-contained design note.
- **What's coming** → `aidocs/16-dispatcher-backlog.md` (the live
  backlog) and the §"Where it's going" section above (kept current).

## How this doc stays honest

The standing rule in `CLAUDE.md` makes this doc's update part of
any PR that changes user-visible behaviour. The §"Where it's going"
section is the most-likely-to-rot section — every time a feature
moves from "queued" to "shipped," the corresponding bullet moves
from "near horizon" to "what's in the box (today)." A reviewer who
sees a feature-shipping PR without a corresponding `aidocs/42`
update should reject the PR. That's the only way the doc stays
useful.

For richer per-feature detail, follow the links above into the
design corpus.
