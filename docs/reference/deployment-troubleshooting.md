---
layout: default
title: Troubleshooting (deployment reference)
permalink: /reference/deployment-troubleshooting/
description: Common shepard failure modes + fix recipes — backend won't start, login redirects fail, plugin shows FAILED, migration aborted, OIDC unreachable, file upload fails, frontend blank.
---

# Troubleshooting

Each section starts with the **symptom** an operator sees and
walks through the **diagnostic checks** and the **fix recipes**.
Pages elsewhere in this guide cover the design rationale; this
page covers "the box is broken, what now."

The fastest place to look for the underlying cause is usually:

```bash
docker compose logs -f backend
```

Most actionable errors show up on the first 100 lines of
backend startup or as a single `ERROR`-level line during a
request.

## Backend won't start

**Symptom.** `docker compose up -d` followed by
`docker compose logs backend` shows the container exiting
within seconds.

### Check 1 — environment variables

```bash
docker compose config | grep -E '^\s*(OIDC|NEO4J|MONGO|POSTGRES|SHEPARD)_'
```

Missing or empty variables show as blank values. Cross-check
against `infrastructure/.env.example`; every variable except
`OIDC_ROLE` must be set.

### Check 2 — database connectivity

```bash
docker compose logs backend | grep -i "connection refused\|connection timed out"
```

Common shapes:

- **Neo4j not ready yet.** The default
  `shepard.migrations.connection-wait-timeout=PT60S` waits up to
  60 seconds for Neo4j; on a slow host that may not be enough.
  Bump it:

  ```env
  SHEPARD_MIGRATIONS_CONNECTION_WAIT_TIMEOUT=PT180S
  ```

- **Neo4j password wrong.** Symptom is a `Neo4j.AuthenticationException`.
  If you rotated `NEO4J_PW` in `.env` but Neo4j's data volume
  still holds the old password, either reset Neo4j's password
  (`docker exec neo4j cypher-shell -u neo4j -p <old> "ALTER
  USER neo4j SET PASSWORD '<new>'"`) or wipe the Neo4j data
  volume (destructive — only on a fresh deploy).

- **MongoDB authentication failed.** Same shape as Neo4j —
  password rotation must be done on both sides.

### Check 3 — migration aborted

```bash
docker compose logs backend | grep -E "MigrationsException|migration.*FAILED|MigrationsRunner"
```

Post-A1e, a failed migration aborts startup with a
`MigrationsException`. The log carries the failing migration's
name + the underlying error. Fix the migration (often a
Cypher-syntax incompatibility with the running Neo4j version),
or pin to the previous backend version until you can. See
[upgrade path]({{ '/reference/deployment-upgrade/#migration-ordering' | relative_url }}).

### Check 4 — JVM OOM

```bash
docker compose logs backend | grep -i "OutOfMemoryError"
```

Bump `JAVA_OPTS`:

```env
JAVA_OPTS=-Xms2G -Xmx6G
```

See [sizing]({{ '/reference/deployment-sizing/#sizing-the-jvm-heap' | relative_url }}).

### Check 5 — bootstrap-token gating

If the backend logs:

```
BootstrapTokenInitializer: refusing to write bootstrap token; replay-protection flag already set
```

…it means `:BootstrapState` is already populated. Your first
admin was minted in a previous run. This is **not** a failure —
shepard is working as designed.

To **re-bootstrap** (e.g. you lost the API key), clear the flag:

```cypher
MATCH (b:BootstrapState) DELETE b
```

…then restart. The new token file appears under
`/opt/shepard/.bootstrap-token`.

## Login redirects fail

**Symptom.** Click "Log in", get redirected to the OIDC IdP,
authenticate, get redirected back to shepard — and land on an
error page or in a redirect loop.

### Check 1 — redirect URI registered with the IdP

The OIDC client (`CLIENT_ID`) must have the **frontend's
public URL** in its allowed redirect URIs.

Keycloak: Clients → `<client-id>` → Settings → Valid redirect
URIs. Add `https://shepard.example.com/*`.

### Check 2 — backend reachable from the browser

