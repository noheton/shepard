# home-showcase

A second demo dataset alongside `lumen-showcase/`. Where the LUMEN
showcase ships deterministic synthetic data, this one ingests **live
telemetry** from a Home Assistant MQTT broker so visitors see shepard
handling continuously-arriving data.

## Topology

```
┌──────────────┐    MQTT     ┌────────────┐    /shepard/api    ┌───────────┐
│  HA broker   │ ──────────▶ │ collector  │ ────────────────▶ │  shepard  │
│ 192.168.1.60 │             │ (this dir) │                    │           │
└──────────────┘             └────────────┘                    └───────────┘
                                                                     │
                                                                     ▼
                                                              TimescaleDB
                                                              (3 containers)
```

Three TimeseriesContainers:

| Container | Source | Channels |
|---|---|---|
| `solar-powerocean` | PowerOcean inverter (planned) | empty for now — wire up later |
| `home-energy-appliances` | zigbee2mqtt smart plugs | power, energy, voltage, current |
| `home-environment` | zigbee2mqtt sensors | temperature, humidity, illuminance, pressure |

Diagnostics noise (linkquality, battery, last_seen, action, …) is
dropped at the collector — it's not data anyone wants to graph.

## Bring-up

Prerequisites:

1. shepard is running (`docker compose up -d` from
   `infrastructure/`).
2. The LUMEN seed has run at least once so the admin API key exists
   on a shared volume (`bootstrap-token`).

Steps:

```bash
cd infrastructure/

# 1. One-shot provisioning: creates Collection + DataObjects +
#    TimeseriesContainers + TimeseriesReferences. Idempotent.
docker compose run --rm home-showcase-seeder

# 2. Fill .env from .env.example (broker creds + shepard api key).
cp ../examples/home-showcase/.env.example ../examples/home-showcase/.env
$EDITOR ../examples/home-showcase/.env

# 3. Start the collector. Plays through restarts.
docker compose --profile home-showcase up -d home-showcase-collector

# 4. Watch ingest.
docker compose logs -f home-showcase-collector
```

## Disabling

Stop the collector without touching the data:

```bash
docker compose stop home-showcase-collector
```

The Collection / DataObjects / Containers stay (paused). Re-running the
collector picks up where it left off — there's no resume state to
manage.

## When the PowerOcean integration appears

The `solar-powerocean` container exists from day one but is empty
until a PowerOcean bridge publishes to MQTT (or a direct REST poller is
wired in). At that point:

1. Extend `CONTAINER_BY_CLASS` in `collector.py` with the PowerOcean
   field names → `solar-powerocean`.
2. Restart the collector.

No backend changes needed.

## See also

- Design: `aidocs/integrations/70-home-showcase-mqtt-design.md`.
- Sibling demo: `examples/lumen-showcase/` (LUMEN synthetic hot-fire).
- Why the rename to `lumen-showcase/` is queued: see
  `project_open_queue_2026-05-18.md` item 17.
