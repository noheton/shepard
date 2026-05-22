---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# Persona review — Data & Process Ontologist on the SHACL trio

**Reviewer.** Data & Process Ontologist persona (CLAUDE.md Role 2).
**Date.** 2026-05-22.
**Scope.** SHACL trio coherence review. **Caveat up front:** the trio the
task cites (`aidocs/semantics/98-mffd-process-shapes.md`,
`aidocs/platform/100-mffd-views-workspace.md`,
`aidocs/platform/101-view-shapes-and-spi.md`, `aidocs/semantics/shapes/`,
`aidocs/semantics/contexts/mffd-context.jsonld`) **does not exist on
disk** — neither in `main` nor in any of the 10 locked worktrees. The
task framing ("regenerating a prior review lost to worktree cleanup")
suggests the design docs themselves were either lost with the worktree
or never written; this is signal. What I actually reviewed:

- `aidocs/semantics/95-shacl-templates-and-individuals.md` (architecture)
- `aidocs/semantics/96-upper-ontology-alignment.md` (BFO/IAO/IOF/PROV-O)
- `backend/src/main/resources/shapes/*.ttl` — the **shipped** SHACL
  catalogue (8 files, 1596 lines): `shepard-core-shapes.ttl`,
  `mini-shapes.ttl`, `mffd-shapes.ttl`, `fair2r-shapes.ttl`,
  `dqr-shapes.ttl`, `rep-shapes.ttl`, `ledger-anchor-shapes.ttl`,
  `pipeline-shapes.ttl`, plus `README.md`
- `examples/mffd-showcase/ontology/mffd-process.ttl` (domain ontology)
- `plugins/analytics-ts/src/main/resources/shapes/mffd-anomaly-annotation.shacl.ttl`
- `aidocs/agent-findings/shacl-changeover-non-ts.md` (PR-1/3/4 landed)

This is more than enough to review the SHACL trio **as it actually
exists in code**, which is what reviewers should care about.

---

## 1. Verdict

The shape catalogue is **structurally sound** — composition via `sh:and`
+ `sh:node`/`sh:class`, semver via `dcterms:hasVersion`, sentinel
namespaces, and a clean upper-ontology alignment story (BFO → IAO/IOF →
shepard-upper → mffd). The pattern is right; OBO Foundry / IOF-Core
alignment is the correct anchor choice for aerospace manufacturing; the
"opt-in cognition, mandatory alignment" UX principle in aidocs/96 §4 is
the cleanest framing of upper-ontology pedagogy I have seen in an RDM
platform doc. **However**, the catalogue contains **three PROV-O type
errors** (most importantly `prov:wasInformedBy` used on Entities), a
**class-per-parameter modelling choice** that breaks at scale, **lost
QUDT units** between `mffd-process.ttl` and `mffd-shapes.ttl`, **three
incompatible namespace decisions** for `fair2r:`, `mffd:`, and the
plugin-side `shepard:` placeholder, **no SHACL enforcement** of the
NDT-FAIL→NCR rework invariant that the comment promises, and a missing
design doc for views (the "100/101" half of the trio). **I would not
sign off** until forks 1, 2, 4, and 5 below are resolved.

---

## 2. What works

- **Upper-ontology choice (aidocs/96 §2).** BFO 2020 (ISO 21838-2) + IOF
  Core + IAO + PROV-O is the **defensible** four-pillar choice for an
  RDM platform that bridges biomedical-style FAIR (OBO Foundry, ~1500
  ontologies) and aerospace-manufacturing FAIR (IOF, NIST/Boeing/BAM).
  Adding EMMO Core + CHAMEO + MSEO as opt-in domain bridges (not in the
  upper) is the right scoping call.
- **Composition pattern in `mffd-shapes.ttl`.** `MFFDCampaignShape →
  ProcessStepShape (sh:and + sh:node) → BridgeWeldingShape (sh:and)` plus
  reusable mini-shapes (`NDTGateShape`, `CalibrationCertificateShape`,
  `NCRShape`) lines 44–370 is genuinely Lego-set composition, not the
  monolith-per-domain anti-pattern most SHACL catalogues fall into.
