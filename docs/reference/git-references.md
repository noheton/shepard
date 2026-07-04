---
title: Git references — reference
audience: user
---

# Git references reference

**Feature ID:** G1 (G1a–G1d)  
**Design doc:** `aidocs/workflows/38-git-integration-design.md`  
**API surface:** `/v2/` (this fork's development surface; upstream `/shepard/api/...` untouched)  
**Plugin:** `shepard-plugin-git` (enabled by default; `shepard.plugins.git.enabled=true`)

---

## Overview

A **GitReference** links a DataObject to a Git repository — a repository URL,
an optional ref (branch/tag/SHA), and an optional path. Three modes offer
increasing degrees of tracking:

| Mode | Behaviour |
|------|-----------|
| `LOOSE_LINK` | Default. Rendered as a clickable URL. No PAT required; no upstream check performed. |
| `TRACKED_ARTIFACT` | Server-side inline preview via `GET …/preview`. Uses the caller's stored PAT for private repos. Resolves the upstream commit SHA on each preview fetch (cached PT5M per user × repo × ref × path). |
| `PINNED_SNAPSHOT` | Resolves `ref` to a commit SHA at create/PATCH time; SHA is frozen thereafter (read-only). Exported as `schema:SoftwareSourceCode` with the SHA permalink in RO-Crate / Regulatory Evidence Pack exports. |

All three modes are stored under the same `:GitReference` Neo4j node. CRUD lives on
the unified `/v2/references?kind=git` surface. The preview and check-update operations
have their own paths because they are write-side-effecting or binary in nature.

---

## Entity fields

| Field | Type | Writable | Description |
|-------|------|----------|-------------|
| `appId` | UUID v7 | — (read-only) | Stable identifier. |
| `mode` | `LOOSE_LINK` \| `TRACKED_ARTIFACT` \| `PINNED_SNAPSHOT` | ✓ (create; read-only after PINNED_SNAPSHOT resolves) | Use-mode. Default: `LOOSE_LINK`. |
| `repoUrl` | string | ✓ | Full repository URL, e.g. `https://gitlab.dlr.de/group/repo`. |
| `ref` | string? | ✓ | Branch name, tag, or commit SHA. `null` = repository default branch. |
| `path` | string? | ✓ | Path within the repository. `null` = repository root. |
| `sha` | string? | — (read-only) | Resolved commit SHA (PINNED_SNAPSHOT mode). `null` in other modes. |
| `resolvedSha` | string? | — (read-only) | SHA of the commit `ref` resolved to at last preview fetch (TRACKED_ARTIFACT). `null` in other modes. |
| `resolvedAtMillis` | number? | — (read-only) | Epoch-ms timestamp when `resolvedSha` was last captured. `null` in LOOSE_LINK. |

On the wire each GitReference arrives inside a `ReferenceV2IO` envelope:

```json
{
  "appId": "019e7244-0000-7000-8000-000000000001",
  "kind": "git",
  "name": "analysis-code",
  "payload": {
    "repoUrl": "https://gitlab.dlr.de/zlp/mffd-analysis",
    "ref": "main",
    "path": "notebooks/",
    "mode": "TRACKED_ARTIFACT",
    "resolvedSha": "a3f1c9e",
    "resolvedAtMillis": 1750000000000
  }
}
```

---

## Endpoints

### List git references for a DataObject

```
GET /v2/references?kind=git&dataObjectAppId={appId}
```

Returns `ReferenceV2IO[]`. Permission: Read on the DataObject.

**Example:**
```bash
curl -H "X-API-KEY: $KEY" \
  "https://shepard.example.org/v2/references?kind=git&dataObjectAppId=019e7244-…"
```

---

### Create a git reference

```
POST /v2/references?kind=git&dataObjectAppId={appId}
Content-Type: application/json
```

**Permission:** Write on the DataObject.

**Request body:**
```json
{
  "repoUrl": "https://gitlab.dlr.de/group/repo",
  "ref": "main",
  "path": "data/",
  "mode": "TRACKED_ARTIFACT"
}
```

`repoUrl` is required. `ref`, `path`, and `mode` are optional.
PINNED_SNAPSHOT: the server resolves `ref` to a SHA immediately; if resolution
fails (unknown host, no PAT for private repo) the endpoint returns 422.

**Response (201 Created):** Full `ReferenceV2IO` with the new `appId`.

---

### Get a single git reference

```
GET /v2/references/{appId}
```

**Permission:** Read on the parent DataObject.

---

### Update a git reference (merge-patch)

```
PATCH /v2/references/{appId}
Content-Type: application/json
```

**Permission:** Write on the parent DataObject.

**Request body:** any subset of `{repoUrl, ref, path}`. `mode` is not patchable
after creation (changing mode requires delete + recreate to keep the provenance
record clean).

```json
{ "ref": "v2.1.0" }
```

---

### Delete a git reference

```
DELETE /v2/references/{appId}
```

**Permission:** Write on the parent DataObject.

---

### Inline preview (TRACKED_ARTIFACT mode)

```
GET /v2/data-objects/{dataObjectAppId}/git-references/{appId}/preview
```

**Permission:** Read on the DataObject.

Resolves the GitReference, picks the caller's stored PAT for the matching git
host (via G1-cred), routes through the per-host `GitAdapter`, and returns file
content (UTF-8) up to `shepard.git.preview.max-bytes` (default 1 MB). The
result is cached for `PT5M` per `(user, repoUrl, ref, path)`.

All non-fatal failure modes return `200` with `available: false` and a `reason`
discriminator so the UI can render a human-readable explanation rather than an
error:

| `reason` | Meaning |
|----------|---------|
| `not-tracked` | Reference mode is not TRACKED_ARTIFACT. |
| `unsupported-host` | No adapter registered for this host URL. |
| `no-credential` | No PAT stored for the calling user on this host. |
| `invalid-repo-url` | `repoUrl` could not be parsed. |
| `fetch-failed` | Adapter request to the git host failed. |

**Response body (`available: true`):**
```json
{
  "available": true,
  "content": "# MFFD Analysis\n…",
  "mimeType": "text/plain",
  "resolvedSha": "a3f1c9e",
  "truncated": false
}
```

---

### Check for upstream changes (TRACKED_ARTIFACT mode)

```
POST /v2/data-objects/{dataObjectAppId}/git-references/{appId}/check-update
```

**Permission:** Read on the DataObject.

Resolves the GitReference's `ref` via the matching adapter (anonymous for public
repos, PAT-authenticated for private). Compares the current upstream SHA to the
persisted `resolvedSha`. Always updates `resolvedSha` + `resolvedAtMillis` on
the reference, even when unchanged (refreshes the timestamp).

