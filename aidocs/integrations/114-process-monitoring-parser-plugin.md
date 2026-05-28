---
title: Process-monitoring file-format parser plugin family — thermography (OTvis, FLIR, IRBIS) + thermal-analysis (NETZSCH DEA / DSC / TGA)
stage: feature-defined
last-stage-change: 2026-05-28
audience: contributors, plugin authors, RDM operators
---

# 114 — Process-monitoring parser plugin family

**Tier-1 implementation status (2026-05-28):** Edevis OTvis tier-1
metadata parser shipped (`OTVIS-PARSE-1`) as the standalone Maven
module [`plugins/fileformat-thermography/`](../../plugins/fileformat-thermography/).
27 JUnit tests green against the real `sample_S4_M13_L18_F4.OTvis`
fixture. VIEW_RECIPE "thermography" renderer (tier-1 stub) wired up
in `shapes/render.vue` + `ViewRecipeBuilderDialog.vue` (`OTVIS-VIEW-1`).
Tier-2 work (frame extraction, channel-bound playback) tracked as
`OTVIS-PARSE-2` + `THERMO-CHANNELS-1`; aggregator wire-up as
`OTVIS-WIRE-AGGREGATOR-1`.



**Status:** Concept · MFFD-driven · companion to [`110-file-format-parser-plugin.md`](110-file-format-parser-plugin.md) (the SPI baseline) and [`112-mfg-plugin-design.md`](112-mfg-plugin-design.md) (manufacturing-domain ontology)

**Source artefacts (operator-uploaded 2026-05-28):**
- `28a30131-DisplayImg_Dateiformat_Rev_H.pdf` — Edevis OTvis camera-grid format documentation cover
- `93e70381-S4_M13_L18_F4.OTvis` — one Edevis OTvis measurement (tar archive, lock-in active thermography)
- `a228b946-20240925_153942.nxpdea` — NETZSCH Proteus DEA dielectric analysis (ZIP archive, OpenXML pattern, 2 binary CDbTable streams)

**DO-sprawl containment (user directive 2026-05-28):**
Per-measurement uploads enrich the EXISTING DataObject + FileReference with annotations. The plugin MUST NOT auto-create child DataObjects from manifest files (e.g. the `.diproj` project file is **ignored** by this plugin — no per-entry DO explosion). One upload = one FileReference enrichment + (tier-2) at most one new TimeseriesContainer or one SignedZarrUrl, anchored to the same parent DO.

## 0. Scope

Two vendor families enabled in one plugin module:

| Family | Phase of process | What it measures | Why it matters for the MFFD digital thread |
|---|---|---|---|
| **Thermography (active NDT)** | After AFP layup / after cure | IR amplitude + phase under modulated halogen excitation | Quality-state map of laid-up plies; cross-correlates with AFP layup TS (laser, force, speed) |
| **Thermal analysis (cure monitoring)** | During resin infusion / autoclave / RTM | Dielectric ε', ε'', ion viscosity, temperature vs cure-cycle program | Real-time cure-state evidence; cross-correlates with cure recipe + downstream NDT |

