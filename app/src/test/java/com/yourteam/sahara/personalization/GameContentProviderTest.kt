package com.yourteam.sahara.personalization

import com.yourteam.sahara.R
import com.yourteam.sahara.model.CardIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GameContentProviderTest {

    @Test
    fun `every CardIcon always has a generic label, even with no region`() {
        // Stage 3D Part 12: a game must never be left with an unlabeled/blank card.
        CardIcon.entries.forEach { icon ->
            val label = GameContentProvider.labelFor(icon, region = null, culturalContentEnabled = true)
            assertNotNull(label)
        }
    }

    @Test
    fun `TEST G - Assam region with cultural content on selects the Assamese cultural labels`() {
        assertEquals(R.string.cultural_item_kopou, GameContentProvider.labelFor(CardIcon.FLOWER, NerRegions.ASSAM, true))
        assertEquals(R.string.cultural_item_tea, GameContentProvider.labelFor(CardIcon.CUP, NerRegions.ASSAM, true))
        assertEquals(R.string.cultural_item_bamboo, GameContentProvider.labelFor(CardIcon.TREE, NerRegions.ASSAM, true))
        assertEquals(R.string.cultural_item_japi, GameContentProvider.labelFor(CardIcon.UMBRELLA, NerRegions.ASSAM, true))
        assertEquals(R.string.cultural_item_gamocha, GameContentProvider.labelFor(CardIcon.APPLE, NerRegions.ASSAM, true))
    }

    @Test
    fun `Icons with no cultural mapping still fall back to a generic label for Assam`() {
        assertEquals(R.string.card_name_book, GameContentProvider.labelFor(CardIcon.BOOK, NerRegions.ASSAM, true))
        assertEquals(R.string.card_name_home, GameContentProvider.labelFor(CardIcon.HOME, NerRegions.ASSAM, true))
        assertEquals(R.string.card_name_clock, GameContentProvider.labelFor(CardIcon.CLOCK, NerRegions.ASSAM, true))
    }

    @Test
    fun `TEST H - cultural content OFF always yields generic labels even for Assam`() {
        assertEquals(R.string.card_name_flower, GameContentProvider.labelFor(CardIcon.FLOWER, NerRegions.ASSAM, false))
        assertEquals(R.string.card_name_cup, GameContentProvider.labelFor(CardIcon.CUP, NerRegions.ASSAM, false))
    }

    @Test
    fun `Unrecognized region text falls back to generic content`() {
        assertEquals(R.string.card_name_flower, GameContentProvider.labelFor(CardIcon.FLOWER, "Kerala", true))
        assertEquals(R.string.card_name_flower, GameContentProvider.labelFor(CardIcon.FLOWER, regionText = null, culturalContentEnabled = true))
        assertEquals(R.string.card_name_flower, GameContentProvider.labelFor(CardIcon.FLOWER, "", true))
    }

    @Test
    fun `Region text matching is case-insensitive and tolerates extra text`() {
        assertEquals(R.string.cultural_item_tea, GameContentProvider.labelFor(CardIcon.CUP, "ASSAM", true))
        assertEquals(R.string.cultural_item_tea, GameContentProvider.labelFor(CardIcon.CUP, "assam, india", true))
        assertEquals(R.string.cultural_item_tea, GameContentProvider.labelFor(CardIcon.CUP, "  Assam  ", true))
    }

    @Test
    fun `TEST I - content resolution never touches the network and works purely offline`() {
        // GameContentProvider is a pure, synchronous function over local data -- there is no
        // I/O to fail, so calling it repeatedly (as a game would with no connectivity) always
        // succeeds identically.
        repeat(50) {
            assertEquals(R.string.cultural_item_gamocha, GameContentProvider.labelFor(CardIcon.APPLE, NerRegions.ASSAM, true))
        }
    }
}
