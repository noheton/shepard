---
stage: deployed
last-stage-change: 2026-05-26
task: "101 — MCP fix (task #80) end-to-end validation"
---

# MCP End-to-End Validation — 2026-05-26

**Task:** #101 — validate that the MCP endpoint at `https://shepard.nuclide.systems/v2/mcp`
works end-to-end after the task #80 fix (McpToolSupport, type-checking, clean errors).

**Protocol used:** MCP Streamable HTTP (`/v2/mcp` POST with `mcp-session-id` header).
SSE transport (`/v2/mcp/sse`) also confirmed live (returns `event: endpoint` immediately).

**Auth tested:** Keycloak OIDC Bearer JWT (`admin` user + `flo` user for container-owned resources).

---

## Tool registry — all 8 tools present

`tools/list` returns all expected tools:

| Tool | Registered | Status |
|---|---|---|
| `list_collections` | yes | PASS |
| `list_data_objects` | yes | PASS |
| `get_data_object` | yes | PASS |
| `list_channels` | yes | PASS |
| `get_channel_data` | yes | PASS |
| `list_files` | yes | PASS |
| `list_structured_data` | yes | PASS |
| `list_annotations` | yes | PASS |

Server identity: `{"name":"backend","version":"6.0.0-SNAPSHOT"}`, protocol `2024-11-05`.

---

## Tool call results

### `list_collections` — PASS

Returns 4 collections visible to the `admin` user.
Key row confirmed:
```json
{"appId":"019e30b0-99a2-79e7-b7d8-c15396095b42","id":42,
 "name":"LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)","status":null}
```

### `list_data_objects` — PASS

Called on LUMEN collection (`019e30b0-99a2-79e7-b7d8-c15396095b42`).
Returns 17 DataObjects including TR-001 through TR-015, plus the
`Anomaly Investigation — TR-004 Fuel Turbopump` child DataObject and a `Publications` node.

Count fields (`timeseriesCount`, `fileCount`, `structuredDataCount`) are populated correctly
via the `findRefCountsByAppIds` Cypher query.

### `get_data_object` (TR-004) — PASS WITH KNOWN CAVEAT

Called on TR-004 (`019e30b0-9e96-7ef6-ad1e-6221d6220244`). Returns:
- `attributes`: propellant, bench, anomaly notes, duration correctly populated
- `predecessorSummaries`: `[TR-003]` — correct
- `successorSummaries`: `[TR-005, Anomaly Investigation — TR-004 Fuel Turbopump]` — correct
- `childSummaries`: `[Anomaly Investigation — TR-004 Fuel Turbopump]` — correct
- `containers.files`: 1 container (correct)
- `containers.structuredData`: 1 container (correct)
- `containers.timeseries`: **0 items** — see caveat below

**Caveat — LUMEN timeseries container not visible via `get_data_object`:**
`list_data_objects` reports `timeseriesCount: 1` for TR-001 through TR-015 (excluding TR-005
and TR-012). However `get_data_object` returns `containers.timeseries: []` for all of them.

Root cause: the LUMEN `lumen-inspired-sensors` `TimeseriesContainer` was seeded before the
`is_in_container` relationship was introduced. The `DataObjectDetailV2IO` builds its
`containers.timeseries[]` list by iterating `TimeseriesReference.getTimeseriesContainer()` —
which is `null` when no `is_in_container` relationship exists on the reference node.

The count in `list_data_objects` uses a separate Cypher query (`findRefCountsByAppIds`) that
counts `TimeseriesReference` nodes via `has_reference` — those nodes DO exist, so the count is
non-zero. The divergence is a data-model mismatch in legacy seeded data, not an MCP code bug.

**Impact:** An agent navigating LUMEN TR-004 data via MCP cannot discover the timeseries
container appId through `get_data_object`. It would need to use the v1 API legacy numeric
container ID path to retrieve LUMEN timeseries data. MFFD and AFP-TCP data (seeded with the
newer container binding) works correctly.

**Ticket to create:** `TS-CORE-SCHEMA-02` — add `is_in_container` backfill migration for
legacy LUMEN `TimeseriesReference` nodes.

### `list_channels` — PASS (on properly-bound container)

Called on `AFP-TCP-thermal-trail` container (`019e62bb-2a88-70ba-ab0b-609d0eb541cf`) as
user `flo` (container owner). Returns 4 channels:
```
{measurement: "kinematics", device: "afp-robot", location: "mould-1", symbolicName: "tcp_x", field: "metres"}
{measurement: "kinematics", device: "afp-robot", location: "mould-1", symbolicName: "tcp_y", field: "metres"}
{measurement: "kinematics", device: "afp-robot", location: "mould-1", symbolicName: "tcp_z", field: "metres"}
{measurement: "thermal", device: "pyrometer-head", location: "nip-point", symbolicName: "head_temp", field: "celsius"}
```

Permission note: MFFD and AFP-TCP containers are owned by user `flo`, not `admin`. An agent
authenticating as `admin` gets `-32603 / InvalidAuthException` on these containers. This is
correct permissions behavior — the error code should ideally be `-32002` (FORBIDDEN) rather
than `-32603` (INTERNAL_ERROR), since `InvalidAuthException` is a caller-fixable condition.
See open issue below.

### `get_channel_data` — PASS

