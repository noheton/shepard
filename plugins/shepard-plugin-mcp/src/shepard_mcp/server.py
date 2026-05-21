"""
shepard-plugin-mcp — FastMCP server exposing Shepard to AI agents.

Run:
    python -m shepard_mcp.server
or:
    shepard-mcp

Environment:
    SHEPARD_API_BASE  Base URL of the Shepard backend (default: http://localhost:8080)
    PORT              Listening port (default: 8811)
"""

from __future__ import annotations

import logging
import os
import threading
import time

import httpx
from fastmcp import FastMCP

from shepard_mcp import plugin_state
from shepard_mcp.tools import collections as collections_tools
from shepard_mcp.tools import timeseries as timeseries_tools

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
log = logging.getLogger("shepard-mcp")

_CAPABILITIES_POLL_INTERVAL = 30  # seconds


def _refresh_enabled_state() -> None:
    """Poll /v2/instance/capabilities and update plugin_state.enabled."""
    base = os.environ.get("SHEPARD_API_BASE", "http://localhost:8080")
    try:
        resp = httpx.get(f"{base}/v2/instance/capabilities", timeout=5)
        resp.raise_for_status()
        plugins = resp.json().get("plugins", [])
        now_enabled = any(p.get("id") == "mcp" for p in plugins)
        if now_enabled != plugin_state.enabled:
            plugin_state.enabled = now_enabled
            log.info("MCP plugin state changed → %s", "ENABLED" if now_enabled else "DISABLED")
        else:
            log.debug("MCP plugin state: %s", "ENABLED" if now_enabled else "DISABLED")
    except Exception as exc:
        log.warning("Failed to refresh plugin state from %s: %s", base, exc)


def _start_state_poller() -> None:
    """Refresh on startup, then poll every 30 s in a daemon thread."""
    _refresh_enabled_state()

    def _loop() -> None:
        while True:
            time.sleep(_CAPABILITIES_POLL_INTERVAL)
            _refresh_enabled_state()

    t = threading.Thread(target=_loop, daemon=True, name="mcp-plugin-state-poller")
    t.start()

mcp = FastMCP(
    name="Shepard",
    instructions=(
        "You are connected to a Shepard research data management instance. "
        "Shepard organises scientific and engineering data as Collections → DataObjects → Containers.\n\n"
        "Key concepts:\n"
        "- Collection: a project or campaign (e.g. 'LUMEN Engine Test Campaign 2024')\n"
        "- DataObject: one experimental unit (e.g. 'TR-004 — Hot-fire run')\n"
        "- Container: payload attached to a DataObject:\n"
        "  • timeseries — high-rate sensor data (vibration, pressure, temperature, …)\n"
        "  • files      — lab reports, NDT scans, images, PDFs\n"
        "  • structuredData — JSON test parameters or run configs\n"
        "- Predecessor / Successor chain: the causal history of an experiment\n"
        "  (TR-004 anomaly → investigation sub-object → TR-005 hold → TR-006 re-test)\n\n"
        "Typical workflow for anomaly investigation:\n"
        "1. list_collections() — find the campaign\n"
        "2. list_data_objects(collectionAppId) — find the test run\n"
        "3. get_data_object(collectionAppId, dataObjectAppId) — get containers + chain\n"
        "4. list_channels(containerId) — see what sensors are available\n"
        "5. get_channel_data(..., measurement='vibration', field='rms_g') — get data\n"
        "6. compare_channels(..., measurements=['vibration','thrust']) — causal analysis\n"
        "7. get_successor_chain(...) — see what happened after the anomaly\n\n"
        "All IDs come in two forms:\n"
        "- appId (UUID v7 string): used by v2 API paths\n"
        "- id (integer): used by v1 API paths and some v2 stats endpoints\n"
        "get_data_object returns both forms in the response."
    ),
)

# Register tool groups
collections_tools.register(mcp)
timeseries_tools.register(mcp)

_start_state_poller()
log.info(
    "Shepard MCP server ready — plugin %s. Backend: %s",
    "ENABLED" if plugin_state.enabled else "DISABLED",
    os.environ.get("SHEPARD_API_BASE", "http://localhost:8080"),
)


def main() -> None:
    port = int(os.environ.get("PORT", "8811"))
    mcp.run(transport="sse", host="0.0.0.0", port=port)


if __name__ == "__main__":
    main()