- **Constraint provenance traceable.** `BridgeWeldingShape` numeric
  bounds (`mffd-shapes.ttl` lines 149–245) mirror `mffd:Constraint`
  triples in `mffd-process.ttl` §4 with a citation-grade comment. Auditor
  can verify the form bound came from the domain ontology, not a coder's
  guess.
- **F(AI)²R no-parentless-claim invariant.** `fair2r:ClaimShape` (lines
  186–226 of `fair2r-shapes.ttl`) with `prov:wasGeneratedBy minCount 1
  maxCount 1` enforces the strongest provenance invariant in the
  catalogue at the SHACL gateway. Per `project_fair2r_integration.md`
  this is the structural fix; it's correctly implemented.
- **Status vocabulary upgrade.** `shepard:DataObjectShape` `sh:in`
  enumeration (lines 99–100, `shepard-core-shapes.ttl`) added `NCR_OPEN`
  and `REJECTED` to the legacy 5-state set. That's the EN-9100 gap the
  Manufacturing/Quality persona flagged — quietly closed in this
  catalogue.

---

## 3. What's wrong (cite-and-claim)

### 3.1 `prov:wasInformedBy` is misused (PROV-O type error)

`shepard-core-shapes.ttl` line 107: `DataObjectShape` declares
`sh:property [ sh:path prov:wasInformedBy ; sh:class shepard:DataObject ]`.
PROV-O defines `prov:wasInformedBy` with **`rdfs:domain prov:Activity`
and `rdfs:range prov:Activity`**. Under `shepard:DataObject ⊑ iao:DataItem
⊑ bfo:Continuant` (aidocs/96 §3.1), this is **provably inconsistent** —
an OWL DL reasoner would flag the Continuant-as-Activity coercion. The
Entity→Entity lineage relation is **`prov:wasDerivedFrom`**; the
"transitive lineage" relation is **`prov:hadPrimarySource`** /
**`prov:wasRevisionOf`**. This is the kind of error that an external
OBO/IOF reviewer (the very gate aidocs/95 §0 calls out as required for
TPL3) will find within five minutes.

### 3.2 16 weld parameters as 16 OWL classes — wrong abstraction layer

`mffd-process.ttl` §4 lines 151–242 declare `mffd:CM_I`, `mffd:CM_t`,
`mffd:CM_p`, `mffd:W1_I` … `mffd:BridgePosition` each as
`owl:Class subClassOf mffd:ProcessParameter`. This means every welder
channel is a **class** (an abstract kind), not an **instance** (a
specific measurement). Concretely: when Track 1 Run 22192 (source DO
101456 in collection 48297) records "W1 current = 17.43 A at t=22.3 s",
where does that measurement attach? Not to `mffd:W1_I` — that's a class.
The shape `mffd:BridgeWeldingShape` (`mffd-shapes.ttl` line 142) papers
over this by introducing `sh:path mffd:W1_I` as if it were a datatype
property carrying a float — but `mffd:W1_I` is declared as a class, not a
property. **The shape and the domain ontology disagree on what
`mffd:W1_I` is.** IOF/EMMO's pattern is one `ProcessParameter` class
with a `parameterType` discriminator and per-instance quality values; or
PROV-O's `prov:Quantity` pattern. Either works; "class-per-channel" does
not scale to 16 → 160 channels and loses the QUDT unit binding (see
3.3).

### 3.3 QUDT units are declared, then thrown away by the form

