package com.yourteam.sahara.model

import androidx.annotation.StringRes

/**
 * Text chosen by engines and ViewModels but resolved by the UI. ViewModels outlive the
 * Activity recreation that a language switch triggers, so they must not cache finished strings.
 * Arguments may themselves be [UiText] (for example a localized activity name).
 */
class UiText(@StringRes val id: Int, vararg val args: Any)
