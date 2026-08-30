package hq.playfoundry.questgrow.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.R
import hq.playfoundry.questgrow.core.Loadable
import hq.playfoundry.questgrow.data.model.Approval
import hq.playfoundry.questgrow.data.model.OwnershipStage
import hq.playfoundry.questgrow.restartApp
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.Field
import hq.playfoundry.questgrow.ui.SecondaryButton
import hq.playfoundry.questgrow.ui.STARTERS
import hq.playfoundry.questgrow.ui.collectAsStateSafe
import hq.playfoundry.questgrow.ui.fa
import hq.playfoundry.questgrow.ui.faDigits
import hq.playfoundry.questgrow.ui.faFraction

private enum class Section { Home, Routines, Rewards, Ownership, Children, Settings }

@Composable
fun ParentFlow(container: AppContainer, onExit: () -> Unit) {
    val vm: ParentViewModel = viewModel { ParentViewModel(container) }
    val s by vm.state.collectAsStateSafe()
    var section by remember { mutableStateOf(Section.Home) }
    var selectedChild by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshFamily(); vm.loadQuests() }
    LaunchedEffect(s.children) {
        if (selectedChild == null) selectedChild = s.children.firstOrNull()?.childId
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (section != Section.Home) {
                TextButton(onClick = { section = Section.Home }) { Text("‹ " + stringResource(R.string.parent_home)) }
            }
            Text(
                title(section), Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onExit) { Text(stringResource(R.string.back)) }
        }
        s.message?.let {
            Text(
                it, Modifier.fillMaxWidth().padding(12.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            LaunchedEffect(it) { kotlinx.coroutines.delay(2500); vm.clearMessage() }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (section) {
                Section.Home -> Home(vm, s, selectedChild, { selectedChild = it }) { section = it }
                Section.Routines -> Routines(vm, s, selectedChild)
                Section.Rewards -> Rewards(vm, s)
                Section.Ownership -> Ownership(vm, s, selectedChild)
                Section.Children -> Children(vm, s)
                Section.Settings -> Settings(vm, container)
            }
        }
    }
}

@Composable
private fun title(sec: Section) = when (sec) {
    Section.Home -> stringResource(R.string.parent_home)
    Section.Routines -> stringResource(R.string.nav_routines)
    Section.Rewards -> stringResource(R.string.nav_rewards)
    Section.Ownership -> stringResource(R.string.nav_ownership)
    Section.Children -> stringResource(R.string.nav_children)
    Section.Settings -> stringResource(R.string.nav_settings)
}

