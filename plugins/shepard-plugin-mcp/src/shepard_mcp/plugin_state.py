"""Runtime enabled/disabled state for the MCP plugin sidecar.

Polled from GET /v2/instance/capabilities every 30 seconds by server.py.
All tool calls check `enabled` before forwarding requests to the backend.
"""

enabled: bool = True
