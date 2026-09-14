package com.yourteam.sahara.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.model.AttentionSymbolItem
import com.yourteam.sahara.model.CardIcon
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.personalization.GameContentProvider
import com.yourteam.sahara.personalization.NerRegions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttentionGameState(
    val currentRound: Int = 1,
    val totalRounds: Int = 5,
    val targetIcon: CardIcon = CardIcon.FLOWER,
    val gridSymbols: List<AttentionSymbolItem> = emptyList(),
    val correctCount: Int = 0,
    val mistakes: Int = 0,
    val timeSeconds: Long = 0,
    val isGameComplete: Boolean = false,
    val difficulty: Difficulty = Difficulty.EASY,
    val gridColumns: Int = 2,
    val accuracy: Int = 0,
    val feedbackMessage: String? = null
)

class AttentionGameViewModel(
    private val repository: GameResultRepository,
    private val initialDifficulty: Difficulty = Difficulty.EASY,
    private val patientRegion: String? = null,
    private val culturalContentEnabled: Boolean = true
) : ViewModel() {

    private val _state = MutableStateFlow(AttentionGameState())
    val state: StateFlow<AttentionGameState> = _state.asStateFlow()

    private val regionProfile = NerRegions.match(patientRegion)

    private var timerJob: Job? = null
    var lastGameResult: GameResult? = null
        private set

    /** See [com.yourteam.sahara.viewmodel.MemoryGameViewModel.labelFor]. */
    @StringRes
    fun labelFor(icon: CardIcon): Int = GameContentProvider.labelFor(icon, regionProfile, culturalContentEnabled)

    init {
        startGame(initialDifficulty)
    }

    fun startGame(difficulty: Difficulty = initialDifficulty) {
        timerJob?.cancel()

        val columns = when (difficulty) {
            Difficulty.EASY -> 2
            Difficulty.MEDIUM -> 3
            Difficulty.HARD -> 3
        }

        _state.value = AttentionGameState(
            difficulty = difficulty,
            gridColumns = columns,
            totalRounds = 5
        )

        setupRound(round = 1, difficulty = difficulty, columns = columns)
        startTimer()
    }

    private fun setupRound(round: Int, difficulty: Difficulty, columns: Int) {
        val totalSymbolsCount = when (difficulty) {
            Difficulty.EASY -> 6
            Difficulty.MEDIUM -> 9
            Difficulty.HARD -> 12
        }

        // Pick 1 target icon
        val availableIcons = CardIcon.entries.shuffled()
        val target = availableIcons.first()

        // Pick other icons for the rest of grid
        val otherIcons = availableIcons.drop(1)
        val gridIcons = mutableListOf(target)
        
        while (gridIcons.size < totalSymbolsCount) {
            gridIcons.add(otherIcons.random())
        }

        val shuffledItems = gridIcons.shuffled().mapIndexed { index, icon ->
            AttentionSymbolItem(id = index, icon = icon)
        }

        _state.update {
            it.copy(
                currentRound = round,
                targetIcon = target,
                gridSymbols = shuffledItems,
                feedbackMessage = null
            )
        }
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(timeSeconds = it.timeSeconds + 1) }
            }
        }
    }

    fun onSymbolClicked(item: AttentionSymbolItem) {
        val currentState = _state.value
        if (currentState.isGameComplete) return

        if (item.icon == currentState.targetIcon) {
            // Correct tap
            val newCorrect = currentState.correctCount + 1
            if (currentState.currentRound >= currentState.totalRounds) {
                // Completed game!
                completeGame(newCorrect, currentState.mistakes)
            } else {
                setupRound(
                    round = currentState.currentRound + 1,
                    difficulty = currentState.difficulty,
                    columns = currentState.gridColumns
                )
                _state.update { it.copy(correctCount = newCorrect) }
            }
        } else {
            // Incorrect tap
            val newMistakes = currentState.mistakes + 1
            _state.update {
                it.copy(
                    mistakes = newMistakes,
                    feedbackMessage = "Try finding the matching symbol above!"
                )
            }
        }
    }

    private fun completeGame(correct: Int, mistakes: Int) {
        timerJob?.cancel()
        val totalAttempts = correct + mistakes
        val acc = if (totalAttempts > 0) ((correct.toFloat() / totalAttempts) * 100).toInt() else 0

        val result = GameResult(
            patientId = repository.patientId,
            gameType = CognitiveActivityType.ATTENTION_TAP.id,
            difficulty = _state.value.difficulty.name,
            totalPairs = _state.value.totalRounds,
            matchedPairs = correct,
            mistakes = mistakes,
            completionTimeSeconds = _state.value.timeSeconds,
            accuracy = acc.toFloat(),
            completed = true
        )

        lastGameResult = result

        _state.update {
            it.copy(
                correctCount = correct,
                isGameComplete = true,
                accuracy = acc
            )
        }

        viewModelScope.launch {
            repository.saveGameResult(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class AttentionGameViewModelFactory(
    private val repository: GameResultRepository,
    private val initialDifficulty: Difficulty = Difficulty.EASY,
    private val patientRegion: String? = null,
    private val culturalContentEnabled: Boolean = true
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AttentionGameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AttentionGameViewModel(repository, initialDifficulty, patientRegion, culturalContentEnabled) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
