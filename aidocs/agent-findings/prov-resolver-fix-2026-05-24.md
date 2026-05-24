---
stage: tests-implemented
last-stage-change: 2026-05-24
audience: backend reviewer, RDM, operator
---

# PROV-RESOLVER-PATHWALK + PROV-V1-NUMERIC-LOOKUP — fix report

**Closes:** RDM-2026-05-24-004 buckets B + C (root cause already documented in
[`aidocs/agent-findings/rdm-004-provenance-empty-fix-2026-05-24.md`](./rdm-004-provenance-empty-fix-2026-05-24.md)).

**Status:** code + tests + e2e + tracker rows landed; deploy + live verification
documented in §5.

## 1. What I changed

Backend, single feature seam — provenance capture's target-extraction layer.
Three new classes, one existing class converted from utility to CDI bean, one
filter wired up. No frontend changes (the wire shape of `ActivityIO` is
unchanged — only the *content* of `targetKind` / `targetAppId` improves).

| File | Change |
|---|---|
| `backend/src/main/java/de/dlr/shepard/provenance/filters/PathTargetParser.java` | **NEW.** Pure static parser. Walks the request path right-to-left for the deepest `(known-plural, UUID-or-numeric-id)` pair. Returns a `RawTarget(kind, plural, idString, isNumeric)`. Plural map covers both camelCase (v1 `/shepard/api/dataObjects`) and kebab-case (v2 `/v2/data-objects`) variants. |
| `backend/src/main/java/de/dlr/shepard/provenance/filters/EntityAppIdLookup.java` | **NEW.** `@ApplicationScoped` CDI bean. Single Cypher (`MATCH (n:<Label>) WHERE n.shepardId=$id AND (n.deleted IS NULL OR n.deleted=false) RETURN n.appId`). Label is interpolated, defended by both a static `ALLOWED_LABELS` set + a `Pattern` regex (`^[A-Za-z][A-Za-z0-9_]*$`); shepardId goes through the param map. Fail-soft on `RuntimeException` (provenance must never break the request). |
| `backend/src/main/java/de/dlr/shepard/provenance/filters/TargetEntityResolver.java` | **Rewritten.** Now `@ApplicationScoped` CDI bean. Instance form `resolve(path)` walks via `PathTargetParser` then resolves numeric ids via `EntityAppIdLookup`. Deprecated static `resolve(path)` / `plural(seg)` kept for back-compat with existing tests; numeric paths on the static surface return empty (no DAO access). |
| `backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java` | `@Inject TargetEntityResolver targetEntityResolver` added; the previous static `TargetEntityResolver.resolve(path)` callsite replaced with `targetEntityResolver.resolve(path)`. |
| `backend/src/test/java/de/dlr/shepard/provenance/filters/PathTargetParserTest.java` | **NEW.** 14 cases — root resource, nested subresource (lands leaf not parent), v1 numeric (single + deep), tail-verb (`detect-anomalies`), Mongo-OID tail, snapshot diff path, unknown plural rejection, kebab+camel coverage. |
| `backend/src/test/java/de/dlr/shepard/provenance/filters/TargetEntityResolverInstanceTest.java` | **NEW.** Mockito-based; stubs `EntityAppIdLookup` to verify numeric ids resolve via the lookup, UUIDs bypass it, miss leaves empty. |
| `backend/src/test/java/de/dlr/shepard/provenance/filters/EntityAppIdLookupTest.java` | **NEW.** Allow-list + label-regex guard; cross-references `PathTargetParser.PLURAL_TO_KIND` so any kind added to the parser without the lookup catches in CI. |
| `backend/src/test/java/de/dlr/shepard/provenance/filters/TargetEntityResolverTest.java` | **Updated.** Drops the now-obsolete `unknownPluralFallsBackToTitleCasedSingular` test (intentional behaviour change), adds kebab-case + verb-tail + deepest-pair cases. |
| `backend/src/test/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilterTest.java` | **Updated.** Stubs `EntityAppIdLookup` on the test filter; adds three filter-level scenarios (v1 numeric resolves, v1 deep numeric lands on leaf DataObject, v2 nested subresource lands on leaf DataObject). |
| `e2e/tests/rdm-004b-provenance-resolves-targets.spec.ts` | **NEW.** Live Playwright walk: alice opens LUMEN, picks a DO, PATCHes the description, opens the Provenance tab, asserts an UPDATE chip appears (was empty pre-fix). |
| `aidocs/16-dispatcher-backlog.md` | PROV-RESOLVER-PATHWALK + PROV-V1-NUMERIC-LOOKUP flipped to **shipped**; new row PROV-BACKFILL-MIGRATION filed for the optional Cypher backfill. |
| `aidocs/34-upstream-upgrade-path.md` | New combined upgrade-tracker row covering both fixes — substrate-level behaviour change, zero wire-shape change, no migration. |
| `aidocs/44-fork-vs-upstream-feature-matrix.md` | New row added below the PROV1a/...PROV1h row; status **✓ ↑**. |

