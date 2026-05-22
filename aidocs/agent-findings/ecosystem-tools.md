---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Ecosystem Tools — Findings

*Written: 2026-05-21. Based on source inspection of five uploaded repositories and one PPTX.*

---

## Tools Inventoried

| Tool | Repo | Stack | Status | Role |
|---|---|---|---|---|
| **shepard-dataship** | `zlp-augsburg/inner-source/shepard-dataship` | Python + NiceGUI + uv | Active, v0.x | Databus publication wizard |
| **shepard-stc-config-helper** | `zlp-augsburg/inner-source/shepard-stc-config-helper` | Python + uv | Active | OPC UA → STC config generator |
| **infusion-analysis** | (local) | Python + uv + Jupyter | Early draft | ForInfPro ML analysis |
| **instdlr** | Helmholtz Cloud (Federico Díaz Capriles) | Python + FastAPI + MongoDB | Mature (CI badge, DOI) | PIDINST instrument registry |
| **ForInfPro presentation** | PPTX | — | Reference doc | Infusion process data collection use case |

---

## 1. shepard-dataship

**Purpose:** A guided web wizard (NiceGUI / browser) that publishes Shepard data to the [DBpedia Databus](https://databus.dbpedia.org/). Core publication workflow:

1. **Source picker:** "Browse Shepard" (browses collections → data objects → references) or "Upload files"
2. **Payload selector:** checkboxes on timeseries (CSV download), structured data (JSON), or files
3. **Metadata enrichment:** title + description with optional AI fill (OpenAI)
4. **DALICC license picker:** SPARQL-powered dropdown of valid Databus license URIs
5. **Group/Artifact selection:** pick or create Databus group + artifact via SPARQL queries
6. **SHA-256 + byte size:** computed automatically per distribution Part
7. **JSON-LD preview:** live preview of the Databus deposition before POST
8. **One-click publish:** `POST {DATABUS_API_URL}/api/register`

**Key files:**
- `src/shepard_adapter.py` — wraps the OpenAPI-generated `shepard-client` (`shepard_client.api.*`) to list collections, fetch data object trees, download payloads
- `src/databus_formatter.py` — assembles the JSON-LD Databus deposition
- `src/databus_client.py` — HTTP POST to Databus API
- `src/llm_enhancer.py` — optional OpenAI call for title/description suggestions
- `src/state.py` — per-session NiceGUI state container

**Uses the upstream v1 Shepard API** (the generated `shepard-client` package). Does **not** yet use the v2 API or appIds.

**Issue flagged by user:** "sample collection cannot run — assumes finished collection manifest JSON is not yet existing." This refers to an example script in the repo that expects a pre-generated Databus manifest JSON file that is only produced after a full publication run. Likely a documentation/example ordering issue, not a code bug.

**MFFD export running:** User is currently running dataship to export MFFD data. As of 2026-05-21 the export was at 58.9% (4982/8457 DataObjects), 101 GiB at 0.63 MiB/s, ETA >1 day. This reveals that:
- MFFD data volume: ~8457 DataObjects, ~170 GiB total estimated
- The current download-then-upload path (v1 API → local → Databus) is the bottleneck
- The `shepard-plugin-airflow` Mode C (shared MinIO file references) would eliminate this re-transfer entirely

**Integration path with Shepard fork:**
- Near term: switch `shepard_adapter.py` to use v2 appId-based endpoints → typed container refs → faster browsing
- Medium term: add a `/v2/collections/{appId}/export/databus` endpoint that generates the JSON-LD server-side → one-click from the Shepard UI (no wizard needed for standard cases)
- See `aidocs/integrations/83-rebar-airflow-integration.md` §5 for the shared MinIO shortcut

---

## 2. shepard-stc-config-helper

**Purpose:** Automates creation of [Shepard Timeseries Collector (STC)](https://gitlab.com/dlr-shepard/shepard-timeseries-collector) configuration YAML files from a list of OPC UA node IDs.

**Workflow:**
1. Provide a list of OPC UA node IDs (e.g. `ns=6;s=MAIN.Temperature`)
2. Tool generates `{source}.yml` (OPC UA source block with endpoint, sampling/publishing intervals, security)
3. Tool generates `sinks.yml` (Shepard timeseries container sink with host URL, API key, container ID)
4. Optional: generate a basic Grafana dashboard JSON for live preview

**Gap:** Currently uses the **numeric container ID** in the sink config (`timeseries_container_id: 168588`). After the TS-appId migration (task #58 / `aidocs/platform/87-timeseries-appid-migration.md`), this should use the appId instead. The config helper needs a corresponding update.

**Integration path:**
- Tight integration with Shepard: after creating a TimeseriesContainer via the UI, the user should be able to click "Generate STC config" and get a ready-to-deploy YAML blob — no manual node-ID list needed
- The Shepard fork's "watched containers" feature (`/v2/collections/{appId}/watched-containers`) is the natural hook point
- This belongs as a feature in the Shepard UI (or a CLI in the Python client), not a separate tool

---

## 3. infusion-analysis

**Purpose:** Jupyter notebook / uv-based analysis workspace for the ForInfPro infusion process data. Very early stage (README is `# TODO`).

**Dependencies** (from `pyproject.toml`):
- `shepard-client >5.0.0` — v1 API client
- `sparqlwrapper` — for ontology/SPARQL queries
- `pandas`, `matplotlib`, `seaborn`, `networkx`, `opencv-python`, `imageio`
- `pygwalker` — interactive data exploration in Jupyter

**Key signal:** Uses both `shepard-client` (data retrieval) and `sparqlwrapper` (semantic queries). This is the "Jupyter notebook analyst" persona in practice — raw data + ontology queries in one environment.

**ForInfPro context** (from PPTX): The infusion process (Liquid Resin Infusion / LRI) collects data from:
- OPC UA: infusion machine (pressure, temperature, resin flow)
- MODBUS: vacuum pump
- OPC UA: vacuum sensors (3× independent sensors)
- Hot folder: process camera frames (JPEG, timed acquisition)

Each experiment = one DataObject with 4 containers (timeseries × 3, files × 1). The analysis notebook correlates vacuum pressure, resin flow, and camera frames to detect dry spots.

**Integration with MCP:** The `compare_channels` tool in `shepard-plugin-mcp` is exactly what this analysis does manually. A Claude agent with MCP access could answer "did this infusion run have a dry spot?" by fetching pressure + flow channels and comparing against the semantic annotation constraints (QA-1).

---

## 4. instdlr (INST.DLR)

**Purpose:** An instrument metadata database following the [PIDINST schema](https://docs.pidinst.org/en/latest/white-paper/metadata-schema.html). Provides PIDs for physical instruments.

**Stack:** Python + FastAPI + MongoDB + Caddy. Helmholtz AAI (DLR IdP) + API key auth.

**PIDINST schema highlights:**
- Identifier + IdentifierType (PID from e.g. ePIC)
- Manufacturer, Model, SerialNumber
- MeasuredVariable — what the instrument measures
- Date — calibration dates, deployment dates
- RelatedIdentifier — links to other PIDs (e.g. related dataset, calibration certificate)

**DOI:** `10.5281/zenodo.15180781` — published and citable tool.

**Integration path with Shepard:**
- **Calibration link:** When a DataObject represents a measurement run, its semantic annotations (or a structured data container) should link to the instrument's PIDINST PID. This enables the EN 9100 auditor to trace "which calibrated instrument produced this data?"
- **Instrument PID as semantic annotation:** `propertyIRI = "https://schema.org/instrument"`, `valueIRI = "https://hdl.handle.net/21.T11998/..."` (the ePIC handle for the instrument)
- **Automatic linking in STC:** The timeseries collector knows which OPC UA endpoint it's reading from; instdlr can mint a PID for that endpoint, and the STC config can embed the PID so every ingested timeseries references its instrument
- This is the missing link for FAIR principle A (Accessible) — data tied to its instrument provenance

---

## 5. ForInfPro PPTX Highlights

Key facts not in any codebase:

- **Process:** Liquid Resin Infusion (LRI) of CFRP components. Vacuum-bag process, no autoclave.
- **Problem being solved:** Dry spots (resin-starved regions) are detectable in pressure traces *during* infusion but currently require manual inspection hours after the fact.
- **Data density:** ~1 sample/second per channel, 30–90 minute runs → ~2,000–5,000 data points per channel. Well within Shepard/TimescaleDB capacity.
- **Shepard role:** Data collection, storage, and provenance. The analysis (dry-spot detection) happens in Jupyter notebooks via `shepard-client`.
- **Semantic constraints in use:** The team has demonstrated (as per `project_dlr_institutional_strategy.md`) automated constraint violation detection using ontology-stored min/max values. QA-1 (`numericValue` + `unitIRI` on SemanticAnnotation) directly supports this.

---

## What Surprised Me

1. **Dataship is more mature than expected.** Full NiceGUI UI, test suite, CI, DLR corporate styling, AI-assist metadata. This is a real tool, not a prototype.

2. **STC config helper is a gap-filler for a missing UI feature.** The fact that users need a Python script to generate STC YAML points to a real Shepard gap: there's no "wire this container to a live OPC UA source" button in the UI. This is a high-value Shepard UI feature.

3. **infusion-analysis is the canonical MCP client.** If the MCP server works correctly, the infusion analysis notebook becomes ~5 lines: `get_data_object → list_channels → get_channel_data → compare_channels → check_annotations`. The 100+ lines of `shepard_client` setup code become one MCP connection.

4. **instdlr is an independent, citable DLR tool.** Not part of Shepard, but a natural complement. The PIDINST schema links instruments to datasets — exactly what EN 9100 calibration traceability requires.

5. **MFFD data is massive.** 8457 DataObjects, 170 GiB estimated. The current dataship export is direct-download-re-upload, which is impractical at this scale. The shared MinIO path (RB2b in the ReBAR doc) eliminates this bottleneck.

---

## Integration Priorities

| Priority | Integration | Value | Effort |
|---|---|---|---|
| 1 | `infusion-analysis` → MCP server | Replace 100 lines of boilerplate with MCP tools | Low (MCP server is built) |
| 2 | Shepard UI "Generate STC config" button | Replace stc-config-helper standalone tool | Medium |
| 3 | Dataship → v2 appId API | Fix slow collection browsing at MFFD scale | Low (API exists, adapter needs update) |
| 4 | instdlr PID → SemanticAnnotation link | PIDINST instrument traceability for EN 9100 | Medium (needs new annotation flow) |
| 5 | Shared MinIO (RB2b) → dataship bypasses download | Fix 1-day export time for MFFD 170 GiB | High effort, high value |
