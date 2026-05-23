---
title: AAS + EDC reuse survey for shepard-plugin-aas + shepard-plugin-edc
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributors, plugin authors, design reviewers
---

# AAS + EDC reuse survey for `shepard-plugin-aas` + `shepard-plugin-edc`

**Survey date.** 2026-05-23.
**Author.** Reuse-before-reimplement agent dispatched per `feedback_reuse_before_reimplement.md`.
**Scope.** Reuse audit for the AAS (Asset Administration Shell) plugin and a new EDC (Eclipse Dataspace Components) plugin. Output is the §1 reuse survey that `aidocs/integrations/52-aas-backend-integration.md` was missing, retroactively, for the still-pending slices (AAS1c deep SubmodelElement walk, AAS1e writes, AAS1f AASX export, AAS1g RDF projection, AAS1-edc dataspace publication) and for the new `shepard-plugin-edc` scope.

**Frame.** `aidocs/52` already shipped substantial code:

- AAS1a (Shell list), AAS1b (single Shell + Submodel refs), AAS1d (IDTA template bundling), AAS1-reg (outbox-based registry sync), AAS1-well-known (self-description), AAS1-plugin (extraction to `plugins/aas/`).
- All landed without a §1 reuse survey. The wire shape is **hand-rolled** in `AasShellIO.java`, `AasReferenceIO.java`, `AasShellDescriptorIO.java`, etc. — Lombok records with `@Schema` annotations, **no `eclipse-aas4j`, no BaSyx libraries on the classpath** (verified via `grep` of all `pom.xml` files: zero matches).
- The plugin works for the slices that shipped, but the deferred slices (AAS1c walking nested SubmodelElements; AAS1e write-side validation against IDTA Part 1 metamodel; AAS1f AASX zip export; AAS1g RDF/Turtle projection) are exactly where hand-rolled wire shapes turn into multi-quarter projects.

This survey says: **stop hand-rolling. Adopt `eclipse-aas4j` as the model layer now, before AAS1c.** For EDC, sidecar-adopt `tractusx-edc` and Fraunhofer's `EDC-Extension-for-AAS`; do not build a Java-native EDC implementation.

**Web-evidence discipline.** Activity heuristics are quoted with the exact URL the agent fetched on 2026-05-23. Where two fetches disagreed on a date (FA³ST v1.3.0 release date came back as both "October 28, 2024" and "October 28, 2025"), the entry is flagged `[date verified against URL; ambiguity noted]` and the more conservative interpretation chosen. The MDPI paper `10.3390/app152111623` ("Integration Approaches for Digital Twins in Dataspaces") was retrieved as binary PDF that defeated text extraction; claims that depend on it are flagged `[per abstract + search snippets; full paper unread]`.

---

## §1 — Candidate inventory

Fifteen candidates. The AAS side is well-populated; the EDC side narrower because the Eclipse EDC ecosystem consolidates around two productisable downstreams (Tractus-X, Sovity).

