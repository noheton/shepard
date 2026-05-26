---
layout: default
title: Instance-wide activity log (admin)
permalink: /help/admin-activity-log/
audience: admin
---
# Instance-wide activity log

Instance administrators can see a real-time feed of every provenance activity across
the entire shepard instance — who accessed or mutated what, and when.

## Where it lives

Open **Admin** in the main navigation, then choose **Activity Log** from the left sidebar
(or from the Admin landing page). The URL fragment is `/admin#activity-log`.

This section is restricted to instance administrators. Regular users can see their own
activity on individual Collection pages (see
[Monitor Collection activity](monitor-collection-activity.md)).

## Server-side filters

The **Server-side filters** card controls which rows the backend returns.
After adjusting filters, click **Apply** (or press Enter in a text field).

| Filter | What it does |
|---|---|
| **Actor username** | Restrict rows to a specific user's account name. |
| **Target kind** | Restrict to a specific entity type (Collection, DataObject, FileReference, …). |
| **Target App ID** | Restrict to a specific entity by its stable UUID. |

Click **Reset** to clear all three filters and reload the default view (most recent
100 activities across all users).

## Client-side filters

Once rows are loaded, two further filters apply locally without re-fetching:

- **Action kind chips** (CREATE / UPDATE / READ / DELETE / EXECUTE) — click a chip
  to toggle visibility for that action type. All are selected by default.
- **Free-text search** — matches against actor username, summary, HTTP path, and
  target kind. Useful for quickly isolating a specific endpoint or actor.

The footer shows "Showing N of M loaded rows" — if N is unexpectedly small, check
that the relevant action kinds are selected.

## Loading more rows

The log loads 100 rows at a time. When more rows are available, a **Load more** button
appears at the bottom of the table. Each click loads another 100 rows.

> **Tip:** For large time spans, use the Target App ID or Actor username filters to
> narrow the set before loading more rows — this gives faster, more relevant results.

## Reading the table

| Column | Meaning |
|---|---|
| **When** | Relative time (e.g. "3 min ago"). Hover for the exact ISO timestamp. |
| **Action** | Colour-coded chip: green=CREATE, blue=UPDATE, grey=READ, red=DELETE, orange=EXECUTE. |
| **Actor** | Username of the authenticated user who made the request. |
| **Target** | Entity kind (e.g. "DataObject") and its stable App ID. |
| **Summary** | Server-generated description plus the HTTP method and path for debugging. |
| **Status** | HTTP response status code (green=2xx, orange=4xx, red=5xx). |

## What is captured — and what is not

- **Write operations** (POST / PUT / PATCH / DELETE) are always captured.
- **Read operations** (GET) are captured only if
  `shepard.provenance.capture-reads=true` is set in the deployment config.
  By default, read requests do not appear in the log.
- Request bodies and headers are never echoed — the `summary` is generated
  server-side from the method and target metadata.
- **Permission changes** appear as `actionKind=UPDATE / targetKind=Permissions`
  rows. For the full who-granted-what diff, use the
  [Permission Audit Log](../admin/security.md) instead.

## Retention

By default shepard keeps activity rows for two years. The operator can adjust
this via `shepard.provenance.retention-days` in `application.properties`
(negative value = keep forever). Rows older than the configured window are pruned
nightly.

## Programmatic access

The same data is accessible via REST. Instance admins see all rows; other users
see only their own:

```
GET /v2/provenance/activities?agent=alice&targetKind=DataObject&limit=200
Authorization: Bearer <token>
```

Supported filters: `agent`, `targetKind`, `targetAppId`, `since` (epoch ms),
`until` (epoch ms), `limit` (max 1000, default 100).

For PROV-O or metadata4ing (m4i) serialisations, see the
[Provenance reference](../reference/provenance.md).
