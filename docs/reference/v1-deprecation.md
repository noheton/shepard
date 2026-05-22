# Legacy v1 API deprecation

shepard's upstream-frozen API surface lives at `/shepard/api/...`.
That path is the byte-compat contract with `gitlab.com/dlr-shepard/shepard`
5.2.0; every byte of every response is identical to upstream so a
client built against upstream keeps working against this fork.

The fork's own additions land under `/v2/...`. Operators choose
when their instance retires the `/shepard/api/...` surface; the
fork does NOT impose a sunset timeline.

## What you'll see in the UI

When any request in your current session has flowed through the
`/shepard/api/...` surface, the frontend shows a non-alarming
banner:

> You're using the legacy v1 surface. Disable in admin → Legacy v1
> when your tools no longer need it.

The banner is informative, not blocking. You can keep using the v1
surface indefinitely — it works exactly as it always has. The
banner exists so you can see, at a glance, that you (or a tool you
have open) is still on the v1 surface.

If you don't see the banner: nothing in your session has gone
through v1. Everything you're using is on the modern `/v2/...`
surface.

## What changed in the response headers

Every `/shepard/api/...` response now carries three additive
headers:

| Header | Value | Standard |
|---|---|---|
| `Deprecation` | `true` | RFC 8594 |
| `Link` | `</v2/>; rel="successor-version"` | RFC 8594 |
| `X-Shepard-Legacy` | `true` | Fork-specific |

A deprecation-aware HTTP client picks these up automatically. The
shepard frontend banner keys on `X-Shepard-Legacy`. None of these
headers change response bodies or status codes — they are strictly
additive.

## What an operator can do

An instance-admin can flip the v1 surface off via the runtime
admin API (see `plugins/v1-compat/docs/quickstart.md` for the full
runbook). When it's off, every `/shepard/api/...` request returns
HTTP 410 Gone + an RFC 7807 problem-detail body pointing at
`/v2/...`. The runbook recommends inspecting the
`/v2/admin/legacy/v1/stats` endpoint first — it shows which clients
have been hitting v1, when, and how often.

## What an admin can see

`GET /v2/admin/legacy/v1/stats` (instance-admin only) returns
in-memory hit counters since the last shepard restart:

- total hits across the v1 surface
- top N endpoints by hit count
- top N principals by hit count
- first + most-recent hit timestamps

This is the data you need to decide whether v1 still has live
callers worth waiting for before flipping it off.

## What an admin should NOT do

Don't flip `:LegacyV1Config.enabled=false` on a live system without
first reading the stats endpoint and confirming the remaining
callers can absorb a 410. The Phase 1 design trusts the admin to
read the stats first — the Phase 2 design will add a confirmation
gate.

Don't disable the plugin (`shepard.plugins.v1-compat.enabled=false`)
as an alternative to flipping the singleton. With the plugin off,
the v1 surface is on (no gating), the admin REST + stats endpoint
disappear, and you lose the audit trail.

## When v1 retires (someday)

`aidocs/platform/103` describes Phase 2: the eventual full move of
the v1 REST classes themselves into the plugin JAR. Phase 2 keeps
v1 byte-compatible while it's on; an operator who has run with
`:LegacyV1Config.enabled=false` for a sustained period and seen no
410s can then build a smaller image with
`-DnoPlugins -Pwithout=v1-compat` (the actual flag depends on what
Phase 2 ships).

There is no calendar date for v1 retirement. The decision is per-
instance and per-operator.

## See also

- `plugins/v1-compat/docs/quickstart.md` — operator tasks
- `plugins/v1-compat/docs/reference.md` — full endpoint reference
- `aidocs/34-upstream-upgrade-path.md` — upstream-upgrade ledger
- `aidocs/platform/103a-v1-compat-marker-plugin.md` — Phase 1 design
