---
stage: decommissioned
last-stage-change: 2026-05-23
---

# Quarkus MCP, Kadi4Mat, and RDM Ecosystem Gaps — Findings

**Date:** 2026-05-21  
**Scope:** Three parallel research threads: (1) Quarkus MCP extension version/fitness, (2) Kadi4Mat competitor analysis, (3) RDM ecosystem gaps vs. competitors  
**Method:** Design doc review, pom.xml audit, Maven Central version checks, external web fetches for all five named competitors, cross-reference with existing `quarkus-ecosystem.md` and `ecosystem-advocate.md` findings

---

## What I found

### Quarkus version in use

`backend/pom.xml` line 54: `<quarkus.platform.version>3.27.2</quarkus.platform.version>` (LTS release, Java 21).

### quarkus-mcp-server-http — artifact status

| Property | Value |
|---|---|
| Group ID | `io.quarkiverse.mcp` |
| Artifact ID | `quarkus-mcp-server-http` |
| Latest version | **1.12.1** (released 2026-05-18, two days before today) |
| Design doc version | 1.12.0 (published 2026-04-21) |
| MCP spec implemented | 2025-11-25 |
| Transports in this artifact | Streamable HTTP (default, `/mcp`) + SSE (legacy, `/mcp/sse`) — both in one JAR |
| Stability | Production-ready per Quarkiverse docs; 47 releases, 190 stars, 63 forks |
| OIDC integration | Confirmed — `quarkus-oidc` bearer tokens work on both SSE and Streamable HTTP endpoints; auth failures return MCP error code `-32001`, not HTTP 401 |
| Former SSE-only artifact (`quarkus-mcp-server-sse`) | Relocated → `quarkus-mcp-server-http` as of 1.12.x; the old artifact is a Maven relocation stub only |
| Quarkus 3.x compat | Confirmed for Quarkus 3.27.x range (the getting-started scaffolding references Quarkus 3.33.2 as baseline; the BOM gap does not block usage — Quarkiverse extensions are Quarkus-BOM-independent) |

### CDI annotation set available (from Quarkiverse docs)

- `@Tool` — with `description`, `annotations.readOnlyHint`, `annotations.destructiveHint`, `annotations.idempotentHint`, `annotations.openWorldHint`, `annotations.title`
- `@ToolArg` — per-parameter description injected into tool schema
- `@Resource` / `@ResourceTemplate` — with `audience` and `priority`
- `@Prompt` — prompt templates
- Injectable per-call context: `McpLog`, `McpConnection`, `RequestId`

### Open issues to watch (GitHub issue tracker, May 2026)

| Issue | Impact on Shepard |
|---|---|
| #790 — Native mode + JSON schema output fails | Not a concern — Shepard is not building a GraalVM native image |
| #789 — Tracing context not propagated into tool executions | **Notable**: the `quarkus-ecosystem.md` finding "1.12.x adds per-tool OTel tracing" needs a caveat. Spans are created per tool invocation, but the OTel context is not propagated downstream into service calls. Tool-level spans will appear in Grafana Tempo but Neo4j / TimescaleDB child spans will not be attributed to them until #789 is resolved. |
| #711 — URL mode elicitation (MCP 2025-11-25) not yet supported | Minor — relevant only if tools need to request file uploads from the client |

### SSE deprecation — what the design doc does not flag

`aidocs/88` §2 proposes mounting the Quarkus MCP endpoint at `/mcp/sse` (the SSE path) for Phase 2 cutover. However, the MCP protocol specification deprecated SSE as a standalone transport in 2025-03-26. The `quarkus-mcp-server-http` artifact ships **both** SSE (at `{rootPath}/sse`) and Streamable HTTP (at `{rootPath}`) in the same JAR. The design doc's Zoraxy config table shows both phases using the SSE URL — this is valid for now (Claude remote connectors still support SSE) but the post-Phase-3 target should shift to `{rootPath}` (Streamable HTTP) when Claude desktop and the Keycloak `mcp-client` PKCE flow support it. Worth a one-liner in the design doc.

### Alternative Quarkus MCP implementations

There are no mature alternatives to `io.quarkiverse.mcp:quarkus-mcp-server-http`. The Quarkiverse extension is the canonical, actively-maintained implementation. No other Quarkus-native MCP server library exists on Maven Central. The Python FastMCP sidecar is the only "alternative" and is the incumbent being replaced.

---

## Quarkus MCP recommendation

**Use `io.quarkiverse.mcp:quarkus-mcp-server-http:1.12.1`.**

