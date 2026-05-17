---
layout: default
title: Publish to the Helmholtz Knowledge Graph (Unhide)
permalink: /help/publish-to-helmholtz-unhide/
---

# Publish your shepard install to the Helmholtz Knowledge Graph

This page is for **operators** — the person running a shepard
instance who wants the Collections on it to show up in the
[Helmholtz Knowledge Graph (HKG / Unhide)](https://unhide.helmholtz-metadaten.de/),
the federated Helmholtz-wide search portal.

Three things you'll do, in order: **enable the plugin**, **set a
contact email**, and **mint a harvest API key**. Then you tell
Unhide's data-provider team about your feed URL.

The full mechanism (endpoints, auth model, config keys) is on the
[Unhide publish reference page](/reference/unhide-publish/).

## Prerequisites

- A shepard instance with `instance-admin` access.
- The `shepard-admin` CLI installed and configured against your
  instance (see the [Admin CLI reference](/reference/admin-cli/)).
- An email address you're happy to expose to Unhide's harvester as
  your contact (e.g. `ops@example.dlr.de` — usually a team alias,
  not a personal email).

## Step 1 — Decide how public the feed should be

Two options:

| `feedPublic` | What happens |
|---|---|
| `false` (default) | The feed requires an `X-API-KEY` matching the harvest API key you mint in step 3. Stricter — recommended for instances that haven't reviewed every Collection's metadata for sensitivity. |
| `true` | The feed is reachable by anyone. Use only when you've audited what Collections expose and confirmed the metadata is OK to publish to the open web. |

Most operators want `false`. The default is `false`.

## Step 2 — Enable + configure

```bash
# Set the contact email Unhide's harvester will see.
shepard-admin unhide set-contact-email ops@example.dlr.de

# (Optional — only if you want the feed public.)
shepard-admin unhide set-feed-public true

# Flip the master toggle on. The feed endpoint returns 503
# unhide.feed.disabled until this runs.
shepard-admin unhide enable
```

Verify with:

```bash
shepard-admin unhide status
```

You should see:

```text
FIELD                      VALUE
enabled                    true
feedPublic                 false
contactEmail               ops@example.dlr.de
harvestApiKey.fingerprint  (no key)
harvestApiKey.mintedAt     (never)
```

## Step 3 — Mint a harvest API key

(Skip this if you went `feedPublic=true` in step 1 — no key needed.)

```bash
shepard-admin unhide rotate-harvest-key | gopass insert -m shepard/unhide-harvest-key
```

The CLI prints the plaintext key on `stdout` (the pipe captures it
into your secret store) and a warning + fingerprint on `stderr`.

> **The plaintext is shown exactly once.** If you lose it before
> you save it, run `rotate-harvest-key` again — it mints a fresh
> key and invalidates the previous one. There is no "show me my
> current key" path; that would defeat the whole point of hashing
> at rest.

## Step 4 — Verify the feed works

```bash
# The plaintext key — same value you piped into your secret store.
HARVEST_KEY=...

curl -s -H "X-API-KEY: $HARVEST_KEY" \
  https://shepard.example.dlr.de/v2/unhide/feed.jsonld?page=0&page-size=10 \
  | jq '._meta'
```

You should see something like:

```json
{
  "page": 0,
  "pageSize": 10,
  "totalEntries": 42,
  "totalPages": 5,
  "generatedAt": "2026-05-13T05:11:00Z",
  "contactEmail": "ops@example.dlr.de"
}
```

If you get a `401` with `"type": "/problems/unhide.harvest-key.absent"`,
the key didn't match — check the env var and try again.

If you get a `503` with `"type": "/problems/unhide.feed.disabled"`,
the master toggle is off — re-run `shepard-admin unhide enable`.

## Step 5 — Register with Unhide

shepard never runs code on Unhide's infrastructure. To get
harvested, either:

- **Self-service**: visit
  [`unhide.helmholtz-metadaten.de/dataprovider/register`](https://unhide.helmholtz-metadaten.de/dataprovider/register)
  and submit your feed URL + the contact email you set in step 2.
  The HMC team will reach out to schedule the first harvest.
- **HMC outreach**: HMC's data-provider liaison often reaches out
  to Helmholtz centres directly. If your shepard install becomes
  operator-visible they may contact you first.

When Unhide is harvesting, the daily cron's `GET` requests will
show up in your shepard's [activity log](/help/monitor-collection-activity/)
(filter on `targetKind: UnhideConfig` won't catch them — feed
reads aren't admin mutations; filter on the request path
`/v2/unhide/feed.jsonld` instead, once reads are captured via
`shepard.provenance.capture-reads=true`).

## Turn it off

Emergency shut-off — instantly stops new harvest requests from
succeeding without restarting shepard:

```bash
shepard-admin unhide disable
```

The feed immediately returns `503 unhide.feed.disabled` on the
next harvester request. Re-run `shepard-admin unhide enable` to
turn it back on; no further config is lost.

To rotate or revoke the harvest key without disabling the feed:

```bash
shepard-admin unhide rotate-harvest-key   # mints a new key, old one stops working
shepard-admin unhide revoke-harvest-key   # clears the key entirely — feed inaccessible until next rotate
```

## What gets published

Every non-deleted Collection that has **not** been opted out appears
in the feed as a `schema:Dataset` + `m4i:Dataset` JSON-LD entry
(NFDI4Ing metadata4ing, the engineering-research extension of W3C
PROV-O). Each entry carries the Collection's name, description,
creation / update timestamps, plus the creator (as a `schema:Person`,
with an ORCID `@id` if the creator User has one on file).

### Opting a Collection out of the feed

To exclude a specific Collection without disabling the feed
instance-wide:

1. Open the Collection's detail page.
2. Expand the **Publishing** section at the bottom of the page.
3. Toggle **Publish to Helmholtz Knowledge Graph** off.

The change takes effect on the next harvest cycle — no restart
required. To re-include the Collection, toggle it back on.

The master toggle (`shepard-admin unhide enable/disable`) and the
per-Collection toggle are independent gates: a Collection with the
toggle **off** is excluded even when the instance-wide feed is
enabled; a Collection with the toggle **on** is still excluded if
the instance-wide feed is disabled.

## See also

- [Unhide publish reference](/reference/unhide-publish/) — every
  endpoint, every config key, the RFC 7807 error envelopes.
- [Admin CLI reference](/reference/admin-cli/) — the full
  `shepard-admin` surface.
- [Monitor Collection activity](/help/monitor-collection-activity/)
  — once `capture-reads` is on, harvester requests show up here.
