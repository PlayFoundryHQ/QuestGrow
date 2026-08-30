package hq.playfoundry.questgrow.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.core.Loadable
import hq.playfoundry.questgrow.data.model.OwnershipStage
import hq.playfoundry.questgrow.restartApp
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.SecondaryButton
import hq.playfoundry.questgrow.ui.collectAsStateSafe

private val ADAPT_DIMS = listOf(
    "text_style", "audio_narration", "iconography", "quests_shown_at_once",
    "interaction", "task_complexity", "reading_requirement", "reward_presentation",
)

@Composable
fun ParentFlow(container: AppContainer, onExit: () -> Unit) {
    val vm: ParentViewModel = viewModel { ParentViewModel(container) }
    val s by vm.state.collectAsStateSafe()

    if (!s.signedIn) {
        SignInScreen(vm, onExit, container)
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Dashboard", "Approvals", "Family", "Quests", "Rewards", "Ownership", "Settings")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i },
                    text = { Text(t, Modifier.testTag("tab_$t")) })
            }
        }
        s.message?.let {
            Text(
                it,
                Modifier.fillMaxWidth().padding(12.dp).testTag("parent_message"),
                color = MaterialTheme.colorScheme.primary,
            )
            LaunchedEffect(it) { kotlinx.coroutines.delay(3000); vm.clearMessage() }
        }
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> DashboardTab(vm)
                1 -> ApprovalsTab(vm)
                2 -> FamilyTab(vm)
                3 -> QuestsTab(vm)
                4 -> RewardsTab(vm)
                5 -> OwnershipTab(vm)
                6 -> SettingsTab(vm, onExit, container)
            }
        }
    }
}

// --------------------------------------------------------------------------- #
// reusable state-aware section                                                 #
// --------------------------------------------------------------------------- #
/**
 * Renders one [Loadable] list section with explicit Loading / Failed+retry /
 * Empty / content states (Phase J grant §3).
 */
@Composable
private fun <T> LoadableList(
    state: Loadable<List<T>>,
    tag: String,
    emptyText: String,
    onRetry: () -> Unit,
    item: @Composable (T) -> Unit,
) {
    when (state) {
        is Loadable.Idle -> Unit
        is Loadable.Loading -> Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("${tag}_loading"),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator(Modifier.size(28.dp)) }
        is Loadable.Failed -> Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("${tag}_error"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (state.offline) "You're offline — can't load this right now." else state.message,
                color = MaterialTheme.colorScheme.error,
            )
            SecondaryButton("Try again") { onRetry() }
        }
        is Loadable.Loaded -> if (state.value.isEmpty()) {
            Text(emptyText, Modifier.padding(vertical = 4.dp).testTag("${tag}_empty"),
                style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(Modifier.testTag("${tag}_list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.value.forEach { item(it) }
            }
        }
    }
}