The plugin module is `shepard-plugin-fileformat-process-monitoring`. Each parser registers individually via the `FileParserPlugin` SPI from [aidocs/110 §3](110-file-format-parser-plugin.md#3-fileparserplugin-spi-sketch). One module, many parsers — same shape as `shepard-plugin-fileformat-spatial` (SA + RDK) in 110.

## 1. Feasibility per format

### 1.1 Edevis OTvis (`.OTvis`, `.xOTvis`, `.diproj`)

| Aspect | Verdict |
|---|---|
| Container | ✅ POSIX tar (`.OTvis`) + XML (`.diproj`); both trivially openable |
| Metadata | ✅ `content.xml` is UTF-16 XML — straight parse; emits ~25 normalized annotations (frame rate, integration time, excitation device/type/frequency/amplitude, recording type, resolution, periods, transformation chains) |
| Raw frames (`sequence0/f0.bin`) | ⚠️ Proprietary little-endian 16-bit-int frame buffer. Layout deducible from `content.xml` `Window`/`FrameRate`/timing fields. Calibration in `sequence1/calibration.bin` (camera radiometric coefficients). |
| Lock-in result (`sequence1/f*.bin`) | ⚠️ Vendor binary; ~1.6 MB for one (S, M, L) → consistent with `1024×768 × 2 floats (amplitude + phase) × 1 layer` after compression-equivalent quantization. |
| FAIR intermediate | OME-Zarr (T × H × W single-channel float32) or HDF5 NeXus NXdetector — both standard, both convertible. |
| **Tier-1 verdict** | ✅ Trivial. Metadata scrape from `content.xml` + filename pattern (`S<n>_M<n>_L<n>_F<n>`) → annotations on the parent FileReference. |
| **Tier-2 verdict** | ⚠️ Requires either community reverse-engineering of the frame layout OR an operator-provided ASCII export from Edevis OTvis (the software has an export-to-CSV mode). |

### 1.2 NETZSCH Proteus DEA (`.nxpdea`)

| Aspect | Verdict |
|---|---|
| Container | ✅ Standard ZIP using OpenXML packaging (`[Content_Types].xml` + `Streams/` + `Props.xml`). `unzip` works directly. |
| Stream structure | `stream_1.table` (66 KB) = method header / cure-cycle program; `stream_2.table` (4.5 MB) = measurement table |
| Internal table format | ⚠️ NETZSCH proprietary `CDbTable` (binary; magic `Netzsch TA file` + `_db_format_1` + `CDbTable` header). **Partial community reverse-engineering exists** — see `netzschReader`-style projects on GitHub for the older `.ngb` and `.sta` formats. The new `.nxpdea` is the ZIP-wrapped Proteus 9 evolution; the inner table format is similar. |
| Tier-1 (header scrape) | ✅ Trivial. Read `Props.xml` + the printable strings in stream_1 → cure program name, instrument, sample ID, frequency sweep list, target temperature ramp. |
| Tier-2 (data extraction) | Two viable paths: (a) **community Python reader** extended for nxpdea CDbTable layout; (b) **Wine-sandboxed Proteus CLI** sidecar — Proteus has an ASCII-export CLI mode; runs in Wine for headless conversion, declared per `feedback_plugins_declare_sidecars.md` and gated behind operator opt-in (`NETZSCH_PROTEUS_ENABLE=true` + bind-mounted Proteus install). |
| DEA channels expected | `time_s`, `temperature_C`, per-frequency `permittivity_real` (ε'), `permittivity_imag` (ε''), `ion_viscosity_logOhmCm` (calculated), `loss_factor` (tan δ), `cure_index` (NETZSCH proprietary calculation if exported) — typically multi-frequency sweep (0.1 Hz, 1 Hz, 10 Hz, 100 Hz, 1 kHz, 10 kHz, 100 kHz). |
| **Tier-1 verdict** | ✅ Trivial. Parse XML + scrape header strings → emit `urn:shepard:cure:*` annotations + `urn:shepard:netzsch:*` metadata. |
| **Tier-2 verdict** | ⚠️–⚠️⚠️ Two-path feasible. CDbTable RE is the better long-term answer; Proteus-Wine sidecar is the fallback. |

### 1.3 Plausible third-leg: FLIR `.seq` / `.csq` / `.fff`

Mentioned for completeness — FLIR thermography in MFFD context (drone IR, handheld IR thermography of final part). Open-source readers exist (`fnv-fff`, `flir-image-extractor`); tier-2 is solved. Out of scope for v1 of this plugin; included as a future parser once an MFFD FLIR dataset arrives.

### 1.4 InfraTec IRBIS `.irb` / `.IRBacq`

Mentioned for completeness — vendor active/passive thermography. Vendor SDK is Windows-only, paid. Community Python readers exist with partial support. Same Wine-sandboxed sidecar pattern as NETZSCH Proteus if v1.5 demand arrives.

## 2. Plugin shape

```
shepard-plugin-fileformat-process-monitoring/
├── pom.xml                              (declares the JAR + SPI registration)
├── src/main/java/de/dlr/shepard/plugin/fileformat/processmonitoring/
│   ├── ProcessMonitoringPluginManifest.java
│   ├── thermography/
│   │   ├── OTvisParser.java             (tier-1 + tier-2)
│   │   ├── OTvisMetadataMapper.java     (content.xml → annotations)
│   │   └── OTvisFrameExtractor.java     (sequence0/sequence1 → OME-Zarr)
│   │   // NB: no DiprojParser — manifest files are deliberately ignored
│   │   // to prevent DO-sprawl per the containment rule above.
│   ├── netzsch/
│   │   ├── NetzschDeaParser.java
│   │   ├── NetzschCdbTableReader.java   (the RE'd binary reader)
│   │   ├── NetzschProteusCliFallback.java (Wine-sandboxed sidecar fallback)
│   │   └── NetzschCureCycleMapper.java  (method-table → urn:shepard:cure:* annotations)
│   ├── normalize/
│   │   ├── ProcessMonitoringDataset.java   (common entity — superclass of ThermographyDataset + CureMonitoringDataset)
│   │   ├── ThermographyDataset.java
│   │   └── CureMonitoringDataset.java
│   └── omezarr/
│       └── OmeZarrFrameWriter.java      (shared 3D image writer)
├── sidecars/
│   └── proteus-cli/                     (optional; Wine + Proteus install for tier-2 DEA fallback)
└── plugins/process-monitoring/docs/
    ├── reference.md                     (per-parser endpoints + annotation keys)
    ├── quickstart.md                    (how to upload an OTvis or .nxpdea file)
    └── install.md                       (sidecar opt-ins; CDbTable status)
```

Each parser implements:

```java
public interface FileParserPlugin {
    boolean accepts(MimeType mime, String filename);
    void parse(ParseContext ctx);            // async, called by ParseJob
}
```

(From `aidocs/110 §3`.) Dispatched by the `FileParserRegistry` on upload, runs async, emits derived entities + annotations under the FileReference's parent DataObject.

## 3. Normalized data model

Shared by all process-monitoring parsers:

```
:ProcessMonitoringDataset (HasAppId)
  ├─ measurementCampaign : "MFFD" | …
  ├─ instrument : { vendor, model, serial }
  ├─ operatorIdentifier : string (annotated)
  ├─ recordedAt : ISO-8601
  ├─ phase : "layup_inspection" | "cure_monitoring" | "post_cure_ndt" | "post_test"
  └─ (subclass)
       :ThermographyDataset
          ├─ frameRate_Hz : float
          ├─ integrationTime_s : float
          ├─ excitationDevice : "halogen" | "flash" | "ultrasound" | "passive"
          ├─ excitationFrequency_Hz : float (lock-in only)
          ├─ excitationAmplitude_pct : float
          ├─ resolution : "1024x768"
          ├─ recordingType : "evaluation" | "raw" | "burst"
          ├─ gridPosition : { section, module, layer, frame }
          └─ frameStorage : SignedZarrUrl | OmeZarrInternalRef | HDF5Ref
       :CureMonitoringDataset
          ├─ cureProgram : { name, ramp_steps[], dwell_steps[] }
          ├─ resinSystem : "RTM6" | "PEKK" | "LMPAEK" | …
          ├─ frequencies_Hz : float[]
          ├─ channels : { time_s, temperature_C, permittivity_real[f], permittivity_imag[f], ion_viscosity_logOhmCm[f] }
          └─ tsContainerAppId : appId of created TimeseriesContainer
```

`gridPosition` connects each thermography DO into the 14 × 14 surface grid (Section × Module) → maps to a coordinate frame on the upper-shell CAD via `aidocs/data/85-coordinate-frame-tree.md`. That gives 3D-mapped phase-image overlay on the digital twin — concrete MFFD-grade visualization.

## 4. Annotation keys (`urn:shepard:*`)

Per [`feedback_template_driven_create_all_refs.md`](../../-opt-shepard/memory/feedback_template_driven_create_all_refs.md) — every reference type pre-fills from parent-DataObject templates. New keys this plugin family contributes:

| Predicate | Domain | Range | Source format |
|---|---|---|---|
| `urn:shepard:thermography:excitationDevice` | ThermographyDataset | "halogen"\|"flash"\|"ultrasound"\|"passive" | OTvis |
| `urn:shepard:thermography:excitationFrequency_Hz` | ThermographyDataset | float | OTvis |
| `urn:shepard:thermography:frameRate_Hz` | ThermographyDataset | float | OTvis |
| `urn:shepard:thermography:integrationTime_s` | ThermographyDataset | float | OTvis |
| `urn:shepard:mffd:section` / `:module` / `:layer` / `:frame` | DataObject (any) | string | OTvis filename (S<n>_M<n>_L<n>_F<n>) |
| `urn:shepard:cure:program` | CureMonitoringDataset | string | NETZSCH `.nxpdea` |
| `urn:shepard:cure:resinSystem` | CureMonitoringDataset | string | NETZSCH `.nxpdea` |
| `urn:shepard:cure:frequencySweep_Hz` | CureMonitoringDataset | float[] | NETZSCH `.nxpdea` |
| `urn:shepard:cure:targetGlassTransition_C` | CureMonitoringDataset | float | NETZSCH `.nxpdea` |
| `urn:shepard:netzsch:instrumentModel` | CureMonitoringDataset | string | NETZSCH `.nxpdea` |
| `urn:shepard:netzsch:proteusVersion` | CureMonitoringDataset | string | NETZSCH `.nxpdea` |

## 5. Visualization recipes (separately filed)

VIEW_RECIPE templates registered alongside Trace3D and URDF (see `aidocs/113-urdf-viewer.md` for the renderer-hint pattern):

- `renderer = "thermography"` — IR sequence player + lock-in amplitude/phase image side-by-side; optional 3D overlay on the upper-shell CAD via gridPosition → CoordinateFrame.
- `renderer = "cure-monitoring"` — ion-viscosity-vs-time chart with cure-cycle temperature program overlay + log-of-cure milestone markers. Same Three.js scene infra is irrelevant here (2-D chart), but the VIEW_RECIPE template-driven UX is identical.

## 6. Backlog rows

Filed in [`aidocs/16-dispatcher-backlog.md`](../16-dispatcher-backlog.md) under the `THERMO-DEA-*` and `OTVIS-*` prefixes (see entries below this doc's commit).

## 7. Why this is the right shape

- **Reuses existing SPI** (aidocs/110) — no new infra needed.
- **One module, many parsers** — vendor releases new format → drop in a sibling parser, the SPI does the rest.
- **Tier-1 trivial for both formats** — uploads enrich themselves with annotations on day one; tier-2 raw-data extraction is a separable follow-up.
- **FAIR-aligned output** — normalized ProcessMonitoringDataset entity with controlled vocabulary; OME-Zarr / HDF5 NeXus as FAIR intermediate; TimeseriesContainer for DEA channels makes the data queryable by existing UI + MCP tools.
- **Cross-modal correlation enabled** — `urn:shepard:mffd:section` / `:module` connects thermography surface tiles to AFP layup TS at the same (S, M) cell. `urn:shepard:cure:program` connects DEA cure runs to the autoclave recipe and (eventually) to the part's NDT inspection at the matching layup phase.
- **Plugin-first per CLAUDE.md** — new payload kinds (ThermographyDataset, CureMonitoringDataset) ship as a plugin from day one. Core entities stay generic.

## 8. Risk / open questions

- **CDbTable RE depth** — how complete is the community reverse engineering of the NETZSCH `.nxpdea` stream layout? Tier-2 viability rides on this. If RE is incomplete, the Wine-sandboxed Proteus CLI fallback works but is operator-licence-gated.
- **OTvis frame byte layout** — `sequence0/f0.bin` is 6.3 MB. For 1024 × 768 × 16-bit at 30 Hz over 7 ms integration with 2 acquisition periods × 1/0.015 Hz ≈ 133 s, expected raw size at 30 FPS is huge (>1 GB uncompressed); 6.3 MB implies either compression or only the evaluation result is in `sequence0` and the raw frames are dropped (`RecordingType = Evaluation`). Confirm with one test parse before committing tier-2 scope.
- **Sidecar policy** — Proteus-on-Wine sidecar is the heaviest dep in the family. Document the opt-in clearly in `plugins/process-monitoring/docs/install.md`; default-OFF.

## 9. Companion reading

- [`aidocs/integrations/110-file-format-parser-plugin.md`](110-file-format-parser-plugin.md) — the SPI baseline this plugin implements
- [`aidocs/integrations/112-mfg-plugin-design.md`](112-mfg-plugin-design.md) — manufacturing-domain ontology
- [`aidocs/data/85-coordinate-frame-tree.md`](../data/85-coordinate-frame-tree.md) — the CoordinateFrame entity that anchors thermography grid positions
- [`aidocs/agent-findings/mffd-wiki-analysis-findings.md`](../agent-findings/mffd-wiki-analysis-findings.md) §"QS Auswertung as per-ply timeseries" — the upgrade from QS table to real OTvis phase-image map
