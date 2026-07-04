"""Dataclasses crossing the IK module boundary.

These are the typed shapes the sidecar (-04) and the parser-to-IK
glue (within -02 / -04) speak to each other in. They are pure data;
no behaviour. ``IkResult`` is the persistence shape the sidecar will
serialise into the trajectory response.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional


# Default IK convergence tolerance in metres on Cartesian position.
# 1 mm is the design doc's tier-1 default (aidocs/117 ss 3.2).
DEFAULT_TOLERANCE = 1e-3

# Default max IK iterations. ikpy's internal cap; we expose for parity
# with the request shape even though we cannot influence ikpy's
# internal SLSQP / L-BFGS-B iteration count once dispatched.
DEFAULT_MAX_ITERATIONS = 100


@dataclass(frozen=True)
class TargetPose:
    """Cartesian 6-DOF target for the IK back-solver.

    ``rx, ry, rz`` are Euler angles in radians using the **XYZ
    intrinsic** convention (scipy ``Rotation.from_euler('xyz', ...)``).
    KRL's ``A / B / C`` angles use the **ZYX intrinsic** convention;
    the parser (-02) is responsible for the convention swap before
    handing a TargetPose to the solver. Documenting here so the
    contract is one-sided clear.
    """

    x: float
    y: float
    z: float
    rx: float = 0.0
    ry: float = 0.0
    rz: float = 0.0


@dataclass(frozen=True)
class IkWarning:
    """Non-fatal IK condition (e.g. unreachable target).

    The solver never raises on unreachable; it returns a result with
    ``converged=False`` plus one of these warnings appended. The
    sidecar -04 surfaces them in the response per aidocs/117 ss 3.3.
    """

    kind: str
    message: str
    severity: str = "WARN"  # one of INFO / WARN / ERROR


@dataclass(frozen=True)
class IkRequest:
    """A single Cartesian target plus IK knobs.

    Kept frozen so the sidecar can fan out requests into a job queue
    without aliasing concerns.
    """

    target: TargetPose
    seed: Optional[List[float]] = None
    tolerance: float = DEFAULT_TOLERANCE
    max_iterations: int = DEFAULT_MAX_ITERATIONS


@dataclass
class IkResult:
    """Per-pose solve result.

    ``joints`` is the **full ikpy chain array** (length matches
    ``chain.links``), not the movable-joint subset. Indices of fixed
    links are always ``0.0``. The sidecar (-04) projects to channels
    via :meth:`UrdfLoader.movable_joint_indices`.

    ``iterations`` is ``-1`` whenever the underlying ikpy IK was used
    (ikpy does not expose its scipy iteration count via the public
    API; see module docstring point 2).
    """

    joints: List[float]
    residual: float
    iterations: int
    converged: bool
    warnings: List[IkWarning] = field(default_factory=list)
