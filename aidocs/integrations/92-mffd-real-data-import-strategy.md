---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 92 — MFFD real-data import strategy

**Status:** design draft 2026-05-21 · superseded by an actual `import-mffd.py`
once it lands · pairs with `aidocs/integrations/82-confluence-import.md`
for the Confluence layer.

## 0. Why this doc exists

The synthetic `examples/mffd-showcase/seed.py` is a stand-in. The real
MFFD data has just been dropped into
`examples/mffd-showcase/raw-data/mffd-data/` (7.2 GB compressed →
**10.9 GB / 256 k files** uncompressed; **NEVER enters the repo** —
`examples/mffd-showcase/.gitignore` blocks `raw-data/`,
`raw-data/*`, and `raw-data/**`).

The drop is **already in shepard's export shape** — it was produced by
running the existing exporter against the live MFFD shepard at
`backend.bt-au-cube3.intra.dlr.de/shepard/api/` on **2026-05-20** with
`exporter_version: 1.2`, `skip_lab_journals: true`. So the import is a
**replay of an export**, not a green-field schema design.

That makes the strategy short on creativity and long on reliability.

## 1. What's in the drop

### 1.1 Top-level layout

| Subtree | Size | What it is |
|---|---|---|
| `mffd-tapelaying/` | 5.9 GB | L-AFP tape-laying process (the big one — Track-level granularity) |
| `mffd-framewelding/` | 1.7 GB | Frame welding line — Frame → AF → ProcessData hierarchy |
| `mffd-confluence-space-export/` | 527 MB | 116 Confluence pages + 980 attachments — runbooks, lab notes |
| `cell/` | 12 MB | One `MFZ.rdk` (RoboDK workcell model) + 4 `.url` shortcuts |

Per-process bucket, the layout is uniform:

```
<process>/
  data-objects/                # do-<old-id>.json metadata + .done sentinel
  references/                  # ref-payload sidecars + payload subdirs
    file-<id>.json + file-<id>/<oid>      # FileReferences
    sd-<id>.json   + sd-<id>/<oid>.json   # StructuredDataReferences
    ts-<id>.csv    + ts-<id>.json         # TimeseriesReferences (see 1.4)
  annotations.json             # collection-level semantic annotations
  lab-journal/                 # empty (skip_lab_journals: true)
  manifest.json                # only in framewelding — exporter manifest
```

### 1.2 Tapelaying (the big one)

**5012 `do-*.json` metadata files** (4090 successfully parsed in the
spot-check; the rest are zero-byte stubs the exporter laid down but
never filled). Hierarchy follows the ProcessOntology:

| ProcessOntology IRI | Count | Role |
|---|---|---|
| `…#PlyGroup` | 28 | Top-level layup units |
| `…#Ply` | 77 | Plies inside a PlyGroup |
| `…#Track` | 3985 | Individual AFP tape-passes inside a Ply |

Each Track DataObject has a `predecessorIds: [prev-track-id]` link
producing a chronological tape-laying chain inside each Ply. **107
distinct parents** — i.e. ~107 Ply-or-PlyGroup containers carrying the
3985 Tracks.

Reference counts:
- **23 328 file references** with real payloads (~5.5 GB) — PNGs of the
  tape-head, INI parameter dumps, ASCII process logs
- **4627 timeseries references** → see §1.4: payload IS empty
- **0 structured-data references**

### 1.3 Framewelding

**3371 DataObjects** in a 4-level hierarchy:

```
FrameWelding (root)
└── Frame N (24 frames)
    └── AF_n (Anchor Feature, ~22 per frame)
        └── Process Data n (1..7 — the welding steps)
```

References:
- **984 file references** (PNG/INI/log — same shape as tapelaying)
- **3371 structured-data references** carrying per-step process
  parameters as MongoDB BSON documents (`processParameterNumber`,
  `BridgePosition`, `Frame`, etc.)
- **0 timeseries**

The `manifest.json` (374 KB) is the exporter's record: collection
`MFFD_FinalDemonstrator`, source URL above, and the full DataObject id
tree.

### 1.4 Timeseries: ⚠️ all stubs

