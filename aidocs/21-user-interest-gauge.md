# User / Scientific Interest Gauge — HDF5, Tabular Storage, KG Interfaces

**Snapshot date:** 2026-05-05.
**Scope.** A repo-internal *gauge* of demand for three candidate
development directions the maintainer flagged: (a) HDF5 / HSDS
support, (b) tabular / relational storage in shepard, (c) knowledge-
graph interfaces. Inventories the signals visible from a development
environment, separates evidence from anecdote, and recommends a
lightweight survey + interview plan to convert the unknowns into
something actionable.

Companion to `aidocs/14-semantic-improvements.md` (already proposes
the KG path), `aidocs/13-search-improvements.md` (where these would
be queried), `aidocs/16-dispatcher-backlog.md` (live backlog) and
`aidocs/03-issues-status.md` (the richest signal source).

---

## 1. Executive summary

| Area | Verdict (one line) |
|---|---|
| **HDF5 / HSDS** | Low–medium evidence; one named asker (Mathieu Vinot, `input_raw.md:3103`) plus repeated mentions in maintainer roadmaps and HMC project ideas, but zero open GitLab issue and no demonstrated pilot user beyond aspiration. Concentrated in the FE / large-experiment-data community. |
| **Tabular / relational storage** | Thin evidence; appears once on the maintainer's internal roadmap (`input_raw.md:701`) and once as an oblique user remark about "timeseries to excel via SQL" (`input_raw.md:5`). No issue, no MR, no Slack thread asking for it. The interface variant ("expose existing DBs as a payload kind") has more latent demand than the storage variant. |
| **Knowledge-graph interfaces** | Strongest evidence of the three. Active Slack/Mattermost discussions on Fuseki / SPARQL (`input_raw.md:2918-2976, 4874`), four open GitLab issues (#43 / #553 / #656 / #660 — `aidocs/03-issues-status.md:70-85`), an existing partial implementation (`SparqlConnector.java`), a written design proposal (`aidocs/14-semantic-improvements.md`), and explicit demand from at least three institutes (BT-Augsburg MEMAS, turbine-data department, Damast project). |

Recommend: prioritise (c). Defer (a) until the survey in §7 confirms
≥3 distinct user groups with HDF5 corpora. Decompose (b) into
"interface" vs "storage" before deciding — the question as stated
hides two products.

---

## 2. Methodology and limits

This is a *gauge*, not a measurement. From a development worktree we
have access to:

- 166 open GitLab issues with effort/value/staleness scores
  (`aidocs/03-issues-status.md`).
- Forum / Mattermost / Slack excerpts captured in
  `aidocs/input/input_raw.md` (8409 lines; mix of conversations,
  roadmap notes, code snippets and design memos).
- Maintainer-curated design notes (`aidocs/14-semantic-improvements.md`,
  others) that already encode prior triage.
- The code itself (presence / absence of integration points).
- ADR history (`architecture/src/09_architecture_decisions/`).

We do **not** have access to: usage analytics from production
shepard instances, support-ticket volume by topic, mailing-list
traffic, the DLR-internal wiki forum, the MOSS / dataship
sister-project's user list, conference-presentation Q&A captures,
or any structured user survey. See §8 for the explicit gap list.

Therefore: every claim below is a *signal* of demand, not a
quantification of it. A single named asker is recorded as one named
asker, not as "the FE community needs HDF5". Where evidence is one
person, we say so.

---

## 3. HDF5 / HSDS support

### 3.1 What's being asked

Two distinct things, sometimes conflated:

1. **HDF5 as a payload kind** — store HDF5 files in shepard
   (today: would be a generic `FileReference`). Adds nothing
   beyond the existing file payload.
2. **HSDS as a backing store** — run an HSDS instance alongside
   the existing TimescaleDB / MongoDB / Neo4j set, expose its
   datasets as a first-class shepard container with REST proxying,
   slice-and-dice queries, and likely h5pyd-compatible client access
   (`input_raw.md:3110, 3120, 4212`).

The interesting ask is (2). (1) is already supported.

### 3.2 Demand signals

- **Mattermost thread**, Mathieu Vinot ↔ Florian, 10:47–10:58
  (`aidocs/input/input_raw.md:3103-3144`). Asks directly: *"habt ihr
  irgendwann geplant, eine HDF5-Datenbank an Shepard anzudocken?"*
  Justification given: large data (FE meshing) and HDF5 experimental
  data in the department; JSON works but doesn't scale. Florian
  agrees it's "irgendwann auf der roadmap — aber aktuell ist die
  nachfrage überschaubar". This is the only direct user ask in the
  inputs and the maintainer's own gauge confirms low volume.
- **Maintainer roadmap, "Container / Data Types"** lists
  `HDF5 support through HSDS endpoints` (`aidocs/input/input_raw.md:699`).
- **Maintainer "Erweiterungen" list**: `HDF5 support mittels HSDS - HMC2`
  (`aidocs/input/input_raw.md:2895`).
- **HMC Phase 2 project sketch** (Semantic Satellite Data Storage
  System) lists `HDF5 container?` (`input_raw.md:7981, 8337`).
  Question mark is in the source — it's a candidate, not a
  commitment.
- **FLINT roadmap** lists `HDF5 + HSDS API` under
  "Data Formats" (`input_raw.md:8061`).
- **Earlier 2024 Slack reference** (`input_raw.md:4212`) — Florian
  to Jakob Haug: *"für 'funktionalen' (online) Zugriff auf HDF5,
  wäre da RESTFUL HDF5 hilfreich?"* Cites the HDF Group
  RESTful_HDF5 paper. Open question; no recorded answer in the
  inputs.

### 3.3 Counter-signals

- **No open GitLab issue** mentions `hdf5` or `hsds`
  (`aidocs/03-issues-status.md` covers all 166 open items; none
  match this cluster).
- **TimescaleDB satisfies the obvious overlap.** Numeric, multi-
  variable, time-indexed scientific data — the original HDF5
  niche — already has a tuned ingest / query path (ADR-010/011,
  `aidocs/12-timescaledb-performance-analysis.md`). The remaining
  HDF5-specific use case is *non-temporal* multidimensional arrays
  (mesh data, image stacks, sparse arrays); only Vinot's request
  fits this shape.
- **Operational cost.** HSDS is a full extra service (object
  store + service tier) requiring on-prem deployment alongside
  Caddy + Keycloak + 4 DBs (`aidocs/01-repo-overview.md:48`).
  ADR-008 already tracks the cost of multi-DB ops; adding a
  fifth store needs an explicit ADR.
- **No code or branch** anywhere in `backend/` or `clients/`
  references `hdf5`, `hsds`, or `h5pyd` (verified by grep).
- **Maintainer self-assessment** (Florian, `input_raw.md:3111`):
  "aktuell ist die nachfrage überschaubar" — i.e. the maintainer
  closest to the user channel reports thin demand.

### 3.4 Implementation cost

**XL.** Touches:

- New container kind (entity, REST surface, OpenAPI, client
  generation) — equivalent in scope to the existing
  TimeseriesContainer surface.
- New backing service in `infrastructure/` (HSDS docker compose,
  health checks, migration story).
- New auth bridge — HSDS has its own auth model; would need to
  integrate with the existing JWT/X-API-KEY flow
  (`aidocs/01-repo-overview.md:49`).
- New client-side helpers (h5pyd-style) — otherwise users will
  end up bypassing shepard and going to HSDS direct, defeating
  the FAIR / permissions story.

### 3.5 Decision question to ask users

> "If shepard could store and serve HDF5 datasets natively
> (mountable from h5pyd, with the same permissions and metadata
> as a Collection): does this unlock work you currently cannot
> do? If yes, roughly how large is the corpus
> (GB / TB), and how many distinct users in your group?"

Threshold for action: ≥3 distinct user groups with non-trivial
corpora (≥100 GB each), or ≥1 group with TB-scale.

---

## 4. Tabular / relational storage in shepard

### 4.1 The two sub-asks (must be separated)

The maintainer roadmap entry `Table Store / relational database in
shepard` (`aidocs/input/input_raw.md:701`) hides two distinct
products:

- **(a) Interface mode.** shepard exposes an *existing* relational
  DB (Postgres, MariaDB, …) as a new payload kind, much like a
  `FileReference` but typed as a SQL table or query. shepard
  does not own the data; it owns the link, the permissions
  envelope, and the metadata. Closest existing analogue:
  `URIReference`. Roughly: "give me a shepard-managed handle on
  the lab's experiment database".
- **(b) Storage mode.** shepard *stores* tabular data internally
  in a new relational backing store. Users upload CSV / parquet,
  shepard owns the schema, supports SQL queries, exports, and
  permissions. Closest existing analogue: today's
  StructuredDataContainer (which stores JSON in MongoDB,
  `data/structureddata/services/StructuredDataService.java:36-66`).

These are different products. (a) is a connector; (b) is a new
storage subsystem.

### 4.2 Demand signals

- **Maintainer roadmap, single line** (`input_raw.md:701`).
  Captured under "Container / Data Types" alongside HDF5 and
  S3/MinIO. No user attribution.
- **Indirect, single-line user remark** at the very top of
  `input_raw.md:5`: *"rest api improvements? feels clunky. maybe
  even other apis that are faster or easier to integrate eg
  timeseries to excel via sql"*. This is closer to "I want SQL
  as a query language over my existing timeseries" than "I want
  shepard to host my tables". Suggests interface mode (a) over
  storage mode (b).
- **Search-improvements doc §4** already proposes a
  `raw.sql` operator over the unified search
  (`aidocs/13-search-improvements.md`) — also pulls toward
  interface mode (let users query, not let users store).
- **DeepWiki ADR analysis**, recorded in `input_raw.md:2497, 6025,
  7544` (the same line copied in three places): *"From a user
  perspective it feels easier to navigate through a graph
  database instead of a relational database"* — historical
  rationale for *not* picking relational for the metadata layer.
  Argues against (b) for metadata; says nothing about (b) for
  payloads.

### 4.3 Counter-signals

- **No open GitLab issue** mentions `table store`, `tabular`,
  `relational storage`, or similar (`aidocs/03-issues-status.md`).
- **No Slack/Mattermost thread** asking for this. The single
  user-side remark is line 5 of input, generic and not followed
  up.
- **Existing StructuredDataContainer covers many "tabular" use
  cases** as JSON. No issue arguing this is insufficient.
- **TimescaleDB already gives users SQL over timeseries** for
  the one concrete user remark we have
  (`aidocs/01-repo-overview.md:48`). That is the lowest-cost
  delivery of the "timeseries to excel via SQL" ask.

### 4.4 Implementation cost

- **(a) Interface mode** — **M to L.** New reference type (~ the
  size of `URIReference` plus a SQL-injection-safe query
  pass-through). Auth flows for the upstream DB are the unknown.
- **(b) Storage mode** — **XL.** A new container surface, a new
  query language pass-through (already a hard call — see security
  finding C5 for what hand-rolled query translation costs), and
  an ADR weighing it against MongoDB-as-document-store
  (which is what StructuredData already is).

### 4.5 Decision question to ask users

> "Today, when you have tabular data (CSV, parquet, lab DB
> tables) you want to register with a shepard collection, what
> do you do? (a) upload as a file, (b) ingest into
> StructuredDataContainer as JSON, (c) link to an external DB,
> (d) avoid shepard for this, (e) other. What would make this
> easier — having shepard query an existing DB, having shepard
> host the table, or neither?"

