package com.yourteam.sahara.data.repository

import com.yourteam.sahara.data.local.CaregiverAlertDao
import com.yourteam.sahara.data.local.toDomain
import com.yourteam.sahara.data.local.toEntity
import com.yourteam.sahara.model.CaregiverAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CaregiverAlertRepository(private val dao: CaregiverAlertDao) {

    suspend fun insertAlert(alert: CaregiverAlert) = withContext(Dispatchers.IO) {
        dao.insertAlert(alert.toEntity())
    }

    suspend fun markAlertReviewed(alertId: String) = withContext(Dispatchers.IO) {
        dao.markAlertReviewed(alertId)
    }

    fun getAlertsForPatient(patientId: String = "patient_001"): Flow<List<CaregiverAlert>> {
        return dao.getAlertsForPatient(patientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
