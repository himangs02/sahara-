from __future__ import annotations

import uuid

from tests.conftest import auth_headers, register

PATIENT_PAYLOAD = {"name": "Kamala Devi", "age": 74, "preferred_language": "Assamese", "region": "Assam"}


def _token(client, email: str) -> str:
    return register(client, email)["access_token"]


def _create_patient(client, token: str, name: str = "Kamala Devi") -> dict:
    payload = dict(PATIENT_PAYLOAD, name=name)
    response = client.post("/patients", json=payload, headers=auth_headers(token))
    assert response.status_code == 201, response.text
    return response.json()


def test_create_patient_with_client_supplied_id_uses_it(client):
    token = _token(client, "client-id@example.com")
    client_id = str(uuid.uuid4())
    response = client.post(
        "/patients", json=dict(PATIENT_PAYLOAD, id=client_id), headers=auth_headers(token)
    )
    assert response.status_code == 201
    assert response.json()["id"] == client_id


def test_re_create_with_same_client_id_upserts_instead_of_duplicating(client):
    """The sync-retry case: uploading the same client-generated patient id twice must
    update the existing row, not create a second patient."""
    token = _token(client, "upsert-patient@example.com")
    client_id = str(uuid.uuid4())
    client.post("/patients", json=dict(PATIENT_PAYLOAD, id=client_id, age=70), headers=auth_headers(token))
    response = client.post(
        "/patients", json=dict(PATIENT_PAYLOAD, id=client_id, age=71), headers=auth_headers(token)
    )

    assert response.status_code == 201
    assert response.json()["id"] == client_id
    assert response.json()["age"] == 71

    listing = client.get("/patients", headers=auth_headers(token)).json()
    assert len([p for p in listing if p["id"] == client_id]) == 1


def test_client_id_already_used_by_an_unlinked_patient_is_rejected(client):
    token_a = _token(client, "id-conflict-a@example.com")
    token_b = _token(client, "id-conflict-b@example.com")
    patient_a = _create_patient(client, token_a)

    response = client.post(
        "/patients", json=dict(PATIENT_PAYLOAD, id=patient_a["id"]), headers=auth_headers(token_b)
    )
    assert response.status_code == 409


def test_create_patient_links_to_caller(client):
    token = _token(client, "create@example.com")
    patient = _create_patient(client, token)
    assert patient["name"] == "Kamala Devi"
    assert patient["age"] == 74
    assert patient["preferred_language"] == "Assamese"


def test_create_patient_rejects_invalid_age(client):
    token = _token(client, "invalid-age@example.com")
    response = client.post(
        "/patients", json=dict(PATIENT_PAYLOAD, age=999), headers=auth_headers(token)
    )
    assert response.status_code == 422


def test_create_patient_requires_authentication(client):
    response = client.post("/patients", json=PATIENT_PAYLOAD)
    assert response.status_code == 401


def test_list_returns_only_own_patients(client):
    token = _token(client, "list@example.com")
    _create_patient(client, token, "Patient One")
    _create_patient(client, token, "Patient Two")
    response = client.get("/patients", headers=auth_headers(token))
    assert response.status_code == 200
    assert {p["name"] for p in response.json()} == {"Patient One", "Patient Two"}


def test_list_requires_authentication(client):
    response = client.get("/patients")
    assert response.status_code == 401


def test_get_own_patient_succeeds(client):
    token = _token(client, "get@example.com")
    patient = _create_patient(client, token)
    response = client.get(f"/patients/{patient['id']}", headers=auth_headers(token))
    assert response.status_code == 200
    assert response.json()["id"] == patient["id"]


def test_get_unknown_patient_returns_404(client):
    token = _token(client, "unknown@example.com")
    response = client.get(f"/patients/{uuid.uuid4()}", headers=auth_headers(token))
    assert response.status_code == 404


def test_update_own_patient_succeeds(client):
    token = _token(client, "update@example.com")
    patient = _create_patient(client, token)
    response = client.patch(
        f"/patients/{patient['id']}", json={"age": 75}, headers=auth_headers(token)
    )
    assert response.status_code == 200
    body = response.json()
    assert body["age"] == 75
    assert body["name"] == patient["name"]  # untouched fields are preserved


