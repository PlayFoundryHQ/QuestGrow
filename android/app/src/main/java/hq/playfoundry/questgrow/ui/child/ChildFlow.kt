package hq.playfoundry.questgrow.ui.child

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.adapt.ComplexityProfile
import hq.playfoundry.questgrow.data.model.CompletionOutcome
import hq.playfoundry.questgrow.data.model.QuestVisualState
import hq.playfoundry.questgrow.data.model.TodayQuest
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.ErrorRetry
import hq.playfoundry.questgrow.ui.Loading
import hq.playfoundry.questgrow.ui.SecondaryButton
import hq.playfoundry.questgrow.ui.collectAsStateSafe
import hq.playfoundry.questgrow.ui.isReducedMotion

@Composable
fun ChildFlow(container: AppContainer, onExit: () -> Unit) {
    val vm: ChildViewModel = viewModel { ChildViewModel(container) }
    val nav = rememberNavController()
    val narrator = rememberNarrator()
    LaunchedEffect(Unit) { vm.refresh() }

    NavHost(nav, startDestination = "today") {
        composable("today") {
            TodayScreen(
                vm = vm, narrator = narrator,
                onOpenQuest = { q -> nav.navigate("doit/${q.questId}/${q.title}/${q.icon}") },
                onProgress = { vm.loadProgress(); nav.navigate("progress") },
                onExit = onExit,
            )
        }
        composable("doit/{qid}/{title}/{icon}") { entry ->
            val qid = entry.arguments?.getString("qid").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty()
            val icon = entry.arguments?.getString("icon").orEmpty()
            DoItScreen(
                title = title, icon = icon, narrator = narrator,
                onBack = { nav.popBackStack() },
                onDid = {
                    vm.complete(qid) { outcome ->
                        when (outcome) {
                            is CompletionOutcome.Verified -> nav.navigate("celebrate") { popUpTo("today") }
                            is CompletionOutcome.WaitingForGrownup,
                            is CompletionOutcome.QueuedOffline -> nav.navigate("waiting") { popUpTo("today") }
                            is CompletionOutcome.Rejected -> nav.popBackStack()
                        }
                    }
                },
            )
        }
        composable("waiting") { WaitingScreen(onBack = { nav.popBackStack("today", false) }) }
        composable("celebrate") {
            CelebrationScreen(vm) { vm.clearCelebration(); nav.popBackStack("today", false) }
        }
        composable("progress") { ChildProgressScreen(vm) { nav.popBackStack() } }
    }
}

