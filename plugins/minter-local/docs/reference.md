# Plugin: minter-local — Local PID Minter

Mints stable, versioned, instance-local persistent identifiers without
any external service — the default minter for fresh shepard installs.

## What it does

Implements the `Minter` SPI with id `local`. On each publish call,
produces a PID of the form:

```
shepard:<instance.id>:<kind>:<appId>:v<n>
```

- `<instance.id>` — from `shepard.instance.id` (e.g. `dlr.de/shepard-prod`)
- `<kind>` — entity kind segment (`data-objects`, `collections`, …)
- `<appId>` — the entity's UUID v7
- `v<n>` — version number; increments on each `POST /publish?force=true`

Same entity + same version always produces the same PID (stable, no
timestamp randomness). The minter never calls an external service and
never throws `MinterException`.

A startup `WARN` is logged when `shepard.instance.id` is unset or blank
and the fallback `local` is used — production installs must set a
namespaced value to avoid PID collisions across instances.

## Config keys

| Key | Default | Description |
|-----|---------|-------------|
| `shepard.publish.minter` | `local` | Selects this minter. Must be `local` to activate. |
| `shepard.instance.id` | `local` | Namespace embedded in every minted PID. Set to a namespaced string (e.g. `dlr.de/shepard-prod`) before minting any PIDs in production. |
| `shepard.plugins.minter-local.enabled` | `true` | Gates the plugin lifecycle hook in `GET /v2/admin/plugins`. |

## How to enable

`minter-local` is the out-of-the-box default. Include
`shepard-plugin-minter-local` on the backend classpath (bundled in the
`with-plugins` Maven profile) and ensure:

```properties
shepard.publish.minter=local
shepard.instance.id=your.org/your-instance-name
```

Verify via:
```
GET /v2/admin/plugins   # should include { "id": "minter-local", "version": "1.0.0-SNAPSHOT" }
```
