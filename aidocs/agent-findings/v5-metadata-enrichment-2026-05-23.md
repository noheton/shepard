---
title: "V5-METADATA-SURVEY — v5 OpenAPI surface: metadata enrichment opportunities"
stage: concept
last-stage-change: 2026-05-26
audience: contributors, frontend developers, product owner
---

# V5-METADATA-SURVEY — Metadata enrichment opportunities from v5 OpenAPI surface

**Context.** Backlog item V5-METADATA-SURVEY (`aidocs/16`). Research
pass over the full v5 OpenAPI fixture
(`backend/src/test/resources/fixtures/v5/openapi-5.4.0.json`, based on
upstream shepard 5.4.0 / 5.2.0 wire compat) to find endpoints and fields
we either don't call at all or call but silently discard. Compared against
what the frontend composables and Vue components actually access.

---

## What I found

### v5 endpoint inventory (relevant groups)

The v5 surface has 85 paths. The groups relevant to metadata enrichment
are summarised below with their current usage status.

**User / identity group** (`/users`, `/users/{username}`,
`/users/{username}/subscriptions`, `/users/{username}/apikeys`)

The `User` schema returns:

| Field | Type | Notes |
|-------|------|-------|
| `username` | string | Primary key |
| `firstName` | string | Nullable |
| `lastName` | string | Nullable |
| `email` | string | Nullable |
| `subscriptionIds` | int64[] | readOnly — IDs only, not the objects |
| `apiKeyIds` | UUID[] | readOnly — IDs only, not the objects |
| `appId` | string | Fork addition — UUID v7 |
| `orcid` | string | Fork addition — ISO 7064 mod 11-2 |
| `displayName` | string | Fork addition — user-chosen override |
| `effectiveDisplayName` | string | Fork addition — computed with fallbacks |

The fork's `UserIO.java` extends the upstream shape with `appId`, `orcid`,
`displayName`, and `effectiveDisplayName`. These are exposed on both the
v1 `/shepard/api/users` surface (with `@JsonInclude(NON_NULL)` guards to
stay upstream-compatible) and the v2 `/v2/users/me` surface via `MeIO`.

The v2 `MeIO` record also adds `watchedCollectionCount` (int) — number of
collections the caller is watching (CW1 feature).

**UserGroup group** (`/userGroups`, `/userGroups/{id}`,
`/userGroups/{id}/permissions`, `/userGroups/{id}/roles`)

The `UserGroup` schema returns:

| Field | Type | Notes |
|-------|------|-------|
| `id` | int64 | Numeric internal ID |
| `name` | string | Human name |
| `usernames` | string[] | All member usernames |
| `createdAt` | datetime | |
| `createdBy` | string | Username |
| `updatedAt` | datetime | Nullable |
| `updatedBy` | string | Nullable |

No fork extensions exist on this schema. The `UserGroupDetailView.vue`
only displays the `id` field from audit metadata; `createdAt` / `createdBy`
/ `updatedAt` / `updatedBy` are carried in the response but never rendered.

**Permissions group** (six endpoints: `/collections/{id}/permissions`,
`/fileContainers/{id}/permissions`, `/timeseriesContainers/{id}/permissions`,
`/structuredDataContainers/{id}/permissions`,
`/spatialDataContainers/{id}/permissions`,
`/userGroups/{id}/permissions`)

The `Permissions` schema:

| Field | Type | Notes |
|-------|------|-------|
| `entityId` | int64 | readOnly |
| `owner` | string | Username |
| `permissionType` | enum | `Public`, `PublicReadable`, `Private` |
| `reader` | string[] | Usernames |
| `writer` | string[] | Usernames |
| `manager` | string[] | Usernames |
| `readerGroupIds` | int64[] | Group IDs |
| `writerGroupIds` | int64[] | Group IDs |

Note: there is no `managerGroupIds` in the v5 schema — groups can only
be readers or writers, not managers. The `manager` array is username-only.

**Subscriptions** (`/users/{u}/subscriptions`, `/users/{u}/subscriptions/{id}`)

The `Subscription` schema:

| Field | Type | Notes |
|-------|------|-------|
| `id` | int64 | |
| `name` | string | |
| `callbackURL` | string | Nullable — the HTTP endpoint to notify |
| `subscribedURL` | string | The Shepard resource URL being watched |
| `requestMethod` | enum | HTTP method for callbacks |
| `createdBy` | string | |
| `createdAt` | datetime | |

