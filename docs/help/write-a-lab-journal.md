---
layout: default
title: Write a lab journal entry
permalink: /help/write-a-lab-journal/
audience: basic
---

# Write a lab journal entry

Every DataObject has a **Lab Journal** tab where you can record observations,
hypotheses, debrief notes, or any free-text context about the data. Entries
support full **Markdown** (CommonMark + GitHub Flavored Markdown), including
tables, code blocks, task lists, and bold/italic text.

## Open the lab journal

1. Navigate to a Collection and open a DataObject.
2. Click the **Lab Journal** tab on the DataObject detail page.
3. The current entry (if any) is shown rendered. Click **Edit** to change it.

## Write your first entry

1. In the editor, type your notes in Markdown.
2. Click **Save**. The entry is rendered and displayed immediately.

### Markdown basics

| What you type | What you see |
|---|---|
| `**bold**` | **bold** |
| `_italic_` | *italic* |
| `# Heading` | a large heading |
| `` `code` `` | inline code |
| `- item` | bullet list |
| `- [x] done` | checked task |

For a full code block, wrap lines in triple backticks:

````
```python
import pandas as pd
df = pd.read_csv("data.csv")
```
````

## View edit history

Every time you save a changed entry, the previous version is automatically
snapshotted. To browse the history:

1. Open the Lab Journal tab.
2. Click **History** (clock icon, top-right of the journal panel).
3. Each revision shows its timestamp and author. Click a revision to read the
   old text.

History is read-only — you cannot revert, but you can copy-paste from an old
revision back into the editor.

## Link a Jupyter notebook

If a `.ipynb` notebook file is attached to the same DataObject (as a
FileReference), it appears in the **Notebooks** section of the Lab Journal tab.

- Click **Open in JupyterHub** to launch the notebook in the configured
  JupyterHub instance (requires an admin to set the JupyterHub URL under
  **Admin → JupyterHub**).
- Click **Download** to save the `.ipynb` to your local machine.

To attach a notebook, upload it as a File reference on the DataObject's
**References** tab, then return to the Lab Journal tab to see it listed.

## Tips

- **Markdown preview** — the entry is rendered live in the view panel as
  soon as you save; no separate preview step is needed.
- **No size limit on content** — use as much text, tables, and code as you
  need. Very large entries (> 1 MB markdown source) may be slow to render.
- **One entry per DataObject** — each DataObject has a single journal entry.
  For multiple notes, separate them with Markdown headings within the same
  entry.

## Related help

- [Browse a collection](browse-collections.md)
- [Work with Jupyter notebooks](work-with-notebooks.md)
- [Annotate your data](annotating-data.md)