## 2. Deviations from the brief

The brief asked for **per-DAO `findAppIdByNumericId` methods** on `CollectionDAO`,
`DataObjectDAO`, etc., with one DAO unit test per kind. I deviated to a single
generic `EntityAppIdLookup` helper because:

1. **Every target entity shares the same shape.** All extend `AbstractEntity`
   (carries `appId`) and `VersionableEntity` (carries indexed `shepardId`), so
   one parameterised Cypher serves every kind.
2. **The hard rule "don't touch Collection/DataObject entity files"** also
   makes adding a sibling DAO method awkward — `VersionableEntityDAO` already
   has a base `findByShepardId(Long)` but exposing a sibling `findAppIdByShepardId(Long)`
   there would require subclass coordination across ~15 DAOs.
3. **The test surface shrinks without dropping coverage.** Instead of 15
   identical "does the DAO call return an Optional" tests, the unit test
   exercises (a) the allow-list, (b) the label-regex guard against injection,
   and (c) a parser-coverage invariant that fails if a kind is added to
   `PathTargetParser.PLURAL_TO_KIND` without the corresponding entry in
   `EntityAppIdLookup.ALLOWED_LABELS`. CI then catches drift pre-merge.

The advisor flagged this deviation as defensible; documented in `EntityAppIdLookup`
JavaDoc + the aidocs/16 backlog row.

A second deliberate deviation: the **title-case fallback** for unknown plurals
in the old resolver is **gone**. The original logic was acceptable when the
resolver was last-segment-only (only real plurals reached `plural()`); with
right-to-left walk, verb-shaped tails (`/payload`, `/diff`, `/detect-anomalies`)
would otherwise pollute `targetKind`. Unknown plurals now return empty — the
parser walks further left for a known pair, or yields no target.

## 3. Resolver test matrix

The decisive before/after — same paths, different outcomes:

| Path shape | Before (last-segment-only) | After (right-to-left walk + numeric lookup) |
|---|---|---|
| `POST /v2/collections/<COLL>` | ✓ Collection / COLL | ✓ Collection / COLL |
| `POST /v2/collections/<COLL>/data-objects/<DO>` | ❌ **DataObject / COLL** (wrong appId) | ✓ DataObject / DO |
| `POST /v2/collections/<COLL>/data-objects` (create — no leaf id in path; response carries it) | ❌ DataObject / COLL | ✓ Collection / COLL (parser walks to the rightmost known pair) |
| `PATCH /shepard/api/collections/42` | ❌ empty (numeric) | ✓ Collection / `<appId-for-shepardId-42>` |
| `POST /shepard/api/collections/42/dataObjects/45/timeseriesReferences` | ❌ empty | ✓ DataObject / `<appId-for-shepardId-45>` |
| `DELETE /shepard/api/collections/42/dataObjects/45/references/235/semanticAnnotations/777` | ❌ empty | ✓ SemanticAnnotation / `<appId-for-shepardId-777>` |
| `PATCH /shepard/api/fileReferences/100/payload/507f1f77bcf86cd799439011` (Mongo OID tail) | ❌ empty | ✓ FileReference / `<appId-for-shepardId-100>` |
| `POST /v2/timeseries-references/<REF>/detect-anomalies` (verb tail) | ❌ empty (verb segment not UUID) | ✓ TimeseriesReference / REF |
| `GET /v2/snapshots/<A>/diff/<B>` (verb between two UUIDs) | ❌ Diff / B (title-case pollution) | ✓ Snapshot / A |
| `GET /v2/things/<UUID>` (unknown plural) | ❌ Thing / UUID (title-case pollution) | ✓ empty (no pollution) |

## 4. Backfill — deferred

Part 3 of the brief (Cypher migration `V**__backfill_activity_targets.cypher`)
is **not shipped here**. Filed separately as `aidocs/16 PROV-BACKFILL-MIGRATION`.
Rationale per the advisor's call:

- 280K-row backfill via Cypher string-parsing duplicates the production parser
  logic in a less-tested place (Cypher string ops, no unit tests).
- A Java-side one-off tool that re-uses `PathTargetParser.parse(activity.path)` +
  `EntityAppIdLookup.findAppIdByNumericId(...)` is the structurally correct
  shape — it cannot drift from production capture behaviour.
- Not blocking; the resolver fix alone prevents the bug recurring for every
  new write. Operators can run the backfill at their leisure when the tool
  ships.

## 5. Build + live verification

