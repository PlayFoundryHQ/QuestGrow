package hq.playfoundry.questgrow.adapt

import hq.playfoundry.questgrow.data.net.ComplexityProfileDto

/**
 * The server-resolved age-adaptation profile (TECHNICAL_MODEL §13). The client
 * **consumes** these values — it never runs its own age logic (grant §6). This
 * type only maps the string values to rendering intents; if the server adds a
 * new value the UI falls back to the middle option rather than inventing one.
 *
 * INV-8: there is deliberately no ownership_stage / level / readiness here.
 */
data class ComplexityProfile(
    val band: String,
    val textStyle: TextStyle,
    val audioNarration: AudioNarration,
    val iconography: Iconography,
    val questsShownAtOnce: Int,
    val interaction: String,
    val taskComplexity: String,
    val readingRequirement: String,
    val rewardPresentation: RewardPresentation,
) {
    enum class TextStyle { ICON_ONLY, SHORT_LABEL, SHORT_SENTENCE }
    enum class AudioNarration { ALWAYS, ON_TAP }
    enum class Iconography { LARGE_SIMPLE, STANDARD }
    enum class RewardPresentation { BIG_ANIMATION, ANIMATION_PROGRESS, PROGRESS_COLLECTIBLES }

    val showLabels: Boolean get() = textStyle != TextStyle.ICON_ONLY
    val autoReadOnOpen: Boolean get() = audioNarration == AudioNarration.ALWAYS

    companion object {
        fun from(dto: ComplexityProfileDto) = ComplexityProfile(
            band = dto.band,
            textStyle = when (dto.textStyle) {
                "icon_only" -> TextStyle.ICON_ONLY
                "short_sentence" -> TextStyle.SHORT_SENTENCE
                else -> TextStyle.SHORT_LABEL
            },
            audioNarration = if (dto.audioNarration == "always") AudioNarration.ALWAYS else AudioNarration.ON_TAP,
            iconography = if (dto.iconography == "large_simple") Iconography.LARGE_SIMPLE else Iconography.STANDARD,
            questsShownAtOnce = dto.questsShownAtOnce.coerceIn(1, 12),
            interaction = dto.interaction,
            taskComplexity = dto.taskComplexity,
            readingRequirement = dto.readingRequirement,
            rewardPresentation = when (dto.rewardPresentation) {
                "big_animation" -> RewardPresentation.BIG_ANIMATION
                "progress_collectibles" -> RewardPresentation.PROGRESS_COLLECTIBLES
                else -> RewardPresentation.ANIMATION_PROGRESS
            },
        )
    }
}
