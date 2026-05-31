"""Tests for krl_interpreter.sidecar.schemas.

Focus: InterpretRequest validation, especially the Latin-1 / CP1252
encoding waterfall introduced for real MFFD KUKA .src files.

Run from the repo root:

    cd plugins/krl-interpreter && python -m pytest tests/ -v

Or from the repo root:

    python -m pytest plugins/krl-interpreter/tests/ -v
"""

from __future__ import annotations

import base64
import sys
import os

import pytest

# Make the plugin importable when running pytest from the repo root.
sys.path.insert(
    0,
    os.path.join(os.path.dirname(__file__), ".."),
)

from krl_interpreter.sidecar.schemas import InterpretRequest  # noqa: E402

# ---------------------------------------------------------------------------
# Minimal valid URDF stub (inline, avoids filesystem access in unit tests)
# ---------------------------------------------------------------------------

_MINIMAL_URDF_B64 = base64.b64encode(
    b"""<?xml version="1.0"?>
<robot name="stub">
  <link name="base_link"/>
</robot>"""
).decode()

# A minimal KRL PTP motion statement (syntactically valid for tier-1 parser)
_MINIMAL_SRC = "PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"


# ---------------------------------------------------------------------------
# Baseline: plain srcText round-trip
# ---------------------------------------------------------------------------


def test_src_text_plain_accepted():
    """srcText + urdfContent produces a valid request with src_text set."""
    req = InterpretRequest(
        src_text=_MINIMAL_SRC,
        urdf_content=_MINIMAL_URDF_B64,
    )
    assert req.src_text == _MINIMAL_SRC


def test_missing_src_raises():
    """Neither srcText nor srcContent → ValidationError."""
    with pytest.raises(Exception):
        InterpretRequest(urdf_content=_MINIMAL_URDF_B64)


def test_missing_urdf_raises():
    """Neither urdfPath nor urdfContent → ValidationError."""
    with pytest.raises(Exception):
        InterpretRequest(src_text=_MINIMAL_SRC)


def test_both_src_text_and_content_raises():
    """Providing both srcText and srcContent is ambiguous → ValidationError."""
    with pytest.raises(Exception):
        InterpretRequest(
            src_text=_MINIMAL_SRC,
            src_content=base64.b64encode(_MINIMAL_SRC.encode()).decode(),
            urdf_content=_MINIMAL_URDF_B64,
        )


def test_defaults_are_sane():
    """Default time_step, options, and frame fields have correct values."""
    req = InterpretRequest(
        src_text=_MINIMAL_SRC,
        urdf_content=_MINIMAL_URDF_B64,
    )
    assert req.time_step == pytest.approx(0.01)
    assert req.options.max_iterations == 300
    assert req.base_frame is None
    assert req.tool_frame is None
    assert req.seed_pose is None


# ---------------------------------------------------------------------------
# Encoding regression tests (KRL-INTEGRATION-MFFD-REAL-01-ENCODING-LATIN1)
# ---------------------------------------------------------------------------


def test_src_content_latin1_decoded():
    """Latin-1 bytes in srcContent are decoded to correct Unicode characters.

    Real MFFD KUKA .src files from WorkVisual use ISO-8859-1. German
    variable names and comments contain ü (0xFC), ä (0xE4), ß (0xDF).
    The validator must produce the correct Unicode code-points, not
    replacement characters or garbage.
    """
    # Build a KRL snippet that contains Latin-1 umlauts and a sharp-s.
    # In ISO-8859-1:  ä=0xE4  ö=0xF6  ü=0xFC  ß=0xDF
    # We assemble the bytes directly to ensure we are testing the bytes, not
    # Python's own string representation.
    # "für" → 66 FC 72 (ü=0xFC)
    # "Nahtlänge" → 4E61 68 74 6C E4 6E 67 65 (ä=0xE4)
    # "Schweißnaht" → 53 63 68 77 65 69 DF 6E 61 68 74 (ß=0xDF)
    raw_bytes = (
        b"; Lagekorrektur f\xfcr Spannvorrichtung\n"  # ü = 0xFC
        b"DECL REAL Nahtl\xe4nge = 250.0\n"           # ä = 0xE4
        b"; Schwei\xdfnaht\n"                          # ß = 0xDF
        b"PTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
    )
    b64 = base64.b64encode(raw_bytes).decode()

    req = InterpretRequest(
        src_content=b64,
        urdf_content=_MINIMAL_URDF_B64,
    )

    assert req.src_text is not None, "src_text must be populated from srcContent"
    # The specific umlaut characters must survive intact
    assert "ü" in req.src_text, "ü (U+00FC) must decode correctly from 0xFC"
    assert "ä" in req.src_text, "ä (U+00E4) must decode correctly from 0xE4"
    assert "ß" in req.src_text, "ß (U+00DF) must decode correctly from 0xDF"
    # The PTP motion line must be present (parser needs it)
    assert "PTP" in req.src_text


