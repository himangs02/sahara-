package com.yourteam.sahara.ai

import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.GameResultEntity
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.SequencePhase
import com.yourteam.sahara.viewmodel.SequenceRecallViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.lifecycle.ViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class SequenceRecallViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeDao = object : GameResultDao {
        override fun insertGameResult(gameResult: GameResultEntity): Long = 1L
        override fun getAllGameResults(): Flow<List<GameResultEntity>> = flowOf(emptyList())
        override fun getAllGameResultsSync(): List<GameResultEntity> = emptyList()
        override fun getRecentGameResults(limit: Int): Flow<List<GameResultEntity>> = flowOf(emptyList())
        override fun getResultsForGameType(gameType: String): Flow<List<GameResultEntity>> = flowOf(emptyList())
    }

    private val repository = GameResultRepository(fakeDao)

    private val viewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    private fun ViewModel.clear() {
        try {
            val method = ViewModel::class.java.getDeclaredMethod("clear")
            method.isAccessible = true
            method.invoke(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun `Easy sequence length = 3`() {
        val viewModel = SequenceRecallViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        assertEquals(3, viewModel.state.value.targetSequence.size)
    }

    @Test
    fun `Medium sequence length = 4`() {
        val viewModel = SequenceRecallViewModel(repository, Difficulty.MEDIUM).also { viewModels.add(it) }
        assertEquals(4, viewModel.state.value.targetSequence.size)
    }

    @Test
    fun `Hard sequence length = 5`() {
        val viewModel = SequenceRecallViewModel(repository, Difficulty.HARD).also { viewModels.add(it) }
        assertEquals(5, viewModel.state.value.targetSequence.size)
    }

    @Test
    fun `Correct sequence accepted and advances round`() {
        val viewModel = SequenceRecallViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        viewModel.startRecallPhase()

        val target = viewModel.state.value.targetSequence
        target.forEach { icon ->
            viewModel.onChoiceClicked(icon)
        }

        // Correct sequence should advance round to 2
        assertEquals(2, viewModel.state.value.currentRound)
        assertEquals(1, viewModel.state.value.correctRounds)
    }

    @Test
    fun `Incorrect sequence rejected and increments mistakes`() {
        val viewModel = SequenceRecallViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        viewModel.startRecallPhase()

        val target = viewModel.state.value.targetSequence
        // Tap wrong choices to fill length
        val wrongIcon = com.yourteam.sahara.model.CardIcon.entries.first { it != target.first() }
        
        repeat(target.size) {
            viewModel.onChoiceClicked(wrongIcon)
        }

        assertEquals(1, viewModel.state.value.mistakes)
        assertEquals(0, viewModel.state.value.correctRounds)
        assertTrue(viewModel.state.value.userSequence.isEmpty()) // Reset for retry
    }

    @Test
    fun `Accuracy calculated correctly and game completion detected`() {
        val viewModel = SequenceRecallViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        
        // 3 total rounds
        for (round in 1..3) {
            viewModel.startRecallPhase()
            val target = viewModel.state.value.targetSequence
            target.forEach { icon ->
                viewModel.onChoiceClicked(icon)
            }
        }

        assertTrue(viewModel.state.value.isGameComplete)
        assertEquals(3, viewModel.state.value.correctRounds)
    }
}
