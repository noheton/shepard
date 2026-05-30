"""FastAPI app for the KRL interpreter sidecar.

Endpoints
---------
* ``POST /interpret`` — synchronous interpret: parse + IK + emit
  trajectory. Returns :class:`InterpretResponse` on 200.
* ``POST /interpret/async`` — **DEFERRED** to tier-2. Returns 501.
* ``GET /interpret/jobs/{jobId}`` — **DEFERRED** to tier-2. Returns 501.
* ``GET /health`` — health probe (compose healthcheck consumes this).

All responses carry the ``X-KRL-Interpreter-Version`` header per
``aidocs/integrations/117 §6``.
"""

from __future__ import annotations

import logging
from typing import Optional

import numpy as np
from fastapi import FastAPI, HTTPException, Request, Response
from scipy.spatial.transform import Rotation

from krl_interpreter import parse
from krl_interpreter.errors import ParseError, Severity
from krl_interpreter.ik import IkSolver
from krl_interpreter.sidecar.composer import (
    Composer,
    ComposerOptions,
    _frame_from_payload,
)
from krl_interpreter.sidecar.config import SidecarSettings
from krl_interpreter.sidecar.schemas import (
    HealthResponse,
    IkSolverStats,
    INTERPRETER_VERSION,
    InterpretRequest,
    InterpretResponse,
    InterpretUnsupported,
    InterpretWarning,
    JobStubResponse,
    TrajectorySample,
)

logger = logging.getLogger(__name__)

SETTINGS = SidecarSettings.from_env()

app = FastAPI(
    title="shepard-plugin-krl-interpreter",
    description=(
        "Offline KUKA KRL interpreter sidecar. Parses .src/.dat, IK-solves "
        "against a URDF, emits a joint trajectory. See "
        "aidocs/integrations/117-krl-interpreter.md §6 for the contract."
    ),
    version=INTERPRETER_VERSION,
)


@app.middleware("http")
async def _version_header(request: Request, call_next):
    """Stamp every response with the interpreter version header."""
    response: Response = await call_next(request)
    response.headers["X-KRL-Interpreter-Version"] = INTERPRETER_VERSION
    return response


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """Liveness probe. Compose healthcheck reads this."""
    return HealthResponse()


@app.post("/interpret", response_model=InterpretResponse)
async def interpret(body: InterpretRequest) -> InterpretResponse:
    """Parse + IK-solve a KRL program; return the joint trajectory."""

    # 1. Parse.
    try:
        parse_result = parse(body.srcText)
    except ParseError as e:
        # Hard parse failure → 400. The composer can't run.
        raise HTTPException(
            status_code=400,
            detail=f"KRL parse error at line {e.line}, col {e.column}: {e.message}",
        )

    # 2. Build the IK solver.
    try:
        solver = IkSolver(body.urdfPath)
    except FileNotFoundError as e:
        raise HTTPException(status_code=400, detail=f"URDF not found: {e}")
    except Exception as e:  # pragma: no cover - defensive
        raise HTTPException(status_code=400, detail=f"URDF load failed: {e}")

    # 3. Compose.
    options = ComposerOptions(
        time_step=body.timeStep,
        motion_duration=body.options.motionDuration,
        max_iterations=body.options.maxIterations,
        ik_tolerance=body.options.ikTolerance,
        max_ir_iterations=body.options.maxIrIterations,
    )

    base_matrix: Optional[np.ndarray] = None
    if body.baseFrame is not None:
        base_matrix = _frame_from_payload(
            body.baseFrame.x,
            body.baseFrame.y,
            body.baseFrame.z,
            body.baseFrame.rx,
            body.baseFrame.ry,
            body.baseFrame.rz,
        )

    tool_matrix: Optional[np.ndarray] = None
    if body.toolFrame is not None:
        tool_matrix = _frame_from_payload(
            body.toolFrame.x,
            body.toolFrame.y,
            body.toolFrame.z,
            body.toolFrame.rx,
            body.toolFrame.ry,
            body.toolFrame.rz,
        )

    composer = Composer(
        solver=solver,
        options=options,
        base_frame=base_matrix,
        tool_frame=tool_matrix,
        initial_seed=body.seedPose,
    )
    result = composer.compose(parse_result.program)

    # 4. Surface parser warnings ahead of composer warnings (chronological).
    response_warnings = [
        InterpretWarning(
            line=w.line,
            message=w.message,
            severity="error" if w.severity == Severity.ERROR else "warning",
        )
        for w in parse_result.warnings
    ]
    response_warnings.extend(
        InterpretWarning(line=w.line, message=w.message, severity=w.severity)
        for w in result.warnings
    )

    # 5. Unsupported constructs: parser-extracted + composer-passed-through.
    unsupported_list = [
        InterpretUnsupported(construct=u.construct, line=u.line, reason=u.reason)
        for u in parse_result.unsupported
    ]
    unsupported_list.extend(
        InterpretUnsupported(construct=u.construct, line=u.line, reason=u.reason)
        for u in result.unsupported_constructs
    )

    stats = IkSolverStats(
        meanCycleMs=result.mean_cycle_ms,
        maxResidual=result.max_residual,
        failedPoses=result.failed_poses,
        totalPoses=result.total_poses,
    )

    return InterpretResponse(
        trajectory=[
            TrajectorySample(t=p.t, joints=list(p.joints)) for p in result.trajectory
        ],
        warnings=response_warnings,
        unsupportedConstructs=unsupported_list,
        ikSolverStats=stats,
    )


# ----------------------------------------------------------------- #
# Async stub — DEFERRED to tier-2.
#
# The async pattern (POST /interpret/async returning 202 + jobId; GET
# /interpret/jobs/{jobId} polling) is documented in
# aidocs/integrations/117 §6 but not implemented at tier-1. Operators
# with large KRL programs (1000s of motions) should still go through
# /interpret synchronously; the sidecar's IK is fast enough on a
# 6-DOF arm (~12 ms / pose, see ik/solver.py module doc) that even a
# 5000-pose program completes in ~60 s.
#
# Both stubs return 501 with a structured body so a frontend can
# display a "feature deferred" message rather than a raw 501 page.
# ----------------------------------------------------------------- #


@app.post("/interpret/async", response_model=JobStubResponse, status_code=501)
async def interpret_async(_: InterpretRequest) -> JobStubResponse:
    """Async interpret — **DEFERRED** to tier-2 (KRL-INTERPRETER-04-ASYNC)."""
    raise HTTPException(
        status_code=501,
        detail={
            "detail": (
                "Async interpret is deferred to tier-2; use POST /interpret "
                "synchronously. See aidocs/integrations/117 §6."
            ),
            "deferredTo": "KRL-INTERPRETER-04-ASYNC (tier-2)",
        },
    )


@app.get("/interpret/jobs/{job_id}", response_model=JobStubResponse, status_code=501)
async def interpret_job_poll(job_id: str) -> JobStubResponse:
    """Async job poll — **DEFERRED** to tier-2."""
    _ = job_id
    raise HTTPException(
        status_code=501,
        detail={
            "detail": (
                "Async job polling is deferred to tier-2; use POST /interpret "
                "synchronously. See aidocs/integrations/117 §6."
            ),
            "deferredTo": "KRL-INTERPRETER-04-ASYNC (tier-2)",
        },
    )


# Re-export for tests / introspection.
__all__ = ["app", "SETTINGS"]
_ = Rotation  # keep scipy import warm for downstream consumers
