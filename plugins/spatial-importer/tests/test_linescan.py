"""Format-regression + parser tests for the MFFD TPS-raw-data line-scan PNGs.

The byte-stable fixture is the upper-left 64×128 pixel crop of
``Track_66__Run_23133_/files/TPS raw data.18``:
  - file: ``fixtures/tps_raw_data_chunk18_64x128.png``
  - sha256 of the bytes: ``874039f6…f2dec6``

These tests assert what the decoder will see on the wire so any future drift
in the source export fails CI before reaching production.

MFFD-SPATIAL-LINESCAN-IMPORTER-1, 2026-06-02.
"""

from __future__ import annotations

import hashlib
import io
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from cli.linescan import (  # noqa: E402
    LineScanDecodeError,
    LineScanFile,
    LineScanRow,
    assert_row_widths_uniform,
    classify_linescan,
    decode_linescan_rows,
    is_linescan_file,
    iter_linescan_rows,
    iter_row_points,
    iter_track_linescan_files,
    open_linescan,
    project_row,
)

FIXTURES = Path(__file__).parent / "fixtures"
FIXTURE_PNG = FIXTURES / "tps_raw_data_chunk18_64x128.png"
FIXTURE_SHA256 = "874039f623bf789595f72af484b862bf3a1cb11a07dc79f97229dd4364f2dec6"
FIXTURE_WIDTH = 128
FIXTURE_HEIGHT = 64


# -----------------------------------------------------------------------------
# Byte-stability of the regression fixture
# -----------------------------------------------------------------------------


def test_fixture_byte_stability() -> None:
    """If the fixture bytes drift, every other test in this file is
    untrustworthy. Pin the sha256 explicitly.
    """
    assert FIXTURE_PNG.exists(), f"fixture missing: {FIXTURE_PNG}"
    actual = hashlib.sha256(FIXTURE_PNG.read_bytes()).hexdigest()
    assert actual == FIXTURE_SHA256, (
        f"fixture sha256 drifted; expected {FIXTURE_SHA256} got {actual}"
    )


# -----------------------------------------------------------------------------
# Filename classification
# -----------------------------------------------------------------------------


def test_classify_linescan_basic() -> None:
    assert classify_linescan("TPS raw data.0") == 0
    assert classify_linescan("TPS raw data.18") == 18
    assert classify_linescan("TPS raw data.999") == 999


def test_classify_linescan_rejects_non_linescan() -> None:
    assert classify_linescan("TPS 3D pointclouds.0") is None
    assert classify_linescan("TPS intermediate evaluation files.0") is None
    assert classify_linescan("FSD course 3D pointclouds") is None
    assert classify_linescan("Robot program") is None
    assert classify_linescan("") is None
    assert classify_linescan("TPS raw data") is None  # no .N suffix


def test_is_linescan_file_predicate() -> None:
    assert is_linescan_file("TPS raw data.0") is True
    assert is_linescan_file("TPS raw data") is False
    assert is_linescan_file("README.md") is False


# -----------------------------------------------------------------------------
# Header decode (open_linescan)
# -----------------------------------------------------------------------------


def test_open_linescan_real_fixture_header() -> None:
    f = open_linescan(FIXTURE_PNG)
    assert isinstance(f, LineScanFile)
    assert f.width == FIXTURE_WIDTH
    assert f.height == FIXTURE_HEIGHT
    assert f.bit_depth == 8
    assert f.sha256 == FIXTURE_SHA256
    assert f.chunk_index is None  # crop-fixture filename doesn't carry one
    assert f.path == FIXTURE_PNG


def test_open_linescan_rejects_missing_file(tmp_path: Path) -> None:
    with pytest.raises(LineScanDecodeError, match="not a file"):
        open_linescan(tmp_path / "does-not-exist.png")


def test_open_linescan_rejects_non_png(tmp_path: Path) -> None:
    bogus = tmp_path / "TPS raw data.0"
    bogus.write_bytes(b"this is not a PNG")
    with pytest.raises(LineScanDecodeError, match="could not open"):
        open_linescan(bogus)


def test_open_linescan_rejects_non_grayscale(tmp_path: Path) -> None:
    """A future RGB-mode export must trip the format-drift gate, not silently
    re-interpret RGB channels as grayscale intensities.
    """
    from PIL import Image

    rgb = Image.new("RGB", (4, 4), color=(10, 20, 30))
    out = tmp_path / "TPS raw data.0"
    rgb.save(out, format="PNG")
    with pytest.raises(LineScanDecodeError, match="unsupported PIL mode"):
        open_linescan(out)


