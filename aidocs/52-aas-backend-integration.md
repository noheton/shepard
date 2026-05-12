# AAS Backend Integration — Design

**Scope.** Evaluate whether and how shepard can act as a **backend
for the Asset Administration Shell** (AAS, Plattform Industrie 4.0
/ IDTA) repository pattern — i.e. expose shepard's
Collection/DataObject/Reference graph as AAS Shells / Submodels /
SubmodelElements so AAS-aware clients (Eclipse BaSyx, AASX Package
Explorer, vendor portals) can read and write against a shepard
instance without knowing it's shepard.

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors evaluating I4.0 / AAS positioning for
shepard.
**Originating items.** User question: "evaluate AAS as a shepard
backend; reference Plattform Industrie 4.0's 2025 *AAS Backend
Integration* publication." Couples to: `aidocs/47` (payload-kind
plugin SPI), `aidocs/48` (internal semantic repository via n10s —
AAS is RDF-rich), `aidocs/50` (experiment orchestration —
manufacturing-data overlap), `aidocs/39` (T1 templates — IDTA
submodel templates have the same shape), `aidocs/25` (L2 chain —
AAS `idShort` vs shepard `appId` discipline).

**Sources consulted.** Plattform Industrie 4.0, *Guidelines and
best practices for implementing backend integration in real-world
scenarios for AAS provisioning* (2025); IDTA-01001-3-0-2 (Part 1
Metamodel, release 25-01); IDTA-01002-3-2 (Part 2 API, v3.1.2);
admin-shell-io/submodel-templates repo (60+ released templates as
of 2026-05); Eclipse BaSyx wiki (AAS Repository / Submodel
Repository component shapes). Where a claim depends on a passage
in the 2025 backend-integration PDF that could not be retrieved
directly during drafting (the publisher returned 403 to the
WebFetch), it is flagged `[verify against 2025 paper]`.

---

## 1. What AAS is, in one paragraph

The **Asset Administration Shell** is the I4.0 standardised
"digital twin envelope" Plattform Industrie 4.0 and the
**Industrial Digital Twin Association (IDTA)** publish. Every
asset (a pump, a robot, a CNC machine) is represented by a
**Shell**; each Shell carries one or more **Submodels**; each
Submodel is a tree of **SubmodelElements** (Property, File, Blob,
ReferenceElement, SubmodelElementCollection, …). The shape is
RDF-friendly — every element has a semantic `globalId` and the
spec ships JSON / XML / RDF side-by-side. IDTA publishes 60+
**Submodel Templates** (Digital Nameplate v3.0.1, Technical Data
v2.0.1, Handover Documentation v2.0.1, Time Series Data v1.1.1,
Carbon Footprint, Predictive Maintenance, …) — "schemas for the
most common things a shop floor wants to record about an asset."
Three deployment flavours: **Type 1** (passive — AASX file),
**Type 2** (server — REST API, mainstream), **Type 3** (active /
proactive — agent-based; still emerging per *MDPI Future Internet
2025*). The API is normatively defined in IDTA-01002-3-2 with
the AAS Repository and Submodel Repository as the two reusable
service shapes.

## 2. Why this is interesting for shepard

shepard's Collection / DataObject / Reference shape maps
**strikingly well** to AAS Shell / Submodel / SubmodelElement, and
the persistence shepard already runs (Neo4j for the graph,
MongoDB / S3 / TimescaleDB / PostGIS for payloads, n10s for
ontologies once `aidocs/48` lands) is the persistence an AAS
Type 2 server needs. The 2025 *AAS Backend Integration*
publication frames the field as "AAS over your existing
backends" — gateways, mediators, adapters that surface a SQL DB /
SCADA / PIM / ERP under the AAS HTTP-REST contract. shepard sits
squarely in the same architectural slot for the **research-data +
industrial-data** intersection.

Three drivers:

1. **Casual-user enablement (`aidocs/42 §1.0`).** Industrial
   research groups increasingly produce AAS exports — for vendor
   handovers, for EU Battery Regulation carbon-footprint
   reporting, for EU Data Act asset-data sharing. If shepard
   hosts the data *and* speaks AAS, the researcher gets
   compliance "for free" — no second system, no round-trip.
2. **Cross-cutting with `aidocs/50` experiment orchestration.**
   The manufacturing-experiment world is exactly where AAS
   lives. A test-bench experiment driving shepard via the
   coordinator becomes an AAS-conformant data source when
   downstream OEMs ask for it.
3. **Semantic backbone (`aidocs/48`).** AAS Submodels reference
   ontologies (ECLASS, IRDI, IEC CDD); n10s already hosts the
   sibling general-purpose ontologies. Adding ECLASS / IRDI as
   pre-seeded ontologies is the natural N1b sibling.

This is not "shepard becomes an AAS server full-stop." It is
shepard becoming **one of the backends** the AAS world plugs
into.

## 3. Mapping — AAS to shepard primitives

Side-by-side. Where the mapping is lossy or contentious, called
out explicitly.

| AAS concept | shepard primitive | Notes / mismatch |
|---|---|---|
| `AssetAdministrationShell` | `Collection` | Lossless. Shell metadata (`administration`, `description[]`, `derivedFrom`) maps to Collection attributes + (post-J1a) lab-journal entries. |
| `Submodel` | top-level `DataObject` | Lossless. `idShort` ≙ `name`; `id` (global IRI) ≙ `appId` (post-L2d, see §6). |
| `SubmodelElementCollection` | Nested `DataObject` | Lossless. |
| `Property` (typed scalar) | DataObject attribute | **Lossy today, lossless after T1.** AAS Properties have `valueType` (`xs:string`/`xs:double`/`xs:dateTime`/…); shepard attributes are stringly-typed until T1's `AttributeSpec.type` lands. |
| `File` (`contentType` + `value` URL) | `FileReference` | Lossless. AAS `value` URL ≙ presigned URL once `aidocs/45` FS1 lands (proxy URL today). |
| `Blob` (inline base64) | `FileReference` inline | Lossless; discouraged — AAS Blob is rarely used in practice. |
| `ReferenceElement` | DataObject predecessor edge, or a Neo4j relationship | Lossless for first-class links. Cross-Shell references need a `:AAS_REFERENCE` edge — small graph addition, not a primitive. |
| `MultiLanguageProperty` | DataObject attribute keyed by lang tag | **Lossy today.** After T1 + `aidocs/36 §3.2`, either nested SMEC or `lang:`-prefixed keys (decision §10). |
| `Operation` | **Not mapped** | RPC; shepard is a data store, not an actuator. Out-of-scope (§7). |
| `Entity` | Nested DataObject with `entityType` attribute | Lossless. |
| Submodel Template | T1 Template with `templateKind = "AAS_SUBMODEL_TEMPLATE"` | Natural fit; see §6.1. |
| `globalId` / `semanticId` (ECLASS / IEC CDD / IRDI IRI) | Semantic-annotation IRI per `aidocs/14` / `aidocs/48` | Lossless once ECLASS / IRDI pre-seeded in n10s. |
| TimeSeriesData submodel | `TimeseriesReference` + `TimeseriesContainer` | **Lossless and idiomatic** — shepard's 5-tuple is a strict superset of IDTA `TimeSeriesSegment` / `Variable`. |
| AASX package | RO-Crate sibling export with AASX serialiser | Different envelope, same intent. |
| Asset Interface Description (AID) | Out-of-scope v1 | EXP1 territory (`aidocs/50`); not a data submodel. |

**Contentious mappings:**

- **Property typing vs shepard's stringly-typed attributes** —
  the single biggest mismatch. Without T1's `AttributeSpec` the
  round-trip is lossy on numbers / dates. **AAS support ships
  *after* T1b**, not before.
