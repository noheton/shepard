---
title: Store and browse structured data (JSON)
description: How to create a Structured Data container, upload JSON objects, attach them to a DataObject, and view or edit the payload
permalink: /help/structured-data/
layout: default
audience: user
---
# Store and browse structured data (JSON)

**What this is for.** A **Structured Data container** holds any number of
JSON objects — test configuration records, measurement summaries, material
property sheets, anything that arrives as a `.json` file. Once uploaded, the
objects are searchable, viewable in-browser, and downloadable. Attaching a
**Structured Data reference** to a DataObject creates a permanent, provenance-
tracked link between the dataset and its JSON metadata.

**Before you start.** You need *write* access on the Collection to create
containers and references.

---

## Create a Structured Data container

1. Navigate to **Containers** in the top navigation.
2. Click **Create container** and choose **Structured Data**.
3. Give the container a name (e.g. `TR-004-config-records`) and an optional
   description.
4. Click **Create**.

The container page opens. It starts empty; you will upload objects in the
next step.

---

## Upload JSON objects

1. Open the Structured Data container page.
2. Click **Upload JSON** (top right).
3. Select one or more `.json` files from your machine. Non-JSON files are
   silently ignored by the filter.
4. Shepard reads each file, validates that the top-level value is a JSON
   object (`{…}`), and stores it. Primitive values (`"string"`, `123`, `[]`)
   are rejected with an error.

Each uploaded object appears in the table with its **Name** (the filename),
an **Oid** (the internal content identifier, copyable via the clipboard icon),
and a **Created at** timestamp.

> **Tip:** You can upload multiple files in one dialog — hold Ctrl or Cmd to
> select more than one.

---

## Browse and view objects

The container table lists all JSON objects sorted alphabetically by name.
To inspect a payload:

- Click the **eye icon** (👁) on any row to open the payload in a read-only
  JSON viewer with syntax highlighting.
- Use browser-level search (Ctrl+F) inside the viewer to locate a key.

---

## Edit an object's payload

You can update an existing JSON object's content from either the container
page or from a reference detail page:

1. Click the **pencil icon** (✏) on the row you want to update.
2. The **Edit Structured Data Payload** dialog opens with a full JSON editor.
3. Make your changes.  The editor flags syntax errors in red — you cannot save
   while the JSON is malformed.
4. Click **Save as new version**.

> **Note:** Saving creates a *new* entry in the same container; it does not
> overwrite the original in place.  Both versions remain in the table under
> the same name so the history is preserved.

---

## Download a JSON object

To save a copy of any payload to your machine:

- Click the **download icon** (⬇) on the row.

Shepard downloads the file as `<name>.json` with pretty-printed formatting.

---

## Attach a reference to a DataObject

A **Structured Data reference** links a DataObject to one or more objects
inside a Structured Data container.

1. Open the **DataObject** detail page.
2. In the **References** panel, click **Add reference → Structured Data**.
3. In the dialog:
   - Choose the **Structured Data container** from the dropdown.
   - Select the JSON **objects** you want to include (you can pick more than one
     from the same container).
   - Enter a **Reference name** (e.g. `test-config` or `material-properties`).
4. Click **Save**.

The new reference appears under the DataObject's References panel. Click its
name to open the reference detail page.

---

## Work with a reference

The reference detail page shows all the JSON objects that belong to this
reference in a table identical to the container page. From here you can:

- **View** any payload (eye icon).
- **Edit** a payload and save a new version (pencil icon — requires write
  access on the Collection).
- **Download** a payload as JSON.
- **Rename** the reference by clicking the pencil next to the reference title
  at the top of the page.
- **Annotate** the reference with semantic tags (click the tag icon or the
  **Annotate** button in the title bar — see
  [Annotating data with semantic tags](/help/annotating-data/)).
- **Delete** the reference (bin icon in the title bar — this unlinks the
  reference from the DataObject but leaves the JSON objects in the container
  intact).

---

## Annotate the container

The container page has its own **Semantic Annotations** panel (below the
object table).  Annotations on the container describe the container itself —
e.g. `urn:shepard:domain:kind = "configuration"` — rather than any individual
payload.

To annotate the container, click **+ Add annotation** in the Semantic
Annotations panel header and follow the standard annotation flow.

---

## Delete objects from a container

1. Open the Structured Data container page.
2. Click the **bin icon** (🗑) on the row you want to remove.
3. Confirm the deletion.

Deleting an object from the container makes it unavailable to any reference
that pointed to it. The reference itself remains, but affected rows in the
reference detail page will show `(unavailable)` next to the object name.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| "Invalid JSON" error on upload | The file's top-level value is not an object | Wrap the content in `{…}` or use a proper JSON object file |
| Eye / pencil icons are greyed out | The payload is marked unavailable (object was deleted from the container) | Re-upload the JSON object to the container |
| Pencil icon absent on container page | You do not have write access on this container | Ask the container owner to grant you edit permissions |
| Reference not listed on the DataObject | You are looking at a different collection or the page has not refreshed | Hard-refresh the DataObject page; check the correct Collection is open |
| Container does not appear in the reference dialog | The container has no objects yet | Upload at least one JSON object to the container first |

---

## See also

- [Upload data](/help/upload-data/) — attach file-shaped data (PDFs, CSVs) instead
- [Annotating data with semantic tags](/help/annotating-data/) — add controlled vocabulary terms
- [Browse vocabularies](/help/browse-vocabularies/) — find the right annotation term
- [Provenance tracing](/help/provenance-tracing/) — see how JSON objects link into the PROV-O graph
