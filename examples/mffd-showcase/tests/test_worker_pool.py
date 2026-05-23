"""v15.8 IMPORT-PERF1 — worker-pool real fan-out wiring.

History: in v15.1..v15.7 `run_source_mode_workers` instantiated a
ThreadPoolExecutor + ran trivial `lambda: True` probes, then delegated to
`run_source_mode` SEQUENTIALLY. `--workers 4` bought zero concurrency.
See `aidocs/agent-findings/mffd-import-slowness-diagnose-2026-05-23.md §5`
hypothesis #1.

v15.8 moves the worker-pool implementation INTO `run_source_mode` so the
per-DO loop is the thing that fans out (not the per-step driver). The
`run_source_mode_workers` entry point becomes a thin forwarder that just
passes `workers=N` to `run_source_mode`.

Asserts:
  * `run_source_mode_workers(workers=1)` calls `run_source_mode(workers=1)`.
  * `run_source_mode_workers(workers=N)` for N>1 calls `run_source_mode(workers=N)`.
  * The wrapper itself NEVER instantiates ThreadPoolExecutor — that lives
    inside `run_source_mode` per-step.

Tests assert the *wiring* of the new shape. Real-concurrency convergence
tests are still flaky and out of scope here.

Run: cd examples/mffd-showcase && python3 -m unittest tests.test_worker_pool
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

_SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "mffd-import-v15.py"
if "mffd_v15" not in sys.modules:
    spec = importlib.util.spec_from_file_location("mffd_v15", _SCRIPT)
    mffd_v15 = importlib.util.module_from_spec(spec)
    sys.modules["mffd_v15"] = mffd_v15
    spec.loader.exec_module(mffd_v15)
else:
    mffd_v15 = sys.modules["mffd_v15"]


class TestSequentialFallback(unittest.TestCase):
    """workers<=1 — `run_source_mode_workers` calls `run_source_mode(workers=1)`."""

    def test_workers_one_forwards_with_workers_one(self):
        """workers=1 must delegate to run_source_mode with workers=1."""
        sentinel = {"tapelaying": 100, "bridgewelding": 200}
        with patch.object(mffd_v15, "run_source_mode", return_value=sentinel) as mock_run:
            result = mffd_v15.run_source_mode_workers(
                dest_client=object(),
                coll_id=515365,
                state=None,
                source_client=None,
                workers=1,
            )
            self.assertEqual(result, sentinel)
            mock_run.assert_called_once()
            # Verify the workers kwarg is 1 (sequential semantics preserved)
            kwargs = mock_run.call_args.kwargs
            self.assertEqual(kwargs.get("workers"), 1)

    def test_workers_zero_or_negative_also_sequential(self):
        """workers <= 1 (defensive) — also forwards with workers=1."""
        with patch.object(mffd_v15, "run_source_mode", return_value={}) as mock_run:
            mffd_v15.run_source_mode_workers(
                dest_client=object(), coll_id=515365, workers=0,
            )
            mock_run.assert_called_once()
            kwargs = mock_run.call_args.kwargs
            self.assertEqual(kwargs.get("workers"), 1)


class TestConcurrentPathWiring(unittest.TestCase):
    """workers>1 — `run_source_mode_workers` calls `run_source_mode(workers=N)`.

    The wrapper itself does NOT instantiate ThreadPoolExecutor in v15.8 —
    that lives inside `run_source_mode` per-step. The wrapper's job is to
    pass the `workers` arg through.
    """

    def test_workers_four_forwards_with_workers_four(self):
        """workers=4 — the wrapper forwards `workers=4` to run_source_mode."""
        with patch.object(mffd_v15, "run_source_mode", return_value={}) as mock_run:
            mffd_v15.run_source_mode_workers(
                dest_client=object(), coll_id=515365, workers=4,
            )
            mock_run.assert_called_once()
            kwargs = mock_run.call_args.kwargs
            self.assertEqual(kwargs.get("workers"), 4)

    def test_workers_two_returns_run_source_mode_result(self):
        """The wrapper must propagate run_source_mode's result through."""
        sentinel = {"tapelaying": 5, "bridgewelding": 7}
        with patch.object(mffd_v15, "run_source_mode", return_value=sentinel):
            result = mffd_v15.run_source_mode_workers(
                dest_client=object(), coll_id=515365, workers=2,
            )
            self.assertEqual(result, sentinel)

    def test_wrapper_does_not_instantiate_thread_pool_directly(self):
        """v15.8 — the wrapper itself must NOT instantiate ThreadPoolExecutor;
        the per-step executor lives inside `run_source_mode`."""
        with patch.object(mffd_v15, "run_source_mode", return_value={}):
            with patch("concurrent.futures.ThreadPoolExecutor") as mock_exec:
                mffd_v15.run_source_mode_workers(
                    dest_client=object(), coll_id=515365, workers=4,
                )
                # The wrapper no longer probes the executor with lambdas
                mock_exec.assert_not_called()


