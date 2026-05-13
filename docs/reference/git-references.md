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
| **`PINNED_SNAPSHOT`** (G1c — queued) | Same as TRACKED_ARTIFACT plus the resolved commit SHA is frozen onto the reference; RO-Crate exports include a `SoftwareSourceCode` entity. |

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

## Adapter support

- **GitLab** ships in G1b. Adapter dispatch is host-substring match
  on `gitlab` (so `gitlab.com`, `gitlab.dlr.de`, etc. work out of
  the box). Self-hosted GitLab on a non-obvious DNS (e.g.
  `code.example.org`) can be added via the operator config
  `shepard.git.adapter.gitlab.hosts=code.example.org,...` (CSV).
- **GitHub** + **Gitea** — queued (G1d). Until then, non-GitLab
  hosts return RFC 7807 `git.adapter.unsupported-host` 501 on
  `…/preview`.

## See also

- `aidocs/38-git-integration-design.md` — full design.
- `aidocs/63-architecture-decision-log.md` ADR-0021 — adapter-seam
  decision.
- `aidocs/16-dispatcher-backlog.md` G1a-G1f — slice-by-slice status.
