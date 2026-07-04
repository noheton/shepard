---
audience: user
---

# Build a template (visual shape editor)

A **template** in shepard is a reusable blueprint for a data object — and, under
the hood, a SHACL *shape* that says which metadata a data object of that kind
should carry. The **visual template editor** lets you compose that shape by
picking semantic predicates from a palette, without writing any Turtle or JSON
by hand.

> You need the **instance-admin** role to create or edit templates. If you don't
> see the Templates pane, ask your administrator.

## Open the editor

1. Go to **Admin** (the gear / admin entry in the top navigation).
2. Open the **Templates** pane.
3. Click **New template** (or the pencil on an existing template to edit it).
4. In the dialog, leave the **Body** mode toggle on **Visual** (the default).

## Compose a shape

The editor has four areas:

1. **Predicate palette** (left). Search by name or IRI — it merges your
   instance's curated vocabulary (`/v2/shapes/predicates`) with an ontology
   term search. Click a predicate to add it as a property row. The **Add blank
   property** button adds an empty row you can fill in by hand.
2. **Compose shape** (right). Each property row is one constraint:
   - **Predicate IRI** — what the metadata field *is* (e.g. a "status" or
     "propellant" predicate). Filled in for you when you pick from the palette.
   - **Datatype** — the value type (string, integer, dateTime, …), or "(none)"
     for a value that points at another entity.
   - **minCount / maxCount** — how many values are required / allowed. Set
     minCount to 1 to make the field mandatory.
   - **Allowed values (sh:in)** — an optional pick-list. Add literal values
     (e.g. `DRAFT`, `READY`) or term IRIs.
   - **Nested node shape** — for a value that must itself satisfy another shape.

   Above the rows you can set the shape's own IRI, its **target class**, and a
   **Closed shape** switch (reject any metadata field you didn't declare).

3. **Live SHACL preview** (bottom-left). As you edit, the editor compiles your
   rows to canonical SHACL Turtle and shows it. This is exactly what gets stored
   on the template — you never type Turtle yourself.

4. **Round-trip validate** (bottom-right). Paste a small sample data graph and
   click **Validate against this shape** to confirm the shape accepts good data
   and rejects bad data before you save.

## Inherit from a parent template

Use the **Extends (parent template)** picker to base your template on another
one. Your template inherits the parent's fields; your own rows override on
collision. The inherited fields appear read-only above the body so you can see
what you're building on. Only same-kind, non-cyclic templates are offered.

## Save

Click **Create** (or **Save (new version)** when editing — edits are
copy-on-write, so older versions stay valid for anything that already used
them). The saved template immediately:

- appears in the template picker when you create a data object,
- drives the create form (the fields you declared become the form),
- validates new instances against the shape (a violation blocks creation with a
  clear message).

## Prefer hand-editing?

Flip the **Raw JSON** toggle to edit the template body's JSON DSL directly. The
visual editor and the raw view share the same body; switching back to Visual
reopens whatever the editor last produced.

## See also

- [Create a data object or collection from a template](./create-from-template.md)
- [Template editor (reference)](../reference/template-editor.md)
