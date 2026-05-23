---
title: "Predecessor systems at DLR ZLP Augsburg — continuity of field before Shepard"
subtitle: "(thesis chapter draft — continuity of field)"
stage: fragment
last-stage-change: 2026-05-23
audience: [thesis, strategy, historian]
---

# Predecessor systems at DLR ZLP Augsburg — continuity of field before Shepard

*Thesis chapter draft. A continuity-of-field section establishing the
local lineage of research-data-management (RDM) systems at the DLR
Centre for Lightweight Production Technology in Augsburg (ZLP) that
preceded Shepard.*

## 1. State of the field at DLR ZLP that made Shepard necessary

The DLR Centre for Lightweight Production Technology in Augsburg
(ZLP Augsburg) was established to industrialise the automated
production of carbon-fibre-reinforced polymer (CFRP) components for
aerospace. By the early 2010s its robot cells, autoclaves, layup
machines and inspection systems were generating heterogeneous data
at a pace and variety that ordinary departmental file shares were
not built to absorb. A CFRP layup run produced trajectory logs from
the placement head, layer-by-layer images, thermocouple traces from
adjacent autoclaves, ultrasonic NDT scans of the cured part,
metrology reports, operator annotations, and — increasingly —
simulation outputs intended to be compared against the measured
result. Each of these artefacts lived in a tool-specific format,
on a tool-specific machine, with file-naming conventions that
varied by operator and shift.

The institutional response, mirroring a wider pattern across DLR
and the European aerospace research community, was to attempt to
build or adopt research-data-management systems specific to composite
manufacturing. Three such systems are documented or discoverable in
primary sources from the Augsburg context, and together they form
**Florian Krebs's own intellectual trajectory at DLR ZLP across
roughly a decade** — a continuity-of-architect that the public
record does not reveal but the internal artefacts and the author's
own testimony (2026-05-23) establish:

- **PRAESTO** (documented in the DLR eLib record at DLRK 2014):
  in the author's own words, a *professional / commercial product*
  brought into ZLP and *evaluated as a bad fit* for the centre's
  actual needs. The system did not survive the evaluation.
- **KIBID** (attested by internal code, c. 2016–2017): a
  company-built timeseries-and-entity system that worked technically
  but whose supplying company stopped providing adequate support —
  while remaining, in the author's testimony, *critically formative
  for what Shepard later became*.
- **CUBE iDMS** (a fully-designed and prototyped platform,
  2017–2020, with **Florian Krebs as the named architect and
  presenter** on the 2020-11-04 final presentation): the internal
  answer Krebs built after the external systems failed — a graph-
  oriented research-data platform whose architecture, on the
  primary-source evidence of the final presentation, is Shepard's
  architecture three to four years before Shepard appeared on
  GitLab.

PRAESTO is documented in the DLR open publication record but its
operational duration beyond 2014 is unverified from public sources;
KIBID is attested by internal code and named in the author's
testimony as company-built and vendor-abandoned; CUBE iDMS was
developed to remarkable completeness and saw use in the **IPRO**
research project but never reached institute-wide deployment.