`mffd-process.ttl` line 138 binds `mffd:hasUnit unit:A` on `mffd:CM_I` at
the class level. `mffd-shapes.ttl` line 150–158 declares the same
property in `BridgeWeldingShape` as `sh:datatype xsd:float`,
`sh:minInclusive 4.5`, `sh:maxInclusive 5.5`. **No unit travels with the
value.** The TPL1c form renderer (aidocs/95 §4 widget #13) reads the
shape, not the ontology — so what gets persisted is a bare `5.0`, not a
`qudt:QuantityValue [qudt:numericValue 5.0 ; qudt:hasUnit unit:A]`. This
is a **FAIR-Interoperable failure**: the data does not round-trip into
QUDT-aware downstream tooling (Mat-O-Lab, EMMO, IOF). The fix is
mechanical (the shape needs `sh:node` to a QUDT QuantityValue
sub-shape), but it has to happen before MFFD ships.

### 3.4 Three incompatible namespaces in production

| Namespace | Backend catalogue | Plugin (analytics-ts) | Status |
|---|---|---|---|
| `fair2r:` | `https://noheton.org/f-ai-r/ns#` | `https://noheton.github.io/f-ai-r/v0/` | **Diverged** |
| `mffd:` | `http://semantics.dlr.de/mffd-process#` | `https://shepard.example.org/mffd/` | **Diverged** |
| `shepard:` (upper) | `http://semantics.dlr.de/shepard-upper#` | `https://shepard.example.org/ontology/` | **Plugin uses example.org placeholder** |

When the plugin's `mffd:TimeseriesAnomalyAnnotation` lands in the same
graph as the backend's `mffd:BridgeWelding`, they are **different IRIs**.
SPARQL "all anomalies on bridge welding runs" returns empty. The
backend's `fair2r:AuthoringPass` and the plugin's
`fair2r:StatisticalPass` are sibling concepts in different namespaces
and the `subClassOf prov:Activity` chain in the plugin is **redundant**
because the plugin re-declares the class in its own IRI space. The
analytics-ts shape file's own header (lines 18–23) acknowledges the
coordination risk; the fix has not landed.

### 3.5 NDT-FAIL → NCR invariant is documented in prose, not enforced

`mffd-shapes.ttl` line 311–370 — `NDTGateShape` declares `ndtResult` as
`sh:in ("PASS" "FAIL" "CONDITIONAL" "PENDING")` and `raisedNCR` with
`sh:class shepard:NCR` — but **`raisedNCR` has no `sh:minCount`** and
**no `sh:qualifiedValueShape` / `sh:sparql` constraint** binding
"`ndtResult = FAIL → minCount(raisedNCR) ≥ 1`". The comment on line 369
says "If FAIL, raisedNCR should reference …" — that's a human
guideline, not a SHACL constraint. A FAIL gate without an NCR
**passes validation**, which is exactly the EN-9100 §8.7 gap the
catalogue is supposed to close. Equivalent gap on
`ProcessStepShape.hasNDTGate` (line 119–125): `sh:maxCount 1` with
no `sh:minCount` — a process step can ship with no NDT gate at all.

---

## 4. Forks

### Fork 1 — PROV-O lineage predicate on `DataObjectShape`

- **Path A (keep `prov:wasInformedBy`).** Pros: ships today; matches the
  text of aidocs/95 §11 ("Predecessor edge gains PROV-O typing").
  Cons: violates PROV-O typing (3.1); fails an OBO Foundry / IOF
  reviewer audit; reasoner-incompatible.
- **Path B (switch to `prov:wasDerivedFrom`).** Pros: PROV-O-correct
  (Entity→Entity); already aligned to BFO via `prov:Entity ⊑ bfo:Continuant`;
  doesn't break aidocs/95 §11 narrative (the *typing* there can be any
  PROV-O predicate). Cons: one-line shape edit + a 30-line migration
  re-tagging existing typed edges. Idempotent + reversible.
- **Path C (hybrid: `prov:wasDerivedFrom` for DataObject→DataObject,
  `prov:wasInformedBy` only on the `:Activity` log).** Pros: most
  defensible; lets each predicate stay in its native domain. Cons:
  slightly more code; two write paths.

**My pick: Path C.** Path B is the minimum; Path C is the right shape
because the `:Activity` log (PROV1a) genuinely is Activity→Activity and
`wasInformedBy` belongs there. Cost of doing this once now ≪ cost of
fixing it after 10⁵ rows.

### Fork 2 — Process-parameter modelling pattern

- **Path A (class-per-channel, status quo).** 16 OWL classes today,
  per-shape `sh:datatype xsd:float`. Pros: shipped; readable. Cons:
  doesn't scale (next welder has 32 channels, AFP has 40+); units lost;
  shape and ontology disagree on what `mffd:W1_I` is (3.2).