All 4627 tapelaying TS files are **0 bytes** — `.csv` empty, `.json`
empty. The exporter wrote the sentinel marker but the actual TS payload
never made it out. (Likely cause: exporter v1.2 ran with TS export
disabled or it timed out on InfluxDB.) Whatever script eventually walks
this drop must surface this as a hard-stop **"TS payload absent"**
finding rather than silently create empty TimeseriesContainers — the
worst outcome is an MFFD operator finding empty containers in shepard
and not knowing whether the data is missing or never existed.

### 1.5 Semantic annotations

Per-DataObject `_annotations[]` arrays carrying `propertyIRI` /
`valueIRI` from the **DLR Process Ontology**:
`http://semantics.dlr.de/ProcessOntology#`. Three IRIs dominate the
tapelaying corpus (`#Track`, `#Ply`, `#PlyGroup`); framewelding's
collection-level annotation is the schema.org `ResearchProject` tag.

A separate `propertyRepositoryId` / `valueRepositoryId` field references
the source semantic repository's numeric id from the source instance —
**these are export-side, will be remapped on import** (see §3.2).

### 1.6 Confluence export

Pre-analysed by the drop's own `analyze-confluence-export.py`. 116
HTML pages, 980 attachments, 33 pages with data tables (table-→
StructuredData opt-in), 217 internal page links, 165 external HTTP
links. Recipe and decision matrix already documented in
`aidocs/integrations/82-confluence-import.md`. This part of the import
runs separately.

### 1.7 Cell model

`cell/MFZ.rdk` (12 MB) is a RoboDK workcell model. Out of scope for the
first import — needs the SP1 spatial-data plugin and a RoboDK plugin
(neither exists today). Keep the file at rest in `raw-data/` and call
it out in the import report.

## 2. Mapping table

| Drop kind | Count | Bytes | shepard primitive | Plugin / endpoint | Notes |
|---|---|---|---|---|---|
| DataObject (export) | 8 383 | ~40 MB JSON | `DataObject` | `POST /shepard/api/.../dataObjects` | Direct; preserves parent chain after id-remap |
| FileBundleReference + payloads | 24 312 files (~7.6 GB) | n/a | `FileContainer` + `FileBundleReference` | upload via presigned PUT (P23) or legacy `/files/{oid}` | Payloads are blobs without extensions — use `file -b` to guess MIME, store as-is |
| StructuredDataReference | 3 371 | small | `StructuredDataContainer` + `StructuredDataReference` | `POST /shepard/api/.../structuredDataReferences` | Embedded MongoDB BSON dialect (`_id.$oid`, `$date`) — strip on insert |
| TimeseriesReference (stubs) | 4 627 | 0 | `TimeseriesContainer` + `TimeseriesReference` | n/a (deferred) | Do NOT create empty TS — emit "missing-payload" report instead |
| SemanticAnnotation | ~12 k IRIs | small | `:SemanticAnnotation` (N1c) | `POST /v2/semantic/annotations` | Resolve `propertyIRI`/`valueIRI` against the ProcessOntology repo present on the target instance; create the repo first if missing |
| Confluence DataObject + LabJournalEntry | 112 | 527 MB total | `DataObject` + `LabJournalEntry` (J1) | confluence-importer | See aidocs/82 |
| Confluence attachment | 980 | included above | `FileBundleReference` | confluence-importer | See aidocs/82 |
| Confluence external link | 165 | n/a | `URIReference` | confluence-importer | See aidocs/82 |
| RoboDK workcell (.rdk) | 1 | 12 MB | `SpatialDataContainer` (planned SP1) | n/a today | Deferred — keep file at rest; document path |
| LabJournalEntry | 0 | 0 | `LabJournalEntry` (J1) | n/a | Exporter ran `skip_lab_journals: true` |

## 3. Strategy

### 3.1 The replay tool

Build a **one-off import script** —
`examples/mffd-showcase/import-mffd.py` — first. Resist the urge to
make this `shepard-plugin-importer` v1; that plugin (project memory:
`project_importer_plugin.md`) is the wider abstraction this drop will
inform, not the abstraction this drop blocks on.

The script's contract:

- **Inputs:** a `--source ./raw-data/mffd-data/<process>` directory and a
  target shepard URL + Bearer.