Threshold for action: if (e)/avoid dominates and ≥3 groups want
"shepard query an existing DB", build (a) [interface] only. Do
not build (b) without strong evidence.

---

## 5. Knowledge-graph interfaces

### 5.1 The three sub-asks

`aidocs/14-semantic-improvements.md` already enumerates the
options; restated here so this doc stands alone:

- **(i) SPARQL endpoint over the existing Neo4j data.** No new
  store; expose the annotation triples via n10s
  (neosemantics) or a thin façade. `aidocs/14-semantic-improvements.md:362-368`
  recommends starting here.
- **(ii) Full triplestore** (RDF4J / GraphDB / Fuseki) as a new
  backing store, with ontology zone + annotation projection
  zone (`aidocs/14-semantic-improvements.md:341-359`).
- **(iii) Databus-style federation** — publish shepard
  collections to a DBpedia-Databus-compatible registry,
  cross-instance discovery and citation
  (`aidocs/input/input_raw.md:721, 8158-8306`).

### 5.2 Demand signals

- **Open GitLab issues #43, #553, #656, #660** — semantic-
  annotation cluster, four issues, all mid-staleness, three
  blocked on the Neo4j refactor #274
  (`aidocs/03-issues-status.md:70-85`). #43 is PARTIAL —
  implementation has been touched.
