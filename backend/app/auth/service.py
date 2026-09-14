from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.security import hash_password, verify_password
from app.db.models import User


class EmailAlreadyRegisteredError(Exception):
    pass


class InvalidCredentialsError(Exception):
    pass


def register_user(db: Session, email: str, password: str) -> User:
    normalized = email.strip().lower()
    existing = db.execute(select(User).where(User.email == normalized)).scalar_one_or_none()
    if existing is not None:
        raise EmailAlreadyRegisteredError()
    user = User(email=normalized, password_hash=hash_password(password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def authenticate_user(db: Session, email: str, password: str) -> User:
    """Raises InvalidCredentialsError for an unknown email, a wrong password, and a
    deactivated account alike -- one failure for all three so a caller cannot use the
    error to probe which emails have accounts or which are disabled."""
    normalized = email.strip().lower()
    user = db.execute(select(User).where(User.email == normalized)).scalar_one_or_none()
    if user is None or not user.is_active or not verify_password(password, user.password_hash):
        raise InvalidCredentialsError()
    return user


def get_user_by_id(db: Session, user_id: uuid.UUID) -> User | None:
    return db.get(User, user_id)
