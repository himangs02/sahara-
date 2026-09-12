package com.yourteam.sahara.model

enum class SequencePhase {
    MEMORIZE,
    RECALL
}

data class SequenceItem(
    val id: Int,
    val icon: CardIcon
)
