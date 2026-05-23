---
title: git — Install
stage: deployed
last-stage-change: 2026-05-23
audience: plugin-author
synthetic_batch: true
generation_rule: feedback_no_synthetic_provenance.md
---

> 🤖 **BACKFILL — created retroactively 2026-05-23 by Claude Opus 4.7**
> per the docs-gap audit at `aidocs/agent-findings/plugin-docs-gap-audit-2026-05-23.md`.
> The plugin's behaviour is documented from the source code as it stood
> at commit `8bdc8c6163ee4ea88acde244a1c7e9672ab593a3`. If anything is
> inaccurate, the source is authoritative; please open a PR or issue.

# git — install

`shepard-plugin-git` adds the `GitReference` payload kind —
DataObjects can cite a specific commit, branch, tag, or file
inside a git repository hosted on GitHub, GitLab, Gitea, Forgejo,
or Codeberg. The plugin requires **no sidecars** — it talks
directly to the git host's REST API using the calling user's
stored personal access token (PAT).

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven
  profile (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-git-${revision}.jar`.
- Outbound HTTPS connectivity from the backend container to the
  git hosts you intend to support (e.g. `api.github.com`,
  `gitlab.dlr.de`).
- Per-user PATs for TRACKED_ARTIFACT / PINNED_SNAPSHOT modes — see
  [`reference.md` §"Per-user credentials"](reference.md#per-user-credentials).
  LOOSE_LINK mode doesn't require any credentials.

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.git.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |
| `shepard.git.adapter.gitlab.hosts` | (empty CSV) | Additional hostnames the GitLab adapter should claim. Defaults: any host containing `gitlab`. |
| `shepard.git.adapter.github.hosts` | (empty CSV) | Additional hostnames the GitHub adapter should claim. Defaults: `github.com`, `*.github.com`. Add GitHub Enterprise hostnames here (e.g. `github.example.dlr.de`). |
| `shepard.git.adapter.gitea.hosts` | (empty CSV) | Additional hostnames the Gitea adapter should claim. Defaults: any host containing `gitea`. Add `codeberg.org` + any Forgejo hosts here. |
| `shepard.git.artifact-cache.ttl-seconds` | `300` | PT5M cache TTL for the TRACKED_ARTIFACT preview path (per `(user, repoUrl, ref, path)`). |

Minimal `application.properties`:

```properties
shepard.plugins.git.enabled=true
shepard.git.adapter.github.hosts=github.dlr.de
shepard.git.adapter.gitea.hosts=codeberg.org
```

No credentials live in `application.properties` — the plugin
delegates auth to per-user PATs stored under
`/v2/me/git-credentials`.

---

## Per-host PAT setup

Each user who wants to use TRACKED_ARTIFACT or PINNED_SNAPSHOT
modes must provision a PAT on the relevant host and POST it to
shepard:

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems

curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "host": "gitlab.dlr.de",
    "username": "alice",
    "pat": "glpat-AbCdEf1234567890"
  }' \
  "$SHEPARD_URL/v2/me/git-credentials"
```

Required scopes per host:

| Host | Classic PAT scope | Fine-grained PAT permission |
|---|---|---|
| **GitLab** | `read_repository` | n/a (GitLab doesn't separate fine-grained) |
| **GitHub** | `repo` (private) / `public_repo` (public-only) | `Contents: Read` |
| **Gitea / Forgejo / Codeberg** | `read:repository` | n/a |

A PAT with weaker scopes returns RFC 7807 problem-detail bodies
with the right scope hint when shepard's preview path fails.

---

## Healthcheck

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems

# 1. Plugin registry.
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "git")'

# 2. Stored credentials for current user.
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/me/git-credentials" | jq .
```

A LOOSE_LINK reference creates and reads correctly without any
PAT — that's the smoke-test for "is the plugin alive at all".

---

## GitHub Enterprise / self-hosted GitLab

For non-standard hostnames, widen the per-adapter CSV. Example:
an institute that runs both `github.example.dlr.de` (GitHub
Enterprise) and `gitlab.dlr.de` (self-hosted GitLab):

```properties
shepard.git.adapter.github.hosts=github.example.dlr.de
# GitLab adapter already claims gitlab.dlr.de via the default substring match
```

The dispatch order is **most-specific to least-specific** —
GitHub → Gitea → GitLab. A host added to the GitHub CSV is never
stolen by the GitLab substring matcher.

---

## Disabling the plugin

```properties
shepard.plugins.git.enabled=false
```

When disabled, all `/v2/data-objects/{appId}/git-references/*`
endpoints return 404. The `:GitReference` Neo4j nodes are
preserved; the `:GitCredential` subsystem (auth perimeter) stays
in-tree and remains available.

---

## Known pitfalls

- **No outbound HTTPS in the backend container.** TRACKED_ARTIFACT
  + PINNED_SNAPSHOT both make live calls to the git host. If
  the cluster blocks egress, only LOOSE_LINK mode works.
- **PAT scope too narrow.** GitHub's fine-grained PATs default to
  no repository access; explicitly grant `Contents: Read` on the
  repositories you want to cite.
- **Codeberg + Forgejo land on the Gitea adapter.** Add their
  hostnames to `shepard.git.adapter.gitea.hosts` — the Gitea
  adapter uses Gitea's `Authorization: token <pat>` convention,
  which Codeberg/Forgejo inherit.
- **Rate-limits**. GitHub's unauthenticated rate-limit is 60
  req/h. With a PAT, 5000 req/h. The PT5M TRACKED_ARTIFACT cache
  blunts the rate, but heavy users may still hit limits.
- **Commit signing verification not enforced.** The plugin does
  not validate that the pinned commit is signed. A future slice
  may add `shepard.git.require-signed-commits=true`; track under
  aidocs/16 `G1-signing`.

---

## Neo4j migrations

The plugin's `:GitReference` entity registers on first start via
the `PayloadKind` ServiceLoader SPI in `NeoConnector.connect()`.
Three Cypher migrations (V19, V20, V26) cover `:GitCredential`
constraints; they ship in the backend resources (not the plugin
JAR) because the `:GitCredential` entity stays in the auth
perimeter.

---

## See also

- [`reference.md`](reference.md) — modes, endpoints, RO-Crate export.
- [`quickstart.md`](quickstart.md) — reference your first commit.
- `aidocs/38-git-integration-design.md` — full design.
- `aidocs/63-architecture-decision-log.md` ADR-0021 — adapter-seam decision.
