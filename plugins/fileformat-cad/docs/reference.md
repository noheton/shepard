# Reference — shepard-plugin-fileformat-cad (tier-1)

**Status:** alpha · tier-1 · metadata-only (phase 1)

Comprehensive reference for the CAD file format parser plugin.
Each section answers a question an operator or power user would ask.

## 1. What the plugin does

When a file with a recognized CAD extension is uploaded to a
FileContainer, this plugin is invoked via the `FileParserPlugin` SPI
(see `aidocs/platform/47 §3`). It:

1. Sniffs the file's magic bytes to determine the actual format.
2. Dispatches to the appropriate sub-parser.
3. Extracts metadata — product name, authoring tool, creation date,
   STEP schema, composite-manufacturing hints (ply count, fibre angles,
   material).
4. Writes one `:SemanticAnnotation` per discovered field, anchored on
   the FileReference appId (or the parent DataObject if no FileReference
   appId is available).

The plugin creates no DataObjects, no DataContainers, and never writes
outside its `AnnotationWriter` callback.

## 2. Accepted inputs

| Extension | Format | Parser | Magic bytes |
|---|---|---|---|
| `.step`, `.stp` | STEP ISO 10303-21 | `StepP21Parser` | `ISO-10303-21` at byte 0 |
| `.3dxml` | Dassault 3DXML | `ThreeDxmlParser` | ZIP magic `PK\x03\x04` or `PK\x05\x06` |
| `.jt` | JT ISO 14306 | magic-only | `#!JT` at byte 0 |
| `.obj` | Wavefront OBJ mesh | `ObjParser` | `.obj` extension only |

Extension matching is case-insensitive. Files not matching these
extensions are ignored regardless of content.

## 3. Annotation key reference

### 3.1 `urn:shepard:cad:*` — format-agnostic (FileReference subject)

| Predicate | Source | Type | Example |
|---|---|---|---|
| `urn:shepard:cad:format` | always emitted | `step` \| `3dxml` \| `jt` \| `obj` | `"step"` |
| `urn:shepard:cad:step_schema` | STEP `FILE_SCHEMA` entity | string | `"AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF"` |
| `urn:shepard:cad:product_name` | STEP `PRODUCT` entity / 3DXML `<Reference3D name>` | string | `"MFFD_UPPER_SHELL_PANEL_Q1"` |
| `urn:shepard:cad:organisation` | STEP `FILE_NAME` arg 4 / 3DXML `<AuthorAndDateCreated>` | string | `"DLR ZLP Augsburg"` |
| `urn:shepard:cad:author` | 3DXML `<AuthorAndDateCreated>` first field | string | `"J. Müller"` |
| `urn:shepard:cad:application` | STEP `FILE_NAME` arg 5 / 3DXML `version` attribute | string | `"CATIA V5"` or `"Dassault 3DXML v3"` |
| `urn:shepard:cad:created_at` | STEP `FILE_NAME` arg 2 / 3DXML timestamp | ISO 8601 | `"2024-02-07T14:32:00"` |
| `urn:shepard:cad:description` | STEP `FILE_DESCRIPTION` entity | string | `"MFFD upper shell panel"` |
| `urn:shepard:cad:unit` | OBJ (future; not yet parsed) | `mm` \| `m` \| `inch` | — |
| `urn:shepard:cad:vertex_count` | OBJ `v ` lines | integer string | `"182441"` |
| `urn:shepard:cad:face_count` | OBJ `f ` lines | integer string | `"360832"` |
| `urn:shepard:cad:mtl_library` | OBJ `mtllib` directive | filename | `"panel.mtl"` |

### 3.2 `urn:shepard:mffd:cad:*` — MFFD / AP242 composite manufacturing

| Predicate | Source | Type | Example |
|---|---|---|---|
| `urn:shepard:mffd:cad:ply_count` | Count of `COMPOSITE_*` entity occurrences in STEP DATA section (up to 512 KB scan) | integer string | `"24"` |
| `urn:shepard:mffd:cad:material` | First `MATERIAL` entity in STEP DATA section | string | `"CF/LMPAEK"` |
| `urn:shepard:mffd:cad:fibre_angles` | Up to 10 unique numeric values near `REINFORCEMENT` keyword | comma-separated floats | `"0,45,-45,90"` |
| `urn:shepard:mffd:cad:catia_instance_count` | 3DXML `<Instance3D>` node count | integer string | `"1"` |

Notes on STEP DATA scan limits:
- Scans up to 512 KB from `DATA;` — larger DATA sections are partially scanned.
- Ply count is a heuristic: counts `COMPOSITE_*` occurrences, not unique ply entities.
- Fibre angles: first 10 unique values; false positives possible on non-AP242 files.

## 4. REST endpoints

None. The plugin operates entirely via the `FileParserPlugin` SPI on upload.
No new paths are added under `/v2/` or `/shepard/api/`.

## 5. Admin config

None. The parser runs unconditionally for accepted file types.
No `:*Config` singleton. No `PATCH /v2/admin/fileformat-cad/config` endpoint.

## 6. Neo4j entities

None. The plugin writes only `:SemanticAnnotation` nodes via `AnnotationWriter`.
No new node types or relationship types are introduced.

## 7. Build

The plugin is a Tier-1 Maven module installed by `scripts/install-plugins.sh`
before the backend builds:

```sh
cd plugins/fileformat-cad && ./../../backend/mvnw -B -DnoPlugins -Dmaven.test.skip=true install
```

The backend's `with-plugins` Maven profile picks it up via:

```xml
<!-- backend/pom.xml -->
<dependency>
  <groupId>de.dlr.shepard.plugins</groupId>
  <artifactId>shepard-plugin-fileformat-cad</artifactId>
  <scope>provided</scope>
</dependency>
```

## 8. Known limitations (phase 1)

- **No geometry rendering.** glTF preview is deferred to `CAD-RENDER-1`.
- **STEP DATA scan is partial** for files > 512 KB DATA section.
- **3DXML `<Reference3D>`** product name only from `ProductStructure.xml`
  or the first `3DRep` entry — not all assembly levels.
- **JT files** are detected (format=jt emitted) but no metadata is extracted.
- **OBJ vertex/face counts** scan the first 2 MB only.
