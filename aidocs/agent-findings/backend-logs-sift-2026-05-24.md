# Backend logs sift — 2026-05-24 ~06:00 CEST

Window: `docker logs --since 6h infrastructure-backend-1`
Backend log span: **23:59:08.299 → 05:59:07.099** (5h 60m)

## Summary

- Total backend log lines (6h): **35 191**
- Known-noise subtracted (TS-732 / TS-729 payload InvalidPathException + Coll-515365 InvalidPathException + sporadic JWT-signature-mismatch + Caddy zoraxy broken-pipe): **~33 100**
- Distinct *new* error patterns: **4**
- **CRITICAL: 0 / MAJOR: 1 / MINOR: 3**

The ingest is broadly healthy. There is exactly **one HTTP 500** in the 6-hour window, and the cluster is showing no resource pressure.

## Resource snapshot (single `docker stats --no-stream`, captured at sift start)

| container | mem% | cpu% | note |
| --- | --- | --- | --- |
| infrastructure-backend-1 | 8.63% (2.76 GiB / 32) | 0.11% | comfortable; 121 PIDs |
| infrastructure-neo4j-1 | 7.70% (2.46 GiB / 32) | 0.29% | normal |
| infrastructure-timescaledb-1 | **18.40%** (5.89 GiB / 32) | 0.16% | highest but well under 80% gate |
| infrastructure-mongodb-1 | 0.47% | 0.32% | normal |
| infrastructure-keycloak-1 | 1.48% | 0.10% | normal |
| infrastructure-frontend-1 | 4.50% (1.44 GiB / 32) | 0.00% | normal |
| infrastructure-caddy-1 | 0.06% | 0.00% | normal |
| infrastructure-pgbouncer-1 | 0.00% (1.59 MiB) | 1.62% | normal |
| shepard-garage | 0.02% | 0.00% | normal |
| alloy | 0.31% (102 MiB) | 1.06% | normal |

No container is >80% memory or saturating CPU. **No JVM-level memory pressure on backend.**

---

## New error patterns (newest first)

### Pattern 1 — One-off `HTTP 500 — Error capturing CSV header!` on TS-661951 import after ~4h41m importer idle

