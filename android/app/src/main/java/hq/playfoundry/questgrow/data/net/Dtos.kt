package hq.playfoundry.questgrow.data.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models — a 1:1 mirror of the QuestGrow API response/request schemas
 * (`src/questgrow/api.py`). Field names match the JSON exactly; nothing here
 * derives product state.
 */

// ---- auth ----------------------------------------------------------------
@Serializable data class SignupBody(val email: String, val password: String, val pin: String)
@Serializable data class LoginBody(val email: String, val password: String)
@Serializable data class UnlockBody(@SerialName("session_token") val sessionToken: String, val pin: String)
@Serializable data class ChildTokenBody(@SerialName("child_id") val childId: String)

@Serializable data class SignupResp(@SerialName("account_id") val accountId: String)
@Serializable data class LoginResp(@SerialName("session_token") val sessionToken: String)
@Serializable data class UnlockResp(@SerialName("parent_token") val parentToken: String)
@Serializable data class ChildTokenResp(@SerialName("child_token") val childToken: String)
@Serializable data class PairBody(val code: String)
@Serializable data class PairCodeResp(val code: String)

// ---- errors -------------------------------------------------------------
@Serializable data class ApiError(val detail: String = "", val code: String = "error")

// ---- children / profile ----------------------------------------------
@Serializable data class ChildBody(
    @SerialName("child_id") val childId: String,
    val name: String,
    @SerialName("age_band") val ageBand: String = "5-6",
    val avatar: String = "",
    val birthdate: String? = null,
)

@Serializable data class ChildProfileBody(
    val name: String? = null,
    val avatar: String? = null,
    @SerialName("age_band") val ageBand: String? = null,
    val birthdate: String? = null,
    @SerialName("adaptation_overrides") val adaptationOverrides: Map<String, String>? = null,
)

@Serializable data class ChildOut(
    @SerialName("child_id") val childId: String,
    val name: String,
    @SerialName("age_band") val ageBand: String,
    val avatar: String = "",
    val birthdate: String? = null,
)

// ---- quests / rewards ----------------------------------------------
@Serializable data class QuestBody(
    @SerialName("quest_id") val questId: String,
    val title: String,
    val icon: String,
    val points: Int = 10,
)
@Serializable data class QuestEditBody(
    val title: String? = null, val icon: String? = null, val points: Int? = null,
    val active: Boolean? = null, val archived: Boolean? = null,
)
@Serializable data class QuestOut(
    @SerialName("quest_id") val questId: String,
    val version: Int,
    val title: String,
    val icon: String,
    val points: Int,
    val active: Boolean,
    val archived: Boolean,
)
@Serializable data class ScheduleBody(val recurrence: String = "daily", val weekdays: List<Int> = emptyList())

@Serializable data class RewardBody(
    @SerialName("reward_id") val rewardId: String,
    val name: String, val icon: String, val cost: Int, val mode: String,
)
@Serializable data class RewardEditBody(
    val name: String? = null, val icon: String? = null, val cost: Int? = null,
    val mode: String? = null, val active: Boolean? = null,
)
@Serializable data class RewardOut(
    @SerialName("reward_id") val rewardId: String,
    val name: String, val icon: String, val cost: Int, val mode: String, val active: Boolean,
)

// ---- assignment / ownership --------------------------------------
@Serializable data class AssignBody(@SerialName("quest_id") val questId: String)
@Serializable data class OwnershipBody(val target: String)
@Serializable data class OwnershipPlanOut(val direction: String, val bypassed: List<String>)
@Serializable data class SuggestionOut(
    @SerialName("quest_id") val questId: String,
    @SerialName("from_stage") val fromStage: String,
    @SerialName("to_stage") val toStage: String,
)
@Serializable data class AssignedQuestDto(
    @SerialName("quest_id") val questId: String,
    val title: String,
    val icon: String,
    val points: Int,
    @SerialName("ownership_stage") val ownershipStage: String,
)