Called on AFP `thermal` channel, `maxPoints: 100`. Response:
```json
{
  "raw_count": 1000,
  "returned_count": 100,
  "downsampled": true,
  "algorithm": "LTTB",
  "points": [{"timestamp": 1779769684770832896, "value": 20.0}, ...]
}
```
LTTB downsampling confirmed working. Timestamp is nanoseconds since Unix epoch as documented.

### `list_files` — PASS

Called on TR-004 file container (`019e30b0-a8c8-7b68-ae91-4b0e1e6d1e7d`). Returns 30 files
with `filename`, `oid`, `fileSize`, `md5`, `createdAt` fields populated.

### `list_structured_data` — PASS

Called on TR-004 SD container (`019e30b0-aabc-70c1-952b-d752ddc5e60c`). Returns 16 records.

### `list_annotations` — PASS

Called on TR-004 DataObject (`019e30b0-9e96-7ef6-ad1e-6221d6220244`). Returns 9 annotations:
```
Test Outcome = Anomaly Detected
Campaign Role = Anomaly Run
Anomaly Type = Vibration Anomaly
...
```

---

## Error-handling validation (the core task-#80 fix)

### Previously-broken pattern: referenceId passed as dataObjectId

Called `get_data_object` with a `TimeseriesReference` appId (`019e30c4-9fa3-7bbe-8c21-c26710780d15`)
as the `dataObjectAppId`. Response:

```json
{
  "code": -32602,
  "message": "Wrong type for dataObjectAppId=019e30c4-9fa3-7bbe-8c21-c26710780d15: expected a DataObject but found BasicReference,TimeseriesReference,VersionableEntity,BasicEntity. Get a DataObject appId from `list_data_objects` or any *Summaries entry on `get_data_object`."
}
```

**PASS.** Code is `-32602` (Invalid Params — caller-fixable). Message names the wrong type
found and tells the agent exactly where to get the correct ID. This is the exact fix task #80 required.

### Wrong container type: FileContainer passed to list_channels

```json
{
  "code": -32602,
  "message": "Wrong type for containerAppId=019e30b0-a8c8-7b68-ae91-4b0e1e6d1e7d: expected a TimeseriesContainer but found FileContainer,BasicEntity,BasicContainer. Get a TimeseriesContainer appId from `get_data_object → containers.timeseries[].containerAppId`."
}
```

**PASS.** Correct code + actionable message.

### Missing required argument: list_data_objects with no collectionAppId

Returns MCP result with `isError: true`:
```json
{"isError": true, "content": [{"text": "Missing required argument: collectionAppId", "type": "text"}]}
```

**PASS.** (MCP `isError` pattern is the correct way to surface tool execution errors.)

### No auth header

Returns HTTP 401 with JSON body:
```json
{"status":401,"type":"AuthenticationException","message":"Missing Authorization or X-API-KEY header"}
```

**PASS.** 401 before the MCP frame — clean.

### Missing 5-tuple fields in get_channel_data

Returns `-32602` with message:
`"All five channel-identity fields are required (measurement, device, location, symbolicName, field). Get them from a list_channels row."`

**PASS.**

---

## Open issues found during validation

### ISSUE-1 (MINOR) — InvalidAuthException wraps as -32603 instead of -32002

When a container exists but the calling user has no READ permission,
`McpToolSupport.run()` catches `RuntimeException` (which `InvalidAuthException` is) and
maps it to `-32603 INTERNAL_ERROR` rather than `-32002 FORBIDDEN`. This misleads an agent
into thinking there's a server bug rather than a permission issue.

Fix: add `InvalidAuthException` to the explicit catch list in `McpToolSupport.run()` and
map it to the existing `FORBIDDEN` code (`-32002`).

File: `backend/src/main/java/de/dlr/shepard/v2/mcp/McpToolSupport.java`, line ~119 (the
`catch (RuntimeException e)` block).

### ISSUE-2 (KNOWN, PRE-EXISTING) — LUMEN timeseries containers not discoverable via get_data_object

Described in detail in the `get_data_object` section above.
Root cause: missing `is_in_container` Neo4j relationship on LUMEN-era `TimeseriesReference` nodes.
Needs a Cypher backfill migration in `backend/src/main/resources/neo4j/migrations/`.
Tracked as `TS-CORE-SCHEMA-02`.

---

## Overall verdict

| Gate | Result |
|---|---|
| SSE endpoint live | PASS |
| Streamable HTTP transport live | PASS |
| `initialize` handshake | PASS |
| All 8 tools registered | PASS |
| `list_collections` functional | PASS |
| `list_data_objects` functional | PASS |
| `get_data_object` functional | PASS (timeseries containers empty for legacy LUMEN data) |
| `list_channels` functional | PASS (requires user with container READ permission) |
| `get_channel_data` + LTTB | PASS |
| `list_files` functional | PASS |
| `list_structured_data` functional | PASS |
| `list_annotations` functional | PASS |
| Wrong-type error message (task #80 fix) | PASS — -32602 with actionable hint |
| Auth failure response | PASS — 401 JSON body |
| Missing arg response | PASS — isError:true |
| `InvalidAuthException` error code | MINOR ISSUE — -32603 instead of -32002 |

**Summary: task #80 fix is verified. All 8 tools are live. Two follow-up items: one minor
(InvalidAuthException error code mapping) and one pre-existing data migration needed for LUMEN
timeseries container discoverability.**
