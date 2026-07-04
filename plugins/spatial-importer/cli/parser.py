"""Parser for the two ASCII pointcloud formats that the MFFD AFP tapelaying
export ships per Track folder.

Format (both files):
    Six whitespace-separated columns, CRLF line endings, no header:
        X  Y  Z  R  G  B
    where:
      - X, Y, Z are floats in millimetres in the AFP TCP frame.
      - R, G, B are integers 0..255 — but in the real export they are *always*
        the literal triple ``0 130 255`` (the Keyence default visualisation
        colour). We treat the RGB columns as an export artefact and drop them
        at parse time. Storing the false signal would poison downstream
        colour-mapped renders.

Empirically confirmed against:
  - Track_66__Run_23133_/files/TPS 3D pointclouds.0          (4118 rows, RGB constant)
  - Track_66__Run_23133_/files/FSD course 3D pointclouds     (4168 rows, RGB constant)
  - Track_67__Run_24043_/files/TPS 3D pointclouds.0          (varies XYZ, RGB constant)
  - Track_67__Run_24043_/files/TPS 3D pointclouds.1          (twin of .0, RGB constant)
  - Track_67__Run_24043_/files/FSD course 3D pointclouds     (varies X+Y+Z, RGB constant)

See the parent module docstring for the W7 wave context.

If a future export ever ships real per-point RGB (i.e. the (R,G,B) triple
varies row-to-row), the parser will detect that and preserve it as a
``color_rgb`` measurement entry — see ``_rgb_is_uniform`` below.
"""

from __future__ import annotations

import hashlib
import io
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Iterator, Sequence


@dataclass(frozen=True)
class PointcloudPoint:
    """A single point from a TPS 3D pointcloud or FSD trajectory file.

    All coordinates are in millimetres in the source file's coordinate frame
    (AFP TCP frame per ``aidocs/agent-findings/mffd-afp-spatial-analysis-cases.md``).
    """

    x: float
    y: float
    z: float
    # Per-point RGB is only populated when the file's RGB column actually varies.
    # In MFFD exports it does not, so this is None for every observed file.
    r: int | None = None
    g: int | None = None
    b: int | None = None


@dataclass(frozen=True)
class PointcloudFile:
    """Parsed contents of one ASCII pointcloud/trajectory file."""

    points: tuple[PointcloudPoint, ...]
    rgb_was_uniform: bool  # True if every point shared the same RGB (= export artefact).
    uniform_rgb: tuple[int, int, int] | None  # The shared triple when ``rgb_was_uniform``.
    sha256: str  # Stable idempotency key.

    @property
    def n_points(self) -> int:
        return len(self.points)

    @property
    def bbox_min(self) -> tuple[float, float, float]:
        xs = [p.x for p in self.points]
        ys = [p.y for p in self.points]
        zs = [p.z for p in self.points]
        return (min(xs), min(ys), min(zs))

    @property
    def bbox_max(self) -> tuple[float, float, float]:
        xs = [p.x for p in self.points]
        ys = [p.y for p in self.points]
        zs = [p.z for p in self.points]
        return (max(xs), max(ys), max(zs))


class PointcloudParseError(ValueError):
    """Raised when a file cannot be parsed as MFFD ASCII pointcloud/trajectory."""


def _parse_row(row: str, lineno: int) -> PointcloudPoint:
    parts = row.strip().split()
    if len(parts) < 3:
        raise PointcloudParseError(
            f"line {lineno}: expected at least 3 columns (X Y Z), got {len(parts)}: {row!r}"
        )
    try:
        x = float(parts[0])
        y = float(parts[1])
        z = float(parts[2])
    except ValueError as exc:
        raise PointcloudParseError(
            f"line {lineno}: could not parse XYZ as floats: {row!r}"
        ) from exc

    r = g = b = None
    if len(parts) >= 6:
        try:
            r = int(parts[3])
            g = int(parts[4])
            b = int(parts[5])
        except ValueError:
            # Malformed RGB columns are treated as missing rather than a hard
            # error — XYZ is the contract; RGB is best-effort.
            r = g = b = None

    return PointcloudPoint(x=x, y=y, z=z, r=r, g=g, b=b)


def _rgb_is_uniform(points: Sequence[PointcloudPoint]) -> tuple[bool, tuple[int, int, int] | None]:
    """Return ``(True, (r,g,b))`` iff every point shares the same non-None RGB
    triple. ``(False, None)`` otherwise.
    """
    if not points:
        return (False, None)
    first = (points[0].r, points[0].g, points[0].b)
    if first[0] is None:
        return (False, None)
    for p in points[1:]:
        if (p.r, p.g, p.b) != first:
            return (False, None)
    return (True, first)  # type: ignore[return-value]


def parse_pointcloud_text(text: str) -> PointcloudFile:
    """Parse a CRLF/LF ASCII pointcloud/trajectory string.

    Raises ``PointcloudParseError`` on the first bad row.
    """
    points: list[PointcloudPoint] = []
    # Stable SHA256 over the canonical UTF-8 byte form so two callers reading
    # the same bytes always derive the same idempotency key.
    sha = hashlib.sha256(text.encode("utf-8")).hexdigest()

    for i, raw in enumerate(io.StringIO(text), start=1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#"):
            # No comment syntax seen in the wild, but cheap to support.
            continue
        points.append(_parse_row(line, i))

    if not points:
        raise PointcloudParseError("file contains no points")

    rgb_uniform, uniform = _rgb_is_uniform(points)
    return PointcloudFile(
        points=tuple(points),
        rgb_was_uniform=rgb_uniform,
        uniform_rgb=uniform,
        sha256=sha,
    )


def parse_pointcloud_file(path: Path) -> PointcloudFile:
    """Read and parse one pointcloud/trajectory file from disk."""
    text = path.read_text(encoding="utf-8", errors="strict")
    return parse_pointcloud_text(text)


# -----------------------------------------------------------------------------
# kind classifier — maps a source filename to a SpatialDataContainer.kind
# -----------------------------------------------------------------------------

SPATIAL_KIND_PROFILE = "profile"
SPATIAL_KIND_TRAJECTORY = "trajectory"

# These are the *Shepard* spatial kinds we promote into. The PostGIS hypertable
# stores them on its `profile_kind` column. The naming is borrowed from
# aidocs/data/90 §3 and the spatiotemporal plugin's reference.md.


def classify_kind(filename: str) -> str | None:
    """Map a source file's *basename* to a SpatialDataContainer kind.

    Returns ``None`` if the filename doesn't match any known shape.
    """
    name = filename.strip()
    if name.startswith("TPS 3D pointclouds"):
        return SPATIAL_KIND_PROFILE
    if name == "FSD course 3D pointclouds":
        return SPATIAL_KIND_TRAJECTORY
    return None


def iter_track_spatial_files(track_dir: Path) -> Iterator[tuple[Path, str]]:
    """Yield ``(file_path, spatial_kind)`` pairs for every spatial file in a
    ``Track_NN__Run_NN_/files/`` directory.

    Files that aren't pointclouds/trajectories (PNGs, robot programs,
    intermediate evaluation files) are skipped.
    """
    files_dir = track_dir / "files"
    if not files_dir.is_dir():
        return
    for entry in sorted(files_dir.iterdir()):
        if not entry.is_file():
            continue
        kind = classify_kind(entry.name)
        if kind is None:
            continue
        yield (entry, kind)
