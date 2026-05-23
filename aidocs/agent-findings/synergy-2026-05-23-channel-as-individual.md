---
stage: fragment
last-stage-change: 2026-05-23
---

# S-01 — Channel-as-individual: HSDS HDF5 × AAS TimeSeriesData × sTC

## Synergy

The 5-tuple timeseries channel descriptor
(`measurement`, `device`, `location`, `symbolicName`, `field`)
becomes a single named individual in the semantic repository; that
one individual auto-projects to three exports — an **HDF5 dataset
path**, an **AAS `TimeSeriesData` Submodel segment**, and a **sTC /
OPC UA NodeId binding**. Channel identity stops being substrate-
specific.

## Elements (named anchors)

- **Backend:** `backend/src/main/java/de/dlr/shepard/v2/timeseries/`
  (the 5-tuple channel surface; migrating to single `appId` per
  `aidocs/platform/87-timeseries-appid-migration.md`).
- **Plugin (shipped, no UI):** `shepard-plugin-hdf` —
  `aidocs/data/35-hdf5-hsds-implementation-design.md`,
  `aidocs/16` rows A5a/A5b/A5d.
- **Plugin (designed):** `shepard-plugin-aas` —
  `aidocs/integrations/52-aas-backend-integration.md` (Type 2
  Submodel Repository; IDTA-02008-1-1 *TimeSeriesData* v1.1).
- **External tool:** `shepard-timeseries-collector` (sTC) +
  `shepard-stc-config-helper` — both in `aidocs/40-ecosystem.md §2`.
- **Ontology bridge:** SOSA/SSN (`aidocs/40 §6` — planned).
- **Semantic substrate:** internal n10s repository
  (`aidocs/semantics/48`, N1b shipped).

## Why this is non-obvious

- The 5-tuple has been treated as a Shepard-specific identifier
  problem (TS-IDa). The synergy reframes it: each substrate already
  has its own channel identity — HDF5 has a dataset path, AAS has a
  `Segment.Record` element, OPC UA has a NodeId. They never met
  because nothing in Shepard tied them to one ontology node.
- The HDF5 design doc currently treats HSDS as a sidecar; the AAS
  design doc treats Submodels as a different surface; the sTC tool
  is described as an OPC UA bridge. None of the three design docs
  cites the other two.
- The TS-appId migration (currently scoped as backend refactor)
  becomes an **integration multiplier** — the appId is the single
  individual that all three substrates resolve against.
- SOSA/SSN is already in the planned ontology pre-seed
  (`aidocs/40 §6`) — the four-substrate bridge is one SOSA
  `:Sensor` per individual + `sosa:Observation` per timeseries
  point. The ontology was already going to be there.
- Reverse direction works too: any external AAS-aware client
  (Eclipse BaSyx, AASX Package Explorer) can read a Shepard channel
  via the AAS submodel without knowing Shepard exists.

## Concrete output

### 1. Named-individual shape (TTL fragment)

```ttl
@prefix shp:   <https://shepard.dlr.de/ns/> .
@prefix sosa:  <http://www.w3.org/ns/sosa/> .
@prefix aas:   <https://admin-shell.io/aas/3/0/> .
@prefix qudt:  <http://qudt.org/schema/qudt/> .

shp:channel/0192fcd5-c1f3-7000-9c2a-9f8a3e4b1c2d a sosa:Sensor ;
    shp:appId            "0192fcd5-c1f3-7000-9c2a-9f8a3e4b1c2d" ;
    shp:measurement      "afp_robot" ;
    shp:device           "kuka-quantec" ;
    shp:location         "mfz-cell-1" ;
    shp:symbolicName     "tcp_temp" ;
    shp:field            "value_celsius" ;
    qudt:hasUnit         qudt:DegreeCelsius ;
    shp:hsdsDatasetPath  "/runs/2026-05-22/tcp_temp" ;
    shp:aasSubmodelRef   <https://aas.dlr.de/mfz-cell-1/tcp_temp> ;
    shp:opcUaNodeId      "ns=2;s=KUKA.TCP.Temperature" .
```

### 2. New REST projections (no new endpoints — content negotiation)

`GET /v2/timeseries/{appId}` with `Accept: application/aas+json`
returns the channel as an IDTA-02008-1-1 `TimeSeriesData` Submodel.
With `Accept: application/x-hdf5-dataref+json` returns the HSDS
dataset descriptor. With `Accept: text/turtle` returns the
named-individual fragment above.

### 3. SHACL shape validating the binding

