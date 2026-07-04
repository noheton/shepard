---
title: Anomaly detection — reference
audience: researcher, data-scientist
---

# Anomaly detection reference

**Feature ID:** AI1b  
**Endpoint:** `POST /v2/references/{appId}/detect-anomalies`  
**UI entry point:** "Detect anomalies" button on the TimeseriesReference detail page  
**Auth:** Read on the parent DataObject (Write required when `createAnnotations=true`)  

---

## Overview

Shepard ships a built-in **rolling-median Median Absolute Deviation (MAD)**
detector for timeseries data. It requires no external AI service — it is a
pure-Java statistical algorithm that runs on any install.

The detector:

1. Loads all data points for a selected timeseries channel from TimescaleDB.
2. For each point, computes a rolling median over a configurable window and derives
   the Z-score via the 1.4826-scaled MAD: `z = (v − median) / (1.4826 × MAD)`.
   A MAD floor of 0.001 prevents division by zero on constant segments.
3. Flags a point as anomalous when `|z| > k` (the configurable threshold).
4. Merges contiguous anomalous points into intervals and returns them.

Optionally, each detected interval can be persisted as a `TimeseriesAnnotation`
node (`aiGenerated=true`) for future querying and display in the annotations panel.

### Algorithm parameters

| Field | Type | Default | Constraint | Meaning |
|-------|------|---------|------------|---------|
| `window` | integer | 51 | ≥ 3 | Rolling window size. Forced odd. Clamped to series length. |
| `k` | float | 6.0 | > 0 | Z-score threshold. Higher = fewer detections. |

**Typical settings:**

- `k = 6.0, window = 51` — conservative; catches strong spikes only.
- `k = 3.5, window = 21` — more sensitive; useful for slow trends.
- `k = 2.5, window = 7` — aggressive; surface every departure from local baseline.

---

## Series selection

A `TimeseriesReference` may hold multiple timeseries channels (one per combination
of `measurement`, `device`, `location`, `symbolicName`, `field`).

**Auto-select (empty body `{}`):** works when the reference holds exactly one series.

**Explicit selection:** supply any non-null subset of the five 5-tuple fields.
All non-null fields are applied as exact-match filters; the request fails with 400
if zero or more than one channel matches.

---

## Endpoint

```
POST /v2/references/{appId}/detect-anomalies
Content-Type: application/json
Authorization: Bearer <JWT>  |  X-API-KEY: <key>
```

`{appId}` is the `appId` of the `:TimeseriesReference` node (UUID v7 string).

### Request body

All fields are optional. Send `{}` to run with defaults against a single-channel reference.