- **Existing partial implementation.** `SparqlConnector.java:17-110`
  already calls remote SPARQL endpoints to fetch labels for
  user-pasted IRIs. The wiring exists; the local-store half is
  what's missing.
- **Mattermost thread on Fuseki SPARQL.** Mathieu Vinot ↔
  Florian, ~14:10–14:24
  (`aidocs/input/input_raw.md:2918-3072`). Real production
  traffic against `https://fuseki.bt-au-semantics.intra.dlr.de/MEMAS-ontology/sparql`
  — i.e. a DLR-internal Fuseki is already hosting a project
  ontology and being queried by users. Volume in the snippet
  is small but the *infrastructure already exists* outside
  shepard.
- **Sven Durchholz's question** (`aidocs/input/input_raw.md:4874`):
  *"reevaluating our definitions and creating a ontology…
  resources how the integration of those ontologies work in
  shepard (It seems to have the possibility to use a SPARQL
  database for semantic annotations)?"* — a different group
  (turbine-data department), independently arriving at the same
  question, asking for guidance not features. 17 replies in the
  snippet. Indicates sustained interest.
- **Subsequent question, same user**
  (`aidocs/input/input_raw.md:4901`): *"shepard wie 'rdf:Class'
  als property IRI verwenden?"* — using shepard's annotation
  model the way the design intends, hitting the next concrete
  problem.
