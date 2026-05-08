# shepard Ecosystem — Tools, Integration, Improvements

**Scope.** Inventory of the shepard ecosystem beyond this backend +
frontend repo, plus integration recommendations for **shepard-process-wizard**
(SPW) and improvement suggestions for **shepard-timeseries-collector**
(sTC). Includes a recommendation for **bringing process design and
runtime into shepard core** as part of the templates feature
(`aidocs/39`).

**Status.** Concept design. Coordinates with the upstream maintainers
of SPW / sTC are required for §2 / §3 changes.
**Snapshot date.** 2026-05-08.
**Originating items.** User request "regarding compatibility between
process wizard and shepard, what would be the best approach (changes
to process wizard are possible but have to be discussed); integration
of process design and runtime in Shepard would be great" + "make
suggestions for improvement of shepard-timeseries-collector."

## 1. Ecosystem inventory

| Repo | What it is | Language | Talks to shepard via | Notes |
|---|---|---|---|---|
| **`dlr-shepard/shepard`** (this fork: `noheton/shepard`) | The platform — Quarkus backend + Nuxt 3 frontend | Java 21 / Vue 3 | own REST | Upstream is the source of truth; this fork tracks it via `aidocs/34`. |
| **`dlr-shepard/shepard-process-wizard`** | Desktop GUI to model + run processes that produce shepard data | Java + JavaFX | shepard REST | Apache 2.0, ~160 commits. Detailed integration design in §2. |
| **`dlr-shepard/shepard-timeseries-collector`** (sTC) | Headless collector — pulls timeseries from OPC/UA / MQTT / KUKA RSI; pushes to shepard | Java 25+, runs as JAR or Docker | shepard REST (timeseries push) | Apache 2.0, ~339 commits. Improvement plan in §3. |
| **`dlr-shepard/shepard-dataship`** | Curated data publication pipeline (the M-series in `aidocs/16` row X1) | Mixed | shepard REST + RO-Crate exports | Out of repo; coordinated separately. |
| **`dlr-shepard-clients/python`** | Generated Python client | Python | OpenAPI-generated | Currency tied to upstream OpenAPI; this fork's `/v2/` work needs a parallel client crank. |
| **`dlr-shepard-clients/typescript`** | Generated TS client | TypeScript | OpenAPI-generated | Same as above. |
| **`dlr-shepard-clients/java`** | Generated Java client | Java | OpenAPI-generated | Same as above. |
| **`dlr-shepard-frontend`** | Standalone frontend repo (the Nuxt 3 app) | Vue 3 / Vuetify 3 | shepard REST | Per `aidocs/33` review. |
| **(future)** companion notebooks / examples (`examples/seed-showcase/notebooks/`) | Tutorial / showcase notebooks | Python | shepard-client | Lives in this repo as part of the showcase seed (`examples/seed-showcase/`). |

The fork-vs-upstream rule from `CLAUDE.md` applies across every
boundary: **the upstream OpenAPI surface stays frozen** so existing
SPW / sTC / generated-client builds keep working unmodified.
Anything we add lives at `/v2/`.

## 2. shepard-process-wizard — bring process design + runtime into shepard

### 2.1 What SPW does today

SPW is a **JavaFX desktop app** that models a *process* — a sequence
of steps that each produce shepard entities — and then runs the
process, calling shepard's REST API to materialise Collections /
DataObjects / References as the user steps through. Three usage
phases: design (create the process model), persist (save the model
to disk and/or shepard), execute (run the model against a live
shepard instance).

### 2.2 Compatibility today

SPW is built against the upstream OpenAPI; it talks to
`/shepard/api/...`. Per `CLAUDE.md`, that surface stays frozen, so
**SPW keeps working against this fork's `main` without changes**.
This is the immediate compatibility answer: **SPW is compatible
today, no breakage**.

The interesting question is the future direction.

### 2.3 Three integration paths

| Path | Effort | Pro | Con |
|---|---|---|---|
| **(a)** Keep SPW desktop, no shepard-side changes | XS | Zero work | SPW stays a separate distribution; new shepard features (templates, /v2/) require parallel SPW updates; users juggle two installs |
| **(b)** Adapter layer in shepard exposing a stable "process API" SPW talks to | M | SPW evolves independently; shepard owns the data model | Two surfaces to maintain (OpenAPI + adapter) |
| **(c)** **Bring process design + runtime into shepard core, retire SPW desktop** | L | Single source of truth, in-browser UX, leverages templates feature, no SPW redistribution | Significant frontend work; coordination with SPW maintainers required |

