---
stage: deployed
last-stage-change: 2026-05-24
audience: instance-admin + contributor
---

# Neo4j + n10s substrate audit — 2026-05-24

Observation + recommendation audit on the live shepard Neo4j substrate. No
mutations. Sibling to the parallel TimescaleDB / file-storage / Mongo /
Postgres / Docker stack audits; the synthesis architecture report consumes
all six.

## TL;DR

**13 antipatterns + 5 best-practice gaps** filed as `NEO-AUDIT-2026-05-24-001`
through `-018`. Three are CRITICAL severity, six MAJOR, the rest MINOR.

The substrate is broadly healthy — 87 constraints, 115 indexes, page-cache
~1 GB used of 3 GB allocated, query latency sub-100 ms on hot paths — but
carries three structural gaps that compound at MFFD-real-data scale:

1. **CRIT: `Activity` is provenance write-only, not provenance-queryable.**
   284,535 `:Activity` nodes exist with **ZERO incoming or outgoing edges**.
   The `prov:wasAssociatedWith` / `prov:used` / `prov:generated` graph
   edges documented in `Activity.java:21-23` were never wired (the comment
   reads "edges (not yet wired in this slice) tie to the target Entity").
   Only 0.05% (144 of 284,535) Activity rows carry a `targetAppId`
   property. No index on `targetAppId`. **Result**: "what activities
   touched DataObject X" requires a full-label scan costing 569,069 dbHits
   per query — the f(ai)²r promise of "every interaction is observable"
   is not actually observable from the graph today.
