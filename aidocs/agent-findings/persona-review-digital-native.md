---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Persona Review: Digital Native Researcher (2026-05-22)

**Persona:** 28yo postdoc. GitHub + Jupyter + Claude. Excel is legacy. First arrival question: "is there an API?"

**Regenerated** after worktree cleanup lost the previous review pass. State-of-the-codebase as of 2026-05-22 — different from the `persona-digital-native.md` snapshot (MCP now shipped, v2 surface broader, SPARQL endpoint live).

**Brief cited three docs that do not exist on disk:**
- `aidocs/semantics/98-mffd-process-shapes.md` — absent
- `aidocs/platform/100-mffd-views-workspace.md` — absent
- `aidocs/platform/101-view-shapes-and-spi.md` — absent

The closest extant design is `aidocs/semantics/95-shacl-templates-and-individuals.md` (1792 lines, "ontology IS the UI", shapes as templates/views/agent-contracts). I'm treating 95 as the stand-in and flagging the gap in `[NEEDS-CLARIFICATION]` below. **The brief's "Track 1 / Run 22192" is fictional** — no such ID in the MFFD seed. I substituted real DataObject names from `examples/mffd-showcase/seed.py` (`"AFP Layup — Q1 Shell"`, predecessor chain → `"NDT Thermography — Q1 (FAIL)"` → `"Rework — Q1 Rib Station 7"`).

---

## 1. The 5-Line Test

**Goal:** load AFP Layup Q1 Shell + predecessor chain + timeseries channels into pandas in ≤5 lines.

**Attempt A — typed Kiota v2 SDK:**

```python
from shepard_v2 import ShepardV2Client  # clients-v2/python-kiota/shepard_v2/
```

`ModuleNotFoundError`. The directory has `pyproject.toml` + README. The `shepard_v2/` package is empty — `find` returns zero `.py` files. `make generate-python` still uncommitted, same gap as the prior review. Same 30-min fix.

**Attempt B — v1 client + MCP-style flow (what actually works today):**

```python
import requests, pandas as pd
H = {"Authorization": f"Bearer {token}"}
do = requests.get(f"{base}/v2/collections/{c}/data-objects/{do_app_id}", headers=H).json()
chain = requests.get(f"{base}/v2/collections/{c}/data-objects/{do_app_id}/predecessor-chain", headers=H).json()
chans = requests.get(f"{base}/v2/timeseries-containers/{do['containers']['timeseries'][0]['containerAppId']}/channels", headers=H).json()
# ...and now I still need a 5-tuple per channel + a separate POST to /v2/sql/timeseries to get rows. Not 5 lines.
```

That's 5 lines of plumbing and zero rows. The actual data fetch is another POST to `/v2/sql/timeseries` (P10) with hand-written SQL — **no v2 GET endpoint reads channel rows by appId yet**. `get_channel_data` exists *as an MCP tool* but requires the 5-tuple `{measurement, device, location, symbolicName, field}`, which I'd have to thread from `list_channels`. A Python caller has no MCP transport without an MCP client library.

**Verdict: 5 lines is still not possible.** Realistic minimum today is ~12 lines if you know your `collectionAppId`, `dataObjectAppId`, and the SQL schema. The shipping path to 5 lines is (a) generated Kiota client + (b) a `get_channel_data_by_appid` REST endpoint that uses `timeseriesAppId` (the TS-IDc migration described in `aidocs/platform/87`).

---

## 2. MCP Gap List

**State now (not stale):** MCP IS shipped in-tree at `backend/.../v2/mcp/`. Tools that work today:
- `list_collections`, `list_data_objects`, `get_data_object` (CollectionMcpTools)
- `list_files`, `list_structured_data`, `list_annotations` (ContentMcpTools)
- `list_channels`, `get_channel_data` (TimeseriesMcpTools)
- exposed at `shepard.nuclide.systems/mcp/sse` per `project_mcp_path.md`

