---
title: Browse and manage collections
description: How to find your collections, explore their contents, and manage access — the starting point for any researcher using Shepard
permalink: /help/browse-collections/
layout: default
audience: user
---
# Browse and manage collections

A **Collection** in Shepard is the top-level folder for a research campaign,
experiment series, or dataset group. Everything else — DataObjects, references,
timeseries, files — lives inside a Collection. This page shows you how to find
your Collections, explore their contents, and share them with collaborators.

---

## Find your collections

From the top navigation bar click **Collections**. Shepard opens the collections list at `/collections`.

The list shows every Collection you have at least *read* access to, sorted by
last-updated time. Use the **search bar** at the top to filter by name. Click
any row to open the Collection.

If you expect to see a Collection but can't find it, it was either created with
a different access level (check with the owner) or your filter is hiding it.

---

## What you see on a Collection page

The Collection detail page has several panels that open in sequence as you scroll down:

| Panel | What it contains |
|---|---|
| **Header** | Name, description, status badge (DRAFT / READY / PUBLISHED / ARCHIVED), license and access-rights tags. Click the name or description to edit them (requires *write* access). |
| **Semantic Annotations** | Structured tags — process type, material, campaign metadata — applied by you, your team, or the AI assistant. Click **+** to add a new annotation. |
| **DataObjects** | The list of datasets in this Collection. Scroll, filter by status, or search by name. Click a row to open a DataObject. |
| **Lab Journal** | Timestamped notes and observations. Add an entry with **+ Entry** or use the AI wiki-writer to draft a structured summary. |
| **Lineage** | A graph showing every DataObject and the predecessor/successor links between them. Zoom in for labels; zoom out for structure. See [Navigate a large lineage graph]({% link help/lineage-graph.md %}). |
| **Cross-track view** | Overlay timeseries channels from multiple DataObjects on a single shared timeline. See [Cross-track view]({% link help/cross-track-view.md %}). |
| **Timeline** | A heatmap of DataObject creation and update activity over time. See [Collection timeline]({% link help/collection-timeline.md %}). |
| **Snapshots** | Point-in-time captures of the Collection's metadata graph. Use them for version control or to compare states. See [Compare snapshots]({% link help/compare-snapshots.md %}). |

---

## Create a collection

1. On the Collections list page click **+ New Collection** (top-right corner).
2. Fill in the **Name** (required) and an optional **Description**.
3. Choose a **Status** — start with *DRAFT* until the data is ready to share.
4. Click **Create**.

The new Collection appears at the top of your list. You are automatically its
owner with full write access.

---

## Add datasets (DataObjects)

Inside the Collection, click **+ Add DataObject** in the DataObjects panel. Each
DataObject represents one experiment run, one production step, or one measurement
session. After creating the DataObject you can attach references (files,
timeseries channels, structured data) to it from its own detail page.

For bulk ingestion of many DataObjects at once see
[Importing data with the import wizard]({% link help/observing-an-import.md %}).

---

## Share a collection

Click the **Roles** (🔒 lock icon) on the Collection header. The roles panel shows:

- **Owner** — full read/write/admin rights. Transferred, not shared.
- **Write** — can add, edit, and delete DataObjects and references.
- **Read** — can view everything but cannot make changes.

Enter a username or email in the **Grant access** field and choose a role.
Permissions take effect immediately. To revoke access, click the **×** next to
a user's entry.

Groups of users can be granted access by their user-group name instead of
individual usernames.

---

## Watch a collection for changes

Click the **bell icon** (🔔) in the Collection header to start watching. You will
receive a notification whenever a DataObject is added, a reference is updated, or
a lab-journal entry is posted. See [Get notified]({% link help/get-notified.md %}).

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Collection doesn't appear in the list | You don't have read access | Ask the Collection owner to grant you at least read access |
| Can't click **+ New Collection** | Your user account has no write permission on the instance | Contact your Shepard administrator |
| **Edit** pencil is greyed out | You have read-only access to this Collection | Ask the owner for write access |
| DataObjects panel is empty | Collection has no DataObjects yet, or the status filter is hiding them | Click **Reset** on the status filter pills |
| **+ Entry** is missing in Lab Journal | Lab journal is disabled on this instance | Ask your administrator to enable the lab-journal feature toggle |

---

## See also

- [Upload data to a DataObject]({% link help/upload-data.md %})
- [Annotating data with semantic tags]({% link help/annotating-data.md %})
- [Navigate a large lineage graph]({% link help/lineage-graph.md %})
- [Collection timeline]({% link help/collection-timeline.md %})
- [Cross-track view]({% link help/cross-track-view.md %})
- [Compare snapshots]({% link help/compare-snapshots.md %})
- [Collections reference]({% link reference/collections.md %}) — full entity model, API endpoints, and fork additions
