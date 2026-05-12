# Upstream upgrade path — `dlr-shepard/shepard 5.2.0` → `noheton/shepard main`

**Snapshot date.** 2026-05-08.
**Upstream baseline.** `gitlab.com/dlr-shepard/shepard` v5.2.0 (the
container tags pinned in `infrastructure/docker-compose.yml`:
`registry.gitlab.com/dlr-shepard/shepard/backend:5.2.0`,
`.../frontend:5.2.0`).

This is the **live ledger** of every merged change on `main` that an
admin upgrading from upstream 5.2.0 needs to know about. New rows
land in the same PR as the change they describe — see `CLAUDE.md` at
the repo root for the standing rule.

The intended audience is an **operator** who ran `docker compose up`
on upstream 5.2.0, has data in their Neo4j / Mongo / Timescale, and
wants to upgrade to a build of this repo's `main` without losing it.

## TL;DR

As of the snapshot date, **upgrading is essentially `pull-and-restart`**.
No data-mutating migration has shipped yet; every change is either
additive (new endpoints, new optional config) or migration-safe
(idempotent Neo4j constraint adds with null-tolerance). The biggest
operator-visible change is a single config-key namespace deprecation
(A3c) which is alias-compatible until v6.0.

## API-version policy (the big rule)

The standing rule in `CLAUDE.md` (repo root) is reproduced here for
admin convenience:

- **`/shepard/api/...` is frozen.** Byte-compatible with upstream
  `dlr-shepard/shepard 5.2.0`. Existing clients (Python / TS / Java
  generated against upstream OpenAPI) keep working unchanged.
- **`/v2/...` is this fork's development surface.** Every new
  endpoint we add lands here. Opt-in for clients that want our
  additions; ignored by clients that don't.
- L2d formalises the split (`aidocs/25 §4 Phase 4`); L2e eventually
  drops the upstream long-id paths after a deprecation window.

Each row below indicates whether the endpoint touches `/shepard/api/`
(compat surface, additive only) or `/v2/` (this fork). Rows that
touch neither (config keys, internal behaviour) leave the column
blank.

The L2 chain (`L2b` … `L2e`) will introduce data-mutating migrations
and an API deprecation window — see "What's coming" at the bottom.

## Operator quick-start

```bash
# Stop upstream stack.
cd /opt/shepard && docker compose down

# Update images. Build locally, or pull this repo's CI-built tags
# once they're published.
git pull origin main
docker compose build      # if building locally
# or: docker compose pull  # if using published tags

# Optional one-time prep: rotate any default credentials per
# aidocs/07 H8 (this repo's main flags these as already-public).
$EDITOR .env

# Bring up. V11 (Neo4j unique constraints) applies on first start.
docker compose up -d
docker compose logs -f backend  # confirm 'neo4j migrations' lines log V11 applied
```

The frontend container does not need a separate migration step;
the backend is the only service with schema state.

## Change ledger

Status legend:

- **ZERO** — additive only; nothing for an admin to do.
- **CONFIG** — admin should change config (with backward-compatible
  alias / fallback in place).
- **AWARE** — admin should know about a new behaviour even though
  it's not breaking.
- **BREAKING** — a real breaking change with required admin action;
  spelled out in the row.

Migration column: filename of the Neo4j / SQL / app-side migration,
or `—` if no migration ships.

