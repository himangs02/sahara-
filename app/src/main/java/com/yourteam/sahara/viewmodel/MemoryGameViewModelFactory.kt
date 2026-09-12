package com.yourteam.sahara.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yourteam.sahara.data.repository.GameResultRepository

class MemoryGameViewModelFactory(
    private val repository: GameResultRepository,
    private val initialDifficulty: com.yourteam.sahara.model.Difficulty
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryGameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MemoryGameViewModel(repository, initialDifficulty) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}