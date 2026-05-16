# 64 — Provenance architecture (what's shipped + where it's going)

**Status.** Live reference, kept current with the PROV1 series.
**Snapshot date.** 2026-05-13.
**Audience.** Contributors and operators trying to understand
shepard's provenance / activity-capture stack, and the path from
PROV-O to a richer engineering-research description.

**Originating items.** PROV1 design in `aidocs/55`; this doc is the
"what shipped + what's coming" overview rather than the original
design. Touches `aidocs/63` ADR-0004 (PROV-O over OpenLineage),
ADR-0019 (preseed default-on).

---

## 1. What's shipped (PROV1a–d, f, g)

The PROV1 series is end-to-end. Five backend slices + one frontend
slice landed in this session.

### 1.1 Capture layer (PROV1a)

| Component | Path / shape |
|---|---|
| `:Activity` Neo4j entity | `de.dlr.shepard.context.provenance.entities.Activity` — `HasAppId`, modelled on `prov:Activity` |
| Fields | `appId`, `actorSub`, `actorDisplayName` (via `DisplayNameResolver` from U1b), `verb` (`POST` / `PUT` / `PATCH` / `DELETE`), `path`, `responseStatus`, `targetAppId`, `targetKind`, `recordedAt`, `instanceId` |
| JAX-RS filter | `ProvenanceCaptureFilter` — request+response filter on every mutating endpoint; captures 2xx by default |
| Service-layer hook | For non-REST flows (migrations, background jobs) — `ProvenanceService.recordServiceActivity(...)` |
| Constraint | `V15__Add_appId_constraint_Activity.cypher` — appId uniqueness |

**Operator config keys** (all default-safe):
- `shepard.provenance.enabled=true` — master toggle
- `shepard.provenance.capture-reads=false` — opt-in read capture (row volume grows ~10×)
- `shepard.instance.id=local` — per-deployment origin stamp (used by `aidocs/60` Edge sync)

**Permission posture:** casual users see only their own rows;
`instance-admin` sees all. The capture path never propagates
failures back to the user write — provenance is observability, not
a blocker.

### 1.2 Query layer

| Endpoint | What it returns |
|---|---|
| `GET /v2/provenance/activities` | Paginated activity stream (PROV1a) |
| `GET /v2/provenance/count` | Quick cardinality probe (PROV1a) |
| `GET /v2/provenance/entity/{appId}` | Per-entity trail (PROV1b); `TargetEntityResolver` extracts target identity heuristically from the request path |
| `GET /v2/provenance/stats?scope=instance\|collection\|user&id=&since=&until=` | Single-Cypher aggregation (PROV1c) — totals + sparkline buckets (auto daily/weekly switch at 90 days) + per-kind histogram + distinct-agent count + cumulative integral + `contentCensus` (entity-kind counts) + `byteTotals` (FB1a's stored `fileSize`) |

The `?profile=metadata|relations|all` output-profile machinery from
V2S1a applies — clients pick the level of detail.

### 1.3 Export layer (PROV1g)

`Accept: application/prov+json` on
`/v2/provenance/{activities,entity/{appId}}` emits a W3C PROV-JSON
subset with `activity` / `agent` / `entity` / `wasAssociatedWith`
/ `used` / `wasGeneratedBy` blocks. The `ProvJsonRenderer` wraps
the existing IO shapes; no second model.

### 1.4 Lifecycle (PROV1f)

`ProvenanceRetentionJob` — `@Scheduled` cron, default 2-year
window via `shepard.provenance.retention-days=730`. Negative value
keeps forever.

### 1.5 Frontend (PROV1d)

`ActivitySparklineCard.vue` on every Collection detail page.
Vanilla SVG (no Chart.js dep) — per-bucket bars + cumulative
polyline overlay + per-kind histogram + distinct-contributors
count + time-range picker (7d / 30d / 90d / 1y).

### 1.6 What's deferred

| Slice | Why deferred |
|---|---|
| `PROV1b2` | Explicit `:USED` / `:GENERATED` Neo4j edges — today `targetAppId` is enough; edges become valuable once SPARQL queries hit the graph |
| `PROV1c2` | Pre-aggregated `:ActivityRollup` — only on observed need |
| `PROV1e` | Instance-admin all-instance dashboard — depends on PROV1d (now shipped) + `aidocs/51 §9.5` admin-page strip |

---

## 2. Where PROV-O alone falls short

PROV-O is **abstract** by design: `Activity`, `Agent`, `Entity`,
`used`, `wasGeneratedBy`. shepard's current capture answers *who
did what when*, but not:

- *What kind of activity?* — "A POST" doesn't tell a downstream
  consumer it was a CT scan, a Raman measurement, a parameter
  sweep, a simulation run.
- *With what method, on what investigated object?* — PROV-O has
  no `Method`, `Tool`, `InvestigatedObject`.
- *With what units?* — PROV-O has no quantitative vocabulary.
- *What role did the agent play?* — PROV-O has `wasAssociatedWith`
  but no standardised role taxonomy.

For engineering-research the gap is real. shepard's PROV-O baseline
makes activities findable and citable; it doesn't make them
**reusable** in the sense the FAIR principles imply.

---

## 3. metadata4ing — the engineering-research extension

NFDI4Ing's **metadata4ing** ontology (current v1.4.0, December
2025; `http://w3id.org/nfdi4ing/metadata4ing/`) is a PROV-O-
compatible upper ontology for the data-generation process.