- **`idShort` is not globally unique** (only within a parent);
  shepard wants stable identifiers. Map `idShort` to DataObject
  `name`; use shepard `appId` (`aidocs/25` L2d) as the
  AAS-exposed identifier.
- **MultiLanguageProperty.** Best as nested SMEC; lighter as
  `lang:`-prefixed attribute keys. Decision deferred to §10.

## 4. API surface options

IDTA-01002 defines several interfaces; the two that matter for a
backend are the **AAS Repository API** (CRUD over Shells +
discovery) and the **Submodel Repository API** (CRUD over
Submodels, addressable independently of the Shell that references
them). The OpenAPI specs live on SwaggerHub at `Plattform_i40` and
mirror to `admin-shell-io/aas-specs-api`.

Three integration shapes, with effort × value.

### 4.1 Option (a) — Adapter shim at `/v2/aas/...`

A thin JAX-RS layer under `backend/.../aas/` that exposes the
AAS Repository + Submodel Repository HTTP contracts. Each
incoming request translates to shepard CRUD over the underlying
graph. No new persistence; the adapter is stateless.

```
HTTP                                       shepard
─────────────────────────────────────────────────────────────
GET /v2/aas/shells                  →    GET /v2/collections (+ project)
GET /v2/aas/shells/{aasId}          →    GET /v2/collections/{appId}
PUT /v2/aas/shells/{aasId}          →    PUT /v2/collections/{appId}
GET /v2/aas/shells/{aasId}/submodels →   list top-level DataObjects
GET /v2/aas/submodels/{submodelId}  →    GET /v2/data-objects/{appId}
GET .../submodel-elements/{idShortPath} → walk nested DataObjects
GET .../submodel-elements/{idShortPath}/$value → typed scalar
```

`aasId` / `submodelId` are AAS-flavour Base64-URL-encoded IRIs
(per IDTA-01002-3-2). shepard accepts both the raw `appId` (for
native clients) and the IRI-encoded form (for AAS clients) — see
§6.2.

**Pros.** Smallest footprint. Reuses every existing service. AAS
clients see a conformant repository; shepard clients see no
change. All under `/v2/aas/` per `CLAUDE.md`.

**Cons.** Projection logic accumulates (deep `idShortPath`
queries, `level=Deep` recursion, `extent=WithBlobValue` content
negotiation). Mitigation: ship a subset (the
"Profile_AasxFileServer_Read" surface) and document the gap.

**Effort.** ~6–8 eng-weeks for AAS1a–AAS1d (see §9).

### 4.2 Option (b) — Submodel-only payload-kind plugin

Drop AAS support to **just the Submodel Repository surface** —
shed Shell-level discovery entirely. Ship as a payload-kind
plugin per `aidocs/47 §2`: a new `AasSubmodelReference` payload
kind whose REST surface
`/v2/aas-submodels-references/{appId}/...` projects the
underlying graph as Submodel JSON.

**Why interesting.** The 2025 publication observes that the
Submodel Repository is the most reused surface — most consumers
care about a specific Submodel (Nameplate, Carbon Footprint, Time
Series Data) and don't traverse Shell discovery [verify against
2025 paper].

**Pros.** Fits the plugin SPI; no special-case in core; new
payload-kind for free. Closer to "shepard sells Submodels" than
"shepard pretends to be an AAS server."

**Cons.** AAS clients that expect the full Repository surface
(BaSyx, AASX Package Explorer) won't find shepard via Shell
discovery — they'd need direct-Submodel configuration. Worse
casual-user story.

**Effort.** ~3–4 eng-weeks.

### 4.3 Option (c) — Full AAS Type 2 server, native

Shell / Submodel as first-class shepard entities alongside
Collection / DataObject; existing primitives become a projection.

**Pros.** Cleanest conformance story.
**Cons.** Massive — inverts shepard's data model; internal
refactor touches every persistence path. Rules out for v1, likely
indefinitely unless shepard pivots from "research-data-first" to
"AAS-first."
**Effort.** Multi-quarter. Out of scope.

