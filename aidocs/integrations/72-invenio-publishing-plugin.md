# aidocs/72 — InvenioRDM publishing plugin (`shepard-plugin-invenio`)

**Date:** 2026-05-16
**Status:** Design — ready for slice planning.
**Audience:** Contributors; operators planning a workbench-to-repository pipeline.

**Originating items.** User request 2026-05-16 (institutional InvenioRDM publishing via
shepard API). Couples to `aidocs/67` Unhide plugin, `aidocs/66` KIP/DOI minting,
`aidocs/31` RO-Crate export, `aidocs/71 §9` open question, `aidocs/47` plugin SPI.

---

## 1. What InvenioRDM is, in one paragraph

InvenioRDM is the open-source institutional repository framework powering Zenodo,
InvenioRDM.org, and dozens of institutional deployments (TU Wien, KIT, CERN). It exposes
a **REST API** for record creation, file attachment, and DOI minting via a DataCite
connection. Unlike Unhide (which is harvest-pull), InvenioRDM is **push**: the submitting
system calls InvenioRDM's API to create a draft, attach files, and publish. This makes it
the canonical destination for a researcher who has finished their campaign in shepard and
wants a citable, open-access record in the institutional repository.

shepard's role is the **workbench**; InvenioRDM is the **publication endpoint**. The
plugin bridges the two without the researcher having to interact with InvenioRDM directly.

---

## 2. Plugin shape

`shepard-plugin-invenio` follows the same `PluginManifest` SPI seam as `shepard-plugin-unhide`
(per ADR-0023, `aidocs/47 §2`). It registers:

- **REST endpoints** under `/v2/invenio/...`
- **Admin REST endpoints** under `/v2/admin/invenio/...`
- **CLI subcommands** under `shepard-admin invenio ...`
- **A Neo4j singleton config entity** `:InvenioConfig`

Plugin id: `invenio` — matches `shepard.plugins.invenio.enabled`.

---

## 3. Admin configuration

### 3.1 `:InvenioConfig` entity (runtime-mutable)

```cypher
(:InvenioConfig {
  appId: "invenio-config",
  baseUrl: "https://repository.example.org",
  apiToken: "<encrypted>",
  communityId: "dlr-bt",          // optional; targets a specific community
  defaultResourceType: "dataset",  // InvenioRDM resource_type.id
  enabled: false
})
```

The `apiToken` is stored encrypted (same pattern as `shepard-plugin-unhide` harvest key).
It is exposed via a **rotate** action, never returned in plain text by the GET endpoint.

### 3.2 Admin REST surface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/admin/invenio/config` | Return current config (token masked as `"***"`) |
| `PATCH` | `/v2/admin/invenio/config` | RFC 7396 merge-patch; `@RolesAllowed("instance-admin")` |
| `POST` | `/v2/admin/invenio/config/rotate-token` | Generate and store a new API token slot |
| `GET` | `/v2/admin/invenio/health` | Ping InvenioRDM base URL; verify token; return reachable/unreachable |

### 3.3 CLI parity

```
shepard-admin invenio status
shepard-admin invenio set-base-url https://repository.example.org
shepard-admin invenio set-api-token <token>
shepard-admin invenio set-community dlr-bt
shepard-admin invenio enable
shepard-admin invenio disable
shepard-admin invenio health
```

All flags: `--url`, `--api-key`, `--output={human,json}`.

---

## 4. Submission workflow

### 4.1 Researcher-facing REST surface

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v2/collections/{appId}/submit-to-invenio` | Start a submission job |
| `GET` | `/v2/invenio/submissions/{submissionId}` | Poll submission status |
| `GET` | `/v2/collections/{appId}/invenio-submissions` | List all submissions for a Collection |
| `POST` | `/v2/invenio/webhook/{submissionId}` | InvenioRDM callback receiver (unauthenticated, HMAC-verified) |

Permission: `POST` requires **Manager** on the Collection. `GET` requires **Reader**.

### 4.2 Step-by-step flow

```
Researcher
  │
  ├─ POST /v2/collections/{appId}/submit-to-invenio
  │    body: { license: "CC-BY-4.0", community: "dlr-bt", embargo: null }
  │    → 202 Accepted  { submissionId, statusUrl }
  │
  └─ (background job)
       │
       ├─ 1. Validate: KIP PID must exist; plugin must be enabled
       │
       ├─ 2. Build InvenioRDM metadata from Collection (see §5)
       │
       ├─ 3. POST /api/records → draft { id: "abc123" }
       │
       ├─ 4. Trigger RO-Crate export (FS1g presigned URL path):
       │      POST /v2/collections/{appId}/export → { exportJobId }
       │      Poll until done → { downloadUrl }
       │
       ├─ 5. Upload RO-Crate ZIP to InvenioRDM:
       │      POST /api/records/abc123/draft/files  [{"key": "ro-crate.zip"}]
       │      PUT  /api/records/abc123/draft/files/ro-crate.zip/content  ← stream from downloadUrl
       │      POST /api/records/abc123/draft/files/ro-crate.zip/commit
       │
       ├─ 6. If reserve-DOI requested:
       │      POST /api/records/abc123/draft/pids/doi
       │
       ├─ 7. Publish: POST /api/records/abc123/draft/actions/publish
       │      → { doi, landing_page_url }
       │
       ├─ 8. Store `:InvenioSubmission` node in Neo4j:
       │      { submissionId, collectionAppId, invenioRecordId,
       │        invenioRecordUrl, doi, status: "PUBLISHED",
       │        submittedAt, publishedAt }
       │
       ├─ 9. Record `:Activity` (PROV1a path):
       │      agent: submitting user
       │      action: "PUBLISHED_TO_INVENIO"
       │      target: collectionAppId
       │      metadata: { invenioRecordUrl, doi }
       │
       └─ 10. Send notification to researcher (see §7)