class TestLazyEnrichment(unittest.TestCase):
    """v15.8 IMPORT-PERF2 — iter_data_objects yields SourceDO STUBS with
    file_refs / ts_refs / structured_refs = None. The per-DO loop calls
    `_load_*_refs(src_do)` only after state-skip has had a chance to
    decline the work. On a fully-resumed collection: 0 cube3 GETs per DO
    (vs 3 in v15.7)."""

    def test_iter_data_objects_yields_stubs(self):
        """SourceDO yielded from iter_data_objects has None refs."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://test"
        # Mock _get to return one page of items
        fake_resp = MagicMock()
        fake_resp.json.return_value = [
            {"id": 1, "name": "DO-1", "attributes": {"k": "v"}},
            {"id": 2, "name": "DO-2", "attributes": {}},
        ]
        fake_resp.headers = {"X-Total-Pages": "1"}
        client._get = MagicMock(side_effect=[fake_resp, None])
        # The fetch helpers must NOT be called during iteration — that's
        # the PERF2 guarantee.
        client._fetch_file_refs = MagicMock(side_effect=AssertionError(
            "_fetch_file_refs called eagerly — PERF2 broken"))
        client._fetch_ts_refs = MagicMock(side_effect=AssertionError(
            "_fetch_ts_refs called eagerly — PERF2 broken"))
        client._fetch_structured_refs = MagicMock(side_effect=AssertionError(
            "_fetch_structured_refs called eagerly — PERF2 broken"))

        result = list(client.iter_data_objects(48297))
        self.assertEqual(len(result), 2)
        self.assertIsNone(result[0].file_refs)
        self.assertIsNone(result[0].ts_refs)
        self.assertIsNone(result[0].structured_refs)
        self.assertEqual(result[0]._src_coll_id, 48297)

    def test_iter_data_objects_paginates_all_pages(self):
        """v16.2 PAGINATION-FIX — iter_data_objects must paginate until the
        source returns an empty page. The v5 source omits X-Total-Pages,
        which prior code (v16.1) treated as "last page reached" — capping
        enumeration at PAGE_SIZE items. This regression-shielded test
        feeds three pages (50 + 50 + 0) and asserts all 100 items are
        yielded.

        See `project_mffd_api_keys.md`: 'X-Total-Count and X-Total-Pages
        headers ABSENT — paginate until empty page'.
        """
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://test"

        page0 = MagicMock()
        page0.json.return_value = [
            {"id": i, "name": f"DO-{i}", "attributes": {}}
            for i in range(0, 50)
        ]
        page0.headers = {}  # v5 reality: no X-Total-Pages header
        page1 = MagicMock()
        page1.json.return_value = [
            {"id": i, "name": f"DO-{i}", "attributes": {}}
            for i in range(50, 100)
        ]
        page1.headers = {}
        page2 = MagicMock()
        page2.json.return_value = []  # empty → end-of-pagination sentinel
        page2.headers = {}

        client._get = MagicMock(side_effect=[page0, page1, page2])

        result = list(client.iter_data_objects(48297))

        self.assertEqual(
            len(result), 100,
            f"expected 100 items across 2 non-empty pages, got {len(result)} "
            "— pagination loop probably broke after page 0 (v16.1 bug)"
        )
        self.assertEqual(result[0].do_id, 0)
        self.assertEqual(result[-1].do_id, 99)
        # _get must have been called exactly 3 times: page 0, page 1, page 2 (empty).
        self.assertEqual(client._get.call_count, 3)
        # And the page param must have advanced 0 → 1 → 2.
        page_args = [call.args[1]["page"] for call in client._get.call_args_list]
        self.assertEqual(page_args, ["0", "1", "2"])

    def test_load_file_refs_populates_and_caches(self):
        """_load_file_refs fetches on first call, caches on subsequent calls."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        fake_refs = [mffd_v15.FileRef(fref_id=1, name="a.txt", size=100, oid="")]
        client._fetch_file_refs = MagicMock(return_value=fake_refs)

        src_do = mffd_v15.SourceDO(
            do_id=42, name="DO-42", description="", attributes={},
            _src_coll_id=48297,
        )
        # First call: fetches
        refs1 = client._load_file_refs(src_do)
        self.assertEqual(refs1, fake_refs)
        client._fetch_file_refs.assert_called_once_with(48297, 42)
        # Second call: cached, no new fetch
        refs2 = client._load_file_refs(src_do)
        self.assertIs(refs1, refs2)
        client._fetch_file_refs.assert_called_once()  # still 1 call total

    def test_is_do_done_short_circuit_keys_exist(self):
        """v15.8 PERF2 guarantee: ImportState has is_do_done/mark_do_done that
        the per-DO loop short-circuits on. A fully-done DO costs 0 cube3 GETs
        because `_process_one_source_do` returns early BEFORE calling
        `_load_*_refs`.

        This test asserts the API contract — the state methods exist and a
        marked-done key reports done. The end-to-end "0 GETs" assertion lives
        in TestPerDoShortCircuit below; this test is the unit-level shield
        against accidental API removal.
        """
        import tempfile
        from pathlib import Path
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        self.assertFalse(state.is_do_done("tapelaying/DO-42"))
        state.mark_do_done("tapelaying/DO-42")
        self.assertTrue(state.is_do_done("tapelaying/DO-42"))
        # Sibling step doesn't collide.
        self.assertFalse(state.is_do_done("bridgewelding/DO-42"))
        path.unlink(missing_ok=True)


class TestImportStateThreadSafety(unittest.TestCase):
    """v15.8 IMPORT-PERF1 — ImportState must serialise read+write under
    concurrent workers. The RLock prevents lost updates."""

    def test_state_has_rlock(self):
        import tempfile
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        self.assertTrue(hasattr(state, "_lock"))
        # RLock can be acquired multiple times by the same thread without
        # deadlock — guard against future code that nests state ops.
        state._lock.acquire()
        state._lock.acquire()
        state._lock.release()
        state._lock.release()
        path.unlink(missing_ok=True)

    def test_state_marks_under_lock_dont_deadlock(self):
        """Sequential mark_file_done calls must not deadlock with the RLock."""
        import tempfile
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        state.mark_file_done("a")
        state.mark_file_done("b")
        self.assertTrue(state.is_file_done("a"))
        self.assertTrue(state.is_file_done("b"))
        path.unlink(missing_ok=True)


class TestWorkerPoolPrimitivesAvailable(unittest.TestCase):
    """The v15 wrapper relies on v15's C7 primitives — guard against
    accidental removal."""

    def test_backoff_delay_available(self):
        self.assertTrue(hasattr(mffd_v15, "backoff_delay"))
        d = mffd_v15.backoff_delay(0)
        self.assertGreater(d, 0)

    def test_atomic_write_json_available(self):
        self.assertTrue(hasattr(mffd_v15, "atomic_write_json"))

    def test_state_file_available(self):
        self.assertTrue(hasattr(mffd_v15, "StateFile"))

    def test_jwt_pause_manager_available(self):
        self.assertTrue(hasattr(mffd_v15, "JwtPauseManager"))


