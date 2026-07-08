---
stage: deployed
last-stage-change: 2026-07-08
---

# APISIMP sweep — fire-481 (2026-07-08)

Scope: full v2 REST surface + plugin MCP tools; focus on residual numeric-id
leaks in MCP responses, inline `ProblemJson` constructions that survived the
PROBLEM-DEDUP migration, and undocumented query parameters.

Five findings filed below. Smallest dispatchable is XS (2 items); targeting
`APISIMP-MCP-SUMMARY-NUMERIC-ID` (CRITICAL/XS) next fire.

## Findings

### F1 — APISIMP-MCP-SUMMARY-NUMERIC-ID (CRITICAL / XS)

`CollectionMcpTools.java:452,462` — both `collectionSummary()` and
`dataObjectSummary()` helper methods call `m.put("id", c.getShepardId())`
and `m.put("id", d.getShepardId())` respectively, emitting the raw Neo4j
numeric `Long` alongside the correct `"appId"` UUID. These helpers back
four tools: `create_collection` (:263), `update_collection` (:302),
`create_data_object` (:361), `update_data_object` (:420). Any MCP client
that reads `"id"` from these responses gets a numeric internal ID that
fails on every `/v2/` endpoint that requires a UUID `appId`.

Fix: remove `m.put("id", c.getShepardId())` (:452) and
`m.put("id", d.getShepardId())` (:462); `"appId"` is the stable identifier.

AC: `create_collection`, `update_collection`, `create_data_object`,
`update_data_object` tool responses contain no `"id"` key — only `"appId"`.

### F2 — APISIMP-MCP-TS-CHANNEL-KEY (MINOR / XS)

`TimeseriesMcpTools.java:280` — `list_timeseries_channels` puts each
channel's UUID under key `"shepardId"` instead of `"appId"`:
`ch.put("shepardId", row.getShepardId() == null ? null : row.getShepardId().toString())`.
The value IS a UUID (not a numeric ID), but the key name diverges from
every other MCP tool response and from the CLAUDE.md rule that all entities
expose their stable identifier as `"appId"`. An MCP client that follows
the `list_timeseries_channels` → `get_channel_data` flow fails to find
the `appId` it needs.

Fix: change `ch.put("shepardId", ...)` to `ch.put("appId", ...)` at line 280.

AC: each row in `list_timeseries_channels` uses key `"appId"`, not `"shepardId"`.

### F3 — APISIMP-SHAPES-DEDUP-MISSED (MAJOR / S)

`ShapesRenderRest.java` and `ShapesBuildRest.java` were not covered by
PROBLEM-DEDUP (PR #2413). `ShapesRenderRest` has a narrow
`badRequest(String)` helper for HTTP 400 only, plus 10 inline
`new ProblemJson(...)` constructions at lines 209, 219, 285, 295, 452,
471, 536, 555, 572, 655 for 404, 422, and 500 responses.
`ShapesBuildRest` has no helper at all — 2 inline 400s at lines 108, 126.
12 total bypass sites across the two files.

Fix: add `import static de.dlr.shepard.v2.common.ProblemResponse.problem;`
to both files; replace all 12 inline `new ProblemJson(...)` + `.type(...)` +
`.build()` chains with `ProblemResponse.problem(...)` calls using the
appropriate overload.

AC: zero `new ProblemJson(` expressions in either file; `mvn verify -pl backend` green.

### F4 — APISIMP-PROBLEM-HELPER-BYPASS (MAJOR / S)

Three v2/plugin REST files define their own `problem()` helper but bypass
it for domain errors that need a custom type URI, constructing
`new ProblemJson(...)` inline:
- `CollectionPublicationStateRest.java:104–110, 146–152, 167–173` (3 sites)
- `plugins/spatiotemporal/.../SpatialPromoteRest.java:101–109, 131–141` (2 sites)
- `plugins/wiki-writer/.../WikiWriterRest.java:114–121, 131–141` (2 sites)

7 bypass sites total. The inline `ProblemJson` constructions are correct
but undermine the centralised error shape and risk divergence.

Fix: add an `overloaded problem(Response.Status status, String typeUri,
String detail)` overload to each class's helper (or use `ProblemResponse`
from the shared utility); migrate the 7 inline sites.

AC: no file that defines `problem()` also directly constructs `new ProblemJson`; `mvn verify -pl backend` green.

### F5 — APISIMP-PREFER-PARAM-UNDOC (MINOR / XS)

`ReferencesV2Rest.java:423` — `@QueryParam("prefer")` on
`GET /v2/references/{appId}/content` has no `@Parameter` annotation.
The `prefer=source` hint (which overrides the default browser-friendly
proxy for video references per `ReferenceKindHandler.java:226–229`) is
invisible in the generated OpenAPI spec.

Fix: add `@Parameter(name = "prefer", description = "Download preference.
'source' returns original bytes; 'proxy' (default) returns a browser-friendly
transcode. Only honoured by the video plugin; other kinds ignore it.")`
immediately before `@QueryParam("prefer")` at line 423.

AC: `prefer` appears as a documented optional string query parameter in
the OpenAPI spec for `GET /v2/references/{appId}/content`;
`mvn verify -pl backend` green.
