---
layout: default
title: Share a collection with collaborators
description: How to add teammates as readers, writers, or managers on a collection and control its visibility
permalink: /help/share-collection/
audience: user
stage: deployed
---
# Share a collection with collaborators

By default a new collection is **Private** — only you (the owner) can
see or edit it. This page shows how to grant access to colleagues and
how to change the collection's overall visibility.

## Roles at a glance

| Role | Can read | Can write | Can manage permissions |
|---|---|---|---|
| **Reader** | Yes | No | No |
| **Writer** | Yes | Yes | No |
| **Manager** | Yes | Yes | Yes |
| **Owner** | Yes | Yes | Yes (+ delete) |

A **Manager** can add and remove other users (except the owner). An
owner cannot be changed through the permissions panel — contact your
instance admin if you need to transfer ownership.

## Step 1: open the Permissions panel

1. Open the collection.
2. Click **Settings** in the collection action bar (or the gear icon
   at the top right of the collection detail page).
3. Select **Members / Permissions**.

The panel shows the current owner, and the reader, writer, and manager
lists.

## Step 2: add a collaborator

1. In the **Readers**, **Writers**, or **Managers** field, type the
   colleague's username and press Enter (or pick from the autocomplete).
2. Repeat for each person.
3. Click **Save**.

The collaborator immediately sees the collection in their collections
list without needing to do anything on their end.

To remove a collaborator, click the `×` next to their name and save.

## Step 3: add a user group (optional)

If your instance has user groups configured, you can grant access to
a whole group at once:

1. Switch to the **Groups** tab in the permissions panel.
2. Type the group name and pick **Reader group** or **Writer group**.
3. Click **Save**.

Groups are managed by your instance admin at `/shepard/api/usergroups`.

## Visibility modes

Each collection has a visibility setting in addition to the individual
access lists:

| Mode | Who can read |
|---|---|
| **Private** | Only the owner, managers, writers, and readers you listed. |
| **PublicReadable** | Anyone logged in to this shepard instance. No need to list them individually. |
| **Public** | Broader read; governed by your instance's public-access policy. |

To change the visibility:

1. In the **Members / Permissions** panel, find the **Visibility** selector.
2. Pick `Private`, `PublicReadable`, or `Public`.
3. Click **Save**.

`PublicReadable` is useful for a campaign you want all institute
members to browse without having to invite each person. Only the
users and groups you explicitly listed as writers or managers can
make changes.

## REST equivalent

For scripted onboarding (adding a whole team at once), send a single
`PUT` to the permissions endpoint:

```
PUT /shepard/api/collections/{collectionId}/permissions
Content-Type: application/json

{
  "permissionType": "Private",
  "reader": ["alice", "bob"],
  "writer": ["carol"],
  "manager": [],
  "readerGroupIds": [],
  "writerGroupIds": []
}
```

`GET /shepard/api/collections/{collectionId}/permissions` returns the
current state so you can diff it before updating.

To find the numeric `collectionId` from an `appId` (UUID v7), use
`GET /v2/collections/{appId}` — the response carries both `id` and
`appId`.

## What if you cannot see the Permissions panel?

You need **Manager** or **Owner** role to edit permissions. If you only
have Writer or Reader access you can see the collection but not modify
its access list. Ask the collection owner or a Manager to add you as
a Manager, or to invite your collaborator directly.

## Further reading

- [User guide: Permissions section](/user-guide/#permissions) — full
  permission model with all role definitions.
- [Trace dataset provenance](/help/provenance-tracing/) — see who
  performed actions on a collection, including permission changes.