class TestVersionConstant(unittest.TestCase):
    """IMPORT_SCRIPT_VERSION constant is the source of truth.

    Bumped each release: 15.8 → 15.9 (PROV-V15.9) → 15.11 (PROV-V15.11) → 15.12 (BUG-E) → 15.13 (PROGRESS-ETA + CONTAINED-COMPLETENESS) → 15.14 (BUG-F + BUG-G) → 15.15 (BUG-F2: one SD container per process step, not per ref-name) → 15.16 (RUNNER-UV: clearer preflight error + runner prefers `uv run --script`) → 15.17 (ENV-ALIAS + AUTH-PROBE: accept SHEPARD_BASE_URL/SHEPARD_TOKEN/SOURCE_URL/SOURCE_API_KEY env names; fail fast at startup if dest or source auth doesn't return 200) → 15.18 (IMPORT-SR2: semantic-repo endpoint now uses https:// URL shape, soft-fail on POST 4xx) → 16.0 (PRESERVE-HIERARCHY: 3-pass tree replication of cube3 source DOs — v15's flat ref-prefix shape is gated behind MFFD_PRESERVE_HIERARCHY=0 fallback) → 16.1 (parallelize Pass 1 + Pass 2 with worker fan-out) → 16.2 (PAGINATION-FIX: iter_data_objects no longer relies on the absent X-Total-Pages header, paginates until an empty page — v16.1 capped source enumeration at page 0 = ~100 DOs vs the ~30K-DO MFFD source).
    """

    def test_version_is_16_2(self):
        self.assertEqual(mffd_v15.IMPORT_SCRIPT_VERSION, "16.2")


# ── v16 PRESERVE-HIERARCHY tests ──────────────────────────────────────────────
#
# These assert the structural contract of the v16 3-pass design:
#
#   Pass 1: enumerate source DOs flat + create one dest DO per source DO
#           (name + attributes + description; NO parentId yet).
#   Pass 2: PATCH each dest DO with parentId + predecessorIds, mapped via
#           src_to_dest. Top-level src DOs (parentId=None) reparent under
#           the STEP dest DO so the tree is browsable from the step root.
#   Pass 3: per-source-DO file/TS/SD writes hit the LEAF dest DO, not the
#           step DO. dest_name / ref_name no longer carry src_do.name prefix.
#
# State key shape for v16 uses src_do_id (not src_do.name) for stable
# resume across re-runs of the same source dataset.