**What's still missing for a Claude agent exploring a real MFFD track:**

1. **`get_channel_data` still takes the 5-tuple.** `TimeseriesMcpTools.java:117–125` requires `containerAppId + measurement + device + location + symbolicName + field` — six positional params for one channel. Claude burns context re-passing them. TS-IDc collapses to one. **Block until TS-IDc lands.**
2. **No `get_predecessor_chain` / `get_successor_chain` MCP tool.** Tracing the AFP→NDT-FAIL→Rework→NDT-PASS chain requires N calls to `get_data_object` walking `predecessorSummaries[]`. The REST endpoint `/predecessor-chain` exists; the MCP tool wrapping it does not.
3. **No SPARQL MCP tool.** `POST /v2/semantic/{repoAppId}/sparql` is live (N1f), but Claude can't reach it. For SHACL-shape-driven discovery ("find all DataObjects that conform to `mffd:NDTGateShape` with status FAIL"), SPARQL is the right surface — currently invisible to the agent. **No `list_semantic_repositories` either**, so even if I add a SPARQL tool, the agent has no way to discover `repoAppId`.
4. **No view-shape-aware navigation tool.** Per `aidocs/semantics/95 §1` ("shapes as views"), the natural MCP pattern is `list_views(collectionAppId)` → `render_view(shapeIri, dataObjectAppId)` returning the filtered subgraph. Doesn't exist; design docs for it (100/101 cited in brief) don't exist either.
5. **No `search_data_objects` over attribute facets.** v1 has a POST search DSL; no MCP equivalent. Agent can't answer "all DataObjects where `propellant=LOX/LH2` AND `status=FAILED`" in one call.
6. **`get_file_text` missing.** PDFs, lab notes, CSV summaries — opaque to the agent. Was P3 in `aidocs/30 §3.4`; not in the shipped tool set.
7. **No write tools.** `create_annotation`, `set_predecessor`, `create_data_object` are designed but not shipped (read-only MCP for now). An agentic ingest flow needs them.

---

## 3. What Works (terse)

- **MCP read-side parity** for collections/data-objects/containers/channels is real. This wasn't true 2 weeks ago.
- **`/v2/sql/timeseries`** is the right escape hatch — raw SQL over TimescaleDB with JSON/CSV output. Data scientists who think in SQL can get unblocked without an SDK.
- **`appId` (UUID v7) is sortable, globally unique, k8s-label-safe.** Will be `shepardId` post-rename. Right shape.
- **v2 surface is broad now** — `/v2/timeseries-references`, `/v2/timeseries-containers/{id}/channels`, `/v2/import` (validate-then-commit), `/v2/semantic/{repo}/sparql`, `/v2/admin/*`, `/v2/snapshot/*`. ~30 distinct resource paths. Real platform.
- **The `@Tool` annotation pattern** in `TimeseriesMcpTools.java` is clean — Quarkus MCP server gives free schema generation. Adding a new tool is ~30 LOC.

## 4. What's Missing (terse)

- **Typed Python SDK** — `shepard-v2-client` pyproject exists, generated code does not. Same gap as the prior review.
- **`get_channel_data` by single `timeseriesAppId`** — blocks 5-line test, blocks ML pipeline addressing, blocks MCP ergonomics. The TS-IDc migration (`aidocs/87`) is queued.
- **View-shape-to-pandas converter** — if SHACL shapes drive UI views (per `aidocs/semantics/95 §1.2`), they should also produce `pd.DataFrame` schemas: `shepard.view(shapeIri).to_dataframe(collectionAppId)`. Doesn't exist; design unwritten (the missing docs 100/101 would specify it).
- **Jupyter widget per view** — registered SHACL shape → `ipywidget` that renders the same columns/facets as the UI. Currently zero Jupyter integration in-tree.
- **SPARQL MCP tool** — endpoint live, agent surface absent.

## 5. Arguments for Different Paths

