---
stage: fragment
last-stage-change: 2026-07-02
---

# APISIMP REST Surface Sweep — fire-373 (2026-07-02)

Axes scanned: 1 (uncapped diff-result lists). Scope: remaining snapshot endpoints
not yet covered by prior sweeps; `backend/src/main/java/de/dlr/shepard/v2/**/*.java`.

Context: prior sweeps (fire-353, fire-355, fire-367) cleared the bulk of fake-paged
wrappers, unbounded admin lists, and uncapped DQR/manifest/pinned-DO endpoints.
This fire completed a targeted pass over the snapshot diff surface.

---

## Axis 1 — Uncapped diff-result lists (1 finding)

### F1 — APISIMP-SNAPSHOT-DIFF-UNCAPPED

| | |
|---|---|
| **Severity** | MINOR |
| **Size** | S |
| **File** | `backend/src/main/java/de/dlr/shepard/v2/snapshot/resources/SnapshotDiffRest.java` |
| **Endpoint** | `GET /v2/snapshots/{aAppId}/diff/{bAppId}` |

`SnapshotDiffRest.diff()` builds the full diff between two snapshot entry maps and
returns three unbounded lists:

- `added`: `List<String>` of entityAppIds in B but not A
- `removed`: `List<String>` of entityAppIds in A but not B
- `changed`: `List<DiffEntry{entityAppId, revA, revB}>` in both with different revision

For a 50 000-entity collection where all entities changed between snapshots, the
`changed` list alone serialises approximately 2.6 MB of UUID strings + revision
pairs (50 000 × `{"entityAppId":"<36-char-uuid>","revisionA":N,"revisionB":M}` ≈
52 bytes each). The `added` and `removed` lists compound this further when a
large-scale re-import occurs.

**Root cause:** No `?maxItems=` parameter, no per-list cap, no `truncated` signal.
Both snapshot entry maps are also loaded fully into memory as `Map<String, Long>` —
for 50 000 entries that is manageable (~3–4 MB JVM heap) but pairs with the
serialisation overhead to yield an outsized synchronous response.

The revision values stored in the maps are **domain-meaningful sequential integers**
(snapshot entry revisions, not Neo4j internal node IDs) — they must be preserved as-is
in any paginated/capped variant.

**Fix:** Add `@QueryParam("maxItems") @DefaultValue("5000") @Min(1) @Max(20000) int maxItems`;
cap each of the three lists independently (at `maxItems/3` rounded up); add
`truncated: boolean`, `totalAdded: int`, `totalRemoved: int`, `totalChanged: int`
fields to `SnapshotDiffIO` so callers know the full diff sizes regardless of truncation.
The summary fields (`unchangedCount`, `totalAdded`, etc.) are always accurate;
the list fields are capped when `truncated: true`.

**Acceptance criteria:**
- `?maxItems=100` with a 200-entry diff returns ≤100 total items across all three
  lists, `truncated: true`, and correct `totalAdded`/`totalRemoved`/`totalChanged`.
- `?maxItems=20001` is rejected 400 (JSR-380 `@Max(20000)`).
- Default call (no `?maxItems=`) with a 3-entry diff returns `truncated: false`.
- `mvn verify -pl backend` green.

---

## Summary

| ID | Size | Status |
|----|------|--------|
| APISIMP-SNAPSHOT-DIFF-UNCAPPED | S | ⏳ queued |

Rest of v2 surface surveyed this fire (CrossDoBulkDataRest, SqlTimeseriesRest,
ShepardTemplateRest, SnapshotDiffRest) was clean — no additional findings.
The v2 REST surface is in good shape after the extensive fire-353–fire-371 sweep
programme.
