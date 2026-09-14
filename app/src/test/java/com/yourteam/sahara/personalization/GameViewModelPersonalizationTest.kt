package com.yourteam.sahara.personalization

import com.yourteam.sahara.R
import com.yourteam.sahara.data.local.GameResultDao
import com.yourteam.sahara.data.local.GameResultEntity
import com.yourteam.sahara.data.repository.GameResultRepository
import com.yourteam.sahara.model.CardIcon
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.viewmodel.AttentionGameViewModel
import com.yourteam.sahara.viewmodel.MemoryGameViewModel
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
import org.junit.Before
import org.junit.Test

/** Verifies the three cognitive games resolve card labels the same way GameContentProvider
 * would -- i.e. region + the caregiver's cultural-content preference genuinely reach the game,
 * rather than the games hard-coding content themselves (Stage 3D, Part 7/8/9). */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelPersonalizationTest {

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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Memory Match uses Assamese labels for an Assam patient with cultural content on`() {
        val viewModel = MemoryGameViewModel(repository, Difficulty.EASY, patientRegion = "Assam", culturalContentEnabled = true)
        assertEquals(R.string.cultural_item_tea, viewModel.labelFor(CardIcon.CUP))
    }

    @Test
    fun `Memory Match uses generic labels when cultural content is off`() {
        val viewModel = MemoryGameViewModel(repository, Difficulty.EASY, patientRegion = "Assam", culturalContentEnabled = false)
        assertEquals(R.string.card_name_cup, viewModel.labelFor(CardIcon.CUP))
    }

    @Test
    fun `Memory Match uses generic labels for a patient with no matching region`() {
        val viewModel = MemoryGameViewModel(repository, Difficulty.EASY, patientRegion = "Delhi", culturalContentEnabled = true)
        assertEquals(R.string.card_name_cup, viewModel.labelFor(CardIcon.CUP))
    }

    @Test
    fun `Memory Match difficulty pair counts are unaffected by personalization`() {
        val viewModel = MemoryGameViewModel(repository, Difficulty.HARD, patientRegion = "Assam", culturalContentEnabled = true)
        assertEquals(8, viewModel.state.value.cards.size / 2)
    }

    @Test
    fun `Sequence Recall uses Assamese labels for an Assam patient`() {
        val viewModel = SequenceRecallViewModel(repository, Difficulty.EASY, patientRegion = "Assam", culturalContentEnabled = true)
        assertEquals(R.string.cultural_item_bamboo, viewModel.labelFor(CardIcon.TREE))
    }

    @Test
    fun `Attention Tap uses Assamese labels for an Assam patient`() {
        val viewModel = AttentionGameViewModel(repository, Difficulty.EASY, patientRegion = "Assam", culturalContentEnabled = true)
        assertEquals(R.string.cultural_item_gamocha, viewModel.labelFor(CardIcon.APPLE))
    }

    @Test
    fun `Games default to generic content when no region or preference is supplied`() {
        // Existing callers (and existing tests) that construct these ViewModels without the new
        // parameters must keep working exactly as before.
        val memory = MemoryGameViewModel(repository, Difficulty.EASY)
        val sequence = SequenceRecallViewModel(repository, Difficulty.EASY)
        val attention = AttentionGameViewModel(repository, Difficulty.EASY)

        assertEquals(R.string.card_name_flower, memory.labelFor(CardIcon.FLOWER))
        assertEquals(R.string.card_name_flower, sequence.labelFor(CardIcon.FLOWER))
        assertEquals(R.string.card_name_flower, attention.labelFor(CardIcon.FLOWER))
    }
}
