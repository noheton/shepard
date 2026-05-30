---
stage: concept
last-stage-change: 2026-05-30
---

# UI-PATHS-01-AUDIT — Free-form path/URL input audit

**Date:** 2026-05-30
**Backlog row:** UI-PATHS-01-AUDIT

## Summary

**5 VIOLATION / 6 BORDERLINE / OK (all others)**

Top-priority fix: **UI-PATHS-02-SHAPES-RENDER** (URDF renderer URL inputs in
`ViewRecipeBuilderDialog.vue` + `shapes/render.vue`). This is the canonical
violator called out in CLAUDE.md, confirmed present and active.

---

## Findings

### VIOLATION 1 — `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue:191-206`

- **Component/endpoint:** `ViewRecipeBuilderDialog.vue`, URDF renderer section
- **Pattern:** User types a raw URL into "URDF source URL" (`v-model="urdfUrl"`,
  line 192) and a filesystem path into "Mesh package path" (`v-model="urdfPackagePath"`,
  line 200). The dialog's `openUrdf()` function (line 103–116) then forwards both as
  query params `?renderer=urdf&urdfUrl=<encoded>&packagePath=<encoded>` to
  `/shapes/render`.
- **Why it violates:** The user is asked to supply a signed Garage URL (an internal
  pre-signed storage address) or a static path under `/urdf-samples/`. Neither is a
  Shepard data address; both bypass the Reference layer and the permission gate.
  The hint text even says "Signed URL of the .urdf file, or a static path under
  /urdf-samples/." — explicitly leaking storage internals to the user.
- **Remediation:** Follow-up row **UI-PATHS-02-SHAPES-RENDER**. Replace with a
  FileReference picker (appId input). The backend resolves the URDF and meshes from
  the FileReference + its `urn:shepard:urdf:*` annotations. Parameter becomes
  `?renderer=urdf&urdfFileAppId=<appId>`.

---

### VIOLATION 2 — `frontend/pages/shapes/render.vue:498-534`

- **Component/endpoint:** `/shapes/render` page, URDF bootstrap
- **Pattern:** The page reads `?urdfUrl=<encoded>` and `?packagePath=<encoded>` from
  the route query (lines 498–534) and passes them directly to `<UrdfView>` as props.
  Line 533: `urdfUrl.value = q.urdfUrl ? decodeURIComponent(...) : "/urdf-samples/two-link-arm.urdf"`.
- **Why it violates:** The URL that a user typed in the builder dialog (VIOLATION 1)
  propagates into the render page as a raw URL. If the builder dialog is fixed to use
  appId, this page must be updated in the same PR to accept `urdfFileAppId` instead
  and call the backend resolver.
- **Remediation:** Part of **UI-PATHS-02-SHAPES-RENDER**. Change the query param
  to `urdfFileAppId`; call `GET /v2/files/{appId}/resolve` (or the equivalent
  URDF-specific resolver planned in URDF-WEBVIEW-1) to get the actual signed URL
  server-side before rendering. The user never sees or types the URL.

---

### VIOLATION 3 — `frontend/components/context/dataobject/GitReferencesPane.vue:195-233` and `frontend/components/context/dataobject/legacy/LegacyGitReferencesPane.vue:195-233`

- **Component/endpoint:** `GitReferencesPane.vue` and `LegacyGitReferencesPane.vue`,
  Create and Edit forms
- **Pattern:** Both components ask the user to type a "Repository URL" (a free-form
  `v-combobox` on line 196/199 bound to `createForm.repoUrl`, and a `v-text-field`
  on line 251 bound to `editForm.repoUrl`). A second field asks for a "Path"
  (subdirectory within the repo, lines 228/267).
- **Why it violates:** Repository URL is user-typed external data, not a Shepard
  data address. The `path` field is a subdirectory path within the external repo.
  Both bypass the Reference layer. The combobox does surface URL suggestions derived
  from the user's registered GitCredentials, which partially mitigates the UX issue,
  but the underlying concept — the user types a URL — remains a rule violation.
- **Remediation:** The `GitReference` entity is inherently about external git
  repositories (not Shepard-managed data). This is a domain-specific case where the
  "URL" is the identity of the external resource, not a path to Shepard-owned data.
  Evaluate whether `GitCredential.host` + `owner/repo` decomposition would eliminate
  the raw URL input (the user picks a pre-registered host + types `owner/repo`, never
  the full URL). The `path` field (repo subdirectory) is an intrinsic property of the
  git reference, not a Shepard storage path — document this distinction clearly.
  File follow-up row **UI-PATHS-03-GIT-REFERENCES**.

---

### VIOLATION 4 — `frontend/components/context/dataobject/HdfReferencesPane.vue:276-283` and `frontend/components/context/dataobject/legacy/LegacyHdfReferencesPane.vue:276-283`

