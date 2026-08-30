package hq.playfoundry.questgrow.data

import hq.playfoundry.questgrow.adapt.ComplexityProfile
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.local.OfflineQueue
import hq.playfoundry.questgrow.data.local.PendingCompletion
import hq.playfoundry.questgrow.data.model.Celebration
import hq.playfoundry.questgrow.data.model.ChildProgress
import hq.playfoundry.questgrow.data.model.CompletionOutcome
import hq.playfoundry.questgrow.data.model.QuestVisualState
import hq.playfoundry.questgrow.data.model.TodayQuest
import hq.playfoundry.questgrow.data.model.TodayView
import hq.playfoundry.questgrow.data.net.NotYetBody
import hq.playfoundry.questgrow.data.net.QuestGrowApi
import hq.playfoundry.questgrow.data.net.apiCall

/**
 * The child surface. Offline-first for the one action a child performs — "I
 * did it". The server stays authoritative for verification, celebration and
 * points (grant §4, §9); this repository never decides an outcome locally.
 */
class ChildRepository(
    private val api: QuestGrowApi,
    private val queue: OfflineQueue,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun today(day: String): ApiResult<TodayView> =
        apiCall { api.today(day) }.let { r ->
            when (r) {
                is ApiResult.Ok -> {
                    val d = r.value
                    val queuedIds = queue.all().filter { it.day == day }.map { it.questId }.toSet()
                    ApiResult.Ok(
                        TodayView(
                            childId = d.childId,
                            onDate = d.onDate,
                            lifetimeAchievement = d.lifetimeAchievement,
                            spendableBalance = d.spendableBalance,
                            profile = ComplexityProfile.from(d.complexityProfile),
                            quests = d.items.map { item ->
                                val state = when {
                                    item.questId in queuedIds && item.state == "available" ->
                                        QuestVisualState.QUEUED_OFFLINE
                                    item.state == "verified" -> QuestVisualState.VERIFIED
                                    item.state == "pending" -> QuestVisualState.PENDING_GROWNUP
                                    else -> QuestVisualState.AVAILABLE
                                }
                                TodayQuest(item.questId, item.title, item.icon, state, item.waitsForGrownup)
                            },
                        ),
                    )
                }
                is ApiResult.Failure -> r
                is ApiResult.Offline -> r
            }
        }

    /**
     * Mark a quest done. Online → the server decides (verified / pending).
     * Offline or 5xx → queue the intent and report [CompletionOutcome.QueuedOffline].
     * A `409` means the server already has this completion → treat as resolved,
     * drop any queued copy, do **not** surface an error (INV-11).
     */
    suspend fun complete(questId: String, day: String, note: String = ""): CompletionOutcome {
        return when (val r = apiCall { api.complete(questId, NotYetBody(day, note)) }) {
            is ApiResult.Ok -> {
                queue.remove(PendingCompletion(questId, day))
                if (r.value.state == "verified") CompletionOutcome.Verified
                else CompletionOutcome.WaitingForGrownup
            }
            is ApiResult.Offline -> {
                queue.enqueue(PendingCompletion(questId, day, note, now()))
                CompletionOutcome.QueuedOffline
            }
            is ApiResult.Failure -> when {
                r.isConflict -> {
                    queue.remove(PendingCompletion(questId, day))
                    // already processed server-side — not a failure
                    CompletionOutcome.WaitingForGrownup
                }
                r.status >= 500 -> {
                    queue.enqueue(PendingCompletion(questId, day, note, now()))
                    CompletionOutcome.QueuedOffline
                }
                else -> CompletionOutcome.Rejected(r.code, r.detail)
            }
        }
    }

    /**
     * Flush every queued intent. Idempotent — a `409` drops the entry;
     * anything still unreachable stays queued. Returns how many were cleared.
     */
    suspend fun flushQueue(): Int {
        var cleared = 0
        for (item in queue.all()) {
            when (val r = apiCall { api.complete(item.questId, NotYetBody(item.day, item.note)) }) {
                is ApiResult.Ok -> { queue.remove(item); cleared++ }
                is ApiResult.Failure -> if (r.isConflict || (r.status in 400..499 && !r.isAuthExpired)) {
                    queue.remove(item); cleared++   // resolved or permanently rejected
                }
                is ApiResult.Offline -> return cleared  // stop; try again on next reconnect
            }
        }
        return cleared
    }

    fun pendingCount(): Int = queue.size()

    suspend fun celebrations(since: String?): ApiResult<List<Celebration>> =
        apiCall { api.celebrations(since?.takeIf { it.isNotBlank() }) }.let { r ->
            r.let {
                when (it) {
                    is ApiResult.Ok -> ApiResult.Ok(it.value.map { c ->
                        Celebration(c.questId, c.pointsAwarded, c.at)
                    })
                    is ApiResult.Failure -> it
                    is ApiResult.Offline -> it
                }
            }
        }

    suspend fun progress(weekStart: String): ApiResult<ChildProgress> =
        apiCall { api.progress(weekStart) }.let { r ->
            when (r) {
                is ApiResult.Ok -> ApiResult.Ok(
                    ChildProgress(r.value.lifetimeAchievement, r.value.spendableBalance, r.value.weekActiveDays),
                )
                is ApiResult.Failure -> r
                is ApiResult.Offline -> r
            }
        }

    suspend fun redeem(rewardId: String): ApiResult<String> =
        apiCall { api.redeem(rewardId) }.let { r ->
            when (r) {
                is ApiResult.Ok -> ApiResult.Ok(r.value.state)
                is ApiResult.Failure -> r
                is ApiResult.Offline -> r
            }
        }
}
