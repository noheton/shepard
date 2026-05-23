---
title: User profile
weight: 65
audience: user
---
# User profile (`/v2/users/me`)

shepard's **user profile** surface lets each authenticated user manage
their own account fields without admin involvement. All endpoints in
this section live under `/v2/users/me` — part of the fork's
[development API shelf](api/) (`/v2/`), not the upstream-frozen
`/shepard/api/...` surface.

## Fields managed by the user

| Field | Endpoint | Slice |
|---|---|---|
| `orcid` | `PATCH /v2/users/me` | U1a |
| `displayName` | `PATCH /v2/users/me` | U1b |
| UI preferences (`theme`, `language`, …) | `GET/PATCH /v2/users/me/preferences` | **U1d** (this page) |

## `PATCH /v2/users/me` — update profile fields

RFC 7396 JSON Merge Patch. Fields present in the body replace the
corresponding fields; fields absent are preserved; explicit JSON `null`
clears the field.

### Patchable fields

| Key | Type | Validation | Description |
|---|---|---|---|
| `orcid` | `string` or `null` | ISO 7064 mod 11-2 checksum; `NNNN-NNNN-NNNN-NNN[N\|X]` format. Empty string clears. | ORCID iD. Surfaces in RO-Crate exports as author identifier. |
| `displayName` | `string` or `null` | None (any string). Empty string clears. | User-chosen display name override. When set, this name appears in audit trails ("Created by …") and the page header instead of the raw username. When cleared, shepard derives a name from `firstName`+`lastName`, falling back to a redacted username. |

**Example — set ORCID and display name in one request:**

```http
PATCH /v2/users/me
Content-Type: application/json

{"orcid": "0000-0002-1825-0097", "displayName": "Dr. A. Researcher"}
```

**Responses:**

| Code | Meaning |
|---|---|
| 200 | Updated `UserIO` object. |
| 400 | Body is not a JSON object, ORCID failed checksum, or a field has the wrong type. |
| 401 | Authentication required. |

## `GET /v2/users/me/preferences` — read UI preferences

Returns the calling user's current preference map. Returns `{}` when
no preferences have been set yet.

```http
GET /v2/users/me/preferences
Accept: application/json
```

**Response 200:**

```json
{
  "theme": "dark",
  "language": "de",
  "timeZone": "Europe/Berlin",
  "dateFormat": "DD.MM.YYYY",
  "defaultPageSize": "25",
  "defaultLandingPage": "/collections"
}
```

**Responses:**

| Code | Meaning |
|---|---|
| 200 | Current preference map (may be `{}`). |
| 401 | Authentication required. |

## `PATCH /v2/users/me/preferences` — update UI preferences

RFC 7396 JSON Merge Patch over the preference map.

- Keys with **non-null string values** are inserted or updated.
- Keys with **explicit `null` values** are removed from the map.
- Keys **absent from the body** are left unchanged.

Returns the resulting map after the patch is applied.

**Example — switch theme to dark, remove a key:**

```http
PATCH /v2/users/me/preferences
Content-Type: application/json

{"theme": "dark", "defaultLandingPage": null}
```

**Response 200:**

```json
{
  "theme": "dark",
  "language": "de",
  "timeZone": "Europe/Berlin"
}
```

**Responses:**

| Code | Meaning |
|---|---|
| 200 | Updated preference map. |
| 400 | Body is not a JSON object, or a value is not a string or `null`. |
| 401 | Authentication required. |

## Known preference keys

The keys below are understood by the frontend. The server accepts
**any** string key (open-world) — this list is informational only.

| Key | Expected values | Default |
|---|---|---|
| `theme` | `"light"`, `"dark"` | system default |
| `language` | BCP 47 tag (`"en"`, `"de"`, …) | browser default |
| `timeZone` | IANA tz identifier (`"Europe/Berlin"`, `"UTC"`, …) | browser default |
| `dateFormat` | Format string (`"DD.MM.YYYY"`, `"YYYY-MM-DD"`, …) | locale default |
| `defaultPageSize` | Numeric string (`"10"`, `"25"`, `"50"`, …) | `"25"` |
| `defaultLandingPage` | Path string (`"/collections"`, `"/me"`, …) | `"/collections"` |

The U1c `SettingDescriptor` layer (queued) will add typed, validated
descriptors for each key. Until then, the server stores any string
key/value pair without validation.

## Storage details

Preferences are stored as a JSON blob in the `preferencesJson`
property on the `:User` Neo4j node. The property is additive and
nullable — existing users have `null` until they set at least one
preference. No migration is required.

## Cross-references

- `aidocs/16` U1d — dispatcher backlog entry.
- `aidocs/36 §3.2` — `SettingDescriptor` typed-map design (future U1c).
- `aidocs/36 §7` — full preference-key registry spec.