@Composable
private fun Home(
    vm: ParentViewModel,
    s: ParentState,
    selected: String?,
    onSelectChild: (String) -> Unit,
    onGo: (Section) -> Unit,
) {
    LaunchedEffect(selected) { selected?.let { vm.loadApprovals(it) } }
    val questTitles = (s.quests.valueOrNull ?: emptyList()).associate { it.questId to it.title }

    // per-child glance
    when (val fam = s.family) {
        is Loadable.Loading, Loadable.Idle -> Text("…")
        is Loadable.Failed -> Text(fam.message, color = MaterialTheme.colorScheme.error)
        is Loadable.Loaded -> fam.value.forEach { row ->
            Card(
                Modifier.fillMaxWidth().selectable(selected == row.child.childId) { onSelectChild(row.child.childId) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == row.child.childId) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.child.name, style = MaterialTheme.typography.titleMedium)
                    val d = row.summary
                    Text(
                        stringResource(R.string.parent_done_line, faFraction(d?.verified ?: 0, d?.total ?: 0)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if ((d?.pending ?: 0) > 0) Text(
                        stringResource(R.string.parent_pending_line, (d?.pending ?: 0).fa()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    // approvals inbox
    Text(stringResource(R.string.parent_approvals), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
    val cid = selected
    when (val ap = s.approvals) {
        is Loadable.Idle, Loadable.Loading -> Text("…")
        is Loadable.Failed -> Column {
            Text(ap.message, color = MaterialTheme.colorScheme.error)
            SecondaryButton(stringResource(R.string.retry)) { cid?.let { vm.loadApprovals(it) } }
        }
        is Loadable.Loaded -> if (ap.value.isEmpty()) {
            Text(stringResource(R.string.parent_approvals_empty), style = MaterialTheme.typography.bodyLarge)
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ap.value.size > 1 && cid != null) {
                BigButton(stringResource(R.string.parent_approve_all, ap.value.size.fa())) { vm.approveAll(cid) }
            }
            ap.value.forEach { a ->
                ApprovalCard(a, questTitles[a.questId] ?: a.questId) { yes ->
                    cid?.let { if (yes) vm.approve(it, a) else vm.notYet(it, a) }
                }
            }
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SecondaryButton(stringResource(R.string.nav_routines), Modifier.weight(1f)) { onGo(Section.Routines) }
        SecondaryButton(stringResource(R.string.nav_rewards), Modifier.weight(1f)) { onGo(Section.Rewards) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SecondaryButton(stringResource(R.string.nav_ownership), Modifier.weight(1f)) { onGo(Section.Ownership) }
        SecondaryButton(stringResource(R.string.nav_children), Modifier.weight(1f)) { onGo(Section.Children) }
    }
    SecondaryButton(stringResource(R.string.nav_settings), Modifier.fillMaxWidth()) { onGo(Section.Settings) }
}

@Composable
private fun ApprovalCard(a: Approval, title: String, onDecide: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(a.onDate.faDigits(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(stringResource(R.string.parent_approve), Modifier.weight(1f)) { onDecide(true) }
                SecondaryButton(stringResource(R.string.parent_not_yet), Modifier.weight(1f), minHeight = 56.dp) { onDecide(false) }
            }
            Text(stringResource(R.string.parent_not_yet_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Routines(vm: ParentViewModel, s: ParentState, childId: String?) {
    Text(stringResource(R.string.routine_starters), style = MaterialTheme.typography.titleMedium)
    val existing = (s.quests.valueOrNull ?: emptyList()).map { it.questId }.toSet()
    STARTERS.forEach { st ->
        val added = st.id in existing
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${st.icon}  ${st.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (added) {
                childId?.let {
                    SecondaryButton(stringResource(R.string.routine_assign)) { vm.assign(it, st.id) }
                }
            } else {
                SecondaryButton(stringResource(R.string.routine_create)) {
                    vm.createQuest(st.id, st.title, st.icon, st.points, "daily")
                    childId?.let { vm.assign(it, st.id) }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(stringResource(R.string.nav_routines), style = MaterialTheme.typography.titleMedium)
    when (val q = s.quests) {
        is Loadable.Loaded -> if (q.value.isEmpty()) Text(stringResource(R.string.routines_empty))
        else q.value.forEach { Text("${it.icon}  ${it.title}  (${it.points.fa()})", Modifier.padding(vertical = 2.dp)) }
        is Loadable.Failed -> Text(q.message, color = MaterialTheme.colorScheme.error)
        else -> Text("…")
    }
}

@Composable
private fun Rewards(vm: ParentViewModel, s: ParentState) {
    LaunchedEffect(Unit) { if (s.rewards is Loadable.Idle) vm.loadRewards() }
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableIntStateOf(20) }
    var byParent by remember { mutableStateOf(true) }
    Text(stringResource(R.string.reward_add), style = MaterialTheme.typography.titleMedium)
    Field(stringResource(R.string.reward_name), name, { name = it })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reward_cost) + ": " + cost.fa(), Modifier.weight(1f))
        SecondaryButton("−") { if (cost > 5) cost -= 5 }
        SecondaryButton("+") { cost += 5 }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(if (byParent) R.string.reward_redeem_parent else R.string.reward_redeem_self), Modifier.weight(1f))
        Switch(checked = byParent, onCheckedChange = { byParent = it })
    }
    BigButton(stringResource(R.string.reward_create), enabled = name.isNotBlank()) {
        vm.createReward("r" + System.currentTimeMillis(), name.trim(), "⭐", cost, if (byParent) "parent_confirmed" else "self_service")
        name = ""
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    when (val r = s.rewards) {
        is Loadable.Loaded -> if (r.value.isEmpty()) Text(stringResource(R.string.rewards_empty))
        else r.value.forEach { Text("${it.icon}  ${it.name} — ${it.cost.fa()}", Modifier.padding(vertical = 2.dp)) }
        is Loadable.Failed -> Text(r.message, color = MaterialTheme.colorScheme.error)
        else -> Text("…")
    }
}

@Composable
private fun Ownership(vm: ParentViewModel, s: ParentState, childId: String?) {
    LaunchedEffect(childId) { childId?.let { vm.loadSuggestions(it) } }
    var quest by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(OwnershipStage.PARENT_GUIDED) }
    val labels = listOf(
        OwnershipStage.PARENT_MANAGED to R.string.ownership_most,
        OwnershipStage.PARENT_GUIDED to R.string.ownership_guided,
        OwnershipStage.CHILD_PARTICIPATED to R.string.ownership_leads,
        OwnershipStage.CHILD_OWNED to R.string.ownership_owns,
    )
    Text(stringResource(R.string.ownership_support_title), style = MaterialTheme.typography.titleMedium)
    val quests = s.quests.valueOrNull ?: emptyList()
    quests.forEach { q ->
        Row(Modifier.fillMaxWidth().selectable(quest == q.questId) { quest = q.questId }.padding(vertical = 4.dp)) {
            Text((if (quest == q.questId) "● " else "○ ") + "${q.icon}  ${q.title}")
        }
    }
    labels.forEach { (st, res) ->
        Row(Modifier.fillMaxWidth().selectable(stage == st) { stage = st }.padding(vertical = 4.dp)) {
            Text((if (stage == st) "● " else "○ ") + stringResource(res))
        }
    }
    BigButton(stringResource(R.string.ownership_update), enabled = quest.isNotBlank() && childId != null) {
        childId?.let { vm.setOwnership(it, quest, stage) }
    }
    Text(stringResource(R.string.ownership_note), style = MaterialTheme.typography.bodyMedium)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(stringResource(R.string.ownership_suggestions), style = MaterialTheme.typography.titleMedium)
    when (val sug = s.suggestions) {
        is Loadable.Loaded -> sug.value.forEach { g ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(quests.firstOrNull { it.questId == g.questId }?.title ?: g.questId)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(stringResource(R.string.ownership_accept), Modifier.weight(1f)) { childId?.let { vm.acceptSuggestion(it, g.questId) } }
                        SecondaryButton(stringResource(R.string.ownership_dismiss), Modifier.weight(1f)) { childId?.let { vm.dismissSuggestion(it, g.questId) } }
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun Children(vm: ParentViewModel, s: ParentState) {
    var name by remember { mutableStateOf("") }
    var band by remember { mutableStateOf("5-6") }
    s.children.forEach { Text("• ${it.name}  (${it.ageBand})", Modifier.padding(vertical = 2.dp)) }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Field(stringResource(R.string.onb_child_name), name, { name = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("3-4", "5-6", "7-8").forEach { b ->
            SecondaryButton((if (band == b) "● " else "") + b) { band = b }
        }
    }
    BigButton(stringResource(R.string.onb_next), enabled = name.isNotBlank()) {
        vm.addChild("c" + System.currentTimeMillis(), name.trim(), band); name = ""
    }
}

@Composable
private fun Settings(vm: ParentViewModel, container: AppContainer) {
    val s by vm.state.collectAsStateSafe()
    val context = LocalContext.current
    var notif by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(container.baseUrl) }
    val childId = s.children.firstOrNull()?.childId

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_notif_verified), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.settings_notifs_help), style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = notif, onCheckedChange = { notif = it; vm.setNotifications(it) })
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(stringResource(R.string.settings_child_code), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.settings_pair_help), style = MaterialTheme.typography.bodyMedium)
    BigButton(stringResource(R.string.settings_create_code), enabled = childId != null) { childId?.let { vm.issueChildCode(it) } }
    s.lastChildCode?.let {
        Text(
            it.faDigits(),
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall,
        )
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(stringResource(R.string.settings_backend), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.settings_backend_help), style = MaterialTheme.typography.bodyMedium)
    Field(stringResource(R.string.settings_backend_url), url, { url = it }, ltr = true)
    SecondaryButton(stringResource(R.string.settings_save_restart)) { container.setBaseUrl(url); context.restartApp() }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(stringResource(R.string.settings_signout), Modifier.weight(1f)) { vm.signOut() }
        SecondaryButton(stringResource(R.string.settings_forget), Modifier.weight(1f)) { vm.forgetDevice() }
    }
    Text("", Modifier.padding(bottom = 24.dp))
}