### 4.4 Comparison

| Aspect | (a) Adapter shim | (b) Submodel-only plugin | (c) Full native server |
|---|---|---|---|
| Eng cost | ~6–8 weeks | ~3–4 weeks | Multi-quarter |
| AAS conformance ceiling | High (subset of IDTA conformance profiles) | Medium (Submodel Repository only) | Full |
| Casual-user "show in AASX Explorer" | Yes | Hand-rolled | Yes |
| Internal disruption | Low — additive `/v2/aas/` surface | Very low — plugin shape | High |
| Couples to upstream upgrade-path discipline | Compatible | Compatible | Compatible only in letter, not spirit |
| Fits `CLAUDE.md` `/v2/` policy | Yes | Yes (plugin endpoints already `/v2/`) | Yes |

## 5. Recommended path

**Ship (a) — the adapter shim — as v1. Keep (b) as the fall-back
if (a)'s scope balloons. (c) is parked indefinitely.**

Rationale:

1. **AAS clients expect a repository.** BaSyx, AASX Package
   Explorer, IDTA test harnesses all assume an AAS Repository at
   the top. (b) makes shepard invisible without per-deployment
   configuration; (a) gives the casual user "configure your AAS
   client with this URL and you're done."
2. **Effort sized like other feature slices.** 6–8 eng-weeks
   matches `aidocs/35` HDF5 / `aidocs/38` Git — deliberate
   feature-slice, not a multi-quarter pivot.
3. **Fall-back is clean.** If AAS1a–AAS1c reveal conformance
   gaps bigger than projected, the work pivots to (b) — the
   Submodel projection logic is the shared core. No throw-away.
4. **(c) is the wrong fit** for a research-data platform.

Recommendation is **conditional** on §10 — if the maintainer
decides shepard should *not* host its own AAS Repository, (b)
becomes default.

## 6. Data-model concerns

### 6.1 Submodel templates ride T1

IDTA Submodel Templates declare which SubmodelElements a
conformant Submodel carries, their `valueType`s, their
`semanticId`s, and required-vs-optional disposition — the same
problem `aidocs/39` T1 solves. **Reuse the machinery:** an IDTA
template becomes a shepard Template with `templateKind =
"AAS_SUBMODEL_TEMPLATE"`, stored in `__templates`. The
`AttributeSpec` carries `valueType` + `semanticId` + required;
`FileSlot` carries AAS File-element MIME constraints.

Two consequences: **AAS support ships *after* T1b** (typed
`AttributeSpec` is the prerequisite for type-preserving
round-trips); and a `shepard-admin aas import-idta-templates`
CLI command bulk-imports from `admin-shell-io/submodel-templates`.

### 6.2 Identifier discipline — `idShort` / `appId` / OGM id

| AAS field | Maps to shepard |
|---|---|
| `Submodel.id` (global IRI — IRDI / URN / `https://`) | shepard `appId` (post-L2d, `aidocs/25`); adapter accepts both URL-encoded `Submodel.id` and bare `appId` |
| `Submodel.idShort` (human-friendly short, not globally unique) | DataObject `name` |
| `Submodel.administration.version` / `.revision` | `Version` marker today; V2 snapshot appId post-`aidocs/41` |

**Hard cases.** (1) An AAS client `PUT`s a Submodel with `id =
urn:foo:bar`. shepard mints its own UUID v7 `appId` and stores
the AAS `id` as `aas:globalId`; both round-trip. (2) Legacy
numeric OGM ids must never leak to AAS clients — AAS endpoints
live exclusively under `/v2/`. (3) `idShortPath` queries walk
nested DataObjects by mutable `name`; clients break under rename.
Accept + document rather than freeze the rename surface.

### 6.3 RDF interop via n10s

