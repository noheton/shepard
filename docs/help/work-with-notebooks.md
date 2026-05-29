---
title: Work with Jupyter notebooks attached to DataObjects
---

# Work with Jupyter notebooks attached to DataObjects

Shepard treats Jupyter notebooks (`.ipynb` files) as regular file references —
they show up in the unified **Data References** table on every DataObject
detail page, distinguished by a notebook icon. When your instance admin has
configured a JupyterHub link-out, each notebook row gets a one-click **Open
in JupyterHub** action.

## Attach a notebook to a DataObject

1. Open the DataObject detail page.
2. Drag the `.ipynb` file onto the **Data References** panel — or click
   **Add data reference → File**.
3. The new row appears immediately, classified as **Notebook** (the notebook
   icon and the row kind chip both say so).

The same FR1b singleton path that powers every other file upload also handles
notebooks; you don't need to choose a special "notebook" upload mode.

## Open a notebook in JupyterHub

If your instance admin has configured the link-out (see the **JupyterHub
link-out** admin page), the notebook row shows an **Open in JupyterHub**
button:

1. Click the button. A new browser tab opens at
   `https://your-jupyterhub.example.org/hub/spawn?file=<download URL>`.
2. JupyterHub spawns your personal server (or you log in if you don't have
   one yet) and pulls the notebook file from Shepard.

If the button is missing, your admin hasn't enabled the link-out yet — they
can do so via **Administration → JupyterHub link-out** without any code
deployment.

## Download a notebook without going through JupyterHub

Every notebook row also has a plain **Download** icon. Click it to get the
raw `.ipynb` file — useful when you want to open the notebook locally, send
it to a colleague, or version it in your own git repo.

## Delete a notebook reference

The row's red **Delete** icon removes both the Shepard reference and the
stored bytes (FR1b singletons own their bytes; there is no soft-delete safety
net for the underlying file). The deletion is captured as a `:Activity`
record so the audit trail keeps the deletion event even after the bytes are
gone.

## Annotate a notebook

The tag icon on the notebook row opens the same semantic annotation dialog
used everywhere else in Shepard. You can attach predicates like
`urn:shepard:notebook:framework` → `jupyter` to make notebook references
discoverable via SPARQL.

## See also

- `docs/reference/notebooks.md` — the per-feature reference (every endpoint,
  every config field).
- `docs/admin/runbooks/jupyterhub-config.md` — how the admin turns the
  link-out on.
- `docs/help/annotating-data.md` — semantic annotation walk-through.