- **Severity**: MAJOR (one failed import; importer's own 30s-cadence retry succeeded immediately afterward, so net effect on ingest is nil — but the failure mode is uncategorized and worth pinning).
- **First seen**: 2026-05-24 05:20:10.843
- **Last seen**: 2026-05-24 05:20:10.843
- **Count**: 1 (in 6h window)
- **Rate**: 1 occurrence over 5h60m
- **Endpoint**: `POST /shepard/api/timeseriesContainers/661951/import`
- **Sample log**:
  ```
  05:20:10.821 [INFO] [LoggingFilter] Received POST request on /shepard/api/timeseriesContainers/661951/import
                from ee4c010f-d648-4630-aea6-b81ef2a9c296
  05:20:10.843 [ERROR] [ShepardExceptionMapper] [d9d05daa-...] Unhandled RuntimeException
                on POST /shepard/api/timeseriesContainers/661951/import -> HTTP 500
  05:20:10.843 [INFO] [ShepardExceptionMapper] [d9d05daa-...] cause: Error capturing CSV header!
  ```
- **Correlation that matters**:
  - TS-661951 had been imported by the same client `ee4c010f-...` at a steady **30-second cadence** from 22:23 through 00:39:44 (160 successful imports in the prior window — log line numbers 4295–5168).
  - Then the importer **went silent for 4h41m** — no `/661951/import` POSTs at all between 00:39:44 and 05:20:10.
  - The **first** POST after the silence was the one that returned 500.
  - Immediately after at `05:20:40.021` the next `/661951/import` POST succeeded (no 500 on any subsequent attempt for the remainder of the window).
- **Hypothesis**: Most likely the importer's persistent HTTP connection went stale during the long idle, and the first re-attempt sent a body that the server-side CSV header parser couldn't read — possibly an empty / truncated multipart body, possibly a TCP-half-open issue surfacing at the parser. NOT a parser bug in steady state, since 160 prior + ~160 subsequent imports of the same container all succeeded.
- **Could not pin source**: the literal string "Error capturing CSV header!" doesn't appear in `backend/src/` nor in any jar under `/deployments/` (likely an InfluxDB-side error message surfacing through the influx-client driver wrapped in `RuntimeException`). Stack traces are not emitted at this log level — would need DEBUG logging or thread dump to attribute precisely.
- **Suggested next step**:
  - Backlog row (LOW): map the "Error capturing CSV header!" code path so the next occurrence has a routed exception type + a useful body fragment in the log. The current `Unhandled RuntimeException` swallows the structural cause.
  - No hotfix warranted: 1/160 = 0.6% failure rate after a 4h+ idle, and the importer's natural retry recovers.

### Pattern 2 — Importer JWT expired since 2026-05-23T10:36:58Z; healthcheck still using it

- **Severity**: MINOR (cosmetic; healthcheck-only path; doesn't block any ingest call).
- **First seen**: 2026-05-23 23:59:30.435
- **Last seen**: 2026-05-24 05:23:30.775
- **Count**: **45 occurrences** in 6h (exactly one per minute, with a 4h41m gap matching the importer-idle window in Pattern 1)
- **Rate**: ~7.5 / hour (when active)
- **Endpoint**: `GET /shepard/api/versionz` (no security context, then JWT validator fires anyway because the caller sends an Authorization header)
- **Sample log**:
  ```
  05:23:30.768 [INFO] [LoggingFilter] Received GET request without security context on
                /shepard/api/versionz with query params {}
  05:23:30.775 [WARN] [JwtTokenAuthService] Invalid token: JWT expired at 2026-05-23T10:36:58Z.
                Current time: 2026-05-24T05:23:30Z, a difference of 67592775 milliseconds.
  ```
- **Hypothesis**: The MFFD importer is sending its (now-expired) JWT on every health probe to `/versionz`. The JWT was issued for ~24h validity on 2026-05-22 and tipped over yesterday. `/versionz` is unauth and tolerates this (the call still returns 200), so it is purely log noise — but it *also* indicates the importer is presenting a known-expired token to every backend endpoint it touches, and only the JWTFilter is silently rejecting it. The fact that the actual ingest endpoints don't show a corresponding 401-spam suggests the importer is using the **X-API-KEY** header (which is the seeded long-lived key) for ingest while the JWT is being attached out of habit on health probes.
- **Cross-reference**: matches the operator notes — the MFFD api keys & JWTs were issued 2026-05-22 (per `MEMORY.md → project_mffd_api_keys.md`).
- **Suggested next step**:
  - Importer-side: drop the JWT from `/versionz` probes; either it's an X-API-KEY path or an unauth path.
  - Backend-side: `versionz` is correctly unauth; the JWTFilter is doing the right thing by warning. Lowering this to DEBUG would also be reasonable.
  - No backend code change required.

### Pattern 3 — Synthetic "DB staleness recovery" events on idle, hitting all three substrates simultaneously

- **Severity**: MINOR (auto-recovers in 1–6 ms; no user-visible impact). Worth flagging because it's the kind of thing that becomes alarming on a metrics dashboard without context.
- **Pattern**: Every ~55 min of low traffic, the `DbRecoveryScheduler` reports `ageMs` between **30019 and 30115** (just barely over the 30000ms threshold) for all three substrates **at the same timestamp**, and **all three** recover within 1–6 ms.
- **First seen**: 00:04:23
- **Last seen**: 05:35:59
- **Count**: 7 staleness cycles × 3 DBs = **21 ping-recover events**
- **Endpoints affected**: internal (no API impact)
- **Sample log**:
  ```
  03:48:28.000 [INFO] [DbRecoveryScheduler] (db-recovery-mongodb)     Recovery: re-pinging mongodb     (ageMs=30115, maxStalenessMs=30000)
  03:48:28.000 [INFO] [DbRecoveryScheduler] (db-recovery-neo4j)       Recovery: re-pinging neo4j       (ageMs=30115, maxStalenessMs=30000)
  03:48:28.000 [INFO] [DbRecoveryScheduler] (db-recovery-timescaledb) Recovery: re-pinging timescaledb (ageMs=30115, maxStalenessMs=30000)
  03:48:28.001 [INFO] [DbRecoveryScheduler] (db-recovery-mongodb)     Recovery: mongodb is back UP after staleness
  03:48:28.001 [INFO] [DbRecoveryScheduler] (db-recovery-timescaledb) Recovery: timescaledb is back UP after staleness
  03:48:28.002 [INFO] [DbRecoveryScheduler] (db-recovery-neo4j)       Recovery: neo4j is back UP after staleness
  ```
- **Hypothesis**: The `ageMs` is **the scheduler thread's own wall-clock since its last successful heartbeat**, not "the DB has been silent for 30s". The scheduler is being descheduled by the JVM (or by Linux CPU throttling, given the cgroup limits) past its 30s budget — and the moment it wakes, all three "stale" because all three share that wall-clock. Re-pinging confirms each DB is live in ~1ms. The recovery is a non-event, but the WARN-style framing in the code makes it look scarier than it is.
- **Cross-reference with log gaps**: the 9-minute log silence at 05:10:38 → 05:19:38 shows the JVM is genuinely idle for that long, not stalled. No GC stalls or thread starvation seen elsewhere.
- **Suggested next step**:
  - Maybe nothing — this is the recovery mechanism working as designed.
  - Optional polish: in `DbRecoveryScheduler`, downgrade the `re-pinging` line to DEBUG when ageMs is within +200ms of `maxStalenessMs` (i.e., "just barely over"), keep INFO when ageMs > 60000ms (i.e., the scheduler was *actually* starved). Current logging makes idle-cluster periods look like minor incidents.
  - Possibly bump `maxStalenessMs` from 30000 → 60000 for idle-tolerance, since the wakeup window is the bottleneck here, not real DB health.

### Pattern 4 — Frontend SSR `unhandledRejection: [nuxt] instance unavailable` in `fetchAndMergeReferences` / `fetchFileContainerMeta`

- **Severity**: MINOR (frontend-side; appears only in `infrastructure-frontend-1` logs; auth-related fallback path during SSR; no user-visible 500).
- **Container**: `infrastructure-frontend-1`
- **Count**: log spans 6h; the unhandledRejection block appears once explicitly but is masked by a huge volume of preceding `Refresh token is not valid or does not exist RefreshTokenError` lines (~178 occurrences) and `[nuxt] setInterval should not be used on the server` (~40 occurrences).
- **Endpoint**: `addContainerName` → `fetchAndMergeReferences` → `fetchFileContainerMeta` (`useShepardApi`)
- **Sample log**:
  ```
  [unhandledRejection] Error: [nuxt] instance unavailable
    at useNuxtApp (...server.mjs:274:13)
    at useState  (...server.mjs:789:19)
    at makeCommonAuthState (...server.mjs:966:16)
    at useAuthState (...server.mjs:991:28)
    at useAuth (...server.mjs:1168:7)
    at useShepardApi (...useShepardApi-lsZM2Eqg.mjs:385:20)
    at fetchFileContainerMeta (...index-BYT5gwuD.mjs:8334:12)
    at fetchAndMergeReferences (...index-BYT5gwuD.mjs:8391:57)
    at Array.map
    at addContainerName (...index-BYT5gwuD.mjs:8371:34)
  ```
- **Hypothesis**: `fetchAndMergeReferences` is invoked during SSR for a page that includes file-reference metadata. Inside an `Array.map` callback, the Nuxt async context is lost (a long-standing Nuxt SSR pitfall); `useAuth` then resolves `useNuxtApp()` against an undefined context and throws. The triggering visit is likely an SSR fetch of a collection detail page touching collection 661923 / 42 (matches the `[Vue Router warn]: No match found` warnings just above).
- **Side observation**: the same logs show `[nuxt] setInterval should not be used on the server` repeating heavily — that's a separate (and probably benign) frontend hygiene issue, but the volume suggests an interval is being started inside an SSR-rendered component without an `onMounted` guard.
- **Suggested next step**:
  - Backlog row: `fetchAndMergeReferences` must not call composables inside `.map(...)` on the server — either preserve the Nuxt app context with `runWithContext`, or pre-resolve the auth headers once outside the map.
  - Separate backlog row: hunt the offending `setInterval(...)` and wrap with `onMounted` / `process.client` guard.

---

## What I didn't see (negative findings)

- **No OutOfMemoryError, no stuck-thread warnings, no GC pauses logged.** (Even GC-event logging is silent — the JVM is comfortably under its heap budget at ~2.76 GiB / 32 GiB.)
- **No JDBC / Neo4j-driver / Mongo-driver connection-pool exhaustion warnings.** No `HikariPool` warnings, no `MongoSocketException`, no `Neo4jException` propagating to the backend log.
- **No Garage / S3 driver errors.** Zero `S3Exception`, zero `GarageException`, zero `FileStorage` warnings.
- **No slow-query warnings.** No `slow_query`, `Long-running`, or `executed in [Nms]` over-threshold messages.
- **No 403s on writes — BUG #148 (Permissions seed) is NOT visible in this window.** Searched for `403`, `Forbidden`, `denied`, `Permission`-related WARN/ERROR — zero hits. Either the bug isn't being triggered by current ingest patterns, or it's silent on the backend log side. Doesn't rule it out — but it isn't actively firing.
- **No 5xx anywhere besides the single CSV-header 500.**
- **No rate-limit / circuit-breaker / Hystrix fallback firings.**
- **No deadlock detector messages.**
- **No `setInterval`-style hot loops on the backend side.** (Frontend has the setInterval misuse, but backend is clean.)
- **No SPARQL query validator errors** (and `SparqlQueryValidator.java` is one of the files modified on the working tree — heads up that any local changes haven't yet shown up as runtime issues).

## What's suspicious but I couldn't pin

- **Pattern 1's underlying cause isn't in source.** The string "Error capturing CSV header!" is not present in `backend/src/` nor in any deployed jar under `/deployments/`. It's likely an InfluxDB driver-side message bubbling through `RuntimeException`. The currently-logged framing (`Unhandled RuntimeException` with the literal cause as a free-text fragment) doesn't let me confirm the call site without a thread dump or DEBUG re-run. Recommend instrumenting the InfluxDB CSV-import call site so the next occurrence carries enough context to attribute (request size, headers, content-type, first 200 bytes).
- **The 4h41m importer pause itself.** I see it in this backend window but I don't know what the importer side was doing during that gap — likely a deliberate operator pause or a remote-cube outage. If it's neither, that's a separate "importer dropped the ball" question that lives outside this log sift.
- **The frontend `Vue Router warn`: `No match found for location with path "/shepard/api/v2/collections"`.** That looks like an SSR-side fetch path being passed to the router instead of to `$fetch` — possibly the same composable layer as Pattern 4. Worth a separate frontend-side dig.

## Recommendations (top 3 actionable items)

1. **Backlog row — CSV header import path observability** (Pattern 1, MAJOR):
   replace `throw new RuntimeException(...)` in the InfluxDB CSV-import code path with a typed `CsvImportFailedException` that carries: container ID, content-length, content-type, and first 200 bytes of body. Today's log emits only `cause: Error capturing CSV header!` which is unattributable. Aim: when this next happens, the operator can decide in one log line whether it's a client bug (truncated body) or a backend bug (parser regression). Low-risk fix; one source file touched.

2. **Importer-side cleanup — stop sending expired JWT to `/versionz`** (Pattern 2, MINOR):
   communicate to the MFFD importer maintainer that the JWT expired 2026-05-23T10:36:58Z. Either rotate it, or drop the Authorization header from `/versionz` probes (it's an unauthenticated endpoint). Today this is generating 45 WARN/hr of pure noise that drowns out real issues during sifts like this one.

3. **`DbRecoveryScheduler` log-level tuning** (Pattern 3, MINOR):
   downgrade `re-pinging <db> (ageMs=30019, maxStalenessMs=30000)` to DEBUG when the overshoot is <2× threshold. The current INFO logging on idle wakeups makes a healthy cluster look like it's experiencing 21 incidents per 6h. Optional: bump `maxStalenessMs` from 30000 → 60000 to widen the idle-tolerance window. Three-line change in `DbRecoveryScheduler.java`.

(A frontend-side `[unhandledRejection]` Pattern 4 backlog row is the natural fourth item but lives in the frontend repo's `useShepardApi` composable, not in the backend sift's scope.)

---

## Sift methodology (for the next sifter)

```bash
# 1. Pull logs once
docker logs --since 6h infrastructure-backend-1 > /tmp/backend.log 2>&1
sed 's/\x1b\[[0-9;]*m//g' /tmp/backend.log > /tmp/backend.clean.log

# 2. Categorise by ERROR/WARN source class
grep '\[ERROR\]' /tmp/backend.clean.log | \
  sed -E 's/^[0-9:.]+ \[ERROR\] \[([^]]+)\].*/\1/' | sort | uniq -c | sort -rn

# 3. Find unique exception types
grep 'Unhandled' /tmp/backend.clean.log | \
  sed -E 's/.*Unhandled ([A-Za-z]+).*/\1/' | sort | uniq -c

# 4. Find HTTP 5xx (the high-value short list)
grep -E 'HTTP 5[0-9]{2}' /tmp/backend.clean.log

# 5. Find log gaps >5s (JVM stall / GC suspect)
awk '{
  t=substr($1,1,8);
  if (prev!="") {
    diff=((substr(t,1,2)*3600+substr(t,4,2)*60+substr(t,7,2))-
          (substr(prev,1,2)*3600+substr(prev,4,2)*60+substr(prev,7,2)));
    if (diff<0) diff+=86400;
    if (diff>5) print diff " " prev " -> " t;
  }
  prev=t;
}' /tmp/backend.clean.log | sort -rn | head -10

# 6. DB driver chatter
grep -iE 'Neo4j|Postgres|Mongo|S3|Garage|Hikari|deadlock|timed?.*out' /tmp/backend.clean.log
```

Total wall-clock spent on this sift: ~25 min.
