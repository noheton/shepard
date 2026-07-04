---
title: Manage file bundles
description: When to use a FileBundleReference vs a singleton FileReference; uploading files as a bundle; working with groups; the image frame scrubber
permalink: /help/manage-file-bundle/
layout: default
audience: user
---
# Manage file bundles

**What this is for.** A **FileBundleReference** groups several files under one
Reference when the files genuinely belong together — a series of camera frames
from one capture run, a mesh set for a 3-D model, or the unpacked contents of
an archive.  If you only have one file to attach, use a **singleton
FileReference** instead (see [Upload data](/help/upload-data/)) — it avoids the
"which file?" picker and is directly addressable by every viewer and resolver.

**Before you start.** You need *write* access on the Collection to create
references.

---

## Choose: bundle or singleton?

| Situation | Use |
|---|---|
| Single CAD file, PDF, URDF, KRL program | **Singleton FileReference** — one file, one reference, no groups |
| 200 camera frames from one acquisition run | **FileBundleReference** — all frames in one reference, organised into groups |
| Mesh set (geometry + material + texture) | **FileBundleReference** — logically a unit, needs a group for easy navigation |
| You're not sure yet | Start with a singleton; convert to a bundle later if you need sub-run structure |

---

## Upload files as a bundle

1. Open the **DataObject** detail page.
2. In the **References** panel click **Upload files** (the upload button in the
   top-right of the panel).
3. Drag one or more files onto the upload area, or click **Browse** to select
   from your machine.
4. Below the file picker, find the **Upload Shape** toggle:
   - **One Reference per file** (default) — each file becomes its own
     singleton FileReference.  Use this for independent files.
   - **Bundle as one Reference** — all selected files land under one
     FileBundleReference.  Switch to this mode only when the files
     form a logical unit.
5. In **Bundle as one Reference** mode:
   - Under **Storage Location**, pick an existing **File container** from the
     collection, or switch to **Create new** to make one on the fly.
   - Enter a **Reference name** (e.g. `capture-run-2026-06-01`).
6. Click **Upload**.

A progress panel shows each file's upload progress.  When all files have
finished, the bundle appears in the References panel.

> **Tip:** you can set the selected file container as the default for the
> collection by ticking the "Set as new default file container" checkbox — it
> pre-fills on the next upload.

---

## What are file groups?

Every FileBundleReference is divided into **file groups** — sub-collections
within the bundle.  A bundle always has at least one group (named `"default"`).
Groups let you slice a long capture run into logical segments:

| Group name | Contains |
|---|---|
| `sub-run 1` | frames 1–500 (first pass) |
| `sub-run 2` | frames 501–1 000 (second pass) |
| `calibration` | a dark-field and flat-field reference frame |

You can add groups and rearrange files via the REST API (see
[File bundle reference](/reference/file-bundle/) for the curl examples), or
directly through the frame-scrubber UI for image bundles (see below).

---

## Browse an image bundle

When a FileBundleReference's first group contains image files (`.png`, `.jpg`,
`.jpeg`, `.tif`, `.tiff`), Shepard automatically shows a **frame scrubber
panel** on the DataObject detail page.

The panel has three areas:

- **Large preview** — the currently selected frame at full resolution.
- **Frame slider** — drag or click anywhere on the bar to jump to a specific
  frame (`Frame N of TOTAL`).
- **Thumbnail strip** — scrollable row of all frames; click any thumbnail to
  jump directly to it.  Thumbnails load lazily — only the visible ones
  are fetched, so even thousand-frame bundles open instantly.

No configuration is needed — Shepard detects the image bundle automatically
and renders the panel.

---

## Download a file from a bundle

Bundle files are listed under the **Files** tab on the bundle reference detail
page (click the bundle's name in the References panel to open it).

From the files table:

- Click the **download icon** (⬇) on any row to save that file to your
  machine.
- The filename is preserved from the original upload.

---

## Delete a file from a bundle

From the files table on the bundle reference detail page:

1. Click the **bin icon** (🗑) on the row you want to remove.
2. Confirm the deletion.

Deleting a file is permanent — the bytes are removed from the file container
and cannot be recovered.

---

## Annotate a bundle

The bundle reference detail page has a **Semantic Annotations** panel.
Annotations on a bundle describe the bundle as a whole — for example,
`urn:shepard:data:captureMode = "continuous"`.

Click **+ Add annotation** in the Semantic Annotations panel header and follow
the standard annotation flow.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| "Bundle as one Reference" toggle is absent | You opened the upload dialog via a route that doesn't create references (e.g. embedded in a lab journal) | Use the **References** panel on the DataObject detail page to trigger the upload dialog |
| No file containers in the dropdown | No file container is linked to this collection yet | Switch to **Create new** to create one, or ask an admin to link one |
| Frame scrubber does not appear for an image bundle | The first group's first file is not recognised as an image | Ensure filenames end in `.png`, `.jpg`, `.jpeg`, `.tif`, or `.tiff` |
| Frame scrubber shows `0 of 0` | The group exists but has no files yet | Upload files into the bundle's first group via the API or re-upload |
| Thumbnails load slowly | Many high-resolution frames in one group | Switch the group's page size via the REST API or reduce frames per group |
| Bundle reference not visible on DataObject | Upload completed with 0 files (all failed) | Check the per-file error messages in the progress panel; verify file container health |

---

## See also

- [Upload data](/help/upload-data/) — singleton FileReference (one file) vs bundle, full API examples
- [File bundle reference](/reference/file-bundle/) — technical reference with REST API curl examples and group metadata fields
- [Annotating data with semantic tags](/help/annotating-data/) — add controlled vocabulary terms
- [Provenance tracing](/help/provenance-tracing/) — see how bundle uploads link into the PROV-O graph
