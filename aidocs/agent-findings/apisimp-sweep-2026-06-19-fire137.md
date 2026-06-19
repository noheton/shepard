---
stage: deployed
last-stage-change: 2026-06-19
---

# APISIMP Sweep — 2026-06-19 (fire-137)

Scope: scan the live `/v2` REST surface for residual API-simplification sprawl not covered by prior fires.

Previous sweeps covered: `backend/src/main/java/de/dlr/shepard/v2/**` (fires 113–135 documented all bare `@QueryParam` across the main v2 REST resources via PRs #1990–#2014). Plugin REST files were partially swept (fire-135, PR #2021 pending). This fire focuses on finding **remaining gaps not yet filed**.

---

## Findings

### §F1 — `APISIMP-PROVENANCE-PROV-VARIANT-PARAMS` (S)

**Files:** `backend/src/main/java/de/dlr/shepard/v2/provenance/resources/ProvenanceRest.java`

**Pattern:** The primary JSON content-negotiation method `listActivities()` (lines 99–141) has proper `@Parameter(description=…)` annotations on all 6 query params (`agent`, `targetKind`, `targetAppId`, `since`, `until`, `pageSize`). However, its content-negotiation siblings — the ProvJson, JSON-LD, and `countActivitiesJsonLd` variants — are missing the same annotations:

| Method | Line | Missing @Parameter params |
|---|---|---|
| `listActivitiesProvJson()` | 156–178 | agent, targetKind, targetAppId, since, until, pageSize (6) |
| `listActivitiesJsonLd()` | 195–224 | agent, targetKind, targetAppId, since, until, pageSize (6) |
| `listEntityActivitiesProvJson()` | 275–292 | since, until, pageSize (3) |
| `listEntityActivitiesJsonLd()` | 305–328 | since, until, pageSize (3) |
| `countActivitiesJsonLd()` | 376–400 | agent, targetKind, targetAppId, since, until (5) |

Total: 23 bare `@QueryParam` params across 5 methods. A caller inspecting the OpenAPI schema for `application/provenance+json` or `application/ld+json` response variants sees no param descriptions, while the JSON variant is fully documented.

**Fix:** Mirror the `@Parameter` annotations from the primary JSON method to each variant. The descriptions are identical for the same param across variants — copy-paste is safe. Add 5 reflection regression tests (one per method) asserting `@Parameter.description` is non-blank on one representative param per method.

**AC:** OpenAPI spec shows identical param docs across all ProvenanceRest content-negotiation variants; `mvn verify -pl backend` green.

**Size:** S | **First-references:** `ProvenanceRest.java:156,195,275,305,376`

---

### §F2 — `APISIMP-REFERENCES-CREATE-LIST-PARAMS` (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java`

Two methods carry bare `@QueryParam` params validated as required at runtime but invisible in the OpenAPI schema:

**`POST /v2/references` — `create()` at lines 123–128:**
- Line 124: `@QueryParam("kind") String kind` — required; returns 400 if absent or blank (line 132); accepted values are installed reference kind names (`file`, `timeseries`, `uri`, `git`, plugin-defined).
- Line 125: `@QueryParam("dataObjectAppId") String dataObjectAppId` — required; returns 400 if absent (line 135); must be a UUID v7 appId of an existing DataObject.

**`GET /v2/references` — `list()` at lines 400–404:**
- Line 401: `@QueryParam("kind") String kind` — required (400 if absent, line 408)
- Line 402: `@QueryParam("dataObjectAppId") String dataObjectAppId` — required (400 if absent, line 411)
- Line 403: `@QueryParam("fileKind") String fileKind` — optional; only meaningful when `kind=file`; filters by file-format subtype (e.g. `urdf`, `krl`, `otvis`)

**Fix:** Add `@Parameter(required=true, description=…)` to `kind` and `dataObjectAppId` on both methods. Add `@Parameter(description="Optional file-format subtype filter. Only effective when kind=file. Values: urdf, krl, otvis, svdx, xit, or any plugin-registered fileKind.")` to `fileKind`. Add 3 reflection regression tests.

**AC:** OpenAPI schema for `createReference` and `listReferences` shows all 5 params documented; required params marked required; `mvn verify -pl backend` green.

**Size:** XS | **First-references:** `ReferencesV2Rest.java:124-125,401-403`

---

### §F3 — `APISIMP-REFERENCES-UPLOAD-CONTENT-FILENAME` (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/references/resources/ReferencesV2Rest.java`

**`PUT /v2/references/{appId}/content` — `uploadContent()` at lines 284–296:**
- Line 286: `@QueryParam("filename") String filename` — required; returns 400 if absent or blank (line 293–294); used by the backend to detect MIME type and set the `fileKind` discriminator on the FileReference. The `@Operation` description mentions content upload; the param itself carries no description.

**Fix:** Add `@Parameter(required=true, description="Original filename (required). Used to detect MIME type and set the fileKind discriminator (e.g., 'robot.urdf' sets fileKind=urdf). Returns 400 when absent or blank.")` to the `filename` param; add 1 reflection regression test.

**AC:** OpenAPI schema for `uploadReferenceContent` shows `filename` with description and required=true; `mvn verify -pl backend` green.

**Size:** XS | **First-references:** `ReferencesV2Rest.java:286`

---

## Dispatched next fire

Smallest new row: `APISIMP-REFERENCES-UPLOAD-CONTENT-FILENAME` (XS) — single param on one method + 1 test. Can be combined with `APISIMP-REFERENCES-CREATE-LIST-PARAMS` (XS, same file) for efficiency — one PR touching `ReferencesV2Rest.java`.
