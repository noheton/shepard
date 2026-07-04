"""Tests for the IkSolver public surface.

Six tests per the dispatch row:
1. happy-path reachable target on two-link arm (zero seed)
2. identity target (zero pose -> tool0 at its rest position)
3. seeded solve on two-link arm
4. unreachable target -> converged=False + warning, no exception
5. solve with LastSolutionSeed updates after each call
6. solve with NamedPoseSeed pulls from the table
+ one KR210 reachable sanity solve (so total = 7 in this file).
"""

from __future__ import annotations

import math

from krl_interpreter.ik.seed_strategy import LastSolutionSeed, NamedPoseSeed
from krl_interpreter.ik.solver import IkSolver
from krl_interpreter.ik.types import TargetPose


# ---------------- two-link arm happy paths ---------------- #

def test_solve_reachable_target_zero_seed(two_link_urdf):
    s = IkSolver(two_link_urdf)
    # Reach forward + slightly up; well inside the 2.0 unit workspace.
    target = TargetPose(x=1.5, y=0.5, z=0.0)
    res = s.solve(target, tolerance=1e-3)
    assert res.converged, f"expected convergence, got residual={res.residual}"
    assert res.residual <= 1e-3
    assert len(res.joints) == s.chain_length
    # Iterations is sentinel -1 (ikpy does not expose count).
    assert res.iterations == -1
    # No unreachable warning.
    assert not any(w.kind == "unreachable" for w in res.warnings)


def test_solve_identity_target_at_rest_pose(two_link_urdf):
    s = IkSolver(two_link_urdf)
    # Two-link arm at zero joints reaches (2.0, 0, 0) along +X.
    target = TargetPose(x=2.0, y=0.0, z=0.0)
    res = s.solve(target, seed=[0.0] * s.chain_length, tolerance=1e-3)
    assert res.converged
    # Joints near zero -- the seed already satisfies; residual tiny.
    assert res.residual < 1e-6


def test_solve_with_explicit_seed_converges(two_link_urdf):
    """Reach a known-reachable forward-kinematic target from a
    deliberately non-zero seed.

    Construct the target by FK on joints [pi/6, pi/4] so we know it's
    inside the reachable workspace, then seed the IK from a nearby
    pose. ikpy's SLSQP wrapper enforces both position and orientation
    by default; with our identity-orientation TargetPose default the
    seed needs to put the wrist orientation in the right neighbourhood
    for the planar 2-DOF arm. We use the FK pose itself as a seed
    perturbation to keep this within ikpy's basin of attraction.
    """
    import numpy as np
    s = IkSolver(two_link_urdf)
    movable = s.movable_joint_indices
    joints_fk = np.zeros(s.chain_length)
    joints_fk[movable[0]] = math.pi / 6
    joints_fk[movable[1]] = math.pi / 4
    fk = s.chain.forward_kinematics(joints_fk)
    target = TargetPose(x=float(fk[0, 3]), y=float(fk[1, 3]), z=0.0)
    # Seed: nudge each movable joint slightly off the truth.
    seed = list(joints_fk)
    seed[movable[0]] += 0.05
    seed[movable[1]] -= 0.05
    res = s.solve(target, seed=seed, tolerance=1e-3)
    assert res.converged, f"residual={res.residual}"


def test_solve_unreachable_returns_warning_not_exception(two_link_urdf):
    s = IkSolver(two_link_urdf)
    # Far outside the 2.0 unit reach.
    target = TargetPose(x=10.0, y=0.0, z=0.0)
    res = s.solve(target, tolerance=1e-3)
    assert not res.converged
    assert res.residual > 1e-3
    kinds = [w.kind for w in res.warnings]
    assert "unreachable" in kinds


# ---------------- seed strategy integration ---------------- #

def test_solve_with_last_solution_seed_updates_history(two_link_urdf):
    s = IkSolver(two_link_urdf)
    strategy = LastSolutionSeed()

    # First solve: no history; strategy returns None; solver uses zero.
    assert strategy.next_seed(s.chain_length) is None
    t1 = TargetPose(x=1.5, y=0.3, z=0.0)
    r1 = s.solve(t1, seed=None, tolerance=1e-3)
    assert r1.converged
    strategy.update(r1.joints)

    # Second solve: strategy now returns the warm seed.
    seed2 = strategy.next_seed(s.chain_length)
    assert seed2 is not None and len(seed2) == s.chain_length
    t2 = TargetPose(x=1.2, y=0.7, z=0.0)
    r2 = s.solve(t2, seed=seed2, tolerance=1e-3)
    assert r2.converged


def test_solve_with_named_pose_seed(two_link_urdf):
    s = IkSolver(two_link_urdf)
    # An operator-provided named pose table (e.g. derived from .dat).
    table = {
        "tucked": [0.0] * s.chain_length,
        "elbow_up": [0.0] * s.chain_length,
    }
    # Place a meaningful seed on the movable joints of "elbow_up".
    for idx in s.movable_joint_indices:
        table["elbow_up"][idx] = 0.5
    strategy = NamedPoseSeed(table, "elbow_up")
    seed = strategy.next_seed(s.chain_length)
    res = s.solve(TargetPose(x=1.4, y=0.4, z=0.0), seed=seed, tolerance=1e-3)
    assert res.converged


# ---------------- KR210 sanity ---------------- #

def test_solve_kr210_reachable_sanity(kr210_urdf):
    s = IkSolver(kr210_urdf, base_link="base_link")
    # Forward-kinematic a known joint vector to derive a guaranteed
    # reachable target, then back-solve from a non-zero seed.
    import numpy as np

    joints_fk = np.zeros(s.chain_length)
    for idx, val in zip(s.movable_joint_indices, [0.1, -0.3, 0.4, 0.1, 0.2, -0.1]):
        joints_fk[idx] = val
    fk = s.chain.forward_kinematics(joints_fk)
    target = TargetPose(x=float(fk[0, 3]), y=float(fk[1, 3]), z=float(fk[2, 3]))
    res = s.solve(target, tolerance=5e-3)
    assert res.converged
    assert res.residual <= 5e-3
