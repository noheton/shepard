"""home-showcase collector — Home Assistant REST API polling → Shepard timeseries bridge.

Polls HA /api/states/{entity_id} every POLL_INTERVAL_SEC (default 30) and POSTs
numeric values to the corresponding Shepard TimeseriesContainer via the
/timeseriesContainers/{id}/payload endpoint.

Auth:
- HA: HA_TOKEN (long-lived access token), HA_HOST (default 192.168.1.60), HA_PORT (default 8123)
- Shepard: SHEPARD_API_KEY + BACKEND_URL (injected by entrypoint.sh)

Sensor mapping is hardcoded in SENSORS below.  To discover the exact entity IDs on
your HA instance go to Settings → Developer Tools → States and filter by "powerocean"
or "electricity".  Edit SENSORS to match, then restart the container.
"""

from __future__ import annotations

import json
import logging
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Optional

# ---------------------------------------------------------------------------
# Config

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    level=os.environ.get("LOG_LEVEL", "INFO"),
)
log = logging.getLogger("home-collector")

HA_HOST = os.environ.get("HA_HOST", "192.168.1.60")
HA_PORT = os.environ.get("HA_PORT", "8123")
HA_TOKEN = os.environ.get("HA_TOKEN", "")
POLL_INTERVAL_SEC = int(os.environ.get("POLL_INTERVAL_SEC", "30"))
BACKEND_URL = os.environ.get("BACKEND_URL", "http://backend:8080/shepard/api")
SHEPARD_API_KEY = os.environ.get("SHEPARD_API_KEY", "")
LOOKUP_PATH = os.environ.get("HOME_SHOWCASE_LOOKUP_PATH", "/opt/home-showcase/seeded.json")


# ---------------------------------------------------------------------------
# Sensor map
#
# Each entry: (ha_entity_id, container_name, measurement, device, location, symbolic_name, field)
#
# - container_name must match an entry in seeded.json (written by seed.py).
# - (measurement, device, location, symbolic_name, field) is the Shepard 5-tuple that
#   uniquely identifies a channel within a container.
# - field names are kept short; units are informational only (not sent to Shepard).
#
# Verify entity IDs in HA → Settings → Developer Tools → States.

@dataclass(frozen=True)
class Sensor:
    entity_id: str
    container: str
    measurement: str
    device: str
    location: str
    symbolic_name: str
    field: str
    unit: str = ""  # informational only


SENSORS: list[Sensor] = [
    # ── PowerOcean solar inverter (sensor.powerocean_<serial>_*) ──────────────
    Sensor("sensor.powerocean_hj37zdh5zg5w0109_solar_power",
           "solar-inverter", "solar", "powerocean_inverter", "roof",
           "mppt", "power_w", "W"),
    Sensor("sensor.powerocean_hj37zdh5zg5w0109_ac_output_power",
           "solar-inverter", "solar", "powerocean_inverter", "inverter",
           "ac_output", "power_w", "W"),
    Sensor("sensor.powerocean_hj37zdh5zg5w0109_grid_feed_in",
           "solar-inverter", "solar", "powerocean_inverter", "grid",
           "feed_in", "power_w", "W"),
    Sensor("sensor.powerocean_hj37zdh5zg5w0109_self_consumption",
           "solar-inverter", "solar", "powerocean_inverter", "house",
           "self_use", "power_w", "W"),
    Sensor("sensor.powerocean_hj37zdh5zg5w0109_today_yield",
           "solar-inverter", "solar", "powerocean_inverter", "roof",
           "yield", "energy_kwh", "kWh"),
    # ── Klostergasse electricity meter (sensor.electricity_klostergasse_20a_*) ─
    Sensor("sensor.electricity_klostergasse_20a_power",
           "home-consumption", "electricity", "klostergasse_20a", "grid",
           "meter", "power_w", "W"),
    Sensor("sensor.electricity_klostergasse_20a_energy_import",
           "home-consumption", "electricity", "klostergasse_20a", "grid",
           "import", "energy_kwh", "kWh"),
    Sensor("sensor.electricity_klostergasse_20a_energy_export",
           "home-consumption", "electricity", "klostergasse_20a", "grid",
           "export", "energy_kwh", "kWh"),
]


# ---------------------------------------------------------------------------
# HA REST helpers