- **Component/endpoint:** `HdfReferencesPane.vue` and `LegacyHdfReferencesPane.vue`,
  "Dataset Path" field
- **Pattern:** The Create form asks the user to type a "Dataset Path" (line 277/278,
  `v-model="createForm.datasetPath"`, placeholder `/sensor_data/channel_A`). This is
  a path within an HDF5 file stored in a Shepard `HdfContainer`.
- **Why it violates:** The user must know the internal HDF5 dataset path (e.g.
  `/sensor_data/channel_A`). This is an internal file-system path that leaks the
  HDF5 container structure. The correct experience would be a picker that browses the
  HDF5 tree (backend exposes `GET /v2/hdf/{containerId}/datasets`) so the user never
  types the path manually.
- **Remediation:** File follow-up row **UI-PATHS-04-HDF-DATASET-PATH**. Browse the
  HDF5 tree via the API; let the user click the dataset, not type its path. A
  free-form path override may remain as an "advanced" opt-in for one deprecation
  window with a visible warning.

---

### VIOLATION 5 — `frontend/components/context/collection/edit-dialog/EditCollectionDialog.vue:73-81`

- **Component/endpoint:** `EditCollectionDialog.vue`, "Hero image URL" field
  (advanced mode only)
- **Pattern:** The user types a raw public image URL (JPEG/PNG) into a `v-text-field`
  bound to `updatedCollection.heroImageUrl` (line 74). The hint says "Enter a public
  image URL".
- **Why it violates:** The URL is an external resource reference typed by the user.
  The correct shape per CLAUDE.md is to upload the image as a FileReference and let
  the backend resolve the URL. A user-typed external URL bypasses the permission gate
  and breaks for operators whose instance cannot reach external hosts.
- **Remediation:** File follow-up row **UI-PATHS-05-HERO-IMAGE-URL**. Replace with
  a FileReference picker (or file upload) so the image is stored in Shepard's
  FileContainer and served via the content endpoint. The backend resolves to the
  signed URL when rendering. External URL as an "advanced override" with a visible
  warning is acceptable for one deprecation window.

---

## BORDERLINE findings

### BORDERLINE A — `frontend/components/context/user/ProfilePane.vue:379-390` and `frontend/components/context/lab-journal/DataObjectNotebooksPane.vue:85-103`

- **Component:** User profile page and notebooks pane, "JupyterHub base URL" field
- **Pattern:** User sets their personal JupyterHub base URL (`v-model="jupyterUrlInput"`)
  stored in user preferences.
- **Why BORDERLINE:** This is a user-level deployment address, not a Shepard data
  address. The user is configuring which external service they want to use, not
  addressing Shepard-owned data. Similar to setting a timezone or locale preference.
  The URL is never used to resolve Shepard data — it only constructs a launch URL
  for an external notebook service. CLAUDE.md specifically calls this out as the
  canonical BORDERLINE case.
- **Verdict:** ACCEPTABLE. Document the distinction: "the user sets the base URL of
  their hub once; Shepard appends a `/hub/spawn?file=<presigned-url>` suffix
  computed from FileReference appIds".

---

### BORDERLINE B — `frontend/components/context/admin/AdminJupyterPane.vue:183-193`

- **Component:** Admin Jupyter config pane, "JupyterHub base URL" field (instance-admin)
- **Pattern:** Instance admin sets the instance-wide JupyterHub hub URL
  (`v-model="editHubUrl"`).
- **Why BORDERLINE:** Same reasoning as BORDERLINE A — this is infrastructure
  configuration (cluster topology), not a Shepard data address. It falls under the
  CLAUDE.md exception: "Cluster identity / topology (… DB URLs, the OIDC issuer URL)
  — these can't be flipped at runtime without re-bootstrapping." JupyterHub URL is
  closer to this exception than to a data-address input.
- **Verdict:** ACCEPTABLE for admin-only configuration surface. No change needed.

---

### BORDERLINE C — `frontend/components/context/user/GitCredentialsPane.vue:184-191`

- **Component:** User Git Credentials pane, "Host" field
- **Pattern:** User types a hostname (e.g. `gitlab.com`) for a new Git credential
  (`v-model="addForm.host"`).
- **Why BORDERLINE:** This is a credential configuration surface, not a data
  address. The user is registering which git host they have credentials for — not
  addressing Shepard-owned data. The hostname feeds into the GitCredential entity
  that backs URL-suggestion autocomplete in the VIOLATION 3 combobox.
- **Verdict:** ACCEPTABLE. Hostname entry for credential registration is
  infrastructure configuration analogous to configuring an OIDC provider URL.

---

### BORDERLINE D — `frontend/components/context/configuration/semantic-repository-create-dialog/EndpointInput.vue` (via `CreateSemanticRepositoryDialog.vue:66`)

- **Component:** `CreateSemanticRepositoryDialog.vue`, "Endpoint" field
- **Pattern:** User types a SPARQL endpoint URL for registering an external semantic
  repository.
