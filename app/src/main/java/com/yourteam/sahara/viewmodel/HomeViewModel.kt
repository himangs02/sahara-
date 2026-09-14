package com.yourteam.sahara.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourteam.sahara.R
import com.yourteam.sahara.ai.AdaptiveDifficultyEngine
import com.yourteam.sahara.ai.AdaptiveRecommendation
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.data.repository.ReminderRepository
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.Reminder
import com.yourteam.sahara.model.TodayReminder
import com.yourteam.sahara.model.UiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val primaryActivity: CognitiveActivityType = CognitiveActivityType.MEMORY_MATCH,
    val primaryRecommendation: AdaptiveRecommendation = AdaptiveRecommendation(
        recommendedDifficulty = Difficulty.EASY,
        performanceScore = 0.5f,
        reason = UiText(R.string.reason_loading),
        confidence = 0f
    ),
    val recommendationsMap: Map<CognitiveActivityType, AdaptiveRecommendation> = emptyMap()
)

class HomeViewModel(
    repository: GameResultRepository,
    private val reminderRepository: ReminderRepository,
    patientId: String = repository.patientId
) : ViewModel() {
    private val engine = AdaptiveDifficultyEngine()

    val state: StateFlow<HomeState> = repository
        .getAllGameResults()
        .map { results ->
            val map = CognitiveActivityType.entries.associateWith { activityType ->
                engine.analyzeAndRecommend(results, activityType)
            }

            // Determine primary activity (most recently completed or default to Memory Match)
            val mostRecent = results.filter { it.completed }.maxByOrNull { it.timestamp }
            val primaryType = if (mostRecent != null) {
                CognitiveActivityType.fromId(mostRecent.gameType)
            } else {
                CognitiveActivityType.MEMORY_MATCH
            }

            val primaryRec = map[primaryType] ?: engine.analyzeAndRecommend(results, primaryType)

            HomeState(
                primaryActivity = primaryType,
                primaryRecommendation = primaryRec,
                recommendationsMap = map
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = HomeState()
        )

    val todayReminders: StateFlow<List<TodayReminder>> = reminderRepository
        .todayReminders(patientId, includeDisabled = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setReminderDone(reminder: Reminder, done: Boolean) {
        viewModelScope.launch {
            reminderRepository.setCompleted(reminder.id, done)
        }
    }
}

class HomeViewModelFactory(
    private val repository: GameResultRepository,
    private val reminderRepository: ReminderRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, reminderRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
