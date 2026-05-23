---
layout: default
title: Authentication
description: OIDC + API-key model, role mapping, instance-admin role, audit trail.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin/auth/
---

# Authentication

shepard authenticates requests through two mechanisms, both first-class:

1. **OIDC** — Keycloak-flavoured by default; any standards-compliant
   provider works. Browser-driven sessions land here.
2. **API key** (`X-API-KEY` header) — long-lived per-user tokens for
   scripts, CLI access, and integrations. Minted from the user's
   `/me` profile.

Both arrive at the same `JWTFilter` and are mapped to the same `User`
graph entity; permissions are enforced uniformly.

## OIDC configuration

Three environment variables wire the backend to your IdP:

| Variable | Example | Effect |
|---|---|---|
| `OIDC_AUTHORITY` | `https://keycloak.example.com/realms/shepard` | Issuer URL — backend validates tokens against this JWKS endpoint |
| `OIDC_PUBLIC` | `shepard-frontend` | Public client id used by the Nuxt frontend |
| `OIDC_ROLE` | `shepard-user` | Realm-role required for access. Users without this role are rejected at the JWT filter, before they hit a resource |

The frontend reads the same three values plus an `OIDC_REDIRECT_URI`. For
a typical Keycloak deployment, see the developer-realm export at
`infrastructure-local/keycloak_frontend-dev.json` — it is the structural
template, not production-ready.

## Roles

Two role tiers exist:

- **`shepard-user`** (`OIDC_ROLE`) — required for any access. Mapped via the
  IdP's realm-roles claim.
- **`instance-admin`** — required for `/v2/admin/...` endpoints
  (`@RolesAllowed("instance-admin")`). Granted either via IdP realm-roles
  or via a `:User { instanceAdmin: true }` flag set by an existing admin.
  Bootstrap path: the very first user with `instance-admin` is granted
  manually via the `BootstrapService` Cypher seed; thereafter admins
  promote each other through the UI.

## API keys

Minted from `/me` in the frontend. Each key is a long-lived JWT bound
to the issuing user; revocation is per-key (the user can void any key
they minted). Per-key permissions are the union of the user's grants
at the moment of the request — no key-scoped delegation.

Use:

```bash
curl -fsS https://shepard.example.com/v2/collections \
     -H "X-API-KEY: <token>"
```

API keys also satisfy `instance-admin` if the issuing user holds that
role.

## Permission model

Permissions are stored in Neo4j as edges between `:User` (or `:UserGroup`)
and the target entity (`:Collection`, `:DataObject`, `:Container`, …).
Three levels:

- **Read** — list / get
- **Write** — list / get / create / update
- **Manager** — Read + Write + permission management

A `PermissionsCacheWarmer` keeps the per-request cost low; cache TTL is
tunable via `shepard.permissions.cache.ttl-seconds`.

## Audit trail

Mutations against `/v2/admin/...` endpoints land in `:Activity` nodes via
`ProvenanceCaptureFilter` (PROV1a, automatic — admin endpoints capture by
default). Query the audit trail through the existing
`/v2/data-objects/{id}/provenance` API or directly in Cypher:

```cypher
MATCH (a:Activity)
WHERE a.endpoint STARTS WITH "/v2/admin/"
  AND a.timestamp > datetime("2026-05-01")
RETURN a.endpoint, a.actor, a.timestamp
ORDER BY a.timestamp DESC
LIMIT 50;
```

`PermissionAuditService` exposes the same data via REST for the admin UI.

## See also

- [`backend/src/main/java/de/dlr/shepard/auth/`](https://github.com/noheton/shepard/tree/main/backend/src/main/java/de/dlr/shepard/auth) — `JWTFilter`, `PermissionsService`, `UserGroupService`
- [`aidocs/data/00-model-inventory.md`](https://github.com/noheton/shepard/blob/main/aidocs/data/00-model-inventory.md) — `:User`, `:UserGroup`, `:Permission` entity shapes
- [Configuration]({{ '/admin/config/' | relative_url }}) — runtime `:*Config` singletons (feature toggles, etc.)
