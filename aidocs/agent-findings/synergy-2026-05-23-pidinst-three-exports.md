---
stage: fragment
last-stage-change: 2026-05-23
---

# S-05 — One PID, three exports: PIDINST × SOSA/SSN × AAS Nameplate

## Synergy

A single `schema:instrument <pidinst-handle>` SemanticAnnotation on
a Shepard `DataObject` auto-projects to three downstream surfaces:
(a) a SOSA `:Sensor` triple in the internal n10s repository, (b) an
IDTA Nameplate Submodel in the AAS plugin export, (c) a PROV-O
`prov:wasAttributedTo` edge in the provenance export. PIDINST stops
being a stub identifier and becomes the calibration-traceability
hub that unifies materials/aerospace QA + I4.0 + FAIR provenance.

## Elements (named anchors)

- **External tool (mature):** `instdlr` (INST.DLR) —
  `aidocs/40 §2`, Federico Díaz Capriles (DLR), DOI
  `10.5281/zenodo.15180781`. PIDINST-schema instrument registry
  with handles for manufacturer/model/serial/measured-variable.
- **Standard:** RDA PIDINST 1.0 (white paper Krahl et al. 2020,
  *Data Science Journal* `10.5334/dsj-2020-018`).
- **Ontology (planned):** SOSA/SSN — `aidocs/40 §6`, planned
  pre-seed alongside HMC. `sosa:Sensor`, `sosa:Observation`,
  `ssn:SystemCapability`.
- **Plugin (designed):** `shepard-plugin-aas` —
  `aidocs/integrations/52-aas-backend-integration.md` (IDTA-02006
  *Digital Nameplate for Industrial Equipment* v2.0.1).
- **Shipped feature:** SemanticAnnotation on DataObject —
  `aidocs/40 §3` (P-series / `:SemanticAnnotation`).
