---
layout: default
title: "Runbook — Grant a role and refresh the user's session"
description: "Grant the `instance-admin` (or any `:Role`) edge via the v2 REST endpoint or direct Cypher, and instruct the affected user to sign out + back in so the dual-source role resolver picks up the new grant on the next JWT."
stage: feature-defined
last-stage-change: 2026-05-29
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Grant a role and refresh the user's session

> **When to use this runbook**: An existing instance-admin needs to grant the
> `instance-admin` role (or any other `:Role` node) to a user, and the user is
> currently signed in. Covers both grant paths (REST + direct Cypher) and
> explains the mandatory sign-out + sign-back-in step.
>
> **This is a workaround, not the structural fix.** The real fixes are tracked
> as `ROLE-GRANT-STALE-SESSION-02` (backend per-token rejection or Keycloak
> session forced-logout) and `ROLE-GRANT-STALE-SESSION-03` (frontend hint on
> 403 to "your session may be out of date") in
> [`aidocs/16-dispatcher-backlog.md`](../../../aidocs/16-dispatcher-backlog.md).
> Until those land, the human-in-the-loop sign-out is the operational gap.
> Surfaced 2026-05-29 during `J1e-PR-05-VERIFY-SSO`.

---

## Prerequisites

- An existing `instance-admin` (either to call the REST endpoint, or to run
  Cypher against the Neo4j container).
- The target user **must have logged in via OIDC at least once** — only then
  does a `:User` node exist for the `:HAS_ROLE` edge to attach to.
- You know the target user's `:User.username`. **This is the Keycloak `sub`
  UUID, not the user's display name.** This is where today's verify agent
  confused two `flo`s — see step 0.
- `${API_BASE}` — the Shepard API base URL.
- `${INSTANCE_ADMIN_API_KEY}` — an existing instance-admin's API key.
- `${NEO4J_PASSWORD}` — sourced from `/opt/shepard/infrastructure/.env`.
- `curl`, `jq`.

---

## Step 0 — Identify the right user (disambiguation)

`:User.username` is populated from the OIDC `sub` claim, which is a UUID, not
a human-readable username. Two different OIDC accounts can share a display
name (e.g. real `Florian Krebs` and the demo `Flo Researcher`); only the `sub`
is canonical.

Find the user by display name or email and read the `username` field
(the `sub`):

```bash
# [nuclide]
export DISPLAY_HINT="<part of firstName / lastName / email>"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User)
   WHERE u.email CONTAINS '${DISPLAY_HINT}'
      OR u.firstName CONTAINS '${DISPLAY_HINT}'
      OR u.lastName  CONTAINS '${DISPLAY_HINT}'
   RETURN u.username AS sub, u.email, u.firstName, u.lastName;" \
  --format plain
```

Expected: one or more rows. **If more than one row comes back, you have an
ambiguity — pick the row whose `email` / `firstName` / `lastName` matches
the intended user.** Copy the `sub` (a UUID) into the next step:

```bash
export TARGET_USER="<sub-UUID-from-above>"
```

> **Disambiguation incident, 2026-05-29.** Today's verify agent granted a
> role using the username "flo" without checking the `sub` — and granted it
> to the demo `Flo Researcher` (`sub ee4c010f-...`) instead of the real
> `Florian Krebs` (`sub 7eead942-...`). The user-visible names matched; the
> `sub` UUIDs did not. The demo realm user has since been renamed to
> `flodemo` per `ROLE-GRANT-DEMO-FLO-DISAMBIGUATE`, but the principle stands:
> grants are by `sub`, always.

---

## Path A — Grant via the v2 REST endpoint (preferred)

```bash
# [operator-machine]
curl -fsS -X POST \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"username\": \"${TARGET_USER}\"}" \
  "${API_BASE}/v2/admin/instance-admins" \
  | jq .
```

Expected: HTTP 201 / 200 with a confirmation payload.

Implementation: `InstanceAdminRest.grant()` at
`backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java`
(`@Path("/v2/admin/instance-admins")`, `@POST`). The handler resolves the
`:User` node by `username` and MERGEs `:HAS_ROLE` to the
`{name: 'instance-admin'}` `:Role` node.