**Response (200):**
```json
{
  "currentSha": "d4e2a11",
  "previousSha": "a3f1c9e",
  "updated": true,
  "checkedAtMillis": 1750001000000
}
```

---

## Git credential management

Inline preview and check-update for private repositories require the calling
user to have a stored Personal Access Token (PAT) for the matching host.

### Why per-user PATs?

Private repository access is per-user. Stored credentials are encrypted at rest
with AES-GCM-256 (`shepard.secrets.encryption-key`). PATs are never returned by
any API endpoint after creation.

### User-facing credential endpoints

| Operation | Endpoint |
|-----------|----------|
| List credentials | `GET /v2/me/git-credentials` |
| Add a credential | `POST /v2/me/git-credentials` |
| Get one | `GET /v2/me/git-credentials/{appId}` |
| Update (PAT rotation) | `PATCH /v2/me/git-credentials/{appId}` |
| Delete | `DELETE /v2/me/git-credentials/{appId}` |

**Create body:**
```json
{
  "host": "gitlab.dlr.de",
  "username": "fkrebs",
  "pat": "glpat-xxxx",
  "displayName": "DLR GitLab"
}
```

`host` and `pat` are required. The UI profiles page (`/me#git-credentials`)
provides a form for this.

### Admin credential endpoints

An instance-admin can preseed or rotate git credentials on behalf of another
user (e.g. for service accounts or initial lab onboarding):

