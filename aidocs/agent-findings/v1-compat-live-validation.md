# V1COMPAT.0 Phase 1 — live validation findings

**Date.** 2026-05-22
**Target.** `shepard.nuclide.systems` (frontend) + `shepard-api.nuclide.systems` (backend, direct)
**Scope.** Marker plugin landed on `main` in commits `7cfde4c1 … f2221e64`. Three verifications: deprecation headers, frontend banner, 410 gate.
**Verdict.** **All three verifications FAILED.** The plugin compiles, packages, deploys, and its admin REST endpoints are partially reachable — but the load-bearing JAX-RS request/response filters are not running on the live `/shepard/api/...` request path. Detail below.

---

## Deploy summary

The marker plugin's deploy required four operator-level workarounds before backend would even start. None are caused by V1COMPAT.0 itself; all are pre-existing main-state hazards. They are documented here for the V1COMPAT.0 author + the team because they currently block any operator who tries to deploy the just-shipped state.

1. **Frontend TS build error.** `frontend/composables/common/api/useV1DeprecationMiddleware.ts` `post(): Promise<...> | void` violates the SDK's `Middleware.post: (ctx) => Promise<Response | void>` signature — the build fails with `error TS2322`. Fixed inline by making `post` `async`. This IS a V1COMPAT.0 defect — the middleware is new in PR-3 and never passed `npm run build` against main's `@dlr-shepard/backend-client` runtime types. Sole code patch I applied during validation; left in working tree, NOT committed to main.
2. **`shepard.audit.instance-secret` missing operator config** (SHACL-1, commit `0f535314`). Quarkus 3.27 rejects `defaultValue=""` for the required `@ConfigProperty`. Added `SHEPARD_AUDIT_INSTANCE_SECRET` placeholder to `infrastructure/docker-compose.override.yml`. Pre-existing, not a V1COMPAT.0 defect.
3. **Flyway migration `V1.11.0__add_shepard_id_to_timeseries.sql` requires pgcrypto extension** but the shepard postgres role lacks CREATE privilege. Manually ran `CREATE EXTENSION IF NOT EXISTS pgcrypto;` as the `postgres` superuser. Pre-existing TS-ID PR-1 / IMP1b operator-gap.
4. **Duplicate Neo4j migration V59** — a stale `V59__NOOP_ShepardFile_migration_rollback_fields.cypher` artifact survived in `backend/target/classes/neo4j/migrations/` from a pre-FS1e3a build; `V59__InstanceConfig_constraint.cypher` (SHACL-1) collided. Resolved by deleting the stale class file + re-packaging. Pre-existing — any operator with a dirty `target/` after pulling main hits this. A future CI safety net could `mvn clean` before `package`; that's out of scope for this validation.

After all four were addressed, the backend reaches `healthy` and the v1-compat plugin manifest logs:

> `V1COMPAT.0: v1-compat plugin v1.0.0-SNAPSHOT active via PluginManifest SPI (id=v1-compat, compat=>=6.0.0-SNAPSHOT,<7). Phase 1 marker — :LegacyV1Config singleton seeded by V63 Cypher migration; runtime knob at /v2/admin/legacy/v1/config; stats at /v2/admin/legacy/v1/stats.`

Cypher migration `V63 (Bootstrap legacy v1 config)` applied cleanly. Built image: `shepard-backend-patched:local` (re-packaged at 17:23 UTC). Frontend image: `shepard-frontend:local`.

---

## Verification 1 — deprecation headers on default-state v1 responses

**Expected.** Every `/shepard/api/...` response carries `Deprecation: true`, `Link: </v2/>; rel="successor-version"`, and `X-Shepard-Legacy: true`.

**Actual.** **FAIL.** None of the three headers are present.

```
$ curl -sS -D - -o /dev/null -H "X-API-KEY: <jws>" \
    https://shepard-api.nuclide.systems/shepard/api/users
HTTP/2 200
content-type: application/json;charset=UTF-8
content-length: 632
date: Fri, 22 May 2026 15:45:33 GMT
```

(No `Deprecation`, no `Link`, no `X-Shepard-Legacy`.)

Cross-checked against `/v2/admin/legacy/v1/stats`:

```
$ curl -sS -H "X-API-KEY: <jws>" \
    https://shepard-api.nuclide.systems/v2/admin/legacy/v1/stats
{"totalHits":0,"byEndpoint":[],"byPrincipal":[]}
```