def test_src_content_cp1252_decoded():
    """CP1252 bytes in srcContent are decoded to correct Unicode characters.

    Newer KRL Editor versions on Windows 10/11 default to CP1252. The
    distinguishing byte is 0x80 → U+20AC (€, Euro sign), which does not
    exist in ISO-8859-1 (0x80 is undefined / C1 control in Latin-1).
    """
    # In CP1252: byte 0x80 → Euro sign U+20AC.
    # We build the raw bytes directly (0x80 is undefined in ISO-8859-1 and
    # invalid in UTF-8, so it is the canonical CP1252-only distinguisher).
    prefix = b"; Preis: 1"
    euro_byte = bytes([0x80])   # CP1252 byte for €
    suffix = b" pro Lage\nPTP {X 0, Y 0, Z 0, A 0, B 0, C 0}\n"
    raw_bytes = prefix + euro_byte + suffix
    b64 = base64.b64encode(raw_bytes).decode()

    req = InterpretRequest(
        src_content=b64,
        urdf_content=_MINIMAL_URDF_B64,
    )

    assert req.src_text is not None, "src_text must be populated from srcContent"
    # 0x80 in CP1252 → Euro sign U+20AC
    assert "€" in req.src_text, (
        "Euro sign (U+20AC, CP1252 byte 0x80) must decode correctly"
    )
    assert "PTP" in req.src_text


def test_src_content_fallback_replace():
    """Bytes that are invalid in UTF-8, Latin-1, AND CP1252 decode without exception.

    The final fallback (errors=replace) must never raise; undecodable bytes
    become U+FFFD replacement characters. The resulting src_text must be
    set (not None) and must contain the valid ASCII content.
    """
    # Craft a byte string that cannot be decoded cleanly by any of the three
    # codecs. ISO-8859-1 can technically decode any byte (it maps 0x00-0xFF
    # one-to-one), but CP1252 has five undefined bytes: 0x81, 0x8D, 0x8F,
    # 0x90, 0x9D. To construct a sequence that forces the errors=replace
    # path, we use a sequence that is invalid UTF-8 AND contains a CP1252
    # undefined byte. We verify that the waterfall completes without exception
    # and that the known-good ASCII portion is present in the output.
    #
    # Byte layout:
    #   b"PTP valid start\n" — valid ASCII
    #   b"\xff\xfe"         — valid UTF-16 BOM but invalid UTF-8 start (0xFF)
    #   b"\x81"             — CP1252 undefined byte
    #   b"\n"               — valid ASCII newline
    poisoned_bytes = b"PTP valid start\n\xff\xfe\x81\n"
    b64 = base64.b64encode(poisoned_bytes).decode()

    # Must not raise — errors=replace is the final fallback
    req = InterpretRequest(
        src_content=b64,
        urdf_content=_MINIMAL_URDF_B64,
    )

    assert req.src_text is not None, (
        "src_text must be set even when the fallback errors=replace path is taken"
    )
    # The valid ASCII prefix must survive intact
    assert "PTP valid start" in req.src_text, (
        "ASCII content before the undecodable bytes must be preserved"
    )
    # The replacement path must not raise or produce None
    # (the exact replacement character content is implementation-defined)
