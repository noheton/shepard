---
stage: deployed
last-stage-change: 2026-07-18
audience: admin
layout: default
title: Instance registry
description: Operator reference for registering peer Shepard instances so cross-instance shepardIds render friendly names in the UI
permalink: /reference/instance-registry/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The feature shipped
> on 2026-05-27 (`FE-PROV-INSTANCE-REGISTRY`); this page documents its
> behaviour from the source as it stands at the backfill date.

<!-- backfill: DOCS-3A6/7 2026-07-18 -->

# Instance registry reference

**Feature ID:** FE-PROV-INSTANCE-REGISTRY  
**Admin route:** `PATCH /v2/admin/instances` (`instance-admin`)  
**Public route:** `GET /v2/instance/registry` (no auth)  
**Design doc:** `aidocs/frontend/100 §5 + §4.7`

---

## What the instance registry is

Shepard identifies every entity by a **shepardId** (`appId`, a UUID v7). Those
IDs are globally unique but not human-readable, and when data moves *between*
Shepard instances — an import from a peer institute's Shepard, a federated
provenance edge, a cross-instance reference — the UI would otherwise show a bare
instance id with no way to tell whose instance it came from.

The **instance registry** is the operator-curated lookup that maps a peer
Shepard's instance id to a friendly display name, base URL, and DLR institute.
The frontend's `useInstanceRegistry()` composable reads it to render tooltips
like *"Origin: DLR-SP Augsburg (shepard.zlp.example.org)"* instead of an opaque
id.

It follows the same **admin-configurable-at-runtime** pattern as the feature
toggle registry (A3b), semantic config (N1c2), and Unhide config (UH1a): a
single Neo4j singleton, an `instance-admin` `PATCH` surface, RFC 7396
merge-patch semantics, and a deploy-time-empty default (operators opt in).

---

## Data model

A single `:InstanceRegistry` Neo4j node (`HasAppId`, one instance-wide). The
registered peers are stored as a JSON array in the `instancesJson` property; the
REST surface exposes them as a typed list. Each entry:

| Field | Type | Meaning |
|---|---|---|
| `instanceId` | string | The peer Shepard's instance id (`shepard.instance.id`). The lookup key. |
| `displayName` | string | Human-facing name shown in tooltips and lists. |
| `baseUrl` | string | The peer's public base URL, used to build deep links back to the origin. |
| `dlrInstitute` | string | The owning DLR institute / department (free text, e.g. `DLR-SP`). |

The registry seeds **empty** on first start — nothing is registered until an
operator adds a peer. A `V91` uniqueness-constraint migration guards the
singleton.

---

## Endpoints

### Read the registry (public)

```
GET /v2/instance/registry
```

Unauthenticated (`@PermitAll`) — the registry holds only public identity
metadata (names + URLs), never per-entity data, so any client that renders
cross-instance references can resolve names without a token.

```json
{
  "instances": [
    {
      "instanceId": "zlp-augsburg",
      "displayName": "DLR-SP Augsburg (ZLP)",
      "baseUrl": "https://shepard.zlp.example.org",
      "dlrInstitute": "DLR-SP"
    }
  ]
}
```

### Update the registry (instance-admin)

```
PATCH /v2/admin/instances
Authorization: Bearer <instance-admin JWT>
Content-Type: application/merge-patch+json
```

RFC 7396 merge-patch against the singleton. The body carries the desired
`instances` array (the array is replaced wholesale, so send the complete list
you want to end up with):

```bash
curl -X PATCH \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/merge-patch+json" \
  https://shepard.example.com/v2/admin/instances \
  -d '{
    "instances": [
      { "instanceId": "zlp-augsburg", "displayName": "DLR-SP Augsburg (ZLP)",
        "baseUrl": "https://shepard.zlp.example.org", "dlrInstitute": "DLR-SP" },
      { "instanceId": "lampoldshausen", "displayName": "DLR-RA Lampoldshausen",
        "baseUrl": "https://shepard.ra.example.org", "dlrInstitute": "DLR-RA" }
    ]
  }'
```

Every successful `PATCH` is captured as an `:Activity` node by
`ProvenanceCaptureFilter` (admin mutations audit by default), so *"who changed
the instance registry, and when"* is one Cypher query away.

---

## Where it surfaces in the UI

- **`/admin/instance-registry`** — a standalone admin page with an add-form
  (`instanceId`, `displayName`, `baseUrl`, `dlrInstitute`) and a list table with
  a delete action (`AdminInstanceRegistryPane.vue`, backed by
  `useInstanceRegistryAdmin.ts`).
- **`/admin` hub** — the same pane is mounted as a tile so an operator reaches
  it from the admin landing grid.
- **Tooltips everywhere** — `useInstanceRegistry()` exposes a reactive
  `registryMap` that any component rendering a cross-instance reference reads to
  show the friendly name.

For the casual admin task ("I just want to add a peer"), see
`docs/help/register-peer-instances.md`.

---

## Precedence and defaults

- The registry is **runtime state** — there is no deploy-time
  `application.properties` seed for the peer list (unlike toggles or AAS
  config). It starts empty and is populated only via the admin surface.
- A missing registry entry is **not an error**: the UI falls back to showing the
  raw instance id. The registry is a display convenience, never a gate.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `PATCH /v2/admin/instances` returns 403 | API key / JWT lacks `instance-admin` | Rotate to an admin token; see `docs/admin/runbooks/14-role-grants.md` |
| A cross-instance reference still shows a raw id | Peer not registered, or `instanceId` mismatch | Confirm the peer's `shepard.instance.id` matches the registered `instanceId` exactly |
| New admin grant sees old registry state | Stale JWT role cache | Sign out and back in (see `ROLE-GRANT-STALE-SESSION`) |

---

## Cross-references

- `docs/help/register-peer-instances.md` — casual admin task page
- `docs/reference/nfdi4ing-federation.md` — the *outbound* federation surface (Unhide/NFDI4Ing); the instance registry is the *inbound* naming surface
- `docs/reference/admin-config.md` — the generic runtime-config registry pattern
- `aidocs/frontend/100 §5` — design doc