The callback schema (`Event`) carries `{subscription, subscribedObject, url,
requestMethod}` — `subscribedObject` is a `HasId` reference.

**Search** (`/search`, `/search/collections`, `/search/containers`,
`/search/users`, `/search/userGroups`)

The `CollectionSearchResult` carries a `totalResults: int32` alongside
`results: Collection[]`. `ContainerSearchResult` similarly carries
`totalResults` + `results: BasicContainer[]`. Both are **currently used**
by the frontend (`useSearchCollections.ts` reads `response.totalResults`).

`UserSearchResult` returns `results: User[]` — full `User` objects.
`UserGroupSearchResult` returns `results: UserGroup[]` — full `UserGroup`
objects.

The search query shape for users and user groups is identical: a bare
`query: string` matching against the entity name. The query is constructed
by `buildUserQueryString()` / `buildUserGroupQueryString()` in the frontend.

**Statistics / counts**

The v5 surface has **no dedicated statistics or count endpoints**. The
only numeric metadata are:

- `CollectionSearchResult.totalResults` — used in pagination
- `ContainerSearchResult.totalResults` — used in pagination
- `UserSearchResult.results.length` — no separate count endpoint
- `User.subscriptionIds.length` — not surfaced anywhere in the UI
- `User.apiKeyIds.length` — not surfaced anywhere in the UI

The v2 `MeIO` adds `watchedCollectionCount` (CW1), but this is a fork
extension, not in the v5 surface.

---

## Enrichment opportunities

