package com.yourteam.sahara.model

data class MemoryCardModel(
    val id: Int,
    val icon: CardIcon,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false
)