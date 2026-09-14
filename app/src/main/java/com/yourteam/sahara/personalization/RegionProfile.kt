package com.yourteam.sahara.personalization

import androidx.annotation.StringRes
import com.yourteam.sahara.R
import com.yourteam.sahara.language.AppLanguage

/**
 * A North-Eastern-Region profile. Stage 3D implements high-quality support for one region
 * (Assam) and leaves the registry open for more -- adding a state later means adding one more
 * [RegionProfile] entry and (optionally) a [CulturalContentPack], never touching game logic.
 *
 * Deliberately NOT implemented yet (Stage 3D, Part 5 "do not build content for every state
 * now"): Meghalaya, Manipur, Mizoram, Nagaland, Tripura, Arunachal Pradesh, Sikkim. A patient
 * whose region doesn't match any profile here simply gets the generic content -- never a crash,
 * never a blank screen (see [GameContentProvider]).
 */
data class RegionProfile(
    val regionCode: String,
    @StringRes val displayNameRes: Int,
    val supportedLanguages: List<AppLanguage>,
    val culturalContentPack: CulturalContentPack? = null
)

object NerRegions {
    val ASSAM = RegionProfile(
        regionCode = "assam",
        displayNameRes = R.string.region_assam,
        supportedLanguages = listOf(AppLanguage.ASSAMESE, AppLanguage.ENGLISH, AppLanguage.HINDI),
        culturalContentPack = AssamContentPack
    )

    private val all = listOf(ASSAM)

    /** Matches a patient's free-text `region` field (e.g. "Assam", "assam, India") against a
     * known profile. Returns null for anything unrecognized -- that's not an error, it just
     * means only generic content is available for this patient yet. */
    fun match(regionText: String?): RegionProfile? {
        val normalized = regionText?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return null
        return all.find { normalized.contains(it.regionCode) }
    }
}
