# Analytics-TS — operator install

## Prerequisites

- Shepard 6.0.0-SNAPSHOT or newer (the `TimeseriesAnalytics` SPI lives
  in `de.dlr.shepard.spi.analytics` and ships with this fork's
  6.0.0-SNAPSHOT line).
- No external dependencies. The `mad-v1` detector is pure-Java and
  runs in the backend JVM (`IN_PROCESS` execution mode).

## Build + install

The plugin ships with the backend image by default — operators
consuming the published GHCR images do not need to do anything.

For local builds:

```bash
$ cd backend && ./mvnw -DnoPlugins -DskipTests -Dmaven.test.skip=true \
    -Dquarkus.build.skip=true install
$ cd plugins/analytics-ts && ../../backend/mvnw -DskipTests install
$ cd backend && ./mvnw package
```

For drop-in install on an already-running backend:

```bash
$ cp plugins/analytics-ts/target/shepard-plugin-analytics-ts-*.jar \
     /path/to/shepard/backend/plugins/
```

## Enable

Defaults to **off** (per the project's universal plugin posture). Flip
via one of:

- **env var (deploy-time):**
  `SHEPARD_PLUGINS_ANALYTICS_TS_ENABLED=true`
- **admin REST (runtime):**
  `PATCH /v2/admin/plugins/analytics-ts/enabled` with body
  `{"enabled": true}`
- **CLI (runtime):**
  `shepard-admin plugins enable analytics-ts`

When enabled, `GET /v2/admin/plugins` reports
`analytics-ts vX enabled`. Disabling does not remove the plugin from
the classpath — it just gates the per-plugin scope.

## Config keys

| key | type | default | runtime-flippable | notes |
|---|---|---|---|---|
| `shepard.plugins.analytics-ts.enabled` | bool | false | yes (via admin REST) | Per-plugin gate. |

The plugin has no `:*Config` singleton in this version — every detector
takes its parameters per-request (the AI1b `window` / `k` fields, plus
the new optional `detectorId`).

## Healthcheck

No dedicated healthcheck endpoint. The plugin is healthy if
`GET /v2/admin/plugins` shows it `enabled`, and the existing AI1b
endpoint `POST /v2/timeseries-references/{refAppId}/detect-anomalies`
returns 200 for a known-good reference.

## Known pitfalls

- **Wire-stability invariant.** Old clients that omit `detectorId`
  receive byte-identical responses to the pre-AT1 behaviour.
  `AnomalyDetectRequestIOWireStabilityTest` enforces this.
- **Behavioural-equivalence invariant.** The `MADDetector` math is
  byte-for-byte identical to the in-tree `AnomalyDetectionService` —
  `MADDetectorBehaviouralEquivalenceTest` enforces this. A maintainer
  swapping in a different median library that handles even-length-window
  ties differently breaks this test (intentionally — the substitution
  is not safe).
- **VIA_ORCHESTRATOR tier not live.** The SPI surface for
  orchestrator-tier detectors (`RemoteTimeseriesAnalytics`) ships in
  AT1 PR-1 as a forward-compat stub. Concrete adapters land with
  `shepard-plugin-mlops`. Until then, attempting to dispatch to a
  `VIA_ORCHESTRATOR` detector throws `UnsupportedOperationException`
  with a "future home" message.
