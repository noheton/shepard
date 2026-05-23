---
title: minter-local — Install
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

# minter-local — install

`shepard-plugin-minter-local` is the **default PID minter** that ships
with every shepard install. It produces stable, versioned,
instance-local identifiers without contacting any external service.
Operators only need to verify two config keys.

---

## Prerequisites

- A shepard backend image built with the `with-plugins` Maven profile
  (the default). The plugin JAR is already in
  `/deployments/plugins/shepard-plugin-minter-local-${revision}.jar`.
- A non-default `shepard.instance.id` chosen for the deployment.
- No external services, no sidecars, no credentials.

---

## Configuration keys

| Key | Default | Description |
|---|---|---|
| `shepard.publish.minter` | `local` | Selects this minter. Must be `local` to activate. |
| `shepard.instance.id` | `local` | Namespace embedded in every minted PID. Set to a unique value (e.g. `dlr.de/shepard-prod`) before minting any production PIDs. |
| `shepard.plugins.minter-local.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |

A startup `WARN` is logged when `shepard.instance.id` is unset or
equal to the fallback `local` — production installs must override
this to avoid PID collisions across instances.

Minimal `application.properties`:

```properties
shepard.publish.minter=local
shepard.instance.id=dlr.de/shepard-prod
```

---

## PID format

Every PID has the shape:

```
shepard:<instance.id>:<kind>:<appId>:v<n>
```

For example:

```
shepard:dlr.de/shepard-prod:data-objects:019e1cee-654f-7554-8543-0ba62ae14113:v1
```

Re-publishing an entity (`POST .../publish?force=true`) increments
`<n>` while keeping the entity's `appId` constant. The PID is
deterministic — same `(instance.id, kind, appId, n)` always returns
the same string.

---

## Healthcheck

```bash
curl -X POST \
  -H "X-API-KEY: $SHEPARD_API_KEY" \
  https://shepard-api.nuclide.systems/v2/data-objects/<some-appId>/publish
```

Returns a fresh local PID under `publication.pid`. There is no
external connectivity to test — if the publish endpoint responds
2xx, the minter is operational.

---

## Switching to a different minter

To replace `local` with the DataCite or ePIC plugins, flip
`shepard.publish.minter`:

```properties
shepard.publish.minter=datacite   # or: epic
```

`shepard.publish.minter` is a deploy-time-only key (per the
CLAUDE.md "cluster identity / topology" exception) — restart the
backend after flipping. PIDs already minted under `local` remain
resolvable forever via the bundled
[KIP resolver](../../kip/docs/reference.md); the switch only
affects future mints.

---

## Disabling the plugin

```properties
shepard.plugins.minter-local.enabled=false
```

When disabled while `shepard.publish.minter=local` is still set,
every `POST /v2/{kind}/{appId}/publish` returns 503
`publish.minter.not-installed`. Either flip the toggle back on or
switch `shepard.publish.minter` to another active minter.

---

## Known pitfalls

- **`shepard.instance.id=local` in production**. PIDs minted with
  the fallback are collision-prone across institutes — every
  shepard install on default config produces identical PIDs for
  the same entity. Always set a namespaced value.
- **Changing `shepard.instance.id` post-mint**. Existing PIDs in
  `:Publication` rows keep their old namespace; new mints use the
  new one. The chain of PIDs for an entity becomes split-namespace
  if you rotate mid-deployment. Migration is a manual `MATCH
  (p:Publication) ... SET p.pid = ...` Cypher rewrite — none ships
  out of the box.

---

## Verify

```bash
curl -H "X-API-KEY: $SHEPARD_API_KEY" \
  https://shepard-api.nuclide.systems/v2/admin/plugins | \
  jq '.[] | select(.id == "minter-local")'
```

Should return:

```json
{
  "id": "minter-local",
  "version": "1.0.0-SNAPSHOT",
  "state": "ENABLED"
}
```

---

## See also

- [`reference.md`](reference.md) — full plugin reference.
- [`quickstart.md`](quickstart.md) — mint your first PID.
- `aidocs/integrations/66-publish-and-pids.md` — KIP1 design.
