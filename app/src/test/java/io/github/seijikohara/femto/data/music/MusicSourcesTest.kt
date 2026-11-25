package io.github.seijikohara.femto.data.music

import org.junit.Test
import kotlin.test.assertEquals

class MusicSourcesTest {
    private val own = "io.github.seijikohara.femto"

    @Test
    fun keeps_picks_in_order_when_nothing_played() =
        assertEquals(listOf("spotify", "radio"), musicSourceOrder(listOf("spotify", "radio"), null, own))

    @Test
    fun leads_with_last_used_when_it_is_not_picked() =
        assertEquals(listOf("tidal", "spotify", "radio"), musicSourceOrder(listOf("spotify", "radio"), "tidal", own))

    @Test
    fun keeps_last_used_in_place_when_it_is_picked() =
        assertEquals(listOf("spotify", "radio"), musicSourceOrder(listOf("spotify", "radio"), "radio", own))

    @Test
    fun drops_duplicates_and_the_launcher_itself() =
        assertEquals(listOf("spotify"), musicSourceOrder(listOf("spotify", own, "spotify"), own, own))

    @Test
    fun returns_empty_when_nothing_picked_or_played() =
        assertEquals(emptyList(), musicSourceOrder(emptyList(), null, own))
}
