"""pytest discovery shim — adds the plugin root to ``sys.path`` so
``import krl_interpreter`` works without ``pip install -e .``."""

from __future__ import annotations

import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))
