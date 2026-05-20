---
title: Git references
weight: 60
---

# Git references

shepard's **`GitReference`** payload kind anchors a `DataObject` to
a git repository — useful for citing the exact piece of code,
configuration, or specification that produced your data.

## Modes

| Mode | Behaviour |
|---|---|
| **`LOOSE_LINK`** (G1a) | Just `repoUrl` + `ref` + `path`. shepard renders a clickable link in the DataObject pane; no contact with the git host. |
| **`TRACKED_ARTIFACT`** (G1b) | shepard fetches the file's content + SHA via the host's REST API, using your stored PAT. Inline preview in the DataObject pane; PT5M cache per `(you, repoUrl, ref, path)`. |
| **`PINNED_SNAPSHOT`** (G1c) | At create/PATCH time shepard resolves `ref` → commit SHA via the host adapter and the caller's PAT, then freezes the result. The `sha` field is server-managed (client writes rejected 400). RO-Crate exports include a `schema:SoftwareSourceCode` contextual entity with a commit-SHA permalink as `@id`. |

## Endpoints

| Verb / Path | What it does |
|---|---|
| `GET /v2/data-objects/{appId}/git-references` | List references on a DataObject. |
| `POST /v2/data-objects/{appId}/git-references` | Create a new reference. Mode defaults to `LOOSE_LINK`. |
| `GET /v2/data-objects/{appId}/git-references/{refAppId}` | Fetch a single reference's metadata. |
| `PATCH /v2/data-objects/{appId}/git-references/{refAppId}` | RFC 7396 merge-patch. Accepts `mode`, `repoUrl`, `ref`, `path`. |
| `GET /v2/data-objects/{appId}/git-references/{refAppId}/preview` | **G1b.** Returns `{available, sha, mimeType, byteSize, content, contentTruncated, reason}`. `available=false` with `reason=no-credential` if you haven't stored a PAT for the host. |
| `DELETE /v2/data-objects/{appId}/git-references/{refAppId}` | Delete a reference. |

## Per-user credentials

The TRACKED_ARTIFACT preview path reads the calling user's stored
PAT from the G1-cred subsystem. Manage credentials at
`/me` → "Git credentials" tab (frontend) or `/v2/me/git-credentials`
(API). Each credential is `(host, username, PAT)`; shepard picks the
right one by host.

## Supported hosts

The TRACKED_ARTIFACT preview dispatches by hostname. Three per-host
adapters ship; operators can widen each adapter's host claim via a
CSV config key.

| Adapter | Default-claimed hostnames | Config key (CSV widener) | Auth header | Slice |
|---|---|---|---|---|
| **GitLab** | any host containing `gitlab` (e.g. `gitlab.com`, `gitlab.dlr.de`) | `shepard.git.adapter.gitlab.hosts=` | `Authorization: Bearer <pat>` | G1b |
| **GitHub** | `github.com`, any `*.github.com` subdomain | `shepard.git.adapter.github.hosts=` (intended for GitHub Enterprise on non-`*.github.com` DNS like `github.example.dlr.de`) | `Authorization: Bearer <pat>` | G1d |
| **Gitea** | any host containing `gitea` (e.g. `gitea.com`, `gitea.dlr.de`) | `shepard.git.adapter.gitea.hosts=` (intended for **Forgejo** and **`codeberg.org`**) | `Authorization: token <pat>` (Gitea convention) | G1d |

**Dispatch order.** Adapters are consulted most-specific to
least-specific (GitHub → Gitea → GitLab) so a host added to one of
the more-specific adapters' allowlists is never stolen by the GitLab
substring-matcher fallback. A host claimed by no adapter returns
RFC 7807 `git.adapter.unsupported-host` 501 on `…/preview`.

**PAT scopes.** Each adapter surfaces the right scope hint in its
4xx error messages:

- GitLab: `read_repository`.
- GitHub: `repo` (private) or `public_repo` for classic PATs;
  `Contents: Read` for fine-grained PATs.
- Gitea / Forgejo: `read:repository`.

## PINNED_SNAPSHOT mode

`PINNED_SNAPSHOT` provides an immutable code-citation record — the
commit SHA is resolved once at creation time and never changes.

**Create a pinned snapshot:**

```http
POST /v2/data-objects/{dataObjectAppId}/git-references
Content-Type: application/json

{
  "mode": "PINNED_SNAPSHOT",
  "repoUrl": "https://gitlab.dlr.de/group/repo",
  "ref": "v1.2.3",
  "path": "analysis/main.py"
}
```

shepard resolves `v1.2.3` to a commit SHA via the registered host
adapter and the caller's stored PAT, then returns:

```json
{
  "appId": "...",
  "mode": "PINNED_SNAPSHOT",
  "repoUrl": "https://gitlab.dlr.de/group/repo",
  "ref": "v1.2.3",
  "path": "analysis/main.py",
  "sha": "abc123def456...",
  "resolvedSha": "abc123def456...",
  "resolvedAtMillis": 1716000000000
}
```

**Requirements:**
- The caller must have a git credential stored at `/v2/me/git-credentials`
  for the matching host (`gitlab.dlr.de` in this example).
- A registered adapter must support the host (GitLab / GitHub / Gitea).

**Error responses:**
- `400` — missing `ref`, no adapter for host, or no credential for host.
- `502` — adapter contacted the host but SHA resolution failed (e.g.
  branch not found).

**RO-Crate export:** every DataObject containing a `GitReference`
(any mode) now produces a `schema:SoftwareSourceCode` contextual entity
in `ro-crate-metadata.json`. For PINNED_SNAPSHOT the entity's `@id` is
an immutable SHA-blob permalink; for other modes the best available
identifier (ref-based URL, or bare `repoUrl`) is used.

**Mutability:** the `sha` field is server-managed. Sending `sha` in a
PATCH body is rejected with `400`. To re-pin to a newer commit, PATCH
the reference's `mode` to `LOOSE_LINK` first, then create a new
PINNED_SNAPSHOT entry.

## See also

- `aidocs/38-git-integration-design.md` — full design.
- `aidocs/63-architecture-decision-log.md` ADR-0021 — adapter-seam
  decision.
- `aidocs/16-dispatcher-backlog.md` G1a-G1f — slice-by-slice status.
