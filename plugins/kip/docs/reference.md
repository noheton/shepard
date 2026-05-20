# Plugin: kip — Helmholtz KIP Resolver

Serves a public HMC Kernel Information Profile record for every PID
minted by this shepard instance.

## What it does

Adds a single unauthenticated endpoint that lets any HMC PID resolver
look up a published entity by its PID and receive a JSON-LD-flavoured
KIP record (landing page, digital object type, timestamps, rights holder,
version).

## Endpoint

| Method | Path | Auth |
|--------|------|------|
| `GET` | `/v2/.well-known/kip/{pid-suffix}` | None (public) |

**Success (200):**

```json
{
  "@context": "https://hmc.helmholtz.de/kip/v1",
  "id": "shepard:dlr.de/shepard-prod:data-objects:01HF...:v1",
  "kernelInformationProfile": {
    "id": "shepard:dlr.de/shepard-prod:data-objects:01HF...:v1",
    "landingPage": "https://shepard.example.org/v2/data-objects/01HF...",
    "digitalObjectType": "http://shepard.dlr.de/types/dlr:DataObject",
    "dateCreated": "2024-01-15T10:30:00Z",
    "dateModified": "2024-01-15T10:30:00Z",
    "rightsHolder": "alice",
    "digitalObjectVersion": "v1"
  }
}
```

**Not found (404):** RFC 7807 `application/problem+json` with
`type: https://shepard.dlr.de/problems/kip.pid.not-found`.

## Config keys

| Key | Default | Description |
|-----|---------|-------------|
| `shepard.plugins.kip.enabled` | `true` | Gates the plugin lifecycle hook visible in `GET /v2/admin/plugins`. |

No additional deploy-time config keys. The resolver builds landing-page
URLs from the incoming request's own scheme + host, so no base-URL key
is needed.

## How to enable

Include `shepard-plugin-kip` on the backend classpath (it is bundled in
the default `with-plugins` Maven profile). The endpoint is active as
soon as the backend starts — no further configuration required.

Verify via:
```
GET /v2/admin/plugins   # should include { "id": "kip", "version": "1.0.0-SNAPSHOT" }
```