- **Idempotency:** writes a sidecar
  `./raw-data/mffd-data/<process>/.import-map.json`
  recording `{old-export-id → new-appId}` and a content-hash per file
  oid. Re-runs skip already-imported items.
- **Phases:** one CLI flag per phase (§3.4), each runnable independently.
  Default = run all in order.
- **Dry-run:** every phase supports `--dry-run` — emits what it would
  POST without doing so. Use this against the IMP1 plan-seal endpoint
  (`POST /v2/import/validate`) once IMP1 supports manifests of this shape.
- **Concurrency:** the file-upload phase parallelises (workers tunable,
  default 4 — matching the exporter's `workers: 4`). Other phases run
  serial because they're parent-chain-dependent.

### 3.2 Pre-flight (Phase 0, no shepard mutations)

Before any POST, walk the drop and produce a report:

- DataObject count per process, depth histogram, parent_id resolution
  (every non-null `parentId` must resolve to a DataObject in the drop)
- Reference count per kind per DataObject
- Total payload bytes per kind
- Orphans: references whose payload is missing on disk; payloads with
  no metadata sidecar
- TS stubs: explicit count of empty `ts-*.csv` files
- Annotation IRI catalog: union of all `propertyIRI` + `valueIRI`,
  grouped by repository
- Semantic repository gap: for each IRI, check whether the target
  shepard's semantic repos contain it; emit a "missing-IRI" list
- The whole thing as a markdown report + a JSON manifest the script
  consumes in later phases

Without this report, the import is flying blind.

### 3.3 Id remapping

The export uses **integer ids** (`163814`, `123746`, …) from the source
shepard's Neo4j OGM. Our target uses the L2c **appId (UUID v7)** scheme.

Strategy:
- For each DataObject created on target, store
  `{export_id: <int>, appId: <uuid-v7>}` in the import map
- Re-write `parentId` and `predecessorIds` from int → uuid via lookup
- Same for `propertyRepositoryId` / `valueRepositoryId` → resolve to
  the target instance's semantic repo appIds (or fail with "missing
  repo" message)

### 3.4 Phase order

| Phase | Step | Why this order |
|---|---|---|
| **0** | Pre-flight report (§3.2) | Catch broken-export issues before mutating anything |
| **1** | Create Collection + DataObjects (topologically) | Parent must exist before child |
| **2** | Upload file payloads | Largest chunk by bytes (~7.6 GB) — parallelise but isolate so retry is cheap |
| **3** | Insert structured-data payloads (~3.4k small JSON) | Fast; could be co-scheduled with Phase 2 |
| **4** | Attach semantic annotations | Needs DataObject appIds (Phase 1) + repo IRIs present |
| **5** | Create timeseries stubs (defer or skip) | See §3.6 |
| **6** | Confluence pages + attachments | Standalone — see aidocs/82 |
| **7** | Run the publish-readiness / FAIR check | Quality gate before announcing |

### 3.5 Idempotency rules

- Skip a DataObject if `export_id` already in import map
- Skip a FileBundleReference if its `name` + `fileOids` set already
  matches one on the target DataObject
- For StructuredDataReferences, dedupe by the embedded
  `_meta.name` + `processParameterNumber`
- Annotations dedupe by `(dataObjectAppId, propertyIRI, valueIRI)`

The map sidecar is the source of truth for resumability — if a run is
killed mid-Phase 2, restarting it picks up where it stopped.

### 3.6 Timeseries: stubs are not data

**Recommendation: do NOT create TimeseriesContainers for the 4627 empty
stubs.** The fork's `:DataObject` model tracks ts ref counts; an empty
TS container is indistinguishable from "TS data was uploaded then
zeroed" via the API.

Instead:
- Phase 0 report flags this explicitly: `4 627 TS references present
  in export, 0 bytes of payload — re-export from source with TS
  enabled, OR ingest from upstream InfluxDB directly`
- Track the gap as a follow-up MFFD-INFLUX-EXPORT item
- The remaining 99 % of the data is independently usable

If the operator chooses to "import the stubs anyway" the script
supports `--include-empty-timeseries` and creates clearly-named
containers (`<channel-name> (no payload — pending re-export)`).

### 3.7 What this drop teaches us about `shepard-plugin-importer`

The wider project memory (`project_importer_plugin.md`) is for a
library of importers. This MFFD drop is the **first acid test**: if
the one-off `import-mffd.py` lands cleanly, lift its core into the
plugin as `replay-shepard-export` (the export shape is generic).
Specific importers (`afp-csv`, `confluence-html`, `robodk-rdk`) become
siblings.

Order:
1. Ship `import-mffd.py` as a script (this doc)
2. Generalise to `shepard-plugin-importer.replay-shepard-export` once
   we've replayed this drop and a second export successfully
3. The script's `.import-map.json` becomes the plugin's per-source
   resumable state

## 4. Decisions

### 4.1 Locked (2026-05-21)

- **Target Collection name:** `MFFD-Augsburg-2026`. Distinguishes this
  fork's instance from the source's `MFFD_FinalDemonstrator`, encodes
  the date the data landed, easier to grep in logs.
- **Timeseries handling:** **skip entirely.** Drop is incomplete —
  4 627 TS references with 0 bytes of payload. Do NOT create empty
  TimeseriesContainers; emit them in the Phase 0 report as a known
  gap. If a complete TS export arrives later, run that as a separate
  import pass.

### 4.2 Still open — pending more evidence

These are intentionally undecided. §5 lists the structural facts to
gather before committing.

- **Collection shape — one vs two.** Tapelaying + framewelding as one
  Collection with two top-level DataObjects, or two sibling
  Collections? Will be decided after we measure cross-process linkage
  in §5.1 (if Track-↔-Frame relationships exist, one Collection wins).
- **Confluence wiki — sibling vs co-located.** A wiki sibling
  Collection or pages mixed in with process data? Will be decided
  after §5.2 (sampling the wiki page content for runbook-vs-run
  character).
- **Annotation repository setup.** Does the target shepard already
  carry the DLR ProcessOntology IRIs in a `:SemanticRepository`? If
  not, seed it once (V49 already bootstraps an internal semantic repo;
  we add ProcessOntology entries to it).
- **Owner / created-by.** All DataObjects in the drop have no
  `createdBy`. Default plan: a dedicated `mffd-import-bot` user —
  keeps the provenance shape (PROV1a) clean. Revisit only if
  operators want per-engineer attribution preserved from somewhere
  else in the drop we haven't found yet.

## 5. Discovery findings (2026-05-21)

All passes are read-only walks of `raw-data/mffd-data/` — none touched
shepard.

### 5.1 Cross-process linkage (Track ↔ Frame): **NONE**

Probed: union of all `parentId` + `predecessorIds` across both
subtrees, checked for cross-references.

- tapelaying → framewelding: **0 cross-links** (parent or predecessor)
- framewelding → tapelaying: **0 cross-links**
- tapelaying orphan parent_ids: **2** (`96801`, `123741`) — point at
  the zero-byte ghosts (§5.3) and a top-level "MFFD" node that wasn't
  exported
- framewelding has **0 dangling parent/predecessor IDs** — clean tree

**Conclusion:** the source treated tapelaying and framewelding as two
disjoint trees. shepard parent/predecessor links would NOT bridge a
Collection split here even if we kept them as siblings — there's no
provenance edge to preserve. **Decision between one vs two Collections
is now purely UX**, not technical.

### 5.2 Confluence wiki character: **runbook-shaped, not per-run**

Sampled all 116 pages. The 116 page titles cover:

- **Hardware reference**: `TPS (TapeProfileSensor)`, `Schweißbrücke -
  Heizpatronen`, `Powerunit humm3`, `cUS Kopf`, `Laserline LDM 6000`,
  `Roboter`, `Schaltschrank …`, `Cleat Magazin`
- **Process howtos**: `L-AFP`, `Resistance Welding`, `Ultrasonic
  Welding`, `Howto`, `Troubleshooting`, `Lifehacks`,
  `Kalibrierroutine`, `Kommunikation`
- **Project-level documentation**: `MFFD Startseite`, `Project Plan`,
  `Übersicht Deliverables MFFD`, `Plan for the test shell manufacture`,
  `Lessons Learned`, `Studentische Arbeiten`
- **A handful per-run-ish** (~15 %): `Steering - Erste Versuche
  KW45/46` (calendar weeks), `Materialinfo und Entnahme - Batch
  226368` (specific batch), `Ergebnisse Plattenfertigung 11/2020`,
  consolidated `Test Log` pages per process

**Conclusion:** sibling **MFFD-Wiki** Collection is the natural shape.
This material explains the *line* and the *process*, not the daily
runs. The handful of per-run-ish pages can carry URIReferences back
into the process Collection's DataObjects if cross-citation matters.

### 5.3 Other discovery items

#### Engineer attribution in the process data: **absent**

Every `updatedBy` field (4 783 occurrences across both process
subtrees) is `null`. No `createdBy`, `operator`, `engineer`,
`responsible`, or `author` fields anywhere in the data-object or
reference sidecars.

**Decision:** keep the planned `mffd-import-bot` service-account
attribution — there's no upstream attribution to preserve.

#### Engineer attribution in the wiki: **rich**

Confluence pages carry `<span class='author'>Name</span>` markup.
**111 of 112 pages have a creator**, **26 have a last-editor**:

| Author | Created | Last-edited |
|---|---|---|
| Mayer, Monika | 40 | 6 |
| Vistein, Michael | 19 | 2 |
| Unknown User (dede_di) | 26 | 3 |
| Unknown User (fisc_fe) | 14 | 3 |
| Endraß, Manuel | 2 | 4 |
| (others) | 10 | 8 |

**Decision:** the confluence-importer (separate from the process-data
importer) should map these into the LabJournalEntry's `createdBy` /
`updatedBy` fields verbatim. The "Unknown User (…)" entries are
Keycloak username fragments that survived the Confluence migration —
preserve them as opaque strings.

#### Date span of the data: **April – May 2023**

Scanned ~800 framewelding structured-data documents for embedded
MongoDB `$date` stamps:

- earliest: `2023-04-14T13:18:29Z`
- median:   `2023-04-14T13:19:09Z`
- latest:   `2023-05-16T07:55:38Z`

So the source exporter ran on 2026-05-20, but the actual MFFD process
data is from the **2023-04 → 2023-05** campaign (about one month).
Tapelaying structured-data didn't yield dates (no SD documents — only
ts stubs + file payloads), but file modification times in the
`references/file-*/` payload subdirs presumably bracket the same span.

