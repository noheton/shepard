---
stage: deployed
last-stage-change: 2026-05-23
---

# User Profile & Settings — Design

**Scope.** Implementation design for a user-profile feature
(originating in **issue #29** — "Add field for ORCID for user")
plus a forward-looking "user settings" surface that other small
per-user knobs can grow into without a redesign every time. Also
reassesses the existing frontend "Configuration" area from
`aidocs/33 §W12` and recommends a split, not a merge.

**Status.** Design. No code or migration shipped.
**Snapshot date.** 2026-05-08.
**Originating items.** #29 (ORCID, this design's anchor),
#694 (display first/last name not username), #628 (cryptic Keycloak
usernames), #483 (user groups admin), `aidocs/33 §W12` (today's
Configuration UI shape).

## 1. Why this is a single design, not three commits

Three things land together because they share state and surface:

1. **A new field on `User`** (`orcid`) needs a writable endpoint and
   a UI input. We don't have a per-user "profile" surface today; the
   existing `User` is OIDC-mirrored and effectively read-only.
2. **More fields will follow.** The next likely additions:
   `displayName` override, `defaultPermissionsForNewEntities`, UI
   theme, time zone, notification preferences. Designing the
   `/users/me` shape and the UI extension point once is cheaper
   than four piecemeal PRs.
3. **The Configuration page is mixed.** Today's umbrella mixes
   personal data (profile, my API keys, my subscriptions) with
   admin / shared-system data (user groups, semantic repositories).
   The audience is different; the navigation should reflect that.

## 2. Current state (read once, don't repeat)

Today's UI per `aidocs/33 §W12` and `pages/configuration/index.vue`:

| Pane | URL fragment | What it manages | Audience |
|---|---|---|---|
| Profile | `#profile` | First/last name + email (read-only mirror of OIDC userinfo) | **me** |
| API Keys | `#apikeys` | My API keys: list, create, delete, copy JWT (#629) | **me** |
| Subscriptions | `#subscriptions` | My webhook subscriptions | **me** |
| User Groups | (route in `userMenuItems.ts`) | Create / edit / delete groups; assign members | **admin** |
| Semantic Repos | (route in `userMenuItems.ts`) | Register / configure ontology endpoints | **admin** |

Backend `User` entity (`backend/.../auth/users/entities/User.java`):
`username` (PK; OIDC subject — sometimes the cryptic Keycloak UUID
form per #628), `firstName`, `lastName`, `email`, `appId` (from L2a),
plus relationships to `ApiKey[]` and `Subscription[]`. **No fields
that the user themselves owns and writes.**

## 3. The "user profile" concept

A profile is the small set of attributes about *me* that:

- shepard surfaces in audit-trail / authorship contexts (resolves
  #694: "Created by …" should show display name, not the OIDC
  subject UUID),
- the operator does **not** need an admin role to set,
- have **no impact on access control** (those stay in the permission
  graph).

**Profile fields, today:** `firstName`, `lastName`, `email`
(mirrored from OIDC, not user-editable since the IdP is the source
of truth — changing them in shepard would drift on next login).
**Profile fields, after this design:**

| Field | Editable by | Source of truth | Validation |
|---|---|---|---|
| `firstName`, `lastName`, `email` | (read-only) | OIDC userinfo, refreshed at every login | n/a |
| `displayName` | **me** | shepard-local override | Optional. ≤ 80 chars. Falls back to `firstName + " " + lastName` then to a redacted form of `username` (#628). |
| `orcid` | **me** | shepard-local | Format `0000-0000-0000-000X` (the X-checksum form). ISO 27729 / ORCID's mod 11-2 algorithm. Empty allowed. |

**Profile picture (avatar).** Tiered design — see §3.1 below.

Two explicit non-fields:

- **Password** — IdP's job, not shepard's.
- **Permissions defaults** — a real ask but a separate axis (touches
  `aidocs/24 §F`); deferred until the permission-system evolutions
  land.

### 3.1 Profile picture / avatar

Three sources, in precedence order at render time. shepard never
stores a copy of an external avatar; it composes a URL.

| Source | When | Cost | UI control |
|---|---|---|---|
| **shepard-uploaded** | User uploaded a file via `/me/avatar` | Mongo storage (small) | "Upload" + "Remove" buttons |
| **OIDC `picture` claim** | IdP returned `picture` in userinfo | None — the URL is whatever the IdP gives | Read-only; "use IdP avatar" toggle |
| **Gravatar** | Computed from `email` hash | None — Gravatar serves the URL | Always-on default; no UI toggle |

Render order: shepard-uploaded > IdP > Gravatar > built-in initials
fallback (`<displayName>` initials in a coloured circle, server-side
seed = `appId` so the colour is stable per user). The fallback is
**always present** — no broken-image icons.

**Gravatar URL composition.** `https://www.gravatar.com/avatar/<sha256(email.trim().toLowerCase())>?s=128&d=identicon`.
shepard sends only the SHA-256 of the email (not the email itself);
Gravatar matches the hash against its registered users, returns an
`identicon` if there's no match. **Privacy note** in the operator
docs: this leaks (the hash of) every shepard user's email to
Gravatar's CDN at avatar-render time. Recommend the operator-side
toggle `shepard.profile.gravatar.enabled` (default `true`) so a
DLR-internal deployment can disable it for compliance.

**shepard-uploaded avatar specifics.**

| Field | Value |
|---|---|
| Endpoint | `PUT /users/me/avatar` (multipart), `DELETE /users/me/avatar` |
| Max size | 1 MiB (enforced server-side; reject 413) |
| Formats | PNG / JPEG / WebP. Reject GIF (animated avatars are noise). |
| Storage | Mongo via `ShepardFile`, but in a dedicated `userAvatars` Mongo collection so the lifecycle is clearly per-user, not collection-attached |
| Lifecycle | Deleted when the user is deleted; never garbage-collected on its own |
| Public URL | `GET /users/{appId}/avatar` returns a stable cacheable URL with `Cache-Control: public, max-age=300` and `ETag` |
| Image processing | None — accept what the user uploads, render at the requested `?s=` size client-side or via a Caddy/nginx image-resize filter. shepard does not ship ImageMagick. |

**`UserPublicIO`** projection includes the **render URL** (an opaque
URL the frontend can `<img src=...>` directly), not the raw
upload-path. The URL resolves through the precedence chain. This
keeps the frontend ignorant of which source served the image.

**Out of scope for v1:** image cropping UI, multiple avatars,
animated avatars, NFT-checked profile pictures (no, really, no).

### 3.2 Preferences (account-scoped knobs)

Preferences are per-user settings that affect *how* shepard renders
or behaves for me, not *what* I can access. They sit on the same
`User` node as profile fields, in a typed map (see §7
`SettingDescriptor`). Inventory for v1 + planned slots:

| Key | Type | Default | Slice | Notes |
|---|---|---|---|---|
| `theme` | enum `light` / `dark` / `system` | `system` | U1d | UI only; no backend wiring beyond storage. |
| `language` | enum `en` / `de` (extend later) | from `Accept-Language` then `en` | U1d | UI translation key. Aligns with the multi-lang work in `aidocs/14`. |
| `timeZone` | IANA TZ string (e.g. `Europe/Berlin`) | from browser at first login | U1d | All timestamp render uses this. Stored as UTC; converted on display. |
| `dateFormat` | enum `iso` / `locale` | `iso` (research-data audience) | U1d | `iso` = `2026-05-08T14:32Z`; `locale` = `8 May 2026, 14:32`. |
| `defaultPageSize` | int (25 / 50 / 100) | 25 | U1d | Applied to paginated lists (collections, search, journal). |
| `defaultLandingPage` | enum `collections` / `search` / `dashboard` | `collections` | U1d | What `/` redirects to after login. |
| `notifications.inApp` | bool | `false` | **deferred** | Today's subscriptions are URL-webhooks (`SubscriptionFilter`); in-app needs a server-push channel. Placeholder slot. |
| `git.pat` | encrypted-at-rest string | — | **U2-coupled** | Per-user Git Personal Access Token for the upcoming Git-artifact-tracking feature (`aidocs/38`, design pending). **Stored encrypted with the shepard server's master key**, never returned in `GET /users/me` (only `gitPatPresent: bool`). Re-enter to update. See **§3.3** for the security shape. |
| `git.host` | string (e.g. `gitlab.dlr.de`, `github.com`) | — | **U2-coupled** | Companion to `git.pat`. Defines which git host the PAT authenticates against. Multi-host support deferred to U2 vNext. |
| `editor.preferredJupyter` | URL | — | **U3-coupled** | Optional URL of the user's preferred JupyterHub (e.g. for the "Open in Jupyter" link from `aidocs/37`'s lab-journal-with-Jupyter feature). |
| `ai.apiKey` | encrypted-at-rest string | — | **U2-coupled** | Per-user OpenAI-compatible API key (`aidocs/43 §4.1`). Powers all AI features (`aidocs/43 §3 / §5`) — when unset and no admin fallback is configured, AI controls stay hidden in the UI. Same secret-class pattern as `git.pat`; never returned in `GET /users/me` (only `aiApiKeyPresent: bool`). |
| `ai.baseUrl` | URL | — | **U2-coupled** | OpenAI-compatible endpoint. Examples: `https://api.openai.com/v1`, `https://api.anthropic.com/v1` (via the AI1p adapter), `https://openrouter.ai/api/v1`, `http://localhost:11434/v1` (Ollama), `http://vllm:8000/v1` (vLLM). |
| `ai.model` | string | — | **U2-coupled** | Model id at the chosen provider — `gpt-4o-mini`, `claude-sonnet-4-5`, `llama3.1:8b`, etc. The dashboard chat (`aidocs/43 §5.8`) shows the active model and lets the user switch mid-conversation. |

**Why a typed map and not flat fields.** The list above is already
seven entries; the next year will probably add three or four more.
A flat schema means a Neo4j schema change for each — and a
`UserIO` regen, and a tracker row, and a frontend regen.
Storing as `Map<String, String>` (with the type encoded in
`SettingDescriptor`) means new preferences are an enum-row +
validator + UI input, no migration. Trade-off: lose typed Java
fields. Worth it: this is a true open-set surface, not a closed
schema.

`SettingDescriptor` becomes load-bearing the moment U1d ships and
should be implemented at U1c (frontend split) — not deferred to "the
second per-user setting" as §7 currently says. **§7 is updated to
reflect this** (see below).

### 3.3 Secret-class preferences (Git PAT pattern)

`git.pat` is the first preference that's a **secret**. The pattern
established here applies to any future secret preference:

- **Stored encrypted at rest** — a server-side AES-GCM key per
  shepard instance (operator-provisioned at first start; if absent,
  shepard refuses to accept secret-class settings rather than store
  them in cleartext). Key file path:
  `~/.shepard/keys/secrets.key` (sibling to the existing PKI keys
  in `aidocs/07` M2; same `0600` discipline post-our-recent fix).
- **Never returned in API responses** — `GET /users/me` includes
  `git.patPresent: bool`, never the value itself. To rotate, the
  client `PUT`s a new value; to clear, `DELETE`.
- **Audited** — every set/clear emits a structured log line at
  `info` (no value, just `user=<appId>, key=<keyName>, action=set|clear`).
- **No echo on the UI** — the input field is `<input type="password">`;
  past values are not retained client-side after submit.
- **Operator escape hatch** — `shepard-admin user purge-secrets <appId>`
  (per `aidocs/22 §4.x`) wipes a user's secret bag without touching
  their other settings. For when the encryption key rotates and an
  operator wants a clean slate.

This pattern lets us ship `git.pat` confidently at U2 without a
larger encryption-of-everything project. **Out of scope:** envelope
encryption with KMS, per-user keys, key rotation. v1 = single server
key, manual rotation if needed.

`ai.apiKey` (added 2026-05-08, per `aidocs/43`) is the **second**
secret-class setting. Same pattern; lights up AI features when set,
falls back to operator-configured `shepard.ai.fallback.*` if not,
hides AI features entirely if neither.

## 4. ORCID specifics

ORCID iDs are 16-digit identifiers in the form
`0000-0002-1825-0097` (Goonan's iD, used in their docs as a known-good
example), where the last digit is a mod 11-2 checksum and may be `X`
when the value is 10. Two implementation choices:

### 4.1 Validation: format + checksum, not a network round-trip

shepard validates the format (`^\d{4}-\d{4}-\d{4}-\d{3}[\dX]$`)
**and** the checksum locally. The checksum implementation is the
canonical
[mod 11-2 algorithm](https://support.orcid.org/hc/en-us/articles/360006897674)
— ~10 lines of Java. **No call to the ORCID API at write-time** —
that would couple shepard to ORCID's uptime and adds nothing for the
common case. A future "verify ORCID" badge (§4.3) is opt-in.

A unit test fixture covers: known-good iDs (Goonan, Tim Berners-Lee,
shepard team), known-bad checksums, malformed strings, and the `X`
edge case.

### 4.2 Storage: just a string on `User`

```java
@Property("orcid")
private String orcid;
```

Indexed for lookup (per #29's "useful for data exports for
publication" use case — RO-Crate exports cite the orcid). Unique
constraint **not** enforced — if two shepard accounts genuinely
belong to the same researcher (rare, but possible across institutions),
shepard isn't the place to detect it.

### 4.3 Verified vs claimed (deferred)

ORCID supports OAuth: a user can prove ownership by completing an
ORCID OAuth flow that returns the iD signed by ORCID. Adds a
`verifiedAt: Instant` and a UI badge.

**Out of scope for v1.** Mention only because the data shape needs
to anticipate it: the field is a `String` today; verification adds
a sibling `verifiedAt: Instant` later, no migration of the iD itself.

### 4.4 Where ORCID gets used downstream

- **RO-Crate exports** (`aidocs/31 §metadata`) — author entries gain
  `@id: https://orcid.org/<orcid>` when the iD is set, falling back
  to `mailto:<email>` then to `_:user-<appId>`. **This is the
  motivation for #29.** No code change in the exporter beyond the
  author-projection function picking the new field up.
- **Lab journal / annotation byline** — the same fallback chain.
- **Audit trail "Created by …"** — display-only, no orcid render
  (the display-name resolution from §3 is what fixes #694; orcid
  doesn't belong inline).

## 5. UI reassessment — split, not merge

The user's question is whether to merge the Configuration area with
the new profile UI. **Recommendation: split into two top-level
destinations** rather than merge:

| New top-level route | Replaces today's | Audience | Panes |
|---|---|---|---|
| **`/me`** "My Account" | `#profile`, `#apikeys`, `#subscriptions` from `/configuration` | every user | Profile (with ORCID), My API Keys, My Subscriptions, UI Preferences (future) |
| **`/admin`** "System" | The admin slice from `/configuration`'s side menu | admin only | User Groups, Semantic Repositories, Feature Toggles (future, post-A3b), Stats (future, post-`aidocs/22 §4.9`) |

Why split, not merge:

- **Audience.** "Configuration" today is a tab cocktail mixing
  personal and admin. Every user sees the admin tabs but most can't
  use them — that's the friction noted in `aidocs/33 §W12`.
- **Permissions hint.** `/admin` being a separate destination makes
  the "you need admin" boundary visible from the global nav, not
  from a per-pane 403.
- **Cognitive load.** The Profile + API Keys + Subscriptions trio
  fits on one page; adding `displayName`/`orcid` to that page is
  cheaper than adding a row to a 5-pane combined page.
- **Mobile / responsive.** A future mobile-friendly admin pass is
  easier when the per-user view is a single-column profile, not an
  admin-controls tab strip with a profile pane wedged in.

The **transition** is cheap because today's panes already exist as
Vue components — the split is a router change plus a left-rail
restructure, not new components. `aidocs/33` already lists
`UserProfilePane.vue` (or equivalent) as one of the W12 panes.

### 5.1 The new `/me` page (v1)

Single Vue page with stacked sections:

```
/me
├─ Profile
│   ├─ Identity (read-only: firstName, lastName, email — sourced from OIDC)
│   ├─ Display name (editable; falls back chain)
│   └─ ORCID iD (editable; validates on blur)
├─ My API Keys                  [existing pane, unchanged]
├─ My Subscriptions             [existing pane, unchanged]
└─ Preferences                  [v2; placeholder for theme / page-size / TZ]
```

Save model: per-section save buttons, RFC 7396 merge-patch on
`PATCH /users/me` (single endpoint, all profile fields). Unsaved
changes prompt on navigation away (already standard in `aidocs/33
§W2-W3-W5` editing patterns).

### 5.2 The new `/admin` page

Defers to `aidocs/22 §4.x` for any CLI-mirroring concerns, and to
the existing User Groups / Semantic Repos panes for content. **Not in
this design's scope.** Mentioned only so the split is intelligible.

## 6. API design

One new resource group plus a partial extension of `User`:

| Method + path | Purpose | Body shape | Notes |
|---|---|---|---|
| `GET /users/me` | Read my profile | n/a | Returns the full `UserIO` (existing) plus the new `displayName`, `orcid` fields |
| `PATCH /users/me` | Update my profile | `application/merge-patch+json` per **P21x** | Whitelisted updatable fields: `displayName`, `orcid`. Everything else in the body is silently ignored (RFC 7396 semantics — but server validates the whitelist; sending `firstName` returns 400 with a per-field `unmodifiable` reason) |
| `GET /users/{userId}` | Read another user's profile | n/a | Returns `UserPublicIO` — a stripped projection: `username` (or `displayName` if set), `orcid` if set, **never** `email`. Used by author-render and search-author-facet. |

Why `/users/me` and not `/users/{username}`: the OIDC subject is the
canonical key but it's exactly the cryptic UUID form we want to stop
exposing (#628). Routing through `/me` keeps the URL stable across
rename/migrate scenarios and avoids URL-encoding the colon-laden
Keycloak subject form. **Post-L2c** the public endpoint becomes
`/users/{appId}` (UUID v7 string); pre-L2c, lookups by username are
fine since they're rare.

`PATCH` semantics use the merge-patch content type (`P21x`,
`Constants.APPLICATION_MERGE_PATCH_JSON`) consistent with other
PATCH endpoints. No need to invent a JSON Patch shape for a 2-field
surface.

## 7. Settings extension pattern (how the next field lands cleanly)

`SettingDescriptor` is the small unifying abstraction. **Land it at
U1c** (along with the frontend split) so the preferences inventory
in §3.2 has a stable home from day 1 — not deferred to "the second
setting" as an earlier draft of this doc suggested.

Shape:

```java
public enum SettingDescriptor {
  THEME("theme", String.class, "system", new EnumValidator("light","dark","system"), Visibility.PUBLIC),
  LANGUAGE("language", String.class, null, new EnumValidator("en","de"), Visibility.PUBLIC),
  TIME_ZONE("timeZone", String.class, null, new IanaTimeZoneValidator(), Visibility.PUBLIC),
  DEFAULT_PAGE_SIZE("defaultPageSize", Integer.class, 25, new IntInValidator(25,50,100), Visibility.PUBLIC),
  // ... plus the secret-class entries below
  GIT_PAT("git.pat", String.class, null, new GitPatValidator(), Visibility.SECRET),
  GIT_HOST("git.host", String.class, null, new HostnameValidator(), Visibility.PUBLIC),
  ;
  // fields, getters
}
```

`Visibility.SECRET` triggers the §3.3 handling (encrypted-at-rest,
`*Present: bool` projection, audit log, no echo). Other settings
flow through plain JSON in `UserIO`.

Adding a new preference is now: enum row + validator (5 lines if
existing), UI input, optional row in `aidocs/34` if it materially
changes behaviour. **No Neo4j migration**, **no `UserIO` regen**,
**no allowlist edit**. Backend / frontend stay decoupled.

## 8. Backend changes

### 8.1 Entity

Add to `User.java`:

```java
@Property("displayName")
private String displayName;        // nullable

@Property("orcid")
private String orcid;              // nullable; validated format on save
```

Both Lombok-generated getters/setters via `@Data`. EqualsContract:
include in equals (these are state — diverges from `appId` which is
deliberately excluded per L2a's commit).

### 8.2 IO types

`UserIO` (the existing private/me projection): expose
`displayName`, `orcid`, plus a derived read-only `effectiveDisplayName`
(`displayName ?? "${firstName} ${lastName}".trim() ??
redactUsername(username)`).

`UserPublicIO` (new, for `/users/{userId}`): `username` (or its
redacted form if it matches the cryptic Keycloak shape from #628),
`displayName` (preferred for render), `orcid`, `appId`. **No
`email`** — protect from scraping; a logged-in user who needs an
email can find it through the IdP, not through shepard.

### 8.3 Resource

New `UserMeResource` mounting `/users/me` (`GET`, `PATCH`).
`UserResource` gains a `GET /users/{userId}` projection method.
Both methods reuse `UserService` + a small new
`UserProfileUpdateService` that does the orcid validation, the
allowlist check, and emits the merge-patch.

### 8.4 Validation

A `OrcidValidator` (in `auth/users/util/`) implementing the mod 11-2
algorithm. ~30 lines. Unit-tested against the format-only regex,
checksum-positive, checksum-negative, and the `X` edge.

### 8.5 Migration

**No data-mutating migration needed** — both new fields are
nullable, default-null, no schema constraint. The L2a `appId` unique
constraint already covers `User`.

**Tracker row** in `aidocs/34-upstream-upgrade-path.md` will be
added in the implementing PR — status `ZERO` (additive nullable
fields, no operator action, no client breakage; existing clients
will simply not send the new fields).

## 9. Phasing

| ID | Slice | Size | Gate |
|---|---|---|---|
| **U1a** | `User.orcid` field + `OrcidValidator` + `PATCH /users/me` (merge-patch, orcid only) + `UserPublicIO` projection | S | None — close #29 in this slice |
| **U1b** | `User.displayName` + extend `PATCH /users/me` allowlist + `effectiveDisplayName` derivation + audit-trail render switch ("Created by …" uses display name) | S | Closes #694; touches #628 by exposing the redacted-username path |
| **U1c** | Frontend split: introduce `/me` route, move Profile/API Keys/Subscriptions panes, add ORCID + displayName editors with inline validation. Renames `/configuration` → `/admin` for the residual admin surface (User Groups, Semantic Repos) | M | Frontend; depends on U1a + U1b |
| **U1d** | Preferences pane in `/me`: `theme`, `language`, `timeZone`, `dateFormat`, `defaultPageSize`, `defaultLandingPage`. Backend ships `SettingDescriptor` enum + `Map<String,String>` storage on `User` + `GET/PATCH /users/me/preferences`. | M | U1c |
| **U1e** | Avatar: shepard-uploaded path (`PUT/DELETE /users/me/avatar`), Mongo `userAvatars` collection, public render at `/users/{appId}/avatar`. Gravatar + IdP precedence already wired by U1c. | S | U1c |
| **U2-coupled** | Secret-class settings (encrypted-at-rest pattern from §3.3) + `git.pat` + `git.host` settings. Lands as part of the Git-integration umbrella (`aidocs/38`, design pending) — these settings have no use without that feature. | S | U1d + the Git-integration design |
| **U1f** | (deferred) Verified-ORCID via OAuth flow, `verifiedAt`, UI badge | M | None |
| **U3-coupled** | `editor.preferredJupyter` — added with the lab-journal-Jupyter feature (`aidocs/37`, design pending). | XS | aidocs/37 implementation |

Recommended order: U1a → U1b → U1c. Three weeks total at one
backend + one frontend hand. Closes **#29** at U1a, **#694** at U1b,
makes meaningful progress on **#628** at U1b (no fix on the OIDC
side, but the cryptic form stops being the user-visible default).

## 10. Open decisions (deferred; do not block U1a)

1. **Display-name fallback for #628's cryptic form.** Should an
   un-set `displayName` for a Keycloak `f:UUID:username`-shaped
   subject render as `username` (the trailing segment) or as a fully
   redacted `<unnamed user>`? Currently `aidocs/33` doesn't say.
   Recommend the trailing-segment fallback at U1b — it preserves
   identity recognition for AD-backed deployments without exposing
   the raw subject.
2. **ORCID indexing.** Index the field in Neo4j? **Yes** — the
   "find by ORCID" query is fast and the cardinality is one-per-user.
   Cypher index `CREATE INDEX user_orcid FOR (u:User) ON (u.orcid)`,
   shipped in `V14__Add_user_orcid_index.cypher` with U1a.
3. **Should `/users/me` PATCH support unsetting orcid?** RFC 7396
   says `null` removes a field. Honour that — a user who entered a
   wrong iD must be able to clear it.

## 11. Cross-references

- **Issues:** #29 (ORCID, this design's anchor); #694 (display name);
  #628 (cryptic Keycloak usernames); #483 (user groups admin —
  out of scope but the `/admin` split makes its UI cleaner);
  #629 (display JWT after API-key creation — same pane).
- **aidocs:**
  - `aidocs/33 §W12` — current Configuration UI shape.
  - `aidocs/22` — admin CLI; `/admin` REST surface intersects with
    the future `shepard-admin features set` and `apikey list/revoke`
    commands. Same backend endpoints serve both surfaces.
  - `aidocs/24` — permission system review; defaults-for-new-entities
    is the next axis of profile expansion and lives there.
  - `aidocs/25` — L2c gates the public `/users/{appId}` form.
  - `aidocs/31` — RO-Crate exports consume the new `orcid` field
    automatically once present.
  - `aidocs/34` — upgrade-path tracker; U1a/U1b each get a `ZERO`
    row.
- **Backlog:** new **U1** umbrella + sub-IDs land in
  `aidocs/16-dispatcher-backlog.md` in the same PR as this design.
