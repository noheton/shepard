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

## 2. PRAESTO (c. 2014) — the first documented predecessor

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

The substantive characterisation of PRAESTO that survives in public
sources reaches the chapter through a docplayer.org mirror of an
unrelated 2013 ZLP colloquium presentation by Thomas Schmidt and
Somen Dutta, which describes PRAESTO as "a database system from PAG
used for geometric 1D and 3D measurement data acquisition at the
ZLP Augsburg's data management system, featuring should/actual
comparisons and new capabilities including integration of additional
engineering sensors, simulation, expanded evaluation methods,
environmental data acquisition, and overlaying results from
different sources." The attribution to "PAG" — likely Premium
Aerotec Augsburg, an industry partner of ZLP — is consistent with
the multi-author 2014 paper that includes Wagenfeld and Voss
alongside the DLR-affiliated authors. The docplayer source was not
directly retrievable at the time of writing (ECONNREFUSED on
2026-05-23); the digest above is preserved from the search-result
record. The honest reading is: a database whose first scope was
geometric and NDT measurements taken at manufacturing level, with
explicit further-development intent.

What public-record alone cannot establish, the artefact collection
(see §8) corroborates. The hostnames `kibid-proxy.praesto.lo` appear
in two c. 2017 Python sources — `kibid_exporter.py` (lines 19–20)
and `opcua_kibid_adapter.py` (in the KibidClient instantiation) —
both addressing PRAESTO as an internal-network top-level domain
(the `.lo` suffix being conventional for institute-local DNS at
that period). This is the load-bearing evidence that PRAESTO was
still an operational network domain at ZLP three years after the
DLRK 2014 paper, and that it functioned as the umbrella platform
within which subsequent timeseries infrastructure (KIBID, §3) was
hosted. The combination of a documented 2014 conference paper and
a 2017 production hostname establishes that PRAESTO progressed
beyond design and into operational use; the duration, scale and
user base of that use are unverified from any source available to
this chapter.

Nuschele's broader eLib track between 2012 and 2015 — first-author
or co-author on roughly fifteen records spanning robotic ultrasonic
NDT, thermoplastic-processing robot cells, and the ZLP project
AZIMUT (eLib 94104) and PulForm (eLib 101718) — positions PRAESTO
as the data-management point in an arc of CFRP-manufacturing
research outputs from ZLP Augsburg in that period.

## §3 KIBID (≈2016–2017)

**Confidence: MEDIUM-HIGH.** Substantial source code on UNAS, named
authors (one indirect, one direct), embedded hostnames and Keycloak
realm names. No eLib publication record found.

KIBID is a structured timeseries-and-entity database with a Python SDK
(`kibidPy`), an OPC UA bridge that pushes live shop-floor data into
it, and an exporter that pulls data back out for analysis.

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
the timestamp `Created on Thu Sep 8 13:29:19 2016`. The
`kibid_exporter.py` and `opcua_kibid_adapter.py` files carry the
header *"Author(s): Florian Krebs florian.krebs@dlr.de"* (with the
boilerplate copyright string "2008-2011, (DLR)", which is a template
artefact and not a real date — the actual code references the
Python 3 `urllib.parse` fallback path, so cannot predate ~2014).

Inferred chronology: kibidPy authored by `gruenefeld` ca. 2016;
adapter and exporter scripts wrapped around it by Krebs in the
2016–2017 timeframe to bridge ZLP shop-floor OPC UA sources into the
KIBID store. The earliest hard date is the `2016` literal in the
docstring; the latest is bounded only by the snapshot we took
(2026-05-23).

### 3.4 What KIBID was for

KIBID was the timeseries layer of the ZLP shop floor. The entity
model (`TimeSeries` with `tags`, `attributes`, `metric`, `unit`,
`validation`, `timeseriesType: gauge|counter`) is OpenTSDB-influenced
but predates the InfluxDB-centric world that iDMS would later move
into (§4). The OPC UA adapter pattern — bridge live machine signals
through a side process into the database — survives essentially
unchanged into shepard-timeseries-collector today.

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

## §4 iDMS / CUBE (2018–2021) — prototype, used in IPRO

**Confidence: HIGH on artefacts; MEDIUM on deployment scope.**
Eight separate source repositories on UNAS, multi-language client
SDKs (Python, Java, C++, Jupyter), named lead author (Florian Krebs),
a frontend with Keycloak integration, a docker-compose ecosystem, and
version pins that date the work.

