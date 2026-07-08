---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP sweep — fire-483 (2026-07-08)

Scope: full v2 REST surface + plugins; focus on remaining `new ProblemJson()`
inline constructions after waves 1–3 (#2413/#2414/#2415), private helper
residuals, and undocumented query parameters.

## Context — fire-482/483 close-out

| Row | Status after fire-483 |
|---|---|
| APISIMP-MCP-SUMMARY-NUMERIC-ID | ✅ merged (fire-482, PR #2414, SHA 6ca1c76) |
| APISIMP-MCP-TS-CHANNEL-KEY | ✅ merged (fire-482, PR #2414) |
| APISIMP-SHAPES-DEDUP-MISSED | ✅ merged (fire-482, PR #2414) |
| APISIMP-PROBLEM-HELPER-BYPASS | ✅ merged (fire-482, PR #2414) |
| APISIMP-PREFER-PARAM-UNDOC | ✅ merged (fire-482, PR #2414) |
| APISIMP-PROBLEM-HELPER-BYPASS-3 | ✅ merged (fire-483, PR #2415, SHA b38a81b) |

## Findings

### F1 — APISIMP-PROBLEM-HELPER-BYPASS-4 (MAJOR / M)

31 remaining `new ProblemJson(` inline constructions in 22 REST files across
v2 + plugins, not covered by waves 1–3.

Key clusters:
- `TemplatePortabilityRest.java:132,192,215,227` — 4 sites (has `import static
  ProblemResponse.problem` but still constructs inline for 500/400 responses)
- `TemplateInstantiationRest.java:262` — 1 site
- `StructuredDataContainerStatsRest.java:44` — 1 site
- `FileContainerStatsRest.java:44` — 1 site
- `FileBundleReferenceRest.java:176` — 1 site
- `MirroredUserRest.java:137` — 1 site
- `AdminUserOrcidRest.java:82` — 1 site
- `AdminFeaturesRest.java:46` — 1 site
- `FileContainerKindHandler.java:183` — 1 site (handler, not a REST resource)
- `SemanticAnnotationV2Rest.java:425` — 1 site (violation body)
- `DataObjectV2Rest.java:242` — 1 site
- `MappingsMaterializeRest.java:267,273` — 2 sites (inside delegating private helper)
- `ProvenanceRest.java:636` — 1 site (inside delegating private helper)
- Plugin: `WikiWriterTombstoneRest.java:51` — 1 site
- Plugin: `GitReferenceRest.java:82` — 1 site
- Plugin: `GitReferenceActionsRest.java:113,171` — 2 sites
- Plugin: `EpicAdminRest.java:74,97` — 2 sites
- Plugin: `HdfAdminRest.java:244` (inside delegating private helper)
- Plugin: `VideoTranscodeBackfillRest.java:89` — 1 site
- Plugin: `DataciteAdminRest.java:74,97` — 2 sites
- Plugin: `AasShellsRest.java:81` — 1 site
- Plugin: `AasAdminRest.java:70,77` — 2 sites

Fix: for each site, replace `new ProblemJson(type, title, statusCode, detail, null)` 
with `ProblemResponse.problem(type, title, Status.fromStatusCode(statusCode), detail)` 
(or the matching factory overload). For `MappingsMaterializeRest` and `ProvenanceRest`
the inline construction is inside a private helper body — delete the helper and
use the static import. `FileContainerKindHandler` is a handler, not a REST class —
apply the same factory call but note it is in the handlers package.

AC: `grep -rn "new ProblemJson(" backend/src/main/java/de/dlr/shepard/v2/ plugins/`
returns zero lines outside `ProblemResponse.java`; `mvn verify -pl backend` green.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/template/resources/TemplatePortabilityRest.java:132`;
`backend/src/main/java/de/dlr/shepard/v2/mappings/resources/MappingsMaterializeRest.java:265`;
`plugins/git/src/main/java/de/dlr/shepard/v2/git/resources/GitReferenceRest.java:82`;
`plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasAdminRest.java:70`.

---

### F2 — APISIMP-PROBLEM-HELPER-RESIDUAL (MINOR / S)

9 v2/plugin REST files still carry a private `problem()` helper that now
delegates to `ProblemResponse.problem()` but has not been deleted. After PR
#2415 converted these bodies, the helpers are 1-line wrappers adding no value.
Each can be deleted and call sites replaced with a static import.

Files:
- `AdminConfigRest.java:183` — `return ProblemResponse.problem(type, title, status, detail);`
- `SemanticAdminRest.java:598` — same delegating body
- `MffdProcessChainMappingRest.java:167` — same delegating body
- `PluginsAdminRest.java:236` — same delegating body
- `NotificationTransportRest.java:255` — same delegating body
- `MappingsMaterializeRest.java:265,271` — non-standard signatures (StatusType + int); these callers will need the matching factory overload added to `ProblemResponse`
- `UnhideFeedRest.java:200` — same delegating body
- `HdfAdminRest.java:243` — same delegating body

Fix: delete the 4-arg delegating helpers in the 7 standard-signature files; add
`import static de.dlr.shepard.v2.common.ProblemResponse.problem;`. For
`MappingsMaterializeRest`, add `problem(StatusType, String, String)` and
`problem(int, String, String)` overloads to `ProblemResponse` then delete the
two local helpers.

AC: private-helper grep returns zero results; `mvn verify -pl backend` green.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/admin/config/resources/AdminConfigRest.java:183`;
`backend/src/main/java/de/dlr/shepard/v2/mappings/resources/MappingsMaterializeRest.java:265`.

---

### F3 — APISIMP-LABJOURNALHISTORY-UNDOC-PARAMS (MINOR / XS)

`LabJournalHistoryRest.java:118–119` — `@QueryParam("page")` and
`@QueryParam("pageSize")` have no `@Parameter` annotation. The sibling
`CollectionLabJournalEntriesRest.java:114–117` already documents these params
and is the template.

Fix: add two `@Parameter(description = "...")` annotations before the two
`@QueryParam` lines, matching `CollectionLabJournalEntriesRest`. Two-liner.

AC: `@QueryParam("page")` and `@QueryParam("pageSize")` in `LabJournalHistoryRest`
each have a preceding `@Parameter`; `mvn verify -pl backend` green.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/labjournal/resources/LabJournalHistoryRest.java:118`.

---

### F4 — APISIMP-SCHEMA-MISSING-IO (MINOR / M)

37 of ~175 IO response classes in `de.dlr.shepard.v2.*.io` lack a class-level
`@Schema(description = "...")` annotation, making generated OpenAPI output
incomplete for those types. Example files: `DQRIO.java`, `AutosweepConfigIO.java`,
`InstanceRegistryIO.java`, and 34 others across quality, admin, and plugin packages.

Fix: add `@Schema(description = "<...>")` at the class level to each of the 37 IO
classes; descriptions can be derived from the class name and existing endpoint
documentation. Batch as XS PRs per domain package (quality, admin/instance,
admin/storage, etc.).

AC: `find . -path '*/v2/*/io/*.java' | xargs grep -L '@Schema'` returns empty;
`mvn verify -pl backend` green.

**First refs:** `backend/src/main/java/de/dlr/shepard/v2/quality/io/DQRIO.java`.

---

## Summary

| Row | Severity | Size | Next-fire target? |
|-----|----------|------|-------------------|
| APISIMP-PROBLEM-HELPER-BYPASS-4 | MAJOR | M | yes (largest payoff) |
| APISIMP-PROBLEM-HELPER-RESIDUAL | MINOR | S | yes (quick follow-on) |
| APISIMP-LABJOURNALHISTORY-UNDOC-PARAMS | MINOR | XS | yes (smallest/fastest) |
| APISIMP-SCHEMA-MISSING-IO | MINOR | M | batched later |

Smallest dispatchable next fire: **APISIMP-LABJOURNALHISTORY-UNDOC-PARAMS** (XS, 2 lines).