**Recommendation: (c).** The user explicitly said "integration of
process design and runtime in shepard would be great" — that's
path (c). And **the templates feature (`aidocs/39`)** already gives
us the building blocks: a template *is* a process step.

### 2.4 Proposed shape — "Process" as a templates extension

A **Process** is a typed sequence of templates. Define a new entity
(`ProcessDefinition`) that lives in `__templates` alongside
DataObject blueprints, with a structure like:

```
ProcessDefinition "Hot-fire test campaign"
├─ step 1: instantiate template "Pre-cool checklist"  (creates DataObject)
├─ step 2: instantiate template "Sensor run"           (creates DataObject)
├─ step 3: instantiate template "Debrief"              (creates LabJournalEntry)
└─ step N: instantiate template "Anomaly investigation" (conditional)
```

Each step references an existing template (`templateAppId`) plus
optional **flow control**: `condition`, `next`, `parallel`. Stored
as a JSON-blob `processSpec` on the `ProcessDefinition` DataObject,
same pattern as `AttributeSpec` (`aidocs/39 §2.4`).

**Runtime** is a new browser-hosted wizard: the `/me`-style frontend
shows the process steps in a stepper; each step's UI is the same
template-instantiation form already designed for T1e (`aidocs/39 §6`).
On step submit, shepard records `(:DataObject)-[:STEP_OF]->(:ProcessRun)`
+ the step's outcome, and advances. Resume from any step on next
visit.

**Endpoints (all `/v2/`):**

| Method + path | Purpose |
|---|---|
| `GET /v2/processes` | List `ProcessDefinition`s in `__templates` |
| `POST /v2/processes/{appId}/runs` | Start a new ProcessRun for a process; returns the first step's instantiation form |
| `POST /v2/process-runs/{appId}/steps/{stepId}/complete` | Submit step output, advance |
| `GET /v2/process-runs/{appId}` | Read the run state (which step, which outputs so far) |

### 2.5 Migration of existing SPW process models

SPW's process models are stored as XML files today. A small importer
(`POST /v2/processes/import`) accepts SPW XML and translates to the
new JSON `processSpec` shape. Best-effort; explicit warnings on
unrepresentable constructs. Keeps existing SPW users productive
during the transition.

### 2.6 Phasing (P-series? PR-series? — call it **PR** for "Process Run")

| ID | Slice | Size | Gate |
|---|---|---|---|
| **PR1a** | `ProcessDefinition` model + JSON `processSpec` blob storage. CRUD `/v2/processes`. **Read-only** for v1. | M | T1b (so processes ride on the template model) |
| **PR1b** | `ProcessRun` runtime: start a run, advance steps, persist progress. UI stepper. | L | PR1a + T1e (template instantiation flow) |
| **PR1c** | SPW XML importer (`POST /v2/processes/import`). | M | PR1a |
| **PR1d** | Conditional / parallel flow control. | M | PR1b |
| **PR1e** | (deferred) Headless / API-driven run mode (cron, CI). | M | PR1b |

### 2.7 Required SPW-side changes

If the maintainers agree, **SPW becomes a process-design assistant
that exports to shepard's process format** (deprecating its own
runtime over time). Specifically:

- SPW gains an "Export to shepard process" action that POSTs the
  current model to `/v2/processes/import`.
- SPW's runtime mode is marked deprecated, with a banner pointing
  at the in-shepard runtime.
- Eventually SPW retires; the design tool moves into shepard's
  frontend as well (at PR1b or PR1d).

This is the conversation to have with SPW maintainers — the
desktop-app investment is real and shouldn't be discarded without
their input.

## 3. shepard-timeseries-collector — improvements

### 3.1 What sTC does today

sTC is a headless Java app (Java 25+, runs as `java -jar` or
Docker/Podman) with a Sources → Bridges → Sinks architecture wired
through an internal event bus. Sources today: OPC/UA, MQTT, KUKA
RSI. Sinks: shepard. Configuration via YAML files in a `config/`
folder.

