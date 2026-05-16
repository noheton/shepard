# aidocs/74 — shepard: Stakeholder brief for DLR-BT (Stuttgart)

**Date:** 2026-05-16
**Audience:** DLR Institut für Bauweisen und Strukturtechnologie (BT), Stuttgart —
institute leadership, data stewards, research engineers, potential champion researchers.
**Purpose:** Make the case for a shepard pilot at BT; map concrete BT research campaigns
to specific shepard features; propose the next step.

For the DLR-wide brief, see `aidocs/73`.

---

## 1. Why BT is the right first pilot

DLR-BT (Institut für Bauweisen und Strukturtechnologie, Stuttgart; formerly BK —
Institut für Bauweisen- und Konstruktionsforschung) produces exactly the kind of
heterogeneous, multi-scale, high-volume research data that shepard was built for:

- **Crash and impact tests (CITE)** — millisecond-resolution sensor arrays from
  full-scale eVTOL and helicopter crash tests generate tens of thousands of time-series
  channels. Raw output is typically split across proprietary DAQ formats, post-processing
  CSVs, high-speed camera video, and handwritten test-card notes. Today these live in
  different places with informal provenance.
- **Ceramic matrix composites (CMC)** — material characterisation is multi-step:
  prepreg layup → sintering → destructive testing → microscopy → modelling. Each step
  produces different data types; the chain from material spec to failure curve is rarely
  machine-readable.
- **Space structures (CALLISTO, Space Systems Integration)** — reusable rocket
  demonstrators require tying simulation predictions to as-built geometry, test results,
  and flight telemetry. Today this is a manual cross-referencing task.
- **Hydrogen propulsion (TeTeAnt-H2, ADAPT)** — thermal characterisation of pressure
  tanks and propulsion components generates time-series + HDF5 simulation data across
  parameter sweeps. Managing hundreds of runs without losing the mapping between config
  and result is a known pain point.
- **Automated fibre placement (AFP, MFFD)** — manufacturing process data (layup
  sequences, robot trajectories, quality inspection images) needs to be linked to the
  resulting structural test results to close the process-to-performance loop.

BT is also the institute closest to shepard's development — this makes feedback cycles
fast and championing the upstream adoption proposal realistic.

---

## 2. Feature-to-campaign mapping

### 2.1 CITE crash and impact tests

**Pain point:** Sensor channels from a crash test are ingested into a proprietary DAQ
system. Post-processing produces CSV files. High-speed camera video ends up on a NAS.
Test-card notes are scanned PDFs. The campaign record is a folder on a shared drive.

**shepard answer:**

| Shepard feature | How it helps |
|---|---|
| **Instrument dropbox (IL1)** | File-system or S3-prefix watcher auto-ingests DAQ output into a pre-configured Collection; no manual upload |
| **Timeseries annotations (TA1a, shipped)** | Label impact events, ramp phases, and anomalies on sensor channels with start/end timestamps and confidence flags |
| **Video payload kind (VID1)** | High-speed camera footage stored as HLS segments; navigable by wall-clock time; linked to the same Collection as the sensor data |
| **FileBundle + FileGroup (FR1a, shipped)** | Group post-processing CSVs, test-card PDFs, and photos into structured sub-runs |
| **Provenance graph (PROV1a, shipped)** | Machine-readable record: which test article, which rig configuration, which analysis code version produced this result |
| **RO-Crate export (FS1g, shipped)** | One-click citable package for handover to elib or InvenioRDM |

**Concrete example:** `CITE-eVTOL-2026-04` campaign → one `Collection`; each drop test
is one `DataObject`; each DataObject has a `TimeseriesReference` (sensor channels),
a `FileBundle` (post-processing outputs), a linked video segment, and semantic
annotations (`m4i:InvestigatedObject = "eVTOL-demonstrator"`, `m4i:Method = "drop-test"`).
The full campaign exports as a single RO-Crate ZIP with provenance.

### 2.2 CMC material characterisation

**Pain point:** A CMC development cycle spans months and multiple laboratories. The
mapping from "which batch of SiC fibres, which sintering cycle, which geometry" to "which
failure mode at what load" exists in spreadsheets and lab notebooks but not in a
machine-queryable form.

**shepard answer:**

| Shepard feature | How it helps |
|---|---|
| **Predecessor/lineage graph** | `prepreg-batch-07` → `sintered-panel-07A` → `test-coupon-07A-3` — the derivation chain is a graph edge, not a filename convention |
| **Semantic annotations** | Annotate each DataObject with CMC-domain ontology terms (material composition, manufacturing method, test standard); query across the whole campaign |
| **StructuredDataReference** | Store sintering cycle parameters, test machine configs, and QA measurements as structured JSON linked to the physical sample |
| **Templates (T1)** | Admin defines a "CMC coupon characterisation" template; researchers instantiate it with required fields pre-defined — no forgotten metadata fields |

### 2.3 CALLISTO reusable rocket demonstrator

**Pain point:** CALLISTO requires tracing a component from CAD model through
manufacturing inspection to ground test to flight. Today this trace exists in
disconnected PDFs, CAD versions on a PLM, and test data in an institute file share.

**shepard answer:**

| Shepard feature | How it helps |
|---|---|
| **Git integration (G1, in-flight)** | Pin the exact simulation code commit that produced a specific prediction; link code version to the DataObject it generated |
| **HDF5/HSDS payload kind (A5a, shipped)** | Simulation outputs in HDF5 format are a first-class payload; existing `h5py` analysis code works unchanged |
| **Experiment orchestration (EXP1)** | Model the CALLISTO test campaign as a templated sequence; each step's output is auto-captured; checkpoint-based restart if a step fails |
| **FileBundle + spatial references** | CAD geometry as a `SpatialDataReference` or `FileBundle`; linked to the manufacturing inspection record and the test result |

