package com.yourteam.sahara.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourteam.sahara.ai.ActivityChangeBreakdown
import com.yourteam.sahara.ai.AdaptiveDifficultyEngine
import com.yourteam.sahara.ai.AdaptiveRecommendation
import com.yourteam.sahara.ai.CaregiverInsightEngine
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.PatientRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.data.repository.CaregiverAlertRepository
import com.yourteam.sahara.model.CaregiverAlert
import com.yourteam.sahara.model.CaregiverInsight
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.sync.SyncManager
import com.yourteam.sahara.sync.SyncState
import com.yourteam.sahara.sync.SyncStatusInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaregiverUiState(
    val patient: Patient = Patient(),
    val overallEngagement: Int = 0,
    val memoryPerformance: Int = 0,
    val attentionPerformance: Int = 0,
    val sequencePerformance: Int = 0,
    val recentActivities: List<GameResult> = emptyList(),
    val trends7Day: Map<CognitiveActivityType, List<Pair<String, Float>>> = emptyMap(),
    val insights: List<CaregiverInsight> = emptyList(),
    val alerts: List<CaregiverAlert> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val recommendationsMap: Map<CognitiveActivityType, AdaptiveRecommendation> = emptyMap(),
    val syncStatus: SyncStatusInfo = SyncStatusInfo(SyncState.SYNCED),
    val isLoading: Boolean = false
)

class CaregiverViewModel(
    private val gameResultRepository: GameResultRepository,
    private val patientRepository: PatientRepository,
    private val reminderRepository: ReminderRepository,
    private val syncManager: SyncManager? = null,
    private val patientId: String = "patient_001"
) : ViewModel() {

    private val adaptiveEngine = AdaptiveDifficultyEngine()
    private val insightEngine = CaregiverInsightEngine()

    val state: StateFlow<CaregiverUiState> = combine(
        patientRepository.getPatientById(patientId),
        gameResultRepository.getAllGameResults(),
        reminderRepository.getRemindersForPatient(patientId),
        syncManager?.syncStatusInfo ?: kotlinx.coroutines.flow.flowOf(SyncStatusInfo(SyncState.SYNCED))
    ) { patient, allResults, remindersList, syncInfo ->
        val currentPatient = patient ?: Patient()
        val completed = allResults.filter { it.completed }.sortedByDescending { it.timestamp }

        // Compute activity-isolated performances
        val memoryResults = completed.filter {
            it.gameType.equals(CognitiveActivityType.MEMORY_MATCH.id, ignoreCase = true) ||
            it.gameType.equals(CognitiveActivityType.MEMORY_MATCH.displayName, ignoreCase = true)
        }
        val attentionResults = completed.filter {
            it.gameType.equals(CognitiveActivityType.ATTENTION_TAP.id, ignoreCase = true) ||
            it.gameType.equals(CognitiveActivityType.ATTENTION_TAP.displayName, ignoreCase = true)
        }
        val sequenceResults = completed.filter {
            it.gameType.equals(CognitiveActivityType.SEQUENCE_RECALL.id, ignoreCase = true) ||
            it.gameType.equals(CognitiveActivityType.SEQUENCE_RECALL.displayName, ignoreCase = true)
        }

        val memoryPerf = calculateAverageAccuracy(memoryResults)
        val attentionPerf = calculateAverageAccuracy(attentionResults)
        val sequencePerf = calculateAverageAccuracy(sequenceResults)

        val activePerformances = listOf(memoryPerf, attentionPerf, sequencePerf).filter { it > 0 }
        val overall = if (activePerformances.isNotEmpty()) activePerformances.average().toInt() else 0

        val recsMap = CognitiveActivityType.entries.associateWith { activityType ->
            adaptiveEngine.analyzeAndRecommend(completed, activityType)
        }

        val insights = insightEngine.generateInsights(completed)
        val alerts = insightEngine.generateAlerts(completed)
        val trends = calculate7DayTrends(completed)

        CaregiverUiState(
            patient = currentPatient,
            overallEngagement = overall,
            memoryPerformance = memoryPerf,
            attentionPerformance = attentionPerf,
            sequencePerformance = sequencePerf,
            recentActivities = completed.take(10),
            trends7Day = trends,
            insights = insights,
            alerts = alerts,
            reminders = remindersList,
            recommendationsMap = recsMap,
            syncStatus = syncInfo,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = CaregiverUiState(isLoading = true)
    )

    fun getWhyChangeBreakdown(
        allResults: List<GameResult>,
        activityType: CognitiveActivityType
    ): ActivityChangeBreakdown {
        return insightEngine.getWhyChangeBreakdown(allResults, activityType)
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderRepository.updateReminder(reminder)
        }
    }

    fun triggerManualSync(context: Context) {
        syncManager?.triggerManualSync(context)
    }

    private fun calculateAverageAccuracy(results: List<GameResult>): Int {
        if (results.isEmpty()) return 0
        return results.take(5).map { it.accuracy }.average().toInt()
    }

    private fun calculate7DayTrends(results: List<GameResult>): Map<CognitiveActivityType, List<Pair<String, Float>>> {
        val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayMillis = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()

        return CognitiveActivityType.entries.associateWith { activityType ->
            val activityResults = results.filter {
                it.gameType.equals(activityType.id, ignoreCase = true) ||
                it.gameType.equals(activityType.displayName, ignoreCase = true)
            }

            (6 downTo 0).map { dayOffset ->
                val dayTime = now - (dayOffset * dayMillis)
                val dayLabel = dateFormat.format(Date(dayTime))

                val dayResults = activityResults.filter {
                    val diff = Math.abs(it.timestamp - dayTime)
                    diff < dayMillis / 2 || dateFormat.format(Date(it.timestamp)) == dayLabel
                }

                val avgAcc = if (dayResults.isNotEmpty()) {
                    dayResults.map { it.accuracy }.average().toFloat()
                } else {
                    if (activityResults.isNotEmpty()) activityResults.map { it.accuracy }.average().toFloat() else 0f
                }

                Pair(dayLabel, avgAcc)
            }
        }
    }
}

class CaregiverViewModelFactory(
    private val gameResultRepository: GameResultRepository,
    private val patientRepository: PatientRepository,
    private val reminderRepository: ReminderRepository,
    private val syncManager: SyncManager? = null,
    private val patientId: String = "patient_001"
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CaregiverViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CaregiverViewModel(gameResultRepository, patientRepository, reminderRepository, syncManager, patientId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
