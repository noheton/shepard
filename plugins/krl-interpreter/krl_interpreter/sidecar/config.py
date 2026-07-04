"""Env-driven sidecar settings.

Tier-1 (KRL-INTERPRETER-04) settings are deploy-time-only environment
variables — the runtime-mutable ``:KrlInterpreterConfig`` Neo4j
singleton (per ``## Always: surface operator knobs in the admin
config``) is deferred to **KRL-CONFIG-1** (filed in
``aidocs/16-dispatcher-backlog.md`` as part of this row).

Env vars:

==========================  ================================================
Variable                    Meaning
==========================  ================================================
``PORT``                    HTTP port (default ``8000``).
``HOST``                    Bind host (default ``0.0.0.0``).
``MAX_BODY_SIZE``           Max ``Content-Length`` for ``/interpret``;
                            informational here, enforced by the reverse
                            proxy in front of the sidecar.
``KRL_IK_TOLERANCE``        Default IK tolerance in metres; overridable
                            per-request via ``options.ikTolerance``.
``KRL_MAX_ITERATIONS``      Default IK iteration cap; informational on
                            ikpy 3.4 (see ``ik/solver.py`` module doc).
``KRL_TIME_STEP_DEFAULT``   Default trajectory sample interval (s).
``KRL_MOTION_DURATION``     Tier-1 fixed per-motion duration (s); the
                            number of trajectory samples emitted per
                            motion is ``ceil(duration / time_step)``.
``KRL_MAX_IR_ITERATIONS``   Safety cap on ``WHILE`` / ``LOOP`` unrolling.
==========================  ================================================
"""

from __future__ import annotations

import os
from dataclasses import dataclass


def _env_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or raw == "":
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw == "":
        return default
    try:
        return int(raw)
    except ValueError:
        return default


@dataclass(frozen=True)
class SidecarSettings:
    """Resolved environment-driven settings.

    Built once per process; the FastAPI app reads it on startup. The
    instance is intentionally frozen — admins flipping a knob restart
    the container (deploy-time-only at tier-1).
    """

    port: int
    host: str
    max_body_size: int
    default_ik_tolerance: float
    default_max_iterations: int
    default_time_step: float
    default_motion_duration: float
    max_ir_iterations: int

    @classmethod
    def from_env(cls) -> "SidecarSettings":
        return cls(
            port=_env_int("PORT", 8000),
            host=os.getenv("HOST", "0.0.0.0"),
            max_body_size=_env_int("MAX_BODY_SIZE", 5 * 1024 * 1024),
            default_ik_tolerance=_env_float("KRL_IK_TOLERANCE", 1e-3),
            default_max_iterations=_env_int("KRL_MAX_ITERATIONS", 100),
            default_time_step=_env_float("KRL_TIME_STEP_DEFAULT", 0.01),
            default_motion_duration=_env_float("KRL_MOTION_DURATION", 1.0),
            max_ir_iterations=_env_int("KRL_MAX_IR_ITERATIONS", 100000),
        )
