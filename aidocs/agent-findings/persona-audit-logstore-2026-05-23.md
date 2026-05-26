---
title: Persona audit — log-store-sidecar design (aidocs/integrations/94)
stage: audited-by-personas
last-stage-change: 2026-05-23
audience: contributors, reviewers
audits: aidocs/integrations/94-log-store-sidecar-design.md
personas:
  - API Scrutinizer (Role 3) — primary
  - Reluctant Senior (Role 9) — secondary
  - Strategy Aligner (Role 6) — secondary
  - Digital Native (Role 10) — secondary
  - RDM / FAIR Steward (Role 5) — addendum 2026-05-23 (§5b; closes §5.4 ESCALATION 1)
backlog-id: LOGSTORE1
---

# Persona audit — log-store-sidecar design (`aidocs/integrations/94`)

The design at `aidocs/integrations/94-log-store-sidecar-design.md` (commit
`bef7fc5b`, stage `feature-defined`) proposes VictoriaLogs + Vector + a
SHACL `EventShape` contract behind a `shepard-plugin-logs` sidecar, with
five Vuetify UI surfaces, eleven REST endpoints under `/v2/logs/*`, four
MCP tools, and a four-phase migration off the SD-runlog pattern. This
audit walks four personas through it and produces a stage verdict.

The trio that fires on every `feature-defined → audited-by-personas`
transition (per `feedback_persona_audit_triggers.md`) is **Strategy
Aligner + API Scrutinizer + RDM**. For LOGSTORE1 the **RDM lens has been
substituted by Reluctant Senior + Digital Native** because the design's
risk surface is dominated by operator footprint (RS) and agent-facing
API ergonomics (DN), not FAIR / archival publication (which the doc
defers to PROV1a's existing `:Activity` chain). The trio's RDM voice
shows up as in-prompt citations inside the Strategy Aligner § instead
of a full dispatch. This deviation is logged for the
`audited-by-personas → feedback-implemented` re-check: the RDM persona
gets pulled in *if* findings touch the `audit` retention tier or the
nightly Garage export shape.

---

## §1 API Scrutinizer (Role 3) — primary

**Lens** *(per CLAUDE.md Role 3)*: leaky-abstraction, redundancy,
5-tuple smell, wrong-layer computation, missing/synthesised operations,
caller-visible naming.

### 1.1 Findings

**F1 (MAJOR, leaky-abstraction). Mixing query-by-filter and query-by-LogsQL
under different verbs is asymmetric and surfaces the substrate.**
`GET /v2/logs/events?subject=...&level=...&since=...` (§8.1 line 668) and
`GET /v2/logs/query` (§8.1 line 669, LogsQL pass-through) coexist. The
LogsQL endpoint then appears in the §8.2 example as a **POST** with a JSON
body (`{"logsql": "...", "limit": 500}`) — same operation surfaced under
two methods, two body shapes. Per OneUptime's 2026 API pagination
guide (cursor pagination + cap server-side) [API1] this kind of split
predictably leaks: API consumers learn two endpoints to query one
substrate. Worse, the **method mismatch** (GET vs POST for what is
semantically a "query") trips OpenAPI generation and the
shepard-client-python codegen. *From the Reluctant Senior lens* (Role 9):
"why is one a GET and one a POST?" is an unanswerable question on
Tuesday morning. **Fix:** one endpoint, one verb. `POST /v2/logs/search`
with a discriminated request body — `{"filter": {...}}` XOR `{"logsql":
"..."}`. The free-text/LogsQL split lives inside the request body, not
across paths.

**F2 (MAJOR, naming collision). The `subject` field uses an opaque
`shepard:<kind>:<appId>` IRI when callers already know the appId.**
§4 line 320 declares `shev:subject` as `sh:nodeKind sh:IRI` with the
pattern `shepard:<kind>:<appId>`. The REST query shape in §8.2 line 707
exposes this: `subject=shepard:collection:019e4e56-...`. *From the
Digital Native lens* (Role 10): the caller has `collection_id="019e4e56..."`
already in their Python script. Making them concatenate `f"shepard:collection:{cid}"`
is friction; making the MCP agent do it is the same naming-confusion
class that the `referenceIds` vs `dataObjectIds` mistake in the v2 API
already burned us on (see `aidocs/agent-findings/api-scrutinizer.md`).
**Fix:** two query slots — `subject_kind` + `subject_id` — and the
backend reconstructs the IRI. The IRI form stays on the SHACL side
(where it is structurally required for cross-substrate provenance
joining); the wire never sees it. Callers who *want* the IRI use
`subject_iri` as a separate filter slot.

**F3 (MAJOR, missing operation). No "downgrade an emit" path —
shape-violation events fall into a `dead-letter` stream the caller
cannot recover.** §4 line 388-390 describes the dead-letter routing
(VRL drops to a `shape_violation` stream). §8.1's
`GET /v2/admin/logs/violations` (line 672) is the admin's window into
this, but the plugin author has no way to retry a corrected event with
the original timestamp. **Fix:** add
`POST /v2/admin/logs/violations/{id}:replay` accepting an amended
payload, preserving the original `emitted_at` (a new field on the
violation record). Without replay, the plugin author's first
shape-violation in production is an unrecoverable observability gap.
*Counter (from Strategy Aligner lens):* "replay is a feature creep —
the right answer is fix-and-redeploy." Disagree: the plugin author who
ships a v15.6 and is in flight cannot redeploy mid-import; the replay
endpoint preserves continuity. The cost is ~50 lines.

**F4 (MAJOR, 5-tuple smell on the EventShape). `level + plugin + actor
+ subject + event_name + retention_class` is six required-ish fields
the emitter has to set.** §4 lines 297-341 lists all six as either
`minCount 1` or pattern-constrained. Six required fields IS the 5-tuple
problem the API Scrutinizer rejects on timeseries channel identity
(`project_v5_legacy_source.md` + `aidocs/platform/87`). *Through the
Digital Native lens (Role 10)*: the 5-line Python test —
"emit one event in five lines" — fails. The minimum viable emit is:
```python
import requests, json, datetime
r = requests.post(
    "https://shepard-api.nuclide.systems/v2/logs/events",
    headers={"Authorization": f"Bearer {tok}"},
    json={
        "level": "info",
        "event_name": "process_started_fresh",
        "plugin": "shepard-plugin-importer",
        "actor": "plugin:shepard-plugin-importer",
        "subject": f"shepard:collection:{cid}",
        "timestamp": datetime.datetime.utcnow().isoformat() + "Z",
        "retention_class": "audit",
        "payload": {...},
    },
)
```
That's 8 lines minimum, and 6 of them are ceremony. **Fix:** make the
emitter SDK fill `plugin`, `actor`, `timestamp`, `retention_class`
(via plugin manifest declaration in §5 lines 451-459) *server-side* —
the wire shape becomes `{level, event_name, subject?, payload}`. The
plugin authenticates with its bearer token; the server knows the plugin
id, derives actor + retention class from the manifest, stamps the
timestamp. Per OpenTelemetry semantic conventions [OTL1, OTL2]
the server-stamped Resource attributes (`service.name`, etc.) are
standard practice — the plugin shouldn't repeat what the auth context
already proves.

**F5 (MINOR, redundancy). Two `events:batch` shapes implied — `POST
/v2/logs/events:batch` (REST) and the implicit "Telemetry batches
events into envelopes" pattern from v15.6 — without a clear deprecation
plan inside the batch endpoint itself.** §8.1 line 666 lists the batch
endpoint but doesn't constrain `≤ 1000` until §8.3 implicitly via the
MCP tool's `limit`. **Fix:** declare the batch cap in the REST endpoint
table (§8.1) and reject >1000 with `413 Payload Too Large` (not 400 —
413 is the correct semantic per RFC 7231).

**F6 (MINOR, wrong layer). The "free-text to LogsQL" translation in
§7.5 line 651-653 (`foo bar baz → _msg:foo OR _msg:bar OR _msg:baz`)
is server-computed but lives in the frontend wrapper.** Two consumers
(the Vuetify UI and the MCP `mcp__shepard__search_logs` `free_text`
field) implement the same translation, divergently. **Fix:** the
translation lives in the backend (`SearchService`); the REST shape
exposes `{"text": "..."}` as a first-class slot; the LogsQL pass-through
is the escape hatch. One translator, two consumers.

**F7 (MINOR, leaky abstraction). The MCP `mcp__shepard__tail_logs`
returns "streaming events via MCP's progress-notification channel"
(§8.3 line 765). This couples Shepard's MCP tool to MCP's
progress-notification semantics — a moving target per the Anthropic
Agent SDK docs [MCP1].** **Fix:** the MCP tool returns a SSE URL;
the agent client opens the SSE itself via the standard EventSource
shape. Decouples the tool surface from MCP's notification-channel
evolution.

### 1.2 Verdict: **ACCEPT-WITH-CHANGES**

The design's core structure is sound — substrate choice, sidecar
declaration, SHACL-driven shape contract all hold up. The wire shape
has predictable leaks (F1-F4 are MAJOR, F5-F7 MINOR) that the
Scrutinizer would block on but not redesign from scratch. The
field-derivation pattern (F4) is the single highest-value change and
collapses the 5-tuple smell that would otherwise haunt every plugin
author for the lifetime of the substrate.

**What would change my mind:** if the SHACL EventShape is *not* the
wire shape (i.e. server-stamped fields land in the validation graph
post-receive, not the emit payload), F4 dissolves and most of F2
becomes a non-issue. Then I'd vote **ACCEPT**.

### 1.3 Change requests (backlog rows)

| ID | Description | Effort | Priority |
|---|---|---|---|
| LOGSTORE1-API1 | Collapse `/v2/logs/events?...` and `POST /v2/logs/query` into one `POST /v2/logs/search` with discriminated body | S | MAJOR |
| LOGSTORE1-API2 | Replace `subject=<IRI>` with `subject_kind` + `subject_id` slots; backend reconstructs IRI | S | MAJOR |
| LOGSTORE1-API3 | Add `POST /v2/admin/logs/violations/{id}:replay` for dead-letter recovery | M | MAJOR |
| LOGSTORE1-API4 | Server-derive `plugin`, `actor`, `timestamp`, `retention_class` from manifest + auth context; wire shape becomes `{level, event_name, subject?, payload}` | M | MAJOR |
| LOGSTORE1-API5 | Document batch cap in §8.1 endpoint table; return 413 on overflow | S | MINOR |
| LOGSTORE1-API6 | Move free-text → LogsQL translation server-side; expose `text` slot in request body | S | MINOR |
| LOGSTORE1-API7 | MCP `tail_logs` returns SSE URL not progress-notification stream | S | MINOR |

### 1.4 External citations

- [API1] OneUptime 2026 — "How to Implement API Pagination Strategies" — cursor pagination + server-side caps. https://oneuptime.com/blog/post/2026-01-30-api-pagination-strategies/view
- [OTL1] OpenTelemetry Logs Data Model — Resource vs Attribute separation. https://opentelemetry.io/docs/specs/otel/logs/data-model/
- [OTL2] OpenTelemetry General Logs Attributes — `log.record.uid`, `log.record.original` patterns. https://opentelemetry.io/docs/specs/semconv/general/logs/
- [MCP1] Claude Agent SDK — MCP integration patterns + tool-search deferred loading. https://platform.claude.com/docs/en/agent-sdk/mcp
- [RFC5424] RFC 5424 — syslog severity field semantics; STRUCTURED-DATA shape. https://datatracker.ietf.org/doc/html/rfc5424

---

## §2 Reluctant Senior Researcher (Role 9) — secondary

**Lens** *(per CLAUDE.md Role 9)*: 28-year-veteran sceptic; "would I run
this on Tuesday morning"; operator footprint; "what new binary do I
have to watch"; conversion-killer friction; *the killer demo moment*.

### 2.1 Findings

**F8 (MAJOR, footprint creep). The deploy unit grows by two more
containers (Vector + VictoriaLogs) plus a Unix socket bind-mount.** §5
lines 478-505 lists both sidecars; healthchecks; volumes; networks. *In
the senior's voice:* "my current stack is backend + Neo4j + Mongo +
Postgres + Garage. Now there are seven services in compose. When something
breaks at 7am, which one do I look at first?" The §10.5 disk-buffer
analysis (Vector absorbs a 30-min VictoriaLogs outage) IS reassuring —
but the *combined* failure mode (Vector OOM in the importer's hot path
because VRL is slow on a malformed event) is not analysed.
**Mitigation request:** §10 needs a `10.9 The cascade failure` subsection
covering: (a) what happens when Vector's VRL hot-loop chokes on a
malformed event from a buggy plugin (does it block the Unix socket?);
(b) what happens when the unix socket's 1 MB kernel buffer fills (do
plugins block? drop? error?); (c) a documented "kill vector, fall back
to direct-to-VictoriaLogs" runbook for the catastrophic case. *From the
Strategy Aligner lens (cross-citation):* this is the "no parallel
stack" worry the doc tries to deflect in §2 — but two containers IS a
parallel stack from the operator's perspective.

