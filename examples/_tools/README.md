# _tools/

Migration + maintenance scripts shared by multiple showcases.
Pure-stdlib + the upstream `shepard_client` so they work against
any shepard 5.2.0+ instance (this fork OR an unmodified legacy
instance).

## `export-collection.py`

Dumps one shepard Collection — metadata, data-objects, references,
payloads, annotations, permissions, lab-journal — to a portable
directory tree.

Used to migrate the MFFD dataset from a legacy shepard instance
into this fork. The user runs the script against their source
instance; the resulting directory ships back here and gets fed to
the matching `import-collection.py` (forthcoming).

Key behaviours for large datasets:

- **Resumable** — every per-reference write drops a `*.done` marker;
  re-running picks up where it left off.
- **Rate-limited** — configurable min-ms-between-requests +
  download-bandwidth cap so the source instance stays responsive.
- **Retries** with exponential backoff on 429 / 5xx / network glitches.
- **Streams** file payloads chunk-by-chunk to disk (no large
  bytes-in-memory).

Run with `--dry-run` first to size the dataset:

```bash
pip install shepard-client
python export-collection.py \
    --host https://legacy-shepard.example.dlr.de/shepard/api \
    --apikey YOUR-API-KEY \
    --collection-id 42 \
    --out ./mffd-export/ \
    --dry-run
```

The manifest.json that lands has totals (data-object count,
reference counts per kind, payload bytes). Use it to budget the
real run.

## Next: `import-collection.py`

Forthcoming. Will:

1. Read the manifest.json + per-entity files written by the exporter.
2. Mint a fresh Collection on the target instance.
3. Replay containers (re-use by name, or mint fresh — `--container-policy`).
4. Replay DataObjects, References, Annotations, LabJournal entries.
5. Re-attach payloads (timeseries CSV → POST /payload, files →
   POST multipart, structured-data → POST JSON).

Source-side numeric ids in the export are replaced by target-side
ids during import; the manifest carries enough cross-references to
preserve the graph shape.
