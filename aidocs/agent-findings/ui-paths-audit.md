---
stage: feature-defined
last-stage-change: 2026-06-02
last-content-change: 2026-06-02
---

# UI-PATHS-01-AUDIT — Free-form path/URL input audit

**Date:** 2026-06-02
**Rule:** CLAUDE.md "UI never asks for paths/URLs — pulls from references"
**Backlog section:** `aidocs/16-dispatcher-backlog.md` §UI-PATHS-FROM-REFERENCES
**Auditor:** worktree agent, full `frontend/` sweep

## Summary

**4 CRITICAL · 3 MAJOR · 10 INFO**

Seventeen distinct issues found across 14 files. Four are CRITICAL (user-visible
free-form URL or filesystem path fields in production researcher workflows that
directly violate the "UI never asks for paths" rule). Three are MAJOR (partially
violating — internal numeric IDs where an `appId` picker should be used, or
half-fixed paths where the entry point is clean but the downstream shape is wrong).
Ten are INFO (admin-infra config URLs that are intentionally operator-supplied
infrastructure addresses, external links that are rule-exempt, or read-only
display surfaces).

The canonical violator pair (`ViewRecipeBuilderDialog.vue` lines 191–206) is
confirmed. A secondary chain (`KrlInterpretResultPanel` → `shapes/render.vue`)
also passes raw URLs through query params even when the user-entry-point is clean.

---

## Findings