| ID | Change | Status | Config / endpoint impact | Migration | Operator action |
|---|---|---|---|---|---|
| **A1**  | DB connection wait: bounded timeout + exponential backoff | ZERO | New optional config `shepard.migrations.connection-wait-timeout` (default `PT60S`) | — | None. Default is safe. Consider raising the timeout if your Neo4j boots slowly. |
| **A1b** | Per-DB health check separation (startup vs runtime) | ZERO | `/healthz` body now reports per-DB `state` and `kind` | — | None. Useful for monitoring — point your liveness probe at `/healthz` (was already there) and your readiness probe at the same endpoint. |
| **A1c** | Graceful degradation when optional DBs (PostGIS) are down | ZERO | Spatial endpoints return 503 with RFC 7807 body + `Retry-After` when PostGIS is DOWN, 404 when feature toggle is OFF | — | None. Replaces a previously-broken behaviour where the whole API hung. |
| **A1e** | `MigrationsRunner` fails fast on migration errors | **AWARE** | A failed Neo4j-Migrations run now **aborts startup** (was: log + continue) | — | If you were dodging a known-broken migration by relying on the old swallow, that no longer works. Fix the migration or pin to upstream 5.2.0 until you can. |
| **A1f** | Automated DB recovery scheduler | ZERO | New `quarkus-scheduler` dependency. Config `shepard.health.recovery.interval` default `PT15S` | — | None. |
| **A3**  | Runtime feature toggles via `@ConditionalOnFeature` | ZERO | Mechanism only; `versioning` toggle migrated to it | — | None. |
| **A3b** | Runtime feature-toggle admin API: `GET /v2/admin/features`, `PATCH /v2/admin/features/{name}` | ZERO | New `/v2/` endpoints, `@RolesAllowed("instance-admin")`-gated. No config keys. No schema changes. Callers without the `instance-admin` role receive 403. Override is in-process only (does not survive restart). | — | None. Additive surface; opt-in. |
| **A3c** | Config namespace `shepard.spatial-data.*` → `shepard.infrastructure.spatial.*` | **CONFIG** | Both keys resolve. Old keys log a one-shot deprecation warning. Removal in **v6.0**. | — | Rename `shepard.spatial-data.*` keys in your `.env` / `application.properties` at your leisure. See `aidocs/A3c-namespace-migration.md` for the full mapping. |
| **A4**  | Permission cache TTL / max-size made tunable | ZERO | New optional `shepard.permissions.cache.ttl` (default `PT5M`), `.max-size` (default `10000`) | — | None. Defaults match upstream's effective behaviour. |
| **L5**  | Semi-permanent API keys with expiry | **AWARE** | `ApiKeyIO` now carries `validUntil`. JWTs minted on this branch carry an `exp` claim. | — | **Existing API keys keep working** — they were minted without `exp`, so the validator treats them as non-expiring. Newly-minted keys honour the new field. Distinguishable 401 on expiry. |
| **P3**  | Migration progress monitoring endpoint | ZERO | New `GET /migrations/progress` (read-only) | — | None. Wire up to your monitoring stack if useful. |
| **P3c** | `/temp/migrations/*` now requires `instance-admin` role (security fix; migration state is operator information) | **AWARE** | `GET /shepard/api/temp/migrations/{state,*containerId*}` returns 403 for non-admin callers (was 200 for any authenticated user) | — | If you had a monitoring scraper polling `/temp/migrations/state` with a regular user's JWT/API-key, switch it to an instance-admin API-key, or rotate the API-key to one with `roles: ["instance-admin"]` per the A0 role-claim mechanism. |
| **U1a** | `User.orcid` field + `PATCH /v2/users/me` (merge-patch, orcid only) | ZERO | New nullable `User.orcid` property (auto-persists via OGM; no migration). `UserIO` carries `orcid` additively — existing `GET /shepard/api/users` clients ignore unknown fields. New `/v2/users/me` PATCH endpoint accepts `application/merge-patch+json` (also `application/json` for compatibility). | — | None. Users can self-set their ORCID via the new endpoint; the field surfaces automatically in `GET /shepard/api/users` and the RO-Crate export (`aidocs/31`). |
| **G1a** | `:GitReference` (loose-link mode-(a)) + `/v2/data-objects/{appId}/git-references` CRUD | ZERO | New `:GitReference` node label with `appId` unique constraint (`V19`, idempotent). New `/v2/data-objects/{appId}/git-references` endpoints (GET list, POST create, GET single, DELETE). Permission piggybacks on DataObject Read/Write. | `V19__Add_appId_constraint_GitReference.cypher` (idempotent, additive) | None. Greenfield label, no existing rows to migrate. |
| **U1b** | `User.displayName` override field + `effectiveDisplayName` derivation; `PATCH /v2/users/me` extended | ZERO | New nullable `User.displayName` property (auto-persists; no migration). `UserIO` adds `displayName` (raw user override) and a computed read-only `effectiveDisplayName` that falls back through `displayName → firstName lastName → redacted-username` per `aidocs/16 U1b`. `PATCH /v2/users/me` accepts the new field alongside `orcid`. | — | None. Users with cryptic Keycloak-UUID usernames can now set a friendlier rendered name; audit-trail render switch wired in U1b2 (see below). |
| **G1-cred** | Per-user git credentials (host + username + AES-GCM encrypted PAT) + `/v2/me/git-credentials` CRUD | **AWARE** | New `:GitCredential` node label with `appId` unique constraint (`V20`, idempotent). New `OWNS_CREDENTIAL` outgoing relationship on `:User`. New `/v2/me/git-credentials` endpoints (GET list, POST create, GET single, PATCH, DELETE). PAT encrypted at rest with AES-GCM-256; **requires new config key `shepard.secrets.encryption-key`** (base64-encoded 32-byte key). If key is absent, the GET/PATCH credential endpoints return 501. | `V20__Add_appId_constraint_GitCredential.cypher` (idempotent, additive) | **Config action required**: generate a 32-byte key and set `shepard.secrets.encryption-key` in `application.properties` or as env var `SHEPARD_SECRETS_ENCRYPTION__KEY`. Without it, PAT storage is disabled and endpoints 501. Generate: `openssl rand -base64 32`. The encryption key must be kept secret — treat it like a database password. |
| **U1b2** | Audit-trail display-name render switch — `createdBy` / `updatedBy` fields in all IO classes and RO-Crate export now surface resolved display names instead of raw usernames | **AWARE** | `BasicEntityIO`, `VersionIO`, `SubscriptionIO`, `LabJournalEntryIO` all call `DisplayNameResolver.effectiveDisplayName()` for `createdBy`/`updatedBy`. RO-Crate `addPersonEntity()` uses ORCID as `@id` when set, otherwise falls back to username; `name` property uses `effectiveDisplayName()`. JSON field **keys** are unchanged (`createdBy`, `updatedBy`) — values change from raw username to human-readable display name. | — | **For users with `firstName`/`lastName` set or a `displayName` override:** the `createdBy` field in API responses and RO-Crate exports will now contain the rendered name rather than the raw username. Clients that hard-code username comparison against `createdBy` (e.g. scripted exports or test fixtures) should switch to comparing against `displayName`/`effectiveDisplayName` from `GET /v2/users/me`. For users whose names are not set, the fallback redacts cryptic Keycloak UUIDs to an 8-char prefix — no information loss for readable usernames. |
| **P14** | NDJSON streaming ingest for timeseries | ZERO | `POST /timeseriesContainers/{id}/payload` accepts `application/x-ndjson` in addition to existing JSON | — | None. Optional. New caps `shepard.timeseries.ingest.ndjson.batch-size` (default 5000), `…max-duration` (default `PT5M`). |
| **R2**  | Body-form selective RO-Crate export (Phase 1) | ZERO | New sibling `POST /collections/{id}/export` with `ExportSelection` body. Existing GET unchanged. | — | None. Existing exports byte-identical. |
| **R2b/c/d/d2** | Selective-export refinements (per-payload picks, redaction, metadata bundling, subscription emission) | ZERO | Additive fields on the body-form endpoint. Defaults preserve current behaviour. | — | None. |
| **L2a** | Additive `appId` (UUID v7) on every Neo4j entity write | **AWARE** | New nullable `appId: STRING` property on 28 node labels. New per-label unique constraint (Neo4j ignores nulls until backfill). | `V11__Add_appId_unique_constraints.cypher` (idempotent, additive) | None at upgrade time. **Be aware**: subsequent writes mint `appId`; existing rows keep `appId = null` until L2b backfills them. The L2 chain is documented in `aidocs/25`. |
| **L2b** | Backfill `appId` on pre-L2a rows | ZERO | No endpoint or config impact | `V12__Backfill_appId.cypher` (chunked 10k rows per batch, idempotent via `WHERE n.appId IS NULL`) + operator-run rollback at `V12_R__Rollback_Backfill_appId.cypher` | None — runs automatically on next startup. After completion, every node in the 28 labels has a non-null `appId`. The V11 unique constraint (which previously tolerated nulls) becomes meaningfully unique. Backfill is bounded by graph size; chunked at 10k rows so it doesn't lock the entire graph. On a `> 1M` node graph, expect minutes-to-low-tens-of-minutes startup delay the first time. Subsequent starts are no-ops (`WHERE n.appId IS NULL` returns zero rows). |
| **C5** | Cypher injection fix — parameter-binding + property-name allowlist in `Neo4jQueryBuilder`, `PermissionsDAO`, `DataObjectDAO` | ZERO | No endpoint or config impact (internal Cypher construction only) | — | None. Search and DAO query shapes change internally — the wire contract (request body, response shape) is unchanged. Closes the injection vector against user-controlled property names + IRI values; subsumes M9. |
| **C5b** | Second-wave parameter-binding fix — `GenericDAO.getSearchForReachableReferences*`, `VersionDAO`, `ShepardFileDAO`, `StructuredDataDAO`, the `*ReferenceDAO` family, `SemanticAnnotationDAO` | ZERO | No endpoint or config impact | — | None. Same shape as C5; closes the remaining `id()=` concat sites. After this lands, the L2c read-path swap from `long` to `String appId` can ship without re-opening injection sites. |
| **H5** | `PublicEndpointRegistry` exact-match (was `startsWith`) — closes the path-traversal vector + the future-entry foot-gun | ZERO | No endpoint or config impact | — | None. Internal filter behaviour change only. Today's single registered entry `/versionz` keeps matching; `/versionzanything` and `/versionz/../containers/1` now correctly do not match. |
| **P4** | `/v2/` JAX-RS routing scaffolding — `quarkus.http.root-path` flipped to `/`; existing resources carry explicit `@Path("/shepard/api/...")` prefix; `de.dlr.shepard.v2` package reserved with ArchUnit fence; `@Sunset` annotation skeleton ready for L2e | ZERO | User-facing URLs unchanged (`/shepard/api/...` paths still resolve identically). Internal config + package convention only. | — | None. Confirms after upgrade that `https://<host>/shepard/api/healthz` still returns 200. The `/v2/` shelf is now reachable for future L2d endpoints; today no `/v2/...` paths exist. |
| **L2c** | Read-path swap — internal Cypher reads switch from `WHERE ID(e) = $entityId` to `WHERE e.appId = $appId` across `PermissionsDAO`, `DataObjectDAO`, `GenericDAO.getSearchForReachableReferences*`, the `*ReferenceDAO` family, `VersionDAO.findVersionLightByNeo4jId`/`createLink`, `ShepardFileDAO.find`, `StructuredDataDAO.find`, `SemanticAnnotationDAO.findAllSemanticAnnotationsByNeo4jId`. New request-scoped `EntityIdResolver` translates the OGM Long to its `appId` at the DAO boundary so call signatures stay `long`. | ZERO | No endpoint, config, or wire-contract impact. The `/shepard/api/...` (`/v1/`) surface is byte-frozen; only the internal Cypher changes. The permissions cache key stays `long` (per `aidocs/25 §3.3`). The OGM `Long id` stays in place. `Neo4jQueryBuilder`'s search-JSON-derived numeric-id predicates also stay (changing them would be a public-API-shape change — that's L2d). `id(c_pointer) = id(c_pointed_to)` in `VersionDAO.copyInternalDataObjectReferences` flipped to `c_pointer.appId = c_pointed_to.appId` (semantically identical given the V11 unique constraint). | — | None. Internal-only change; no migration needed (constraint and backfill already shipped in L2a/L2b). Read paths now require `appId` to be populated — already guaranteed by L2b's V12 backfill. The resolver bootstraps via `MATCH (e) WHERE id(e) = $ogmId RETURN e.appId LIMIT 1` (still uses `id()` once at the boundary; full `id()` retirement is L2e's job, gated on Neo4j-OGM removal `#274`). |
| **CI quality gates** | JaCoCo coverage gate (60% line / 60% branch); SpotBugs + findsecbugs on `verify`; OWASP Dependency-Check (weekly, fail at CVSS≥7); gitleaks secret scan | ZERO | New `backend/pom.xml` properties `jacoco.haltOnFailure` + `spotbugs.failOnError` (default `false` locally; CI flips to `true`) | — | None for an admin running shepard. Affects developers contributing to this fork: `mvn verify` now invokes JaCoCo `check` and SpotBugs `check` (informational locally; gating in CI). New `dependency-check-suppressions.xml` (empty stub, template-documented) lives at `backend/`. |
| **CI image build** | This fork now publishes its own GHCR images (`ghcr.io/noheton/shepard-{backend,frontend}:{latest,sha-<7>,vX.Y.Z}`) via `.github/workflows/build-images.yml` on push to `main` and on `v*.*.*` tags. | ZERO | An admin running their own shepard deployment can opt into the fork's GHCR images via `infrastructure/docker-compose.override.yml.example` (rename to `docker-compose.override.yml`). Default compose still references upstream gitlab.com images. | — | **Optional**: copy the override file template to use the fork's images. Without the override, `docker compose up` fetches the upstream 5.2.0 images and you don't get this fork's changes. With the override, `docker compose pull` fetches `ghcr.io/noheton/shepard-{backend,frontend}:latest`. Pin to `:sha-<7>` for stability. |
| **CI security extension** | Six security gates now in CI: SpotBugs+findsecbugs (SAST/Java) + **CodeQL** (SAST/Java+JS, security-extended queries) + OWASP Dependency-Check (SCA, weekly) + **Trivy** (container CVE scan on GHCR images) + gitleaks (secrets) + **dependency-review** (PR-time licence + new-CVE check). **SBOM** (CycloneDX) generated for every published image via `anchore/sbom-action` and attached to GitHub releases. New `.github/dependency-review-config.yml` carries the GPL/AGPL/SSPL deny-list. | ZERO | An admin running their own shepard deployment is unaffected — these run only in this repo's CI. Affects developers contributing to the fork: more gates per PR, more findings to fix or suppress (with justification). | — | None. Findings flow to the Security tab → Code Scanning. |
| **M4 / M5 / M2** | JWT-filter Bearer-prefix mangling fix; redaction of token values in warn-level logs; `0600` perms on the generated PKI private key | ZERO | No config / endpoint impact | — | None. Behaviour change observable only in (a) tokens that contain the literal substring `"Bearer "` mid-payload now parse correctly, (b) `Log.warn` of `"Invalid/missing authorization header"` now reports `present`/`absent` instead of full header values, (c) `~/.shepard/keys/private.key` is created with `rw-------` rather than the umask default. Pre-existing key files keep their old perms — re-run `chmod 600 ~/.shepard/keys/private.key` once on upgrade if your audit story requires it. |
| **H4** | RFC 7807 (`application/problem+json`) error responses + sanitised exception logging — subsumes M7 | **AWARE** | Every error response (`/shepard/api/...` and `/v2/...`) now defaults to `application/problem+json` with the RFC 7807 fields (`type`, `title`, `status`, `detail`, `instance`). For `Accept: application/json` (with no `application/problem+json` and no wildcard), the legacy `ApiError` shape is still returned — preserves upstream-client wire compatibility. The `instance` is `X-Request-Id` if present, otherwise a request-scoped UUID. `type` URIs are `https://noheton.github.io/shepard/errors/<code>` (docs page can be authored later — URI is a stable identifier per RFC 7807 §3.1 even before the page exists). | — | **Be aware**: 5xx response bodies are now generic — `detail` reads `"An internal server error occurred. Reference: <traceId>"` instead of leaking the underlying Hibernate / Neo4j / Mongo exception message. Clients that scraped the legacy leaky message stop seeing it. Known shepard 4xx exceptions (`InvalidAuthException`, `InvalidBodyException`, `InvalidPathException`, …) keep surfacing their (controlled) message as `detail`. Server-side: full stacktrace logs at `debug`; the `error` line carries only `traceId + exception class + http method + request path` (no request body, no headers, no exception message). To get the legacy `ApiError` shape on a one-off basis, send `Accept: application/json` (must not also accept `*/*` or `application/problem+json`). |
| **A0 + F8** | Instance-Admin role mechanism (backend slice). New `instance-admin` role tier; configurable OIDC roles-claim path (`shepard.oidc.roles-claim-path`, default `realm_access.roles`); `shepard.instance-admin.role` names the IdP-claim role-string that maps to `instance-admin`; new `Role` Neo4j entity + `:HAS_ROLE` relationship; bootstrap-token mechanism (writes `/opt/shepard/.bootstrap-token` mode 0600 on first start when no instance-admin exists); `POST /v2/admin/bootstrap`, `GET/POST/DELETE /v2/admin/instance-admins`, `GET /v2/admin/permission-audit`; API-key `roles` field with allowlist + caller-must-have-each-role validation. | **AWARE** | New optional config keys (all default safe): `shepard.oidc.roles-claim-path`, `shepard.instance-admin.role`, `shepard.apikey.role-allowlist`, `shepard.bootstrap.token-path`, `shepard.permissions.default-owner` (see C3 row). New endpoints under `/v2/admin/*` — additive surface, all `@RolesAllowed("instance-admin")`-gated except the bootstrap one. The IdP-side admin grant is **opt-in** — leave `shepard.instance-admin.role` empty (default) to disable IdP-side grants entirely; bootstrap-token + Neo4j edges remain the only path. | `V13__Add_appId_constraint_Role.cypher` (single-statement constraint add, idempotent) | **First-start operator runbook**: (1) `docker compose up -d`. (2) Watch the logs: a single-line `WARN` reports the bootstrap token path. (3) `sudo cat /opt/shepard/.bootstrap-token` and feed it to `POST /v2/admin/bootstrap` with body `{"token": "<token>", "username": "<your-IdP-username>"}` — the user must have logged in at least once via OIDC. (4) Confirm via `GET /v2/admin/instance-admins`. (5) Optional: configure `shepard.instance-admin.role=<keycloak-role>` to also grant via the IdP claim. The `shepard-admin` CLI commands that wrap these endpoints land in L1 Phase 1 (post-this-slice). |
| **C3** | Permissions fallback fail-closed — `PermissionsService.getRoles` now returns `Roles(false, false, false, false)` for entities lacking a `:has_permissions` edge (was: full read+write+manage for every authenticated user). Pre-migration hook `OrphanPermissionsBackfillContext` aborts startup if orphans exist AND `shepard.permissions.default-owner` is unset. | **AWARE** | Bundled with **A0**. New required-when-orphans-exist config `shepard.permissions.default-owner=<existing-username>`. Greenfield deployments (no pre-permission entities) can leave it unset. | `V14__Backfill_orphan_permissions.cypher` (idempotent — only attaches Permissions to nodes lacking them; the Java pre-hook aborts if config is unset and orphans exist) + `V14_R__Rollback_Backfill_orphan_permissions.cypher` (operator-run; deletes the V14-marker Permissions nodes via the `legacyBackfill = 'A0-V14'` property tag) | **Operators upgrading from a really old shepard with pre-permission entities MUST set `shepard.permissions.default-owner` before the next start** — otherwise the Java pre-migration hook aborts startup with a clear error pointing here. Pick a username that already exists as a `:User` node in your Neo4j (the user must have logged in at least once via OIDC, or be pre-created). After the backfill applies, the C3 fail-closed default takes effect — every entity now has a Permissions node, no fallback needed. Verify via `GET /v2/admin/permission-audit` (post-A0): list should be empty. **Existing healthy deployments** (no orphans) can leave the config unset; the hook is a no-op. |
| **A0 — API-key roles claim** | `POST /apikeys` body grows an optional `roles: [...]` field; minted JWTs carry a `roles` claim cross-checked against the Neo4j-stored `roles` Set on every (uncached) request. | **AWARE** | Existing API keys keep working with empty `roles` (default behaviour preserved). Clients that want to call admin-gated `/v2/admin/...` paths via an API key must mint **new** keys with `roles: ["instance-admin"]` — and the minting user must already hold the role (no privilege escalation). The role must also be in `shepard.apikey.role-allowlist` (default `["instance-admin"]`; shrink to `[]` to forbid role-bearing keys entirely). Mismatch between the JWT's `roles` claim and the stored Set on read returns 401 (treats as forged). | — | **No action required for upgrade.** Existing keys stay role-less and stay valid. Mint new role-bearing keys when adopting admin tooling that needs them (e.g. the future L1 `shepard-admin` CLI in Phase 1). |
| **Removed: deploy-target + deploy guides** | Dropped `.github/workflows/deploy-test-instance.yml`, `docs/deploy-self-hosted-zoraxy.md`, `docs/deploy-oracle-free.md`; `docs/deploy.md` rewritten without those two paths. GHCR image-build workflow is retained — operators can still pull `ghcr.io/noheton/shepard-{backend,frontend}` via the override template. | ZERO | An admin running their own shepard deployment is not affected (no shipped endpoint, config, or schema change). The two deleted guides were maintainer-host-specific recipes; the upstream production deploy guidance (`gitlab.com/dlr-shepard/shepard` wiki) remains authoritative. | — | **None.** The deletions only remove maintainer-side infra. If you were following one of the deleted guides verbatim, fall back to the upstream wiki or to `docs/deploy.md`'s comparison matrix. |
| **PROV1a — provenance / activity capture** | New `:Activity` Neo4j entity (HasAppId; modelled on W3C PROV-O `prov:Activity`); JAX-RS request+response filter captures POST/PUT/PATCH/DELETE on 2xx responses by default. New `GET /v2/provenance/{activities,count}` query endpoints (casual users see only their own rows; instance-admin sees all). Designed in `aidocs/55`. | **AWARE** | Three new optional config keys (all default-safe): `shepard.provenance.enabled=true` (master toggle), `shepard.provenance.capture-reads=false` (opt-in read capture), `shepard.instance.id=local` (per-deployment origin stamp; used by `aidocs/60` Edge sync). The capture filter is a JAX-RS Provider — applies to every authenticated endpoint. Captures NOT persisted on failure paths (provenance is observability, never blocks the request). | `V15__Add_appId_constraint_Activity.cypher` (single-statement constraint add, idempotent; mirrors V13's shape for `:Role`). | **No action required for upgrade.** Activity rows begin landing from the first 2xx mutation post-deploy. Operators wanting read capture flip `shepard.provenance.capture-reads=true` (cost: row-volume grows ~10×). To verify capture is working: `GET /v2/provenance/activities` after the first POST returns at least one row. |
| **AAS1-well-known — AAS self-description** | New unauthenticated `GET /v2/aas/.well-known/aas-server` endpoint (`aidocs/52 §4a.5`). Returns JSON: `enabled`, `aasApiProfile`, `endpoints`, `supportedSubmodelTemplates`, `shellCount`, `registryRegistrations`. Capability flags + counts only — never per-Shell identifiers — so reachable pre-auth is the design (mirrors `/versionz` / `/v2/admin/bootstrap`). | ZERO | Two new optional config keys (both default-safe): `shepard.aas.enabled=false` (master toggle; when `false`, the endpoint still answers — `enabled: false` is the signal an AAS-aware client uses to skip), `shepard.aas.api-profile=Submodel-Repository-Read-3.1` (advertised profile). | — | **None.** Operators wanting to advertise AAS capability flip `shepard.aas.enabled=true`. `supportedSubmodelTemplates` auto-populates from `ShepardTemplate` rows with `templateKind=AAS_SUBMODEL_TEMPLATE`. `shellCount=0` and `registryRegistrations=[]` until AAS1a / AAS1-reg land. |

## Out-of-band changes (not yet visible to admins)

Several entries are **design done** in `aidocs/` but not yet on `main`.
They appear here only to flag the upgrade-path implications for when
they land:

| ID | What it'll do | Operator impact when it lands |
|---|---|---|
| **L2b** | Backfill `appId` on all pre-L2a rows via `V12__Backfill_appId.cypher` (chunked, idempotent) | Auto-migrates on next startup. Logs progress. Aborts startup on collision (post-A1e fail-fast). Rollback: `V12_R`. |
| **C5** | Cypher injection fix (parameter binding + property-name whitelist) | Internal only. No admin action. |
| **L2d** | `/v2/` API exposes `appId` natively | **AWARE** — additive new versioned API surface. `/v1/` paths keep `long` for ≥ 2 minor releases. |
| **L2e** | Drops `/v1/` long-id paths; flips permission-cache key shape; drops legacy TimescaleDB `containerId: long` column | **BREAKING** — clients on `/v1/` numeric IDs must migrate to `/v2/` `appId` strings. Generated clients (Python / TS / Java) will be regenerated as part of the deprecation window; pin to the previous client version if you need time. The TimescaleDB column drop will ship with a one-shot cross-DB backfill job; the operator runbook will land in this doc when the change does. |

## Image-build notes

The published `registry.gitlab.com/dlr-shepard/shepard/backend:5.2.0`
and `.../frontend:5.2.0` images on GitLab are the **upstream** tags;
this repo does not currently publish images. Two paths to deploy
from this repo:

- **Build locally.** `docker compose build` from `infrastructure/`
  builds both backend and frontend against your local maven cache.
  Reproducible per `Dockerfile`.
- **CI-built tags.** Not yet published. When they are, this row will
  cite the registry path. Until then, building locally is the
  supported path.

For the **ARM64** caveat (Apple Silicon, ARM-based cloud VMs), the
upstream images are amd64-only; either rebuild via
`docker buildx --platform linux/arm64` or run with QEMU emulation.

## Migration tests — deferred

Per the standing rule in `CLAUDE.md`, migration tests are tracked
but not blocking. The matrix below records what regression tests
should land alongside each migration:

| Migration | Required test | Status |
|---|---|---|
| `V11__Add_appId_unique_constraints.cypher` | Apply against a pre-L2a dump; assert all 28 constraints created; assert pre-existing nulls don't conflict | **deferred** — covered indirectly by `AppIdGeneratorTest` + DAO seam tests. Add explicit constraint-application IT after L2b ships. |
| `V12__Backfill_appId.cypher` (forthcoming, L2b) | Apply against a pre-L2a dump with rows of every label; assert post-backfill `MATCH (n) WHERE n.appId IS NULL RETURN count(n) = 0` | will ship with L2b |
| `V13__Add_appId_constraint_Role.cypher` | Apply against a pre-A0 dump; assert constraint created on `Role` label | **deferred** — covered indirectly by `RoleTest` + the `OrphanPermissionsBackfillContextTest` covering the V14 pre-flight checks. Add explicit constraint-application IT alongside V11/V12 once they land. |
| `V14__Backfill_orphan_permissions.cypher` | Apply against a pre-A0 dump with one orphan; assert post-backfill `(orphan)-[:has_permissions]->(p:Permissions)-[:owned_by]->(:User {username: $defaultOwner})`; assert idempotency on second apply (zero new edges) | **partial** — `OrphanPermissionsBackfillContextTest` (4 tests) covers the Java pre-hook's refusal logic + happy path with mocked driver. Apply-the-Cypher-against-a-live-Neo4j IT deferred to the migration-test profile alongside V12. |

## Reporting an upgrade-path gap

If you find a change on `main` that breaks an upstream upgrade and
**isn't documented here**, that's the bug. File an issue on
`noheton/shepard` with the breaking commit hash and the upstream
behaviour you expected; the fix is either to add a row above or to
revert the change. The standing rule in `CLAUDE.md` makes the
tracker update part of the change — a missing row is a process gap,
not a documentation backlog item.