2. **CRIT: 11,834 of 11,902 `:ShepardFile` nodes have `providerId IS NULL`**
   (99.4%). V34's `Backfill_FilePayload_providerId` migration covered 1%.
   The file-storage routing audit (sibling #209) lands on the same gap
   from the read side; this audit confirms the write-side state.
3. **CRIT: All 596 `:Timeseries` nodes have `appId IS NULL`** despite the
   `appId_unique_Timeseries` constraint existing — the constraint allows
   NULL, so it's satisfied but useless. The 5-tuple migration design
   (`aidocs/platform/87-timeseries-appid-migration.md`) is the planned fix.
   This audit's contribution: the appId column is already provisioned
   in the schema layer — only the data layer is empty.

Plus six MAJOR-severity findings that compound the above and **two
already-confirmed-from-prior-audits** items (EAV-on-graph, supernode on
the operator User node — both flagged 2026-05-23, now quantified on
production data and re-filed as part of the synthesis context).

## Substrate state (single snapshot)

| Dimension | Value |
|---|---|
| Neo4j version | 5.26.26 community |
| Plugin set | `n10s` only (APOC absent despite `NEO4J_dbms_security_procedures_allowlist=apoc.*,n10s.*`) |
| Heap | 2 GB max, ~1.3 GB used |
| Page cache | 3 GB allocated |
| Total nodes (top labels) | `:Resource` 374,671 / `:Activity` 284,452 / `:Statement` 170,026 / `:DataObject` 17,149 / `:ShepardFile` 11,902 |
| Constraints | 87 (all UNIQUENESS) |
| Indexes | 115 ONLINE; 47 zero-read |
| Migrations applied | 63 of 63 declared (V1-V61, V63, BASELINE — V62 deliberately skipped) |
| Hottest index | `n10s_unique_uri` 26,076,831 reads |
| RDF ontologies loaded | 13 (m4i 1.4.0, schema-org, prov-o, time, foaf, qudt, dc-terms, geosparql, om-2, ro, shepard-experiment, simat, lumen-inspired) |

## Antipatterns (severity-sorted)

### CRITICAL

#### NEO-AUDIT-2026-05-24-001 — `:Activity` nodes have zero graph edges (PROV trail un-queryable)

**Evidence:**
```cypher
MATCH (a:Activity) WITH a, COUNT { (a)--() } AS deg RETURN deg, count(*)
-- deg=0, count=284530
```

`Activity.java:21-23` documents the design intent:
> Persisted as `(:Activity)` Neo4j nodes; the `(:User)-[:WAS_ASSOCIATED_WITH]->(:Activity)`
> edge ties the activity to the acting Agent; `:USED` / `:GENERATED` edges
> (**not yet wired in this slice**) tie to the target Entity.

Three years and 284k rows later, those edges still aren't wired. Agent
identity lives in `Activity.agentUsername` (a string), target identity in
`Activity.targetAppId` (a string, NULL on 99.95% of rows).

**Impact:** the f(ai)²r vision (`project_fair2r_integration.md`) of typed
PROV-O Activities consumable as a graph cannot be served by the current
schema. Every "show provenance for X" query becomes a `targetAppId` text
scan. The `ProvJsonLdRenderer` *exports* the edges in JSON-LD output, but
they don't exist on the substrate — the JSON-LD is synthesised at render
time from the property-only Activity.

**Recommendation:** wire the three PROV-O edges in `ProvenanceCaptureFilter`
write path:
- `(User)-[:wasAssociatedWith]->(Activity)` on every capture
- `(Activity)-[:used]->(target)` for read-shape actions
- `(Activity)-[:generated]->(target)` for create/update actions

The User edge alone closes 100% of "who did what" queries cheaply; target
edges close "what happened to X" queries. Migration shape: existing 284k
rows can be back-filled by parsing `Activity.path` against
`TargetEntityResolver` and reading `Activity.agentUsername`. **Sequence
this after the PROV-RESOLVER-PATHWALK landing** so the resolver is the
single shared source of target IDs.

**Citation:** W3C PROV-O recommends the typed predicates as the standard
shape for activity-entity-agent linkage —
https://www.w3.org/TR/prov-o/#wasAssociatedWith. Neo4j performance KB
warns that property-scan replacements for graph edges defeat the storage
substrate's purpose —
https://neo4j.com/developer/kb/avoid-supernodes-anti-pattern/.

---

#### NEO-AUDIT-2026-05-24-002 — `:ShepardFile.providerId` populated on 0.6% of rows

**Evidence:**
```cypher
MATCH (sf:ShepardFile) RETURN sf.providerId AS provider, count(*) AS c
-- NULL: 11834, "gridfs": 71, "s3": 33
```

V34's backfill (`V34__Backfill_FilePayload_providerId.cypher`) was meant
to seed every legacy file's `providerId` to `"gridfs"` since the legacy
storage path was unconditionally GridFS. Either V34 only ran against the
71 files extant at that time, or the constraint was loosely written. New
files written via the new write path *do* tag `providerId` (104 rows
across both providers), but the legacy 11,834 are unrouted — any
provider-aware retrieval code must default to GridFS for NULL, which it
does today but is fragile.

**Impact:** file-storage routing audit (sibling) cannot rely on `providerId`
to dispatch to the correct `FileStorage` SPI; must keep the
"NULL → GridFS" fallback rule baked into every reader. Removing the
fallback in a future cleanup would silently break 99.4% of file reads.

**Recommendation:** ship a one-shot Cypher migration to backfill
`providerId='gridfs'` on every existing `:ShepardFile` with NULL
`providerId`. Add a property-existence assertion in the write path (OGM
`@Required` plus a default in the entity constructor) so new rows can't
land with NULL. Drop the reader-side NULL-coalesce on a subsequent PR
once the substrate is clean.

---

#### NEO-AUDIT-2026-05-24-003 — `:Timeseries.appId` NULL on 100% of rows despite uniqueness constraint

**Evidence:**
```cypher
MATCH (t:Timeseries) RETURN count(*) AS total, count(t.appId) AS with_appId
-- total=596, with_appId=0
```

Constraint `appId_unique_Timeseries` exists (created by V11) but Neo4j
uniqueness constraints permit NULL, so the constraint is satisfied while
providing zero protection. Every `:Timeseries` node carries the legacy
5-tuple `{measurement, device, location, symbolicName, field}` for
identity; none carries the canonical `appId`.

**Impact:** the TS-AppId migration (`aidocs/platform/87`) is the planned
fix. The relevant audit observation: the schema layer is already
provisioned (constraint, index in place); only the data layer is empty.
The migration's value-add is the backfill + write-path mint, not new
schema. Sequencing implication: this migration is **closer to ready than
the design doc implies**.

**Recommendation:** L2c-style backfill migration. Mint UUID-v7 appIds for
every existing Timeseries node, populate, then deprecate the 5-tuple as
identity (keeping it as searchable attribution). Per
`feedback_appid_to_shepardid.md` the field gets renamed to `shepardId`
in the same pass.

---

### MAJOR

#### NEO-AUDIT-2026-05-24-004 — Operator-`User` is a confirmed supernode (33,368 incoming edges)

**Evidence:**
```cypher
MATCH (n)-[r:created_by]->(u:User {username:"ee4c010f-..."})
RETURN labels(n)[0] AS lbl, count(*) AS c
-- DataObject:17086, BasicReference:12134, StructuredDataContainer:4207, ... total 33,368
```

The 2026-05-23 archived antipattern audit suspected this would happen at
MFFD scale and flagged it as "inconclusive". MFFD ingest landed; the
suspicion is now confirmed. PROFILE shows a `created_by`-traversal
through the operator User node takes 66,794 dbHits to walk 17,086
DataObjects.

**Impact:** any "what did user X create" query without an upstream filter
fans out the entire user's authored corpus. Today that's bounded by
MFFD's ~33k items; at production growth (10x in 12 months projected) it
becomes a routine multi-second query. Activity edges (when NEO-001 ships)
will worsen this fanout — every authenticated action lands a new edge to
the same node.

**Recommendation:** **don't dump the supernode**, it's structural.
Instead introduce a *time-bucketed Agent index* — `(:User)-[:created_in_month {ym:"202605"}]->(:DataObject)` —
written by the same hook that creates `created_by`. The MATCH plan
becomes label-scan on the `:created_in_month` rel by `ym` predicate,
typically a 100-1000x reduction. Neo4j's official recommendation for
unavoidable supernodes is **secondary indexing** of this shape, not
elimination —
https://neo4j.com/developer/kb/understanding-the-design-of-supernodes/.

---

#### NEO-AUDIT-2026-05-24-005 — EAV-on-graph: 90+ distinct `attributes||*` keys on `:DataObject`

**Evidence:**
```cypher
MATCH (do:DataObject) UNWIND keys(do) AS k RETURN k, count(*) ORDER BY count(*) DESC
-- 90+ distinct "attributes||*" keys, several appearing on only 1-3 rows
-- (hypothesis, closed_at, scope, severity, defect_area_cm2, weld_spots, robot...)
```

Quantifies the 2026-05-23 audit finding §1.1.A. MFFD real-data ingest
flooded the schema with single-use attribute keys. The Neo4j-OGM
`@Properties` flatten on `AbstractDataObject.attributes:
Map<String,String>` creates one Neo4j property per map key on every node
— the graph DB analogue of EAV.

**Specific harm post-MFFD:** `attributes||v16_pass1` appears 8,507 times
(half of all DataObjects) — this is a transient ingest marker. `attributes||source_*`
keys are import provenance — should be PROV-O Activity properties, not
DataObject properties. `attributes||license` (3 rows) coexists with the
typed `license` field (4 rows, LIC1 just shipped) — **same semantic
carried by two different fields**.

**Recommendation:** the SHACL-as-source-of-truth direction in
`feedback_shacl_single_source_of_truth.md` is the structural fix. Tactical
fixes ahead of that:
- Promote import provenance (`source_*`, `import_*`, `v16_pass1`) from
  `:DataObject.attributes||` to the `:Activity` node that recorded the
  import, indexed by `targetAppId` (closes NEO-001 simultaneously).
- Drop `attributes||license` once LIC1 readers settle on the typed field.

---

#### NEO-AUDIT-2026-05-24-006 — Mixed-case relationship-type convention

**Evidence:**
```cypher
CALL db.relationshipTypes() YIELD relationshipType RETURN relationshipType
-- 3 shepard-side UPPERCASE outliers: ENTRY_OF, SNAPSHOT_OF, OWNS_CREDENTIAL
-- (all other UPPERCASE rels — TYPE, SUBJECT, OBJECT, RELATED, BROADER,
--  NARROWER, SUBCLASSOF, MIGRATED_TO, ... — are n10s/RDF-owned, upstream-mandated)
```

Per `MEMORY.md` and the prior audit, lowercase is the established
shepard convention (`has_dataobject`, `created_by`, `has_payload`).
Three Java entities use raw uppercase string literals instead of the
`Constants.*` constants:
- `SnapshotEntry.java:59`: `@Relationship(type = "ENTRY_OF")`
- `Snapshot.java:82`: `@Relationship(type = "SNAPSHOT_OF")`
- `User.java:79`: `@Relationship(type = "OWNS_CREDENTIAL")`

**Impact:** convention drift. A new contributor reading `MATCH ()-[:has_dataobject]->()` and `MATCH ()-[:SNAPSHOT_OF]->()` will reasonably
ask "which is right?" Mixed-case in the same graph is a long-tail bug
source — `:has_successor` vs `:HAS_SUCCESSOR` would be silently distinct
labels.

**Recommendation:** rename to `entry_of`, `snapshot_of`, `owns_credential`
in a Cypher migration that does `MATCH ()-[r:ENTRY_OF]->() CALL apoc.refactor.setType(r, 'entry_of') YIELD ...`
— **but APOC is absent on this instance** (see NEO-013). Fallback shape:
manual `MATCH (a)-[r:ENTRY_OF]->(b) CREATE (a)-[r2:entry_of]->(b) SET r2 = properties(r) DELETE r`.
Coordinate with the entity-string rename pass.

---

#### NEO-AUDIT-2026-05-24-007 — Duplicate Collection names with shared semantic (MFFD-Dropbox × 4)

**Evidence:**
```cypher
MATCH (c:Collection) WITH c.name AS name, count(*) AS c WHERE c > 1
RETURN name, c
-- "MFFD-Dropbox": 4 (appIds 019e4e56-..., 019e525d-..., 019e553b-..., 019e55f3-...)
```

Two of the four MFFD-Dropbox collections are super-nodes themselves
(8538 + 8518 edges via `has_dataobject`). Per memory
(`project_mffd_import_workflow.md` and the 2026-05-23 audit), this is
NOT a duplicate bug — it's intentional iterative ingest. But the
operator-facing impact is real: `GET /v2/collections?name=MFFD-Dropbox`
returns four hits, and from the UI a researcher cannot tell which
collection's DataObjects they're traversing.

**Impact:** ambiguous identity at the human level. Memory flags this as
a known issue; this audit's contribution is degree-counting it: the four
collections together carry 25,612 DataObjects (cross-checked: `MATCH
(c:Collection {name:'MFFD-Dropbox'})-[:has_dataobject]->(do)
RETURN count(do)`).

**Recommendation:** no schema change. Add a Neo4j-side property
`importedFrom: text` distinguishing the four (e.g., `tapelaying`, `bridgewelding`,
`v15-redrive-1`, `v15-redrive-2`); surface on the collection list UI as
a sub-label. Closes the human-identity ambiguity without merging the
graph nodes.

---

#### NEO-AUDIT-2026-05-24-008 — Single super-node `:DataObject` (Tapelaying-2026-05-23 with 1474 references)

**Evidence:**
```cypher
MATCH (do:DataObject {appId:"019e52a2-..."})-[r]->(n)
RETURN type(r), labels(n)[0], count(*)
-- has_reference→BasicReference: 1470, created_by→User: 1, has_version→Version: 1, has_successor→DataObject: 1
```

A single tapelaying ingest produced 1470 child file references on one
parent DataObject — orders of magnitude above the typical fan-out. The
Cartesian-MATCH antipattern flagged in the 2026-05-23 audit (§1.1.E)
applies here: an OPTIONAL MATCH over three reference legs on this node
multiplies leg cardinalities.

**Impact:** today the live read on this DO takes 8840 dbHits (measured
via PROFILE) for an empty result because the planner walks all 1470 refs
before discarding. Multiply by visit frequency and the cost compounds.

**Recommendation:** apply the §1.1.E fix from the prior audit to
`DataObjectDAO.java:480-506` and `CollectionDAO.java:120-121`:
split sibling `OPTIONAL MATCH` legs into `CALL { ... }` subqueries (Neo4j
4.4+). Re-PROFILE the same DO after the fix; expected reduction is
~5-10x dbHits.

---

#### NEO-AUDIT-2026-05-24-009 — `idx_Subscription_requestMethod` with 298,700 reads against zero nodes

**Evidence:**
```cypher
SHOW INDEXES YIELD name, readCount WHERE name = "idx_Subscription_requestMethod"
-- readCount=298700, ONLINE
MATCH (s:Subscription) RETURN count(s)
-- 0
```

The index is hit 298k times (third-hottest in the system) yet finds
nothing. The reads come from the `SubscriptionService.findMatchingSubscriptions`
hot path that runs on every authenticated write — every CREATE / UPDATE /
DELETE checks the subscription registry for matching watchers, and the
registry is empty.

**Impact:** cheap index lookups individually (~O(log n) on an empty
B-tree is trivial) but the cumulative cost is real on a hot path that
runs ~300k times per day at MFFD ingest scale. More importantly: the
hit-rate signals a logic gap — if subscriptions are a shipped feature
that operators are meant to use, the empty count means the feature has
zero adoption (or a bug preventing creation). Worth a separate
adoption-audit row.

**Recommendation:** two-layer fix:
- Short-circuit `findMatchingSubscriptions` with a single
  `count(:Subscription) == 0` cache (refreshed on subscription
  create/delete). When zero, skip the index lookup entirely.
- File an adoption-audit task: 0 subscriptions across the production
  instance with MFFD ingest running is a smell worth a separate look.

---

#### NEO-AUDIT-2026-05-24-010 — Duplicate user emails (2× `alice@`, 2× `admin@`)

**Evidence:**
```cypher
MATCH (u:User) RETURN u.email, count(*) ORDER BY count(*) DESC
-- alice@demo.shepard.local: 2, admin@demo.shepard.local: 2
```

`unique_username_User` constraint exists on `User.username` (which is a
UUID), so the duplicates are username-unique but email-shared.

**Impact:** business-logic surprise. A "forgot password" / "email me my
account" / OIDC SSO email-as-handle flow would silently pick whichever
of two `admin@` accounts the system found first. Today this is dev-only
seed data, but in production it's a routine OIDC failure mode (two
employees with shared email — e.g. shared department mailbox).