### 2.4 Hydrogen propulsion (TeTeAnt-H2, ADAPT)

**Pain point:** Thermal characterisation of Type IV pressure tanks involves hundreds of
parameter sweeps (fill pressure, temperature profile, fill rate). Managing which
simulation run corresponds to which experimental result, and which version of the thermal
model was used, is a manual bookkeeping task.

**shepard answer:**

| Shepard feature | How it helps |
|---|---|
| **Timeseries + HDF5** | Experimental thermocouple data (timeseries) + simulation outputs (HDF5) in the same Collection, linked by parameter sweep ID |
| **StructuredDataReference** | Run configuration (fill pressure, temperature, material properties) as structured JSON — queryable, not buried in a filename |
| **AI anomaly detection (TA1c)** | Flag thermal runaway candidates automatically across hundreds of runs; `aiGenerated` flag on annotations distinguishes AI from human labels |
| **Predecessor graph** | Simulation run → experimental validation run → corrected model — the iteration is graph edges, not filename suffixes |

### 2.5 Automated fibre placement (AFP, MFFD)

**Pain point:** AFP process data (robot trajectories, layup sequences, quality inspection
images) and the resulting structural test results live in separate systems. Closing the
process-to-performance loop requires manual data assembly.

**shepard answer:**

| Shepard feature | How it helps |
|---|---|
| **Instrument dropbox (IL1)** | AFP machine produces output files; dropbox auto-ingests them into a Collection keyed to the panel ID |
| **FileGroup** | Group layup pass files (one FileGroup per ply) inside a FileBundle for the panel |
| **Provenance edge** | `:wasGeneratedBy: Instrument(AFP-machine-1)` on each DataObject; links to inst.dlr PIDINST record |
| **Templates** | "AFP panel" template enforces required fields (part number, material spec, layup sequence file) at DataObject creation time |

---

## 3. Publication pipeline

BT generates data that needs to reach elib.dlr.de (DLR's institutional repository) and
potentially external NFDI or Helmholtz portals. shepard provides a one-click path:

```
Campaign complete
       │
       ├─ "Publish to Helmholtz KG" → Unhide feed (shepherd-plugin-unhide)
       │   Discoverability in Helmholtz Knowledge Graph search
       │
       ├─ "Mint DOI" → KIP PID minter → persistent citable URL
       │   Resolves via /v2/.well-known/kip/...
       │
       └─ "Submit to InvenioRDM" → shepard-plugin-invenio (aidocs/72)
           Researcher-friendly landing page, DOI, downloadable RO-Crate
           Possible target: DLR's elib or a NFDI InvenioRDM deployment
```

The researcher interacts only with shepard. The three downstream systems receive data
automatically via the plugin layer.

---

## 4. RDA compliance alignment

BT research data generated under DFG or BMBF projects must align with RDA recommendations.
shepard satisfies the key ones out of the box:

| RDA recommendation | Shepard alignment |
|---|---|
| **FAIR Data Principles** | PID-per-entity, machine-readable metadata, RO-Crate export, Unhide discoverability |
| **PID Kernel Information (KIP)** | KIP1a–KIP1h shipped; `POST .../publish` mints conformant KIP records |
| **RO-Crate community standard** | First-class export format; FS1g delivers presigned download URL |
| **TRUST principles for digital repositories** | PROV-O audit trail; S3 storage durability; SSL/TLS; per-entity ACL; admin activity log |
| **Machine-actionable DMPs (maDMP)** | RDMO plan ID linkage (§4.8 in `aidocs/70`); DMP-declared retention enforced as metadata |
| **Schema.org metadata interoperability** | Unhide feed is schema.org JSON-LD; metadata4ing for engineering-domain provenance |

---

## 5. What a pilot looks like

**Duration:** 3 months.
**Scope:** One active BT research campaign (suggested: a CITE crash-test series or a
CMC characterisation batch).
**Infrastructure:** One VM (4 vCPU, 16 GB RAM, 500 GB SSD) at BT Stuttgart or DLR
compute infrastructure. Deploy via `docker compose up` — 30 minutes from zero.
**Effort from BT:** One champion researcher (½ day/week to interact with the system
and provide feedback); one IT contact for VM provisioning.
**Effort from shepard side:** Initial deployment assistance, feature requests addressed
within sprint cycles, documentation of BT-specific configuration.

**Success metrics:**
- Campaign data accessible to all authorised collaborators via the web UI and Python
  client.
- At least one RO-Crate export suitable for elib submission produced.
- Researcher time saved on data assembly: target > 5 hours per campaign.
- At least one inst.dlr instrument record linked via provenance.

---

## 6. The ask

1. **Identify a champion researcher** at BT who will engage with the pilot for 3 months.
   The ideal person runs a data-intensive campaign and has felt the manual data assembly
   pain.
2. **Provision one VM** (or identify a slot in DLR's existing compute infrastructure)
   for the deployment.
3. **One working session** (90 minutes) to map the champion's campaign data model to
   shepard's primitives and configure the initial instance.
4. **Introduce the shepard team to BT's IT representative** to agree on backup, identity
   (Keycloak / Helmholtz AAI config), and data residency.

Contact: Felix Krebs (fkrebs@nucli.de) — development lead for this fork.

---

## 7. See also

- `aidocs/strategy/73-dlr-stakeholder.md` — DLR-wide brief (institute-fit matrix, cost-benefit)
- `aidocs/42-vision.md` — researcher-facing vision
- `aidocs/integrations/72-invenio-publishing-plugin.md` — InvenioRDM publication path
- `aidocs/data/50-experiment-orchestration.md` — experiment campaign orchestration
- `aidocs/integrations/67-unhide-publish-plugin.md` — Helmholtz KG publishing