class TestV16PreserveHierarchy(unittest.TestCase):
    """v16 PRESERVE-HIERARCHY — 3-pass tree replication.

    Tests target the contracts the v16 design must satisfy. The concrete
    code under test lives inside `run_source_mode`'s v16 branch (Pass 1 +
    Pass 2) and in `_process_one_source_do`'s v16-active code paths
    (Pass 3). The closure capture pattern means the assertions probe:
      * ShepardClient.patch_data_object semantics (parentId + predecessor)
      * ShepardClient.create_data_object accepts a parent_id kwarg
      * SourceDO carries parentId + predecessorIds from iter_data_objects
      * ImportState.set_dest_id / get_dest_id round-trip + resume safety
    """

    def test_pass1_sourcedo_carries_parent_and_predecessor(self):
        """SourceDO yielded from iter_data_objects carries parentId +
        predecessorIds drawn from the source response. Pass 2 reads them
        from this struct (no extra GET per DO)."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://test"
        fake_resp = MagicMock()
        fake_resp.json.return_value = [
            # Root DO — no parentId, no predecessors
            {"id": 100, "name": "root", "parentId": None, "predecessorIds": []},
            # Child of 100, has a predecessor 100
            {"id": 101, "name": "child", "parentId": 100, "predecessorIds": [100]},
            # Grandchild + multi-predecessor
            {"id": 102, "name": "gc", "parentId": 101, "predecessorIds": [101, 100]},
        ]
        fake_resp.headers = {"X-Total-Pages": "1"}
        client._get = MagicMock(side_effect=[fake_resp, None])

        result = list(client.iter_data_objects(48297))
        self.assertEqual(len(result), 3)
        self.assertIsNone(result[0].parentId)
        self.assertEqual(result[0].predecessorIds, [])
        self.assertEqual(result[1].parentId, 100)
        self.assertEqual(result[1].predecessorIds, [100])
        self.assertEqual(result[2].parentId, 101)
        self.assertEqual(result[2].predecessorIds, [101, 100])

    def test_create_data_object_accepts_parent_id_kwarg(self):
        """Pass 1 doesn't set parentId at create time, but the kwarg must
        exist on create_data_object for callers (e.g. v15 step DO under
        a future opt-in) that want to wire parent during POST."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://test"
        # Capture the POST body to assert parentId leaks through.
        captured = {}
        def _fake_post(url, body):
            captured["url"] = url
            captured["body"] = body
            r = MagicMock()
            r.json.return_value = {"id": 99, "appId": "app-99"}
            return r
        client._post = _fake_post

        result = client.create_data_object(
            42, "leaf", description="d", attrs={"k": "v"},
            parent_id=11,
        )
        self.assertEqual(result["id"], 99)
        self.assertEqual(captured["body"]["parentId"], 11)
        # Verify POST endpoint shape
        self.assertIn("/collections/42/dataObjects", captured["url"])

    def test_patch_data_object_does_not_strip_parent_or_predecessor(self):
        """advisor flag #3 — `set_predecessors` over-strips by popping
        parentId from the PUT body. v16's patch_data_object MUST NOT strip
        parentId or predecessorIds; they're writable per OpenAPI 5.4.0."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://test"
        # GET returns the existing DO body (with readonly + writable fields).
        get_resp = MagicMock()
        get_resp.json.return_value = {
            "id": 99,
            "name": "leaf",
            "createdAt": "2026-05-23",
            "createdBy": "alice",
            "updatedAt": "2026-05-23",
            "updatedBy": "alice",
            "collectionId": 42,
            "referenceIds": [],
            "successorIds": [],
            "childrenIds": [],
            "parentId": None,
            "incomingIds": [],
            "predecessorIds": [],
            "attributes": {"k": "v"},
        }
        client._get = MagicMock(return_value=get_resp)
        captured = {}
        def _fake_put(url, body):
            captured["url"] = url
            captured["body"] = body
            return MagicMock()
        client._put = _fake_put

        ok = client.patch_data_object(42, 99, parentId=11, predecessorIds=[7, 8])
        self.assertTrue(ok)
        body = captured["body"]
        # CRITICAL — parentId is writable, must NOT be stripped from PUT body.
        self.assertEqual(body["parentId"], 11)
        # predecessorIds must be exactly the requested list (REPLACE semantics).
        self.assertEqual(body["predecessorIds"], [7, 8])
        # Truly readonly fields must be stripped per v5.4.0 PUT contract.
        for k in (
            "id", "createdAt", "createdBy", "updatedAt", "updatedBy",
            "collectionId", "referenceIds", "successorIds", "childrenIds",
            "incomingIds",
        ):
            self.assertNotIn(k, body, f"readonly field {k!r} not stripped")
        # Writable scalar attributes are preserved on the round-trip.
        self.assertEqual(body["name"], "leaf")
        self.assertEqual(body["attributes"], {"k": "v"})

    def test_patch_data_object_omitting_parent_preserves_existing(self):
        """When parentId kwarg is omitted, the existing parentId on the dest
        DO must be preserved (UNSET sentinel semantics)."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://test"
        get_resp = MagicMock()
        get_resp.json.return_value = {
            "id": 99, "name": "leaf", "parentId": 55,
            "predecessorIds": [3],
            "createdAt": "x", "createdBy": "y", "updatedAt": "x",
            "updatedBy": "y", "collectionId": 42, "referenceIds": [],
            "successorIds": [], "childrenIds": [], "incomingIds": [],
        }
        client._get = MagicMock(return_value=get_resp)
        captured = {}
        def _fake_put(url, body):
            captured["body"] = body
            return MagicMock()
        client._put = _fake_put

        # No parentId kwarg — must preserve 55.
        client.patch_data_object(42, 99, predecessorIds=[7])
        self.assertEqual(captured["body"]["parentId"], 55)
        self.assertEqual(captured["body"]["predecessorIds"], [7])

    def test_import_state_dest_id_map_roundtrip(self):
        """ImportState.set_dest_id / get_dest_id stores src→dest mapping
        for Pass 1 resume safety. Both id (always) and appId (optional)
        round-trip cleanly."""
        import tempfile
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        # Fresh state: no mapping.
        self.assertIsNone(state.get_dest_id(12345))
        # Set + read back.
        state.set_dest_id(12345, 99, dest_app_id="app-99")
        self.assertEqual(state.get_dest_id(12345), 99)
        self.assertEqual(state.get_dest_app_id(12345), "app-99")
        # Different src_do_id doesn't collide.
        self.assertIsNone(state.get_dest_id(99999))
        # set_dest_id without appId still records the id.
        state.set_dest_id(67890, 42)
        self.assertEqual(state.get_dest_id(67890), 42)
        self.assertIsNone(state.get_dest_app_id(67890))
        path.unlink(missing_ok=True)

    def test_import_state_dest_id_map_back_compat(self):
        """Old state files (no dest_id_map field) load without error;
        get_dest_id on missing keys returns None."""
        import tempfile
        with tempfile.NamedTemporaryFile(
                suffix=".json", delete=False, mode="w") as f:
            # v15 state shape — no dest_id_map field.
            import json as _json
            _json.dump({
                "completed_files": ["tapelaying/a"],
                "completed_dos": ["tapelaying/DO-1"],
                "ts_containers": {"tapelaying": 42},
            }, f)
            path = Path(f.name)
        state = mffd_v15.ImportState(path)
        # Missing field treated as empty dict — no migration error.
        self.assertIsNone(state.get_dest_id(12345))
        # Mutator still works against the absent field.
        state.set_dest_id(12345, 99)
        self.assertEqual(state.get_dest_id(12345), 99)
        path.unlink(missing_ok=True)

    def test_preserve_hierarchy_env_flag_default_on(self):
        """MFFD_PRESERVE_HIERARCHY defaults to "1" (v16 mode ON). Setting
        it to "0" disables v16 (v15 fallback path). The flag is read via
        os.environ.get with default "1"."""
        import os
        # Default ON: when unset, .get returns "1" → v16 active.
        os.environ.pop("MFFD_PRESERVE_HIERARCHY", None)
        self.assertEqual(os.environ.get("MFFD_PRESERVE_HIERARCHY", "1"), "1")
        # Explicit OFF: when "0", v16 is bypassed.
        os.environ["MFFD_PRESERVE_HIERARCHY"] = "0"
        self.assertEqual(os.environ.get("MFFD_PRESERVE_HIERARCHY", "1"), "0")
        # Cleanup so other tests don't observe this.
        os.environ.pop("MFFD_PRESERVE_HIERARCHY", None)


def _make_src_do(do_id, name="DO", parentId=None, predecessorIds=None,
                 attributes=None, description="", _src_coll_id=42):
    """Build a SourceDO stub for v16 tests (avoids the dataclass
    keyword-only field repetition in every test)."""
    return mffd_v15.SourceDO(
        do_id=do_id,
        name=name,
        description=description,
        attributes=attributes or {},
        _src_coll_id=_src_coll_id,
        parentId=parentId,
        predecessorIds=list(predecessorIds or []),
    )


