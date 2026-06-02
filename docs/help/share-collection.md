---
layout: default
title: Share a collection with collaborators
permalink: /help/share-collection/
audience: user
---
# Share a collection with collaborators

Every collection in shepard has its own access-control list. You set who
can see, edit, or manage the collection — by individual user or by group.

---

## Permission levels

| Role | Can do |
|---|---|
| **Reader** | View the collection and all its data objects, containers, and annotations. |
| **Writer** | Everything a Reader can do, plus create and edit data objects, upload data, and add annotations. |
| **Manager** | Everything a Writer can do, plus add and remove other users' permissions. Managers cannot transfer ownership. |
| **Owner** | Full control, including transferring ownership. There is always exactly one owner. |

Groups can be assigned Reader or Writer — not Manager.

---

## Open the Permissions dialog

1. Navigate to the collection you want to share.
2. In the left sidebar, hover over the collection name — a three-dot context
   menu appears to the right of the name.
3. Click the three-dot menu and choose **Permissions**.

You need **Manager** or **Owner** role on the collection to see this option.

---

## Set the general visibility

At the top of the Permissions dialog, the **Permission Type** dropdown controls
who can access the collection without being named explicitly:

- **Private** — only users and groups listed below can access it (default).
- **Public Readable** — anyone on this shepard instance can view it, even
  without a named permission entry.
- **Public** — anyone can view and write.

For most research collaborations, leave this on **Private** and add named
collaborators below.

---

## Add a collaborator

1. In the **Additional Permissions** section, type a username or group name
   into the **User or group id** field and select the person or group from
   the dropdown.
2. Choose a role (**Reader**, **Writer**, or **Manager**) from the
   **User or group role** dropdown.
3. Click **ADD**.

The new entry appears in the **List of Additional Permissions** table.
Assigning **Manager** automatically grants **Writer** and **Reader** as well;
assigning **Writer** automatically grants **Reader**.

Repeat for each additional collaborator.

---

## Remove a collaborator

In the **List of Additional Permissions** table, click the delete icon on the
row you want to remove.

---

## Save

Click **Save** at the bottom of the dialog. The change takes effect immediately
— collaborators do not need to refresh their session.

---

## Transfer ownership

Ownership transfer is in the **Owner** field at the top of the dialog. Type
the new owner's username and select them from the autocomplete. Only the
current owner can transfer ownership.

---

## See also

- [Annotating data](/help/annotating-data/) — annotations inherit the
  collection's read/write permission.
- [Publish a DataObject or Collection](/help/publish-data-object/) — making
  a collection publicly citable via a PID is separate from sharing it inside
  shepard.