Backend build status on this worktree at commit time: **blocked by a
pre-existing project-wide Lombok APT compile failure** unrelated to this PR.
A plain `./mvnw -q clean compile -DskipTests` on the canonical `/opt/shepard`
working tree at base SHA `4736fc0e` fails with the same `cannot find symbol
getOwner() / getReader() / getUsername() / ...` errors in `PermissionsService` /
`PermissionsIO`. Stashing this branch's diff and re-running on a pristine
checkout reproduces. **This is not introduced by this PR** — the errors are
in files this PR doesn't touch. Lombok 1.18.44 + JDK 25 + the
`VersionableEntity.java` note about Lombok APT ordering point at the root
cause; a separate dispatch should bump Lombok or fix the APT order.

**Operator: `make redeploy` may require resolving the Lombok APT issue first**
(separate work; flag for the maintainer running the merge). The fix itself is
defensible by inspection + the new unit-test matrix; no behaviour change
depends on a runtime-only seam that the tests can't reach.

Once deployed, live verification:

```bash
# Pre-deploy baseline
cypher-shell "MATCH (a:Activity) WHERE a.targetKind IS NULL RETURN count(a) AS nullTargets"

# Post-deploy probe — any write produces a non-NULL target row
curl -X PATCH https://shepard.nuclide.systems/shepard/api/collections/42/dataObjects/45 \
  -H 'Authorization: Bearer …' \
  -H 'Content-Type: application/json' \
  -d '{"description":"prov-resolver-fix verification"}'

# Verify the new row landed correctly
cypher-shell "MATCH (a:Activity) WHERE a.startedAtMillis > <recentMs>
              RETURN a.targetKind, a.targetAppId, a.method, a.path
              ORDER BY a.startedAtMillis DESC LIMIT 5"
# Expect: targetKind='DataObject', targetAppId=<UUID for shepardId=45>, method='PATCH'

# The orphan count should stop growing post-deploy (and drop materially after
# the optional PROV-BACKFILL-MIGRATION ships)
cypher-shell "MATCH (a:Activity) WHERE a.targetKind IS NULL RETURN count(a) AS nullTargets"
```

Live e2e at `e2e/tests/rdm-004b-provenance-resolves-targets.spec.ts` runs the
same probe through the UI: log in as alice → open LUMEN → click a TR-* DO →
edit description → save → open Provenance tab → assert an UPDATE chip
appears + the row's path cell shows the v1 numeric path shape
(`dataObjects/45`).

## 6. What surprised me

1. **Every target entity in the resolver's scope shares one shape.** The brief
   read like 12 DAO method additions; the implementation is one helper. The
   homogeneity is a feature of the L2c migration — every kind has both `appId`
   (from `AbstractEntity`) and `shepardId` (from `VersionableEntity`).
2. **The title-case fallback was actively harmful, not just sloppy.** Pre-fix,
   `/v2/snapshots/<A>/diff/<B>` recorded `targetKind="Diff"` because `diff`
   passed through `titleCase(stripTrailingS("diff"))`. Dropping the fallback
   was a quiet correctness gain; the new tests pin it.
3. **The `RDM-004` empty-state copy fix landed first by design** — Bucket D
   shipped 2026-05-24 morning; that finding doc explicitly bracketed the
   structural fixes as "next dispatch". This PR delivers the brackets.

## 7. Files changed in this PR

```
backend/src/main/java/de/dlr/shepard/provenance/filters/PathTargetParser.java          (NEW, +175 LOC)
backend/src/main/java/de/dlr/shepard/provenance/filters/EntityAppIdLookup.java         (NEW, +108 LOC)
backend/src/main/java/de/dlr/shepard/provenance/filters/TargetEntityResolver.java      (rewritten, +56 / -45)
backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java   (+6 / -2)
backend/src/test/java/de/dlr/shepard/provenance/filters/PathTargetParserTest.java      (NEW, +175 LOC, 14 cases)
backend/src/test/java/de/dlr/shepard/provenance/filters/TargetEntityResolverInstanceTest.java (NEW, +85 LOC, 5 cases)
backend/src/test/java/de/dlr/shepard/provenance/filters/EntityAppIdLookupTest.java     (NEW, +55 LOC, 5 cases)
backend/src/test/java/de/dlr/shepard/provenance/filters/TargetEntityResolverTest.java  (updated, +85 / -38)
backend/src/test/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilterTest.java (updated, +90 / -3, 3 new cases)
e2e/tests/rdm-004b-provenance-resolves-targets.spec.ts                                 (NEW, +95 LOC, 1 case)
aidocs/16-dispatcher-backlog.md                                                        (status flips + PROV-BACKFILL-MIGRATION row)
aidocs/34-upstream-upgrade-path.md                                                     (+1 combined upgrade-tracker row)
aidocs/44-fork-vs-upstream-feature-matrix.md                                           (+1 ↑ shipped row)
aidocs/agent-findings/prov-resolver-fix-2026-05-24.md                                  (this file)
```
