---
title: minter-datacite — Install
stage: deployed
last-stage-change: 2026-05-23
audience: plugin-author
synthetic_batch: true
generation_rule: feedback_no_synthetic_provenance.md
---

> 🤖 **BACKFILL — created retroactively 2026-05-23 by Claude Opus 4.7**
> per the docs-gap audit at `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
> The plugin's behaviour is documented from the source code as it stood
> at commit `8bdc8c6163ee4ea88acde244a1c7e9672ab593a3`. If anything is
> inaccurate, the source is authoritative; please open a PR or issue.

# minter-datacite — install

`shepard-plugin-minter-datacite` mints real DataCite DOIs against
DataCite Fabrica test (default) or production. The plugin is
**bundled but inactive** until an operator configures Fabrica
credentials and flips `shepard.publish.minter=datacite`.

For the casual operator walkthrough see
[`quickstart.md`](quickstart.md); this page is the durable
install runbook.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven
  profile (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-minter-datacite-${revision}.jar`.
- A DataCite Member account. Sign up at
  [Fabrica test](https://doi.test.datacite.org/sign-in) for
  testing; production requires a paid Member contract.
- `shepard-admin` CLI configured against the instance (per the
  [Admin CLI reference](../../../docs/reference/admin-cli.md)).
- A stable, publicly resolvable URL for the shepard instance —
  DataCite resolves DOIs to the `url` field, which must be
  reachable from the open internet.

---

## Install-default config keys

Seven `application.properties` keys seed the
`:DataciteMinterConfig` Neo4j singleton on first start. After
that, runtime PATCHes / CLI commands win.

| Key | Default | Purpose |
|---|---|---|
| `shepard.publish.minter` | `local` | **Selects the active minter.** Set to `datacite` to route every `POST .../publish` through this plugin. Deploy-time-only (cluster-topology exception). |
| `shepard.minters.datacite.enabled` | `false` | Seeds the master toggle. Default `false` — operator must enable after configuring. |
| `shepard.minters.datacite.api-base-url` | `https://api.test.datacite.org` | Fabrica test by default; production is `https://api.datacite.org`. |
| `shepard.minters.datacite.handle-prefix` | (empty) | DataCite-allocated DOI prefix (`10.5072` for Fabrica test; member-specific in production). |
| `shepard.minters.datacite.repository-id` | (empty) | DataCite Member account login (HTTP Basic user). |
| `shepard.minters.datacite.publisher` | (empty) | Publisher name embedded in every minted DOI's metadata. |
| `shepard.minters.datacite.landing-page-base` | (empty) | Base URL prepended to `/<kind>/<appId>` when building the DOI's `url` field. |
| `shepard.minters.datacite.default-state` | `draft` | One of `draft` / `registered` / `findable`. Maps to DataCite's `event` field. |
| `shepard.plugins.minter-datacite.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |

**The password is NOT a deploy-time key.** Set it via
`POST /v2/admin/minters/datacite/credential` or `shepard-admin
minters datacite set-password` only — gitleaks would flag a
plaintext password in `application.properties` as a credential
leak.

---

## CLI install flow

```bash
# 1. Point at Fabrica test (default; explicit is OK).
shepard-admin minters datacite set-api-url https://api.test.datacite.org

# 2. DataCite-allocated prefix (10.5072 = Fabrica test default).
shepard-admin minters datacite set-prefix 10.5072

# 3. DataCite Member login.
shepard-admin minters datacite set-repository-id DLR.SHEPARD

# 4. Publisher name (in every DOI's metadata).
shepard-admin minters datacite set-publisher "DLR e.V."

# 5. Landing-page base — DataCite resolves the DOI to <base>/<kind>/<appId>.
shepard-admin minters datacite set-landing-page-base \
  https://shepard.example.dlr.de/v2

# 6. Set the password (prompts via tty; not on the command line).
shepard-admin minters datacite set-password

# 7. Connection probe — sanity-check before enabling.
shepard-admin minters datacite test-connection

# 8. Flip the master toggle.
shepard-admin minters datacite enable

# 9. Activate as the default minter (deploy-time-only key).
#    Add to application.properties:
echo "shepard.publish.minter=datacite" >> application.properties

# 10. Restart shepard.
```

After restart, every `POST /v2/{kind}/{appId}/publish` mints a
real DOI.

---

## Healthcheck

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems

# Plugin registry.
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "minter-datacite")'

# Live config snapshot.
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/minters/datacite/config" | jq .

# Probe DataCite.
curl -X POST -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/minters/datacite/test-connection"
```

`test-connection` returns
`{"reachable": true, "statusCode": 200, "latencyMs": ...}` when
DataCite's API is reachable.

---

## Credential at rest

Password storage uses AES-GCM-256 keyed off `shepard.instance.id`:

- Key: `SHA-256("shepard:KIP1d:datacite:" ‖ shepard.instance.id)`
  truncated to 32 bytes.
- Format: `gcm1:` prefix + base64-url-no-padding of
  `IV(12) ‖ ciphertext ‖ tag(16)`.

**Rotating `shepard.instance.id` post-mint invalidates the stored
cipher.** Decryption fails loudly with `IllegalStateException`;
re-set the password first.

This is **not a substitute for a real KMS** — the threat model
is "an attacker reads `:DataciteMinterConfig` in Neo4j". An
attacker who also reads the running JVM's `shepard.instance.id`
recovers the plaintext.

---

## Moving from Fabrica test to production

```bash
shepard-admin minters datacite set-api-url https://api.datacite.org
shepard-admin minters datacite set-prefix <your-prod-prefix>
shepard-admin minters datacite set-repository-id <your-prod-id>
shepard-admin minters datacite set-password   # different password
shepard-admin minters datacite test-connection
```

No restart required — credentials are runtime-mutable. Existing
Fabrica-test DOIs remain resolvable through the KIP resolver
(it does opaque PID lookups; the API base URL is irrelevant).

---

## Disabling the plugin

Runtime soft-disable (preferred):

```bash
shepard-admin minters datacite disable
```

Future mint calls throw `MinterException` immediately — no DataCite
HTTP call fires.

Hard disable:

```properties
shepard.publish.minter=local            # or any other minter
shepard.plugins.minter-datacite.enabled=false
```

Restart. The plugin's lifecycle hook stays disabled; DataCite
calls are unreachable from the publish path.

---

## Known pitfalls

- **`shepard.publish.minter=datacite` flip with no credentials.**
  Every `POST .../publish` returns 503 `publish.minter.not-installed`.
  Set credentials first.
- **Production DOIs minted as `findable` by accident.** `findable`
  DOIs cannot be deleted from DataCite. Default `defaultState=draft`
  is the safe shape. Promote drafts via Fabrica UI when ready.
- **Wrong prefix vs. repository-id.** 422 on mint with
  `prefix not allowed`. Re-run `set-prefix` with the value
  DataCite allocated for that Member account.
- **Network timeouts**. Connect timeout is 10s; per-request 30s.
  European compute may need warm DataCite connections — keep
  the JVM warm in production.
- **Password rotation on long-running deployments**. Use
  `set-password` to rotate without redeploying the backend; the
  cipher updates on the next call.

---

## Migration

V33 (`V33__Add_appId_constraint_DataciteMinterConfig.cypher`)
runs automatically on backend startup via `MigrationsRunner`. It
adds a unique constraint on `:DataciteMinterConfig.appId`. The
migration is **idempotent**.

No rollback script ships. To undo:

```cypher
DROP CONSTRAINT appId_unique_DataciteMinterConfig IF EXISTS;
MATCH (n:DataciteMinterConfig) DETACH DELETE n;
```

The singleton's seed-on-first-start logic recreates it on next
boot.

---

## See also

- [`reference.md`](reference.md) — comprehensive plugin reference.
- [`quickstart.md`](quickstart.md) — operator walkthrough.
- `aidocs/integrations/66-publish-and-pids.md §6` — KIP1d design.
- [DataCite Fabrica](https://doi.test.datacite.org/) — test sandbox.
- [DataCite REST API docs](https://support.datacite.org/docs/api).