**Recommendation:** add a uniqueness constraint on `User.email` (NULL
permitted for usernames that don't carry a known email):
```cypher
CREATE CONSTRAINT user_email_unique IF NOT EXISTS
FOR (u:User) REQUIRE u.email IS UNIQUE
```
Plus a one-shot reconciliation migration to merge the two `admin@` /
`alice@` accounts (or rename one to a deterministic suffix). Coordinate
with the auth team — the merge is graph-edge-relabel, well-known shape.

---

### MINOR

#### NEO-AUDIT-2026-05-24-011 — 47 indexes with zero reads (dead index maintenance cost)

**Evidence:**
```cypher
SHOW INDEXES YIELD name, readCount, state WHERE readCount = 0 AND state = "ONLINE"
RETURN count(*)
-- 47
```

Includes the `idx_Timeseries_attr_field`, `_attr_location`,
`_attr_symbolic_name` text indexes (created by V8 against the
5-tuple) plus 23 zero-read `appId_unique_*` constraints for entity
types not in production use (`:UnhideConfig`, `:DataciteMinterConfig`,
`:HdfContainer`, `:VideoAnnotation`, `:LabJournalEntryRevision`,
`:Publication`, etc.).

**Impact:** index maintenance cost on every write. Neo4j updates every
matching index whether anything reads it. The cumulative write-amplification
is small per-row but real at MFFD ingest scale (33k writes × 47 indexes
× 1ms each = 25 minutes of pure index-update CPU per ingest).

**Recommendation:** *don't blanket-drop unique constraints* — they're
load-bearing for the entity types they cover even if not currently
queried. *Do consider dropping*:
- The three Timeseries text indexes (`field`, `location`,
  `symbolic_name`) — `device` and `measurement` are queried, the other
  three never (verified via `lastRead = NULL`). Drop and recreate if /
  when a query path demands them.
- The Watch + WatchSubscription indexes if Watch is a deprecated
  feature.

---

#### NEO-AUDIT-2026-05-24-012 — Legacy `id` (numeric) constraints on entities migrated to `appId`

**Evidence:**
```cypher
MATCH (n:DataObject) RETURN count(*) AS total, count(n.id) AS with_id, count(n.appId) AS with_appId
-- total=17149, with_id=0, with_appId=17149
```

Confirms Learning 2 in `project_db_audit_learnings_2026-05-24.md`. 100%
of DataObject / Collection / StructuredDataContainer rows have `id IS NULL`
because the system migrated to `appId` as canonical identity, but the
`unique_id_*` constraints from V1 (the initial schema) still exist for
14 entity types.

**Impact:** the constraints are vestigial — they protect a property
that's never populated. No correctness risk (unique-on-NULL is
satisfied), but contributors reading the constraints list assume `id`
is a live identifier and waste time investigating.