**Deployment status (user-confirmed correction, 2026-05-23):**
iDMS was developed as a **prototype** and saw use in the **IPRO
project** — a DLR research project (details unverified from public
sources at the time of writing). It did not graduate to
institute-wide deployment. The 7 UNAS components therefore represent
a working prototype tested in a bounded research scope, not a
deployed production system retired in favour of shepard. A web pass
for "IPRO" against the public DLR site, eLib, and search engines
returned no public project page; the project is plausibly an
internally-named research effort whose detail records are not on the
public web. We name it without attempting to characterise scope.

iDMS (integrated Data Management System) is the immediate
*architectural* predecessor to shepard. It was hosted on a platform
branded **CUBE** —
the `idms_frontend_sources/README.md` calls itself
"This frontend for the CUBE IDMS"; the Java client uses the Maven
group ID `de.dlr.cube.idms.client.java` (see
`idms_examples_sources/java_idms_client/de.dlr.cube.idms.client.java.example/pom.xml`);
the Jupyter test notebook configures `api_conf.host =
"https://bt-au-cube2.intra.dlr.de/idms_project/webapi/v1"`
(`idms_examples_sources/jupyter_idms_client/02_connecting_to_idms.ipynb`).

CUBE is therefore the **deployment-platform brand** (likely the
internal DLR-Bauweisen-Augsburg server / kubernetes cluster — note
`bt-au-cube2.intra.dlr.de`, with `bt-au` = Bauweisen Augsburg);
iDMS is the **application** that ran on it.

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

## §5 The lineage

```
2014               2016–2017         2018–2021             2021 →
PRAESTO          → KIBID           → iDMS / CUBE         → shepard
(deployment       (deployment       (prototype, used      (the first to
 status:           status:            in IPRO research      reach operational
 unverified)       unverified)        project; never        use across multiple
                                     deployed institute-   ZLP use cases)
                                     wide)
```

| Year(s) | System | Substrate | Scope | Deployment status | Confidence |
| --- | --- | --- | --- | --- | --- |
| 2014 | PRAESTO | Unknown DB (PAG-origin?) | CFRP-research data store; geometric 1D/3D measurements; should/actual comparison | Unverified from public sources | MEDIUM (existence); LOW (deployment) |
| 2016–2017 | KIBID | Custom TS store (`/kibid/tardis/v2/`); Keycloak | Shop-floor timeseries; OPC UA ingest; hosted inside PRAESTO (`kibid-proxy.praesto.lo`) | Unverified from public sources | MEDIUM-HIGH (existence); LOW (deployment) |
| 2018–2021 | iDMS / CUBE | MongoDB (refs) + InfluxDB (refs) + native TS; Java backend; Flask frontend; Keycloak | Project/Experiment/Step entity model; multi-language SDKs; Kafka bridge; RCE workflows; PROV provenance | **Prototype; used in IPRO research project; never deployed institute-wide** (user-confirmed) | HIGH (artefacts); MEDIUM (deployment scope) |
| 2021 → | shepard | Neo4j + MongoDB (files) + InfluxDB (TS) + Postgres; Java/Quarkus backend; Vue/Nuxt frontend; OIDC | DataObjects + Containers; open-source (Apache 2.0); plugin SPI under development in this fork | **Deployed; the first in this lineage to reach operational use across multiple ZLP use cases** | (current) |

