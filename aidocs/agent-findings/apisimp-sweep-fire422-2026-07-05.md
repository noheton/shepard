---
stage: deployed
last-stage-change: 2026-07-05
---

# APISIMP Sweep — fire-422 (2026-07-05)

Sweep of the remaining `subList`-based in-memory pagination sites in the
`/v2/` REST surface. Companion to the fire-420 sweep
(`apisimp-sweep-fire420-2026-07-05.md`) which filed F1–F3; this sweep closes
the remaining actionable set.

## §F1 — APISIMP-SNAPSHOT-MANIFEST-IN-MEMORY-PAGING (✅ implemented this fire)

**File:** `SnapshotRest.java:164–175` (before this fix)

`manifest()` called `snapshotService.findEntries(snapshot.getId())` — an
unbounded `session.query(SnapshotEntry.class, …)` materialising every
non-deleted SnapshotEntry for the snapshot — then sliced with
`allEntries.subList(from, to)`.

**Severity:** High. Snapshots of large Collections can contain 10 000+
SnapshotEntry nodes. Loading all of them for a page-200 request is O(N) on
every manifest call.

**Fix shipped in this PR:**
- `SnapshotDAO.countEntriesBySnapshot(long)` — `RETURN count(e) AS total`
- `SnapshotDAO.findEntriesBySnapshot(long, int skip, int limit)` — adds
  `SKIP $skip LIMIT $limit` to the existing Cypher; params cast to `(long)`.
- `SnapshotService.countEntries(long)` + `findEntriesPage(long, int, int)` —
  thin delegates.
- `SnapshotRest.manifest()` — calls `countEntries` then `findEntriesPage`;
  `subList` removed.
- `SnapshotRestTest` — 4 pagination tests updated to mock the new delegates.

25/25 unit tests pass.

---

## §F2 — APISIMP-REFERENCES-LIST-IN-MEMORY-PAGING

**File:** `ReferencesV2Rest.java:~490–503`

`listReferences()` (`GET /v2/dataobjects/{appId}/references`) calls an
unbounded service method and slices with `all.subList(from, to)`. Medium
urgency — large DataObjects in MFFD image stacks may have hundreds of
references.

**Proposed fix:** Add `countReferencesByDataObjectAppId(String)` + bounded
`findReferencesByDataObjectAppId(String, int skip, int limit)` to the
reference DAO. Update `ReferencesService` delegates and `ReferencesV2Rest`.

**Row filed:** `APISIMP-REFERENCES-LIST-IN-MEMORY-PAGING` in `aidocs/16`.

---

## §F3 — APISIMP-CONTAINERS-LIST-IN-MEMORY-PAGING

**File:** `ContainersV2Rest.java:~460–475`

`listContainers()` (`GET /v2/dataobjects/{appId}/containers`) loads all
containers for a DataObject then Java-slices. Similar profile to F2.

**Proposed fix:** Add `countContainersByDataObjectAppId(String)` + bounded
`findContainersByDataObjectAppId(String, int skip, int limit)` to
ContainerDAO; update service + REST.

**Row filed:** `APISIMP-CONTAINERS-LIST-IN-MEMORY-PAGING` in `aidocs/16`.

---

## §F4 — APISIMP-DATAOBJECT-PREDECESSORS-IN-MEMORY-PAGING

**File:** `DataObjectV2Rest.java:679, 793, 842`

Three list endpoints — `listPredecessors()`, `listSuccessors()`,
`listChildren()` — all load the complete set of related DataObjects then
Java-paginate. These are lineage graph traversals; for MFFD process chains
with many successors (Q1 + Q2 parallel tracks) this materialises the full
neighbourhood.

**Proposed fix:** Add bounded Cypher SKIP/LIMIT variants for each direction
(`PREDECESSOR_OF`, `SUCCESSOR_OF`, parent/child) in `DataObjectDAO`; add
`count*` partners; update `DataObjectService` + three REST handlers.

**Row filed:** `APISIMP-DATAOBJECT-PREDECESSORS-IN-MEMORY-PAGING` in
`aidocs/16` (covers all three directions).

---

## §F5 — APISIMP-VOCABULARY-BROWSE-IN-MEMORY-PAGING

**Files:**
- `VocabularyBrowseRest.java:172`
- `PersonalVocabularyRest.java:206`

Both vocabulary term list endpoints load all terms then slice. Low urgency
(typical vocabulary ≤ 500 terms) but violates the bounded-DAO contract and
will break if a large SHACL vocabulary is imported.

**Proposed fix:** Add `countTermsByVocabulary` + bounded `findTermsByVocabulary`
DAO pair; update both REST handlers.

**Row filed:** `APISIMP-VOCABULARY-BROWSE-IN-MEMORY-PAGING` in `aidocs/16`
(one row covers both endpoints).

---

## Additional sites NOT filed as actionable rows

The following `subList` calls were found but are NOT in-memory pagination
violations requiring a DB-push fix:

- `ReferenceAnnotationRest.java:164`, `TimeseriesContainerKindHandler.java:526,564`
  — These use the same subList pattern but the query scope is per-entity (one
  reference / one container); the annotation set is bounded by the number of
  annotations on a single entity. Typically O(10s), not O(10 000s). Low priority;
  may be filed if operator feedback surfaces performance issues.
- `SnapshotDiffRest.java:207,211,215` — Safety cap on diff result lists, not
  user-facing pagination. Correct as-is.
- `CollectionDQRRest.java:102,208` — DQR result aggregation; correctness requires
  loading all results before scoring. Not a pagination anti-pattern.
- `SnapshotPinnedReadRest.java:154` — Pinned entity list bounded by snapshot entry
  count; already paginated at the snapshot-manifest layer (this fire). Low urgency.
- `ImportDiagnosticsV2Rest.java:152` — Import event log; truncation-cap pattern,
  not user-facing pagination.
- `ContentMcpTools.java:215`, `SearchMcpTools.java:240` — MCP tool internal
  buffering; out of scope for REST pagination fixes.