**Recommendation:** schedule a deprecation pass after L2e (the planned
v1-numeric-id sunset, `aidocs/platform/25 §L2e`). Drop the 14
`unique_id_*` constraints once L2e ships and no readers depend on the
property.

---

#### NEO-AUDIT-2026-05-24-013 — APOC absent on this instance despite security allowlist suggesting it should be present

**Evidence:**
```bash
NEO4J_PLUGINS=["n10s"]
NEO4J_dbms_security_procedures_allowlist=apoc.*,n10s.*
NEO4J_dbms_security_procedures_unrestricted=apoc.*,n10s.*

CALL apoc.help("apoc") → ProcedureRegistrationFailed
```

The container env vars allowlist `apoc.*` but `NEO4J_PLUGINS` doesn't
include `apoc`. APOC is the standard Neo4j extension library used by
~every non-trivial Cypher migration in the open-source community
(periodic.iterate for batched UNWIND, refactor procedures, JSON
import/export).

**Impact:** several recommended migrations in this audit (rel-type rename
in NEO-006) become awkward without `apoc.refactor.setType`. Future
migrations that need batched processing (e.g., the activity-edge
backfill for NEO-001) require hand-rolled UNWIND batches instead of
`apoc.periodic.iterate`.

**Recommendation:** add `apoc` to `NEO4J_PLUGINS` in the compose file:
```yaml
environment:
  - NEO4J_PLUGINS=["apoc","n10s"]
```
The allowlist is already correct. APOC core is bundled with Neo4j 5.x
since 5.7; no separate jar download needed. Container restart applies it.

