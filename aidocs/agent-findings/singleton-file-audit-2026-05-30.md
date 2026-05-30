# Singleton-File Bundle Audit — 2026-05-30

**Task:** SINGLETON-FILE-01-AUDIT  
**Arc:** SINGLETON-FILE-MIGRATION  
**Date:** 2026-05-30  
**Analyst:** agent (worktree singleton-file-01-audit)

---

## Summary

This audit supports the SINGLETON-FILE-MIGRATION backlog arc, which tracks
migration of `:FileBundleReference` nodes holding exactly one file into the
FR1b singleton shape (`:SingletonFileReference`, label and `/v2/files/`
endpoint).

The rule (CLAUDE.md §"Always: singleton FileReference for one-file uploads"):
> Whenever a Reference will carry exactly one file, mint a singleton
> FileReference (FR1b) via POST /v2/files — not a FileBundleReference.
> Reserve FileBundleReference for genuinely multi-file bundles.

The V23 Java migration (`V23__Split_singleton_bundles_to_FileReferences.java`)
ships the server-side conversion but is **opt-in** (guarded by
`shepard.migration.split-singletons.enabled=false` by default). Before enabling
it, an operator needs to know how many bundles are affected and where they live.

The Cypher audit script is at `scripts/audit-singleton-file-bundles.cypher`.

---

## How to Run the Cypher Queries

```bash
# Against a local compose stack (default password):
cypher-shell -u neo4j -p neo4j < scripts/audit-singleton-file-bundles.cypher

# Or pipe one query at a time:
cypher-shell -u neo4j -p neo4j \
  "MATCH (b:FileBundleReference) WHERE size(b.fileOids) = 1 RETURN count(b) AS singleton_bundle_count"
```

The script contains four queries:

| # | Name | Purpose |
|---|------|---------|
| 1 | Global count | Fast pre-flight — how many bundles total? |
| 2 | Per-Collection breakdown | Which collections hold the most debt? |
| 3 | Per-DataObject detail (top 50) | Greppable node list for manual review |
| 4 | V23 migration readiness | Subset that V23 will actually convert |

Query 4 is narrower than Query 1: V23 only converts bundles that have exactly
one `:FileGroup` child with exactly one `:ShepardFile` through that group (the
canonical V21 single-file shape). Bundles that somehow lack a group node or have
multiple files per group are not touched.

---

## Code-Level Inventory: Where Single-File Bundles Are Created

### 1. `examples/mffd-rdk-urdf-showcase/seed.py` — PRIMARY OFFENDER

**File:** `examples/mffd-rdk-urdf-showcase/seed.py`  
**Function:** `upload_file_reference` (line 360)

```python
def upload_file_reference(apis, coll, do, fc, file_path, ref_name):
    """Upload a single file under one FileReference (FR1b: one FileRef per file)."""
    ...
    oid = _upload_file_presigned(v2, api_key, fc_app, file_path, ctype)
    fr = FileReference(
        name=ref_name,
        dataObjectId=do.id,
        fileContainerId=fc.id,
        fileOids=[oid],       # <-- single-OID bundle
    )
    fr = apis.file_reference.create_file_reference(coll.id, do.id, fr)
```

This function is called at lines **719** (MFZ.rdk source), **739** (URDF file),
and **741–743** in a loop over 7 Collada mesh files. The comment says "FR1b: one
FileRef per file" but the implementation uses the v1 `create_file_reference`
endpoint (`/shepard/api/.../fileReferences`) which creates a
`:FileBundleReference` — not the v2 singleton.

**Instances per seed run:**

| Call site | Line | Count |
|-----------|------|-------|
| MFZ.rdk source (`mfz-rdk-source`) | 719 | 1 |
| URDF file (`kr210-urdf`) | 739 | 1 |
| Mesh loop × 7 (`kr210-mesh-*.dae`) | 741–743 | 7 |
| **Total per seed run** | | **9** |

All 9 are canonical single-file bundles that should be FR1b singletons.

---

### 2. `examples/mffd-showcase/seed.py`

**File:** `examples/mffd-showcase/seed.py`  
**Function:** `upload_do_files` (line 763)

```python
def upload_do_files(apis, coll, do, fc, data_dir, md_files):
    ...
    for fname in md_files:
        oid = _upload_file_presigned(v2, api_key, fc_app_id, path)
        oids.append(oid)
    fr = FileReference(
        name=ref_name,
        dataObjectId=do.id,
        fileContainerId=fc.id,
        fileOids=oids,        # <-- may be 1 OID (all DO_FILES entries are 1 file)
    )
    fr = apis.file_reference.create_file_reference(coll.id, do.id, fr)
```

The `DO_FILES` dict (line 211) maps each DataObject to exactly one Markdown file:

```python
DO_FILES = {
    "AFP Layup S1":           ["afp-layup-recipe-s1.md"],    # 1 file
    "AFP Layup S2":           ["afp-layup-recipe-s2.md"],    # 1 file
    "Rework S1":              ["rework-s1-protocol.md"],     # 1 file
    "Stringer Welding S1":    ["welding-protocol.md"],       # 1 file
    "LBR Cleat Installation": ["lbr-cleat-spec.md"],         # 1 file
}
```

Five calls × 1 file each = **5 single-file bundles** per seed run, all of which
should be FR1b singletons.

---

### 3. `examples/lumen-showcase/seed.py`

**File:** `examples/lumen-showcase/seed.py`  
**Function:** `upload_run_files` (line 811)

