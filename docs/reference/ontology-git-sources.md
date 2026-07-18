---
stage: deployed
last-stage-change: 2026-07-18
audience: admin
layout: default
title: Ontology git sources
description: Operator reference for registering git repositories of ontology files that Shepard clones and ingests into its semantic repository
permalink: /reference/ontology-git-sources/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The surface shipped
> as `TPL5`; this page documents it from the source
> (`OntologyGitSourceRest.java`) as it stands at the backfill date.

<!-- backfill: DOCS-3A6/7-sweep2 2026-07-18 -->

# Ontology git sources

**Feature ID:** TPL5
**Route root:** `/v2/admin/semantic/git-sources` (`instance-admin`)

---

## What it is

An **ontology git source** is a registered git repository that holds ontology
files (Turtle / OWL). Once registered, Shepard can **clone it shallow
(`--depth=1`), find the files that match a glob, and ingest them** into a
semantic repository — so your instance's vocabulary catalogue can track a
canonical ontology repo instead of being hand-uploaded a file at a time.

It complements the two existing preseed paths in
[Semantic repositories](semantic-repositories.md):

- **Bundled ontologies** — the built-in metadata4ing / CHAMEO / Dublin Core set.
- **Custom-bundle upload** — a one-off TTL upload via the admin UI.
- **Git sources (this page)** — a *tracked* source you can re-ingest on demand
  when the upstream repo moves.

Registering a source does **not** ingest it. Registration and ingest are two
steps, so you can add a source, review it, then trigger the first pull.

---

## Endpoints

| Verb | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/semantic/git-sources` | List every source (enabled and disabled), paged. |
| `POST` | `/v2/admin/semantic/git-sources` | Register a new source (does not ingest). |
| `DELETE` | `/v2/admin/semantic/git-sources/{appId}` | Remove the source record. Ingested triples stay. |
| `POST` | `/v2/admin/semantic/git-sources/{appId}/ingest` | Clone + ingest matching files now. |

All four require the `instance-admin` role; `401`/`403` bodies are RFC 7807.

### List

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "https://shepard.example.org/v2/admin/semantic/git-sources?page=0&pageSize=50"
```

Returns a paged envelope (`items` + `total` + `page` + `pageSize`); the total
count is also on the `X-Total-Count` header. `pageSize` is 1–200 (default 50).

### Register a source

`POST` with this body:

| Field | Required | Default | Notes |
|---|---|---|---|
| `name` | ✅ | — | Human-readable label, e.g. `"InfAI m4i ontologies"`. |
| `repoUrl` | ✅ | — | Git clone URL (https). |
| `branch` | — | `main` | Branch to clone. |
| `pathPattern` | — | `*.ttl` | Glob for the files to ingest. A bare pattern (`*.ttl`) matches at any depth; a path-qualified one (`ontologies/*.owl`) is relative to the repo root. |
| `targetRepoAppId` | — | — | The semantic repository to ingest into. |
| `enabled` | — | `true` | Disabled sources are kept but skipped by bulk operations. |

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "name": "InfAI m4i ontologies",
        "repoUrl": "https://github.com/example/m4i-ontologies.git",
        "branch": "main",
        "pathPattern": "*.ttl" }' \
  https://shepard.example.org/v2/admin/semantic/git-sources
```

`201` returns the created record, including its `appId` and the read-only
status fields (`lastIngestedAt`, `lastStatus`, `lastError`, `createdAt`,
`createdBy`).

### Ingest now

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.org/v2/admin/semantic/git-sources/{appId}/ingest
```

Returns `200` with `{ "ok": true, "filesIngested": <n> }` on success, or
`{ "ok": false, "filesIngested": <n>, "error": "<message>" }` when the clone or
parse failed. **Always check the `ok` field** — a `200` is returned even for a
failed ingest, so the outcome is in the body, not the status line. The
`lastStatus` / `lastError` fields on the source record are updated to match.

### Delete

```bash
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.org/v2/admin/semantic/git-sources/{appId}
```

`204` on success. **Deleting the source does not remove already-ingested
triples** from the semantic repository — it only stops future re-ingests. Use
the ontology-bundle disable/remove controls (see
[Semantic repositories](semantic-repositories.md)) to withdraw the terms
themselves.

---

## Where it is in the UI

The **Admin → Ontology Bundles** and **Semantic Repositories** tiles are the
UI home for the semantic catalogue. The git-source registration + ingest flow
is primarily an **API/CLI surface** today; script it from the endpoints above.

---

## See also

- [Semantic repositories](semantic-repositories.md) — connector types, bundled
  ontologies, custom-bundle upload, and the neosemantics-backed `INTERNAL`
  store these sources feed.
- [Manage vocabularies runbook](../admin/runbooks/manage-vocabularies.md) —
  operator workflow for enabling/disabling vocabulary bundles.
- [Ontology term search](ontology-term-search.md) — how ingested terms surface
  in the annotation autocomplete.