AAS ships RDF alongside JSON / XML. `aidocs/48` plans n10s as
the in-shepard ontology host. Two implications: (1) AAS-RDF
emission rides n10s — once N1a + N1f land, the projection is
SPARQL-queryable with no new code beyond the emitter. (2)
**ECLASS + IRDI become pre-seeded ontologies** in N1b's bundle
(~30 MB additional, within budget) — resolves `semanticId` IRIs
without external lookup.

### 6.4 Type-3 (active) AAS — out of scope

Type 3 (agent-based, autonomous) **lacks formal specifications**
as of 2026-05. Active AAS implies an OPC-UA companion emitting
events; `aidocs/50` EXP1 owns OPC-UA on the test bench, and
conflating it with the AAS adapter mixes the concerns. Keep the
AAS adapter passive (Type 2 only); revisit Type 3 if a formal
spec lands post-2027.

## 7. Security / permissions

AAS is auth-agnostic on the wire but practitioners default to
**OAuth2 / OIDC bearer tokens** (BaSyx ships OAuth2 by default).
shepard's `JWTFilter` (`aidocs/24`) already speaks OIDC — wire-
level integration is one-line config.

**AuthN — JWT reuse.** `/v2/aas/` endpoints reuse `JWTFilter`;
the AAS client's bearer is shepard's bearer. No new mint path.
API-key minting (`aidocs/51`) accepts `aas:read` / `aas:write`
in the role allowlist; v1 gates writes on `instance-admin`.

**AuthZ — per-Shell ACLs.** Per-Collection / per-DataObject
Permissions (`aidocs/24`) **already** model what AAS clients
expect (vendor multi-tenancy where each customer sees only their
assets). The adapter respects the graph:

- `GET /v2/aas/shells` returns only Shells projected from
  readable Collections (`filterAllowedForUser`).
- `GET /v2/aas/submodels/{submodelId}` returns 404 (not 403) for
  unreadable Submodels — shepard's existing 404-on-no-read
  discipline.
- `PUT` / `DELETE` reuse the Permissions edge shepard already
  enforces.