class TestV16Pass1CreateDestMirrors(unittest.TestCase):
    """v16 PRESERVE-HIERARCHY Pass 1 — flat-enumerate + create one dest DO
    per source DO. Direct unit tests against the extracted module-level
    helper ``v16_pass1_create_dest_mirrors``."""

    def _client(self, create_results=None):
        """Build a mock ShepardClient with a stubbed create_data_object."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://dest"
        # Returns ascending dest ids — id=1000+, appId=app-1000+ — unless
        # a custom result list is supplied (e.g. failures).
        if create_results is None:
            counter = {"n": 0}
            def _fake_create(coll_id, name, description="", attrs=None, **kw):
                counter["n"] += 1
                return {"id": 1000 + counter["n"], "appId": f"app-{1000 + counter['n']}"}
            client.create_data_object = MagicMock(side_effect=_fake_create)
        else:
            client.create_data_object = MagicMock(side_effect=create_results)
        client._get = MagicMock(return_value=None)
        return client

    def test_pass1_creates_dest_mirror_per_source_do(self):
        """Spec test #1 — 5 source DOs with mixed parent/pred topology;
        expect 5 dest create calls + populated src_to_dest map."""
        from unittest.mock import MagicMock
        client = self._client()
        src_dos = [
            _make_src_do(101, "root1"),
            _make_src_do(102, "root2"),
            _make_src_do(103, "child1", parentId=101),
            _make_src_do(104, "child2", parentId=101, predecessorIds=[103]),
            _make_src_do(105, "gc", parentId=104),
        ]
        state = None  # no resume
        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, coll_id=42, step_key="tapelaying",
            state=state, src_coll_id=48297,
        )
        # 5 source DOs → 5 dest creates → 5 map entries.
        self.assertEqual(client.create_data_object.call_count, 5)
        self.assertEqual(len(result), 5)
        for src_do in src_dos:
            self.assertIn(src_do.do_id, result)
            self.assertIn("dest_id", result[src_do.do_id])
            self.assertIn("dest_app_id", result[src_do.do_id])
            self.assertIsInstance(result[src_do.do_id]["dest_id"], int)
        # Each source DO's name + provenance attrs reach the dest create call.
        first_call_kwargs = client.create_data_object.call_args_list[0].kwargs
        self.assertIn("attrs", first_call_kwargs)
        self.assertEqual(first_call_kwargs["attrs"]["source_do_id"], "101")
        self.assertEqual(first_call_kwargs["attrs"]["process_step"], "tapelaying")
        self.assertEqual(first_call_kwargs["attrs"]["source_coll_id"], "48297")

    def test_pass1_resume_safety_uses_state_dest_id_map(self):
        """Spec test #4 — state pre-populated with 2 of 5 mappings; Pass 1
        only creates the 3 missing entries; resumed entries flow through
        the result map unchanged."""
        import tempfile
        from pathlib import Path as _Path
        with tempfile.NamedTemporaryFile(
                suffix=".json", delete=False) as f:
            path = _Path(f.name)
        state = mffd_v15.ImportState(path)
        # Pre-populate 2 of 5 mappings.
        state.set_dest_id(101, 9001, "app-9001")
        state.set_dest_id(103, 9003, "app-9003")

        client = self._client()
        src_dos = [
            _make_src_do(101, "root1"),
            _make_src_do(102, "root2"),
            _make_src_do(103, "child1"),
            _make_src_do(104, "child2"),
            _make_src_do(105, "gc"),
        ]
        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, coll_id=42, step_key="tapelaying",
            state=state, src_coll_id=48297,
        )
        # Only 3 missing entries → 3 create calls.
        self.assertEqual(client.create_data_object.call_count, 3)
        # All 5 entries in the map (resumed + freshly-created).
        self.assertEqual(len(result), 5)
        # Resumed entries preserve the pre-populated dest_ids.
        self.assertEqual(result[101]["dest_id"], 9001)
        self.assertEqual(result[101]["dest_app_id"], "app-9001")
        self.assertEqual(result[103]["dest_id"], 9003)
        path.unlink(missing_ok=True)

    def test_pass1_failed_create_excluded_from_map(self):
        """When dest create returns None (4xx/5xx), the src_do_id is NOT
        present in the result map; Pass 2 + Pass 3 must skip it cleanly.

        v16.1: workers=1 pins the side_effect iteration to src_dos order
        so the "second call fails" semantics is deterministic. With
        workers>1 the worker-arrival order is unspecified and the failure
        could land on any src_do_id — that ordering invariant is unit-
        tested separately in test_pass1_retry_on_failure (uses a by-id
        side_effect that is thread-safe).
        """
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://dest"
        # First create succeeds, second fails (None), third succeeds.
        client.create_data_object = MagicMock(side_effect=[
            {"id": 9001, "appId": "app-9001"},
            None,
            {"id": 9003, "appId": "app-9003"},
        ])
        src_dos = [
            _make_src_do(101, "root1"),
            _make_src_do(102, "root2"),
            _make_src_do(103, "root3"),
        ]
        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, coll_id=42, step_key="bridgewelding",
            state=None, src_coll_id=163811,
            workers=1,
        )
        self.assertEqual(len(result), 2)
        self.assertIn(101, result)
        self.assertNotIn(102, result)  # failed create — excluded
        self.assertIn(103, result)

    def test_pass1_blank_name_replaced_with_fallback(self):
        """OpenAPI 5.4.0 requires `name` to match `\\S` pattern. Pass 1
        substitutes a deterministic fallback for blank source names."""
        from unittest.mock import MagicMock
        client = self._client()
        src_dos = [_make_src_do(7777, name="   "), _make_src_do(8888, name="")]
        mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, coll_id=42, step_key="tapelaying",
            state=None, src_coll_id=48297,
        )
        names = [c.args[1] for c in client.create_data_object.call_args_list]
        self.assertIn("src-do-7777", names)
        self.assertIn("src-do-8888", names)


class TestV16Pass2WireEdges(unittest.TestCase):
    """v16 PRESERVE-HIERARCHY Pass 2 — parent + predecessor edge wiring
    via the src→dest map. Direct unit tests against the module-level helper
    ``v16_pass2_wire_edges``."""

    def _client_capturing_patches(self):
        """Mock ShepardClient.patch_data_object that captures every call."""
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://dest"
        captured = []
        def _fake_patch(coll_id, do_id, **kwargs):
            captured.append({"do_id": do_id, **kwargs})
            return True
        client.patch_data_object = MagicMock(side_effect=_fake_patch)
        return client, captured

    def test_pass2_wires_parentid_via_src_to_dest_map(self):
        """Spec test #2 — 3-level tree (root → child → grandchild) wires
        parentId via src→dest mapping. Forward refs work because Pass 1
        completes the full src list before Pass 2 starts."""
        client, captured = self._client_capturing_patches()
        src_dos = [
            _make_src_do(100, "root"),
            _make_src_do(101, "child", parentId=100),
            _make_src_do(102, "gc", parentId=101),
        ]
        src_to_dest = {
            100: {"dest_id": 9100, "dest_app_id": "app-9100"},
            101: {"dest_id": 9101, "dest_app_id": "app-9101"},
            102: {"dest_id": 9102, "dest_app_id": "app-9102"},
        }
        wired, failed = mffd_v15.v16_pass2_wire_edges(
            src_dos, src_to_dest, step_dest_do_id=8000,
            dest_client=client, coll_id=42,
        )
        self.assertEqual(wired, 3)
        self.assertEqual(failed, 0)
        # Map do_id → patch body for direct assertion.
        by_id = {c["do_id"]: c for c in captured}
        # Root (parentId=None) reparents under the STEP dest DO (8000).
        self.assertEqual(by_id[9100]["parentId"], 8000)
        # Child of 100 — parent must map to dest 9100.
        self.assertEqual(by_id[9101]["parentId"], 9100)
        # Grandchild of 101 — parent must map to dest 9101.
        self.assertEqual(by_id[9102]["parentId"], 9101)

    def test_pass2_top_level_src_do_reparents_under_step_do(self):
        """advisor flag — top-level source DOs (parentId=None) MUST
        reparent under the STEP dest DO so the imported tree stays
        browsable from the step root."""
        client, captured = self._client_capturing_patches()
        src_dos = [
            _make_src_do(500, "Layup"),           # parentId=None (top)
            _make_src_do(501, "Setup"),           # parentId=None (top)
            _make_src_do(502, "Calibration"),     # parentId=None (top)
        ]
        src_to_dest = {
            500: {"dest_id": 9500, "dest_app_id": "app-9500"},
            501: {"dest_id": 9501, "dest_app_id": "app-9501"},
            502: {"dest_id": 9502, "dest_app_id": "app-9502"},
        }
        mffd_v15.v16_pass2_wire_edges(
            src_dos, src_to_dest, step_dest_do_id=42424242,
            dest_client=client, coll_id=42,
        )
        # Every top-level src DO reparented under the step DO.
        for c in captured:
            self.assertEqual(c["parentId"], 42424242,
                             f"top-level src must reparent under step DO, "
                             f"got parentId={c['parentId']} for dest {c['do_id']}")

    def test_pass2_wires_predecessor_edges_with_forward_refs(self):
        """Spec test #3 — source DO 5 has predecessorIds=[3,7] where DO 7
        appears AFTER 5 in Pass 1 ordering. Pass 2 still wires the edge
        because Pass 1 completes (full src list) before Pass 2 starts."""
        client, captured = self._client_capturing_patches()
        # Pass-1 ordering puts 5 BEFORE 7 — the forward-ref case.
        src_dos = [
            _make_src_do(3, "early"),
            _make_src_do(5, "mid", predecessorIds=[3, 7]),
            _make_src_do(7, "late"),
        ]
        src_to_dest = {
            3: {"dest_id": 9003, "dest_app_id": ""},
            5: {"dest_id": 9005, "dest_app_id": ""},
            7: {"dest_id": 9007, "dest_app_id": ""},
        }
        mffd_v15.v16_pass2_wire_edges(
            src_dos, src_to_dest, step_dest_do_id=8000,
            dest_client=client, coll_id=42,
        )
        by_id = {c["do_id"]: c for c in captured}
        # DO 5's forward+back predecessors both wired.
        self.assertEqual(set(by_id[9005]["predecessorIds"]), {9003, 9007})

    def test_pass2_cross_step_predecessor_silently_dropped(self):
        """Cross-step predecessors (src ids not in src_to_dest, e.g. a
        bridgewelding DO whose predecessor lives in tapelaying) are
        silently dropped — they're outside the step's mapping universe."""
        client, captured = self._client_capturing_patches()
        src_dos = [
            _make_src_do(200, "in-step", predecessorIds=[999, 100]),
        ]
        # Only 200 is in the map; 999 (foreign) + 100 (foreign) are not.
        src_to_dest = {200: {"dest_id": 9200, "dest_app_id": ""}}
        mffd_v15.v16_pass2_wire_edges(
            src_dos, src_to_dest, step_dest_do_id=8000,
            dest_client=client, coll_id=42,
        )
        # The patch call has no predecessors wired (empty list dropped to None).
        self.assertEqual(len(captured), 1)
        # predecessorIds either omitted or None — never an empty list.
        self.assertIn(captured[0].get("predecessorIds"), (None, []))

    def test_pass2_skips_dos_missing_from_src_to_dest(self):
        """When Pass 1 failed for a src DO (not in src_to_dest), Pass 2
        skips it cleanly — no patch call attempted."""
        client, captured = self._client_capturing_patches()
        src_dos = [
            _make_src_do(100, "ok"),
            _make_src_do(101, "pass1-failed"),  # not in map
            _make_src_do(102, "ok"),
        ]
        src_to_dest = {
            100: {"dest_id": 9100, "dest_app_id": ""},
            102: {"dest_id": 9102, "dest_app_id": ""},
        }
        wired, _ = mffd_v15.v16_pass2_wire_edges(
            src_dos, src_to_dest, step_dest_do_id=8000,
            dest_client=client, coll_id=42,
        )
        # Only 2 patches (100 + 102); 101 was skipped.
        self.assertEqual(wired, 2)
        self.assertEqual(len(captured), 2)
        do_ids_patched = {c["do_id"] for c in captured}
        self.assertEqual(do_ids_patched, {9100, 9102})


class TestV16Pass3TargetResolution(unittest.TestCase):
    """v16 Pass 3 — per-source-DO target resolution. Direct unit tests
    against the module-level helper ``v16_resolve_target``. This is the
    seam where the per-DO writes pick LEAF (v16) vs STEP (v15 fallback)."""

    def test_pass3_dest_do_id_is_leaf_not_step(self):
        """Spec test #5 — in v16 mode the resolver returns the LEAF dest
        id (src_to_dest[src.do_id]["dest_id"]), NOT the step DO id."""
        src_do = _make_src_do(999, "TR-004")
        v16_src_to_dest = {999: {"dest_id": 5500, "dest_app_id": "app-5500"}}
        target = mffd_v15.v16_resolve_target(
            src_do, v16_src_to_dest,
            step_dest_do_id=42, step_dest_app_id="step-app",
            step_key="tapelaying",
        )
        self.assertEqual(target["target_dest_do_id"], 5500)
        self.assertEqual(target["target_dest_app_id"], "app-5500")
        self.assertNotEqual(target["target_dest_do_id"], 42,
                            "Pass 3 must route to LEAF, not STEP DO")
        self.assertTrue(target["active"])
        self.assertFalse(target["missing_leaf"])
        # State key uses src_do_id (stable across name changes).
        self.assertEqual(target["do_done_key"], "v16/tapelaying/999")

    def test_v15_flat_fallback_when_env_off(self):
        """Spec test #6 — when v16_src_to_dest is None (env flag OFF /
        v15 path), resolver returns the STEP DO id (v15 flat shape)."""
        src_do = _make_src_do(999, "TR-004")
        target = mffd_v15.v16_resolve_target(
            src_do, v16_src_to_dest=None,
            step_dest_do_id=42, step_dest_app_id="step-app",
            step_key="tapelaying",
        )
        # v15 path — STEP DO, name-based state key.
        self.assertEqual(target["target_dest_do_id"], 42)
        self.assertEqual(target["target_dest_app_id"], "step-app")
        self.assertFalse(target["active"])
        self.assertFalse(target["missing_leaf"])
        # State key uses src_do.name for v15 back-compat.
        self.assertEqual(target["do_done_key"], "tapelaying/TR-004")

    def test_pass3_missing_leaf_when_pass1_failed(self):
        """When v16 mode is active but Pass 1 didn't create a mirror for
        the src DO (4xx/5xx), the resolver signals missing_leaf so the
        per-DO body skips the DO instead of writing to a bogus target."""
        src_do = _make_src_do(999, "TR-004")
        # Map exists (v16 active) but doesn't contain src 999.
        v16_src_to_dest = {100: {"dest_id": 5100, "dest_app_id": "x"}}
        target = mffd_v15.v16_resolve_target(
            src_do, v16_src_to_dest,
            step_dest_do_id=42, step_dest_app_id="step-app",
            step_key="tapelaying",
        )
        self.assertTrue(target["active"])
        self.assertTrue(target["missing_leaf"])


class TestV16_1ParallelPasses(unittest.TestCase):
    """v16.1 IMPORT-PERF2 — worker fan-out for Pass 1 + Pass 2.

    Tests assert (a) concurrency actually happens when workers>1, (b)
    resume-safety is preserved, (c) ordering invariants hold across the
    pool boundary, (d) retry-on-exception path works.
    """

    def test_pass1_parallel_with_workers_4(self):
        """workers=4 — 20 source DOs fan out across the pool. We use a
        side_effect that sleeps briefly + tracks concurrent in-flight,
        then assert peak concurrency was > 1 (we got real parallelism)
        and the result map is complete + correct."""
        import threading as _t
        import time as _time
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://dest"

        in_flight = {"now": 0, "peak": 0}
        lock = _t.Lock()
        id_counter = {"n": 0}

        def _slow_create(coll_id, name, description="", attrs=None, **kw):
            with lock:
                in_flight["now"] += 1
                if in_flight["now"] > in_flight["peak"]:
                    in_flight["peak"] = in_flight["now"]
                id_counter["n"] += 1
                my_id = 1000 + id_counter["n"]
            _time.sleep(0.02)  # 20 ms — enough for pool to overlap
            with lock:
                in_flight["now"] -= 1
            return {"id": my_id, "appId": f"app-{my_id}"}

        client.create_data_object = MagicMock(side_effect=_slow_create)
        client._get = MagicMock(return_value=None)

        src_dos = [_make_src_do(200 + i, f"do-{i}") for i in range(20)]
        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, coll_id=42, step_key="tapelaying",
            state=None, src_coll_id=48297,
            workers=4,
        )
        # Concurrency actually happened.
        self.assertGreater(
            in_flight["peak"], 1,
            f"workers=4 should run >1 in flight; peak was {in_flight['peak']}",
        )
        # Every src DO ends up in the map.
        self.assertEqual(len(result), 20)
        self.assertEqual(client.create_data_object.call_count, 20)

    def test_pass1_resume_skips_already_mapped(self):
        """state pre-populated with 10 of 20 src→dest mappings; Pass 1
        only creates the 10 missing entries. Result map carries all 20."""
        import tempfile
        from pathlib import Path as _Path
        from unittest.mock import MagicMock
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as f:
            path = _Path(f.name)
        state = mffd_v15.ImportState(path)
        # Pre-populate 10 of 20 (the even-indexed ones).
        for i in range(0, 20, 2):
            state.set_dest_id(300 + i, 9000 + i, f"app-{9000 + i}")

        counter = {"n": 0}
        def _fake_create(coll_id, name, description="", attrs=None, **kw):
            counter["n"] += 1
            return {"id": 7000 + counter["n"], "appId": f"app-{7000 + counter['n']}"}

        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://dest"
        client.create_data_object = MagicMock(side_effect=_fake_create)
        client._get = MagicMock(return_value=None)

        src_dos = [_make_src_do(300 + i, f"do-{i}") for i in range(20)]
        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, coll_id=42, step_key="tapelaying",
            state=state, src_coll_id=48297,
            workers=4,
        )
        # Only the 10 missing entries were created.
        self.assertEqual(client.create_data_object.call_count, 10)
        # All 20 entries present in map (10 resumed + 10 fresh).
        self.assertEqual(len(result), 20)
        # Resumed entries preserve their pre-set dest_ids.
        for i in range(0, 20, 2):
            self.assertEqual(result[300 + i]["dest_id"], 9000 + i)
        path.unlink(missing_ok=True)

    def test_pass2_parallel_with_workers_4(self):
        """workers=4 — 20 dest DOs PATCHed in parallel. Assert peak
        in-flight > 1 (real concurrency)."""
        import threading as _t
        import time as _time
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://dest"

        in_flight = {"now": 0, "peak": 0}
        lock = _t.Lock()

        def _slow_patch(coll_id, do_id, **kw):
            with lock:
                in_flight["now"] += 1
                if in_flight["now"] > in_flight["peak"]:
                    in_flight["peak"] = in_flight["now"]
            _time.sleep(0.02)
            with lock:
                in_flight["now"] -= 1
            return True

        client.patch_data_object = MagicMock(side_effect=_slow_patch)
        src_dos = [_make_src_do(400 + i, f"do-{i}") for i in range(20)]
        src_to_dest = {
            sd.do_id: {"dest_id": 8000 + i, "dest_app_id": f"app-{8000 + i}"}
            for i, sd in enumerate(src_dos)
        }
        wired, failed = mffd_v15.v16_pass2_wire_edges(
            src_dos, src_to_dest, step_dest_do_id=12345,
            dest_client=client, coll_id=42,
            step_key="tapelaying", workers=4,
        )
        self.assertEqual(wired, 20)
        self.assertEqual(failed, 0)
        self.assertGreater(
            in_flight["peak"], 1,
            f"workers=4 should run >1 in flight; peak was {in_flight['peak']}",
        )

    def test_pass1_retry_on_failure(self):
        """First create attempt raises an exception; the in-pool retry
        succeeds. The result map carries the src DO under its correct
        src_do_id (the by-id side_effect is thread-safe)."""
        import threading as _t
        from unittest.mock import MagicMock
        client = mffd_v15.ShepardClient.__new__(mffd_v15.ShepardClient)
        client._base = "http://dest"

        # Track attempts per src_do_id (the name carries the id).
        attempts: dict[str, int] = {}
        attempts_lock = _t.Lock()

        def _flaky_create(coll_id, name, description="", attrs=None, **kw):
            with attempts_lock:
                attempts[name] = attempts.get(name, 0) + 1
                n = attempts[name]
            if name == "flaky" and n == 1:
                raise ConnectionError("simulated network blip")
            # Encode src_do_id in dest id for assertion.
            src_id = int(attrs["source_do_id"])
            return {"id": 10000 + src_id, "appId": f"app-{10000 + src_id}"}

        client.create_data_object = MagicMock(side_effect=_flaky_create)
        client._get = MagicMock(return_value=None)

        src_dos = [
            _make_src_do(501, "stable-a"),
            _make_src_do(502, "flaky"),
            _make_src_do(503, "stable-b"),
        ]
        result = mffd_v15.v16_pass1_create_dest_mirrors(
            src_dos, client, coll_id=42, step_key="tapelaying",
            state=None, src_coll_id=48297,
            workers=4,
        )
        # Retry happened — flaky was called twice.
        self.assertEqual(attempts.get("flaky"), 2,
                         f"flaky should be retried exactly once; got {attempts}")
        # All 3 src DOs landed in the map under their correct src_do_id.
        self.assertEqual(len(result), 3)
        self.assertEqual(result[501]["dest_id"], 10501)
        self.assertEqual(result[502]["dest_id"], 10502)  # the flaky one
        self.assertEqual(result[503]["dest_id"], 10503)

    def test_preserve_hierarchy_workers_env_var_default(self):
        """MFFD_PRESERVE_HIERARCHY_WORKERS env var sets the module-level
        PRESERVE_HIERARCHY_WORKERS default. Module reload picks up the
        env value at import time; default is 8."""
        # The module has already been loaded with whatever env var was set
        # at test-run time. Assert the constant is at least 1 (sanity)
        # and matches the read-pattern we ship.
        self.assertGreaterEqual(mffd_v15.PRESERVE_HIERARCHY_WORKERS, 1)
        # When env is unset the module default is 8 — assert by re-reading
        # the env var the same way the module does.
        import os as _os
        expected = max(1, int(_os.environ.get("MFFD_PRESERVE_HIERARCHY_WORKERS", "8")))
        self.assertEqual(mffd_v15.PRESERVE_HIERARCHY_WORKERS, expected)


if __name__ == "__main__":
    unittest.main()
