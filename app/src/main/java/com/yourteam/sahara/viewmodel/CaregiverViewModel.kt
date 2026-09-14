package com.yourteam.sahara.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourteam.sahara.ai.CaregiverDashboardAnalyzer
import com.yourteam.sahara.ai.DashboardSummary
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.PatientRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.Patient
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.TodayReminder
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
    /** Newest first, including sessions that were not finished. */
    val recentActivities: List<GameResult> = emptyList(),
    /** Day start timestamps with average accuracy; the UI formats the day in its own language. */
    val trends7Day: Map<CognitiveActivityType, List<Pair<Long, Float>>> = emptyMap(),
    /** Null until the first data load completes. */
    val dashboard: DashboardSummary? = null,
    val syncStatus: SyncStatusInfo = SyncStatusInfo(SyncState.SYNCED),
    val isLoading: Boolean = false
)

class CaregiverViewModel(
    private val gameResultRepository: GameResultRepository,
    private val patientRepository: PatientRepository,
    private val reminderRepository: ReminderRepository,
    private val syncManager: SyncManager? = null,
    private val patientId: String = gameResultRepository.patientId
) : ViewModel() {

    private val dashboardAnalyzer = CaregiverDashboardAnalyzer()

    val state: StateFlow<CaregiverUiState> = combine(
        patientRepository.getPatientById(patientId),
        gameResultRepository.getAllGameResults(),
        syncManager?.syncStatusInfo ?: kotlinx.coroutines.flow.flowOf(SyncStatusInfo(SyncState.SYNCED)),
        // Re-evaluated each minute so "today" and activity status roll over without new data.
        minuteClock()
    ) { patient, allResults, syncInfo, clock ->
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

        val trends = calculate7DayTrends(completed)

        CaregiverUiState(
            patient = currentPatient,
            overallEngagement = overall,
            recentActivities = allResults.sortedByDescending { it.timestamp }.take(HISTORY_LIMIT),
            trends7Day = trends,
            dashboard = dashboardAnalyzer.summarize(allResults, clock),
            syncStatus = syncInfo,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = CaregiverUiState(isLoading = true)
    )

    val todayReminders: StateFlow<List<TodayReminder>> = reminderRepository
        .todayReminders(patientId, includeDisabled = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Adds a new reminder or replaces an edited one (same id). */
    fun saveReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderRepository.insertReminder(reminder.copy(patientId = patientId))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(reminder)
        }
    }

    fun setReminderDone(reminder: Reminder, done: Boolean) {
        viewModelScope.launch {
            reminderRepository.setCompleted(reminder.id, done)
        }
    }

    fun setReminderEnabled(reminder: Reminder, enabled: Boolean) {
        viewModelScope.launch {
            reminderRepository.setEnabled(reminder.id, enabled)
        }
    }

    fun triggerManualSync(context: Context) {
        syncManager?.triggerManualSync(context)
    }

    private companion object {
        const val HISTORY_LIMIT = 50
    }

    private fun calculateAverageAccuracy(results: List<GameResult>): Int {
        if (results.isEmpty()) return 0
        return results.take(5).map { it.accuracy }.average().toInt()
    }

    private fun calculate7DayTrends(results: List<GameResult>): Map<CognitiveActivityType, List<Pair<Long, Float>>> {
        val dateFormat = SimpleDateFormat("EEE", Locale.ROOT)
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

                Pair(dayTime, avgAcc)
            }
        }
    }
}

class CaregiverViewModelFactory(
    private val gameResultRepository: GameResultRepository,
    private val patientRepository: PatientRepository,
    private val reminderRepository: ReminderRepository,
    private val syncManager: SyncManager? = null,
    private val patientId: String = gameResultRepository.patientId
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CaregiverViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CaregiverViewModel(gameResultRepository, patientRepository, reminderRepository, syncManager, patientId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
