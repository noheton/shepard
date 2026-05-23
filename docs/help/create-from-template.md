---
audience: user
---

# Create a data object or collection from a template

Templates let you start with a predefined structure — pre-filled attributes, file slots, and a consistent name — instead of filling in everything from scratch.

## Creating a data object from a template

1. Open a collection in the left sidebar.
2. Click the **+** button (top-right of the sidebar) or the **Add new data object** button at the bottom.
3. The **Create Data Object** dialog opens with a template picker. Choose a template card to create the data object instantly.
4. If you prefer to start without a template, click **Start from blank** to open the standard creation form.

> **Note:** The template picker only shows templates that your collection admin has allowed for this collection. If no templates are configured, the blank form opens directly.

## Creating a collection from a template

1. From the Collections list page, click **Create collection**.
2. If your administrator has set up collection templates, the template picker appears first. Choose a template to pre-fill the collection description.
3. Complete the name and any other fields in the form, then click **Create**.
4. Click **Start from blank** to skip the template picker.

## What templates provide

- **Attributes**: pre-defined key–value pairs copied to the new data object.
- **Provenance**: a `[:CREATED_FROM_TEMPLATE]` edge recorded in the graph, linking the data object back to the template version used.
- **Consistency**: all data objects created from the same template share a common structure, making bulk queries and exports predictable.

## Setting up templates (admin)

See [Templates reference](../reference/templates.md) for how to create and publish templates.