def test_unauthorized_and_nonexistent_patient_are_indistinguishable(client):
    """A caregiver must not be able to tell "this patient id belongs to someone else"
    apart from "this patient id doesn't exist" -- both must produce the exact same
    response, or the API becomes an oracle for discovering valid patient ids."""
    token_a = _token(client, "indist-a@example.com")
    token_b = _token(client, "indist-b@example.com")
    someone_elses_patient = _create_patient(client, token_b)

    unauthorized = client.get(f"/patients/{someone_elses_patient['id']}", headers=auth_headers(token_a))
    nonexistent = client.get(f"/patients/{uuid.uuid4()}", headers=auth_headers(token_a))

    assert unauthorized.status_code == nonexistent.status_code == 404
    assert unauthorized.json() == nonexistent.json()


def test_update_unknown_patient_returns_404(client):
    token = _token(client, "update-unknown@example.com")
    response = client.patch(
        f"/patients/{uuid.uuid4()}", json={"age": 80}, headers=auth_headers(token)
    )
    assert response.status_code == 404


def test_duplicate_caregiver_patient_relationship_is_rejected(db_session):
    """Exercises the DB-level uniqueness constraint directly, since Stage 1 does not
    expose a public endpoint for linking an *existing* patient to a caregiver (that
    needs a consent/invite flow -- see backend/README.md known limitations)."""
    from app.core.security import hash_password
    from app.db.models import Patient, User
    from app.patients.service import DuplicateRelationshipError, link_caregiver_to_patient

    caregiver = User(email="rel-owner@example.com", password_hash=hash_password("password-1"))
    patient = Patient(**PATIENT_PAYLOAD)
    db_session.add_all([caregiver, patient])
    db_session.commit()

    link_caregiver_to_patient(db_session, caregiver.id, patient.id)

    try:
        link_caregiver_to_patient(db_session, caregiver.id, patient.id)
    except DuplicateRelationshipError:
        pass
    else:
        raise AssertionError("expected DuplicateRelationshipError on a duplicate link")


def test_cross_caregiver_access_matrix(client):
    """The critical Stage 1 security test: two caregivers, three patients, verifying
    every allowed and forbidden combination -- read, list, and update alike."""
    token_a = _token(client, "caregiver-a@example.com")
    token_b = _token(client, "caregiver-b@example.com")

    patient_a = _create_patient(client, token_a, "Patient A")
    patient_b = _create_patient(client, token_a, "Patient B")
    patient_c = _create_patient(client, token_b, "Patient C")

    headers_a = auth_headers(token_a)
    headers_b = auth_headers(token_b)

    # Caregiver A: own patients allowed, Caregiver B's patient forbidden.
    assert client.get(f"/patients/{patient_a['id']}", headers=headers_a).status_code == 200
    assert client.get(f"/patients/{patient_b['id']}", headers=headers_a).status_code == 200
    assert client.get(f"/patients/{patient_c['id']}", headers=headers_a).status_code == 404

    # Caregiver B: own patient allowed, Caregiver A's patients forbidden.
    assert client.get(f"/patients/{patient_c['id']}", headers=headers_b).status_code == 200
    assert client.get(f"/patients/{patient_a['id']}", headers=headers_b).status_code == 404
    assert client.get(f"/patients/{patient_b['id']}", headers=headers_b).status_code == 404

    # Cross-caregiver writes must be rejected too, not just reads.
    assert client.patch(f"/patients/{patient_a['id']}", json={"age": 99}, headers=headers_b).status_code == 404
    assert client.patch(f"/patients/{patient_c['id']}", json={"age": 99}, headers=headers_a).status_code == 404

    # Each caregiver's list must contain exactly their own patients, never the other's.
    list_a = {p["id"] for p in client.get("/patients", headers=headers_a).json()}
    list_b = {p["id"] for p in client.get("/patients", headers=headers_b).json()}
    assert list_a == {patient_a["id"], patient_b["id"]}
    assert list_b == {patient_c["id"]}

    # Cross-caregiver update must not have actually changed the target patient.
    unchanged = client.get(f"/patients/{patient_a['id']}", headers=headers_a).json()
    assert unchanged["age"] == PATIENT_PAYLOAD["age"]
