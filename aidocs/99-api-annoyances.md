# 99 — Shepard API annoyances (structural clunkiness)

**Status.** Living document — append-only.
**Snapshot date.** 2026-05-21.
**Audience.** Backend maintainers (these are *architectural* fix targets);
API consumers (these are workarounds to know about).
**Originating prompt.** User 2026-05-21: *"try to use the api as much as
possible (direct test) — if we notice things become hairy list it in api
annoyances document"* + later *"api annoyances more structurally —
clunkyness"*.

## Inclusion bar

> *"More structural — clunkiness."*

This doc captures **design-level** issues that force API consumers into
unnatural patterns. NOT field-rename suggestions, NOT
error-message-clarity wishes, NOT minor inconsistencies.

Each entry MUST satisfy:

1. **Structural, not paper-cut.** The fix is a *design change* (new
   endpoint, model rework, contract redesign, lifecycle pattern), not a
   rename or copy-edit.
2. **Forces unnatural client code.** A reasonable consumer doing a
   reasonable task hits a wall and has to write workaround logic that
   shouldn't exist (N+1, manual JWT-decode, polling instead of
   subscribing, two calls to do one job, etc.).
3. **Reproducible across consumers.** Anyone writing a script against
   Shepard would hit it; not a one-off quirk.

In scope:
- Missing core conveniences (no `/me`, no batch, no streaming where it'd
  matter, no transaction primitives)
- Wrong abstraction level (storage IDs leaking, internal types in
  responses, container ID + reference ID + payload ID all needed for one
  fetch)
- Identity / naming mismatches across surfaces (V1 numeric ID vs V2 appId
  vs OIDC subject vs preferred_username)
- N+1 fetch patterns by design
- Inconsistent error envelopes / status codes across endpoint families
- Missing lifecycle states (no soft delete, no versioning where users
  expect it)

Out of scope (handled elsewhere):
- Field naming preferences (PR comment / OpenAPI polish)
- Error message wording (PR comment / i18n)
- HTML-vs-markdown render preferences (frontend concern)
- Single-endpoint bugs (issue tracker)

---

## A-01 — Username is the Keycloak UUID, not `preferred_username`

**Severity.** ★★ (medium — bites every script that wants a stable username)

**What I tried.** `GET /shepard/api/users/claude-opus-4-7/apikeys` to list my own keys.

**What bit.** 403 / 404 — Shepard stores the JWT `sub` claim as `username`,
which for Keycloak users is the UUID (`9a176950-418c-…`). The
human-friendly `preferred_username: claude-opus-4-7` is **discarded** at JWT
parsing time (`JwtTokenAuthService.parsePrincipalFromAccessToken` line 154:
`String username = splitted[splitted.length - 1]` where the split is on `:`).

**Worked around** with the UUID. Pinned it in
`/root/.config/shepard/claude-credentials.env` as
`SHEPARD_INTERNAL_USERNAME`.

**Fix proposal.** Two clean options:
- (a) Prefer `preferred_username` over `sub` when building `JWTPrincipal`,
  fall back to `sub` only if absent. Mostly-additive change.
- (b) Add a `/shepard/api/users/me` convenience endpoint that resolves to
  the caller's stored username. Avoids the UUID-in-URL ugliness.

Both are low-risk; (b) is the bigger UX win.

---

## A-02 — No `/users/me` endpoint

**Severity.** ★★★ (high — every API consumer trips on this first thing)

**What I tried.** `GET /shepard/api/users/me`.

**What bit.** 404 *"User with name me not found"* — `me` is treated as a
literal username. There is no self-resolving alias.

**Worked around** by extracting `sub` from the JWT client-side before
calling. Means every consumer must know about (A-01) + decode JWT to find
themselves.

**Fix proposal.** Add `/shepard/api/users/me` (and `/me/apikeys`,
`/me/subscriptions`) that resolves from the authenticated principal. Two
hours of work; closes maybe ¼ of the script frustration.

---

## A-03 — Collection contains `dataObjectIds` but not a `dataObjects` summary list

**Severity.** ★★ (medium — forces N+1 fetches)

**What I tried.** `GET /shepard/api/collections/493423` to see what's in
the AI Exchange Collection.

**What bit.** Got `"dataObjectIds": [493456]` — just the numeric IDs.
To learn the names / kinds / per-payload counts, I have to fan out N
GETs. The V2 API (per existing memory entry on `/v2/collections/{appId}/data-objects`)
already returns summaries; the V1 surface doesn't.

**Worked around** by following the IDs one at a time.

**Fix proposal.** Backport the V2 `/data-objects` summary endpoint
behaviour as an optional `?withSummaries=true` on the V1 list endpoint.
Saves bandwidth and the N+1 pattern.

---

## A-04 — Payload fetch needs three IDs (container + reference + oid) for what should be one resource

**Severity.** ★★★★ (high — every single payload fetch hits this)

**What I tried.** Download one of the markdown files I uploaded to
DataObject 495374. The file lives at oid `6a0f693c115ed57ea82090d5` inside
container 493473, referenced by FileReference 495420.

**What bit.** To download, the endpoint is
`/shepard/api/fileContainers/{containerId}/payload/{oid}` — separate from
the reference (which is at
`/shepard/api/collections/{cid}/dataObjects/{doid}/fileReferences/{refId}`).
**Two paths into one logical thing.** The reference knows the oid + the
container; a consumer that holds only the reference URL must do an extra
GET to learn the container + oid before downloading. The MCP `get_data_object`
similarly has to walk the reference → container → oid chain to compose
the download URL.

**Structural problem.** The model conflates *storage location*
(`{container,oid}`) with *logical identity* (the FileReference). Real
storage moves (S3 vs GridFS rebinding under the StorageProvider SPI); the
consumer-facing path should be opaque to storage. **The reference itself
should expose `payloadUrl` / `payloadHref`** in its JSON, so a consumer
holding the reference holds everything needed to download.

**Same shape on structured + timeseries** — a structured-data document
fetch needs `/structuredDataContainers/{cid}/structuredDatas/{oid}` with
the SD container ID gathered separately. Timeseries reference → channel
identity → 5-tuple → query window is the same problem at higher fan-out.
Every payload kind needs the same three-IDs-for-one-resource walk.

**Fix proposal.** On every Reference (File / Structured / Timeseries),
include `payloadUrl` (or HAL-style `_links.payload.href`) populated by the
server. Consumers stop assembling URLs from path components; storage
relocations become transparent.

---

*(Add new entries below as encountered.)*
