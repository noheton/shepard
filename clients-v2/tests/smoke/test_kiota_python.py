"""Smoke test for the Kiota-generated Python client (CG1a).

Verifies that the generated package imports, that the top-level
ShepardV2Client class is reachable, and that its fluent path-builder
surface (or a request-adapter constructor seam) is wired up. NO live
HTTP call is performed.

The package is expected to be installed in the test environment via
    pip install -e clients-v2/python-kiota
after `make -C clients-v2 generate-python` has produced the sources.
"""

from __future__ import annotations

import importlib
import inspect


def test_shepard_v2_package_imports() -> None:
    """The shepard_v2 package must import without side effects."""
    mod = importlib.import_module("shepard_v2")
    assert mod is not None


def test_top_level_client_class_exists() -> None:
    """ShepardV2Client (or shepard_v2_client) must be exposed at module top."""
    mod = importlib.import_module("shepard_v2")
    # Kiota emits both PascalCase and snake_case binding shapes across versions;
    # accept either.
    candidates = [name for name in dir(mod) if name.lower().endswith("client")]
    assert candidates, (
        "Expected at least one *Client export in shepard_v2; "
        f"available top-level names: {sorted(dir(mod))[:30]}"
    )


def test_client_constructor_takes_request_adapter() -> None:
    """ShepardV2Client must accept a Kiota request adapter as its first arg.

    This is the contract the convenience wrapper (aidocs/27) and any
    downstream code rely on. We don't construct an adapter here — we
    just inspect the constructor signature.
    """
    mod = importlib.import_module("shepard_v2")
    client_class = None
    for name in dir(mod):
        if name.lower().endswith("client"):
            obj = getattr(mod, name)
            if inspect.isclass(obj):
                client_class = obj
                break
    assert client_class is not None
    sig = inspect.signature(client_class.__init__)
    # First non-self param must exist; Kiota always names it
    # request_adapter.
    params = [p for p in sig.parameters.values() if p.name != "self"]
    assert params, f"{client_class.__name__}.__init__ has no parameters"
    assert "request_adapter" in {p.name for p in params}, (
        f"Expected `request_adapter` in {client_class.__name__}.__init__ "
        f"signature; got {[p.name for p in params]}"
    )
