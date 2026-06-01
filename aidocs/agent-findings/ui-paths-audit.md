---
stage: fragment
last-stage-change: 2026-06-01
last-content-change: 2026-06-01
---

# UI-PATHS-01-AUDIT — Free-form path/URL input audit

**Date:** 2026-06-01
**Rule:** CLAUDE.md "UI never asks for paths/URLs — pulls from references"
**Backlog section:** `aidocs/16-dispatcher-backlog.md` §UI-PATHS-FROM-REFERENCES

## Summary

12 distinct violations found across 10 files. Four are HIGH (user-visible input
fields in production researcher flows). Six are MEDIUM (admin-only or infra config
surfaces where the URL is intentionally an infrastructure pointer, not a Shepard
data address). Two are LOW (prop names that suggest URL but the value is resolved
server-side or displayed read-only).

The canonical violator (`ViewRecipeBuilderDialog` URDF source URL + package path)
is confirmed at lines 191–206. The `KrlInterpretResultPanel` is clean by design —
it accepts an `urdfPayloadUrl` prop but never exposes an input to the user (it
derives the URL internally and uses it only to build a deep-link button).

---

## Findings

| # | File | Line(s) | Field / prop | What it collects | Severity | Migration path |
|---|---|---|---|---|---|---|
| 1 | `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue` | 191–197 | `v-model="urdfUrl"` / `label="URDF source URL"` | Raw HTTP(S) URL or static `/urdf-samples/` path to a `.urdf` file | **HIGH** | UI-PATHS-02-SHAPES-RENDER: replace with a FileReference picker (`urdfFileAppId`); backend `UrdfResolver` returns signed content URL. |
| 2 | `frontend/components/container/timeseries/ViewRecipeBuilderDialog.vue` | 199–206 | `v-model="urdfPackagePath"` / `label="Mesh package path (optional)"` | Filesystem root path for `package://` mesh URI resolution | **HIGH** | Ship as `urn:shepard:urdf:package-path` semantic annotation on the URDF FileReference; backend reads annotation, no user input needed. |
| 3 | `frontend/components/context/dataobject/HdfReferencesPane.vue` | 276–284 | `v-model="createForm.datasetPath"` / `label="Dataset Path"` | HDF5 dataset path within an HDF container (e.g. `/sensor_data/channel_A`) | **HIGH** | Requires `GET /v2/hdf/{appId}/datasets` backend endpoint returning a dataset tree + a picker component. Track as HDF-PATHS-PICKER in `aidocs/16`. |
| 4 | `frontend/components/context/dataobject/legacy/LegacyHdfReferencesPane.vue` | 276–284 | `v-model="createForm.datasetPath"` / `label="Dataset Path"` | Same as #3 (legacy duplicate of HdfReferencesPane) | **HIGH** | Same migration as #3. Legacy v1 path; will be removed when v1 API is deprecated. |
| 5 | `frontend/components/context/collection/edit-dialog/EditCollectionDialog.vue` | 73–83 | `v-model="updatedCollection.heroImageUrl"` / `label="Hero image URL"` | Public URL of an external banner image (JPEG/PNG) | **MEDIUM** | Replace with a FileReference upload/picker that creates a singleton FileReference; backend serves via `/v2/files/{appId}/content`. Advanced-mode-only field — lower priority than HIGH items. |
| 6 | `frontend/components/context/user/AddSubscriptionDialog.vue` | 44, 46–47 | `v-model:input-string="callbackUrl"` / `v-model:input-string="subscribedUrl"` / `label="Callback URL"` / `label="Subscribed URL"` | Webhook delivery endpoint URL + Shepard resource URL to subscribe to | **MEDIUM** | `callbackUrl` is an external webhook — legitimate URL input (not Shepard data). `subscribedUrl` should become a Collection/DataObject picker whose `appId` the backend turns into the subscription URL internally. |
| 7 | `frontend/components/context/admin/AdminJupyterPane.vue` | 183–193 | `v-model="editHubUrl"` / `label="JupyterHub base URL"` | JupyterHub deployment base URL (infrastructure config) | **MEDIUM** | Admin-only infra config — URL is an infrastructure address, not a Shepard data reference. Exempt under the operator-knob pattern (`:JupyterConfig` singleton). Document as exempt. |
| 8 | `frontend/components/context/admin/AdminInstanceRegistryPane.vue` | 264–272 | `v-model="newBaseUrl"` / `label="Base URL"` | Peer Shepard instance base URL (provenance registry config) | **MEDIUM** | Admin-only infra config — peer instance address. Exempt under operator-knob pattern. Document as exempt. |
| 9 | `frontend/components/context/admin/AdminNotificationsPane.vue` | 237–244 | `v-model="actionUrl"` / `label="Action URL (optional)"` | URL for notification action button (admin test-send form) | **MEDIUM** | Admin-only test-send affordance. For real notifications the `actionUrl` should be derived from a Shepard entity (`/collections/{appId}` etc.). Add an entity picker that pre-fills the field. Track as NTF-ACTION-URL-PICKER. |
| 10 | `frontend/components/context/admin/NotificationTransportSection.vue` | 293, 371 | `v-model="addForm.matrixHomeserver"` / `v-model="editForm.matrixHomeserver"` / `label="Homeserver URL"` | Matrix homeserver URL (transport config) | **MEDIUM** | Admin infra config — external Matrix server URL. Exempt; no Shepard data address involved. Document as exempt. |
| 11 | `frontend/components/context/admin/OntologyBundlesAdminPane.vue` | 365–370 | `v-model="uploadMeta.canonicalUrl"` / `label="Canonical URL (for future refresh)"` | Canonical ontology IRI/URL from a public registry (e.g. schema.org) | **MEDIUM** | Admin ontology upload — references an external ontology's canonical address. This is an external metadata field, not a Shepard data path. Exempt; document as such. |
| 12 | `frontend/components/context/configuration/semantic-repository-create-dialog/EndpointInput.vue` (via `CreateSemanticRepositoryDialog.vue`) | EndpointInput:8–17 | `v-model:endpoint` / `label="Endpoint*"` | SPARQL endpoint URL of an external semantic repository | **MEDIUM** | Admin infra config — external SPARQL store address. Exempt under operator-knob pattern. Document as exempt. |
| 13 | `frontend/components/krl/KrlInterpretResultPanel.vue` | 36, 56–59 | `urdfPayloadUrl` prop (component prop only — no user input rendered) | Backend-derived payload URL passed as prop; used only to build a deep-link button | **LOW** | Prop is derived from `FileReference.id` via v1 payload path. Future cleanup: accept `urdfFileAppId` directly, let the panel request the signed URL itself. Track under UI-PATHS-02. |
| 14 | `frontend/components/context/dataobject/GitReferencesPane.vue` | 195–213, 226–234, 249–253, 266–274 | `v-model="createForm.repoUrl"` / `v-model="createForm.path"` / `v-model="editForm.repoUrl"` / `v-model="editForm.path"` | Git remote URL + subdirectory path | **LOW** | By design: `GitReference` is an external URL pointer. CLAUDE.md rule exception applies — external repository links are not Shepard-owned data. See exception note below. |
| 15 | `frontend/components/context/dataobject/legacy/LegacyGitReferencesPane.vue` | 196, 227, 250, 267 | Same fields as #14 | Same as #14 (legacy duplicate) | **LOW** | Same rule exception as #14. Legacy path; removed when v1 is deprecated. |