| Aspect | What it adds beyond PROV-O |
|---|---|
| Domain | `m4i:ProcessingStep` (subtype of `prov:Activity`), `m4i:Method`, `m4i:Tool`, `m4i:InvestigatedObject` |
| Quantities | `m4i:NumericalVariable` + QUDT units — same primitives a typed `TimeseriesReference` would need |
| Roles | DataCite v4.4 individuals (ContactPerson / DataCollector / DataCurator / …) for `wasAssociatedWith` |
| RO-Crate | Official m4i ↔ RO-Crate mapping (since v1.3) — composes with shepard's existing RO-Crate export (`aidocs/31`) |
| Croissant | Data-files-as-input/output modelling — clean fit for FileBundleReference + FileGroup |
| Compatibility | v1.3 explicitly flexibilised the processing-step ↔ actor relationship to be PROV-O-compatible — m4i wants to be the engineering layer **above** PROV-O |
| Imports | Already imports PROV-O, DCAT, DCTerms, FOAF, OWL, QUDT, RDF/S, schema.org, SIO, SKOS, SSN, biro, cr, dcc |

**Verdict.** metadata4ing is exactly the layer above PROV1's
PROV-O baseline. It's not a replacement; it's the vocabulary that
lets PROV1's abstract `:Activity` carry "this was a CT scan with a
Zeiss Versa 620 at 80 kV" instead of just "POST". Adoption is
**additive**.

### 3.1 ONT1b — preseed the ontology

ONT1b ships metadata4ing as the 10th bundle in the N1b/ONT1a
pre-seed pattern (`backend/src/main/resources/ontologies/metadata4ing.ttl`,
SHA-256-pinned in `ontologies-manifest.json`, licence CC BY 4.0).
This makes m4i terms resolvable in the n10s internal repository on
day one — the annotation picker can offer `m4i:ProcessingStep`,
`m4i:Method`, etc. directly.

### 3.2 PROV1h — lift PROV-O rendering to m4i

The follow-up slice (not yet scheduled, but the natural next
PROV step):

- New content-type `Accept: application/ld+json; profile="http://w3id.org/nfdi4ing/metadata4ing/"` on
  `/v2/provenance/{activities,entity/{appId}}` alongside the
  existing `application/prov+json`.
- Mapping rules:
  - `:Activity` → `m4i:ProcessingStep` (subclass of `prov:Activity`)
  - `targetAppId` + `targetKind` → `prov:used` / `prov:generated`
    typed by the Reference kind (`m4i:DataSet` etc.)
  - `actorSub` + `actorDisplayName` → `prov:wasAssociatedWith`
    with a DataCite v4.4 role individual
  - `verb` (POST / PUT / PATCH / DELETE) → `m4i:Method` subclass
    (Create / Update / Delete) — pragmatic mapping, the verb
    isn't a real method but it makes the JSON-LD parseable as a
    typed event
- Wire format: additive content-type; existing `application/prov+json`
  callers see no change.
- New ADR (to be assigned when shipped) records "m4i as PROV-O
  extension, not replacement" — analogous to ADR-0004's PROV-O
  pick.

---

## 4. Helmholtz Kernel Information Profile (KIP) — the PID layer

