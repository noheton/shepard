# Security Issues — shepard

Audit date: 2026-05-04. Scope: backend Java, scripts Python, frontend, infrastructure config.

## Executive summary

Five **CRITICAL** findings: (1) SQL injection in the spatial-data native query builder; (2) wildcard CORS configuration combined with state-changing methods; (3) permissions fallback that grants full read/write/manage to every user when an entity has no Permissions node; (4) SSRF + ReDoS in the subscription webhook filter; (5) Cypher injection in the Neo4j search query builder via user-controlled property names.

**Eight HIGH** findings around JWT lifetimes, cache TTL contradictions, exception leakage, default credentials, EOL crypto library (jjwt 0.11), and a supply-chain risk (`dotenv` package).

A configured-but-unused `spotbugs+findsecbugs` plugin (`backend/pom.xml:476-490`) provides no runtime signal — it lives in `<reporting>` and is never invoked by `mvn verify` or CI.

None of the critical findings are tracked as open GitLab issues. Two HIGH findings overlap with permission-cluster issues (#41, #62, #424, #483, #667, #717).

---

## CRITICAL findings

### C1 — SQL injection in spatial-data native query builder
- **Location**: `backend/src/main/java/de/dlr/shepard/data/spatialdata/repositories/NativeQueryStringBuilder.java:40,65,116-123`; callers `SpatialDataPointRepository.java:47-55,86-94`
- **Description**: `addJsonContainsCondition` interpolates a JSON-serialized user filter into a Postgres single-quoted literal: `" AND %s @> '%s'".formatted(parameterName, filterAsString)`. JSON serialization escapes `"` but **not `'`**; any `'` in a string value or key closes the literal and pivots into SQL. `addJsonFilterConditions` (lines 116-123) inlines `filterCondition.getKey()` and `filterCondition.getValue()` directly. The insert path inlines `JsonConverter.convertToString(metadata)` and `measurements` into `'...'` JSONB casts. Authenticated user with write on a spatial container can pivot to arbitrary SQL on the spatial datasource (PostGIS).
- **Recommendation**: Replace with named parameters via `query.setParameter("metadata", jsonString)` and `CAST(:metadata AS JSONB)`. Switch JSONB filtering to JDBC bind parameters; for dynamic JSON paths use `jsonb_path_exists(:json, :path::jsonpath)` with bound paths.
- **Fix effort**: M
- **Tracked**: No

### C2 — CORS misconfiguration: wildcard origin + state-changing methods
- **Location**: `backend/src/main/resources/application.properties:18-21`
- **Description**: `quarkus.http.cors.origins=*` together with `methods=GET,POST,HEAD,OPTIONS,DELETE,PUT,PATCH` and `Authorization,X-API-KEY` in allowed headers. Browsers block credentialed wildcard usage, but this still permits any web origin to issue cross-site reads (which return 401 but expose error bodies/timing) and, more importantly, makes Authorization-bearing requests possible cross-origin if a token leaks via a forwarding proxy or attacker-controlled subdomain. Combined with the X-API-KEY long-lived header, an XSS on any origin a user visits can spend tokens cross-origin.
- **Recommendation**: Replace with an environment-driven allowlist (`quarkus.http.cors.origins=${SHEPARD_ALLOWED_ORIGINS}`), set `quarkus.http.cors.access-control-allow-credentials=false`, and pin per deployment.
- **Fix effort**: XS
- **Tracked**: No

### C3 — Permissions fallback grants full access to entities with no Permissions node
- **Location**: `backend/src/main/java/de/dlr/shepard/auth/permission/services/PermissionsService.java:258-262`
- **Description**: `getRoles` returns `new Roles(false, true, true, true)` — i.e. manager + writer + reader = true — for any "legacy entity without permissions". `isAccessTypeAllowedForUser` (line 115) consults `getRoles` for every authorization decision; a single missed `createPermissions(...)` call (in a new entity type or due to a race condition during creation) silently exposes data with full write/manage rights to **every authenticated user**. Architecture documents this as known tech-debt #1, but the severity from a security standpoint is critical.
- **Recommendation**: Invert the default — empty Optional should return `new Roles(false,false,false,false)`. Add a Neo4j constraint or startup audit job that fails the deploy if any `BasicEntity` is missing its `has_permissions` edge. Add an integration test that creates each entity type and verifies a Permissions node exists.
- **Fix effort**: S
- **Tracked**: Partial overlap with #41/#62/#424/#483/#667 (permissions cluster) and tech-debt #1.

### C4 — SSRF + ReDoS in subscription webhook filter
- **Location**: `backend/src/main/java/de/dlr/shepard/common/filters/SubscriptionFilter.java:63-78`
- **Description**: `Pattern.compile(sub.getSubscribedURL())` compiles a user-supplied regex on every successful API response and runs `pattern.matcher(event.getUrl()).matches()` with **no timeout** — a catastrophic-backtracking regex DoSes every request. `client.target(sub.getCallbackURL())` POSTs to **any URL** the user supplied: no scheme allowlist, no IP filter for `169.254.169.254` (cloud metadata) / `127.0.0.1` / RFC1918, no DNS-rebinding mitigation. Outbound notifications include the `EventIO` payload (entity body), so this can also exfiltrate other tenants' data.
- **Recommendation**: (a) Validate `subscribedURL` regexes with a complexity checker / timeout-bounded matcher; (b) enforce HTTPS and an allowlist (or denylist of `localhost`/loopback/link-local/private CIDRs after DNS resolution) on `callbackURL`; (c) disable redirects on the JAX-RS client; (d) drop entity body from EventIO and only send IDs + URL.
- **Fix effort**: M
- **Tracked**: No

### C5 — Cypher injection via user-controlled property names and IRI types
- **Status**: **DONE** (commit `ab3f9da`). New `Neo4jQuery(cypher, params)` record; `ParamBinder` threaded through every recursive `Neo4jQueryBuilder` helper. Property-name allowlist (`KNOWN_PROPERTIES` + `[A-Za-z_][A-Za-z0-9_|.]*` regex) sits next to `OP_PROPERTY`/`OP_VALUE`. `Neo4jQueryBuilderInjectionTest` (10 tests) covers the malicious payloads from the recommendation. **Subsumes M9.**
- **Follow-up**: Same shape applies to additional `id()=` / `ID()=` sites surfaced by C5's exit-grep — `GenericDAO.getSearchForReachableReferences*`, `VersionDAO`, `ShepardFileDAO`, `StructuredDataDAO`, `*ReferenceDAO` family, `SemanticAnnotationDAO`. Tracked as backlog row **C5b**; should land before L2c.
- **Original Location**: ~~`Neo4jQueryBuilder.java:376` (`atPart`), `:386` (`iRIPart`), `:280` (lowercased JSON value), `:200-202,239-241` (annotation IRI/Name)~~
- **Original Description**: ~~`node.get(OP_PROPERTY).textValue()` user-controlled concatenated as a Cypher identifier path; annotation pair values inserted between literal `"` quotes; `valuePart` insertion of raw `node.get(OP_VALUE).toString()`. Impact: read access bypassing `readableByPart`, Neo4j RCE-equivalent via cluster procedures, cross-tenant exfiltration.~~
- **Tracked**: Partial overlap with #717 area.

---

## HIGH findings

### H1 — JWT API-key tokens are minted without `setExpiration`
- **Location**: `backend/src/main/java/de/dlr/shepard/auth/apikey/services/ApiKeyService.java:138-150`; validation in `JWTFilter.java:210-247`
- **Description**: API-key JWTs carry `setIssuedAt` / `setNotBefore` only — no `setExpiration`. Once issued, an API-key JWT is valid forever. Revocation requires a DB delete plus a 5-minute cache flush (line 232). If the private key in `~/.shepard/keys/private.key` ever leaks (file permissions are OS default, no `chmod 600` in `PKIHelper.java:107-110`), an attacker can mint API tokens for any username.
- **Recommendation**: Set `setExpiration` on JWT mint (e.g. 90 days) and rotate. Restrict key files to `0600` via `Files.setPosixFilePermissions`. Move to JWKS so keys can be cycled.
- **Fix effort**: S
- **Tracked**: No

### H2 — `UserLastSeenCache` is 30 minutes, contradicting documented "5 minute" grace period
- **Location**: `backend/src/main/java/de/dlr/shepard/auth/security/UserLastSeenCache.java:8`
- **Description**: Architecture docs say "5-minute grace period" for revoked sessions. `UserLastSeenCache` is `30 * 60 * 1000`. While JWT roles are still checked per-request, `UserFilter`'s userinfo→DB sync (which validates token-vs-userinfo username match, line 59) is bypassed for up to 30 minutes. This widens token-theft impact and disables Keycloak-side disablement for 30 min vs the advertised 5.
- **Recommendation**: Reduce TTL to 5 min (match `ApiKey`/`Permission` caches) or update the docs and architecture to match.
- **Fix effort**: XS
- **Tracked**: No

### H3 — CORS allows `OPTIONS` preflight bypass of authentication by design
- **Location**: `backend/src/main/java/de/dlr/shepard/common/filters/JWTFilter.java:98-101`
- **Description**: All `OPTIONS` requests are allowed without a token. Combined with C2 (wildcard origin), this gives browser-side attackers free reconnaissance of the API surface and lets them read CORS error envelopes.
- **Recommendation**: Subsumed by C2; once CORS origin is restricted, OPTIONS bypass becomes scoped.
- **Fix effort**: XS (no separate change once C2 is fixed)

### H4 — `ShepardExceptionMapper` leaks internal exception messages to clients
- **Location**: `backend/src/main/java/de/dlr/shepard/common/exceptions/ShepardExceptionMapper.java:21-24`
- **Description**: Catches all exceptions and returns `exception.getClass().getSimpleName()` and `exception.getMessage()` to the client. Hibernate / Neo4j / MongoDB exceptions frequently embed query fragments, parameter values, schema details, and constraint names. Full stack traces are logged at `error` on every 4xx (line 21), so logs fill with PII and noisy stacks.
- **Recommendation**: Return generic message for `INTERNAL_SERVER_ERROR`; only expose message for explicit `WebApplicationException` subclasses; log full stack only at `debug` or with sanitization.
- **Fix effort**: S
- **Tracked**: No

### H5 — `PublicEndpointRegistry` uses `startsWith` — path-traversal risk
- **Location**: `backend/src/main/java/de/dlr/shepard/common/filters/PublicEndpointRegistry.java:8-15`
- **Description**: `requestContext.getUriInfo().getPath().startsWith("/versionz")`. Any future entry like `/users` would match `/usersearch`, `/users/admin`, etc. URI normalization isn't performed — `/versionz/../containers/1` could match. Currently only `/versionz` is registered with no realistic prefix collision, but the implementation is unsafe by construction.
- **Recommendation**: Use exact-match or anchored regex; normalize the path; add unit tests.
- **Fix effort**: XS
- **Tracked**: No

### H6 — `dotenv` 0.9.9 in `scripts/poetry.lock` — supply-chain risk
- **Location**: `scripts/pyproject.toml:18`, `scripts/poetry.lock:158-169`
- **Description**: `dotenv` (PyPI) is a low-quality orphan package that re-exports `python-dotenv`. Owned by a single PyPI account; if the account is compromised, every CLI invocation runs attacker code. (Note: `jinja2` is locked to 3.1.6 — patched against the 2024-2025 CVEs.)
- **Recommendation**: Replace `dotenv = "^0.9.9"` with `python-dotenv = "^1.0"` and purge `dotenv` from `poetry.lock`.
- **Fix effort**: XS
- **Tracked**: No

### H7 — jjwt 0.11.5 (EOL) still in use across all auth code
- **Location**: imports in `JWTFilter.java:12-16`, `ApiKeyService.java:12`
- **Description**: jjwt 0.11.x branch is no longer maintained. Concrete impact today is limited (algorithm-confusion attacks not directly exploitable here because `setSigningKey(PublicKey)` pins to RSA and refuses HMAC), but lack of patches is a HIGH dependency-hygiene issue.
- **Recommendation**: Upgrade to `io.jsonwebtoken:jjwt-api:0.12.x`; switch to `parser().verifyWith(key).build()` API. Bundle with H1 (add `setExpiration` while migrating).
- **Fix effort**: S
- **Tracked**: No (Renovate `<0.12` pin currently blocks)

### H8 — Default credentials checked into the repo
- **Location**: `backend/src/main/resources/application.properties:133-145` (mongodb password `"password"`, neo4j `"shepardshepard"`, postgres `"shepard_secret"`); `infrastructure/.env.example:2,10,14,18,24,27,30` (Neo4j/Mongo/Postgres/Influx/Grafana all `"secret"`)
- **Description**: While `%dev` profile credentials are not used in prod and `.env.example` is a template, README guidance does not force users to change them and `infrastructure-local` containers default to these. Operators frequently copy `.env.example` to `.env` unchanged.
- **Recommendation**: Replace example values with placeholders that fail at startup if not changed (e.g. `CHANGE_ME_<random>`); document hardening; add a startup check.
- **Fix effort**: XS
- **Tracked**: No

---

## MEDIUM findings (compact)

| # | Location | Issue | Recommendation | Effort |
|---|---|---|---|---|
| M1 | `application.properties:85` | `quarkus.http.limits.max-body-size=` (unlimited) → trivial DoS via large uploads | Set explicit limit (e.g. 1 GiB) and per-endpoint constraints | XS |
| M2 | ~~`PKIHelper.java:107-110`~~ | ~~Generated RSA keys written without explicit `0600` perms~~ — **DONE** (`PKIHelper.restrictPrivateKeyPermissions`, `PosixFilePermissions.fromString("rw-------")`, best-effort on non-POSIX). | — | XS |
| M3 | `PKIHelper.java:104` | RSA 2048; modern guidance is RSA-3072 / Ed25519 | Increase or migrate to EdDSA | S |
| M4 | ~~`JWTFilter.java:159`~~ | ~~`header.replace("Bearer ", "")` strips `"Bearer "` anywhere~~ — **DONE** (`startsWith("Bearer ") ? substring(7) : header`). | — | XS |
| M5 | ~~`JWTFilter.java:113-117`~~ | ~~Logs the entire failed Authorization and X-API-KEY header values at warn level~~ — **DONE** (now logs `present`/`absent` only). | — | XS |
| M6 | `application.properties:38-41,45` | Logging filtered to suppress Neo4j deprecations and `BoltRequest` at INFO — masks security-relevant query failures | Keep WARN+ for these categories or route to a separate logger | XS |
| M7 | `ShepardExceptionMapper.java:21` | All exceptions logged with full stack at error — log-volume DoS plus PII | Sanitize and rate-limit | S |
| M8 | `PermissionsService.isAllowed:198-243` | Hard-coded path-segment whitelist; comments say "permissions are already checked inside …Service" — that's a doc invariant, not code-enforced | Add unit tests asserting each Service performs its own check; consider annotation-based authorization | M |
| M9 | ~~`Neo4jQueryBuilder.java:280`~~ | ~~`node.get(OP_VALUE).toString().toLowerCase()`~~ — **DONE** (subsumed by C5; commit `ab3f9da` parameter-binds `createdBy`/`updatedBy`). | — | S |
| M10 | `application.properties:18-22` | CORS responses do not configure `Vary: Origin` — browser-cache poisoning risk if origins is later restricted | Set `quarkus.http.cors.origins-vary=true` if Quarkus supports | XS |
| M11 | `infrastructure/.env.example:35` | `FRONTEND_AUTH_SECRET="Frontend auth secret"` is not high-entropy | Document required entropy; validate at startup | XS |
| M12 | `backend/pom.xml:476-490` | SpotBugs/findsecbugs in `<reporting>` only; never runs during `mvn verify`; CI does not call `mvn site` | Move to `<build><plugins>` with `verify` execution; fail on High findings | S |

---

## LOW / informational

| # | Location | Note |
|---|---|---|
| L1 | `JWTFilter.java:202-203` | OIDC subject split on `:` → cryptic usernames (tech-debt #2). Consider stable `sub`-based UUID mapping. |
| L2 | `application.properties:117-118` | Prometheus metrics endpoint at `/shepard/api/metrics/prometheus` — confirm auth is required (only `/versionz` is in the public registry). |
| L3 | `HtmlSanitizer.java:34` | `addAttributes(":all", "style", …)` — inline `style` is a CSS-XSS sink; jsoup strips most but `style` remains broad. |
| L4 | `SubscriptionRest.java` | No per-user cap on subscriptions — DoS via 10k subscriptions slows every successful response (line 60-73 iterates all matching). |
| L5 | `ApiKeyRest.java:115-123` | `createApiKey` returns the JWT once via `ApiKeyWithJWTIO` — good. No rate limiting on creation. |
| L7 | `application.properties:48` | MongoDB connection string defaults to `mongodb://mongo@mongodb:27017` — relies on env for password in prod profile. |
| L8 | `JWTFilter.java:58` | Package-private no-arg constructor exists for CDI proxying; bypasses key initialization on direct instantiation. Acceptable due to CDI semantics. |

---

## Doc-vs-implementation gaps

- **"Static OIDC public-key configuration"** — confirmed (`JWTFilter.java:75-83`; `oidc.public` decoded from base64 and pinned at startup). No JWKS rotation; key change requires redeploy.
- **"5-minute grace period for revoked keys/permissions"** — partially false. `ApiKeyLastSeenCache` and `PermissionLastSeenCache` are 5 min; `UserLastSeenCache` is **30 min**. See H2.
- **"Container deletion only by owner"** — not directly verified; recommend coverage in permission tests.
- **"Public Readable" semantics** — implementation is correct (`PermissionsService.isReader:281-286` returns true for `Public` and `PublicReadable`; `isWriter:289-293` only for `Public`).
- **"Pre-permission entities have NO permissions → fallback grants access"** — confirmed; rated CRITICAL (C3). Architecture lists this as known tech-debt; severity from a security standpoint deserves elevation.
- **`@EndpointDisabled` / `@IfBuildProperty` toggles do not work in native builds** — confirmed by code presence (`SpatialDataPointRest.java:47`, `CollectionVersioningRest.java:39`); native-build behavior not testable here.
- **`/healthz` is not in `PublicEndpointRegistry`** — `/versionz` is the only public path. Operators expecting unauthenticated `/healthz` will hit 401.

---

## CI/CD security recommendations

1. **Activate SAST**: move SpotBugs + findsecbugs from `<reporting>` to `<build>` and run in the `check` stage (`mvn -Pci verify spotbugs:check`). Fail build on `Threshold=Medium` security categories.
2. **Add Trivy** (`aquasecurity/trivy-action` or GitLab template) to scan the produced container image and Maven dependencies; fail on high CVE severity.
3. **Add ESLint security plugin** (`eslint-plugin-security`) to the frontend lint step.
4. **Add `bandit` or `semgrep` for `scripts/`**: catches the `dotenv` issue and Jinja2 SSTI patterns.
5. **Run full `verify` (not just `check`) on main and release pipelines** so integration tests + SAST run before tag.
6. **Add a startup integration test** that creates each entity type and asserts it acquires a Permissions node — addresses the C3 invariant.
7. **Extend secret scanning** to fail on default values like `shepard_secret` / `shepardshepard`.
8. **Add OWASP Dependency-Check or Renovate-driven CVE policy** for jjwt, jinja2, and Quarkus BOM updates.
