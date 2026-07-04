"""IK back-solver layer for the KRL interpreter.

Public surface:

- :class:`IkSolver` -- the per-request solver. Wrap a URDF once at init,
  call :meth:`IkSolver.solve` many times per program.
- :class:`UrdfLoader` -- URDF -> ikpy chain construction; movable-joint
  index extraction so the sidecar knows which channels to emit.
- :class:`SeedStrategy` (+ :class:`LastSolutionSeed`,
  :class:`NamedPoseSeed`, :class:`ZeroSeed`) -- pluggable seed
  selection used by the sidecar (-04) to keep IK warm across a
  program's many targets.
- :class:`TargetPose`, :class:`IkRequest`, :class:`IkResult`,
  :class:`IkWarning` -- typed dataclasses crossing the module
  boundary.

Design contract for the sidecar (-04):

1. Joint vectors are full ikpy chain arrays, not movable-joint-only
   subsets. Length matches ``len(chain.links)``. Indices of fixed
   links are always ``0.0`` on output and ignored on input. Use
   :meth:`UrdfLoader.movable_joint_indices` to project to the
   channels you actually emit.
2. :class:`IkResult` reports ``iterations = -1`` whenever the
   underlying ``ikpy.chain.Chain.inverse_kinematics_frame`` is used;
   ikpy does not surface a count via its public API. Treat this as
   "unknown" rather than zero.
3. Convergence is decided here, not by ikpy. We forward-kinematic the
   solution and compare to the target. If the position residual
   exceeds the request tolerance, ``converged = False`` and a single
   ``IkWarning(kind="unreachable", ...)`` is appended. We never raise
   on unreachable.
"""

from .types import IkRequest, IkResult, IkWarning, TargetPose
from .seed_strategy import (
    LastSolutionSeed,
    NamedPoseSeed,
    SeedStrategy,
    ZeroSeed,
)
from .solver import IkSolver
from .urdf_loader import UrdfLoader

__all__ = [
    "IkRequest",
    "IkResult",
    "IkSolver",
    "IkWarning",
    "LastSolutionSeed",
    "NamedPoseSeed",
    "SeedStrategy",
    "TargetPose",
    "UrdfLoader",
    "ZeroSeed",
]
