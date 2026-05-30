"""Uvicorn entry point.

Run with::

    python -m krl_interpreter.sidecar

Reads ``PORT`` + ``HOST`` from env (see :mod:`config`).
"""

from __future__ import annotations

import os

import uvicorn


def main() -> None:  # pragma: no cover - thin shim
    host = os.getenv("HOST", "0.0.0.0")
    port = int(os.getenv("PORT", "8000"))
    uvicorn.run(
        "krl_interpreter.sidecar.app:app",
        host=host,
        port=port,
        log_level=os.getenv("LOG_LEVEL", "info"),
    )


if __name__ == "__main__":  # pragma: no cover
    main()