---

## Notes on findings

### URDF URL + package path (#1, #2) — canonical violator, confirmed

`ViewRecipeBuilderDialog.vue` (lines 191–206) exposes two free-form text fields:

- **"URDF source URL"** — pre-filled with `/urdf-samples/two-link-arm.urdf`, a
  static path served from `frontend/public/`. The hint explicitly says "replace
  with a signed Garage URL of a real URDF FileReference to render your own robot."
- **"Mesh package path (optional)"** — freeform filesystem root for mesh
  `package://` URI resolution.

Both feed `?urdfUrl=` and `?packagePath=` on `pages/shapes/render.vue`. These are
the exact violations called out in CLAUDE.md and
`aidocs/16-dispatcher-backlog.md` §UI-PATHS-02 and §UI-PATHS-03.

**Secondary chain:** `RunKrlPreviewDialog.vue` (the KRL interpreter entry point) is
itself clean — it uses a `v-autocomplete` bound to `urdfFileAppId` (a
FileReference `appId` picker). However, it internally derives `urdfPayloadUrl` by
constructing a v1 API URL from `FileReference.id` and passes that URL to the
shapes renderer via `?urdfUrl=`. So even when the user entry point is clean, the
shapes renderer still receives a raw URL, and the violating query param shape
persists end-to-end. Fixing UI-PATHS-02 on the renderer side will automatically
fix the KRL downstream path too.

### HDF5 dataset path (#3, #4) — genuine violation, needs a backend picker endpoint

`HdfReferencesPane` asks the user to type a raw HDF5 internal path like
`/sensor_data/channel_A`. There is no UX affordance to browse the container's
dataset tree. The fix requires a `GET /v2/hdf/{appId}/datasets` backend endpoint
returning a dataset tree, with a picker component in the dialog.

### GitReference URL inputs (#14, #15) — rule exception, not a violation

`GitReferencesPane` asks the user to enter a `repoUrl`
(e.g. `https://gitlab.com/user/repo`) and an intra-repo `path`. Per CLAUDE.md:
> "Exceptions: external documentation links (cross-references to GitHub, w3.org,
> vendor docs) are fine — those are external resources, not Shepard data."

A `GitReference` is semantically a pointer to an _external_ repository linked to a
DataObject. The URL is the resource identity — there is no Shepard `appId` to pick
instead. These inputs are legitimate by the rule's own exception clause and should
be documented as exceptions rather than migrated. The `urlSuggestions` computed
property already reduces friction by pre-populating URL prefixes from stored git
credentials.

