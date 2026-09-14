from __future__ import annotations

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.auth.service import get_user_by_id
from app.core.security import decode_access_token
from app.db.database import get_db
from app.db.models import User

bearer_scheme = HTTPBearer(auto_error=False)

_UNAUTHORIZED = HTTPException(
    status.HTTP_401_UNAUTHORIZED,
    "Not authenticated",
    headers={"WWW-Authenticate": "Bearer"},
)


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    if credentials is None:
        raise _UNAUTHORIZED
    user_id = decode_access_token(credentials.credentials)
    if user_id is None:
        raise _UNAUTHORIZED
    user = get_user_by_id(db, user_id)
    # Deleted/deactivated accounts must lose access immediately, even with a
    # still-valid, unexpired token.
    if user is None or not user.is_active:
        raise _UNAUTHORIZED
    return user
