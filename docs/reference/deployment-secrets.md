---
layout: default
title: Secrets management (deployment reference)
permalink: /reference/deployment-secrets/
description: How shepard stores credentials — CredentialCipher per-instance static key (KIP1d), bootstrap-token mechanism (A0), and the Vault / KMS roadmap.
---

# Secrets management

shepard stores **credentials** in two distinct paths:

1. **Operator-side secrets** — DB passwords, OIDC public keys,
   reverse-proxy TLS material. These live in the
   `infrastructure/.env` file (or your secret-injection
   mechanism), not in shepard's data store.
2. **Plugin-side credentials** — DataCite Fabrica username +
   password (KIP1d), ePIC password, git host PATs (G1c), Unhide
   harvest keys (UH1a). These are stored **inside shepard** so
   the relevant plugin can use them; the storage path goes
   through the `CredentialCipher`.

This page covers both. The bootstrap-token mechanism (the
file-on-disk one-shot for the first admin) is documented in
[OIDC setup]({{ '/reference/deployment-oidc/' | relative_url }})
§"Bootstrap-token mechanism".

## Operator-side secrets in `.env`

The shipped `infrastructure/.env.example` carries placeholder
values for every secret. **Every placeholder must be rotated**
before the host accepts any data — they're already in the
public git history (see
[`aidocs/07`](https://github.com/noheton/shepard/blob/main/aidocs/07-security-issues.md)
H8).

Generate fresh per-host secrets:

```bash
# 32-byte random base64 strings, suitable for passwords +
# frontend session secret
openssl rand -base64 32     # NEO4J_PW
openssl rand -base64 32     # MONGO_ROOT_PASSWORD
openssl rand -base64 32     # MONGO_PASSWORD
openssl rand -base64 32     # POSTGRES_PASSWORD
openssl rand -base64 32     # POSTGRES_SHEPARD_USER_PW
openssl rand -base64 32     # FRONTEND_AUTH_SECRET
openssl rand -base64 32     # GRAFANA_ADMIN_PASSWORD
openssl rand -base64 32     # HSDS_PASSWORD (if you run the hdf profile)
```

### `.env` permissions

The `.env` file holds production credentials. Set permissions
to deny world / group reads:

```bash
chmod 600 infrastructure/.env
chown root:root infrastructure/.env
```

If you use a secret-injection sidecar (Vault Agent, sops,
SealedSecrets in Kubernetes), the agent injects values into the
container's environment without leaving a plaintext file on
disk.

### Rotating `.env` secrets

Most secrets are rotatable without data loss:

- **Database passwords** — rotate inside the database
  (`ALTER USER … WITH PASSWORD …`), then update `.env`, then
  restart the backend. The application doesn't store the DB
  password persistently; it reads it from env at startup.
- **`FRONTEND_AUTH_SECRET`** — rotating this **invalidates all
  active frontend sessions** (users get redirected to log in
  again). Rotate during a maintenance window.
- **`OIDC_PUBLIC`** — rotate when the IdP rotates its signing
  key. Stale `OIDC_PUBLIC` = users can't log in.

## Plugin-side credentials via `CredentialCipher`

Plugins that talk to **external authenticated services**
(DataCite Fabrica, ePIC, GitHub PATs, Unhide harvest keys) need
to store their credentials inside shepard. Plaintext storage
isn't acceptable; the
**`CredentialCipher`** is shepard's encryption-at-rest path.

### Current shape (KIP1d-era)

`CredentialCipher` is an `@ApplicationScoped` CDI bean that
encrypts / decrypts credential blobs using **AES-GCM** with a
**per-instance static key**.

The key is derived from a long-lived secret on the shepard
host — today, the same `~/.shepard/keys/` directory the API-key
signing keys live under. (M2 — the `0600` permissions hardening
— applies.)

#### Where it's used

- **`shepard-plugin-minter-datacite`** (KIP1d) — DataCite
  Fabrica username + password stored on `:DataCiteCredential`
  Neo4j nodes; written via the `CredentialCipher`, read back at
  mint time.
- **`shepard-plugin-unhide`** (UH1a) — harvest API keys minted
  via `POST /v2/admin/unhide/harvest-keys`; encrypted with
  `CredentialCipher`; plaintext returned exactly once at mint
  (only the SHA-256 fingerprint is retained).
- **G1c (queued)** — git host PATs encrypted at rest per the
  `aidocs/36 §3.3` design.

#### Operator behaviour

The key file lives at `~/.shepard/keys/secrets.key` inside the
backend container. The container's home directory persists
across restarts via the `/opt/shepard/backend/config` volume —
**back up that volume** along with your DBs.

**Losing the key file means losing every credential stored
through `CredentialCipher`.** The DataCite plugin will fail
mints; the Unhide harvest keys become unreadable. There is
**no recovery path** — you re-provision the affected
credentials.

#### Key rotation (manual today)

Today there's no `CredentialCipher` rotation command. The
manual path:

1. Read each affected credential back (via the plugin's
   admin endpoint — e.g. `GET /v2/admin/minter-datacite/credentials`
   returns the decrypted blob to an `instance-admin`).
2. Stop the backend.
3. Replace `secrets.key`.
4. Start the backend.
5. Re-issue each credential through the plugin's admin
   endpoint.

This is **clunky** and the team knows it; a rotation runbook
(`shepard-admin secrets rotate`) is queued under the same
follow-up that ships Vault / KMS integration.

## Bootstrap token (first admin)

The bootstrap token is a **one-shot file-on-disk credential** for
minting the first `instance-admin` API key without going through
the IdP. See
[OIDC setup §"Bootstrap-token mechanism"]({{ '/reference/deployment-oidc/#bootstrap-token-mechanism-first-admin' | relative_url }})
for the operator recipe.

Key properties:

- The file lives at `/opt/shepard/.bootstrap-token` (mode 0600,
  owned by the backend process user). Path overridable via
  `shepard.bootstrap.token-path`.
- Single-use, replay-protected via a `:BootstrapState` Neo4j flag
  node + the token hash.
- **Delete the file after use** — there's no reason to keep it,
  and a file you don't delete is a credential someone else can
  find.

## API keys + roles

API keys are shepard's primary machine-to-machine credential.
Mint via `POST /apikeys`:

```bash
curl -fsS -X POST \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"name":"ci-key", "validUntil": "2026-12-31T23:59:59Z", "roles": ["instance-admin"]}' \
  https://shepard.example.com/shepard/api/apikeys
```

Returns the minted key in the response — **store it
immediately**; the backend keeps only the SHA-256 fingerprint
post-mint and there's no recovery path.

L5 introduced `validUntil`. JWTs minted on this branch carry an
`exp` claim; on expiry the validator returns a distinguishable
401 (`InvalidAuthException` with `apikey.expired` reason). Keys
minted **before** L5 have no `exp` and remain valid (the
validator treats them as non-expiring).

`shepard.apikey.role-allowlist` (default `["instance-admin"]`)
restricts which roles a caller can mint into the new key. Trying
to mint a role outside the allowlist returns
`InvalidRequestException`; trying to mint a role the caller
themselves doesn't hold returns `InvalidAuthException`.

## Roadmap: Vault + KMS integration

The current `CredentialCipher` shape is "good enough for
self-hosted single-host shepard." Multi-host clusters,
compliance-driven deployments, and cloud-native deploys want
managed-secret integration:

| Path | Status | Notes |
|---|---|---|
| **HashiCorp Vault Transit** | queued | `CredentialCipher` becomes a thin adapter delegating crypto to Vault's `transit/encrypt` + `transit/decrypt` endpoints. Vault Agent injects the token into the backend container. |
| **AWS KMS** | queued | Same shape, KMS as the backend. Envelope encryption (KMS-wrapped data key cached for ~1 hour). |
| **Azure Key Vault** | queued | Same shape. |
| **GCP KMS** | queued | Same shape. |

The SPI to add a `KmsBackend` interface lives queued under
`aidocs/47 §2` (plugin-first heuristic — adapters in plugins,
SPI in core). Until it ships, the per-instance static key is the
only path.

## Operator checklist

- [ ] `.env` placeholders rotated; `chmod 600` on the file.
- [ ] `~/.shepard/keys/` directory persisted across restarts via
      a Docker volume.
- [ ] `~/.shepard/keys/` included in your backup schedule (see
      [backup + restore]({{ '/reference/deployment-backup/' | relative_url }})).
- [ ] Bootstrap-token file deleted after first admin mint.
- [ ] At least one human admin minted with `instance-admin`
      role (so you're not locked out if the bootstrap path fails).
- [ ] Plugin credentials minted via the plugin's admin endpoint
      (not by direct Neo4j writes — that bypasses
      `CredentialCipher`).
- [ ] API keys minted with `validUntil` set to a reasonable
      horizon (e.g. one year for CI keys, 90 days for individual
      humans).
- [ ] Operator runbook documents the **what-if** for key loss
      (the operator on-call knows the recovery path is
      "re-provision each affected credential").

## Security posture

- **No DB password in code.** Every secret reads from
  environment / file at startup.
- **API keys hashed at rest.** SHA-256 fingerprints in Neo4j;
  plaintext returned once at mint.
- **JWT signature validation** is centralised
  (`JWTFilter`); the auth-header echo is `present`/`absent`
  only post-M5 to avoid token-leak via log scraping.
- **Cypher injection** closed across the DAO surface (C5 + C5b).
- **Path traversal** in `PublicEndpointRegistry` closed (H5).

For the full security-issues ledger, see
[`aidocs/07`](https://github.com/noheton/shepard/blob/main/aidocs/07-security-issues.md).

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [OIDC / authentication]({{ '/reference/deployment-oidc/' | relative_url }}) — bootstrap-token mechanism, role allowlist.
- [Admin CLI reference]({{ '/reference/admin-cli/' | relative_url }})
- [Plugins reference]({{ '/reference/plugins/' | relative_url }}) — JAR signing.
- [Publish + PIDs reference]({{ '/reference/publish-and-pids/' | relative_url }}) — KIP1d's credential-flow as the worked example.
- [`aidocs/07`](https://github.com/noheton/shepard/blob/main/aidocs/07-security-issues.md) — security-issues ledger.
- [`aidocs/51`](https://github.com/noheton/shepard/blob/main/aidocs/51-instance-admin-role.md) — instance-admin role + bootstrap-token design.