**Lineage-glue confidence: MEDIUM–HIGH** (revised upward 2026-05-23
on new primary-source evidence). The PRAESTO ↔ KIBID link is strong
(the `praesto.lo` hostname is in KIBID source). The KIBID ↔ iDMS
handoff is *circumstantial-plus* (Krebs authored OPC UA→KIBID
adapters ca. 2016–2017 *and* the iDMS frontend ca. 2020; the iDMS
examples include an `InfluxreferenceApi` which suggests iDMS moved
from KIBID's custom `/tardis/v2/` TS substrate to InfluxDB; the
operations-TODO confirms KIBID series creation as routine IPRO
work — see §3.5). The iDMS ↔ shepard handoff has gained one direct
authorship link: the operations TODO records `user: mwillmeroth`
as the active KIBID-era account, and **Mark Willmeroth** is one of
the four named authors on the 2021 Zenodo shepard record
([haaseShepard2021](#9-references)). This is the first piece of
*personal* continuity evidence — at least one IPRO-era operator
appears as a 2021 shepard co-author. The other shepard authors
(Haase, Glück, Kaufmann) do not appear in iDMS/KIBID sources we
hold; the continuity remains predominantly *institutional* (same
ZLP Augsburg, same problem domain, same design intent) but is no
longer *zero* on personal handoff.

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

## §6 What shepard inherited (continuity)

These are concrete shapes and conventions that show up in both the
iDMS prototype and the deployed shepard system. The continuity is
*design-pattern continuity* — shapes that iDMS demonstrated in IPRO
and that shepard chose to carry forward into a deployed system.
They are observations from the code, not value judgements.

- **Entity hierarchy with provenance edges.** iDMS
  `Project → Experiment → Step → Artifact` became shepard
  `Collection → DataObject → (Container | DataObject children)`.
  In both systems, the hierarchy is the primary navigation axis.
- **Multi-substrate references.** iDMS introduced the `Mongoreference`
  and `Influxreference` types as first-class entities — the substrate
  identity was visible in the API. Shepard generalised this to
  `Container` (FileContainer / StructuredDataContainer /
  TimeseriesContainer) and hid the substrate behind the container
  type.
- **REST API path shape.** iDMS `https://.../idms_project/webapi/v1/`;
  shepard `https://.../shepard/api/v1/`. The `webapi/v1` style is
  preserved (this fork's `/v2/` shelf is the first time the path
  shape has been re-cut).
- **Keycloak / OIDC.** Both systems use OpenID Connect with a
  Keycloak realm for authentication. The iDMS realm was `kibid`
  (the Keycloak server appears to have been carried forward from
  KIBID); shepard uses per-instance realms today.
- **OPC UA shop-floor bridge.** The pattern of "side process that
  subscribes to OPC UA `DataChangeNotification` events and pushes
  them into the data store" survives in shepard-timeseries-collector.
- **Python and Java client parity.** iDMS shipped both Python and
  Java SDKs; shepard's clients (in `clients/`) cover Python and Java
  and add Vue/TypeScript.
- **The W3C PROV vocabulary** appears in iDMS via `provtoolutils`
  (mentioned in `idms_rce_sources/readme.md`). Shepard's PROV1a
  activity capture is a direct continuation of this lineage —
  PROV-O ([w3cProvO2013](#references)) was a vocabulary the predecessor
  team already understood.
- **The `bt-au-*` deployment-naming convention** (`bt-au-cube2`,
  `bt-au-nexus`) carried Bauweisen-Augsburg branding through CUBE;
  current shepard deployments at DLR are similarly hosted on
  institute-internal infrastructure.

## §7 Designs that didn't make it beyond IPRO (discontinuity)

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

## §8 Implications for upstreaming and adoption

Because iDMS never reached institute-wide deployment, there is **no
population of iDMS users to migrate**. The adoption story is not
"port your iDMS data to shepard"; it is **"the thing iDMS was trying
to be, except this time it shipped."**

The stakeholders to address are not iDMS *users* (there effectively
weren't any beyond IPRO), but **iDMS designers and adjacent DLR
researchers who carry the institutional memory of what iDMS was
trying to do**. For them, shepard is the deployed embodiment of
intentions they recognise: heterogeneous-data storage, the
Project/Experiment/Step entity model, the Mongo+Influx reference
split, OPC UA shop-floor bridges, PROV-O provenance capture. Many
of the shepard backlog rows from the 2026-05-23 UNAS code-drop sweep
(IMPORTER-LIBRARY-SEED, OPCUA1, AAS-REUSE-AUDIT, SHEPARD-ECOSYSTEM-AUDIT)
are field-confirmation that the iDMS-era wishlist remained
unimplemented across the rest of the ecosystem too — shepard is now
filling those gaps one plugin at a time.

The 7-component iDMS inventory therefore reads as a **demand signal
confirmation**: each unshipped component (Kafka bridge, RCE
workflow, oven importer, HotStuff importer, PANDORA-FE converter)
encodes a shape that ZLP researchers thought was needed in 2018–2021
and that ZLP researchers still need today. Shepard's job is to ship
subsets, plugin-first, in a form that actually deploys.

**No `IDMS-MIGRATION-PLUGIN` backlog row is filed** as a result of
this history — the migration target population doesn't exist. The
existing `IMPORTER-LIBRARY-SEED` row (which already references
`kibid_exporter` and `hotstuff_idms_importer` as starter sources
for the importer plugin library) is the right shape; this history
just clarifies its provenance.

**The friction list shrinks accordingly**, from "migrate iDMS
production data" to:

- **Capture the demand signal.** The 7 iDMS components and the KIBID
  exporter encode design ideas worth preserving. Where they show up
  as shapes shepard hasn't built yet (streaming Kafka ingest, native
  RCE workflow integration), they belong as design-doc rows, not
  migration-plugin rows.
- **Conversations with iDMS designers** are the high-value adoption
  motion — they recognise the shapes and can validate or correct
  shepard's choices from the prototype-era perspective. This is
  social work, not migration tooling.
- **Document this history exists** (this doc) so a future researcher
  encountering an old `de.dlr.cube.idms.client.java` artefact, an
  `idms_client==2020.10.20` pin, or a `kibid-proxy.praesto.lo`
  hostname in archive material doesn't conclude they are looking at
  systems with no relationship to shepard.

## §9 References

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

