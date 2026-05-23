---
title: minter-local — Quickstart
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

# minter-local — quickstart

**Goal:** mint your first persistent identifier (PID) for a
Collection. No external service, no credentials, no waiting on a
DOI registry.

Time: 2 minutes.

---

## Step 1 — pick a Collection

Any Collection you can write to works. The `appId` is the UUID v7
you see in the URL bar on the Collection detail page, e.g.
`019e4e56-ca63-76f3-9bf0-6681f7fe6d56`.

```bash
COLLECTION_APPID=019e4e56-ca63-76f3-9bf0-6681f7fe6d56
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=...   # your JWT or API key
```

---

## Step 2 — verify `local` is the active minter

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "minter-local") | {id, state}'
```

Expected:

```json
{ "id": "minter-local", "state": "ENABLED" }
```

If `state` is `DISABLED`, see [`install.md`](install.md).

---

## Step 3 — mint the PID

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/collections/$COLLECTION_APPID/publish"
```

Response (201):

```json
{
  "appId": "019e7f00-...",
  "entityAppId": "019e4e56-ca63-76f3-9bf0-6681f7fe6d56",
  "kind": "collections",
  "pid": "shepard:dlr.de/shepard-prod:collections:019e4e56-ca63-76f3-9bf0-6681f7fe6d56:v1",
  "versionNumber": 1,
  "mintedAt": 1716470000000,
  "mintedBy": "alice"
}
```

The PID is **immutable** — even if you re-publish, the `v1` PID
keeps resolving to this exact snapshot.

---

## Step 4 — re-mint after changes (versioning)

Edit the Collection (add DataObjects, change description, etc.),
then re-publish with `?force=true`:

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/collections/$COLLECTION_APPID/publish?force=true"
```

Response carries `versionNumber: 2` and a fresh PID ending in `:v2`.
The previous `:v1` PID still resolves to the old snapshot.

---

## Step 5 — resolve the PID via KIP

The bundled [KIP plugin](../../kip/docs/quickstart.md) exposes every
local PID as a public Helmholtz Kernel Information Profile record.
The PID's suffix (everything after the `shepard:` scheme) is the
KIP path:

```bash
PID_SUFFIX="dlr.de/shepard-prod:collections:019e4e56-ca63-76f3-9bf0-6681f7fe6d56:v1"

curl -s "$SHEPARD_URL/v2/.well-known/kip/$PID_SUFFIX" | jq .
```

Returns a JSON-LD KIP record carrying the landing page URL,
creation timestamp, digital object type, and version. No
authentication required — the resolver is public.

---

## Going further

- **Switch to a real DOI minter**: when ready to publish to a
  registry, replace `local` with the
  [DataCite plugin](../../minter-datacite/docs/quickstart.md). Old
  `shepard:` PIDs keep resolving; new mints land at DataCite.
- **Audit who minted what**: PROV1a's
  [`/v2/provenance/*`](../../../docs/reference/provenance.md)
  surface captures every `POST .../publish` as an `:Activity` row.
- **List all PIDs for an entity**: `GET
  /v2/collections/{appId}/publications` returns the version chain.

---

## See also

- [`reference.md`](reference.md) — full PID format reference.
- [`install.md`](install.md) — operator install + config.
- [DataCite minter quickstart](../../minter-datacite/docs/quickstart.md)
  — when you're ready for real DOIs.
