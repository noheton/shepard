"""Format-regression tests for the MFFD ASCII pointcloud/trajectory parser.

The fixtures in ``tests/fixtures/`` are the first 50 lines of two real source
files captured 2026-06-02:
  - ``tps_pointcloud_sample.txt`` ← Track_66__Run_23133_/files/TPS 3D pointclouds.0
  - ``fsd_trajectory_sample.txt`` ← Track_66__Run_23133_/files/FSD course 3D pointclouds

If the export format ever changes, these tests will catch the drift.
"""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

import pytest

# Make the CLI package importable when pytest is invoked from the worktree root.
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from cli.parser import (  # noqa: E402  — sys.path massage above
    SPATIAL_KIND_PROFILE,
    SPATIAL_KIND_TRAJECTORY,
    PointcloudFile,
    PointcloudParseError,
    classify_kind,
    iter_track_spatial_files,
    parse_pointcloud_file,
    parse_pointcloud_text,
)

FIXTURES = Path(__file__).parent / "fixtures"


def test_parse_tps_pointcloud_fixture_count() -> None:
    cloud = parse_pointcloud_file(FIXTURES / "tps_pointcloud_sample.txt")
    assert cloud.n_points == 50, "TPS sample is the first 50 lines of the real file"


def test_parse_fsd_trajectory_fixture_count() -> None:
    cloud = parse_pointcloud_file(FIXTURES / "fsd_trajectory_sample.txt")
    assert cloud.n_points == 50


def test_tps_pointcloud_xyz_first_row() -> None:
    cloud = parse_pointcloud_file(FIXTURES / "tps_pointcloud_sample.txt")
    first = cloud.points[0]
    # Real bytes from Track_66__Run_23133_/files/TPS 3D pointclouds.0 line 1:
    #   296.718231 -266.534088 -46.035942 0 130 255
    assert first.x == pytest.approx(296.718231)
    assert first.y == pytest.approx(-266.534088)
    assert first.z == pytest.approx(-46.035942)


def test_fsd_trajectory_xyz_first_row() -> None:
    cloud = parse_pointcloud_file(FIXTURES / "fsd_trajectory_sample.txt")
    first = cloud.points[0]
    # Real bytes line 1:
    #   279.940000 -255.225000 -6.511000 0 130 255
    assert first.x == pytest.approx(279.940000)
    assert first.y == pytest.approx(-255.225000)
    assert first.z == pytest.approx(-6.511000)


def test_rgb_uniform_artefact_detected() -> None:
    # Both fixtures have the constant (0, 130, 255) export-default RGB. The
    # parser must flag this so the importer can omit per-point colour from
    # measurements.
    cloud = parse_pointcloud_file(FIXTURES / "tps_pointcloud_sample.txt")
    assert cloud.rgb_was_uniform is True
    assert cloud.uniform_rgb == (0, 130, 255)

    cloud = parse_pointcloud_file(FIXTURES / "fsd_trajectory_sample.txt")
    assert cloud.rgb_was_uniform is True
    assert cloud.uniform_rgb == (0, 130, 255)


def test_rgb_uniform_false_when_rgb_varies() -> None:
    # If a future export ever ships real per-point colour, the parser must
    # see it. Synthesise a tiny file with varying RGB.
    text = "1.0 2.0 3.0 10 20 30\n4.0 5.0 6.0 40 50 60\n"
    cloud = parse_pointcloud_text(text)
    assert cloud.rgb_was_uniform is False
    assert cloud.uniform_rgb is None


def test_bbox_min_max_calculation() -> None:
    cloud = parse_pointcloud_file(FIXTURES / "tps_pointcloud_sample.txt")
    bmin = cloud.bbox_min
    bmax = cloud.bbox_max
    # XYZ in mm; the first 50 lines of the TPS file occupy a tight slab in
    # Y/Z while X drifts slightly. Sanity-check directionality.
    assert bmin[0] <= bmax[0]
    assert bmin[1] <= bmax[1]
    assert bmin[2] <= bmax[2]