```bash
curl -fsS https://backend.shepard.example.com/shepard/api/healthz/live
```

If this fails from the browser network but succeeds from the
backend host, the reverse-proxy config doesn't expose the
backend to the public.

### Check 3 — token validation

```bash
docker compose logs backend | grep -i "Invalid token\|JWT.*signature"
```

`OIDC_PUBLIC` doesn't match the IdP's current signing key — see
[OIDC §"Invalid token"]({{ '/reference/deployment-oidc/#troubleshooting' | relative_url }}).

### Check 4 — `FRONTEND_AUTH_SECRET` rotation

If you recently rotated `FRONTEND_AUTH_SECRET`, every active
frontend session is now invalid. The user must clear cookies
and log in fresh.

## Plugin shows FAILED

**Symptom.** `shepard-admin plugins list` shows a row with
`STATE = FAILED`.

### Check 1 — read the failure message

```bash
shepard-admin plugins list --output=json \
  | jq '.plugins[] | select(.state == "FAILED") | {id, failureMessage}'
```

Common `failureMessage` prefixes (from `plugins.md`):

| Message | Fix |
|---|---|
| `plugin.signature.unsigned` | Set `shepard.plugins.signing.required=false` or sign the JAR with `jarsigner` |
| `plugin.signature.untrusted` | Import the signer cert via `keytool -importcert` into the truststore |
| `plugin.compatibility.failed` | Upgrade the plugin to a version supporting your shepard, or set `shepard.plugins.compatibility.strict=false` |
| `plugin.dependency.missing` | Install the named sibling plugin into `/deployments/plugins/` |
| `plugin.dependency.version-mismatch` | Upgrade the sibling plugin |
| `plugin.dependency.cycle` | The plugin author has a circular dependency — open an issue against the plugin |

### Check 2 — backend startup log

The `plugin.discovery.failed` WARN line carries the underlying
exception + class name. Often it's a missing config key the
plugin needs — e.g. the DataCite plugin will fail at
`onRegister` if `shepard.publish.minter.datacite.endpoint` is
unset.

### Check 3 — JAR contents

```bash
unzip -l /deployments/plugins/<plugin>.jar | grep META-INF/services/
jar tf /deployments/plugins/<plugin>.jar | grep PluginManifest
```

The JAR must contain
`META-INF/services/de.dlr.shepard.plugin.PluginManifest` listing
the manifest implementation class.

Full plugin troubleshooting at
[plugins reference]({{ '/reference/plugins/#troubleshooting' | relative_url }}).

## Migration aborted

**Symptom.** Backend log on startup:

```
neo4j-migrations: Migration V<N>__<name>.cypher failed: <reason>
ABORTING STARTUP
```

### Check 1 — read the Cypher error

The `<reason>` is the Cypher engine's complaint. Common shapes:

- **`SyntaxError: ...`** — the migration uses syntax that's
  deprecated in the running Neo4j version. Pin Neo4j to the
  version range called out in
  `infrastructure/docker-compose.yml`'s comments, then re-run.

- **`ConstraintValidationFailed: ...`** — the migration is
  trying to add a UNIQUE constraint but the data violates it
  (the per-`appId` constraints are written to be tolerant of
  nulls, but if you skipped the L2b backfill, you'll hit this).

  Run the backfill manually:

  ```cypher
  // V12__Backfill_appId.cypher equivalent, condensed:
  MATCH (n) WHERE n.appId IS NULL
  CALL { WITH n SET n.appId = randomUUID() } IN TRANSACTIONS OF 10000 ROWS
  ```

  Restart the backend; the constraint migration retries.

- **`DatabaseError: out of memory`** — Neo4j heap too small for
  the migration's working set. Bump
  `NEO4J_dbms_memory_heap_max__size` in `docker-compose.yml`.

### Check 2 — rollback (if needed)

Some migrations ship a `V##_R__*.cypher` rollback. Invoke via
`cypher-shell`:

```bash
docker exec -i neo4j cypher-shell -u neo4j -p $NEO4J_PW \
  < /var/lib/neo4j/migrations/V12_R__Rollback_Backfill_appId.cypher
```

Then **fix the underlying issue and re-apply** — don't leave
the data in the rolled-back state.

## OIDC unreachable

**Symptom.** Login fails; backend log shows
`User info could not be retrieved` or
`AuthenticationException: ...`.

### Check 1 — DNS / network

From inside the backend container:

```bash
docker exec shepard-backend curl -fsS \
  $OIDC_AUTHORITY/.well-known/openid-configuration
```

If this fails:

- DNS doesn't resolve from inside the container (check
  `/etc/resolv.conf` if you have custom Docker networking).