- **Path B (IOF-style `ProcessParameter` instance + `parameterType`
  taxonomy).** One `ProcessParameter` class; `mffd:W1_I` becomes an
  instance of `mffd:WeldCurrentParameterType` (a SKOS-like concept).
  Per-measurement value lives on the instance with `qudt:QuantityValue`.
  Pros: scales arbitrarily; QUDT round-trips; aligns to IOF directly.
  Cons: bigger refactor; shape needs the QUDT sub-shape.
- **Path C (EMMO/SOSA `:Observation` pattern).** Each measurement is an
  `sosa:Observation` with `sosa:observedProperty mffd:W1_I` and
  `sosa:hasResult [qudt:QuantityValue …]`. Pros: standard W3C ontology
  for sensor observations; future-proofs against the timeseries-appId
  migration (aidocs/87). Cons: most foreign to current code; introduces
  SOSA as a fifth upper anchor.

**My pick: Path B.** It's the natural IOF-style fit; SOSA is the right
answer for sensors but overkill for "discrete process-parameter
recorded once per process step." Defer SOSA to the timeseries side
where it actually buys something.

### Fork 3 — Claim `wasGeneratedBy` cardinality (F(AI)²R)

- **Path A (`minCount 1 maxCount 1`, status quo).** `fair2r:ClaimShape`
  line 196–197. Pros: enforces the no-parentless-claim invariant
  strictly; satisfies EU AI Act Article 50 (2026-08-02 deadline). Cons:
  every human-only annotation must be wrapped in an `:Activity` first;
  may surprise contributors.
- **Path B (`minCount 1`, no `maxCount`).** Allows multiple Activities
  to claim joint authorship (human + AI cosigning). Pros: matches real
  AI-assisted research; matches f(ai)²r's "verification ladder" where a
  Claim can be ai-confirmed AND human-confirmed. Cons: relaxes the
  invariant in name; still enforces at-least-one provenance edge.

**My pick: Path B.** The whole f(ai)²r point is the verification ladder
— Path A makes climbing the ladder require *replacing* the generating
Activity instead of *adding* a verifying one. Path B is the right
default; the "at least one" invariant is the load-bearing part.

### Fork 4 — Where do QUDT units travel?

- **Path A (on the class via `mffd:hasUnit`, status quo).** Domain
  ontology binds unit at class level; shape sees only `xsd:float`. Cons:
  units lost on write (3.3).
- **Path B (replace `xsd:float` with `sh:node` → `qudt:QuantityValue`
  sub-shape in the shape itself).** Form widget #10 (aidocs/95 §4) already
  promises "numeric input with unit pill suffix" but the shape doesn't
  encode it. Pros: round-trips correctly; one-shape change in `mini-shapes.ttl`
  + one-line edit per BridgeWelding parameter. Cons: every form-renderer
  consumer must learn `qudt:QuantityValue`.

**My pick: Path B.** Without it, the FAIR-I score regresses the moment
the first weld measurement lands.

### Fork 5 — Where does the SHACL invariant for NDT-FAIL→NCR live?

- **Path A (SHACL `sh:sparql` constraint, in the shape).** Most
  declarative; the rule lives where the validation lives. Pros: visible
  to every consumer that reads SHACL. Cons: SHACL-SPARQL is the
  aidocs/95 §4 "out-of-scope for v2" feature.
- **Path B (`sh:qualifiedValueShape` + `sh:qualifiedMinCount`).**
  In-pure-SHACL Core. Pros: ships with v1 widget set; pure SHACL.
  Cons: clunky to author by hand.
- **Path C (service-layer rule, not in the shape).** Pros: trivial.
  Cons: the rule is now invisible to plugins, MCP, the form renderer;
  defeats "the ontology IS the UI."

**My pick: Path B.** Don't skip the constraint just because SPARQL is
v2. The NCR-on-FAIL rule is the load-bearing audit invariant — it
must live in the shape catalogue or the catalogue's value claim is
hollow.

---

## 5. `[NEEDS-CLARIFICATION]` blocks

