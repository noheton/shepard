---
title: unhide — Install
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

# unhide — install

`shepard-plugin-unhide` exposes a JSON-LD `schema:Dataset` /
`m4i:Dataset` feed at `/v2/unhide/feed.jsonld` for harvesting by
the [Helmholtz Knowledge Graph (HKG / Unhide)](https://unhide.helmholtz-metadaten.de/).
This page is the operator-runbook companion to
[`quickstart.md`](quickstart.md) — once the install is done, the
quickstart walks through enabling the feed.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven
  profile (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-unhide-${revision}.jar`.
- `shepard-admin` CLI installed (per the
  [Admin CLI reference](../../../docs/reference/admin-cli.md)).
- A contact email you're willing to expose to harvesters
  (typically a team alias like `ops@example.dlr.de`).
- Optional: a minted PID per Collection you want to publish (so
  feed entries carry `schema:identifier`). See
  [`minter-local/quickstart.md`](../../minter-local/docs/quickstart.md).

---

## Install-time defaults

The five `application.properties` keys below seed the
`:UnhideConfig` Neo4j singleton on first start. The first three
are runtime-mutable through `PATCH /v2/admin/unhide/config` or
the `shepard-admin unhide` CLI; the last two are deploy-time
only (buffer-sizing exception per
CLAUDE.md §"Admin-configurable at runtime").

| Key | Default | Purpose | Runtime-mutable? |
|---|---|---|---|
| `shepard.unhide.enabled` | `false` | Seeds the master toggle. | Yes |
| `shepard.unhide.feed.public` | `false` | Seeds the feed-visibility flag. | Yes |
| `shepard.unhide.contact-email` | (empty) | Seeds the contact email. | Yes |
| `shepard.unhide.feed.page-size` | `100` | Cursor page size, capped at `1000`. | **No** — deploy-time. |
| `shepard.unhide.feed.provenance-window` | `5` | UH1b window — most-recent N processing-step entries per Collection. Capped at `100`. | **No** — deploy-time. |

| Plugin-lifecycle key | Default | Description |
|---|---|---|
| `shepard.plugins.unhide.enabled` | `true` | Gates the lifecycle hook visible in `GET /v2/admin/plugins`. |

---

## CLI flow (the runbook)

The complete enable-and-rotate-key flow, top to bottom:

```bash
# 1. Set the contact email harvesters will see.
shepard-admin unhide set-contact-email ops@example.dlr.de

# 2. Decide whether the feed should be public.
# (Default: feedPublic=false; the harvester must present an
# X-API-KEY matching the minted harvest key.)
shepard-admin unhide set-feed-public false

# 3. Flip the master toggle on. Feed returns 503 until this fires.
shepard-admin unhide enable

# 4. Mint a harvest API key (the plaintext is shown ONCE).
shepard-admin unhide rotate-harvest-key | gopass insert -m shepard/unhide-harvest-key

# 5. Verify the feed responds.
HARVEST_KEY=$(gopass show -o shepard/unhide-harvest-key)
curl -s -H "X-API-KEY: $HARVEST_KEY" \
  "https://shepard.example.dlr.de/v2/unhide/feed.jsonld?page=0&page-size=10" | \
  jq '._meta'
```

Status check at any point:

```bash
shepard-admin unhide status
```

For the per-Collection opt-out toggle, see the
[`quickstart.md` "What gets published"](quickstart.md#what-gets-published)
section.

---

## Healthcheck

```bash
# Live feed page (HARVEST_KEY required when feedPublic=false):
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-API-KEY: $HARVEST_KEY" \
  "https://shepard.example.dlr.de/v2/unhide/feed.jsonld?page=0&page-size=1"
```

| HTTP code | Meaning |
|---|---|
| `200` | Plugin healthy; feed reachable. |
| `401` | `feedPublic=false` AND key is missing / wrong. Re-check `X-API-KEY`. |
| `503` | `:UnhideConfig.enabled=false`. Run `shepard-admin unhide enable`. |
| `404` | `shepard.plugins.unhide.enabled=false` or plugin JAR missing from `/deployments/plugins/`. |

---

## Registering with Helmholtz Unhide

shepard never runs code on Unhide's infrastructure. Once the feed
is operational:

- **Self-service**: submit your feed URL at
  <https://unhide.helmholtz-metadaten.de/dataprovider/register>.
- **HMC outreach**: HMC's data-provider liaison may contact you
  proactively once a public-facing shepard install becomes
  visible.

Unhide is **pull-harvest** — shepard never POSTs anything.

---

## Disabling the plugin

Quickest path (emergency shut-off, no restart):

```bash
shepard-admin unhide disable
```

The feed immediately returns 503 on the next harvester request.
Re-enable with `shepard-admin unhide enable`; no state is lost.

For a hard disable (no startup load):

```properties
shepard.plugins.unhide.enabled=false
```

---

## Known pitfalls

- **Forgot to save the harvest key**. There's no recovery —
  `rotate-harvest-key` is the only path. Re-rotate and update
  the harvester's stored key.
- **Stale Unhide cache after a `disable`/`enable` cycle**.
  HKG harvests are daily; expect 24h propagation.
- **`feedPublic=true` over-exposure**. Audit every Collection's
  metadata before flipping public — Collection
  descriptions become world-readable through the feed.
- **PROV1a captures `/credential` paths but not bodies**. The
  harvest-key plaintext never enters `:Activity`. Don't try to
  recover it from the audit trail.

---

## Migration

V47 (`V47__Add_appId_constraint_UnhideConfig.cypher`) runs
automatically on backend startup via `MigrationsRunner`. It adds
a unique constraint on `:UnhideConfig.appId`. The migration is
**idempotent** and safe to re-run.

---

## See also

- [`reference.md`](reference.md) — every endpoint, every field,
  every config key, the RFC 7807 envelopes.
- [`quickstart.md`](quickstart.md) — end-to-end operator walkthrough.
- `aidocs/integrations/67-unhide-publish-plugin.md` — full design.
- [Helmholtz Unhide data-provider portal](https://unhide.helmholtz-metadaten.de/dataprovider/register).