@Composable
private fun SignInScreen(vm: ParentViewModel, onExit: () -> Unit, container: AppContainer) {
    val s by vm.state.collectAsStateSafe()
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(container.baseUrl) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()).testTag("sign_in"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(email, { email = it }, label = { Text("Email") },
            modifier = Modifier.fillMaxWidth().testTag("email"))
        OutlinedTextField(pw, { pw = it }, label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("password"))
        OutlinedTextField(pin, { pin = it }, label = { Text("Parent PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().testTag("pin"))
        BigButton("Unlock parent mode", enabled = !s.busy) { vm.signIn(email, pw, pin) }
        SecondaryButton("Create account") { vm.signUp(email, pw, pin) }
        Text("The PIN is the parent gate — required every session.", style = MaterialTheme.typography.labelLarge)
        s.message?.let { Text(it, Modifier.testTag("signin_message"), color = MaterialTheme.colorScheme.error) }
        SecondaryButton(if (showServer) "Hide server" else "Backend server") { showServer = !showServer }
        if (showServer) {
            OutlinedTextField(server, { server = it }, label = { Text("Backend URL") },
                modifier = Modifier.fillMaxWidth())
            SecondaryButton("Save & restart") { container.setBaseUrl(server); context.restartApp() }
        }
        SecondaryButton("Back") { onExit() }
    }
}

@Composable private fun sectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

@Composable private fun field(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false) =
    OutlinedTextField(
        value, onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().testTag("field_${label.substringBefore(' ')}"),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )

// --------------------------------------------------------------------------- #
// tabs                                                                         #
// --------------------------------------------------------------------------- #
@Composable
private fun DashboardTab(vm: ParentViewModel) {
    val s by vm.state.collectAsStateSafe()
    LaunchedEffect(Unit) { if (s.family is Loadable.Idle) vm.refreshFamily() }
    var cid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var band by remember { mutableStateOf("5-6") }
    sectionCard("Add a child") {
        field("Child id", cid, { cid = it }); field("Name", name, { name = it }); field("Age band", band, { band = it })
        BigButton("Create child", enabled = cid.isNotBlank() && name.isNotBlank()) { vm.addChild(cid, name, band) }
        SecondaryButton("Add starter quests") { vm.seedStarters() }
    }
    var codeChild by remember { mutableStateOf("") }
    sectionCard("Child sign-in code") {
        field("Child id", codeChild, { codeChild = it })
        BigButton("Create code", enabled = codeChild.isNotBlank()) { vm.issueChildCode(codeChild) }
        s.lastChildCode?.let {
            Text("Code: $it", Modifier.testTag("child_code"), style = MaterialTheme.typography.labelLarge)
        }
    }
    sectionCard("Children") {
        LoadableList(
            state = s.family, tag = "family",
            emptyText = "No children yet — add one above.",
            onRetry = vm::refreshFamily,
        ) { row ->
            val d = row.summary
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(row.child.name, style = MaterialTheme.typography.titleLarge)
                Text("Today: ${d?.verified ?: 0}/${d?.total ?: 0} done · ${d?.pending ?: 0} waiting")
                Text("Showed up ${days(d?.weekActiveDays ?: 0)} this week.")
                SecondaryButton("Materialise today") { vm.materialiseToday(row.child.childId) }
            }
        }
    }
}

@Composable
private fun ApprovalsTab(vm: ParentViewModel) {
    val s by vm.state.collectAsStateSafe()
    var cid by remember { mutableStateOf(s.children.firstOrNull()?.childId ?: "") }
    LaunchedEffect(Unit) { if (cid.isBlank()) s.children.firstOrNull()?.let { cid = it.childId } }
    sectionCard("Approvals") {
        field("Child id", cid, { cid = it })
        BigButton("Load queue", enabled = cid.isNotBlank()) { vm.loadApprovals(cid) }
        (s.approvals as? Loadable.Loaded)?.value?.takeIf { it.isNotEmpty() }?.let {
            BigButton("Approve all (${it.size})") { vm.approveAll(cid) }
        }
        LoadableList(
            state = s.approvals, tag = "approvals",
            emptyText = "Nothing waiting — you're all caught up.",
            onRetry = { vm.loadApprovals(cid) },
        ) { a ->
            HorizontalDivider()
            Text("${a.questId} · ${a.onDate}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Approve", Modifier.weight(1f)) { vm.approve(cid, a) }
                SecondaryButton("Not yet", Modifier.weight(1f)) { vm.notYet(cid, a) }
            }
        }
    }
}

@Composable
private fun FamilyTab(vm: ParentViewModel) {
    val s by vm.state.collectAsStateSafe()
    LaunchedEffect(Unit) { if (s.family is Loadable.Idle) vm.refreshFamily() }
    var cid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var band by remember { mutableStateOf("") }
    var bd by remember { mutableStateOf("") }
    val overrides = remember { mutableStateMapOf<String, String>() }
    sectionCard("Children") {
        LoadableList(
            state = s.family, tag = "family_family",
            emptyText = "No children yet — add one on the Dashboard tab.",
            onRetry = vm::refreshFamily,
        ) { row -> Text("${row.child.name}  (${row.child.childId}, age ${row.child.ageBand})") }
    }
    sectionCard("Child profile") {
        field("Child id", cid, { cid = it })
        field("Name (blank = keep)", name, { name = it })
        field("Age band (blank = keep)", band, { band = it })
        field("Birthdate YYYY-MM-DD (optional)", bd, { bd = it })
        BigButton("Save profile", enabled = cid.isNotBlank()) {
            vm.editChild(cid, name.ifBlank { null }, band.ifBlank { null }, bd.ifBlank { null }, null)
        }
    }
    sectionCard("Age-adaptation overrides") {
        Text("Blank = use the age-band default.", style = MaterialTheme.typography.labelLarge)
        ADAPT_DIMS.forEach { dim ->
            field(dim.replace('_', ' '), overrides[dim] ?: "", { overrides[dim] = it })
        }
        BigButton("Save overrides", enabled = cid.isNotBlank()) {
            vm.editChild(cid, null, null, null, overrides.filterValues { it.isNotBlank() })
        }
    }
}