[NEEDS-CLARIFICATION] Does the missing trio (`98-mffd-process-shapes.md`,
`100-mffd-views-workspace.md`, `101-view-shapes-and-spi.md`) need to be
written before any further SHACL work, or is `aidocs/95` + `aidocs/96`
+ `shapes/README.md` the canonical design?
  Context: The task references three docs that don't exist; the shape
  catalogue is shipped and `aidocs/95` covers the architecture. It's
  unclear whether 98/100/101 are (a) abandoned, (b) deferred until
  views land, or (c) lost to worktree cleanup and need rebuilding.
  Options:
    A) Treat `aidocs/95` + `96` + `shapes/README.md` as canonical;
       delete the 98/100/101 references — pro: less ceremony, doc
       weight already paid; con: views design is genuinely missing
       (no doc explains list-column / detail-tab / facet rendering
       from shape).
    B) Reconstruct 98/100/101 as three short companions to 95 —
       pro: closes the "trio" by what it should have been (domain,
       views, SPI); con: 1–2 person-days of writing before next slice.
    C) Promote `shapes/README.md` to the implementation manual + add a
       single `aidocs/platform/97-shape-driven-views.md` covering
       100+101 — pro: minimum documentation surface; con: leaves
       MFFD-specific design (98) without a home.
  Lean: C, because the catalogue's `README.md` already does most of
  98's job and a single views-and-SPI doc closes the genuine gap
  without padding.

[NEEDS-CLARIFICATION] Pick a fork on `prov:wasInformedBy`
(see §4 Fork 1).
  Context: `shepard-core-shapes.ttl` line 107 violates PROV-O typing.
  Options A/B/C as above.
  Lean: C, because PROV-O semantics matter for cross-domain reasoning
  and Path C is the right shape regardless of effort.

[NEEDS-CLARIFICATION] Pick a fork on the 16-weld-parameter modelling
(see §4 Fork 2).
  Context: class-per-channel doesn't scale beyond the BridgeWelding
  worked example; units are lost.
  Options A/B/C as above.
  Lean: B (IOF instance-and-taxonomy).

[NEEDS-CLARIFICATION] Should `fair2r:ClaimShape.prov:wasGeneratedBy`
allow multiple Activities (joint human+AI cosigning) or stay 1..1?
  Context: aidocs/95 Part 15 / project_fair2r_integration.md describe
  a verification ladder; current shape `fair2r-shapes.ttl` line
  196–197 caps at one Activity.
  Options A/B as in §4 Fork 3.
  Lean: B.

[NEEDS-CLARIFICATION] Namespace-canonicalisation pass before any more
plugins ship: who owns the canonical IRI bases for `fair2r:`, `mffd:`,
and `shepard:`?
  Context: §3.4 — three diverged namespaces in production today.
  Options:
    A) Backend wins (`semantics.dlr.de/mffd-process#`,
       `semantics.dlr.de/shepard-upper#`, `noheton.org/f-ai-r/ns#`);
       analytics-ts edits its file — pro: DLR-canonical domains;
       con: invalidates any data already in the plugin's tables.
    B) Plugin wins for its scope (analytics-ts can publish under its
       own namespace); add `owl:sameAs` bridge — pro: no data
       migration; con: doubles the namespace surface.
    C) Mint a single canonical set in a new tiny doc
       (`aidocs/semantics/97-canonical-iris.md`) and freeze them —
       pro: future-proofs; con: ceremony.
  Lean: A + C — backend wins, then freeze via 97 so this doesn't
  happen a fourth time.

[NEEDS-CLARIFICATION] NDT-FAIL → NCR invariant: SHACL-Core
qualified-value or SHACL-SPARQL?
  Context: §3.5 — currently neither.
  Options A/B/C as in §4 Fork 5.
  Lean: B (qualified-value), even though clunky, because v1 shouldn't
  ship without the invariant.

---

## 6. What's missing from my domain