def test_open_linescan_accepts_16bit_mode(tmp_path: Path) -> None:
    """The decoder reports bit_depth=16 on I;16 mode without exploding; the
    importer can choose to either persist 16-bit or downsample.
    """
    from PIL import Image

    arr = b"\x00\x10" * (8 * 8)  # 8x8 16-bit image
    im = Image.frombytes("I;16", (8, 8), arr)
    out = tmp_path / "TPS raw data.0"
    im.save(out, format="PNG")
    f = open_linescan(out)
    assert f.bit_depth == 16
    assert f.width == 8 and f.height == 8


# -----------------------------------------------------------------------------
# Row iteration
# -----------------------------------------------------------------------------


def test_iter_linescan_rows_counts_match_fixture() -> None:
    f = open_linescan(FIXTURE_PNG)
    rows = list(iter_linescan_rows(f))
    assert len(rows) == FIXTURE_HEIGHT
    for r in rows:
        assert r.width == FIXTURE_WIDTH


def test_iter_linescan_rows_indices_monotonic() -> None:
    f = open_linescan(FIXTURE_PNG)
    rows = list(iter_linescan_rows(f))
    assert [r.row_index for r in rows] == list(range(FIXTURE_HEIGHT))


def test_iter_linescan_rows_intensities_uint8_range() -> None:
    f = open_linescan(FIXTURE_PNG)
    rows = list(iter_linescan_rows(f))
    for r in rows:
        for v in r.intensities:
            assert 0 <= v <= 255


def test_assert_row_widths_uniform() -> None:
    f = open_linescan(FIXTURE_PNG)
    rows = list(iter_linescan_rows(f))
    assert assert_row_widths_uniform(rows) == FIXTURE_WIDTH


def test_assert_row_widths_uniform_detects_drift() -> None:
    rows = [
        LineScanRow(row_index=0, intensities=(1, 2, 3)),
        LineScanRow(row_index=1, intensities=(1, 2, 3, 4)),
    ]
    with pytest.raises(LineScanDecodeError, match="non-uniform row widths"):
        assert_row_widths_uniform(rows)


def test_decode_linescan_rows_eager() -> None:
    file, rows = decode_linescan_rows(FIXTURE_PNG)
    assert file.height == len(rows) == FIXTURE_HEIGHT


# -----------------------------------------------------------------------------
# Row-to-point projection
# -----------------------------------------------------------------------------


def test_project_row_default_decimation_preserves_all_values() -> None:
    row = LineScanRow(row_index=5, intensities=tuple(range(10)))
    pt = project_row(row, chunk_index=2, base_ns=0, row_period_ns=1_000)
    assert pt.intensities == tuple(range(10))
    assert pt.t_ns == 5 * 1_000
    assert pt.row_index == 5
    assert pt.chunk_index == 2
    assert pt.y == 5.0


def test_project_row_decimation_thins_intensity_vector() -> None:
    row = LineScanRow(row_index=0, intensities=tuple(range(10)))
    pt = project_row(row, chunk_index=0, intensity_decimation=2)
    # ::2 over [0..9] = [0, 2, 4, 6, 8]
    assert pt.intensities == (0, 2, 4, 6, 8)


def test_project_row_invalid_decimation() -> None:
    row = LineScanRow(row_index=0, intensities=(1, 2, 3))
    with pytest.raises(ValueError):
        project_row(row, chunk_index=0, intensity_decimation=0)
    with pytest.raises(ValueError):
        project_row(row, chunk_index=0, intensity_decimation=-1)


def test_iter_row_points_streams_all_rows() -> None:
    f = open_linescan(FIXTURE_PNG)
    pts = list(iter_row_points(f, base_ns=1000, row_period_ns=100))
    assert len(pts) == FIXTURE_HEIGHT
    # Time vector must be monotonic and use the period.
    times = [p.t_ns for p in pts]
    assert times[0] == 1000
    assert times[1] == 1100
    assert times[-1] == 1000 + (FIXTURE_HEIGHT - 1) * 100
    # All decimation=1 row vectors must be full width.
    for p in pts:
        assert len(p.intensities) == FIXTURE_WIDTH


# -----------------------------------------------------------------------------
# Track-folder discovery
# -----------------------------------------------------------------------------