Group-based ACL ("members of OEM group X can read all Shells
tagged `oem=X`") is `aidocs/24` F2 territory — visible-gap
follow-up, not a gate on AAS1.

**Operations endpoint — not exposed.** AAS `Operation`
SubmodelElements are RPC. v1 does **not** expose them — shepard
is a data store, not an actuator. Any `Operation` element
projects as a stub with `invocable = false`.

## 8. Standards-conformance budget

IDTA publishes conformance profiles in IDTA-01002-3-2. shepard's
v1 target is **the read-side of the AAS Repository + Submodel
Repository profiles, partial write**, against the **three**
IDTA Submodel Templates that cover 80% of real-world deployments:

| IDTA Submodel Template | v1 support | Rationale |
|---|---|---|
| **Digital Nameplate v3.0.1** | Full RW | Universal; maps to Collection-level attributes + `manufacturerName` / `serialNumber` / `manufacturingDate`. Closes the casual "publish an AAS for my test bench" path. |
| **Technical Data v2.0.1** | Full RW | Generic equipment specs ≙ DataObject with structured attributes; `TechnicalProperties` SMEC ≙ nested DataObject. |
| **Time Series Data v1.1.1** | Read full, write append-only | shepard's `TimeseriesContainer` + 5-tuple is a **strict superset** of IDTA `TimeSeriesSegment` / `Variable`. Lossless projection. |
| Handover Documentation v2.0.1 | Read-only | Lower-priority; write punted to AAS1f. |
| Carbon Footprint v1.0.1, Predictive Maintenance, AID/AIMC, AI Model Nameplate | Out of scope v1 | Revisit on demand. AID/AIMC is `aidocs/50` EXP1 territory. |

**Why these three first:** Nameplate is the gateway template
every AAS client expects; TechnicalData is the gateway for the
OEM-to-customer handover scenario; TimeSeriesData is the gateway
shepard **already wins on** — shepard's timeseries support is
more capable than most vendor AAS implementations [verify against
2025 paper], so it's the "shepard's value-add visible" submodel.

**Conformance test plan.** Run IDTA's `aas-test-engines` Python
suite against a CI-booted shepard with the adapter wired + LUMEN
seed loaded. CI artifact alongside JaCoCo (`CLAUDE.md`
§coverage). v1 target: pass `Profile_AasxFileServer_Read` +
Submodel Repository read-only profile. Full conformance is a
stretch goal, not a v1 gate.

## 9. Phasing — AAS1 series

| ID | Slice | Size | Gate |
|---|---|---|---|
| **AAS1a** | Tiny observable win: `GET /v2/aas/shells` returns a JSON list of Shells, one per readable Collection. Minimal AAS payload — `id` (from `appId`), `idShort` (from `name`), empty `submodels`. No write, no nesting. | S | L2d (`aidocs/25`) |
| **AAS1b** | `GET /v2/aas/shells/{aasId}` + `GET .../submodels` (top-level DataObjects as Submodel refs). Read-only. | M | AAS1a |
| **AAS1c** | `GET /v2/aas/submodels/{submodelId}` + `GET .../submodel-elements/{idShortPath}` — read-side Submodel Repository; nested walk; typed `valueType` projection. | M | AAS1b + T1b |
| **AAS1d** | Three IDTA templates imported as `templateKind = "AAS_SUBMODEL_TEMPLATE"` (Nameplate, Technical Data, Time Series Data). `shepard-admin aas import-idta-templates` CLI. | M | T1b + N1a |
| **AAS1e** | Write side: `PUT /v2/aas/shells/{aasId}` + `POST /v2/aas/submodels` + `PUT .../submodel-elements/{idShortPath}/$value`. Validation against templates. | L | AAS1c + AAS1d |
| **AAS1f** | AASX package export — `GET .../aasx` returns a conformant `.aasx` zip. Reuses `aidocs/31` R2 streaming. | M | AAS1c |
| **AAS1g** | RDF projection — `GET .../shells/{aasId}` with `Accept: text/turtle` emits AAS-RDF; n10s SPARQL proxy queries it (`aidocs/48` N1f). | M | AAS1c + N1f |
| **AAS1h** | CI conformance — `aas-test-engines` against a CI-booted shepard. Failures documented, not gating, until subset stable. | S | AAS1c |
| **AAS1i** | (deferred) AAS Registry surface (`/v2/aas/shell-descriptors` + `/v2/aas/submodel-descriptors`) for federated discovery. | M | parked (X3) |
| **AAS1j** | (deferred) Operation invocation stubs — 405 with `aas:operation_not_supported`. | S | AAS1c |
| **AAS1k** | (deferred) Submodel-only fall-back (option (b)) as a `PayloadKind` plugin. Lands only if AAS1a–AAS1c reveal the Repository surface is too costly. | M | AAS1a (decision-gate) |

**Recommended order: AAS1a → AAS1b → AAS1d → AAS1c → AAS1f →
AAS1e → AAS1g → AAS1h.** AAS1a is the tiny observable win — ships
independent value the moment L2d is in. AAS1d front-loads template
import so AAS1c has real templates. AAS1e (writes) ships last to
isolate write-side risk. Each slice ≤ 2 weeks; if AAS1e exceeds,
split AAS1e1 (single-submodel writes) / AAS1e2 (deep-element).

## 10. Open questions / decisions for the maintainer

Should **not** be decided by the design agent.

1. **Is shepard taking on "AAS backend" as a positioning shift?**
   §5 assumes "yes, additively." If "no — AAS is a niche we don't
   want," AAS1 stays parked. **The gate question.**
2. **Adapter (a) vs Submodel-only plugin (b).** §5 recommends
   (a); a maintainer who concludes Submodel-only suffices flips
   to (b). Substantially smaller; cleaner plugin-SPI fit; worse
   AAS-client UX.
3. **Bundle the IDTA Nameplate template in the LUMEN showcase
   seed?** AAS1d ships the machinery; whether the showcase
   Collection demos a populated Nameplate is a content decision.
4. **MultiLanguageProperty mapping convention** — nested SMEC vs
   `lang:`-prefixed attribute keys (§3). Either works; maintainer
   picks. One-liner in AAS1c.
5. **Is Type 3 (active) AAS on the roadmap at all?** §6.4
   recommends out-of-scope until the spec stabilises.
6. **OpenAPI tag strategy** — separate `aas-repository` /
   `aas-submodel-repository` tags vs inline with `/v2/`.
   Recommend separate per `aidocs/47 §6`.
7. **Does AAS conformance failure block AAS1?** §8 proposes
   "documentation, not gate." A stricter maintainer could require
   the subset to pass before AAS1e merges.
8. **AAS Registry (AAS1i) — ever?** Parked; revisit if `aidocs/16`
   X3 federation wakes up.

## 11. Cross-references

- `aidocs/16` — AAS1 series queueing entry follows this design.
  AAS1a gates on L2d; AAS1c on T1b + N1a; AAS1g on N1f.
- `aidocs/22 §4.x` — new `shepard-admin aas
  import-idta-templates` + `aas conformance-check` commands.
- `aidocs/25` L2d — `appId` as the AAS-exposed identifier; gate
  for AAS1a.
- `aidocs/31` — AASX export reuses R2 streaming + selectivity.
- `aidocs/34` — AAS1a is **AWARE** (new top-level `/v2/aas/`
  surface); per-slice rows added as slices ship.
- `aidocs/39` T1 — AAS Submodel Templates as
  `templateKind = "AAS_SUBMODEL_TEMPLATE"`; AAS1c gates on T1b.
- `aidocs/42`, `aidocs/44` — dispatcher updates after this lands.
- `aidocs/45` FS1 — AAS `File` value URLs ride presigned shape
  when available; proxy fall-back otherwise.
- `aidocs/47 §2` plugin SPI — AAS1k fall-back path implements
  Submodel-only as a `PayloadKind` plugin.
- `aidocs/48` neosemantics — ECLASS + IRDI as pre-seeded
  ontologies (N1b extension); AAS-RDF projection (AAS1g) rides
  N1f SPARQL proxy.
- `aidocs/50` EXP1 — coordinator emits AAS-shaped Time Series
  Data submodels as downstream consumer; AID / AIMC belong to
  EXP1, not AAS1.
- `aidocs/51` instance-admin — `aas:write` gates on
  `instance-admin` for v1; revisit when F2 group-permissions land.
- **External:** IDTA-01001-3-0-2 (Metamodel), IDTA-01002-3-2
  (API), `admin-shell-io/submodel-templates`,
  `admin-shell-io/aas-test-engines` (conformance suite), Eclipse
  BaSyx (reference Type 2 server — comparison, not dependency).

## 12. What this is NOT

- **Not** a commitment to drop shepard's existing primitives in
  favour of AAS shape. Shell / Submodel are projections of
  Collection / DataObject; the underlying graph stays as-is and
  native clients see no change.
- **Not** a "shepard is now an industrial digital-twin platform"
  positioning shift. shepard remains a **research-data platform**
  per `aidocs/42`; AAS is additive surface for the
  industrial-research intersection.
- **Not** an OPC-UA / SCADA gateway. The 2025 paper's gateway /
  mediator patterns sit at a different layer; shepard speaks
  persistence + REST. OPC-UA / Modbus / KUKA RSI live in
  `aidocs/50` EXP1's coordinator and in sTC.
- **Not** Type-3 (active) AAS. Passive Type-2 only.
- **Not** a replacement for IDTA / BaSyx. BaSyx remains the
  canonical reference; shepard's value-add is "researchers who
  already have data in shepard get AAS access without operating a
  second system."
- **Not** a guarantee of full IDTA conformance from day 1. §8
  budgets the subset honestly.
- **Not** changing the upstream API surface. All AAS endpoints
  under `/v2/aas/...` per `CLAUDE.md`; `/shepard/api/...`
  untouched.