// ---- verification / review / ledger -----------------------------
@Serializable data class DayBody(val day: String)
@Serializable data class NotYetBody(val day: String, val note: String = "")
@Serializable data class ReviewBody(
    @SerialName("quest_id") val questId: String, val day: String, val note: String,
    val flagged: Boolean = false,
)
@Serializable data class AdjustmentBody(val amount: Int, val reason: String = "")
@Serializable data class ApprovalOut(
    @SerialName("quest_id") val questId: String,
    @SerialName("on_date") val onDate: String,
    val state: String,
)
@Serializable data class DashboardOut(
    @SerialName("child_id") val childId: String,
    @SerialName("on_date") val onDate: String,
    val total: Int, val verified: Int, val pending: Int, val available: Int, val expired: Int,
    @SerialName("week_active_days") val weekActiveDays: Int,
)
@Serializable data class RedemptionOut(
    val id: String, @SerialName("reward_id") val rewardId: String, val state: String,
)
@Serializable data class PendingRedemptionOut(
    val id: String,
    @SerialName("child_id") val childId: String,
    @SerialName("child_name") val childName: String,
    @SerialName("reward_id") val rewardId: String,
    @SerialName("reward_name") val rewardName: String,
    @SerialName("reward_icon") val rewardIcon: String,
    val cost: Int,
    @SerialName("requested_at") val requestedAt: String,
)
@Serializable data class MeRewardOut(
    @SerialName("reward_id") val rewardId: String,
    val name: String, val icon: String, val cost: Int, val mode: String,
    val affordable: Boolean, val pending: Boolean,
)
@Serializable data class MeRewardsOut(
    @SerialName("child_id") val childId: String,
    @SerialName("spendable_balance") val spendableBalance: Int,
    val rewards: List<MeRewardOut>,
)

// ---- child-facing (INV-8: NO stage / level / readiness field) --------
@Serializable data class ComplexityProfileDto(
    val band: String,
    @SerialName("text_style") val textStyle: String,
    @SerialName("audio_narration") val audioNarration: String,
    val iconography: String,
    @SerialName("quests_shown_at_once") val questsShownAtOnce: Int,
    val interaction: String,
    @SerialName("task_complexity") val taskComplexity: String,
    @SerialName("reading_requirement") val readingRequirement: String,
    @SerialName("reward_presentation") val rewardPresentation: String,
)
@Serializable data class TodayItemDto(
    @SerialName("quest_id") val questId: String,
    val title: String,
    val icon: String,
    val state: String,                       // available | pending | verified
    @SerialName("waits_for_grownup") val waitsForGrownup: Boolean,
)
@Serializable data class TodayDto(
    @SerialName("child_id") val childId: String,
    @SerialName("on_date") val onDate: String,
    val items: List<TodayItemDto>,
    @SerialName("lifetime_achievement") val lifetimeAchievement: Int,
    @SerialName("spendable_balance") val spendableBalance: Int,
    @SerialName("complexity_profile") val complexityProfile: ComplexityProfileDto,
)
@Serializable data class CompletionOut(
    @SerialName("quest_id") val questId: String,
    val state: String,                       // child-visible subset only
)
@Serializable data class CelebrationDto(
    @SerialName("quest_id") val questId: String,
    @SerialName("on_date") val onDate: String,
    @SerialName("points_awarded") val pointsAwarded: Int,
    val at: String,
)
@Serializable data class NotificationDto(
    @SerialName("child_id") val childId: String,
    val kind: String, val text: String, val at: String,
)
@Serializable data class NotificationsPrefBody(val enabled: Boolean)
@Serializable data class ProgressDto(
    @SerialName("child_id") val childId: String,
    @SerialName("lifetime_achievement") val lifetimeAchievement: Int,
    @SerialName("spendable_balance") val spendableBalance: Int,
    @SerialName("week_active_days") val weekActiveDays: Int,
)

@Serializable data class OkResp(val ok: Boolean = true)
@Serializable data class HealthResp(val status: String, val api: String)