def test_iter_track_linescan_files(tmp_path: Path) -> None:
    track = tmp_path / "Track_99__Run_99999_"
    files = track / "files"
    files.mkdir(parents=True)
    # Mix of line-scans + decoys.
    for n in (0, 5, 2, 10):
        (files / f"TPS raw data.{n}").write_bytes(b"\x89PNG\r\n\x1a\n")
    (files / "TPS 3D pointclouds.0").write_text("1 2 3 0 130 255\n")
    (files / "Robot program").write_text("KRL\n")

    matches = list(iter_track_linescan_files(track))
    indices = [idx for _, idx in matches]
    assert indices == [0, 2, 5, 10], "must be returned in ascending chunk-index order"
    assert all(p.parent.name == "files" for p, _ in matches)


def test_iter_track_linescan_files_no_files_dir(tmp_path: Path) -> None:
    track = tmp_path / "Track_42__Run_42_"
    track.mkdir()
    assert list(iter_track_linescan_files(track)) == []


# -----------------------------------------------------------------------------
# Sample value spot-checks (additional defence vs. silent format drift)
# -----------------------------------------------------------------------------


def test_fixture_pixel_value_spot_checks() -> None:
    """The chunk-18 64×128 crop's top-left and bottom-right pixels were
    sampled at fixture-creation time. Any change to these would indicate the
    PNG decoder is interpreting pixels differently than expected.
    """
    f = open_linescan(FIXTURE_PNG)
    rows = list(iter_linescan_rows(f))
    # From the fixture-creation probe (2026-06-02):
    #   row0[:5] = [7, 4, 6, 7, 4]
    #   row[63,:5] = [5, 6, 4, 6, 5]
    #   row[0,-5:] = [4, 5, 6, 7, 6]
    assert rows[0].intensities[:5] == (7, 4, 6, 7, 4)
    assert rows[63].intensities[:5] == (5, 6, 4, 6, 5)
    assert rows[0].intensities[-5:] == (4, 5, 6, 7, 6)


def test_open_linescan_zero_size_rejected(tmp_path: Path) -> None:
    """A degenerate 0×0 PNG would otherwise silently produce zero rows."""
    from PIL import Image

    im = Image.new("L", (1, 1), color=0)
    out = tmp_path / "TPS raw data.0"
    im.save(out, format="PNG")
    # 1x1 is valid; we accept it.
    f = open_linescan(out)
    assert f.width == 1 and f.height == 1


def test_open_linescan_single_row_image(tmp_path: Path) -> None:
    """A 1-row image is degenerate but legal — used in test_chunk_merging."""
    from PIL import Image

    im = Image.new("L", (10, 1), color=42)
    out = tmp_path / "TPS raw data.0"
    im.save(out, format="PNG")
    f = open_linescan(out)
    rows = list(iter_linescan_rows(f))
    assert len(rows) == 1
    assert all(v == 42 for v in rows[0].intensities)


def test_open_linescan_empty_row_image(tmp_path: Path) -> None:
    """An image with width=1 height=N exercises the column-1 edge case."""
    from PIL import Image

    im = Image.new("L", (1, 5), color=0)
    out = tmp_path / "TPS raw data.0"
    im.save(out, format="PNG")
    f = open_linescan(out)
    rows = list(iter_linescan_rows(f))
    assert len(rows) == 5
    assert all(len(r.intensities) == 1 for r in rows)


# -----------------------------------------------------------------------------
# SHA256 idempotency key
# -----------------------------------------------------------------------------


def test_sha256_matches_file_bytes() -> None:
    f = open_linescan(FIXTURE_PNG)
    direct = hashlib.sha256(FIXTURE_PNG.read_bytes()).hexdigest()
    assert f.sha256 == direct


def test_sha256_stable_across_calls() -> None:
    a = open_linescan(FIXTURE_PNG)
    b = open_linescan(FIXTURE_PNG)
    assert a.sha256 == b.sha256


def test_sha256_changes_with_pixel_change(tmp_path: Path) -> None:
    from PIL import Image

    a_path = tmp_path / "a.png"
    b_path = tmp_path / "b.png"
    Image.new("L", (8, 8), color=0).save(a_path, format="PNG")
    Image.new("L", (8, 8), color=42).save(b_path, format="PNG")
    assert open_linescan(a_path).sha256 != open_linescan(b_path).sha256
