package hq.playfoundry.questgrow.data

import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.core.map
import hq.playfoundry.questgrow.data.model.AdvancementSuggestion
import hq.playfoundry.questgrow.data.model.Approval
import hq.playfoundry.questgrow.data.model.ChildProfile
import hq.playfoundry.questgrow.data.model.Dashboard
import hq.playfoundry.questgrow.data.model.OwnershipStage
import hq.playfoundry.questgrow.data.model.ParentNotification
import hq.playfoundry.questgrow.data.model.ParentQuest
import hq.playfoundry.questgrow.data.model.ParentReward
import hq.playfoundry.questgrow.data.model.TransitionPlan
import hq.playfoundry.questgrow.data.net.AdjustmentBody
import hq.playfoundry.questgrow.data.net.AssignBody
import hq.playfoundry.questgrow.data.net.ChildBody
import hq.playfoundry.questgrow.data.net.ChildProfileBody
import hq.playfoundry.questgrow.data.net.DayBody
import hq.playfoundry.questgrow.data.net.NotYetBody
import hq.playfoundry.questgrow.data.net.NotificationsPrefBody
import hq.playfoundry.questgrow.data.net.OwnershipBody
import hq.playfoundry.questgrow.data.net.QuestBody
import hq.playfoundry.questgrow.data.net.QuestEditBody
import hq.playfoundry.questgrow.data.net.QuestGrowApi
import hq.playfoundry.questgrow.data.net.RewardBody
import hq.playfoundry.questgrow.data.net.ScheduleBody
import hq.playfoundry.questgrow.data.net.apiCall

/** The parent surface. Every call is ParentScope; the API + server enforce it. */
class ParentRepository(private val api: QuestGrowApi) {

    // ---- family ----
    suspend fun children(): ApiResult<List<ChildProfile>> =
        apiCall { api.listChildren() }.map { list ->
            list.map { ChildProfile(it.childId, it.name, it.ageBand, it.avatar, it.birthdate) }
        }

    suspend fun child(id: String): ApiResult<ChildProfile> =
        apiCall { api.getChild(id) }.map { ChildProfile(it.childId, it.name, it.ageBand, it.avatar, it.birthdate) }

    suspend fun addChild(childId: String, name: String, ageBand: String): ApiResult<ChildProfile> =
        apiCall { api.addChild(ChildBody(childId, name, ageBand)) }
            .map { ChildProfile(it.childId, it.name, it.ageBand, it.avatar, it.birthdate) }

    suspend fun editChild(
        id: String, name: String? = null, ageBand: String? = null, birthdate: String? = null,
        overrides: Map<String, String>? = null,
    ): ApiResult<ChildProfile> =
        apiCall { api.editChild(id, ChildProfileBody(name = name, ageBand = ageBand, birthdate = birthdate, adaptationOverrides = overrides)) }
            .map { ChildProfile(it.childId, it.name, it.ageBand, it.avatar, it.birthdate) }

    // ---- quests ----
    private fun q(it: hq.playfoundry.questgrow.data.net.QuestOut) =
        ParentQuest(it.questId, it.version, it.title, it.icon, it.points, it.active, it.archived)

    suspend fun quests(): ApiResult<List<ParentQuest>> = apiCall { api.listQuests() }.map { it.map(::q) }

    suspend fun createQuest(questId: String, title: String, icon: String, points: Int, recurrence: String): ApiResult<ParentQuest> {
        val created = apiCall { api.createQuest(QuestBody(questId, title, icon, points)) }
        if (created is ApiResult.Ok) apiCall { api.setSchedule(questId, ScheduleBody(recurrence)) }
        return created.map(::q)
    }

    suspend fun editQuest(questId: String, title: String? = null, points: Int? = null, archived: Boolean? = null): ApiResult<ParentQuest> =
        apiCall { api.editQuest(questId, QuestEditBody(title = title, points = points, archived = archived, active = archived?.let { !it })) }.map(::q)

    suspend fun assign(childId: String, questId: String): ApiResult<Unit> =
        apiCall { api.assignQuest(childId, AssignBody(questId)) }.map { }

    suspend fun assignedQuests(childId: String): ApiResult<List<hq.playfoundry.questgrow.data.model.AssignedRoutine>> =
        apiCall { api.assignedQuests(childId) }.map { list ->
            list.map {
                hq.playfoundry.questgrow.data.model.AssignedRoutine(
                    it.questId, it.title, it.icon, it.points,
                    hq.playfoundry.questgrow.data.model.OwnershipStage.of(it.ownershipStage),
                )
            }
        }