- **Databus integration in dataship**
  (`aidocs/input/input_raw.md:721-908, 1274-1306, 3356, 8306`).
  Explicit cross-project work going on (Damast prototype June
  2026; dataship / databus_client.py). RDF JSON-LD payloads,
  Turtle export, SPARQL discovery
  (`input_raw.md:778, 8230`). A live integration project, not
  a wishlist.
- **Federation as a recurring theme**:
  `input_raw.md:721, 7951, 8045, 8158-8306, 8380`. Repeated in
  multiple roadmap fragments and project sketches. Damast
  prototype expected ~6/26.
- **Maintainer design proposal** —
  `aidocs/14-semantic-improvements.md` (572 lines) is itself the
  strongest signal: a maintainer has already invested in writing
  this up rather than parking it. Phase D in the roadmap
  (`aidocs/14-semantic-improvements.md:507-518`) is the n10s
  / SPARQL-on-Neo4j step.
- **HMC project ideas** consistently include "semantic
  annotation of … data" and "ontology" (`input_raw.md:7972-7985,
  8326-8341, 8353-8355`).

### 5.3 Counter-signals

- The four open semantic-annotation issues are **mid-staleness**
  (#553, #656 stale; #43, #660 very stale —
  `aidocs/03-issues-status.md:70-85`). Demand exists but has
  not turned into a closed feature in over a year.
- **#274 (Neo4j refactor) is the load-bearing blocker** for the
  whole cluster (`aidocs/03-issues-status.md:79-85`,
  `aidocs/14-semantic-improvements.md:90-100`). KG work needs
  it landed first; the refactor itself is XL with low confidence.
- The Fuseki / Databus signals are real but **not yet inside
  shepard** — they're in adjacent DLR systems (MEMAS ontology
  service, Damast / dataship). Demand could be served by
  *interop with* those systems (a smaller change) rather than
  shepard owning a triplestore.
- **Permissions on SPARQL** is unsolved
  (`aidocs/14-semantic-improvements.md:545-547`). Releasing a
  read-everything SPARQL endpoint without a permission model is
  a security regression (cf. C-level findings in
  `aidocs/07-security-issues.md`).

### 5.4 Implementation cost

- **(i) SPARQL-on-Neo4j via n10s** — **M.** Plugin install,
  projection job, REST façade, label-cache refactor
  (`aidocs/14-semantic-improvements.md:507-513`).
- **(ii) Full triplestore** — **L–XL.** New container in
  `infrastructure/`, dual-write or outbox, ontology import
  pipeline, ADR (`aidocs/14-semantic-improvements.md:514-517`).
- **(iii) Databus federation** — **L.** Heavy client-side
  work in dataship already done; shepard side is JSON-LD export
  + a "publish to databus" REST endpoint (the dataship code at
  `databus_client.py` shows what's needed —
  `input_raw.md:1274`).

### 5.5 Decision question to ask users

> "Which of these would unlock work for you? (a) read shepard
> annotations as RDF triples in your existing SPARQL tooling,
> (b) ask shepard 'find every collection annotated as a
> X experiment' across instances, (c) publish shepard
> collections to the DLR Databus for citation, (d) define
> SHACL shapes that flag incomplete annotations, (e) none of
> the above. Rank top three."

Threshold for action: if (a) is in the top 3 for ≥3 groups, do
phase D of `14`. If (c) is, prioritise the dataship integration
path over the local triplestore.

---

## 6. Other adjacent asks worth recording

These are not in the three flagged areas but are strongly hinted
at in the inputs and would likely surface in any survey. One line
each:

- **AAS / Asset Administration Shell integration.**
  `input_raw.md:712`, `input_raw.md:8046`
  ("Manufacturing-X Integration → AAS Adapter"). Recurring;
  no issue, no implementation.
- **NovaCrate integration for RO-Crate authoring.**
  `input_raw.md:1163, 2897, 8389`. Concrete tool reference;
  three independent mentions.
- **RO-Crate import** (export already exists —
  `aidocs/01-repo-overview.md:77`). `input_raw.md:7982, 8338`.
  HMC2 candidate.
- **ORCID / IDP-driven user profile.**
  `input_raw.md:92, 985-1001`. A dataship requirement; would
  benefit shepard's own publication / credit story.
- **Apache Superset integration / dashboarding.**
  `input_raw.md:2896, 8067`. Also: Grafana dashboard generator
  (`input_raw.md:2913`).
- **MCP server for shepard** (LLM tool surface).
  `input_raw.md:2900, 8065-8066`. Two mentions; cross-cutting.
- **"Data parsers" plug-in / importer tooling.**
  `input_raw.md:2901, 8053`. Also surfaces as templates
  (`input_raw.md:98`) and process-wizard alignment.
- **Shepard MCP / federation prototypes in adjacent repos**
  (`shepard-process-wizard`, `shepard-timeseries-collector`,
  `processcontrol`) — `input_raw.md:104, 1376`. The ecosystem
  exists; the user-visible question is whether shepard core
  picks any of these up.

Recommend logging each as a one-line backlog ticket so the
survey can score them against the three flagged areas.

---

## 7. Recommended survey + interview plan

Three signals are missing for all three areas: distinct user
groups, corpus sizes, and ranking against alternatives. The
plan below converts each unknown into a question.

### 7.1 One-page survey (8–12 questions)

Audience: every named user contact across the input corpus
(extract from `input_raw.md` author lines + GitLab issue
authors of last 24 months). Aim ~50 distributed; expect
~15 responses.

Likert (1–5) or yes/no, no open-ended (those go in interviews):

1. *(yes/no)* In the last 12 months, have you stored or wished
   to store HDF5 data in shepard?
2. *(yes/no)* Same question for tabular data (CSV / parquet
   / a SQL table).
3. *(yes/no)* Same question for RDF / SPARQL queries against
   your shepard data.
4. *(Likert)* "I would use shepard for HDF5 datasets if
   it served them via HSDS."
5. *(Likert)* "I would use shepard to query an existing lab
   database (Postgres / MariaDB) as a payload kind."
6. *(Likert)* "I would use shepard to host tabular data
   directly (upload CSV, query as SQL)."
7. *(Likert)* "I want to query shepard's annotations as RDF
   triples in my own tooling."
8. *(Likert)* "I want shepard collections discoverable across
   instances via DLR Databus."
9. *(rank top 3)* Of: HDF5, tabular interface, tabular storage,
   SPARQL endpoint, full triplestore, Databus federation, RO-
   Crate import, NovaCrate, AAS, MCP — pick three.
10. *(short text, optional)* What is the largest dataset you
    have *not* put into shepard, and why?
11. *(yes/no)* Are you willing to be interviewed for 30
    minutes?
12. *(text, optional)* Your role + group / institute.

Hosting: any DLR-internal form tool. No PII beyond
self-disclosure.

### 7.2 Interview targets (5–8, by role)

Each gets 30 minutes; each gets one *primary* question plus
the standard probe set.

| # | Role | Primary question |
|---|---|---|
| 1 | FE-modelling researcher (HDF5 corpus owner; cf. Vinot, `input_raw.md:3125`) | "Walk me through the workflow you would want from upload to analysis if shepard owned the HDF5 store." |
| 2 | Turbine-data ontology lead (cf. Durchholz, `input_raw.md:4874`) | "What does the ideal SPARQL / annotation tooling look like, and where does shepard fit between your ontology and your data?" |
| 3 | Damast / dataship integrator (cf. `input_raw.md:1274`) | "Of the dataship → Databus path you've built, what would you push *back into shepard* if shepard would accept it?" |
| 4 | MEMAS-ontology user (cf. Vinot at Fuseki, `input_raw.md:2953`) | "If shepard had its own SPARQL endpoint, would you stop running your own Fuseki, or would you federate?" |
| 5 | Lab-database owner (cf. line-5 SQL remark, `input_raw.md:5`) | "If shepard could query your lab DB as a payload, what permission model would you trust?" |
| 6 | Process-wizard / collector author (`input_raw.md:104`) | "Where do shepard's payload kinds force you to compromise today?" |
| 7 | HMC2 satellite-data PI (`input_raw.md:7972, 8326`) | "Of the HMC project sketch, which payload kinds are committed and which are still open?" |
| 8 | xit-* contractor handover lead (`aidocs/03-issues-status.md:128`) | "What was the original demand behind the long-tail research issues you owned, and is it still live?" |

Probe set, every interview: corpus size, distinct downstream
users, alternative tools currently used, deal-breakers.

### 7.3 Folding results into the backlog

Output of survey + interviews: a small ranked table
(area × evidence-strength × distinct-groups). Fold into:

- `aidocs/16-dispatcher-backlog.md` — promote any area that
  clears thresholds in §3.5 / §4.5 / §5.5 to a tracked
  backlog item; demote (close-with-comment) those that
  clear no threshold.
- `aidocs/20-epic-roadmap.md` — slot the survivors into the
  appropriate epic. KG: align with Phases A–H of
  `aidocs/14-semantic-improvements.md:507-518`. HDF5 /
  Tabular: write a fresh epic, weighed against C-level
  security findings (`aidocs/07-security-issues.md`) which
  outrank feature work today.

Allow one quarter for survey + interviews + analysis; result
is a one-page decision memo, not a research paper.

---

## 8. What I didn't have access to

Explicit list, so the team can fill the gap:

- **Production usage analytics** — request volumes per endpoint,
  payload-type distribution, average corpus size, retention
  curves. shepard exposes Prometheus metrics
  (`aidocs/01-repo-overview.md:78`) but the metric history
  is not in the repo.
- **Support tickets / help-desk volume by topic.** Would tell
  us *which* features users actually struggle with, vs which
  they ask for in roadmaps.
- **Mailing-list traffic.** No mailing-list export in the
  inputs.
- **Conference Q&A / workshop captures.** The maintainer's note
  on `Storage-Konferenz 2024` (`input_raw.md:4224`) is the only
  reference; no transcript.
- **DLR-internal wiki traffic.** `wiki.dlr.de` references appear
  (`input_raw.md:4260, 7964, 8320`) but the wiki itself is not
  scrape-accessible from this environment.
- **Adjacent project user lists** (Damast, MOSS, dataship,
  shepard-process-wizard). We see code and design, not user
  count.
- **GitHub mirror traffic.** `aidocs/04-reconciliation.md`
  documents the mirror but no traffic data.
- **MOSS user guide draft** (`input_raw.md:1333` — Overleaf
  link, behind login).
- **Survey responses, interview transcripts** — none yet
  conducted; this doc proposes the first round.
- **Anything from outside DLR.** All signals here are
  DLR-internal. If shepard has external users, they're invisible
  to this gauge.

---

## 9. References

- Maintainer roadmap: `aidocs/input/input_raw.md:670-721, 2893-2901,
  8043-8071`.
- HDF5 / HSDS thread: `aidocs/input/input_raw.md:3103-3144,
  4212`.
- Tabular roadmap line + indirect ask: `aidocs/input/input_raw.md:5, 701`.
- KG / SPARQL discussions:
  `aidocs/input/input_raw.md:2918-3072, 4874, 4901`.
- Databus / federation: `aidocs/input/input_raw.md:721-908,
  1274-1306, 7951, 8158-8306, 8380`.
- HMC project ideas: `aidocs/input/input_raw.md:7972-7985,
  8326-8355`.
- Adjacent asks (AAS, NovaCrate, ORCID, RO-Crate, MCP):
  `aidocs/input/input_raw.md:92, 712, 985-1001, 1163, 2896-2901,
  8046, 8065-8066, 8389`.
- Open issues cluster: `aidocs/03-issues-status.md:70-85`.
- Existing SPARQL code: `backend/src/main/java/de/dlr/shepard/context/semantic/SparqlConnector.java:17-110`.
- StructuredData (today's "tabular as JSON"):
  `backend/src/main/java/de/dlr/shepard/data/structureddata/services/StructuredDataService.java:36-66`.
- Companion design notes: `aidocs/14-semantic-improvements.md`,
  `aidocs/13-search-improvements.md`,
  `aidocs/12-timescaledb-performance-analysis.md`.
- Backlog targets: `aidocs/16-dispatcher-backlog.md`,
  `aidocs/20-epic-roadmap.md` (registration handled by
  the dispatcher; this doc does not modify `00-index.md`).
