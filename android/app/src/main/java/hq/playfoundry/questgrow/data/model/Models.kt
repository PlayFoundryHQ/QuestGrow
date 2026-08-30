package hq.playfoundry.questgrow.data.model

import hq.playfoundry.questgrow.adapt.ComplexityProfile

/** Child-visible quest state. Never carries a stage/level (INV-8). */
enum class QuestVisualState { AVAILABLE, PENDING_GROWNUP, VERIFIED, QUEUED_OFFLINE }

data class TodayQuest(
    val questId: String,
    val title: String,
    val icon: String,
    val state: QuestVisualState,
    val waitsForGrownup: Boolean,
)

data class TodayView(
    val childId: String,
    val onDate: String,
    val quests: List<TodayQuest>,
    val lifetimeAchievement: Int,
    val spendableBalance: Int,
    val profile: ComplexityProfile,
    /** true when served from the on-disk cache (device is offline). */
    val stale: Boolean = false,
) {
    /** Layout hint from the server; the client honours it, doesn't compute it. */
    val visibleQuests: List<TodayQuest> get() = quests.take(profile.questsShownAtOnce)
    val allDone: Boolean
        get() = visibleQuests.isNotEmpty() && visibleQuests.all { it.state == QuestVisualState.VERIFIED }
}

/** Result of submitting a completion (online or from the flush). */
sealed interface CompletionOutcome {
    data object Verified : CompletionOutcome            // Mode B — celebrate now
    data object WaitingForGrownup : CompletionOutcome   // Mode A — pending
    data object QueuedOffline : CompletionOutcome       // captured locally, will sync
    data class Rejected(val code: String, val detail: String) : CompletionOutcome
}

data class Celebration(
    val questId: String,
    val pointsAwarded: Int,
    val at: String,
)

data class ChildProgress(
    val lifetimeAchievement: Int,
    val spendableBalance: Int,
    val weekActiveDays: Int,
    val stale: Boolean = false,
)

// ---- parent side ----
data class ChildProfile(
    val childId: String,
    val name: String,
    val ageBand: String,
    val avatar: String,
    val birthdate: String?,
)

data class ParentQuest(
    val questId: String,
    val version: Int,
    val title: String,
    val icon: String,
    val points: Int,
    val active: Boolean,
    val archived: Boolean,
)

data class ParentReward(
    val rewardId: String,
    val name: String,
    val icon: String,
    val cost: Int,
    val mode: String,
    val active: Boolean,
)

/** A pending "child asked to spend points" request — the parent redemption inbox. */
data class PendingRedemption(
    val id: String,
    val childId: String,
    val childName: String,
    val rewardId: String,
    val rewardName: String,
    val rewardIcon: String,
    val cost: Int,
)

/** One reward as the child sees it, plus whether they can afford it / already asked. */
data class KidReward(
    val rewardId: String,
    val name: String,
    val icon: String,
    val cost: Int,
    /** true when the reward needs a grown-up's yes (`parent_confirmed`). */
    val needsGrownup: Boolean,
    val affordable: Boolean,
    val pending: Boolean,
)

data class KidRewards(val spendableBalance: Int, val rewards: List<KidReward>)

/** Outcome of a child asking to spend points. */
sealed interface RedeemOutcome {
    data class Granted(val rewardName: String) : RedeemOutcome   // self-service — celebrate
    data object AskedGrownup : RedeemOutcome                     // parent_confirmed — now pending
    data class Rejected(val detail: String) : RedeemOutcome
}

/** One child that has been activated on THIS device (its board can be shown). */
data class DeviceChild(val childId: String, val name: String)

data class Approval(val questId: String, val onDate: String)

data class AdvancementSuggestion(
    val questId: String,
    val fromStage: String,
    val toStage: String,
)

data class TransitionPlan(val direction: String, val bypassed: List<String>)

data class Dashboard(
    val childId: String,
    val onDate: String,
    val total: Int,
    val verified: Int,
    val pending: Int,
    val available: Int,
    val expired: Int,
    val weekActiveDays: Int,
)

data class ParentNotification(val childId: String, val kind: String, val text: String, val at: String)

/** The four support levels — parent-facing copy only, never shown to the child. */
enum class OwnershipStage(val wire: String, val parentLabel: String) {
    PARENT_MANAGED("PARENT_MANAGED", "Most support — do it together"),
    PARENT_GUIDED("PARENT_GUIDED", "Guided — you check it"),
    CHILD_PARTICIPATED("CHILD_PARTICIPATED", "Child leads — no check"),
    CHILD_OWNED("CHILD_OWNED", "Child owns it");

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire } ?: PARENT_GUIDED
    }
}
