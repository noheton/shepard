---
stage: fragment
last-stage-change: 2026-06-19
---

# APISIMP Sweep — fire-135 (2026-06-19)

Scanned `backend/src/main/java/de/dlr/shepard/v2/**` and `plugins/*/src/main/java/**`
for residual bare `@QueryParam` annotations not yet covered by any open or merged PR.
Two new gaps found.

## Method

1. Grepped all v2 REST resource files for `@QueryParam` not immediately preceded by
   `@Parameter` on the same or adjacent line.
2. Cross-checked against all open PRs (#1993–#2017) to avoid duplicates.
3. Scanned plugin REST resources for the same pattern.

## Finding 1 — `APISIMP-FILEREF-UPLOAD-PARAMS` (XS)

**File:** `backend/src/main/java/de/dlr/shepard/v2/file/resources/FileReferenceV2Rest.java`

`POST /v2/files` (the singleton-file upload endpoint) accepts two bare `@QueryParam`
annotations at lines 77–78:

```java
@QueryParam("parentDataObjectAppId") String parentDataObjectAppId
@QueryParam("name") String name
```

Neither carries a `@Parameter(description=…)` annotation. A caller reading the
OpenAPI schema cannot tell:
- `parentDataObjectAppId`: required? what type of entity? DataObject UUID v7 only?
- `name`: optional or required? length constraints? used as the display name on the
  resulting FileReference?

**Not covered by:** PR #2006 targets `ReferencesV2Rest.java` (`/v2/references`),
a different file. No other open PR touches `FileReferenceV2Rest.java`.

**Fix:** Add `@Parameter(required=true, description="UUID v7 appId of the parent
DataObject that will own this FileReference.")` to `parentDataObjectAppId`; add
`@Parameter(required=true, description="Display name for the created FileReference.
Becomes the Reference node's name label in the graph.")` to `name`. Add 2 reflection
regression tests (`fileUpload_parentDataObjectAppIdParamIsDocumented`,
`fileUpload_nameParamIsDocumented`). AC: OpenAPI schema documents both params;
`mvn verify -pl backend` green. No runtime change.

## Finding 2 — `APISIMP-PLUGIN-V2-PARAMS-UNDOCUMENTED` (S)

Four plugin REST resources carry bare `@QueryParam` annotations:

### 2a — `AasShellsRest.java` lines 98,100

**File:** `plugins/aas/src/main/java/de/dlr/shepard/plugins/aas/v2/resources/AasShellsRest.java`
**Endpoint:** `GET /v2/aas/shells`

```java
@QueryParam("page") @DefaultValue("0") int page
@QueryParam("pageSize") @DefaultValue("50") int pageSize
```

No `@Parameter` on either. Standard pagination pair; same fix as in-tree resources
(document defaults, document any server-side cap if one exists).

### 2b — `SpatialPromoteRest.java` line 92

**File:** `plugins/spatiotemporal/src/main/java/de/dlr/shepard/v2/spatial/promote/SpatialPromoteRest.java`
**Endpoint:** spatial promotion endpoint

```java
@QueryParam("fileReferenceAppId") String fileReferenceAppId
```

No `@Parameter`. `fileReferenceAppId` is a required UUID v7; a caller cannot tell
whether to pass a FileReference appId or a FileBundleReference appId (it's a singleton
FileReference — the API should say so).

### 2c — `UnhideFeedRest.java` lines 130–132

**File:** `plugins/unhide/src/main/java/de/dlr/shepard/plugins/unhide/resources/UnhideFeedRest.java`
**Endpoint:** Unhide harvest feed endpoint

```java
@QueryParam("page") @DefaultValue("0") int page
@QueryParam("pageSize") @DefaultValue("20") int pageSize
@QueryParam("validate") @DefaultValue("false") boolean validate
```

No `@Parameter` on any of the three. `validate` in particular has non-obvious
semantics (triggers SHACL validation on returned records — not a filter); it must
be documented.

### 2d — `VideoStreamReferenceV2Rest.java` line 66

**File:** `plugins/video/src/main/java/de/dlr/shepard/v2/video/resources/VideoStreamReferenceV2Rest.java`
**Endpoint:** `POST /v2/video/references` (create)

```java
@QueryParam("name") String name
```

No `@Parameter`. Same pattern as FileReferenceV2Rest Finding 1 — required field,
no documentation.

**Fix:** Add `@Parameter` to all 6 bare params across the 4 plugin files (document
required/optional, defaults, semantics). Add 1 reflection regression test per file
(4 tests total). AC: OpenAPI schema for each plugin endpoint documents its params;
`mvn verify -pl backend -pl plugins/aas -pl plugins/spatiotemporal -pl plugins/unhide -pl plugins/video` green. No runtime change.
