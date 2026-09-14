from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.deps import get_current_user
from app.db.database import get_db
from app.db.models import User
from app.game_results import service
from app.schemas.game_result import GameResultRead, GameResultUpsert

router = APIRouter(prefix="/patients/{patient_id}/game-results", tags=["game-results"])

_NOT_FOUND = HTTPException(status.HTTP_404_NOT_FOUND, "Patient not found")


@router.post(
    "",
    response_model=GameResultRead,
    summary="Upload a completed game result for an authorized patient (idempotent upsert by id)",
    responses={
        404: {"description": "Patient not found, or not linked to this caregiver"},
        409: {"description": "This result id already belongs to a different patient"},
    },
)
def upload_game_result(
    patient_id: uuid.UUID,
    payload: GameResultUpsert,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GameResultRead:
    try:
        result = service.upsert_game_result(db, current_user.id, patient_id, payload.model_dump())
    except service.PatientNotAuthorizedError as exc:
        raise _NOT_FOUND from exc
    except service.GameResultPatientMismatchError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, "This result id belongs to a different patient") from exc
    return GameResultRead.model_validate(result)


@router.get(
    "",
    response_model=list[GameResultRead],
    summary="List game results for an authorized patient, newest first",
    responses={404: {"description": "Patient not found, or not linked to this caregiver"}},
)
def list_game_results(
    patient_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[GameResultRead]:
    try:
        results = service.list_game_results_for_patient(db, current_user.id, patient_id)
    except service.PatientNotAuthorizedError as exc:
        raise _NOT_FOUND from exc
    return [GameResultRead.model_validate(r) for r in results]
