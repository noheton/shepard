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
| 2026-05-23 | SSSOM spec | Simple Standard for Sharing Ontological Mappings | ONT-AI-MAP1 dispatch brief | **OBO Foundry-endorsed community standard** (mapping-commons; NOT W3C — corrected per persona-audit 2026-05-23 F2.1); if Shepard emits SSSOM, mappings travel | unread |
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
| 2026-05-23 | [datacite-metadata-schema.readthedocs.io](https://datacite-metadata-schema.readthedocs.io/en/4.6/properties/contributor/) | DataCite Schema 4.5/4.6 — Contributor / nameIdentifier with ORCID + affiliation with ROR | LOGSTORE1 RDM addendum (F25) | Canonical attribution shape every FAIR-citable Shepard `audit`-tier event should map to; informs `shev:actorOrcid` + `shev:actorAffiliation` slots in EventShape | cited |
| 2026-05-23 | [iceberg.apache.org/spec](https://iceberg.apache.org/spec/) + [olake.io blog](https://olake.io/blog/2025/10/03/iceberg-metadata/) | Apache Iceberg snapshot metadata as immutable audit trail | LOGSTORE1 RDM addendum (F26) | Best-practice audit-trail-on-S3 pattern; current target for Shepard's Garage NDJSON to migrate to if audit volume 100×; engine-replacement-survivable | cited |
| 2026-05-23 | [schema.org/Action](https://schema.org/Action) | schema.org Action / CreateAction vocabulary | LOGSTORE1 RDM addendum (F28) | Canonical shape for publishing an audit-trail event as a citable Unhide-harvestable resource (agent + object + startTime/endTime + result) | cited |

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

## 2026-05-23 batch 2 — from PROMPT1 / LOGSTORE1 / ADMIN-STALE-CH / ONT-AI-MAP1

### PromptLog substrate + LLM observability

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [opentelemetry.io semantic-conventions/gen-ai](https://opentelemetry.io/docs/specs/semconv/gen-ai/) v1.38+ | OTel GenAI semantic conventions (`gen_ai.input.messages` etc.) | PROMPT1 §1 ADOPT | Wire format Shepard adopts instead of inventing one. v1.38 deprecated v1.36 fields — receiver needs both | skimmed |
| 2026-05-23 | Procko 2024 — PROV-O for LLMs | PROV-O extension for LLM provenance | PROMPT1 §1 ontology decision | Confirms PROV-O converges as the LLM provenance shape | unread |
| 2026-05-23 | PROV-AGENT 2025 + PROV-ML 2019 | PROV-O LLM agent provenance + ML lineage | PROMPT1 references | Multi-paper convergence on PROV-O for AI workflows | unread |
| 2026-05-23 | [langfuse.com docs](https://langfuse.com/docs) | Langfuse substrate-split pattern (Postgres + object store + analytics) | PROMPT1 §1 storage pattern | Adopt pattern, NOT stack — realise on Shepard's Postgres+Garage+pgvector | skimmed |
| 2026-05-23 | [promptfoo.dev](https://promptfoo.dev) | promptfoo eval harness CLI | PROMPT1 §1 ADOPT | Sidecar for prompt evaluation; OSS, MIT | unread |

### Log substrate

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [docs.victoriametrics.com/victorialogs](https://docs.victoriametrics.com/victorialogs/) | VictoriaLogs — Apache-2.0, ~30 MB Go binary | LOGSTORE1 §6 ADOPT | Primary log substrate; 87% less memory than Loki (TrueFoundry 2025 benchmark) | skimmed |
| 2026-05-23 | TrueFoundry 2025 benchmark | Loki vs VictoriaLogs memory comparison | LOGSTORE1 §6 reasoning | Decisive data — 87% memory reduction at identical workload | unread |
| 2026-05-23 | Loki / Tempo / Quickwit / Parseable 2026 licensing | AGPLv3 across the board | LOGSTORE1 §3 reuse-survey | License-axis was the discriminator; sidecar-isolation defeats dep-review but operator-redistribution burden remains | unread |
| 2026-05-23 | [vector.dev VRL](https://vector.dev/docs/reference/vrl/) | Vector Remap Language — pre-validation expressions | LOGSTORE1 §4 SHACL-shape pre-validation | How Shepard validates EventShape SHACL at ingest before Logs storage | unread |

### Stale-channel admin + storage cleanup

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | TimescaleDB chunk docs + compression stats | Hypertable cleanup patterns | ADMIN-STALE-CH §3 detection algo | Cross-substrate set-difference using Postgres `REPEATABLE READ` + Cypher | unread |
| 2026-05-23 | Prometheus issue #10598 | TSDB stale-series garbage collection patterns | ADMIN-STALE-CH §11 risks | Community discussion on tombstone problem post-deletion | unread |
| 2026-05-23 | Cloudflare blog + Maxima FinOps + TechTarget | FinOps storage cost management | ADMIN-STALE-CH §1 motivation | Quantifies dataset retention cost — why this matters | unread |

### LLM ontology alignment

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [arxiv.org/html/2503.21902v1](https://arxiv.org/html/2503.21902v1) — Babaei Giglou et al. 2025 | **OntoAligner v1.8.0** (Apache-2.0, TIB Hannover) | ONT-AI-MAP1 §3 ADOPT | **Released 2026-05-22**, day before survey. Supports MSE track exactly matching CHAMEO ↔ Material OWL. TIB = NFDI hub Shepard already aligned to | skimmed |
| 2026-05-23 | [arxiv.org/abs/2409.14038](https://arxiv.org/abs/2409.14038) + [2503.21813](https://arxiv.org/pdf/2503.21813) — Lin, Zhou et al. | **OAEI-LLM hallucination benchmark** | ONT-AI-MAP1 §2 risk evidence | 5-18% false-positive + 20-35% false-negative; TBox alignment 2-3× worse than ABox. Justifies LLM-as-oracle pattern (not LLM-as-proposer) | unread |
| 2026-05-23 | [doi.org/10.1093/database/baac035](https://doi.org/10.1093/database/baac035) — Matentzoglu et al. 2022 + OAEI 2024 synthesis | **SSSOM** spec + CANARD +45% F-measure with LLM embeddings | ONT-AI-MAP1 §8 SSSOM emission | **OBO Foundry-endorsed community standard** (mapping-commons; NOT W3C — corrected per persona-audit 2026-05-23 F2.1); Shepard emits SSSOM for portability | unread |
| 2026-05-23 | OLaLa (Hertling & Paulheim K-CAP 2023) | One of the first LLM ontology alignment works | ONT-AI-MAP1 §2 literature | Cited as origin of LLM-as-oracle pattern | unread |
| 2026-05-23 | DeepOnto (He et al., SWJ 2024) | KRR-Oxford Python package for ontology engineering w/ deep learning | ONT-AI-MAP1 §3 ADOPT fallback | Backup option to OntoAligner; Apache-2.0 | unread |
| 2026-05-23 | [github.com/AgreementMakerLight](https://github.com/AgreementMakerLight) (AML) | AML — long-running OAEI competitor | ONT-AI-MAP1 §3 reuse-survey | Long history; LogMap-style deterministic baseline | unread |
| 2026-05-23 | [inria.hal.science/hal-05447839v1](https://inria.hal.science/hal-05447839v1/document) — *Results of OAEI 2025* | **OAEI 2025 synthesis report** (16 tracks, NO MSE/materials track) | ONT-AI-MAP1 persona-audit F3.3 | MSE track dropped after 2023; survey claim of "MSE track support out of the box" needs revision; potential ESWC 2026 OAEI-MSE-revival paper opportunity | unread |
| 2026-05-23 | [github.com/EngyNasr/MSE-Benchmark](https://github.com/EngyNasr/MSE-Benchmark) | **MSE-Benchmark** (last commit Nov 2022, dormant) | ONT-AI-MAP1 persona-audit F3.3 | The only public ground-truth dataset for CHAMEO/MatOnto-style alignment; dormant means Shepard must run its own evaluation or revive the benchmark | unread |
| 2026-05-23 | [pypi.org/project/OntoAligner](https://pypi.org/project/OntoAligner/) | **OntoAligner PyPI release history** (8 releases Jun-2025 → May-2026) | ONT-AI-MAP1 persona-audit F3.1 evidence | Confirms OntoAligner is genuinely active; refutes any concern about dependency staleness | cited |
| 2026-05-23 | [pypi.org/project/deeponto](https://pypi.org/project/deeponto/) | **DeepOnto PyPI release history** (last v0.9.3 Mar 2025) | ONT-AI-MAP1 persona-audit F3.1 BLOCKING finding | 14-months stale as of 2026-05; survey §3.2 T2 ADOPT-as-fallback must downgrade to WATCH per `feedback_reuse_before_reimplement.md` activity-heuristic discipline | cited |
| 2026-05-23 | [easa.europa.eu/.../easa-artificial-intelligence-concept-paper-issue-2](https://www.easa.europa.eu/en/document-library/general-publications/easa-artificial-intelligence-concept-paper-issue-2) | **EASA AI Concept Paper Issue 2** — W-shaped ML development process | ONT-AI-MAP1 persona-audit F4.1 | Learning Assurance posture if Shepard ever feeds safety-critical aviation; currently scope is research-RDM (gap is documentation-only) | unread |
| 2026-05-23 | [artificialintelligenceact.eu/transparency-rules-article-50](https://artificialintelligenceact.eu/transparency-rules-article-50/) | **EU AI Act Article 50** enforcement date 2026-08-02 | ONT-AI-MAP1 persona-audit F3.5 + TPL9 | Hard deadline for f(ai)²r-attributed AI provenance to be production-ready; structural differentiator vs other RDM platforms | unread |

### Log substrates + observability (from LOGSTORE1 persona audit)

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [opentelemetry.io/docs/specs/otel/logs/data-model/](https://opentelemetry.io/docs/specs/otel/logs/data-model/) | **OpenTelemetry Logs Data Model** — Resource vs Attribute separation, `log.record.uid`, `log.record.original` | LOGSTORE1 persona audit F4/F18 | Server-stamped Resource attributes are standard practice — directly applicable to the EventShape wire-shape collapse fix; OTLP-equivalence column on the SHACL shape forward-compatibility | skimmed |
| 2026-05-23 | [docs.victoriametrics.com/operator/](https://docs.victoriametrics.com/operator/) | **VictoriaLogs Kubernetes Operator** — `VLSingle` / `VLCluster` CRDs, render-before-apply patterns | LOGSTORE1-OPS5 (compose render) | The production-deployment pattern Shepard's compose-render endpoint should mirror; VictoriaLogs GA'd in VM Cloud Q1 2026 — operational maturity still building | skimmed |
| 2026-05-23 | [victoriametrics.com/blog/q1-2026-whats-new-victoriametrics-cloud/](https://victoriametrics.com/blog/q1-2026-whats-new-victoriametrics-cloud/) | **VictoriaMetrics Cloud Q1 2026** — VictoriaLogs GA, Splunk ingestion, MCP server, advanced LogsQL tooling | LOGSTORE1-STR2 (maturity risk) | GA is recent; quarterly review trigger for the maturity-risk runbook should track these release-notes cadence; Splunk + MCP suggest direction-of-travel for Shepard's own MCP tool surface | unread |
| 2026-05-23 | [docs.victoriametrics.com/victorialogs/data-ingestion/](https://docs.victoriametrics.com/victorialogs/data-ingestion/) | **VictoriaLogs jsonline ingestion** — `_msg_field`, `_time_field`, `VL-Stream-Fields`, AccountID/ProjectID tenant headers | LOGSTORE1-API4 (server-derive fields) + tenant tier mapping | The actual wire-shape Shepard's emit endpoint should wrap; tenant header maps directly to the four retention tiers (`audit`/`ops-30d`/`debug-7d`/`ephemeral-1d`); LogLayer transport example is the cleanest reference | skimmed |
| 2026-05-23 | [opencoreventures.com/blog/agpl-license-is-a-non-starter-for-most-companies](https://www.opencoreventures.com/blog/agpl-license-is-a-non-starter-for-most-companies) | **AGPL as enterprise non-starter** — operator-redistribution friction even with sidecar isolation | LOGSTORE1 §3 licence gate | Confirms the "kinder-licence-by-default" posture; relevant beyond logs (every Loki/Tempo/Quickwit/Parseable evaluation) — fork-wide adoption pattern | skimmed |

### PromptLog persona audit (PROMPT1, 2026-05-23)

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [Tiger Data — pgvectorscale vs Pinecone benchmark](https://www.tigerdata.com/blog/pgvector-is-now-as-fast-as-pinecone-at-75-less-cost) | pgvectorscale 471 QPS @ 99% recall on 50M vectors; 28× lower p95 latency vs Pinecone s1 | PROMPT1 §4 + audit AI-1 | Decisive — pgvectorscale (not bare pgvector) is what PROMPT-c should commit to | skimmed |
| 2026-05-23 | [Langfuse v3 evolution](https://langfuse.com/blog/2024-12-langfuse-v3-infrastructure-evolution) + [Langfuse Postgres-only deprecated](https://langfuse.com/self-hosting/deployment/infrastructure/postgres) | Postgres-only deployment officially unsupported; full ClickHouse stack mandatory | PROMPT1 §4.1 counter-evidence | Sharpens PROMPT-j threshold to "100k events/day sustained" — the real Langfuse breakpoint | skimmed |
| 2026-05-23 | [promptfoo issue #5808 — remote-call default](https://github.com/promptfoo/promptfoo/issues/5808) | promptfoo sends data to remote API by default unless disabled | PROMPT1 §8 + audit AI-4 | Security/IP gotcha — flag in plugins/promptlog/docs/install.md before PROMPT-e ships | unread |
| 2026-05-23 | [morphllm — Traceloop/OpenLLMetry acquired by ServiceNow March 2026](https://www.morphllm.com/openllmetry) | OpenLLMetry parent acquired ($60-80M); Apache-2.0 license persists, governance changed | PROMPT1 §1.1 + audit AI-5 | "Very active" trajectory now subject to corporate roadmap dynamics | unread |
| 2026-05-23 | [Bird & Bird — May 2026 Article 50 Guidelines summary](https://www.twobirds.com/en/insights/2026/taking-the-eu-ai-act-to-practice-reading-the-commissions-draft-article-50-guidelines) | Article 50 Guidelines focus on watermarking + content labeling, NOT prompt logging | PROMPT1 §7 + audit RDM counter-evidence | Reframe required: Article 50 = labeling; Article 53 = the actual prompt-log hook | unread |
| 2026-05-23 | [data443 — Why Logging AI Prompts Creates Compliance Risk](https://data443.com/blog/why-logging-ai-prompts-creates-compliance-risk/) | Metadata-only + SHA-256 hash chain satisfies SOC2/HIPAA/GDPR/PCI/ISO27001/NIST CSF/CCPA | PROMPT1 §11 Risk 2 + audit RDM-1 | External validation for hash-only default; basis for ESC-2 per-Collection mode | unread |
| 2026-05-23 | [Springer 2024 — Right to be Forgotten in Cloud Data Lakes](https://link.springer.com/chapter/10.1007/978-3-031-53963-3_16) | Metadata-preserving erasure patterns | PROMPT1 §11 + audit RDM-2 | Pattern for `prov:wasInvalidatedBy` post-erasure — evidence-of-call without body | unread |

### ADMIN-STALE-CH persona audit (2026-05-23)

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [IETF RFC 9457 — Problem Details for HTTP APIs](https://datatracker.ietf.org/doc/rfc9457/) | Obsoletes RFC 7807 (July 2023); adds multi-problem `errors[]` pattern | ADMIN-STALE-CH audit F3-1 | Doc cites RFC 7807; should cite RFC 9457. Multi-problem pattern directly applies to `refused[]` array in bulk-delete responses | skimmed |
| 2026-05-23 | [DataCite — Best Practices for Tombstone Pages](https://support.datacite.org/docs/tombstone-pages) + [DOI Persistence](https://support.datacite.org/docs/doi-persistence) | DataCite DOIs cannot be deleted; tombstone-page pattern when an object is withdrawn | ADMIN-STALE-CH audit F5-2 | The external-PID immunity gate is missing from §89 §7. Tombstone pattern is the right shape for harvest-exposed channels | skimmed |
| 2026-05-23 | [BPRHub — AS9100D Record Retention](https://www.bprhub.com/blogs/as9100d-record-retention) + [Elsmar AS9100 retention threads](https://elsmar.com/elsmarqualityforum/threads/as9100-records-retention-time-requirements.59481/) | AS9100/EN 9100 retention: 7-40 years, customer-driven, EN 9130:2020 guidance | ADMIN-STALE-CH audit F4-3 | 30-day soft-delete window is incompatible with typical aerospace 10-15 year contracts; per-container retention override needed | skimmed |
| 2026-05-23 | [TimescaleDB GitHub #6196 — compressed-chunk delete 20× size increase](https://github.com/timescale/timescaledb/issues/6196) + [Tiger Data — 42× Faster DELETEs](https://www.tigerdata.com/blog/42x-faster-deletes-accelerating-analytics-and-high-volume-ingestion-with-timescaledb-2-21) | 25 GB hypertable → 330 GB during delete-on-compressed-chunks; TimescaleDB 2.21+ batch-deletion mitigation | ADMIN-STALE-CH audit F9-4 | Free-space pre-flight check before each delete batch; "disk filled while freeing space" is adoption-killer | skimmed |
| 2026-05-23 | [Cultured Systems — Avoiding the soft delete anti-pattern](https://www.cultured.systems/2024/04/24/Soft-delete/) + [HN discussion](https://news.ycombinator.com/item?id=40326815) | Soft-delete anti-pattern: query complexity, FK semantics, unique-constraint violations; Shawn Mendes double-booking case | ADMIN-STALE-CH audit F9-2 | The 5-tuple collision case on recovery is silent data-loss vector parallel to HARD-REQ-1; partial unique index `WHERE deleted_at IS NULL` is the fix | skimmed |
| 2026-05-23 | [Zalando — restful-api-guidelines issue #127 (batch/bulk + 207)](https://github.com/zalando/restful-api-guidelines/issues/127) + [SPS Commerce — Bulk Operations standard](https://spscommerce.github.io/sps-api-standards/standards/bulk.html) | 207 Multi-Status vs. 200+receipt for partial-success bulk operations | ADMIN-STALE-CH audit F3-2 | Doc's 200+receipt choice is right (Stripe/AWS pattern) but needs argued rationale + falsifiability bar | skimmed |
| 2026-05-23 | [UX Movement — How to Make Sure Users Don't Accidentally Delete](https://uxmovement.com/buttons/how-to-make-sure-users-dont-accidentally-delete/) + [UX Psychology — destructive action modals](https://uxpsychology.substack.com/p/how-to-design-better-destructive) | Type-to-confirm pattern halves accidental-click rate on destructive ops | ADMIN-STALE-CH audit F9-1 | v-stepper alone is insufficient for ≥10-row deletes; typed channel-count confirmation needed | skimmed |
| 2026-05-23 | [Stripe — Idempotent Requests pattern](https://stripe.com/docs/api/idempotent_requests) | Idempotency-Key header + 24h receipt cache for safe-retry on bulk operations | ADMIN-STALE-CH audit F3-3 | Bulk-delete is currently non-idempotent — a retry after network blip can delete recovered channels | unread |

### IMPORTER-CARRY — patterns from v15.x → shepard-plugin-importer (2026-05-23)

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [Singer.io — open standard for moving data](https://www.singer.io/spec/) | tap/target/state.json protocol; foundational ELT decoupling thesis | IMPORTER-CARRY §1 + §2 Pattern 3 | Confirms v15's checkpoint shape is industry-standard; we deliberately reject Singer's tap/target decoupling because Shepard sources must know about `:DataObject` | skimmed |
| 2026-05-23 | [dlt — data-load-tool docs](https://dlthub.com/docs/) | Python ELT framework with declarative pipelines + incremental state | IMPORTER-CARRY §1 + §6.2 | Critique of imperative ETL informs the `AbstractImportSource` base-class scaffolding decision; SPI-enforced patterns prevent each source from hand-coding retry/state ad-hoc | skimmed |
| 2026-05-23 | [Airbyte protocol — connector-spec model](https://docs.airbyte.com/understanding-airbyte/airbyte-protocol) | spec/check/discover/read contract per source; JSON-schema config | IMPORTER-CARRY §3.1 (`ImportSource.connectorSpec()`) | Directly adopted as the source-adapter registry shape; informs frontend form rendering for per-source config | skimmed |
| 2026-05-23 | [Apache NiFi — User Guide](https://nifi.apache.org/docs/nifi-docs/html/user-guide.html) | Processor-graph dataflow engine | IMPORTER-CARRY §1 (REJECT) | Rejected as substrate (separate runtime cost); pattern source for retry+backpressure semantics | skimmed |
| 2026-05-23 | [Prefect 3.x — flow concepts](https://docs.prefect.io/v3/develop/write-flows) | Python-native flow engine with state machine | IMPORTER-CARRY §1 (REJECT) + Pattern 10 | Confirms max-retries cap belongs on the JobService; rejected as substrate (wrong language) | unread |

### MFFD-IMPORT-PERF diagnostic (2026-05-23)

| Date | Source | Topic | Surface | Why interesting | Status |
|---|---|---|---|---|---|
| 2026-05-23 | [Python concurrent.futures — ThreadPoolExecutor caveats](https://docs.python.org/3/library/concurrent.futures.html#threadpoolexecutor) | Pool's executor only runs callables submitted via `.submit()`; constructing the pool around an inline sequential call leaves workers idle | MFFD-IMPORT-PERF1 | Direct confirmation that v15's pool wrapping a sequential call is a no-op fan-out — the executor never receives real work, only the `lambda: True` probes | skimmed |
| 2026-05-23 | [Singer.io spec — state.json incremental replication](https://github.com/singer-io/getting-started/blob/master/docs/SPEC.md#state-messages) | "State should be persisted only AFTER records are committed" pattern; corollary: state-check should happen BEFORE the expensive fetch | MFFD-IMPORT-PERF2 | The eager-enrichment-before-skip-check problem is what Singer's state-before-fetch pattern explicitly avoids; lazy-fetch is the canonical ELT shape | skimmed |
| 2026-05-23 | [aiohttp — bounded-concurrency patterns](https://docs.aiohttp.org/en/stable/client_advanced.html#connector) + [asyncio.Semaphore](https://docs.python.org/3/library/asyncio-sync.html#asyncio.Semaphore) | `Semaphore(N)` + `asyncio.gather()` is the canonical N-bounded WAN-fan-out shape | MFFD-IMPORT-PERF1 alt-shape | If the refactor goes async instead of ThreadPoolExecutor, semaphores are simpler than executor + queue; reference for the actual fan-out implementation | unread |

## Decommissioned

*(Nothing yet — entries land here when re-read and judged not-relevant.)*