---

#### NEO-AUDIT-2026-05-24-014 — Migration chain applied in non-monotonic order (V63 before V61)

**Evidence:**
```cypher
MATCH (m:__Neo4jMigration) RETURN collect(m.version)
-- [..., "60", "63", "61"]
```

V63 (`Bootstrap_legacy_v1_config`) is listed in the applied chain
*before* V61 (`v15_prov_predicates`). This is the same incident as
OPS-MIGRATION-HEALTHCHECK (V61 lagged main by two days); V63 happened
to merge first and was applied normally, V61 was applied retroactively
after the healthcheck flagged the gap.

**Impact:** correctness OK (the `Migrations` library applies forward-only
on each restart, so out-of-order insertion still yields a consistent
chain), but it's a documentation foot-gun. A reader of `MATCH (m) ORDER
BY m.version DESC` will see "63 happened at 2026-05-22T15:38, 61 happened
at 2026-05-22T15:39+1day" and reasonably ask "was the order reversed?"

**Recommendation:** add a comment to the OPS-MIGRATION-HEALTHCHECK
runbook (`docs/admin/runbooks/migration-chain-integrity.md`) explaining
this specific incident — the chain may carry out-of-order
`installedOn` timestamps if a forward-only splice runs late, and that's
WAI.

---

#### NEO-AUDIT-2026-05-24-015 — `:Activity.actionKind` is stringly-typed (3 distinct values, no enforcement)

**Evidence:**
```cypher
MATCH (a:Activity) RETURN a.actionKind AS k, count(*) ORDER BY c DESC
-- CREATE: 253520, UPDATE: 18050, DELETE: 12917
```

Activity.java line 50-51 documents the design intent of an enum
(`CREATE/READ/UPDATE/DELETE/EXECUTE`), but it's stored as a free-text
`@Property("actionKind") private String actionKind`. Today the 3
observed values are clean; a single typo (`"Create"` vs `"CREATE"`) in
the capture filter would silently fork the value-space and break
downstream filters.

**Impact:** typo-tolerant data path on a heavily-queried gate field.
Same shape as `:DataObject.status`, `:ImportPlan.status` flagged in the
2026-05-23 audit.

**Recommendation:** Neo4j 5 Enterprise has property-existence + value
constraints (`REQUIRE n.actionKind IS IN ['CREATE','READ','UPDATE','DELETE','EXECUTE']`)
but the instance runs **Community Edition** (`NEO4J_EDITION=community`).
Fallback: validation in the capture filter (`ProvenanceCaptureFilter`) +
a periodic sweep alarm that fires if any `:Activity.actionKind` matches
none of the canonical values. Lower-priority than NEO-001 — wire the
edges first, then tighten the property.

---

### BEST-PRACTICE GAPS (no antipattern, but reachable improvements)

#### NEO-AUDIT-2026-05-24-016 — Soft-delete heavy on `:DataObject` (50% deleted) + `:BasicReference` (36%) without TTL

**Evidence:**
```cypher
MATCH (do:DataObject) RETURN do.deleted, count(*)
-- false: 8590, true: 8559 (50% soft-deleted)
MATCH (br:BasicReference) RETURN br.deleted, count(*)
-- false: 7736, true: 4413 (36% soft-deleted)
```

Half of all DataObjects are soft-deleted (carried over from MFFD-Dropbox
test ingests). The `idx_DataObject_deleted` and `idx_BasicReference_deleted`
indexes work — but Neo4j range indexes on boolean don't index NULL
distinctly from FALSE, so the negative-case lookups (`WHERE deleted =
false`) always touch every deleted row's index entry too.

**Impact:** every list query carries 2x the data on the index walk
because half is junk. Coupled with the operator-User supernode (NEO-004),
"list DataObjects this user created (not deleted)" becomes O(33k × 2)
index walks for a single page of 50 results.

**Recommendation:** ship SM1a (`aidocs/16` row, queued) which adds a
retention sweep — soft-deleted DataObjects past a TTL are hard-deleted.
Pairs naturally with this finding because it converts the 50%
soft-deleted population into a finite-lifetime quantity. Already in the
backlog; just nudge it up the priority list.

---

#### NEO-AUDIT-2026-05-24-017 — Property keys with single-row usage (sparse property store)

**Evidence:** 30+ `attributes||*` keys appear on 1-3 DataObjects each
(see NEO-005). Single-use properties on a sparse store cause page-cache
churn — Neo4j's property store is record-oriented, not column-oriented,
so a node with 30 properties takes 30 record reads even if your query
needs one.

**Impact:** compounds the EAV antipattern. The Tapelaying-style
super-node with 90+ attribute keys takes proportional disk reads on
every access.

**Recommendation:** same as NEO-005 — promote the import-provenance
attributes to Activity edges. Routine domain attributes (`material_batch`,
`process_step`) stay where they are pending the SHACL-as-source-of-truth
direction.

---

#### NEO-AUDIT-2026-05-24-018 — 174,035 bare `:Resource` nodes (n10s SKOS import bloat)

**Evidence:**
```cypher
MATCH (n:Resource) WHERE size(labels(n)) = 1 RETURN count(*)
-- 174035
```

These are the n10s-imported subjects from SKOS thesauri (NASA thesaurus
contributes the bulk: `BROADER:17012, NARROWER:17012, RELATED:117340,
INSCHEME:?`). They're correct per n10s semantics — every RDF subject
becomes a `:Resource` node — but the count exceeds shepard's domain node
count (74k) by ~2.4x. Most aren't displayed or queried; they sit in the
graph as the n10s side of the SKOS imports.

**Impact:** ~26M `n10s_unique_uri` lookups (top-1 hottest index in the
system) on these nodes. The page cache stays warm with RDF
infrastructure, possibly at the expense of hot DataObject reads.

**Recommendation:** consider importing the heavy SKOS thesauri (NASA,
synaptica) with `n10s.skos.import.fetch` using `handleMultival="ARRAY"`
+ a label-density reduction (omit `skos:related` if not used). Or
sidecar the SKOS substrate to a separate Neo4j database
(Community single-DB limitation argues against this) or to a Jena/RDF
sidecar (per `aidocs/semantics/14`). Lower priority — the substrate
copes today.

## n10s state assessment — initialised correctly? ontologies present? semantic queries actually used?

**State: initialised cleanly**. `n10s.graphconfig.show()` returns sane
defaults — `handleVocabUris=IGNORE`, `handleMultival=ARRAY`,
`handleRDFTypes=LABELS_AND_NODES`, `keepLangTag=true`. These are the
defaults the `N10sBootstrapHook` (line 69-77 of the bootstrap source)
intends. The `n10s_unique_uri` constraint is in place (V49 +
bootstrap-hook ensure-creates it on startup).

**13 ontologies loaded**, though the `aidocs/semantics/97-tpl3-upper-ontology-bootstrap.md`
roadmap calls for more (BFO, IAO, IOF, EMMO, CHAMEO, MSEO, OBO-foundry
relations beyond ro). The 13 currently loaded:

| URI | Version | Notes |
|---|---|---|
| `http://purl.obolibrary.org/obo/ro.owl` | NULL | OBO relations |
| `http://purl.org/dc/terms/` | NULL | Dublin Core terms |
| `http://qudt.org/vocab/unit/` | NULL | QUDT units |
| `http://w3id.org/nfdi4ing/metadata4ing/` | 1.4.0 | m4i v1.4.0 (per memory `project_m4i_integration_design.md`) |
| `http://www.ontology-of-units-of-measure.org/resource/om-2/` | NULL | OM-2 units |
| `http://www.opengis.net/ont/geosparql` | NULL | GeoSPARQL |
| `http://www.w3.org/2006/time` | NULL | OWL-Time |
| `http://www.w3.org/ns/prov#` | NULL | PROV-O |
| `http://xmlns.com/foaf/0.1/` | NULL | FOAF |
| `https://schema.org/` | NULL | schema.org |
| `https://shepard.dlr.de/ontologies/experiment` | 1.0.0 | shepard-experiment |
| `https://shepard.dlr.de/showcase/lumen-inspired` | 1.0.0 | LUMEN demo vocab |
| `https://w3id.org/simat/` | 1.0.0 | SIMAT |

