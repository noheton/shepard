# Persona: Digital Native Researcher — Findings

**Persona:** 28-year-old postdoc. GitHub for everything. All analysis in Jupyter. Uses Claude and ChatGPT daily. FAIR believer. First thing on arrival: check if there's an API.

**Explored:** v2 REST surface, Python clients, MCP design, Jupyter integration design, import system, LUMEN seed data, SQL-over-HTTP surface.

---

## What I Found

### First 90 seconds

Opened `docs/user-guide.md`. Saw a Vuetify UI walkthrough. Scrolled to bottom looking for "API", found one mention. Closed it. Went straight to `backend/src/main/java/de/dlr/shepard/v2/` and grepped for `@Path`. That's where the real story is.

The v2 surface is real and growing: collections, data objects, import context, SQL timeseries, provenance, templates, annotations. The architecture is sound — appId as native identifier, clean CRUD, typed IO records. Someone who knows their stuff designed this.

Then I tried to actually use it from a notebook.

---

## The 5-Line Python Test

**Goal:** Load TR-004's timeseries channels into a DataFrame in 5 lines.

**What I tried first:**

```python
import shepard_v2  # clients-v2/python-kiota/shepard_v2/
```

**Result:** `ModuleNotFoundError`. The Kiota client directory is empty — only a `.gitkeep`. `make generate-python` has never been run on this repo. The `shepard-v2-client` package on PyPI does not exist yet.

**What I tried second:**

```python
from shepard_client import ApiClient, TimeseriesContainerApi, TimeseriesReferenceApi
# This is the v1 client, targeting /shepard/api/ — it works.
```

Getting the data for TR-004 with this client requires: find the collection numeric ID, find the DataObject numeric ID, find the timeseries container numeric ID, find the timeseries reference numeric ID, then call `get_timeseries_reference_payload` — all five before I see a single data point. Not 5 lines. Closer to 20, plus knowing the numeric IDs upfront.

**What actually works today (closest to 5 lines):**

```python
import requests, pandas as pd

r = requests.post(
    "https://shepard.nuclide.systems/v2/sql/timeseries",
    headers={"Authorization": f"Bearer {token}"},
    json={"sql": "SELECT time, value FROM \"lumen-inspired-sensors\" WHERE measurement='vib_fuel_pump' AND field='g_rms' AND time >= '2024-06-04 00:08:00' ORDER BY time"},
    params={"format": "json"},
)
df = pd.DataFrame(r.json()["rows"])
```

**Problems with this:**
1. Requires knowing the container name (`"lumen-inspired-sensors"`) ahead of time — no discovery API without the v1 client.
2. Requires knowing the exact SQL syntax for the TimescaleDB schema.
3. Getting `token` requires either an OIDC password grant dance or having a pre-issued API key — the code above is 3 lines, but the auth setup is another 5.
4. The container name varies by dataset. The full SQL query for "give me all channels for TR-004" requires joining across the metadata schema that isn't documented outside the seed script.

**Honest answer: 5 lines is not possible today.** 12-20 lines is achievable if you already know your container names, token, and the SQL schema. A v1 client call with numeric ID lookup is 20+ lines. The designed `shepard-py` API — `client.load_timeseries_reference(collection="lumen-dataset", data_object="Run-001", reference="Measurements")` — would be 3 lines, but that library doesn't exist.

---

## API Friction Score (1 = friction-free, 5 = needs 3+ workarounds)

| Operation | Score | Notes |
|---|---|---|
| **Authenticate (API key)** | 1 | `"X-Api-Key: <key>"` header. Done. No dance. |
| **Authenticate (OIDC)** | 2 | Standard Keycloak password grant. 5 lines of `requests`. Works. |
| **List collections** | 1 | `GET /v2/collections?page=0&size=20`. Clean. appId in response. |
| **Get a DataObject by appId** | 2 | `GET /v2/collections/{cAppId}/data-objects/{doAppId}`. Requires knowing the collection appId too — not globally addressable by DataObject appId alone yet. |
| **Find DataObjects where `attributes.propellant = "LOX/LH2"`** | 3 | Must use v1 `POST /shepard/api/search` with an undocumented JSON DSL. No v2 search endpoint. One call, but opaque and on the frozen surface. |
| **List timeseries channels for a DataObject** | 4 | Must go through v1: get DataObject → find container referenceIds (which are DataObjectReference node IDs, not container IDs — the MCP-documented bug is real) → resolve to container → list channels via v1 API with numeric IDs. Three calls minimum, with a confusing ID mismatch trap. |
| **Load timeseries channel data into DataFrame** | 4 | Either v1 5-tuple client (~20 lines + numeric IDs) or P10 SQL endpoint (requires knowing container name + SQL schema). No v2 endpoint for timeseries data reads at all. |
| **Bulk-read all channels for TR-004** | 5 | P10 SQL can do it, but requires knowing all container names + writing a UNION query. No `get_all_channels_for_data_object` endpoint exists anywhere. |
| **Upload analysis result** | 3 | v1 `TimeseriesReferenceApi` works but requires all the numeric ID plumbing first. No v2 write endpoint for timeseries. |
| **Import a directory of files** | 3 | `GET /v2/import/context` + `POST /v2/import/validate` work cleanly. But `POST /v2/import/jobs` (execute the plan) is a stub — the validation plan has a 24h commitId that currently expires without a consumer. |
| **Use Claude to analyse TR-004 via MCP** | 5 | The MCP server doesn't exist. The design in aidocs/30 is detailed and correct — but it's a design doc, not a running service. |

