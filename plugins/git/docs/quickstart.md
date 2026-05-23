---
title: git — Quickstart
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

# git — quickstart

**Goal:** cite a GitHub commit from one of your DataObjects so the
provenance graph carries the exact source-code state behind a
test run.

Time: 3 minutes.

---

## When to use which mode

| Mode | When to pick it |
|---|---|
| **LOOSE_LINK** | You just want a clickable link. No credentials. shepard never touches the host. |
| **TRACKED_ARTIFACT** | You want the file's content + SHA shown in the DataObject pane. Per-user PAT required. |
| **PINNED_SNAPSHOT** | You need an immutable code-citation record (FAIR, EN 9100, EASA AMC). shepard resolves the ref to a SHA once and freezes it. Per-user PAT required. |

This walkthrough uses **PINNED_SNAPSHOT** because it's the most
research-grade citation. LOOSE_LINK is identical except you skip
step 1 + you set `"mode": "LOOSE_LINK"`.

---

## Step 1 — store your GitHub PAT in shepard

Mint a classic PAT at <https://github.com/settings/tokens> with
the `repo` scope (or fine-grained PAT with `Contents: Read` on the
repos you'll cite), then POST it to shepard:

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...

curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "host": "github.com",
    "username": "alice",
    "pat": "ghp_AbCdEf1234567890..."
  }' \
  "$SHEPARD_URL/v2/me/git-credentials"
```

You can verify:

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/me/git-credentials" | jq '.[].host'
```

The PAT plaintext is never returned — only the host + username
list back.

---

## Step 2 — create a PINNED_SNAPSHOT reference

Pick a DataObject (e.g. a test run that consumed a specific
revision of your analysis code). The DataObject's `appId` is in
the URL bar on its detail page.

```bash
DATA_OBJECT_APPID=019e4e56-...

curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "PINNED_SNAPSHOT",
    "repoUrl": "https://github.com/noheton/shepard",
    "ref": "main",
    "path": "examples/lumen-showcase/seed.py"
  }' \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/git-references"
```

shepard resolves `main` → commit SHA via GitHub's REST API,
freezes the SHA into the reference, and returns:

```json
{
  "appId": "019e7f10-...",
  "mode": "PINNED_SNAPSHOT",
  "repoUrl": "https://github.com/noheton/shepard",
  "ref": "main",
  "path": "examples/lumen-showcase/seed.py",
  "sha": "8bdc8c6163ee4ea88acde244a1c7e9672ab593a3",
  "resolvedSha": "8bdc8c6163ee4ea88acde244a1c7e9672ab593a3",
  "resolvedAtMillis": 1716470000000
}
```

The `sha` field is **server-managed** — sending it in PATCH bodies
is rejected with 400 (mutability gate). To re-pin to a newer
commit, create a fresh PINNED_SNAPSHOT reference.

---

## Step 3 — view the file contents inline

The TRACKED_ARTIFACT preview endpoint resolves to the file's
current content + SHA. Even on a PINNED_SNAPSHOT reference,
preview uses the pinned SHA:

```bash
REF_APPID=019e7f10-...

curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/git-references/$REF_APPID/preview" | \
  jq '{sha, mimeType, byteSize, contentTruncated}'
```

Response:

```json
{
  "sha": "8bdc8c6163ee4ea88acde244a1c7e9672ab593a3",
  "mimeType": "text/x-python",
  "byteSize": 78421,
  "contentTruncated": false
}
```

The `content` field (omitted above for readability) carries the
file's text. Large binaries return `contentTruncated: true` with
a `reason` explaining the truncation.

---

## Step 4 — export with RO-Crate

When you export the DataObject as RO-Crate, the git reference
becomes a `schema:SoftwareSourceCode` contextual entity:

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/data-objects/$DATA_OBJECT_APPID/export?format=ro-crate" \
  -o ro-crate.zip
unzip -p ro-crate.zip ro-crate-metadata.json | jq '.["@graph"][] |
  select(."@type" == "schema:SoftwareSourceCode")'
```

The contextual entity's `@id` is a commit-SHA permalink:
`https://github.com/noheton/shepard/blob/8bdc8c6.../examples/lumen-showcase/seed.py`.
The reference is now durable + citable, surviving repository
deletion (as long as you keep the RO-Crate ZIP).

---

## Going further

- **GitLab**: same shape, swap `github.com` for `gitlab.dlr.de`
  in the credential POST + the repoUrl.
- **Codeberg / Forgejo / Gitea**: add the host to
  `shepard.git.adapter.gitea.hosts` (per
  [`install.md`](install.md)), then the same flow works.
- **Cite multiple files from one commit**: create one reference
  per `path`. They share the resolved SHA when you use the same
  `ref` and the host's tree didn't move between calls.
- **Audit who pinned what**: PROV1a's `:Activity` rows capture
  every reference create/update — see
  [`docs/reference/provenance.md`](../../../docs/reference/provenance.md).

---

## See also

- [`reference.md`](reference.md) — full mode reference, endpoint
  contract, RO-Crate semantics.
- [`install.md`](install.md) — operator setup + per-host PAT
  scopes.
- `aidocs/38-git-integration-design.md` — full design.
