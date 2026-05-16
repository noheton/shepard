# Provenance and Activity Overhaul — Design

**Scope.** Pivot the long-implicit "activity log" idea into a
**first-class provenance surface** backed by the W3C PROV-O
vocabulary (already pre-seeded per `aidocs/48`), with a
casual-user-visible **dashboard** (counts, byte-volumes, sparkline)
on top. Closes the gap between "shepard tracks `createdAt` /
`createdBy` per entity" (the entire current story) and "shepard
shows me what happened in this Collection last week."

**Status.** Concept design.
**Snapshot date.** 2026-05-12.
**Audience.** Contributors. Pivots the activity-log idea into a
PROV-O-backed provenance surface with a casual-user dashboard.
**Originating items.** User brief: "activity log overhaul. / maybe
more in direction provenance? ,provenance / maybe stats about
activity x mb timeseries, y gb files captured total, sparkline like
commits?" Couples to: `aidocs/48` (PROV-O bundled in n10s
pre-seed), `aidocs/30` (broader operational/derivation/publication
lineage exploration — this design implements the *operational*
slice of `aidocs/30 §2.1`), `aidocs/42` (vision — provenance moves
from mid-horizon bullet to in-the-box feature), `aidocs/51`
(instance-admin role gates the instance-level dashboard).

---

## 1. Today

shepard has almost no activity tracking. The entire surface area:

| Source | What it records | Surfaced where |
|---|---|---|
| `AbstractEntity.createdAt` / `updatedAt` (`@DateLong` on every `:BasicEntity`) | First-create + last-update timestamps | API; "Created N days ago" label |
| `AbstractEntity.createdBy` / `updatedBy` (`User` relationships) | Username of last creator/updater | API; "Created by …" label |
| `LoggingFilter` | Request-line stdout logging (method, path, status) | Process logs — nothing persistent |
| `PermissionAuditEntry` (post-A0, `aidocs/51 §10`) | One-shot scan for orphan `:BasicEntity` lacking `:has_permissions` | `GET /v2/admin/permission-audit` |

Three concrete gaps a researcher hits:

1. **"What did my collaborator change last week?"** No answer.
   `updatedAt` shows *that* it changed, not *what*, not *how
   often*. Consecutive edits collapse into "last touched today."
2. **"How much data does this Collection contain?"** No answer.
   Per-DataObject sizes show on click; no aggregate "this campaign
   is 4.2 GB across 1843 DataObjects."
3. **"Is shepard being used?"** No instance-wide activity summary,
   no usage curves, no "GB of files captured this quarter."