```python
def upload_run_files(apis, coll, run_do, fc, data_dir, run_idx):
    """One CAD stub + one test report per run as a single FileReference."""
    files = [
        data_dir / "files" / f"tr-{run_idx:03d}-cad-stub.bin",
        data_dir / "files" / f"tr-{run_idx:03d}-test-report.md",
        data_dir / "files" / f"tr-{run_idx:03d}-thermal.png",
    ]
    ...
    fr = FileReference(
        ...
        fileOids=oids,    # <-- up to 3 OIDs per reference
    )
```

This function bundles up to 3 files per test run into one bundle. Whether this
creates a single-file bundle depends on whether the local `files/` directory
contains 1, 2, or 3 of the three expected files. In a fully-seeded install all
three files exist, producing genuine 3-file bundles — **NOT** single-file debt.
However, partial seeds (only one file present on disk) would produce single-file
bundles.

The LUMEN seed therefore contributes 0–15 single-file bundles (0 in a full seed,
up to 1 per run if only the `.md` report exists). Full seeds are safe; partial
seeds create debt.

---

### 4. `examples/microsections-showcase/seed.py` — USES FR1b CORRECTLY

**File:** `examples/microsections-showcase/seed.py`  
**Function:** `upload_singleton_file` (line 143)

```python
def upload_singleton_file(self, parent_data_object_app_id, name,
                           file_path, content_type):
    """Upload a file as a FR1b singleton FileReference...
    Uses POST /v2/files?parentDataObjectAppId=...&name=..."""
    url = self._url(f"/v2/files?{qs}")
    ...
```

This is the **correct** pattern. The microsections showcase explicitly calls
`POST /v2/files` (the FR1b singleton endpoint) at line 160 and is called at line
253. No single-file bundle debt is created here.

---

### 5. Frontend: `DataObjectFileUploadDialog.vue`

**File:** `frontend/components/context/data-object/upload-data/DataObjectFileUploadDialog.vue`  
**Lines:** 122–141

```typescript
const fileRef: FileRef = { fileOids: oids };
await addFileReference(newReferenceName.value, fileContainerId.value, fileRef);
```

The frontend "Add data reference" dialog collects all selected files into a
single bundle regardless of count. A user uploading one file via this dialog
creates a single-file bundle. This is the **primary source of operational
single-file bundle debt** on a live instance — every user who uploads a lone
file through the standard UI produces a `:FileBundleReference` with one OID.

The fix is tracked as part of the SINGLETON-FILE-MIGRATION arc: the dialog
should route single-file uploads to `POST /v2/files` (FR1b) and only fall back
to the bundle endpoint when multiple files are selected.

---

### 6. Frontend: `CreateDataReferenceDialog.vue`

**File:** `frontend/components/context/data-references/create-dialog/CreateDataReferenceDialog.vue`  
**Lines:** 85–93

```typescript
.createFileReference({
    collectionId: props.collectionId,
    dataObjectId: props.dataObjectId,
    fileReference: { ...fileRef.value, name: ..., fileContainerId: ... }
})
```

Same pattern as `DataObjectFileUploadDialog` — unconditionally creates a
`:FileBundleReference` via the v1 API. A single-file selection creates a
single-file bundle.

---

## Estimated Instance Count on the Live Showcase

Based on code-level analysis of seed scripts against the known showcase
collections on `shepard.nuclide.systems`:

| Source | Collection | Est. single-file bundles |
|--------|-----------|--------------------------|
| mffd-rdk-urdf-showcase | MFFD RDK/URDF Showcase | 9 |
| mffd-showcase | MFFD AFP Showcase | 5 |
| lumen-showcase (full seed) | LUMEN Hotfire | 0 |
| user uploads via UI | all collections | unknown (ongoing) |

The 9 bundles in the RDK/URDF showcase are the canonical instance surfaced in
CLAUDE.md §"Singleton FileReference" backfill rule: the `kr210-r2700-urdf`
showcase was the original reported case. The MFFD showcase adds 5 more.

Run Query 1 in `cypher-shell` to get the real live count before enabling V23.

---

## Migration Readiness

V23 is ready to run but is opt-in. The operator flow:

1. Take a Neo4j + MongoDB backup (per `aidocs/45 §2.1 W3`).
2. Run Query 4 from `scripts/audit-singleton-file-bundles.cypher` to confirm
   the candidate count.
3. Set `shepard.migration.split-singletons.enabled=true` in
   `application.properties` or via environment variable.
4. Restart shepard. Migration logs progress every 1 000 rows.
5. Post-run verification:
   ```cypher
   MATCH (s:SingletonFileReference)
   RETURN count(s) AS singletons,
          sum(CASE WHEN (s)-[:has_payload]->(:ShepardFile) THEN 1 ELSE 0 END) AS with_file;
   ```
6. Rollback available via
   `V23_R__Rejoin_singletons_into_FileBundleReferences.cypher` within the
   timestamp-guard window defined in that file.

---

## Remaining Debt (Post-V23)

After V23 converts existing single-file bundles, new ones will continue to be
created by:

- `DataObjectFileUploadDialog.vue` (any single-file upload via the UI)
- `CreateDataReferenceDialog.vue` (same)
- The MFFD-RDK-URDF and MFFD seed scripts (if re-seeded)

These are tracked as follow-on work in the SINGLETON-FILE-MIGRATION arc. The
seed script fixes (SINGLETON-FILE-02-SEED-MIGRATION) are being handled in the
parallel worktree `singleton-file-02-seed-migration`.