- The IdP isn't reachable on the network (firewall / proxy).
- The IdP is down (check from your laptop too).

### Check 2 — `OIDC_AUTHORITY` shape

Must end with a **trailing slash**:

```env
OIDC_AUTHORITY=https://keycloak.example.com/realms/master/
```

(With the slash — without, some IdPs will return discovery doc,
others will 404, and shepard's behaviour depends on the IdP.)

### Check 3 — `OIDC_PUBLIC` matches current signing key

Keycloak: Realm settings → Keys → RS256 → Public key. Copy the
PEM body (no `BEGIN/END` lines) into `OIDC_PUBLIC`. Restart the
backend.

If the IdP just rotated keys, you'll see this until you update
`OIDC_PUBLIC`.

## File upload fails

**Symptom.** Uploading a file via the frontend returns
`413 Request Entity Too Large` or hangs.

### Check 1 — reverse-proxy body size

Nginx defaults to 1 MiB max body size. Bump it in your
`server { }` block:

```nginx
client_max_body_size 5G;
```

Caddy doesn't impose a default body-size limit; if you're using
Caddy, look elsewhere.

### Check 2 — Quarkus body size

```env
QUARKUS_HTTP_LIMITS_MAX_BODY_SIZE=5G
```

Restart the backend.

### Check 3 — MongoDB GridFS chunk size

Default chunk size (255 KiB) is fine for any reasonable file.
If you're uploading **millions of small files** and the index
on `fs.chunks` is becoming a bottleneck, see
[storage backends]({{ '/reference/deployment-storage/' | relative_url }})
— this is the kind of scale where S3-compatible storage
(post-FS1b) pays off.

### Check 4 — disk space

```bash
df -h /opt/shepard/mongodb/
```

GridFS silently degrades when the disk fills up — you'll get
write errors that look like timeouts. Add disk or prune old
data.

## Frontend stays blank

**Symptom.** Open the frontend; get a blank page or a
non-rendering shell.

### Check 1 — frontend container health

```bash
docker compose ps frontend
docker compose logs frontend
```

If the container is restarting, check for missing env vars —
`BACKEND_URL`, `OIDC_AUTHORITY`, `CLIENT_ID`,
`FRONTEND_AUTH_SECRET`.

### Check 2 — backend URL reachable from the browser

The frontend talks to `BACKEND_URL` from the browser. Open
DevTools → Network; the failed request will name the URL.

If you set `BACKEND_URL=http://backend:8080/`, the browser
can't reach that — `backend` resolves inside the Docker
network only. Use the **public** backend URL:

```env
BACKEND_URL=https://backend.shepard.example.com/
```

### Check 3 — CORS

```bash
docker compose logs backend | grep -i "CORS"
```

Tightened `QUARKUS_HTTP_CORS_ORIGINS` that doesn't include the
frontend's actual origin will reject every API call. Either
loosen to `*` for development or add the frontend's origin
explicitly.

## Backend slow under load

### Check 1 — permission cache hit ratio

```bash
curl -fsS http://localhost:8080/shepard/doc/metrics/prometheus \
  | grep -E "cache_gets_total\{cache=\"permissions-service-cache\""
```

Hit ratio < 50% → cache too small. Bump:

```env
SHEPARD_PERMISSIONS_CACHE_MAX_SIZE=50000
SHEPARD_PERMISSIONS_CACHE_TTL=PT15M
```

See [sizing]({{ '/reference/deployment-sizing/#sizing-the-jvm-heap' | relative_url }}).

### Check 2 — JVM heap pressure

```bash
curl -fsS http://localhost:8080/shepard/doc/metrics/prometheus \
  | grep -E "jvm_memory_(used|max)_bytes"
```

`used / max > 0.8` consistently → bump heap or scale
horizontally.

### Check 3 — slow query

Enable Cypher slow-query logging in Neo4j:

```env
NEO4J_dbms_logs_query_enabled=INFO
NEO4J_dbms_logs_query_threshold=1s
```

Slow query logs end up in `/data/logs/query.log` inside the
Neo4j container.

## n10s plugin not registered

**Symptom.** Backend startup logs:

```
N10sBootstrapHook: neosemantics (n10s) procedures not registered in Neo4j.
SemanticRepositoryType.INTERNAL will report unhealthy.
```

The Neo4j compose service is missing the `n10s` plugin. Check:

```yaml
# infrastructure/docker-compose.yml — neo4j service env
NEO4J_PLUGINS: '["n10s"]'
NEO4J_dbms_security_procedures_allowlist: 'n10s.*,...'
NEO4J_dbms_security_procedures_unrestricted: 'n10s.*,...'
```

Re-add if you removed them, restart Neo4j, restart the backend.
External SPARQL repositories continue to work even without n10s;
only `SemanticRepositoryType.INTERNAL` is gated.

## DataCite Fabrica mint failed

**Symptom.** `POST /v2/<kind>/<appId>/publish` returns
`503 publish.minter.not-installed` or
`5xx publish.minter.failed`.

### Check 1 — minter installed

```bash
shepard-admin plugins list | grep minter
```

If no `minter-*` plugin is `ENABLED`, install one — typically
`minter-local` (bundled) for self-hosted scenarios or
`minter-datacite` (drop-in JAR) for DOI minting.

### Check 2 — DataCite credentials

```bash
shepard-admin minter-datacite credentials show
```

Returns the stored Fabrica username (password is masked). If
unset, mint via:

```bash
shepard-admin minter-datacite credentials set \
  --username <Fabrica-user> --password <Fabrica-password>
```

### Check 3 — DataCite reachable

From inside the backend:

```bash
docker exec shepard-backend curl -fsS https://api.test.datacite.org/dois \
  -u "$DATACITE_USERNAME:$DATACITE_PASSWORD"
```

A `200 OK` confirms credentials work. A `401` means the
credentials are wrong; a `5xx` means DataCite's side is down
(check [status.datacite.org](https://status.datacite.org/)).

## Where to ask for help

If you're stuck after working through this page:

1. **GitHub issues** —
   [github.com/noheton/shepard/issues](https://github.com/noheton/shepard/issues).
   Search first; open a new issue with the **symptom + the
   diagnostic outputs** above + your shepard version.
2. **Upstream** —
   [gitlab.com/dlr-shepard/shepard](https://gitlab.com/dlr-shepard/shepard).
   If the issue is on the `/shepard/api/...` byte-frozen surface
   (i.e. it would reproduce on upstream too), the upstream
   maintainers are the right audience.

Include in your report:

- `docker compose ps` (which services are up).
- The first 200 lines of `docker compose logs backend`.
- `shepard-admin plugins list --output=json`.
- `shepard-admin migrations status`.
- The commit hash (`git rev-parse HEAD` from your shepard
  checkout) or the image tag you're running.
- Steps to reproduce.

## See also

- [Deployment front door]({{ '/reference/deployment/' | relative_url }})
- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [OIDC / authentication]({{ '/reference/deployment-oidc/#troubleshooting' | relative_url }})
- [Backup + restore]({{ '/reference/deployment-backup/' | relative_url }}) — restore is a "we had backups" fix recipe.
- [Monitoring + observability]({{ '/reference/deployment-monitoring/' | relative_url }}) — wire alerts on the failures above.
- [Plugins reference]({{ '/reference/plugins/#troubleshooting' | relative_url }})
- [Admin CLI reference]({{ '/reference/admin-cli/' | relative_url }})
- [`aidocs/34`](https://github.com/noheton/shepard/blob/main/aidocs/34-upstream-upgrade-path.md) — admin-facing upgrade ledger.
