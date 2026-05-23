---
title: kip — Quickstart
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

# kip — quickstart

**Goal:** dereference a shepard PID via the public KIP resolver
and read the resulting JSON-LD record.

Time: 1 minute. No login required.

---

## Prerequisite — a PID exists

You need an entity that has been published. If you haven't done
that yet, see
[`minter-local/quickstart.md`](../../minter-local/docs/quickstart.md)
— it takes 30 seconds.

For this walkthrough, assume you have a PID like:

```
shepard:dlr.de/shepard-prod:collections:019e4e56-ca63-76f3-9bf0-6681f7fe6d56:v1
```

The KIP resolver consumes the **suffix** — everything after the
`shepard:` prefix.

---

## Step 1 — call the resolver

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
PID_SUFFIX="dlr.de/shepard-prod:collections:019e4e56-ca63-76f3-9bf0-6681f7fe6d56:v1"

curl -s "$SHEPARD_URL/v2/.well-known/kip/$PID_SUFFIX" | jq .
```

No `-H "X-API-KEY: ..."` needed — the endpoint is public.

Response:

```json
{
  "@context": "https://hmc.helmholtz.de/kip/v1",
  "id": "shepard:dlr.de/shepard-prod:collections:019e4e56-...:v1",
  "kernelInformationProfile": {
    "id": "shepard:dlr.de/shepard-prod:collections:019e4e56-...:v1",
    "landingPage": "https://shepard-api.nuclide.systems/v2/collections/019e4e56-...",
    "digitalObjectType": "http://shepard.dlr.de/types/dlr:Collection",
    "dateCreated": "2026-01-15T10:30:00Z",
    "dateModified": "2026-01-15T10:30:00Z",
    "rightsHolder": "alice",
    "digitalObjectVersion": "v1"
  }
}
```

---

## Step 2 — follow the landing page

The `landingPage` field is the human-readable shepard URL.
Browser-redirect or curl with `-L` to view the entity:

```bash
curl -L -H "X-API-KEY: $YOUR_KEY" \
  "https://shepard-api.nuclide.systems/v2/collections/019e4e56-ca63-76f3-9bf0-6681f7fe6d56"
```

(The landing page itself requires read permission — the KIP
record is the only fully-public surface.)

---

## Step 3 — handle a not-found

When the PID has no matching `:Publication` row, the resolver
returns RFC 7807:

```bash
curl -s -i "$SHEPARD_URL/v2/.well-known/kip/does:not:exist:v1"
```

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "https://shepard.dlr.de/problems/kip.pid.not-found",
  "title": "KIP record not found for PID suffix",
  "status": 404,
  "detail": "No :Publication row matches the requested PID."
}
```

---

## Why this matters

The KIP resolver is the surface that lets **external PID
catalogues** (Helmholtz Unhide, Helmholtz Knowledge Graph,
re3data crawlers) cite your shepard data without scraping the
authenticated REST API. Every PID minted by `minter-local`,
`minter-datacite`, or `minter-epic` becomes resolvable here.

The Unhide publish plugin's
[feed](../../unhide/docs/reference.md) embeds the KIP URL as
`schema:url` in every entry — that's how the HKG dereferences
shepard datasets after harvest.

---

## See also

- [`reference.md`](reference.md) — full endpoint contract.
- [`install.md`](install.md) — operator-side setup.
- [`minter-local/quickstart.md`](../../minter-local/docs/quickstart.md)
  — mint a PID to resolve.
- [`unhide/reference.md`](../../unhide/docs/reference.md) — where
  external harvesters read the KIP URL from.
- [Helmholtz KIP spec](https://docs.hmc.helmholtz.de/kernel-information-profile/)
  — upstream profile.
