package com.yourteam.sahara.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.sahara.model.CardIcon
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.GameResult
import com.yourteam.sahara.model.MemoryCardModel
import com.yourteam.sahara.personalization.GameContentProvider
import com.yourteam.sahara.personalization.NerRegions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryGameState(
    val cards: List<MemoryCardModel> = emptyList(),
    val pairsFound: Int = 0,
    val mistakes: Int = 0,
    val timeSeconds: Long = 0,
    val isGameComplete: Boolean = false,
    val difficulty: Difficulty = Difficulty.EASY,
    val isEvaluating: Boolean = false,
    val accuracy: Int = 0
)

class MemoryGameViewModel(
    private val repository: com.yourteam.sahara.data.repository.GameResultRepository,
    private val initialDifficulty: Difficulty = Difficulty.EASY,
    /** The patient's free-text region (e.g. "Assam"); null means only generic content. */
    private val patientRegion: String? = null,
    /** Caregiver-controlled preference (Stage 3D, Part 6); see PersonalizationPreferences. */
    private val culturalContentEnabled: Boolean = true
) : ViewModel() {
    private val _state = MutableStateFlow(MemoryGameState())
    val state: StateFlow<MemoryGameState> = _state.asStateFlow()

    private val regionProfile = NerRegions.match(patientRegion)

    private var timerJob: Job? = null
    // Cache the most recent result for UI display
    var lastGameResult: GameResult? = null
        private set

    /** The familiar name to show for [icon]'s picture -- generic unless a cultural pack for
     * this patient's region is available and enabled (Stage 3D, Part 7). Never returns nothing:
     * every [CardIcon] always has at least a generic name. */
    @StringRes
    fun labelFor(icon: CardIcon): Int = GameContentProvider.labelFor(icon, regionProfile, culturalContentEnabled)

    init {
        startGame(initialDifficulty)
    }

    fun startGame(difficulty: Difficulty = Difficulty.EASY) {
        timerJob?.cancel()
        val icons = CardIcon.entries.take(difficulty.pairs)
        val cardPairs = (icons + icons).shuffled().mapIndexed { index, icon ->
            MemoryCardModel(id = index, icon = icon)
        }

        _state.value = MemoryGameState(
            cards = cardPairs,
            difficulty = difficulty
        )
        startTimer()
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(timeSeconds = it.timeSeconds + 1) }
            }
        }
    }

    fun onCardClicked(card: MemoryCardModel) {
        val currentState = _state.value
        if (currentState.isEvaluating || card.isFaceUp || card.isMatched || currentState.isGameComplete) return

        val updatedCards = currentState.cards.map {
            if (it.id == card.id) it.copy(isFaceUp = true) else it
        }
        _state.update { it.copy(cards = updatedCards) }

        val faceUpCards = updatedCards.filter { it.isFaceUp && !it.isMatched }
        if (faceUpCards.size == 2) {
            _state.update { it.copy(isEvaluating = true) }
            viewModelScope.launch {
                delay(1000) // Brief pause to show the mismatched cards
                evaluateMatch(faceUpCards[0], faceUpCards[1])
            }
        }
    }

    private fun evaluateMatch(card1: MemoryCardModel, card2: MemoryCardModel) {
        val isMatch = card1.icon == card2.icon
        val currentCards = _state.value.cards.toMutableList()
        var newMistakes = _state.value.mistakes
        var newPairs = _state.value.pairsFound

        if (isMatch) {
            newPairs++
            for (i in currentCards.indices) {
                if (currentCards[i].id == card1.id || currentCards[i].id == card2.id) {
                    currentCards[i] = currentCards[i].copy(isMatched = true)
                }
            }
        } else {
            newMistakes++
            for (i in currentCards.indices) {
                if (currentCards[i].id == card1.id || currentCards[i].id == card2.id) {
                    currentCards[i] = currentCards[i].copy(isFaceUp = false)
                }
            }
        }

        val totalPairsNeeded = _state.value.difficulty.pairs
        val isComplete = newPairs == totalPairsNeeded

        var acc = 0
        val totalAttempts = newPairs + newMistakes
        if (totalAttempts > 0) {
            acc = ((newPairs.toFloat() / totalAttempts) * 100).toInt()
        }

        if (isComplete) {
            timerJob?.cancel()
            saveGameResult(newPairs, newMistakes, acc)
        }

        _state.update {
            it.copy(
                cards = currentCards,
                isEvaluating = false,
                mistakes = newMistakes,
                pairsFound = newPairs,
                isGameComplete = isComplete,
                accuracy = acc
            )
        }
    }

    private fun saveGameResult(pairs: Int, mistakes: Int, acc: Int) {
        val result = GameResult(
            patientId = repository.patientId,
            difficulty = _state.value.difficulty.name,
            totalPairs = _state.value.difficulty.pairs,
            matchedPairs = pairs,
            mistakes = mistakes,
            completionTimeSeconds = _state.value.timeSeconds,
            accuracy = acc.toFloat(),
            completed = true
        )
        lastGameResult = result
        
        viewModelScope.launch {
            repository.saveGameResult(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
