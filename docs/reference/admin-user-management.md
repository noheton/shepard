---
stage: deployed
last-stage-change: 2026-07-18
audience: admin
layout: default
title: Admin user management
description: Operator reference for the admin-only user endpoints — set a user's ORCID and mint mirrored (shadow) users from peer instances
permalink: /reference/admin-user-management/
---

> 🤖 **BACKFILL — created retroactively 2026-07-18 by Claude Opus 4.8**
> per DOCS-3A6/3A7 (`aidocs/16-dispatcher-backlog.md`). Documented from the
> source (`AdminUserOrcidRest.java`, `MirroredUserRest.java`) as it stands at
> the backfill date.

<!-- backfill: DOCS-3A6/7-sweep2 2026-07-18 -->

# Admin user management

Two narrow, admin-only user surfaces that sit outside the normal self-service
profile flow. Both require the `instance-admin` role.

For everything a user manages about *their own* account (ORCID self-set, git
credentials, display name), see [User profile](user-profile.md) — this page is
only the **admin-acting-on-another-user** surface, plus the cross-instance
mirror.

| Surface | Route | Purpose |
|---|---|---|
| [Set a user's ORCID](#set-a-users-orcid) | `PATCH /v2/admin/users/{username}/orcid` | Preseed or clear another user's ORCID. |
| [Mirror a user](#mirror-a-user-from-a-peer-instance) | `POST /v2/admin/users/mirror` | Mint a shadow user for a peer-instance identity. |
| Manage a user's git credentials | `…/git-credentials` | See [Git references](git-references.md#admin-credential-endpoints). |

---

## Set a user's ORCID

`PATCH /v2/admin/users/{username}/orcid`

A one-shot admin preseed for demo and migration scenarios — for example,
stamping ORCIDs onto imported accounts that were created before the user could
log in and set their own. This is the admin counterpart of the self-service
ORCID field on `/me`; the **Admin → User ORCID** tile drives it in the UI.

Body:

```json
{ "orcid": "0000-0002-1825-0097" }
```

- A non-null value **must be a valid ORCID** (checksum-validated). An invalid
  format returns `400`.
- Passing `"orcid": null` **clears** the user's ORCID.

| Code | Meaning |
|---|---|
| `204` | ORCID updated. |
| `400` | Invalid ORCID format. |
| `401` | Not authenticated. |
| `403` | Caller lacks `instance-admin`. |
| `404` | No user with that username. |

```bash
curl -s -X PATCH \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"orcid":"0000-0002-1825-0097"}' \
  https://shepard.example.org/v2/admin/users/jdoe/orcid
```

Once set, the ORCID renders as a badge next to the user throughout the UI (see
[User profile](user-profile.md)).

---

## Mirror a user from a peer instance

`POST /v2/admin/users/mirror`

Mints a **`:MirroredUser`** shadow node representing a user who lives on a
*different* Shepard instance. It's the identity half of the cross-instance story
that the [instance registry](instance-registry.md) started: when a peer
instance's data (or a federated provenance edge) references a user who is not
local, the mirror node gives that identity a stable local handle and a friendly
display name instead of a bare `sourceInstance/sourceUsername` pair.

The call is **idempotent** — keyed on `(sourceInstance, sourceUsername)`:

- **`201`** — new pair; a fresh mirror node is minted.
- **`200`** — pair already existed; the same `appId` is returned (fields
  refreshed).

Body:

| Field | Required | Notes |
|---|---|---|
| `sourceInstance` | ✅ | Base URL / instance id of the source Shepard. Pairs with an [instance-registry](instance-registry.md) entry for friendly rendering. |
| `sourceUsername` | ✅ | Username as known on the source instance. |
| `sourceDisplayName` | — | Human-readable name from the source side. |
| `sourceEmail` | — | Email from the source side. |

| Code | Meaning |
|---|---|
| `201` | Mirror node created (new pair). |
| `200` | Mirror node updated (pair existed). |
| `400` | `sourceInstance` or `sourceUsername` missing/blank. |
| `401` | Not authenticated. |
| `403` | Caller lacks `instance-admin`. |

```bash
curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "sourceInstance": "https://shepard.peer.example.org",
        "sourceUsername": "mschmidt",
        "sourceDisplayName": "M. Schmidt (DLR-SP)" }' \
  https://shepard.example.org/v2/admin/users/mirror
```

> This is primarily an **API surface** used by import/federation tooling; there
> is no dedicated admin tile for minting mirror nodes by hand. The registered
> mirror identities surface wherever cross-instance users are rendered.

---

## See also

- [User profile](user-profile.md) — the self-service account surface (ORCID, git
  credentials, display name).
- [Instance registry](instance-registry.md) — friendly names for the peer
  instances a mirrored user comes from.
- [Git references](git-references.md) — the admin-managed git-credential surface.
- [Role grants runbook](../admin/runbooks/14-role-grants.md) — granting the
  `instance-admin` role these endpoints require.
