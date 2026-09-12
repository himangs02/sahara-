package com.yourteam.sahara.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.ui.graphics.vector.ImageVector

enum class CardIcon {
    FLOWER, CUP, TREE, BOOK, HOME, UMBRELLA, CLOCK, APPLE;

    val imageVector: ImageVector
        get() = when (this) {
            FLOWER -> Icons.Default.LocalFlorist
            CUP -> Icons.Default.LocalCafe
            TREE -> Icons.Default.Park
            @Suppress("DEPRECATION")
            BOOK -> Icons.Default.MenuBook
            HOME -> Icons.Default.Home
            UMBRELLA -> Icons.Default.Umbrella
            CLOCK -> Icons.Default.Schedule
            APPLE -> Icons.Default.Star // Fallback icon since Apple is not in this version
        }
}