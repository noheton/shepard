---
stage: deployed
last-stage-change: 2026-07-03
---

# APISIMP Sweep — fire-378 (2026-07-03)

Targeted scan of `/v2/` REST resources and plugin REST for new findings not covered by
previous sweeps. Scanned 6 axes: uncapped arrays, numeric id leaks, pagination gaps,
bespoke admin configs, hand-rolled error bodies, bulk endpoint caps.

## Findings

| # | Slug | File:line | Status |
|---|------|-----------|--------|
| 1 | APISIMP-CROSS-TIMELINE-UNCAP | `CollectionCrossTimelineRest.java:119` | ❌ False positive — already fixed in PR #2252 (`MAX_COLLECTIONS = 20` guard present) |
| 2 | APISIMP-INDEPENDENCE-PROOF-CAP | `IndependenceProofRequestIO.java:32,40` | ❌ False positive — already fixed in PR #2251 (`@Size(min=1, max=500)` on `setA`/`setB`) |
| 3 | APISIMP-REFERENCES-LIST-REAL-PAGINATION | `ReferencesV2Rest.java:469-497` | ❌ False positive — already fixed (real `?page`/`?pageSize` params + server slicing present) |
| 4 | **APISIMP-AAS-GET-SHELL-UNCAPPED** | `AasShellsRest.java:151` | ✅ **NEW** — `getShell()` embeds all DataObjects inline without cap |

*Note:* Findings 1–3 were produced by the sweep agent scanning a diverged local-main
branch. After resetting local to `origin/main` (which included the fixes), all three
were confirmed already addressed. Only Finding 4 is a genuine new gap.

## Finding 4: APISIMP-AAS-GET-SHELL-UNCAPPED

**Path:** `GET /v2/aas/shells/{aasId}`  
**File:** `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java:151`

```java
List<DataObject> dataObjects = dataObjectDAO.findTopLevelByCollectionAppId(appId);
return Response.ok(mappingService.toShell(collection, dataObjects)).build();
```

The `getShell()` method calls the 2-arg (no-pagination) form of
`findTopLevelByCollectionAppId`, loading the entire DataObject list and embedding it
inline in the Shell body as submodel references. A Collection with 10,000 top-level
DataObjects returns a response body with 10,000 inline references — effectively a
DoS vector with no guard.

The sibling `listSubmodels()` method (line 192) was fixed by `APISIMP-AAS-SUBMODELS-UNBOUNDED`
(fire-281, PR #2147) to use the paginated 3-arg form. The `getShell()` method was not
updated in that same PR.

**Fix:** Add `static final int SHELL_MAX_SUBMODELS = 500` constant; check
`dataObjects.size() > SHELL_MAX_SUBMODELS` after the DAO call and return the first 500
with `X-Shepard-Truncated: true` and `X-Shepard-Truncated-At: 500` response headers.

**AC:**
- A Collection with 501 top-level DataObjects: `GET /v2/aas/shells/{aasId}` returns 200
  with exactly 500 submodel references + `X-Shepard-Truncated: true` header
- A Collection with 10 DataObjects: returns all 10, no truncation header
- `mvn verify -pl plugins/aas` green

**Filed as:** `APISIMP-AAS-GET-SHELL-UNCAPPED` in `aidocs/16`
