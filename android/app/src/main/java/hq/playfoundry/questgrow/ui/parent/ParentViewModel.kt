package hq.playfoundry.questgrow.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.core.Loadable
import hq.playfoundry.questgrow.core.toLoadable
import hq.playfoundry.questgrow.data.model.AdvancementSuggestion
import hq.playfoundry.questgrow.data.model.Approval
import hq.playfoundry.questgrow.data.model.ChildProfile
import hq.playfoundry.questgrow.data.model.Dashboard
import hq.playfoundry.questgrow.data.model.OwnershipStage
import hq.playfoundry.questgrow.data.model.ParentQuest
import hq.playfoundry.questgrow.data.model.ParentReward
import hq.playfoundry.questgrow.data.model.PendingRedemption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One row of the dashboard: the child + its (independently loaded) summary. */
data class ChildDashboard(val child: ChildProfile, val summary: Dashboard?)

data class ParentState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    /** transient one-shot action feedback ("ذخیره شد.", "Created.") — auto-cleared. */
    val message: String? = null,
    // per-section load state (Phase J)
    val family: Loadable<List<ChildDashboard>> = Loadable.Idle,
    val quests: Loadable<List<ParentQuest>> = Loadable.Idle,
    val rewards: Loadable<List<ParentReward>> = Loadable.Idle,
    val approvals: Loadable<List<Approval>> = Loadable.Idle,
    val suggestions: Loadable<List<AdvancementSuggestion>> = Loadable.Idle,
    val redemptions: Loadable<List<PendingRedemption>> = Loadable.Idle,
    val lastChildCode: String? = null,
) {
    val children: List<ChildProfile> get() = family.valueOrNull?.map { it.child } ?: emptyList()
    val approvalItems: List<Approval> get() = approvals.valueOrNull ?: emptyList()
}

class ParentViewModel(private val container: AppContainer) : ViewModel() {
    // the parent gate (PIN pad) has already unlocked before this VM is shown
    private val _state = MutableStateFlow(
        ParentState(signedIn = container.tokenStore.parentTokenBlocking() != null),
    )
    val state: StateFlow<ParentState> = _state
    private val repo get() = container.parentRepo

    private fun today() = LocalDate.now().toString()
    private fun monday() =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    private fun set(f: (ParentState) -> ParentState) { _state.value = f(_state.value) }
    private fun msg(m: String?) = set { it.copy(message = m) }

    /** for one-shot *actions* (create / edit / approve …) — keeps the transient message model. */
    private fun <T> action(r: ApiResult<T>, ok: (T) -> Unit) = when (r) {
        is ApiResult.Ok -> ok(r.value)
        is ApiResult.Offline -> msg("آفلاین — ذخیره نشد.")
        is ApiResult.Failure ->
            if (r.isAuthExpired) set { it.copy(signedIn = false, message = "نشست تمام شد — دوباره وارد شوید.") }
            else msg(r.detail.ifBlank { r.code })
    }

    // ---- auth ----
    fun signIn(email: String, password: String, pin: String) {
        set { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            container.useParentScope()
            when (val r = container.authRepo.signInAsParent(email, password, pin)) {
                is ApiResult.Ok -> { set { it.copy(signedIn = true, busy = false) }; refreshFamily() }
                is ApiResult.Failure -> set {
                    it.copy(busy = false, message =
                        if (r.status == 403) "رمز اشتباه است — یا تلاش زیاد بوده."
                        else r.detail.ifBlank { r.code })
                }
                is ApiResult.Offline -> set { it.copy(busy = false, message = "آفلاین هستید.") }
            }
        }
    }

    fun signUp(email: String, password: String, pin: String) = viewModelScope.launch {
        action(container.authRepo.signUp(email, password, pin)) { msg("حساب ساخته شد.") }
    }

    fun signOut() = viewModelScope.launch { container.authRepo.signOutParent(); set { ParentState() } }
    fun forgetDevice() = viewModelScope.launch { container.authRepo.forgetEverything(); set { ParentState() } }

    // ---- family / dashboard ----
    fun refreshFamily() {
        set { it.copy(family = Loadable.Loading) }
        viewModelScope.launch {
            val r = repo.children()
            set { it.copy(family = r.toLoadable { kids -> kids.map { ChildDashboard(it, null) } }) }
            (r as? ApiResult.Ok)?.value?.forEach { loadDashboard(it.childId) }
            // this is the family device — keep the kid board's switcher in sync
            // with the account (every child, no per-device "activate" step).
            container.authRepo.syncFamilyChildren()
        }
    }

    fun loadDashboard(childId: String) = viewModelScope.launch {
        (repo.dashboard(childId, today(), monday()) as? ApiResult.Ok)?.let { d ->
            set { st ->
                val fam = st.family.valueOrNull ?: return@set st
                st.copy(family = Loadable.Loaded(fam.map {
                    if (it.child.childId == childId) it.copy(summary = d.value) else it
                }))
            }
        }
    }

