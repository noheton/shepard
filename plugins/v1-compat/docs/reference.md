# shepard-plugin-v1-compat — reference

Phase 1 marker plugin per `aidocs/platform/103a`. Ships the
control plane for the upstream-frozen `/shepard/api/...` surface;
moves no v1 code.

## Endpoints

### `GET /v2/admin/legacy/v1/config`

Read the current state of the `:LegacyV1Config` singleton.

**Auth:** `@RolesAllowed("instance-admin")` — 403 to non-admins.

**Request:**

```http
GET /v2/admin/legacy/v1/config HTTP/1.1
X-API-KEY: <admin-key>
```

**Response:**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "enabled": true,
  "appId": "01HF-AAA",
  "updatedAt": "2026-05-22T15:50:00.000+00:00",
  "updatedBy": "admin@example"
}
```

`updatedAt` / `updatedBy` are `null` until the first PATCH (the
seed itself does not stamp them).

### `PATCH /v2/admin/legacy/v1/config`

RFC 7396 merge-patch the singleton.

**Auth:** `@RolesAllowed("instance-admin")`.

**Request:**

```http
PATCH /v2/admin/legacy/v1/config HTTP/1.1
X-API-KEY: <admin-key>
Content-Type: application/merge-patch+json

{"enabled": false}
```

Phase 1 patchable fields:

- `enabled` (boolean) — master toggle. `true` keeps the v1 surface
  on (default); `false` makes every `/shepard/api/...` return 410.

Absent fields are no-ops (RFC 7396 "absent = leave alone").

**Response:** 200 with the post-patch IO. Cache invalidates
immediately; the next `/shepard/api/...` request observes the new
value.

### `GET /v2/admin/legacy/v1/stats`

Read the in-memory hit counters.

**Auth:** `@RolesAllowed("instance-admin")`.

**Query params:**

- `topN` (int, optional) — cap on the per-endpoint and per-principal
  breakdown lists. Default 50, max 1000.

**Response:**

```json
{
  "totalHits": 1247,
  "byEndpoint": [
    {"pathPattern": "/shepard/api/collections", "hits": 800}
  ],
  "byPrincipal": [
    {"principalSub": "alice@example", "hits": 1200}
  ],
  "firstHitAt": "2026-05-22T08:14:23.000+00:00",
  "mostRecentHitAt": "2026-05-22T15:51:08.000+00:00"
}
```

Counters reset on process restart. Path patterns are aggregated by
endpoint family (the second path segment after `/shepard/api/`).

## Behaviour: when `:LegacyV1Config.enabled=false`

Every `/shepard/api/...` request returns:

```http
HTTP/1.1 410 Gone
Content-Type: application/problem+json
Deprecation: true
Link: </v2/>; rel="successor-version"
X-Shepard-Legacy: true

{
  "type": "https://shepard.dlr.de/problems/v1-disabled",
  "title": "Legacy v1 surface disabled",
  "status": 410,
  "detail": "The legacy /shepard/api/... surface is disabled on this instance. Migrate to /v2/.",
  "instance": "<original-path>"
}
```

The `LegacyV1GateFilter` runs at `Priorities.AUTHENTICATION - 100`,
so it executes **before** `JWTFilter`. An anonymous caller hitting
a disabled v1 surface sees the 410 (the right answer:
"administratively removed"), NOT a 401 (which would imply "send
credentials"). The 410-before-auth ordering also keeps the
deprecated surface cheap on the auth path.

## Behaviour: when `:LegacyV1Config.enabled=true` (default)

Every `/shepard/api/...` response gains three additive headers:

```
Deprecation: true
Link: </v2/>; rel="successor-version"
X-Shepard-Legacy: true
```

- The first two are RFC 8594-standard deprecation signals — any
  deprecation-aware client picks them up automatically.
- The third is a fork-specific marker the shepard frontend banner
  watches without re-parsing the standard headers.

All three are additive; no existing client wire shape changes.

## Logging

The `LegacyV1DeprecationFilter` emits at most one WARN line per
`(path-pattern × principal-sub)` pair per process lifetime:

```
WARN  V1COMPAT.0: first v1 hit this process by principal 'alice@example' on path-pattern '/shepard/api/collections'. The /shepard/api/... surface is deprecated; migrate to /v2/. See /v2/admin/legacy/v1/stats for the full breakdown.
```

This is the load-bearing dedup: the home-showcase MQTT collector
hits `/shepard/api/timeseriesContainers/.../payload` continuously;
without the dedup, the log would flood.

Write methods (POST / PUT / PATCH / DELETE) on `/shepard/api/...`
additionally emit an INFO line per request:

```
INFO  V1COMPAT.0 WRITE: principal='alice@example' method=POST path='shepard/api/collections/42/dataObjects' pattern='/shepard/api/collections'
```

The durable `:Activity` audit row is still written by PROV1a's
`ProvenanceCaptureFilter` — this filter only adds the human-readable
INFO marker for easy grep.

## Neo4j entity

```
(:LegacyV1Config {
  appId: STRING,         // UUID v7
  enabled: BOOLEAN,      // default true
  createdAt: INTEGER,    // millis since epoch
  updatedAt: INTEGER,    // millis; null until first PATCH
  updatedBy: STRING      // sub claim of the patching admin
})
```

Constraint: `LegacyV1Config_appId_unique` — appId uniqueness.

The singleton invariant ("exactly one `:LegacyV1Config` node ever
exists") is held by:

1. The V63 Cypher migration's idempotent `MERGE`.
2. The JVM-layer `LegacyV1ConfigService.seedIfNeeded()`
   defence-in-depth path.
3. (Implicitly) operators not running `CREATE (c:LegacyV1Config)`
   manually.

The DAO's `findSingleton()` picks the deterministic min-id row when
duplicates accidentally exist.

## Service: `LegacyV1ConfigService`

The hot-path read (`isEnabled()`) goes through a 5-second in-process
read-through cache so the gate filter never blocks on Neo4j. The
cache:

- Invalidates immediately on a successful `setEnabled()` PATCH.
- Fails open on DAO exception — returns the deploy-time install
  default (`true` by default) rather than 410-storming legitimate
  callers during a Neo4j hiccup.
- Refreshes after the 5-second TTL on any read.

## Out of scope for Phase 1

- Movement of any v1 REST resource Java code (Phase 2).
- Per-endpoint disable (`{disabledRootPaths: [...]}`) — YAGNI per
  the design's clarification 2.
- Deprecation log-level enum — set via standard logging config, not
  the singleton.
- Durable per-hour / per-day / per-week counters — restart-resets
  is the documented behaviour; Phase 2 may add durable shape.

## See also

- `plugins/v1-compat/docs/install.md` — install + config
- `plugins/v1-compat/docs/quickstart.md` — operator tasks
- `aidocs/platform/103a-v1-compat-marker-plugin.md` — design
- `aidocs/platform/103-v1-compat-plugin-extraction.md` — Phase 2
- `docs/reference/v1-deprecation.md` — end-user-facing context