### 3.2 Top improvement candidates

Ranked by leverage × likely-easy-to-ship:

| # | Improvement | Why | Where in sTC |
|---|---|---|---|
| **i1** | **Use NDJSON ingest (P14, shipped)** instead of per-batch JSON POSTs | shepard's `/timeseriesContainers/{id}/payload` now accepts `application/x-ndjson` (P14, commit `24d4585`). Streaming reduces memory pressure on both sides for high-throughput sources. | shepard sink, batch-flush path |
| **i2** | **REST-managed sink** — implement the configured-but-unimplemented REST interface | sTC's CLI flag mentions a REST control surface that's "not yet implemented." Operators want to tweak source/sink config at runtime without restart. | New REST resource adapter |
| **i3** | **Health endpoint compatible with shepard A1b** | sTC reporting `/healthz` with the same shape shepard backend uses (per `aidocs/16` row A1b) lets a single dashboard cover both. | New health endpoint |
| **i4** | **Stable `containerAppId` lookup post-L2c** | When shepard's L2c lands and timeseries containers are addressed by `appId`, sTC's container-resolution path should switch to `appId`. Lock-step migration. | shepard sink, container provisioning |
| **i5** | **Per-source rate limiting / backpressure** | A flapping OPC/UA source can saturate the event bus. Token-bucket per source, configurable in YAML. | Internal event bus |
| **i6** | **Schema-aware sources** — emit per-channel metadata (unit, range, hysteresis) into shepard `Attribute` writes | shepard's attributes store this today but sTC sends only raw timeseries. Enriches search and the showcase-style channel inventory (per `examples/seed-showcase`). | All sources + sink |
| **i7** | **Java 21 LTS instead of Java 25** | Java 25 is non-LTS as of 2025-09. Many DLR deploy targets pin to LTS. Compile-target downgrade to Java 21 if no Java-25-only features used. | `pom.xml` / `build.gradle` |
| **i8** | **MQTT 5 support** | sTC's MQTT source likely targets MQTT 3.1.1; 5 adds shared subscriptions, message expiry, and reason codes useful for telemetry triage. | MQTT source |
| **i9** | **Modbus + REST sources** | Common in industrial test rigs; would broaden adoption beyond OPC/UA shops. | New source modules |
| **i10** | **OpenLineage emission alongside shepard sink** | Each batch flush emits an OpenLineage event; shepard's lineage view (`aidocs/30`) picks them up automatically. Free provenance. | Sink chain |

### 3.3 Coordinated rollout

i1, i3, i4 are **shepard-side dependent**:

- i1 needs P14 NDJSON ingest (shipped — commit `24d4585`).
- i3 needs A1b health-shape spec (shipped).
- i4 needs L2c (gated on C5 → L2b → L2c).

Recommend i1 + i2 + i3 + i7 as the first sTC-side PR (no
shepard-side dependencies); i4 + i6 + i10 as a second PR after L2c.

The remaining items (i5, i8, i9) are sTC-internal and can ship
whenever the maintainers prioritise.

## 3a. shepard-dataship — publication pipeline

Previously parked under `aidocs/16` X1 ("M1–M9 dataship milestones").
Bringing it back into scope because the **S3 file-storage backend**
from `aidocs/45` (FS1 series) makes dataship's "drop curated data
at a public URL" workflow tractable — you don't need a separate
publication store, the FS1 S3 bucket is the publication store
when you mark a Collection as published.

**What dataship is.** A curated data publication pipeline that
takes a shepard Collection (typically already snapshotted per
`aidocs/41`), exports it as RO-Crate (`aidocs/31`), and publishes
the result to an external archive (Zenodo, B2SHARE, the user's own
git+web setup). M1-M9 are the milestones in the original dataship
roadmap.

**Why FS1 unblocks it.** Three concrete wins:

1. **Direct presigned-URL delivery.** Today shepard backend
   proxies every export ZIP byte. With FS1 + a public-bucket S3
   policy on the published-collections bucket, dataship's "give
   me a stable URL for this RO-Crate" answer is a presigned URL
   with `Expires: never` (or a long TTL). Closes
   `aidocs/31 §O3` simultaneously.
