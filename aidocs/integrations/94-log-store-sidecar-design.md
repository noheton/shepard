---
title: Log-store sidecar — structured logging substrate for Shepard + plugins
stage: audited-by-personas
last-stage-change: 2026-05-23
audience: contributors, plugin authors, ops, reviewers
backlog-id: LOGSTORE1
audit-record: aidocs/agent-findings/persona-audit-logstore-2026-05-23.md
audit-status: ACCEPT-WITH-CHANGES (4 personas — API Scrutinizer + Reluctant Senior + Strategy Aligner + Digital Native); 20 change requests filed; closure required before feedback-implemented
supersedes: ad-hoc SD-runlog pattern in `examples/mffd-showcase/scripts/mffd-import-v15.py` §3096
companions:
  - feedback_shepard_measures_itself.md
  - feedback_plugins_declare_sidecars.md
  - aidocs/16-dispatcher-backlog.md §LOGSTORE1
  - aidocs/integrations/93-mffd-import-v15-requirements.md
---

# 94 — Log-store-with-shape sidecar design

> **TL;DR.** Adopt **VictoriaLogs** (Apache-2.0, LogsQL, per-field indexing,
> S3 object-store tiering on roadmap, ~30 MB binary) as Shepard's primary
> log substrate. Plugins emit through **Vector** (MPL-2.0, structured
> routing + VRL pre-validation) into a `shepard-plugin-logs` sidecar.
> Event shapes are SHACL-declared per `feedback_shacl_single_source_of_truth.md`;
> the plugin manifest references the shape IRIs (per `feedback_plugins_declare_sidecars.md`).
> Loki / Tempo / Quickwit / Parseable are all **AGPL-licensed in 2026** —
> dependency-review gate (`CLAUDE.md §"keep the security gates green"`)
> rules them out for in-process embedding; sidecar isolation is defensible
> but VictoriaLogs achieves the same outcome with a kinder licence and
> 87% less memory (TrueFoundry 2025 benchmark, cited §3).
>
> **Second choice:** Grafana Loki (AGPLv3, sidecar-isolated) — LogQL is
> the literacy default in ops circles, S3-on-Garage works (with a known
> issue thread to follow). Switch on if VictoriaLogs S3-native lands late
> on the roadmap.
>
> **Top risk:** plugin-author cardinality discipline — even VictoriaLogs'
> per-field index degrades when authors label every event with a free
> `subject` IRI. Mitigation: SHACL `sh:in`/`sh:pattern` on labels at
> the Vector-VRL pre-validation step; admin alert when a plugin breaches
> a cardinality budget.

---

## §1 The problem — what works, what doesn't with the SD-runlog pattern

**Current state.** The MFFD v15 import script
(`examples/mffd-showcase/scripts/mffd-import-v15.py:3096-3220`) ships an
embedded `Telemetry` class that flushes:

- **Metrics** → a `TimeseriesContainer` (one row per counter/gauge,
  every heartbeat). Plotted by Shepard's existing TS chart UI per
  `OBS-MFFD1` / `feedback_shepard_measures_itself.md`.
- **Events** → a `StructuredDataContainer` (id `593753` =
  `mffd-import-runlog`). Each flush appends one JSON envelope
  carrying a batch of `{ts, level, event, ...}` rows.

**What works.**

- Zero new infrastructure — the data lives inside Shepard, plotted by
  the same UI a researcher uses for sensor data. The recursive principle
  works: the import is observable through the system it is producing into.
- Provenance carry-over — the SD container has an `:Activity` lineage
  edge to the collection it observes (`PROV1a` `ProvenanceCaptureFilter`).
  Querying "who watched what when" is a single Cypher pattern.
- Survives backend reboots — the dest Shepard *is* the substrate; no
  parallel telemetry stack to fall over.
- Each event carries the import version (`v15.6`) → the runlog already
  doubles as a release-history audit trail.

**What doesn't.**

1. **One emitter, one container.** Hard-coded container id `593753`.
   Adding a second emitter (e.g. the `shepard-plugin-importer`
   library-of-importers, the AI plugin's structured-data activity log,
   the Matrix notification plugin's outbound dispatch trail) means
   either fan-in into the same container (loses provenance separability)
   or a thicket of per-plugin container ids. Both are wrong.
2. **No retention policy.** SD container rows are forever unless
   manually pruned. The MFFD v15.6 run alone has produced ~4 rows so far
   (small), but a 7-day full-stack debug session would produce tens of
   thousands. Mongo grows linearly; no rolling-window discard.
3. **No timestamp range index.** Querying "all events between 14:05 and
   14:07Z" requires a full container scan + client-side JSON parse
   per row. The SD-container search filter takes a substring against
   the serialised JSON; it is not a temporal index.
4. **No event-shape contract.** The `Telemetry` class hand-rolls the
   envelope `{ts, host, version, mode, events: [...]}`. A second
   emitter would invent a second envelope; consumers must branch.
5. **No live-tail.** Researcher in the Vuetify UI must refresh the
   container view to see new events. No SSE / WebSocket subscription.
6. **No full-text search.** The runlog is JSON-in-Mongo; querying
   `event="process_started_fresh"` is a `$regex` against the entire
   blob. With ~10 emitters it becomes unusable.
7. **Audit conflation.** PROV1a's `:Activity` audit (admin endpoint
   mutations) and the import-step audit (every TR-batch start/end) land
   in the same substrate, blurring the "what did a human do" vs.
   "what did the importer do" line. Two consumers with different
   retention requirements (forever vs. 30 days) hit the same store.

**Persona lens read.** From the **API Scrutinizer** lens (CLAUDE.md
Role 3), the SD container is a *leaky abstraction* — the storage
substrate (Mongo collection) is the API. From the **Reluctant Senior
Researcher** lens (Role 9), the lack of timestamp-range query is the
killer — every "what happened around 14:05" question reverts to
`grep` on a downloaded dump, which is exactly the folder-and-Excel
workflow Shepard is supposed to improve on. From the **Strategy
Aligner** lens (Role 6), shipping one more bespoke substrate per
plugin is the kind of accretion that kills adoption velocity for
future plugin authors.

---

## §2 Tension with `feedback_shepard_measures_itself.md`

The recursive-observability principle says: *when Shepard has a payload
kind that already solves the measurement problem, use Shepard's substrate
inside Shepard.* Does this extend to log events?

**The principle's own carve-out applies.** That memory explicitly lists
"unbounded cardinality" and "sub-second resolution" as default-no:

> The default-no (still belongs in Prometheus):
> - Sub-second metrics needed for an alert
> - Anything where the cardinality is unbounded or where a 5-minute
>   sample is too sparse

