---
stage: deployed
last-stage-change: 2026-07-18
audience: admin
layout: default
title: Provenance dashboard & capture config
description: Operator guide to the instance-wide provenance activity dashboard and the runtime provenance-capture knobs
permalink: /admin/provenance-dashboard/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). Documented from the
> source (`ProvenanceRest.java`, `ProvenanceConfigDescriptor.java`) as it
> stands at the backfill date.

<!-- backfill: DOCS-3A6/7-sweep2 2026-07-18 -->

# Provenance dashboard & capture config

Two operator-facing halves of the provenance subsystem:

1. **The dashboard** — an instance-wide activity view at **Admin → Provenance
   Dashboard** (`/admin/provenance`).
2. **The capture knobs** — runtime settings for *whether* and *how long*
   provenance is recorded, at `/v2/admin/config/provenance`.

For the provenance *data model* (what an `:Activity` is, the query/count
surface, PROV-O export), see [Provenance](../reference/provenance.md). This page
is the admin operational view only.

---

## The dashboard

**Route:** `/admin/provenance` — reachable from the `/admin` hub tile
"Provenance Dashboard".

The dashboard renders the **activity sparkline** for the whole instance: how
many `:Activity` rows were recorded over a window, driven by:

```
GET /v2/provenance/stats?scope=instance
```

| Param | Notes |
|---|---|
| `scope` | `instance` (admin-only, shown here), `collection`, or `user`. |
| `subject` | Collection appId (`scope=collection`) or username (`scope=user`). Ignored for `scope=instance`. |
| `since` / `until` | ISO-8601 window bounds. **Default window = last 90 days** when omitted. |

`scope=instance` requires the `instance-admin` role (`403` otherwise). The same
endpoint powers the per-collection activity sparkline that non-admins see on a
Collection detail page — see
[Monitor collection activity](../help/monitor-collection-activity.md).

> The dashboard shows *counts and trend*. To read the individual activity rows
> (who did what, when), use the **Admin → Activity Log**
> ([instance-wide activity log](../help/admin-activity-log.md)) or the
> [provenance query surface](../reference/provenance.md).

---

## Capture configuration — `/v2/admin/config/provenance`

Provenance capture is runtime-configurable through the generic
[admin config registry](../reference/admin-config.md). The feature key is
`provenance`.

```bash
# Read current settings
curl -s -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.org/v2/admin/config/provenance
```

| Field | Type | Meaning |
|---|---|---|
| `enabled` | Boolean | Master switch for provenance capture. When `false`, the `ProvenanceCaptureFilter` stops writing `:Activity` rows. |
| `captureReads` | Boolean | Whether read (GET) requests are captured, not just mutations. Off keeps the trail focused on changes; on gives a full access log at higher volume. |
| `retentionDays` | Long (> 0) | How long activity rows are retained. Must be greater than 0; set to `null` to revert to the deploy-time default (`shepard.provenance.retention-days`). |

RFC 7396 merge-patch semantics apply — absent field = leave alone, explicit
`null` = revert to the deploy-time default, value = replace.

```bash
# Turn off read-capture and set a 365-day retention window
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"captureReads": false, "retentionDays": 365}' \
  https://shepard.example.org/v2/admin/config/provenance
```

- An invalid `retentionDays` (`<= 0`) is rejected with a `400` naming the
  deploy-time default it would otherwise fall back to.
- **Precedence:** the runtime `:ProvenanceConfig` singleton wins; the
  deploy-time `shepard.provenance.*` keys only seed it on first start. Flipping
  a field here does not require a restart.
- Capture is a **secondary, fire-and-forget write** — turning it off never
  affects the primary operation; the trail simply stops growing.

---

## See also

- [Provenance](../reference/provenance.md) — the `:Activity` model, query, count,
  and PROV-O/PROV-N export.
- [Instance-wide activity log](../help/admin-activity-log.md) — the row-level
  admin view.
- [Admin config registry](../reference/admin-config.md) — the generic
  `/v2/admin/config/{feature}` surface this config rides on.
- [Monitor collection activity](../help/monitor-collection-activity.md) — the
  non-admin, per-collection view of the same stats.