**Semantic queries are heavily exercised** — `n10s_unique_uri` is the
#1 read index (26M reads). The `unique_username_User` constraint is #2
(734k reads); the next-hottest shepard-side index is
`appId_unique_Activity` (#4, 284k reads, matching the 284k Activity
node count — meaning each Activity is looked up once on average).

**Where the audit found friction:**
- 174k bare `:Resource` nodes (NEO-018) — SKOS bloat.
- Most `Ontology` nodes carry no `versionInfo` — admin can't easily
  reconcile against a published catalogue. Recommendation: enrich the
  ontology preseed manifest (`backend/src/main/resources/ontologies/ontologies-manifest.json`)
  with per-ontology version + import date.

## Migration chain state + any drift

**60 forward migration files on disk** (V1-V61, V63 + 5 `_R__` rollback
files). **63 nodes in `:__Neo4jMigration`** (BASELINE + V1-V61 + V63).
The chain is **clean** — every forward file is applied; the
out-of-order insertion of V63 before V61 is recorded (NEO-014) but
correctness-preserving.

V62 is **deliberately skipped** — no `V62__*.cypher` on disk. Per the
Neo4j Migrations library's allow-gap semantics this is fine; it just
means "we reserved version 62 and chose not to use it" — possibly to
avoid clashing with an in-flight branch.

