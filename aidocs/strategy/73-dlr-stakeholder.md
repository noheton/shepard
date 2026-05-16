# aidocs/73 — shepard: Stakeholder brief for DLR

**Date:** 2026-05-16
**Audience:** DLR program managers, infrastructure committee members, institute heads,
IT operations leads.
**Purpose:** Explain what shepard is, why DLR should care, how it fits DLR's research
landscape, and what the adoption path looks like.

For the DLR-BT institute-specific brief, see `aidocs/74`.

---

## 1. The problem: DLR's data lives in silos

DLR operates across four research areas — aeronautics, space, energy, transport — with
roughly 30 research institutes generating experimental, simulation, and observational data
continuously. The structural data management problem is the same in every institute:

- **Heterogeneous formats.** A single campaign produces sensor timeseries, HDF5 simulation
  outputs, CAD/geometry files, photos, lab-journal notes, and structured run-logs — stored
  in different systems with no unifying context.
- **Provenance is informal.** "Which version of the analysis code produced this result,
  and from which raw measurement?" is answered by email chains, not machine-readable
  records.
- **Discovery stops at institute boundaries.** A researcher at DLR-SR cannot find the
  complementary dataset at DLR-FA unless they happen to know the right person.
- **Publication is a manual handoff.** Getting data into elib.dlr.de or an external
  repository requires a researcher to manually assemble metadata, choose a format, and
  navigate multiple systems.
- **Compliance pressure is increasing.** NFDI, DFG data management plan requirements,
  and Helmholtz's own FAIR data obligations mean that "we have the data somewhere" is no
  longer sufficient.

---

## 2. What shepard is

shepard is an open-source **active research data management platform** — the structured
layer between raw instrument output and eventual archival. It is not a file share, not a
database, and not a publication repository: it is the **data context** in between.

A researcher puts a campaign in. Collaborators see what they are allowed to see. The data
has provenance, semantic annotations, and typed structure. When the campaign is done, a
citable export (RO-Crate, DOI, InvenioRDM record) comes out.

shepard is built on an open stack (Quarkus, Neo4j, MongoDB, TimescaleDB, S3-compatible
object storage) and designed to be **self-hosted** by the institute or a central DLR
infrastructure team. It is Apache 2.0 licensed.

---

## 3. DLR-specific fit

### 3.1 Already built for the Helmholtz ecosystem

Several features are DLR- and Helmholtz-native by design:

| Feature | What it does | Why DLR specifically |
|---|---|---|
| **Unhide/HKG publish** | One-click feed to Helmholtz Knowledge Graph (Unhide); schema.org + metadata4ing JSON-LD | Helmholtz FAIR data obligation; Unhide harvests participating centres |
| **HMC Kernel Information Profile (KIP) DOIs** | Mint persistent identifiers (local, ePIC, DataCite) from shepard | HMC NFDI compliance; replaces informal elib manual submission |
| **metadata4ing semantic layer** | NFDI4Ing engineering-research extension of PROV-O baked in | DLR's core domain (engineering research) has native ontology support |
| **PIDINST federation (EQ1)** | Link shepard data provenance to inst.dlr instrument records | inst.dlr is a DLR-internal instrument metadata registry; provenance ties to it natively |
| **Helmholtz AAI compatibility** | Keycloak + OIDC; maps to Helmholtz AAI (HIFIS) identity layer | Institute SSO via Helmholtz AAI out of the box |

### 3.2 Institute-by-institute fit

#### Aeronautics

| Institute | Focus | Typical data challenge | Shepard fit |
|---|---|---|---|
| **FA** Flugsystemtechnik | Flight systems, UAV, avionics | Multi-sensor onboard telemetry; run configurations embedded in filenames; flight envelope datasets from distributed airborne rigs | Timeseries payload; StructuredDataReference for run configs; provenance edge to PIDINST instrument record (pitot, IMU, GPS) |
| **AS** Aerodynamik und Strömungstechnik | CFD, wind tunnel, aeroelasticity | Multi-TB CFD HDF5 outputs; wind-tunnel pressure-scanner arrays; PIV image stacks; linking solver version to result file | HDF5/HSDS payload kind; git integration (G1) pins solver commit; predecessor graph (mesh → simulation → post-processed); FileBundle for PIV stacks |
| **BT** Bauweisen und Strukturtechnologie | Structural composites, crash testing, AFP, CALLISTO | Crash sensor arrays + high-speed video + post-processing CSVs in separate systems; AFP process-to-performance gap | Full feature mapping in `aidocs/74`; instrument dropbox, timeseries annotations, video payload kind (VID1), RO-Crate export |
| **SR** Softwaretechnologie | Software methods, safety-critical systems | Benchmark datasets, test result archives, model-verification artefacts; tracing code version to test outcome | Git integration (G1) pins code commit to result; StructuredDataReference for benchmark configs; semantic annotations for ISO 26262 / DO-178C test standard terms |

