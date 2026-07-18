"""v16.9 MFFD-TELEMETRY-ORPHAN — resolve_observability_containers().

Self-heals the importer's telemetry/manifest/runlog containers across dest
full-resets. Three behaviours verified:
  1. configured ids still resolve → kept, nothing created;
  2. a wiped id (container_exists False) → fresh container provisioned, the
     module global reassigned to the new id;
  3. provisioning fails (creator returns None) → id set to 0 so the channel
     fails SILENT (Telemetry _enabled gate turns off) instead of 404-spamming.

Run: python -m unittest tests.test_obs_container_resolve
"""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
if "mffd_v15" not in sys.modules:
    _spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
    mffd_v15 = importlib.util.module_from_spec(_spec)
    sys.modules["mffd_v15"] = mffd_v15
    _spec.loader.exec_module(mffd_v15)
else:
    mffd_v15 = sys.modules["mffd_v15"]


class FakeClient:
    """Minimal stand-in exposing only what the resolver calls."""

    def __init__(self, exists_map, create_returns):
        self._exists = exists_map            # (kind_path, id) -> bool
        self._create_returns = create_returns  # kind_path -> new id | None
        self.created = []                    # [(kind_path, name), ...]

    def container_exists(self, kind_path, container_id):
        return self._exists.get((kind_path, int(container_id)), False)

    def _mk(self, kind_path, name):
        self.created.append((kind_path, name))
        return self._create_returns.get(kind_path)

    def create_ts_container(self, name):
        return self._mk("timeseriesContainers", name)

    def create_file_container(self, name):
        return self._mk("fileContainers", name)

    def create_structured_container(self, name):
        return self._mk("structuredDataContainers", name)


class TestResolveObservabilityContainers(unittest.TestCase):
    def setUp(self):
        # Baseline the three module globals + point the sidecar at a throwaway
        # path so no prior-session state leaks between cases.
        mffd_v15.TELEMETRY_TS_CONTAINER_ID = 100
        mffd_v15.MANIFEST_CONTAINER_ID = 200
        mffd_v15.RUNLOG_SD_CONTAINER_ID = 300
        self._tmp = tempfile.TemporaryDirectory()
        self._prev_sidecar = mffd_v15._OBS_SIDECAR
        mffd_v15._OBS_SIDECAR = Path(self._tmp.name) / "obs.json"

    def tearDown(self):
        mffd_v15._OBS_SIDECAR = self._prev_sidecar
        self._tmp.cleanup()

    def test_all_exist_keeps_ids_and_creates_nothing(self):
        client = FakeClient(
            exists_map={
                ("timeseriesContainers", 100): True,
                ("fileContainers", 200): True,
                ("structuredDataContainers", 300): True,
            },
            create_returns={},
        )
        resolved = mffd_v15.resolve_observability_containers(client)
        self.assertEqual(resolved["TELEMETRY_TS_CONTAINER_ID"], 100)
        self.assertEqual(resolved["MANIFEST_CONTAINER_ID"], 200)
        self.assertEqual(resolved["RUNLOG_SD_CONTAINER_ID"], 300)
        self.assertEqual(client.created, [])
        self.assertEqual(mffd_v15.TELEMETRY_TS_CONTAINER_ID, 100)

    def test_wiped_telemetry_is_reprovisioned(self):
        client = FakeClient(
            exists_map={
                ("timeseriesContainers", 100): False,  # wiped by reset
                ("fileContainers", 200): True,
                ("structuredDataContainers", 300): True,
            },
            create_returns={"timeseriesContainers": 999},
        )
        resolved = mffd_v15.resolve_observability_containers(client)
        self.assertEqual(resolved["TELEMETRY_TS_CONTAINER_ID"], 999)
        self.assertEqual(mffd_v15.TELEMETRY_TS_CONTAINER_ID, 999)
        # Only the wiped one is (re)created; the survivors are untouched.
        self.assertEqual(len(client.created), 1)
        self.assertEqual(client.created[0][0], "timeseriesContainers")
        self.assertEqual(resolved["MANIFEST_CONTAINER_ID"], 200)

    def test_provision_failure_disables_channel(self):
        client = FakeClient(
            exists_map={
                ("timeseriesContainers", 100): False,
                ("fileContainers", 200): True,
                ("structuredDataContainers", 300): True,
            },
            create_returns={"timeseriesContainers": None},  # creation fails
        )
        resolved = mffd_v15.resolve_observability_containers(client)
        self.assertEqual(resolved["TELEMETRY_TS_CONTAINER_ID"], 0)
        self.assertEqual(mffd_v15.TELEMETRY_TS_CONTAINER_ID, 0)

    def test_sidecar_ids_preferred_over_stale_globals(self):
        # Simulate a runner restart: the sidecar holds ids from an earlier pass.
        mffd_v15._OBS_SIDECAR.write_text(
            '{"TELEMETRY_TS_CONTAINER_ID": 777, "MANIFEST_CONTAINER_ID": 888,'
            ' "RUNLOG_SD_CONTAINER_ID": 999}'
        )
        client = FakeClient(
            exists_map={
                ("timeseriesContainers", 777): True,
                ("fileContainers", 888): True,
                ("structuredDataContainers", 999): True,
            },
            create_returns={},
        )
        resolved = mffd_v15.resolve_observability_containers(client)
        self.assertEqual(resolved["TELEMETRY_TS_CONTAINER_ID"], 777)
        self.assertEqual(client.created, [])  # sidecar ids resolved → no creates


if __name__ == "__main__":
    unittest.main()
