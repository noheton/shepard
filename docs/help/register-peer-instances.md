---
stage: deployed
last-stage-change: 2026-07-18
audience: admin
layout: default
title: Register a peer Shepard instance
description: Add a neighbouring institute's Shepard to the registry so cross-instance data shows a friendly name instead of a raw id
permalink: /help/register-peer-instances/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The feature shipped
> on 2026-05-27 (`FE-PROV-INSTANCE-REGISTRY`); this page documents its
> behaviour from the source as it stands at the backfill date.

<!-- backfill: DOCS-3A6/7 2026-07-18 -->

# Register a peer Shepard instance

When data crosses between two Shepard instances — you import a Collection from a
partner institute, or a provenance edge points at an entity that lives on
another Shepard — the origin is identified by an **instance id**. On its own
that id is opaque (`zlp-augsburg`, `lampoldshausen`). Registering the peer turns
it into a readable label wherever it appears: *"DLR-SP Augsburg (ZLP)"*.

This is a one-time-per-peer admin task. You need the **instance-admin** role.

## Where it lives

1. Open **`/admin`** (the admin hub) and click the **Instance Registry** tile —
   or go straight to **`/admin/instance-registry`**.
2. You'll see an **add-form** and a table of already-registered peers.

## Add a peer

Fill in the four fields and click **Add**:

| Field | What to enter | Example |
|---|---|---|
| **Instance id** | The peer's `shepard.instance.id` — ask their operator, or read it from their `GET /v2/instance/registry`. Must match **exactly**. | `zlp-augsburg` |
| **Display name** | The friendly name you want shown in tooltips. | `DLR-SP Augsburg (ZLP)` |
| **Base URL** | The peer's public URL — used to build deep links back to the origin. | `https://shepard.zlp.example.org` |
| **DLR institute** | The owning institute or department. | `DLR-SP` |

The change takes effect immediately — no restart. Reload any page showing a
cross-instance reference and the raw id is replaced by your display name.

## Remove a peer

Click the delete action on the peer's row. The reference falls back to showing
the raw instance id (never an error) until you re-add it.

## Why the id must match exactly

The registry is a lookup keyed on the instance id. If the peer's real
`shepard.instance.id` is `zlp-augsburg` but you register `zlp_augsburg`, the
lookup misses and the tooltip stays raw. When in doubt, curl the peer:

```bash
curl https://shepard.zlp.example.org/v2/instance/registry
```

and copy the `instanceId` values verbatim.

## Doing it from a script

The registry is just an admin endpoint — everything the UI does is callable via
REST. See `docs/reference/instance-registry.md` for the
`PATCH /v2/admin/instances` merge-patch shape.

## See also

- `docs/reference/instance-registry.md` — full API + data-model reference
- `docs/help/importing-from-dlr-cube3.md` — importing data from another instance
- `docs/admin/runbooks/14-role-grants.md` — granting the `instance-admin` role
