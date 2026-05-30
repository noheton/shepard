"""Tests for the seed strategy interface and its three implementations."""

from __future__ import annotations

from krl_interpreter.ik.seed_strategy import (
    LastSolutionSeed,
    NamedPoseSeed,
    ZeroSeed,
)


def test_last_solution_seed_updates_after_solve():
    s = LastSolutionSeed()
    # Before any solve: defer to solver default.
    assert s.next_seed(5) is None
    # After update: returns the stored vector.
    s.update([0.1, 0.2, 0.3, 0.4, 0.5])
    assert s.next_seed(5) == [0.1, 0.2, 0.3, 0.4, 0.5]
    # Length mismatch on a subsequent solve resets to None defensively.
    assert s.next_seed(7) is None


def test_named_pose_seed_lookup_hit_and_miss():
    table = {
        "home": [0.0, 0.0, 0.0, 0.0],
        "park": [0.0, 1.0, -1.0, 0.0],
    }
    hit = NamedPoseSeed(table, "park")
    assert hit.next_seed(4) == [0.0, 1.0, -1.0, 0.0]
    assert hit.requested_name == "park"
    assert hit.known_names == ["home", "park"]

    miss = NamedPoseSeed(table, "ferry")
    assert miss.next_seed(4) is None


def test_zero_seed_returns_chain_length_zero_vector():
    z = ZeroSeed()
    assert z.next_seed(8) == [0.0] * 8
    # update is a no-op for ZeroSeed; calling it should not raise.
    z.update([1, 2, 3])
    assert z.next_seed(8) == [0.0] * 8
