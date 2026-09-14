"""Shared authorization check, used by every patient-scoped resource (patients
themselves, game results, and future patient-scoped data) so the "is this caregiver
actually linked to this patient" rule is defined exactly once."""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models import CaregiverPatientRelationship


def is_caregiver_linked_to_patient(db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID) -> bool:
    stmt = select(CaregiverPatientRelationship.id).where(
        CaregiverPatientRelationship.caregiver_id == caregiver_id,
        CaregiverPatientRelationship.patient_id == patient_id,
    )
    return db.execute(stmt).scalar_one_or_none() is not None
