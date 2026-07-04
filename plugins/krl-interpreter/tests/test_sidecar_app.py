"""FastAPI TestClient tests for the sidecar REST surface."""

from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from krl_interpreter.sidecar.app import app
from krl_interpreter.sidecar.schemas import INTERPRETER_VERSION

FIXTURES = Path(__file__).parent / "sidecar_fixtures"
IK_FIXTURES = Path(__file__).parent / "ik_fixtures"


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def test_health_returns_200(client: TestClient) -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["version"] == INTERPRETER_VERSION
    # Version header stamped on every response.
    assert response.headers["x-krl-interpreter-version"] == INTERPRETER_VERSION


def test_interpret_happy_round_trip(client: TestClient) -> None:
    src_text = (FIXTURES / "hello_world.src").read_text()
    urdf_path = str(IK_FIXTURES / "two_link_arm.urdf")
    body = {
        "srcText": src_text,
        "urdfPath": urdf_path,
        "timeStep": 0.5,
        "options": {
            "motionDuration": 1.0,
            "ikTolerance": 1e-2,  # loose tolerance — planar arm can't reach z=0.5
        },
    }
    response = client.post("/interpret", json=body)
    assert response.status_code == 200, response.text
    payload = response.json()
    assert "trajectory" in payload
    # hello_world has 2 motions (PTP + LIN) + 1 WAIT.
    # motions × 2 samples + wait × 2 samples = 6 samples
    assert len(payload["trajectory"]) >= 2
    assert payload["interpreterVersion"] == INTERPRETER_VERSION
    assert "ikSolverStats" in payload
    assert payload["ikSolverStats"]["totalPoses"] == 2
    assert response.headers["x-krl-interpreter-version"] == INTERPRETER_VERSION


def test_interpret_missing_srctext_422(client: TestClient) -> None:
    response = client.post(
        "/interpret",
        json={"urdfPath": "/tmp/robot.urdf"},
    )
    assert response.status_code == 422


def test_interpret_invalid_timestep_422(client: TestClient) -> None:
    response = client.post(
        "/interpret",
        json={
            "srcText": "DEF p() PTP {X 0, Y 0, Z 0, A 0, B 0, C 0} END",
            "urdfPath": "/tmp/robot.urdf",
            "timeStep": 0.0,
        },
    )
    assert response.status_code == 422


def test_interpret_async_stub_returns_501(client: TestClient) -> None:
    response = client.post(
        "/interpret/async",
        json={
            "srcText": "DEF p() END",
            "urdfPath": "/tmp/robot.urdf",
        },
    )
    assert response.status_code == 501


def test_interpret_jobs_poll_returns_501(client: TestClient) -> None:
    response = client.get("/interpret/jobs/abc123")
    assert response.status_code == 501
