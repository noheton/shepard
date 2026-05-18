"""home-showcase collector — long-lived MQTT → shepard timeseries bridge.

Subscribes to the Home Assistant MQTT broker, parses zigbee2mqtt device
state messages, and POSTs the numeric values to shepard's TimescaleDB-
backed timeseries containers (created by `seed.py`).

Auth posture:
- MQTT: env vars MQTT_HOST / MQTT_PORT / MQTT_USER / MQTT_PASSWORD.
  TLS is optional via MQTT_TLS=1; cert verification is off by default
  (set MQTT_TLS_VERIFY=1 for production deploys). A WARN is logged at
  startup when running plain or no-verify so the security posture is
  visible.
- shepard: SHEPARD_API + SHEPARD_API_KEY (apikey header). The upstream
  client speaks the /shepard/api/ v1 surface — same as the LUMEN seed.
  Once V2BASE phase B lands for timeseries endpoints this same wire
  becomes the integration smoke for the compat layer.

The collector is stateless aside from a small in-memory batch buffer
flushed every BATCH_INTERVAL_SEC seconds (default 30). Restart is
zero-pain — there's no on-disk state to corrupt.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import re
import signal
import ssl
import sys
import threading
import time
from collections import defaultdict
from dataclasses import dataclass
from typing import Any, Optional

import paho.mqtt.client as mqtt  # type: ignore


# ---------------------------------------------------------------------------
# Configuration

logging.basicConfig(
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    level=os.environ.get("LOG_LEVEL", "INFO"),
)
log = logging.getLogger("home-collector")

# Map MQTT fields → measurement class. The class is the channel's
# `measurement` segment in the shepard 5-tuple key.
ENERGY_FIELDS = {"power", "energy", "voltage", "current"}
ENVIRONMENT_FIELDS = {"temperature", "humidity", "illuminance", "pressure"}
# All other numeric fields are ignored (linkquality, battery, last_seen
# epoch ints, motor_state, action_duration — diagnostics noise).

# Which container takes which measurement class.
CONTAINER_BY_CLASS = {
    "power": "home-energy-appliances",
    "energy": "home-energy-appliances",
    "voltage": "home-energy-appliances",
    "current": "home-energy-appliances",
    "temperature": "home-environment",
    "humidity": "home-environment",
    "illuminance": "home-environment",
    "pressure": "home-environment",
}

BATCH_INTERVAL_SEC = int(os.environ.get("BATCH_INTERVAL_SEC", "30"))
LOOKUP_PATH = os.environ.get("HOME_SHOWCASE_LOOKUP_PATH",
                             "/opt/home-showcase/seeded.json")


# ---------------------------------------------------------------------------
# Helpers


def _import_client_or_die():
    try:
        from shepard_client import (  # type: ignore
            ApiClient,
            Configuration,
            TimeseriesContainerApi,
            TimeseriesDataPoint,
        )
    except Exception as e:  # pragma: no cover
        log.error("shepard_client import failed: %s", e)
        sys.exit(2)
    return ApiClient, Configuration, TimeseriesContainerApi, TimeseriesDataPoint


def _now_ns() -> int:
    return time.time_ns()


def _room_of(friendly_name: str) -> str:
    """zigbee2mqtt device names like 'Wohnzimmer Rolladen' embed the
    room as the first word. Strip non-letters as a soft normaliser."""
    head = friendly_name.split(" ", 1)[0] if friendly_name else ""
    return re.sub(r"[^A-Za-zÀ-ſ]", "", head) or "unknown"


# Backend validates 5-tuple key segments against a banlist that rejects
# spaces, commas, and a few other delimiters. Map disallowed chars to "_"
# so zigbee2mqtt friendly names like "Wohnzimmer Rolladen" round-trip.
_FORBIDDEN_IN_KEY = re.compile(r"[\s,.;:'\"|/\\=\?\&#@<>]")


def _sanitize_key_segment(s: str) -> str:
    if not s:
        return "_"
    s = _FORBIDDEN_IN_KEY.sub("_", s)
    # Collapse repeated underscores so "  " doesn't become "__"
    s = re.sub(r"_+", "_", s).strip("_")
    return s or "_"


def _ieee_address(payload: dict) -> Optional[str]:
    """zigbee2mqtt friendly_name → ieee_address. Some payloads include it;
    others don't. Falls back to the friendly_name slug."""
    v = payload.get("ieee_address")
    if isinstance(v, str) and v.startswith("0x"):
        return v
    return None