@Composable
private fun QuestsTab(vm: ParentViewModel) {
    val s by vm.state.collectAsStateSafe()
    LaunchedEffect(Unit) { if (s.quests is Loadable.Idle) vm.loadQuests() }
    var id by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("⭐") }
    var points by remember { mutableStateOf("10") }
    var rec by remember { mutableStateOf("daily") }
    sectionCard("New quest") {
        field("Quest id", id, { id = it }); field("Title", title, { title = it }); field("Icon", icon, { icon = it })
        field("Points", points, { points = it }, number = true); field("Recurrence", rec, { rec = it })
        BigButton("Create + schedule", enabled = id.isNotBlank() && title.isNotBlank()) {
            vm.createQuest(id, title, icon, points.toIntOrNull() ?: 10, rec)
        }
        SecondaryButton("One-tap starter templates") { vm.seedStarters() }
    }
    var acid by remember { mutableStateOf("") }
    var aqid by remember { mutableStateOf("") }
    sectionCard("Assign to a child") {
        field("Child id", acid, { acid = it }); field("Quest id", aqid, { aqid = it })
        BigButton("Assign", enabled = acid.isNotBlank() && aqid.isNotBlank()) { vm.assign(acid, aqid) }
    }
    sectionCard("Quests") {
        LoadableList(
            state = s.quests, tag = "quests",
            emptyText = "No quests yet — create one or add starter templates.",
            onRetry = vm::loadQuests,
        ) { q ->
            Text("${q.icon} ${q.title}  (v${q.version}, ${q.points} pts${if (q.archived) ", archived" else ""})")
        }
    }
}

@Composable
private fun RewardsTab(vm: ParentViewModel) {
    val s by vm.state.collectAsStateSafe()
    LaunchedEffect(Unit) { if (s.rewards is Loadable.Idle) vm.loadRewards() }
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🎁") }
    var cost by remember { mutableStateOf("50") }
    var mode by remember { mutableStateOf("self_service") }
    sectionCard("New reward") {
        field("Reward id", id, { id = it }); field("Name", name, { name = it }); field("Icon", icon, { icon = it })
        field("Cost", cost, { cost = it }, number = true)
        field("Redemption (self_service / parent_confirmed)", mode, { mode = it })
        BigButton("Create", enabled = id.isNotBlank() && name.isNotBlank()) {
            vm.createReward(id, name, icon, cost.toIntOrNull() ?: 0, mode)
        }
    }
    sectionCard("Rewards") {
        LoadableList(
            state = s.rewards, tag = "rewards",
            emptyText = "No rewards yet — create one above.",
            onRetry = vm::loadRewards,
        ) { r -> Text("${r.icon} ${r.name} — ${r.cost} (${r.mode})") }
    }
}

@Composable
private fun OwnershipTab(vm: ParentViewModel) {
    val s by vm.state.collectAsStateSafe()
    var cid by remember { mutableStateOf("") }
    var qid by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(OwnershipStage.PARENT_GUIDED) }
    sectionCard("Advancement suggestions") {
        field("Child id", cid, { cid = it })
        BigButton("Load suggestions", enabled = cid.isNotBlank()) { vm.loadSuggestions(cid) }
        LoadableList(
            state = s.suggestions, tag = "suggestions",
            emptyText = "No suggestions right now — that's fine.",
            onRetry = { vm.loadSuggestions(cid) },
        ) { sug ->
            HorizontalDivider()
            Text("${sug.questId}: ready for a bit more independence?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Yes, let's try", Modifier.weight(1f)) { vm.acceptSuggestion(cid, sug.questId) }
                SecondaryButton("Ask me later", Modifier.weight(1f)) { vm.dismissSuggestion(cid, sug.questId) }
            }
        }
    }
    sectionCard("Set support level for a quest") {
        field("Quest id", qid, { qid = it })
        OwnershipStage.entries.forEach { st ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = stage == st, onClick = { stage = st })
                Text(st.parentLabel)
            }
        }
        BigButton("Update", enabled = cid.isNotBlank() && qid.isNotBlank()) { vm.setOwnership(cid, qid, stage) }
        Text("Moving to more support is a normal adjustment, never a setback.",
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SettingsTab(vm: ParentViewModel, onExit: () -> Unit, container: AppContainer) {
    val context = LocalContext.current
    var notif by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(container.baseUrl) }
    sectionCard("Notifications") {
        Text("Off by default. Informational only — never loss-framed, never sent to the child.",
            style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().semantics {
            contentDescription = "Quiet note when a completion is verified, ${if (notif) "on" else "off"}"
        }, verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = notif, onCheckedChange = { notif = it; vm.setNotifications(it) },
                modifier = Modifier.testTag("notif_switch"))
            Text("Quiet note when a completion is verified", Modifier.padding(start = 8.dp))
        }
    }
    sectionCard("Parent gate") {
        Text("The PIN is required every session. Set at account creation; changing it is post-MVP.",
            style = MaterialTheme.typography.labelLarge)
    }
    sectionCard("Backend server") {
        Text("Which QuestGrow backend this device talks to.", style = MaterialTheme.typography.labelLarge)
        field("Backend URL", server, { server = it })
        SecondaryButton("Save & restart app") { container.setBaseUrl(server); context.restartApp() }
    }
    sectionCard("Session") {
        SecondaryButton("Sign out") { vm.signOut() }
        SecondaryButton("Forget this device") { vm.forgetDevice() }
        SecondaryButton("Switch to kid mode") { onExit() }
    }
}