#### Space

| Institute | Focus | Typical data challenge | Shepard fit |
|---|---|---|---|
| **RY** Raumflugbetrieb und Astronautentraining | Space operations, ISS, mission support | Satellite telemetry streams, mission planning documents, ground-station logs across disconnected systems | Timeseries payload for telemetry; FileBundle for mission documents; provenance graph ground-test → launch → flight |
| **ME** Werkstoff-Forschung | Space materials, coatings, tribology | Multi-lab characterisation: synthesis → specimen → destructive test → microscopy → model; no machine-readable chain | Predecessor graph linking each process step; StructuredDataReference for test parameters; semantic annotations with materials ontologies |
| **OP** Optische Sensorsysteme | Hyperspectral sensors, lidar, Earth observation | Large spectral image cubes, lidar point clouds, calibration datasets; linking sensor calibration run to product | SpatialDataReference or FileBundle for spectral cubes; StructuredDataReference for calibration coefficients; provenance to PIDINST sensor record |
| **DFD** / EOC Fernerkundungsdatenzentrum | SAR processing, satellite data products, Copernicus | Large raster time-series (Sentinel SAR, MODIS, Landsat); processing-pipeline provenance; cross-mission dataset linkage | OpenLineage provenance for processing chains; GeoSPARQL spatial references; RO-Crate export for Copernicus product delivery |

#### Energy

| Institute | Focus | Typical data challenge | Shepard fit |
|---|---|---|---|
| **TT** Technische Thermodynamik | Combustion, heat transfer, energy conversion | Thermocouple / pressure timeseries from combustion rigs; HDF5 simulation outputs; linking rig configuration to measurement | Timeseries payload; HDF5/HSDS; StructuredDataReference for rig configs; AI anomaly detection (TA1c) for thermal events |
| **VE** Vernetzte Energiesysteme | Grid integration, renewables, storage | Power-flow timeseries, grid sensor archives, battery cycle data; hundreds of parameter-sweep runs with manual bookkeeping | Timeseries payload; StructuredDataReference for sweep parameters; GeoSPARQL for grid topology; predecessor graph across sweep iterations |

#### Transport

| Institute | Focus | Typical data challenge | Shepard fit |
|---|---|---|---|
| **TS** Verkehrssystemtechnik | Traffic systems, road, rail | Traffic detector streams, simulation outputs, field measurement campaigns with geographic coverage | Timeseries payload; GeoSPARQL spatial references; OpenLineage provenance for simulation pipeline |
| **TP** Fahrzeugkonzepte | Future vehicle concepts, autonomous driving | Driving-test logs (CAN bus, lidar, camera), scenario datasets; linking test scenario to vehicle configuration | Timeseries + FileBundle for sensor-fusion datasets; StructuredDataReference for scenario config; instrument dropbox for CAN logger |
| **VM** Fahrzeugsysteme | Vehicle systems integration, powertrain | Multi-sensor test-vehicle datasets; component-to-system performance traceability | Provenance predecessor graph (component test → system integration test); instrument dropbox auto-ingest |

### 3.3 Compliance

shepard generates the artefacts DFG and Helmholtz data management plans require:

- **FAIR data**: PROV-O provenance, PID assignment, RO-Crate export, machine-readable
  metadata in schema.org + metadata4ing — FAIR from the point of data creation, not
  retrofitted at publication.
- **Audit trail**: every mutation is captured as a W3C PROV-O `:Activity` node with
  agent, timestamp, and action. Per-entity and instance-admin dashboards.
- **Export for archival**: `POST /v2/collections/{appId}/export` → RO-Crate ZIP, optionally
  submitted to InvenioRDM (`aidocs/72`) or Unhide (`aidocs/67`) in one click.

---

## 4. Deployment topology: three options

### Option A — Central DLR instance

One shared instance operated by SISTEC/DLR-IT. All institutes use it.

- **Pros:** Lowest operational overhead; single SSO config; DLR-wide discoverability
  without federation.
- **Cons:** Governance complexity (who is the instance-admin?); data residency concerns
  for institute-sensitive data; one outage affects everyone.
- **Best for:** Pilot phase; institutes without their own ops capacity.

### Option B — Per-institute instances (federated)