- **Why BORDERLINE:** This is admin/operator configuration of an external service
  endpoint (a SPARQL triplestore), not a Shepard data address. It is conceptually
  similar to setting a database URL — an integration address, not a data path.
- **Verdict:** ACCEPTABLE for the admin/configuration context. This is the "external
  service integration address" category that is out of scope for the rule (which
  targets Shepard-owned data addresses).

---

### BORDERLINE E — `frontend/components/context/admin/OntologyBundlesAdminPane.vue:365-370`

- **Component:** OntologyBundlesAdminPane, "Canonical URL" field
- **Pattern:** Admin types a canonical URL for an uploaded ontology bundle
  (`v-model="uploadMeta.canonicalUrl"`, label "Canonical URL (for future refresh)").
- **Why BORDERLINE:** This is metadata for the ontology bundle itself (its canonical
  source URI for provenance and future refresh), not a Shepard data address. The URL
  is descriptive metadata about the ontology's origin, not a path to Shepard-owned
  content. The field hint says "for future refresh" — it is a reference to an
  external resource, not a path within Shepard.
- **Verdict:** ACCEPTABLE with a caveat: when the "future refresh" feature lands, the
  backend should pull from this URL automatically (server-side fetch) rather than
  surfacing the URL for the user to re-enter. Document this intent in the field hint.

---

### BORDERLINE F — `frontend/components/context/user/AddSubscriptionDialog.vue:44-48`

- **Component:** `AddSubscriptionDialog.vue`, "Callback URL" and "Subscribed URL"
  fields
- **Pattern:** User enters a webhook callback URL and a subscribed URL pattern
  (a regex matched against Shepard's own resource paths).
- **Why BORDERLINE:** "Callback URL" is an external webhook receiver address —
  operator integration configuration, not a Shepard data address. "Subscribed URL" is
  a regex pattern matching Shepard internal paths, which is upstream v1 API surface
  (frozen, not modifiable per the API-version policy). Both are integration
  configuration, not user-facing data addressing.
- **Verdict:** ACCEPTABLE for the callback URL (external receiver). The subscribed
  URL regex is upstream-compat surface that cannot be changed.

---

## Backend v2 API — no violations found

Scanned all `@QueryParam` annotations in `backend/src/main/java/de/dlr/shepard/v2/`.
No `@QueryParam` taking a URL, path, or URI was found. The canonical violator
described in CLAUDE.md (`GET /v2/shapes/render?renderer=urdf&urdfUrl=…&packagePath=…`)
**does not exist in the backend** — the current `ShapesRenderRest.java` is a
`POST /v2/shapes/render` accepting a JSON body with `templateAppId` and
`focusShepardId` (both appIds). The violating pattern is **frontend-only**: the
`ViewRecipeBuilderDialog.vue` and `shapes/render.vue` page use `?urdfUrl` and
`?packagePath` query params in client-side navigation between two frontend pages,
never reaching the backend as query params to the shapes/render endpoint.

---

## How to grep for regressions

```bash
# Frontend: v-text-field or v-combobox with URL/path labels
grep -rn 'label.*[Uu][Rr][Ll]\|label.*[Pp]ath\|label.*[Uu][Rr][Ii]' frontend/ --include="*.vue"
grep -rn 'label.*[Ee]ndpoint\|label.*[Hh]ost\|label.*[Aa]ddress' frontend/ --include="*.vue"

# Frontend: v-model bound to URL/path variable names
grep -rn 'v-model=.*[Uu]rl\|v-model=.*[Pp]ath\|v-model=.*[Uu]ri' frontend/ --include="*.vue"
grep -rn 'v-model=.*[Ee]ndpoint\|v-model=.*[Aa]ddress' frontend/ --include="*.vue"

# Backend v2: query params with URL/path names
grep -rn '@QueryParam.*[Uu]rl\|@QueryParam.*[Pp]ath\|@QueryParam.*[Uu]ri' \
  backend/src/main/java/de/dlr/shepard/v2/ --include="*.java"

# Frontend: client-side URL construction passed to navigateTo/router.push
grep -rn 'urdfUrl\|urdfPackagePath\|packagePath' frontend/ --include="*.vue" --include="*.ts"
```

---

## Follow-on rows to file

| Row ID | Surface | Priority |
|--------|---------|----------|
| UI-PATHS-02-SHAPES-RENDER | URDF URL + packagePath inputs in ViewRecipeBuilderDialog + shapes/render.vue | HIGH |
| UI-PATHS-03-GIT-REFERENCES | Repository URL field in GitReferencesPane (both legacy + v2) | MEDIUM |
| UI-PATHS-04-HDF-DATASET-PATH | HDF5 dataset path free-form input in HdfReferencesPane | MEDIUM |
| UI-PATHS-05-HERO-IMAGE-URL | Hero image URL free-form input in EditCollectionDialog | LOW |