---

## MCP Gap List

The `aidocs/platform/30-mcp-plugin-design.md` design doc identifies these gaps accurately. Status: none are built.

**Critical missing (blocks any useful AI agent workflow):**

- `list_timeseries_containers(dataObjectAppId)` — without this, an agent can't discover what sensor data exists
- `list_channels(containerAppId)` — can't know what channels a container has
- `get_channel_data(containerAppId, timeseriesAppId, startTime?, endTime?, maxPoints?)` — the tool the LUMEN TR-004 analysis needed; completely absent
- `get_channel_summary(containerAppId, timeseriesAppId)` — min/max/mean for quick anomaly triage
- `compare_channels(containerAppId, channels[], ...)` — multi-channel alignment for causal analysis

**The `referenceIds` bug (documented in aidocs/30, confirmed in seed data):**

`get_data_object(TR-004)` returns `referenceIds: [331, 335, 337, 1077]`. These are `DataObjectReference` join-record node IDs. An agent calling `get_data_object(331)` → 404. The field name gives zero hint it contains internal join-record IDs, not navigable DataObject IDs. The MCP fix (break out containers by kind with real `containerAppId` values) is designed but not built.

**Important but secondary:**

- `get_file_text(containerAppId, fileId)` — can't read PDF reports or CSV summaries today
- `get_structured_data(containerAppId)` — can't read structured JSON containers
- `get_predecessor_chain(dataObjectAppId)` — provenance traversal is a single call in design, requires multiple API calls today
- `search_data_objects(query, collectionAppId?)` — the v1 search DSL works but isn't an MCP tool

**The `"null"` string bug** (mentioned in aidocs/30 as the anti-pattern to fix): `search_data_objects` with no `collection_id` currently requires passing the string `"null"` not omitting the param — a classic leaky default.

---

## OPC UA Wiring Test

> "Could you wire an OPC UA source to Shepard in an afternoon?"

Looking at `examples/home-showcase/collector.py` (MQTT bridge): the pattern is clear — MQTT message → extract measurement + value → `api.create_timeseries_data_point(container_id, measurement, device, location, field, timestamp, value)`. 

For OPC UA: need to map OPC UA NodeIds to the 5-tuple `{measurement, device, location, symbolicName, field}`. That mapping is manual — write a config dict, write the subscription loop. The 5-tuple is the friction point. With `timeseriesAppId` (post-TS-IDc), this drops to a one-field config. Before that: about a day's work, not an afternoon.

---

## Top 3 Features That Would Make This My Daily Driver

**1. `shepard-py` SDK with working `load_timeseries_reference()` → DataFrame**

The design in aidocs/81 is exactly right. I need:
```python
import shepard
df = shepard.client().load_timeseries_reference(collection="lumen-dataset", data_object="TR-004", reference="Hot-fire sensors")
```
This is the "killer" from a notebook workflow standpoint. Everything else can be patched around. This cannot. Until it exists, Shepard is a storage backend I query with raw `requests`, not a research workflow tool.

**2. TS-IDa/IDb/IDc: channels get a stable `appId`**

My ML pipeline looks like this today: `zip(measurements, devices, locations, symbolic_names, fields)` across 5 parallel lists to identify channels. After TS-IDc: `channel_ids: List[str]`. This is not a cosmetic fix — it changes how I serialize model inputs, how I reference channels in paper figures, how I write annotation scripts. The TS-ID migration should have happened before the v2 surface shipped. It's queued but not in-flight.