### Admin infrastructure URLs (#7, #8, #10, #11, #12) — exempt

These inputs configure external infrastructure (JupyterHub, peer Shepard instances,
Matrix homeserver, ontology registries, SPARQL endpoints). They are not Shepard
data addresses — they configure external systems under the operator-knob pattern.
CLAUDE.md's rule targets _user-facing Shepard data surfaces_, not admin config for
external integrations. All are exempt and should be documented as such.

---

## Clean (no violation)

Components inspected and found to have no free-form path/URL user inputs:

- `frontend/components/context/admin/AdminFileMigrationPane.vue` — no URL inputs
- `frontend/components/context/admin/AdminStoragePane.vue` — no URL inputs
- `frontend/components/context/admin/AdminTemplateDialog.vue` — no URL inputs
- `frontend/components/context/admin/SemanticConfigPane.vue` — no URL inputs
- `frontend/components/context/admin/SparqlPlaygroundPane.vue` — query input, not a URL
- `frontend/components/context/admin/PermissionAuditLogPane.vue` — no URL inputs
- `frontend/components/context/user/McpPane.vue` — read-only display of system-derived URLs; no user input field
- `frontend/components/dialog/RunKrlPreviewDialog.vue` — uses `v-autocomplete` with `appId` picker (clean entry point; downstream renderer is the violation)
- `frontend/components/krl/KrlInterpretResultPanel.vue` — prop-only, no user input exposed (LOW, tracked under UI-PATHS-02)
- `frontend/components/container/file/OpenInSceneGraphButton.vue` — navigates to an internal route path; no user input
- `frontend/components/scenegraph/AddFrameDialog.vue` — no URL inputs
- `frontend/components/scenegraph/AddJointDialog.vue` — no URL inputs
- `frontend/components/scenegraph/SceneGraphFrameInspector.vue` — no URL inputs
- `frontend/components/context/display-components/relationships/edit-uri-dialog/EditUriReferenceDialog.vue` — URI input is for external linked-data URIs (rule exception — external resource)
- `frontend/components/context/input-components/relationship/UriReferenceInput.vue` — same exception as above
- `frontend/pages/shapes/render.vue` — consumes `?urdfUrl=` and `?packagePath=` query params but does not expose a user-visible input form; the violation lives in `ViewRecipeBuilderDialog` which produces those params
- `frontend/components/context/dataobject/GitReferencesPane.vue` — rule exception (documented above as #14)
- `frontend/components/context/admin/AdminNotificationsPane.vue` actionUrl — flagged MEDIUM (#9) as fixable, but admin-only

---

## Recommended aidocs/16 rows to file

Add the following rows to `aidocs/16-dispatcher-backlog.md`
§UI-PATHS-FROM-REFERENCES (after the existing UI-PATHS-05 row):

| ID | Item | Size | Status | Notes |
|---|---|---|---|---|
| UI-PATHS-06-HDF-PICKER | Replace HDF5 dataset path text field with a browse-and-select picker. Requires `GET /v2/hdf/{appId}/datasets` backend endpoint returning a dataset tree. Frontend: tree-select component in `HdfReferencesPane` create dialog. Same fix for `LegacyHdfReferencesPane`. | M | queued | Findings #3, #4. No backend endpoint exists today. |
| UI-PATHS-07-HERO-IMAGE | Replace `heroImageUrl` free-text field in `EditCollectionDialog` with a FileReference upload/picker that creates a singleton FileReference; backend serves via `/v2/files/{appId}/content`. Advanced-mode only — lower urgency. | S | queued | Finding #5. |
| UI-PATHS-08-SUBSCRIPTION-RESOURCE | Replace `subscribedUrl` text field in `AddSubscriptionDialog` with a Collection/DataObject picker; backend constructs the subscription resource URL from the selected `appId`. Keep `callbackUrl` as text input (external webhook — rule exception) but add URL format validation. | S | queued | Finding #6. |
| UI-PATHS-09-NOTIFICATION-ACTION | Add entity picker (Collection/DataObject) to `AdminNotificationsPane` test-send form that pre-fills `actionUrl` from the selected entity's canonical URL. Keep text field as manual override for non-entity action URLs. | S | queued | Finding #9. |
| UI-PATHS-10-GIT-EXCEPTION-DOCUMENTED | Document `GitReferencesPane` `repoUrl` + `path` inputs as rule exceptions in this backlog section. External git repository URLs are not Shepard data addresses — CLAUDE.md explicitly exempts external resource links. No code change required. | XS | queued | Findings #14, #15. |
| UI-PATHS-11-ADMIN-INFRA-EXCEPTIONS-DOCUMENTED | Document admin infra URL inputs (JupyterHub, instance registry, Matrix homeserver, ontology canonical URL, SPARQL endpoint) as rule exceptions. These configure external infrastructure, not Shepard data paths. | XS | queued | Findings #7, #8, #10, #11, #12. |