```

### 4.3 Idempotency and retry

The `:InvenioSubmission` node is keyed on `(collectionAppId, invenioRecordId)`. If the
background job fails partway through, re-submitting detects the existing draft via
`GET /api/records/abc123/draft` and resumes from the failed step — it does not create a
duplicate record in InvenioRDM.

---

## 5. Metadata mapping

| shepard field | InvenioRDM field | Notes |
|---|---|---|
| `Collection.name` | `metadata.title` | Direct |
| `Collection.description` | `metadata.description` | Direct |
| `Collection.createdAt` | `metadata.publication_date` | ISO 8601 date portion |
| `User.firstName + " " + lastName` | `metadata.creators[0].person_or_org.name` | |
| `User.orcid` | `metadata.creators[0].person_or_org.identifiers[0]` | `{scheme:"orcid", identifier:...}` |
| `body.license` (SPDX id) | `metadata.rights[0].id` | e.g. `"cc-by-4.0"` |
| `"dataset"` (default) | `metadata.resource_type.id` | Overridable per submission request |
| KIP `:Publication.pid` (resolver URL) | `metadata.related_identifiers[0]` | `{scheme:"url", relation_type:"isIdenticalTo"}` |
| `body.community` | `parent.communities.default` | Optional; targets InvenioRDM community |
| `body.embargo` | `access.embargo` | Optional; ISO 8601 date |
| `access.record = "public"` | `access.record` | Always public for published records |

The KIP PID is surfaced as a `relatedIdentifier` so the InvenioRDM record links back
to the shepard KIP resolver. This avoids DOI duplication: InvenioRDM mints the canonical
citable DOI; the KIP PID is the workbench-side persistent identity.

---

## 6. DOI coordination with KIP

Two PIDs coexist for a published Collection:

| PID | Minted by | Purpose |
|---|---|---|
| `shepard:<instanceId>:collection:<appId>:v<n>` | KIP minter (`aidocs/66`) | Workbench-side identity; resolves to the shepard Collection page |
| `10.5072/...` (DataCite DOI) | InvenioRDM | Publication-side citable identifier; resolves to InvenioRDM landing page |

The InvenioRDM record gets the KIP PID as a `relatedIdentifier` (relation: `isIdenticalTo`).
The KIP resolver record gets the InvenioRDM DOI stored in the `:Publication` node's
`relatedDoi` field (new optional field, backwards-compatible). This lets a reader at either
end navigate to the other.

If the operator's InvenioRDM instance also has a DataCite connection *and* the operator
has configured `shepard-plugin-minter-datacite` (`aidocs/66` KIP1d), the DOI minting is
deduplicated: shepard reserves the DOI in InvenioRDM via
`POST /api/records/{id}/draft/pids/doi` *instead of* calling DataCite directly.
Configuration flag: `invenio.deduplicateDoi = true`.

---

## 7. Notification dependency

A complete submission loop requires the researcher to know when:
- The submission is accepted (`PUBLISHED`)
- The submission fails (`FAILED` with reason)
- (Optional) Community moderation status changes

This is the **notification system** (N10 in `aidocs/16`). The plugin delivers notifications
via two mechanisms:

### 7.1 In-app SSE notifications (N10a)

`GET /v2/notifications/stream` (Server-Sent Events) — the UI subscribes when the
researcher is logged in. The plugin emits:

```json
{
  "type": "INVENIO_SUBMISSION_PUBLISHED",
  "collectionAppId": "...",
  "doi": "10.5072/zenodo.123456",
  "landingPageUrl": "https://repository.example.org/records/abc123",
  "submissionId": "..."
}
```

### 7.2 Email notifications (N10b)

Admin-configured SMTP (`shepard.notifications.smtp.*`). The plugin sends an email to the
submitting researcher with the DOI and landing page URL when status transitions to
`PUBLISHED` or `FAILED`.

### 7.3 InvenioRDM webhook receiver (N10c — optional)

If the InvenioRDM instance is configured to send webhooks on record state changes (e.g.
community approval), the receiver endpoint

```
POST /v2/invenio/webhook/{submissionId}
```

accepts the callback, verifies the HMAC signature against a shared secret (configurable
via `PATCH /v2/admin/invenio/config`), updates the `:InvenioSubmission` node, and fires
the SSE/email notification to the researcher.

**If InvenioRDM does not support webhooks** (the common case for stock deployments): the
plugin completes at step 7 of §4.2 and notifies immediately on publish. Community
moderation review status is not tracked in v1.

---

## 8. `:InvenioSubmission` Neo4j model

```
(:Collection {appId: $collectionAppId})
    -[:HAS_SUBMISSION]->
