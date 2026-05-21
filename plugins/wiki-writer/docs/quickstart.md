---
title: Generate a lab journal entry
weight: 96
---

# Generate a lab journal entry with AI

The wiki-writer plugin can write a first-draft Markdown lab journal entry for
any DataObject based on its metadata and its siblings in the same Collection.

## Prerequisites

- `shepard-plugin-ai` is deployed and the **TEXT** capability is configured
  (see [AI plugin install](../../ai/docs/install.md)).
- You have Write permission on the Collection that contains the DataObject.

## Step 1 — Navigate to a DataObject

Open a Collection, then click into any DataObject.

## Step 2 — Generate an entry

In the DataObject action menu (…), click **Generate journal entry**.

The system calls the configured LLM and writes a Markdown lab journal entry
containing:

- A brief summary of the DataObject's purpose
- Its status and key attributes
- Its relationship to predecessor and successor DataObjects
- How it fits among its siblings in the Collection

The entry appears immediately in the **Lab journal** tab.

## Step 3 — Review and refine

The generated entry is a first draft. Edit it like any other lab journal entry —
click the pencil icon in the journal panel to open the Markdown editor.

## Using the API directly

```bash
# Generate an entry with default settings
curl -X POST \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  https://<your-instance>/v2/collections/<collectionAppId>/data-objects/<dataObjectAppId>/wiki-write

# With a custom instruction (e.g. focus on anomalies)
curl -X POST \
  -H "Authorization: Bearer <your-token>" \
  -H "Content-Type: application/json" \
  -d '{"extraInstruction": "Highlight any anomalies or deviations from expected values."}' \
  https://<your-instance>/v2/collections/<collectionAppId>/data-objects/<dataObjectAppId>/wiki-write
```

## Tips

- **Rich attributes win.** The more attributes a DataObject has, the richer the
  generated entry. Annotate DataObjects before calling wiki-write.
- **Predecessor links matter.** The LLM sees the predecessor/successor chain and
  can summarise the process lineage.
- **Use `extraInstruction` for domain focus.** E.g.:
  - `"Focus on the thermal measurements and any exceedances."`
  - `"Write for a DIN EN 9100 audit trail — include all quality-relevant fields."`
  - `"Summarise in German."`
- **Token budget.** The default `maxTokens=1024` (~800 words) is enough for most
  DataObjects. For a DataObject with many siblings, increase to 2048.