The design doc (`aidocs/88`) specifies `1.12.0`. Bump to `1.12.1` — released 2026-05-18, it is a patch release with no breaking changes. There is no better-maintained or more Quarkus-native alternative. The artifact is the right choice for the following confirmed reasons:

1. **Both SSE and Streamable HTTP in one JAR** — no separate artifact needed. The design doc's Phase 1 → Phase 2 SSE-first then Streamable HTTP transition is supported within the single dependency.

2. **OIDC bearer token gate confirmed** — `quarkus-oidc` applies natively to both `/mcp/sse` and `/mcp` endpoints. The "Phase 1 blocker" flagged in `aidocs/88` §2 is resolved: no extra `@RolesAllowed("**")` filter is needed; standard `quarkus.http.auth.permission.mcp.paths=/mcp/*` + `policy=authenticated` is sufficient, same as the `/v2/` surface.

3. **`with-plugins` Maven profile makes CDI tool discovery build-time-complete** — plugin JARs on the compile-time classpath have their `@Tool` CDI beans discovered at build time, identical to how `@Path` REST resources are discovered today. The Python `McpToolProvider` SPI is architecturally redundant.

4. **OTel tracing** — spans per tool invocation ship in 1.12.x. Caveat: issue #789 means downstream context propagation (Neo4j / TimescaleDB child spans) is not yet attributed to tool spans. Monitor issue resolution before advertising full distributed tracing coverage.

**Maven coordinates for `backend/pom.xml`:**

```xml
<dependency>
  <groupId>io.quarkiverse.mcp</groupId>
  <artifactId>quarkus-mcp-server-http</artifactId>
  <version>1.12.1</version>
</dependency>
```

**One amendment to `aidocs/88`:** Add a note in §6 that the Phase 2 Zoraxy cutover should target the Streamable HTTP root path (`/mcp`, not `/mcp/sse`) once the Claude remote connector supports Streamable HTTP — SSE remains valid for now but is deprecated in the MCP spec. The version bump from 1.12.0 to 1.12.1 is the only other material change.

---

## Kadi4Mat comparison

### What Kadi4Mat is

Kadi4Mat (Karlsruhe Institute of Technology, Institute for Applied Materials) is a generic open-source virtual research environment. Originally built for materials science but domain-agnostic in its design. Apache 2.0 license. Stack: Flask (Python backend), SQLAlchemy ORM, Vue.js frontend, Jinja2 templates; database is SQL (likely PostgreSQL). Self-hosted or available as GWDG-SaaS. ~98 releases; KadiAPY Python client library + CLI; KadiAI (ML integration), KadiStudio (workflow designer), KadiFS (filesystem mount).

### Kadi4Mat data model

| Concept | Description |
|---|---|
| **Records** | Core data entity — analogous to Shepard DataObject. Holds files, metadata key-value pairs, and record-type schema |
| **Collections** | Grouped records — analogous to Shepard Collection |
| **Templates** | Reusable metadata schemas with SHACL export — analogous to Shepard ShepardTemplate DSL (T1a) |
| **Groups** | Access-control groups of users |
| **Workflows** | Documented, reproducible pipelines (KadiStudio) |
| **Knowledge Graphs** | Auto-generated visualization of record relationships |

### Feature comparison: Shepard vs Kadi4Mat

| Feature | Shepard | Kadi4Mat |
|---|---|---|
| **Timeseries** | First-class: TimescaleDB native, MQTT/OPC-UA ingestion, inline ECharts, MAD anomaly detection, LTTB downsampling | None native — users bring external TS tools (InfluxDB, etc.) |
| **Graph provenance** | Neo4j Predecessor/Successor DAG, W3C PROV-O + metadata4ing dual-typing | Knowledge Graph visualization; no formal W3C PROV-O model confirmed |
| **Semantic annotation** | n10s inside Neo4j; 14 pre-seeded ontologies; SPARQL proxy; SemanticAnnotation SPI | Template-based schema with SHACL export; EBI OLS ontology lookup; more structured than Shepard's key-value annotations |
| **FAIR compliance** | DOI (DataCite plugin), RO-Crate 1.2 export, Helmholtz HKG/Unhide plugin, KIP PID | DOI registration (user-initiated), RO-Crate export, documented FAIR compliance |
| **API surface** | REST v1 (upstream compat) + v2 (fork additions) + MCP server (in design) | Full REST API (v1), KadiAPY Python client with CLI, KadiAI integration |
| **Plugin / extension** | Drop-in JAR SPI; 11 plugins shipped | Plugin API; Flask extensions; KadiAI + KadiStudio ecosystem |
| **Template / schema enforcement** | T1 DSL with required-field enforcement | Record types with schema enforcement and SHACL export |
| **Deployment** | Docker Compose, self-hosted | Self-hosted or GWDG SaaS |
| **Adoption** | DLR internal; <5 known external instances | 100+ public records; NFDI4ING community; KIT + FZJ + partner institutes |
| **License** | Apache 2.0 | Apache 2.0 |
| **ELN capability** | Lab journal (J1 shipped in backend; UI partial) | Full ELN integration — documented workflows with API automation |

