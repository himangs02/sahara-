from __future__ import annotations

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.deps import get_current_user
from app.db.database import get_db
from app.db.models import User
from app.patients import service
from app.schemas.patient import PatientCreate, PatientRead, PatientUpdate

router = APIRouter(prefix="/patients", tags=["patients"])

_NOT_FOUND = HTTPException(status.HTTP_404_NOT_FOUND, "Patient not found")


@router.post(
    "",
    response_model=PatientRead,
    status_code=status.HTTP_201_CREATED,
    summary="Create a patient, linked to the authenticated caregiver",
    responses={409: {"description": "The supplied id belongs to a patient this caregiver isn't linked to"}},
)
def create_patient(
    payload: PatientCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> PatientRead:
    data = payload.model_dump(exclude={"id"})
    try:
        patient = service.create_patient_for_caregiver(db, current_user.id, data, patient_id=payload.id)
    except service.PatientIdConflictError as exc:
        raise HTTPException(status.HTTP_409_CONFLICT, "This patient id is already in use") from exc
    return PatientRead.model_validate(patient)


@router.get(
    "",
    response_model=list[PatientRead],
    summary="List patients linked to the authenticated caregiver",
)
def list_patients(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[PatientRead]:
    patients = service.list_patients_for_caregiver(db, current_user.id)
    return [PatientRead.model_validate(patient) for patient in patients]


@router.get(
    "/{patient_id}",
    response_model=PatientRead,
    summary="Get one patient, if the authenticated caregiver is linked to them",
    responses={404: {"description": "Patient not found, or not linked to this caregiver"}},
)
def get_patient(
    patient_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> PatientRead:
    patient = service.get_patient_for_caregiver(db, current_user.id, patient_id)
    if patient is None:
        raise _NOT_FOUND
    return PatientRead.model_validate(patient)


@router.patch(
    "/{patient_id}",
    response_model=PatientRead,
    summary="Update one patient, if the authenticated caregiver is linked to them",
    responses={404: {"description": "Patient not found, or not linked to this caregiver"}},
)
def update_patient(
    patient_id: uuid.UUID,
    payload: PatientUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> PatientRead:
    patient = service.update_patient_for_caregiver(
        db, current_user.id, patient_id, payload.model_dump(exclude_unset=True)
    )
    if patient is None:
        raise _NOT_FOUND
    return PatientRead.model_validate(patient)