`totalHits=0` after multiple `/shepard/api/...` requests confirms the `LegacyV1DeprecationFilter` is **not being invoked**. Counter increments happen in the filter's request side; the absence means request side never fired; therefore the response side never fires either; therefore the headers are absent.

**Diagnosis.** Likely a Quarkus REST class-discovery problem for `@Provider`-annotated JAX-RS filters living inside an `index-dependency` plugin JAR. Symptom triangulation:

- The plugin manifest log line above confirms the JAR loaded and the `V1CompatPluginManifest` startup observer ran.
- `/v2/admin/legacy/v1/stats` returning 200 confirms the plugin's **REST resources** are scanned by Quarkus REST.
- The plugin's filter classes are in the jar (`jar tf` confirms `LegacyV1DeprecationFilter.class` + `LegacyV1GateFilter.class`).
- The two filters use `@Provider` only — no `@ApplicationScoped` / `@Singleton` scope. This is the typical JAX-RS pattern for filters, but Quarkus's bean removal pass can drop `@Provider`-only beans loaded from index-dependency JARs unless they're marked unremovable (`@Unremovable`) or scoped (`@ApplicationScoped`).

No comparable filter has ever shipped from a plugin JAR in this codebase before — every other plugin's filter precedent is in-tree under `backend/`. This is the first one. The hypothesis is that `application.properties`'s `quarkus.index-dependency.shepard-plugin-v1-compat.*` is sufficient for resource (`@Path`) scanning but NOT for filter (`@Provider`) registration without an additional scope annotation.

**Recommended fix** (for the V1COMPAT.0 author to land in a follow-up PR, NOT applied here):

- Add `@ApplicationScoped` (or `@Singleton`) to both `LegacyV1GateFilter` and `LegacyV1DeprecationFilter`, in addition to the existing `@Provider`. This is the standard Quarkus pattern for filters discovered via index-dependency and matches the `UserFilter` precedent in core (`@ApplicationScoped + @Provider`).
- Plugin integration tests should run against an actual Quarkus-assembled JAR, not just direct-instantiation unit tests — the unit tests pass cleanly because they bypass CDI entirely. The smoke test from `infrastructure/smoke-test.sh` could grow a 5-line assertion: any GET against a `/shepard/api/...` endpoint must include `Deprecation: true`.

---

## Verification 2 — frontend deprecation banner at three viewports

**Expected.** When a response carrying `X-Shepard-Legacy: true` flows through the SDK middleware (`useV1DeprecationMiddleware`), the session-scoped `_v1HitCount` increments and `V1DeprecationBanner.vue` (mounted in `DefaultLayout.vue`) becomes visible. Wording must be non-alarming per `project_v1_sunset_strategy.md`.

**Actual.** **FAIL — banner cannot trigger** because the upstream cause from V1 is unresolved (no header → no banner).

Validation performed at three viewports (1440×900, 1920×1080, 3840×2160) via Playwright. For each viewport I:

1. Loaded `https://shepard.nuclide.systems/` (anonymous — sign-in screen).
2. Triggered a manual `fetch()` from inside the page to `https://shepard-api.nuclide.systems/shepard/api/users` with the JWS API key. (This bypasses the SDK middleware on purpose — to capture raw header state — but the absence of `X-Shepard-Legacy` rules out the middleware path too, since the middleware can only see what the backend emits.)
3. Captured screenshots and counted any DOM matches for `/legacy v1|deprecat/i`.

Result, identical across all three viewports:

```
deprecation header: <missing>
x-shepard-legacy  : <missing>
link header       : <missing>
banner-text match count: 0
```

Screenshots:
- `validation-screenshots/v1-compat-1440-home.png`
- `validation-screenshots/v1-compat-1440-after-v1-fetch.png`
- `validation-screenshots/v1-compat-1920-home.png`
- `validation-screenshots/v1-compat-1920-after-v1-fetch.png`
- `validation-screenshots/v1-compat-4k-home.png`
- `validation-screenshots/v1-compat-4k-after-v1-fetch.png`

**No 4K-specific layout issues** because the banner never rendered to assess.

