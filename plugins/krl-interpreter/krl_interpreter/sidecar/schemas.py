"""Pydantic v2 request / response models for the sidecar REST API.

Shape mirrors ``aidocs/integrations/117-krl-interpreter.md §6``. The
backend (KRL-INTERPRETER-05) is the upstream caller that resolves
``srcFileAppId`` / ``urdfFileAppId`` to actual file contents + paths
before posting to ``POST /interpret``; the sidecar itself only sees
``srcText`` + ``urdfPath`` (it does not reach into Shepard's storage).
"""

from __future__ import annotations

from typing import Any, List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

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
    """``POST /interpret`` body. Originally §6 of the design doc spec'd
    ``srcText`` (plaintext) + ``urdfPath`` (disk-path) — but the backend
    runs in a separate container from the sidecar and has no shared FS,
    so the wire-format the KRL-INTERPRETER-05 backend chose is
    ``srcContent`` / ``urdfContent`` (base64 of the bytes) + optional
    ``srcFileAppId`` / ``urdfFileAppId`` for traceability. The sidecar
    accepts BOTH spellings here so the design-doc-shape and the
    actually-shipped-backend-shape both work.

    On the way in we materialise ``urdfContent`` to a temp file the
    composer can hand to ``ikpy.urdf.URDF.from_urdf_file()``; that
    happens in ``app.py`` once the request is validated.
    """

    model_config = ConfigDict(extra="allow")

    # Either srcText (plaintext) OR srcContent (base64) — validator below
    # normalises to plaintext on the srcText field.
    srcText: Optional[str] = None
    srcContent: Optional[str] = None  # base64-encoded .src bytes
    datText: Optional[str] = None
    datContent: Optional[List[str]] = None  # base64-encoded .dat bytes
    # Either urdfPath (host-side disk path) OR urdfContent (base64 of URDF XML).
    urdfPath: Optional[str] = None
    urdfContent: Optional[str] = None  # base64-encoded URDF XML
    # Traceability passthrough — backend includes these; sidecar treats as opaque.
    srcFileAppId: Optional[str] = None
    urdfFileAppId: Optional[str] = None
    datFileAppIds: Optional[List[str]] = None
    sceneAppId: Optional[str] = None
    baseFrame: Optional[FramePayload] = None
    toolFrame: Optional[FramePayload] = None
    seedPose: Optional[List[float]] = None
    timeStep: float = Field(0.01, gt=0)
    options: InterpretOptions = Field(default_factory=InterpretOptions)

    @model_validator(mode="after")
    def _normalise_src_and_urdf(self) -> "InterpretRequest":
        import base64

        # Normalise srcText: prefer srcText, fall back to base64-decoding srcContent.
        # Real DLR KUKA .src files are commonly ISO-8859-1 (Latin-1) — German
        # comments (`für`, `Schließen`, `übermitteln`) — so we try UTF-8 first
        # then fall back to Latin-1 → cp1252 → utf-8 with errors=replace as
        # last resort. Filed as KRL-INTEGRATION-MFFD-REAL-01-ENCODING-LATIN1
        # 2026-05-30 after 5/7 real MFFD .src files were rejected on first try.
        if not (self.srcText and self.srcText.strip()):
            if self.srcContent and self.srcContent.strip():
                try:
                    raw = base64.b64decode(self.srcContent)
                except ValueError as exc:
                    raise ValueError(f"srcContent must be valid base64: {exc}")
                for codec in ("utf-8", "iso-8859-1", "cp1252"):
                    try:
                        self.srcText = raw.decode(codec)
                        break
                    except UnicodeDecodeError:
                        continue
                else:
                    self.srcText = raw.decode("utf-8", errors="replace")
            else:
                raise ValueError("either srcText or srcContent must be provided")
        # urdfPath / urdfContent — defer the actual file write to app.py
        # so we can keep schemas.py free of FS side-effects; just check
        # one of them is present.
        if not (self.urdfPath and self.urdfPath.strip()) and not (
            self.urdfContent and self.urdfContent.strip()
        ):
            raise ValueError("either urdfPath or urdfContent must be provided")
        return self


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
