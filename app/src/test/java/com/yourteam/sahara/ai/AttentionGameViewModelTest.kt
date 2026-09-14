package com.yourteam.sahara.ai

import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.GameResultEntity
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.viewmodel.AttentionGameViewModel
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
class AttentionGameViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val fakeDao = object : GameResultDao {
        override fun insertGameResult(gameResult: GameResultEntity): Long = 1L
        override fun getAllGameResults(): Flow<List<GameResultEntity>> = flowOf(emptyList())
        override fun getGameResultsForPatient(patientId: String): Flow<List<GameResultEntity>> = flowOf(emptyList())
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
    fun `Easy has correct grid size (6 symbols)`() {
        val viewModel = AttentionGameViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        assertEquals(6, viewModel.state.value.gridSymbols.size)
    }

    @Test
    fun `Medium has correct grid size (9 symbols)`() {
        val viewModel = AttentionGameViewModel(repository, Difficulty.MEDIUM).also { viewModels.add(it) }
        assertEquals(9, viewModel.state.value.gridSymbols.size)
    }

    @Test
    fun `Hard has correct grid size (12 symbols)`() {
        val viewModel = AttentionGameViewModel(repository, Difficulty.HARD).also { viewModels.add(it) }
        assertEquals(12, viewModel.state.value.gridSymbols.size)
    }

    @Test
    fun `Correct target increases score`() {
        val viewModel = AttentionGameViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        val initialCorrect = viewModel.state.value.correctCount
        
        val targetItem = viewModel.state.value.gridSymbols.first { it.icon == viewModel.state.value.targetIcon }
        viewModel.onSymbolClicked(targetItem)

        assertEquals(initialCorrect + 1, viewModel.state.value.correctCount)
    }

    @Test
    fun `Incorrect target increments mistakes`() {
        val viewModel = AttentionGameViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        val wrongItem = viewModel.state.value.gridSymbols.first { it.icon != viewModel.state.value.targetIcon }
        
        viewModel.onSymbolClicked(wrongItem)

        assertEquals(1, viewModel.state.value.mistakes)
    }

    @Test
    fun `Accuracy is calculated correctly and game completion is detected`() {
        val viewModel = AttentionGameViewModel(repository, Difficulty.EASY).also { viewModels.add(it) }
        
        // Complete 5 rounds with 1 mistake along the way
        val wrongItem = viewModel.state.value.gridSymbols.first { it.icon != viewModel.state.value.targetIcon }
        viewModel.onSymbolClicked(wrongItem) // 1 mistake

        for (i in 1..5) {
            val targetItem = viewModel.state.value.gridSymbols.first { it.icon == viewModel.state.value.targetIcon }
            viewModel.onSymbolClicked(targetItem)
        }

        assertTrue(viewModel.state.value.isGameComplete)
        // 5 correct, 1 mistake = 5/6 = 83%
        assertEquals(83, viewModel.state.value.accuracy)
    }
}
