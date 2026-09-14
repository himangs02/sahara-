package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourteam.sahara.model.Patient

@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey
    val id: String,
    val syncId: String = id,
    val name: String,
    val age: Int,
    val region: String,
    val language: String
)

fun PatientEntity.toDomain(): Patient {
    return Patient(
        syncId = syncId,
        id = id,
        name = name,
        age = age,
        region = region,
        language = language
    )
}

fun Patient.toEntity(): PatientEntity {
    return PatientEntity(
        id = id,
        syncId = syncId,
        name = name,
        age = age,
        region = region,
        language = language
    )
}
