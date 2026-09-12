package com.yourteam.sahara.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.model.CardIcon
import com.yourteam.sahara.model.CognitiveActivityType
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.SequencePhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SequenceRecallState(
    val currentRound: Int = 1,
    val totalRounds: Int = 3,
    val phase: SequencePhase = SequencePhase.MEMORIZE,
    val targetSequence: List<CardIcon> = emptyList(),
    val shuffledChoices: List<CardIcon> = emptyList(),
    val userSequence: List<CardIcon> = emptyList(),
    val mistakes: Int = 0,
    val correctRounds: Int = 0,
    val timeSeconds: Long = 0,
    val isGameComplete: Boolean = false,
    val difficulty: Difficulty = Difficulty.EASY,
    val feedbackMessage: String? = null
)

class SequenceRecallViewModel(
    private val repository: GameResultRepository,
    private val initialDifficulty: Difficulty = Difficulty.EASY
) : ViewModel() {

    private val _state = MutableStateFlow(SequenceRecallState())
    val state: StateFlow<SequenceRecallState> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var memorizeJob: Job? = null

    var lastGameResult: GameResult? = null
        private set

    init {
        startGame(initialDifficulty)
    }

    fun startGame(difficulty: Difficulty = initialDifficulty) {
        timerJob?.cancel()
        memorizeJob?.cancel()

        _state.value = SequenceRecallState(
            difficulty = difficulty,
            totalRounds = 3
        )

        setupRound(round = 1, difficulty = difficulty)
        startTimer()
    }

    private fun setupRound(round: Int, difficulty: Difficulty) {
        val sequenceLength = when (difficulty) {
            Difficulty.EASY -> 3
            Difficulty.MEDIUM -> 4
            Difficulty.HARD -> 5
        }

        val availableIcons = CardIcon.entries.shuffled()
        val targetSeq = availableIcons.take(sequenceLength)
        val choices = targetSeq.shuffled()

        _state.update {
            it.copy(
                currentRound = round,
                phase = SequencePhase.MEMORIZE,
                targetSequence = targetSeq,
                shuffledChoices = choices,
                userSequence = emptyList(),
                feedbackMessage = null
            )
        }

        // Auto transition after 4 seconds if user doesn't tap "I'M READY"
        memorizeJob?.cancel()
        memorizeJob = viewModelScope.launch {
            delay(4000)
            if (_state.value.phase == SequencePhase.MEMORIZE) {
                startRecallPhase()
            }
        }
    }

    fun startRecallPhase() {
        memorizeJob?.cancel()
        _state.update {
            it.copy(
                phase = SequencePhase.RECALL,
                userSequence = emptyList(),
                feedbackMessage = null
            )
        }
    }

    fun onChoiceClicked(icon: CardIcon) {
        val currentState = _state.value
        if (currentState.phase != SequencePhase.RECALL || currentState.isGameComplete) return

        val newUserSeq = currentState.userSequence + icon
        _state.update { it.copy(userSequence = newUserSeq) }

        // Check if user has entered the full sequence length
        if (newUserSeq.size == currentState.targetSequence.size) {
            if (newUserSeq == currentState.targetSequence) {
                // Correct sequence
                val newCorrectRounds = currentState.correctRounds + 1
                if (currentState.currentRound >= currentState.totalRounds) {
                    completeGame(newCorrectRounds, currentState.mistakes)
                } else {
                    setupRound(currentState.currentRound + 1, currentState.difficulty)
                    _state.update { it.copy(correctRounds = newCorrectRounds) }
                }
            } else {
                // Incorrect sequence
                val newMistakes = currentState.mistakes + 1
                _state.update {
                    it.copy(
                        mistakes = newMistakes,
                        userSequence = emptyList(),
                        feedbackMessage = "Not quite right. Try again!"
                    )
                }
            }
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

    private fun completeGame(correctRounds: Int, mistakes: Int) {
        timerJob?.cancel()
        memorizeJob?.cancel()

        val totalAttempts = correctRounds + mistakes
        val acc = if (totalAttempts > 0) ((correctRounds.toFloat() / totalAttempts) * 100).toInt() else 0

        val result = GameResult(
            gameType = CognitiveActivityType.SEQUENCE_RECALL.id,
            difficulty = _state.value.difficulty.name,
            totalPairs = _state.value.totalRounds,
            matchedPairs = correctRounds,
            mistakes = mistakes,
            completionTimeSeconds = _state.value.timeSeconds,
            accuracy = acc.toFloat(),
            completed = true
        )

        lastGameResult = result

        _state.update {
            it.copy(
                correctRounds = correctRounds,
                isGameComplete = true
            )
        }

        viewModelScope.launch {
            repository.saveGameResult(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        memorizeJob?.cancel()
    }
}

class SequenceRecallViewModelFactory(
    private val repository: GameResultRepository,
    private val initialDifficulty: Difficulty = Difficulty.EASY
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SequenceRecallViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SequenceRecallViewModel(repository, initialDifficulty) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