| # | Name + URL | License | Activity heuristic (verified 2026-05-23) | Language / stack | One-sentence what-it-does | Slice coverage estimate |
|---|---|---|---|---|---|---|
| 1 | **eclipse-aas4j** — https://github.com/eclipse-aas4j/aas4j | Apache-2.0 | Latest release **2.0.2** dated 2024-05-13 (Eclipse Foundation project; releases page shows ~5 over 2.5 yrs). Cadence slow but predictable. | Java 17+; Maven coords `org.eclipse.digitaltwin.aas4j:aas4j-model` etc. | Java model classes + JSON/XML/AASX (de)serializers for AAS metamodel v3.1 per IDTA spec. | **~40% of `shepard-plugin-aas`** — provides the IO layer Shepard hand-rolled. Does NOT provide REST or storage. |
| 2 | **Eclipse BaSyx Java Server SDK** — https://github.com/eclipse-basyx/basyx-java-server-sdk | MIT | Latest release **2.0.0-milestone-10** dated 2025-04-23 (still milestone-tagged, **not GA**). 836 commits on main. | Java 21 + Spring Boot; Docker images on Docker Hub. | Full-stack AAS Type 2 server (AAS Repository, Submodel Repository, Concept Description Repo, AAS Registry, Submodel Registry, Discovery, AASX File Server). | **~80% of a sidecar-adopted shepard-plugin-aas** if Shepard delegates persistence; **but** Spring Boot collides with Shepard's Quarkus — sidecar only, not library embed. |
| 3 | **FraunhoferIOSB/FAAAST-Service** — https://github.com/FraunhoferIOSB/FAAAST-Service | Apache-2.0 | Latest release **v1.3.0** dated 2024-10-28 [date verified against URL; one fetch suggested 2025-10-28, releases-page authoritative]. ~5 releases since 2024-03; 2,257 commits on main. | Java native (CLI + Docker + embedded library) | Reactive AAS Type 2 service — load AAS models, expose via HTTP REST + OPC UA. Asset-sync mechanisms. | **~75% of a sidecar-adopted shepard-plugin-aas.** Lighter than BaSyx; supports v3.0.1 API; embeddable as library (but pulls a lot of transitive deps). |
| 4 | **Eclipse AASX Package Explorer** — https://github.com/eclipse-aaspe/package-explorer | Apache-2.0 (LICENSE.md present) | Latest release **v2025-10-21.alpha** (alpha tag, active dev). Migrated from `admin-shell-io` to Eclipse incubation. | C# / .NET (Windows desktop primarily) | Desktop GUI for viewing/editing AAS models; embedded REST + OPC UA servers. | **~5% of any Shepard plugin** — wrong stack, but the canonical AAS GUI an operator uses to **inspect** Shepard's output. Useful as a verification client, not a code dep. |
| 5 | **admin-shell-io/aas-test-engines** — https://github.com/admin-shell-io/aas-test-engines | Apache-2.0 | 237 commits on main; `pip install aas_test_engines`; covers Metamodel v3.0.1, API v3.0.3, 7 API profiles incl. AAS-Repository-Read & Submodel-Registry-Read. | Python 94.6% | Conformance test suite (file + API). `aas_test_engines check_server URL SSP-002` runs ~140 tests per profile. | **0% as a library** but **100% of the CI conformance harness** AAS1h needs. Adopt as a CI runner (testcontainer). |
| 6 | **admin-shell-io/submodel-templates** — https://github.com/admin-shell-io/submodel-templates | CC-BY-4.0 (data, not code) | 795 commits on main; 63 published templates; 109 open issues; actively maintained by IDTA office. | XML / JSON template artefacts (data only) | Canonical bundle of IDTA Submodel Templates (Nameplate, Technical Data, Time Series Data, Carbon Footprint, Handover Documentation, AID, BoM, etc.) | **100% of AAS1d** template provisioning — Shepard already bundles 3 of the 63 (Nameplate, Technical Data, Time Series Data); this is the upstream canonical source. |
| 7 | **admin-shell-io/aas-specs-api** — https://github.com/admin-shell-io/aas-specs-api | CC-BY-4.0 | Version **3.1.2** dated 2026-04-23; mirrored on SwaggerHub `Plattform_i40`. | OpenAPI JSON/YAML | Normative OpenAPI specs for IDTA-01002 (the AAS API). | **0% as code** but the **wire-contract source-of-truth** every other candidate (and Shepard) is implementing. |
| 8 | **eclipse-tractusx/tractusx-edc** — https://github.com/eclipse-tractusx/tractusx-edc | Apache-2.0 (code) + CC-BY-4.0 (docs) | Latest release **0.12.1** dated 2025-05-21; **0.13.0-rc2** dated 2025-05-22; 1-2 month cadence; **still 0.x, no 1.0**. Production-Docker images on GHCR; Helm charts. | Java + Postgres + HashiCorp Vault; Docker + Helm. | Catena-X-certified EDC distribution: control-plane + data-plane Docker images, Helm charts, vault-backed secrets, Tractus-X identity integration. | **~85% of a sidecar-adopted `shepard-plugin-edc`** when shipped as sidecar; **0%** as a library embed (wrong stack and wrong shape). |
| 9 | **eclipse-edc/Connector** — https://github.com/eclipse-edc/Connector | Apache-2.0 | Latest release **v0.17.0** dated 2026-04-20; 43 releases; 3,113 commits on main; very active. | Java + extensions architecture | Upstream Eclipse EDC core — Dataspace Protocol implementation (DSP), control plane + data plane + extension points. | **~70%** as the upstream a sidecar provides; usually consumed via Tractus-X or Sovity downstream rather than directly. |
| 10 | **sovity/edc-ce** — https://github.com/sovity/edc-ce | Apache-2.0 (CE; commercial features separate) | Latest release **v16.6.0** dated 2026-05-07; 81 releases; 1,402 commits on main. Catena-X-certified. | Java 21 (Kotlin in places) + TypeScript UI; Node 20. Docker. | Sovity Community Edition EDC — Eclipse EDC + management UI + DAPS preconfigured + Catena-X compatibility. | **~85% of a sidecar-adopted `shepard-plugin-edc`** when the operator wants a UI (admin console). |
| 11 | **eclipse-tractusx/sldt-digital-twin-registry** — https://github.com/eclipse-tractusx/sldt-digital-twin-registry | Apache-2.0 + CC-BY-4.0 | Latest release **0.11.0** dated 2026-03-03; 112 releases; very active. | Java 97.9%; Maven; Docker; PostgreSQL. | Catena-X Digital Twin Registry — AAS Registry implementation with `libraries/edc-extension` for EDC integration. | **~60% of AAS1-fed (parent federation)** when Shepard wants a Catena-X-shaped DTR sidecar. |
| 12 | **FraunhoferIOSB/EDC-Extension-for-AAS** — https://github.com/FraunhoferIOSB/EDC-Extension-for-AAS | Apache-2.0 | Latest release **v2.2.0** dated 2024-11-05; supports EDC v0.15.0 + aas4j; 785 commits on main; active but cadence slower than upstream EDC. | Java + Gradle; Docker | Bridges Eclipse EDC with AAS: auto-links EDC Assets to AAS HTTP endpoints, contract generation for AAS elements, FA³ST message-bus listening, AAS Registry sync. | **~90% of the EDC↔AAS bridge `shepard-plugin-edc` would otherwise build** — the single biggest "don't reinvent" find of this survey. |
| 13 | **eclipse-edc/IdentityHub** — https://github.com/eclipse-edc/IdentityHub | Apache-2.0 | Latest release **v0.17.0** dated 2026-04-20 (in lockstep with EDC core). 99.9% Java; Docker. **Versions ≤0.3.1 have a documented security warning — do not use.** | Java; uses EDC SPI | Decentralized Claims Protocol (DCP) implementation — VerifiableCredentials wallet for dataspace participant identity. | **~50% of dataspace identity for shepard-plugin-edc** when DID/DCP is the operator's identity choice; alternative is the older DAPS path. |
| 14 | **International-Data-Spaces-Association/ids-specification** (Dataspace Protocol) — https://github.com/International-Data-Spaces-Association/ids-specification | CC-BY-4.0 | DSP version **2024-1** is the release candidate considered stable; targets ISO PAS transposition during 2025; Technology Compatibility Kit (TCK) ships ~140 automated tests. | Spec only | The Dataspace Protocol — successor to IDS-G (which was archived 2025-06-13); the wire contract every EDC implementation conforms to. | **0% as code** but the **canonical wire contract** an EDC sidecar must speak. Required reading. |
| 15 | **NOVAAS** — https://github.com/HSDarmstadt/NOVAAS (referenced in PMC10255844 survey) | Open source (license check needed) | Node-RED based; rate of development noted as "moderate" in the 2023 reactive-AAS survey [PMC10255844]. | JavaScript / Node-RED | No-code reactive AAS Type 2 server. | **~30%** as a sidecar for no-code demo deployments; not the production answer. Mentioned for completeness; not recommended. |

