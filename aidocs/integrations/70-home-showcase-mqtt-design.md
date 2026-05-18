# home-showcase — MQTT → shepard collector + demo (HOME1)

**Scope.** Design for a second demo seed alongside the existing LUMEN
hot-fire campaign showcase. This one ingests live Home-Assistant
telemetry through MQTT and stores it in shepard timeseries containers,
so visitors see shepard handling live, continuously-arriving data — not
just the deterministic synthetic LUMEN payload.

**Status.** Design. Broker explored 2026-05-18 (see
`project_home_showcase_mqtt.md` for raw findings). Implementation
deferred until the v1-compat-layer (V2BASE) lands at least Phase B
since the collector will exercise it as the integration smoke.

**Snapshot date.** 2026-05-18.

**Companion docs.** `aidocs/platform/68-v2-baseline-v1-compat-layer.md`
(this collector is its smoke test).

---

## 1. Broker survey (live capture, 2026-05-18)

**Endpoint:** `192.168.1.60:1883` plain MQTT (the 8883/TLS port
protocol-errors with the supplied creds; demo uses 1883). Creds `mqtt`/`mqtt`.

**Topic shape:**

| Root | Count | What it carries |
|---|---:|---|
| `homeassistant/<domain>/<entity_id>/config` | 1189 | Retained HA MQTT auto-discovery descriptors — pure metadata (entity name, unit, device_class, value_template). Used by the collector to *discover* what to ingest, not as a data source itself. |
| `zigbee2mqtt/<friendly_name>` | 33 | One device per topic. Payload is a JSON blob with every current reading the device exposes (power, energy, voltage, temperature, humidity, position, motor_state, …). Updated ~0.5–2.3 msg/s per active device. **Primary data source.** |
| `awtrix_fcf0bc/...` | 25 | LED clock — out of scope for the demo. |
| `ps5-mqtt/...` | 1 | Console status — out of scope. |
| `appdaemon_mqtt_client/...` | 1 | AppDaemon ping — out of scope. |