2. **Zenodo / B2SHARE integration via S3 presigned URLs.** Most
   archives accept "fetch from this URL" semantics; presigned URLs
   match that contract.
3. **dataship's own bucket as an artifact store.** dataship needs
   a place to stage manifests, reviewer comments, approval
   records. If shepard's FS1 ships, dataship reuses the same
   bucket-per-deployment pattern instead of a separate Mongo /
   filesystem store.

**Coordination shape.** dataship lives in a separate repo today;
this fork's design contributions land as:

- An updated `aidocs/16` X1 row pointing at the FS1-enabled
  publication shape (no longer fully parked).
- A future `aidocs/49` (or successor number) for the dataship-side
  contract: which shepard `/v2/` endpoints dataship calls, which
  S3 bucket layout it expects, the RO-Crate-with-presigned-URLs
  manifest shape.

**Out of scope here:** the M1–M9 milestone-by-milestone plan
itself. That stays in dataship's own roadmap; this fork only
tracks the **integration contract**.

## 3b. BI / dashboarding integrations — Grafana + Superset + the SQL win

See `aidocs/47 §4.8` for the full design. Headlines:

- **Grafana** — ship a shepard data source plugin for timeseries
  panels; one-click "create dashboard from DataObject" in the
  shepard UI.
- **Superset / Metabase** — once P10 ships
  (`POST /v2/sql/timeseries`, gated on C5 — **C5 shipped**), a
  Superset SQLAlchemy URI is one line of operator config.
- **Tableau / PowerBI** — same SQL-adapter shape; add recipes to
  `docs/deploy.md` as users ask.

The **casual-user north star** in `aidocs/47 §1.0` puts the snap
dashboards (`aidocs/43 §5.8`) first; BI tool integrations are the
power-user follow-on for users who want richer dashboards than
chat-driven one-offs.

## 3c. Reference deployments

Test / demo deployments worth knowing about:

| URL | Path | Notes |
|---|---|---|
| **`https://shepard.nuclide.systems`** | Self-hosted Docker host fronted by Zoraxy (per `docs/deploy-self-hosted-zoraxy.md` §5a — "existing-host dev workflow"). **Dispatcher-deployable** via `.github/workflows/deploy-test-instance.yml` (§5a.10) — every push to `main` builds GHCR images and SSH-deploys. Image tags: `ghcr.io/noheton/shepard-{backend,frontend}:latest` plus per-SHA tags. | The primary test deployment for this fork. Iterates ahead of `main`; useful as a smoke target after each landing. Operator: this fork's maintainer. |

Add new rows here as more reference deployments come online.

## 4. Cross-tool concerns

- **OpenAPI versioning.** The `/v2/` shelf in shepard means a parallel
  generated-client crank for Python / TS / Java. Consumers of the
  upstream `/shepard/api/...` paths keep their existing client. The
  client repos need a CI job that publishes both a "5.x compat" tag
  (upstream OpenAPI) and a "6.x next" tag (this fork's OpenAPI
  including `/v2/`).
- **Auth.** Every ecosystem tool authenticates via shepard API key
  today. Once L5 (shipped) is in operator hands, **all tools should
  honour `validUntil` expiry** and surface a clear 401 to users with
  a "your key has expired" message. sTC and SPW likely silently fail
  today; a small audit of their 401-handling is worth the time.
- **Discovery.** A future `GET /shepard/api/ecosystem` (or `/v2/ecosystem`)
  endpoint that lists known companion tools + recommended versions
  would help operators keep their stack coherent. Out of scope for
  now; mentioned because it'd close the inventory loop.

## 5. Cross-references

- **aidocs:** `aidocs/16` (X1 row — shepard-dataship is parked here),
  `aidocs/22` (admin-CLI overlap with the future REST control surface
  proposed for sTC),
  `aidocs/25` (L2c gates sTC's i4 + the SPW process runtime's stable IDs),
  `aidocs/34` (CONFIG-status row required when sTC's i4 lands and
  flips to `appId`), `aidocs/30` (OpenLineage shape for sTC i10 +
  process-run lineage), `aidocs/39` (templates feature — process
  steps are templates; PR-series builds on T-series).
- **Backlog:** new **PR1** umbrella + PR1a-PR1e in `aidocs/16` for the
  process-runtime work.
