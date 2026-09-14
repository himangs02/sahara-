"""Password hashing and JWT issuance/verification.

Passwords are hashed with bcrypt (salt embedded in the hash, no separate salt column
needed). This is deliberately independent from the Android client's local PBKDF2
verifier (see auth/PasswordHasher.kt) -- the server is now the source of truth for
credential verification; the Android-side hasher remains only as an offline-login
fallback and is unaffected by this backend.
"""

from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt

from app.core.config import get_settings


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))
    except ValueError:
        return False


def create_access_token(subject: uuid.UUID) -> str:
    settings = get_settings()
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(subject),
        "iat": now,
        "exp": now + timedelta(minutes=settings.access_token_expire_minutes),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_access_token(token: str) -> uuid.UUID | None:
    """Returns the caregiver id the token was issued for, or None if the token is
    missing, malformed, expired, or signed with a different secret/algorithm."""
    settings = get_settings()
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    except jwt.PyJWTError:
        return None
    subject = payload.get("sub")
    if subject is None:
        return None
    try:
        return uuid.UUID(subject)
    except ValueError:
        return None