**(a) Vue SFC view-renderer vs headless `viewSpec`:**
- **A1 — render view in Vue SFC only.** Pro: ships fast, single source. Con: Python/Jupyter/CLI consumers must reinvent the rendering rules (column order, facets, filters) from shapes. Drift inevitable.
- **A2 — headless `viewSpec` JSON + Vue + Python renderers.** Pro: SDK and agent get the same view. Con: extra abstraction, schema-of-a-schema. **Lean A2** — without it, the "ontology IS the UI" claim is UI-only and the API-first persona is locked out.

**(b) MCP tools auto-generated from view-shape vs hand-curated:**
- **B1 — hand-curated tools** (current path). Pro: tight descriptions, controlled cardinality, no schema-explosion. Con: every new payload kind = new Java code; per `aidocs/95 §1.3` the explicit goal is "zero MCP code for new shape".
- **B2 — auto-generate one `query_<shapeName>` tool per registered SHACL shape.** Pro: matches the design promise; new ontology = new tool. Con: schema bloat in `tools/list`, weaker descriptions, harder to test.
- **B3 — hybrid: hand-curated for primitives (`get_data_object`, `get_channel_data`), auto-generated for domain shapes (`query_NDTGate`, `query_AFPLayup`).** **Lean B3** — keeps primitives sharp, lets the ontology layer extend the agent surface without Java edits.

**(c) SDK shape — sync vs async vs OpenAPI-generated vs hand-crafted:**
- **C1 — Kiota-generated** (already chosen per ADR-0022, `aidocs/63`). Pro: bracketed scope, regenerates on every release tag. Con: every Kiota update can break Jupyter notebooks; ergonomics middling; sync-only-by-default for CPython.
- **C2 — hand-crafted thin client** (`shepard-py` design in `aidocs/81`) over `requests`/`httpx`. Pro: 200 LOC, 5-line ergonomics, `df = shepard.load_reference(...)`. Con: drifts from OpenAPI surface.
- **C3 — both: Kiota for full coverage, thin `shepard` facade for the 80% common ops.** **Lean C3** — facade calls Kiota under the hood. Same pattern as `kubernetes-asyncio` vs `kubernetes`. The facade is the one I import in notebooks; Kiota is the one I escape to when I need the long tail.

---

## 6. `[NEEDS-CLARIFICATION]` Block

