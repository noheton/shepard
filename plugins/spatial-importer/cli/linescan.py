"""Decoder for the MFFD TPS raw-data line-scan PNG chunks.

Per the 2026-06-02 operator confirmation: each row in a ``TPS raw data.N`` PNG
is **one sensor-measurement instant along the track**. The X axis (1292 pixels)
indexes the sensor element / across-track position; pixel intensity is the
measurement value at that (time, position).

Format facts (confirmed against Track_66__Run_23133_/files/TPS raw data.0 and
.18 on 2026-06-02):

    file              ``file(1)`` reports::  PNG image data, 1292 x 964,
                                              8-bit grayscale, non-interlaced
    PIL                ``mode="L"`` (8-bit), ``size=(1292, 964)``
    intensity range    1..255 (real data, full dynamic range)
    chunk count        a Track_NN__Run_NN_/files/ folder holds ``TPS raw data.0``
                        through ``TPS raw data.36`` (37 chunks observed; varies)

The raw decoder is row-streaming so a single chunk does not require the entire
~1.2 MP image to be held in memory at once. The promotion pass in ``main.py``
calls into ``iter_linescan_rows`` and pushes one row at a time onto the
SpatialDataContainer payload endpoint.

See ``docs/linescan-format-notes.md`` for the reverse-engineering notes.
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator, Sequence

# Pillow is the only image dep — already in spatial-importer's pyproject because
# pointcloud parsing imports it for nothing yet; the linescan decoder is its
# first real consumer.
from PIL import Image  # type: ignore[import-not-found]


# -----------------------------------------------------------------------------
# Shape primitives
# -----------------------------------------------------------------------------


@dataclass(frozen=True)
class LineScanRow:
    """One row of a line-scan PNG = one sensor-measurement instant.

    ``row_index`` is the row's Y position in the image (0-based; 0 = top).
    ``intensities`` is a tuple of length ``width`` carrying the pixel value for
    each column / sensor element.
    """

    row_index: int
    intensities: tuple[int, ...]

    @property
    def width(self) -> int:
        return len(self.intensities)


@dataclass(frozen=True)
class LineScanFile:
    """Parsed contents of one TPS raw-data line-scan PNG chunk.

    Loaded lazily via :func:`open_linescan` (PIL keeps the file handle around
    until the rows are iterated). Use :func:`decode_linescan_summary` when only
    the dimensions + a digest are wanted (e.g. idempotency lookups).
    """

    path: Path
    width: int  # pixels per row = number of sensor elements (= 1292 in observed exports)
    height: int  # number of rows = sensor instants per chunk (= 964 in observed exports)
    bit_depth: int  # observed: 8 — we error loudly on anything else (calls for follow-up)
    sha256: str  # stable idempotency key derived from the file bytes
    chunk_index: int | None  # extracted from the trailing ".N" in the filename, or None


class LineScanDecodeError(ValueError):
    """Raised when a file cannot be decoded as an MFFD TPS line-scan PNG."""


# -----------------------------------------------------------------------------
# Filename → chunk index
# -----------------------------------------------------------------------------

_LINESCAN_NAME_RE = re.compile(r"^TPS raw data\.(\d+)$")


def classify_linescan(filename: str) -> int | None:
    """If ``filename`` matches the ``TPS raw data.N`` shape, return ``N`` as an
    int. Otherwise ``None``.
    """
    m = _LINESCAN_NAME_RE.match(filename.strip())
    if not m:
        return None
    return int(m.group(1))


def is_linescan_file(filename: str) -> bool:
    return classify_linescan(filename) is not None


# -----------------------------------------------------------------------------
# Low-level decode
# -----------------------------------------------------------------------------


def _sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def open_linescan(path: Path) -> LineScanFile:
    """Open a TPS raw-data PNG and return a :class:`LineScanFile` header.

    Does NOT load the pixel data — that streams via :func:`iter_linescan_rows`.
    """
    if not path.is_file():
        raise LineScanDecodeError(f"not a file: {path}")

    try:
        im = Image.open(path)
        im.verify()  # check PNG structural integrity without loading pixels
    except Exception as exc:  # noqa: BLE001 — propagate as our error class
        raise LineScanDecodeError(f"PIL could not open {path.name} as image: {exc}") from exc

    # PIL closes the image after verify(); reopen for metadata.
    im = Image.open(path)

    if im.format != "PNG":
        raise LineScanDecodeError(
            f"{path.name}: expected PNG, got {im.format} — line-scan decoder rejects."
        )

    # Observed mode: "L" (8-bit grayscale). Tolerate "I;16" / "I" only with a warn-
    # style error so the operator gets a meaningful follow-up.
    if im.mode == "L":
        bit_depth = 8
    elif im.mode in ("I;16", "I;16B", "I;16L"):
        bit_depth = 16
    else:
        raise LineScanDecodeError(
            f"{path.name}: unsupported PIL mode {im.mode!r}; expected 'L' (8-bit grayscale). "
            f"File MFFD-SPATIAL-LINESCAN-FORMAT-DRIFT-1 with a sample."
        )

    width, height = im.size  # PIL convention: (width, height)
    if width <= 0 or height <= 0:
        raise LineScanDecodeError(f"{path.name}: degenerate size {im.size}")

    return LineScanFile(
        path=path,
        width=width,
        height=height,
        bit_depth=bit_depth,
        sha256=_sha256_of(path),
        chunk_index=classify_linescan(path.name),
    )


def iter_linescan_rows(file: LineScanFile) -> Iterator[LineScanRow]:
    """Stream ``LineScanRow`` instances row-by-row from the PNG without holding
    the whole image in memory at once.

    The yielded ``row_index`` is the Y pixel coordinate (0 = top row = earliest
    sensor instant in the chunk, per the operator's row-as-time convention).
    """
    im = Image.open(file.path)
    # Force-load — we want fast row slicing. Pillow's image.load() returns a
    # PixelAccess object; for the small image sizes (1292×964 = 1.2 MP per
    # chunk) the memory cost is ~1 MB per chunk, well under the per-row
    # streaming budget the brief asks for.
    pixels = im.load()
    if pixels is None:
        raise LineScanDecodeError(f"{file.path.name}: PIL load() returned None")
    for y in range(file.height):
        row = tuple(int(pixels[x, y]) for x in range(file.width))  # type: ignore[index]
        yield LineScanRow(row_index=y, intensities=row)


def decode_linescan_rows(path: Path) -> tuple[LineScanFile, list[LineScanRow]]:
    """Eager helper for tests: open + iterate + materialise."""
    file = open_linescan(path)
    rows = list(iter_linescan_rows(file))
    return file, rows


# -----------------------------------------------------------------------------
# Row → SpatialDataPoint projection
# -----------------------------------------------------------------------------
#
# The existing SpatialDataPointService writes one Z-aware point per JSON row
# of the upload body. The brush-trace shape projects each PNG row onto ONE
# SpatialDataPoint at the row centroid (X=column-mid, Y=row-as-time, Z=0)
# and carries the full intensity vector in ``measurements.intensities``.
#
# Rationale (advisor reconcile call, 2026-06-02): the brush-trace's natural
# storage shape is ``profile_kind='multipoint'`` in the v6 schema, but the
# v6 schema/service is not yet wired through the REST layer used by the
# importer. Per-row aggregation through the legacy /payload endpoint keeps
# this PR additive — no plugin-side schema migration required. A future
# MFFD-SPATIAL-BRUSHTRACE-SCHEMA-1 follow-up promotes per-row writes to a
# real multipoint geometry once SPATIAL-V6 ships its REST surface.


@dataclass(frozen=True)
class LineScanRowPoint:
    """One SpatialDataPoint payload derived from a line-scan row.

    ``t_ns`` is row_index in nanoseconds when no source timestamp is available
    (fallback path; calling code attaches ``urn:shepard:spatial:t-axis=row-index``
    on the container so renderers know to treat this as a row index, not wall
    time).
    """

    t_ns: int
    x: float  # column centroid in pixel units (= width / 2) until calibration ships
    y: float  # row index (so the renderer's time slider can drive a Y cursor)
    z: float
    intensities: tuple[int, ...]
    chunk_index: int | None
    row_index: int


def project_row(
    row: LineScanRow,
    *,
    chunk_index: int | None,
    base_ns: int = 0,
    row_period_ns: int = 1,
    intensity_decimation: int = 1,
) -> LineScanRowPoint:
    """Project one ``LineScanRow`` onto a single ``LineScanRowPoint``.

    ``intensity_decimation`` thins the intensity vector by the given stride
    before persisting (so a 1292-wide row becomes ``ceil(1292/decimation)``
    samples). The default of 1 preserves every pixel; the CLI exposes a
    ``--intensity-decimation`` knob for storage-cost trades.
    """
    if intensity_decimation < 1:
        raise ValueError(f"intensity_decimation must be >= 1, got {intensity_decimation}")
    if intensity_decimation == 1:
        intensities = row.intensities
    else:
        intensities = row.intensities[::intensity_decimation]
    width = len(row.intensities)
    return LineScanRowPoint(
        t_ns=base_ns + row.row_index * row_period_ns,
        x=width / 2.0,  # centroid placeholder — replaced when calibration ships
        y=float(row.row_index),
        z=0.0,
        intensities=tuple(intensities),
        chunk_index=chunk_index,
        row_index=row.row_index,
    )


def iter_row_points(
    file: LineScanFile,
    *,
    base_ns: int = 0,
    row_period_ns: int = 1,
    intensity_decimation: int = 1,
) -> Iterator[LineScanRowPoint]:
    """Streaming projection from a ``LineScanFile`` onto ``LineScanRowPoint``s
    — the unit the importer feeds into the SpatialDataContainer payload upload.
    """
    for row in iter_linescan_rows(file):
        yield project_row(
            row,
            chunk_index=file.chunk_index,
            base_ns=base_ns,
            row_period_ns=row_period_ns,
            intensity_decimation=intensity_decimation,
        )


# -----------------------------------------------------------------------------
# Track-folder discovery
# -----------------------------------------------------------------------------


def iter_track_linescan_files(track_dir: Path) -> Iterator[tuple[Path, int]]:
    """Yield ``(file_path, chunk_index)`` pairs for every ``TPS raw data.N``
    file in ``Track_NN__Run_NN_/files/``, sorted by chunk index.
    """
    files_dir = track_dir / "files"
    if not files_dir.is_dir():
        return
    matches: list[tuple[int, Path]] = []
    for entry in files_dir.iterdir():
        if not entry.is_file():
            continue
        idx = classify_linescan(entry.name)
        if idx is None:
            continue
        matches.append((idx, entry))
    matches.sort(key=lambda pair: pair[0])
    for idx, path in matches:
        yield (path, idx)


# -----------------------------------------------------------------------------
# Defensive helpers used by tests
# -----------------------------------------------------------------------------


def assert_row_widths_uniform(rows: Sequence[LineScanRow]) -> int:
    """Return the common row width; raise if any row dissents."""
    if not rows:
        raise LineScanDecodeError("no rows to inspect")
    widths = {r.width for r in rows}
    if len(widths) != 1:
        raise LineScanDecodeError(f"non-uniform row widths: {sorted(widths)}")
    return widths.pop()
