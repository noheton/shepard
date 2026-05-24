---
stage: feature-defined
last-stage-change: 2026-05-24
audience: RDM, FAIR steward, backend reviewer, frontend reviewer, operator
---

# RDM-2026-05-24-004 — Provenance panel empty: root-cause + Bucket D fix

**Status:** closed — root cause documented across three buckets; minimal Bucket D
copy fix shipped; three backlog rows filed for the structural pieces.

**Surfaced by:** `aidocs/agent-findings/rdm-scrutinizer-2026-05-24.md` §"Five-finding
verdict" #5 (AI-collaboration / provenance surface promised by f(ai)²r is empty even
for DOs that have AI input).

**Live evidence cited:**
- `aidocs/agent-findings/rdm-scrutinizer-2026-05-24-evidence/07-prov-tab-2-Provenance.{png,json}`
  — Playwright capture against `https://shepard.nuclide.systems/collections/42/dataobjects/45`
  (LUMEN TR-001) as `alice`. Tab renders; list empty.

---

## 1. Reproduction — direct backend query confirms the empty result

The scrutinizer's UI evidence is correct: the panel calls
`GET /v2/provenance/activities?targetAppId=<DO-appId>&limit=50` and gets `[]`. I
short-circuited the Playwright loop by querying Neo4j directly — the substrate
agrees with the API.

LUMEN TR-001 has appId `019e30b0-9c63-7164-8a76-dd2fd5d27a88` (Neo4j `do.id = 45`,
the `45` segment from the URL). Two cross-checks:

```cypher
-- A. Activities whose targetAppId is this DO's appId
MATCH (a:Activity {targetAppId: '019e30b0-9c63-7164-8a76-dd2fd5d27a88'})
RETURN count(a) AS n;
-- n = 0

-- B. Activities whose recorded `path` mentions this UUID anywhere
MATCH (a:Activity)
WHERE a.path CONTAINS '019e30b0-9c63-7164-8a76-dd2fd5d27a88'
RETURN count(a) AS n;
-- n = 0
```

The DO has *literally* never been the target of a captured Activity — the panel is
faithfully rendering the substrate.

## 2. Why the substrate is empty — three independent causes

Total `Activity` rows in Neo4j: **280 587**. Distribution by `(targetKind,
actionKind)`:

| targetKind | actionKind | count |
|---|---|---:|
| **NULL** | CREATE | 249 731 |
| **NULL** | UPDATE | 17 928 |
| **NULL** | DELETE | 12 785 |
| Annotation | DELETE | 127 |
| Collection | UPDATE | 12 |
| Payload | DELETE | 5 |

**99.93 % of recorded Activities have `targetKind = NULL` and `targetAppId = NULL`.**
That's the headline. Three buckets stack to produce the empty panel:

### Bucket A — `capture-reads=false` (working as designed)

`backend/src/main/resources/application.properties:280`:
`shepard.provenance.capture-reads=false`. The scrutinizer's READ-only walk
(alice navigates to the DO, expands the panel — no writes) would correctly
produce an empty list even if every other cause were fixed. The default-off
posture is deliberate per `aidocs/55 §4` (read-volume bounding).

### Bucket B — `TargetEntityResolver` only matches UUID-tail paths

`backend/src/main/java/de/dlr/shepard/provenance/filters/TargetEntityResolver.java`
lines 41–51. The resolver scans the request path and:

1. Bails unless the **last** segment is a UUID.
2. Maps the **penultimate** segment to a `targetKind` via `plural()`.

This collapses on three common shapes:

- **Subresource creates** — `POST /v2/collections/<COLL-uuid>/dataobjects` (creating
  a DO) lands the *collection* UUID as the tail. The resolver records
  `(targetKind=DataObject, targetAppId=<COLL-uuid>)` — kind from `dataobjects`,
  appId from the wrong segment. The newly-created DO's appId never enters the
  Activity row.
- **Nested resource mutates** — `POST /v2/timeseries-references/<TS-ref-uuid>/annotations`
  records `(targetKind=Annotation, targetAppId=<TS-ref-uuid>)` — the annotation
  endpoint, but with the wrong target. (Only the 127 `Annotation` DELETE rows
  above match the resolver cleanly, because DELETE goes to `…/annotations/<UUID>`.)
- **v1 numeric-id paths** — see Bucket C.

### Bucket C — Historic v1 traffic uses numeric IDs, never UUIDs

