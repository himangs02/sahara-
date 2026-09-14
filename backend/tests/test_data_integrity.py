"""Database-level correctness: foreign keys, cascades, and transaction rollback.

These exercise the actual SQLAlchemy session/engine rather than going through the
HTTP layer, since they're checking database behavior (constraint enforcement,
rollback) rather than API behavior.
"""

from __future__ import annotations

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from app.core.security import hash_password
from app.db.models import CaregiverPatientRelationship, Patient, User
from app.patients.service import DuplicateRelationshipError, link_caregiver_to_patient

PATIENT_PAYLOAD = {"name": "Kamala Devi", "age": 74, "preferred_language": "Assamese", "region": "Assam"}


def test_relationship_foreign_keys_are_enforced(db_session):
    """A relationship row can never reference a caregiver or patient that doesn't
    exist -- enforced by the database, not just application code."""
    db_session.add(
        CaregiverPatientRelationship(caregiver_id=uuid.uuid4(), patient_id=uuid.uuid4())
    )
    try:
        db_session.commit()
    except IntegrityError:
        db_session.rollback()
    else:
        raise AssertionError("expected a foreign key violation for a nonexistent caregiver/patient")


def test_patient_cascade_delete_removes_relationships(db_session):
    """Deleting a patient must also remove its relationship rows (ON DELETE CASCADE) --
    never leave an orphaned link behind."""
    caregiver = User(email="cascade@example.com", password_hash=hash_password("password-1"))
    patient = Patient(**PATIENT_PAYLOAD)
    db_session.add_all([caregiver, patient])
    db_session.commit()
    link_caregiver_to_patient(db_session, caregiver.id, patient.id)

    db_session.delete(patient)
    db_session.commit()

    remaining = db_session.execute(
        select(CaregiverPatientRelationship).where(CaregiverPatientRelationship.patient_id == patient.id)
    ).scalars().all()
    assert remaining == []


def test_caregiver_cascade_delete_removes_relationships(db_session):
    caregiver = User(email="cascade-caregiver@example.com", password_hash=hash_password("password-1"))
    patient = Patient(**PATIENT_PAYLOAD)
    db_session.add_all([caregiver, patient])
    db_session.commit()
    link_caregiver_to_patient(db_session, caregiver.id, patient.id)

    db_session.delete(caregiver)
    db_session.commit()

    remaining = db_session.execute(
        select(CaregiverPatientRelationship).where(CaregiverPatientRelationship.caregiver_id == caregiver.id)
    ).scalars().all()
    assert remaining == []


def test_duplicate_relationship_rollback_leaves_exactly_one_row(db_session):
    """After a duplicate-link IntegrityError, the session must still be usable (which
    proves the service layer actually rolled back the failed transaction) and exactly
    one relationship row must exist -- not zero, not two."""
    caregiver = User(email="rollback@example.com", password_hash=hash_password("password-1"))
    patient = Patient(**PATIENT_PAYLOAD)
    db_session.add_all([caregiver, patient])
    db_session.commit()

    link_caregiver_to_patient(db_session, caregiver.id, patient.id)
    try:
        link_caregiver_to_patient(db_session, caregiver.id, patient.id)
    except DuplicateRelationshipError:
        pass
    else:
        raise AssertionError("expected DuplicateRelationshipError on the second link")

    # A query here would itself raise if the transaction were left in a broken state.
    rows = db_session.execute(
        select(CaregiverPatientRelationship).where(
            CaregiverPatientRelationship.caregiver_id == caregiver.id,
            CaregiverPatientRelationship.patient_id == patient.id,
        )
    ).scalars().all()
    assert len(rows) == 1