#### Zero-byte DataObjects in tapelaying: **922 ghosts**

Confirmed: 922 of 5 012 `do-*.json` files in `mffd-tapelaying/data-objects/`
are exactly 0 bytes. Pattern:

- ALL 922 zero-byte files have a matching `.done` sentinel (50/50 in
  the sample) — the exporter "completed" them but wrote no content.
- **0 of the 922 ids are referenced as parent or predecessor by any
  populated DataObject.** They are dangling exporter stubs, not
  orphaned DataObjects that broke a chain.
- Cause: most likely the exporter pre-allocated filenames from an old
  id list and then skipped a subset (e.g. soft-deleted at source,
  filtered by an unstated rule).

**Decision:** treat the 922 as a non-issue. Phase 0 report flags them
("922 do-*.json stubs in tapelaying — no content, no inbound refs;
treating as no-op"). The import proceeds on the populated 4 090
DataObjects.

#### Missing tapelaying `manifest.json`: **expected, mild concern**

`mffd-framewelding/manifest.json` is present and lists 3 371
DataObjects matching the directory count. `mffd-tapelaying/` has no
`manifest.json`.

Without the manifest we lose the source's authoritative count and
exporter options (e.g. whether `skip_lab_journals: true` applied —
likely yes; whether timeseries export was attempted — yes, since the
`ts-*` stubs exist; concurrency / dry-run state).

**Decision:** synthesize a `manifest.json`-equivalent at the start of
Phase 0 by walking the `data-objects/` directory ourselves. The
exporter options we'd need are inferable (lab journals empty →
`skip_lab_journals: true`; ts files all 0 bytes → effectively
`skip_timeseries: true`). Phase 0 report calls this out so an
operator can decide whether to chase the source for the real manifest.