    suspend fun unassign(childId: String, questId: String): ApiResult<Unit> =
        apiCall { api.unassignQuest(childId, questId) }.map { }

    // ---- rewards ----
    private fun rw(it: hq.playfoundry.questgrow.data.net.RewardOut) =
        ParentReward(it.rewardId, it.name, it.icon, it.cost, it.mode, it.active)

    suspend fun rewards(): ApiResult<List<ParentReward>> = apiCall { api.listRewards() }.map { it.map(::rw) }

    suspend fun createReward(rewardId: String, name: String, icon: String, cost: Int, mode: String): ApiResult<ParentReward> =
        apiCall { api.createReward(RewardBody(rewardId, name, icon, cost, mode)) }.map(::rw)

    // ---- reward redemptions (the "child asked to spend" inbox) ----
    suspend fun pendingRedemptions(): ApiResult<List<hq.playfoundry.questgrow.data.model.PendingRedemption>> =
        apiCall { api.redemptions() }.map { list ->
            list.map {
                hq.playfoundry.questgrow.data.model.PendingRedemption(
                    it.id, it.childId, it.childName, it.rewardId, it.rewardName, it.rewardIcon, it.cost,
                )
            }
        }

    suspend fun grantRedemption(id: String): ApiResult<Unit> =
        apiCall { api.grantRedemption(id) }.map { }

    suspend fun declineRedemption(id: String): ApiResult<Unit> =
        apiCall { api.declineRedemption(id) }.map { }

    // ---- ownership ----
    suspend fun setOwnership(childId: String, questId: String, target: OwnershipStage): ApiResult<TransitionPlan> =
        apiCall { api.setOwnership(childId, questId, OwnershipBody(target.wire)) }
            .map { TransitionPlan(it.direction, it.bypassed) }

    suspend fun suggestions(childId: String): ApiResult<List<AdvancementSuggestion>> =
        apiCall { api.suggestions(childId) }.map { it.map { s -> AdvancementSuggestion(s.questId, s.fromStage, s.toStage) } }

    suspend fun acceptSuggestion(childId: String, questId: String): ApiResult<TransitionPlan> =
        apiCall { api.acceptSuggestion(childId, questId) }.map { TransitionPlan(it.direction, it.bypassed) }

    suspend fun dismissSuggestion(childId: String, questId: String): ApiResult<Unit> =
        apiCall { api.dismissSuggestion(childId, questId) }.map { }

    // ---- verification / progress ----
    suspend fun approvals(childId: String): ApiResult<List<Approval>> =
        apiCall { api.approvals(childId) }.map { it.map { a -> Approval(a.questId, a.onDate) } }

    suspend fun approve(childId: String, questId: String, day: String): ApiResult<Unit> =
        apiCall { api.approve(childId, questId, DayBody(day)) }.map { }

    suspend fun notYet(childId: String, questId: String, day: String, note: String = ""): ApiResult<Unit> =
        apiCall { api.notYet(childId, questId, NotYetBody(day, note)) }.map { }

    /** Batch "approve all" — sequential; the API has no bulk endpoint. Stops on the first hard error. */
    suspend fun approveAll(childId: String, items: List<Approval>): ApiResult<Int> {
        var n = 0
        for (a in items) {
            when (val r = approve(childId, a.questId, a.onDate)) {
                is ApiResult.Ok -> n++
                is ApiResult.Failure -> return r
                is ApiResult.Offline -> return r
            }
        }
        return ApiResult.Ok(n)
    }

    suspend fun dashboard(childId: String, day: String, weekStart: String): ApiResult<Dashboard> =
        apiCall { api.dashboard(childId, day, weekStart) }.map {
            Dashboard(it.childId, it.onDate, it.total, it.verified, it.pending, it.available, it.expired, it.weekActiveDays)
        }

    suspend fun adjustment(childId: String, amount: Int, reason: String): ApiResult<Unit> =
        apiCall { api.adjustment(childId, AdjustmentBody(amount, reason)) }.map { }

    suspend fun materialise(day: String): ApiResult<Int> =
        apiCall { api.materialise(DayBody(day)) }.map { it["created"] ?: 0 }

    // ---- settings ----
    suspend fun setNotifications(enabled: Boolean): ApiResult<Boolean> =
        apiCall { api.setNotifications(NotificationsPrefBody(enabled)) }.map { it["notifications_enabled"] ?: enabled }

    suspend fun notifications(childId: String): ApiResult<List<ParentNotification>> =
        apiCall { api.notifications(childId) }.map { it.map { n -> ParentNotification(n.childId, n.kind, n.text, n.at) } }
}
