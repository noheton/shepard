---
layout: default
title: "Runbook — Diagnose and repair lost collection permissions"
description: "A user reports 403 or 404 on a Collection (or child entity) they previously had access to. Diagnose via the permissions audit REST endpoint and Cypher, then repair the Neo4j permission edge."
stage: feature-defined
last-stage-change: 2026-05-26
audience: instance-admin
host: nuclide
tested: "— (procedure derived from codebase; not exercised end-to-end)"
---

# Diagnose and repair lost collection permissions

> **When to use this runbook**: A user reports they can no longer see or write to a
> Collection (or DataObject / Container within it) that they previously had access to.
> The API returns 403 Forbidden or 404 Not Found even though the entity exists in Neo4j.

---

## Background — permissions model

Shepard stores permissions as Neo4j relationships between a principal
(`:User` or `:UserGroup`) and the target entity:

```
(principal:User|UserGroup)-[:HAS_PERMISSIONS {level: "READ"|"WRITE"|"MANAGER"}]
  ->(entity:Collection|DataObject|...)
```

Access is checked by `PermissionsDAO` on every API call. A missing or wrong-level
edge causes 403/404. Common causes:
- User was removed from a UserGroup that held the permission.
- A cleanup cascade accidentally `DETACH DELETE`d a permissions edge.
- The entity was created via a direct-import path that skipped the permission bootstrap.
- The target entity's `deleted: true` flag was set incorrectly.

---

## Prerequisites

- An instance-admin API key (`${INSTANCE_ADMIN_API_KEY}`).
- `${API_BASE}` — the Shepard API base URL.
- `${NEO4J_PASSWORD}` available (from `/opt/shepard/infrastructure/.env`).
- The `appId` (UUID) or numeric `id` of the entity the user cannot access.
- SSH access to the nuclide host for direct Cypher commands.

---

## Steps

### 0. Reproduce the failure

```bash
# [operator-machine]
# Substitute the user's API key and the entity appId
curl -v \
  -H "X-API-KEY: ${AFFECTED_USER_API_KEY}" \
  "${API_BASE}/v2/collections/${COLLECTION_APP_ID}" \
  2>&1 | grep '< HTTP'
```

Expected (broken state): `403` or `404`.

Note the exact status code — 403 means entity exists but access denied; 404 usually
means `deleted: true` is set OR the entity doesn't exist for that user.

### 1. Confirm the entity exists in Neo4j

```bash
# [nuclide]
export COLLECTION_APP_ID="<uuid>"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (c:Collection {appId: '${COLLECTION_APP_ID}'})
   RETURN c.appId, c.name, c.deleted, labels(c);" \
  --format plain
```

If no row is returned: the entity does not exist — this is not a permissions issue.
Check if it was accidentally deleted and use the appropriate restore runbook.

If `deleted: true`: the entity was soft-deleted. Fix:

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (c:Collection {appId: '${COLLECTION_APP_ID}'})
   SET c.deleted = false, c.updatedAt = timestamp()
   RETURN c.appId, c.deleted;"
```

Then re-run step 0 to confirm the issue is resolved. If the user still gets 403,
continue to step 2.

### 2. Check existing permissions on the entity

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e {appId: '${COLLECTION_APP_ID}'})<-[r:HAS_PERMISSIONS]-(p)
   RETURN labels(p) AS principalType, p.username AS username, p.name AS groupName,
          r.level AS level;" \
  --format plain
```

If the output is empty: the entity has no permission edges at all. Go to step 4.

If the user is listed but with `level: READ` when `WRITE` is needed: go to step 3.

### 3. Check which UserGroups the affected user belongs to

```bash
# [nuclide]
export AFFECTED_USERNAME="<username>"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${AFFECTED_USERNAME}'})-[:MEMBER_OF]->(g:UserGroup)
   RETURN u.username, g.appId, g.name;" \
  --format plain
```

Cross-reference against the principals listed in step 2. If the user belongs to
a group that should have access but the group edge is missing, the user was removed
from the group or the group edge was deleted. Fix by re-adding to the group:

```bash
# [nuclide]
export USER_GROUP_APP_ID="<group-appId>"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${AFFECTED_USERNAME}'})
   MATCH (g:UserGroup {appId: '${USER_GROUP_APP_ID}'})
   MERGE (u)-[:MEMBER_OF]->(g)
   RETURN u.username, g.name;"
```

### 4. Grant permissions directly (targeted repair)

If the entity has no permissions or the user needs a direct grant:

```bash
# [nuclide]
# Choose level: READ, WRITE, or MANAGER
export LEVEL="WRITE"
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${AFFECTED_USERNAME}'})
   MATCH (e {appId: '${COLLECTION_APP_ID}'})
   MERGE (u)-[r:HAS_PERMISSIONS]->(e)
   SET r.level = '${LEVEL}', r.updatedAt = timestamp()
   RETURN u.username, type(r), r.level, e.appId;"
```

### 5. Verify via the REST API

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${AFFECTED_USER_API_KEY}" \
  "${API_BASE}/v2/collections/${COLLECTION_APP_ID}" \
  | jq '{appId, name, deleted}'
```

Expected: HTTP 200 with the collection data.

### 6. Verify via the permissions audit endpoint (if available)

```bash
# [operator-machine]
curl -fsS \
  -H "X-API-KEY: ${INSTANCE_ADMIN_API_KEY}" \
  "${API_BASE}/v2/admin/permissions/audit?entityAppId=${COLLECTION_APP_ID}" \
  | jq .
```

Expected: the response includes the affected user with the granted level.

---

## Bulk repair — entity created without permissions

If a bulk import created entities with no permissions edges (reported as universal
404s across all users), repair all affected entities:

```bash
# [nuclide]
# Find all Collections with no HAS_PERMISSIONS edges
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (c:Collection)
   WHERE NOT (c)<-[:HAS_PERMISSIONS]-()
   RETURN c.appId, c.name LIMIT 20;" \
  --format plain
```

For each, decide the correct owner and grant MANAGER-level access (or PUBLIC read):

```bash
# [nuclide]
# Grant PUBLIC read to all orphaned collections
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (c:Collection)
   WHERE NOT (c)<-[:HAS_PERMISSIONS]-()
   MERGE (p:Permissions {ownerType: 'PUBLIC'})
   MERGE (c)<-[:HAS_PERMISSIONS {level: 'READ'}]-(p)
   RETURN count(c) AS repaired;"
```

---

## Rollback

Permission additions can be reversed:

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (u:User {username: '${AFFECTED_USERNAME}'})-[r:HAS_PERMISSIONS]->(e {appId: '${COLLECTION_APP_ID}'})
   DELETE r
   RETURN count(r) AS deleted;"
```

---

## End-state verification

```bash
# [operator-machine]
# Affected user can access the collection
curl -fsS \
  -H "X-API-KEY: ${AFFECTED_USER_API_KEY}" \
  "${API_BASE}/v2/collections/${COLLECTION_APP_ID}" \
  | jq '.appId'

# Instance admin sees permissions in Cypher
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (e {appId: '${COLLECTION_APP_ID}'})<-[r:HAS_PERMISSIONS]-(p)
   RETURN labels(p), p.username, r.level;" \
  --format plain
```

---

## Provenance

- Auth model: `docs/admin/auth.md` §Permissions.
- PermissionsDAO: `backend/src/main/java/de/dlr/shepard/auth/permission/`.
- DB audit learnings: `project_db_audit_learnings_2026-05-24.md` (cleanup cascade
  that triggered permissions loss).
- Tracked: `ADMIN-RUNBOOKS-LIBRARY` in `aidocs/16-dispatcher-backlog.md`.