## 6. Decided shape (2026-05-21)

**One Collection — `MFFD-Augsburg-2026` — with the upper-fuselage
process chain as 7 top-level DataObjects, connected via
`predecessorIds` from step 1 → 7.** The digital thread is the
organising metaphor. Steps that have data carry it now; placeholder
steps wait for data to follow.

```
MFFD-Augsburg-2026 (Collection)
│
├── 1  Tape Layup           — Tapeablage / L-AFP / T-AFP
│       └── status: DATA PRESENT (file refs + empty TS stubs)
│           └── grafts the existing tapelaying export tree:
│               PlyGroup (28) → Ply (77) → Track (3 985)
│
├── 2  Skin Inspection      — Prüfung Haut / Thermographie
│       └── status: PLACEHOLDER (Chris's data may follow)
│           ──> predecessor = step 1
│
├── 3  Stringer Welding     — Ultrasonic Welding (US)
│       └── status: PLACEHOLDER  ──> predecessor = step 2
│
├── 4  Spot Welding         — Punktschweißen (clips / flanges → frames)
│       └── status: PLACEHOLDER  ──> predecessor = step 3
│           (note: some clips already integrated, some not — research)
│
├── 5  Bridge Welding       — Brückeschweißen / Resistance Welding
│       └── status: DATA PRESENT (file refs + structured-data docs)
│           └── grafts the existing framewelding export tree:
│               FrameWelding → Frame (24) → AF (~22) → ProcessData (1..7)
│           ──> predecessor = step 4
│
├── 6  Stringer Connection  — Stringerverbindung (quarter frame ↔ quarter frame)
│       └── status: PLACEHOLDER  ──> predecessor = step 5
│
└── 7  Cleats with LBR      — LBR (KUKA LeichtBauRoboter) integration of cleats
        └── status: PLACEHOLDER  ──> predecessor = step 6
```