**Revised container layout** (user direction 2026-05-18 — "too many.
powerocean is inverter. 3 container tops. grid is in tibber an inoexy"):

| Container | What it carries |
|---|---|
| **`solar-powerocean`** | Inverter telemetry from PowerOcean — solar production + battery state + grid in/out. The headline numerical signal. |
| **`home-environment`** | Combined temperature / humidity / illuminance / pressure readings across all rooms. The measurement class is encoded as a channel attribute, not a separate container. |
| **`home-energy-appliances`** | Per-appliance smart-plug power + energy from zigbee2mqtt (the 27/27 power+energy sensors). |

**Dropped from this design** per the same direction:
- `home-airquality` (eco2 / aqi) — fold into `home-environment` if the data is interesting, otherwise drop.
- `home-plants` (soil_moisture / soil_fertility) — drop.
- `home-diagnostics` (linkquality / battery / last_seen) — drop. Diagnostics noise belongs in HA's UI, not shepard.

Grid metering data lives in **Tibber + Inoexy** cloud APIs (not on MQTT) —
a separate connector if/when we add them. Out of scope for this design.

**Solar / grid telemetry**: not visible at the explored prefixes.
Either the inverter integration publishes under a different root, or
it's not configured. **Defer the inverter container** until the user
either flips it on or points at the right topic.

**Weather data**: same — not at the discovered roots. May be under a
`weather/...` or `weatherflow/...` topic; a targeted re-capture with
`-t 'weather*/#' -t 'weatherflow*/#'` will confirm.

---

## 2. Demo collection shape

Single shepard Collection: **"Home energy & environment (live)"**.

DataObject tree (matches the 3-container layout above):

```
Home energy & environment (live)           [Collection]
├── Solar & battery                        [DataObject]
│   └── ref → solar-powerocean             [TimeseriesReference]
├── Smart plugs                            [DataObject]
│   └── ref → home-energy-appliances       [TimeseriesReference]
└── Indoor environment                     [DataObject]
    └── ref → home-environment             [TimeseriesReference]
```

Channel keys inside each timeseries container map 1:1 from zigbee2mqtt:

- `measurement` = sensor class (e.g. `power`, `temperature`)
- `device` = the zigbee2mqtt `friendly_name` (e.g. `"Wohnzimmer Rolladen"`)
- `location` = inferred from the friendly_name's first word (room name)
- `symbolic_name` = the zigbee ieee_address (stable across renames)
- `field` = `value`

Collection annotations (SA-CONT) tag the collection itself:
`is about → "home automation"`, `instrument → "Zigbee 2 MQTT bridge"`.

Container annotations: each TimeseriesContainer gets a single
`measurement_class` annotation (`measurement_class → "power"` etc) so
future SPARQL / search lights up.

---

## 3. Collector design

`examples/home-showcase/collector.py` — a long-lived python process:

```
┌──────────────────────────────────────────────────────────────┐
│                          collector                            │
├──────────────────────────────────────────────────────────────┤
│  paho.mqtt.Client                                             │
│  └─ subscribe('zigbee2mqtt/+', qos=0)                         │
│                                                               │
│  on_message(topic, payload):                                  │
│    1. friendly_name = topic.split('/', 1)[1]                  │
│    2. parsed = json.loads(payload)                            │
│    3. for each numeric field that maps to a known class:      │
│         emit_to(container_for_class[class],                   │
│                 measurement=class,                            │
│                 device=friendly_name,                         │
│                 location=room_of(friendly_name),              │
│                 symbolic_name=ieee_address_cache[friendly],   │
│                 field='value',                                │
│                 timestamp_ns=now_ns(),                        │
│                 value=parsed[field])                          │
│    4. snapshot to home-state-snapshots StructuredDataContainer│
│       (once per minute, full JSON, for debugging)             │
│                                                               │
│  Background ticker: every 30 s, flush per-container batch     │
│  via the legacy POST /timeseriesContainers/{id}/payload       │
│  (NDJSON streaming ingest — P14).                             │
└──────────────────────────────────────────────────────────────┘
```

**Critical: collector uses the upstream Python `shepard_client`** —
i.e. the `/shepard/api/...` V1 surface. This is intentional. Once
V2BASE Phase B lands for the timeseries endpoints, the collector
becomes the integration smoke for the compat adapter. A 24-hour
run with no data loss confirms the wire is byte-identical.

---

## 4. Auth + secrets

Per `project_home_showcase_mqtt.md`:
- **Broker password is NOT committed.** The collector reads
  `MQTT_HOST` / `MQTT_PORT` / `MQTT_USER` / `MQTT_PASSWORD` from
  env vars. The docker-compose `home-showcase-collector` service
  declares them as required env vars; the demo deployment sets
  them externally.
- The "no TLS-cert verification" posture on 8883 is documented but
  the working endpoint is 1883 plain MQTT inside the LAN. Surface
  this as a one-line WARN at collector startup so the security
  posture is visible in the log.
- **shepard API key** also via env (`SHEPARD_API_KEY`).
- A `home-showcase/.env.example` documents the variables. Real
  values stay out of the repo.

---

## 5. Docker-compose integration

New service in `infrastructure/docker-compose.override.yml`:

```yaml
home-showcase-collector:
  image: python:3.12-slim
  profiles: ["home-showcase"]   # off by default
  environment:
    MQTT_HOST: ${MQTT_HOST:?required}
    MQTT_PORT: ${MQTT_PORT:-1883}
    MQTT_USER: ${MQTT_USER:?required}
    MQTT_PASSWORD: ${MQTT_PASSWORD:?required}
    SHEPARD_API: "http://backend:8080/shepard/api"
    SHEPARD_API_KEY: ${SHEPARD_API_KEY:?required}
  volumes:
    - ../examples/home-showcase:/collector
  command: bash -c "pip install paho-mqtt shepard-client && python /collector/collector.py"
  depends_on:
    backend:
      condition: service_healthy
  restart: unless-stopped
```

Run with `docker compose --profile home-showcase up -d`.

The matching seed (one-shot setup of the Collection + DataObjects +
empty containers) lives at `examples/home-showcase/seed.py` and is
invoked by a separate seeder service (same shape as the LUMEN seeder).

---

## 6. Renames before this lands

User direction 2026-05-18:
- `examples/lumen-showcase/` → `examples/lumen-showcase/`
- new `examples/home-showcase/`
- separate `examples/instance-seed/` for the bits shared by both
  data seeds (ROR preseed, ontology config, templates, …)

Lands in three commits:

1. **rename**: pure `git mv` of `lumen-showcase/` → `lumen-showcase/`,
   update `infrastructure/docker-compose.override.yml` `seeder` service
   to point at the new path. Zero behaviour change.
2. **extract instance seed**: lift the instance-level helpers
   (`best_effort_ror_preseed`, `best_effort_template`,
   `ensure_semantic_repo` partially, `best_effort_api_keys`) into a
   new `examples/instance-seed/instance.py` module. Both showcases
   import it.
3. **new home-showcase**: add `examples/home-showcase/seed.py` +
   `collector.py` + `.env.example`.

---

## 7. Watchlist concept (deferred, related)

User hint 2026-05-18:
> "containers could be added [to] some kind of watchlist by
> collections to also get the 'raw data' overview"

For home-showcase the natural shape: the "Home energy & climate
(live)" Collection's DataObjects each reference one container.
But a researcher visiting the Collection page might want to see ALL
the live data flowing in, not just per-DataObject slices.

Proposed: `:watches` relationship from Collection to Container.
Detail page renders a "Watched containers" panel listing them with
the same "Referenced by" detail expansion, just inverted (collection
expanding the watched container, not container expanding referencing
data objects).

Track as separate item — design lives next to this one once we start.

---

## 8. Open follow-ups (need user input)

1. **Solar / grid inverter telemetry.** Not at the discovered topic
   roots. User mentioned "solar / energy grid". Need either the
   broker topic for the inverter, or confirmation that this stays
   pure home-monitoring without the inverter.
2. **Weather data.** Same — re-capture with weather-specific
   wildcards when the user can point at a topic, or skip.
3. **Friendly-name → room mapping.** zigbee2mqtt device names like
   "Wohnzimmer Rolladen" embed German room names. The collector
   parses the first word as `location`. Want a smarter mapping
   (configurable JSON file mapping device names → room labels),
   or accept the heuristic?
4. **State-string vs numeric handling.** zigbee2mqtt payloads also
   carry state strings like `"OPEN"`, `"OFF"`, `"idle"`. Map these
   to a separate StructuredDataContainer (the per-minute snapshot)
   or skip entirely?

---

## 9. Out of scope

- **Bidirectional control.** The collector only ingests; it never
  publishes back to MQTT. No HA → shepard → HA round-trips.
- **Schema enforcement.** Drift in the zigbee2mqtt payload shape
  (vendor adds a new field) just gets dropped silently if it
  doesn't match a known sensor class. No alerting yet.
- **Multi-broker support.** One broker per collector instance.
  Running multiple brokers means multiple collectors.