@Composable
private fun TodayScreen(
    vm: ChildViewModel,
    narrator: Narrator,
    onOpenQuest: (TodayQuest) -> Unit,
    onProgress: () -> Unit,
    onExit: () -> Unit,
) {
    val state by vm.today.collectAsStateSafe()
    when (val s = state) {
        is TodayUi.Loading -> Loading()
        is TodayUi.NeedsCode -> CodeEntry(onCode = vm::setCode, onGrownUp = onExit)
        is TodayUi.Error ->
            if (s.authExpired) CodeEntry(onCode = vm::setCode, onGrownUp = onExit)
            else ErrorRetry(s.message, vm::refresh, onGrownUp = onExit)
        is TodayUi.Ready -> {
            val view = s.view
            LaunchedEffect(view.onDate) {
                if (view.profile.autoReadOnOpen && view.visibleQuests.isNotEmpty()) {
                    narrator.say(view.visibleQuests.joinToString(". ") { it.title })
                }
            }
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("QuestGrow", style = MaterialTheme.typography.headlineMedium)
                if (view.stale) Text(
                    "📴 You're offline — showing your last board",
                    Modifier.fillMaxWidth().semantics { contentDescription = "Offline. Showing your last board." },
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (s.queued > 0) Text("${s.queued} waiting to send", color = MaterialTheme.colorScheme.secondary)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(view.visibleQuests, key = { it.questId }) { q ->
                        QuestCard(q, view.profile, narrator) {
                            when (q.state) {
                                QuestVisualState.VERIFIED -> {}
                                QuestVisualState.PENDING_GROWNUP, QuestVisualState.QUEUED_OFFLINE -> {}
                                QuestVisualState.AVAILABLE -> onOpenQuest(q)
                            }
                        }
                    }
                }
                if (view.allDone) {
                    Text(
                        "🌟 Day complete!",
                        Modifier.fillMaxWidth().padding(8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Progress", Modifier.weight(1f), minHeight = 64.dp, onClick = onProgress)
                    SecondaryButton("Grown-up", Modifier.weight(1f), minHeight = 64.dp, onClick = onExit)
                }
            }
        }
    }
}

@Composable
private fun QuestCard(q: TodayQuest, profile: ComplexityProfile, narrator: Narrator, onClick: () -> Unit) {
    val cue = when (q.state) {
        QuestVisualState.VERIFIED -> "✓ done"
        QuestVisualState.PENDING_GROWNUP -> "⏳ waiting"
        QuestVisualState.QUEUED_OFFLINE -> "⏳ will send"
        QuestVisualState.AVAILABLE -> ""
    }
    val border = when (q.state) {
        QuestVisualState.VERIFIED -> MaterialTheme.colorScheme.tertiary
        QuestVisualState.PENDING_GROWNUP, QuestVisualState.QUEUED_OFFLINE -> MaterialTheme.colorScheme.secondary
        QuestVisualState.AVAILABLE -> MaterialTheme.colorScheme.primary
    }
    Card(
        onClick = onClick,
        border = CardDefaults.outlinedCardBorder().copy(width = 3.dp, brush = androidx.compose.ui.graphics.SolidColor(border)),
        modifier = Modifier
            .heightIn(min = 120.dp)
            .semantics {
                contentDescription = buildString {
                    append(q.title)
                    if (cue.isNotBlank()) append(", ").append(cue.removePrefix("✓ ").removePrefix("⏳ "))
                }
            }
            .pointerInput(q.questId) { detectTapGestures(onLongPress = { narrator.say(q.title) }, onTap = { onClick() }) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(q.icon.ifBlank { "⭐" }, fontSize = 48.sp)
            if (profile.showLabels) {
                Text(q.title, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            // state as text + glyph, never colour alone (accessibility baseline)
            if (cue.isNotBlank()) Text(cue, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DoItScreen(title: String, icon: String, narrator: Narrator, onBack: () -> Unit, onDid: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
        SecondaryButton("‹ Today", minHeight = 64.dp, onClick = onBack)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon.ifBlank { "⭐" }, fontSize = 76.sp)
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            BigButton("🔊 Hear it", contentDescription = "Hear $title") { narrator.say(title) }
        }
        BigButton("I did it!", enabled = !busy, contentDescription = "I did $title") {
            busy = true; onDid()
        }
    }
}

@Composable
private fun WaitingScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag("waiting"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("👍", fontSize = 64.sp)
        Text("All done — waiting for your grown-up.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        BigButton("Back to Today", Modifier.padding(top = 24.dp)) { onBack() }
    }
}

@Composable
private fun CelebrationScreen(vm: ChildViewModel, onDone: () -> Unit) {
    val cel by vm.celebration.collectAsStateSafe()
    val reduceMotion = isReducedMotion()
    val scale = if (reduceMotion) 1f else {
        val t = rememberInfiniteTransition(label = "pop")
        t.animateFloat(
            initialValue = 0.85f, targetValue = 1.1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s",
        ).value
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag("celebration"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⭐", fontSize = 96.sp, modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale))
        Text(
            cel?.let { "+${it.pointsAwarded}" } ?: "Nice work!",
            style = MaterialTheme.typography.headlineMedium,
        )
        BigButton("Back to Today", Modifier.padding(top = 28.dp)) { onDone() }
    }
}

@Composable
private fun ChildProgressScreen(vm: ChildViewModel, onBack: () -> Unit) {
    val p by vm.progress.collectAsStateSafe()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryButton("‹ Today", minHeight = 64.dp, onClick = onBack)
        Text("This week", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(7) { i ->
                Box(
                    Modifier
                        .size(34.dp)
                        .padding(2.dp)
                        .background(
                            if (i < (p?.weekActiveDays ?: 0)) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        // progressive consistency — never a streak (DECISION-013/014)
        Text("You showed up ${p?.weekActiveDays ?: 0} ${if (p?.weekActiveDays == 1) "day" else "days"} this week.")
        Text("Stars earned", style = MaterialTheme.typography.titleLarge)
        Text("${p?.lifetimeAchievement ?: 0} ⭐", style = MaterialTheme.typography.headlineMedium)
        Text("To spend: ${p?.spendableBalance ?: 0}")
    }
}

@Composable
private fun CodeEntry(onCode: (String) -> Unit, onGrownUp: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Text("Ask your grown-up for your code.", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code") }, modifier = Modifier.fillMaxWidth())
        BigButton("Start", enabled = code.isNotBlank()) { onCode(code.trim()) }
        SecondaryButton("Grown-up", minHeight = 64.dp) { onGrownUp() }
    }
}
