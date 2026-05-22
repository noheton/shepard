# Importer plugin — Quickstart

**Status (PR-1).** This quickstart is a placeholder. The
user-visible importer flow lands across PR-3..PR-6. Once PR-6
lands the frontend, this page will be the front-door for casual
users — "I have a remote shepard instance and a target collection
on this instance; how do I pull data over?"

## Target flow (PR-6)

1. **Settings → Importers → New import**.
2. Pick a **source kind** (PR-3 ships *DLR shepard v5*; later PRs
   add *Git*, *S3*, *Local dropbox*).
3. Fill source-specific fields (URL + API key for v5).
4. Pick a **target collection** on this instance.
5. Click **Start import**. The run appears in the runs list with
   `PENDING` status; 2s polling updates the progress bar.

Until PR-6 lands, the only thing this plugin does on a fresh
install is appear in `shepard-admin plugins list`. See
`reference.md` for the full target shape, `install.md` for
operator concerns.