def _ha_base() -> str:
    return f"http://{HA_HOST}:{HA_PORT}/api"


def _ha_get_state(entity_id: str) -> Optional[float]:
    """Return the current numeric state of an HA entity, or None on error."""
    url = f"{_ha_base()}/states/{entity_id}"
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {HA_TOKEN}", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            state = body.get("state", "")
            if state in ("unavailable", "unknown", ""):
                return None
            return float(state)
    except (ValueError, TypeError):
        log.debug("Non-numeric state for %s: %r", entity_id, state)
        return None
    except urllib.error.HTTPError as e:
        log.warning("HA %s → HTTP %s", entity_id, e.code)
        return None
    except Exception as e:
        log.warning("HA %s → %s", entity_id, e)
        return None


# ---------------------------------------------------------------------------
# Shepard ingest helper

def _shepard_ingest(
    container_id: int,
    sensor: Sensor,
    timestamp_ns: int,
    value: float,
) -> bool:
    """POST one data point to /timeseriesContainers/{id}/payload. Returns True on success."""
    url = f"{BACKEND_URL.rstrip('/')}/timeseriesContainers/{container_id}/payload"
    body = json.dumps({
        "timeseries": {
            "measurement": sensor.measurement,
            "device": sensor.device,
            "location": sensor.location,
            "symbolicName": sensor.symbolic_name,
            "field": sensor.field,
            "valueType": "Float",
        },
        "points": [{"timestamp": timestamp_ns, "value": value}],
    }).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=body,
        headers={
            "X-API-KEY": SHEPARD_API_KEY,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status in (200, 201)
    except urllib.error.HTTPError as e:
        preview = e.read().decode("utf-8", errors="replace")[:200]
        log.warning("Ingest %s → HTTP %s: %s", sensor.entity_id, e.code, preview)
        return False
    except Exception as e:
        log.warning("Ingest %s → %s", sensor.entity_id, e)
        return False


# ---------------------------------------------------------------------------
# Poll loop

def run_poll_loop(container_ids: dict[str, int]) -> None:
    log.info(
        "Starting HA REST poll loop — interval=%ds, HA=%s:%s, %d sensors",
        POLL_INTERVAL_SEC, HA_HOST, HA_PORT, len(SENSORS),
    )
    while True:
        cycle_start = time.monotonic()
        timestamp_ns = time.time_ns()
        ok = 0
        skipped = 0
        for sensor in SENSORS:
            cid = container_ids.get(sensor.container)
            if cid is None:
                log.warning("Unknown container %r for %s — skipping", sensor.container, sensor.entity_id)
                skipped += 1
                continue
            value = _ha_get_state(sensor.entity_id)
            if value is None:
                skipped += 1
                continue
            if _shepard_ingest(cid, sensor, timestamp_ns, value):
                ok += 1
            else:
                skipped += 1
        log.info("Poll cycle: %d/%d sensors ingested (%.1fs)",
                 ok, len(SENSORS), time.monotonic() - cycle_start)
        # sleep the remainder of the interval
        elapsed = time.monotonic() - cycle_start
        sleep_for = max(0.0, POLL_INTERVAL_SEC - elapsed)
        time.sleep(sleep_for)


# ---------------------------------------------------------------------------
# Main

def main(argv: Optional[list[str]] = None) -> int:
    if not HA_TOKEN:
        log.error(
            "HA_TOKEN is empty — set it to a long-lived Home Assistant access token. "
            "Exiting clean so the container does not crashloop."
        )
        return 0

    if not SHEPARD_API_KEY:
        log.error("SHEPARD_API_KEY is empty — cannot ingest data. Exiting.")
        return 1

    # Load the seeded container IDs written by seed.py
    for attempt in range(30):
        try:
            with open(LOOKUP_PATH) as f:
                lookup = json.load(f)
            break
        except FileNotFoundError:
            log.info("Waiting for seeded.json at %s (attempt %d/30) …", LOOKUP_PATH, attempt + 1)
            time.sleep(10)
    else:
        log.error("seeded.json not found after 300 s — seed.py did not complete?")
        return 1

    container_ids: dict[str, int] = lookup.get("container_ids", {})
    log.info("Loaded container map: %s", container_ids)

    try:
        run_poll_loop(container_ids)
    except KeyboardInterrupt:
        log.info("Interrupted — exiting.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
