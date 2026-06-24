---
layout: default
title: Organise work with Projects
permalink: /help/organise-with-projects/
audience: basic
---

# Organise work with Projects

A **Project** is a Collection that acts as an umbrella for related
sub-Collections. Use it when a research effort spans multiple Collections and
you want a single landing page for the whole campaign — one place to see all
sub-teams' data, aggregate counts, and programme labels at a glance.

**Examples:** an AFP manufacturing campaign with five process-step Collections;
a rocket-engine test programme with quarterly Collections; a satellite mission
bundling Integration & Test, Launch, and Nominal-Operations Collections.

## Find existing Projects

Click **Projects** in the top navigation bar (between Collections and
Containers). The page lists every Project on the instance with its programme
labels, sub-Collection count, and last-activity timestamp.

Use the **Programme** side-filter to narrow the list when many Projects share
a funder or initiative name.

## Create a Project

A Project is a regular Collection that has been given one semantic annotation.
There is no separate "Create Project" wizard — you promote an existing
Collection.

1. Open the Collection you want to turn into a Project (or create a new one
   from the Collections page).
2. On the Collection detail page, click the **Annotations** panel → **Add
   annotation**.
3. In the annotation dialog, set:
   - **Predicate IRI:** `urn:shepard:project`
   - **Value:** `true`
4. Click **Save**.

The Collection now appears in the **Projects** top-nav list and its detail
page gains a **Sub-Collections** panel.

> If you see a 422 error, check that the value is exactly `true` (lowercase,
> no quotes when typed in the dialog).

## Add sub-Collections

Tell a child Collection it belongs to your Project by adding a `partOf`
annotation on the child:

1. Open the **child** Collection (the process step, test campaign quarter,
   mission phase, etc.).
2. Click **Annotations** → **Add annotation**.
3. Set:
   - **Predicate IRI:** `urn:shepard:partOf`
   - **Value:** the Project Collection's `appId` (copy it from the Project's
     detail-page header or URL)
4. Click **Save**.

Repeat for each sub-Collection. A Collection can belong to **more than one
Project** — just add one `urn:shepard:partOf` annotation per parent.

## Add programme / funder labels

Annotate the **Project** Collection (not a sub-Collection) with:

- **Predicate IRI:** `urn:shepard:programme`
- **Value:** funder or initiative name, e.g. `Clean Aviation JU` or
  `DLR Project Line 4`

Add one annotation per programme when a Project falls under multiple
initiatives. The values appear as chips on the Projects list page and on the
Project detail header.

## Browse the Project

On the Project Collection's detail page, the **Sub-Collections** panel shows:

- Programme chips at the top
- One tile per sub-Collection with its name, DataObject count, owning user
  group, and last-activity timestamp
- "Also in N Projects" chips on sub-Collections that belong to multiple Projects

Click any tile to open that sub-Collection's detail page.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Project doesn't appear in the Projects list | The `urn:shepard:project` annotation is missing or the value is not `true` | Open the Collection → Annotations → confirm the annotation exists |
| Sub-Collections panel is empty | No child Collection has a `urn:shepard:partOf` annotation pointing to this Project | Add `partOf` on each child |
| 422 when adding `partOf` | The target is not yet a Project | Mark the parent as a Project first (`urn:shepard:project = true`) |
| 422 when adding `programme` | You added it to a non-Project Collection | Only Project Collections may carry `urn:shepard:programme` |

## Related help

- [Browse a collection](browse-collections.md)
- [Annotate your data](annotating-data.md)
- [Link datasets with relationships](link-datasets.md)
- **Reference:** [Projects](/reference/projects/)
