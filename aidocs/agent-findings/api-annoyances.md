# API annoyances — running log

Running log of friction encountered while using the shepard REST API
(`/shepard/api/...` upstream-compat + `/v2/...` fork surface). Append-only,
date-grouped. Each entry follows the shape:

- **verb + path** + status observed
- **expected:** one sentence
- **got:** one sentence
- **workaround:** copy-paste recipe
- **fix:** the change that would make this not friction

This file is the standing input to the `api-scrutinizer` agent role and to
backlog rows under `aidocs/16` API-critique section. See
`feedback_capture_api_ui_annoyances.md` in agent memory for the discipline.

---

## 2026-05-22

### File upload via v1: 404 on the documented `POST /shepard/api/fileContainers/{id}/files` path

- **verb + path:** `POST /shepard/api/fileContainers/473932/files` (multipart `file=@...`)
- **expected:** create a new file in the container, return the file IO with `appId` so I can curl the content back
- **got:** `HTTP 404` with body `{"type":"https://noheton.github.io/shepard/errors/not_found.entity","title":"Resource not found","status":404,"detail":"Unable to find matching target resource method"}`
- **workaround:** unknown yet — `POST /file` (singular) under the same prefix had not returned by the time I had to context-switch; needs `/v2/file-containers/{appId}/...` exploration
- **fix:** the file-upload endpoint should be discoverable from `GET /v2/file-containers/{appId}` (currently returns container metadata without an `_links` / `_actions` map naming the upload sub-path). The 404 problem-json body should include `_hints.allowedMethods` or `_hints.relatedEndpoints` so a caller can find the right verb without a tab-out to the Swagger UI.

### `GET .../dataObjects/{id}/fileReferences` returns `[]` on a DO that visibly has a file

- **verb + path:** `GET /shepard/api/collections/515365/dataObjects/515376/fileReferences` (the upstream-compat path) and the v2 equivalent
- **expected:** the list of FileReferences on the DataObject — at least the v14 `mffd-dropbox-import.py` file ref I had just confirmed via `GET /v2/files/{appId}`
- **got:** `[]` (empty array, status 200) — but `GET /v2/files/019e50bd-...` returns the file with `dataObjectId: 515376`, proving the reference exists
- **workaround:** address the file directly by its appId via `GET /v2/files/{appId}` and `GET /v2/files/{appId}/content`; the listing endpoint is misleading
- **fix:** either the listing is hitting a permissions side-effect (file present, ref-list filtered) or there's a stale-cache bug. Either way, a list that returns `[]` while a direct GET on a member returns 200 is the worst kind of API friction — silent inconsistency.

### `POST /shepard/api/fileContainers` documented; `GET /v2/file-containers/{appId}` returns metadata without an `_actions` map

- **verb + path:** `GET /v2/file-containers/{appId}`
- **expected:** the container shape plus a hint at how to upload a file into it (HATEOAS `_links`, or `uploadUrl`, or even `actions: ["POST /v2/file-containers/{appId}/upload-url"]`)
- **got:** just the container metadata (id, name, appId, createdAt, createdBy …) — no action hints
- **workaround:** read `aidocs/integrations/93-mffd-import-v15-requirements.md §6` to find the presigned-URL three-step flow
- **fix:** add the actions hint to the response body. Even a single `_actions.upload` field naming the relative path would save a caller a tab-out.

### Constants.SYMBOLICNAME = `symbolic_name` query param but `symbolicName` in response JSON

- **verb + path:** all timeseries query endpoints
- **expected:** request and response use the same case convention for the same field
- **got:** request param is snake_case (`symbolic_name`, `csv_format`); response JSON is camelCase (`symbolicName`)
- **workaround:** memorise the split (durable note in `project_mffd_api_keys.md`)
- **fix:** align both at the next major version. Standardise on camelCase across all v2 surfaces; deprecate the snake_case param with a `Deprecation` header.

### `X-Total-Count` + `X-Total-Pages` headers ABSENT on paged list endpoints

- **verb + path:** all v2 list endpoints
- **expected:** the standard pagination headers from RFC 7807-adjacent practice, or at least a `_meta.total` envelope
- **got:** neither — caller has to paginate until empty page or assume page size
- **workaround:** loop until empty
- **fix:** RFC 7807 problem-json envelope on paged lists, with `_meta.total` and `_meta.totalPages`. Or `X-Total-Count` header. Pick one and ship.

---

## How to add an entry

Open this file, add a new dated subsection under today's date heading,
and follow the entry shape above. **Capture friction the moment you hit
it**, not at session end — memory of "what was annoying" decays fast.
