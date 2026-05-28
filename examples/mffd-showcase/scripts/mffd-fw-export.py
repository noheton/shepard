# /// script
# requires-python = ">=3.11"
# dependencies = ["requests"]
# ///
#!/usr/bin/env python3
"""mffd-fw-export.py — Bridge-welding-only export wrapper for mffd-ts-export.py.

Same shape, retry logic, and output layout as ``mffd-ts-export.py``, but
configured to export ONLY the bridge-welding (frame-welding) collection
(default coll_id=163811) and into its own ``OUT_DIR`` so it can run in parallel
with an already-running AFP tapelaying export without fighting over
``manifest.json``.

This is just a thin wrapper that sets env defaults and re-execs the parent
script — every flag exposed by ``mffd-ts-export.py`` (WORKERS, SKIP_TS,
SKIP_FILES, SKIP_STRUCTURED, SKIP_METADATA, PAGE_SIZE, RETRY_MAX, RETRY_BASE,
SOURCE_SHEPARD_URL, SOURCE_SHEPARD_API_KEY) still works.

Usage:
    SOURCE_SHEPARD_URL=https://backend.bt-au-cube3.intra.dlr.de \\
    SOURCE_SHEPARD_API_KEY=<jwt> \\
    uv run python mffd-fw-export.py

    # Override output dir:
    OUT_DIR=/data/exports/fw uv run python mffd-fw-export.py

    # Override collection id (default 163811):
    BRIDGEWELDING_COLL_ID=999999 uv run python mffd-fw-export.py

Output layout:
    ts-export-bridgewelding/
    ├── manifest.json
    └── bridgewelding/
        ├── collection.json
        ├── hierarchy.json
        └── <do_name>/
            ├── metadata.json
            ├── ts/<ref>.csv
            ├── files/<name>
            └── structured/<ref>.json
"""

import os
import runpy
import sys
from pathlib import Path

# Force the bridge-welding-only mode and a distinct OUT_DIR so a parallel
# tapelaying export's manifest.json is untouched. Only fill defaults if the
# caller didn't already set them.
os.environ.setdefault("SKIP_TAPELAYING", "1")
os.environ.setdefault("INCLUDE_BRIDGEWELDING", "1")
os.environ.setdefault("OUT_DIR", "ts-export-bridgewelding")

# Re-exec the parent script in our env. runpy gives us the same process and
# avoids needing to import the parent as a module (it was written as a
# top-level script with module-level config evaluation).
PARENT = Path(__file__).with_name("mffd-ts-export.py")
if not PARENT.exists():
    print(f"[ERROR] {PARENT} not found", file=sys.stderr)
    sys.exit(2)

# Set argv so the parent script sees a sensible $0 if it ever introspects it.
sys.argv = [str(PARENT)]
runpy.run_path(str(PARENT), run_name="__main__")