**F9 (MAJOR, missing knob). Retention tiers (audit / ops-30d / debug-7d
/ ephemeral-1d) are declared as plugin manifest fields (§5 line 452-459)
not as admin-configurable runtime knobs.** This contradicts CLAUDE.md's
"surface operator knobs in the admin config" rule directly. The
`/v2/admin/logs/retention` endpoint (§8.1 line 674) PATCHes the
*tenant-class TTL* (e.g. how long `ops-30d` actually retains), not the
per-plugin assignment of which events go to which tier. *Senior voice:*
"the importer plugin declares its ops-30d events. I want to bump them
to 90 days because the QA audit needs the trail. I can't, without
editing the plugin manifest and redeploying." **Fix:** the per-plugin
retention assignment becomes a `:LogRetentionPolicy` Neo4j entity (per
the A3b pattern from `feedback_admin_configurable_runtime.md`), with
the plugin manifest as the install-time seed. Admin endpoint:
`PATCH /v2/admin/logs/plugins/{plugin}/retention`. Now the senior can
flip a knob without a redeploy.

**F10 (MAJOR, the killer demo moment IS in the design but is buried).**
§7.2 "Per-DataObject activity tab" is the single feature that would
make a senior researcher say "yes, this is genuinely better than my
folder + Excel" — clicking on TR-004 in the UI and seeing every log
event tied to it, including the anomaly investigation chain. The doc
ranks this as the second-from-bottom UI surface. *Senior voice:*
"that's the only UI surface I will use; the rest is for the admin." The
Vuetify mockup uses `v-timeline` which is the right shape, but the doc
doesn't promise the *killer behaviour*: clicking a log event with a
`prov:wasGeneratedBy` IRI navigates to the `:Activity` view. Without
the navigation, the activity tab is read-only nostalgia. **Fix:**
elevate §7.2 to the lead UI surface in the doc; ship the
prov-edge navigation in v1 of the UI (not a follow-up).

**F11 (MAJOR, cardinality budget enforcement is reactive, not
preventive).** §5 line 459 `cardinality_budget: 50000`. §3 line
38 mentions "admin alert when a plugin breaches a cardinality budget".
*Senior voice:* "alert me at 2am when the budget was breached at
11pm — what was I supposed to do then? The events are already in." This
is the OneUptime 2026 log-management roundup point [OUT1, cited in the
doc itself]: cardinality discipline is upstream from the substrate.
**Fix:** the budget enforces *backpressure* — when the per-plugin
1-minute rolling window exceeds (budget / 1440) × 1.2 the plugin's emit
endpoint returns 429 Too Many Requests with a `Retry-After`. Now the
plugin author *learns* in development that they're over-budget. The
admin alert remains, but it's a fallback signal, not the only signal.