- **No `MFFDContext` JSON-LD context anywhere.** The task references
  `aidocs/semantics/contexts/mffd-context.jsonld` — it doesn't exist.
  Without a JSON-LD context, every consumer (MCP, the analytics plugin,
  any external SPARQL endpoint) has to invent its own prefix table.
  This is the most easily fixed item in the whole review (~50 lines).
- **No SOSA/SSN alignment for sensor channels.** `mffd-process.ttl`
  declares 16 ProcessParameter classes but says nothing about the
  sensors that observe them. SOSA's `Sensor`–`ObservableProperty`–
  `Observation` triad is the W3C standard for exactly this case;
  aidocs/96 §2.5 lists EMMO/CHAMEO/MSEO but not SOSA. Worth one
  paragraph in aidocs/96.
- **No CHAMEO defect-characterisation alignment.** `mffd-process.ttl`
  §5 enumerates 13 defect types (ColdLap, Underweld, Porosity, …) as
  bare `mffd:Defect` instances with no CHAMEO `chameo:Characteristic`
  link. CHAMEO is the EU Mat-O-Lab vocabulary for exactly this; the
  audit at `aidocs/agent-findings/industrial-robotics-ontology-audit.md`
  flags CHAMEO; the shapes don't use it.
- **No worked predecessor-chain story for the MFFD demo.** For Track 1
  Run 22192 (source DO 101456 in collection 48297), the typed
  predecessor chain "TapeLayup → SkinInspection → StringerWelding →
  SpotWelding → BridgeWelding → StringerConnection → CleatsWithLBR" is
  asserted as classes but **no shape constrains the sequence** — a
  CleatsWithLBR DataObject with a SkinInspection predecessor validates.
  IOF-Core has `iof:precedes` exactly for this.
- **No `provenance trail` shape for the rework loop.** The LUMEN
  showcase (TR-004 → repair → TR-006) and the MFFD AFP-FAIL→Rework→
  Re-NDT-PASS scenario both need a shape that *says* "this DataObject
  is a rework retry of that one, with this NCR as cause." Today the
  catalogue has `NCRShape` but nothing wiring the loop closed.
- **No reasoning story.** aidocs/96 §1 says "Shepard does not run OWL DL
  reasoners." Fine — but then `rdfs:subClassOf` chains are only useful
  if the SPARQL engine applies them. n10s/neosemantics' OWL-RL
  capabilities are weak; the design has not said what the actual
  inference posture is at query time.

---

## 7. Top 3 changes I would require before signing off

1. **Fix the PROV-O typing on `DataObjectShape.wasInformedBy` (Fork 1
   Path C) and pair it with the QUDT round-trip shape (Fork 4 Path B).**
   These two together prevent FAIR-I regression on day one of MFFD
   data ingest. One PR, both fixes; ~2 hours of work; uncontroversial.
2. **Refactor the 16-class welder model to a single
   `mffd:ProcessParameter` with an instance-level taxonomy (Fork 2
   Path B).** This is the load-bearing decision: it determines whether
   the next 14 process step variants ship in 30 minutes (Path B) or
   take 16 new classes each (Path A). Pair with the
   `parameterType`-vs-class fix in `BridgeWeldingShape`. ~1 day.
3. **Land the namespace-canonicalisation pass (Clarification 5,
   Path A+C) before any more plugins ship.** Ship a tiny
   `aidocs/semantics/97-canonical-iris.md` freezing
   `semantics.dlr.de/mffd-process#`, `semantics.dlr.de/shepard-upper#`,
   `noheton.org/f-ai-r/ns#` and edit the analytics-ts plugin file to
   match. The longer this waits, the more `owl:sameAs` bridges get
   accreted. ~3 hours of cleanup + a CI lint that rejects new
   `example.org` prefixes in `.ttl` files.

Sign-off after these three. The five remaining issues (NDT invariant,
SOSA, CHAMEO, predecessor sequence, reasoning posture) are real but
non-blocking for the MFFD pilot — they can land iteratively under the
v1.1 shape revision.

---

*End of review. Cite-grade: every claim above names file + line range.
Reviewable in 10 min. If the trio (98/100/101) is recreated, this
review's findings should re-apply against whatever those docs say —
the shapes on disk are what production sees, not the docs.*
