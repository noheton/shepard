---
stage: deployed
last-stage-change: 2026-07-18
audience: user
layout: default
title: Bulk DataObject creation
description: Developer reference for POST /v2/data-objects/batch — create up to 500 DataObjects across collections in one HTTP 207 call
permalink: /reference/batch-data-objects/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). The endpoint shipped
> as `MFFD-BATCH-01`; this page documents its behaviour from the source
> (`DataObjectBatchV2Rest.java`) as it stands at the backfill date.
>
> The matching admin UI tile ("Bulk DataObject creation") is still a
> **placeholder pane** (`PLACEHOLDER-REPLACE-MFFD-BATCH-01-UI`), so this
> is an **API-only** surface today. Drive it from a script or the API, not
> the UI.

<!-- backfill: DOCS-3A6/7-sweep2 2026-07-18 -->

# Bulk DataObject creation

**Feature ID:** MFFD-BATCH-01
**Route:** `POST /v2/data-objects/batch` (authenticated)
**Response:** `207 Multi-Status`

---

## What it is

The batch endpoint creates **1–500 DataObjects in a single request**, optionally
targeting **more than one Collection** in the same call. It exists for
MFFD-scale imports where issuing one `POST` per DataObject would mean hundreds
of round-trips.

It is deliberately rooted at `/v2/data-objects/batch` — not under a Collection
path — because a single batch may span collections. Each item names its own
target Collection.

Unlike a transactional bulk insert, items are processed **sequentially and
independently**: a failure on item _N_ does **not** stop items _N+1…M_. You
always get a per-item outcome back.

---

## Request

`Content-Type: application/json`, body is a **JSON array** of item objects.

| Field | Required | Type | Notes |
|---|---|---|---|
| `collectionAppId` | ✅ | UUID v7 string | Target Collection. Caller needs **Write** permission on it. |
| `name` | ✅ | string | Non-blank DataObject name. |
| `description` | — | string | Free text. |
| `parentAppId` | — | UUID v7 string | An existing DataObject to use as the hierarchical parent. |
| `attributes` | — | object (string→string) | Legacy free-text attribute bag. Prefer [semantic annotations](semantic-annotations.md) for new metadata. |
| `status` | — | string | Free-form. Suggested vocabulary: `DRAFT` / `IN_REVIEW` / `READY` / `PUBLISHED` / `ARCHIVED` / `NCR_OPEN` / `ON_HOLD` / `REJECTED` / `CERTIFIED`. The quality statuses (`NCR_OPEN`, `ON_HOLD`, `REJECTED`, `CERTIFIED`) require the `quality-engineer` role (MFG1). |

**Size limits.** The array must contain **1–500 items**. An empty array or one
over 500 items returns `400` **before any item is processed**.

### Example body

```json
[
  { "collectionAppId": "018f5e2a-…", "name": "TR-001" },
  { "collectionAppId": "018f5e2a-…", "name": "TR-002", "attributes": { "campaign": "Q3" } },
  { "collectionAppId": "018f7c91-…", "name": "AFP-ply-05", "parentAppId": "018f7c40-…" }
]
```

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @batch.json \
  https://shepard.example.org/v2/data-objects/batch
```

---

## Response — `207 Multi-Status`

A valid-sized batch **always** returns `207`, even when every item failed. The
body carries a per-item `results[]` array plus roll-up counters.

```json
{
  "created": 2,
  "failed": 1,
  "results": [
    { "index": 0, "status": "created", "appId": "018f9a01-…" },
    { "index": 1, "status": "created", "appId": "018f9a02-…" },
    { "index": 2, "status": "error", "errorCode": "PARENT_NOT_FOUND",
      "errorMessage": "parentAppId 018f7c40-… does not resolve to a DataObject." }
  ]
}
```

- `created + failed == results.size()`.
- Each entry echoes its `index` (0-based, matching the request array), a
  `status` of `created` or `error`, and either `appId` (success) or
  `errorCode` + `errorMessage` (failure).

### Per-item error codes

| `errorCode` | Meaning |
|---|---|
| `COLLECTION_NOT_FOUND` | `collectionAppId` does not resolve to a Collection. |
| `FORBIDDEN` | Caller lacks Write permission on the target Collection. |
| `PARENT_NOT_FOUND` | `parentAppId` does not resolve to a DataObject. |
| `INVALID_INPUT` | Item was null, or a required field (`collectionAppId` / `name`) was missing/blank. |
| `INTERNAL_ERROR` | Unexpected server-side failure creating that item. |

### Request-level status codes

| Code | When |
|---|---|
| `207` | Batch was valid-sized and processed (inspect `results[]` per item). |
| `400` | Array empty, over 500 items, or body is not an array. |
| `401` | Not authenticated. |
| `500` | Genuine server error (not an item-level failure). |

---

## Behaviour notes

- **Permission memoisation.** Write-permission on a Collection is checked once
  per Collection per batch and cached — repeated items targeting the same
  Collection do not re-hit the permissions service.
- **Provenance.** One `CREATE` `:Activity` is recorded for the whole batch
  request (the `207` is in the 2xx range, so `ProvenanceCaptureFilter` fires
  once). Per-item provenance granularity is a deferred `PROV2` item — see
  [Provenance](provenance.md).
- **Ordering.** Items are created in array order; `parentAppId` must reference
  a DataObject that already exists (an earlier batch, not a later item in the
  same array).

---

## See also

- [DataObjects](data-objects.md) — the single-create surface and full field set.
- [Import & validate](import-validate.md) — the plan-seal importer for whole-tree
  ingests (files + references + hierarchy), the right tool when you have more
  than bare DataObjects to create.
- [Provenance](provenance.md) — what the batch `:Activity` records.
