---
layout: default
title: Work with Data Objects
permalink: /help/work-with-data-objects/
audience: basic
---
# Work with Data Objects

A **DataObject** is a single dataset record inside a Collection — one
experiment run, one manufacturing step, one measurement session, one simulation
case. Think of it as a folder-with-metadata that holds all the data (timeseries,
files, structured data, video) recorded during that event, plus a provenance
trail linking it to related events.

---

## Open a Data Object

1. Click **Collections** in the top navigation bar.
2. Select the Collection the DataObject lives in.
3. Scroll to the **DataObjects** panel and click the row you want.

You can also search by name inside the panel using the search field.

---

## Create a Data Object

1. Inside a Collection, click **+ Add DataObject** in the DataObjects panel.
2. Fill in:
   - **Name** (required) — a short label such as `TR-004` or `AFP-LayupStep-Q1-2026`.
   - **Description** (optional) — a free-text summary of what was recorded.
   - **Status** (optional, defaults to `DRAFT`) — see the lifecycle table below.
3. Optionally choose a **Template** to pre-populate expected annotations and
   reference slots. See [Build a template]({% link help/build-a-template.md %}).
4. Click **Create**.

The DataObject appears in the list immediately. Its `appId` (UUID) is shown in
the detail-page header and in the URL; copy it with the clipboard icon.

---

## Edit name, description, or status

On the DataObject detail page:

- Click the **name** or **description** text to edit it in-place (requires
  write access to the Collection).
- Click the **status chip** to open the status picker and select a new status.

Changes save automatically when you click away or press **Enter**.

---

## Status lifecycle

| Status | Meaning |
|---|---|
| `DRAFT` | Work in progress — data is being collected or reviewed. Default for new DataObjects. |
| `IN_REVIEW` | Under peer or quality review; write access may be restricted by policy. |
| `READY` | Data is complete and has passed internal checks. |
| `PUBLISHED` | Formally published; a persistent identifier (PID) may have been minted. |
| `ARCHIVED` | Read-only long-term archive. |

> **Quality engineering statuses** (`NCR_OPEN`, `ON_HOLD`, `CERTIFIED`, etc.) require
> the `quality-engineer` role and are described in the
> [Data Objects reference](/reference/data-objects/#quality-lifecycle-statuses-mfg1--qm1a).

---

## Add data (files, timeseries, structured data)

From the DataObject detail page, use the **References** panel to attach data:

- **Files** — drag and drop one file (singleton) or multiple files (bundle).
  See [Upload data]({% link help/upload-data.md %}).
- **Timeseries channels** — from a Timeseries Container already linked to
  this DataObject. See [Timeseries plotting]({% link help/timeseries-plotting.md %}).
- **Structured data** — JSON/YAML documents attached as structured-data references.
  See [Structured data]({% link help/structured-data.md %}).
- **Video** — from the Video References panel (requires the video plugin).
  See [Upload and annotate video]({% link help/upload-and-annotate-video.md %}).

---

## Link to related Data Objects

To show that one DataObject follows from another (e.g. a re-test after a repair):

1. Open the DataObject detail page.
2. Click the **Predecessor** tab or **+ Add predecessor** in the provenance panel.
3. Search by name and select the predecessor DataObject.
4. Optionally set the **relationship type** (`revision`, `rework`, `concession`)
   for typed provenance chains.

The relationship appears in the Collection's **Lineage** graph and in the DataObject's
breadcrumb trail. See [Link datasets with relationships]({% link help/link-datasets.md %}).

---

## Annotate a Data Object

Add semantic tags (material grade, process type, campaign metadata) via the
**Annotations** panel on the detail page. Click **+ Add annotation** and pick a
predicate from the vocabulary browser. See [Annotating data]({% link help/annotating-data.md %}).

---

## Delete a Data Object

1. Open the DataObject detail page.
2. Click the **⋮** (more) menu in the top-right corner.
3. Choose **Delete DataObject**.
4. Confirm in the dialog.

Deleting a DataObject also removes all its References and their associated
payloads. The deletion is captured in the provenance log. This action requires
write access to the Collection.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| **+ Add DataObject** button is missing | You have read-only access | Ask the Collection owner for write access |
| Name edit doesn't save | Your session may have expired | Refresh the page and try again |
| Status change is blocked | Status requires the `quality-engineer` role (NCR / certified statuses) | Ask your administrator to assign the role |
| DataObject doesn't appear after creation | The status filter may be hiding it | Reset the status filter pills in the DataObjects panel |
| Predecessor search shows no results | The predecessor DataObject is in a different Collection | DataObjects can only link to predecessors within the same Collection |
| AppId copy button is missing | Feature requires a modern browser | Use the URL bar (the DataObject's appId is the last UUID segment) |

---

## See also

- [Upload data to a Data Object]({% link help/upload-data.md %})
- [Link datasets with relationships]({% link help/link-datasets.md %})
- [Navigate a lineage graph]({% link help/lineage-graph.md %})
- [Annotating data]({% link help/annotating-data.md %})
- [Browse and manage collections]({% link help/browse-collections.md %})
- [Build a template]({% link help/build-a-template.md %})
- **Reference:** [Data Objects](/reference/data-objects/) — full entity model, API endpoints, quality-lifecycle statuses, and fork additions
