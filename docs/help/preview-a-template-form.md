---
title: Preview a template form
description: Use the Form preview tool to inspect a template's fields and download a pre-populated Excel recording sheet.
permalink: /help/preview-a-template-form/
layout: default
audience: user
---

# Preview a template form

The **Form preview** tool shows you what a data-kind template's form looks like —
which fields are required, what type they are, and what values they accept —
before you start recording a DataObject. You can also download a pre-populated
Excel sheet from any template.

## When to use this

- You want to know what fields a template like `mffd:afp-course` requires before
  you create a DataObject with it.
- A colleague sent you a template name and you want to check what it collects.
- You are building a new template and want to see the compiled form before assigning it
  to a DataObject.

## Getting there

**From a DataObject (recommended — zero typing):**
On any DataObject detail page, open the **⋮ Actions** menu and choose
**Record a …**. The Form preview page opens with the template and DataObject
already filled in — go straight to reading the fields.

**From the Tools menu:**
1. Click **Tools** in the top navigation bar.
2. Choose **Form preview** from the list.

## Reading the form

Once you select a template, the page shows the form descriptor compiled from the
template's SHACL shape. Fields are organised into groups (sections):

| Column | What it means |
|--------|--------------|
| **Field name** | The predicate label (e.g. *Material batch*, *Ply count*). |
| **Type** | The expected value type (text, number, date, URI, …). |
| **Required** | Whether the field must be filled before the DataObject can be published. |
| **Hint** | The editor hint for this field (text area, numeric range, dropdown, date picker, …). |

A field marked **Required** will cause a validation error (422) if it is missing when
you call the template's instantiation endpoint.

## Picking a template

Use the **Template** autocomplete field: start typing the template name and choose from
the suggestions. The dropdown shows all templates you have access to.

If you know the template's appId (UUID v7), you can also paste it directly into the
**Raw appId override** field below the autocomplete.

## Downloading an Excel recording sheet

When you have both a template and a DataObject selected, the **Download Excel** button
generates a pre-populated spreadsheet:

1. Select the template.
2. Paste the DataObject's appId into the **DataObject appId** field.
3. Click **Download Excel**.

The spreadsheet has one column per form field, with the field label as the header and
any allowed-values constraint pre-filled as a dropdown. Useful for batch-entering
measurements offline before importing.

## Tips

- **No data is recorded here.** Form preview is read-only — it shows the form descriptor
  but does not save anything to a DataObject. To record data, use the **Record a …**
  action on the DataObject itself.
- **Template not in the list?** You may not have read access to it, or the template
  belongs to a Collection you cannot see. Ask your instance admin.
- **422 on submission?** Check the required fields in the form preview — the violation
  message keys on the field's predicate path (e.g.
  `urn:shepard:mffd:material-batch`).

## See also

- [Build a template](./build-a-template.md) — create your own data-kind template
- [Create from template](./create-from-template.md) — record a DataObject using a template
- [Validate against a shape](./validate-against-a-shape.md) — check an existing DataObject against a template's constraints
- [Form preview — reference](../reference/form-preview.md) — descriptor format, endpoint spec, Excel export API