### Where Shepard wins vs Kadi4Mat

1. **Timeseries is the decisive differentiator.** Kadi4Mat has no timeseries storage. An aerospace manufacturing experiment with 10 simultaneous OPC-UA channels and 30-minute runs cannot use Kadi4Mat as its primary data store — it needs an external TS database and a manual linkage mechanism. Shepard solves this natively.
2. **Helmholtz Knowledge Graph publishing.** The Unhide plugin makes Shepard the only DLR-origin platform that publishes machine-readable JSON-LD to the HKG automatically after configuration. Kadi4Mat does not have HKG integration.
3. **W3C PROV-O provenance model.** Shepard's Predecessor/Successor chain is dual-typed with `m4i:ProcessingStep` (metadata4ing / NFDI4Ing) — this makes provenance records interoperable with SPARQL queries across the NFDI network. Kadi4Mat's knowledge graphs are visualization aids, not machine-actionable provenance.
4. **Plugin-first JAR SPI.** Kadi4Mat uses a Flask plugin API; Shepard's JAR SPI is more structurally isolated (each plugin has its own release cycle, test suite, and CVE surface). The pattern is closer to Eclipse Equinox than to Flask blueprints.

### Where Kadi4Mat wins vs Shepard

1. **Community and adoption.** 100+ public records, NFDI4ING embedded presence, multi-institute deployment. Shepard has deeper capabilities but narrower adoption.
2. **ELN completeness.** KadiStudio workflow designer + ELN integration is more complete than Shepard's lab journal (J1 is backend-complete, UI is partial).
3. **Schema enforcement.** SHACL export of record type schemas is a stronger interoperability guarantee than Shepard's T1 DSL. A downstream tool can consume a Kadi4Mat SHACL constraint and validate records independently.
4. **Materials science domain specifics.** Kadi4Mat has mature support for simulation code outputs (DFT, FEM), spectra, and materials structure files. Shepard has no equivalent specialized payload support for computational materials science.
5. **SaaS offering.** GWDG SaaS reduces operational friction for institutes without DevOps capacity. Shepard is self-hosted only.

### DLR-specific note

Kadi4Mat is used at Helmholtz centres including KIT and (reported in some NFDI contexts) FZJ. There is no confirmed DLR adoption as of the time of this writing. The natural DLR niche for Kadi4Mat would be computational materials science groups (e.g., DLR Institute for Materials Research / Institut für Werkstoff-Forschung); Shepard's niche is experimental / manufacturing / sensor data (ZLP Augsburg, LUMEN Lampoldshausen). They are more complementary than directly competitive for the typical DLR research group.

---

## RDM ecosystem gaps

This section compares Shepard against all six named competitors: Kadi4Mat (covered above), Coscine, NOMAD, SciCat, openBIS, and FAIRDOM-SEEK. The `ecosystem-advocate.md` file already covers Coscine and SciCat in a competitive table; this section focuses on what each platform offers that Shepard **does not have yet**, with priority ordering for Shepard's roadmap.

### Competitor profiles (what they do that Shepard doesn't)

**Coscine (FZJ / RWTH, NFDI4ING)**
- 1,500+ users, 138 institutions — largest adoption footprint in German engineering RDM
- Tight DMP (Data Management Plan) integration: DMPs link to individual datasets at creation time
- Institutional identity management (DFN-AAI, federation)
- What Shepard lacks: structured DMP linkage at collection creation; no "DMP-linked dataset" concept
- Note: Coscine has no timeseries support and no provenance DAG, making it weaker than Shepard for experimental campaigns.

