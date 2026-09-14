package com.yourteam.sahara.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.yourteam.sahara.model.AlertKind
import com.yourteam.sahara.model.AlertSeverity
import com.yourteam.sahara.model.CaregiverAlert
import com.yourteam.sahara.model.CognitiveActivityType

@Entity(tableName = "caregiver_alerts")
data class CaregiverAlertEntity(
    @PrimaryKey
    val id: String,
    val patientId: String = com.yourteam.sahara.auth.DemoIdentity.PATIENT_ID,
    val title: String,
    val message: String,
    val activityType: String?,
    val severity: String,
    val reviewed: Boolean = false,
    val timestamp: Long
)

// The title column stores the AlertKind; alert text is resolved in the UI language at display time.
fun CaregiverAlertEntity.toDomain(): CaregiverAlert {
    return CaregiverAlert(
        id = id,
        kind = AlertKind.entries.find { it.name == title } ?: AlertKind.LOW_PERFORMANCE,
        activityType = activityType?.let { CognitiveActivityType.fromId(it) },
        severity = try { AlertSeverity.valueOf(severity) } catch (_: Exception) { AlertSeverity.WARNING },
        reviewed = reviewed,
        timestamp = timestamp
    )
}

fun CaregiverAlert.toEntity(patientId: String = com.yourteam.sahara.auth.DemoIdentity.PATIENT_ID): CaregiverAlertEntity {
    return CaregiverAlertEntity(
        id = id,
        patientId = patientId,
        title = kind.name,
        message = "",
        activityType = activityType?.id,
        severity = severity.name,
        reviewed = reviewed,
        timestamp = timestamp
    )
}
