---
title: kip — Install
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

# kip — install

`shepard-plugin-kip` adds a single unauthenticated endpoint that
serves a public Helmholtz Kernel Information Profile (KIP) record
for every PID minted by the shepard instance. Install is one
flag-flip — no sidecars, no credentials, no migrations.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven
  profile (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-kip-${revision}.jar`.
- At least one active `Minter` plugin (the default
  [`minter-local`](../../minter-local/docs/install.md) is fine;
  `minter-datacite` and `minter-epic` work equally).

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.plugins.kip.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |

No additional deploy-time keys are introduced. The resolver builds
landing-page URLs from the incoming request's own scheme + host,
so no base-URL key is needed.

The plugin is **on by default** — operators who want the resolver
silenced can flip the toggle, but most installs leave it as-is so
external PID resolvers can dereference shepard's PIDs without
extra configuration.

---

## Endpoint surface

The plugin contributes one path:

```
GET /v2/.well-known/kip/{pid-suffix}
```

Public — no auth required. Returns a JSON-LD-flavoured KIP record
describing the entity behind the PID. Returns 404 RFC 7807
`kip.pid.not-found` when the PID has no matching `:Publication`
row in Neo4j.

---

## Healthcheck

Mint a PID for any entity (see
[`minter-local/quickstart.md`](../../minter-local/docs/quickstart.md))
then dereference it through the resolver:

```bash
SHEPARD_URL=https://shepard-api.nuclide.systems
PID_SUFFIX="dlr.de/shepard-prod:collections:01HF...:v1"

curl -s "$SHEPARD_URL/v2/.well-known/kip/$PID_SUFFIX" | jq .
```

Returns the KIP record. A 404 confirms the route is wired —
trying with an unknown PID confirms the not-found branch fires.
Either response means the plugin is operational.

---

## Disabling the plugin

```properties
shepard.plugins.kip.enabled=false
```

Or at runtime via admin REST:

```http
PATCH /v2/admin/plugins/kip
{"enabled": false}
```

When disabled, the `/v2/.well-known/kip/*` path returns 404 for
every request. Existing PIDs are still resolvable through their
issuing minter's own resolver (e.g. DataCite Commons for
DOI-minted PIDs).

---

## Known pitfalls

- **Reverse-proxy stripping `.well-known`**. Some HTTP proxies
  treat `.well-known/*` specially. Verify your nginx /
  traefik / Zoraxy configuration routes `/v2/.well-known/kip/*`
  to the backend — a 404 from the proxy looks identical to a
  404 from the resolver.
- **Trailing slashes**. The PID suffix may contain colons,
  slashes, and percent-encoded characters. Use a literal HTTP
  client (`curl`, not `wget --max-redirect=0`) to debug —
  some clients normalise paths and lose data.
- **Schema version**. The plugin emits the `v1` KIP profile
  shape. Helmholtz publishes the profile spec at
  <https://docs.hmc.helmholtz.de/kernel-information-profile/>;
  future profile bumps will be tracked under a `KIP1g-vN`
  follow-up.

---

## Verify

```bash
curl -s -H "X-API-KEY: $SHEPARD_API_KEY" \
  "$SHEPARD_URL/v2/admin/plugins" | \
  jq '.[] | select(.id == "kip")'
```

Should return:

```json
{ "id": "kip", "version": "1.0.0-SNAPSHOT", "state": "ENABLED" }
```

---

## See also

- [`reference.md`](reference.md) — endpoint contract + record shape.
- [`quickstart.md`](quickstart.md) — resolve a PID in 30 seconds.
- [Helmholtz KIP spec](https://docs.hmc.helmholtz.de/kernel-information-profile/)
  — upstream profile definition.
- `aidocs/integrations/66-publish-and-pids.md §KIP1g` — design rationale.