(:InvenioSubmission {
    appId: $submissionId,       // UUID v7
    invenioRecordId: "abc123",
    invenioRecordUrl: "https://repository.example.org/records/abc123",
    doi: "10.5072/...",         // null until published
    status: "PUBLISHED",        // PENDING | UPLOADING | PUBLISHED | FAILED
    errorMessage: null,
    submittedAt: <ISO8601>,
    publishedAt: <ISO8601>,
    submittedBy: <userAppId>
})
```

Linked to the submitting user via `(:User)-[:SUBMITTED]->(:InvenioSubmission)`.
Linked to the `:Activity` provenance node via `(:Activity)-[:RECORDED]->(:InvenioSubmission)`.

---

## 9. Phasing

| Phase | ID | Deliverable | Gate |
|---|---|---|---|
| 1 | INV1a | Plugin skeleton: `PluginManifest`, `:InvenioConfig`, admin GET/PATCH/health, CLI `status/enable/disable/set-*` | PM1a (plugin SPI) |
| 2 | INV1b | `POST /v2/collections/{appId}/submit-to-invenio` → synchronous (no job queue); metadata mapping; InvenioRDM draft create + file upload + publish | FS1g (presigned export URL) |
| 3 | INV1c | Move submission to background job (async, per `aidocs/32` pattern); `GET /v2/invenio/submissions/{id}` polling endpoint | aidocs/32 job pattern |
| 4 | INV1d | In-app SSE notifications on publish/fail (N10a) | N10a notification system |
| 5 | INV1e | Email notification on publish/fail (N10b) | SMTP config, N10b |
| 6 | INV1f | InvenioRDM webhook receiver (N10c); community moderation status tracking | N10c; optional |
| 7 | INV1g | DOI deduplication with DataCite (if `invenio.deduplicateDoi=true`) | KIP1d DataCite minter |

INV1a + INV1b are the minimal viable plugin. INV1c–INV1e complete the production-grade
experience. INV1f–INV1g are refinements.

---

## 10. Workflows and notification system design (N10)

The InvenioRDM plugin is the first consumer that makes the notification system a
**near-term priority** rather than a deferred nicety. The system design:

### 10.1 Notification entity

```
(:Notification {
    appId: $notifId,
    userId: $userAppId,
    type: "INVENIO_SUBMISSION_PUBLISHED",
    payload: <JSON>,
    read: false,
    createdAt: <ISO8601>
})
```

### 10.2 REST surface

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v2/notifications` | List unread (+ recent read) notifications for caller |
| `POST` | `/v2/notifications/{id}/read` | Mark read |
| `DELETE` | `/v2/notifications/{id}` | Dismiss |
| `GET` | `/v2/notifications/stream` | SSE stream for live delivery |

### 10.3 Delivery

1. **SSE** (N10a): `GET /v2/notifications/stream` keeps the frontend connected; a Quarkus
   `@ServerSentEvent` emitter pushes events as they land. Frontend subscribes when logged in.
2. **Email** (N10b): `quarkus-mailer` with admin-configured SMTP. Sent on
   notification creation for notification types that opt in (plugin-defined).
3. **Webhook** (N10c): Outbound `POST` to a user-configured URL (future — not in INV1 scope).

### 10.4 Plugin extension point

Each plugin registers the notification types it emits via `PluginManifest.notificationTypes()`.
The `NotificationService` (core) handles persistence + SSE delivery; plugins call
`notificationService.emit(userId, type, payload)`.

---

## 11. See also

- `aidocs/integrations/66-hmc-kip-integration.md` — KIP PID minting (DOI coordination)
- `aidocs/integrations/67-unhide-publish-plugin.md` — parallel plugin shape (reference implementation)
- `aidocs/workflows/31-rocrate-export-optimisation.md` — RO-Crate export (the file we attach)
- `aidocs/platform/47-dev-experience-and-plugin-system.md` — plugin SPI reference
- `aidocs/strategy/71-fork-adoption-as-upstream.md §9` — originating open question
- `https://inveniordm.docs.cern.ch/reference/rest_api_drafts_records/` — InvenioRDM REST API