```json
{
  "window":            51,
  "k":                 6.0,
  "createAnnotations": false,
  "measurement":       null,
  "device":            null,
  "location":          null,
  "symbolicName":      null,
  "field":             null,
  "detectorId":        "mad-v1"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `window` | integer | Rolling window size (≥ 3; forced odd; clamped to series length). |
| `k` | float | Z-score threshold (`|z| > k` = anomalous; must be > 0). |
| `createAnnotations` | boolean | Persist one `TimeseriesAnnotation` per detected interval when `true`. Requires Write permission. |
| `measurement` | string | 5-tuple filter — measurement tag. |
| `device` | string | 5-tuple filter — device tag. |
| `location` | string | 5-tuple filter — location tag. |
| `symbolicName` | string | 5-tuple filter — symbolic name tag. |
| `field` | string | 5-tuple filter — field tag. |
| `detectorId` | string | Detector to use. Default `"mad-v1"`. Future detectors register via `shepard-plugin-analytics-ts`. |

### Response body (200)

```json
{
  "anomalies": [
    {
      "startNs":   1685606408000000000,
      "endNs":     1685606428000000000,
      "peakValue": 12.34,
      "maxZScore": 9.71
    }
  ],
  "windowSize":        51,
  "threshold":         6.0,
  "totalPoints":       3600,
  "annotationsCreated": 0
}
```

| Field | Type | Description |
|-------|------|-------------|
| `anomalies` | array | Detected contiguous intervals. Empty array when the series is clean. |
| `anomalies[].startNs` | long | First anomalous sample timestamp (nanoseconds since Unix epoch). |
| `anomalies[].endNs` | long | Last anomalous sample timestamp. Equal to `startNs` for single-point anomalies. |
| `anomalies[].peakValue` | float | Raw value at the sample with the highest `|z|` in the interval. |
| `anomalies[].maxZScore` | float | Highest `|z|` across all samples in the interval. |
| `windowSize` | integer | Effective window used (after odd-forcing and clamping). |
| `threshold` | float | Effective `k` used. |
| `totalPoints` | integer | Number of data points evaluated. |
| `annotationsCreated` | integer | Number of `TimeseriesAnnotation` nodes created (0 when `createAnnotations=false`). |

### Status codes

| Code | Meaning |
|------|---------|
| 200 | Detection completed. `anomalies` may be empty. |
| 400 | Invalid parameters (`window < 3`, `k ≤ 0`), ambiguous series selection, or container missing/deleted. |
| 401 | No authentication (missing JWT or API key). |
| 403 | Caller lacks Read (or Write when `createAnnotations=true`) on the parent DataObject. |
| 404 | No `TimeseriesReference` found for `{appId}`. |

Error bodies follow [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807)
(`application/problem+json`).

---

## Annotation creation mode

When `createAnnotations=true`, each detected interval is persisted as a
`TimeseriesAnnotation` node:

- **label** `"anomaly"`
- **`aiGenerated`** `true`
- **`confidence`** `= min(1.0, maxZScore / (2 × k))`
- linked to the `TimeseriesReference` by the standard `HAS_ANNOTATION` edge

The annotations appear immediately in the annotations panel on the
TimeseriesReference detail page and are queryable via
`GET /v2/references/{appId}/channel-annotations`.

---

## Worked examples

### 1 — Single-channel reference, default params

```bash
curl -X POST https://shepard.example.org/v2/references/01968d2f-a1b2-7abc-def0-123456789abc/detect-anomalies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
```

Response (no anomalies):

```json
{
  "anomalies": [],
  "windowSize": 51,
  "threshold": 6.0,
  "totalPoints": 1800,
  "annotationsCreated": 0
}
```

### 2 — Multi-channel reference, explicit selection, save annotations

```bash
curl -X POST https://shepard.example.org/v2/references/01968d2f-a1b2-7abc-def0-123456789abc/detect-anomalies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "measurement": "vibration",
    "field": "g_rms",
    "window": 7,
    "k": 2.5,
    "createAnnotations": true
  }'
```

Response (one interval detected):

```json
{
  "anomalies": [
    {
      "startNs":   1685606408000000000,
      "endNs":     1685606428000000000,
      "peakValue": 12.34,
      "maxZScore": 9.71
    }
  ],
  "windowSize": 7,
  "threshold": 2.5,
  "totalPoints": 3600,
  "annotationsCreated": 1
}
```

---

## UI

Open the **TimeseriesReference detail page** → click **"Detect anomalies"**
(button in the top-right action bar). A dialog opens with sliders for
`window` and `k`, a toggle for `createAnnotations`, and a result table
showing detected intervals after the run.

The feature is always available on any install — it requires no AI API key
or external service.

---

## Python client snippet

```python
import requests, os

SHEPARD = "https://shepard.example.org"
TOKEN   = os.environ["SHEPARD_TOKEN"]
REF_ID  = "01968d2f-a1b2-7abc-def0-123456789abc"

resp = requests.post(
    f"{SHEPARD}/v2/references/{REF_ID}/detect-anomalies",
    headers={"Authorization": f"Bearer {TOKEN}"},
    json={"window": 21, "k": 3.5},
)
resp.raise_for_status()
result = resp.json()
print(f"{len(result['anomalies'])} anomalies in {result['totalPoints']} points")
for a in result["anomalies"]:
    print(f"  {a['startNs']} → {a['endNs']}  peak={a['peakValue']:.3f}  z={a['maxZScore']:.2f}")
```

---

## Notes

- The detector is **stateless** — every call re-reads all data from TimescaleDB.
  For large channels consider narrowing the query window server-side first (not
  yet exposed; track `AI1c` for channel-quality scoring that runs on-schedule).
- The `detectorId` field is forward-compatible: future detectors added via
  `shepard-plugin-analytics-ts` become reachable through the same endpoint
  without any request-shape change.
- Annotation confidence (`min(1.0, maxZScore / (2k))`) saturates at 1.0 for
  very strong spikes; it is a heuristic, not a calibrated probability.