**NOMAD (Max Planck / Fritz Haber / Helmholtz)**
- 12.5M+ entries, 109 TB of files; computational materials science focus
- 60+ format parsers for DFT output codes (VASP, Quantum ESPRESSO, ABINIT, etc.)
- Schema-based metadata extraction: uploads are parsed and structured automatically; no manual annotation required
- NOMAD Oasis: self-hosted instance with federation to central NOMAD — datasets on a local Oasis appear searchable in the central portal
- What Shepard lacks: automated metadata extraction from uploaded file content (no parser pipeline for any file format); no federation mechanism to a central catalogue
- Note: NOMAD is domain-specific to computational physics/chemistry. Aerospace manufacturing sensor data is outside its intended scope.

**SciCat (PSI / ESS / DESY / MAX IV / ILL / MLZ)**
- Photon/neutron large-scale facility experiments
- Proposal-based data model: datasets are linked to proposals and techniques (beamline, method)
- Ingestor pattern: data from facility instruments flows in via instrument-specific ingestors; no manual upload UI
- What Shepard lacks: proposal/experiment-centric organization (Shepard's Collection is free-form); no ingestor plugin pattern that instruments push to automatically
- Note: SciCat's model is tightly coupled to large-scale facility workflows. It is not self-serve in the same way Shepard is.

**openBIS (ETH Zürich / Empa / QBiC Tübingen / BAM Berlin)**
- Full ELN + inventory management + data management
- Hierarchical data model: Space → Project → Experiment → Sample → Dataset
- Inventory tracking: physical samples, reagents, instruments with barcode/QR-code support
- Audit trail on every database modification (provenance at the infrastructure level, not the graph level)
- Plugin system (Jython-based)
- Strong biomedical track record; also adopted at materials science institutes (Empa, BAM)
- What Shepard lacks: physical inventory / sample tracking; hierarchical Space/Project organization (Shepard's graph is flat with only Predecessor/Child); ELN completeness (lab journal UI is partial)

**FAIRDOM-SEEK (HITS gGmbH / University of Manchester)**
- Systems biology primary domain (COMBINE, BioModels, metabolomics)
- Investigation → Study → Assay hierarchy (ISA model)
- DOI via DataCite, SPARQL queries, BioSchemas semantic markup, EBI OLS ontology lookup
- Zenodo push-deposit for archival
- What Shepard lacks: ISA model hierarchy (Study → Assay) for experimental design; Zenodo push-deposit integration; BioSchemas markup output

### Consolidated gap table — features competitors have that Shepard lacks

| Gap | Competitor(s) with it | Shepard gap severity | Roadmap status |
|---|---|---|---|
| **Structured DMP linkage** | Coscine, FAIRDOM-SEEK | Medium — required for DFG/Horizon compliance | Not designed |
| **Federation to central catalogue** | NOMAD Oasis → central NOMAD | High — Shepard instances are siloed | Partially addressed by Unhide/HKG plugin (UH1a) |
| **Automated file content parsing** | NOMAD (60+ format parsers) | Medium-High — researchers must annotate manually | Not designed; closest: AI plugin auto-annotation (aidocs/86) |
| **Sample / inventory tracking** | openBIS | Medium for life sciences, lower for aerospace | Not designed; DataObject covers this use case loosely |
| **Ingestor push pattern** | SciCat | Low for current DLR use case | STC collector fills this role for timeseries; file ingestor gap remains |
| **ELN completeness** | openBIS, Kadi4Mat, FAIRDOM-SEEK | High — lab journal UI is backend-complete but UI-partial | J1 backend done; UI sprint needed |
| **SHACL schema export** | Kadi4Mat | Low — Shepard templates enforce fields but don't export SHACL | Not designed |
| **Zenodo push-deposit** | FAIRDOM-SEEK | Medium — important for final archival | Not designed; Databus (dataship) fills partial role |
| **Proposal/experiment hierarchy** | SciCat | Low for manufacturing use case | Not needed for Shepard's primary use case |
| **ISA model (Investigation/Study/Assay)** | FAIRDOM-SEEK | Low for aerospace/engineering | Not needed |
| **SaaS offering** | Kadi4Mat (GWDG), Coscine | High for adoption | Not planned |
| **License field on entity** | All competitors | **Critical** — missing field blocks FAIR compliance and Unhide | One-sprint fix (KIP1e, flagged in ecosystem-advocate.md Tier 1) |
| **ORCID stamp at creation** | Kadi4Mat, openBIS, FAIRDOM-SEEK | High — loss of attribution on user deletion | One-line fix; flagged in ecosystem-advocate.md Tier 1 |
| **Access rights enum (open/restricted/closed)** | All competitors | High — required for embargo and FAIR principle A | Not designed; `status` field is the closest proxy |

### Priority ordering for Shepard's roadmap (gap-closing, highest value first)

1. **License field on Collection/DataObject** — one sprint, blocks Unhide FAIR compliance, DataCite, InvenioRDM push, Metadata Completeness Score. Every competitor has this. (KIP1e)
2. **ORCID stamp at entity creation** — one-line service change, closes FAIR F2/R1 gap, prevents attribution anonymisation on user deletion.
3. **ELN UI completeness (lab journal panel)** — J1 backend is done; the UI sprint closes the gap vs. Kadi4Mat and openBIS for the researchers who need it most.
4. **Access rights / embargo field** — needed for FAIR principle A; straightforward additive field on DataObject with enforcement in the Unhide feed and the RO-Crate export.
5. **AI auto-annotation from file content** — closes the NOMAD-style automated metadata extraction gap; design in aidocs/86 (shepard-plugin-ai); this is the highest-complexity item but would be Shepard's strongest differentiator against NOMAD for mixed file+sensor datasets.
6. **Zenodo push-deposit integration** — closes the archival gap vs. FAIRDOM-SEEK; can be a thin plugin built on the Databus model.
7. **SHACL schema export from ShepardTemplates** — closes the Kadi4Mat schema interoperability gap; medium effort (Jena SHACL generation from T1 DSL).
8. **DMP linkage** — needed for DFG/Horizon mandates; design is open.

---

## What surprised me

**1. quarkus-mcp-server-sse no longer exists as a standalone artifact.**  
As of 1.12.x, `quarkus-mcp-server-sse` is a Maven relocation stub that points to `quarkus-mcp-server-http`. The design doc references the correct artifact (`quarkus-mcp-server-http`) but anyone searching Maven Central for the old SSE artifact will still find it — it just redirects. This relocation signals that the Quarkiverse team considers SSE and Streamable HTTP as a single transport module, not separate concerns. The `aidocs/88` design is structurally aligned with this.

**2. The OTel tracing claim in `quarkus-ecosystem.md` is partially contradicted by open issue #789.**  
The existing findings file states "1.12.x adds per-tool OTel tracing." GitHub issue #789 (currently open) says "Tracing context is not propagated into e.g. tool executions." Both can be true simultaneously: spans are created at the tool boundary, but the OTel context (trace ID, parent span) is not propagated into CDI beans called by the tool. The practical consequence is that Neo4j and TimescaleDB query spans will not appear as children of the tool span in Grafana Tempo — they will appear as orphaned root spans instead. This is worth flagging before investing time in OTel dashboards for MCP tools.

**3. Kadi4Mat and Shepard are more complementary than competitive for the DLR use case.**  
A DLR computational materials science group (DFT simulations, crystal structures) would reach for Kadi4Mat. A DLR manufacturing/testing group (AFP robot runs, engine test campaigns, satellite I&T) would reach for Shepard. The decision is domain-driven, not feature-driven. The real competitive tension is for the institute that does both (e.g., a group that runs CFD simulations and then validates against wind tunnel sensor data). For that group, Kadi4Mat has no answer; Shepard needs the license field, ORCID stamping, and ELN UI completeness to close the case.

**4. NOMAD's federation model is the gap Shepard most underestimates.**  
Every other competitor is self-contained. NOMAD Oasis instances federate to the central NOMAD portal: a dataset on a private Oasis instance appears searchable at nomad-lab.eu without the researcher doing anything additional after upload. The Helmholtz Unhide/HKG integration is Shepard's equivalent answer, but it is Helmholtz-specific. A DLR group collaborating with KIT or TU Munich partners cannot discover each other's Shepard datasets through any mechanism today. This is the structural federation gap that a "Shepard Databus MOSS" integration (referenced in `aidocs/integrations/`) would close.

**5. The license field omission is the most damaging single gap.**  
Every competitor — Kadi4Mat, Coscine, NOMAD, SciCat, openBIS, FAIRDOM-SEEK — stores a license field on individual datasets. Shepard does not. The Unhide feed emits `schema:license` using an instance-wide default. This means every collection published to the HKG asserts a license that is not the researcher's actual choice. In a FAIR audit, this is not a minor gap — it is a fundamental breach of FAIR principle R1.1 (clear and accessible usage license). The fix is a single additive field on `AbstractDataObject`; it unblocks DataCite minting, InvenioRDM push, RO-Crate export, and the Metadata Completeness Score widget in one change.