**Honourable mentions / not separately tabled but considered:** `BiancoRoyal/aasx-server-io` (C# server, niche), `eclipse-aaspe/aasx-server` (incubation), `BaSyx Python` SDK (consumer side, not server), Sovity DT Registry (Catena-X-certified DTR; commercial fork of Tractus-X registry). FIWARE-TrueConnector and the Mobility Data Space (MDS) connectors were checked but provide nothing Tractus-X EDC doesn't already provide for an aerospace adopter.

### Activity-precision callouts

- **BaSyx is `2.0.0-milestone-10` — not GA.** Adoption today carries milestone-API churn risk. The Eclipse Foundation has not signed off on 2.0.0 final. Flagged in §6.
- **Tractus-X EDC is `0.12.1` — not 1.0.** The downstream is production-tested in Catena-X deployments, but the version number is honest about API stability ceiling.
- **`EDC-Extension-for-AAS` v2.2.0 is dated 2024-11-05** — 18 months old at survey time. The extension supports EDC `0.15.0`, and EDC core is now `0.17.0`, so the extension may be behind. Verify compatibility before adopting; if the extension is stale, the maintainers (Fraunhofer IOSB) likely accept PRs (same org runs FA³ST).
- **eclipse-aas4j 2.0.2 is dated 2024-05-13** — 12 months at survey time. Slower than EDC but matches the Eclipse Foundation's "stable model, infrequent breaking changes" cadence.

---

## §2 — Element-by-element reuse map

For each capability Shepard's AAS+EDC plugins need, the candidate that covers it + glue work + risk. Sorted by the order Shepard's roadmap (`aidocs/52 §9`) would hit them.

| Element | Candidate(s) | Glue work for Shepard | Risk |
|---|---|---|---|
| **AAS metamodel — Java POJOs for Shell / Submodel / SubmodelElement / Property / File / Blob / Reference / SubmodelElementCollection** | `eclipse-aas4j:aas4j-model` | Replace hand-rolled `AasShellIO.java`, `AasReferenceIO.java`, future `SubmodelIO`, `SubmodelElementIO` with aas4j types; map Shepard's `Collection`/`DataObject`/`FileReference` to aas4j types in `AasShellMappingService`. **Most of AAS1c shrinks from ~3 eng-weeks to ~1.** | LOW. aas4j is Apache-2.0, Eclipse-stewarded, 12-month-stable. Worst case is a major-version bump (1.0→2.0 already done). |
| **AAS JSON serialization (request/response wire format)** | `aas4j-dataformat-json` | Replace Quarkus Jackson + hand-rolled `@Schema` with aas4j's `JsonSerializer`/`JsonDeserializer`. Validates against IDTA-01002 wire shape automatically. | LOW. Quarkus's Jackson stays in place for non-AAS endpoints; aas4j json serializer wraps Jackson. |
| **AAS XML serialization** | `aas4j-dataformat-xml` | Adds XML content-negotiation to `/v2/aas/...` endpoints for AAS clients that prefer XML (rare but spec-compliant). | LOW. Add as feature, no migration cost. |
| **AASX package format (zip envelope, AAS1f)** | `aas4j-dataformat-aasx` | Replace AAS1f's planned hand-rolled streaming-zip implementation with aas4j's `AASXSerializer`. Saves ~1 eng-week. | LOW. AASX zip format is non-trivial (OPC-aware); reusing the lib eliminates a class of file-format bugs. |
| **AAS RDF serialization (AAS1g)** | aas4j does **not** ship an RDF serializer per the 2.0.2 module list. | Shepard owns this glue; reuse n10s + Jena/RDF4J for the projection. `aidocs/52 §6.3` already calls this out. | MEDIUM. RDF emission is the spec's least-implemented dialect; conformance is harder. |
| **IDTA template bundle (AAS1d already shipped)** | `admin-shell-io/submodel-templates` | Shepard already bundles 3 templates (Nameplate, Technical Data, Time Series Data). Periodic sync against upstream (60+ templates) — automate via a `make sync-idta-templates` target. | LOW. CC-BY-4.0 data; just stay current. |
| **AAS Type 2 (reactive HTTP REST) server-side semantics** | hand-rolled today in Shepard's `AasShellsRest`, `AasAdminRest`, `AasWellKnownRest` — works fine for AAS1a+AAS1b. For AAS1c (deep `idShortPath` traversal, content-negotiation modifiers `level=Deep`, `extent=WithBlobValue`), the work explodes. | **Recommend:** stay hand-rolled BUT use aas4j types as the IO layer. Adopt sidecar BaSyx or FA³ST only if AAS1c reveals scope >2 eng-weeks. | MEDIUM. AAS Repository semantics have ~31 SSPs (§AAS-specs research); a hand-rolled subset is fine, full conformance is not. |
| **AAS Type 1 (passive AASX file)** | aas4j-dataformat-aasx (read + write) | Not a server mode — Shepard cannot **be** a Type 1 deployment. Shepard **emits** AASX as a file via AAS1f. | N/A. Type 1 is a file format, not a runtime. |
| **AAS Type 3 (active / proactive / agent)** | None (spec immature as of 2026) | Out of scope per `aidocs/52 §6.4`. | N/A. Don't plan. |
| **AAS Registry — outbound registration (AAS1-reg, shipped)** | aas4j has descriptor types; sidecar candidates: BaSyx Registry, FA³ST Registry, Tractus-X DTR. | Shepard's `AasRegistryClient` + `AasRegistryOutboxService` already work hand-rolled. Replace `AasShellDescriptorIO` with aas4j `ShellDescriptor` type when the model migration happens. | LOW. The outbox plumbing is correct; only the wire shape benefits. |
| **AAS Registry — inbound (Shepard *is* a registry, AAS1i)** | sidecar Tractus-X DTR | Parked per `aidocs/52`; if it wakes up, deploy `sldt-digital-twin-registry` as a sidecar rather than build it in-Quarkus. | LOW. Sidecar pattern matches `feedback_plugins_declare_sidecars.md`. |
| **AAS Discovery (asset-link lookup)** | `aas-specs-api` SSP-001/002 in scope of BaSyx Discovery Service. | Parked; Shepard's `.well-known/aas-server` (§4a.5 in `aidocs/52`) is a Shepard-flavoured alternative. | LOW. |
| **AAS conformance testing (AAS1h)** | `admin-shell-io/aas-test-engines` | Add CI testcontainer job: boot Shepard with seed loaded, run `aas_test_engines check_server URL SSP-002` against `/v2/aas/`. Save report as CI artefact. | LOW. Adds ~10 min to CI; standard pattern. |
| **EDC core (DSP, control plane, data plane)** | sidecar `tractusx-edc` OR sidecar `sovity/edc-ce` | `shepard-plugin-edc` declares sidecar in manifest (per `feedback_plugins_declare_sidecars.md`); Shepard publishes Asset descriptors to the connector's management API. | MEDIUM. EDC operator burden is real; an aerospace research org without a dataspace footprint may be over-served. Park until first dataspace deployment asks. |
| **EDC contract negotiation (ODRL policies)** | EDC core (Tractus-X or Sovity) | Shepard does not implement ODRL — it produces Asset descriptors with a Shepard-flavoured policy schema and lets the EDC engine evaluate. | MEDIUM. ODRL policy modelling is non-trivial; pick a small subset (e.g. allow-list of dataspace participants) for v1. |
| **EDC data plane (HTTP / S3)** | Tractus-X EDC ships HTTP and S3 data planes; Sovity EDC parity. | Shepard's Garage S3 backend (`project_storage_s3_garage.md`, ADR-0024) maps naturally to the EDC S3 data plane: Shepard mints a presigned URL, EDC vends it to the consumer with usage policy attached. **This is the cleanest EDC integration.** | LOW. Garage already speaks S3 API. |
| **EDC + AAS bridge** (publish a Shepard Submodel as an EDC contract offer) | `FraunhoferIOSB/EDC-Extension-for-AAS` v2.2.0 | Sidecar adopt. `shepard-plugin-edc` declares Fraunhofer's extension as a sub-sidecar of the EDC sidecar; Shepard's AAS endpoints become EDC Assets via the extension's auto-sync. | **MEDIUM-HIGH** — the extension is 18 months behind EDC core. Verify version compat first; consider contributing back a bump PR if Fraunhofer hasn't shipped 2.3+ yet. |
| **Dataspace participant identity (IATP / DID / DCP)** | `eclipse-edc/IdentityHub` (DCP) OR DAPS (legacy IDS-style) OR Shepard's own JWT/OIDC | If Shepard already issues JWTs (it does — `aidocs/24`), the cleanest path is a **bridge identity claim** issued by Shepard's Keycloak → presented to the EDC IdentityHub as a Verifiable Credential. | HIGH. DCP is the spec, but adoption is uneven. Aerospace-X and Catena-X both still use DAPS-flavoured identity in practice. Track but don't gate. |
| **Catena-X / Manufacturing-X / Factory-X profile fit** | Tractus-X EDC (Catena-X profile native); Aerospace-X uses the same stack (per Fraunhofer ISST project page). | Shepard's adoption story aligns naturally with Aerospace-X (BMWE 13MX004F, April 2024 – June 2026; partners include DLR, Airbus, MTU Aero Engines, Rolls-Royce, SAP, Capgemini, T-Systems). | LOW (alignment) / MEDIUM (operator complexity). |
| **Provenance integration (PROV-O Activity emission for AAS/EDC operations)** | Shepard's existing `ProvenanceCaptureFilter` (PROV1a). | Every `/v2/aas/...` and `/v2/edc/...` write fires a `:Activity` node automatically. m4i predicates already vendored (`project_m4i_integration_design`). | LOW. Already covered by core; no new work. |
| **Shepard's Garage S3 substrate as EDC data plane backing** | Garage (ADR-0024) + Tractus-X / Sovity EDC HTTP/S3 data plane | The EDC data plane already speaks S3; Garage already speaks S3. Shepard's role: mint presigned URLs, write them into EDC Asset `dataAddress`. | LOW. Substrate alignment is correct. |
| **Shepard's Neo4j + SHACL substrate as AAS submodel typing source** | n10s + Jena/RDF4J (already in roadmap per `aidocs/48`); aas4j model classes | When `aidocs/48` (n10s ontology repo) and `aidocs/47` (PayloadKind SPI) land, AAS `semanticId` references become first-class queryable IRIs. T1 templates' `AttributeSpec.type` carries `valueType` per `aidocs/52 §6.1`. | LOW. The pieces line up; just sequencing. |
| **First live use case — MFFD CFRP digital thread → AAS publication → Catena-X / Aerospace-X exposure** | Tractus-X EDC + EDC-Extension-for-AAS + Shepard `/v2/aas/` | See §5 demonstrator pairing. | MEDIUM. Real-data demonstrator depends on MFFD data import (active task) and an Aerospace-X dataspace test endpoint (Fraunhofer ISST contact required). |

---

## §3 — Recommended adoption

Two recommendations, AAS and EDC separately, each with reasoning.

### §3.1 — For `shepard-plugin-aas`: **library embed `eclipse-aas4j` immediately; sidecar BaSyx OR FA³ST as a fall-back only**

**Adopt:** `org.eclipse.digitaltwin.aas4j:aas4j-model` + `aas4j-dataformat-json` + `aas4j-dataformat-aasx` (Apache-2.0; verified 2026-05-23 at https://github.com/eclipse-aas4j/aas4j).

**Reasoning (paragraph, the rationale the rule requires):**

The current plugin hand-rolls `AasShellIO`, `AssetInformationIO`, `LangStringIO`, `AasReferenceIO`, and similar records with Lombok + `@Schema` annotations. Verified by `grep`: zero `aas4j`/`BaSyx`/`FAAAST` references in any `pom.xml` across the repo. The hand-rolled approach works for AAS1a+AAS1b (Shell list + single-Shell GET) because the wire payload is minimal. **It does not scale to AAS1c** (the `idShortPath`-walk endpoint exposes every `Property`, `File`, `Blob`, `SubmodelElementCollection`, `MultiLanguageProperty`, `ReferenceElement`, `Entity` subtype as a distinct typed payload). IDTA-01001-3-1 defines 17+ SubmodelElement subtypes; each has its own JSON wire shape; aas4j ships them all as battle-tested POJOs with the `JsonSerializer`/`JsonDeserializer` already conformant to IDTA-01002-3-1. Replacing hand-rolled IO records with aas4j types **before** AAS1c starts saves an estimated 2 eng-weeks of reimplementation and eliminates a class of wire-shape conformance bugs (the `aas-test-engines` SSP-002 profile would catch these, but only after they ship). The migration is **incremental**: add aas4j as a `provided` dep in `plugins/aas/pom.xml`, replace one IO record per PR, run the existing 47 unit tests + add new round-trip tests. Effort: S (one focused sprint).

**Failure mode this avoids:** hand-rolling 17+ SubmodelElement subtypes, then discovering during AAS1h conformance testing that 3 of them are subtly wrong, then bug-fixing while AAS clients in the wild already cached our wrong wire shape.

**Fall-back (sidecar) — pick when:** AAS1c implementation reveals that a hand-rolled adapter cannot reach `Profile_AasxFileServer_Read` + `Submodel_Repository_Read_SSP-002` conformance in ~6 eng-weeks. Then:

- **First-choice sidecar: FA³ST.** Lighter than BaSyx; native Java; embeddable; supports AAS API v3.0.1 fully; Fraunhofer IOSB maintainer is also the maintainer of EDC-Extension-for-AAS (good ecosystem alignment for the EDC piece). Apache-2.0 (https://github.com/FraunhoferIOSB/FAAAST-Service).
- **Second-choice sidecar: BaSyx Java Server SDK.** Production-friendlier (Spring Boot, MongoDB persistence, Docker Hub images, MQTT integration); but **still milestone-tagged** (v2.0.0-milestone-10, 2025-04-23). MIT-licensed. Pick this if the operator wants per-resource ACLs and the Spring Boot stack is acceptable as a sidecar (it never enters Shepard's Quarkus process).
- **Sidecar pattern per `feedback_plugins_declare_sidecars.md`:** `plugins/aas/` `AasPluginManifest` declares the sidecar; deploy assembles compose; Shepard's `AasShellMappingService` translates Shepard CRUD to sidecar HTTP calls. The hand-rolled REST surface becomes a thin proxy, not a re-implementation.

### §3.2 — For `shepard-plugin-edc`: **sidecar Tractus-X EDC + Fraunhofer's EDC-Extension-for-AAS; design-only until first dataspace deployment requests it**

**Adopt (when triggered):** sidecar `eclipse-tractusx/tractusx-edc` (Apache-2.0; verified 2026-05-23). Supplement with sidecar-or-libs `FraunhoferIOSB/EDC-Extension-for-AAS` for the EDC↔AAS bridge.

**Reasoning (paragraph):**

`aidocs/52 §4a.3` already parks AAS1-edc as "external precondition (dataspace operator owns it)." This survey reinforces that posture: an EDC deployment without a dataspace participant on the other side is theatre. **However**, when the trigger fires (the most likely candidate is Aerospace-X — BMWE 13MX004F, ending June 2026, DLR is a partner), Shepard should not build EDC support in-house. Tractus-X EDC is the production-tested Catena-X-certified distribution that the Aerospace-X stack uses (per Fraunhofer ISST project page and the Neubauer et al. 2023 paper "Architecture for manufacturing-X: Bringing asset administration shell, eclipse dataspace connector and OPC UA together", Manufacturing Letters, 58 citations). Sovity EDC CE is the alternative if the operator wants a management UI; Sovity is Catena-X-certified and licensed identically. Either way, the EDC is a **sidecar** declared in `shepard-plugin-edc`'s manifest, not a library embed (the EDC runtime is large, Spring-shaped, and assumes operator control over its lifecycle). The single biggest reuse opportunity for the bridge layer is `FraunhoferIOSB/EDC-Extension-for-AAS` v2.2.0 — it already does what `shepard-plugin-edc`'s headline feature would be: auto-link EDC Assets to AAS HTTP endpoints, generate contracts, sync with the AAS Registry. It's 18 months stale (supports EDC 0.15.0; current EDC 0.17.0); the right play is to verify compatibility and contribute a bump PR upstream rather than build a parallel bridge.

**Failure mode this avoids:** a Quarkus-native reimplementation of the Dataspace Protocol that gets nowhere near the 140-test DSP TCK conformance, and a hand-rolled AAS↔EDC bridge that diverges from the Aerospace-X-aligned ecosystem.

**Park-until trigger:** the first concrete dataspace deployment request (likely Aerospace-X-attached). Until then, the design hook in `aidocs/52 §4a.3` is enough.

### §3.3 — Reject

- **NOVAAS (#15).** No-code Node-RED implementation; useful for demo videos, not for the Shepard plugin. Wrong stack, wrong scale.
- **Reference-impl fork.** No candidate is "90% there but unmaintained" — every primary candidate is active. Don't fork; adopt + contribute.
- **`eclipse-edc/Connector` directly.** The upstream Eclipse EDC is correct as a research substrate but Tractus-X / Sovity downstream is what production aerospace deployments actually use; consuming upstream directly invents the productisation problem.
- **`AASX Package Explorer` as a code dep.** It's the GUI an operator uses to inspect Shepard's output; not a code dependency.

---

## §4 — Glue work scope

What `shepard-plugin-aas` + `shepard-plugin-edc` still ship after adopting the candidates above.

| # | Glue task | Plugin | Effort | Notes |
|---|---|---|---|---|
| G1 | Replace hand-rolled `AasShellIO` / `AasReferenceIO` / `AssetInformationIO` / `LangStringIO` with `aas4j-model` types via an `AasShellMappingService` adapter | AAS | S | Incremental: one IO record per PR; existing tests stay green by wrapping; new round-trip tests verify wire conformance. |
| G2 | Add `aas4j-dataformat-json` to the `/v2/aas/...` response pipeline (replace bespoke Jackson serialisation) | AAS | S | Quarkus ConfigSource + JSON-B adapter. |
| G3 | Add `aas4j-dataformat-aasx` and implement `GET /v2/aas/shells/{aasId}/aasx` (AAS1f AASX export) | AAS | M | Streams a zip via Shepard's existing R2 mechanism. |
| G4 | Implement AAS1c — `GET /v2/aas/submodels/{submodelId}/submodel-elements/{idShortPath}` walk over nested DataObjects | AAS | M | Now M-sized because aas4j types remove the per-subtype reimplementation cost. Without aas4j: L. |
| G5 | Implement AAS1e — write-side `PUT/POST` against aas4j-validated payloads | AAS | M | Template-validating writes (AAS1d templates as the schema source). |
| G6 | Implement AAS1g — RDF/Turtle projection via n10s + Jena/RDF4J | AAS | M | Depends on `aidocs/48` N1f (SPARQL proxy). |
| G7 | CI conformance harness — testcontainer that runs `aas_test_engines check_server` against a booted Shepard with LUMEN seed | AAS | S | Add to `backend-ci.yml`; report saved as CI artefact. |
| G8 | Frontend AAS Submodel browse — Vuetify pages under `/aas/shells/{aasId}/submodels/{submodelId}` showing the typed `SubmodelElement` tree | AAS | M | Uses Shepard's existing tree-view components; one new composable `useAasShell.ts`. |
| G9 | MCP tool integration — `aas_list_shells`, `aas_get_submodel`, `aas_walk_element` per `aidocs/platform/30` pattern | AAS | S | Mirrors existing MCP tools. |
| G10 | OpenAPI tag strategy — separate `aas-repository` / `aas-submodel-repository` tags per `aidocs/52 §10`, decision (6) | AAS | S | One-line config. |
| G11 | Sidecar declaration in `AasPluginManifest` for the OPTIONAL FA³ST or BaSyx fallback | AAS | S | Only fires if §3.1 fall-back triggers. Per `feedback_plugins_declare_sidecars.md`. |
| G12 | `shepard-plugin-edc` skeleton — `EdcPluginManifest`, three docs files (`reference.md`/`quickstart.md`/`install.md`), no code yet | EDC | S | Design hook only until trigger. |
| G13 | Sidecar declaration for Tractus-X EDC control-plane + data-plane + Postgres + Vault | EDC | M | When triggered. Helm chart reference rather than reinventing compose. |
| G14 | `EdcAssetSyncService` — Shepard side that publishes Asset descriptors to the EDC management API on Collection/DataObject creation | EDC | M | When triggered. Outbox pattern parallels AAS1-reg. |
| G15 | Sub-sidecar declaration for `EDC-Extension-for-AAS` | EDC | S | Wraps G13; auto-syncs AAS endpoints into EDC Assets. |
| G16 | Frontend EDC contract dashboard — Vuetify pages for managing offered Assets, active contracts, transfer processes | EDC | L | When triggered. ODRL policy editor is the hard sub-task. |
| G17 | MCP tool integration for EDC — `edc_list_assets`, `edc_negotiate_contract` | EDC | M | When triggered. |
| G18 | Identity bridge — Shepard's Keycloak → EDC IdentityHub VerifiableCredential mint | EDC | L | When triggered. Highest risk task. |
| G19 | Operator install guide — `plugins/edc/docs/install.md` walking through Tractus-X EDC config keys, Vault setup, DSP endpoint registration | EDC | M | When triggered. |
| G20 | Aerospace-X conformance test — boot Shepard + EDC sidecar + Fraunhofer ext + a Tractus-X DTR; round-trip an MFFD DataObject as an AAS Submodel offered via EDC contract | both | L | The acceptance demonstrator; depends on every other task. |

**Effort totals** — AAS glue (G1-G11): roughly 9 eng-weeks (4×S + 5×M + 1×S sidecar). EDC glue (G12-G19): roughly 14 eng-weeks (3×S + 4×M + 2×L). Demonstrator (G20): 4 eng-weeks if scoped tightly.

---

## §5 — Demonstrator pairing

The MFFD + LUMEN demonstrators are Shepard's two live testbeds. For each: the first compelling AAS submodel + the first compelling EDC contract offer, concrete with IRIs and Collection IDs.

### §5.1 — MFFD AFP tape-laying (first AAS Submodel + first EDC offer)

**First compelling AAS Submodel: IDTA Time Series Data submodel for an AFP tape-laying run.**

- Source Shepard entity: `DataObject` representing one AFP tape-laying run (e.g. `urn:shepard:dataobject:01977d2e-92a1-7234-9f6d-83a4cdf5b001`), backed by a `TimeseriesContainer` with thermal-trail channels (TCP temperature, force-torque, robot joint positions).
- AAS Shell IRI: `urn:shepard:collection:01977d2e-tapelaying-collection` (mapped from the MFFD tape-laying Collection ID 48297).
- AAS Submodel IRI: `urn:shepard:dataobject:01977d2e-92a1-7234-9f6d-83a4cdf5b001`.
- `semanticId`: `https://admin-shell.io/idta/submodels/time-series-data/1/1` (the IDTA Time Series Data v1.1.1 template Shepard already bundles via AAS1d).
- Wire conformance: `aas_test_engines check_server http://shepard.nuclide.systems/v2/aas https://admin-shell.io/aas/API/3/0/SubmodelServiceSpecification/SSP-002` (Read profile).

**First compelling EDC contract offer: this same Submodel as an Aerospace-X-compatible offer.**

- EDC Asset descriptor: maps to `dataAddress.type = "https-aas"`, `dataAddress.baseUrl = "https://shepard.nuclide.systems/v2/aas/submodels/{base64url:urn:shepard:dataobject:01977d2e...}"`.
- Contract offer policy (ODRL): "Aerospace-X verified participants from {DLR, Airbus, MTU, Rolls-Royce} may read; no redistribution; cite as `cite-as: shepard-collection-48297`."
- Glue via `EDC-Extension-for-AAS` auto-sync.

**Why this submodel first:** AFP tape-laying timeseries is the MFFD demonstrator's flagship payload — already shipped in `examples/mffd-showcase/seed.py`; the Time Series Data template is the one Shepard's `aidocs/52 §8` calls out as where Shepard's substrate is **better than** typical AAS vendor implementations.

### §5.2 — LUMEN TR-004 hotfire anomaly (second AAS Submodel + research-data EDC contract)

**First compelling AAS Submodel: IDTA Handover Documentation submodel for TR-004.**

- Source Shepard entity: Collection 42 (LUMEN showcase), DataObject `TR-004` (the anomalous test run).
- AAS Shell IRI: `urn:shepard:collection:42-lumen-showcase`.
- AAS Submodel IRI: `urn:shepard:dataobject:TR-004`.
- `semanticId`: `https://admin-shell.io/idta/submodels/handover-documentation/2/0`.
- Carries: investigation child DataObject reference, anomaly description (12g rms at t=8s), corrective-action chain (TR-005 hold/repair, TR-006 post-fix re-test).

**First compelling EDC offer: research-data restricted-share.**

- EDC Asset descriptor: a sealed Snapshot (per `aidocs/41 V2b`) of the TR-004 → TR-005 → TR-006 sub-tree.
- Contract offer policy: "DLR-internal researchers + RDA-cleared aerospace partners; embargo until LUMEN paper publication date; mandatory citation."
- This demonstrates the **research-data variant** of dataspace exchange — distinct from the Catena-X-style supply-chain variant. Same EDC, different policy.

### §5.3 — Why both demonstrators

The MFFD demonstrator proves the **industrial-data** EDC path (Aerospace-X-aligned). The LUMEN demonstrator proves the **research-data** EDC path (FAIR + embargo + citation). Together they justify `shepard-plugin-edc` as a Shepard concern, not just an industrial concern.

---

## §6 — Risks + counter-evidence

What the literature and community say against AAS adoption, against EDC adoption, and what alternative paradigms exist.

### §6.1 — Risks against AAS adoption

**Risk: AAS implementation landscape is fragmented; no full conformance.** The 2023 survey "Open-Source Implementations of the Reactive Asset Administration Shell" (PMC10255844; ScienceDirect Sensors 23(15): https://pmc.ncbi.nlm.nih.gov/articles/PMC10255844/) explicitly states "there is no AAS implementation that fully implements the AAS specification" — surveyed AASX Server, Eclipse BaSyx, FA³ST, NOVAAS. The authors recommend "waiting for stable v3 specification release before major deployments to ensure interoperability." **As of 2026-05-23, Part 1 v3.1 (Metamodel) and Part 2 v3.1.2 (API) are stable, but BaSyx is still milestone-tagged.** Adopters are on the leading edge.

**Risk: AAS Part 2 API churn.** IDTA-01002 went 3.0 → 3.0.3 → 3.1 → 3.1.1 → 3.1.2 in ~24 months. The `aas-test-engines` defaults to v3.0.3; aas4j 2.0.2 supports v3.1; Shepard's AAS1d templates are v1.1/2.0/3.0 (Time Series / Technical Data / Nameplate). Version-skew matrix exists; pick one canonical (recommend v3.1, follow aas4j).

**Risk: AAS is over-served for research data.** The AAS spec was designed for industrial digital twins, not research datasets. Many Submodel elements (Operations, Entities with statemodels, AssetInformation.specificAssetIds) don't map cleanly to research data. `aidocs/52 §3` already calls this out; the lossy mappings stay lossy.

### §6.2 — Risks against EDC adoption

**Risk: EDC operator complexity.** Tractus-X EDC requires Postgres + Vault + 2 Docker images (control plane + data plane) + DAPS-or-IdentityHub identity service + DSP-compatible peer. The operator burden is substantial. Sovity CE adds a UI but doesn't shrink the runtime. **A small DLR institute without dataspace participation is over-served by EDC.** Shepard's existing per-Collection ACLs + JWT auth already cover the within-institute use case.

**Risk: DSP version churn.** Dataspace Protocol is at "2024-1 release candidate considered stable"; ISO PAS transposition targeted for 2025 but not yet final. EDC core v0.17.0 (April 2026) still 0.x. Tractus-X EDC 0.12.1 / 0.13.0-rc2. Production deployments exist but the API stability ceiling is 0.x-honest.

**Risk: Aerospace-X is BMWE-funded with end date June 2026.** A Shepard EDC bet anchored entirely on Aerospace-X has a clock on it. If the project doesn't transition to a follow-on, Shepard's EDC integration loses its primary live use case. Mitigation: design for **any** Catena-X-aligned dataspace, not Aerospace-X specifically.

### §6.3 — Alternative paradigms (community counter-evidence)

**FAIR Digital Objects (FDO).** FDO Forum + FDO One project (Fraunhofer ISST, https://www.isst.fraunhofer.de/en/departments/mobility-und-smart-cities/projects/FDOOne.html) explicitly investigates how FDOs can be "merged with the existing approaches of IDSA, EDC, and AAS to ensure interoperability and sharing of data across data spaces." FDO is the **research-data-native paradigm** Shepard's `aidocs/44` already invests in (PID-based, citation-shaped). FDO and AAS are converging, not competing — but a Shepard adopter could legitimately ask "why AAS+EDC and not RO-Crate + Helmholtz Unhide?" The answer: AAS+EDC opens the industrial-research intersection that FDO+Unhide doesn't address.

**RO-Crate.** Maintained, lightweight, JSON-LD-shaped, used at FDO Conference 2026. RO-Crate is the FDO emission format Shepard's `aidocs/52 §3` already plans as a sibling export — AASX vs RO-Crate is **different envelopes, same intent**. They're not in tension.

**NGSI-LD / FIWARE.** Cited in EDC adoption discussions but Catena-X/Tractus-X chose EDC+AAS over NGSI-LD. Smart-city and mobility deployments still use NGSI-LD; manufacturing and aerospace use AAS+EDC. Shepard's research-aerospace niche is AAS+EDC territory, not NGSI-LD.

**Linked Building Data.** Adjacent but BIM-specific; not in Shepard's path.

**Solid.** Personal-data-sovereignty pattern; not aligned with industrial supply-chain dataspaces.

### §6.4 — Three concrete counter-evidence citations

1. **PMC10255844 — Jacoby et al., "Open-Source Implementations of the Reactive Asset Administration Shell: A Survey", Sensors 2023.** Direct citation against premature AAS full-stack adoption; recommends adopting carefully sized subsets.
2. **Neubauer et al. 2023, "Architecture for manufacturing-X: Bringing asset administration shell, eclipse dataspace connector and OPC UA together", Manufacturing Letters 37: 1-6, DOI 10.1016/j.mfglet.2023.05.002 (58 citations).** Validates the AAS+EDC+OPC-UA stack as the manufacturing-X reference architecture, but also documents the operator complexity. (Full text not retrieved by survey agent — abstract + DOI verified.)
3. **MDPI Applied Sciences 2025, "Integration Approaches for Digital Twins in Dataspaces", DOI 10.3390/app152111623 (Fraunhofer ISST).** Discusses three architectures for AAS+EDC integration (EDC extension vs separate component vs dataspace connector into AAS repository). **[Per abstract + search snippets; full paper unread by the survey agent — PDF binary defeated text extraction.]**

---

## §7 — Acceptance criteria

How would we know the AAS + EDC plugins are working end-to-end?

**AAS:**

1. ✓ **aas4j-typed conformance.** `mvn test` in `plugins/aas/` passes a new `AasShellWireRoundTripTest` that serialises a Shepard `Collection` via aas4j → JSON → back, with no field loss vs the IDTA-01002-3-1 schema.
2. ✓ **aas-test-engines green for SSP-002 + Submodel-Repository-Read.** CI job boots Shepard with LUMEN+MFFD seeds, runs `aas_test_engines check_server` against `/v2/aas/`, the report shows ≥95% of SSP-002 tests passing. Failures documented (per `aidocs/52 §8 conformance budget`).
3. ✓ **AASX Package Explorer can browse Shepard.** Open AASX Package Explorer (Eclipse alpha 2025-10-21 or later), point at `https://shepard.nuclide.systems/v2/aas/`, see Collections as Shells and DataObjects as Submodels. Manual verification, screenshot saved to `aidocs/agent-findings/`.
4. ✓ **`shepard-admin aas import-idta-templates --refresh`** pulls the latest 60+ templates from `admin-shell-io/submodel-templates`, idempotently updates Shepard's `__templates`.

**EDC (when triggered):**

5. ✓ **Tractus-X EDC sidecar deploys via plugin manifest.** Operator runs `make compose-up-edc`; Tractus-X EDC + Vault + Postgres come up; `EdcAssetSyncService` publishes one MFFD Asset to the EDC management API; the EDC catalog endpoint shows it.
6. ✓ **Aerospace-X-aligned round-trip.** A Catena-X-flavoured tester (Tractus-X CLI, Sovity CLI, or BaSyx client) can negotiate an EDC contract for the MFFD AFP tape-laying DataObject and pull the resulting AAS Submodel JSON.

**Demonstrator (§5):**

7. ✓ **MFFD demonstrator screencast** — 3-minute video: open Shepard, point at a tape-laying DataObject, click "Publish as AAS Submodel + EDC offer", verify in AASX Package Explorer + Tractus-X EDC console.
8. ✓ **LUMEN demonstrator screencast** — 3-minute video: TR-004 sealed snapshot offered as embargoed EDC contract; embargo expiration triggers re-publication.

---

## §8 — Open questions

1. **Aerospace-X membership for DLR-nuclide.** Does the nuclide Shepard deployment have access to an Aerospace-X test endpoint, or is engagement currently via DLR Institute for AI Safety & Security only (the Catena-X-leading institute per the DLR page)? Establishing a contact at Fraunhofer ISST (Aerospace-X project lead) is the gating question for §5 / §7 acceptance.
2. **DLR Catena-X Internationalisation Committee participation.** The DLR Catena-X page names DLR as "leading the Internationalisation Committee." Is there a low-friction path to register Shepard as a Catena-X-compatible connector via this committee? Could short-circuit the §5 round-trip.
3. **License compatibility for sidecar adoption.** Tractus-X EDC and Sovity EDC ship Apache-2.0 code; their default Docker images may bundle commercial fonts/icons/UI dependencies — verify before redistributing as part of Shepard's compose. `feedback_github_pm_policies §dependency-review-config` already bans GPL/AGPL/SSPL; check Tractus-X transitive deps.
4. **DCP vs DAPS for identity.** EDC IdentityHub uses Decentralized Claims Protocol; Aerospace-X and Catena-X still use DAPS in practice. Which identity flavour does the operator deploy? Affects G18 substantially.
5. **`EDC-Extension-for-AAS` v2.3+ compatibility with current EDC v0.17.0.** The extension supports EDC 0.15.0 (Nov 2024); is a 2.3 release imminent, or does Shepard contribute a compatibility PR? Direct Fraunhofer IOSB contact (same maintainer org as FA³ST).
6. **BaSyx GA release timing.** Milestone-10 was 2025-04-23; an `aas4j`-only Shepard adoption (no sidecar) is the safer bet pending BaSyx GA. Track BaSyx releases monthly.
7. **MFFD data IP-clearance for Aerospace-X.** The real MFFD ZLP Augsburg data is DLR industrial IP. EDC contracts must encode the IP restrictions; ODRL policy modelling for this is non-trivial. Coordinate with DLR data governance before §5.1 ships.

---

## §9 — Cross-references

- `aidocs/integrations/52-aas-backend-integration.md` — the primary design doc this survey retroactively §1's.
- `aidocs/16-dispatcher-backlog.md` — AAS1 series queueing.
- `aidocs/34-upstream-upgrade-path.md` — AAS plugin extraction tracked.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — AAS row already at "AAS1-plugin shipped" status.
- `aidocs/platform/47-dev-experience-and-plugin-system.md` — plugin SPI shape.
- `aidocs/platform/30-mcp-plugin-design.md` — MCP tool integration pattern.
- `aidocs/40-ecosystem.md` — `dlr-shepard/shepard-edc-proxy` (archived) noted; this survey explains the "superseded" entry.
- `feedback_reuse_before_reimplement.md` — the standing rule this survey serves.
- `feedback_plugins_declare_sidecars.md` — the sidecar discipline §3.2 invokes.
- `project_storage_s3_garage.md` — Garage as EDC data plane backing.
- `project_m4i_integration_design.md` — m4i predicates as the provenance vocabulary covering AAS/EDC activities.

## §10 — Sources consulted

External, web-fetched 2026-05-23 (timestamps on §1 entries):

- https://github.com/FraunhoferIOSB/FAAAST-Service + /releases
- https://github.com/eclipse-basyx/basyx-java-server-sdk + /releases
- https://github.com/eclipse-aas4j/aas4j + /releases
- https://github.com/eclipse-tractusx/tractusx-edc + /releases
- https://github.com/eclipse-edc/Connector
- https://github.com/eclipse-edc/IdentityHub
- https://github.com/sovity/edc-ce
- https://github.com/FraunhoferIOSB/EDC-Extension-for-AAS + README
- https://github.com/eclipse-tractusx/sldt-digital-twin-registry
- https://github.com/admin-shell-io/aas-specs-api
- https://github.com/admin-shell-io/aas-test-engines
- https://github.com/admin-shell-io/submodel-templates
- https://github.com/eclipse-aaspe/package-explorer
- https://industrialdigitaltwin.io/aas-specifications/IDTA-01002/v3.1.1/http-rest-api/service-specifications-and-profiles.html
- https://www.isst.fraunhofer.de/en/departments/industrial-manufacturing/projects/aerospace-x.html
- https://www.dlr.de/en/research-and-transfer/projects-and-missions/catena-x-ein-datenokosystem-fur-die-automobilindustrie
- https://internationaldataspaces.org/offers/dataspace-protocol/
- PMC10255844 (Jacoby et al., reactive AAS survey)
- DOI 10.1016/j.mfglet.2023.05.002 (Neubauer et al., manufacturing-X architecture; abstract only)
- DOI 10.3390/app152111623 (Fraunhofer ISST integration approaches; abstract only, full PDF unread)

Internal (Shepard codebase):

- `plugins/aas/pom.xml` (confirmed no aas4j/BaSyx deps)
- `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/io/AasShellIO.java` (hand-rolled IO record)
- `aidocs/integrations/52-aas-backend-integration.md` (existing design doc)
- `aidocs/44-fork-vs-upstream-feature-matrix.md` (AAS row at AAS1-plugin shipped)
- `aidocs/40-ecosystem.md` (archived shepard-edc-proxy reference)

---

**End of survey.**