**3. MCP-1b: timeseries tools in the MCP server**

I use Claude daily. If I can say "compare vibration channels for TR-004 vs TR-003 and tell me where the anomaly starts" and get a real answer from real data — not a hallucinated summary — Shepard becomes part of my analysis workflow, not parallel to it. The MCP design is solid. Building MCP-1a + MCP-1b is the shortest path to making Claude a genuine co-analyst instead of a note-taker.

---

## What Surprised Me

**Good surprises:**
- The P10 SQL-over-HTTP endpoint (`POST /v2/sql/timeseries`) is genuinely useful and not something I expected in an RDM system. Writing SQL directly against TimescaleDB via HTTP with JSON or CSV output is the right call for data scientists who think in SQL.
- The import system (IMP1) has a real dry-run / validate / plan-seal pattern. This is exactly what a script-first ingest workflow needs — validate before committing, get a commitId, execute separately. The design is mature.
- appId as UUID v7 is the right call. Sortable by creation time, globally unique, works as a k8s label. Someone thought about this.

**Bad surprises:**
- The Kiota Python client directory (`clients-v2/python-kiota/shepard_v2/`) is empty. The README describes it as the v2 client; the directory is a `.gitkeep`. This is the first thing a new researcher finds when they try to `pip install` the v2 client.
- There is no v2 endpoint for reading timeseries data at all. `GET /v2/...` has no timeseries data read. The only v2 path to timeseries data is the SQL endpoint, which requires knowing your schema. This is a significant gap given that timeseries IS the primary payload kind.
- The `referenceIds` field returns DataObjectReference node IDs, not DataObject IDs or container IDs. The field name is actively misleading. Any competent API user hits a 404 the first time they try to use it.

---

## Honest Verdict

**Interesting prototype with real architectural bones.**

The design decisions are good — appId-first, plugin SPI, clean v1/v2 split, SQL surface. The team clearly knows what they're building toward. The aidocs directory is better organized than most internal research projects I've seen. The vision is coherent.

But the Python-first workflow is still design docs and empty directories. `shepard-py` doesn't exist. The Kiota client hasn't been generated. The MCP server is a 336-line design doc. The Jupyter integration is a 354-line design doc. Until at least one of these paths is actually installed — `pip install shepard-py && import shepard` returns a working client — the API-first researcher hits a wall at step 2.

The v1 `shepard-client` on PyPI works, but it's for the frozen surface and uses numeric IDs. It's not where new features land.

**I'd use it in parallel with my folder system for 3 months while watching the `shepard-py` PR. Then migrate.**

---

## PR Likelihood Score

**7/10** — would open a PR.

The gaps are clear, the codebase is readable, and the architecture doesn't fight you. The specific PRs I'd open:

1. **Run `make generate-python` and commit the output** — this is a 30-minute task that unblocks everyone trying the v2 Python client.
2. **`shepard-py` minimal viable SDK** — `client.load_timeseries_reference()` → DataFrame using P10 SQL under the hood until TS-IDc ships. ~200 lines of Python. This is the killer feature.
3. **TS-IDa + TS-IDb** — mint appIds on existing Timeseries nodes + expose in v2 responses. The migration is one Cypher statement; the IO change is one field. Zero breaking changes. This unblocks everything else.

What stops me from being a 9/10: the backend is Java/Quarkus + Neo4j OGM, and the migration coordination (Neo4j migration files, OGM entities, IO records, REST resources) has a lot of moving parts for a one-sprint outside contribution. Easier to contribute the Python SDK and docs, harder to contribute the backend timeseries layer without spending time with the full local dev setup.

---

## Gaps Summary

| Gap | Effort | Unblocks |
|---|---|---|
| Run `make generate-python` and commit | 30 min | v2 Python client exists |
| `shepard-py` minimal SDK (`load_timeseries_reference` via P10 SQL) | 2 days | 5-line notebook code |
| TS-IDa + TS-IDb (mint + expose channel appIds) | 1 sprint | TS-IDc, MCP-1b, ML pipelines |
| MCP-1a + MCP-1b (navigation + timeseries tools) | 2 sprints | Claude as co-analyst |
| v2 attribute search (`GET /v2/search?attributes.propellant=LOX/LH2`) | 3 days | filter without POST DSL |
| `POST /v2/import/jobs` (execute the import plan) | 1 sprint | agentic ingest |
| JupyterHub integration (J2a-J2c) | 2 sprints | click-to-notebook workflow |