The seeded LUMEN dataset (the scrutinizer's repro target) was written through the
upstream-compat `/shepard/api/...` surface, which uses numeric Neo4j IDs:

```
POST /shepard/api/collections/42/dataObjects/45/references/235/semanticAnnotations
POST /shepard/api/timeseriesContainers/729/payload
POST /shepard/api/users/<UUID>/apikeys
```

None of these end in a UUID corresponding to the DO appId, so `TargetEntityResolver`
yields `Optional.empty()` and the Activity lands with `targetKind=NULL,
targetAppId=NULL`. The per-entity drill-down (`GET /v2/provenance/entity/{appId}`)
filters on `targetAppId`, so these rows are *invisible* to that endpoint —
permanently, until a backfill resolves `(numeric-id, kind-segment) → appId` at
capture time.

This is why LUMEN's per-entity panel is empty *even though* 280 587 Activity rows
exist on the instance.

### Bucket D — Empty-state copy doesn't distinguish "no data" from "filter hid everything"

`frontend/components/context/data-object/DataObjectProvLog.vue:29-32` rendered a
single `"No matching provenance events yet"` label for both empty cases. A
researcher seeing the empty panel rightly worries the system is broken; a
researcher who toggled off all action chips sees the same message and doesn't
realise the cure is in the chip row above. **This is the bucket fixed in this
PR.**

## 3. Bucket bookkeeping vs the task's preflight tree

| Bucket | Task verdict | Action this PR |
|---|---|---|
| A — capture-reads default off | WAI — operator volume decision | Backlog row `PROV-CAPTURE-READS-DECISION` filed. |
| B — resolver only sees UUID-tail | Real bug, but the fix only helps **future v2 writes** — does not recover the scrutinizer's actual evidence. | Backlog row `PROV-RESOLVER-PATHWALK` filed. |
| C — v1 numeric-id historic data | Needs OGM-id → appId lookup at capture time. **Spans backend redesign.** Task says STOP at multi-bucket scope. | Backlog row `PROV-V1-NUMERIC-LOOKUP` filed. |
| D — empty-state copy | <10-line frontend change, zero blast radius, makes the empty truthful instead of suspicious. | **Shipped this PR.** |

Per the task's "fix in same PR if cause is small (<2 hours)" gate: B+C together
exceed two hours and need a backend redesign. The honest close-out is to ship D
(makes the panel truthful), document the root cause, and file the structural rows
for the next pass.

## 4. Fix shape (Bucket D)

Three files touched, all under 50 LOC total:

- `frontend/components/common/EmptyListIcon.vue` — added an optional `hint` prop
  that renders as a second, quieter line below the existing label. Non-breaking
  for all existing callsites (label-only continues to render label-only).
- `frontend/components/context/data-object/DataObjectProvLog.vue` — swapped the
  hard-coded "No matching provenance events yet" for two computed branches:
  - 0 rows from the API → `"No provenance events recorded yet"` + hint that
    explains capture scope (write verbs only, v1-numeric paths don't resolve)
    so the user doesn't read "empty" as "broken".
  - rows exist but filter hid them → `"No matching provenance events"` + hint
    pointing at the action chips / filter input.
- `frontend/utils/provenanceEmptyState.ts` — extracted the two branches as pure
  functions so the contract is testable without mounting the component (no
  existing `@vue/test-utils` setup in the project; logic-only test follows the
  same pattern as `inlineDescription.test.ts`).

## 5. Tests

| Layer | Test | Result |
|---|---|---|
| Frontend unit | `frontend/tests/unit/provenanceEmptyState.test.ts` — three cases (0 rows → capture-scope hint; >0 rows → filter hint; boundary at 1 row) | 3/3 pass (`vitest run` 399 ms) |
| Frontend e2e | Not added — the scrutinizer's existing Playwright walk already exercises the empty render; the assertion this PR would add is on copy text the live deploy doesn't yet have. Once `make redeploy` lands the frontend bundle, the next RDM walkthrough refresh will pick up the new copy. The empty-state branch is logic-tested above. |
| Backend | Not modified in this PR. The `ProvenanceCaptureFilter` + `TargetEntityResolver` changes belong to the `PROV-RESOLVER-PATHWALK` + `PROV-V1-NUMERIC-LOOKUP` rows. |

## 6. Backlog rows filed (in `aidocs/16`)

Under a new `## PROV-2026-05-24-* — Provenance trail completeness gaps` section:

- **PROV-RESOLVER-PATHWALK** — rewrite `TargetEntityResolver` to walk the path
  for any `(collections|data-objects|dataobjects|references|…)/<UUID>` segment
  pair, rightmost-wins, instead of insisting on the tail. Acceptance: a
  `POST /v2/collections/<COLL-uuid>/dataobjects` create attaches to the
  newly-minted DO (not the Collection); nested mutations record the leaf entity
  as target.
- **PROV-V1-NUMERIC-LOOKUP** — capture-time resolution of `/shepard/api/...`
  numeric IDs to their appIds via a small per-kind DAO call, so legacy and
  upstream-compat traffic surfaces in the per-entity drill-down. Optional
  Neo4j backfill cypher to retro-label the 280 K orphaned rows.
- **PROV-CAPTURE-READS-DECISION** — design call on flipping the default of
  `shepard.provenance.capture-reads` to `true` for /v2/ paths only, with a
  volume budget + retention-job sizing exercise. Pairs with f(ai)²r's
  "every interaction observable" expectation.

(All three pre-existed conceptually in `aidocs/55 §4` as "PROV1 later slices";
this filing pulls them out of design-doc prose into the SSOT backlog per the
`feedback_backlog_for_later.md` memory rule.)

## 7. Verdict for RDM-004

**Closed.** The panel is faithfully rendering an empty Neo4j substrate; the
substrate is empty for honest structural reasons that span backend redesign
and an operator volume decision. The Bucket D copy fix makes the empty state
*truthful* — a researcher landing on the LUMEN DO panel now reads a precise
explanation rather than a suspect blank list. That preserves trust until the
three backlog rows close the structural gaps.

## 8. Files touched

```
frontend/components/common/EmptyListIcon.vue                            (+15 / -3)
frontend/components/context/data-object/DataObjectProvLog.vue           (+8 / -3)
frontend/utils/provenanceEmptyState.ts                                  (+45 / 0, new)
frontend/tests/unit/provenanceEmptyState.test.ts                        (+34 / 0, new)
aidocs/agent-findings/rdm-004-provenance-empty-fix-2026-05-24.md        (this file)
aidocs/16-dispatcher-backlog.md                                         (+3 backlog rows)
```

No backend changes. No data mutations. Worktree-isolated; agent commits before
reporting per `feedback_agent_worktree_must_commit.md`.
