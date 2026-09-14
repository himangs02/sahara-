"""Patient CRUD, scoped entirely through caregiver_patient_relationships.

Every read/update here re-derives authorization from that table on every call -- the
caller's claimed patient_id is never trusted on its own, only in combination with a
matching relationship row for the authenticated caregiver (see app/core/deps.py for
how that caregiver identity itself is derived from the verified JWT, never from a
client-supplied id). This is what makes cross-caregiver access impossible even if the
Android client's own local checks are bypassed.
"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.authorization import is_caregiver_linked_to_patient
from app.db.models import CaregiverPatientRelationship, Patient


class DuplicateRelationshipError(Exception):
    pass


class PatientIdConflictError(Exception):
    """Raised when a client-supplied patient id already belongs to a patient this
    caregiver is not linked to -- refuses to silently adopt or overwrite it."""


def create_patient_for_caregiver(
    db: Session, caregiver_id: uuid.UUID, data: dict, patient_id: uuid.UUID | None = None
) -> Patient:
    """Creates a patient and links it to the creating caregiver in one transaction.

    When [patient_id] is given (the Android sync path, which generates a stable client
    UUID up front so a retried upload is idempotent instead of creating a duplicate):
    - if no patient with that id exists yet, it's created with that id;
    - if one exists and this caregiver is already linked to it, its fields are updated
      in place (an upsert) instead of erroring;
    - if one exists but this caregiver is NOT linked to it, that's a conflict -- refuses
      rather than silently exposing or overwriting another caregiver's patient.
    """
    if patient_id is not None:
        existing = db.get(Patient, patient_id)
        if existing is not None:
            if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
                raise PatientIdConflictError()
            for field, value in data.items():
                setattr(existing, field, value)
            db.commit()
            db.refresh(existing)
            return existing
        patient = Patient(id=patient_id, **data)
    else:
        patient = Patient(**data)

    db.add(patient)
    db.flush()  # assigns patient.id without committing, so the link row can reference it
    db.add(CaregiverPatientRelationship(caregiver_id=caregiver_id, patient_id=patient.id))
    db.commit()
    db.refresh(patient)
    return patient


def link_caregiver_to_patient(
    db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID
) -> CaregiverPatientRelationship:
    """Links an existing patient to an existing caregiver. Not exposed over HTTP in
    this stage (no invite/consent flow yet) -- used directly by tests to exercise the
    uniqueness constraint, and reserved for a future "add co-caregiver" endpoint."""
    link = CaregiverPatientRelationship(caregiver_id=caregiver_id, patient_id=patient_id)
    db.add(link)
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise DuplicateRelationshipError() from exc
    db.refresh(link)
    return link


def list_patients_for_caregiver(db: Session, caregiver_id: uuid.UUID) -> list[Patient]:
    stmt = (
        select(Patient)
        .join(CaregiverPatientRelationship, CaregiverPatientRelationship.patient_id == Patient.id)
        .where(CaregiverPatientRelationship.caregiver_id == caregiver_id)
        .order_by(Patient.name)
    )
    return list(db.execute(stmt).scalars().all())


def get_patient_for_caregiver(db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID) -> Patient | None:
    """Returns None both when the patient does not exist and when it exists but this
    caregiver has no link to it -- the two cases are indistinguishable to the caller
    (surfaced as 404 either way), so a valid patient id cannot be discovered by probing."""
    if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
        return None
    return db.get(Patient, patient_id)


def update_patient_for_caregiver(
    db: Session, caregiver_id: uuid.UUID, patient_id: uuid.UUID, updates: dict
) -> Patient | None:
    if not is_caregiver_linked_to_patient(db, caregiver_id, patient_id):
        return None
    patient = db.get(Patient, patient_id)
    if patient is None:
        return None
    for field, value in updates.items():
        if value is not None:
            setattr(patient, field, value)
    db.commit()
    db.refresh(patient)
    return patient