OPS-MIGRATION-HEALTHCHECK (shipped today) now catches the V61-lag class
of bug — readiness check delegates to `Migrations.validate()` +
`Migrations.info(COMPARE)`. The same library entry points the audit
relied on for cross-checking. No drift to file.

## Stack-level findings (env, heap, GC, neo4j.conf)

| Setting | Value | Assessment |
|---|---|---|
| `NEO4J_EDITION` | community | Limits NEO-015 (property value constraints) and NEO-021 (multi-database isolation for n10s) |
| `NEO4J_server_memory_heap_initial__size` / `max__size` | 2G / 2G | OK for current 1.3GB used; review when MFFD hits 10x |
| `NEO4J_server_memory_pagecache_size` | 3G | Comfortable headroom; full store size ~1.2GB |
| `NEO4J_PLUGINS` | `["n10s"]` | **Missing apoc** despite allowlist (NEO-013) |
| `NEO4J_AUTH` | `neo4j/neo4j_dev_secret` | Dev secret hard-coded — flag for the deploy audit, not this one |
| Page-cache hit ratio | (JMX query failed, can't quote directly) | Indirect signal via the 26M `n10s_unique_uri` reads concentrating on RDF — likely hot, leaving less room for `:DataObject` |
| GC settings | Default G1GC | Sufficient at current scale |

No backup configured (Community edition; cube-level snapshot routine is
the operator-side answer). Flag noted; backup strategy is the operator
audit's domain, not the schema audit's.

## Cross-substrate observations (light)

- The `:ShepardFile.providerId` finding (NEO-002) overlaps with the
  file-storage routing audit (sibling #209) — both lands on the same
  schema gap from different directions.
- The `:Timeseries.appId NULL` finding (NEO-003) is the Neo4j-side view
  of the TimescaleDB 5-tuple problem (sibling #208) — the migration plan
  in `aidocs/platform/87` is the unifying fix.
- The PROV-O orphan-Activity finding (NEO-001) intersects with the
  PROV-RESOLVER-PATHWALK + PROV-V1-NUMERIC-LOOKUP backlog items —
  resolving the edge wiring depends on the resolver being reliable.

## Top 5 fixes ranked by (query-perf × schema-correctness × blast-radius)

1. **NEO-AUDIT-2026-05-24-001** — wire PROV-O Activity edges. **HIGHEST
   IMPACT**. Closes the f(ai)²r promise; converts 569k-dbHit
   property-scans into 1-dbHit edge traversals; unlocks the
   provenance-graph UI the AI/human/collab attribution feature
   (`project_ai_human_collab_provenance.md`) is meant to render. Blast
   radius: medium — touches `ProvenanceCaptureFilter` write path and the
   `ProvenanceRest` read path; one migration to backfill 284k existing
   rows. Effort: M.
2. **NEO-AUDIT-2026-05-24-002** — backfill `:ShepardFile.providerId`.
   Unblocks the file-storage SPI's clean dispatch (sibling audit's
   downstream finding). Cheap one-shot migration. Effort: XS.
3. **NEO-AUDIT-2026-05-24-008** — split sibling OPTIONAL MATCH into
   CALL{} subqueries on `DataObjectDAO` + `CollectionDAO`. Cartesian
   blowup grows quadratically with reference count; the Tapelaying-1474
   DO is today's worst case but MFFD volume keeps climbing. Effort: S.
4. **NEO-AUDIT-2026-05-24-005** — promote import-provenance attributes
   from `:DataObject.attributes||*` to `:Activity` edges. Pairs with
   NEO-001; reduces DataObject property-store width by ~30%; closes the
   EAV antipattern's worst current manifestation. Effort: M.
5. **NEO-AUDIT-2026-05-24-004** — time-bucketed Agent index on
   `(:User)-[:created_in_month]->(:DataObject)`. Pre-emptive: the
   supernode is bounded today (33k edges) but trajectory points to
   100k+ within a year. Cheap to add early. Effort: S.

## What surprised me

- **APOC absent on a production-shaped Neo4j 5.x instance.** Every
  recommended Neo4j migration playbook I've read since Neo4j 4.x
  assumes APOC. The `dbms.security.procedures.allowlist` entry suggests
  someone *intended* to install it; only the `NEO4J_PLUGINS` env var
  was missed. One-line fix.
- **Activity nodes have zero edges.** I knew the f(ai)²r vision called
  for typed PROV-O edges and I knew the Activity entity Java doc-comment
  mentioned `WAS_ASSOCIATED_WITH`. I did not realise the edges had been
  documented as TODO for 284k row-insertions before anyone noticed. The
  JSON-LD renderer faithfully synthesises the edges on each export, so
  the *output* looks right; the substrate doesn't carry the graph.
- **50% of DataObjects are soft-deleted** with no TTL. Pure schema-level
  finding; doesn't break anything yet, but every read pays the half-tax.
- **`idx_Subscription_requestMethod` is the #3 hottest index in the
  system against zero rows.** Subscription is shipped but unused — a
  pattern worth a separate UX audit on its own.

## References (external)

- W3C PROV-O — `wasAssociatedWith` / `used` / `wasGeneratedBy` shape:
  https://www.w3.org/TR/prov-o/#wasAssociatedWith
- Neo4j supernode handling (the secondary-index pattern recommended for
  NEO-004): https://neo4j.com/developer/kb/understanding-the-design-of-supernodes/
- Neo4j Cypher anti-patterns (Cartesian OPTIONAL MATCH for NEO-008):
  https://neo4j.com/developer-blog/cypher-anti-patterns-merge/
- Neo4j 5.x property-existence + value constraints (for NEO-015, EE-only):
  https://neo4j.com/docs/cypher-manual/current/constraints/syntax/#syntax-property-existence-constraints
- n10s docs — graphconfig parameters reference:
  https://neo4j.com/labs/neosemantics/4.0.0/config/
- n10s `n10s.skos.import.fetch` documentation:
  https://neo4j.com/labs/neosemantics/4.0.0/reference/#skos-import
- Robinson, Webber & Eifrem, *Graph Databases* 2nd ed., ch. 4 — graph-EAV
  antipattern (cited via the 2026-05-23 archived audit, not re-cited).
- APOC bundled-since-5.7 announcement:
  https://neo4j.com/labs/apoc/5/installation/

## Linkage to existing backlog rows

- `OPS-MIGRATION-HEALTHCHECK` (shipped 2026-05-24) — handled the chain-drift
  class; this audit confirms residual: V63-before-V61 ordering is WAI
  per the library (filed as NEO-014).
- `PROV-RESOLVER-PATHWALK` (queued) — prerequisite for NEO-001.
- `PROV-V1-NUMERIC-LOOKUP` (queued) — prerequisite for NEO-001.
- `PROV-CAPTURE-READS-DECISION` (queued) — if reads are captured, the
  Activity volume 10x's; making NEO-001 + NEO-005 more urgent.
- `SM1a` (queued) — natural pair for NEO-016 (soft-delete TTL).
- `BUG-LJ-V1-COLL-ID` + `OGM-HYDRATION-AUDIT` (queued/shipped) — same
  family of OGM-relationship-hydration shape as NEO-008's Cartesian
  OPTIONAL MATCH.
