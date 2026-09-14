from __future__ import annotations

import uuid

from tests.conftest import auth_headers, register

PATIENT_PAYLOAD = {"name": "Kamala Devi", "age": 74, "preferred_language": "Assamese", "region": "Assam"}


def _token(client, email: str) -> str:
    return register(client, email)["access_token"]


def _create_patient(client, token: str, name: str = "Kamala Devi") -> dict:
    response = client.post("/patients", json=dict(PATIENT_PAYLOAD, name=name), headers=auth_headers(token))
    assert response.status_code == 201, response.text
    return response.json()


def _reminder_payload(reminder_id: str | None = None, **overrides) -> dict:
    payload = {
        "id": reminder_id or str(uuid.uuid4()),
        "title": "Morning Medicine",
        "description": "Take with water",
        "reminder_type": "MEDICINE",
        "minute_of_day": 8 * 60,
        "enabled": True,
    }
    payload.update(overrides)
    return payload


def test_create_reminder_for_own_patient_succeeds(client):
    token = _token(client, "reminder-create@example.com")
    patient = _create_patient(client, token)
    payload = _reminder_payload()

    response = client.post(f"/patients/{patient['id']}/reminders", json=payload, headers=auth_headers(token))

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["id"] == payload["id"]
    assert body["patient_id"] == patient["id"]
    assert body["title"] == "Morning Medicine"
    assert body["minute_of_day"] == 8 * 60
    assert body["enabled"] is True


def test_create_requires_authentication(client):
    response = client.post(f"/patients/{uuid.uuid4()}/reminders", json=_reminder_payload())
    assert response.status_code == 401


def test_create_for_unauthorized_patient_returns_404(client):
    token_a = _token(client, "rem-a@example.com")
    token_b = _token(client, "rem-b@example.com")
    patient_b = _create_patient(client, token_b)

    response = client.post(
        f"/patients/{patient_b['id']}/reminders", json=_reminder_payload(), headers=auth_headers(token_a)
    )
    assert response.status_code == 404


def test_create_for_nonexistent_patient_returns_404(client):
    token = _token(client, "rem-nonexistent@example.com")
    response = client.post(
        f"/patients/{uuid.uuid4()}/reminders", json=_reminder_payload(), headers=auth_headers(token)
    )
    assert response.status_code == 404


def test_duplicate_create_is_idempotent_upsert(client, db_session):
    from app.db.models import Reminder

    token = _token(client, "rem-idempotent@example.com")
    patient = _create_patient(client, token)
    reminder_id = str(uuid.uuid4())
    payload = _reminder_payload(reminder_id)

    first = client.post(f"/patients/{patient['id']}/reminders", json=payload, headers=auth_headers(token))
    second = client.post(f"/patients/{patient['id']}/reminders", json=payload, headers=auth_headers(token))

    assert first.status_code == 200
    assert second.status_code == 200
    assert first.json()["id"] == second.json()["id"]

    rows = db_session.query(Reminder).filter(Reminder.id == uuid.UUID(reminder_id)).all()
    assert len(rows) == 1


def test_reminder_id_reused_for_a_different_patient_is_rejected(client):
    token = _token(client, "rem-mismatch@example.com")
    patient_1 = _create_patient(client, token, "Patient One")
    patient_2 = _create_patient(client, token, "Patient Two")
    reminder_id = str(uuid.uuid4())

    first = client.post(
        f"/patients/{patient_1['id']}/reminders", json=_reminder_payload(reminder_id), headers=auth_headers(token)
    )
    second = client.post(
        f"/patients/{patient_2['id']}/reminders", json=_reminder_payload(reminder_id), headers=auth_headers(token)
    )

    assert first.status_code == 200
    assert second.status_code == 409


def test_list_reminders_returns_only_this_patients_reminders(client):
    token = _token(client, "rem-list@example.com")
    patient = _create_patient(client, token)
    r1 = _reminder_payload(minute_of_day=8 * 60, title="Morning Medicine")
    r2 = _reminder_payload(minute_of_day=11 * 60, title="Hydration")
    client.post(f"/patients/{patient['id']}/reminders", json=r1, headers=auth_headers(token))
    client.post(f"/patients/{patient['id']}/reminders", json=r2, headers=auth_headers(token))

    response = client.get(f"/patients/{patient['id']}/reminders", headers=auth_headers(token))

    assert response.status_code == 200
    ids = [r["id"] for r in response.json()]
    assert set(ids) == {r1["id"], r2["id"]}


def test_list_reminders_for_unauthorized_patient_returns_404(client):
    token_a = _token(client, "rem-list-a@example.com")
    token_b = _token(client, "rem-list-b@example.com")
    patient_b = _create_patient(client, token_b)

    response = client.get(f"/patients/{patient_b['id']}/reminders", headers=auth_headers(token_a))
    assert response.status_code == 404