# ---------------------------------------------------------------------------
# State


class CollectorState:
    """Batch buffer + lookup table. Thread-safe."""

    def __init__(self, lookup: dict[str, Any]):
        self.collection_id: int = lookup["collection_id"]
        self.container_ids: dict[str, int] = lookup["container_ids"]
        # buffer[container_name] -> list of (channel_key_tuple, value_dict)
        self._buf: dict[str, list[tuple[tuple, dict]]] = defaultdict(list)
        self._lock = threading.Lock()
        self.message_count = 0
        self.flush_count = 0
        self.last_flush_ts = time.time()

    def add(self, container: str, channel: tuple, point: dict) -> None:
        with self._lock:
            self._buf[container].append((channel, point))
            self.message_count += 1

    def drain(self) -> dict[str, list[tuple[tuple, dict]]]:
        with self._lock:
            out = dict(self._buf)
            self._buf.clear()
            return out


# ---------------------------------------------------------------------------
# MQTT


def on_connect(client, userdata, flags, reason_code, properties=None):
    log.info("MQTT connected (rc=%s)", reason_code)
    client.subscribe("zigbee2mqtt/+", qos=0)
    log.info("Subscribed: zigbee2mqtt/+")


def on_message(client, state: CollectorState, msg):
    topic = msg.topic
    try:
        payload = json.loads(msg.payload.decode("utf-8"))
    except Exception:
        return
    if not isinstance(payload, dict):
        return

    # topic shape: zigbee2mqtt/<friendly_name>
    parts = topic.split("/", 1)
    if len(parts) != 2:
        return
    friendly = parts[1]
    if friendly in {"bridge", ""}:
        return
    ieee = _ieee_address(payload) or friendly
    location = _room_of(friendly)
    ts_ns = _now_ns()

    for field, value in payload.items():
        if not isinstance(value, (int, float)):
            continue
        if isinstance(value, bool):
            continue
        # Skip diagnostics noise
        if field == "linkquality" or field == "battery":
            continue
        # Map to a known class
        if field in ENERGY_FIELDS:
            cls = field
        elif field in ENVIRONMENT_FIELDS:
            cls = field
        else:
            continue
        container_name = CONTAINER_BY_CLASS.get(cls)
        if not container_name:
            continue
        channel = (
            _sanitize_key_segment(cls),       # measurement
            _sanitize_key_segment(friendly),  # device — friendly names contain spaces, sanitise
            _sanitize_key_segment(location),  # location
            _sanitize_key_segment(ieee),      # symbolicName
            "value",                          # field
        )
        state.add(container_name, channel, {"timestamp": ts_ns, "value": float(value)})


# ---------------------------------------------------------------------------
# Flush

def flush_loop(
    state: CollectorState,
    ts_api,  # unused — kept for backwards compatibility
    TimeseriesDataPoint,  # unused
    stop_event: threading.Event,
    shepard_api: str,
    api_key: str,
) -> None:
    """Direct urllib POST to /timeseriesContainers/{id}/payload — bypasses
    the upstream client's `cfg.api_key["apikey"]` mapping which on this
    deploy was producing a header the backend rejected. Backend expects
    `X-API-KEY` exactly; sending that explicitly works."""
    import urllib.request
    import urllib.error

    while not stop_event.wait(BATCH_INTERVAL_SEC):
        batches = state.drain()
        if not batches:
            continue
        for container_name, items in batches.items():
            cid = state.container_ids.get(container_name)
            if cid is None:
                log.warning("No container id for %s — dropping %d points",
                            container_name, len(items))
                continue
            grouped: dict[tuple, list] = defaultdict(list)
            for (channel, point) in items:
                grouped[channel].append(point)

            ok_blocks = 0
            ok_points = 0
            for (meas, dev, loc, sym, fld), points in grouped.items():
                body = json.dumps({
                    "timeseries": {
                        "measurement": meas,
                        "device": dev,
                        "location": loc,
                        "symbolicName": sym,
                        "field": fld,
                        "valueType": "Float",
                    },
                    "points": [{"timestamp": p["timestamp"], "value": p["value"]} for p in points],
                }).encode("utf-8")
                req = urllib.request.Request(
                    f"{shepard_api.rstrip('/')}/timeseriesContainers/{cid}/payload",
                    data=body,
                    headers={
                        "X-API-KEY": api_key,
                        "Content-Type": "application/json",
                    },
                    method="POST",
                )
                try:
                    with urllib.request.urlopen(req, timeout=15) as resp:
                        if resp.status in (200, 201):
                            ok_blocks += 1
                            ok_points += len(points)
                except urllib.error.HTTPError as e:
                    body_preview = e.read().decode("utf-8", errors="replace")[:200]
                    log.warning("Flush %s/%s/%s -> HTTP %s: %s",
                                container_name, meas, fld, e.code, body_preview)
                except Exception as e:
                    log.warning("Flush %s/%s/%s failed: %s",
                                container_name, meas, fld, e)
            if ok_blocks:
                state.flush_count += 1
                log.info("Flushed %d points across %d/%d channels to %s (cid=%s)",
                         ok_points, ok_blocks, len(grouped), container_name, cid)
        state.last_flush_ts = time.time()