**Wording check — deferred.** The `V1DeprecationBanner.vue` component exists at `frontend/components/context/legacy/V1DeprecationBanner.vue` but I did not render it to validate wording (the component is gated behind a v1HitCount > 0 condition that the live system never satisfies). A static source-read pass over the component is required to confirm wording is non-alarming; that's an open item once Verification 1 is fixed.

---

## Verification 3 — admin pane + 410 gate flip

**Expected.** Navigate to `AdminLegacyV1Pane.vue`, see current `enabled=true` state, flip to `false` (via UI or `PATCH /v2/admin/legacy/v1/config`), confirm subsequent `/shepard/api/...` returns 410 + `application/problem+json`, then flip back and confirm 200 restored.

**Actual.** **FAIL — admin endpoint broken**, cannot flip the toggle.

```
$ curl -sS -X PATCH -H "X-API-KEY: <jws>" -H "Content-Type: application/merge-patch+json" \
    -d '{"enabled":false}' \
    https://shepard-api.nuclide.systems/v2/admin/legacy/v1/config
HTTP/2 500
content-type: application/problem+json
content-length: 260

{"type":"https://noheton.github.io/shepard/errors/internal.unexpected","title":"Internal server error",...}
```

Same NPE on GET:

```
$ curl -sS -H "X-API-KEY: <jws>" \
    https://shepard-api.nuclide.systems/v2/admin/legacy/v1/config
HTTP/2 500
{"type":".../internal.unexpected", ...}
```

Backend log discloses the cause:

```
ERROR de.dlr.shepa.commo.excep.ShepardExceptionMapper —
  Unhandled NullPointerException on GET /v2/admin/legacy/v1/config -> HTTP 500
  cause: Cannot invoke "org.neo4j.ogm.session.Session.loadAll(java.lang.Class, int)"
         because "this.session" is null
  at de.dlr.shepard.plugins.v1compat.daos.LegacyV1ConfigDAO.findSingleton(LegacyV1ConfigDAO.java:47)
```

This is reproducibly the same NPE that was logged at startup-seed time:

```
WARN  V1COMPAT.0: could not seed :LegacyV1Config on startup; admin actions will retry on first read
java.lang.NullPointerException: Cannot invoke "org.neo4j.ogm.session.Session.loadAll(...)"
  because "this.session" is null
  at de.dlr.shepard.plugins.v1compat.daos.LegacyV1ConfigDAO.findSingleton(LegacyV1ConfigDAO.java:47)
```

**Diagnosis.** `LegacyV1ConfigDAO` extends `GenericDAO<T>`, whose constructor body does `session = NeoConnector.getInstance().getNeo4jSession()`. The DAO is `@ApplicationScoped`, so it's instantiated lazily on first injection. `NeoConnector.getNeo4jSession()` returns `null` when `sessionFactory == null`, and on the timeline of the plugin's `@Observes StartupEvent` firing, `NeoConnector.connect()` has not yet populated `sessionFactory`. The DAO captures that `null` into `protected Session session` once, and never re-reads `NeoConnector.getInstance().getNeo4jSession()` — so every subsequent call (including HTTP-request-time, hours later) sees the cached null.

**Why this isn't a generic GenericDAO bug for in-tree DAOs:** in-core DAOs are first injected DURING a JAX-RS request, well after `NeoConnector.connect()` has populated the factory. The v1-compat DAO is uniquely the first DAO to be injected from a `@Observes StartupEvent` observer (the plugin's startup-time `seedIfNeeded()`), which fires before the connector is ready.

The plugin's startup observer catches the NPE and logs the WARN — so the start-time failure is graceful. But the DAO instance is now permanently broken, so the admin REST endpoints can never read the singleton. This contradicts the design's clarification 4 leans where the runtime knob is the **central operator lever** for V1COMPAT.0.

**410 gate flip — not testable.** Because PATCH `/config` returns 500, I cannot put the singleton into `enabled=false` to verify the gate. Inspecting `LegacyV1GateFilter` source confirms the right behaviour IF it ran: 410 + ProblemJson + `X-Shepard-Legacy: true`. The filter likely has the same registration problem as `LegacyV1DeprecationFilter` (see Verification 1), so even if the singleton flip succeeded, the gate filter wouldn't fire either.

**Recommended fixes:**

