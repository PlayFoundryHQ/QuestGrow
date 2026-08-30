package hq.playfoundry.questgrow.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.model.AdvancementSuggestion
import hq.playfoundry.questgrow.data.model.Approval
import hq.playfoundry.questgrow.data.model.ChildProfile
import hq.playfoundry.questgrow.data.model.Dashboard
import hq.playfoundry.questgrow.data.model.OwnershipStage
import hq.playfoundry.questgrow.data.model.ParentQuest
import hq.playfoundry.questgrow.data.model.ParentReward
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ParentState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val children: List<ChildProfile> = emptyList(),
    val quests: List<ParentQuest> = emptyList(),
    val rewards: List<ParentReward> = emptyList(),
    val approvals: List<Approval> = emptyList(),
    val suggestions: List<AdvancementSuggestion> = emptyList(),
    val dashboards: Map<String, Dashboard> = emptyMap(),
    val lastChildCode: String? = null,
)

class ParentViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ParentState())
    val state: StateFlow<ParentState> = _state
    private val repo get() = container.parentRepo

    private fun today() = LocalDate.now().toString()
    private fun monday() =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    private fun set(f: (ParentState) -> ParentState) { _state.value = f(_state.value) }
    private fun msg(m: String?) = set { it.copy(message = m) }

    private fun <T> handle(r: ApiResult<T>, ok: (T) -> Unit) = when (r) {
        is ApiResult.Ok -> ok(r.value)
        is ApiResult.Offline -> msg("You're offline.")
        is ApiResult.Failure -> {
            if (r.isAuthExpired) set { it.copy(signedIn = false, message = "Session expired — sign in again.") }
            else msg(r.detail.ifBlank { r.code })
        }
    }

    fun signIn(email: String, password: String, pin: String) {
        set { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            container.useParentScope()
            when (val r = container.authRepo.signInAsParent(email, password, pin)) {
                is ApiResult.Ok -> { set { it.copy(signedIn = true, busy = false) }; refreshFamily() }
                is ApiResult.Failure -> set { it.copy(busy = false, message = if (r.status == 403) "Wrong email, password or PIN — or too many attempts." else r.detail) }
                is ApiResult.Offline -> set { it.copy(busy = false, message = "You're offline.") }
            }
        }
    }

    fun signUp(email: String, password: String, pin: String) {
        viewModelScope.launch {
            handle(container.authRepo.signUp(email, password, pin)) { msg("Account created — now sign in.") }
        }
    }

    fun signOut() {
        viewModelScope.launch { container.authRepo.signOutParent(); set { ParentState() } }
    }

    fun forgetDevice() {
        viewModelScope.launch { container.authRepo.forgetEverything(); set { ParentState() } }
    }

    fun refreshFamily() {
        viewModelScope.launch {
            handle(repo.children()) { kids ->
                set { it.copy(children = kids) }
                kids.forEach { k -> loadDashboard(k.childId) }
            }
        }
    }

    fun loadDashboard(childId: String) {
        viewModelScope.launch {
            (repo.dashboard(childId, today(), monday()) as? ApiResult.Ok)?.let { d ->
                set { it.copy(dashboards = it.dashboards + (childId to d.value)) }
            }
        }
    }

    fun addChild(childId: String, name: String, ageBand: String) {
        viewModelScope.launch { handle(repo.addChild(childId, name, ageBand)) { refreshFamily() } }
    }

    fun editChild(id: String, name: String?, ageBand: String?, birthdate: String?, overrides: Map<String, String>?) {
        viewModelScope.launch {
            handle(repo.editChild(id, name, ageBand, birthdate, overrides)) { msg("Saved."); refreshFamily() }
        }
    }

    fun issueChildCode(childId: String) {
        viewModelScope.launch {
            handle(container.authRepo.issueChildToken(childId)) { code -> set { it.copy(lastChildCode = code) } }
        }
    }

    fun loadQuests() = viewModelScope.launch { handle(repo.quests()) { qs -> set { it.copy(quests = qs) } } }
    fun createQuest(id: String, title: String, icon: String, points: Int, recurrence: String) =
        viewModelScope.launch { handle(repo.createQuest(id, title, icon, points, recurrence)) { msg("Created."); loadQuests() } }
    fun editQuest(id: String, title: String?, points: Int?, archived: Boolean?) =
        viewModelScope.launch { handle(repo.editQuest(id, title, points, archived)) { msg("Saved."); loadQuests() } }
    fun seedStarters() = viewModelScope.launch { handle(repo.seedStarters()) { msg("Templates added."); loadQuests() } }
    fun assign(childId: String, questId: String) =
        viewModelScope.launch { handle(repo.assign(childId, questId)) { msg("Assigned.") } }

    fun loadRewards() = viewModelScope.launch { handle(repo.rewards()) { rs -> set { it.copy(rewards = rs) } } }
    fun createReward(id: String, name: String, icon: String, cost: Int, mode: String) =
        viewModelScope.launch { handle(repo.createReward(id, name, icon, cost, mode)) { msg("Created."); loadRewards() } }

    fun loadApprovals(childId: String) =
        viewModelScope.launch { handle(repo.approvals(childId)) { a -> set { it.copy(approvals = a) } } }
    fun approve(childId: String, a: Approval) =
        viewModelScope.launch { handle(repo.approve(childId, a.questId, a.onDate)) { loadApprovals(childId); loadDashboard(childId) } }
    fun notYet(childId: String, a: Approval) =
        viewModelScope.launch { handle(repo.notYet(childId, a.questId, a.onDate)) { loadApprovals(childId) } }
    fun approveAll(childId: String) = viewModelScope.launch {
        val items = _state.value.approvals
        handle(repo.approveAll(childId, items)) { msg("Approved $it."); loadApprovals(childId); loadDashboard(childId) }
    }

    fun loadSuggestions(childId: String) =
        viewModelScope.launch { handle(repo.suggestions(childId)) { s -> set { it.copy(suggestions = s) } } }
    fun acceptSuggestion(childId: String, questId: String) =
        viewModelScope.launch { handle(repo.acceptSuggestion(childId, questId)) { loadSuggestions(childId) } }
    fun dismissSuggestion(childId: String, questId: String) =
        viewModelScope.launch { handle(repo.dismissSuggestion(childId, questId)) { loadSuggestions(childId) } }
    fun setOwnership(childId: String, questId: String, stage: OwnershipStage) = viewModelScope.launch {
        handle(repo.setOwnership(childId, questId, stage)) { plan ->
            msg(if (plan.direction == "regress") "Moved to more support — a normal adjustment." else "Updated.")
        }
    }

    fun materialiseToday(childId: String) =
        viewModelScope.launch { handle(repo.materialise(today())) { loadDashboard(childId) } }

    fun setNotifications(enabled: Boolean) =
        viewModelScope.launch { handle(repo.setNotifications(enabled)) { msg(if (it) "Notifications on." else "Notifications off.") } }

    fun clearMessage() = msg(null)
}