def test_update_reminder_changes_fields(client):
    token = _token(client, "rem-update@example.com")
    patient = _create_patient(client, token)
    reminder = client.post(
        f"/patients/{patient['id']}/reminders", json=_reminder_payload(), headers=auth_headers(token)
    ).json()

    response = client.patch(
        f"/patients/{patient['id']}/reminders/{reminder['id']}",
        json={"enabled": False, "minute_of_day": 9 * 60},
        headers=auth_headers(token),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["enabled"] is False
    assert body["minute_of_day"] == 9 * 60
    assert body["title"] == reminder["title"]  # untouched field preserved


def test_update_for_unauthorized_patient_returns_404(client):
    token_a = _token(client, "rem-upd-a@example.com")
    token_b = _token(client, "rem-upd-b@example.com")
    patient_b = _create_patient(client, token_b)
    reminder = client.post(
        f"/patients/{patient_b['id']}/reminders", json=_reminder_payload(), headers=auth_headers(token_b)
    ).json()

    response = client.patch(
        f"/patients/{patient_b['id']}/reminders/{reminder['id']}",
        json={"enabled": False},
        headers=auth_headers(token_a),
    )
    assert response.status_code == 404


def test_update_reminder_not_belonging_to_patient_returns_404(client):
    token = _token(client, "rem-wrong-relationship@example.com")
    patient_1 = _create_patient(client, token, "Patient One")
    patient_2 = _create_patient(client, token, "Patient Two")
    reminder = client.post(
        f"/patients/{patient_1['id']}/reminders", json=_reminder_payload(), headers=auth_headers(token)
    ).json()

    # Same caregiver, but reminder_id belongs to patient_1, addressed under patient_2's path.
    response = client.patch(
        f"/patients/{patient_2['id']}/reminders/{reminder['id']}",
        json={"enabled": False},
        headers=auth_headers(token),
    )
    assert response.status_code == 404


def test_update_nonexistent_reminder_returns_404(client):
    token = _token(client, "rem-upd-missing@example.com")
    patient = _create_patient(client, token)

    response = client.patch(
        f"/patients/{patient['id']}/reminders/{uuid.uuid4()}",
        json={"enabled": False},
        headers=auth_headers(token),
    )
    assert response.status_code == 404


def test_delete_reminder_succeeds_and_excludes_it_from_list(client):
    token = _token(client, "rem-delete@example.com")
    patient = _create_patient(client, token)
    reminder = client.post(
        f"/patients/{patient['id']}/reminders", json=_reminder_payload(), headers=auth_headers(token)
    ).json()

    response = client.delete(f"/patients/{patient['id']}/reminders/{reminder['id']}", headers=auth_headers(token))
    assert response.status_code == 204

    listing = client.get(f"/patients/{patient['id']}/reminders", headers=auth_headers(token))
    assert reminder["id"] not in [r["id"] for r in listing.json()]


def test_delete_for_unauthorized_patient_returns_404(client):
    token_a = _token(client, "rem-del-a@example.com")
    token_b = _token(client, "rem-del-b@example.com")
    patient_b = _create_patient(client, token_b)
    reminder = client.post(
        f"/patients/{patient_b['id']}/reminders", json=_reminder_payload(), headers=auth_headers(token_b)
    ).json()

    response = client.delete(
        f"/patients/{patient_b['id']}/reminders/{reminder['id']}", headers=auth_headers(token_a)
    )
    assert response.status_code == 404

    # And caregiver B's reminder is untouched.
    listing = client.get(f"/patients/{patient_b['id']}/reminders", headers=auth_headers(token_b))
    assert reminder["id"] in [r["id"] for r in listing.json()]


def test_deleted_reminder_cannot_be_resurrected_by_stale_sync(client):
    """A stale, already-queued create/update from another device must not silently
    recreate a reminder that was deleted in the meantime."""
    token = _token(client, "rem-resurrect@example.com")
    patient = _create_patient(client, token)
    reminder_id = str(uuid.uuid4())
    payload = _reminder_payload(reminder_id)

    client.post(f"/patients/{patient['id']}/reminders", json=payload, headers=auth_headers(token))
    delete_response = client.delete(
        f"/patients/{patient['id']}/reminders/{reminder_id}", headers=auth_headers(token)
    )
    assert delete_response.status_code == 204

    # A stale queued upload for the same id arrives after the deletion.
    stale_resurrect = client.post(
        f"/patients/{patient['id']}/reminders", json=payload, headers=auth_headers(token)
    )
    assert stale_resurrect.status_code == 409

    listing = client.get(f"/patients/{patient['id']}/reminders", headers=auth_headers(token))
    assert reminder_id not in [r["id"] for r in listing.json()]


def test_deleting_already_deleted_reminder_returns_404(client):
    token = _token(client, "rem-double-delete@example.com")
    patient = _create_patient(client, token)
    reminder = client.post(
        f"/patients/{patient['id']}/reminders", json=_reminder_payload(), headers=auth_headers(token)
    ).json()

    first = client.delete(f"/patients/{patient['id']}/reminders/{reminder['id']}", headers=auth_headers(token))
    second = client.delete(f"/patients/{patient['id']}/reminders/{reminder['id']}", headers=auth_headers(token))

    assert first.status_code == 204
    assert second.status_code == 404


def test_malformed_reminder_id_returns_422(client):
    token = _token(client, "rem-malformed@example.com")
    patient = _create_patient(client, token)

    response = client.patch(
        f"/patients/{patient['id']}/reminders/not-a-uuid",
        json={"enabled": False},
        headers=auth_headers(token),
    )
    assert response.status_code == 422


def test_malformed_patient_id_returns_422(client):
    token = _token(client, "rem-malformed-patient@example.com")
    response = client.get("/patients/not-a-uuid/reminders", headers=auth_headers(token))
    assert response.status_code == 422


def test_cross_device_same_caregiver_sees_created_reminder(client):
    """Simulates Device A creating a reminder and Device B (same caregiver, independent
    request) reading it back -- the actual Stage 3C multi-device proof, at the API level."""
    token = _token(client, "rem-cross-device@example.com")
    patient = _create_patient(client, token)
    payload = _reminder_payload()

    upload = client.post(f"/patients/{patient['id']}/reminders", json=payload, headers=auth_headers(token))
    assert upload.status_code == 200

    fetched = client.get(f"/patients/{patient['id']}/reminders", headers=auth_headers(token))
    assert fetched.status_code == 200
    ids = [r["id"] for r in fetched.json()]
    assert payload["id"] in ids
