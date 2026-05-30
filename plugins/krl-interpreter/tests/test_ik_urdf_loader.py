"""Tests for the URDF loader helper.

Three tests per the dispatch row:
1. total link / joint count on the two-link-arm fixture
2. movable-joint indices filter out fixed joints (expect 2 on two-link)
3. movable-joint names round-trip back to the URDF declarations
"""

from __future__ import annotations

from krl_interpreter.ik.urdf_loader import UrdfLoader


def test_loader_builds_chain_with_expected_link_count(two_link_urdf):
    chain = UrdfLoader.load(two_link_urdf)
    # ikpy prepends a synthetic "Base link" + we have 4 URDF joints.
    # Chain.links should be 5 (synthetic + base_to_link0 + joint1 +
    # joint2 + tool_joint).
    assert len(chain.links) == 5


def test_loader_movable_joint_indices_returns_two_revolute(two_link_urdf):
    chain = UrdfLoader.load(two_link_urdf)
    movable = UrdfLoader.movable_joint_indices(chain)
    # Exactly two movable joints on the two-link arm (joint1, joint2).
    assert len(movable) == 2
    # And they sit at indices 2 and 3 (after synthetic + base_to_link0).
    assert movable == [2, 3]


def test_loader_movable_joint_names_match_urdf_declarations(two_link_urdf):
    chain = UrdfLoader.load(two_link_urdf)
    names = UrdfLoader.movable_joint_names(chain)
    assert names == ["joint1", "joint2"]