Two of seven steps carry real data today (1 and 5). The other five
are placeholders — created with the same shape as data-bearing steps
so a later drop slots in without restructuring.

### 6.1 Where the existing export data attaches

Source trees graft onto the matching step:

- `mffd-tapelaying/data-objects/` → under step **1 Tape Layup**, with
  the existing 28 PlyGroups becoming children of step 1, then their
  Ply and Track descendants unchanged.
- `mffd-framewelding/data-objects/` → under step **5 Bridge Welding**,
  with the existing `FrameWelding` DataObject becoming a child of
  step 5, then the Frame/AF/ProcessData hierarchy unchanged.

The 922 zero-byte tapelaying ghosts (§5.3) stay skipped — they're
no-ops.

Empty TS stubs are still ignored (§4.1 decision holds).

### 6.2 Process technique map (informs the wiki keyword mapping)

The seven steps don't map 1:1 to one welding technique — there are
two overlapping families, plus AFP layup and NDT:

| # | Step | Technique(s) |
|---|---|---|
| 1 | Tape Layup | AFP (L-AFP / T-AFP) |
| 2 | Skin Inspection | Thermography (NDT) |
| 3 | Stringer Welding | **Ultrasonic** (stringer ↔ skin) |
| 4 | Spot Welding | **Ultrasonic** too (clips / flanges → frames at spots) |
| 5 | Bridge Welding | **Resistance** (the welding-bridge apparatus) |
| 6 | Stringer Connection | **Resistance** (joining quarter-frames at stringers) |
| 7 | Cleats with LBR | LBR-driven (technique TBD; KUKA iiwa) |

So an "Ultrasonic Welding" wiki page is relevant to BOTH step 3 AND
step 4. A "Resistance Welding" wiki page is relevant to BOTH step 5
AND step 6. Pages get cross-attached as LJEs to every matching step
rather than forced into one.

### 6.3 Wiki integration

The Confluence wiki maps into the SAME Collection, attached **as
LabJournalEntries on the appropriate step DataObject(s)**. No
separate "Project Documentation" bucket — per user direction, every
page gets distributed across the 7 steps via context-based best-fit
matching, with cross-attachment when topical relevance is genuinely
broad.

The mapping pipeline:

1. **Title-keyword pass** (the table below) — catches the obvious
   matches.
