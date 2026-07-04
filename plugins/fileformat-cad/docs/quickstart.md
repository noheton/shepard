# Quickstart — uploading CAD files to shepard

This page explains how to upload STEP, 3DXML, JT, or OBJ files and
have shepard automatically extract metadata from them.

## What happens on upload

When you upload a `.step`, `.stp`, `.3dxml`, `.jt`, or `.obj` file
to a FileContainer, shepard automatically:

1. Detects the CAD format from the file content.
2. Extracts available metadata (product name, authoring tool, creation
   date, STEP schema, material, ply count for composite parts).
3. Adds these as semantic annotations on the FileReference.

No extra steps needed — the extraction is automatic.

## Upload a STEP file

1. Open a DataObject.
2. In the **File references** panel, click **Add file reference**.
3. Select or create a FileContainer.
4. Click **Choose file** and select your `.step` or `.stp` file.
5. Click **Upload**.

After upload, open the FileReference detail page. The
**Semantic annotations** section will show extracted fields such as:

| Key | Example value |
|---|---|
| `urn:shepard:cad:format` | `step` |
| `urn:shepard:cad:step_schema` | `AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF` |
| `urn:shepard:cad:product_name` | `MFFD_UPPER_SHELL_PANEL_Q1` |
| `urn:shepard:cad:application` | `CATIA V5` |
| `urn:shepard:cad:created_at` | `2024-02-07T14:32:00` |
| `urn:shepard:mffd:cad:ply_count` | `24` |
| `urn:shepard:mffd:cad:material` | `CF/LMPAEK` |
| `urn:shepard:mffd:cad:fibre_angles` | `0,45,-45,90` |

Not all fields are present in every file — only fields found in
the file's metadata are emitted.

## Upload a 3DXML file

Same steps as STEP. For `.3dxml` files (Dassault Systèmes CATIA exchange
format), you'll see:

| Key | Example value |
|---|---|
| `urn:shepard:cad:format` | `3dxml` |
| `urn:shepard:cad:application` | `Dassault 3DXML v3` |
| `urn:shepard:cad:author` | `J. Müller` |
| `urn:shepard:cad:created_at` | `2024-01-15` |
| `urn:shepard:cad:product_name` | `Fuselage_Section_23` |

## Upload an OBJ mesh

For `.obj` files (Wavefront mesh format), the extracted fields cover
mesh statistics rather than product metadata:

| Key | Example value |
|---|---|
| `urn:shepard:cad:format` | `obj` |
| `urn:shepard:cad:vertex_count` | `182441` |
| `urn:shepard:cad:face_count` | `360832` |
| `urn:shepard:cad:mtl_library` | `panel.mtl` |

## Upload a JT file

`.jt` files are detected and tagged with `urn:shepard:cad:format = jt`,
but no further metadata is extracted (phase 1 limitation).

## Search by CAD metadata

Once files are uploaded, use the SPARQL playground or the annotation
search to find files by metadata. For example, to find all STEP files
with a specific schema:

```sparql
SELECT ?fileRef WHERE {
  ?fileRef <urn:shepard:cad:step_schema>
           "AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF" .
}
```

Or use `GET /v2/semantic/annotations/search?predicateIri=urn:shepard:cad:material&value=CF/LMPAEK`
to find all files annotated with a specific material.
