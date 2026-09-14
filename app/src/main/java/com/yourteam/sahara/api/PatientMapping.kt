package com.yourteam.sahara.api

import com.yourteam.sahara.api.model.PatientCreateDto
import com.yourteam.sahara.api.model.PatientDto
import com.yourteam.sahara.model.Patient

/** [Patient.syncId] becomes the client-supplied `id` the backend upserts by -- see
 * backend/app/patients/service.py::create_patient_for_caregiver. */
fun Patient.toCreateDto() = PatientCreateDto(
    id = syncId,
    name = name,
    age = age,
    preferredLanguage = language,
    region = region
)

fun PatientDto.toDomain() = Patient(
    syncId = id,
    id = id,
    name = name,
    age = age,
    region = region,
    language = preferredLanguage
)
