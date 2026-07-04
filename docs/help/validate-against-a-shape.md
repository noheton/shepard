---
title: Validate a DataObject against a shape
description: Use the SHACL playground to check whether your data meets a template's constraints.
---

# Validate a DataObject against a shape

The **SHACL Playground** lets you check a DataObject's annotations against a set of
rules (a SHACL shape) and get a plain-English list of what passes, what fails, and why.
You do not need to understand SHACL to use it — when you arrive from a DataObject or template
the fields fill in automatically.

## When to use this

- You filled in a DataObject using a template and want to confirm the required fields are present.
- A colleague asks "does TR-004 satisfy the `mffd:afp-course` shape before we hand it off?"
- You are authoring a new template and want to check the SHACL you wrote against an example object.

## Getting there

**From a DataObject detail page (recommended):**
Open the DataObject, then use the three-dot / Tools menu and choose **Validate against shape**.
The data graph pre-fills automatically from the DataObject's annotations.
If the DataObject has an attached template, the shape graph also pre-fills from the template's
constraints — you can go straight to clicking **Validate**.

**From the Tools menu:**
Navigate to **Tools → SHACL Playground** (top-nav Tools entry) to start a blank session.

## Running a validation

1. **Data graph** (left or top pane) — paste Turtle describing the entity you want to check.
   When arrived from a DataObject, this is pre-filled.
2. **Shape graph** (right or bottom pane) — paste the SHACL Turtle that declares the rules.
   When arrived via a template, this is pre-filled from the template body's `shapeGraph` field.
3. Click **Validate**.
4. The result panel shows either **Conforms** (all rules pass) or a list of violations:
   - **Severity** — Info / Warning / Violation
   - **Focus node** — which entity failed
   - **Constraint path** — which predicate or rule was violated
   - **Message** — human-readable explanation

## Understanding the results

| Result | Meaning |
|---|---|
| ✅ Conforms | Every SHACL constraint in the shape is satisfied. |
| ⚠️ Warning | A non-mandatory constraint is not met — data is usable but incomplete. |
| ❌ Violation | A mandatory constraint failed — data must be corrected before publishing. |

A violation report always shows the exact predicate path that caused the failure
(e.g. `urn:shepard:mffd:material-batch` missing on a DataObject of kind `mffd:afp-course`).

## Fixing failures and re-validating

1. Note the failing predicate path in the violation report.
2. Go back to the DataObject and **Add annotation** for the missing predicate.
3. Return to the playground and click **Validate** again — or navigate back via the
   DataObject's Tools menu, which will re-prefill and reset the result.

## Working without a template

You can paste any valid SHACL Turtle into the shape graph and any Turtle into the data graph.
This is useful when authoring new shapes or checking data from an external source.

**Minimum data graph example:**

```turtle
@prefix ex: <http://example.org/> .
ex:alice a ex:Person ;
  ex:age 30 .
```

**Matching shape:**

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix ex: <http://example.org/> .

ex:PersonShape a sh:NodeShape ;
  sh:targetClass ex:Person ;
  sh:property [
    sh:path ex:age ;
    sh:minInclusive 0 ;
    sh:maxInclusive 150 ;
  ] .
```

## Tips

- **Template shortcut** — if you have a template in mind but no DataObject yet, open the
  template from the Templates page, copy its `shapeGraph` Turtle into the shape pane, and
  paste example Turtle in the data pane.
- **Export the report** — the violations are rendered as structured HTML; you can copy-paste
  the table into a report or ticket.
- **No data is saved** — the playground is stateless; closing the tab discards everything.
