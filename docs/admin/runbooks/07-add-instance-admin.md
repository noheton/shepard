---
layout: default
title: "Runbook — Promote a user to instance-admin via the bootstrap token"
description: "Consume the one-shot bootstrap token to grant the instance-admin role to the first administrator. Covers token location, the POST /v2/admin/bootstrap call, and what to do if the token was already consumed."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Promote a user to instance-admin via the bootstrap token

> **When to use this runbook**: A fresh Shepard instance has no instance-admin yet
> (first install), or a subsequent user needs the `instance-admin` role and the
> existing instance-admin is granting it via the normal role-management endpoint.
> This runbook covers **first bootstrap** (consuming the one-shot token) and
> **role grant by an existing admin** (skipping the token path).

---

## Prerequisites

- The user to be promoted must have **logged in via OIDC at least once**. The
  `BootstrapService` looks up the user by username in Neo4j — if no user node
  exists, it returns 404 and the token is NOT consumed.
- For first-bootstrap: SSH access to the host to read the token file.
- `${API_BASE}` — the Shepard API base URL.
- `curl`, `jq`.

---

## Path A — First bootstrap (no existing instance-admin)

### A0. Read the bootstrap token

The token is a UUID written to a file at the path configured by
`SHEPARD_BOOTSTRAP_TOKEN_PATH` (override: `/opt/shepard-bootstrap/.bootstrap-token`
in the named Docker volume `bootstrap-token`; default fallback:
`/opt/shepard/.bootstrap-token`).

```bash
# [nuclide]
# The token file is inside the bootstrap-token named volume.
# Read it via a throwaway container that mounts the volume:
BOOTSTRAP_TOKEN=$(docker run --rm \
  -v bootstrap-token:/opt/shepard-bootstrap \
  busybox cat /opt/shepard-bootstrap/.bootstrap-token)

echo "Bootstrap token: ${BOOTSTRAP_TOKEN}"
```

If the file is empty or the command fails, the backend may not have started yet
(the token is written on the first backend startup). Check backend logs:

```bash
# [nuclide]
docker compose logs backend 2>&1 | grep -i bootstrap
```

Expected: `BootstrapTokenInitializer: bootstrap token written to ...`

### A1. Confirm the target user has a Neo4j node

```bash
# [nuclide]
export TARGET_USER="<username-from-OIDC>"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${TARGET_USER}'}) RETURN u.username, u.email;" \
  --format plain
```

Expected: one row. If no row is returned, the user has never logged in via OIDC —
have them log in first, then re-run this step.

### A2. Consume the token

```bash
# [nuclide or operator-machine]
curl -fsS -X POST \
  -H "Content-Type: application/json" \
  -d "{\"token\": \"${BOOTSTRAP_TOKEN}\", \"username\": \"${TARGET_USER}\"}" \
  "${API_BASE}/v2/admin/bootstrap" \
  | jq .
```

Expected response (HTTP 201):

```json
{
  "username": "<TARGET_USER>",
  "role": "instance-admin"
}
```

**The token is now consumed** — it cannot be reused. The `:BootstrapState` node
in Neo4j is deleted.

### A3. Verify the role assignment

```bash
# [operator-machine]
# Log in as the newly-promoted user and check their roles
curl -fsS \
  -H "X-API-KEY: ${NEW_ADMIN_API_KEY}" \
  "${API_BASE}/v2/users/me" \
  | jq '.roles'
```

Expected: `["instance-admin"]` (or an array that includes `"instance-admin"`).

---

## Path B — Grant instance-admin as an existing instance-admin

If an instance-admin already exists, you do not need the bootstrap token.
Use the role-management endpoint instead:

```bash
# [operator-machine]
curl -fsS -X POST \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"role": "instance-admin"}' \
  "${API_BASE}/v2/admin/users/${TARGET_USER}/roles" \
  | jq .
```

Expected: HTTP 201 with the updated user object or a confirmation payload.

---

## Token already consumed — recovery

If the token has been consumed (replay returns HTTP 403) and you need to bootstrap
again (e.g. the original admin left and revoked their own role):

The bootstrap token is **regenerated on every backend restart** — but only if
the `:BootstrapState` node does not exist in Neo4j. Since consuming the token
deletes the state node, a restart will generate a fresh token automatically.

However: if there is still at least one instance-admin in the system, a restart
will NOT regenerate the token (the system only bootstraps when no admin exists).
Check first:

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User)-[:HAS_ROLE]->(r:Role {name: 'instance-admin'})
   RETURN u.username, r.name;" \
  --format plain
```

If this returns rows, use Path B (existing admin grants the role) instead of
attempting to regenerate the bootstrap token.

If the system is genuinely adminless and you need to recover:

```bash
# [nuclide]
# 1. Restart the backend — BootstrapTokenInitializer will write a new token
#    because no BootstrapState node and no admin exists.
cd /opt/shepard/infrastructure
docker compose restart backend

# 2. Wait for startup, then read the new token (step A0 above)
# 3. Follow Path A from A1.
```

---

## Rollback

Role grants are not automatically reversible via this runbook. To revoke the
instance-admin role from a user that was mistakenly promoted:

```bash
# [operator-machine] — requires an existing instance-admin
curl -fsS -X DELETE \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/users/${TARGET_USER}/roles/instance-admin"
```

---

## End-state verification

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${NEW_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/features" \
  | jq 'length'
```

Expected: positive integer (the new admin can list features — instance-admin-only
endpoint). A 403 means the role was not granted correctly.

---

## Provenance

- `BootstrapRest.java`: `backend/src/main/java/de/dlr/shepard/v2/admin/resources/BootstrapRest.java`
- `BootstrapTokenInitializer.java`: `backend/src/main/java/de/dlr/shepard/auth/bootstrap/BootstrapTokenInitializer.java`
- `BootstrapService.java`: `backend/src/main/java/de/dlr/shepard/v2/admin/services/BootstrapService.java`
- Auth model: `docs/admin/auth.md` §Bootstrap.
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