2. **Body-content pass** — for pages the title doesn't classify
   confidently, parse the HTML body and re-run the keyword set over
   the page text. Pages mentioning "Schweißbrücke" / "humm3" in the
   body land on step 5; pages mentioning "Cleat" land on step 7;
   pages with strong AFP / layup vocabulary land on step 1.
3. **Author / context pass** — Mayer-Monika authored welding-heavy
   content; Vistein-Michael authored robotics-heavy content. Author
   carries a weak signal when title+body are ambiguous.
4. **Cross-technique attachment** — pages tagged "Ultrasonic" attach
   to BOTH steps 3 and 4; pages tagged "Resistance" attach to BOTH
   steps 5 and 6.
5. **Manual overrides** — `wiki_topic_map_overrides.json` ships with
   a hand-curated correction table that always wins.

Today's title-only pass yielded ~38 % coverage (44 of 116). After
body + author + cross-attach, expected coverage is ≥ 95 %; the
operator reviews the residual and either adds an override or accepts
a default best-fit. NO pages land outside the 7 steps.

A handful of genuinely-cross-cutting pages (e.g. `Project Plan`,
`Lessons Learned`, `Hardware overview`) attach to step 1 because
that's the chronologically-first step in the chain — the operator can
re-target via overrides.

### 6.4 Reimport timestamp caveat

The source export was itself produced from a re-imported instance —
the tapelaying `createdAt` timestamps are NOT reliable as a temporal
ordering signal. Phase 0's report mentions this so operators don't
build downstream queries that depend on it. Track order within a Ply
is preserved through `predecessorIds`, which IS authoritative.

### 6.3 Step names — English / German