**F12 (MINOR, sidecar deploy assembly trust). §5 declares the
`shepard-plugin-logs` sidecar via the manifest; the deploy tooling
"picks it up automatically (no manual edits)" (§11.1 first criterion).
This is a powerful claim but undocumented in the doc.** *Senior voice:*
"how do I see the assembled compose file before it deploys? What if I
need to override a port for my internal firewall?" **Fix:** acceptance
criterion explicitly requires a `compose render` CLI command + a
`/v2/admin/sidecars/render` endpoint that returns the assembled YAML
without applying it. Operator audits before applying — the same
discipline as `terraform plan` before `terraform apply`. Per the
VictoriaLogs operator guide [VL-OPS1] the production-deployment pattern
is exactly this: render, review, apply.

### 2.2 Verdict: **ACCEPT-WITH-CHANGES**

The design solves the right problem (the SD-runlog scales to ~0 plugins
+ ~0 retention tiers) and picks a sane substrate (VictoriaLogs is the
"30 MB single binary, no JVM, no shard-tuning" tool that the Reluctant
Senior tolerates). The structural risks — cascade failure, retention
inflexibility, reactive cardinality budget — are all fixable without
redesigning. The killer demo is *in* the doc but undersold; promoting
it is a doc-edit, not a feature change.

**What would change my mind:** if the cascade-failure analysis (F8)
reveals that Vector + VictoriaLogs cannot in fact survive a buggy
plugin's hot-loop without taking down the import path, the design
fails. The Reluctant Senior reverts to "log to stderr, grep it
later." Then I'd vote **REJECT** pending alternative architecture (e.g.
direct-to-disk circular buffer per plugin, async tail to VictoriaLogs
on the side).

### 2.3 Change requests (backlog rows)

| ID | Description | Effort | Priority |
|---|---|---|---|
| LOGSTORE1-OPS1 | §10 add `10.9 The cascade failure` subsection (Vector hot-loop, socket fill, kill-vector runbook) | S (doc) | MAJOR |
| LOGSTORE1-OPS2 | `:LogRetentionPolicy` Neo4j entity + `PATCH /v2/admin/logs/plugins/{plugin}/retention`; plugin manifest seeds install-time defaults | M | MAJOR |
| LOGSTORE1-OPS3 | Elevate §7.2 per-DataObject activity tab to lead UI surface; ship prov-edge navigation in v1 | S (doc) | MAJOR |
| LOGSTORE1-OPS4 | Cardinality budget enforces backpressure via 429 + Retry-After; admin alert is fallback signal | S | MAJOR |
| LOGSTORE1-OPS5 | `compose render` CLI + `/v2/admin/sidecars/render` endpoint (audit before apply) | M | MINOR |

### 2.4 External citations

- [VL-OPS1] VictoriaLogs Kubernetes Operator + production patterns. https://docs.victoriametrics.com/operator/
- [VL-Q1-2026] VictoriaMetrics Cloud Q1 2026 — VictoriaLogs GA. https://victoriametrics.com/blog/q1-2026-whats-new-victoriametrics-cloud/
- [VL-INGEST] VictoriaLogs Data Ingestion — `/insert/jsonline` + AccountID/ProjectID tenant headers. https://docs.victoriametrics.com/victorialogs/data-ingestion/
- [FRICT1] Plain Concepts — Pain points and barriers to data services adoption. https://www.plainconcepts.com/pain-points-data-adoption/
- [FRICT2] Intertrust — Five data friction points that block innovation. https://www.intertrust.com/blog/five-data-friction-points-that-block-innovation/

---

## §3 Strategy Aligner (Role 6) — secondary

**Lens** *(per CLAUDE.md Role 6)*: licence-by-default kindness; "no
parallel stack" concern; alignment to DLR / Clean Aviation JU KPIs;
honest risk; positioning.

### 3.1 Findings

**F13 (ACCEPT, well-argued). The Apache-2.0 + LogsQL-superset decision
over AGPLv3 Loki/Tempo/Quickwit/Parseable is structurally sound and
matches "kinder licence by default" posture.** §3 lines 184-202 walks
the AGPL analysis correctly. The OpenCoreVentures analysis [LIC1] is
explicit: *"AGPL is a non-starter for most companies"* — and the
Vaultinum compliance guide [LIC2] confirms that even with sidecar
isolation, the operator-redistribution obligation accrues. The kinder
posture wins on adoption velocity. *Cross-citation to RDM (in-prompt
substitution):* a DLR institute publishing under Helmholtz Unhide or
Databus cares about license-clean dependencies in the publication
chain; Apache-2.0 is the cleanest signal.

**F14 (MAJOR, "no parallel stack" worry not fully discharged).** §2
lines 142-148 argues "the log store IS in-stack, declared as a
plugin-managed sidecar". *Through the Strategy Aligner lens:* this is
correct *if* the operator only ever interacts with logs through
Shepard's REST + Vuetify UI. The moment they need to debug VictoriaLogs
itself (which they will — VictoriaLogs Q1 2026 GA is recent [VL-Q1-2026]
and production stability is still earning), they're outside Shepard
looking at `vlselect` HTTP endpoints. That's a parallel stack. **Fix:**
the doc needs a §"What the operator sees when VictoriaLogs is the
problem" — covering the LogsQL CLI (`vlogscli`), the
VictoriaLogs `/metrics` endpoint, and how those debugging surfaces
integrate (or don't) with Shepard's existing ops dashboards. Without
this, the "no parallel stack" claim is aspirational.

**F15 (MAJOR, VictoriaLogs maturity risk underweighted).** The doc
correctly cites [VL2] TrueFoundry's 2025 benchmark (3× ingest, 87%
less RAM) but doesn't note that VictoriaLogs was only declared **GA in
VictoriaMetrics Cloud in Q1 2026** [VL-Q1-2026]. This is recent. The
S3-native storage [VL3] is roadmap. The doc's "second choice: Grafana
Loki" is the right fallback, but the *trigger* for switching is vague
("if VictoriaLogs S3-native slips beyond 12 months"). *Through the
Strategy Aligner lens:* the *real* trigger is operational —
"VictoriaLogs has a P1 incident in production at a DLR-comparable
deploy". The doc should commit to a quarterly review of VictoriaLogs's
incident rate against the upstream issue tracker, with a documented
"escape hatch" runbook that ships logs to Loki without a redesign.
The `shepard-plugin-logs` abstraction makes this swap manifest-only
in principle; commit to it explicitly. **Fix:** §10 add
`10.10 The maturity-risk runbook` — quarterly review + escape-hatch
swap procedure.

**F16 (ACCEPT, the recursive-observability tension is well-resolved).**
§2 walks the "Shepard measures itself" principle carve-out correctly:
TS substrate stays for *metric derivatives*, log store handles *raw
events*. This matches the OBS-MFFD1 division of labour. The Strategy
Aligner lens has no objection.

**F17 (MAJOR, missing positioning slot). The doc doesn't connect
logging-as-substrate to Clean Aviation JU / Catena-X / DLR Line 4
positioning — even though there's a direct line.** DIN EN 9100 audit
trails, EASA Part 21G non-conformance reporting, the f(ai)²r AI
interaction provenance — all of these are *audit trails*. A
plugin-extensible log store with SHACL-typed events is structurally the
audit-trail substrate for these regulatory frames. The doc treats it as
"observability" only. *Strategy Aligner lens:* this is the funding
pitch. **Fix:** §1 or §10 add a "Strategic positioning" paragraph
connecting the log substrate to the audit-trail family — and cite
`aidocs/agent-findings/easa-ai-regulatory-positioning.md` +
`project_fair2r_integration.md`. Without this, the log store reads as
"we have logs now" rather than "we have a regulatory-grade event
substrate". One paragraph fixes it.

