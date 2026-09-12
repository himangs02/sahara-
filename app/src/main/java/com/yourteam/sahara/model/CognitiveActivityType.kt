package com.yourteam.sahara.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Spa
import androidx.compose.ui.graphics.vector.ImageVector
import com.yourteam.sahara.R

enum class CognitiveActivityType(
    val id: String,
    val displayName: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector
) {
    MEMORY_MATCH(
        id = "MEMORY_MATCH",
        displayName = "Memory Match",
        titleRes = R.string.memory_match,
        descriptionRes = R.string.memory_match_desc,
        icon = Icons.Default.Spa
    ),
    ATTENTION_TAP(
        id = "ATTENTION_TAP",
        displayName = "Attention Tap",
        titleRes = R.string.attention_tap,
        descriptionRes = R.string.attention_tap_desc,
        icon = Icons.Default.GridOn
    ),
    @Suppress("DEPRECATION")
    SEQUENCE_RECALL(
        id = "SEQUENCE_RECALL",
        displayName = "Sequence Recall",
        titleRes = R.string.sequence_recall,
        descriptionRes = R.string.sequence_recall_desc,
        icon = Icons.Default.ListAlt
    );

    companion object {
        fun fromId(id: String): CognitiveActivityType {
            return entries.find { it.id.equals(id, ignoreCase = true) || it.displayName.equals(id, ignoreCase = true) }
                ?: MEMORY_MATCH
        }
    }
}
