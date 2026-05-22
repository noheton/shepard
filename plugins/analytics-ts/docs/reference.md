# `shepard-plugin-analytics-ts` — reference

The timeseries-analytics plugin. v0 ships a single detector — the
rolling-median MAD anomaly detector (`mad-v1`) — extracted from the
in-tree AI1b implementation behind the `TimeseriesAnalytics` SPI.

## Plugin id

`analytics-ts` — flip the per-plugin toggle via
`shepard.plugins.analytics-ts.enabled=true` (env var
`SHEPARD_PLUGINS_ANALYTICS_TS_ENABLED=true`) or at runtime via
`PATCH /v2/admin/plugins/analytics-ts/enabled`.

## Provided detectors

### `mad-v1` — rolling-median MAD anomaly detector

The historical AI1b detector, extracted verbatim. Implements the
`TimeseriesAnalytics` SPI at the `IN_PROCESS` execution-mode tier.

**Algorithm.** Pure-Java translation of the reference Python:

```python
def rolling_mad_detect(t, v, window=51, k=6.0):
    if window % 2 == 0:
        window += 1
    half = window // 2
    pad_v = np.pad(v, (half, half), mode='edge')
    med = [median(pad_v[i:i+window]) for i in range(len(v))]
    mad = [median(|pad_v[i:i+window] - med[i]|) for i in range(len(v))]
    mad = max(mad, 1e-3)     # floor to avoid div-by-zero
    z   = (v - med) / (1.4826 * mad)
    return z, |z| > k
```

**Parameters.**

| field | type | default | notes |
|---|---|---|---|
| `window` | int | 51 | Rolling window in points. Forced odd. Clamped to series length when too large. |
| `k` | float | 6.0 | Threshold on \|Z-score\|. |

**Behavioural equivalence guarantee.** The plugin's `MADDetector` is
byte-for-byte identical to the in-tree `AnomalyDetectionService` math.
This is enforced by `MADDetectorBehaviouralEquivalenceTest` which
compares `Double.doubleToRawLongBits` of every output across 100
randomized median trials, multiple synthetic series shapes, and a
sweep of `window` × `k` combinations. The JDK `Arrays.sort` + index
arithmetic primitive for `median` is the **trusted primitive** — a
library that handles even-length-window ties differently would
silently shift every Z-score, so the substitution is rejected.

## Execution modes

The `TimeseriesAnalytics` SPI declares two tiers via `ExecutionMode`:

- **`IN_PROCESS`** — light detectors (statistical, threshold-based,
  rule-based, MAD). Run in the backend JVM. Return synchronously from
  `detect(input)`. `mad-v1` is the canonical example.
- **`VIA_ORCHESTRATOR`** — heavy detectors (ML at corpus scale,
  Transformer-based, multi-channel correlation). Future home of
  `shepard-plugin-mlops` (Airflow / REBAR / SAIA / GWDG). Implementations
  extend `RemoteTimeseriesAnalytics` and submit jobs externally;
  `AnalyticsRegistry.dispatch` routes by mode but the
  `VIA_ORCHESTRATOR` branch currently logs "future home of orchestrator
  adapters via `shepard-plugin-mlops`" and throws
  `UnsupportedOperationException`.

A detector declares its tier via `executionMode()`:

```java
@Override
public ExecutionMode executionMode() {
    return ExecutionMode.IN_PROCESS; // default — light detectors
}
```

The dispatching layer (the in-tree `AnomalyDetectionService` after
PR-4) uses this signal to pick the REST response shape (synchronous
result vs. job handle).

## REST surface

Endpoint stays at the AI1b path — `POST /v2/timeseries-references/{refAppId}/detect-anomalies`.
The plugin does not introduce new REST resources. The optional
`detectorId` request field added in AT1 PR-4 routes to the registered
detector; omitting it falls back to `mad-v1` and is byte-identical to
the pre-AT1 wire shape (proven by `AnomalyDetectRequestIOWireStabilityTest`).

## Future: extension points

Adding a new detector is two steps:

1. Drop in a `@ApplicationScoped` bean implementing
   `de.dlr.shepard.spi.analytics.TimeseriesAnalytics` (or
   `RemoteTimeseriesAnalytics` for orchestrator-tier).
2. Set a stable `id()` (e.g. `"stl-residual-v1"`).

CDI discovery + `AnalyticsRegistry` indexing happens automatically.
Callers select the new detector via the `detectorId` field on the
existing REST endpoint.

## SHACL annotation shape (planned, PR-5)

The plugin ships `mffd-anomaly-annotation.shacl.ttl` in
`src/main/resources/shapes/` as the target shape for typed anomaly
annotations:

- `rdf:type fair2r:Claim, mffd:TimeseriesAnomalyAnnotation`
- Provenance: a `fair2r:StatisticalPass` sibling-Activity (not an
  `AuthoringPass`; relaxed required fields — no `promptHash` /
  `modelId`, but `detectorId`, `parametersUsed`, `librarySource`).

Until the parallel SHACL non-TS agent's substrate lands in `main`,
anomalies persist to the existing `:TimeseriesAnnotation` graph
exactly as today, with a TODO marker citing the SHACL target. See
PR-6 in the AT1 task tracker.