| Operation | Endpoint |
|-----------|----------|
| List user's credentials | `GET /v2/admin/users/{username}/git-credentials` |
| Add / replace for user | `POST /v2/admin/users/{username}/git-credentials` |
| Rotate a specific credential | `POST /v2/admin/users/{username}/git-credentials/{appId}/rotate` |

All admin credential endpoints require the `instance-admin` role (`X-API-KEY`
with role, or `Authorization: Bearer <token>` with the role in the JWT).

**List response:**
```json
{
  "items": [
    {
      "appId": "019e7244-…",
      "host": "gitlab.dlr.de",
      "username": "fkrebs",
      "displayName": "DLR GitLab",
      "lastRotatedAt": "2026-05-31T14:00:00Z"
    }
  ]
}
```

---

## Host adapter configuration

Three adapters are bundled; they are matched against `repoUrl` in priority order:

| Adapter | Default host match | Config key to extend |
|---------|--------------------|----------------------|
| **GitLab** | any URL containing `gitlab` | — (substring match, no config required) |
| **GitHub** | `*.github.com` + configured Enterprise hosts | `shepard.git.adapter.github.hosts` |
| **Gitea / Forgejo** | configured hosts only | `shepard.git.adapter.gitea.hosts` |

**Adding GitHub Enterprise or Forgejo hosts** (`application.properties`):
```properties
shepard.git.adapter.github.hosts=git.myinstitute.de,git.partner.example.com
shepard.git.adapter.gitea.hosts=forgejo.lab.example.com,codeberg.org
```

Both keys accept a comma-separated list. An adapter registered via
`shepard.git.adapter.github.hosts` matches any URL whose host equals one of the
listed values; it takes higher priority than the GitLab substring fallback.

---

## Configuration reference

| Key | Default | Description |
|-----|---------|-------------|
| `shepard.plugins.git.enabled` | `true` | Enable/disable the Git plugin. When `false`, `GET /v2/references?kind=git` returns `400`. |
| `shepard.secrets.encryption-key` | — (required for G1-cred) | Base64-encoded 32-byte AES-GCM key for PAT encryption. Generate: `openssl rand -base64 32`. Without this key, credential write endpoints return `501`. |
| `shepard.git.preview.max-bytes` | `1048576` (1 MB) | Maximum bytes returned by the preview endpoint. Larger files are truncated (`truncated: true` in the response). |
| `shepard.git.adapter.github.hosts` | — (empty) | Additional GitHub Enterprise host names, comma-separated. |
| `shepard.git.adapter.gitea.hosts` | — (empty) | Gitea / Forgejo host names, comma-separated. |

---

## RO-Crate / export integration

When a DataObject is exported as RO-Crate (e.g. via the "Download as RO-Crate"
button or `POST /v2/collections/{appId}/export`) each GitReference is included as
a `schema:SoftwareSourceCode` contextual entity:

```json
{
  "@id": "https://gitlab.dlr.de/group/repo/tree/a3f1c9e",
  "@type": "SoftwareSourceCode",
  "name": "analysis-code",
  "codeRepository": "https://gitlab.dlr.de/group/repo",
  "version": "a3f1c9e"
}
```

For PINNED_SNAPSHOT references the `@id` uses the frozen SHA — guaranteeing a
permalink to the exact source state. For LOOSE_LINK the `@id` uses `repoUrl`
(possibly with `ref` appended as a URL fragment if the host supports it).

---

## Neo4j schema

```
(:DataObject)-[:HAS_REFERENCE]->(:GitReference { appId, mode, repoUrl, ref, path, sha, resolvedSha, resolvedAtMillis })
(:User)-[:OWNS_CREDENTIAL]->(:GitCredential { appId, host, username, encryptedPat, displayName, lastRotatedAt })
```

Migrations:
- `V19__Add_appId_constraint_GitReference.cypher` — `appId` unique constraint on `:GitReference`.
- `V20__Add_appId_constraint_GitCredential.cypher` — `appId` unique constraint on `:GitCredential`.
- `V26__GitReference_mode_default.cypher` — backfill `mode = 'LOOSE_LINK'` on pre-G1b rows.