- **Backlog row:** PIDINST integration —
  `aidocs/40 §7 row "PIDINST (RDA)"` ("Once instdlr integration
  lands").

## Why this is non-obvious

- PIDINST is filed in the ecosystem doc as a *standards we could
  contribute to* row — a future state. instdlr is filed as an
  *adjacent tool*. Neither row mentions the other.
- SOSA/SSN, AAS Nameplate, and PROV-O `wasAttributedTo` each have
  their own integration path planned. The shared upstream — the
  instrument identity — is invisible in each plan.
- The Nameplate submodel was published 2024 with explicit fields
  for ManufacturerName, ManufacturerProductDesignation, SerialNumber,
  ProductionDate, CountryOfOrigin. PIDINST has those exact fields
  (Schema v1.0 §3). One-to-one mapping.
- The unlock: one annotation captured once at ingest produces the
  EN 9100 calibration trail, the Catena-X nameplate, and the FAIR
  provenance attribution in one mint, three exports.
- DLR Augsburg already runs `instdlr` (per `aidocs/40 §2`) and
  already mints PIDs for the MFZ AFP cell instruments. The data is
  there; only the cross-walk is missing.

## Concrete output

### 1. Annotation shape (TTL, today and after)

```ttl
# today: just an annotation pointing at the PIDINST handle
shp:dataobject/tr-004 shp:hasAnnotation [
    shp:propertyIRI <https://schema.org/instrument> ;
    shp:valueIRI    <https://hdl.handle.net/21.T11969/abc123> ;
] .

# after: same annotation, n10s materialises three projections
shp:dataobject/tr-004 prov:wasAttributedTo <https://hdl.handle.net/21.T11969/abc123> .

<https://hdl.handle.net/21.T11969/abc123>
    a sosa:Sensor ;
    pidinst:hasIdentifier <https://hdl.handle.net/21.T11969/abc123> ;
    sosa:hasMember <https://hdl.handle.net/21.T11969/abc123/calibration-2026-03-14> ;
    aas:nameplate-ref <https://aas.dlr.de/instruments/abc123> .
```

### 2. n10s post-write trigger

The internal semantic repository (n10s, `aidocs/semantics/48`)
already runs on annotation write. Add an inferencer rule:

```ttl
# rules/pidinst-projection.shacl
[ a sh:NodeShape ;
  sh:targetSubjectsOf schema:instrument ;
  sh:rule [ a sh:TripleRule ;
    sh:subject sh:this ;
    sh:predicate prov:wasAttributedTo ;
    sh:object [ sh:path schema:instrument ] ] ] .
```

When an annotation with `propertyIRI = schema:instrument` is
written, the rule materialises the `prov:wasAttributedTo` edge.
Similar rules for `sosa:hasFeatureOfInterest` and `aas:nameplate-ref`.

### 3. AAS plugin: instrument-bound nameplate export

`GET /v2/dataobjects/{appId}` with `Accept: application/aas+json` —
when the DataObject has a `schema:instrument` annotation, the AAS
exporter inlines the IDTA Nameplate by dereferencing the PIDINST
handle and projecting the schema fields. Caching layer keyed by
the handle (rare-change set), 24h TTL.

| PIDINST field | AAS Nameplate Submodel path |
|---|---|
| `Owner.identifier` | `ManufacturerName.<lang>` |
| `Model.modelName` | `ManufacturerProductDesignation.<lang>` |
| `SerialNumber` | `SerialNumber` |
| `MeasuredVariable.variableName` | `extension:measuredVariable` |
| `Date.dateType=Commissioned` | `YearsOfConstruction` |

### 4. Calibration certificate as `sosa:hasMember`

Each calibration event recorded on the PIDINST handle becomes a
`sosa:hasMember` triple pointing at a `sosa:Procedure` individual
carrying date + certificate IRI + standard reference. The EN 9100
auditor's question "show me TR-004's instrument calibration as of
the test date" is one SPARQL hop.

```sparql
PREFIX sosa: <http://www.w3.org/ns/sosa/>
PREFIX prov: <http://www.w3.org/ns/prov#>
SELECT ?cert ?date ?standard
WHERE {
  shp:dataobject/tr-004 prov:wasAttributedTo ?inst .
  ?inst sosa:hasMember ?cal .
  ?cal sosa:resultTime ?date ;
       sosa:hasResult  ?cert ;
       sosa:usedProcedure/dct:conformsTo ?standard .
  FILTER (?date <= ?testDate)
}
ORDER BY DESC(?date)
LIMIT 1
```

## Real-world use case

**Persona:** quality auditor performing an EN 9100 audit on the
MFFD upper-fuselage AFP campaign. They ask: "show me the calibration
chain for the KUKA Quantec arm that ran ply 5 on 2026-05-22." Today:
the engineer pulls the PIDINST page in a browser, copies the
handle, searches the campaign wiki for the calibration entry,
hopes the page is current. After this synergy: one SPARQL query
against the Shepard semantic repository returns instrument →
calibration → certificate → standard reference, all timestamped.

For Catena-X (the EU industrial data-space): the same annotation
exports the Nameplate Submodel — a Catena-X "vehicle pass" can
include the instruments that produced the data, not just the
data itself. Provenance becomes a sustainability-passport feature.

For PLUTO (DLR satellite mission, Welzmüller et al. 2024): a
PIDINST handle on every commissioning-phase telemetry channel
gives the operator "what instrument was in use" without consulting
the mission ICD.

## External evidence

- **RDA PIDINST 1.0 white paper, Krahl et al., *Data Science
  Journal* 2020** —
  [datascience.codata.org/articles/10.5334/dsj-2020-018](https://datascience.codata.org/articles/10.5334/dsj-2020-018)
  Takeaway: the schema explicitly anticipates use as a metadata
  source for datasets via `schema:instrument` — exactly the
  Shepard annotation shape.
- **PIDINST documentation §12 *Linking instrument PIDs to
  datasets*** —
  [docs.pidinst.org/en/latest/white-paper/linking-datasets.html](https://docs.pidinst.org/en/latest/white-paper/linking-datasets.html)
  Takeaway: the FAIR linking direction is dataset → instrument PID;
  the schema:instrument annotation IS the recommended linkage.
- **IDTA-02006 *Digital Nameplate for Industrial Equipment*
  v2.0.1** — referenced in `aidocs/integrations/52 §1`.
  [industrialdigitaltwin.org/en/content-hub/submodels](https://industrialdigitaltwin.org/en/content-hub/submodels)
  Takeaway: Nameplate fields directly map to PIDINST schema; no
  custom adapter logic beyond the table above.
- **W3C SOSA/SSN Working Group Note** —
  [w3.org/TR/vocab-ssn](https://www.w3.org/TR/vocab-ssn/)
  Takeaway: `sosa:Sensor` is the standard class for a measurement
  instrument; PIDINST handle = `sosa:Sensor` URI is idiomatic.

## Effort estimate

**S (small).** Components:

- SemanticAnnotation pattern — already shipped.
- instdlr REST client (the registry has a stable FastAPI surface) —
  ~3-5 days.
- SHACL projection rules (3 inferencer rules) — ~2-3 days.
- Cached PIDINST-handle dereferencer — ~1 day.
- AAS Nameplate Submodel renderer is in the AAS plugin baseline —
  no additional work beyond the field mapping above.

Net incremental: ~1.5 weeks given the AAS plugin and SOSA/SSN
pre-seed land separately.

## Risk / counter-evidence

- PIDINST handle resolution depends on the registry being up.
  Mitigation: cache the Nameplate fields locally and tolerate
  registry downtime with a "stale data" badge; the FAIR
  literature explicitly endorses local caching with provenance
  (Wilkinson et al. 2016 §F4).
- The PIDINST schema is RDA-recommended but not yet ISO; vendors
  outside RDA may not consume the handles. Mitigation: the AAS
  export still works (industry standard); SOSA/SSN still works
  (W3C standard); the PIDINST handle is just an IRI of choice.
- instdlr is in Helmholtz Cloud and maintained by a single
  researcher; bus-factor risk. Mitigation: the schema is open; a
  fallback to ePIC handle minting (KIP1c) is already designed.
- Cross-walk fragility: AAS Nameplate is `IDTA-02006-2-0-1` but
  IDTA may revise. Mitigation: pin the version in the AAS plugin
  config; admin-configurable upgrade.
