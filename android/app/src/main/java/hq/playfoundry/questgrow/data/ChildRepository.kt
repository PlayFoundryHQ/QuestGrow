package hq.playfoundry.questgrow.data

import hq.playfoundry.questgrow.adapt.ComplexityProfile
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.local.OfflineQueue
import hq.playfoundry.questgrow.data.local.PendingCompletion
import hq.playfoundry.questgrow.data.local.ReadCache
import hq.playfoundry.questgrow.data.net.TodayDto
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
    private val cache: ReadCache? = null,
    /** which child the board currently shows — cache + queue are scoped to it
     *  (DECISION-021). `null` on a single-child paired device. */
    private val activeChildId: () -> String? = { null },
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** A queue entry belongs to the active child, or is a legacy (id-less) entry. */
    private fun mine(entryChildId: String): Boolean =
        entryChildId.isBlank() || entryChildId == activeChildId()

    private fun mapToday(d: TodayDto, day: String, stale: Boolean): TodayView {
        val queuedIds = queue.all()
            .filter { it.day == day && (it.childId.isBlank() || it.childId == d.childId) }
            .map { it.questId }.toSet()
        return TodayView(
            childId = d.childId,
            onDate = d.onDate,
            lifetimeAchievement = d.lifetimeAchievement,
            spendableBalance = d.spendableBalance,
            profile = ComplexityProfile.from(d.complexityProfile),
            stale = stale,
            quests = d.items.map { item ->
                val state = when {
                    item.questId in queuedIds && item.state == "available" -> QuestVisualState.QUEUED_OFFLINE
                    item.state == "verified" -> QuestVisualState.VERIFIED
                    item.state == "pending" -> QuestVisualState.PENDING_GROWNUP
                    else -> QuestVisualState.AVAILABLE
                }
                TodayQuest(item.questId, item.title, item.icon, state, item.waitsForGrownup)
            },
        )
    }

    /**
     * Online → live board (and cached for later). Offline with a cached board
     * → the last-known board marked `stale` (grant §1). Offline with nothing
     * cached → [ApiResult.Offline].
     */
    suspend fun today(day: String): ApiResult<TodayView> =
        when (val r = apiCall { api.today(day) }) {
            is ApiResult.Ok -> {
                cache?.putToday(activeChildId(), r.value)
                ApiResult.Ok(mapToday(r.value, day, stale = false))
            }
            is ApiResult.Failure -> r
            is ApiResult.Offline -> cache?.getToday(activeChildId())
                ?.takeIf { activeChildId() == null || it.childId == activeChildId() }
                ?.let { ApiResult.Ok(mapToday(it, day, stale = true)) }
                ?: r
        }

    /**
     * Mark a quest done. Online → the server decides (verified / pending).
     * Offline or 5xx → queue the intent and report [CompletionOutcome.QueuedOffline].
     * A `409` means the server already has this completion → treat as resolved,
     * drop any queued copy, do **not** surface an error (INV-11).
     */
    suspend fun complete(questId: String, day: String, note: String = ""): CompletionOutcome {
        val cid = activeChildId() ?: ""
        return when (val r = apiCall { api.complete(questId, NotYetBody(day, note)) }) {
            is ApiResult.Ok -> {
                queue.remove(PendingCompletion(questId, day, childId = cid))
                if (r.value.state == "verified") CompletionOutcome.Verified
                else CompletionOutcome.WaitingForGrownup
            }
            is ApiResult.Offline -> {
                queue.enqueue(PendingCompletion(questId, day, note, now(), cid))
                CompletionOutcome.QueuedOffline
            }
            is ApiResult.Failure -> when {
                r.isConflict -> {
                    queue.remove(PendingCompletion(questId, day, childId = cid))
                    // already processed server-side — not a failure
                    CompletionOutcome.WaitingForGrownup
                }
                r.status >= 500 -> {
                    queue.enqueue(PendingCompletion(questId, day, note, now(), cid))
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
        // only the active child's entries — the attached bearer token is theirs;
        // another child's intents wait until that child is the active board.
        for (item in queue.all().filter { mine(it.childId) }) {
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

    /** Pending intents for the child the board currently shows. */
    fun pendingCount(): Int = queue.all().count { mine(it.childId) }

    /** The child left this device — drop their cached board + queued intents. */
    fun forgetChild(childId: String) {
        queue.removeAllFor(childId)
        cache?.forgetChild(childId)
    }

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
        when (val r = apiCall { api.progress(weekStart) }) {
            is ApiResult.Ok -> {
                cache?.putProgress(activeChildId(), r.value)
                ApiResult.Ok(ChildProgress(r.value.lifetimeAchievement, r.value.spendableBalance, r.value.weekActiveDays))
            }
            is ApiResult.Failure -> r
            is ApiResult.Offline -> cache?.getProgress(activeChildId())
                ?.takeIf { activeChildId() == null || it.childId == activeChildId() }
                ?.let { ApiResult.Ok(ChildProgress(it.lifetimeAchievement, it.spendableBalance, it.weekActiveDays, stale = true)) }
                ?: r
        }

    suspend fun rewards(): ApiResult<hq.playfoundry.questgrow.data.model.KidRewards> =
        when (val r = apiCall { api.meRewards() }) {
            is ApiResult.Ok -> ApiResult.Ok(
                hq.playfoundry.questgrow.data.model.KidRewards(
                    spendableBalance = r.value.spendableBalance,
                    rewards = r.value.rewards.map {
                        hq.playfoundry.questgrow.data.model.KidReward(
                            rewardId = it.rewardId, name = it.name, icon = it.icon, cost = it.cost,
                            needsGrownup = it.mode != "self_service",
                            affordable = it.affordable, pending = it.pending,
                        )
                    },
                ),
            )
            is ApiResult.Failure -> r
            is ApiResult.Offline -> r
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
