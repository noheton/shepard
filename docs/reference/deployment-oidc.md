---
layout: default
title: OIDC / authentication (deployment reference)
permalink: /reference/deployment-oidc/
description: Production OIDC setup for shepard — Keycloak realm import, Helmholtz AAI, university SAML→OIDC bridges, GitHub OAuth, role mapping, JWT validation.
---

# OIDC / authentication

shepard requires an external **OIDC provider** that issues
**JWT access tokens** (RS256-signed). The backend validates the
token signature against a public key you configure
(`OIDC_PUBLIC`); the principal it extracts populates
`JWTPrincipal.name`, `.groups`, and (post-A0) `.roles`.

This page covers production OIDC setup. The mock OIDC for the
local demo is in
[deployment quickstart]({{ '/reference/deployment-quickstart/' | relative_url }})
(DX5a).

## Configuration shape

shepard reads OIDC settings from the backend container's
environment (consumed by `application.properties` via Quarkus's
MicroProfile Config):

| Variable | Required | Example | Effect |
|---|---|---|---|
| `OIDC_AUTHORITY` | yes | `https://keycloak.example.com/realms/shepard/` | Issuer URL **with trailing slash** |
| `OIDC_PUBLIC` | yes | `MIIBIjANBgk…` | RS256 public key (PEM body, no `BEGIN/END` lines) |
| `OIDC_ROLE` | optional | `shepard-user` | Realm role required for access; unset = any authenticated user |
| `CLIENT_ID` | yes | `shepard-frontend` | OIDC client id of the **frontend** (the backend validates token audience against this) |

Trailing slashes matter. `OIDC_AUTHORITY` without a trailing
slash will work in some IdPs and fail in others — set it.

## Keycloak (recommended)

Keycloak is the upstream-reference IdP. Both upstream
`dlr-shepard/shepard` and this fork are tested against it.

### 1. Mint a realm

If you don't have a realm yet, the developer realm export at
`infrastructure-local/keycloak_frontend-dev.json` is the
starting point. **Do not** use it in production — it has known
defaults; mint a fresh realm.

```bash
# Import the dev realm only for evaluation. For production, mint
# a fresh realm via the Keycloak admin console.
```

### 2. Register the frontend client

In the Keycloak admin console:

- **Clients → Create client**
  - Client type: `OpenID Connect`
  - Client ID: `shepard-frontend` (or whatever you'll set
    `CLIENT_ID` to)
  - Client authentication: `Off` (public client — the frontend
    is a SPA, no client secret)
- **Settings**
  - Valid redirect URIs: `https://shepard.example.com/*`
  - Web origins: `+` (mirrors valid redirect URIs)
  - Standard flow: enabled
  - Direct access grants: **disabled** (don't expose password
    grant)
- **Advanced**
  - Access Token Lifespan: 15 minutes (default) is fine; the
    frontend re-fetches via the refresh token.

### 3. Capture the signing key

- **Realm settings → Keys**
- Find the RS256 row → click `Public key`
- Copy the PEM body (one long line, no `BEGIN/END` markers)
- That's `OIDC_PUBLIC`.

When you rotate Keycloak's RS256 key, you must update
`OIDC_PUBLIC` and restart the backend. (The shepard backend
caches the key in memory; it does not yet support JWKS
auto-rotation. Tracked under `aidocs/22` F8.)

### 4. Mint users + roles

- **Users → Create user.** Create at least one human admin and
  one regular user for smoke testing.
- **Realm roles → Create role.**
  - `shepard-user` (or whatever you set `OIDC_ROLE` to) — every
    user who should access shepard gets this role.
  - `instance-admin` — the admin tier introduced by A0. Users
    with this role can hit `/v2/admin/*` endpoints once their
    JWT is in flight. (You can also map `instance-admin` to a
    Neo4j `:HAS_ROLE` edge — see "Dual-source role check"
    below.)
- **Users → <user> → Role mappings.** Assign the realm roles
  you minted.

### 5. Verify the chain

From the host shepard is running on:

```bash
# Discovery document loads
curl -fsS https://keycloak.example.com/realms/shepard/.well-known/openid-configuration | jq .

# Fetch a token (via password grant for testing only — turn it
# off in prod once you've verified)
TOKEN=$(curl -fsS -X POST \
  -d grant_type=password \
  -d client_id=shepard-frontend \
  -d username=test-user \
  -d password=test-pass \
  "https://keycloak.example.com/realms/shepard/protocol/openid-connect/token" \
  | jq -r .access_token)

# Probe the shepard backend
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "https://shepard.example.com/shepard/api/me" | jq .
```

A `200 OK` with a `User` JSON body confirms the OIDC chain is
working end-to-end. A `401 Invalid Authentication` means
`OIDC_PUBLIC` is wrong (or out of sync with what Keycloak signs
with).

## Helmholtz AAI

[Helmholtz AAI](https://login.helmholtz.de/) is the federated
research-IdP backbone for Helmholtz centres. It speaks OIDC,
proxies academic identities (eduGAIN), and issues JWTs that
shepard can consume directly.

### Registration

Helmholtz AAI clients are registered through
[the operator workflow](https://hifis.net/doc/aai/) — typically
a ticket to the HIFIS AAI team. Provide:

- Redirect URIs (`https://shepard.example.com/*`).
- The data-protection contact + use-case description.
- The grace period before going live (HIFIS sets a review
  window).

### Configuration

Once your client is approved:

```env
OIDC_AUTHORITY=https://login.helmholtz.de/oauth2/
OIDC_PUBLIC=<RS256 public key from https://login.helmholtz.de/oauth2-as/.well-known/jwks.json>
CLIENT_ID=<your-helmholtz-client-id>
OIDC_ROLE=  # leave unset; Helmholtz AAI gates access via membership claims
```

The JWKS endpoint surfaces multiple keys (Helmholtz AAI rotates
regularly). Today shepard accepts a single hand-pasted public key;
update on rotation. JWKS auto-rotation is the F8 follow-up under
`aidocs/22`.

### Group / role mapping

Helmholtz AAI exposes group memberships through the
`eduperson_entitlement` claim. Map your shepard `instance-admin`
role to a specific entitlement value:

```properties
shepard.security.oidc.roles-claim-path=eduperson_entitlement
shepard.security.oidc.roles-claim-mapping.instance-admin=urn:geant:helmholtz.de:group:shepard-admins#login.helmholtz.de
```

The dual-source check (A0) means you can **also** carry the role
on a Neo4j `:HAS_ROLE` edge — useful when the AAI claim isn't yet
configured but you've already bootstrapped a local admin.

## University SAML → OIDC bridge

If your institution speaks SAML2 only (Shibboleth, ADFS), the
bridge pattern is **Keycloak as a federation layer**:

```
University Shibboleth IdP ── SAML2 ──→  Keycloak Identity Brokering  ── OIDC ──→  shepard
```

Keycloak's
[identity-brokering feature](https://www.keycloak.org/docs/latest/server_admin/#_identity_broker)
brokers the SAML assertion into a local Keycloak user; the user
receives a Keycloak JWT (which shepard validates as if Keycloak
were the IdP).

This adds an extra hop but keeps shepard's OIDC contract
unchanged.

## GitHub OAuth

For a small lab without an institutional IdP, GitHub OAuth via
Keycloak's identity-brokering is a workable path:

- Register a GitHub OAuth App at
  `https://github.com/settings/developers`.
- In Keycloak: **Identity providers → Add provider → GitHub.**
- Map the GitHub `id` claim onto the Keycloak `username` field.

shepard then sees the Keycloak JWT as usual; the GitHub
identity is transparent past the Keycloak boundary.

This is **not** recommended for institutional / multi-group
deploys — every external GitHub login lands in your Keycloak
without an institutional review. Use it for small private
labs only.

## Role mapping in shepard

Once a user authenticates, shepard derives their **roles** from
two sources (A0 dual-source check):

1. **OIDC claim path.** Configurable via
   `shepard.security.oidc.roles-claim-path` (default:
   `realm_access.roles` — Keycloak's shape). The claim value is
   intersected with shepard's known roles (currently:
   `instance-admin`).
2. **Neo4j `:HAS_ROLE` edge.** A user node with
   `(:User)-[:HAS_ROLE]->(:Role {name: "instance-admin"})`
   carries the role even if the IdP doesn't.

The principal's effective roles is the **union** of both
sources, deduplicated. This means you can:

- Carry an admin in Neo4j without touching the IdP.
- Carry an admin in the IdP without touching shepard's data
  store.
- Or both, for belt-and-braces.

See `aidocs/51-instance-admin-role.md` for the design.

## Bootstrap-token mechanism (first admin)

shepard ships a **file-on-disk bootstrap token** so an operator
can mint the first admin API key without going through the UI
— useful when the IdP isn't yet wired or when you want to
script first-run setup.

On a fresh container:

```bash
# 1. The backend writes a one-shot token to disk on first start.
docker exec shepard-backend cat /opt/shepard/.bootstrap-token

# 2. Consume it (idempotent, single-use; backed by :BootstrapState
#    in Neo4j).
curl -fsS -X POST \
  -H "Authorization: Bearer <bootstrap-token-from-step-1>" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin", "roles":["instance-admin"]}' \
  https://shepard.example.com/v2/admin/bootstrap

# 3. The response carries a long-lived API key with the
#    instance-admin role. Save it; the bootstrap token is now
#    spent.

# 4. Delete the file so it can't be re-issued.
docker exec shepard-backend rm /opt/shepard/.bootstrap-token
```

Configure the file path via
`shepard.bootstrap.token-path` if `/opt/shepard/.bootstrap-token`
is inconvenient.

## Allowlist of admin-mintable roles

`POST /apikeys` lets a caller mint an API key with embedded
roles. The set of roles a caller is allowed to mint is restricted by
`shepard.apikey.role-allowlist` (default: `["instance-admin"]`).
An out-of-allowlist role in the mint body returns
`InvalidRequestException`; trying to mint a role you don't
yourself hold returns `InvalidAuthException`.

## Troubleshooting

**`Invalid token: JWT signature does not match locally computed
signature`.** `OIDC_PUBLIC` is wrong (or the IdP rotated its
key). Re-fetch the RS256 public key from the IdP's JWKS or admin
console.

**`User info could not be retrieved`.** The backend can't reach
`OIDC_AUTHORITY` from inside its container. Check Docker network
connectivity:

```bash
docker exec shepard-backend curl -fsS $OIDC_AUTHORITY/.well-known/openid-configuration
```

**`User is missing required role: <foo>`.** The user's JWT
doesn't carry `OIDC_ROLE`. Either change the user's role
assignment in the IdP or change `OIDC_ROLE` in `.env`.

**`@RolesAllowed("instance-admin")` returns 403.** The user
doesn't carry the `instance-admin` role in either source. Check:

```bash
# IdP side
curl -fsS -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.com/shepard/api/me | jq .roles

# Neo4j side
echo "MATCH (u:User {username: 'foo'})-[:HAS_ROLE]->(r) RETURN r.name" \
  | docker exec -i neo4j cypher-shell -u neo4j -p $NEO4J_PW
```

## See also

- [Pre-flight checklist]({{ '/reference/deployment-checklist/' | relative_url }})
- [Secrets management]({{ '/reference/deployment-secrets/' | relative_url }}) — the bootstrap-token file + credential cipher.
- [Admin CLI reference]({{ '/reference/admin-cli/' | relative_url }}) — `shepard-admin` operator commands.
- [Troubleshooting]({{ '/reference/deployment-troubleshooting/' | relative_url }})
- [`aidocs/51-instance-admin-role.md`](https://github.com/noheton/shepard/blob/main/aidocs/51-instance-admin-role.md) — dual-source role design.
- [`aidocs/22`](https://github.com/noheton/shepard/blob/main/aidocs/22-admin-cli-draft.md) §F8 — JWKS-rotation follow-up.
