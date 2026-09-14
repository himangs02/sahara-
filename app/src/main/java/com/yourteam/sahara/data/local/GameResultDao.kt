package com.yourteam.sahara.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertGameResult(gameResult: GameResultEntity): Long

    @Query("SELECT * FROM game_results ORDER BY timestamp DESC")
    fun getAllGameResults(): Flow<List<GameResultEntity>>

    @Query("SELECT * FROM game_results WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getGameResultsForPatient(patientId: String): Flow<List<GameResultEntity>>

    @Query("SELECT * FROM game_results")
    fun getAllGameResultsSync(): List<GameResultEntity>

    @Query("SELECT * FROM game_results ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentGameResults(limit: Int): Flow<List<GameResultEntity>>

    @Query("SELECT * FROM game_results WHERE gameType = :gameType ORDER BY timestamp DESC")
    fun getResultsForGameType(gameType: String): Flow<List<GameResultEntity>>
}