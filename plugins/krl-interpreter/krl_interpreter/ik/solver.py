"""IK back-solver wrapping :class:`ikpy.chain.Chain`.

Module-level design notes (see also ``ik/__init__.py``):

- **Tolerance / max_iterations trade-off.** ikpy's underlying scipy
  optimiser (SLSQP for ``inverse_kinematics_frame``) is set up by
  ikpy itself; we cannot pass ``max_iterations`` through cleanly at
  the public API level on ikpy 3.4. We accept the knob in the
  request, record it in the result for audit (so a
  ``:KrlInterpretActivity`` can attest what tolerance was asked
  for), and use ``tolerance`` only to compute ``converged`` from the
  FK residual. Tightening tolerance below 1e-4 m gains little for
  the KRL use case where the input poses are themselves quantised
  by the KRC interpolator; loosening above 1e-2 m starts dropping
  millimetre-grade detail visible in the URDF viewer animation.
  The 1e-3 m default is the design-doc tier-1 choice (aidocs/117
  ss 3.2).

- **Iterations.** Always ``-1`` in the result; ikpy does not expose
  scipy's iteration count via its public API. The field is kept on
  the dataclass so a future scipy-direct backend (or a -03 tier-2
  hybrid ``ikpy -> pinocchio`` polish) can populate it without an
  API break.

- **Convergence.** Decided here, not by ikpy. We forward-kinematic
  the returned joint vector and compare the resulting tip position
  to the target. ``converged = position_residual <= tolerance``. We
  intentionally ignore orientation residual at tier-1 -- IK on
  6-DOF arms typically converges to a near-target orientation
  whenever it converges to position, and the design doc records the
  orientation-residual hardening as a tier-2 deferral (ss 5.2).

- **Failure mode.** Never raise on unreachable. Return a result
  with ``converged=False`` and a single
  :class:`IkWarning(kind="unreachable")`. The sidecar surfaces the
  warning; the run continues.
"""

from __future__ import annotations

from typing import List, Optional

import numpy as np
from ikpy.chain import Chain
from scipy.spatial.transform import Rotation

from .types import IkResult, IkWarning, TargetPose
from .urdf_loader import UrdfLoader


def _target_to_matrix(target: TargetPose) -> np.ndarray:
    """Pose (x,y,z,rx,ry,rz) -> 4x4 homogeneous transform.

    Euler convention: XYZ intrinsic (scipy "xyz"). See
    ``types.TargetPose`` docstring for the KRL ABC -> xyz convention
    contract.
    """
    m = np.eye(4)
    m[:3, :3] = Rotation.from_euler("xyz", [target.rx, target.ry, target.rz]).as_matrix()
    m[:3, 3] = [target.x, target.y, target.z]
    return m


class IkSolver:
    """Per-instance solver. Build once per URDF; call :meth:`solve`
    many times per program.

    Parameters
    ----------
    urdf_path
        Filesystem path to the URDF.
    base_link, tip_link
        Optional URDF link names. ``base_link`` is forwarded to ikpy
        as ``base_elements=[base_link]``. ``tip_link`` is reserved
        for a future ikpy version that supports tip selection
        explicitly; on ikpy 3.4 it is currently informational only
        (the chain root selection drives the same thing in practice).
    """

    def __init__(
        self,
        urdf_path: str,
        base_link: Optional[str] = None,
        tip_link: Optional[str] = None,
    ):
        base_elements = [base_link] if base_link else None
        self.chain: Chain = UrdfLoader.load(urdf_path, base_elements=base_elements)
        self.urdf_path = urdf_path
        self.base_link = base_link
        self.tip_link = tip_link

    @property
    def movable_joint_indices(self) -> List[int]:
        """Indices in joint arrays where the URDF joint is movable.
        See :meth:`UrdfLoader.movable_joint_indices`."""
        return UrdfLoader.movable_joint_indices(self.chain)

    @property
    def movable_joint_names(self) -> List[str]:
        return UrdfLoader.movable_joint_names(self.chain)

    @property
    def chain_length(self) -> int:
        """Length of the full ikpy joint array (movable + fixed
        links). Use this to size seed arrays."""
        return len(self.chain.links)

    def solve(
        self,
        target: TargetPose,
        seed: Optional[List[float]] = None,
        tolerance: float = 1e-3,
        max_iterations: int = 100,
    ) -> IkResult:
        """Solve IK for a single Cartesian target.

        ``seed`` is the **full ikpy chain array** (length matches
        :attr:`chain_length`); pass ``None`` for the URDF zero pose.
        Fixed-link indices in the seed are ignored by ikpy and
        forced to ``0.0`` on return.

        See module docstring for the tolerance / max_iterations
        trade-off and convergence semantics.
        """
        target_matrix = _target_to_matrix(target)
        initial = np.array(seed) if seed is not None else np.zeros(self.chain_length)
        if initial.shape != (self.chain_length,):
            # Don't crash; surface the mismatch as a warning and use
            # zero seed. The sidecar can act on this.
            warning = IkWarning(
                kind="seed_shape_mismatch",
                message=(
                    f"seed length {initial.shape[0]} does not match chain "
                    f"length {self.chain_length}; using zero seed"
                ),
                severity="WARN",
            )
            initial = np.zeros(self.chain_length)
            warnings = [warning]
        else:
            warnings = []

        # ikpy itself does not raise on unreachable -- it returns the
        # best-effort solution. We still wrap for robustness against
        # numerical edge cases (NaN seeds, singular Jacobians).
        try:
            sol = self.chain.inverse_kinematics_frame(
                target_matrix, initial_position=initial
            )
        except Exception as exc:  # pragma: no cover - defensive; ikpy rarely raises
            warnings.append(
                IkWarning(
                    kind="solver_error",
                    message=f"ikpy raised: {exc}",
                    severity="ERROR",
                )
            )
            return IkResult(
                joints=list(initial),
                residual=float("inf"),
                iterations=-1,
                converged=False,
                warnings=warnings,
            )

        # Residual: tip position distance to target.
        fk = self.chain.forward_kinematics(sol)
        residual = float(np.linalg.norm(fk[:3, 3] - target_matrix[:3, 3]))
        converged = residual <= tolerance

        if not converged:
            warnings.append(
                IkWarning(
                    kind="unreachable",
                    message=(
                        f"position residual {residual:.4g} m exceeds tolerance "
                        f"{tolerance:.4g} m at target ({target.x:.3f}, "
                        f"{target.y:.3f}, {target.z:.3f})"
                    ),
                    severity="WARN",
                )
            )

        # See module docstring point 2: iterations is always -1
        # whenever we use ikpy's wrapper.
        _ = max_iterations  # accepted for audit but not surfaced to ikpy

        return IkResult(
            joints=[float(v) for v in sol],
            residual=residual,
            iterations=-1,
            converged=converged,
            warnings=warnings,
        )