Free-text log events are precisely the unbounded-cardinality case. A
single import emits 50–500 distinct `event_name` values; a single
`subject` field references thousands of DataObject appIds; a single
`payload` may carry stack traces with high-entropy random bytes. The
TS substrate is built for 11 named channels at 5-minute resolution
(OBS-MFFD1's exact shape) — pushing a thousand `event_name` values
through it would crater both Timescale ingest and the chart UI.

**However — the principle's spirit still applies to one slice.**
Metrics-shaped derivatives of log events (e.g. *count of `error`-level
events per minute*, *p95 of `step_duration_ms` per process step*) DO
belong in the TS substrate. The log store is the source of truth for
events; the metric pipeline derives counters from those events and
writes them into TimescaleDB through OBS-MFFD1's pattern. **The two
substrates are complementary, not competing.**

**From the Strategy Aligner lens:** the worry is "no parallel stack
reflex" — Shepard becoming yet another platform with five backend
substrates an operator has to monitor. Answer: the log store IS
*in-stack*, declared as a plugin-managed sidecar (per
`feedback_plugins_declare_sidecars.md`), assembled into compose by the
deploy tooling, surfaced in the Vuetify UI (so the operator never
leaves Shepard). It is not a parallel stack — it is one more
plugin-declared service alongside Garage, Postgres, Neo4j, etc.

**From the Reluctant Senior lens:** would a senior researcher accept
"go to the log view to see what happened during your import"? Yes —
provided the log view is *inside* Shepard (Vuetify), shows the events
*on the entity you care about* (per-DataObject activity tab, §7), and
can be exported to JSON / CSV in three clicks. Not yes if they have
to learn LogQL syntax. The §7 UI surface answers this directly:
search box is free-text by default; LogsQL is the power-user pass-through.

**Decision.** Logs deserve a separate substrate. The recursive principle
extends *one layer further*: the substrate lives inside Shepard's
deploy unit (compose-declared sidecar), is queried through Shepard's
REST + MCP surface, is rendered in Shepard's Vuetify UI. The
recursive principle was never "use only the four core payload kinds"
— it was "don't stand up an external observability stack that the
researcher has to learn." VictoriaLogs-in-compose satisfies the spirit.

---

## §3 Reuse survey (MANDATORY per `feedback_reuse_before_reimplement.md`)

Per the standing rule: this is a substrate decision and the survey is
the first design step.

| Candidate | Licence | Lang / footprint | S3-native? | Query lang | Activity | Decision | One-line: how it would emit MFFD `process_started_fresh` |
|---|---|---|---|---|---|---|---|
| **Grafana Loki** | AGPLv3 (since 2021) [G1] | Go / 200 MB+ | Yes — S3-compatible chunk store (Garage works with known issue) [G6] | LogQL | Very active, Grafana Labs | **ADOPT (sidecar, fallback)** | `curl -d '{"streams":[{"stream":{"plugin":"importer","event":"process_started_fresh"},"values":[["<ns>","<json>"]]}]}' loki:3100/loki/api/v1/push` |
| **Vector** | MPL-2.0 [V1] | Rust / ~30 MB | Router, not store | VRL (transforms) | Very active, Datadog stewardship | **ADOPT (router)** | Plugin writes JSON to `unix:///var/run/shepard/logs.sock`; Vector parses with VRL, ships to VictoriaLogs |
| **OpenSearch** | Apache-2.0 (Linux Foundation since 2024) [O1] | Java / 8-16 GB JVM minimum [O2] | Indexing tier on disk; snapshot to S3 | Query DSL / SQL plugin | Very active, 400+ Foundation members | **REJECT (heavy)** | `POST /shepard-logs/_doc { "@timestamp": "...", "event": "process_started_fresh", ... }` |
| **Grafana Tempo** | AGPLv3 (same relicense as Loki) [G1] | Go / ~200 MB | Yes — S3 chunk store | TraceQL (traces!) | Active | **REJECT (wrong scope)** | Tempo is distributed traces, not logs; mentioned for completeness |
| **OpenTelemetry Collector** | Apache-2.0 [OT1] | Go / ~80 MB | Router, not store | (None — config-driven) | Very active, CNCF | **ADOPT (alternative router)** | Quarkus 3.16+ OTel logs exporter → OTLP receiver → exporter → VictoriaLogs OTLP endpoint |
| **Quickwit** | AGPLv3 + commercial dual licence [Q1] | Rust / ~80 MB | Yes — S3-native (Parquet on object store) | SQL-like + Lucene | Active until 2024 acqui-hire by Datadog; uncertain since | **REJECT (uncertain)** | `POST /api/v1/<index>/ingest { "event": "...", ... }` |
| **Parseable** | AGPLv3 [P1] | Rust / single binary | Yes — Parquet on S3, S3-native | SQL | Active, commercial company | **REJECT (AGPL + young)** | `POST /api/v1/logstream/shepard { "event": "...", ... }` |
| **VictoriaLogs** | Apache-2.0 [VL1] | Go / ~30 MB | On roadmap; today single local dir; backups via rclone to S3 [VL3] | LogsQL (LogQL-superset) | Very active, VictoriaMetrics | **ADOPT (primary)** | `curl -d '{"_msg":"process_started_fresh","plugin":"importer","level":"info","run":"v15.6.5"}' victorialogs:9428/insert/jsonline` |

**The licence gate (per CLAUDE.md §"keep the security gates green" #6).**
Six of the eight candidates are AGPLv3. The dependency-review workflow
bans `GPL / AGPL / SSPL families` in
`.github/dependency-review-config.yml`. **The gate triggers only when
the licence enters Shepard's own dependency tree.** Sidecar isolation
— a separate container talking HTTP — does NOT trigger the gate; the
log store is not a transitive dependency of `shepard-backend`. But:

- AGPLv3's network-distribution clause (§13) requires the source of
  the AGPL-licensed program be offered to "all users interacting with
  it remotely over a computer network." A Shepard operator who runs
  the Loki container is the distributor of Loki; their obligation
  is to make Loki's source available to their users. Since Grafana
  Labs publishes that source publicly, the operator's obligation is
  trivially satisfied **as long as they don't modify Loki**. If a
  DLR institute patches Loki, they must publish the patch.
- This is operator-survivable. But "no AGPL by default" is a kinder
  posture for DLR institutes that want to ship Shepard internally
  without their legal-team thinking about it.

**The S3-Garage angle (per S-06 synergy doc).** Shepard's planned
object store is Garage (ADR-0024). Five candidates can write to Garage:
Loki ✓ (with known integration friction [G6]), Quickwit ✓, Parseable ✓,
OpenSearch (snapshots only), VictoriaLogs (on roadmap, today rclone-backup
only [VL3]). The "VictoriaLogs S3 not yet native" is the strongest
counter to choosing it. Mitigation: VictoriaLogs's local single-dir
storage is enough for a 6-12 month transition; when S3-native lands
(2026 roadmap [VL3]) the migration is a backup-and-restore.

**Why not OpenSearch.** Despite the Apache-2.0 licence and the LF
Foundation governance (clear winners on the policy axis [O1]), the
JVM heap requirement is 8-16 GB minimum for production [O2]. Shepard's
backend already runs at 2 GB heap; adding a 16 GB-heap OpenSearch
to the deploy unit is a 5× memory budget increase. From the Reluctant
Senior lens: "I have to add 16 GB of RAM to my server to see import
logs?" — rejected. From the Strategy Aligner lens: every Shepard
deploy now needs a beefier box; adoption friction multiplies. Defer
to when Shepard scales to genuinely-large-log volumes (the
"Trace3D voxel stream" case in `aidocs/integrations/98`).

**Why not Loki as primary.** Two reasons:

1. **Cardinality footgun.** Loki indexes labels, not log content. A
   plugin author who labels every event with `subject=<DataObject
   appId>` creates O(N_DataObjects) streams. Loki defaults to
   `max_streams_per_user=10000`; the MFFD-Dropbox collection alone
   has 5012 + 3371 = 8383 DataObjects. *One* plugin running with
   subject-as-label exhausts the budget. Mitigations exist (move
   subject to `structured_metadata`, the 2024 Loki feature [L1]) but
   require plugin-author discipline that VictoriaLogs's per-field
   indexing makes unnecessary.
2. **Licence.** AGPL is operator-survivable (above) but second-choice.

**Why VictoriaLogs primary.**

- **Licence.** Apache-2.0 [VL1] — no dependency-review review,
  no AGPL chatter in operator legal teams, can be linked into
  Shepard's own JAR as a client library if ever needed (the Go-based
  VL has no Java SDK today, but the JSON insert API is trivial).
- **Per-field index.** Every field is indexable; no Loki-style
  cardinality lottery. The TrueFoundry 2025 benchmark [VL2] reports
  3× ingest, 72% less CPU, 87% less memory vs. Loki on identical
  workloads.
- **LogsQL is LogQL-superset.** Operators who know LogQL transfer
  with hours of effort; the docs include a LogQL-to-LogsQL
  translation table [VL4].
- **Single binary, ~30 MB.** Smaller than the Quarkus backend's
  JAR. The Reluctant Senior lens: "what new binary do I have to
  watch?" — one, 30 MB, with `--help` output that fits on a screen.
- **No JVM.** Heap-tuning isn't an operator burden.

**The router (Vector or OTel Collector).** The Vector role is
*router*, not store — same role across all candidates. Vector wins on
ergonomics: VRL is the closest thing to "regex + JSON + a few helpers"
that plugin authors can pick up in a morning [V2]. OTel Collector
is more standards-aligned (OTLP is W3C-adjacent) but configuration
is YAML-only, no script language. **Pick Vector as default**, OTel
Collector as the alternative for Quarkus-native emission (per Quarkus
3.16+ `quarkus.otel.logs.enabled=true` [OT2]).

**Counter-argument from the Strategy Aligner.** "Why not just use
the OpenTelemetry stack everywhere — it's the standard, future-proof,
vendor-neutral?" Two-part answer: (a) OTel logs as a standard is
*new* (Quarkus marks it experimental as of 3.16.0 [OT2]); production
maturity is lower than VictoriaLogs's settled JSONLine API; (b)
nothing in this design *blocks* future migration to OTLP — Vector
has an OTLP source and OTLP sink, VictoriaLogs has an OTLP receiver
[VL4], and OTel Collector can sit in front of VictoriaLogs as
drop-in for Vector. The decision is reversible; the work isn't
wasted.

---

## §4 The shape-driven log dimension

Shepard is shape-first (per `feedback_ontology_first.md` and
`feedback_shacl_single_source_of_truth.md`). Logs are no exception.
Every log emitter declares a SHACL **EventShape** in its plugin
manifest; the shape lives alongside the plugin's other SHACL shapes
in the `shepard-plugin-<id>/src/main/resources/shapes/` directory and
is loaded into Shepard's semantic substrate at plugin registration.

**Canonical EventShape (the contract every emitter MUST conform to).**

```turtle
@prefix sh:    <http://www.w3.org/ns/shacl#> .
@prefix shp:   <https://shepard.dlr.de/ns/shapes/> .
@prefix shev:  <https://shepard.dlr.de/ns/log-event/> .
@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .
@prefix prov:  <http://www.w3.org/ns/prov#> .

shp:EventShape a sh:NodeShape ;
    sh:targetClass shev:Event ;
    sh:property [
        sh:path shev:level ;
        sh:in ( "trace" "debug" "info" "warn" "error" "fatal" ) ;
        sh:minCount 1 ; sh:maxCount 1 ;
    ] ;
    sh:property [
        sh:path shev:eventName ;
        # plugin-declared controlled vocabulary; sub-shape overrides
        sh:pattern "^[a-z][a-z0-9_]{2,63}$" ;
        sh:minCount 1 ; sh:maxCount 1 ;
    ] ;
    sh:property [
        sh:path shev:plugin ;
        sh:datatype xsd:string ;
        sh:minCount 1 ; sh:maxCount 1 ;
    ] ;
    sh:property [
        sh:path shev:actor ;
        # "system:<id>" | "plugin:<id>" | "user:<uuid>"
        sh:pattern "^(system|plugin|user):[A-Za-z0-9_-]{1,128}$" ;
    ] ;
    sh:property [
        sh:path shev:subject ;
        sh:nodeKind sh:IRI ;
        # shepard:<kind>:<appId> form; resolvable via PIDINST
    ] ;
    sh:property [
        sh:path shev:timestamp ;
        sh:datatype xsd:dateTime ;
        sh:minCount 1 ; sh:maxCount 1 ;
    ] ;
    sh:property [
        sh:path shev:durationMs ;
        sh:datatype xsd:nonNegativeInteger ;
    ] ;
    sh:property [
        sh:path shev:payload ;
        # plugin-declared; this base shape says nothing about the inner
        # schema. Plugin sub-shapes constrain payload per event_name.
    ] ;
    sh:property [
        sh:path shev:retentionClass ;
        sh:in ( "audit" "ops-30d" "debug-7d" "ephemeral-1d" ) ;
        sh:minCount 1 ; sh:maxCount 1 ;
    ] ;
    sh:property [
        sh:path prov:wasGeneratedBy ;
        sh:nodeKind sh:IRI ;
        # the :Activity that emitted this event — closes the PROV-O loop
    ] .
```

**Plugin sub-shapes** (example for the importer plugin).

```turtle
shp:ImporterProcessStartedShape a sh:NodeShape ;
    sh:targetClass shev:Event ;
    sh:and (
        shp:EventShape  # inherits the base contract
        [ sh:property [
            sh:path shev:eventName ;
            sh:hasValue "process_started_fresh" ;
        ] ;
          sh:property [
            sh:path shev:payload ;
            sh:node shp:ImporterProcessStartedPayloadShape ;
          ]
        ]
    ) .

shp:ImporterProcessStartedPayloadShape a sh:NodeShape ;
    sh:property [ sh:path shev:run ;        sh:pattern "^v[0-9]+\\.[0-9]+(\\.[0-9]+)?$" ] ;
    sh:property [ sh:path shev:mode ;       sh:in ( "fresh" "resume" "selfupdate" ) ] ;
    sh:property [ sh:path shev:dest ;       sh:nodeKind sh:IRI ] ;
    sh:property [ sh:path shev:collectionId ; sh:datatype xsd:positiveInteger ] .
```

**Where validation happens.**

The dispatch path is **plugin → Vector (VRL pre-validation) →
VictoriaLogs (storage)**. Validation is layered:

1. **Plugin code** — Java emitter SDK exposes typed `EventBuilder`
   classes generated from the SHACL shapes (analogous to JSON-LD
   `@type` typed constructors). Compile-time wrong-event-name and
   wrong-field-type fail at `mvn package`.
2. **Vector VRL** — pre-flight syntactic check: required fields
   present, enums respected, IRI patterns match. VRL [V2] handles
   the fast checks; SHACL-on-RDF is too heavy for the hot path.
   Failures: drop to a `dead-letter` VictoriaLogs stream tagged
   `shape_violation` (so plugin authors can see their bugs without
   the event being silently lost).
3. **Periodic SHACL audit** (admin endpoint) — sample N% of events
   per plugin per day, materialise as RDF, run full SHACL validation
   via the existing `n10s.validation.shacl.validate` procedure. A
   violation report appears in the admin UI per plugin per day.

**VRL pre-validation pattern** (Vector config):

```toml
[transforms.shape_check]
type = "remap"
inputs = ["unix_sock"]
source = '''
. = parse_json!(string!(.message))

# Required field presence
if !exists(.level) || !exists(.event_name) || !exists(.plugin) {
  abort
}

# Enum
if !includes(["trace","debug","info","warn","error","fatal"], .level) {
  .shape_violation = "level not in enum"
  .level = "warn"
}

# Subject IRI shape
if exists(.subject) && !match(string!(.subject), r'^shepard:[a-z]+:[0-9a-f-]{36}$') {
  .shape_violation = "subject not a shepard IRI"
}

# Retention class
if !exists(.retention_class) {
  .retention_class = "ops-30d"  # default
}

# Materialise the OpenTelemetry-compat timestamp
.timestamp = to_timestamp!(.timestamp, format: "%Y-%m-%dT%H:%M:%S%.fZ")
'''

[sinks.victoria_logs]
type = "http"
inputs = ["shape_check"]
uri = "http://victorialogs:9428/insert/jsonline"
method = "post"
encoding.codec = "json"
```

---

## §5 Per-plugin emission contract

Per `feedback_plugins_declare_sidecars.md`, the plugin manifest is the
declaration point. Two extensions to the existing
`shepard.plugin.PluginManifest` shape:

```yaml
# plugins/shepard-plugin-importer/plugin.yaml
id: shepard-plugin-importer
version: 1.0.0

logs:
  emits:
    - shape: https://shepard.dlr.de/ns/shapes/ImporterProcessStartedShape
      retention: audit              # keep forever; lineage-critical
      cardinality_budget: 1000      # per day; alarm if exceeded
    - shape: https://shepard.dlr.de/ns/shapes/ImporterStepCompletedShape
      retention: ops-30d
      cardinality_budget: 50000
    - shape: https://shepard.dlr.de/ns/shapes/ImporterDebugTraceShape
      retention: debug-7d
      cardinality_budget: 500000
  endpoint: log-store-default         # references the sidecar declared below

sidecars:
  - id: log-store-default
    provider: shepard-plugin-logs    # the cluster-wide log store
    optional: false                  # importer can't run without it
```

The cluster-wide `shepard-plugin-logs` plugin declares the sidecar
itself:

```yaml
# plugins/shepard-plugin-logs/plugin.yaml
id: shepard-plugin-logs
version: 1.0.0
description: Cluster-wide log substrate (VictoriaLogs + Vector router)

sidecars:
  - id: vector
    image: timberio/vector:0.42.0-alpine
    ports: ["8686:8686"]
    volumes:
      - "/opt/shepard/log-store/vector.toml:/etc/vector/vector.toml:ro"
      - "/var/run/shepard:/var/run/shepard"        # unix socket for emitters
    networks: ["shepard"]
    healthcheck:
      test: ["CMD", "vector", "validate", "--no-environment"]
      interval: 30s
  - id: victorialogs
    image: victoriametrics/victoria-logs:v1.0.0
    ports: ["9428:9428"]
    volumes:
      - "/opt/shepard/log-store/data:/victoria-logs-data"
    environment:
      - "VL_RETENTION_PERIOD=30d"        # per-tenant override below
    networks: ["shepard"]
    healthcheck:
      test: ["CMD", "wget", "-q", "-O-", "http://localhost:9428/health"]
      interval: 30s

provides:
  - capability: LogStore
    api:
      emit: unix:///var/run/shepard/logs.sock
      query: http://victorialogs:9428/select/logsql/query
      stream: http://victorialogs:9428/select/logsql/tail
```

**Retention semantics.**

| Class | TTL | Backing | Use case |
|---|---|---|---|
| `audit` | infinite | VictoriaLogs `audit` tenant + nightly export to Garage S3 (immutable) | provenance-grade events the lineage depends on (import start/end, security mutations) |
| `ops-30d` | 30 days | VictoriaLogs `ops` tenant | step-completed, status-changed, plugin-emitted operational events |
| `debug-7d` | 7 days | VictoriaLogs `debug` tenant | verbose traces, payload bodies, anything an admin wants for a week |
| `ephemeral-1d` | 24 hours | VictoriaLogs `ephemeral` tenant | live-tail subscription noise, healthcheck rolls |

VictoriaLogs multi-tenancy is via the `_tenant_id` header; the four
classes map 1:1.

---

## §6 The recommended substrate — argued

**Pick: VictoriaLogs as the primary log store, Vector as the
router, SHACL `EventShape` as the contract.**

Argument, in order of weight:

1. **Licence + governance.** Apache-2.0 [VL1], no dependency-review
   review, no operator legal-team conversation. Loki, Tempo,
   Quickwit, Parseable are all AGPLv3 [G1, Q1, P1]; sidecar isolation
   defeats the runtime-link gate but adds the operator-redistribution
   obligation. The kinder posture wins.
2. **Cardinality model.** VictoriaLogs indexes per-field with columnar
   storage [VL5]; Loki's label cardinality is the documented
   footgun of the OSS log space [L1, L2, L3]. Plugin-author discipline
   is the wrong place to put the safety net; the substrate should
   absorb it. VictoriaLogs benchmark [VL2]: 3× ingest, 72% less CPU,
   87% less RAM than Loki on identical workloads.
3. **Operator footprint.** ~30 MB Go binary, no JVM, no shard-tuning,
   no replication-factor knob. Compare OpenSearch's 8-16 GB JVM
   minimum [O2]. The Reluctant Senior lens: this is a tool I can run
   on the same VM as the rest of the stack without re-sizing.
4. **Stack alignment.** S3 backend on the roadmap [VL3] — when it
   lands it will write to the same Garage instance the file plugin
   uses (closing the S-06 synergy). Today's local-dir storage is
   sufficient for 6-12 months of MFFD-scale operation; migration
   path is `vlconverter` + restore.
5. **Quarkus integration.** No native Java SDK, but the JSONLine
   insert API is one HTTP POST per event; the Java emitter SDK
   wraps it (§8). Alternative path via OpenTelemetry: Quarkus
   3.16+ has `quarkus.otel.logs.enabled=true` [OT2]; OTel Collector
   ships logs to VictoriaLogs's OTLP receiver. The integration is
   free in the second case, lightweight in the first.
6. **Operator story.** Admin opens Shepard's Vuetify UI → log view
   (§7) → reads recent events, filters by plugin, exports CSV. No
   leaving Shepard, no Grafana dashboard to maintain, no separate
   credentials.

**What would change my mind.**

- If VictoriaLogs S3-native slips beyond 12 months: switch to Loki
  (the AGPL sidecar isolation is operator-survivable; LogQL is
  better-known; Loki's S3-on-Garage path is documented [G6] even
  if it has a known issue thread).
- If a DLR-internal SAIA-equivalent log service emerges: adopt the
  hosted service and remove the sidecar entirely (the abstraction
  through `shepard-plugin-logs` makes this a manifest swap).
- If Quickwit's Datadog acquisition resolves into a clear OSS path
  (with the Parquet-on-S3 model intact and Apache-2.0 licence
  reaffirmed): re-evaluate. Quickwit's Parquet-on-S3 is structurally
  the strongest design in this space.

**Second choice: Grafana Loki.**

LogQL is the literacy default in ops circles; Garage-S3 backend
works (with a known integration issue [G6] to follow). AGPL is
operator-survivable. The cardinality footgun is real but mitigable
with `structured_metadata` (Loki 3.0+, 2024) [L1]. Switch on if
VictoriaLogs S3-native lands late.

---

## §7 UI surface — Vuetify, per `feedback_ui_api_parity.md`

Five entry points; all reuse existing Vuetify 3 components per
`feedback_reuse_before_reimplement.md`.

### 7.1 Admin-facing log view — `/v2/admin/logs`

Files: `frontend/pages/admin/logs/index.vue` (new),
`frontend/components/admin/LogTable.vue` (new),
`frontend/components/admin/LogFilterBar.vue` (new).

| Component | Role |
|---|---|
| `v-data-table-virtual` | The log table itself — virtualised for 100k+ rows; no client-side render of all events |
| `v-text-field` + `v-menu` | Search box: free-text by default; menu toggles to LogsQL pass-through |
| `v-chip-group` (multi-select) | Plugin filter — chips per registered emitter |
| `v-select` | Level filter (trace/debug/info/warn/error/fatal) |
| `v-date-time-picker` (Vuetify Labs) | Time-range picker; defaults to "last hour" |
| `v-progress-linear` (indeterminate) | Live-tail indicator when SSE is active |
| `v-btn` + `v-icon` (mdi-pause / mdi-play) | Pause/resume live-tail |
| `v-btn` + `v-icon` (mdi-download) | Export visible rows to CSV / JSON |
| `v-snackbar` | Shape-violation alerts (admin gets notified when a plugin emits an invalid event) |

The data-table is virtual scrolling — same pattern as
`CollectionDataObjectsPanel.vue` (per UX auditor finding) but on log
events. Default page size 100, infinite scroll on tail.

### 7.2 Per-DataObject activity tab

Files: `frontend/components/context/DataObjectActivityTab.vue` (new
sibling of `DataObjectProvGraph.vue`).

| Component | Role |
|---|---|
| `v-timeline` (Vuetify 3 timeline) | One node per event, ordered by `timestamp` desc |
| `v-timeline-item` | Per-event card; level icon, event_name, plugin, payload pretty-print on expand |
| `v-card` (inside item) | Payload viewer with `<pre>` for JSON |

Query: `GET /v2/logs/events?subject=shepard:dataobject:<appId>` (§8).

### 7.3 Per-Collection ops dashboard

Files: `frontend/components/context/CollectionLogsPanel.vue` (new tab
on `CollectionSidebar.vue`).

| Component | Role |
|---|---|
| `v-sparkline` | Events-per-minute over the last hour, per level |
| `v-card` × N | Top-5 most-recent error events |
| `v-btn` (text) | "Open full log view" → links to `/admin/logs?collection=<id>` |

Reuses the OBS-MFFD1 channel-derivative pattern: the sparklines come
from VictoriaLogs's `_stream` aggregation, not from the log events
directly.

### 7.4 Live-tail (SSE)

Implementation: `EventSource(/v2/logs/stream?...)` consumed in
`useLogStream.ts` composable; pushed to the same `v-data-table-virtual`
as 7.1 with `prepend-rows` semantics. Existing composable shape per
`useTimeseriesLiveTail.ts` (already shipped for TS charts).

### 7.5 LogsQL search pass-through (power user)

A toggle in the search-box `v-menu` switches from free-text to LogsQL.
Free-text translates to LogsQL via a simple wrapper:

```
foo bar baz   →   _msg:foo OR _msg:bar OR _msg:baz
```

LogsQL pass-through routes directly to
`POST /v2/logs/query { "logsql": "<user_input>" }`.

---

## §8 REST + MCP surface

### 8.1 REST endpoints (under `/v2/`)

```
POST   /v2/logs/events                  # plugin emit (one event)
POST   /v2/logs/events:batch            # plugin emit (≤1000 events)
GET    /v2/logs/events                  # query by structured filter
GET    /v2/logs/query                   # LogsQL pass-through (power user)
GET    /v2/logs/stream                  # SSE — live tail
GET    /v2/logs/shapes                  # list registered EventShapes
GET    /v2/logs/shapes/{id}             # get one shape (RDF turtle)
GET    /v2/admin/logs/violations        # SHACL violation report (last 24h)
GET    /v2/admin/logs/retention         # tenant-class retention windows
PATCH  /v2/admin/logs/retention         # admin-configurable knob (RFC 7396)
```

### 8.2 Example payloads

**Emit (single):**

```json
POST /v2/logs/events
Content-Type: application/json
Authorization: Bearer <plugin-token>

{
  "level": "info",
  "event_name": "process_started_fresh",
  "plugin": "shepard-plugin-importer",
  "actor": "plugin:shepard-plugin-importer",
  "subject": "shepard:collection:019e4e56-ca63-76f3-9bf0-6681f7fe6d56",
  "timestamp": "2026-05-23T23:07:24.124Z",
  "retention_class": "audit",
  "payload": {
    "run": "v15.6.5",
    "mode": "fresh",
    "dest": "https://shepard-api.nuclide.systems",
    "collectionId": 515365
  }
}
```

**Query by filter:**

```
GET /v2/logs/events?subject=shepard:collection:019e4e56-...&level=error&since=2026-05-23T20:00:00Z&limit=100
```

Response:

```json
{
  "events": [ /* matching events, newest first */ ],
  "next_cursor": "eyJ0cyI6IjIw...",
  "total_estimate": 312
}
```

**LogsQL pass-through:**

```json
POST /v2/logs/query
{
  "logsql": "plugin:\"shepard-plugin-importer\" level:error _time:[now-1h, now]",
  "limit": 500
}
```

**Stream (SSE):**

```
GET /v2/logs/stream?since=2026-05-23T23:00:00Z&plugin=shepard-plugin-importer

event: log
data: {"timestamp":"2026-05-23T23:07:24.124Z","level":"info",...}

event: log
data: {"timestamp":"2026-05-23T23:07:25.005Z","level":"info",...}
```

### 8.3 MCP surface — for Claude / agentic clients

Per `aidocs/platform/30-mcp-plugin-design.md` and per `project_mcp_path`.

New tools on the shepard MCP server:

```
mcp__shepard__search_logs
  Args:
    subject?: string         # shepard:<kind>:<appId>
    plugin?: string          # plugin id
    level?: string           # trace|debug|info|warn|error|fatal
    since?: string           # RFC 3339
    until?: string
    event_name?: string
    free_text?: string       # passed to LogsQL _msg:<text>
    limit?: int (≤ 1000)
  Returns: array of events

mcp__shepard__tail_logs
  Args:
    subject?, plugin?, level?, event_name?
    duration_s: int          # how long to tail (≤ 300)
  Returns: streaming events via MCP's progress-notification channel

mcp__shepard__list_log_shapes
  Args: none
  Returns: array of {id, plugin, event_name, sh:targetClass}

mcp__shepard__violations_today
  Args: none
  Returns: array of {plugin, shape, count, sample_event_id}
```

**Why this set.** The Claude / Digital Native lens (Role 10):
"can I write `mcp__shepard__search_logs(subject='shepard:collection:...',
level='error')` and get a usable list in five lines?" — yes. The
agent doesn't need to learn LogsQL; it queries by structured args.
The `free_text` slot is the LogsQL escape hatch when an agent needs
full-text.

---

## §9 Migration plan (the mffd-import-runlog → log-store transition)

**Current state.** SD container 593753 has ~4 envelope rows from the
v15.4–v15.6.5 runs. Each envelope contains a batch of events.

**Decision: deprecate the SD container, don't migrate.** Reasoning:

- Volume is trivial (4 envelopes, ~50 events total).
- The events have low half-life — debugging is in the present, not the
  audit trail. The audit-grade events (which import-version ran, what
  PROV-O Activity wrapped it) are already captured by PROV1a via
  `:Activity` nodes, independent of the runlog.
- Migration cost would be a one-off script (`scripts/migrate-runlog-to-logstore.py`)
  that flatten-unpacks each envelope into individual log events; the
  effort is greater than the value for 50 events.

**Migration steps for v15.x in-flight.**

1. **Phase 1 (now):** v15.6.5 keeps writing to SD 593753. No
   behaviour change.
2. **Phase 2 (logstore lands):** v15.7 ships with dual-write — the
   `Telemetry.event()` method writes to both the SD container AND
   the log-store sidecar via `POST /v2/logs/events:batch`. The
   `Telemetry` class itself absorbs the routing.
3. **Phase 3 (one week of dual-write):** verify event parity between
   the two substrates with a diff script.
4. **Phase 4:** v15.8 drops the SD-container write. SD 593753 is
   left in place as a historical artefact; admin docs note "logs
   prior to 2026-XX live in collection X's SD container 593753;
   logs from 2026-XX onward live in the log store."

**Plugin-author migration story.** A plugin author currently writing
to a self-managed SD container per `feedback_shepard_measures_itself.md`
adopts the log store by:

1. Adding the SHACL shape file to `src/main/resources/shapes/`.
2. Declaring it in `plugin.yaml` (§5).
3. Replacing `client.post("/api/structuredDataContainers/<id>/payload", ...)`
   with `logStore.emit(EventBuilder.<shape>().build())`.

The metric (TimescaleDB) emission path is unchanged — OBS-MFFD1
remains the canonical pattern for metrics. The log store is for
events only.

---

## §10 Risks + counter-evidence

### 10.1 The Loki problem — label cardinality explosion

Loki defaults to 15 labels per stream and `max_streams_per_user=10000`
[L4]. Authors who label by high-cardinality IDs (DataObject appId,
trace id, user id) blow the budget within hours. The community
guidance [L1, L2, L3] is universal: *put high-cardinality data in
log content, not labels*. The 2024 `structured_metadata` feature [L1]
adds a third axis (per-row indexable fields outside the label
cardinality) but only Loki 3.0+ has it and many docs predate it.

**For Shepard:** VictoriaLogs's per-field indexing sidesteps the
cardinality contract entirely. Picking Loki would force every plugin
manifest to carry a cardinality discipline doc.

### 10.2 The OpenSearch problem — JVM heap weight

Production OpenSearch needs 8-16 GB JVM heap minimum; the AWS / OS
guidance is to allocate 50% of system RAM, capped at 30 GB (no
Compressed Oops above) [O2]. A Shepard deploy unit today fits in 8 GB
RAM total (backend 2 GB, Neo4j 4 GB, Mongo 1 GB, headroom 1 GB).
Adding OpenSearch is a structural memory budget rewrite — operator
friction quintuples.

**Counter:** OpenSearch's Apache-2.0 + LF Foundation governance [O1]
is the cleanest in the space. The footprint is the deal-breaker, not
the project quality.

### 10.3 The Vector problem — it's a router, not a store

Vector ships logs; it doesn't store them. The full topology is
`plugin → Vector (parse + validate) → VictoriaLogs (store)`. Vector
alone is half the system. **Mitigation:** the design explicitly
declares both as part of `shepard-plugin-logs`. The risk is mis-reading
the architecture; the doc has to be clear.

### 10.4 The SaaS reflex — Grafana Cloud, Logtail, Better Stack, Datadog

Not applicable. DLR sovereignty constraints prohibit data leaving
DLR infrastructure (per `feedback_dlr_sovereignty.md` precedent and
the broader EU data-sovereignty stance). The hosted-log space is
not an option.

### 10.5 The "single ingest endpoint" problem — what if VictoriaLogs is down?

If the VictoriaLogs sidecar is down, the dispatch chain is:

- Plugin writes to the Vector Unix socket — *the socket stays up
  even if VictoriaLogs is down*; Vector's disk buffer absorbs the
  events.
- Vector's `[sinks.victoria_logs] buffer.type = "disk"` configures a
  bounded disk buffer (default 256 MiB) that fills while VictoriaLogs
  is unavailable; on recovery Vector drains.
- The plugin's emit call is non-blocking (write to a Unix socket
  with 1 MB kernel buffer); plugins never block on log ingest.

**The catastrophic case.** Vector disk buffer fills (256 MiB ÷ ~200
bytes/event = ~1.3M events). Vector starts dropping the oldest events
(per default `buffer.when_full = "drop_newest"` configurable). For
Shepard's volume (~50 events/hour from the importer, ~50 events/hour
from other plugins) this is a 1100-hour outage tolerance — 45 days.
Acceptable.

### 10.6 The Quickwit acquisition

Datadog acquired Quickwit in 2024. The OSS repository remains AGPL +
commercial dual-licence [Q1]. Community direction post-acquisition is
uncertain. **Risk:** investing in Quickwit's Parquet-on-S3 model (the
strongest design in the space) and finding it abandoned. **Mitigation:**
not picked; observable for re-evaluation.

### 10.7 OpenTelemetry logs maturity

Quarkus marks OTel logs experimental as of 3.16.0 [OT2]. Picking OTel
Collector as the router today would tie Shepard's log path to an
unstable surface. **Mitigation:** Vector is the primary router; OTel
Collector is the alternative. Re-evaluate when Quarkus marks logs
stable (no committed date).

### 10.8 Counter-evidence — three external sources arguing AGAINST naive adoption

1. **The Loki community forum thread on Garage** [G6] — operators
   report intermittent issues with Loki shipping to Garage S3. Even
   the second-choice fallback has a known wart.
2. **OneUptime's 2026 log management roundup** [OUT1] — concludes
   "no single OSS log tool dominates; pick by your retention class
   and cardinality model" — affirms the §3 trade-off framework.
3. **HackerNews thread on the Grafana relicense (3.6k comments)** —
   the AGPLv3 move was contentious; commenters report enterprise
   legal teams blocking AGPL adoption regardless of network-distribution
   nuance. Validates the "kinder licence by default" criterion.

---

## §11 Acceptance criteria + open questions

### 11.1 Acceptance criteria (the doc moves from `feature-defined` to `tests-implemented` when)

- [ ] `shepard-plugin-logs/plugin.yaml` ships with the sidecar
      declarations from §5, and `infrastructure/docker-compose*.yml`
      assembly picks it up automatically (no manual edits).
- [ ] The base `shp:EventShape` Turtle file is in the plugin's
      `src/main/resources/shapes/` and is loaded into Neo4j at
      plugin registration (verified via `n10s.validation.shacl.list`).
- [ ] At least one plugin (shepard-plugin-importer per the v15
      migration §9) emits through the log store using the typed
      `EventBuilder` SDK.
- [ ] `POST /v2/logs/events` returns 200 + assigned event_id;
      404 / 400 are correctly differentiated (404 = shape unknown,
      400 = payload violates shape).
- [ ] `GET /v2/logs/events?subject=...` returns matching events
      within 500 ms for a 1M-row store.
- [ ] `GET /v2/logs/stream` SSE delivers a tailing event within
      2 s of emission.
- [ ] Vuetify `/admin/logs` view renders 100k events without
      browser-tab freeze (virtualised table).
- [ ] `mcp__shepard__search_logs` callable from Claude with the
      arg shape in §8.3.
- [ ] Vector disk-buffer survives a 30-min VictoriaLogs outage
      without event loss (verified via test harness).
- [ ] Shape-violation events land in the `dead-letter` stream and
      surface in `/v2/admin/logs/violations` within 60 s.
- [ ] Retention windows are PATCH-able per tenant class without
      restart (`/v2/admin/logs/retention`).
- [ ] Coverage on the new `de.dlr.shepard.v2.logs.*` package is
      ≥ 70% per the CI `min-coverage-changed-files: 70` rule.

### 11.2 Open questions

1. **Multi-tenant SHACL audit cost.** The §4 periodic SHACL audit
   (sample N% of events to RDF, validate) is the only heavy operation.
   What's N? 1% per plugin per day? 0.1%? Defer to the
   prototype phase; admin-configurable per `feedback_admin_configurable_runtime.md`.
2. **AGPL fallback boundary.** If VictoriaLogs S3-native slips: do we
   ship Loki immediately as fallback, or do we stay on local-dir
   VictoriaLogs for 12 months? Ops-team call.
3. **The `:Activity` ↔ `:Event` relationship.** Should every log
   event carry a `prov:wasGeneratedBy` edge to a Neo4j-resident
   `:Activity`, or do events stand alone in VictoriaLogs and `:Activity`
   is materialised on demand? §4's `EventShape` includes the IRI;
   the materialisation policy needs a design pass.
4. **OpenLineage integration.** REBAR / Airflow emits OpenLineage
   events (`aidocs/integrations/83`). Are those events also routed
   through this substrate, or do they have a dedicated path? The
   ontology-first answer says: OpenLineage events have their own
   SHACL shape (OL emits standard JSON), and they ride the same
   sidecar. Confirm with the REBAR persona pass.
5. **shepard-plugin-search interaction.** The future search plugin
   (vector embeddings of DataObject descriptions) might want to
   index log payloads as embeddings. Is that a `shepard-plugin-search`
   feature or a `shepard-plugin-logs` extension? Defer; flag for
   AI-plugin design.
6. **Per-user log access control.** Today's plan is admin-only
   `/v2/admin/logs/*`. The per-DataObject activity tab (§7.2)
   needs the same role-check as the entity it observes. Confirm
   with the auth / PermissionsService layer.

---

## §12 References (external citations, per `feedback_agents_use_research_tools.md`)

| Tag | Source | URL |
|---|---|---|
| [G1] | "Grafana, Loki, and Tempo will be relicensed to AGPLv3", Grafana Labs blog | https://grafana.com/blog/grafana-loki-tempo-relicensing-to-agplv3/ |
| [G6] | "Loki does not ship logs to external S3 storage (garage)", Grafana community forum | https://community.grafana.com/t/loki-does-not-ship-logs-to-external-s3-storage-garage/159780 |
| [L1] | "Cardinality", Grafana Loki documentation | https://grafana.com/docs/loki/latest/get-started/labels/cardinality/ |
| [L2] | "Label best practices", Grafana Loki documentation | https://grafana.com/docs/loki/latest/get-started/labels/bp-labels/ |
| [L3] | "Grafana Loki and what can go wrong with label cardinality", U. Toronto sysadmin blog | https://utcc.utoronto.ca/~cks/space/blog/sysadmin/GrafanaLokiCardinalityProblem |
| [L4] | "Cardinality Explosion", Compile N Run | https://www.compilenrun.com/docs/observability/loki/troubleshooting/cardinality-explosion/ |
| [V1] | "Switching to the MPL 2.0 License", Vector blog | https://vector.dev/highlights/2020-08-31-mpl-2-0-license/ |
| [V2] | "Vector Remap Language (VRL)", Vector documentation | https://vector.dev/docs/reference/vrl/ |
| [VL1] | VictoriaLogs LICENSE file (Apache-2.0) | https://github.com/VictoriaMetrics/VictoriaLogs/blob/master/LICENSE |
| [VL2] | "VictoriaLogs vs Loki — Benchmarking Results", TrueFoundry 2025 | https://www.truefoundry.com/blog/victorialogs-vs-loki |
| [VL3] | "VictoriaLogs Roadmap" (S3 object-storage on the roadmap) | https://docs.victoriametrics.com/victorialogs/roadmap/ |
| [VL4] | "VictoriaLogs: How To Convert Loki Queries to VictoriaLogs Queries" | https://docs.victoriametrics.com/victorialogs/logql-to-logsql/ |
| [VL5] | "Why VictoriaLogs is a better alternative to Grafana Loki", Aliaksandr Valialkin (ITNEXT) | https://itnext.io/why-victorialogs-is-a-better-alternative-to-grafana-loki-7e941567c4d5 |
| [O1] | "OpenSearch (software)", Wikipedia — LF Foundation governance, 400+ members | https://en.wikipedia.org/wiki/OpenSearch_(software) |
| [O2] | "OpenSearch Heap Size Usage and JVM Garbage Collection", Opster | https://opster.com/guides/opensearch/opensearch-basics/opensearch-heap-size-usage-and-jvm-garbage-collection/ |
| [OT1] | OpenTelemetry Collector LICENSE (Apache-2.0) | https://github.com/open-telemetry/opentelemetry-collector |
| [OT2] | "Using OpenTelemetry", Quarkus 3.16+ logs experimental flag | https://quarkus.io/guides/opentelemetry |
| [Q1] | quickwit-oss/quickwit GitHub — AGPL + commercial dual licence | https://github.com/quickwit-oss/quickwit |
| [P1] | "Purpose built log observability", Parseable blog — AGPLv3 rationale | https://www.parseable.com/blog/parseable-purpose-built-log-observability |
| [OUT1] | "Top 10 Open Source Log Management Tools for 2026", Parseable | https://www.parseable.com/blog/open-source-log-management-tools |
| [S-06] | Internal — Snapshots × Garage S3 synergy | aidocs/agent-findings/synergy-2026-05-23-snapshots-garage-gap.md |
| [SHACL] | "SHACL validation", GraphDB 11.3 documentation | https://graphdb.ontotext.com/documentation/11.3/shacl-validation.html |

---

## §13 Process notes

- This doc lands as part of the LOGSTORE1 design pass; no
  code changes accompany it.
- `aidocs/16-dispatcher-backlog.md` LOGSTORE1 Notes column updated
  to cite this file path.
- `aidocs/40-ecosystem.md` plugin table gets a new
  `shepard-plugin-logs` row (📐 designed).
- `aidocs/44-fork-vs-upstream-feature-matrix.md` gets a new
  `shepard-plugin-logs` row in the Dev-track plugins table.
- Doc-stage: `audited-by-personas` (advanced 2026-05-23 per the
  5-persona audit at
  `aidocs/agent-findings/persona-audit-logstore-2026-05-23.md`). All
  five personas verdict ACCEPT-WITH-CHANGES; **25 change requests
  filed (2 CRITICAL, 18 MAJOR, 5 MINOR)** — `LOGSTORE1-API1..7`,
  `LOGSTORE1-OPS1..5`, `LOGSTORE1-STR1..4`, `LOGSTORE1-DN1..4`,
  `LOGSTORE1-RDM1..5` — must close before the next transition to
  `feedback-implemented`. The two **CRITICAL** rows are
  `LOGSTORE1-RDM1` (EventShape needs ORCID/ROR attribution slots for
  externally-citable audit-tier events) and `LOGSTORE1-RDM2` (audit
  archive format on Garage must be VictoriaLogs-independent — NDJSON
  + SHA-256 fixity manifest + COMPLIANCE-mode object lock, readable
  without the substrate). The CRITICAL pair also resolves the primary
  audit's ESCALATION-2 (VictoriaLogs maturity risk) from the FAIR
  lens: archive-format coupling is the relevant variable, not
  operational maturity. Re-check personas: same five (API Scrutinizer +
  Reluctant Senior + Strategy Aligner + Digital Native + RDM),
  scoped to their own filings. Ascends to `tests-implemented` when the
  §11.1 criteria are met and the change requests are resolved.