```
[NEEDS-CLARIFICATION] Three brief-cited docs (98/100/101) don't exist on disk. How do I treat view-shape sections?
  Context: brief cites aidocs/semantics/98-mffd-process-shapes.md, aidocs/platform/100-mffd-views-workspace.md, aidocs/platform/101-view-shapes-and-spi.md.
           grep -r confirms none exist. Closest extant is aidocs/semantics/95-shacl-templates-and-individuals.md (1792 lines, ontology-as-UI design).
  Options:
    A) Skip view-shape sections entirely — pro: no fabrication. Con: leaves §4/§5 thin.
    B) Treat doc 95 as stand-in; speculate from "shapes as views" (§1.2) + composition (§5). — pro: doc 95 is the explicit progenitor. Con: 95 doesn't define a view-spec wire format or Jupyter integration.
    C) Wait for 98/100/101 to be written. — pro: honest. Con: blocks the review.
  Lean: B — flagged in §3/§4/§5 above so a reader knows what's grounded vs extrapolated.

[NEEDS-CLARIFICATION] Brief says "Track 1 (Run 22192)" — no such DataObject in MFFD seed. Substitution?
  Context: grep "22192" examples/mffd-showcase/seed.py → zero matches. Real DataObject names are "AFP Layup — Q1 Shell", "NDT Thermography — Q1 (FAIL)", "Rework — Q1 Rib Station 7", etc.
  Options:
    A) Substitute "AFP Layup — Q1 Shell" as the anchor — same persona test, real data.
    B) Substitute the LUMEN TR-004 (from the older seed; still tested by the original prior persona review).
    C) Refuse to fabricate; ask user.
  Lean: A — I used it. LUMEN is well-trodden; MFFD is the live demonstrator per project_mffd_guiding_principle.

[NEEDS-CLARIFICATION] view-shape-to-pandas converter — is this in scope for SHACL-shape SPI (aidocs/95) or a separate plugin?
  Context: aidocs/95 §1.2 declares "shapes as views" but the view consumer is implicitly the Vue UI.
           Persona wants `shepard.view(shapeIri).to_dataframe(collectionAppId)`.
  Options:
    A) Extend doc 95 with a §1.2b "shapes as data-frames" sub-design.
    B) New plugin: shepard-plugin-py-views.
    C) Bake into shepard-py SDK; SDK fetches shape + materializes pandas client-side.
  Lean: C — keeps the backend lean. SDK is where the pandas opinion belongs.

[NEEDS-CLARIFICATION] SPARQL MCP tool — does it surface as `sparql_query(repoAppId, query)` or auto-prefixed `query_mffd(...)` per repo?
  Context: SPARQL endpoint /v2/semantic/{repoAppId}/sparql is live (N1f). No MCP tool wraps it yet.
  Options:
    A) Single `sparql_query(repoAppId, query)` — full power, requires Claude to know SPARQL.
    B) One auto-generated MCP tool per registered shape — `query_NDTGateShape(...)`, etc. Schema-explosion.
    C) Hybrid — `sparql_query` for power users, shape-typed tools for the common shapes registered as agent-contracts.
  Lean: C — matches §5(b) above.

[NEEDS-CLARIFICATION] Should `shepard-py` facade ship before or after Kiota generation lands?
  Context: empty Kiota dir blocks any v2 typed client. shepard-py facade was 200 LOC over P10 SQL.
  Options:
    A) Ship facade first over raw requests; swap to Kiota when generated. — Pro: 5-line test passes in days.
    B) Generate Kiota first, then facade on top. — Pro: one path. Con: blocks weeks more.
    C) Skip facade entirely; just ship Kiota. — Pro: less code. Con: Kiota ergonomics fail the 5-line test.
  Lean: A — facade is the user-visible win. Kiota is the long-tail safety net.
```

---

## 7. Top 3 Changes Before This Is My Daily Driver

1. **Commit Kiota Python output + a 200-LOC `shepard-py` facade** (`client.load_reference(collection, data_object, reference) → DataFrame`). Closes the 5-line gap. Effort: 2 days. Unblocks: every notebook user, every ML pipeline, every "is there an API?" first impression.
2. **TS-IDc: single `timeseriesAppId` for `get_channel_data`** (REST + MCP). Removes the 5-tuple from every channel call. Effort: 1 sprint per `aidocs/87`. Unblocks: MCP ergonomics, SDK ergonomics, ML serialization, paper figure citations.
3. **SPARQL MCP tool + view-shape MCP auto-registration (B3 from §5).** Hand-curated `sparql_query`, plus auto-generated `query_<Shape>` tools as shapes are registered. Effort: 1 sprint after SHACL shapes ship. Unblocks: Claude can finally explore MFFD process chains by ontology class, not by groping containers.

---

## Verdict

**Interesting prototype that crossed into early-production for the v2 read path.** MCP shipped, SPARQL shipped, v2 surface broad. Three things still block daily-driver status: empty Python SDK, 5-tuple in channel reads, missing view-shape SPI for the agent surface. The architectural bones are right — `shepardId`-first, plugin SPI, SHACL-driven UI. The Python ergonomics layer is the thinnest part of the stack.

**Would I PR? 8/10.** Higher than before (the previous 7/10 was MCP-blocked; now MCP works and the gaps are well-shaped). The committable PRs are clear: `make generate-python` + commit, `shepard-py` facade, SPARQL MCP tool. Two-week side-project.

**Daily-driver in three months** if the top-3 land. **Production tool today** for raw-SQL+`requests` workflows. **Don't use the Kiota client yet — directory is empty.**
