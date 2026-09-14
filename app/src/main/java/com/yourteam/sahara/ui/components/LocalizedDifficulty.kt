package com.yourteam.sahara.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.yourteam.sahara.R
import com.yourteam.sahara.model.Difficulty
import com.yourteam.sahara.model.UiText
import java.util.Locale

@Composable
fun Difficulty.localizedName(): String = stringResource(when (this) {
    Difficulty.EASY -> R.string.easy
    Difficulty.MEDIUM -> R.string.medium
    Difficulty.HARD -> R.string.hard
})

@Composable
fun UiText.asString(): String =
    stringResource(id, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())

/** The UI language's locale, for dates; Locale.getDefault() can lag behind a per-app language. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
