"""Pydantic v2 request / response models for the sidecar REST API.

Shape mirrors ``aidocs/integrations/117-krl-interpreter.md §6``. The
backend (KRL-INTERPRETER-05) is the upstream caller that resolves
``srcFileAppId`` / ``urdfFileAppId`` to actual file contents + paths
before posting to ``POST /interpret``; the sidecar itself only sees
``srcText`` + ``urdfPath`` (it does not reach into Shepard's storage).
"""

from __future__ import annotations

from typing import Any, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator

INTERPRETER_VERSION = "0.1.0"


class FramePayload(BaseModel):
    """Cartesian frame (metres, radians).

    KRL frames are in **millimetres**; the backend (-05) is responsible
    for converting before invoking the sidecar, OR the sidecar's
    composer applies a unit conversion at the IR -> IK boundary. Tier-1
    contract: the values on the wire are already the unit the IK solver
    expects (metres + radians).
    """

    model_config = ConfigDict(extra="forbid")

    x: float = 0.0
    y: float = 0.0
    z: float = 0.0
    rx: float = 0.0
    ry: float = 0.0
    rz: float = 0.0


class InterpretOptions(BaseModel):
    """Tunable interpreter knobs. All optional; defaults applied by the
    composer if unset."""

    model_config = ConfigDict(extra="forbid")

    maxIterations: int = Field(100, ge=1, le=10000)
    ikTolerance: float = Field(1e-3, gt=0)
    timeStep: Optional[float] = Field(default=None, gt=0)
    motionDuration: float = Field(1.0, gt=0)
    maxIrIterations: int = Field(100000, ge=1)
    bcoAsWait: bool = True


class InterpretRequest(BaseModel):
    """``POST /interpret`` body. Mirrors §6 of the design doc."""

    model_config = ConfigDict(extra="forbid")

    srcText: str
    datText: Optional[str] = None
    urdfPath: str
    baseFrame: Optional[FramePayload] = None
    toolFrame: Optional[FramePayload] = None
    seedPose: Optional[List[float]] = None
    timeStep: float = Field(0.01, gt=0)
    options: InterpretOptions = Field(default_factory=InterpretOptions)

    @field_validator("srcText")
    @classmethod
    def _src_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("srcText must not be empty")
        return v

    @field_validator("urdfPath")
    @classmethod
    def _urdf_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("urdfPath must not be empty")
        return v


class TrajectorySample(BaseModel):
    """One emitted joint-trajectory sample."""

    model_config = ConfigDict(extra="forbid")

    t: float
    joints: List[float]


class InterpretWarning(BaseModel):
    """Non-fatal interpreter condition (parser warning, IK unreachable,
    static-bound failure, ...)."""

    model_config = ConfigDict(extra="forbid")

    line: int = 0
    message: str
    severity: Literal["warning", "error"] = "warning"


class InterpretUnsupported(BaseModel):
    """Structured surface of tier-1 unsupported KRL constructs."""

    # ``protected_namespaces=()`` silences pydantic v2's warning about the
    # field name ``construct`` shadowing a BaseModel attribute. The field
    # name is part of the §6 wire contract and cannot change.
    model_config = ConfigDict(extra="forbid", protected_namespaces=())

    construct: str
    line: int = 0
    reason: str = ""


class IkSolverStats(BaseModel):
    """Per-program IK aggregate stats."""

    model_config = ConfigDict(extra="forbid")

    meanCycleMs: float = 0.0
    maxResidual: float = 0.0
    failedPoses: int = 0
    totalPoses: int = 0


class InterpretResponse(BaseModel):
    """``POST /interpret`` response. Mirrors §6 of the design doc."""

    model_config = ConfigDict(extra="forbid")

    trajectory: List[TrajectorySample]
    warnings: List[InterpretWarning] = Field(default_factory=list)
    unsupportedConstructs: List[InterpretUnsupported] = Field(default_factory=list)
    ikSolverStats: IkSolverStats = Field(default_factory=IkSolverStats)
    interpreterVersion: str = INTERPRETER_VERSION


class HealthResponse(BaseModel):
    """``GET /health`` response."""

    model_config = ConfigDict(extra="forbid")

    status: Literal["ok"] = "ok"
    version: str = INTERPRETER_VERSION


class JobStubResponse(BaseModel):
    """``POST /interpret/async`` placeholder body (tier-1 returns 501)."""

    model_config = ConfigDict(extra="forbid")

    detail: str
    deferredTo: str = "KRL-INTERPRETER-04-ASYNC (tier-2)"


# Convenience: anything that can convert to a FramePayload.
FrameInput = Any