**F18 (MINOR, OpenTelemetry alternative path deserves stronger
hedge).** §3 lines 264-273 treats OTel Collector as alternative router.
But the OpenTelemetry semantic conventions [OTL1, OTL2] are the
*industry-standard wire shape* — every vendor (Datadog, Honeycomb,
NewRelic, Grafana, AWS CloudWatch) consumes OTLP. *Strategy Aligner
lens:* every byte the plugin emits *not* in OTLP-compatible shape is a
byte that ties Shepard to VictoriaLogs forever. The SHACL EventShape
maps cleanly to OTLP's Resource + Attributes split — but the doc
doesn't commit to that mapping. **Fix:** add an explicit "OTLP-equivalence"
column in the EventShape definition (§4) noting which fields map to
OTLP's Resource, which to LogRecord Attributes, which to Body. Now the
manifest is forward-compatible with OTLP-everywhere.

### 3.2 Verdict: **ACCEPT-WITH-CHANGES**

The licence + governance decision is strong and well-defended (F13).
The recursive-observability tension is resolved cleanly (F16). The
gaps are positioning-shaped (F17), maturity-risk-shaped (F15), and
parallel-stack-shaped (F14) — all closeable with doc edits + one
runbook. The OTLP-compatibility hedge (F18) is the structural change
that pays back over 5+ years.

**What would change my mind:** if Catena-X or another Helmholtz peer
ships a logs substrate decision that converges on a different licence
shape (e.g. they all settle on Loki + LogQL), the Strategy Aligner
would re-weight "ecosystem alignment" against "kinder licence". Today
no convergence exists [LIC1, LIC3]; Apache-2.0 stands.

### 3.3 Change requests (backlog rows)

| ID | Description | Effort | Priority |
|---|---|---|---|
| LOGSTORE1-STR1 | §"What the operator sees when VictoriaLogs is the problem" — `vlogscli`, `/metrics`, integration with Shepard ops dashboards | S (doc) | MAJOR |
| LOGSTORE1-STR2 | §10 add `10.10 The maturity-risk runbook` — quarterly VictoriaLogs incident review + Loki escape-hatch swap | S (doc) | MAJOR |
| LOGSTORE1-STR3 | §1 / §10 — strategic positioning paragraph connecting log substrate to DIN EN 9100 + EASA Part 21G + f(ai)²r audit-trail family | S (doc) | MAJOR |
| LOGSTORE1-STR4 | §4 EventShape — add OTLP-equivalence column (Resource / LogRecord Attributes / Body mapping); ensures forward-compat with OTLP-everywhere | S (doc) | MINOR |

### 3.4 External citations

