---
layout: default
title: "Runbook — Rotate a user or service API key"
description: "Revoke an existing API key and issue a replacement without disrupting active callers more than one request window. Covers both user keys and service-account keys used by automated pipelines."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: operator-machine
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Rotate a user or service API key

> **When to use this runbook**: An API key has been exposed (leaked to a log, checked
> into a repository, included in a bug report), its bearer has left the project, or
> routine key-rotation policy requires a refresh. The goal is to issue a replacement
> key and revoke the old one with the smallest possible disruption window.

---

## Prerequisites

- An existing **instance-admin** API key (`${INSTANCE_ADMIN_API_KEY}`) or a JWT for
  an instance-admin user.
- The **username** whose key is being rotated (`${TARGET_USER}`).
- The Shepard API base URL (`${API_BASE}` — e.g. `https://shepard-api.nuclide.systems`).
- For service-account callers (MQTT collector, import scripts, CI pipelines): access
  to their configuration so you can swap in the new key without a service outage.
- `curl`, `jq` on the operator machine.

---

## Background — API key model

API keys are stored as `:ApiKey` nodes in Neo4j, linked to a `:User` node via
`[:HAS_API_KEY]`. Each key is a UUID v4 bearer token (stored as a SHA-256 hash in
Neo4j; the plain-text token is only returned once at creation time). Keys do not
expire automatically — revocation is the only lifecycle event after creation.

---

## Steps

### 0. Identify the key to rotate

List all active keys for the target user (instance-admin scope required):

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/users/${TARGET_USER}/apiKeys" \
  | jq '.[] | {id, createdAt, label}'
```

Expected: array of key descriptors. Note the `id` of the key to revoke.

```bash
export OLD_KEY_ID="<id from above>"
```

### 1. Issue a replacement key

```bash
# [operator-machine]
NEW_KEY_RESPONSE=$(curl -fsS -X POST \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"label": "rotation-'"$(date +%Y%m%d)"'"}' \
  "${API_BASE}/v2/users/${TARGET_USER}/apiKeys")

echo "${NEW_KEY_RESPONSE}" | jq .
NEW_KEY=$(echo "${NEW_KEY_RESPONSE}" | jq -r '.key')
NEW_KEY_ID=$(echo "${NEW_KEY_RESPONSE}" | jq -r '.id')

echo "New key ID:    ${NEW_KEY_ID}"
echo "New key value: ${NEW_KEY}"
```

**Copy the key value now** — it is shown exactly once. Store it in your secrets
manager (Vault, SOPS-encrypted `.env`, etc.) before proceeding.

### 2. Verify the new key works

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${NEW_KEY}" \
  "${API_BASE}/shepard/api/healthz/ready" \
  | jq '.status'
```

Expected: `"UP"`

```bash
curl -fsS \
  -H "X-API-KEY: ${NEW_KEY}" \
  "${API_BASE}/v2/users/me" \
  | jq '{username, roles}'
```

Expected: shows `${TARGET_USER}` with their roles.

### 3. Update callers with the new key

Before revoking the old key, update every system that uses it:

| Caller | Config location | Action |
|---|---|---|
| MQTT home collector | `/opt/shepard/examples/home-showcase/.env` | Set `SHEPARD_API_KEY=<new>` then `docker restart infrastructure-home-showcase-collector-1` |
| MFFD import script | `examples/mffd-showcase/seed.py` env / `.env` | Update `SHEPARD_API_KEY`; re-run dry-run to confirm auth |
| CI pipeline | GitHub Actions secrets | Replace `SHEPARD_API_KEY` secret in repo settings |
| `shepard-admin` CLI | shell env / `.env` | `export SHEPARD_ADMIN_API_KEY=<new>` |

For each caller, run a test request with the new key before revoking the old one.

### 4. Revoke the old key

Once all callers are confirmed-working with the new key:

```bash
# [operator-machine]
curl -fsS -X DELETE \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/users/${TARGET_USER}/apiKeys/${OLD_KEY_ID}"
echo "Exit code: $?"
```

Expected: HTTP 204 (no content); exit code 0.

### 5. Confirm the old key is rejected

```bash
# [operator-machine]
# Substitute the actual old key value here (from your secrets manager or rotation log)
OLD_KEY_VALUE="<the-revoked-key-value>"
HTTP_STATUS=$(curl -o /dev/null -s -w "%{http_code}" \
  -H "X-API-KEY: ${OLD_KEY_VALUE}" \
  "${API_BASE}/v2/users/me")
echo "HTTP status with revoked key: ${HTTP_STATUS}"
```

Expected: `401` — the old key is no longer accepted.

---

## Rollback

If the new key is lost before callers are updated:

1. Issue another replacement key (repeat step 1).
2. Do **not** delete the old key until a working replacement is confirmed.
3. If you already deleted the old key and the new key is lost: issue a new key
   and update all callers. The service will be disrupted until callers are updated —
   no recovery path for a lost key exists.

---

## End-state verification

```bash
# [operator-machine]
# List keys for the target user — old key ID should be absent
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/users/${TARGET_USER}/apiKeys" \
  | jq '[.[] | .id]'
```

Expected: array containing `${NEW_KEY_ID}` and NOT containing `${OLD_KEY_ID}`.

---

## Note on JWT vs API key callers

JWTs issued by the OIDC provider (Keycloak) expire according to the realm's
`accessTokenLifespan` setting (typically 5–15 minutes) — you do not need to
revoke them. If a user's *session* needs to be terminated immediately (e.g.
compromised OIDC credentials), revoke their session in the Keycloak admin console
(`/auth/admin/realms/<realm>/sessions`) instead of rotating their Shepard API key.

---

## Provenance

- Key model: `backend/src/main/java/de/dlr/shepard/auth/apikeys/` entity +
  `de.dlr.shepard.v2.auth.apikeys.resources.ApiKeyRest`.
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