Each institute runs its own shepard. Each exposes a `GET /v2/unhide/feed.jsonld`; a
DLR-wide Unhide harvester aggregates them.

- **Pros:** Institute autonomy; isolated data residency; per-institute admin; independent
  upgrade cadence.
- **Cons:** Higher ops overhead unless a shared template/Helm chart lowers it; discovery
  requires the Unhide aggregation layer to be set up.
- **Best for:** Institutes with mature local IT (BT Stuttgart, FA Braunschweig) and
  strong data sovereignty requirements.

### Option C — Hybrid (recommended)

Central pilot instance for early adopters; per-institute instances for institutes ready
to operate their own. The Unhide feed (`/v2/unhide/feed.jsonld`) is the federation seam
that makes both topologies discoverable from the same Helmholtz Knowledge Graph query.

A shared **Helm chart** and a `docker compose` reference stack lower the cost of
standing up a new per-institute instance to hours, not weeks.

---

## 5. Cost-benefit snapshot

Numbers from `aidocs/71 §5–6` (DLR-fork development sprint, May 2026):

| Metric | Value |
|---|---|
| Traditional cost to build the feature set shepard now has | €60 000 – €138 000 |
| AI-assisted development cost (API fees) | ~€625 |
| AI-assisted maintenance: equivalent of €5 000/year buys | 30–50 developer-weeks of maintenance |
| Annual maintenance cost of a deployed instance (ops only) | ~€5 000–15 000 (server + ops admin time) |
| Typical DFG data management plan compliance cost without tooling | €10 000–30 000/project (manual curation) |

**The structural argument:** DLR research funding buys results, not software sustainability.
A traditional RDM tool costs €200 000–€1 M to develop and €20 000–€50 000/year to
maintain. After the project ends, it rots. AI-assisted maintenance changes this equation:
€5 000/year of maintenance budget now has the leverage of €150 000+/year of traditional
development. shepard, developed and maintained this way, is the first DLR-native RDM
platform that is sustainable beyond its originating project.

---

## 6. Adoption path

The three adoption shapes (from `aidocs/71 §2`):

| Shape | Description | Effort | DLR action needed |
|---|---|---|---|
| **A. Reference implementation** | DLR links to this repo in shepard README as "the extended variant" | Minimal | One PR; formal acknowledgement |
| **B. Plugin-layer merge** | Plugin SPI merged upstream so DLR's community can write plugins | 2–4 sprints | Upstream maintainer review; plugin SPI stabilisation |
| **C. Full fork adoption** | This fork replaces upstream `main`; DLR GitLab CI/CD re-points here | 1–2 quarters | IT governance; pilot institute sign-off; CI/CD re-point |

**Recommended immediate step:** a pilot deployment at one DLR institute (BT Stuttgart
is the closest candidate — see `aidocs/74`) as evidence before requesting Shape B/C.

---

## 7. What DLR gets from a pilot

A 3-month pilot at one institute with two active research campaigns delivers:

1. **Evidence for the Shape B proposal** — real RO-Crate exports, real KIP PIDs, real
   Unhide feed entries from a DLR institute.
2. **A reusable deployment template** — Helm chart / `docker compose` stack that every
   subsequent institute can spin up in a day.
3. **A metric** — how many researcher-hours of data assembly work are eliminated per
   campaign? This number is the DFG/BMBF funding-case argument.
4. **Community feedback** — what shepard features does DLR's domain actually need? The
   plugin SPI makes domain-specific extensions (inst.dlr federation, AAS submodels,
   DLR-specific ontologies) low-friction to develop.

---

## 8. The ask

1. **One institute pilot deployment** (3 months, 1–2 active campaigns). BT Stuttgart is
   the proposed pilot (see `aidocs/74`).
2. **One designated champion** in DLR IT or in the pilot institute to co-own the
   deployment and surface operational requirements.
3. **An introduction to the upstream shepard team** (`gitlab.com/dlr-shepard/shepard`)
   for the Shape B plugin-layer merge proposal.

---

## 9. See also

- `aidocs/strategy/74-dlr-bt-stakeholder.md` — BT-specific brief (pilot candidate)
- `aidocs/strategy/71-fork-adoption-as-upstream.md` — full adoption feasibility and cost-benefit
- `aidocs/42-vision.md` — researcher-facing vision
- `aidocs/integrations/72-invenio-publishing-plugin.md` — InvenioRDM submission plugin
- `aidocs/integrations/67-unhide-publish-plugin.md` — Helmholtz KG publishing
- `aidocs/integrations/66-hmc-kip-integration.md` — KIP DOI minting
