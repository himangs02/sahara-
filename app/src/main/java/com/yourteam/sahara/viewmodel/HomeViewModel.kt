package com.yourteam.sahara.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourteam.sahara.ai.AdaptiveDifficultyEngine
import com.yourteam.sahara.ai.AdaptiveRecommendation
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val primaryActivity: CognitiveActivityType = CognitiveActivityType.MEMORY_MATCH,
    val primaryRecommendation: AdaptiveRecommendation = AdaptiveRecommendation(
        recommendedDifficulty = Difficulty.EASY,
        performanceScore = 0.5f,
        reason = "Loading recommendation...",
        confidence = 0f
    ),
    val recommendationsMap: Map<CognitiveActivityType, AdaptiveRecommendation> = emptyMap()
)

class HomeViewModel(repository: GameResultRepository) : ViewModel() {
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
}

class HomeViewModelFactory(
    private val repository: GameResultRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
