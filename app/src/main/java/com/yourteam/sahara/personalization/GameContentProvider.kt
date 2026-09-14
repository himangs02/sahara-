package com.yourteam.sahara.personalization

import androidx.annotation.StringRes
import com.yourteam.sahara.R
import com.yourteam.sahara.model.CardIcon

/**
 * Resolves what a game card's picture should be *called* -- the only thing that ever changes
 * between "generic" and "cultural" content (Stage 3D, Part 7/8/9). Card pictures, matching
 * logic, and difficulty are never touched here or by any caller; this is purely a naming layer,
 * so it works fully offline and can never leave a card unlabeled.
 */
object GameContentProvider {

    private val genericLabels: Map<CardIcon, Int> = mapOf(
        CardIcon.FLOWER to R.string.card_name_flower,
        CardIcon.CUP to R.string.card_name_cup,
        CardIcon.TREE to R.string.card_name_tree,
        CardIcon.BOOK to R.string.card_name_book,
        CardIcon.HOME to R.string.card_name_home,
        CardIcon.UMBRELLA to R.string.card_name_umbrella,
        CardIcon.CLOCK to R.string.card_name_clock,
        CardIcon.APPLE to R.string.card_name_star
    )

    /** Always returns a valid resource id -- generic labels cover every [CardIcon], so this
     * never falls through to null, blank text, or a raw enum/resource name (Stage 3D, Part 12). */
    @StringRes
    fun labelFor(icon: CardIcon, region: RegionProfile?, culturalContentEnabled: Boolean): Int {
        if (culturalContentEnabled) {
            region?.culturalContentPack?.itemLabels?.get(icon)?.let { return it }
        }
        return genericLabels.getValue(icon)
    }

    /** Convenience overload for callers that only have the patient's free-text region field. */
    @StringRes
    fun labelFor(icon: CardIcon, regionText: String?, culturalContentEnabled: Boolean): Int =
        labelFor(icon, NerRegions.match(regionText), culturalContentEnabled)
}
