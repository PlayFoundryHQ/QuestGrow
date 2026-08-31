package hq.playfoundry.questgrow.ui.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.R
import hq.playfoundry.questgrow.core.Loadable
import hq.playfoundry.questgrow.data.model.Approval
import hq.playfoundry.questgrow.data.model.OwnershipStage
import hq.playfoundry.questgrow.restartApp
import hq.playfoundry.questgrow.ui.AppScaffold
import hq.playfoundry.questgrow.ui.Avatar
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.Field
import hq.playfoundry.questgrow.ui.GhostButton
import hq.playfoundry.questgrow.ui.HintText
import hq.playfoundry.questgrow.ui.SectionHeader
import hq.playfoundry.questgrow.ui.SecondaryButton
import hq.playfoundry.questgrow.ui.SelectPill
import hq.playfoundry.questgrow.ui.SelectRow
import hq.playfoundry.questgrow.ui.Space
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

    LaunchedEffect(Unit) { vm.refreshFamily(); vm.loadQuests(); vm.loadRedemptions() }
    LaunchedEffect(s.children) {
        if (selectedChild == null) selectedChild = s.children.firstOrNull()?.childId
    }

    AppScaffold(
        title = title(section),
        onBack = if (section == Section.Home) onExit else ({ section = Section.Home }),
    ) {
        s.message?.let { msg ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    msg, Modifier.fillMaxWidth().padding(Space.md),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2600); vm.clearMessage() }
        }
        when (section) {
            Section.Home -> Home(vm, s, selectedChild, { selectedChild = it }) { section = it }
            Section.Routines -> Routines(vm, s, selectedChild) { selectedChild = it }
            Section.Rewards -> Rewards(vm, s)
            Section.Ownership -> Ownership(vm, s, selectedChild)
            Section.Children -> Children(vm, s)
            Section.Settings -> Settings(vm, container)
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

// ------------------------------------------------------------------ home ---

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

    // per-child glance — a horizontal strip
    SectionHeader(stringResource(R.string.parent_family))
    when (val fam = s.family) {
        is Loadable.Loading, Loadable.Idle -> HintText("…")
        is Loadable.Failed -> Text(fam.message, color = MaterialTheme.colorScheme.error)
        is Loadable.Loaded -> Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            fam.value.forEach { row ->
                val on = selected == row.child.childId
                Card(
                    Modifier.width(150.dp).selectable(on) { onSelectChild(row.child.childId) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (on) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Avatar(row.child.name, selected = on, size = 36.dp)
                            Text(row.child.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
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
    }

    // reward-redemption inbox
    val reqs = s.redemptions.valueOrNull ?: emptyList()
    if (reqs.isNotEmpty()) {
        SectionHeader(stringResource(R.string.parent_redemptions), reqs.size)
        reqs.forEach { rq ->
            Card {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(
                        stringResource(R.string.parent_redemption_line, rq.childName, rq.rewardName, rq.cost.fa()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        BigButton(stringResource(R.string.parent_redeem_grant), Modifier.weight(1f)) { vm.grantRedemption(rq.id) }
                        SecondaryButton(stringResource(R.string.parent_redeem_decline), Modifier.weight(1f), minHeight = 56.dp) { vm.declineRedemption(rq.id) }
                    }
                }
            }
        }
    }

    // approvals inbox
    val cid = selected
    val approvals = s.approvals.valueOrNull ?: emptyList()
    SectionHeader(stringResource(R.string.parent_approvals), approvals.size)
    when (val ap = s.approvals) {
        is Loadable.Idle, Loadable.Loading -> HintText("…")
        is Loadable.Failed -> Column {
            Text(ap.message, color = MaterialTheme.colorScheme.error)
            SecondaryButton(stringResource(R.string.retry)) { cid?.let { vm.loadApprovals(it) } }
        }
        is Loadable.Loaded -> if (ap.value.isEmpty()) {
            Text(stringResource(R.string.parent_all_caught_up), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.tertiary)
        } else {
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

    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    SectionHeader(stringResource(R.string.parent_setup))
    HubGrid(onGo)
}

@Composable
private fun HubGrid(onGo: (Section) -> Unit) {
    val items = listOf(
        Quad("🗓️", R.string.nav_routines, R.string.nav_routines_sub, Section.Routines),
        Quad("🎁", R.string.nav_rewards, R.string.nav_rewards_sub, Section.Rewards),
        Quad("🌱", R.string.nav_ownership, R.string.nav_ownership_sub, Section.Ownership),
        Quad("🧒", R.string.nav_children, R.string.nav_children_sub, Section.Children),
        Quad("⚙️", R.string.nav_settings, R.string.nav_settings_sub, Section.Settings),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                pair.forEach { q ->
                    HubCard(q.emoji, stringResource(q.title), stringResource(q.sub), Modifier.weight(1f)) { onGo(q.section) }
                }
                if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class Quad(val emoji: String, val title: Int, val sub: Int, val section: Section)

@Composable
private fun HubCard(emoji: String, title: String, sub: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(emoji, fontSize = 28.sp)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ApprovalCard(a: Approval, title: String, onDecide: (Boolean) -> Unit) {
    Card {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(a.onDate.faDigits(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                BigButton(stringResource(R.string.parent_approve), Modifier.weight(1f)) { onDecide(true) }
                SecondaryButton(stringResource(R.string.parent_not_yet), Modifier.weight(1f), minHeight = 56.dp) { onDecide(false) }
            }
            HintText(stringResource(R.string.parent_not_yet_note))
        }
    }
}

// -------------------------------------------------------------- sections ---

@Composable
private fun Routines(vm: ParentViewModel, s: ParentState, childId: String?, onPickChild: (String) -> Unit) {
    val kids = s.children
    val activeName = kids.firstOrNull { it.childId == childId }?.name ?: ""

    // On a shared family phone the parent must see which child a routine is being
    // added to — the switcher above the list is the same identity used by every
    // action on this screen.
    if (kids.size > 1) {
        SectionHeader(stringResource(R.string.routine_pick_child))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            kids.forEach { k ->
                SelectPill(k.name, selected = k.childId == childId) { onPickChild(k.childId) }
            }
        }
    }

    LaunchedEffect(childId) { childId?.let { vm.loadAssigned(it) } }
    val assigned = s.assigned.valueOrNull ?: emptyList()
    val assignedIds = assigned.map { it.questId }.toSet()
    var pendingRemove by remember { mutableStateOf<hq.playfoundry.questgrow.data.model.AssignedRoutine?>(null) }

    // --- routines already on this child's plan, each removable ---
    if (activeName.isNotEmpty()) {
        SectionHeader(stringResource(R.string.routine_assigned_header, activeName), assigned.size)
        when (val a = s.assigned) {
            is Loadable.Loaded -> if (a.value.isEmpty()) {
                HintText(stringResource(R.string.routine_assigned_empty, activeName))
            } else a.value.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.icon}  ${r.title}  (${r.points.fa()})", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    GhostButton(stringResource(R.string.routine_remove)) { pendingRemove = r }
                }
            }
            is Loadable.Failed -> Text(a.message, color = MaterialTheme.colorScheme.error)
            else -> HintText("…")
        }
        HorizontalDivider(Modifier.padding(vertical = Space.sm))
    }

    SectionHeader(stringResource(R.string.routine_starters))
    if (activeName.isNotEmpty()) HintText(stringResource(R.string.routine_starters_sub, activeName))
    val existing = (s.quests.valueOrNull ?: emptyList()).map { it.questId }.toSet()
    val addLabel =
        if (activeName.isEmpty()) stringResource(R.string.routine_add_plain)
        else stringResource(R.string.routine_add_to_child, activeName)
    STARTERS.forEach { st ->
        val onPlan = st.id in assignedIds
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text("${st.icon}  ${st.title}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (onPlan) {
                Text(stringResource(R.string.routine_in_plan), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            } else {
                SecondaryButton(addLabel) {
                    if (st.id !in existing) vm.createQuest(st.id, st.title, st.icon, st.points, "daily")
                    childId?.let { vm.assign(it, st.id) }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    SectionHeader(stringResource(R.string.routines_family))
    HintText(stringResource(R.string.routines_family_sub))
    when (val q = s.quests) {
        is Loadable.Loaded -> if (q.value.isEmpty()) HintText(stringResource(R.string.routines_empty))
        else q.value.forEach { Text("${it.icon}  ${it.title}  (${it.points.fa()})", Modifier.padding(vertical = 2.dp)) }
        is Loadable.Failed -> Text(q.message, color = MaterialTheme.colorScheme.error)
        else -> HintText("…")
    }

    pendingRemove?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(r.title) },
            text = { Text(stringResource(R.string.routine_remove_confirm, r.title, activeName)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    childId?.let { vm.unassign(it, r.questId) }
                    pendingRemove = null
                }) { Text(stringResource(R.string.routine_remove)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun Rewards(vm: ParentViewModel, s: ParentState) {
    LaunchedEffect(Unit) { if (s.rewards is Loadable.Idle) vm.loadRewards() }
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableIntStateOf(20) }
    var byParent by remember { mutableStateOf(true) }
    SectionHeader(stringResource(R.string.reward_add))
    Field(stringResource(R.string.reward_name), name, { name = it })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reward_cost) + ": " + cost.fa(), Modifier.weight(1f))
        SecondaryButton("−") { if (cost > 5) cost -= 5 }
        androidx.compose.foundation.layout.Spacer(Modifier.width(Space.sm))
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
    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    when (val r = s.rewards) {
        is Loadable.Loaded -> if (r.value.isEmpty()) HintText(stringResource(R.string.rewards_empty))
        else r.value.forEach { Text("${it.icon}  ${it.name} — ${it.cost.fa()}", Modifier.padding(vertical = 2.dp)) }
        is Loadable.Failed -> Text(r.message, color = MaterialTheme.colorScheme.error)
        else -> HintText("…")
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
    SectionHeader(stringResource(R.string.ownership_support_title))
    val quests = s.quests.valueOrNull ?: emptyList()
    quests.forEach { q ->
        SelectRow(selected = quest == q.questId, onClick = { quest = q.questId }) {
            Text("${q.icon}  ${q.title}", style = MaterialTheme.typography.bodyLarge)
        }
    }
    Text(stringResource(R.string.ownership_stage_label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.sm))
    labels.forEach { (st, res) ->
        SelectRow(selected = stage == st, onClick = { stage = st }) {
            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
        }
    }
    BigButton(stringResource(R.string.ownership_update), enabled = quest.isNotBlank() && childId != null) {
        childId?.let { vm.setOwnership(it, quest, stage) }
    }
    HintText(stringResource(R.string.ownership_note))
    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    SectionHeader(stringResource(R.string.ownership_suggestions))
    when (val sug = s.suggestions) {
        is Loadable.Loaded -> sug.value.forEach { g ->
            Card {
                Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(quests.firstOrNull { it.questId == g.questId }?.title ?: g.questId)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
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

    SectionHeader(stringResource(R.string.parent_family))
    HintText(stringResource(R.string.children_all_on_device))
    s.children.forEach { c ->
        Card {
            Row(Modifier.fillMaxWidth().padding(Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Avatar(c.name, size = 40.dp)
                Text("${c.name}  (${c.ageBand})", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    HintText(stringResource(R.string.children_own_device_hint))
    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    Text(stringResource(R.string.children_add_title), style = MaterialTheme.typography.titleMedium)
    Field(stringResource(R.string.onb_child_name), name, { name = it })
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        listOf("3-4", "5-6", "7-8").forEach { b ->
            SelectPill(b.faDigits(), selected = band == b, modifier = Modifier.weight(1f)) { band = b }
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
            HintText(stringResource(R.string.settings_notifs_help))
        }
        Switch(checked = notif, onCheckedChange = { notif = it; vm.setNotifications(it) })
    }
    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    SectionHeader(stringResource(R.string.settings_child_code))
    HintText(stringResource(R.string.settings_pair_help))
    BigButton(stringResource(R.string.settings_create_code), enabled = childId != null) { childId?.let { vm.issueChildCode(it) } }
    s.lastChildCode?.let {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Text(
                it.faDigits(),
                Modifier.fillMaxWidth().padding(Space.lg),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    SectionHeader(stringResource(R.string.settings_voice))
    val narrator = hq.playfoundry.questgrow.ui.child.rememberNarrator()
    HintText(stringResource(R.string.settings_voice_status, narrator.status))
    SecondaryButton(stringResource(R.string.settings_voice_test), enabled = narrator.hasVoice) {
        narrator.say("سلام! این صدای فارسی است.")
    }
    HintText(stringResource(R.string.settings_voice_help))

    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    SectionHeader(stringResource(R.string.settings_backend))
    HintText(stringResource(R.string.settings_backend_help))
    Field(stringResource(R.string.settings_backend_url), url, { url = it }, ltr = true)
    SecondaryButton(stringResource(R.string.settings_save_restart)) { container.setBaseUrl(url); context.restartApp() }
    HorizontalDivider(Modifier.padding(vertical = Space.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        GhostButton(stringResource(R.string.settings_signout)) { vm.signOut() }
        GhostButton(stringResource(R.string.settings_forget)) { vm.forgetDevice() }
    }
}
