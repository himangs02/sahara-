"""Game-result upload/list, scoped through the same caregiver_patient_relationships
check as app/patients/service.py -- a caregiver can only upload or read results for a
patient they are actually linked to, re-derived from the JWT on every call."""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.authorization import is_caregiver_linked_to_patient
from app.db.models import GameResult


class PatientNotAuthorizedError(Exception):
    """The caregiver is not linked to this patient -- caller should return 404, the
    same response as a patient that doesn't exist, so ids can't be enumerated."""


class GameResultPatientMismatchError(Exception):
    """This result id already exists but belongs to a different patient -- refuses to
    silently reassign or overwrite it (a UUID collision would be exceptionally
    unlikely; this guards a client bug rather than a realistic attack)."""


def upsert_game_result(db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID, data: dict) -> GameResult:
    if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
        raise PatientNotAuthorizedError()

    result_id = data["id"]
    existing = db.get(GameResult, result_id)
    if existing is not None:
        if existing.patient_id != patient_id:
            raise GameResultPatientMismatchError()
        for field, value in data.items():
            if field != "id":
                setattr(existing, field, value)
        db.commit()
        db.refresh(existing)
        return existing

    result = GameResult(patient_id=patient_id, **data)
    db.add(result)
    db.commit()
    db.refresh(result)
    return result


def list_game_results_for_patient(db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID) -> list[GameResult]:
    if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
        raise PatientNotAuthorizedError()
    stmt = (
        select(GameResult)
        .where(GameResult.patient_id == patient_id)
        .order_by(GameResult.occurred_at.desc())
    )
    return list(db.execute(stmt).scalars().all())