```ttl
shp:ChannelIndividualShape a sh:NodeShape ;
    sh:targetClass sosa:Sensor ;
    sh:property [ sh:path shp:appId ; sh:datatype xsd:string ;
                  sh:minCount 1 ; sh:maxCount 1 ] ;
    sh:property [ sh:path shp:hsdsDatasetPath ;
                  sh:maxCount 1 ] ;
    sh:property [ sh:path shp:aasSubmodelRef ;
                  sh:nodeKind sh:IRI ; sh:maxCount 1 ] ;
    sh:xone (
      [ sh:property [ sh:path shp:hsdsDatasetPath ; sh:minCount 1 ] ]
      [ sh:property [ sh:path shp:opcUaNodeId    ; sh:minCount 1 ] ]
      [ sh:property [ sh:path shp:aasSubmodelRef ; sh:minCount 1 ] ]
    ) .
```

(at least one of HSDS / OPC UA / AAS binding must exist; all three
may coexist.)

### 4. sTC config-helper extension

`shepard-stc-config-helper` today emits OPC UA → Shepard YAML keyed
by 5-tuple. After this synergy lands it emits a single channel
`appId` per node and writes the AAS Submodel reference in the same
pass. The helper is unblocked from the TS-appId migration
(`aidocs/40 §2` notes the helper "needs an update after the
TS-appId migration").

## Real-world use case

**Persona:** test-engineer at ZLP Augsburg running the MFZ AFP cell.
Today they have to (a) configure the sTC YAML with the OPC UA node
ID, (b) separately label the channel inside Shepard for the chart UI,
(c) hand-write an AAS Submodel by hand if a Catena-X partner asks
for the cell's I4.0 nameplate, (d) export HDF5 via a different
script for the post-test analysis notebook. Four manual steps,
three substrates, zero shared identity. After this synergy: the
channel is one named individual, all four artefacts derive from it.

The cross-institute payoff: the same channel-as-individual is what
a REBAR Airflow DAG references when reading the channel for ML
(via `shepard-py` + the HDF5 path), what a Catena-X
sustainability-passport demo references (via the AAS submodel),
and what the sTC bridge writes against (via OPC UA). One mint, many
consumers.

## External evidence

- **IDTA-02008-1-1 *Submodel TimeSeriesData* v1.1, March 2023** —
  *External Segment* references files (HDF5 supported via mime
  type `application/x-hdf5`); *Linked Segment* references API
  endpoints; both shapes map cleanly to the HSDS sidecar and the
  `/v2/timeseries/{appId}` endpoint.
  [PDF](https://industrialdigitaltwin.org/wp-content/uploads/2023/03/IDTA-02008-1-1_Submodel_TimeSeriesData.pdf)
  Takeaway: the spec already accommodates HDF5 — no Shepard-specific
  surface bending.
- **DeepWiki summary of `admin-shell-io/submodel-templates`,
  TimeSeriesData section** — confirms `ExternalSegment` /
  `LinkedSegment` duality.
  [URL](https://deepwiki.com/admin-shell-io/submodel-templates/3.5-time-series-data)
  Takeaway: AAS consumers expect this dual shape; Shepard would not
  need to extend the spec.
- **W3C SOSA/SSN Working Group Note** —
  `sosa:Sensor` + `sosa:Observation` + `ssn:SystemCapability` form
  the standard sensor-network vocabulary the channel-as-individual
  uses.
  [URL](https://www.w3.org/TR/vocab-ssn/)
  Takeaway: not a Shepard invention; reuses a recommended W3C note.

## Effort estimate

**M (medium).** Components:

- TS-appId migration lands first (separately planned, ~3-4 weeks per
  `aidocs/platform/87`).
- New JAX-RS resource methods for content-negotiated channel
  projection (~2-3 days).
- SHACL `ChannelIndividualShape` + N1c2 pre-seed of SOSA/SSN
  (~1 week).
- AAS Submodel renderer reused from `shepard-plugin-aas` AAS1a
  (~3-5 days once AAS1a lands).
- HSDS dataset path back-reference field on `TimeseriesReference`
  (~1 day, additive).
- sTC config-helper update (out-of-tree, ~3-5 days).

## Risk / counter-evidence

- The IDTA *TimeSeriesData* template assumes a single time-axis per
  Submodel; multi-channel HSDS datasets (vectors per timestep) need
  one Submodel per channel, which can balloon the AAS shape for
  highly-instrumented cells. Mitigation: keep channel = scalar
  field at the individual level, group via parent Submodel
  Collection.
- Garage S3 is the planned object backend (see S-06); current Garage
  release lacks object versioning, so the HSDS bucket cannot rely on
  S3 versioning for chunk immutability. The channel-as-individual
  doesn't need it — the appId + SHA-256 (PV1b shipped) is the
  immutability boundary.
- The PROV-O ↔ BFO mapping paper (2025, *Scientific Data*
  s41597-025-04580-1) shows that ad-hoc ontology bridges can lose
  fidelity. Mitigation: use SOSA/SSN as the channel vocabulary
  exactly as the W3C note specifies; do not introduce a custom
  predicate where a standard one exists.
