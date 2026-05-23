---
title: Watch an import land in real time
description: Plot Shepard's own ingestion rate while a large import is running
permalink: /help/observing-an-import/
layout: default
audience: user
---
# Watch an import land in real time

When a large dataset is being imported, you want a quick way to confirm that
data is actually flowing — DataObjects appearing, files arriving, Garage
filling up. Shepard ships a collector script that polls the running import
every five minutes and writes the per-payload-kind counters back into a
Shepard `TimeseriesContainer` inside the same collection.

The result: **a live, plottable view of the data-influx, rendered by
Shepard's existing time-series chart UI**. No separate dashboard, no
Prometheus side-stack — the observability lives inside Shepard itself.

## What the collector measures

One row per metric, every five minutes, all under the same 5-tuple
(`measurement=import_progress`, `device=mffd-dropbox`, `location=dest`,
`field=value`):

| Channel | Meaning |
|---|---|
| `dos_total` | Number of DataObjects in the watched collection |
| `dos_tapelaying` / `dos_bridgewelding` / `dos_skeleton` / `dos_other` | DataObject counts by name pattern |
| `file_refs_count` | Total `FileReference` entries across all DataObjects |
| `file_bytes_total` | Sum of `file.fileSize` over every referenced file |
| `ts_refs_count` | Total `TimeseriesReference` entries |
| `ts_channels_total` | Sum of `len(timeseries)` across all TS references |
| `sd_refs_count` | Total `StructuredDataReference` entries |
| `garage_bucket_bytes` | Garage object-store size for the `shepard-files` bucket |

## How to view the live influx chart

1. Open the **MFFD-Dropbox** collection.
2. Find the DataObject named **`ImportStats-2026-05-23-mffd`**.
3. Expand its **Data References** panel.
4. Click **Graph & Metrics** next to the `import-progress-counters` timeseries
   reference.
5. Tick the channels you want to overlay — `dos_total`, `file_bytes_total`,
   and `garage_bucket_bytes` are the headline lines; the per-category DO
   counts are useful for spotting a stuck stage.

The chart updates each time the collector finishes a tick (every five
minutes). Refresh the page to pull in new points.

> **Observer baseline.** Because the stats collector writes _into_ the same
> collection it observes, the `ImportStats-…` DataObject and its
> `TimeseriesReference` are themselves counted. From the second tick onward
> you see a constant `+1` on `dos_total` (the stats DO) and `+11` on
> `ts_channels_total` (its 11 channels). This is intentional: the chart's
> baseline is the observer; the **rate of change** is the import progress.
> If you ever need an observer-free count, filter the stats DO out by appId
> on the client side or subtract the constants by inspection.

## Operator: how to start the collector

The collector lives at `scripts/mffd-import-stats-collector.py`. It runs on
the dev box that hosts the import (it needs `docker exec shepard-garage` for
Garage size readings).

**One-time bootstrap** — mints the `TimeseriesContainer` and the
`ImportStats-…` DataObject (idempotent: re-running reuses the existing pair):

```bash
SHEPARD_API_KEY=… uv run scripts/mffd-import-stats-collector.py --bootstrap
```

The output prints `STATS_TS_CONTAINER_ID` and `STATS_DO_ID` — capture both.

**Then either** run the looping foreground process for an ad-hoc session:

```bash
SHEPARD_API_KEY=… STATS_TS_CONTAINER_ID=… STATS_DO_ID=… \
  uv run scripts/mffd-import-stats-collector.py
```

**Or** install the systemd timer for a persistent, every-five-minute schedule:

```bash
sudo cp infrastructure/systemd/mffd-import-stats-collector.{service,timer} \
        /etc/systemd/system/
sudo install -d -m 0750 /etc/shepard
sudo install -m 0600 /dev/stdin /etc/shepard/mffd-stats-collector.env <<'EOF'
SHEPARD_URL=https://shepard-api.nuclide.systems
SHEPARD_API_KEY=<paste the JWT here>
COLL_APP_ID=019e4e56-ca63-76f3-9bf0-6681f7fe6d56
STATS_TS_CONTAINER_ID=<from --bootstrap>
STATS_DO_ID=<from --bootstrap>
EOF
sudo systemctl daemon-reload
sudo systemctl enable --now mffd-import-stats-collector.timer
```

Verify with `systemctl list-timers | grep mffd-import-stats` and
`journalctl -u mffd-import-stats-collector.service -f`.

## Why this matters

Most observability stacks treat the application as the thing being watched
and the metric store as a separate system. Shepard already has a first-class
time-series substrate (TimescaleDB through the `TimeseriesContainer` payload
shape) — so the most honest place to store Shepard's own ingest rate is
inside Shepard, attached to the collection that's being filled, viewable
with the chart UI every researcher already knows.

It is also a useful self-test: if the chart updates, the import-collection-DO-
TimeseriesReference path is working end-to-end. If the chart stalls but the
import keeps running, the time-series surface itself is the regression.

## Related

- [Plot timeseries data](timeseries-plotting.md) — the chart UI used here.
- [Monitor collection activity](monitor-collection-activity.md) — the
  audit-trail view that complements this rate-of-flow view.
- Dispatcher backlog: `OBS-MFFD1` (this collector), `OBS-MFFD2` (generalise to
  any import job), `OBS-MFFD3` (in-flight imports dashboard view).
