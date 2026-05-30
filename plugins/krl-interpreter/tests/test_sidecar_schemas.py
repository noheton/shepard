"""Pydantic schema validation tests for the sidecar surface."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from krl_interpreter.sidecar.schemas import (
    INTERPRETER_VERSION,
    InterpretRequest,
    InterpretResponse,
    TrajectorySample,
)


def test_happy_body_validates() -> None:
    body = InterpretRequest(
        srcText="DEF p() PTP {X 0, Y 0, Z 0, A 0, B 0, C 0} END",
        urdfPath="/tmp/robot.urdf",
        timeStep=0.05,
    )
    assert body.timeStep == 0.05
    assert body.options.ikTolerance == 1e-3
    assert body.options.motionDuration == 1.0


def test_negative_timestep_rejected() -> None:
    with pytest.raises(ValidationError):
        InterpretRequest(
            srcText="DEF p() END",
            urdfPath="/tmp/robot.urdf",
            timeStep=-0.01,
        )


def test_empty_srctext_rejected() -> None:
    with pytest.raises(ValidationError):
        InterpretRequest(
            srcText="   ",
            urdfPath="/tmp/robot.urdf",
        )


def test_response_round_trips() -> None:
    """Verify InterpretResponse serializes cleanly + version tag set."""
    resp = InterpretResponse(
        trajectory=[TrajectorySample(t=0.0, joints=[0.1, 0.2])]
    )
    assert resp.interpreterVersion == INTERPRETER_VERSION
    dumped = resp.model_dump()
    assert dumped["trajectory"][0]["t"] == 0.0
    assert dumped["interpreterVersion"] == INTERPRETER_VERSION


def test_sidecar_settings_from_env(monkeypatch) -> None:
    """SidecarSettings reads env vars with sensible defaults + parse fallbacks."""
    from krl_interpreter.sidecar.config import SidecarSettings

    # Defaults when no env set.
    monkeypatch.delenv("PORT", raising=False)
    monkeypatch.delenv("KRL_IK_TOLERANCE", raising=False)
    s = SidecarSettings.from_env()
    assert s.port == 8000
    assert s.default_ik_tolerance == 1e-3

    # Overrides.
    monkeypatch.setenv("PORT", "9090")
    monkeypatch.setenv("KRL_IK_TOLERANCE", "0.005")
    s = SidecarSettings.from_env()
    assert s.port == 9090
    assert s.default_ik_tolerance == 0.005

    # Garbage values fall back to defaults (defensive parse).
    monkeypatch.setenv("PORT", "not-a-number")
    monkeypatch.setenv("KRL_IK_TOLERANCE", "not-a-float")
    s = SidecarSettings.from_env()
    assert s.port == 8000
    assert s.default_ik_tolerance == 1e-3
