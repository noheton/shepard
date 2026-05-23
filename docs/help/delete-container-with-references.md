---
title: Delete a container that has references
description: What happens when you try to delete a container that data objects still point at, and how to proceed safely
permalink: /help/delete-container-with-references/
layout: default
audience: user
---
# Delete a container that has references

When you delete a container in shepard, any data objects that still
reference it become **orphaned** — their reference rows survive but
can no longer fetch the data. shepard tells you before this happens
so you can decide.

## The flow

1. Open the container detail page (**Containers → Timeseries / Files /
   Structured Data**, then click the container name).
2. Scroll to the **Referenced by** panel. If it shows a count and a
   list of data objects, those are the references that will be left
   orphaned by a delete.
3. Click the red **trash** icon next to the container title.
4. If references exist, the confirm dialog shows a yellow warning:

   > **3 data objects reference this container. Deleting it now will
   > leave those references orphaned (the data they used to point at
   > will no longer be retrievable).**

5. To proceed, type the container's exact name into the input and
   click **Delete**. To back out, click **Cancel**.

The warning is informational — the delete succeeds either way after
you type the name. shepard doesn't auto-delete the orphaned
references, because they may carry semantic annotations or sit on
data objects with their own permissions that shouldn't be touched.

## What "orphaned" actually means

After the delete:

- The container is marked deleted (soft-delete; an admin can recover
  it from Neo4j if needed).
- Every reference that pointed at it now returns a 404-ish error
  when the frontend tries to load the payload. The reference row
  itself still exists; you can see it in the data object's
  **Data References** panel with an "unavailable" indicator.
- Semantic annotations on the reference or the data object are
  preserved.

## Cleaning up the orphans

There's no one-click "remove all orphaned references" action — that's
intentional. Manual cleanup:

1. Open each affected data object (the Referenced-by list told you
   which ones).
2. In the **Data References** panel, click the trash icon on the
   stale row.

For bulk cleanup across many data objects, an admin can run a Cypher
query against Neo4j (see [admin CLI reference](/reference/admin-cli/)).

## When to actually delete

Common scenarios:

- **You re-uploaded the data under a new container** (typical when
  fixing an upload mistake). Either repoint the existing references
  in each data object, or remove them and add fresh ones pointing
  at the new container.
- **The container was a one-off test and never had real consumers**
  — the Referenced-by panel will be empty, no warning shows.
- **You're tearing down a storage backend** (e.g. retiring a Garage
  S3 cluster). Don't delete the container until you've moved its
  payload to the new backend; see
  [file-storage migration](/reference/file-storage/).

## API equivalent

The frontend always passes `?force=true` because you already saw the
warning. External clients (admin scripts, notebooks) can get the
same safety:

```bash
# This will refuse if references exist (returns 409 with the count).
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  https://shepard.example.dlr.de/v2/timeseries-containers/42
```

See [container safe-delete (reference)](/reference/container-safe-delete/)
for the full wire shape.