| # | File | Line(s) | Field / label | What it collects | Severity | Remediation |
|---|---|---|---|---|---|---|
| 1 | `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue` | 191–197 | `v-model="urdfUrl"` `label="URDF source URL"` | Raw HTTP(S) URL or `/urdf-samples/` static path to a `.urdf` file | **CRITICAL** | Replace with a FileReference `appId` picker (autocomplete scoped to `.urdf` files in the DataObject). Pass `urdfFileAppId` query param to `shapes/render`; backend resolves content URL via `UrdfResolver`. Tracked UI-PATHS-02 in `aidocs/16`. |
| 2 | `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue` | 199–206 | `v-model="urdfPackagePath"` `label="Mesh package path (optional)"` | Filesystem root path for `package://` mesh URI resolution | **CRITICAL** | Move to `urn:shepard:urdf:package-path` semantic annotation on the URDF FileReference. Backend reads annotation; zero user input needed. Tracked UI-PATHS-03 in `aidocs/16`. |
| 3 | `frontend/components/context/dataobject/HdfReferencesPane.vue` | 276–284 | `v-model="createForm.datasetPath"` `label="Dataset Path"` | HDF5 internal dataset path (e.g. `/sensor_data/channel_A`) | **CRITICAL** | Requires `GET /v2/hdf/{appId}/datasets` backend endpoint returning a dataset tree; add a tree-select picker in the create dialog. Track as HDF-PATHS-PICKER. |
| 4 | `frontend/components/context/dataobject/legacy/LegacyHdfReferencesPane.vue` | 276–284 | `v-model="createForm.datasetPath"` `label="Dataset Path"` | Same as #3 (legacy v1 duplicate) | **CRITICAL** | Same migration as #3. Legacy path; removed when v1 API is deprecated. |
| 5 | `frontend/pages/shapes/render.vue` | 844–849 | `v-model="containerId"` `label="TS container ID (numeric)"` | Numeric (v1 long-ID) timeseries container identifier typed by the user | **MAJOR** | Should be a picker backed by `appId` once TS-ID migration lands (TS-CORE-SCHEMA-01). In the meantime, surface a container picker (like `WatchedContainersPanel` style) that resolves the numeric ID internally. The hint text even cites the broken URL shape `/containers/timeseries/{id}` — confirms this is not yet resolved through a Reference. |
| 6 | `frontend/components/context/collection/edit-dialog/EditCollectionDialog.vue` | 73–83 | `v-model="updatedCollection.heroImageUrl"` `label="Hero image URL"` | Public URL of an external banner image (JPEG/PNG) | **MAJOR** | Replace with a FileReference upload/picker; backend serves via `/v2/files/{appId}/content`. Advanced-mode-only field (`v-if="advancedMode"`). Track as UI-PATHS-07-HERO-IMAGE. |
| 7 | `frontend/components/context/user/AddSubscriptionDialog.vue` | 44, 46–48 | `callbackUrl` / `subscribedUrl` `label="Callback URL"` / `label="Subscribed URL"` | Webhook delivery endpoint URL (`callbackUrl`) + Shepard resource URL to monitor (`subscribedUrl`) | **MAJOR** | `callbackUrl` is an external webhook — legitimate URL input (rule-exempt). `subscribedUrl` should become a Collection/DataObject `appId` picker; backend constructs the canonical resource URL from the `appId` internally. Track as UI-PATHS-08-SUBSCRIPTION-RESOURCE. |
| 8 | `frontend/components/context/admin/AdminNotificationsPane.vue` | 237–244 | `v-model="actionUrl"` `label="Action URL (optional)"` | URL for a notification's action button in the admin test-send form | **INFO** | Admin-only test form. Add an entity picker (Collection/DataObject) that pre-fills `actionUrl` from the selected entity's canonical URL. Text override stays for non-entity URLs. Track as UI-PATHS-09-NOTIFICATION-ACTION. Lower priority — admin-only surface. |
| 9 | `frontend/components/context/admin/AdminJupyterPane.vue` | 183–193 | `v-model="editHubUrl"` `label="JupyterHub base URL"` | JupyterHub deployment base URL | **INFO** | Admin infra config — external infrastructure address. Exempt under operator-knob pattern (`:JupyterConfig` singleton). Document as exempt. |
| 10 | `frontend/components/context/admin/AdminInstanceRegistryPane.vue` | 264–272 | `v-model="newBaseUrl"` `label="Base URL"` | Peer Shepard instance base URL for provenance registry | **INFO** | Admin infra config — peer instance address. Per comment in source: "the one URL field intentionally surfaced to the operator." Exempt. |
| 11 | `frontend/components/context/admin/NotificationTransportSection.vue` | 293, 371 | `addForm.matrixHomeserver` / `editForm.matrixHomeserver` `label="Homeserver URL"` | Matrix homeserver URL (transport config) | **INFO** | Admin infra config — external Matrix server. Not Shepard data. Exempt. |
| 12 | `frontend/components/context/admin/OntologyBundlesAdminPane.vue` | 356–370 | `uploadMeta.canonicalUrl` / `uploadMeta.iriPrefix` `label="Canonical URL"` / `label="IRI prefix"` | External ontology's canonical address and IRI prefix | **INFO** | Admin ontology upload — these reference external ontology registry addresses, not Shepard data paths. Exempt. |
| 13 | `frontend/components/context/configuration/semantic-repository-create-dialog/EndpointInput.vue` | 8–17 | `v-model:endpoint` `label="Endpoint*"` | SPARQL endpoint URL of an external semantic repository | **INFO** | Admin infra config — external SPARQL store. Exempt under operator-knob pattern. |
| 14 | `frontend/components/context/dataobject/GitReferencesPane.vue` | 195–234, 249–274 | `createForm.repoUrl` / `createForm.path` / `editForm.repoUrl` / `editForm.path` `label="Repository URL"` / `label="Path"` | Git remote URL + intra-repo subdirectory path | **INFO** | Rule-exempt: `GitReference` is a pointer to an _external_ repository. CLAUDE.md explicitly exempts external resource links. The `urlSuggestions` computed property already reduces friction. Document as exempt. |
| 15 | `frontend/components/context/dataobject/legacy/LegacyGitReferencesPane.vue` | 196, 227, 250, 267 | Same fields as #14 | Same as #14 (legacy duplicate) | **INFO** | Same rule exception. Legacy path; removed when v1 is deprecated. |
| 16 | `frontend/components/context/display-components/relationships/edit-uri-dialog/EditUriReferenceDialog.vue` | 119–125 | `v-model="uri"` `label="URI"` | External linked-data URI (e.g. a DOI, ORCID, or external ontology term) | **INFO** | Rule-exempt: URIReference is by design a pointer to an external linked-data resource. Not Shepard-owned data. No appId exists for an arbitrary external URI. |
| 17 | `frontend/components/context/input-components/relationship/UriReferenceInput.vue` | 22–29 | `uriModel.referenceURI` `label="Paste URI...*"` | External linked-data URI (add dialog counterpart to #16) | **INFO** | Same rule exception as #16. |

---

## Detail notes

### CRITICAL: URDF URL + package path (#1, #2)

`ViewRecipeBuilderDialog.vue` (lines 191–206) exposes two free-form text fields:

- **"URDF source URL"** — pre-filled with `/urdf-samples/two-link-arm.urdf` (a
  static path served from `frontend/public/`). The hint text explicitly suggests
  "replace with a signed Garage URL of a real URDF FileReference to render your own
  robot" — confirming this is a known placeholder that was never migrated.
- **"Mesh package path"** — freeform filesystem root for `package://` mesh URI
  resolution.

Both feed `?urdfUrl=` and `?packagePath=` on `pages/shapes/render.vue`. These are
the violations called out verbatim in CLAUDE.md and in the `UI-PATHS-02` /
`UI-PATHS-03` rows of `aidocs/16`.

**Secondary chain:** `RunKrlPreviewDialog.vue` is itself clean — it uses a
`v-autocomplete` picker bound to `urdfFileAppId` (a FileReference `appId` picker).
However, it then derives `urdfPayloadUrl` by constructing a v1 API URL from
`FileReference.id` and passes that raw URL to the shapes renderer via `?urdfUrl=`.
Even when the user-entry-point is clean, the downstream renderer still receives a
raw URL. Fixing UI-PATHS-02 on the renderer side will automatically clean the KRL
downstream path.

File: `frontend/components/krl/KrlInterpretResultPanel.vue` lines 56–59:
```
return `/shapes/render?renderer=urdf&urdfUrl=${encoded}`;
```
This should become `?urdfFileAppId=${encoded}` once the renderer accepts appId
directly.

### CRITICAL: HDF5 dataset path (#3, #4)

`HdfReferencesPane` asks the user to type a raw HDF5 internal path like
`/sensor_data/channel_A`. There is no browse affordance for the container's dataset
tree. Fix requires:
1. Backend: `GET /v2/hdf/{appId}/datasets` returning a tree of dataset paths.
2. Frontend: tree-select or autocomplete component in the create dialog.

This duplicates in `LegacyHdfReferencesPane` — both must be fixed together.

### MAJOR: Numeric TS container ID (#5)

`pages/shapes/render.vue` line 844 exposes a raw `v-text-field` with label "TS
container ID (numeric)" and a hint citing the v1 URL shape
`/containers/timeseries/{id}`. This is a v1 numeric long-ID — the exact kind of
substrate-internal ID the `appId` principle prohibits crossing UI boundaries. Post
TS-ID migration this should resolve through a TS container picker by `appId`.

### MAJOR: Hero image URL (#6)

`EditCollectionDialog.vue` line 73 provides a free-form URL for a banner image.
The image is displayed in the collection detail page. There is no upload path — the
user must supply an already-public URL. The correct shape is a FileReference upload
(singleton) with `GET /v2/files/{appId}/content` serving the image.

### MAJOR: Subscription `subscribedUrl` (#7)

`AddSubscriptionDialog.vue` collects a `subscribedUrl` — the Shepard resource URL
that triggers the subscription. This should be a Collection/DataObject picker whose
`appId` the backend converts to the canonical resource URL. The `callbackUrl` is
an outbound webhook address (user-controlled external system) and is legitimately
a raw URL input — rule exception applies.

---

## Acceptable exceptions (INFO items, no code change required)

| Finding | Why exempt |
|---|---|
| AdminJupyterPane `hubUrl` (#9) | Operator-infra config (`:JupyterConfig` singleton). External JupyterHub address. |
| AdminInstanceRegistryPane `baseUrl` (#10) | Peer-instance discovery — URL is the identity of another Shepard deployment. No appId exists for a peer instance. |
| NotificationTransportSection `matrixHomeserver` (#11) | External Matrix homeserver. Transport config, not Shepard data. |
| OntologyBundlesAdminPane `canonicalUrl` + `iriPrefix` (#12) | External ontology registry metadata. References third-party vocabularies, not Shepard objects. |
| EndpointInput `endpoint` (#13) | External SPARQL store URL. Admin infra config. |
| GitReferencesPane `repoUrl` + `path` (#14, #15) | CLAUDE.md explicitly exempts "external documentation links / cross-references to GitHub, w3.org, vendor docs". A git repository URL is the external resource identity — no Shepard appId substitute exists. |
| EditUriReferenceDialog / UriReferenceInput `uri` (#16, #17) | URIReference is a linked-data pointer to an external resource (DOI, ORCID, ontology term). By definition it points outside Shepard — the URI _is_ the external identity. |
| AddSubscriptionDialog `callbackUrl` (#7 partial) | Outbound webhook delivery endpoint. User-controlled external system — no Shepard entity maps to this. |

---

## Components confirmed clean

The following components were inspected and have no free-form path/URL user inputs:

- `frontend/components/context/admin/AdminFileMigrationPane.vue` — appId inputs only
- `frontend/components/context/admin/AdminStoragePane.vue` — read-only display
- `frontend/components/context/admin/AdminTemplateDialog.vue` — no URL inputs
- `frontend/components/context/admin/SparqlPlaygroundPane.vue` — query text input, not a URL
- `frontend/components/context/admin/PermissionAuditLogPane.vue` — no URL inputs
- `frontend/components/context/admin/AdminUserGitPane.vue` — "Git host" is a hostname (e.g. `gitlab.com`), not a URL; acceptable as credential config
- `frontend/components/context/admin/AdminUserOrcidPane.vue` — ORCID ID input (structured identifier, not a path/URL)
- `frontend/components/context/user/McpPane.vue` — read-only display of system-derived URLs
- `frontend/components/context/user/GitCredentialsPane.vue` — "Host" is a hostname (same as AdminUserGitPane)
- `frontend/components/context/user/ProfilePane.vue` — no URL inputs (per-user JupyterHub preference was removed 2026-05-30)
- `frontend/components/dialog/RunKrlPreviewDialog.vue` — clean entry (appId picker); downstream chain is tracked under #1
- `frontend/components/scenegraph/AddFrameDialog.vue` — no URL inputs
- `frontend/components/scenegraph/AddJointDialog.vue` — no URL inputs
- `frontend/components/container/file/OpenInSceneGraphButton.vue` — navigates to internal route
- `frontend/components/context/collection/WatchedContainersPanel.vue` — "Container appId" text field is an appId (UUID v7), not a path/URL; acceptable as a power-user appId-direct-entry pattern
- `frontend/pages/scene-graphs/index.vue` — "Open by appId" text field accepts UUID v7 appId, not a path/URL
- `frontend/pages/snapshots/diff.vue` — "Snapshot appId (raw)" fields are behind an "Advanced: raw appIds" toggle; appIds not paths/URLs
- `frontend/pages/semantic/sparql/index.vue` — "Repository ID (appId)" accepts appId, not a path/URL

---

## Recommended `aidocs/16` rows

Add or update the following rows in `aidocs/16-dispatcher-backlog.md`
§UI-PATHS-FROM-REFERENCES:

| ID | Item | Size | Priority |
|---|---|---|---|
| UI-PATHS-02 | Fix URDF URL text field in `ViewRecipeBuilderDialog`: replace with FileReference appId picker; update `shapes/render` to accept `?urdfFileAppId=` and resolve via `UrdfResolver`. | M | HIGH |
| UI-PATHS-03 | Remove "Mesh package path" text field from `ViewRecipeBuilderDialog`: move value to `urn:shepard:urdf:package-path` annotation on the URDF FileReference; backend reads annotation. | S | HIGH |
| UI-PATHS-06-HDF-PICKER | Replace HDF5 dataset path text field with browse-and-select picker. Requires `GET /v2/hdf/{appId}/datasets` endpoint returning dataset tree. Applies to both `HdfReferencesPane` and `LegacyHdfReferencesPane`. | M | HIGH |
| UI-PATHS-05-TS-CONTAINER | Replace numeric "TS container ID" text field in `shapes/render.vue` with appId-based container picker. Depends on TS-CORE-SCHEMA-01 (TS-ID migration). | M | MEDIUM |
| UI-PATHS-07-HERO-IMAGE | Replace `heroImageUrl` free-text in `EditCollectionDialog` with FileReference upload/picker. Advanced-mode only. | S | MEDIUM |
| UI-PATHS-08-SUBSCRIPTION-RESOURCE | Replace `subscribedUrl` text field in `AddSubscriptionDialog` with Collection/DataObject appId picker; keep `callbackUrl` as raw text (rule-exempt). | S | MEDIUM |
| UI-PATHS-09-NOTIFICATION-ACTION | Add entity picker to `AdminNotificationsPane` test-send form to pre-fill `actionUrl`; keep text override. Admin-only surface. | S | LOW |
