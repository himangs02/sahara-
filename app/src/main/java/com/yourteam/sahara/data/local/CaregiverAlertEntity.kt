package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourteam.sahara.model.AlertSeverity
import com.yourteam.sahara.model.CaregiverAlert
import com.yourteam.sahara.model.CognitiveActivityType

@Entity(tableName = "caregiver_alerts")
data class CaregiverAlertEntity(
    @PrimaryKey
    val id: String,
    val patientId: String = "patient_001",
    val title: String,
    val message: String,
    val activityType: String?,
    val severity: String,
    val reviewed: Boolean = false,
    val timestamp: Long
)

fun CaregiverAlertEntity.toDomain(): CaregiverAlert {
    return CaregiverAlert(
        id = id,
        title = title,
        message = message,
        activityType = activityType?.let { CognitiveActivityType.fromId(it) },
        severity = try { AlertSeverity.valueOf(severity) } catch (_: Exception) { AlertSeverity.WARNING },
        reviewed = reviewed,
        timestamp = timestamp
    )
}

fun CaregiverAlert.toEntity(patientId: String = "patient_001"): CaregiverAlertEntity {
    return CaregiverAlertEntity(
        id = id,
        patientId = patientId,
        title = title,
        message = message,
        activityType = activityType?.id,
        severity = severity.name,
        reviewed = reviewed,
        timestamp = timestamp
    )
}