    fun addChild(childId: String, name: String, ageBand: String) = viewModelScope.launch {
        action(repo.addChild(childId, name, ageBand)) { msg("کودک اضافه شد — روی تختهٔ کودک هم هست."); refreshFamily() }
    }

    fun editChild(id: String, name: String?, ageBand: String?, birthdate: String?, overrides: Map<String, String>?) =
        viewModelScope.launch {
            action(repo.editChild(id, name, ageBand, birthdate, overrides)) { msg("ذخیره شد."); refreshFamily() }
        }

    fun issueChildCode(childId: String) = viewModelScope.launch {
        action(container.authRepo.createPairingCode(childId)) { code -> set { it.copy(lastChildCode = code) } }
    }

    // ---- quests ----
    fun loadQuests() {
        set { it.copy(quests = Loadable.Loading) }
        viewModelScope.launch { val r = repo.quests(); set { it.copy(quests = r.toLoadable { q -> q }) } }
    }
    fun createQuest(id: String, title: String, icon: String, points: Int, recurrence: String) = viewModelScope.launch {
        action(repo.createQuest(id, title, icon, points, recurrence)) { msg("روتین ساخته شد."); loadQuests() }
    }
    fun editQuest(id: String, title: String?, points: Int?, archived: Boolean?) = viewModelScope.launch {
        action(repo.editQuest(id, title, points, archived)) { msg("ذخیره شد."); loadQuests() }
    }
    fun assign(childId: String, questId: String) = viewModelScope.launch {
        action(repo.assign(childId, questId)) { msg("اختصاص داده شد.") }
    }

    // ---- rewards ----
    fun loadRewards() {
        set { it.copy(rewards = Loadable.Loading) }
        viewModelScope.launch { val r = repo.rewards(); set { it.copy(rewards = r.toLoadable { rw -> rw }) } }
    }
    fun createReward(id: String, name: String, icon: String, cost: Int, mode: String) = viewModelScope.launch {
        action(repo.createReward(id, name, icon, cost, mode)) { msg("جایزه ساخته شد."); loadRewards() }
    }

    // ---- reward redemptions (the "child asked to spend" inbox) ----
    fun loadRedemptions() {
        set { it.copy(redemptions = Loadable.Loading) }
        viewModelScope.launch { val r = repo.pendingRedemptions(); set { it.copy(redemptions = r.toLoadable { x -> x }) } }
    }
    fun grantRedemption(id: String) = viewModelScope.launch {
        action(repo.grantRedemption(id)) {
            msg("جایزه داده شد 🎉"); loadRedemptions()
            _state.value.children.forEach { loadDashboard(it.childId) }
        }
    }
    fun declineRedemption(id: String) = viewModelScope.launch {
        action(repo.declineRedemption(id)) { msg("رد شد — بدون جریمه."); loadRedemptions() }
    }


    // ---- approvals ----
    fun loadApprovals(childId: String) {
        set { it.copy(approvals = Loadable.Loading) }
        viewModelScope.launch { val r = repo.approvals(childId); set { it.copy(approvals = r.toLoadable { a -> a }) } }
    }
    fun approve(childId: String, a: Approval) = viewModelScope.launch {
        action(repo.approve(childId, a.questId, a.onDate)) { loadApprovals(childId); loadDashboard(childId) }
    }
    fun notYet(childId: String, a: Approval) = viewModelScope.launch {
        action(repo.notYet(childId, a.questId, a.onDate)) { msg("برگشت داده شد — بدون جریمه."); loadApprovals(childId) }
    }
    fun approveAll(childId: String) = viewModelScope.launch {
        action(repo.approveAll(childId, _state.value.approvalItems)) {
            msg("$it تأیید شد."); loadApprovals(childId); loadDashboard(childId)
        }
    }

    // ---- ownership ----
    fun loadSuggestions(childId: String) {
        set { it.copy(suggestions = Loadable.Loading) }
        viewModelScope.launch { val r = repo.suggestions(childId); set { it.copy(suggestions = r.toLoadable { s -> s }) } }
    }
    fun acceptSuggestion(childId: String, questId: String) = viewModelScope.launch {
        action(repo.acceptSuggestion(childId, questId)) { loadSuggestions(childId) }
    }
    fun dismissSuggestion(childId: String, questId: String) = viewModelScope.launch {
        action(repo.dismissSuggestion(childId, questId)) { loadSuggestions(childId) }
    }
    fun setOwnership(childId: String, questId: String, stage: OwnershipStage) = viewModelScope.launch {
        action(repo.setOwnership(childId, questId, stage)) { plan ->
            msg(if (plan.direction == "regress") "به کمکِ بیشتر منتقل شد — یک تنظیم عادی." else "به‌روزرسانی شد.")
        }
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        action(repo.setNotifications(enabled)) { msg(if (it) "اعلان‌ها روشن شد." else "اعلان‌ها خاموش شد.") }
    }

    fun clearMessage() = msg(null)
}