All step DataObject `name`s use the English term; the German term goes
in `attributes.term_de` for searchability. Description carries the
one-line explanation from the doc (e.g. *"Cleats integration via the
KUKA LBR robot — joins the cleats to the frames after stringer
connection"*).

### 6.4 Ontology repository seeding

Unchanged from §5: the target shepard's bootstrap migration (V49) may
not contain `http://semantics.dlr.de/ProcessOntology#` IRIs. Phase 0
verifies; Phase 4 seeds whatever's missing.

## 7. Script design (planning only — not yet written)

Per the user's "plan the script first, hold on running it" decision.

### 7.1 File layout

```
examples/mffd-showcase/
  import-mffd.py            # the entry point (this design)
  import-mffd/
    __init__.py
    phases.py               # Phase 0..7 orchestrator
    shape.py                # the 7-step DataObject template + graft rules
    wiki_topic_map.py       # the keyword → step mapping table (§6.2)
    wiki_topic_map_overrides.json   # hand-curated; takes precedence
    api.py                  # thin shepard REST client (Bearer + retry)
    import_map.py           # idempotent old-int-id → new-appId sidecar
    report.py               # markdown + JSON Phase 0 / postrun reporters
    fixtures/
      step_descriptions_en.json     # the seven step blurbs (English)
      step_descriptions_de.json     # German equivalents
  README-import-mffd.md     # operator-facing how-to
```

`import-mffd.py` stays a script (not a plugin) per §3.7 — once it
proves the shape, it lifts into `shepard-plugin-importer`.

### 7.2 CLI

```
import-mffd.py
  --target https://shepard.nuclide.systems
  --token-file ~/.shepard-mcp-key.txt          # or --token / $SHEPARD_TOKEN
  --source ./raw-data/mffd-data                # the unzipped drop root
  --collection-name "MFFD-Augsburg-2026"       # default already in fixture
  --phase {0,1,2,3,4,5,6,7,all}                # default all
  --dry-run                                    # default off; default-on for --phase 0
  --resume                                     # use the import-map sidecar
  --wiki-overrides ./wiki_topic_map_overrides.json
  --verbose
```

### 7.3 Phase order (refines §3.4)

| # | Phase | Action |
|---|---|---|
| 0 | Pre-flight | Walk drop, validate parent/predecessor closure, count payloads per kind per step, list semantic-IRIs, sample-MIME the file payloads, write `report-phase0.md` + `phase0.json` — **no shepard writes** |
| 1 | Seed shape | Create `MFFD-Augsburg-2026` Collection + 7 step DataObjects with the predecessor chain + `attributes.term_de` |
| 2 | Graft existing data | For step 1: import the tapelaying tree under it. For step 5: import the framewelding tree under it. Topological parent-first; ID-remap as in §3.3 |
| 3 | File payloads | Upload all 24 312 file blobs against their parent FileBundleReferences (parallel, workers=4) |
| 4 | Structured data | Insert 3 371 BSON-flavoured JSON docs for step 5 |
| 5 | Semantic annotations | Resolve ProcessOntology IRIs against the target's semantic repo, seed missing entries, attach |
| 6 | Wiki | Run the confluence-importer on the 116 pages: step-mapped pages become LabJournalEntries on their step DataObject; project-wide pages become LJEs on the Collection (or a "Project Documentation" host DO) |
| 7 | Verify | Walk what we just imported, check counts match Phase 0's expectations, write `report-phase7.md` |

Phases 0 + 7 are diagnostic. Phases 1 + 2 must run serially. Phases
3, 4, 5 can be safely interleaved. Phase 6 (wiki) is the last
mutation and depends on the step DataObjects (Phase 1) existing.

### 7.4 Idempotency

- `<source>/.import-map.json` sidecar with `{old_export_id → new_appId}`
  + `{file_oid → uploaded_bool + sha256}` + `{step_id → new_appId}`
- Re-runs skip anything in the map, write a single `import-log.ndjson`
  per run for audit
- `--resume` makes resumption explicit; without it, default behaviour
  is also resume-safe (the map drives skipping)

### 7.5 Failure modes the design has to handle

- Bearer token expires mid-run (Phase 3 file upload, ~7.6 GB across
  thousands of POSTs) — refresh token, or accept a refresh-token in
  the CLI and re-mint when 401 appears
- A FileContainer hits the orphan-grace-period (storage management
  SM1) during the long Phase 3 — pre-create the container with a
  "no-reap" annotation per `project_storage_management.md`
- Semantic repo seed fails for an unknown IRI — emit a deferred-IRI
  list and continue; operator can re-seed later and re-run Phase 5
- The 7 multi-mapped wiki pages need a human call — Phase 6 stops
  with a prompt unless the overrides file resolves them all

## 8. Open question — Wiki host shape

The doc-design recommends LabJournalEntries on step DataObjects for
step-specific pages, and either (a) LJEs on the Collection itself OR
(b) LJEs on a dedicated "Project Documentation" DataObject for the
68 project-wide pages.

Shepard's J1 lab-journal design treats LJEs as a sub-resource of
DataObjects. Whether a Collection-level LJE exists in the current
schema needs a check before the script's Phase 6 runs.

Default plan: a single "Project Documentation" DataObject hosted at
Collection level OUTSIDE the predecessor chain (it's not a process
step), carrying the 68 unmapped pages as LJEs.

## 9. Acceptance criteria

(unchanged from previous §5 — still applies, plus:)

- The 7 step DataObjects appear in `MFFD-Augsburg-2026` with their
  predecessor chain intact and statuses set
- Steps 1 and 5 carry the grafted source-export trees (full payload
  counts match Phase 0's pre-flight numbers)
- Steps 2, 3, 4, 6, 7 are present but empty (the placeholders)
- 44 of 116 wiki pages live as step-attached LJEs; 68 live on
  Project Documentation
- A user navigating the frontend can do
  `MFFD-Augsburg-2026 → Bridge Welding → Frame 1 → AF_1 → Process Data 1`
  in 5 clicks (same demo path as before)

## 5. Acceptance criteria for the import

- All 8 383 DataObjects present in shepard with correct parent chain
- All 24 312 file payloads downloadable via shepard at the expected
  byte size + MD5
- All 3 371 StructuredData docs queryable
- Annotation IRIs queryable via `/v2/semantic/annotations` for at
  least one Track, one Ply, one PlyGroup, one Frame
- Re-running the script is a no-op (idempotency)
- A second-by-second log of the run lands at `import-log.ndjson` for
  audit
- The MFFD lab leads can navigate `MFFD-Augsburg-2026 → FrameWelding →
  Frame 1 → AF_1 → Process Data 1 → <PNG payload>` end-to-end in the
  shepard frontend within 5 clicks
