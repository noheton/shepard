# shepard-plugin-v1-compat — quickstart

Two operator tasks: **inspect** the v1 surface usage on your instance,
and (much later, when nothing relies on it) **flip it off**.

## Inspect v1 usage

```bash
curl -H "X-API-KEY: <admin-key>" https://<host>/v2/admin/legacy/v1/stats
```

You get a snapshot like:

```json
{
  "totalHits": 1247,
  "byEndpoint": [
    {"pathPattern": "/shepard/api/collections", "hits": 800},
    {"pathPattern": "/shepard/api/dataObjects", "hits": 350},
    {"pathPattern": "/shepard/api/timeseriesContainers", "hits": 97}
  ],
  "byPrincipal": [
    {"principalSub": "alice@example", "hits": 1200},
    {"principalSub": "anonymous", "hits": 47}
  ],
  "firstHitAt": "2026-05-22T08:14:23.000+00:00",
  "mostRecentHitAt": "2026-05-22T15:51:08.000+00:00"
}
```

What to look for:

- **`anonymous` hits** — clients without auth tokens hitting v1
  publicly-exposed endpoints. Usually scrapers or unmaintained
  scripts.
- **High-rate hits from one principal** — typically an integration
  pipeline. Identify whether it's something you control or whether
  a downstream team needs migration help.
- **`firstHitAt` recently** — fresh callers are still showing up;
  the surface isn't dormant yet.

Counters reset on every process restart; the underlying durable
audit row lands in the `:Activity` table via PROV1a for write
methods only (reads don't generate audit rows by design — they'd
flood the DB on high-rate clients).

## Read the current toggle

```bash
curl -H "X-API-KEY: <admin-key>" https://<host>/v2/admin/legacy/v1/config
```

Expected default:

```json
{
  "enabled": true,
  "appId": "01HF-AAA"
}
```

When `enabled` is true (default + shipping state), every
`/shepard/api/...` request flows through unchanged, plus three
additive headers:

```
Deprecation: true
Link: </v2/>; rel="successor-version"
X-Shepard-Legacy: true
```

These tell standard-aware clients and the shepard frontend banner
that the request flowed through the deprecated surface.

## Flip the v1 surface off (operator gesture)

You only do this when:

1. `/v2/admin/legacy/v1/stats` shows zero hits over the last
   week-or-so, or
2. The remaining callers are explicitly known + signed off as
   "they can use the 410 to discover they need to migrate".

```bash
curl -X PATCH \
  -H "X-API-KEY: <admin-key>" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled":false}' \
  https://<host>/v2/admin/legacy/v1/config
```

After this PATCH, every `/shepard/api/...` request returns:

```
HTTP/1.1 410 Gone
Content-Type: application/problem+json
Deprecation: true
Link: </v2/>; rel="successor-version"
X-Shepard-Legacy: true

{
  "type": "https://shepard.dlr.de/problems/v1-disabled",
  "title": "Legacy v1 surface disabled",
  "status": 410,
  "detail": "The legacy /shepard/api/... surface is disabled on this instance. Migrate to /v2/.",
  "instance": "/shepard/api/collections/42"
}
```

To restore (no data loss; the row flips back instantly):

```bash
curl -X PATCH \
  -H "X-API-KEY: <admin-key>" \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"enabled":true}' \
  https://<host>/v2/admin/legacy/v1/config
```

The runtime row wins forever after the first PATCH — deploy-time
`shepard.legacy.v1.enabled` is only the install default.

## See also

- `plugins/v1-compat/docs/reference.md` — every endpoint + every
  config field documented end-to-end
- `plugins/v1-compat/docs/install.md` — install / config / migration
- `docs/reference/v1-deprecation.md` — end-user-facing context for
  the frontend banner
