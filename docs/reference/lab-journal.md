---
layout: default
title: Lab journal entries
permalink: /reference/lab-journal/
---

# Lab journal entries

A **`LabJournalEntry`** is a free-text (CommonMark + GFM markdown, since J1a)
note attached to a `DataObject`. Researchers use journal entries to record
observations, hypotheses, and debrief notes alongside the data they relate to.

## Markdown rendering (J1a)

Since J1a, journal entries are interpreted as
[CommonMark](https://spec.commonmark.org/) + GitHub Flavored Markdown (GFM).
The render endpoint converts any entry's content to sanitised HTML on demand.
Plain-text entries written before J1a render correctly as `<p>` elements —
CommonMark passes plain text through unchanged. No data migration is required.

### Supported extensions

| Extension | Syntax | Output |
|---|---|---|
| GFM tables | `\| col \| col \|` | `<table>` |
| GFM strikethrough | `~~gone~~` | `<del>gone</del>` |
| GFM task list items | `- [x] done` | checkbox input |
| Fenced code blocks | ` ```python … ``` ` | `<pre><code>` |
| All standard CommonMark | bold, italic, links, headings, lists, blockquotes | standard HTML |

`javascript:` hrefs are stripped (`sanitizeUrls=true`) so the rendered HTML is
safe to embed in a sandboxed frame. Additional frame-level isolation (e.g.
`<iframe sandbox="…">`) is the client's responsibility.

## Render endpoint

```
GET /v2/lab-journal/{appId}/render
```

Returns the rendered HTML for a `LabJournalEntry` identified by its `appId`.

### Path parameters

| Parameter | Type | Description |
|---|---|---|
| `appId` | `string` | Application-level identifier (UUID v7) of the `LabJournalEntry`. |

### Content negotiation

| `Accept` header | Response `Content-Type` | Response body |
|---|---|---|
| `text/html` | `text/html; charset=utf-8` | Bare HTML string. |
| `application/json` (or absent) | `application/json` | `{"html": "…", "sourceLength": N}` |

`sourceLength` is the byte count of the original markdown source — useful for
caching or change-detection on the client side.

### Example — JSON envelope

```bash
curl -H "Authorization: Bearer <token>" \
     https://shepard.example.dlr.de/v2/lab-journal/<appId>/render
```

```json
{
  "html": "<p><strong>hello</strong></p>\n",
  "sourceLength": 9
}
```

### Example — bare HTML

```bash
curl -H "Authorization: Bearer <token>" \
     -H "Accept: text/html" \
     https://shepard.example.dlr.de/v2/lab-journal/<appId>/render
```

```html
<p><strong>hello</strong></p>
```

### Response codes

| Code | Meaning |
|---|---|
| 200 | Rendered HTML returned. |
| 401 | Authentication required. |
| 403 | Caller lacks Read permission on the parent DataObject. |
| 404 | No `LabJournalEntry` with that `appId`, or the entry is deleted. |

### Permission model

Read permission on the **parent `DataObject`** is required — the same gate as
`GET /shepard/api/labJournalEntries/{id}`.

## `LabJournalEntryIO` wire shape

The `LabJournalEntryIO` JSON shape (returned by the existing
`/shepard/api/labJournalEntries` endpoints) gains one additive read-only
field in J1a:

| Field | Type | Description |
|---|---|---|
| `contentFormat` | `"MARKDOWN"` (fixed) | Tells clients that the entry's `journalContent` is interpreted as CommonMark + GFM by the render endpoint. Always `"MARKDOWN"` for all entries. |

Existing clients using lenient JSON deserialisation (Jackson default,
Python `requests`, JS `fetch`) are unaffected by this new field.

## Finding an entry's `appId`

The `appId` is returned by every `GET /shepard/api/labJournalEntries` response
as a top-level field (populated since the L2a backfill). Entries created before
L2a may have `appId = null` — they won't resolve via the `/v2/` render endpoint
until they are updated (any write via PUT or PATCH stamps a fresh `appId`).

## Related

- `aidocs/37-lab-journal-and-jupyter-design.md` — design doc for the J1 series.
- J1b (queued) — inline `.ipynb` static render.
- J1c (queued) — "Open in Jupyter" deep link.