The brief wants both: a **provenance trail** ("who did what
when") and a **stats dashboard** ("how much, sparkline"). Same
backing data, two view shapes.

`aidocs/30` is the broader operational + derivation + publication
lineage design. **This design ships the operational slice** with
the casual-user dashboard the brief asks for. Derivation and
publication stay in `aidocs/30`'s R3a–R3e scope.

---

## 2. The pivot — from "activity log" to "provenance"

An "activity log" is what the casual user calls it. A **provenance
graph** is what we actually build:

1. **The W3C standard already exists.** PROV-O defines
   `prov:Entity` (data), `prov:Activity` (event), `prov:Agent`
   (actor), and the three core edges (`USED`, `GENERATED`,
   `WAS_ASSOCIATED_WITH`). Every research-data community speaks
   it. RO-Crate exports already cite it.
2. **PROV-O is already bundled** per `aidocs/48 §4` (3 MB Turtle,
   IRI prefix `http://www.w3.org/ns/prov#`). Annotations on
   entities can reference PROV-O terms today; activities are the
   missing primitives that *should* be PROV-O-shaped from day one.
3. **shepard's primitives map cleanly.** Collection / DataObject /
   References = PROV-O Entities. User actions = PROV-O Activities.
   Users + service accounts (sTC, future PR1 runtimes, bootstrap
   admin) = PROV-O Agents. One-to-one, no impedance.
4. **One model serves both surfaces.** Provenance trail is a graph
   traversal; stats dashboard is an aggregation over the same
   nodes. One store.

The pivot in one sentence: **every mutation generates a PROV-O
`Activity` node linked to the affected `Entity` and the acting
`Agent`; the casual user sees "what happened" + a sparkline; the
API exposes it under `/v2/provenance/`.**

Captures the **WHO, WHAT, WHEN** for every mutation. Does *not*
capture derivation (`aidocs/30 §2.2`) or publication
(`aidocs/30 §2.3`); both ride the same `:Activity` shape later.

---

## 3. Data-model shape

### 3.1 New `:Activity` Neo4j entity

```java
@NodeEntity
public class Activity implements HasAppId {
  @Property private Date startedAtTime;       // PROV-O prov:startedAtTime
  @Property private Date endedAtTime;         // PROV-O prov:endedAtTime
  @Property private ActionKind actionKind;    // CREATE | READ | UPDATE | DELETE | EXECUTE
  @Property private String targetKind;        // "Collection" | "DataObject" | "FileReference" | ...
  @Property private String targetAppId;       // appId of the affected entity (post-L2)
  @Property private String summary;           // human-readable: "Added DataObject 'TR-006' to Collection 'LUMEN-Q3'"
  @Property private long payloadDeltaBytes;   // for ingest activities: bytes written; 0 for metadata-only
  @Property private long payloadDeltaPoints;  // for timeseries ingest: points written; 0 otherwise
  // appId / createdAt etc. inherited from HasAppId/AbstractEntity
}
```

`ActionKind` is a small closed enum. `targetKind` is a string
because new payload kinds drop in via the `aidocs/47` PL1 plugin
SPI — the enum-vs-string tension resolves in favour of the
extension-friendly shape. `targetAppId` references the affected
entity by **appId** (post-L2a), not by `id()` — keeps the model
restartable across re-indexes and survives the L2d cutover.

`payloadDeltaBytes` / `payloadDeltaPoints` are the dashboard's
arithmetic foundation. Empty (0) for non-ingest activities
(permission changes, lab-journal edits, etc.).

### 3.2 Edges (the PROV-O core)

| Edge | PROV-O term | Cardinality | Meaning |
|---|---|---|---|
| `(:Agent)-[:WAS_ASSOCIATED_WITH]->(:Activity)` | `prov:wasAssociatedWith` | 1 per activity | The user / service-account / API-key that did it |
| `(:Activity)-[:USED]->(:Entity)` | `prov:used` | 0..n per activity | Entities the activity *read* (only populated when `capture-reads=true`) |
| `(:Activity)-[:GENERATED]->(:Entity)` | `prov:generated` | 0..n per activity | Entities the activity *created or modified* |
| `(:Activity)-[:WAS_INFORMED_BY]->(:Activity)` | `prov:wasInformedBy` | 0..n per activity | Chained activities — "this export was informed by yesterday's ingest" |

`(:Agent)` here is **not** a new node label — it's a view: the
existing `User` node, plus future `ServiceAccount` nodes (sTC,
bootstrap, future PR1 process runtimes). The `:WAS_ASSOCIATED_WITH`
edge points at whichever node represents the actor; the connector
projects both shapes as PROV-O `prov:Agent`.

`(:Entity)` is the affected `:BasicEntity` (Collection, DataObject,
Reference, Annotation, LabJournalEntry, …). The `:USED` and
`:GENERATED` edges land on the existing Neo4j nodes — no
duplication.

### 3.3 Worked example

A user `alice@dlr` uploads a 24 MB file to an existing DataObject:

```
(alice:User {username: "alice@dlr"})
  -[:WAS_ASSOCIATED_WITH]->
(a:Activity {actionKind: CREATE, targetKind: "FileReference",
             summary: "Uploaded 'sensor-dump-tr006.bin' (24 MB)",
             payloadDeltaBytes: 25165824, ...})
  -[:GENERATED]-> (fileref:FileReference)
  -[:WAS_INFORMED_BY]-> (prior:Activity {summary: "Created DataObject 'TR-006'"})
```

The dashboard rolls up `payloadDeltaBytes` across activities in
the last 90 days, grouped by day, and emits the sparkline. The
provenance trail walks `(:Activity)-[:WAS_INFORMED_BY*]-(:Activity)`
to reconstruct the chain.

---

## 4. Capture surface — where do activities get logged?

Three layers, in order of cleanness:

### 4.1 JAX-RS request-filter on mutating endpoints (recommended primary)

A new `ProvenanceCaptureFilter implements ContainerResponseFilter`
in `backend/src/main/java/de/dlr/shepard/provenance/filters/`,
ordered **after** `LoggingFilter` and **after** the permission
filters so denied requests don't generate activities.

The filter stashes `startedAtTime` + `principal` on the request
context; on a 2xx response to a `POST` / `PUT` / `PATCH` /
`DELETE`, it builds an `Activity`, links `:WAS_ASSOCIATED_WITH`
to the principal's `User`, and links `:GENERATED` to the entity
in the response body. Writes are **best-effort, non-blocking** —
the response is still 2xx if the activity persist fails. Provenance
is a derived feature; it must not break the underlying operation.

**Target-resolution.** `@Path` segment supplies `targetAppId`;
a new `@ProvenanceTarget(kind = "Collection")` annotation on the
JAX-RS method supplies `targetKind`. Endpoints where the target
isn't path-segment-discoverable (bulk create, batch ingest) emit
explicitly via the service-layer hook (§4.3).

### 4.2 Read-capture is opt-in

Reads are **off by default**. The casual user doesn't want
"alice viewed Collection X" rows flooding the provenance trail;
the disk-budget math (§7) only stays sane if reads are excluded
from the default install.

Configuration knob:

```
shepard.provenance.capture-reads=false   # default
```

Operators who need read-capture for compliance flip it to
`true`; the filter then writes `actionKind=READ` activities on
`GET` responses. `USED` edges replace `GENERATED` edges for
read activities. Compliance-heavy deployments (medical, EU
research-with-PII) are the expected consumers.

### 4.3 Service-layer instrumentation for non-REST flows

Some mutations don't enter via JAX-RS:

| Non-REST mutation | How captured |
|---|---|
| sTC ingest | Calls `ProvenanceService.recordIngest` directly. Agent is the API-key-bound service account. |
| `MigrationsRunner` | One synthetic `Activity` per migration with `agent = "migration"`. Admin sees the system's own history alongside user history. |
| RO-Crate export (`aidocs/31`) | `ExportService` emits an `Activity` `:WAS_INFORMED_BY`-linked to every `:Activity` that touched the included entities (publication-lineage primer per `aidocs/30 §2.3`). |
| Future PR1 process runs | One parent `Activity` per process + one per step, chained via `:WAS_INFORMED_BY`. |

Single in-process API:
`ProvenanceService.record(Activity a, Agent agent, List<Entity> generated, List<Entity> used)`.
Idempotent — best-effort dedupe by
`(agent, targetAppId, startedAtTime, actionKind)` within 1 s to
protect against retry storms.

### 4.4 Storage-volume estimate

A typical shepard install (per `aidocs/40`'s deployment survey) sees
**<1k mutating writes / day** across all users. Sizing:

- One `Activity` node ≈ 200 bytes property data + 3 edges ≈
  ~500 bytes Neo4j on-disk.
- 1k/day × 365 = 365k activities/year × 500 bytes ≈ **~180 MB/year**.
- Under 1 GB/year even on a noisy install.

Compared to TimescaleDB volumes (multi-GB-per-month per heavy
campaign) this is negligible. With reads captured, multiply by
~50× and the budget is still acceptable for the default-2-year
retention — but cardinality is why reads stay off by default.

---

## 5. Casual-user visibility — the dashboard

The brief's central ask: **stats and sparklines, commit-graph
style**. Two surfaces.

### 5.1 Per-Collection dashboard

Rendered on the Collection detail page (Nuxt 3 / Vuetify 3),
gated to users with read-access to the Collection. Headline:

> **Last 90 days** — 42 DataObjects added — 3.2 GB files
> captured — 18 M timeseries points — 4 contributors active

Plus a **commit-graph-style sparkline**: a 12-month calendar
heatmap (52 columns × 7 rows), each cell coloured by that day's
activity count. Hover shows "12 May 2026 — 8 activities by 2
contributors." Modelled after GitHub's contribution graph, which
the casual researcher already knows. Plus a **leaderboard** of
top contributors in the window (`effectiveDisplayName` per
`aidocs/36 §3`), plus a **recent activity feed** of the latest
20 entries — drill-in to the full provenance trail.

One collapsible section on the Collection page, default open.

### 5.2 Instance-level admin dashboard

Gated on `@RolesAllowed("instance-admin")` per `aidocs/51`.
Rendered on a new `/admin/dashboard` route. Headline:

> **All-time** — X MB timeseries — Y GB files — Z DataObjects
> across N Collections — M users active in last 30 days

Plus the same 12-month sparkline aggregated across the whole
instance, a **top-active-Collections** leaderboard (useful for
storage-quota conversations), and a **dormant-Collections** list
(no activity in last 180 days — quota-reclamation hint). Ships
only after A0/C3/F8 (`aidocs/51`) land.

### 5.3 Backed by aggregation Cypher + pre-rollups

Both dashboards are backed by `Activity`-node aggregations.
Naïve query for the 90-day Collection dashboard:

```cypher
MATCH (c:Collection {appId: $cid})<-[:GENERATED|USED*1..3]-(a:Activity)
WHERE a.startedAtTime >= datetime() - duration({days: 90})
RETURN
  count(a)                                      AS activityCount,
  sum(a.payloadDeltaBytes)                      AS bytesCaptured,
  sum(a.payloadDeltaPoints)                     AS pointsCaptured,
  count(DISTINCT a.targetAppId)                 AS entitiesTouched,
  size(collect(DISTINCT a))                     AS sparklineRaw
```

On a small install this is fast. On an install with 100k+
activities and deep Collection trees it isn't — see §7's
pre-aggregation rollups.

---

## 6. REST surface — `/v2/provenance/...`

All new endpoints land under `/v2/` per `CLAUDE.md` API-version
policy. None under `/shepard/api/...`.

| Method + path | Purpose |
|---|---|
| `GET /v2/provenance/activities` | List activities; filters: `agent`, `entity`, `from`, `to`, `actionKind`, `targetKind`. Default: last 30 days, descending. Cursor-paginated per `aidocs/13`. |
| `GET /v2/provenance/activities/{appId}` | Single activity drill-down — full PROV-O shape including `:USED` / `:GENERATED` / `:WAS_INFORMED_BY` neighbours. |
| `GET /v2/provenance/entity/{entityAppId}` | Full provenance trail for one entity — its history of mutations, who touched it, in chronological order. The "what happened to TR-004?" answer. |
| `GET /v2/provenance/stats?scope=collection&id=<appId>&window=90d` | Per-Collection stats payload — counts, bytes, points, contributors, sparkline buckets (one row per day in the window). |
| `GET /v2/provenance/stats?scope=instance&window=12m` | Instance-wide stats — gated on `@RolesAllowed("instance-admin")`. |
| `GET /v2/provenance/stats?scope=user&id=<username>&window=30d` | Per-user "what did I do last week?" — see §10 open question. |

### 6.1 Content negotiation — PROV-N JSON

Default `Accept: application/json` returns a shepard-native shape
optimised for the dashboard frontend (`appId`, `actionKind`,
`targetKind`, `agent`, `summary`, `startedAtTime`,
`payloadDeltaBytes`, plus `generated` / `used` / `wasInformedBy`
appId arrays).

`Accept: application/prov+json` returns the **W3C PROV-N JSON
serialisation** for downstream interop — RO-Crate exporters,
federated provenance catalogues, OpenLineage adapters. Same data,
PROV-O-canonical shape with full IRIs. Coupling to `aidocs/48` is
direct: the IRIs reference `http://www.w3.org/ns/prov#` terms
already loaded in the internal semantic repository, so a SPARQL
query against the n10s endpoint resolves them. PROV-N (the compact
RDF text serialisation) is **deferred**; the JSON-LD-flavoured
`application/prov+json` covers the machine-readable need for v1.

---

## 7. Storage size + retention

### 7.1 Retention policy

Default `shepard.provenance.retention-days=730` (2 years). A
nightly `@Scheduled` job DELETE-s activities older than the
window. Compliance deployments bump the value or set `-1` for
"keep forever" (explicit, audited). The dashboard's sparkline
window is independent of retention.

### 7.2 Pre-aggregated rollups

The dashboard's sparkline needs **day-bucket counts** over 12
months — 365 aggregation cells per Collection per dashboard load.
On a 100k-activity install, naïve aggregation takes seconds. To
stay sub-second:

```java
@NodeEntity
public class ActivityRollup implements HasAppId {
  @Property private String scope;                // "collection" | "instance" | "user"
  @Property private String scopeId;              // collection appId / username / "" for instance
  @Property private Date bucketDate;             // truncated to day (UTC)
  @Property private long activityCount;
  @Property private long bytesCaptured;
  @Property private long pointsCaptured;
  @Property private int distinctContributors;
}
```

A nightly job (`@Scheduled` every `PT1H` for the current day,
nightly for closed days) aggregates `:Activity` → `:ActivityRollup`
per (scope, scopeId, day). The dashboard reads from
`:ActivityRollup` (1 row per day in the window) instead of
walking `:Activity` (1 row per mutation).

This keeps the dashboard query sub-second on installs with
multi-million activity counts. Trades a small write cost (one
upsert per active scope per day) for a large read win.

### 7.3 Migration

Two new Cypher migrations under
`backend/src/main/resources/neo4j/migrations/`:

- `V##__Add_appId_constraint_Activity.cypher` — unique constraint
  on `:Activity(appId)`.
- `V##__Add_appId_constraint_ActivityRollup.cypher` — unique
  constraint on `:ActivityRollup(appId)` + composite index on
  `(scope, scopeId, bucketDate)` for the dashboard query.

Both idempotent (`IF NOT EXISTS`) per `CLAUDE.md`. Rollback files
`V##_R__*.cypher` ship alongside (data is derived, safe to drop).

---

## 8. Privacy / compliance

Activity rows reference the user's `appId` (or `username`).
That has GDPR / right-to-erasure implications.

### 8.1 User deletion

Recommendation: on user deletion (post-U1, `aidocs/36`),
**anonymise the activity rows** rather than hard-delete. The
`:WAS_ASSOCIATED_WITH` edge re-targets from `(user:User)` to
`(:Agent {username: "anonymous"})` — the activity stays
(provenance trail of "something happened on this date") but the
actor identity goes. This is the GDPR-recital-26 read of
"pseudonymisation sufficient for non-sensitive operational
records." Compliance-heavy deployments override with
`shepard.provenance.on-user-delete=hard-delete-activities`.
Default is **anonymise**.

### 8.2 What activities don't capture

Per `aidocs/07 §C2 / §H8`: no **request bodies** (the `summary`
field is server-generated from method + target metadata, never
echoed user payload — avoids logging unredacted PII / secrets);
no **request headers** (no IP, no user-agent, no auth tokens —
the IP-and-UA story belongs in F3's security audit log per
`aidocs/24`); no **permission diffs** (permission changes generate
one `actionKind=UPDATE` + `targetKind=Permissions` activity; the
who-gained-what diff lives in F3's tamper-evident sink).

### 8.3 Permission-aware reads

Provenance queries are **filtered by the caller's read-access to
the target entity** — same shape as `PermissionsService`'s
`filterAllowedForUser` (post-P2). A user only sees activities
whose target they can read. Instance-admin sees everything.

---

## 9. Phasing — PROV1 series

| ID | Slice | Size | Gate |
|---|---|---|---|
| **PROV1a** | `:Activity` Neo4j entity + `ActivityDAO` + `ProvenanceCaptureFilter` (JAX-RS) on mutating endpoints + `ProvenanceService.record(…)` service hook + `V##__Add_appId_constraint_Activity.cypher` migration. Best-effort writes; no query surface yet. | M | L2a (appId on entities) |
| **PROV1b** | Query endpoints: `GET /v2/provenance/activities`, `GET /v2/provenance/activities/{appId}`, `GET /v2/provenance/entity/{entityAppId}`. PermissionsService filtering. Cursor pagination. | M | PROV1a + P2 (cypher-first permission filter) |
| **PROV1c** | Pre-aggregation rollup job (`@Scheduled` nightly + hourly-current-day) + `:ActivityRollup` entity + migration + `GET /v2/provenance/stats?scope=collection` + `GET /v2/provenance/stats?scope=instance`. Instance-stats endpoint gated on `@RolesAllowed("instance-admin")`. | M | PROV1b + A0 (instance-admin role per `aidocs/51`) |
| **PROV1d** | Frontend per-Collection dashboard — Vue component with headline stats, 12-month sparkline heatmap (lightweight SVG, no Chart.js dep), top-contributors leaderboard, recent-activity feed. Collapsible section on Collection detail page. | M | PROV1c + frontend route conventions |
| **PROV1e** | Instance-level admin dashboard at `/admin/dashboard` — same component shape, scope=instance. Top-active-Collections + dormant-Collections panels. | S | PROV1d + A0 |
| **PROV1f** | Retention job — `@Scheduled` nightly, deletes activities older than `shepard.provenance.retention-days`. User-anonymisation hook on user-delete. Documented `hard-delete-activities` config alternative. | S | PROV1a |
| **PROV1g** | PROV-N JSON content-negotiation — `Accept: application/prov+json` on every `/v2/provenance/...` endpoint. Maps shepard-native shape → W3C PROV-O IRIs. Couples to `aidocs/48` n10s for IRI resolution. | M | PROV1b + N1b (pre-seeded PROV-O via `aidocs/48`) |

Recommended order: **PROV1a → PROV1b → PROV1c → PROV1d**.
PROV1a is capture; PROV1b is API; PROV1c is rollup
infrastructure; PROV1d is the casual-user dashboard the brief
asks for. PROV1e adds the instance-admin view once A0 is in.
PROV1f is hygiene. PROV1g is the downstream-interop hook. The
headline-feature slice an outsider can test is **PROV1a +
PROV1b + PROV1c + PROV1d**.

---

## 10. Open questions for the maintainer

1. **PROV-O vs simpler home-grown vocab?** **Recommendation:
   PROV-O.** Bundled per `aidocs/48 §4`, RO-Crate exports already
   speak it, downstream tools (Marquez, OpenMetadata) consume it
   natively. The Java field names align with PROV-O terms
   one-to-one. Home-grown is faster on day one and costs everyone
   forever after.
2. **Capture reads by default?** **Recommendation: off.**
   Cardinality is the issue (50× volume amplification per §4.4).
   Compliance-heavy deployments opt in via
   `shepard.provenance.capture-reads=true`.
3. **Where does the sparkline render in the existing UI?**
   **Defer to a UI-design pass during PROV1d.** The Collection
   detail page already has a tab layout — the dashboard can be a
   new tab or a collapsible top-section. `aidocs/33 §W*` weighs
   in before the code lands.
4. **Per-user "what did I do last week?" page for v1?**
   **Recommendation: yes, but tiny.** A `/me/activity` page that
   reuses the dashboard component with `scope=user&id=<self>`.
   No new endpoints. Low cost, high "huh, neat" factor.
5. **Should derivation-lineage (`aidocs/30 §2.2`) ride this
   slice?** **No.** Derivation adds optional DTO fields across
   every payload kind — meaningful PR cost. Defer to R3a once
   PROV1d is shipped.
6. **Should publication-lineage (RO-Crate `provenance.json`)
   ride this slice?** **No.** The `:Activity` node landing
   under PROV1a is the prerequisite; the RO-Crate emission hook
   belongs in `aidocs/31` + `aidocs/30` R3c.
7. **Service-account modelling for sTC + future PR1?** Keep the
   today-shape for v1 — the activity's agent is whatever `User`
   the API key resolves to. A first-class `:ServiceAccount` node
   is an `aidocs/47 §3` PL-series conversation.

---

## 11. Cross-references

- **`aidocs/48`** — PROV-O bundled in the n10s pre-seed (§4
  table). `application/prov+json` references those IRIs; n10s
  resolves them at query time.
- **`aidocs/42`** — vision currency. PROV1d ships the dashboard;
  the §"Where it's going" provenance bullet moves to §"What's in
  the box (today)" in the same PR.
- **`aidocs/51`** — A0 instance-admin role gates the
  instance-level dashboard (PROV1e) and `scope=instance` stats.
- **`aidocs/30`** — broader operational + derivation + publication
  lineage exploration. **This design implements §2.1 (operational)
  only.** Derivation and publication ride the same `:Activity`
  shape when they ship under R3a–R3e.
- **`aidocs/16`** — PROV1a–PROV1g rows land here via the
  dispatcher (not edited by this design directly per `CLAUDE.md`).
- **`aidocs/34`** — three new tracker rows (config-aware:
  `shepard.provenance.capture-reads`, `…retention-days`,
  `…on-user-delete`).
- **`aidocs/44`** — feature-matrix update under §Provenance.
- **`aidocs/47`** — PL1 plugin SPI shapes new payload kinds'
  `targetKind` strings; design assumes string-not-enum to stay
  extension-friendly.
- **`aidocs/24`** — F3 audit-trail is the **sibling** (security
  permission diffs); provenance captures only the fact of a
  `targetKind=Permissions` update.
- **`aidocs/36`** — `effectiveDisplayName` is what the
  leaderboard renders; `/me/activity` couples to the `/me` shell.
- **`aidocs/49`** — `docs/reference/provenance.md` lands with
  PROV1b; `docs/help/find-out-what-changed.md` lands with PROV1d.

---

## 12. What this isn't

- **Not a security audit feature.** `PermissionAuditEntry` (A0,
  `aidocs/51 §10`) is the one-shot compliance scan; F3's
  permission-change audit log (`aidocs/24 §3.7`) is the
  tamper-evident security trail. Both stay; neither is replaced.
- **Not a fine-grained read-watch system.** Read-capture is
  opt-in for a reason — shepard isn't a SIEM. Per-byte read
  auditing belongs in a reverse-proxy log shipper.
- **Not eventsourcing.** Activities are a **log**, not a
  replayable event stream. Re-applying activities doesn't
  reconstitute state — the `:Activity` is a metadata annotation
  on entities that mutate in-place. "What was shepard's state
  on date X?" is V2 snapshots' job (`aidocs/41`).
- **Not derivation lineage** (`aidocs/30 §2.2`) or **publication
  lineage** (`aidocs/30 §2.3`). Both ride this design's
  `:Activity` shape when they ship — different slice.
- **Not a cross-store join.** Activities live in Neo4j alongside
  the entities. The TimescaleDB write-path doesn't get a parallel
  activity log inside Postgres — ingest endpoints emit one Neo4j
  activity like any other write. One backing store keeps the
  dashboard's aggregation one query, not a federation problem.
