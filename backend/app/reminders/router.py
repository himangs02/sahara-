from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.deps import get_current_user
from app.db.database import get_db
from app.db.models import User
from app.reminders import service
from app.schemas.reminder import ReminderRead, ReminderUpdate, ReminderUpsert

router = APIRouter(prefix="/patients/{patient_id}/reminders", tags=["reminders"])

_NOT_FOUND = HTTPException(status.HTTP_404_NOT_FOUND, "Patient not found")
_REMINDER_NOT_FOUND = HTTPException(status.HTTP_404_NOT_FOUND, "Reminder not found")
_DELETED_CONFLICT = HTTPException(status.HTTP_409_CONFLICT, "This reminder was deleted and cannot be recreated")


@router.post(
    "",
    response_model=ReminderRead,
    summary="Create or update a reminder for an authorized patient (idempotent upsert by id)",
    responses={
        404: {"description": "Patient not found, or not linked to this caregiver"},
        409: {"description": "This reminder id belongs to a different patient, or was already deleted"},
    },
)
def upsert_reminder(
    patient_id: uuid.UUID,
    payload: ReminderUpsert,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ReminderRead:
    try:
        reminder = service.upsert_reminder(db, current_user.id, patient_id, payload.model_dump())
    except service.PatientNotAuthorizedError as exc:
        raise _NOT_FOUND from exc
    except service.ReminderPatientMismatchError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, "This reminder id belongs to a different patient") from exc
    except service.ReminderDeletedError as exc:
        raise _DELETED_CONFLICT from exc
    return ReminderRead.model_validate(reminder)


@router.get(
    "",
    response_model=list[ReminderRead],
    summary="List reminders for an authorized patient",
    responses={404: {"description": "Patient not found, or not linked to this caregiver"}},
)
def list_reminders(
    patient_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[ReminderRead]:
    try:
        reminders = service.list_reminders_for_patient(db, current_user.id, patient_id)
    except service.PatientNotAuthorizedError as exc:
        raise _NOT_FOUND from exc
    return [ReminderRead.model_validate(r) for r in reminders]


@router.patch(
    "/{reminder_id}",
    response_model=ReminderRead,
    summary="Update fields of an existing reminder for an authorized patient",
    responses={404: {"description": "Patient or reminder not found, or not linked to this caregiver"}},
)
def update_reminder(
    patient_id: uuid.UUID,
    reminder_id: uuid.UUID,
    payload: ReminderUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ReminderRead:
    try:
        reminder = service.update_reminder(
            db, current_user.id, patient_id, reminder_id, payload.model_dump(exclude_unset=True)
        )
    except service.PatientNotAuthorizedError as exc:
        raise _NOT_FOUND from exc
    except service.ReminderNotFoundError as exc:
        raise _REMINDER_NOT_FOUND from exc
    return ReminderRead.model_validate(reminder)


@router.delete(
    "/{reminder_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_model=None,
    summary="Delete a reminder for an authorized patient",
    responses={404: {"description": "Patient or reminder not found, or not linked to this caregiver"}},
)
def delete_reminder(
    patient_id: uuid.UUID,
    reminder_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> None:
    try:
        service.delete_reminder(db, current_user.id, patient_id, reminder_id)
    except service.PatientNotAuthorizedError as exc:
        raise _NOT_FOUND from exc
    except service.ReminderNotFoundError as exc:
        raise _REMINDER_NOT_FOUND from exc
