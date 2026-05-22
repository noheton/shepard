# Analytics-TS — quickstart

> Two-click task: run anomaly detection on a timeseries reference.

## 1. Find the timeseries reference

In the UI, open a DataObject that has timeseries data. Each reference
has an `appId` — copy it from the URL or the detail panel.

In code:

```http
GET /v2/dataobjects/{dataObjectAppId}/references
```

…returns the references attached to a DataObject. Pick the one
pointing at the timeseries container you want to score.

## 2. POST a detect-anomalies request

The simplest call — defaults everywhere, single-series reference:

```http
POST /v2/timeseries-references/{refAppId}/detect-anomalies
Content-Type: application/json

{}
```

Response:

```json
{
  "intervals": [
    {
      "startNs": 8000000000,
      "endNs":   8500000000,
      "peakValue": 12.4,
      "maxZScore": 8.7
    }
  ],
  "windowSize": 51,
  "k": 6.0,
  "totalPoints": 5000,
  "annotationsCreated": 0
}
```

Each `interval` is one contiguous run of anomalous data points.

## 3. Tune window + threshold

If too many false positives, raise `k`:

```json
{"window": 51, "k": 9.0}
```

If the anomaly is short (a single sample), shrink `window`:

```json
{"window": 11, "k": 6.0}
```

## 4. Persist anomalies as annotations

Set `createAnnotations` to `true` — each interval is persisted as a
`TimeseriesAnnotation` with label `anomaly`, `aiGenerated=true`, and
a confidence derived from the max |Z-score|.

```json
{"createAnnotations": true}
```

You need Write permission on the parent DataObject.

## 5. Pick a different detector (AT1)

The default is `mad-v1`. When new detectors register (`stl-residual-v1`,
future ML-based detectors), select them via `detectorId`:

```json
{"detectorId": "stl-residual-v1", "k": 4.0}
```

Detectors that run in-process (the MAD detector and its siblings)
respond synchronously. Detectors that route via an orchestrator
(future, when `shepard-plugin-mlops` ships) return a job handle that
you poll for status — that path is not live in the current release.