1. **Drop the `@Observes StartupEvent` seed.** The Cypher migration `V63` already seeds the singleton row at migration-runner time (which runs BEFORE the OGM session is opened, against a separate session-less Cypher executor — and we see "Applied migration 63" in the logs). The startup-observer seed is defensive double-cover that fires too early and corrupts the DAO's session cache. Let `LegacyV1ConfigService.isEnabled()`'s 5s cache miss path do the lazy seed instead.
2. **Or:** `LegacyV1ConfigDAO` overrides `findSingleton()` to re-read `NeoConnector.getInstance().getNeo4jSession()` per call instead of relying on the parent's constructor-time capture. Defends against any future code path that exercises a plugin DAO before connector init.
3. **Filter scope fix** from Verification 1 also applies here: `LegacyV1GateFilter` needs `@ApplicationScoped` or `@Singleton` alongside `@Provider`.

---

## State left behind

- Backend image: `shepard-backend-patched:local` (re-packaged at 17:23 UTC after fixing the V59 stale-file collision). Running. Healthy.
- Frontend image: `shepard-frontend:local` (rebuilt with the inline `useV1DeprecationMiddleware.ts` type fix). Running.
- `infrastructure/docker-compose.override.yml` — added `SHEPARD_AUDIT_INSTANCE_SECRET` placeholder. **Uncommitted on `main`'s working tree.** Was committed (`git status` shows it as `M` from the session-start state, but the new lines I added are post that). Recommend committing as `chore(infra): seed SHEPARD_AUDIT_INSTANCE_SECRET placeholder per SHACL-1 Quarkus 3.27 contract`.
- `frontend/composables/common/api/useV1DeprecationMiddleware.ts` — inline type fix (`post` made `async`). **Uncommitted on `main`'s working tree.** Recommend committing as `fix(V1COMPAT.0): make useV1DeprecationMiddleware.post async to match Middleware type`.
- `:LegacyV1Config` singleton was seeded by `V63` migration (Neo4j confirms `Applied migration 63 ("Bootstrap legacy v1 config")`). State: enabled=true. Untouched by validation because PATCH fails with 500.
- Manual `CREATE EXTENSION pgcrypto;` ran as postgres-superuser on the timescaledb instance. Persistent in the DB; not undone.
- No live data was created or modified. No test users created. No admin actions other than failed PATCH attempts.

## Summary table

| Verification | Expected | Actual | Severity |
|---|---|---|---|
| 1. Deprecation headers on v1 response | `Deprecation: true` + `Link: …` + `X-Shepard-Legacy: true` | NONE present | **CRITICAL** — the load-bearing observability mechanism does not run |
| 2. Frontend banner at 1440/1920/4K | Banner visible after v1 hit | Cannot trigger (no header from V1); banner DOM match count = 0 | **CRITICAL** (blocked by V1) |
| 3. Admin pane + 410 gate flip | `GET /config` shows enabled=true; `PATCH enabled=false` → 410 on `/shepard/api/...` | `GET /config` = 500 NPE; `PATCH` = 500 NPE; gate not testable | **CRITICAL** — the operator lever is non-functional |

## Out-of-scope observations (per `feedback_validate_user_viewport.md`)

- The Caddy proxy in front of the shepard.nuclide.systems frontend issues a 302 to the Nuxt sign-in flow for any unauthenticated `/shepard/api/...` request, with a `via: 1.1 Caddy` header. That hop short-circuits the backend for unauthenticated callers — meaning even when the filter bug is fixed, an unauthenticated curl against `https://shepard.nuclide.systems/shepard/api/...` will still not see the headers. For Verification 1 the correct hostname is `shepard-api.nuclide.systems` (direct backend); this is documented in the override.yml comments but worth surfacing as a caller-facing gotcha for the docs in `plugins/v1-compat/docs/reference.md`.
- Cypher migration `V63__Bootstrap_legacy_v1_config.cypher` is in `backend/src/main/resources/neo4j/migrations/` (NOT in `plugins/v1-compat/`). Per the design's clarification 4 lean "C — stay in core" this is intentional (the migrations runner doesn't load plugin classes). Worth confirming the design's framing in `aidocs/34` reflects this.
- Many `Unhandled NotFoundException on GET /health -> HTTP 404` log entries every 3s — likely a Docker healthcheck probe hitting `/health` instead of the configured `/shepard/api/healthz/ready`. Unrelated to V1COMPAT.0 but it's noise that an SRE would file.

## [NEEDS-CLARIFICATION] block

None — the failure modes are concrete enough to act on without further clarification.
