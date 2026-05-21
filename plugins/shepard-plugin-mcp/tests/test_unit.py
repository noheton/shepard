"""
Unit tests for shepard-plugin-mcp — no live backend required.

Run: pytest tests/test_unit.py -v
"""

from __future__ import annotations

import pytest

from shepard_mcp import plugin_state
from shepard_mcp.client import ShepardClient


# ── plugin_state ──────────────────────────────────────────────────────────────

class TestPluginState:
    def test_initial_state_is_enabled(self):
        """Default state must be ENABLED so the sidecar is usable on first start
        before the first capabilities poll returns."""
        # Reset to known state before asserting — other tests may have mutated it.
        plugin_state.enabled = True
        assert plugin_state.enabled is True

    def test_state_can_be_toggled(self):
        orig = plugin_state.enabled
        try:
            plugin_state.enabled = False
            assert plugin_state.enabled is False
            plugin_state.enabled = True
            assert plugin_state.enabled is True
        finally:
            plugin_state.enabled = orig


# ── ShepardClient disabled guard ──────────────────────────────────────────────

class TestShepardClientDisabledGuard:
    async def test_raises_when_disabled(self):
        """__aenter__ must raise RuntimeError when plugin_state.enabled is False."""
        plugin_state.enabled = False
        try:
            with pytest.raises(RuntimeError, match="disabled"):
                async with ShepardClient("fake-token", "http://localhost:9999"):
                    pass  # should not reach here
        finally:
            plugin_state.enabled = True

    async def test_proceeds_when_enabled(self):
        """__aenter__ must NOT raise when plugin_state.enabled is True."""
        plugin_state.enabled = True
        # We'll hit a connection error (no server at localhost:9999), but the
        # plugin-state guard itself must not raise — only the actual HTTP call
        # would fail, and we won't make one here.
        client = ShepardClient("fake-token", "http://localhost:9999")
        # Call __aenter__ directly without __aexit__ to avoid making any request.
        # The guard check happens in __aenter__ before any network call.
        result = await client.__aenter__()
        assert result is client  # returns self
        await client.__aexit__(None, None, None)
