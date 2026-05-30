"""Test fixtures shared across the krl_interpreter suite.

Also adds the plugin root to ``sys.path`` so ``import krl_interpreter``
works without ``pip install -e .``.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

_ROOT = Path(__file__).resolve().parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))


FIXTURES_DIR = Path(__file__).parent / "ik_fixtures"


@pytest.fixture
def two_link_urdf() -> str:
    return str(FIXTURES_DIR / "two_link_arm.urdf")


@pytest.fixture
def kr210_urdf() -> str:
    p = FIXTURES_DIR / "kr210l150.urdf"
    # symlink resolution -- the test relies on the showcase URDF.
    if not p.exists() and not p.is_symlink():
        pytest.skip(f"KR210 URDF fixture missing at {p}")
    return str(p)
