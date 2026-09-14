from __future__ import annotations

from unittest.mock import patch

from app.main import app


def test_health_check_reports_ok_and_database_connectivity(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["database"] == "ok"


def test_health_check_fails_gracefully_when_database_is_unreachable(client):
    """A down database must produce a 503 (not a 200 lying about health, and not an
    unhandled 500 with a stack trace)."""

    class _BrokenSession:
        def execute(self, *_args, **_kwargs):
            raise RuntimeError("simulated: could not connect to postgresql://internal-host/db")

        def close(self):
            pass

    with patch("app.main.SessionLocal", return_value=_BrokenSession()):
        response = client.get("/health")

    assert response.status_code == 503
    assert response.json()["database"] == "unavailable"
    # The simulated driver error message (which could contain host/credential hints)
    # must never reach the response body.
    assert "internal-host" not in response.text