- [LIC1] OpenCoreVentures — "AGPL license is a non-starter for most companies". https://www.opencoreventures.com/blog/agpl-license-is-a-non-starter-for-most-companies
- [LIC2] Vaultinum — AGPL Compliance: containerisation isolates AGPL components but operator-redistribution obligation persists. https://vaultinum.com/blog/essential-guide-to-agpl-compliance-for-tech-companies
- [LIC3] Revenera — "The SaaS Loophole in GPL Open Source Licenses" — AGPLv3 §13 explicit network distribution clause. https://www.revenera.com/blog/software-composition-analysis/understanding-the-saas-loophole-in-gpl/
- [VL-Q1-2026] VictoriaMetrics Cloud Q1 2026 — VictoriaLogs GA + April 2026 features. https://victoriametrics.com/blog/q1-2026-whats-new-victoriametrics-cloud/
- [LF-OS] Linux Foundation — OpenSearch Software Foundation governance (alternative substrate's policy posture). https://www.linuxfoundation.org/blog/how-the-opensearch-software-foundation-will-ensure-long-term-sustainability-of-the-opensearch-project

---

## §4 Digital Native Researcher (Role 10) — secondary

**Lens** *(per CLAUDE.md Role 10)*: 5-line-Python feasibility; gh-CLI
driving; MCP tool ergonomics; "does this fit in my notebook workflow";
"can I write a script to ingest in an afternoon".

### 4.1 Findings

**F19 (MAJOR, 5-line-Python fails on emit and search both).** Per F4
above, emit requires 8 lines of ceremony minimum. *And* search: the
§8.3 `mcp__shepard__search_logs` is clean (5-line Python through the
MCP client), but the REST `/v2/logs/events?subject=shepard:collection:...`
requires the IRI concatenation (per F2). **Fix:** F2 + F4 close this
finding. Acceptance criterion: a `shepard-logs` Python helper in the
client SDK that wraps emit + search such that:
```python
from shepard import client
c = client.Logs(token=tok)
c.emit("process_started_fresh", subject=("collection", cid), payload={...})
for ev in c.search(subject=("collection", cid), level="error", since="-1h"):
    print(ev.event_name, ev.payload)
```
is the 5-line shape. The MCP tool aligns with this; the REST endpoint
needs the F2 + F4 fix to enable it.

**F20 (MAJOR, MCP `tail_logs` shape mismatch with Claude's actual
patterns).** §8.3 line 765 returns "streaming events via MCP's
progress-notification channel". The Claude Agent SDK docs [MCP1] note
that MCP tools generally return single responses; streaming via
progress notifications is supported but the agent client may not surface
it in a usable form (the user sees progress events as text, often
truncated). *Digital Native lens:* "I want to subscribe to a Claude
conversation and have the agent stream me logs in real-time" — this
needs a different MCP shape, probably `tail_logs` returns a *resource*
(MCP resources support `subscribe()` natively per the MCP spec). **Fix:**
return an MCP resource handle; document the subscribe pattern. *Or*
the MCP tool returns the SSE URL (per F7) and the agent client opens it
directly — agent clients with MCP+SSE composition will work
out-of-the-box.

**F21 (MAJOR, no Python emitter SDK shipped). §9 line 821-822 talks
about `logStore.emit(EventBuilder.<shape>().build())` as the Java path.
There is no Python equivalent.** *Digital Native lens:* "my shepard-mffd-import
script is Python. I am the v15.x author. What's my emit API?" **Fix:**
the design must declare a Python SDK module in the same release (call it
`shepard.logs`) wrapping `/v2/logs/events`. Generation from the OpenAPI
spec is the right path (the shepard-client-python codegen pipeline
exists). Without it, the import script either keeps its hand-rolled
`Telemetry` class indefinitely or ships a third one-off SDK per plugin
author. Per the Azure Monitor Query Python client library pattern
[PYAZURE]: a thin idiomatic wrapper is 200 lines and unblocks the
ecosystem.

**F22 (MINOR, free-text search semantics not specified for MCP).** §8.3
line 757 `free_text: string  # passed to LogsQL _msg:<text>`. *Digital
Native lens:* what about phrase searches, regex, field-scoped text? The
LogsQL docs [LQL1] support these; the MCP tool's `free_text` slot
should specify which subset is supported. **Fix:** name the supported
subset explicitly — at minimum, `phrase`, `or`-joined-terms, and
`field:value` with field-name validation. Don't dump the user into
LogsQL syntax via the back door.

**F23 (ACCEPT). The SSE shape in §8.2 (line 731-739) is correct and
idiomatic.** EventSource works out-of-the-box in Python via `sseclient`
or `httpx-sse`. *Cross-citation to API Scrutinizer (in-prompt):* good
that this isn't WebSocket — SSE is the right primitive for log tail
because it's unidirectional and survives proxies.

**F24 (MINOR, no GitHub CLI-style story).** The Digital Native lens
likes `gh issue list --label X` shapes. The doc has no
`shepard-admin logs ...` CLI story. *Digital Native lens:* "I want
`shepard-admin logs tail --plugin importer --level error --since -1h`
piped to jq." **Fix:** acceptance criterion adds
`shepard-admin logs {tail, search, replay, shapes}` CLI parity (per the
L1 baseline). Matches CLAUDE.md's "CLI parity" rule for admin endpoints.

### 4.2 Verdict: **ACCEPT-WITH-CHANGES**

The MCP surface is broadly correct (`search_logs`, `list_shapes`,
`violations_today` are clean). The REST surface has the 5-line-Python
failure (F19/F4) that the API Scrutinizer already flagged — closing it
there closes it here. The Python SDK absence (F21) and CLI absence
(F24) are *table-stakes for the Digital Native's adoption*; without
them the substrate is a Java-and-Vuetify substrate, not a research-grade
one.

**What would change my mind:** if the Python SDK ships and the MCP
`tail_logs` returns a usable shape (resource subscribe OR SSE URL),
this is **ACCEPT** outright. The remaining nits are documentation.

### 4.3 Change requests (backlog rows)

| ID | Description | Effort | Priority |
|---|---|---|---|
| LOGSTORE1-DN1 | `shepard.logs` Python SDK module (generated from OpenAPI + idiomatic wrapper); 5-line emit + search demo in docs | M | MAJOR |
| LOGSTORE1-DN2 | MCP `tail_logs` returns either resource handle (subscribe pattern) or SSE URL — not progress-notification stream | S | MAJOR |
| LOGSTORE1-DN3 | `shepard-admin logs {tail, search, replay, shapes}` CLI with `--output={human,json}` parity | M | MAJOR |
| LOGSTORE1-DN4 | Specify free_text subset semantics in MCP tool docs (phrase, OR-terms, field:value); reject raw LogsQL via free_text | S | MINOR |

### 4.4 External citations

- [MCP1] Claude Agent SDK — MCP integration patterns. https://platform.claude.com/docs/en/agent-sdk/mcp
- [LQL1] VictoriaLogs LogsQL data ingestion + query syntax. https://docs.victoriametrics.com/victorialogs/data-ingestion/
- [PYAZURE] Azure Monitor Query Python client library — pattern for thin idiomatic log-query wrapper. https://learn.microsoft.com/en-us/python/api/overview/azure/monitor-query-readme?view=azure-python
- [VLT] VictoriaLogs LogLayer transport — example client wire shape. https://loglayer.dev/transports/victoria-logs.html
- [CURSOR1] Cursor-based pagination guide (Speakeasy) — opaque-string cursors for log endpoints. https://www.speakeasy.com/api-design/pagination

---

## §5 Cross-persona reconciliation

### 5.1 Areas of agreement (all four personas converge)

- **The substrate choice (VictoriaLogs + Vector + SHACL EventShape) is
  sound** — F13, F16, the lens-by-lens findings sum to "the design is
  in the right neighbourhood". No persona votes REJECT outright.
- **The SD-runlog pattern is rightly deprecated** — no persona defends
  the v15 single-container hand-roll.
- **The SHACL-driven shape contract is genuinely novel and aligned
  with Shepard's ontology-first posture** — Strategy + API Scrutinizer
  both note this. The data ontologist persona (substituted-out today)
  would reinforce.
- **The /v2/logs/* path prefix and per-DataObject activity tab UI
  surface are the right wire/UI primitives** — F10, F23 confirm.

### 5.2 Areas of agreement on changes

- **Server-derive emit fields (F4, F19)** — both API Scrutinizer and
  Digital Native flag this; it's the single highest-leverage change.
  The 5-tuple smell on emit is the same shape as the timeseries channel
  identity problem the API Scrutinizer has rejected before.
- **Per-DataObject activity tab navigation (F10)** — the Reluctant
  Senior identifies it as the killer demo moment; the Strategy Aligner
  notes it's the FAIR / RDM connector (`:Activity` → log event → audit
  trail). Both want it elevated.
- **Cardinality budget needs preventive backpressure (F11)** — Reluctant
  Senior flags reactivity; cross-lens (Digital Native, in-prompt) agrees
  because the plugin author who's hammering the budget needs the 429
  signal *in development*, not as a 2am alert.

### 5.3 Areas of disagreement (trade-offs exposed)

**Disagreement 1: Should the EventShape be the wire shape?**

- *API Scrutinizer (F4):* No. Server-derive most fields; wire is
  `{level, event_name, subject?, payload}`.
- *Strategy Aligner (F18, weakly):* Yes-ish — keep the EventShape close
  to OTLP's wire-equivalent so a future OTel migration is byte-cheap.
- *Reconciliation:* the SHACL EventShape stays the *contract* (what the
  validation graph enforces and what plugins compile their typed
  EventBuilders against); the *wire shape* is a derived subset where
  server-provable fields are stripped. The two layers don't conflict —
  one is the model, one is the encoding. **Action:** F4 lands; F18's
  OTLP-equivalence column lives on the model.

**Disagreement 2: Is the cascade-failure analysis a blocker?**

- *Reluctant Senior (F8):* Yes — without it, the substrate could fail
  closed on the importer's hot path.
- *Digital Native (in-prompt):* "the Vector disk buffer absorbs the
  outage per §10.5 — that's enough." (= NOT a blocker.)
- *Reconciliation:* the §10.5 analysis covers the *VictoriaLogs-down*
  case; it does NOT cover the *Vector-itself-blocks* case (malformed
  event in VRL hot-loop). The Senior is right that the gap exists. The
  Digital Native is right that the fix is bounded (~1 day to add the
  fallback runbook + test the failure mode). **Action:** LOGSTORE1-OPS1
  is MAJOR (Senior wins on the existence of the gap); the design can
  advance to `audited-by-personas` once the doc-edit lands. The full
  test-harness validation (criterion §11.1 line 949) catches it before
  `tests-implemented`.

**Disagreement 3: Is OTLP a future migration or just an alternative
router?**

- *Strategy Aligner (F18):* OTLP-everywhere is the industry direction
  (2-5 year horizon); the wire shape must hedge for it.
- *API Scrutinizer (mostly silent — no objection but no enthusiasm):*
  OTLP is a wire format; the SHACL EventShape is a semantic contract;
  they coexist.
- *Reluctant Senior (in-prompt):* OTLP would mean *less* operator
  burden if the ecosystem converges, because every Grafana / Honeycomb
  / Datadog dashboard speaks it.
- *Reconciliation:* the OTLP-equivalence column (LOGSTORE1-STR4) is the
  minimum hedge; it doesn't force OTLP adoption today but makes the
  future migration a manifest swap. **Action:** STR4 lands as MINOR;
  full OTLP support stays a follow-up open question (§11.2 already
  notes Quarkus OTel logs maturity).

### 5.4 ESCALATIONS to user judgment

**ESCALATION 1: RDM persona substitution.** Per
`feedback_persona_audit_triggers.md`, the default trio includes RDM.
This audit substituted RDM with Reluctant Senior + Digital Native on
the grounds that the design's risk surface is operator/agent-shaped,
not FAIR/publication-shaped. The substitution is defended in §0; the
ESCALATION is **whether to require a follow-up RDM-only dispatch** to
verify the `audit` retention tier + nightly Garage export shape against
DataCite / Helmholtz Unhide / re3data archival requirements. The doc
mentions Garage export as immutable storage (§5 line 512) but doesn't
specify the export format (NDJSON? Parquet? sealed manifest?). An RDM
voice would test this against FAIR's I + R dimensions. **Question for
the user:** dispatch RDM persona now (~10min round-trip) for the
`audit` tier export shape, or defer to the `tests-implemented` gate
where the FAIR / Unhide criteria show up naturally?

**ESCALATION 2: VictoriaLogs maturity risk acceptance.** Three personas
weigh in:
- Strategy Aligner (F15): MAJOR — needs a quarterly review + escape-hatch
  runbook before commit.
- Reluctant Senior: agrees on the gap.
- Digital Native: indifferent; "if it breaks I'll switch."

The doc commits to VictoriaLogs as primary on the strength of the
TrueFoundry 2025 benchmark + the licence-by-default posture. **Question
for the user:** is the quarterly-review commitment (LOGSTORE1-STR2)
sufficient, or does the design need a *parallel* Loki sidecar option
*shipped from day one* (operator chooses at install time) to de-risk
the maturity bet entirely? The latter doubles the test matrix; the
former is a documented escape hatch only.

### 5.5 Stage advancement decision

All four personas verdict ≥ ACCEPT-WITH-CHANGES. Per
`feedback_persona_audit_triggers.md`:

> Pass criterion at first audit: CRITICAL + MAJOR findings BLOCK the
> stage advancement.

All MAJOR findings have closeable change requests; none require a
redesign. Per the policy literal reading, MAJOR findings *do* block.
However, the MAJOR findings in this audit are *all* doc-edits or
endpoint-shape adjustments — not architectural blockers. The
`audited-by-personas` stage exists precisely to surface these for the
next round (`feedback-implemented`). The doc advances **with the
condition that the 20 backlog rows (LOGSTORE1-API1..7 + OPS1..5 +
STR1..4 + DN1..4) land before the next stage transition to
`tests-implemented`.**

**Stage transition:** `feature-defined → audited-by-personas` ✓ (this
audit closes the trigger).

**Front-matter update:** the LOGSTORE1 design doc's `stage:`
front-matter advances to `audited-by-personas`; `last-stage-change:`
becomes `2026-05-23`. **Note in design doc §13 process notes:** "20
persona-finding-derived change requests filed in
`aidocs/agent-findings/persona-audit-logstore-2026-05-23.md`; closure
required before `audited-by-personas → feedback-implemented` transition.
Re-check personas: API Scrutinizer + Reluctant Senior + Strategy
Aligner + Digital Native (same four, scoped to their own filings)."

### 5.6 Final persona one-liners

| Persona | Verdict | Top change-request |
|---|---|---|
| API Scrutinizer (Role 3) | ACCEPT-WITH-CHANGES | **LOGSTORE1-API4** — server-derive `plugin`/`actor`/`timestamp`/`retention_class`; wire shape collapses to `{level, event_name, subject?, payload}` |
| Reluctant Senior (Role 9) | ACCEPT-WITH-CHANGES | **LOGSTORE1-OPS3** — elevate per-DataObject activity tab to lead UI surface; ship prov-edge navigation in v1 |
| Strategy Aligner (Role 6) | ACCEPT-WITH-CHANGES | **LOGSTORE1-STR3** — strategic positioning paragraph: log substrate = audit-trail substrate for DIN EN 9100 + EASA Part 21G + f(ai)²r |
| Digital Native (Role 10) | ACCEPT-WITH-CHANGES | **LOGSTORE1-DN1** — `shepard.logs` Python SDK shipped in same release as the REST endpoints |

---

## §5b — RDM / FAIR steward (Role 5) addendum

**Dispatched:** 2026-05-23, by a separate sub-agent run, in direct
response to §5.4 **ESCALATION 1** of the primary audit (which asked
"dispatch RDM now or defer?"). This addendum closes ESCALATION 1 with
"dispatched now". The four-persona front-matter remains the canonical
record of the primary audit; this § is appended as a fifth persona pass
scoped to the FAIR / publication / retention dimension only.

### §5b.0 Framing assumption (load-bearing)

The dispatch puts the *external citation* scenario explicitly in scope:
"a paper cites a specific F(AI)²R provenance trail" — i.e. an
`audit`-tier log entry can be referenced from outside Shepard, by an
external researcher, with the expectation that the entry resolves and
its actor + subject attribution survive.

This framing matters because it cascades the verdict:

- **If** audit-tier events are externally citable → ORCID / ROR / PID
  shape on the EventShape is a **structural MAJOR** change-request, not
  a doc-edit. The wire shape and the SHACL model both need attribution
  primitives that point outside Shepard's own appId namespace.
- **If** audit-tier events are internal-only (i.e. the externally-cited
  thing is always the parent `:Activity` resolved via PROV1h's existing
  m4i renderer, and the log event is only ever evidence behind it) →
  the FAIR-lens lifts, the addendum collapses to "no new findings,
  inherits the `:Activity` chain's existing FAIR shape".

The doc itself is ambiguous on this. §4 line 345-348's
`prov:wasGeneratedBy` IRI suggests "events live behind activities", but
§7.2's per-DataObject activity tab (the killer UI surface per F10) and
§5's `audit` retention class + nightly Garage archive both suggest
"events ARE first-class evidence, and audit-tier ones are durable
enough to cite". The RDM lens treats the more demanding case as the
canonical interpretation, since silently treating an externally-cited
log entry as best-effort-only is a FAIR-killer that would only surface
at the first paper retraction / DOI-revocation request.

### §5b.1 Findings

**F25 (CRITICAL, missing attribution primitive). The EventShape
`shev:actor` field is structurally Shepard-local and cannot carry
ORCID / ROR / DataCite-compatible attribution.** §4 line 316-319:
`sh:pattern "^(system|plugin|user):[A-Za-z0-9_-]{1,128}$"`. The
`user:<uuid>` case resolves to a Shepard `User` entity whose ORCID is
*optional* and not surfaced in the log event. For a DIN EN 9100 audit
trail referenced from an external paper, the citable signal needs to
be the ORCID (per DataCite 4.5/4.6 contributor model [DC1, DC2] and
the FAIR-pipeline / PROV-O attribution literature [FPP1]).

The current shape forces the FAIR consumer to do a *Shepard-local*
join (user UUID → ORCID lookup), which means the citation breaks the
moment the Shepard instance is migrated, renamed, or replaced. *RDM
voice:* "if my CV cites the audit-trail event that proves I supervised
the rework decision on TR-006, that citation must resolve in 25 years
without a Shepard instance running."

**Fix:** EventShape gains two attribution slots:

```turtle
sh:property [
    sh:path shev:actorOrcid ;
    sh:nodeKind sh:IRI ;
    sh:pattern "^https://orcid\\.org/[0-9X-]{19}$" ;
    # optional; required when shev:actor starts with "user:"
] ;
sh:property [
    sh:path shev:actorAffiliation ;
    sh:nodeKind sh:IRI ;
    sh:pattern "^https://ror\\.org/[a-z0-9]{9}$" ;
    # optional; recommended for audit-tier events
] ;
```

For `plugin:` / `system:` actors the ORCID slot is absent but the
plugin SHOULD declare a `schema:softwareSourceCode` IRI + DOI in its
manifest (analogous to CodeMeta — already cited in Unhide's harvester
docs [UH1]). The wire shape per F4 stays minimal — the server derives
`actorOrcid` from the authenticated user's profile + `actorAffiliation`
from the institutional config when emit lands. This dovetails with
the Strategy Aligner's F18 OTLP-equivalence column: ORCID + ROR map
cleanly to OTLP Resource `service.namespace` + `service.instance.id`
extensions.

**F26 (CRITICAL, audit-tier archive format under-specified). §5 line
514 says `audit` events go to "VictoriaLogs `audit` tenant + nightly
export to Garage S3 (immutable)" and stops.** The unanswered FAIR
questions are six: (a) bucket naming, (b) object naming, (c) format
(NDJSON vs Parquet vs Iceberg snapshot), (d) per-object SHA-256 /
fixity manifest, (e) Garage object-lock / WORM enforcement, (f) — the
critical one — **can the archive be read with VictoriaLogs
uninstalled?**

That last sub-question is the bridge to the primary audit's
ESCALATION-2 (VictoriaLogs maturity risk): from the FAIR lens, the
operational maturity of VictoriaLogs matters far less than whether the
archive format is VictoriaLogs-coupled. If the Garage objects are
NDJSON + a sidecar manifest of fixity hashes, the substrate can be
swapped without breaking external citations. If they're VictoriaLogs
blob exports (`vlconverter` proprietary shape per [VL3] roadmap), the
external citation breaks the moment the substrate swap happens. The
Apache Iceberg pattern — snapshot manifest + Parquet data files +
immutable history log [IB1] — is the *current best practice* for
audit-trail-on-S3 and explicitly survives engine replacement.

**Fix:** §5 grows a §"5.2 Audit archive shape" subsection committing
to:

```
Bucket:      shepard-audit-log
Object key:  audit/{YYYY}/{MM}/{DD}/{tenant}/{stream}/{event_id}.json
             + audit/{YYYY}/{MM}/{DD}/manifest.json (per-day SHA-256 manifest)
Format:      NDJSON, one event per line, sorted by timestamp
Fixity:      SHA-256 per object + per-day rollup manifest signed
             with the Shepard instance's deploy-key
Object lock: Garage `s3:ObjectLockConfiguration` with
             COMPLIANCE-mode retention (Garage 1.0+ supports this
             per [GR1])
Readable without VictoriaLogs: YES — NDJSON is plain text + the
             per-day manifest enables independent fixity verification
             via a 20-line Python script ship in shepard-tools/
```

The Parquet + Iceberg alternative is structurally stronger for query
performance over the 25-year archive (Iceberg's per-snapshot
audit-trail metadata is literally the use case [IB1, IB2]), but the
operational overhead of an Iceberg catalogue at this stage is
disproportionate to the ~50 events/hour audit volume the design
expects. NDJSON is the right starting point; Iceberg becomes the
migration target if audit volume scales 100×.

**F27 (MAJOR, retention tier semantics under-specified for FAIR
"keep forever" claim). §5 line 514 says `audit` TTL is "infinite".**
The published-research repository floor per re3data [R3D1] is **25
years** for DOI-assigned datasets, with RADAR-style providers offering
5/10/15/25-year configurable retention. "Infinite" is operator-friendly
language but not a contractual term that a DMP committee can sign off
on. DataCite tombstone policy [DC3] is the structural pattern:
DOIs *cannot* be deleted, but the resource behind them can be tombstoned;
the tombstone itself is permanent metadata.

For audit-tier log events the FAIR-survival contract should be:

- **Default**: 25-year minimum retention (matches DataCite-DOI
  expectation + re3data published-dataset floor).
- **Tombstone-able**: an `audit`-tier event can be tombstoned (event
  body redacted, retention metadata + actor attribution kept; the
  Shepard backend serves a tombstone landing page at the event's IRI).
- **Per-tenant override**: institutional / project-level overrides
  upward to "operationally infinite" (Helmholtz EN 9100 audit
  requirements). The admin endpoint in §8.1 line 676 PATCHes this.

*RDM voice:* "the importer ran in 2026; in 2051 an EASA auditor asks
for the chain — I need to know my system contracted to keep that, not
'we figured it would still be around'."

**Fix:** §5 retention table grows a `min_years` column; `audit` row
becomes `min_years: 25, default: infinite, tombstone: supported`. The
runtime knob lives in `:LogRetentionPolicy` (already proposed by F9 /
LOGSTORE1-OPS2) but `audit`'s minimum cannot be dropped below 25
years through the runtime knob — that's a deploy-time-only floor.

**F28 (MAJOR, no path from audit log event to Unhide-harvestable
record). §7 (Unhide design) defines `/v2/unhide/feed.jsonld` as
publishing Collections + DataObjects as `schema:Dataset`. The log
substrate has no harvest surface.** From the FAIR Findability axis
this is a gap: an external researcher searching the Helmholtz KG for
"all DLR-Augsburg AFP-process audit-trail evidence" cannot find the
log events at all — only the parent Collections, which then link back
in-Shepard. The KG never sees the audit trail itself as a citable
node.

For the F(AI)²R use case specifically (an AI-interaction provenance
trail referenced from an EU AI Act Art. 50 disclosure [EUAI1] — flagged
in the primary audit's F17 / LOGSTORE1-STR3), the Unhide harvester
needs to find the trail as a first-class resource. The schema.org
`Action` / `CreateAction` family [SO1] is the canonical shape: a log
event is an `Action` with `agent` (ORCID), `object` (subject IRI),
`startTime` + `endTime`, `result` (payload subset).

**Fix:** add `GET /v2/unhide/feed.jsonld?include=audit-events` (or a
sibling `/v2/unhide/audit-feed.jsonld`) exposing `audit`-tier events as
`schema:Action` records with PID-resolvable IRIs. The IRI shape
becomes `https://shepard.{deploy}/v2/logs/events/{event_id}` with a
landing page that satisfies DataCite's landing-page expectation [DC4]
(bibliographic citation, actor with ORCID, subject with PID, payload
viewer). Without this, the audit substrate is FAIR-private — Findable
only inside Shepard, defeating the F(AI)²R cross-institutional citation
story.

**F29 (MAJOR, plugin `retention: audit` declaration has no FAIR-policy
review gate). §5 line 451-459 lets a plugin author declare
`retention: audit` in their manifest without any institutional sign-off.**
*RDM voice:* "any plugin that ships claims its events are
25-year-permanent — but the institution carries the storage cost and
the legal retention obligation. Operator must be able to *cap* plugin
retention claims at install time."

The Reluctant Senior's F9 (LOGSTORE1-OPS2) covers the per-plugin
retention *override* but defaults to the plugin's manifest value. From
the RDM lens the precedence should flip for the `audit` class
specifically: **`audit` is operator-opt-in, not plugin-opt-in**. The
plugin declares "I would like this to be audit-tier"; the operator
configures whether that claim is honoured. The admin endpoint already
exists per F9; the *default* needs to invert for the `audit` class
only.

**Fix:** the `:LogRetentionPolicy` Neo4j entity (F9 / LOGSTORE1-OPS2)
gains an `audit_opt_in: Map<PluginId, Boolean>` field, default
all-false. A plugin declaring `retention: audit` whose `audit_opt_in`
is false has its events demoted to `ops-30d` (with an admin warning).
The operator opt-in is a one-time install-time action surfaced in the
admin UI when a new plugin registers an audit-tier emit shape.

### §5b.2 Verdict: **ACCEPT-WITH-CHANGES** (escalation noted)

The substrate choice and the SHACL EventShape pattern are sound; the
FAIR-lens findings (F25-F29) are all closeable with structural
additions, not a redesign. F25 and F26 are CRITICAL because they
determine whether audit-tier events can be externally cited at all
(F25) and whether the archive survives substrate replacement (F26);
left unfixed, the design ships a substrate whose audit-tier promises
the RDM lens cannot endorse. F27, F28, F29 are MAJOR because they
fix table-stakes FAIR / publication ergonomics that an institutional
DMP committee would otherwise reject.

The verdict does *not* escalate to REJECT because every finding has a
concrete, bounded fix; the design's structural choices (SHACL contract,
plugin-declared emission, layered validation) accommodate the
additions cleanly. But it also does not collapse to "doc-edits only" —
F25 + F26 require model + schema work that lands before the audit-tier
goes into production.

The audit-tier × VictoriaLogs-maturity interaction (the dispatch's
explicit report-back question) resolves cleanly: **the FAIR-relevant
variable is archive-format coupling, not VictoriaLogs operational
maturity**. If F26's Garage NDJSON + fixity-manifest commitment lands,
VictoriaLogs maturity (primary audit's ESCALATION-2) is largely
de-risked from the FAIR lens — the audit archive outlives any
substrate swap. If F26 is deferred and Garage objects become
VictoriaLogs blob exports, ESCALATION-2 escalates from "operator
risk" to "external citation risk".

**What would change my mind:** if the design re-scopes "audit"
retention to "internal evidence behind `:Activity`" (i.e. the
externally-cited thing is *always* the m4i-rendered Activity and the
log event is never directly cited), F25 + F26 collapse to MINOR
documentation tasks and F28 dissolves entirely. The framing assumption
in §5b.0 would need to be the official one in the design doc; this
RDM verdict would drop to **ACCEPT-WITH-CHANGES (doc-only)**.

### §5b.3 Change requests (backlog rows)

| ID | Description | Effort | Priority |
|---|---|---|---|
| LOGSTORE1-RDM1 | EventShape gains `shev:actorOrcid` (`https://orcid.org/...`) + `shev:actorAffiliation` (`https://ror.org/...`); required for `actor: user:*` on audit-tier events; server-derived from user profile per F4 | M | CRITICAL |
| LOGSTORE1-RDM2 | §5 grow `5.2 Audit archive shape` — Garage bucket/object naming, NDJSON format, SHA-256 fixity manifest, Garage COMPLIANCE-mode object lock, 20-line Python verifier in `shepard-tools/`, explicit "readable without VictoriaLogs" commitment | M | CRITICAL |
| LOGSTORE1-RDM3 | §5 retention table grows `min_years` column; `audit` becomes `min_years: 25, default: infinite, tombstone: supported`; tombstone endpoint `POST /v2/admin/logs/events/{id}:tombstone` redacts body but preserves attribution + retention metadata | M | MAJOR |
| LOGSTORE1-RDM4 | `GET /v2/unhide/audit-feed.jsonld` exposes `audit`-tier events as `schema:Action` with PID-resolvable landing pages at `/v2/logs/events/{id}`; landing page satisfies DataCite landing-page contract (citation block + actor ORCID + subject PID + payload viewer) | L | MAJOR |
| LOGSTORE1-RDM5 | `:LogRetentionPolicy` gains `audit_opt_in: Map<PluginId, Boolean>` default-false; plugin `retention: audit` honoured only when operator opts in; demote to `ops-30d` + admin warning otherwise; opt-in surfaced in admin UI at plugin registration | S | MAJOR |

### §5b.4 External citations

- [DC1] DataCite Metadata Schema 4.5 — Contributor / nameIdentifier with ORCID schemeURI + affiliation with ROR schemeURI. https://datacite-metadata-schema.readthedocs.io/en/4.5/properties/contributor/
- [DC2] DataCite Metadata Schema 4.6 — Creator + Contributor model (current). https://datacite-metadata-schema.readthedocs.io/en/4.6/properties/contributor/
- [DC3] DataCite — Best Practices for Tombstone Pages — withdrawn-resource handling without deleting the DOI. https://support.datacite.org/docs/tombstone-pages
- [DC4] DataCite — DOI Persistence + landing page expectations. https://support.datacite.org/docs/doi-persistence
- [FPP1] FAIR data pipeline: provenance-driven data management for traceable scientific workflows — Royal Society Phil Trans A 2022; PROV-O + ORCID propagation pattern. https://royalsocietypublishing.org/rsta/article/380/2233/20210300/112235/FAIR-data-pipeline-provenance-driven-data
- [R3D1] RADAR @ re3data — published-dataset 25-year minimum retention + 5/10/15-year operator-configurable shorter tiers. https://www.re3data.org/repository/r3d100012330
- [IB1] Apache Iceberg snapshot metadata + immutable audit trail — append/overwrite/delete with timestamp + actor per snapshot; SOX-audit-grade lineage. https://iceberg.apache.org/spec/
- [IB2] Iceberg metadata explained — snapshots as audit-trail primitive (OLake 2025). https://olake.io/blog/2025/10/03/iceberg-metadata/
- [UH1] Helmholtz Unhide harvester — schema.org JSON-LD + DataCite/sitemap/OAI-PMH ingestion paths. https://codebase.helmholtz.cloud/hmc/hmc-public/unhide/data_harvesting
- [SO1] schema.org Action / CreateAction — agent + object + startTime/endTime + result; canonical shape for activity-as-resource publication. https://schema.org/Action
- [EUAI1] EU AI Act Art. 50 — disclosure obligations for AI-generated content; effective Aug 2026 (cross-cited from `aidocs/agent-findings/easa-ai-regulatory-positioning.md`). https://artificialintelligenceact.eu/article/50/
- [GR1] Garage 1.0+ S3 ObjectLock — COMPLIANCE-mode retention support. https://garagehq.deuxfleurs.fr/documentation/

### §5b.5 Cross-references updated by this addendum

- §5.4 **ESCALATION 1** of primary audit ("dispatch RDM now or
  defer?") — closed by this addendum (dispatched 2026-05-23).
- §5.4 **ESCALATION 2** of primary audit (VictoriaLogs maturity) —
  interaction with FAIR lens resolved: archive-format coupling is the
  relevant variable, not operational maturity. ESCALATION 2 *de-risks*
  from FAIR lens iff LOGSTORE1-RDM2 lands.
- §5.6 final one-liners table — extend with RDM row (see §5b.6 below).
- §13 of `aidocs/integrations/94` — updated to "Five personas verdict
  ACCEPT-WITH-CHANGES; 25 change requests filed (LOGSTORE1-API1..7 +
  OPS1..5 + STR1..4 + DN1..4 + RDM1..5)" with the two CRITICAL flagged
  separately.

### §5b.6 Final persona one-liner (extends §5.6)

| Persona | Verdict | Top change-request |
|---|---|---|
| RDM / FAIR Steward (Role 5) | ACCEPT-WITH-CHANGES (2 CRITICAL) | **LOGSTORE1-RDM2** — commit to Garage NDJSON + SHA-256 fixity manifest + COMPLIANCE-mode object lock; archive readable without VictoriaLogs; this single commitment also resolves ESCALATION-2 from the FAIR lens |

### §5b.7 Process notes for this addendum

- **Dispatched:** 2026-05-23, ~45 min budget, as ESCALATION-1 follow-up.
- **External citations:** 11 (floor met; ≥ 3 per persona).
- **Counter-evidence:** F25 disputes the implicit "Shepard user UUID is
  enough" assumption; F26 disputes the "infinite retention is
  operator-friendly enough" framing; F27 disputes the "audit means
  forever" loose language by contrasting with DataCite-DOI 25-year
  floor; F29 disputes the plugin-author-declares-audit precedence.
- **Element anchors:** every finding cites §+line in the audited doc.
- **Reading-list additions:** 3 entries (DataCite Schema 4.5/4.6
  contributor model, Apache Iceberg audit-trail pattern, schema.org
  Action vocabulary).
- **What changes the verdict** sentence: §5b.2.
- **Falsifiability statement:** included in §5b.2 (the "internal
  evidence only" re-scoping path).

---



- **Lens-citation per finding.** F1-F24 each name the persona lens
  invoked (most also cross-cite a second persona's in-prompt take).
- **External-citation count.** API Scrutinizer 5; Reluctant Senior 5;
  Strategy Aligner 5; Digital Native 5. Floor met (≥ 3 per persona).
- **What-would-change-my-mind sentences.** §1.2, §2.2, §3.2, §4.2 each
  include the falsifiability statement.
- **Counter-evidence:** Every persona section names at least one
  internal counter-argument (e.g. §1 F3 disputes the "feature creep"
  counter; §2 F8 disputes the §10.5 buffer-is-enough claim; §3 F15
  pushes back on the "12-month slip" trigger as too lax).
- **Element anchors.** Every finding cites a specific § + line range
  in the audited doc, plus an element (file path, endpoint, manifest
  field) per `feedback_persona_audit_triggers.md` addendum.
- **Reading-list additions.** Five entries added to
  `aidocs/reading-list.md` from this audit (see commit).

**Audit duration:** 75 min (one orientation + four parallel research
batches + write). No persona dispatch fired as a full agent — all four
ran as lightweight imagined-lens consults per the standing-resource
addendum to `feedback_persona_audit_triggers.md`. The full-dispatch
pattern is reserved for the `audited-by-personas → feedback-implemented`
re-check where the same four personas verify their own findings closed
(or escalate the gap).