KIP (`aidocs/16` row pending; HMC publication
`10.3289/HMC_publ_03`) is a **different layer**: it's the
PID-record contract, not a domain ontology. KIP defines what
every Handle/DOI resolver must return for a digital object —
type, creation date, locator, checksum, license.

KIP and metadata4ing compose:

- **metadata4ing** = rich semantic description of what was done
- **KIP** = minimal PID-resolution record that points to that
  description

shepard would emit both at publish time:

- KIP-shaped record on the PID-mint hook (lightweight, citation-
  shaped, suitable for cross-Helmholtz services)
- metadata4ing-flavoured JSON-LD as the entity body / RO-Crate
  manifest content (the rich description KIP points at)

Design landing zone: `aidocs/64-hmc-kip-integration.md` (to be
written when this slice is scheduled; not in scope here).

### 4.1 Unhide (Helmholtz Knowledge Graph) harvest

Unhide (`docs.unhide.helmholtz-metadaten.de`) is harvest-pull —
shepard exposes a metadata feed, Unhide pulls it on a schedule
and lands it in the HKG via its internal data model + inward
mappings. Plugin-shape suits this: `shepard-plugin-unhide` would
expose `GET /v2/unhide/feed.jsonld` as schema.org + m4i JSON-LD
of Collections flagged for publication.

Design landing zone: `aidocs/65-unhide-publish-plugin.md` (to be
written; not in scope here). Plugin would depend on the m4i
content-negotiation (§3.2) so the feed cites m4i terms natively.

---

## 5. metadata4nfdi — context, not yet aligned

NFDI is rolling out **metadata4nfdi** as the cross-domain
harmonisation layer above per-domain ontologies (metadata4ing for
engineering, GO-FAIR for life sciences, NFDI4Earth's vocabularies,
etc.). Earlier-stage than metadata4ing — currently workshop /
RFC-shape effort.

**Position.** Align with metadata4ing first; watch metadata4nfdi
as it stabilises; lift to it when it converges. Going
metadata4nfdi-first today would put shepard ahead of where the
standard is.

---

## 6. Recommended phasing

| Phase | Slice | Status / next action |
|---|---|---|
| 1 | **ONT1b** — preseed metadata4ing as the 10th bundle | **In flight** (this session's dispatch); will ship as small PR |
| 2 | **PROV1h** — m4i content-negotiation on `/v2/provenance/*` | Not yet scheduled; ~M; gated on ONT1b |
| 3 | **KIP record + Unhide plugin** designs (`aidocs/64`, `aidocs/65`) | Design-first; PROV1h-gated |
| 4 | **metadata4nfdi alignment** | Watch-only until upstream stabilises |

Phases 1 and 2 together convert shepard's provenance from a
PROV-O-only "audit trail" into a metadata4ing-flavoured "research
activity log" — the same data, rendered with engineering
vocabulary at content-negotiation time. Phase 3 unlocks the
cross-Helmholtz find-and-cite surface. Phase 4 is the long-term
unification target.

---

## 7. Cross-references

- `aidocs/workflows/55-provenance-and-activity-overhaul.md` — the PROV1
  series design doc; this doc is the "what shipped" overlay.
- `aidocs/16-dispatcher-backlog.md` — PROV1a–g rows + ONT1b row.
- `aidocs/platform/63-architecture-decision-log.md` — ADR-0004 (PROV-O
  over OpenLineage), ADR-0019 (preseed default-on); future
  m4i-as-extension ADR lands when PROV1h ships.
- `aidocs/31-rocrate-export.md` — m4i ↔ RO-Crate mapping is the
  natural composition point.
- `aidocs/48-internal-semantic-repository-design.md` — n10s
  internal repo where the pre-seeded m4i terms live.
- `aidocs/ops/27-convenience-clients-design.md` — the typed-client
  layer that would surface `m4i:Method` etc. once PROV1h ships.

External:
- [Metadata4Ing 1.4.0 (NFDI4Ing)](http://w3id.org/nfdi4ing/metadata4ing/1.4.0/)
- [HMC Kernel Information Profile (KIT publication)](https://publikationen.bibliothek.kit.edu/1000173746)
- [Helmholtz Knowledge Graph documentation](https://docs.unhide.helmholtz-metadaten.de/)
- [W3C PROV-O](https://www.w3.org/TR/prov-o/)
