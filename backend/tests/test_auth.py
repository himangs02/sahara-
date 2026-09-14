from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone

import jwt

from app.core.config import get_settings
from tests.conftest import auth_headers, register


def test_register_creates_account_with_hashed_password(client):
    body = register(client, "new-caregiver@example.com")
    assert body["user"]["email"] == "new-caregiver@example.com"
    assert body["user"]["is_active"] is True
    assert "access_token" in body and body["access_token"]
    assert "password" not in body["user"]
    assert "password_hash" not in body["user"]


def test_register_rejects_short_password(client):
    response = client.post("/auth/register", json={"email": "short@example.com", "password": "short"})
    assert response.status_code == 422


def test_duplicate_registration_is_rejected(client):
    register(client, "dup@example.com")
    response = client.post("/auth/register", json={"email": "dup@example.com", "password": "another-password"})
    assert response.status_code == 409


def test_duplicate_registration_is_case_insensitive(client):
    register(client, "Case@Example.com")
    response = client.post("/auth/register", json={"email": "case@example.com", "password": "another-password"})
    assert response.status_code == 409


def test_login_succeeds_with_correct_credentials(client):
    register(client, "login-ok@example.com", "correct-password-1")
    response = client.post("/auth/login", json={"email": "login-ok@example.com", "password": "correct-password-1"})
    assert response.status_code == 200
    assert "access_token" in response.json()


def test_login_rejects_wrong_password(client):
    register(client, "login-bad@example.com", "correct-password-1")
    response = client.post("/auth/login", json={"email": "login-bad@example.com", "password": "wrong-password"})
    assert response.status_code == 401


def test_login_rejects_unknown_email(client):
    response = client.post("/auth/login", json={"email": "nobody@example.com", "password": "whatever-1"})
    assert response.status_code == 401


def test_login_rejects_inactive_account(client, db_session):
    from app.db.models import User
    from app.core.security import hash_password

    user = User(email="inactive@example.com", password_hash=hash_password("password-1"), is_active=False)
    db_session.add(user)
    db_session.commit()

    response = client.post("/auth/login", json={"email": "inactive@example.com", "password": "password-1"})
    assert response.status_code == 401


def test_me_requires_authentication(client):
    response = client.get("/auth/me")
    assert response.status_code == 401


def test_me_rejects_malformed_token(client):
    response = client.get("/auth/me", headers=auth_headers("not-a-real-token"))
    assert response.status_code == 401


def test_me_rejects_expired_token(client):
    settings = get_settings()
    now = datetime.now(timezone.utc)
    expired_token = jwt.encode(
        {"sub": str(uuid.uuid4()), "iat": now - timedelta(hours=2), "exp": now - timedelta(hours=1)},
        settings.jwt_secret,
        algorithm=settings.jwt_algorithm,
    )
    response = client.get("/auth/me", headers=auth_headers(expired_token))
    assert response.status_code == 401


def test_me_rejects_token_signed_with_wrong_secret(client):
    settings = get_settings()
    now = datetime.now(timezone.utc)
    bad_token = jwt.encode(
        {"sub": str(uuid.uuid4()), "iat": now, "exp": now + timedelta(hours=1)},
        "a-completely-different-secret",
        algorithm=settings.jwt_algorithm,
    )
    response = client.get("/auth/me", headers=auth_headers(bad_token))
    assert response.status_code == 401


def test_me_rejects_token_for_deleted_user(client):
    settings = get_settings()
    now = datetime.now(timezone.utc)
    token_for_nobody = jwt.encode(
        {"sub": str(uuid.uuid4()), "iat": now, "exp": now + timedelta(hours=1)},
        settings.jwt_secret,
        algorithm=settings.jwt_algorithm,
    )
    response = client.get("/auth/me", headers=auth_headers(token_for_nobody))
    assert response.status_code == 401


def test_me_returns_the_authenticated_caregiver(client):
    body = register(client, "me@example.com")
    response = client.get("/auth/me", headers=auth_headers(body["access_token"]))
    assert response.status_code == 200
    assert response.json()["email"] == "me@example.com"
    assert response.json()["id"] == body["user"]["id"]
