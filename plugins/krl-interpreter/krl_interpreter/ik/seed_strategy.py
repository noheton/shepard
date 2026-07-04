"""Seed-pose selection strategies for the IK back-solver.

The KUKA Robot Controller (KRC) mimics ``$RC_OLDPOS`` -- each pose's
joint solution is biased toward the previous pose's solution. The
:class:`LastSolutionSeed` reproduces that behaviour. For interactive
single-pose solves, :class:`NamedPoseSeed` lets the operator pick a
fixed reference (``home``, ``park``, etc.) and :class:`ZeroSeed`
always seeds at the URDF zero pose.

The interface is intentionally tiny: ``next_seed(chain_len) -> list[float]
| None``. Returning ``None`` defers to the solver's default (which is
the URDF zero pose, i.e. all-zeros at the chain length the solver
already knows). ``update(solution)`` is the hook the sidecar calls
after each successful solve so strategies that track history can
update.
"""

from __future__ import annotations

from typing import Dict, List, Optional


class SeedStrategy:
    """Base class. Subclasses override :meth:`next_seed` and
    optionally :meth:`update`."""

    def next_seed(self, chain_len: int) -> Optional[List[float]]:
        raise NotImplementedError

    def update(self, solution: List[float]) -> None:
        """Hook called by the sidecar after each successful solve.

        Default is a no-op; only :class:`LastSolutionSeed` cares.
        """
        return None


class ZeroSeed(SeedStrategy):
    """Always seeds at all-zeros. Equivalent to "do not seed" since
    that's already ikpy's default, but expressing it explicitly makes
    the choice auditable in the Activity record."""

    def next_seed(self, chain_len: int) -> Optional[List[float]]:
        return [0.0] * chain_len


class LastSolutionSeed(SeedStrategy):
    """Seeds from the previous successful solve.

    Mimics the KRC's ``$RC_OLDPOS`` joint-redundancy resolution per
    aidocs/117 ss 5.3. First call (no history) returns ``None`` --
    the solver falls back to its default (URDF zero pose), which is
    correct for the first pose of a program.
    """

    def __init__(self) -> None:
        self._last: Optional[List[float]] = None

    def next_seed(self, chain_len: int) -> Optional[List[float]]:
        if self._last is None:
            return None
        if len(self._last) != chain_len:
            # Guard against being reused across chains. Better to
            # restart at zero than to feed a wrong-length seed and
            # crash ikpy mid-flight.
            self._last = None
            return None
        return list(self._last)

    def update(self, solution: List[float]) -> None:
        self._last = list(solution)


class NamedPoseSeed(SeedStrategy):
    """Looks up the seed by name in an operator-supplied table.

    The table is typically derived from the KRL ``.dat`` file's named
    pose constants (``HOME``, ``PARK``, etc.). Unknown names return
    ``None`` so the solver falls back to its default; the sidecar
    surfaces a WARN to the user.
    """

    def __init__(self, named_poses: Dict[str, List[float]], requested_name: str):
        self._named_poses = dict(named_poses)
        self._requested_name = requested_name

    def next_seed(self, chain_len: int) -> Optional[List[float]]:
        pose = self._named_poses.get(self._requested_name)
        if pose is None:
            return None
        if len(pose) != chain_len:
            return None
        return list(pose)

    @property
    def requested_name(self) -> str:
        return self._requested_name

    @property
    def known_names(self) -> List[str]:
        return sorted(self._named_poses.keys())
