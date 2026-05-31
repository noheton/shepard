"""Pydantic schemas for the KRL interpreter sidecar protocol.

Sidecar endpoint: POST /interpret
See aidocs/integrations/117-krl-interpreter.md §6 for the wire protocol.

Encoding note (KRL-INTEGRATION-MFFD-REAL-01-ENCODING-LATIN1):
Real KUKA .src files from ZLP Augsburg are saved by WorkVisual / KRL Editor
in ISO-8859-1 (Latin-1) or Windows-1252 (CP1252) — not UTF-8. The sidecar
accepts base64-encoded file payloads via srcContent and applies a three-step
decode waterfall (UTF-8 → ISO-8859-1 → CP1252 → errors=replace) so that
umlauts and Euro signs in variable names survive the round-trip.
"""

from __future__ import annotations

import base64
import re
from typing import Any

from pydantic import BaseModel, field_validator, model_validator


# ---------------------------------------------------------------------------
# Sub-schemas
# ---------------------------------------------------------------------------


class FrameOffset(BaseModel):
    """6-DOF Cartesian frame offset (translation + ZYX Euler angles in deg)."""

    model_config = {"populate_by_name": True}

    x: float = 0.0
    y: float = 0.0
    z: float = 0.0
    r: float = 0.0  # roll
    p: float = 0.0  # pitch
    yaw: float = 0.0  # yaw


class InterpretOptions(BaseModel):
    ik_tolerance: float = 1e-3
    max_iterations: int = 300
    max_ir_iterations: int = 100_000
    ast_dump: bool = False
    bco_as_wait: bool = True


# ---------------------------------------------------------------------------
# Main request schema
# ---------------------------------------------------------------------------


class InterpretRequest(BaseModel):
    """Body for POST /interpret.

    KRL source can be supplied as:
      - srcText   — already-decoded Unicode string (e.g. from a UTF-8 upload)
      - srcContent — base64-encoded raw bytes from the original file

    Exactly one of srcText / srcContent must be provided.

    URDF can be supplied as:
      - urdfPath    — filesystem path visible to the sidecar container
      - urdfContent — base64-encoded URDF bytes

    Exactly one of urdfPath / urdfContent must be provided.
    """

    # KRL source
    src_text: str | None = None
    src_content: str | None = None  # base64-encoded raw bytes

    # URDF
    urdf_path: str | None = None
    urdf_content: str | None = None  # base64-encoded URDF bytes

    # Optional companion .dat
    dat_content: str | None = None  # base64-encoded

    # IK / frame configuration
    base_frame: FrameOffset | None = None
    tool_frame: FrameOffset | None = None
    seed_pose: list[float] | str | None = None
    time_step: float = 0.01

    options: InterpretOptions = InterpretOptions()

    # Populated by the validator — downstream code reads srcText only.
    # Not part of the wire payload; computed server-side from srcContent.

    @model_validator(mode="before")
    @classmethod
    def _normalise_src_and_urdf(cls, data: Any) -> Any:  # noqa: ANN401
        """Decode srcContent bytes and validate mutual exclusivity.

        Encoding waterfall for srcContent (KRL-INTEGRATION-MFFD-REAL-01-ENCODING-LATIN1):
          1. Try UTF-8   — standard; most toolchains now default here.
          2. Try CP1252 (Windows-1252) — KRL editor on newer Windows hosts.
             CP1252 is tried before ISO-8859-1 because CP1252 is a strict superset
             for the printable range 0xA0-0xFF, but maps bytes 0x80-0x9F to useful
             characters (€, curly quotes, em-dash …) instead of C1 controls. A file
             with a Euro sign (0x80) is almost certainly CP1252-encoded, not Latin-1.
             CP1252 raises UnicodeDecodeError on its five undefined bytes
             (0x81, 0x8D, 0x8F, 0x90, 0x9D), so the waterfall is still meaningful.
          3. Try ISO-8859-1 (Latin-1) — WorkVisual default for older cells.
             Latin-1 maps every byte 0x00-0xFF, so it never raises. This step is
             the final "known-encoding" catch before the errors=replace fallback.
          4. Fall back to errors=replace — never raises; returns text with
             U+FFFD replacement characters for truly undecodable bytes.

        Lines 95-113 of this file implement the waterfall.
        """
        if isinstance(data, dict):
            src_content_b64 = data.get("src_content") or data.get("srcContent")
            src_text = data.get("src_text") or data.get("srcText")

            if src_content_b64 and not src_text:
                raw: bytes = base64.b64decode(src_content_b64)
                # --- encoding waterfall begins (lines 95-113) ---
                decoded: str | None = None
                for encoding in ("utf-8", "cp1252", "iso-8859-1"):
                    try:
                        decoded = raw.decode(encoding)
                        break
                    except (UnicodeDecodeError, LookupError):
                        continue
                if decoded is None:
                    # Final fallback: replace undecodable bytes with U+FFFD
                    decoded = raw.decode("utf-8", errors="replace")
                # --- encoding waterfall ends ---
                # Normalise to canonical key names; clear src_content so the
                # "both" validator below sees only src_text.
                data = dict(data)
                data["src_text"] = decoded
                data["src_content"] = None  # consumed; clear to avoid "both" error
                if "srcContent" in data:
                    del data["srcContent"]
                if "srcText" in data:
                    data["src_text"] = data.pop("srcText")

            # Normalise camelCase aliases to snake_case for pydantic
            _ALIASES = {
                "srcText": "src_text",
                "srcContent": "src_content",
                "urdfPath": "urdf_path",
                "urdfContent": "urdf_content",
                "datContent": "dat_content",
                "baseFrame": "base_frame",
                "toolFrame": "tool_frame",
                "seedPose": "seed_pose",
                "timeStep": "time_step",
            }
            if isinstance(data, dict):
                data = {_ALIASES.get(k, k): v for k, v in data.items()}

        return data

    @model_validator(mode="after")
    def _validate_src_present(self) -> "InterpretRequest":
        if not self.src_text and not self.src_content:
            raise ValueError(
                "One of src_text or src_content is required"
            )
        if self.src_text and self.src_content:
            raise ValueError(
                "Provide src_text OR src_content, not both"
            )
        if not self.urdf_path and not self.urdf_content:
            raise ValueError(
                "One of urdf_path or urdf_content is required"
            )
        if self.urdf_path and self.urdf_content:
            raise ValueError(
                "Provide urdf_path OR urdf_content, not both"
            )
        return self

    # Convenience helpers -------------------------------------------------------

    @property
    def resolved_src_text(self) -> str:
        """Return the KRL source as a Unicode string (always available after validation)."""
        assert self.src_text is not None, "src_text should be set after validation"
        return self.src_text


# ---------------------------------------------------------------------------
# Response schemas
# ---------------------------------------------------------------------------


class IkSolverStats(BaseModel):
    mean_cycle_ms: float
    p99_cycle_ms: float
    max_residual_meters: float
    max_residual_radians: float
    failed_poses: int
    total_poses: int


class Warning(BaseModel):
    line: int
    message: str
    severity: str  # INFO | WARN | ERROR


class UnsupportedConstruct(BaseModel):
    construct_name: str  # 'construct' shadows BaseModel attribute; use construct_name
    line: int
    reason: str


class InterpretResponse(BaseModel):
    trajectory_app_id: str
    warnings: list[Warning] = []
    unsupported_constructs: list[UnsupportedConstruct] = []
    ik_solver_stats: IkSolverStats | None = None
    interpreter_version: str = "0.1.0"
    ast_dump_app_id: str | None = None
