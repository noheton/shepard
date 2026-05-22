# shepard-plugin-importer — Reference

**Status (PR-1).** Scaffold only. The plugin is registered with the
backend's `PluginRegistry` and is visible in `GET /v2/admin/plugins`,
but ships **no REST surface, no DAO, no source adapter** yet. PR-2
adds the `importer_run` Postgres table + `JobService`-shaped service;
PR-3 ships the first source adapter (`DLRv5Source`).

## What this plugin does (target shape)

The importer plugin is the future home of the **agentic data-management
pipeline**: a library of importers that pull data from a remote source
(DLR shepard v5 instance, git repo, S3 bucket, local dropbox, …) into
the local shepard instance running this plugin.

Each invocation is an **asynchronous run** — a row in the
`importer_run` Postgres table that follows the
`aidocs/platform/32-long-running-process-pattern.md` JobService state
machine (`PENDING → RUNNING → SUCCEEDED | FAILED | CANCELLED`). PR-2
ships the table; PR-4 surfaces the REST shape:

| Endpoint (PR-4) | Method | What it does |
|---|---|---|
| `/v2/imports` | POST | Submit a new run. Returns `202 Accepted` + `Location: /v2/imports/{id}`. |
| `/v2/imports/{id}` | GET | Single run status (poll target; default 2s polling per PR-6 decision). |
| `/v2/imports/{id}` | DELETE | Request cancellation. Cooperative. |
| `/v2/imports` | GET | List runs for the calling principal. |

## Source adapters

| Adapter (PR) | Source kind | What it pulls |
|---|---|---|
| `DLRv5Source` (PR-3) | DLR shepard v5 instance | Collections, DataObjects, files, timeseries, attributes. Extracted from `examples/mffd-showcase/scripts/mffd-dropbox-import.py` v12's `ShepardClient` lines 239-1170. Scoped to MFFD-use-case methods in PR-3; expansion to fuller v5 surface in follow-up PRs. |
| `GitSource` (later) | Git repo | Files + manifest from a tagged commit. |
| `S3Source` (later) | S3-compatible bucket | Object tree under a prefix. |
| `LocalDropboxSource` (later) | Local filesystem path | Watched directory. |

## Config keys (target — none yet in PR-1)

PR-1 introduces **zero** new config keys. PR-2 will add the standard
JobService knobs scoped to the importer:

- `shepard.importer.poll-interval` (default `5s`) — how often the
  scheduler claims `PENDING` rows.
- `shepard.importer.max-concurrent` (default `2`) — concurrent runs
  cap per backend instance.
- `shepard.importer.stale-after` (default `5m`) — heartbeat timeout.
- `shepard.importer.retention-days` (default `14`) — terminal-row TTL.

Source-credentials are stored encrypted-at-rest in the source-config
blob; the encryption key is read from `${SHEPARD_INSTANCE_SECRET}`.
See `install.md` for the production-hardening notes — vault
integration is a follow-up.

## CLI (PR-5)

```
shepard-admin importer status            # global plugin status
shepard-admin importer runs list          # paginate runs by status
shepard-admin importer runs show <id>     # single-run detail
shepard-admin importer runs cancel <id>   # cooperative cancel
```

Full design at `aidocs/16` IMP1a + memory `project_importer_plugin`.