Shepard, the system that is the principal subject of this thesis,
emerged at ZLP Augsburg in 2021 ([haaseShepard2021](#9-references),
[dlrZlpShepard](#9-references)) and is the first in this lineage to
reach operational use across multiple ZLP use cases under an open-
source licence. The public-facing record of its origin is thin: the
DLR ZLP project page makes no mention of any predecessor, and the
Zenodo software citation names a fresh authoring team unconnected
by name to the earlier systems. To a reader meeting Shepard today,
it looks like a 2021 greenfield project. It is not. The aim of this
chapter is to establish the historical continuity — what was
tried, what reached operational use and what stalled, and what
Shepard inherited or chose to leave behind — using such primary
sources as can honestly be reconstructed.

## 2. PRAESTO (c. 2014) — commercial product, evaluated and rejected as a bad fit

**What PRAESTO was, per Krebs's testimony (2026-05-23):**
a *professional / commercial product* brought into DLR ZLP to handle
CFRP research data, evaluated against the centre's actual needs,
and *found to be a bad fit*. No further claim is made here about
the supplier or the internals beyond what the eLib record carries;
the system did not survive the evaluation as the centre's
data-management platform.

PRAESTO is the earliest predecessor of Shepard for which a peer
conference record exists. The paper *Datenbank PRAESTO: Speicherung
von CFK-Forschungsdaten auf Fertigungsniveau* ("PRAESTO Database:
Storage of CFRP Research Data at Manufacturing Level") was presented
at the 63rd German Aerospace Congress (DLRK 2014) in Augsburg on
16–18 September 2014, with full authorship Stefan Nuschele, Thomas
Schmidt, Mildred Kießig, Thomas Mühlhausen, Bastian Wagenfeld and
Hajo Voss for the deposited paper record
([nuschelePraesto2014paper](#9-references), eLib 94077), and
Nuschele alone for the accompanying talk record
([nuschelePraesto2014talk](#9-references), eLib 94078). The
deposit at the DLR eLib carries keywords *database, manufacturing,
automation, data storage, non-destructive testing, process* but
contains no full text and no abstract; only the bibliographic
metadata is publicly available.

What public-record alone cannot establish, the artefact collection
(see §8) corroborates. The hostnames `kibid-proxy.praesto.lo` appear
in two c. 2017 Python sources — `kibid_exporter.py` (lines 19–20)
and `opcua_kibid_adapter.py` (in the KibidClient instantiation) —
both addressing PRAESTO as an internal-network top-level domain
(the `.lo` suffix being conventional for institute-local DNS at
that period). This establishes that the PRAESTO namespace was
still a routable network domain at ZLP three years after the DLRK
2014 paper. We report the hostname as factual evidence; we do
**not** infer from the hostname alone that PRAESTO functioned as
the *umbrella platform* hosting KIBID. The institutional structure
of PRAESTO at ZLP is beyond what the publicly retrievable evidence
can settle, and the user has provided no further detail on the
point.

Nuschele's broader eLib track between 2012 and 2015 — first-author
or co-author on roughly fifteen records spanning robotic ultrasonic
NDT, thermoplastic-processing robot cells, and the ZLP project
AZIMUT (eLib 94104) and PulForm (eLib 101718) — positions PRAESTO
as the data-management point in an arc of CFRP-manufacturing
research outputs from ZLP Augsburg in that period.

## 3. KIBID (c. 2016–2017) — company-built, vendor-abandoned, critical inspiration

**Confidence: MEDIUM-HIGH on existence** (substantial source code on
UNAS, embedded hostnames, Keycloak realm name). **HIGH on framing**
(Krebs's first-hand testimony, 2026-05-23).

**What KIBID was, per Krebs's testimony (2026-05-23):**
a system *built by an outside company*. It worked technically, but
the company providing it *stopped supporting it adequately* — and
in doing so, abandoned the customer-side capability that had been
built around it at ZLP. Critically, however, KIBID was
**conceptually formative for Shepard**: the shapes it demonstrated
— tagged timeseries as a first-class entity, REST API + Python SDK,
OPC UA bridge, Keycloak authentication — are shapes that survive
into Shepard today. The lesson Krebs's later work draws from KIBID
is structural: *these shapes are right; vendor dependency is wrong.*

KIBID is a structured timeseries-and-entity database with a Python SDK
(`kibidPy`), an OPC UA bridge that pushes live shop-floor data into
it, and an exporter that pulls data back out for analysis. No eLib
publication record has been found.

### 3.1 Source artefacts (UNAS)

- `kibid_exporter_sources.zip` (24 KB) — `kibidPy` Python SDK +
  `kibid_exporter.py` analyst-side exporter. Sub-packages: `client`,
  `timeseries` (with `_data_models.py`, `query_builder.py`,
  `services.py`), `util`, `tests`, `exceptions`.
- `opcua_kibid_adapter_sources.zip` (6 KB) — `opcua_kibid_adapter.py`
  forwarding live OPC UA data-change notifications into KIBID time
  series.

### 3.2 Architecture (reconstructed from code)

- **Auth:** Keycloak (`/auth/realms/kibid/protocol/openid-connect/token`,
  `opcua_kibid_adapter.py`)
- **Data model:** `TimeSeries(name, key, tags, virtual, columns,
  attributes, validation, timeseriesType, metric, unit)`
  — see `kibid_exporter_sources/kibidPy/timeseries/_data_models.py`
- **API style:** REST, prefix `/kibid/tardis/v2/timeseries`
  (`kibidPy/timeseries/services.py:`
  `self.conn.post('/kibid/tardis/v2/timeseries', data=d)`).
  The `tardis` segment is suggestive — KIBID's TS substrate appears to
  have been named separately from KIBID itself.
- **OPC UA ingest:** Python `opcua` client wraps OPC UA
  `DataChangeNotification` events into timeseries point dicts
  (`opcua_kibid_adapter.py:DebugHandler.datachange_notification`),
  pushed via `KibidClient.add_data_points`.
- **Deployment hostname:** `kibid-proxy.praesto.lo`
  (`kibid_exporter.py:19-20`)

### 3.3 Authorship and vintage

The `kibidPy/connector.py` and `kibidPy/timeseries/_data_models.py`
files carry `@author: gruenefeld` (Python module-docstring style) with
the timestamp `Created on Thu Sep 8 13:29:19 2016`. Given the
author's framing of KIBID as a company-built system (§3 intro),
`gruenefeld` is plausibly the **vendor-side author** of the Python
SDK shipped to ZLP under the KIBID product line. The
`kibid_exporter.py` and `opcua_kibid_adapter.py` files, by contrast,
carry the header *"Author(s): Florian Krebs florian.krebs@dlr.de"*:
these are the **DLR-side integration scripts** that Krebs built
around the vendor's SDK to make KIBID usable on the ZLP shop floor.
The boilerplate copyright string "2008-2011, (DLR)" in those files
is a template artefact (the actual code references the Python 3
`urllib.parse` fallback path and so cannot predate ~2014).

Inferred chronology: vendor-supplied `kibidPy` SDK ca. 2016;
DLR-side adapter and exporter wrappers by Krebs in the 2016–2017
timeframe to bridge ZLP shop-floor OPC UA sources into the KIBID
store. The earliest hard date is the `2016` literal in the
docstring; the latest is bounded only by the snapshot we took
(2026-05-23).

### 3.4 Why KIBID matters to Shepard — the critical-inspiration argument

KIBID was the timeseries layer of the ZLP shop floor in its working
period. The entity model (`TimeSeries` with `tags`, `attributes`,
`metric`, `unit`, `validation`, `timeseriesType: gauge|counter`) is
OpenTSDB-influenced but predates the InfluxDB-centric world that
CUBE iDMS would later move into (§4).

What survives from KIBID into Shepard — the **critical inspiration**
the author named in his 2026-05-23 testimony — is a set of shapes
that *do not appear* in PRAESTO or any earlier ZLP-Augsburg public
record:

- **Tagged timeseries as a first-class entity**
  (`name + tags + metric + unit + validation`) — the shape that
  Shepard's `TimeseriesContainer` carries today.
- **REST API + a Python SDK as the canonical access path.**
- **An OPC UA shop-floor bridge as a side process** that turns
  `DataChangeNotification` events into typed timeseries writes —
  the pattern that survives essentially unchanged into
  `shepard-timeseries-collector` today.
- **Keycloak / OpenID Connect** for both interactive (UI) and
  machine (API-key JWT) access.

The lesson Krebs drew from KIBID's vendor-abandonment shaped what
he built next: **the shapes were right, but the system carrying
them had to be DLR-owned, end to end.** That conclusion is the
through-line into §4.

### 3.5 Operational evidence — channel-namespace discipline

A retrieved IPRO-era operations TODO file (archived ZLP working
document, uploaded to AI working memory 2026-05-23) documents the
channel-namespace conventions in active use at the time. The
namespaces follow a strict top-level-domain pattern:

- `TPZ.SPS.<xyz>` — Siemens PLC (SPS = *Speicherprogrammierbare
  Steuerung*) signals from the TPZ cell
- `TPZ.R10.<xyz>` — KUKA R10 robot (see §4.5 figure) joint
  positions, TCP, force, I/O
- `IPRO.WW.<xyz>` — IPRO project, welding-related signals
- `IPRO.JIG.<xyz>` — IPRO project, jig / fixture signals
- `IPRO.GRIPPER.<xyz>` — IPRO project, gripper signals

The TODO also records: ADC scaling formula `(x/4096)*1000 = V`
(12-bit ADC), force-sensor calibration range tightened to ±50 N
via software, OPC UA integration with the robot and a router
recorded as **DONE**, and "Kibid Reihen anlegen" (create KIBID
series) listed as an active task — direct evidence that KIBID was
actively populated by hand for newly-instrumented channels.
Authentication used a per-user Keycloak account
(`user: mwillmeroth`, with the password redacted as a historical
credential; see §9). The `mwillmeroth` username is one piece of
direct authorship evidence connecting the KIBID-era operations team
to the **Mark Willmeroth** named as a co-author on the 2021 Zenodo
shepard record ([haaseShepard2021](#9-references)) — see §5 for the
implications for the lineage-glue confidence rating.

The channel-namespace discipline is itself a continuity point: KIBID's
`TPZ.*` / `IPRO.*` hierarchical channel identifiers parallel
shepard's `{measurement, device, location, symbolicName, field}`
5-tuple channel identity. Both encode scope and hierarchy into the
channel name; the shepard `aidocs/platform/87` migration from 5-tuple
to `shepardId` is the modernisation of this discipline, not its
abandonment.

## 4. CUBE iDMS (2017–2020) — Krebs's prototype, used in IPRO

**Confidence: HIGH on artefacts** — eight separate source
repositories on UNAS, multi-language client SDKs (Python, Java,
C++, Jupyter), a frontend with Keycloak integration, a
docker-compose ecosystem, version pins that date the work, **and a
35-slide first-party final presentation
([krebsIdms2020](#9-references)) authored by Florian Krebs and
dated 2020-11-04**. **MEDIUM on deployment scope** (IPRO-bounded
per the author's 2026-05-23 testimony).

**Authorship — Florian Krebs (DLR Augsburg ZLP).** The system was
designed, presented, and predominantly written by Florian Krebs at
DLR ZLP Augsburg. The 2020 final presentation
[krebsIdms2020](#9-references) carries the standard DLR footer
"Datenmanagement > Florian Krebs 12.12.2017" on multiple slides
(e.g. slides 10 and 16) and Krebs is the named author / presenter
across the deck. This is the central biographical fact of the
predecessor history: **the architect of CUBE iDMS is the architect
of the present thesis's principal subject**. Shepard, as built by
the 2021 GitLab authoring team, is the deployed embodiment of
Krebs's iDMS architecture — a continuity that the public record
nowhere states.

**Deployment status (Krebs's testimony, 2026-05-23):**
CUBE iDMS was developed as a **prototype** and saw use in the
**IPRO project** — a DLR research project (details unverified from
public sources at the time of writing). It did not graduate to
institute-wide deployment. The seven UNAS components therefore
represent a working prototype tested in a bounded research scope,
not a deployed production system retired in favour of Shepard. A
web pass for "IPRO" against the public DLR site, eLib, and search
engines returned no public project page; the project is plausibly
an internally-named research effort whose detail records are not on
the public web. The chapter names it without attempting to
characterise scope further.

CUBE iDMS (integrated Data Management System) is the immediate
*architectural* predecessor to Shepard. The system name **"CUBE
iDMS"** is confirmed verbatim by slide 14 of the final
presentation, which titles the architecture overview "CUBE iDMS –
Architecture Overview". Code-side corroboration:
the `idms_frontend_sources/README.md` calls itself
"This frontend for the CUBE IDMS"; the Java client uses the Maven
group ID `de.dlr.cube.idms.client.java` (see
`idms_examples_sources/java_idms_client/de.dlr.cube.idms.client.java.example/pom.xml`);
the Jupyter test notebook configures `api_conf.host =
"https://bt-au-cube2.intra.dlr.de/idms_project/webapi/v1"`
(`idms_examples_sources/jupyter_idms_client/02_connecting_to_idms.ipynb`).

CUBE in this context refers to the deployment platform / cluster
identity (the host pattern `bt-au-cube2.intra.dlr.de`, with `bt-au`
the internal abbreviation for the Bauweisen Augsburg group); iDMS
is the application that ran on it. Slide 14 of
[krebsIdms2020](#9-references) is the authoritative reference for
the name's composition.

### 4.0 The final presentation as primary source

The 2020-11-04 final presentation
([krebsIdms2020](#9-references)) is the load-bearing primary source
for this section. Each of the architectural and design claims below
is grounded in a specific slide. The relevant slides for the thesis
record are:

- **Slide 10 ("Vision: One data base – many digital twins"):**
  Krebs's framing of the *purpose* of the system. Three views
  (Part / Process / Resource) onto the same underlying data, as
  *the basis for digital twins*: "Enables different views
  (~ digital twins) on same data base → single source of truth."
  Shepard today is the deployed digital-twin substrate that this
  slide envisioned three years before Shepard appeared.

- **Slide 12 ("Data Pipeline Model"):** three input modes — manual,
  semi-automated / scripted, automatic — with generated client
  libraries and subscription-based change notification. Shepard's
  importer plugin family and the `shepard-plugin-importer` design
  trace directly to this slide.

- **Slide 14 ("CUBE iDMS – Architecture Overview"):** the system
  name. The architecture, in Krebs's own slide text:
  > "Web-Based UI for basic tasks (import / export / basic
  > visualization). REST API for complex analytics.
  > **Graph-oriented data-base for data relationships and provenance
  > information.** Specific data store according to data type
  > (performance / scalability) — **no one-size-fits all!** Enables
  > integration of PLM, SCM, Sharepoint…"

  This is Shepard's architecture, articulated three to four years
  before the public Shepard repository appeared. The "graph for
  relations + provenance, separate substrates for payload by data
  type" decomposition is precisely the principle that drives
  Shepard's Collection/DataObject/Container split today.

- **Slide 15 ("IT-Infrastructure / Technology-Stack"):** the iDMS
  stack, named line by line, and its mapping to Shepard:

  | CUBE iDMS (2017–2020), slide 15 | Shepard (2021 →) |
  | --- | --- |
  | MDMS = Neo4j | Neo4j |
  | Time series DB = InfluxDB | InfluxDB (→ TimescaleDB in v6) |
  | Document / Artifact DB = MongoDB | MongoDB |
  | Backend = Tomcat / Jersey | Jersey (then Quarkus) |
  | Frontend = Flask | Vue / Nuxt (the one substantial break) |
  | "Each modular component… in its own docker container" | docker compose |

  The substrate split is identical. The single architecturally
  consequential break is the move from a server-rendered Flask
  monolith to a Nuxt SPA on the frontend (see §7).

- **Slide 16 ("Excursion: The power of graph-oriented data bases"):**
  Krebs's justification for the Neo4j choice — "no need for
  normalization / strict schema → no need to have a complete data
  model at the start → the model can grow iteratively → agility."
  This is the argumentative ancestor of Shepard's SHACL-driven
  model evolution (see [`aidocs/semantics/98`](../semantics/98-shapes-and-shacl.md)).

- **Slide 17 ("Basic data model"):** "Process-oriented approach.
  Data / references are associated with steps within an
  'Experiment'. Multiple experiments are referenced by a 'Project'."
  This is `Project → Experiment → Step → Artifact` — which becomes
  Shepard's `Collection → DataObject → Container`.

- **Slide 18 ("Security excursion"):** Keycloak at
  `bt-au-keycloak.intra.dlr.de`, OpenID Connect, "users can
  generate API keys as long-living access tokens", "API keys are
  JSON web tokens (JWT)". Shepard's auth model inherits this end
  to end — and the *Keycloak realm itself*, named `kibid`, appears
  to have carried forward from KIBID through iDMS (which is why
  the realm name reads in the iDMS source as an artefact of the
  vendor system that came before, rather than as a current label).

### 4.1 Source artefacts (UNAS)

| Zip | Role |
| --- | --- |
| `idms_examples_sources.zip` | Python / Java / C++ / Jupyter client examples |
| `idms_frontend_sources.zip` | Flask + uWSGI + Keycloak web frontend |
| `idms-kafka-bridge_sources.zip` | Apache Kafka → iDMS batch-import bridge |
| `idms_oven_data_importer_sources.zip` | ZLP furnace-data importer |
| `idms_pandora_converter_sources.zip` | FE-simulation file → iDMS attachment converter |
| `idms_rce_sources.zip` | DLR Remote Component Environment workflow integration |
| `hotstuff_idms_importer_sources.zip` | Resistance-welding rig data importer |
| `python_idms_utils_sources.zip` | Shared client helpers |

A version-pinned dependency in
`idms_frontend_sources/setup.py:21` (`idms_client==2020.10.20`) gives
us a hard date marker: iDMS Python client release 2020-10-20.

### 4.2 Data model

The Python-requests and OpenAPI-generated client examples
(`idms_examples_sources/python_idms_client/api_client_factory.py`)
spell out the iDMS REST surface:

- **Entity hierarchy:** Project → Experiment → Step
- **Attachments:** Artifact (binary blob)
- **Reference types:** `MongoreferenceApi` (file references in MongoDB),
  `InfluxreferenceApi` (timeseries references in InfluxDB),
  `TimeseriesApi` (native iDMS-managed series)

This is **the same shape that surfaces in shepard today.** Compare
to upstream shepard 5.2.0:

- iDMS `Project / Experiment / Step` ↔ shepard `Collection / DataObject (+ nested DataObject)`
- iDMS `Artifact + Mongoreference + Influxreference` ↔ shepard `FileContainer + StructuredDataContainer + TimeseriesContainer`
- iDMS REST path `webapi/v1` ↔ shepard REST path `shepard/api/v1`

The continuity in shape is not subtle. The shepard data model is a
generalisation of the iDMS data model: where iDMS hard-coded the
substrate adapter into the reference type name
(`mongo_references.py`, `influx_references.py`), shepard abstracted
the substrate behind a unified `Container` concept.

### 4.3 Architecture

- **Frontend:** Python 3.8 + Flask + uWSGI + Authlib (Keycloak) +
  Bootstrap + jQuery; "highly modular design", "all logic operations
  performed within python code"; "JavaScript only used for UI event
  handling (e.g. AJAX)" (`idms_frontend_sources/README.md`)
- **Backend:** not in the UNAS drop (the API is exercised through the
  client SDKs; the backend was likely a Java service compiled into
  the artefacts that produced `idms_client` 2020.10.20)
- **Internal Python package index:**
  `bt-au-nexus.intra.dlr.de/repository/pypi-group/simple` — a Nexus
  mirror inside DLR Bauweisen Augsburg
- **Workflow integration:** iDMS-RCE bridge (`idms_rce`) ties iDMS
  records to runs of DLR's Remote Component Environment (RCE), an
  internal workflow tool; the `README.md` references PROV (W3C
  provenance) via `provtoolutils`.
- **Streaming ingest:** Kafka bridge (Kafka → iDMS batch endpoint),
  experimentally only — the bridge README explicitly calls itself
  *"a integration test of Apache KAFKA with IDMS"*
- **Authors:** Florian Krebs (frontend, RCE bridge, exporters);
  Michael Petsch (PANDORA light; aircraft-sizing FE framework, not
  the same lineage — see sidebar)

### 4.4 IPRO — the use case iDMS served

The IPRO project was the bounded research scope within which the
iDMS prototype was operationally tested. Two primary-source artefacts
retrieved 2026-05-23 (uploaded to AI working memory) sharpen what
IPRO was technically:

**Process domain (composite layup).** An archived process-flow
spreadsheet `20170721_Prozessablauf.xlsx` (dated 21 July 2017 in
its filename) records the IPRO production sequence as a CFRP-style
composite layup process. Documented states and operations include:

- "Ausgangssituation" (starting state): positioning foils ready,
  core ready, trailing-edge core ready, compressed-air supply
  active, tool-changer empty
- Manual stacking of cutouts on the tool, addressing the FWD
  (forward), AFT and LE (leading edge) zones of the laminate
- Placement of a glass layer + trailing-edge core
- Automatic drape-cylinder retract; suction activation on the
  LE tool molds
- Stapling tool (*Heftwerkzeug*) and handling tool
  (*Handhabungswerkzeug*) operations under robot control

This confirms IPRO as **direct technical lineage to MFFD**, not
analogy: both are CFRP-family composite-layup processes at ZLP
Augsburg. The instrumentation lessons learned in IPRO (force-sensor
calibration, OPC UA bridge, channel-namespace discipline) are the
in-house experience base on which the present MFFD work draws.

**Instrumentation (KUKA R10 robot cell).** A second retrieved
artefact — an IPRO Grafana dashboard screenshot captured at
**2017-11-06 14:50–15:15 UTC** — visualises live telemetry from the
TPZ cell's KUKA R10 robot, sourced from the KIBID timeseries store
described in §3. The dashboard simultaneously displays:

- Six-axis joint angles (`AXIS_ACT.A1..A6`, ±200° range)
- External axis E1 (linear track, 0–2.5 m range)
- TCP position XYZ (`POS_ACT.X/Y/Z`)
- TCP orientation in ZYX Euler angles
  (`POS_ACT.A/B/C`, ±180° range)
- Tool force in newtons (±500 N range)
- Discrete I/O channels under the
  `TPZ.R10.Inputs` and `IPRO.R10.I/O` namespaces — including
  `ENABLE_HOLD_LE`, `ENABLE_HOLD_TE`, `ENABLE_P_HOLD_AFT`,
  `ENABLE_P_HOLD_FWD`, `EXTEND_H1/H2`, `OPEN_TOOLCHANGER`,
  `ENABLE_STACK_GRIPPER` (the gripper and toolchanger states
  match the process operations documented in the XLSX)
- KRC runtime status: `ACT_TOOL=3`, `ACT_BASE=0`,
  `ROBTIMER=1211982238` (controller uptime)

Figure 4.X (suggested caption): *IPRO project Grafana dashboard,
2017-11-06: live KUKA R10 robot telemetry (six-axis joints, TCP
position and orientation, tool force, discrete I/O states) drawn
from the KIBID timeseries store. The dashboard exemplifies the
"kind of process-cell measurement" Shepard's timeseries substrate
now inherits responsibility for; iDMS was the prototype platform
that made this view possible.*

The screenshot, together with the XLSX process-flow and the
namespace TODO, establishes IPRO as a **working, instrumented,
operationally-monitored composite-layup cell at ZLP Augsburg in
2017**, with KIBID actively serving as its timeseries store and
iDMS in development as the umbrella platform. This is the
strongest evidence available that the iDMS/KIBID stack was an
integrated, deployed-in-its-research-scope system — not paper
designs.

### 4.5 External tools iDMS bridged to (sidebar — **not** in the lineage)

These are systems iDMS *imported from* or *integrated with*, not
predecessors of shepard:

- **PANDORA** — Petsch & Kohlgrüber's Python-based aircraft structural
  sizing framework at DLR Institute of System Architectures in
  Aeronautics (citation: [petschPandora2018](#references), MATEC Web
  of Conferences 2018). `PANDORA_light_for_iDMS` is a converter that
  attaches FE simulation results to iDMS records.
- **RCE** — DLR's open-source Remote Component Environment workflow
  tool (DLR-SC). `idms_rce` is a workflow-component shim, not a data
  store.
- **HotStuff** — the resistance-welding test rig at ZLP Augsburg. The
  `hotstuff_idms_importer` reads its experiment-folder layout
  (`Versuchsprotokoll.txt`, ultrasonic scan `.opd`, FUNCGEN `.ini`
  files; see `hotstuff_idms_importer_sources/notes.md`) and pushes
  it into iDMS.
- **Oven importer** — ZLP autoclave / furnace logs.

## 5. The lineage

```
2014               2016–2017         2017–2020             2021 →
PRAESTO          → KIBID           → CUBE iDMS          → shepard
(commercial        (company-built;    (Krebs prototype;    (the first to
 product;          vendor support     IPRO-scoped;         reach open-source
 evaluated at      abandoned;         distilled the        release, public
 ZLP; rejected     conceptually       lessons of the       citation, and
 as bad fit —      formative for      two earlier          operational use
 Krebs testimony   shepard — Krebs    systems)             across multiple
 2026-05-23)       testimony 2026-                         ZLP use cases)
                   05-23)
```

| Year(s) | System | Substrate | Scope | Status |
| --- | --- | --- | --- | --- |
| 2014 | PRAESTO | (commercial product, supplier not named here) | CFRP-research data store at ZLP Augsburg | Commercial product; evaluated at ZLP; rejected as bad fit (Krebs testimony 2026-05-23) |
| 2016–2017 | KIBID | Custom TS store (`/kibid/tardis/v2/`); Keycloak; vendor-supplied `kibidPy` SDK | Shop-floor timeseries + OPC UA ingest at ZLP | Company-built; vendor support abandoned; conceptually formative for Shepard (Krebs testimony 2026-05-23) |
| 2017–2020 | CUBE iDMS | Neo4j + MongoDB + InfluxDB; Tomcat / Jersey backend; Flask frontend; Keycloak | Project / Experiment / Step entity model; multi-language SDKs; Kafka bridge; RCE workflows; PROV provenance | Krebs-authored prototype; used in IPRO research project; never deployed institute-wide ([krebsIdms2020](#9-references)) |
| 2021 → | Shepard | Neo4j + MongoDB (files) + InfluxDB (TS) + Postgres; Java / Quarkus backend; Vue / Nuxt frontend; OIDC | DataObjects + Containers; open-source (Apache-2.0); plugin SPI under development in this fork | First in the lineage to reach open-source release, public citation (Zenodo DOI), and operational use across multiple ZLP use cases |

**Lineage-glue confidence: HIGH** (revised upward 2026-05-23 on
combined primary-source evidence — Krebs's testimony, the iDMS
final presentation, the IPRO operations artefacts). The lineage
is **Krebs's own intellectual trajectory across roughly a decade at
DLR ZLP**, attested in two ways:

1. *Authorial.* Krebs authored the DLR-side KIBID adapters
   (`kibid_exporter.py`, `opcua_kibid_adapter.py`) in c. 2016–2017,
   and authored CUBE iDMS through 2017–2020 (named on the final
   presentation [krebsIdms2020](#9-references) and on the iDMS
   frontend, RCE bridge, and exporters). PRAESTO is the system
   that preceded his own work in this domain at ZLP, evaluated and
   rejected per his testimony.

2. *Personal handoff into Shepard.* The IPRO operations TODO
   records `user: mwillmeroth` as the active KIBID-era account, and
   **Mark Willmeroth** is one of the four named authors on the 2021
   Zenodo Shepard record ([haaseShepard2021](#9-references)). This
   is the first piece of *personal* continuity evidence from iDMS
   operations into the 2021 Shepard authoring team. The other
   Shepard authors (Haase, Glück, Kaufmann) do not appear in
   iDMS/KIBID sources we hold; the continuity into the 2021 team
   is therefore predominantly *institutional and architectural*
   (same ZLP Augsburg, same architect's principles transcribed
   into a successor team's code) — but is no longer *zero* on
   personal handoff.

Shape-continuity remains the strongest evidence — Project/Experiment/
Step ≈ Collection/DataObject; mongo+influx reference split survives
in shepard's container types; OPC UA→TS-store bridge survives in
shepard-timeseries-collector; channel-namespace discipline survives
in shepard's 5-tuple identity (§3.5).

**The honest positioning claim that follows from this lineage:**
*Shepard is the one that landed.* Where earlier systems prototyped or
were deployed in narrower scopes, shepard is the first to be
open-sourced (Apache-2.0 on GitLab), publicly cited (Zenodo DOI), and
deployed across multiple ZLP use cases. That is structurally a
stronger positioning than "successor in a chain of deployed systems."

**Could not verify, despite searching:** any DLR ZLP communication
or DLR eLib publication that explicitly names shepard as the
successor to iDMS / CUBE / KIBID / PRAESTO. The official DLR ZLP
shepard project page ([dlrZlpShepard](#references)) mentions no
predecessor. Likewise, no public web search returned a project page
or eLib record for IPRO. The public-record silence on both ends of
the lineage is itself a data point — this history is mostly
preserved in internal artefacts (UNAS code, hostnames, group IDs)
rather than in published material.

## 6. What Shepard inherited (continuity)

These are concrete shapes and conventions that show up across the
predecessor lineage and survive into Shepard. Each row attributes
the *origin* of the pattern (which predecessor system first carries
it in the evidence) and the *current Shepard surface*. The
attribution matters: some patterns trace to **KIBID** (the
critical-inspiration system), others to **CUBE iDMS** (Krebs's own
synthesis), and a few to both.

| Pattern | Origin | Shepard surface today |
| --- | --- | --- |
| Tagged timeseries as a first-class entity | **KIBID** (`_data_models.py`: `TimeSeries(name, tags, metric, unit, validation, timeseriesType)`) | `TimeseriesContainer` + measurement / field / tag identity |
| REST API + generated client SDKs | **KIBID** (kibidPy) → broadened by CUBE iDMS into Python + Java + C++ + Jupyter SDKs | `clients/python`, `clients/java` |
| OPC UA shop-floor bridge as a side process | **KIBID** (`opcua_kibid_adapter.py`) | `shepard-timeseries-collector` |
| Keycloak / OIDC authentication, API-key JWTs | **KIBID** (`/auth/realms/kibid/…`) → carried through CUBE iDMS (slide 18 of [krebsIdms2020](#9-references)); the *realm name `kibid`* itself appears to have travelled from KIBID through iDMS as an artefact | Shepard OIDC + per-instance Keycloak realms |
| "Graph for relations + provenance" + "no one-size-fits-all substrate" | **CUBE iDMS** slide 14 of [krebsIdms2020](#9-references) | Neo4j entity graph + Container substrate split |
| Neo4j + MongoDB + InfluxDB substrate stack | **CUBE iDMS** slide 15 of [krebsIdms2020](#9-references) | same three substrates (Postgres added later in Shepard) |
| `Project → Experiment → Step → Artifact` entity hierarchy | **CUBE iDMS** slide 17 of [krebsIdms2020](#9-references) | `Collection → DataObject → Container` |
| Multi-substrate references as first-class API types (`Mongoreference`, `Influxreference`) | **CUBE iDMS** (REST surface) | `Container` family — substrate hidden behind container type |
| REST path `…/webapi/v1` style | **CUBE iDMS** (`idms_project/webapi/v1`) | `/shepard/api/v1` (preserved); `/v2/` is the first re-cut in this fork |
| W3C PROV vocabulary for provenance | **CUBE iDMS** (`idms_rce/readme.md` references `provtoolutils`) | Shepard PROV1a Activity capture; cf. F(AI)²R |
| Modular components in docker containers | **CUBE iDMS** slide 15 of [krebsIdms2020](#9-references) | docker compose stack |
| Schema-light graph model that grows iteratively | **CUBE iDMS** slide 16 of [krebsIdms2020](#9-references) (Krebs's explicit Neo4j justification) | SHACL-driven model evolution (`aidocs/semantics/98`) |
| Three input modes (manual / scripted / automatic) + generated clients | **CUBE iDMS** slide 12 of [krebsIdms2020](#9-references) | importer plugin family + `shepard-plugin-importer` design |
| "One data base – many digital twins" framing | **CUBE iDMS** slide 10 of [krebsIdms2020](#9-references) | Shepard's role as digital-twin substrate (cf. `aidocs/42`) |
| Hierarchical channel-namespace discipline | **KIBID/IPRO operations** (`TPZ.*`, `IPRO.*`; §3.5) | 5-tuple channel identity migrating to `shepardId` (`aidocs/platform/87`) |
| `bt-au-*` Bauweisen-Augsburg deployment-naming convention | **CUBE iDMS** (`bt-au-cube2`, `bt-au-nexus`, `bt-au-keycloak`) | institute-internal Shepard hosting at DLR follows the same pattern |

The continuity is dense and specific. The shapes did not arrive in
Shepard by accident; they arrived because the architect of the
predecessor system distilled them, presented them in a 35-slide
final document, and a 2021 authoring team (one of whom — Willmeroth
— was already in the IPRO operations loop) transcribed them into a
Quarkus / Vue stack that could ship.

## 7. Designs that didn't make it beyond IPRO (discontinuity)

These are shapes the iDMS prototype implemented that shepard chose
not to carry into the deployed system. Because iDMS never reached
institute-wide deployment, "discontinuity" here means "designs that
never had to face production reality" — *not* "features Shepard
chose to drop from a working production system." Over-scope may have
been a factor in why iDMS stayed at prototype scale; shepard's
narrower-scope plus plugin-first approach (see CLAUDE.md
§"Always: think plugin-first") is the structural answer to that.

Framed as **shapes the new system chose**, not gaps:

- **The Kafka streaming-ingest experiment.** The
  `idms-kafka-bridge` README labels itself an "integration test"
  rather than a production component. Shepard today has no canonical
  Kafka ingest path; the timeseries collector handles live data
  directly. The streaming pattern remains future work (cf.
  `aidocs/16` backlog).
- **The native timeseries substrate.** KIBID's `/kibid/tardis/v2/`
  endpoints implemented a custom TS store with its own data model.
  Shepard delegates timeseries to InfluxDB (and now to TimescaleDB in
  the v6 design line), accepting the substrate's own data model
  rather than projecting through a shepard-internal one.
- **The CUBE platform brand.** "CUBE IDMS" as a marketing identity
  did not survive. Shepard is named for itself.
- **Multi-language native SDKs as a same-repository concern.** iDMS
  shipped C++, Java, Python and Jupyter client examples in a single
  `idms_examples` repository (eight subprojects under the same
  GitLab group). Shepard has split client codegen across separate
  per-language repositories.
- **Explicit FE-simulation converter as a first-party concern.**
  `idms_pandora_converter` was a first-party iDMS component that
  knew about FE-solver file formats. Shepard's analogous capability
  is being framed as a plugin (the plugin-first rule in `CLAUDE.md`
  §"Always: think plugin-first for new features").
- **The Flask-monolith frontend pattern.** iDMS rendered HTML on
  the server with Jinja templates and used JavaScript only for
  AJAX. Shepard is a Nuxt 3 SPA. This is a defensible architectural
  choice in either direction, but it does mean every iDMS frontend
  test-pattern (page rendering, form posts) had to be rebuilt.
- **The internal Nexus PyPI mirror dependency.** iDMS pinned its
  client to `bt-au-nexus.intra.dlr.de/repository/pypi-group/simple` —
  the client was only installable from inside DLR-Bauweisen-Augsburg.
  Shepard publishes its Python client to PyPI proper.

## 8. Methodological note on the sources

The evidentiary basis for this chapter is uneven in a way that
deserves explicit acknowledgement, and is itself part of the
chapter's contribution.

PRAESTO is attested in the public DLR eLib by a deposited record of
a 2014 conference presentation, with named authors and an
institutional affiliation, but without a full text or abstract.
The keywords and the two-sentence summary surfaced through
search are the limit of the public record. The framing of PRAESTO
as a commercial product brought into ZLP and rejected as a bad fit
(§2) does not come from the eLib record; it comes from the author's
own testimony recorded during the writing of this chapter. Any
claim in this chapter that exceeds the bibliographic metadata of
the 2014 deposit rests on inference from adjacent evidence and on
that testimony.

KIBID is attested only by working client code in the artefact
collection. There is no conference paper, no eLib deposit, no
public web page. The code's existence, its hostnames, its
authentication patterns and its OPC UA bridge are facts; the
framing of KIBID as company-built and vendor-abandoned (§3) comes
from the same testimonial source as the PRAESTO framing. The
inference that `gruenefeld` was a vendor-side rather than DLR-side
author is plausible but not directly evidenced in the code itself.

CUBE iDMS is the system in this chapter with the most balanced
evidence base. The artefact collection of seven projects, the
2020-11-04 final presentation deposited as `20201104_iDMS_final.pptx`,
the 2017-07-21 process-flow spreadsheet, the IPRO operations TODO
and the 2017-11-06 Grafana dashboard screenshot together establish
the system's design, its IPRO deployment, and the technical texture
of its use to a level that would, under different historiographical
circumstances, support a confident operational claim. The critical
historical claim — that the system never reached institute-wide
deployment beyond IPRO — comes from the author's own testimony and
is consistent with the absence of any public deployment record.

The artefact collection that bears the bulk of the chapter's
evidence is itself a primary historical source of unusual
provenance. It is the contents of a researcher's NAS, retrieved on
23 May 2026 from `/mnt/pve/unas/media/random/` on institutional
storage shared with the present author. Such a collection is not a
curated archive: there is no guarantee that it preserves every
component of every system, nor that the components preserved are
the final versions. What it does establish, beyond reasonable
doubt, is *that these systems existed in the form the code
describes* — that CUBE iDMS was not a paper project, that a Java
client and a C++ client and a Flask frontend were actually written,
that the OPC UA adapter to KIBID actually addressed a server at a
specific hostname in 2017, and that the IPRO cell was actually
instrumented and operationally monitored at the date the Grafana
screenshot records. The collection does not, on its own, tell us
who used those systems, for how long, or in what wider scope; for
that, the historical record requires participant testimony of the
kind the present chapter has been able to draw on directly from
the author of the system itself.

A reader of the thesis who lacks access to the same institutional
storage must take the artefact-based claims on the author's
evidence-of-record. This is a known limitation. The bibliography
entry `unasZlpCodeDrop2026` in `docs/_data/references.bib` exists
to make the evidentiary basis traceable rather than to serve as
a verifiable public reference; the working documents
(`20201104_iDMS_final.pptx`, `20170721_Prozessablauf.xlsx`, the
operations TODO and the Grafana screenshot) are cited likewise as
internal historical sources rather than as published artefacts.
The chapter's evidentiary contribution is the integration of these
internal sources with the public eLib record and the author's
testimony into a single, citable historiography of the lineage.

## 9. Reflexivity

This chapter is being written by an AI assistant working at the
direction of **Florian Krebs** — who is both the principal user of
the assistant on this project and the author of the system the
chapter describes (CUBE iDMS, §4), as well as a contributor to its
predecessor's DLR-side integration (KIBID adapters, §3).

The recursion runs one layer deeper than the typical "AI-assisted
historiography" disclaimer would handle:

- CUBE iDMS's stated vision in 2017–2020 was *"one data base —
  many digital twins"* ([krebsIdms2020](#9-references), slide 10).
- Shepard is the deployed digital-twin substrate that vision pointed
  at.
- The AI assistant writing this chapter uses Shepard's design
  principles — provenance capture, multi-substrate storage, the
  many-views model — to *describe* CUBE iDMS, which originated those
  principles.

The construction is not vicious: each step in §§2–6 is grounded in a
primary source (PPTX slides, UNAS source code, the IPRO operations
artefacts, the author's own testimony) and attributions in §6 are
made line-by-line. But the methodological posture should be named.
This chapter is `fair2r:modeOfProduction "ai"` content with
`fair2r:reviewedBy` Krebs himself in the testimony-and-correction
loop of 2026-05-23 — *three* layers of human-issued corrections
shaped the present framing (correction log:
[`aidocs/agent-findings/predecessor-history-correction-2026-05-23.md`](../agent-findings/predecessor-history-correction-2026-05-23.md)).

Thesis examiners reading this section in a later academic context
should know: the human in the loop is both subject and editor. The
honesty of *naming that* is what makes the artefact defensible. The
elegance of hiding it would not.

## 10. Conclusion

The lineage of research-data-management systems at DLR ZLP
Augsburg before Shepard is best read not as a succession of
deployed services but as a sequence of attempts on the same
problem, each carried in part by the same hands and each with
its own evidence basis. PRAESTO is the moment a commercial
product was evaluated and rejected; the conclusion drawn was
that ZLP's needs were too particular for an off-the-shelf
manufacturing-data database. KIBID is the moment a tagged-
timeseries shape with OPC UA reach and Keycloak authentication
arrived on the shop floor under vendor sponsorship, worked
technically, and was abandoned by its supplier — leaving behind
shapes that proved formative for everything that followed. CUBE
iDMS is the moment Krebs built the in-house answer those two
experiences pointed at: a graph-oriented platform whose
architecture — Project / Experiment / Step with multi-substrate
references, Keycloak authentication, multi-language SDKs, PROV
provenance, RCE integration — is recognisably Shepard's, three
to four years before Shepard appeared on GitLab. iDMS reached
operational use in the bounded scope of the IPRO project but
never graduated to institute-wide deployment.

Shepard, the system the thesis takes as its subject, is the
first in this lineage to reach an operational state across
multiple ZLP use cases and to be released publicly under an
open-source licence. Reading its design choices against the
predecessor record makes the choices legible: the rejection of
vendor lock-in as the structural lesson absorbed from PRAESTO
and KIBID; the narrow open-source core with broad plugin
extension surface as the architectural answer to CUBE iDMS's
unshipped breadth; the timeseries-as-first-class commitment as
the KIBID shape carried forward; the OPC UA and live-
instrumentation reach as the PRAESTO/KIBID/iDMS inheritance;
and the explicit choice of public open-source as the
discontinuous step that the institute-internal predecessors
never took.

The personal continuity established by the `mwillmeroth` →
Mark Willmeroth link, and the architect continuity established
by Krebs's authorship across KIBID adapters, CUBE iDMS and
this fork of Shepard, together close the public-record gap
that the official ZLP project page leaves open. Shepard is
not a 2021 greenfield system; it is the deployed embodiment of
roughly a decade of work on the same problem at the same
institute, by partly the same people, finally arriving in a
form that ships.

What this chapter cannot, and does not attempt to, establish
is the counterfactual question of whether CUBE iDMS might
have reached institute-wide deployment under different
circumstances. That question belongs to participant testimony
beyond the testimonial work already integrated here, and to
archival work that subsequent chapters or follow-up research
can take up.

## 11. References

**iDMS primary source** (the load-bearing primary source for §4):

- [krebsIdms2020](#bib) — Krebs, F. (2020). *CUBE iDMS — Final
  Presentation.* 35-slide internal presentation. Citation:
  *"iDMS final presentation, Krebs F., DLR Augsburg ZLP,
  2020-11-04, archive copy retrieved 2026-05-23"* (filename
  `f3fd7c26-20201104_iDMS_final.pptx`, ~18 MB; uploaded to the AI
  assistant's working memory by Krebs on 2026-05-23). Load-bearing
  evidence for §4: system name ("CUBE iDMS", slide 14),
  architecture (slide 14: graph DB + per-data-type substrate),
  technology stack (slide 15: Neo4j + InfluxDB + MongoDB +
  Tomcat/Jersey + Flask + docker containers), data model (slide
  17: Project / Experiment / Step / Artifact), vision (slide 10:
  "one data base — many digital twins"), data-pipeline model
  (slide 12: three input modes), Neo4j rationale (slide 16:
  schema-light, iterative model growth), and security model
  (slide 18: Keycloak + API-key JWTs at
  `bt-au-keycloak.intra.dlr.de`).

eLib records (PRAESTO):

- [nuschelePraesto2014paper](#bib) — Nuschele, Schmidt, Kießig,
  Mühlhausen, Wagenfeld, Voss (2014). *Datenbank PRAESTO: Speicherung
  von CFK-Forschungsdaten auf Fertigungsniveau.* DLRK 2014, Augsburg.
  eLib record [94077](https://elib.dlr.de/94077/).
- [nuschelePraesto2014talk](#bib) — Nuschele (2014). Same title.
  eLib record [94078](https://elib.dlr.de/94078/).

Related ZLP-Augsburg eLib records by the same authoring community
(Nuschele, Schmidt, Krebs, Dutta) — included for context:

- AZIMUT Abschlussbericht 2014 (Körber, Nieberl, Schmidt-Eisenlohr,
  Nuschele, Krebs, Kaps), eLib [94104](https://elib.dlr.de/94104/).
- PulForm Abschlussbericht 2015 (Nuschele, Endraß, Fischer, Frommel,
  Mayer, Dutta, Schmidt, Ullmann),
  DLR-IB 435-2015/108, eLib [101718](https://elib.dlr.de/101718/).
- Kostenreduzierung in der Qualitätsprüfung durch robotisch basierte
  zerstörungsfreie Prüfung (Krebs, Nuschele, 2012), eLib
  [81012](https://elib.dlr.de/81012/).

External / PANDORA (sidebar, not in lineage):

- [petschPandora2018](#bib) — Petsch & Kohlgrüber (2018). *PANDORA — A
  Python-based framework for modelling and structural sizing of
  transport aircraft.* MATEC Web of Conferences (EASN–CEAS 2018).

Shepard itself:

- [haaseShepard2021](#bib) — Haase, Glück, Kaufmann, Willmeroth
  (2021). *shepard — storage for heterogeneous product and research
  data.* Zenodo, DOI [10.5281/zenodo.5091604](https://doi.org/10.5281/zenodo.5091604), v1.0.0,
  Apache-2.0.
- [dlrZlpShepard](#bib) — DLR ZLP project page,
  [shepard — storage for heterogeneous product and research data](https://www.dlr.de/en/zlp/research-transfer/projects/projects-from-augsburg/project-archive-zlp-augsburg/shepard-storage-for-heterogeneous-product-and-research-data).

UNAS code-drop primary sources (snapshot taken 2026-05-23 from
`/mnt/pve/unas/media/random/`):

| File | Size | First-evidence pointer |
| --- | --- | --- |
| `kibid_exporter_sources.zip` | 24 KB | `kibid_exporter.py:19-20` hostname `kibid-proxy.praesto.lo`; `kibidPy/connector.py:5` `@author: gruenefeld`, dated `Thu Sep 8 13:29:19 2016` |
| `opcua_kibid_adapter_sources.zip` | 6 KB | Keycloak realm URL `/auth/realms/kibid/protocol/openid-connect/token` |
| `idms_frontend_sources.zip` | 105 KB | `README.md:1` "This frontend for the CUBE IDMS"; `setup.py:21` `idms_client==2020.10.20` |
| `idms_examples_sources.zip` | 180 KB | `java_idms_client/.../pom.xml` group `de.dlr.cube.idms.client.java`; notebook config host `bt-au-cube2.intra.dlr.de/idms_project/webapi/v1` |
| `idms-kafka-bridge_sources.zip` | 4 KB | `README.md` self-describes as "integration test of Apache KAFKA with IDMS" |
| `idms_rce_sources.zip` | 746 KB | `readme.md` references DLR `provtoolutils` (W3C PROV) |
| `hotstuff_idms_importer_sources.zip` | 152 MB | `notes.md` documents the ZLP HotStuff rig data layout |
| `idms_pandora_converter_sources.zip` | 8 KB | `README.md` "Converts proprietary finite element simulation files to PANDORA compliant datasets and attaches them to the iDMS" |
| `PANDORA_light_for_iDMS_sources.zip` | 488 KB | `setup.py` author Michael Petsch |
| `idms_oven_data_importer_sources.zip` | 5 KB | (small importer for ZLP autoclave logs) |
| `python_idms_utils_sources.zip` | 7 KB | `readme.md` references `git+https://gitlab.dlr.de/zlp-augsburg/python-idms-utils.git` |

The UNAS files have mtime `2025-09 / 2026-05` — these are snapshot
times of when they were copied into the UNAS share, not original
creation dates. Original creation dates for individual source files
are inferable from in-source `Created on …` docstrings and from
version pins; we have used those throughout.

Primary historical sources retrieved 2026-05-23 (uploaded by the
author to AI working memory; archived ZLP working documents):

| Source | Date | Evidence supplied |
| --- | --- | --- |
| `20201104_iDMS_final.pptx` (iDMS final presentation) | 2020-11-04 | Krebs F., DLR Augsburg ZLP — iDMS architecture and IPRO use case snapshot at the end of the prototype's active development |
| `20170721_Prozessablauf.xlsx` (IPRO process flow) | 2017-07-21 | Composite-layup process sequence at the IPRO cell — confirms IPRO as direct CFRP technical lineage to MFFD (§4.4) |
| IPRO operations TODO (plain-text) | undated, c. 2017 | Channel-namespace taxonomy `TPZ.*` / `IPRO.*`; OPC UA integration recorded DONE; force-sensor calibration parameters; KIBID series-creation worklist; `mwillmeroth` username (Mark Willmeroth, later 2021 shepard co-author) — password `[redacted historical credential]` (§3.5) |
| IPRO Grafana dashboard screenshot | 2017-11-06 14:50–15:15 UTC | KUKA R10 live telemetry view: 6-axis joints, TCP position/orientation, tool force, I/O states — sourced from KIBID; figure-suitable for thesis use (§4.4) |

For the figure-suitable Grafana screenshot specifically, the
caption proposed in §4.4 is the recommended thesis-register form.
The credential `mwillmeroth` appears in the public-facing version
of this document as the username only; the historical password is
not quoted (treated as a redacted internal credential per the
project policy on no-redactions-in-commands-and-yes-redactions-on-
historical-secrets).