| Endpoint | Field | What it contains | How we could use it |
|----------|-------|-----------------|---------------------|
| `GET /users/{username}` | `email` | User email address | Already rendered in `ProfilePane.vue` (own user). **Not rendered for other users** in permission dialogs — `MemberPermissionList` shows only `username`. Enriching with `email` + `effectiveDisplayName` would make the permission list unambiguous (e.g., distinguish two users both named "Alex"). |
| `GET /users/{username}` | `subscriptionIds.length` | How many Shepard resources this user watches | Could surface as "activity signal" — a user who watches 50+ collections is heavily invested. Useful for contributor-view or a future leaderboard feature (#21). Currently never rendered. |
| `GET /users/{username}` | `apiKeyIds.length` | Number of API keys the user has | Could surface on the admin panel as a quick "programmatic-access" indicator. Currently never rendered (the list itself is accessible via `GET /users/{u}/apikeys`). |
| `GET /userGroups/{id}` | `createdAt` + `createdBy` | When/who created this group | The `UserGroupDetailView.vue` exposes `id` but not audit timestamps. Surfacing `createdAt` + `createdBy` closes the provenance gap for the group entity (mirrors the pattern used for Collections and DataObjects). |
| `GET /userGroups/{id}` | `usernames.length` | Member count | Currently the member list is fetched and iterated, but no aggregate count is shown. Displaying "N members" as a chip on the group list view would match the Collection card style. |
| `GET /[container]/{id}/permissions` | `permissionType` | `Public` / `PublicReadable` / `Private` | **Currently fetched but rendered only on the Collection card**. Container-level `permissionType` is fetched by `useFetchCollectionPermissions` but the composed view only exposes `owner`. A container-level permission badge (e.g., a lock icon on FileContainer detail pages) would be a quick win. |
| `GET /[container]/{id}/permissions` | `readerGroupIds` + `writerGroupIds` | Group IDs with access | Currently consumed by `mapPermissions.ts` in the edit dialog. **Not rendered on the read-only permissions view** — a researcher viewing a collection's permissions can't see group-based readers without opening the edit dialog. Surfacing group names (resolved via `GET /userGroups/{id}`) on the read-only view closes this gap. |
| `GET /users/{u}/subscriptions` | `subscribedURL` | The full Shepard resource URL being watched | Currently displayed as raw string in `SubscriptionsPane.vue`. Could be parsed to resolve the resource name inline — e.g., if `subscribedURL` ends in `/collections/42`, resolve the Collection name and render "LUMEN hotfire 2024" instead of the raw URL. |
| `GET /users/{u}/subscriptions` | `createdAt` | When the subscription was created | Used only for sort order in `useFetchSubscriptions.ts`. Not rendered in the `SubscriptionsPane.vue` table despite being in the schema. "Watching since" is useful context for a researcher managing many subscriptions. |
| `GET /search/users` | `User.email` | Email from search results | The `MemberAutocomplete` currently shows `username` (or UserGroup `name`). Showing `email` alongside `username` in the autocomplete dropdown would let managers assign permissions to the right "John Smith" unambiguously. |
| `GET /search/users` | `User.effectiveDisplayName` | Resolved display name | Currently the permission autocomplete renders the bare `username` for users who have a display name set. `effectiveDisplayName` is already in the search response and would make the autocomplete display "Florian Krebs (fkrebs)" instead of "fkrebs". |

---

## Current usage

Fields and endpoints we already call and consume:

| Endpoint | Fields consumed | Where |
|----------|-----------------|-------|
| `GET /users` (current user) | `username`, `firstName`, `lastName`, `email`, `appId`, `orcid`, `displayName`, `effectiveDisplayName` | `ProfilePane.vue`, `PersonalDigest.vue` |
| `GET /users/{username}` (other users) | `username`, `firstName`, `lastName`, `email`, `appId`, `effectiveDisplayName` | `useFetchCollectionPermissions.ts` (for `owner`), `mapPermissions.ts` (for permission lists) |
| `GET /users/{u}/subscriptions` | `id`, `name`, `callbackURL`, `subscribedURL`, `requestMethod`, `createdAt` (for sort) | `SubscriptionsPane.vue` (renders all except sorted-only `createdAt`) |
| `GET /userGroups/{id}` | `id`, `name`, `usernames` | `UserGroupDetailView.vue`, `useHandleUserGroupMembers.ts` |
| `POST /search/users` | `results` (User objects — `username`, `effectiveDisplayName` via `effectiveDisplayName` field) | `useMemberSearch.ts` — but only `username` and `name` actually rendered in autocomplete |
| `POST /search/userGroups` | `results` (UserGroup objects — `name`, `id`) | `useMemberSearch.ts`, `useCreateUserGroup.ts` |
| `GET /[container]/{id}/permissions` | `owner`, `permissionType`, `reader`, `writer`, `manager`, `readerGroupIds`, `writerGroupIds` | `useFetchCollectionPermissions.ts`, `mapPermissions.ts` |
| `GET /collections/{id}/roles` | `owner`, `manager`, `writer`, `reader` (booleans) | Current user's role on a collection — used for UI conditional rendering |

Fields **never rendered** despite being fetched:

- `User.subscriptionIds` — fetched in `UserIO` but never read by the UI
- `User.apiKeyIds` — fetched in `UserIO` but never read by the UI (the
  key list is fetched separately via the dedicated `/apikeys` endpoint)
- `UserGroup.createdAt`, `createdBy`, `updatedAt`, `updatedBy` — never
  rendered
- `Subscription.createdAt` — fetched, used only for array sort, never
  rendered in the table

The `Subscription.callbackURL` is rendered raw; it is never resolved to a
human-readable label.

---

## Quick wins

### QW-1: Resolve subscription URLs to resource names (effort: S)

**What:** In `SubscriptionsPane.vue`, the `subscribedURL` column currently
shows a raw Shepard API path (e.g.,
`https://…/shepard/api/collections/42`). Parse the collection/container ID
from the URL, resolve it to a name via the existing API client, and display
`LUMEN hotfire 2024` with the raw URL as a tooltip.

**Implementation:** Add a `resolveSubscribedUrl(url: string)` helper in the
composable that extracts the entity type + ID, calls the appropriate `getX`
endpoint, and returns `name | id`. Cache results in a Map to avoid N+1
fetches. This pattern is already used for the permission dialog (which
resolves usernames to `User` objects).

**Why it matters:** A user who watches 30+ collections sees 30 opaque API
strings. This is the single highest-friction surface in the Subscriptions
pane and the most commonly requested improvement in the persona audits.

**Risk:** None — purely additive UI rendering. No schema changes.

---

### QW-2: Show `effectiveDisplayName` in permission autocomplete (effort: XS)

**What:** The `MemberAutocomplete.vue` currently renders the bare `username`
for user entries (visible when assigning permissions). The `User` objects
returned by `POST /search/users` already carry `effectiveDisplayName`. Show
`{effectiveDisplayName} ({username})` instead of just `{username}`.

For user groups, the existing `title: \`${member.name} (User Group, ID:
${member.id})\`` pattern is a good model. For users:
`title: \`${member.effectiveDisplayName ?? member.username} (${member.username})\``.

**Implementation:** Single line change in `MemberAutocomplete.vue`'s `title`
computation for the User branch. Already in scope: `effectiveDisplayName`
is in the response from `POST /search/users`.

**Why it matters:** Institutes where the Keycloak identity provider issues
cryptic UUID-format usernames (the pattern PROV-USER-ENRICH and MFFD-IMPORT-
USER-CAPTURE address on the prov side) make permission assignment unusable
because the autocomplete shows opaque strings. The `effectiveDisplayName`
field was added for exactly this problem.

**Risk:** None. The field is already in the API response; no additional
network calls needed.

---

### QW-3: Render `permissionType` badge on container detail pages (effort: S)

**What:** The `Permissions.permissionType` enum (`Public`, `PublicReadable`,
`Private`) is already fetched for Collections and rendered on the Collection
card as a lock icon. The same field exists on all five container types
(FileContainer, TimeseriesContainer, StructuredDataContainer,
SpatialDataContainer) and UserGroup. It is **never rendered** on container
detail pages.

**Implementation:** Add a `PermissionTypeBadge` component (reusing the
Collection card's lock-icon pattern) and include it on `FileContainerPage`,
`TimeseriesContainerPage`, etc. Fetch permissions using the existing
`/{containerType}/{id}/permissions` endpoint that most container detail
pages already call (or add the call where missing).

**Why it matters:** A researcher opening a FileContainer cannot tell at a
glance whether it is private, public-readable, or fully public. Adding a
badge removes the need to open the "Permissions" tab to discover visibility.
The FAIR-A1 dimension (open access metadata) is served by making access
status visible without a click.

**Risk:** Low. Requires the permissions fetch on container pages that don't
yet call it (SpatialDataContainer, some StructuredDataContainer pages). The
same fetch pattern is used for Collections.

---

## What surprised me

**1. The v5 User schema has no ORCID field.** The upstream v5 surface is
bare: `{username, firstName, lastName, email, subscriptionIds, apiKeyIds}`.
This fork already ships `orcid`, `displayName`, `effectiveDisplayName`, and
`appId` as v1 wire extensions — more than the obvious value; it means any
client built for upstream v5.2.0 gets extra metadata for free. The fork's
FAIR-R1 identity work is more complete than it looks from the OpenAPI spec
alone.

**2. There are no statistics endpoints in the v5 surface.** No
`GET /collections/{id}/stats`, no `GET /users/{u}/activity`, no global
count endpoints. Every aggregate the UI needs (DataObject count, container
count, subscriber count) must be either derived client-side from list
endpoints or added in the v2 surface. The `totalResults` field in
`CollectionSearchResult` and `ContainerSearchResult` is the only numeric
aggregate in the v5 surface. The v2 `MeIO.watchedCollectionCount` is the
first instance of the fork filling this gap.

**3. `Permissions.managerGroupIds` does not exist.** Groups can be added as
readers or writers, but not as managers. Only individual usernames can be
managers. This means an admin group cannot be assigned manager-level access
— only individual admins can. This is not a bug in our implementation; it is
a deliberate upstream design constraint that limits the group permission model.

**4. `createdBy` is a bare username string everywhere.** Collections,
UserGroups, LabJournalEntries, all containers — every entity carries
`createdBy: string` (a username) but not a resolved display name or ORCID.
The `citation.ts` utility comment explicitly notes this gap and reasons
through it. A `/v2/collections/{id}` enriched shape that resolves
`createdBy` to `{username, effectiveDisplayName, orcid?}` would close the
"bare username in citations" problem without changing the v1 wire shape.
This is a medium-sized v2 endpoint addition that would pay back across
citations, provenance displays, and the metadata completeness widget.

**5. The `Subscription.subscribedURL` is a Shepard API URL, not a
Collection `appId`.** This means subscriptions are anchored to numeric
internal IDs embedded in REST paths. When a subscription URL is
`/shepard/api/collections/42`, it implicitly ties to the numeric `id = 42`
— not to the stable `appId` (UUID v7). After the L2 migration to
`appId`-native paths, subscriptions created today will carry legacy URL
strings. This is a known upstream design issue but worth flagging as the
fork moves toward `appId`-native paths in v2.