For roles other than `instance-admin`, use the role-management endpoint
documented in
[`07-add-instance-admin.md` Path B](07-add-instance-admin.md#path-b--grant-instance-admin-as-an-existing-instance-admin)
(`POST /v2/admin/users/{TARGET_USER}/roles` with the role name in the body).

---

## Path B — Grant via direct Cypher (when REST isn't reachable)

If the backend is down or the API key is unavailable, write the edge directly:

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${TARGET_USER}'}),
         (r:Role {name: 'instance-admin'})
   MERGE (u)-[:HAS_ROLE]->(r);"
```

Expected: `0 rows`, no error. `MERGE` is idempotent.

If the command runs with no error but no edge is created, the most likely
cause is `${TARGET_USER}` not matching any `:User.username` — re-run step 0
to confirm the `sub` UUID is correct.

> **Cypher path skips the audit trail.** Path A writes a typed `:Activity`
> via the request-level `ProvenanceCaptureFilter`. Path B does not — the edge
> exists but no provenance edge records who granted it or when. Prefer Path
> A whenever the backend is reachable.

---

## Step 1 — **MANDATORY**: have the user sign out + back in

The grant edge now exists in Neo4j. The affected user's active session
**will not see it** until they obtain a new JWT.

**Why:** `JwtTokenAuthService.parseAndAuthenticate()` reads the role set
once per JWT parse, via `resolveDualSourceRoles(username, idpRoles)` at
`backend/src/main/java/de/dlr/shepard/auth/security/JwtTokenAuthService.java:273`.
That call combines the IdP-claim roles (read from the JWT itself, immutable
for the lifetime of the token) with `:HAS_ROLE` edges (read from Neo4j at
parse time). The combined set is then cached on the authenticated principal
for the lifetime of the request — but **the JWT is only re-parsed when the
client presents a new token**. Until the user re-authenticates, every
request they make carries the role set from the pre-grant moment.

Operator action: tell the user to **sign out, then sign back in**. On the
sign-back-in, Keycloak issues a fresh access token; the next request to
Shepard re-parses it, re-reads `:HAS_ROLE`, and the new grant is now in the
role set.

> Until `ROLE-GRANT-STALE-SESSION-02` lands a forced-logout (Keycloak admin
> REST) or a per-token timestamp gate, this human-in-the-loop step is the
> only mechanism. Don't skip it — the symptom is a 403 on an `@RolesAllowed`
> route that the user "should" have access to, which is hard to diagnose
> without this runbook.

---

## Rollback

To revoke the grant:

```bash
# [operator-machine] — REST path
curl -fsS -X DELETE \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/instance-admins/${TARGET_USER}"
```

```bash
# [nuclide] — Cypher path
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${TARGET_USER}'})-[e:HAS_ROLE]->(:Role {name: 'instance-admin'})
   DELETE e;"
```

After revocation, the same sign-out + sign-back-in applies: the user's
existing JWT still carries the role until the next parse.

---

## End-state verification

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${TARGET_USER}'})-[:HAS_ROLE]->(r:Role)
   RETURN u.username AS sub, u.email, collect(r.name) AS roles;" \
  --format plain
```

Expected: one row where `roles` contains `"instance-admin"`.

After the user signs back in, verify from their side:

```bash
# [operator-machine] — as the newly-granted user
curl -fsS \
  -H "X-API-KEY: ${NEW_ADMIN_API_KEY}" \
  "${API_BASE}/v2/users/me" \
  | jq '.roles'
```

Expected: array including `"instance-admin"`. A 403 on
`${API_BASE}/v2/admin/features` after sign-back-in means the JWT still
carries the stale set — confirm the user truly signed out (Keycloak
session ended) rather than just closing the tab.

---

## Provenance

- `InstanceAdminRest.java`: `backend/src/main/java/de/dlr/shepard/v2/admin/resources/InstanceAdminRest.java`
- `JwtTokenAuthService.resolveDualSourceRoles`:
  `backend/src/main/java/de/dlr/shepard/auth/security/JwtTokenAuthService.java:273`
- Related runbook: [`07-add-instance-admin.md`](07-add-instance-admin.md) (first-bootstrap path)
- Tracked: `ROLE-GRANT-STALE-SESSION-01` (this runbook), `-02` (structural fix),
  `-03` (frontend hint), `ROLE-GRANT-DEMO-FLO-DISAMBIGUATE` (demo realm rename)
  in [`aidocs/16-dispatcher-backlog.md`](../../../aidocs/16-dispatcher-backlog.md).
- Surfaced: `J1e-PR-05-VERIFY-SSO` post-deploy verify, 2026-05-29.
