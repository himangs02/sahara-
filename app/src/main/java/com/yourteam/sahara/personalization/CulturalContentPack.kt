package com.yourteam.sahara.personalization

import androidx.annotation.StringRes
import com.yourteam.sahara.R
import com.yourteam.sahara.model.CardIcon

/**
 * An OPTIONAL set of familiar local names for the same pictures every game already uses.
 * A pack never changes game logic, difficulty, or the underlying [CardIcon] set -- it only
 * offers a more familiar *name* for a subset of them (Stage 3D, Part 5-7).
 *
 * This is deliberately NOT a claim about who the user is ("Assamese users like tea"): it is a
 * caregiver-controlled, opt-out convenience so a picture can be called something the patient
 * already recognizes. See [PersonalizationPreferences] for the on/off control.
 */
data class CulturalContentPack(
    val id: String,
    @StringRes val displayNameRes: Int,
    /** Only icons with genuinely familiar local names are listed; everything else falls
     * back to [GameContentProvider]'s generic name for that picture. */
    val itemLabels: Map<CardIcon, Int>,
    /** Short, supportive lines a result/encouragement screen may optionally draw from. */
    @StringRes val encouragementRes: List<Int> = emptyList()
)

/** Real, checked Assamese content: Kopou (the state flower), tea, bamboo, japi and gamocha are
 * everyday, widely recognized objects across Assam -- not a stereotype about any one person. */
val AssamContentPack = CulturalContentPack(
    id = "assam",
    displayNameRes = R.string.cultural_content_label,
    itemLabels = mapOf(
        CardIcon.FLOWER to R.string.cultural_item_kopou,
        CardIcon.CUP to R.string.cultural_item_tea,
        CardIcon.TREE to R.string.cultural_item_bamboo,
        CardIcon.UMBRELLA to R.string.cultural_item_japi,
        CardIcon.APPLE to R.string.cultural_item_gamocha
    )
)
