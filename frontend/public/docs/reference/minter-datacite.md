---
layout: default
title: DataCite DOI minter (reference)
permalink: /reference/minter-datacite/
---

# DataCite DOI minter plugin

The **DataCite minter plugin** (`shepard-plugin-minter-datacite`)
mints real DataCite DOIs against
[DataCite Fabrica test](https://api.test.datacite.org) (default)
or production
([api.datacite.org](https://api.datacite.org)) for shepard's
`POST /v2/{kind}/{appId}/publish` endpoint. KIP1d. Ships as a
drop-in JAR per the
[plugin-first architecture](/reference/plugins/).

Activated by `shepard.publish.minter=datacite` once credentials are
in place; until then the default
[`shepard-plugin-minter-local`](/reference/publish-and-pids/) keeps
producing local-instance PIDs.

## Getting started

Six steps from a fresh install to a real DOI minted against
DataCite Fabrica test:

1. **Acquire a Fabrica test account.** Sign up at
   [Fabrica](https://doi.test.datacite.org/sign-in) — DataCite
   issues a `repositoryId` (the HTTP Basic auth user, e.g.
   `DLR.SHEPARD`), a temporary prefix (typically `10.5072`), and a
   password. Save them.
2. **Configure shepard's plugin** with the CLI (or the matching
   REST PATCH — see [Admin REST endpoints](#admin-rest-endpoints)).
   Every flag is runtime-mutable; nothing here requires a restart.

   ```
   # Point at Fabrica test (the default, but explicit doesn't hurt).
   shepard-admin minters datacite set-api-url https://api.test.datacite.org

   # DataCite-allocated DOI prefix (10.5072 for Fabrica test).
   shepard-admin minters datacite set-prefix 10.5072

   # DataCite Member account login.
   shepard-admin minters datacite set-repository-id DLR.SHEPARD

   # Publisher name that shows up in every minted DOI's metadata.
   shepard-admin minters datacite set-publisher "DLR e.V."

   # Base URL prepended to /<kind>/<appId> when building the DOI's
   # url attribute. DataCite resolves the DOI to this.
   shepard-admin minters datacite set-landing-page-base https://shepard.example.dlr.de/v2

   # DataCite Member password. Prompts on the terminal; the
   # plaintext never appears on the command line.
   shepard-admin minters datacite set-password
   ```

3. **Probe the connection** — verifies your shepard instance can
   reach DataCite's API before you flip the master toggle:

   ```
   shepard-admin minters datacite test-connection
   ```

   Expect `reachable=true, statusCode=200, latencyMs=<small>`. On
   network failure the command exits 1 with a `detail` field
   describing the failure mode.

4. **Enable** the plugin's master toggle:

   ```
   shepard-admin minters datacite enable
   ```

5. **Switch shepard to use DataCite as the active minter** — edit
   `application.properties`:

   ```
   shepard.publish.minter=datacite
   ```

   Restart shepard. `shepard.publish.minter` is deploy-time-only
   per the CLAUDE.md "cluster identity / topology" exception —
   switching the active PID provider is a re-bootstrap decision
   (runtime flips would orphan in-flight publications).

6. **Mint a DOI**:

   ```
   curl -X POST -H "X-API-KEY: $SHEPARD_API_KEY" \
        https://shepard.example.dlr.de/v2/data-objects/<your-appId>/publish
   ```

   The response carries the freshly-minted DOI (`10.5072/abc-def-1`
   in Fabrica test; member-specific in production), plus a
   `versionNumber: 1`. The DOI lands in DataCite as a **draft**
   (the safe default — drafts can be deleted; promoted ones can't).
   Promote to `findable` either via the
   [Fabrica UI](https://doi.test.datacite.org/) or by flipping
   `defaultState=findable` and re-minting.

### Moving from Fabrica test to production

Fabrica production is a different DataCite Member contract — your
repositoryId, prefix, and password are all different. Re-run the
relevant `set-*` commands and point at production:

```
shepard-admin minters datacite set-api-url https://api.datacite.org
shepard-admin minters datacite set-prefix <your-prod-prefix>
shepard-admin minters datacite set-repository-id <your-prod-id>
shepard-admin minters datacite set-password   # different password
shepard-admin minters datacite test-connection
```

`shepard.publish.minter` stays `datacite`; no restart required for
the credential rotation. New mints land against production from
the next `POST .../publish`. Previously-minted Fabrica-test DOIs
are still resolvable via the
[KIP resolver](/reference/publish-and-pids/) (the resolver does an
opaque `findByPid` — the API base URL is irrelevant to it).

## The `:DataciteMinterConfig` singleton

The plugin's runtime state lives on a single
`:DataciteMinterConfig` Neo4j node — the
[admin-config-runtime idiom](/reference/plugins/) shared with
`:UnhideConfig`, `:SemanticConfig`, and `:FeatureToggleRegistry`.

| Field | Type | Description |
|---|---|---|
| `enabled` | `boolean` | Master toggle. When `false`, `mint()` throws `MinterException` immediately (no DataCite HTTP call). |
| `apiBaseUrl` | `string` | DataCite REST API base. Default `https://api.test.datacite.org`. |
| `handlePrefix` | `string` | DataCite-allocated DOI prefix (`10.5072` for Fabrica test; member-specific in production). |
| `repositoryId` | `string` | DataCite Member account login (HTTP Basic user). |
| `passwordCipher` | `string` | AES-GCM-encrypted password. Never read out via REST. |
| `passwordHash` | `string` | SHA-256 hex of the plaintext. Surfaced via its first-8-hex fingerprint only. |
| `publisher` | `string` | Publisher name embedded in every minted DOI's metadata. |
| `landingPageBase` | `string` | Base URL prepended to `/<kind>/<appId>` when building the DOI's `url` field. |
| `defaultState` | `string` | One of `draft` (default) / `registered` / `findable`. Maps to DataCite's `event` field on mint. |
| `updatedAt` | `Long` | Epoch millis of the most recent mutation. |
| `updatedBy` | `string` | Username of the operator who last patched / set credentials. |

The singleton is seeded on first start from the
`shepard.minters.datacite.*` install defaults in
`application.properties`. After first start, runtime PATCHes /
CLI commands win; the deploy-time keys stay valid as install
defaults (so an operator who IaC-pins their deployment still
gets the runtime row as the source of truth post-PATCH).

### Install-default config keys

```
# Master toggle. Default false: an operator must explicitly enable
# after configuring credentials + prefix.
shepard.minters.datacite.enabled=false

# DataCite REST API base. Test is the default.
shepard.minters.datacite.api-base-url=https://api.test.datacite.org

# Required for mint — empty default; operator MUST set on first run.
shepard.minters.datacite.handle-prefix=
shepard.minters.datacite.repository-id=

# Publisher name embedded in DOI metadata.
shepard.minters.datacite.publisher=

# Base URL for the DOI's url attribute.
shepard.minters.datacite.landing-page-base=

# DOI state on mint — draft / registered / findable.
shepard.minters.datacite.default-state=draft
```

**The password is NOT a deploy-time key.** Set it via
`POST /v2/admin/minters/datacite/credential` or
`shepard-admin minters datacite set-password` only. Gitleaks
would flag a plaintext password in `application.properties` as
a credential leak.

## Admin REST endpoints

All endpoints sit on the
[/v2/ shelf](/reference/api/#api-version-policy) under
`/v2/admin/minters/datacite/`. Every endpoint is
`@RolesAllowed("instance-admin")` — the credential-management
surface is one of the highest-trust admin paths.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/minters/datacite/config` | Return the masked singleton snapshot (passwordCipher + passwordHash are never serialised — only `passwordSet` boolean + 8-hex fingerprint). |
| `PATCH` | `/v2/admin/minters/datacite/config` | RFC 7396 merge-patch — flips runtime fields. Touching `passwordHash` / `passwordCipher` / `password` is sentinel-rejected with RFC 7807 `minters.datacite.config.read-only-field`. |
| `POST` | `/v2/admin/minters/datacite/credential` | Set or rotate the password. Body `{"password": "<plaintext>"}`; response carries only `{"passwordSet": true, "fingerprint": "<8-hex>"}`. |
| `DELETE` | `/v2/admin/minters/datacite/credential` | Clear the stored credential. Future mint calls throw `publish.minter.failed` until re-set. |
| `POST` | `/v2/admin/minters/datacite/test-connection` | Probe `<apiBaseUrl>/heartbeat` and report `reachable / statusCode / latencyMs / detail`. |

PROV1a's `ProvenanceCaptureFilter` captures every mutation as an
`:Activity` row. The filter records request method + path +
status only — the `POST /credential` body never enters the audit
trail. Auditing who flipped what when:

```
MATCH (a:Activity)
WHERE a.path STARTS WITH '/v2/admin/minters/datacite/'
RETURN a.actor, a.method, a.path, a.timestamp
ORDER BY a.timestamp DESC LIMIT 50
```

## CLI verbs

The full subcommand set under
`shepard-admin minters datacite <verb>`:

| Verb | Action |
|---|---|
| `status` | Print the current runtime config (table or `--output=json`). |
| `enable` | Flip `enabled=true`. Warns when `passwordSet=false` or `handlePrefix` is unset. |
| `disable` | Flip `enabled=false`. Future mint calls throw immediately. |
| `set-api-url <url>` | Set the DataCite REST API base. Empty arg falls back to test default. |
| `set-prefix <prefix>` | Set the DOI prefix (`10.5072` for Fabrica test). |
| `set-repository-id <id>` | Set the DataCite Member account login. |
| `set-publisher <name>` | Set the publisher name embedded in DOI metadata. |
| `set-landing-page-base <url>` | Set the DOI `url` attribute prefix. |
| `set-state <state>` | One of `draft` / `registered` / `findable`. |
| `set-password` | Set or rotate the password. Reads stdin / tty only — never on the command line. |
| `clear-password` | Wipe the stored credential. |
| `test-connection` | Probe DataCite's API; exits 0 on reachable, 1 on unreachable. |

Shared flags from the [L1 baseline](/reference/admin-cli/):
`--url`, `--api-key`, `--output={human,json}`, `--verbose`.

### Secret-handling discipline

- **`set-password` reads from the terminal** (`Console.readPassword`,
  echo off) when stdin is a tty; falls back to one line of stdin
  for CI piping (`echo $PWD | shepard-admin minters datacite set-password`).
- **The plaintext is never on the command line** — `ps`, shell
  history, and process listings can't leak it.
- **The plaintext is never logged** — only the first-8 hex of the
  SHA-256 fingerprint is.
- **The plaintext is never echoed in REST responses** — `POST
  /credential` returns `passwordSet: true` + the fingerprint only.
- **PROV1a captures method + path + status, not request bodies** —
  the `POST /credential` action lands in `:Activity` but the
  plaintext doesn't.

## Versioning chain

KIP1d respects KIP1h's versioned-PIDs Phase 1 surface:

- The plugin reads `MintRequest.versionNumber()` directly. The
  in-core `PublishService` computes it as
  `findLatestVersionNumber(entityAppId) + 1` over the entity's
  existing `:Publication` rows.
- The DataCite request body's `attributes.version` field is set
  to `"v<n>"` (the literal string `"v1"`, `"v2"`, …).
- When `n > 1`, the request adds a
  `relatedIdentifiers: [{ relationType: "IsNewVersionOf",
  relatedIdentifier: "<previous-DOI>", relatedIdentifierType:
  "DOI" }]` entry. The previous DOI is looked up via the
  in-core `PublicationDAO`.
- A back-fill `PUT /dois/<previous-DOI>` stamps the inverse
  `relationType: "HasVersion"` on the predecessor so DataCite
  Commons renders the chain both ways.
- Back-fill failure is logged but non-fatal — the new mint
  already succeeded. The chain is allowed to be temporarily
  one-directional; an operator can re-publish the previous
  version to retry the back-fill.

## Resource-type mapping

shepard's KIP `digitalObjectType` field (read from the entity
metadata bag) maps onto DataCite's `resourceTypeGeneral`
vocabulary:

| shepard `digitalObjectType` | DataCite `resourceTypeGeneral` |
|---|---|
| `collection`, `collections` | `Collection` |
| `software` | `Software` |
| `publication`, `text` | `Text` |
| `image` | `Image` |
| everything else (including missing) | `Dataset` (the safe default — shepard's most common payload kind is research data) |

A future slice will surface the mapping as an admin-runtime knob
when an operator demonstrates a need to override it
(`shepard-admin minters datacite set-resource-type-map`); KIP1d's
hard-coded mapping is the operator-friendly default.

## State mapping (`event` field)

shepard's `defaultState` → DataCite's `event` field on mint:

| shepard `defaultState` | DataCite `event` |
|---|---|
| `draft` (default) | `draft` |
| `registered` | `register` |
| `findable` | `publish` |

`draft` is the safest default: a misconfigured mint is a
recoverable DataCite-side delete rather than a permanent
registered DOI. Operators promote draft DOIs via the
[Fabrica UI](https://doi.datacite.org/) or by flipping
`defaultState=findable` for future mints.

## Network hardening

- **Connect timeout: 10s.** DataCite's TLS handshake from
  European compute can run several seconds on cold connect.
- **Per-request timeout: 30s.** DataCite occasionally takes
  longer than expected to register a new DOI on first call.
- **Retry policy: at-most-one retry on 5xx or network exception,
  with 1s backoff.** Second failure → `MinterException` →
  in-core `PublishService` maps to RFC 7807 `publish.minter.failed`
  (500). 4xx is final — DataCite's 4xx body already carries the
  operator-actionable error.

## Credential at rest

The plugin stores the DataCite Member password using **AES-GCM-256**:

- Key derivation: `SHA-256("shepard:KIP1d:datacite:" ‖ <shepard.instance.id>)`
  truncated to 32 bytes.
- Ciphertext format: `gcm1:` prefix + base64-url-no-padding of
  `IV(12 bytes) ‖ ciphertext(N bytes) ‖ tag(16 bytes)`.
- The prefix marker `gcm1:` exists so a future cipher upgrade can
  recognise legacy stored shapes.

**This is not a substitute for a real KMS.** The threat model is
"an attacker reads `:DataciteMinterConfig` in Neo4j" — the cipher
means they cannot trivially impersonate the shepard instance
against DataCite. An attacker who reads both Neo4j **and** the
JVM's `shepard.instance.id` recovers the plaintext, which matches
the "operator-managed secret" caveat documented here.

Rotating `shepard.instance.id` post-mint invalidates the stored
cipher (decryption fails loudly with `IllegalStateException`).
Operators who need to rotate the instance id must re-set the
password first.

## Errors

`POST /v2/{kind}/{appId}/publish` paths surface the following
RFC 7807 envelopes when the active minter is DataCite:

| `type` | Status | When |
|---|---|---|
| `publish.minter.failed` | 500 | The `DataciteMinter` threw `MinterException`. `detail` carries the operator-readable message — typically "DataCite mint failed: HTTP 4xx — <truncated body>" or "DataCite mint failed after retry: <network error>". |
| `publish.minter.not-installed` | 503 | KIP1h posture — no active minter. The plugin JAR is missing or `shepard.publish.minter` is unset / `=none`. |

The admin REST surface surfaces its own three problem types:
`minters.datacite.config.read-only-field` (400 — caller PATCHed
`passwordHash` / `passwordCipher` / `password`),
`minters.datacite.config.bad-state` (400 — invalid `defaultState`),
`minters.datacite.config.bad-request` (400 — empty credential body).

## Troubleshooting

**`shepard-admin minters datacite test-connection` reports
`reachable=false`.** Likely network / firewall — DataCite Fabrica
test is `api.test.datacite.org`; production is `api.datacite.org`.
Run `curl -v <apiBaseUrl>/heartbeat` from the shepard host to
diagnose. The `detail` field in the test-connection response
carries the JDK HTTP error message.

**Mint returns `publish.minter.failed` with `HTTP 401`.** Wrong
credentials — re-run `shepard-admin minters datacite set-password`,
double-check `repositoryId`, and verify the test/production URL
matches the contract.

**Mint returns `publish.minter.failed` with `HTTP 422` mentioning
`prefix`.** Wrong handle prefix — the prefix you set doesn't
belong to the DataCite Member account on `repositoryId`. The
Fabrica test default is `10.5072`; production prefixes are
member-specific. Run `shepard-admin minters datacite set-prefix
<correct-prefix>`.

**A DOI minted but the version chain is broken in DataCite
Commons.** The back-fill `HasVersion` PUT on the previous DOI
failed — check the shepard logs for `KIP1d: failed to back-fill
HasVersion on previous DOI <pid>`. The new mint already succeeded;
re-running the previous Publication's `POST .../publish?force=true`
re-attempts the back-fill on the next call.

**`shepard.publish.minter=datacite` but the publish endpoint
returns 503.** Either the plugin JAR isn't on the classpath
(check `ls /deployments/plugins/` for
`shepard-plugin-minter-datacite-*.jar`), or the
`DataciteMinter.isEnabled()` returns false because one of
`enabled` / `handlePrefix` / `repositoryId` / `passwordCipher`
/ `publisher` / `landingPageBase` is unset. Run
`shepard-admin minters datacite status` to see the per-field state.

## Migrations

| Version | What it adds | Idempotent? |
|---|---|---|
| `V33__Add_appId_constraint_DataciteMinterConfig.cypher` | KIP1d — uniqueness constraint on the `:DataciteMinterConfig.appId` property (singleton guarantee at the DB boundary). | Yes — `CREATE CONSTRAINT IF NOT EXISTS`. |

No rollback script ships — an admin who wants to undo can run
`DROP CONSTRAINT appId_unique_DataciteMinterConfig IF EXISTS` and
(if desired) `MATCH (n:DataciteMinterConfig) DETACH DELETE n`
before downgrading. The singleton's seed-on-first-start logic
recreates it on the next boot.

## Plugin shape (build-classpath)

Distribution follows the ADR-0023 drop-in JAR shape:
`shepard-plugin-minter-datacite-${revision}.jar` is baked into
`/deployments/plugins/` in the published backend image, and the
backend's `with-plugins` Maven profile declares the plugin as a
`<dependency>` so Quarkus's build-time CDI scanner picks up the
`@ApplicationScoped` beans + `@Path` resources.

Operators building their own image without `-DnoPlugins` get the
plugin for free; operators with `-DnoPlugins` get a backend with
the `local` minter only (plus the KIP resolver) and must drop the
plugin JAR into `/deployments/plugins/` at runtime to enable
DataCite. The `PluginRegistry` tracks the plugin under
`GET /v2/admin/plugins` regardless.

See the
[plugins reference](/reference/plugins/) for the full plugin
lifecycle + runtime-toggle (`shepard.plugins.minter-datacite.enabled`)
semantics.

## Design + status

- **`aidocs/16 §KIP1d`** — live status entry, shipped this slice.
- **`aidocs/34 §KIP1d`** — admin-upgrade ledger entry (AWARE + CONFIG;
  see "Operator notes" column for the end-to-end upgrade flow).
- **`aidocs/44`** — fork-vs-upstream feature matrix row (the
  DataCite minter is fork-exclusive — upstream shepard has no
  comparable surface).
- **`aidocs/66 §6`** — original design rationale for the KIP1
  DataCite slice.
- **`aidocs/63 ADR-0023`** — plugin distribution shape.