# ---------------------------------------------------------------------------
# Main

def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--mqtt-host", default=os.environ.get("MQTT_HOST"))
    ap.add_argument("--mqtt-port", type=int,
                    default=int(os.environ.get("MQTT_PORT", "1883")))
    ap.add_argument("--mqtt-user", default=os.environ.get("MQTT_USER"))
    ap.add_argument("--mqtt-password", default=os.environ.get("MQTT_PASSWORD"))
    ap.add_argument("--mqtt-tls", action="store_true",
                    default=os.environ.get("MQTT_TLS", "0") == "1")
    ap.add_argument("--mqtt-tls-verify", action="store_true",
                    default=os.environ.get("MQTT_TLS_VERIFY", "0") == "1")
    ap.add_argument("--shepard-api",
                    default=os.environ.get("SHEPARD_API",
                                           "http://backend:8080/shepard/api"))
    ap.add_argument("--shepard-apikey", default=os.environ.get("SHEPARD_API_KEY"))
    args = ap.parse_args(argv)

    if not args.mqtt_host or not args.mqtt_user or not args.mqtt_password:
        ap.error("set MQTT_HOST / MQTT_USER / MQTT_PASSWORD")
    if not args.shepard_apikey:
        ap.error("set SHEPARD_API_KEY")

    if not args.mqtt_tls:
        log.warning("MQTT plain (no TLS) — only acceptable on a trusted LAN.")
    elif not args.mqtt_tls_verify:
        log.warning("MQTT TLS without certificate verification — only acceptable "
                    "on a trusted LAN. Set MQTT_TLS_VERIFY=1 in production.")

    # Load the seeded lookup table.
    try:
        with open(LOOKUP_PATH) as f:
            lookup = json.load(f)
    except Exception as e:
        log.error("Failed to load lookup table at %s: %s", LOOKUP_PATH, e)
        log.error("Did the home-showcase seed run? Re-run it before starting the collector.")
        return 1

    ApiClient, Configuration, TimeseriesContainerApi, TimeseriesDataPoint = (
        _import_client_or_die()
    )
    cfg = Configuration(host=args.shepard_api)
    cfg.api_key["apikey"] = args.shepard_apikey
    ts_api = TimeseriesContainerApi(ApiClient(cfg))

    state = CollectorState(lookup)
    stop_event = threading.Event()

    # MQTT client
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        userdata=state,
    )
    client.username_pw_set(args.mqtt_user, args.mqtt_password)
    if args.mqtt_tls:
        ctx = ssl.create_default_context()
        if not args.mqtt_tls_verify:
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
        client.tls_set_context(ctx)
    client.on_connect = on_connect
    client.on_message = on_message

    log.info("Connecting to MQTT %s:%s ...", args.mqtt_host, args.mqtt_port)
    client.connect(args.mqtt_host, args.mqtt_port, keepalive=60)

    # Flush thread
    flush_thread = threading.Thread(
        target=flush_loop,
        args=(state, ts_api, TimeseriesDataPoint, stop_event,
              args.shepard_api, args.shepard_apikey),
        daemon=True,
    )
    flush_thread.start()

    # Signal handling
    def _sigterm(_signum, _frame):
        log.info("SIGTERM — stopping")
        stop_event.set()
        client.disconnect()
    signal.signal(signal.SIGTERM, _sigterm)
    signal.signal(signal.SIGINT, _sigterm)

    # Run the MQTT network loop in the main thread
    try:
        client.loop_forever()
    finally:
        stop_event.set()
    log.info("Collector exited. messages=%d flushes=%d",
             state.message_count, state.flush_count)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
