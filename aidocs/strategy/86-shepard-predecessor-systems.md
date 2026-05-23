---
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributor, strategy, historian
---

# 86 — Shepard's predecessor systems at DLR ZLP: KIBID, iDMS, PRAESTO

## §1 Why this history matters

Shepard did not arrive on a clean lab bench. The system was built at the
DLR Center for Lightweight Production Technology (ZLP) in Augsburg, a
site that had been wrestling with composite-manufacturing data
management for roughly a decade before the public shepard repository
appeared on GitLab in 2021. The public-facing record is thin —
the DLR ZLP project page for shepard ([dlrZlpShepard](#references)) makes
no mention of any predecessor; the Zenodo software citation
([haaseShepard2021](#references), DOI 10.5281/zenodo.5091604) names a
fresh authoring team. To a reader meeting shepard today, it looks
like a 2021 greenfield project.

It wasn't. There are at least three earlier systems whose source we
hold on the UNAS code-drop (`/mnt/pve/unas/media/random/`), and at
least one of them is documented in DLR's open publication record.
This document reconstructs that history from primary sources — the
zipped source trees, the DLR eLib bibliographic records, the URL
strings embedded in old Python scripts, and two written user
course-corrections on deployment status (see
[`aidocs/agent-findings/predecessor-history-correction-2026-05-23.md`](../agent-findings/predecessor-history-correction-2026-05-23.md)).

**Key framing the user corrected during drafting (2026-05-23):** iDMS
was a **prototype that saw use in the IPRO research project at DLR**
but never graduated to institute-wide deployment. The deployment
status of PRAESTO and KIBID is unverified from public sources. **Shepard
is the first system in this lineage to reach operational use across
multiple ZLP use cases.** That is a stronger positioning claim than a
"linear succession of deployed systems" framing — and it's the honest
one.

The goal is honest reconstruction, not hagiography. A researcher who
built KIBID, or one of the iDMS / IPRO developers, may read this. The
§6 / §7 split below is about what the new system chose to carry
forward from prototype-stage designs, and what designs never had to
face production reality — not about what the old systems "got wrong."

**Reading the confidence column in §5 first** is the recommended way
to use this document. The lineage is real but parts of the glue are
circumstantial: the strongest links are the hostnames embedded in
source code, the second-strongest are the eLib citation chain, and
the weakest is the question of *why* the project line broke and a new
authoring team picked it up in 2021. That last question we cannot
answer from the evidence available; we don't try.

## §2 PRAESTO (2014, oldest documented)

**Confidence: MEDIUM.** Two eLib records, no full text, but
corroborating evidence in the KIBID source.

PRAESTO is the earliest of the three systems with a public DLR
publication trail.

- Title: *Datenbank PRAESTO: Speicherung von CFK-Forschungsdaten auf
  Fertigungsniveau* ("PRAESTO Database: Storage of CFRP Research Data
  at Manufacturing Level")
- Authors (full record, eLib 94077): Stefan Nuschele, Thomas Schmidt,
  Mildred Kießig, Thomas Mühlhausen, Bastian Wagenfeld, Hajo Voss
- Talk record (eLib 94078): Nuschele alone
- Venue: 63. Deutscher Luft- und Raumfahrtkongress (DLRK 2014),
  16–18 September 2014, Augsburg
- Status: unpublished conference contribution; no abstract or full
  text available in eLib
- Citations: [nuschelePraesto2014paper](#references),
  [nuschelePraesto2014talk](#references)

What we know about what PRAESTO **was**, from external sources:

> PRAESTO is a database system from PAG used for geometric 1D and 3D
> measurement data acquisition at the ZLP Augsburg's data management
> system, featuring should/actual comparisons and new capabilities
> including integration of additional engineering sensors, simulation,
> expanded evaluation methods, environmental data acquisition, and
> overlaying results from different sources.

— search-result digest, 2026-05-23, sourced from a docplayer.org
mirror of an unrelated 2013 ZLP colloquium presentation by Thomas
Schmidt / Somen Dutta. We were unable to fetch the docplayer page
directly (ECONNREFUSED at retrieval time, 2026-05-23) but the
attribution to "PAG" (likely Premium AEROTEC Augsburg, an industry
partner of ZLP) is consistent with the multi-author 2014 paper.

What we know about PRAESTO from the **KIBID code we hold** (§3): the
hostnames `kibid-proxy.praesto.lo` appear in
`kibid_exporter_sources/kibid_exporter.py:19-20` and in
`opcua_kibid_adapter_sources/opcua_kibid_adapter.py` (line 1 of the
KibidClient instantiation). `.lo` is a local-network top-level domain
typical of an internal DLR deployment. This is the load-bearing piece
of evidence that **PRAESTO was the umbrella platform within which
KIBID was hosted**, not a separate system. The KIBID Keycloak realm
name is `kibid` (`opcua_kibid_adapter.py`: the URL
`/auth/realms/kibid/protocol/openid-connect/token`), so the brand
hierarchy reads: PRAESTO platform → KIBID database → kibid-proxy
gateway.

Nuschele's eLib track (1st-author or co-author on 15 records between
2012 and 2015) sits at the intersection of robotic ultrasonic NDT,
thermoplastic-processing robot cells, and the ZLP project AZIMUT.
PRAESTO is the data-management point in that arc.

**Date of original deployment: unknown.** The 2014 DLRK paper is the
first publication we can verify; the KIBID code that targets
`praesto.lo` (§3) is later, so PRAESTO was live by 2014 and still
operational at the point KIBID was written.

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

### 4.4 External tools iDMS bridged to (sidebar — **not** in the lineage)

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

**Lineage-glue confidence: MEDIUM.** The PRAESTO ↔ KIBID link is
strong (the `praesto.lo` hostname is in KIBID source). The KIBID ↔
iDMS handoff is *circumstantial* (Krebs authored OPC UA→KIBID adapters
ca. 2016–2017 *and* the iDMS frontend ca. 2020; the iDMS examples
include an `InfluxreferenceApi` which suggests iDMS moved from KIBID's
custom `/tardis/v2/` TS substrate to InfluxDB). The iDMS ↔ shepard
handoff is *evident from shape* (Project/Experiment/Step ≈
Collection/DataObject; mongo+influx reference split survives) but
**not from authorship** — the four named authors on the 2021 Zenodo
shepard record (Tobias Haase, Roland Glück, Patrick Kaufmann, Mark
Willmeroth — [haaseShepard2021](#references)) do not appear on any
iDMS/KIBID source we hold. The continuity is *institutional* (same
ZLP Augsburg, same problem domain) rather than personal — a research
prototype's design notes carried forward by a successor team.

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

