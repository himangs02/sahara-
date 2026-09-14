from __future__ import annotations

import uuid
from datetime import datetime, timezone

from tests.conftest import auth_headers, register

PATIENT_PAYLOAD = {"name": "Kamala Devi", "age": 74, "preferred_language": "Assamese", "region": "Assam"}


def _token(client, email: str) -> str:
    return register(client, email)["access_token"]


def _create_patient(client, token: str, name: str = "Kamala Devi") -> dict:
    response = client.post("/patients", json=dict(PATIENT_PAYLOAD, name=name), headers=auth_headers(token))
    assert response.status_code == 201, response.text
    return response.json()


def _result_payload(result_id: str | None = None, **overrides) -> dict:
    payload = {
        "id": result_id or str(uuid.uuid4()),
        "game_type": "MEMORY_MATCH",
        "difficulty": "EASY",
        "total_pairs": 6,
        "matched_pairs": 6,
        "mistakes": 1,
        "completion_time_seconds": 45,
        "accuracy": 92.5,
        "completed": True,
        "occurred_at": datetime.now(timezone.utc).isoformat(),
    }
    payload.update(overrides)
    return payload


def test_upload_game_result_for_own_patient_succeeds(client):
    token = _token(client, "upload@example.com")
    patient = _create_patient(client, token)
    payload = _result_payload()

    response = client.post(f"/patients/{patient['id']}/game-results", json=payload, headers=auth_headers(token))

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["id"] == payload["id"]
    assert body["patient_id"] == patient["id"]
    assert body["accuracy"] == 92.5


def test_upload_requires_authentication(client):
    response = client.post(f"/patients/{uuid.uuid4()}/game-results", json=_result_payload())
    assert response.status_code == 401


def test_upload_for_unauthorized_patient_returns_404(client):
    token_a = _token(client, "gr-a@example.com")
    token_b = _token(client, "gr-b@example.com")
    patient_b = _create_patient(client, token_b)

    response = client.post(
        f"/patients/{patient_b['id']}/game-results", json=_result_payload(), headers=auth_headers(token_a)
    )
    assert response.status_code == 404


def test_upload_for_nonexistent_patient_returns_404(client):
    token = _token(client, "gr-nonexistent@example.com")
    response = client.post(
        f"/patients/{uuid.uuid4()}/game-results", json=_result_payload(), headers=auth_headers(token)
    )
    assert response.status_code == 404


def test_duplicate_upload_is_idempotent_not_duplicated(client, db_session):
    from app.db.models import GameResult

    token = _token(client, "idempotent@example.com")
    patient = _create_patient(client, token)
    result_id = str(uuid.uuid4())
    payload = _result_payload(result_id)

    first = client.post(f"/patients/{patient['id']}/game-results", json=payload, headers=auth_headers(token))
    second = client.post(f"/patients/{patient['id']}/game-results", json=payload, headers=auth_headers(token))

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["id"] == second.json()["id"]

    rows = db_session.query(GameResult).filter(GameResult.id == uuid.UUID(result_id)).all()
    assert len(rows) == 1


def test_re_upload_with_changed_fields_updates_in_place(client):
    token = _token(client, "update-result@example.com")
    patient = _create_patient(client, token)
    result_id = str(uuid.uuid4())

    client.post(
        f"/patients/{patient['id']}/game-results",
        json=_result_payload(result_id, accuracy=50.0),
        headers=auth_headers(token),
    )
    response = client.post(
        f"/patients/{patient['id']}/game-results",
        json=_result_payload(result_id, accuracy=100.0),
        headers=auth_headers(token),
    )

    assert response.status_code == 200
    assert response.json()["accuracy"] == 100.0


def test_result_id_reused_for_a_different_patient_is_rejected(client):
    token = _token(client, "mismatch@example.com")
    patient_1 = _create_patient(client, token, "Patient One")
    patient_2 = _create_patient(client, token, "Patient Two")
    result_id = str(uuid.uuid4())

    first = client.post(
        f"/patients/{patient_1['id']}/game-results", json=_result_payload(result_id), headers=auth_headers(token)
    )
    second = client.post(
        f"/patients/{patient_2['id']}/game-results", json=_result_payload(result_id), headers=auth_headers(token)
    )

    assert first.status_code == 200
    assert second.status_code == 409


def test_list_results_returns_only_this_patients_results_newest_first(client):
    token = _token(client, "list-results@example.com")
    patient = _create_patient(client, token)
    older = _result_payload(occurred_at="2026-01-01T00:00:00Z")
    newer = _result_payload(occurred_at="2026-06-01T00:00:00Z")
    client.post(f"/patients/{patient['id']}/game-results", json=older, headers=auth_headers(token))
    client.post(f"/patients/{patient['id']}/game-results", json=newer, headers=auth_headers(token))

    response = client.get(f"/patients/{patient['id']}/game-results", headers=auth_headers(token))

    assert response.status_code == 200
    ids = [r["id"] for r in response.json()]
    assert ids == [newer["id"], older["id"]]


def test_list_results_for_unauthorized_patient_returns_404(client):
    token_a = _token(client, "list-a@example.com")
    token_b = _token(client, "list-b@example.com")
    patient_b = _create_patient(client, token_b)

    response = client.get(f"/patients/{patient_b['id']}/game-results", headers=auth_headers(token_a))
    assert response.status_code == 404


def test_cross_device_same_caregiver_sees_uploaded_result(client):
    """Simulates Device A uploading and Device B (same caregiver, independent request)
    reading it back -- the actual Stage 3B multi-device proof, at the API level."""
    token = _token(client, "cross-device@example.com")
    patient = _create_patient(client, token)
    payload = _result_payload()

    upload = client.post(f"/patients/{patient['id']}/game-results", json=payload, headers=auth_headers(token))
    assert upload.status_code == 200

    # A second, independent authenticated request (standing in for "device B") reads it back.
    fetched = client.get(f"/patients/{patient['id']}/game-results", headers=auth_headers(token))
    assert fetched.status_code == 200
    ids = [r["id"] for r in fetched.json()]
    assert payload["id"] in ids
