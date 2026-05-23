---
title: Reading list — surfaced topics, not yet pursued
stage: deployed
last-stage-change: 2026-05-23
audience: contributors, maintainers, future-Claude, future-Flo
---

# Reading list

Topics, papers, OSS projects, standards, and concepts that surfaced during research
+ session work but weren't pursued (or weren't cited in a shipped doc). Companion to
`docs/_data/references.bib` (citations in shipped docs) and `aidocs/16` (backlog of
work). Maintained per `feedback_reading_list.md`.

**Status legend:** `unread` · `skimmed` · `read` · `cited` (→ moved to bib) ·
`dispatched` (→ moved to backlog) · `rejected`

**Update protocol:** every agent dispatch surfaces 2-5 entries; every research-flavoured
session adds a row. Entries graduate to bib / backlog / decommissioned as appropriate.

---

## 2026-05-23 seed (from this session's dispatches)

### AAS + EDC + dataspaces

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [doi.org/10.3390/app152111623](https://doi.org/10.3390/app152111623) | Integration approaches for Digital Twins in Dataspaces — three-architecture comparison | AAS+EDC reuse survey | The reference text for the EDC-plugin architecture decision Shepard needs; WebFetch failed to extract; manual read warranted | unread |
| 2026-05-23 | [github.com/FraunhoferIOSB/EDC-Extension-for-AAS](https://github.com/FraunhoferIOSB/EDC-Extension-for-AAS) | EDC↔AAS bridge, ~90% of the work Shepard would otherwise build | AAS+EDC reuse survey | Apache-2.0, v2.2.0; 18 months stale (supports EDC 0.15.0 vs current 0.17.0) — verify-compat + contribute-bump-PR is the move | skimmed |
| 2026-05-23 | [eclipse-tractusx.github.io](https://eclipse-tractusx.github.io/) | Tractus-X reference impl, Catena-X-certified | AAS+EDC reuse survey | 0.12.1 production-tested; right sidecar for `shepard-plugin-edc` | skimmed |
| 2026-05-23 | BMWE 13MX004F | Aerospace-X project — Apr 2024 – Jun 2026 | AAS+EDC reuse survey | 28 partners incl. Airbus + MTU + Rolls-Royce + SAP, Lead Fraunhofer ISST. DLR is already a partner. Shepard's AAS/EDC adoption = federation accession, not speculative bet | unread |
| 2026-05-23 | [github.com/eclipse-dataspaceprotocol-base/dataspace-protocol](https://github.com/eclipse-dataspaceprotocol-base/) | Dataspace Protocol (DSP) 2024-1 — replaces IDS-G (archived 2025-06-13) | AAS+EDC reuse survey | ISO PAS transposition target 2025; canonical wire contract now | unread |
| 2026-05-23 | [github.com/admin-shell-io/aas-test-engines](https://github.com/admin-shell-io/aas-test-engines) | AAS conformance test engines | AAS+EDC reuse survey | Adopt as CI harness for AAS1h | skimmed |
| 2026-05-23 | Fraunhofer ISST FDO One | FDO ↔ AAS convergence via Digital Product Pass | AAS+EDC reuse survey | Shepard's research-data + industrial-data niche is exactly the intersection both paradigms claim | unread |
| 2026-05-23 | DLR Institute for AI Safety and Security | Internal Catena-X channel | AAS+EDC reuse survey | Alternative route into Aerospace-X (not via LUMEN/MFFD institutes); confidential channel-routing question | unread |

### Synergy + recursive observability

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [openlineage.io](https://openlineage.io/) | OpenLineage RunEvent spec | Synergy S-02 | RunEvent shape maps 1:1 to F(AI)²R Activity — EU AI Act Art-50 evidence pack for free; Aug 2026 deadline | unread |
| 2026-05-23 | [pidinst.org](https://pidinst.org/) | PIDINST schema (RDA Persistent Identifier for Instruments) | Synergy S-05 | Maps 1:1 to IDTA-02006 AAS Nameplate; 1-week integration unlocks EN 9100 + Catena-X + FAIR simultaneously | skimmed |
| 2026-05-23 | [w3.org/TR/vc-data-model-2.0](https://www.w3.org/TR/vc-data-model-2.0/) | W3C Verifiable Credentials 2.0 | Synergy S-08 / AI accountability | If we sign AI-generated artefacts as VCs, downstream consumers can verify without trusting Shepard | unread |
| 2026-05-23 | [github.com/garage-data/garage](https://garage-data.github.io/garage/) docs §"object versioning" | Garage explicitly refuses S3 object versioning | Synergy S-06 | Shepard's snapshot+SHA-256 layer already compensates — turn procurement worry into marketing point | skimmed |

### Ontologies + semantic alignment

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | OAEI 2024 / 2025 LLM track | Ontology Alignment Evaluation Initiative LLM track | ONT-AI-MAP1 dispatch brief | Annual benchmark; latest results inform Shepard's choice between BERTMap / OLaLa / AML+LLM | unread |
| 2026-05-23 | SSSOM spec | Simple Standard for Sharing Ontological Mappings | ONT-AI-MAP1 dispatch brief | W3C-blessed serialisation; if Shepard emits SSSOM, mappings travel | unread |
| 2026-05-23 | OBO Foundry | Open Biological and Biomedical Ontology Foundry — but principles apply broadly | feedback_ontology_first | "ontologies should be reused" principle has formal codification here | unread |

### AI provenance + promptlog

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [github.com/noheton/f-ai-r](https://github.com/noheton/f-ai-r) | f(ai)²r — F(AI)²R vocabulary repo | promptlog dispatch | Vendor ontology for AI provenance; needs `fair2r:syntheticBackfill` extension proposal | skimmed |
| 2026-05-23 | LangSmith / Helicone / Langfuse / Phoenix (Arize) | Commercial + OSS prompt-tracking platforms | promptlog dispatch | Reuse candidates for `shepard-plugin-promptlog`; OSS subset is the realistic adoption pool | unread |
| 2026-05-23 | [github.com/traceloop/openllmetry](https://github.com/traceloop/openllmetry) | OpenLLMetry — OpenTelemetry semantic conventions for LLMs | promptlog dispatch | Vendor-neutral instrumentation; pairs with OpenLineage RunEvent | unread |
| 2026-05-23 | EU AI Act Article 50 (Regulation (EU) 2024/1689) | AI-generated content marking obligation | provenance principle | Becomes enforceable July 2026 for some categories; Shepard's PromptLog + F(AI)²R is the compliance shape | unread |
| 2026-05-23 | EASA "AI Concept Paper Issue 2 — Learning Assurance" | Regulatory guidance for ML in aerospace | REBAR integration | Maps to TPL3-TPL14 series; Shepard is data substrate for REBAR | skimmed |

### Log + observability substrates

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [github.com/quickwit-oss/quickwit](https://github.com/quickwit-oss/quickwit) | Quickwit — distributed search engine for logs (Rust) | LOGSTORE1 dispatch | Newer alternative to OpenSearch; tantivy-indexed, S3-friendly | unread |
| 2026-05-23 | [github.com/parseablehq/parseable](https://github.com/parseablehq/parseable) | Parseable — recent OSS log analytics (Rust) | LOGSTORE1 dispatch | S3-native, schema-on-write; could match Shepard's shape-driven log dimension | unread |
| 2026-05-23 | [github.com/VictoriaMetrics/VictoriaLogs](https://docs.victoriametrics.com/victorialogs/) | VictoriaLogs — Loki-style with VictoriaMetrics lineage | LOGSTORE1 dispatch | Single binary, low operational cost | unread |
| 2026-05-23 | [vector.dev](https://vector.dev/) | Vector — vendor-neutral log + metrics router (Rust) | LOGSTORE1 dispatch | Routes to any backend; could decouple emit shape from substrate choice | skimmed |

### Storage + identity

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | pgvector vs Qdrant vs Weaviate vs Milvus | Embedding-store choice for Shepard | promptlog + ontology mapper dispatches | pgvector wins on co-location with existing Postgres; performance ceiling open | unread |
| 2026-05-23 | [docs.hdfgroup.org/hdf5/develop/group___h_d_f_v_o_l_p_a_s_s_t_h_r_u.html](https://docs.hdfgroup.org/hsds/) | HSDS — HDF5 REST service | Synergy S-01 | Chunked streaming substrate; channel-as-individual unified addressing | unread |

### Repo housekeeping

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | Welzmüller, F. et al. (2024). DLR eLib 215120 | Research Data Management for Space Missions — PLUTO RDM paper | shared orientation | Flo is co-author; motivates PLUTO use case + FAIR requirements for mission data; already in bibliography | cited |
| 2026-05-23 | MFFD JEC World Innovation Award 2025 (Aerospace - Parts) | Thermoplastic CFRP no-autoclave Green Aviation angle | strategy context | Real-world hook for Clean Aviation JU KPI alignment | unread |

---

## Decommissioned

*(Nothing yet — entries land here when re-read and judged not-relevant.)*