def test_sha256_is_stable_across_calls() -> None:
    a = parse_pointcloud_file(FIXTURES / "tps_pointcloud_sample.txt")
    b = parse_pointcloud_file(FIXTURES / "tps_pointcloud_sample.txt")
    assert a.sha256 == b.sha256
    # Direct hash of the bytes must also match — the idempotency key is the
    # SHA256 of the bytes, not of the parsed structure.
    raw = (FIXTURES / "tps_pointcloud_sample.txt").read_text(encoding="utf-8")
    assert a.sha256 == hashlib.sha256(raw.encode("utf-8")).hexdigest()


def test_sha256_differs_between_files() -> None:
    a = parse_pointcloud_file(FIXTURES / "tps_pointcloud_sample.txt")
    b = parse_pointcloud_file(FIXTURES / "fsd_trajectory_sample.txt")
    assert a.sha256 != b.sha256


def test_empty_file_raises() -> None:
    with pytest.raises(PointcloudParseError, match="no points"):
        parse_pointcloud_text("")


def test_malformed_row_raises_with_line_number() -> None:
    text = "1.0 2.0 3.0 0 130 255\nGARBAGE\n"
    with pytest.raises(PointcloudParseError, match="line 2"):
        parse_pointcloud_text(text)


def test_classify_kind_tps_3d_pointclouds() -> None:
    assert classify_kind("TPS 3D pointclouds.0") == SPATIAL_KIND_PROFILE
    assert classify_kind("TPS 3D pointclouds.1") == SPATIAL_KIND_PROFILE
    assert classify_kind("TPS 3D pointclouds.99") == SPATIAL_KIND_PROFILE


def test_classify_kind_fsd_course() -> None:
    assert classify_kind("FSD course 3D pointclouds") == SPATIAL_KIND_TRAJECTORY


def test_classify_kind_rejects_non_spatial() -> None:
    # PNG raw frames and intermediate evaluation files must not be promoted.
    assert classify_kind("TPS raw data.0") is None
    assert classify_kind("TPS intermediate evaluation files.0") is None
    assert classify_kind("Robot program") is None
    assert classify_kind("") is None


def test_classify_kind_rejects_random_names() -> None:
    assert classify_kind("README.md") is None
    assert classify_kind("file.txt") is None


def test_iter_track_spatial_files(tmp_path: Path) -> None:
    track = tmp_path / "Track_99__Run_99999_"
    files = track / "files"
    files.mkdir(parents=True)
    # Create three pointclouds, one trajectory, and a few decoys.
    (files / "TPS 3D pointclouds.0").write_text("1.0 2.0 3.0 0 130 255\n")
    (files / "TPS 3D pointclouds.1").write_text("1.0 2.0 3.0 0 130 255\n")
    (files / "FSD course 3D pointclouds").write_text("1.0 2.0 3.0 0 130 255\n")
    (files / "TPS raw data.0").write_text("png\n")
    (files / "Robot program").write_text("KRL\n")

    matches = list(iter_track_spatial_files(track))
    kinds = sorted(k for _, k in matches)
    assert kinds == [SPATIAL_KIND_PROFILE, SPATIAL_KIND_PROFILE, SPATIAL_KIND_TRAJECTORY]
    assert all(p.parent.name == "files" for p, _ in matches)


def test_iter_track_spatial_files_no_files_dir(tmp_path: Path) -> None:
    track = tmp_path / "Track_99__Run_99999_"
    track.mkdir()
    # No files/ subdir → no yields, no error.
    assert list(iter_track_spatial_files(track)) == []


def test_parse_handles_lf_only_endings() -> None:
    # The real files are CRLF, but the parser must not crash on LF-only too.
    text = "1.0 2.0 3.0 0 130 255\n4.0 5.0 6.0 0 130 255\n"
    cloud = parse_pointcloud_text(text)
    assert cloud.n_points == 2


def test_parse_skips_blank_lines() -> None:
    text = "1.0 2.0 3.0 0 130 255\n\n4.0 5.0 6.0 0 130 255\n"
    cloud = parse_pointcloud_text(text)
    assert cloud.n_points == 2
