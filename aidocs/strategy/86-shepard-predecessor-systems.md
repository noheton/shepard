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

A further primary source pushes the lineage **earlier still**, into
the design-thinking phase that preceded any of the three platforms:
two 2010 PowerPoint decks authored by Krebs at DLR ZLP — the
**MFZ-Anlage control-concept deck** of 2010-10-05
[@krebsMfzSteuerung2010] and a companion *Themen Robotik für
Faserverbundfertigung* slot-deck of 2010-11-16
[@krebsRobotikThemen2010]. The MFZ deck (21 internal revisions
between September and October 2010 — a working document, not a
one-off pitch) does not yet name an RDM system; it specifies the
*automation control concept for the Multifunktionale Fertigungszelle*
(Multifunctional Production Cell). But several of its bullet points
read, with 15 years of hindsight, as the design intent that the
later PRAESTO / KIBID / iDMS / Shepard lineage has spent the rest of
its existence implementing — a finding that warrants its own
sub-section before §2.

## 1.5 Pre-PRAESTO: design thinking already articulated in 2010

The 2010-10-05 *Steuerungskonzept der MFZ-Anlage*
[@krebsMfzSteuerung2010] is the earliest primary source recovered in
the predecessor-systems lineage. It precedes PRAESTO (2014) by four
years, KIBID (2016) by six, CUBE iDMS (2017–2020) by seven, and the
public arrival of Shepard (2021) by eleven. Its subject is not data
management as such: it is the control architecture for the MFZ —
the Multifunktionale Fertigungszelle, a flagship robot cell at ZLP
Augsburg. The deck is a working document (21 internal revisions,
721 words on the substantive slides, an explicit citation to the
EUROP *Robotic Visions to 2020 and beyond — Strategic Research
Agenda* of 2009), authored by Krebs in his role as the cell's
control-concept owner.

Read for design intent, several of its 2010 commitments anticipate
the architecture of the data substrate that would not arrive in
ZLP-deployable form for more than a decade:

| 2010 design commitment (MFZ control-concept deck) | Shepard surface today |
| --- | --- |
| *"Konsequente Vermeidung von Einzel-/Insel-Lösungen"* — consistent avoidance of point/island solutions | the rationale for a shared substrate at all; the thesis case against the per-cell folder-share status quo |
| *"Modulare Architektur für schrittweise Weiterentwicklung"* — modular architecture for incremental evolution | the plugin-first SPI seam (`aidocs/platform/47`); CLAUDE.md §"Always: think plugin-first" |
| *"Generische Schnittstelle für Messdatenerfassung"* — generic interface for measurement data acquisition | `PayloadKind` + `PayloadStorage` SPI (`aidocs/platform/47 §2`); ancestor of the substrate-agnostic Container family |
| *"Automatische Dokumentation von Prozessdaten"* — automatic documentation of process data | `ProvenanceCaptureFilter` (PROV1a) + the F(AI)²R Activity stream; the conversation IS the prov data |
| *"Detektion von Abweichungen zum Kalibrier-Prozess"* — deviation detection from the calibration baseline | anomaly-detection on timeseries (LUMEN TR-004 substrate, `shepard-plugin-ai` design line) |
| *"Zentrale Datenhaltung auf Datenbank"* — central database-backed data storage | the substrate split (Neo4j + Postgres/Timescale + MongoDB + Garage) replacing per-machine local files |
| *"Replay-Fähigkeit in Simulation zur Analyse und Optimierung"* — replay capability in simulation for analysis and optimisation | the Predecessor/Successor chain + Activity history; reproducible reconstruction of a process run from the substrate |
| *"Ableiten von Wirtschaftlichkeitsdaten"* — derivation of economic-performance data | dataset-forging passes (`project_dataset_forging.md`); the substrate-as-analysis-input layer |
| *"Anlagenkonfigurationsmanagement"* — plant configuration management | `:FeatureToggleRegistry` + per-feature `:*Config` (A3b / N1c2 / UH1a / `aidocs/integrations/67`); admin-configurable knobs as a first-class shape |
| *"Offene Schnittstellen: AutomationML, XML, …"* — open interfaces | OpenAPI 3.0 + Codegen client SDKs; SHACL + JSON-LD on the semantic side |
| *"Echtzeit-Ethernet als Basis-Kommunikationstechnologie"* — real-time Ethernet baseline | `shepard-timeseries-collector` as the OPC UA / MQTT side-process pattern |
| *"Drahtloses virtuelles SmartPAD"* — wireless virtual SmartPAD (shop-floor terminal) | the shop-floor-UI requirement the Industrial Manufacturing & Quality Engineer role names in CLAUDE.md (`aidocs/agent-findings/manufacturing-quality.md`) |
| *"Mobile Bedienpanels zur Arbeitsbereichsteuerung"* — mobile control panels for work-area handling | per-cell admin UI (Shepard's per-shelf admin endpoints + frontend admin routes) |
| *"Integration des NDT Frameworks (→ PI-NDT)"* — integration of the NDT framework | `Plugin` family — NDT scans as a first-class payload kind in the visualization-plugin design (`project_vis_categories.md`) |

The deck's companion piece, *Themen Robotik für Faserverbundfertigung*
[@krebsRobotikThemen2010] of 2010-11-16, is a much shorter (29-word,
2-revision) title-and-image deck whose primary content is a single
MFZ photograph — it sits in the archive as a slot-holder for the
research-themes section but does not carry substantive prose.

Three observations follow:

1. **The architectural ambition predates the platforms.** The 2010
   deck names *generic measurement-data interfaces*, *central
   database storage*, *automatic process documentation*, *replay
   capability* and *avoidance of island solutions* as control-cell
   requirements four years before PRAESTO was evaluated. The
   substrate Shepard provides is not a 2021 idea; it is a 2010
   articulation finally meeting its operational form.
2. **The architect is continuous.** PRAESTO (2014, evaluated by
   Krebs's group), KIBID (2016, used in IPRO with Krebs's group),
   CUBE iDMS (2017–2020, *architected by Krebs*) and Shepard (2021,
   *carrying the same design DNA* via the IPRO-to-Shepard transfer
   documented in §4) trace, on the primary-source evidence, to the
   same author's continuously-held design commitments — committed
   to paper in October 2010, refined in iDMS in 2020, and
   ship-shaped in Shepard from 2021 onward.
3. **What the 2010 deck does *not* yet articulate.** The deck is a
   *control-cell* concept, not a data-management concept. It names
   "zentrale Datenhaltung auf Datenbank" but does not yet specify
   the substrate split, the graph-vs-relational decision, the
   provenance vocabulary (W3C PROV arrives in iDMS, not here), the
   semantic-annotation layer, or the multi-tenancy story. Those are
   the layers the later platforms add. The 2010 deck establishes the
   *requirements*; the later platforms iterate the *answers*.

This pushes the methodological note in §8 a layer deeper: the
continuity-of-architect runs not from 2014 (PRAESTO) but from
**2010 — the MFZ control-concept deck**. Whatever else Shepard
inherits from PRAESTO, KIBID and iDMS, it also inherits, by way of
its principal designer, an institutional design-thinking lineage
that was already on paper before any of them.

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

### 2.1 Krebs's own input deck — primary source for the misfit (date undetermined)

A two-slide working deck `Input_Florian_PRAESTO.pptx`
([@krebsInputPraesto](#11-references)) preserved in the artefact
collection (no date in the filename or in the in-slide footer)
records Krebs's contemporaneous documentation of PRAESTO's scope
from his side of the engagement. The deck is short and does not
contain a stated grievance or proposed adjustment — it is a
*scope-stocktake* annotated with an architecture diagram, not a
critique-of-record. What the deck does establish is two facts not
present in the eLib record and one corroborating observation:

1. **The supplier is named: Kisters AG.** Both slides repeat the
   line *"PRAESTO: Weiterentwicklung zusammen mit Kisters AG (DLR
   seit 2010)"* (PRAESTO: further development together with Kisters
   AG; at DLR since 2010). This is the first primary source to
   identify the commercial supplier. Until this batch, §2 carried
   the testimony-only claim "supplier not named here"; the
   evidentiary basis is now upgraded.
2. **PRAESTO's relationship with DLR dates to 2010.** "DLR seit 2010"
   (DLR since 2010) places the engagement four years before the
   DLRK 2014 conference deposit. The 2014 paper is therefore not
   PRAESTO's arrival at DLR but a mid-engagement publication. This
   moves the chronology lower bound from 2014 to 2010 and aligns
   PRAESTO's DLR-presence with the same 2010 window in which the
   MFZ-Anlage control-concept deck (§1.5) articulates the data-
   management requirements that would later be measured against
   PRAESTO's actual capabilities.
3. **The scope, as drawn, is inspection-only.** The architecture
   diagram on both slides is captioned *Inspektionsprozess*
   (inspection process) four times on slide 1, and the workflow
   blocks read *Prüfung Trockenfaserablage* (dry-fibre layup
   inspection) and *Prüfung Endbauteil* (end-component inspection).
   The architecture is: CAD + offline programming + environmental
   data + sensor reading + position information → pre-evaluation
   → spatial data fusion → analysis & visualisation, with a
   robot-cell as the data-source. This is a *spatial-fusion
   inspection analyser*, not a process-data acquisition platform
   for the full manufacturing chain.

The deck does *not* state "PRAESTO was a bad fit"; it shows what
PRAESTO was scoped to do. The misfit thesis remains a testimony
claim. What the artefact supplies is the *evidence shape* that
makes the testimony legible: PRAESTO answered a narrower question
(inspection-data fusion) than the question the centre was forming
in the same period (durchgängige Prozessdatenerfassung — end-to-end
process-data acquisition; see §3.6.1, the ProDES decks). The
"bad fit" — read against this scope evidence — is not that PRAESTO
worked badly at what it did, but that what it did was a subset of
what the centre needed.

The decision to build internally rather than commission a
Kisters-AG-led expansion of PRAESTO is plausibly the *organising
question* of the eight-month 2017 chronology that follows (§3.6).
The chronology, read against the input deck, is what Krebs and
Nuschele put in place of an expanded PRAESTO.

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

### 3.6 The break and the synthesis — eight months in 2017

A run of nine working artefacts dated between **2017-04-25** and
**2017-12-12** documents the period in which the team at ZLP
Augsburg moved from "what should we build?" to a system specified
in enough detail that CUBE iDMS (§4) would be its operational
realisation. Read as a sequence, the eight months are the break
from the commercial supplier (§2) and the synthesis of the
in-house answer that §4 then builds. Three threads run through
the sequence: (a) the *Prozessdatenerfassung* (process-data
acquisition) thread — what the centre actually needed PRAESTO to
do and decided to build itself, (b) the *Prozessleitsystem*
(process-control system) thread — how the cell-level orchestration
fits the data architecture, and (c) the *Vision* thread — the
December 2017 articulation that closes the year and opens the
iDMS development phase.

#### 3.6.1 The ProDES thread (April → June 2017)

The earliest deck in the recovered chronology is
`20170425_Prozessdatenerfassungssystem_ProDES.pptx`
([@krebsProDES2017a](#11-references)), dated 2017-04-25, sole
author Florian Krebs ("Industrie 4.0 @ ZLP — Prozessdatenerfassungssystem
(ProDES) — 11.04.2017, Florian Krebs", with the slide footer
showing 11.04.2017 and the filename carrying 20170425 — the deck
was built up through April). A two-month-later iteration,
`20170619_Prozessdatenerfassungssystem_ProDES.pptx`
([@krebsProDES2017b](#11-references)), dated 2017-06-19, expands
the authorship to **Stefan Nuschele, Alfons Schuster, Florian
Krebs** — three names. This is the first primary-source evidence
in the chapter that **Nuschele (PRAESTO author, §2) and Krebs
(iDMS architect, §4) co-authored a transitional ProDES design in
2017**, with Schuster joining as a third contributor. The
PRAESTO-era authoring community and the iDMS-era architect are
not separate generations; they are the same working group in mid-
1990s-style succession from one platform to the next.

The April deck names *one system — multiple views* (Prozessketten /
Prozessen / Ressourcen as three analysis perspectives; slide 9),
the *layered basis architecture* (Datenerfassung → Datenorganisation
& Datenablage → Datenzugriff → Analyse → Management & Administration;
slides 10–11), and the technology survey (NoSQL: Document / Column /
Key-Value / Graph — slide 13, with CouchDB, MongoDB, hBase,
Cassandra, Neo4j, OrientDB, "Oracle NoSQL", Amazon DynamoDB named).
Slide 6 carries the framing: *Aktueller Stand der Technik:
Praesto, KiBiD … Viele Inseln* (Current state of the art:
PRAESTO, KIBID … many islands). The diagnosis is on record: the
existing platforms are *island solutions*, and the centre's
answer is a layered, multi-substrate data architecture.

The June deck adds the *roadmap* (slide 4): platform selection
through 2017, OPC UA + ProComp + NSR demos through 2018,
spatial-referencing + map-reduce analytics into 2019, with three
milestones (MS1 7/17: Vorauswahl Plattform; MS2 9/17: Demo
Projekt OPC-UA; MS3 x/18: Demo Procomp). Read in 2026, this is
a research-engineering plan that landed on KIBID's OPC UA bridge
(MS2, §3) and led into iDMS by 2018.

#### 3.6.2 The Prozessleitsystem thread (July → September 2017)

`20170718_Status_Prozessleitsystem.pptx`
([@krebsStatusProzessleitsystem2017](#11-references)), dated
2017-07-18, is a status stocktake of the *Prozessleitsystem*
(process-control system) at ZLP. The deck names the goal as
**modularisation of automation solutions (Vision: Plug & Automate)**
with a concrete first step of *prozessübergreifender Austausch
von Prozessinformationen* (cross-process information exchange)
and *Ablaufsteuerung für Prozessketten / -netzwerke* (sequence
control for process chains / networks). The further-future
ambition listed on the same slide is *Selbstkonfiguration / Self-X
Eigenschaften* (self-configuration / self-X properties). The
Prozessleitsystem layer is the orchestration counterpart to the
ProDES data layer.

`20170721_Prozessablauf.xlsx` (the IPRO process-flow spreadsheet
already cited at §4.4) lands three days later — composite-layup
process sequence at the IPRO cell, the operational form the
Prozessleitsystem would orchestrate.

`20170914_NSR_Orchestrator_Konzept.pptx`
([@krebsNSROrchestrator2017](#11-references)), dated 2017-09-14,
is the architectural deliverable of the Prozessleitsystem thread.
**This deck is load-bearing for §3 (KIBID) and §4 (iDMS) both:**
its IPRO-architecture diagram (slide 3, *IPRO: Aufbau*) names the
participating components as *4 Interface-Knoten, 3 Virtuelle
Knoten, Kommunikation über OPC UA, Datenerfassung in KiBiD über
HTTP Schnittstelle* (4 interface nodes, 3 virtual nodes,
communication via OPC UA, data acquisition in KIBID via HTTP
interface). The diagram explicitly labels the participating
boxes: *Process Orchestrator*, *IOT 2040* (Siemens IoT gateway),
*Werkzeug-Wechsler* (tool changer), *Festo Ventil-Insel* (Festo
valve island), *Siemens S7-300 SPS* (PLC), *KiBiD Adapter*,
*Router*, *Big Data Storage*, *KUKA Robot Controller*. This is
the **first primary-source corroboration in this chapter that
KIBID was the data-acquisition layer of the IPRO cell, named
inside an IPRO architecture diagram by Krebs himself** — until
now §3 and §4.4 had only hostname-level evidence (`kibid-proxy.praesto.lo`
in source code) for the KIBID→IPRO connection. The
2017-09-14 deck moves this from inference to documented
architectural fact.

#### 3.6.3 The framing artefact (October 2017)

`20171010_Industry_4.0_defined.pptx`
([@krebsI4Definition2017](#11-references)), dated 2017-10-10, is
not a project artefact but a framing reference — a curated reading
of an external *Design News* piece on smart manufacturing,
positioning ZLP's work in the Industrie 4.0 vocabulary the
ProDES decks were already invoking. The slot it occupies in the
chronology is rhetorical: between the September architectural
deliverable (NSR Orchestrator) and the December vision, the team
documented how it would frame the work to outside audiences.

#### 3.6.4 The architecture-vision-deliverable week (December 2017)

Three consecutive working days in early-to-mid December 2017
bridge architectural thinking to operational vision to written
deliverable:

- **2017-12-04** — `20171204_NSR_Architektur.pptx`
  ([@krebsNSRArchitektur2017](#11-references)). The deck is
  thin on extractable prose (the visible content is largely
  template Lorem-Ipsum boilerplate retained around figure
  placeholders), but its *date and filename* establish that an
  NSR-architecture working deck was iterated on this day.
- **2017-12-05** — `20171205_Vision_Datenmanagement.pptx`
  ([@krebsVisionDatamanagement2017](#11-references)),
  *"Vision: Datenmanagement"* — **Dipl.-inf. Florian Krebs,
  Dr. Stefan Nuschele**, with a footer date of 12.12.2017
  (so the deck was iterated through to 12 December). The opening
  challenge slide (*Herausforderung: Datenmanagement*) names the
  problem in the form a 2017 research-project manager would
  recognise: *ein Projekt erzeugt Vielzahl von unterschiedlichster
  digitale Produkte* (one project produces a multitude of varied
  digital products) — Anwendung, Auslegungsdaten, Werkstoffdaten,
  CAD Modelle, Numerische Modelle, Messdaten, Quellcode, Berichte /
  Publikationen, Medien, *u.v.m.* (application, design data,
  material data, CAD models, numerical models, measurement data,
  source code, reports / publications, media, and much more).
  This is the diagnosis that the iDMS data model — `Project /
  Experiment / Step / Artifact`, multi-substrate references for
  files / timeseries / structured data — is built to answer.
- **2017-12-12** — the footer date of the same deck, and
  evidence that the iteration continued through the week.

The December 2017 week, read in sequence, is the moment the vision
gets named explicitly (*Datenmanagement* in the title) and the
authorship pair that will deliver it gets placed on the cover
slide (Krebs + Nuschele). The April → December chronology
**replaces the previous "iDMS came from somewhere" hand-wave**
(see §1) with an eight-month documented evolution: ProDES (April)
→ ProDES expanded with Schuster (June) → Prozessleitsystem status
(July) → IPRO process flow (July) → NSR Orchestrator with the
*KiBiD Adapter* drawn into the IPRO architecture (September) →
Industry 4.0 framing (October) → NSR Architektur (December 4)
→ Vision Datenmanagement (December 5–12).

#### 3.6.5 An adjacent conceptual artefact (date unknown) — SOA MES

A seven-slide deck `SOA_MES.pptx` ([@krebsSoaMes](#11-references))
in the same artefact collection, undated by filename or footer,
sketches a *service-oriented architecture for a Manufacturing
Execution System* — *"Everything is a service"* — with three
participant classes (physical systems: robot cells, mobile
robots, presses, fixtures; virtual systems: data collectors,
task planners, displays; workers / ERP-APS-PPS systems), a
four-step task-execution model (Task Decomposition → Step
Scheduling → n-step allocation → Distributed Execution), and a
*Basis-Architektur* (slide 6) that places the *SOA MES* layer
alongside *Prozess- und QS-Datensammlung (CUBE, KiBiD?)*,
*Planungsschicht / Visualisierung (externe PPS, ERP …)* and an
*Auswertung* (analysis) layer with Matlab / R / Deep Learning.
The CUBE acronym appears here at slide-label level — the same
acronym that names CUBE iDMS in §4, attached to *Prozess- und
QS-Datensammlung* (process and QA data collection).

The deck cannot be placed in the chronology by its own metadata.
Two indirect cues place it adjacent: the workshop participant
slide (*Workshop Orga: Haase, Krebs / Teilnehmer: FAS: LCL, MS,
RG, CR; PNA: Mkü, …; PQS: AS, CF?*) lists **Tobias Haase** —
the first-named author on the 2021 Shepard Zenodo record
([@haaseShepard2021](#11-references)) — as workshop co-organiser
alongside Krebs. This is the **earliest primary source in this
chapter linking Haase (the eventual Shepard lead author) into the
same workshop community as Krebs (the iDMS architect)**. The
provisional dating range for the deck is therefore the window
in which the CUBE-iDMS-naming convention is in flux (*CUBE,
KiBiD?* with question mark) but Haase is already in the room —
no earlier than the December 2017 vision deck (when the CUBE
naming begins to settle) and no later than the iDMS deployment
phase (when *KiBiD?* would no longer carry a question mark).
The deck is therefore most plausibly *late 2017 or early 2018*,
the bridging months from the chronology's end to §4's prototype.
It is excluded from §3.6.1–3.6.4 because it cannot be ordered by
its own metadata; it is included here as an adjacent conceptual
artefact that the bibliography needs to record.

#### 3.6.6 The chronology table

| Date | Artefact | What it adds |
| --- | --- | --- |
| 2017-04-25 | ProDES v1 [@krebsProDES2017a](#11-references) | Sole-author Krebs; "many islands" diagnosis; layered basis architecture; NoSQL technology survey; multi-view analysis model |
| 2017-06-19 | ProDES v2 [@krebsProDES2017b](#11-references) | Adds **Nuschele + Schuster** as co-authors; roadmap with milestones (MS1 platform selection 7/17, MS2 OPC UA demo 9/17, MS3 ProComp demo 2018) |
| 2017-07-18 | Status Prozessleitsystem [@krebsStatusProzessleitsystem2017](#11-references) | Orchestration goal: modular *Plug & Automate*; cross-process information exchange as first step; Self-X as future ambition |
| 2017-07-21 | IPRO Prozessablauf XLSX | Composite-layup process flow at the IPRO cell (already cited at §4.4) |
| 2017-09-14 | NSR Orchestrator Konzept [@krebsNSROrchestrator2017](#11-references) | IPRO architecture diagram naming *KiBiD Adapter* alongside KUKA / Festo / Siemens — primary-source proof KIBID was IPRO's data layer |
| 2017-10-10 | Industry 4.0 defined [@krebsI4Definition2017](#11-references) | External *Design News* curation framing the work in Industrie-4.0 vocabulary |
| 2017-11-06 | IPRO Grafana dashboard screenshot | Live KIBID-sourced telemetry from the IPRO cell (already cited at §4.4) |
| 2017-12-04 | NSR Architektur [@krebsNSRArchitektur2017](#11-references) | Architecture-vision week opens; deck content thin but date establishes the iteration |
| 2017-12-05 (–12) | Vision Datenmanagement [@krebsVisionDatamanagement2017](#11-references) | *"Vision: Datenmanagement"* — Krebs + Nuschele; the digital-products diagnosis the iDMS data model is built to answer |
| (date undetermined; ≈ late 2017 / early 2018) | SOA MES [@krebsSoaMes](#11-references) | CUBE acronym appears with *KiBiD?* at slide-label level; **Haase named as workshop co-organiser with Krebs** — earliest Haase↔Krebs link |

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

2. *Personal handoff into Shepard.* Two strands of personal
   continuity now stand on the artefacts:

   - **Mark Willmeroth** appears as `user: mwillmeroth` in the IPRO
     operations TODO (the active KIBID-era account) and is one of
     the four named authors on the 2021 Zenodo Shepard record
     ([@haaseShepard2021](#11-references)). This is the operations-
     side handoff: the engineer running the data layer in 2017 is
     on the masthead of the 2021 release.
   - **Tobias Haase**, the first-named author on the 2021 Zenodo
     Shepard record, appears as workshop co-organiser alongside
     Krebs on the SOA MES deck (§3.6.5), most plausibly dated late
     2017 / early 2018. This is the architecture-side handoff: the
     eventual Shepard lead author is in the same workshop room as
     the iDMS architect at the moment the CUBE acronym is being
     decided. The Haase↔Krebs personal link is now also no longer
     zero on the primary-source record — though the SOA MES deck's
     undetermined date keeps this finding at confidence MEDIUM.

   The two remaining 2021 Shepard authors (Glück, Kaufmann) do not
   appear in the predecessor sources we hold; the continuity into
   the 2021 team is therefore now a *braid* — institutional and
   architectural baseline, *plus* two named personal links
   (Willmeroth on operations, Haase on workshop authorship).

3. *Authoring community across the 2017 chronology.* The
   2017-06-19 ProDES iteration ([@krebsProDES2017b](#11-references))
   is co-authored by **Nuschele, Schuster, Krebs** — placing the
   PRAESTO first-author (Nuschele, §2) and the iDMS architect
   (Krebs, §4) on the same authoring document mid-chronology. The
   PRAESTO-era authoring community and the iDMS-era architect are
   the same working group in mid-platform succession. This
   tightens the continuity claim from "shared institution" to
   "shared authorship on the transitional design".

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
the 2017-07-21 process-flow spreadsheet, the IPRO operations TODO,
the 2017-11-06 Grafana dashboard screenshot, and the eight-month
2017 working-deck chronology now established in §3.6
(`20170425_…ProDES`, `20170619_…ProDES`, `20170718_Status_Prozessleitsystem`,
`20170914_NSR_Orchestrator_Konzept`, `20171010_Industry_4.0_defined`,
`20171204_NSR_Architektur`, `20171205_Vision_Datenmanagement`, plus
the undated `SOA_MES` deck) together establish the system's
design, its IPRO deployment, and the technical texture of its use
to a level that would, under different historiographical
circumstances, support a confident operational claim. The critical
historical claim — that the system never reached institute-wide
deployment beyond IPRO — comes from the author's own testimony and
is consistent with the absence of any public deployment record.

PRAESTO's evidence base is also no longer eLib-only: Krebs's own
`Input_Florian_PRAESTO.pptx` (§2.1) supplies primary-source
identification of the supplier (Kisters AG) and the engagement's
2010 lower bound, lifting §2 from "supplier not named here" to
"supplier identified by primary source".

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

**Pre-PRAESTO design thinking** (the load-bearing primary sources for §1.5):

- [krebsMfzSteuerung2010](#bib) — Krebs, F. (2010). *Steuerungskonzept der MFZ-Anlage.*
  Internal DLR ZLP Augsburg working deck, dated 2010-10-05, 21
  internal revisions between 2010-09-28 and 2010-10-05, 721 words on
  substantive slides; cites the EUROP *Robotic Visions to 2020 and
  beyond* Strategic Research Agenda (2009-07). Filename
  `9acbb990-20101005_Steuerungskonzept_der_MFZAnlage.ppt`
  (~3.1 MB Composite Document File V2 .ppt; uploaded to AI working
  memory 2026-05-23). Load-bearing evidence for §1.5: 14 design
  commitments anticipating Shepard surfaces — generic measurement
  interfaces, central DB storage, modular architecture, automatic
  process documentation, replay capability, deviation detection,
  open interfaces (AutomationML, XML), shop-floor mobile panels,
  NDT framework integration.
- [krebsRobotikThemen2010](#bib) — Krebs, F. (2010). *Themen Robotik
  für Faserverbundfertigung.* Internal DLR ZLP Augsburg slot-deck,
  dated 2010-11-16, 2 revisions, 29 words; one MFZ photograph.
  Filename `7f5ba9ac-ThemenRobotik_f_r_Faserverbundfertigung.ppt`
  (~1.2 MB; uploaded to AI working memory 2026-05-23). Companion
  source for §1.5; sparse substantive content but confirms the
  research-themes-around-the-MFZ context.

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

