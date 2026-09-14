from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import service
from app.core.deps import get_current_user
from app.core.security import create_access_token
from app.db.database import get_db
from app.db.models import User
from app.schemas.auth import LoginRequest, RegisterRequest, TokenResponse
from app.schemas.user import UserRead

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post(
    "/register",
    response_model=TokenResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Register a new caregiver account",
    responses={409: {"description": "An account with this email already exists"}},
)
def register(payload: RegisterRequest, db: Session = Depends(get_db)) -> TokenResponse:
    try:
        user = service.register_user(db, payload.email, payload.password)
    except service.EmailAlreadyRegisteredError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, "An account with this email already exists") from exc
    return TokenResponse(access_token=create_access_token(user.id), user=UserRead.model_validate(user))


@router.post(
    "/login",
    response_model=TokenResponse,
    summary="Log in with email and password",
    responses={401: {"description": "Incorrect email or password"}},
)
def login(payload: LoginRequest, db: Session = Depends(get_db)) -> TokenResponse:
    try:
        user = service.authenticate_user(db, payload.email, payload.password)
    except service.InvalidCredentialsError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Incorrect email or password") from exc
    return TokenResponse(access_token=create_access_token(user.id), user=UserRead.model_validate(user))


@router.get(
    "/me",
    response_model=UserRead,
    summary="Get the authenticated caregiver's own profile",
    responses={401: {"description": "Missing, invalid, or expired token"}},
)
def me(current_user: User = Depends(get_current_user)) -> UserRead:
    return UserRead.model_validate(current_user)
