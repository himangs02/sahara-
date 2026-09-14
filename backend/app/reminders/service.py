"""Reminder CRUD, scoped through the same caregiver_patient_relationships check as
app/patients/service.py and app/game_results/service.py -- a caregiver can only
create/read/update/delete reminders for a patient they are actually linked to,
re-derived from the JWT on every call (see app/core/deps.py).

Soft-delete (`deleted_at`) rather than a hard row delete is what makes
upsert_reminder's ReminderDeletedError possible: without it, a stale, already-queued
create/update sent from another device *after* the caregiver deleted the reminder
elsewhere would silently recreate it.
"""

from __future__ import annotations

import uuid
from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.authorization import is_caregiver_linked_to_patient
from app.db.models import Reminder


class PatientNotAuthorizedError(Exception):
    """The caregiver is not linked to this patient -- caller should return 404, the
    same response as a patient that doesn't exist, so ids can't be enumerated."""


class ReminderPatientMismatchError(Exception):
    """This reminder id already exists but belongs to a different patient."""


class ReminderNotFoundError(Exception):
    """The reminder does not exist, or does not belong to the given patient."""


class ReminderDeletedError(Exception):
    """This reminder id was already deleted -- refuses to let a stale, out-of-order
    sync operation resurrect it."""


def upsert_reminder(db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID, data: dict) -> Reminder:
    if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
        raise PatientNotAuthorizedError()

    reminder_id = data["id"]
    existing = db.get(Reminder, reminder_id)
    if existing is not None:
        if existing.patient_id != patient_id:
            raise ReminderPatientMismatchError()
        if existing.deleted_at is not None:
            raise ReminderDeletedError()
        for field, value in data.items():
            if field != "id":
                setattr(existing, field, value)
        db.commit()
        db.refresh(existing)
        return existing

    reminder = Reminder(patient_id=patient_id, **{k: v for k, v in data.items() if k != "id"}, id=reminder_id)
    db.add(reminder)
    db.commit()
    db.refresh(reminder)
    return reminder


def list_reminders_for_patient(db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID) -> list[Reminder]:
    if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
        raise PatientNotAuthorizedError()
    stmt = (
        select(Reminder)
        .where(Reminder.patient_id == patient_id, Reminder.deleted_at.is_(None))
        .order_by(Reminder.minute_of_day)
    )
    return list(db.execute(stmt).scalars().all())


def _authorized_active_reminder(
    db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID, reminder_id: uuid.UUID
) -> Reminder:
    if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
        raise PatientNotAuthorizedError()
    reminder = db.get(Reminder, reminder_id)
    if reminder is None or reminder.patient_id != patient_id or reminder.deleted_at is not None:
        raise ReminderNotFoundError()
    return reminder


def update_reminder(
    db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID, reminder_id: uuid.UUID, updates: dict
) -> Reminder:
    reminder = _authorized_active_reminder(db, caregiver_id, patient_id, reminder_id)
    for field, value in updates.items():
        if value is not None:
            setattr(reminder, field, value)
    db.commit()
    db.refresh(reminder)
    return reminder


def delete_reminder(db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID, reminder_id: uuid.UUID) -> None:
    reminder = _authorized_active_reminder(db, caregiver_id, patient_id, reminder_id)
    reminder.deleted_at = datetime.now(timezone.utc)
    db.commit()
