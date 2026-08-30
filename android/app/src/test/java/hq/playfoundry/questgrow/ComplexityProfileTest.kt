package hq.playfoundry.questgrow

import hq.playfoundry.questgrow.adapt.ComplexityProfile
import hq.playfoundry.questgrow.data.net.ComplexityProfileDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client *consumes* the server's age-adaptation values; it never runs its
 * own age logic (grant §6). An unknown value falls back to the middle option,
 * it does not invent a new one.
 */
class ComplexityProfileTest {

    private fun dto(
        band: String = "5-6", textStyle: String = "short_label", audio: String = "on_tap",
        iconography: String = "standard", shown: Int = 5, reward: String = "animation_progress",
    ) = ComplexityProfileDto(band, textStyle, audio, iconography, shown, "tap_drag",
        "small_multi_step", "minimal", reward)

    @Test fun `3-4 band maps to icon-only, auto-read, big animation`() {
        val p = ComplexityProfile.from(dto("3-4", "icon_only", "always", "large_simple", 3, "big_animation"))
        assertEquals(ComplexityProfile.TextStyle.ICON_ONLY, p.textStyle)
        assertFalse(p.showLabels)
        assertTrue(p.autoReadOnOpen)
        assertEquals(ComplexityProfile.RewardPresentation.BIG_ANIMATION, p.rewardPresentation)
        assertEquals(3, p.questsShownAtOnce)
    }

    @Test fun `7-8 band shows labels and more quests`() {
        val p = ComplexityProfile.from(dto("7-8", "short_sentence", "on_tap", "standard", 7, "progress_collectibles"))
        assertTrue(p.showLabels)
        assertEquals(7, p.questsShownAtOnce)
        assertEquals(ComplexityProfile.RewardPresentation.PROGRESS_COLLECTIBLES, p.rewardPresentation)
    }

    @Test fun `an unknown text_style falls back to the middle option, not an invented one`() {
        val p = ComplexityProfile.from(dto(textStyle = "hologram"))
        assertEquals(ComplexityProfile.TextStyle.SHORT_LABEL, p.textStyle)
    }

    @Test fun `quests_shown is clamped to a sane range`() {
        assertEquals(1, ComplexityProfile.from(dto(shown = 0)).questsShownAtOnce)
        assertEquals(12, ComplexityProfile.from(dto(shown = 999)).questsShownAtOnce)
    }
}
