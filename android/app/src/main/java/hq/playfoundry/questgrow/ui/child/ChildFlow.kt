package hq.playfoundry.questgrow.ui.child

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
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
import hq.playfoundry.questgrow.R
import hq.playfoundry.questgrow.data.model.CompletionOutcome
import hq.playfoundry.questgrow.data.model.KidReward
import hq.playfoundry.questgrow.data.model.QuestVisualState
import hq.playfoundry.questgrow.data.model.RedeemOutcome
import hq.playfoundry.questgrow.data.model.TodayQuest
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.Loading
import hq.playfoundry.questgrow.ui.SecondaryButton
import hq.playfoundry.questgrow.ui.collectAsStateSafe
import hq.playfoundry.questgrow.ui.fa
import hq.playfoundry.questgrow.ui.isReducedMotion

@Composable
fun ChildFlow(container: AppContainer, onGrownUps: () -> Unit) {
    val vm: ChildViewModel = viewModel { ChildViewModel(container) }
    val nav = rememberNavController()
    val narrator = rememberNarrator()
    LaunchedEffect(Unit) { vm.refresh() }

    NavHost(nav, startDestination = "today") {
        composable("today") {
            TodayScreen(
                vm, narrator, onGrownUps = onGrownUps,
                onOpen = { q -> nav.navigate("doit/${q.questId}/${q.title}/${q.icon}") },
                onProgress = { vm.loadProgress(); nav.navigate("progress") },
                onRewards = { vm.loadRewards(); nav.navigate("rewards") },
            )
        }
        composable("doit/{qid}/{title}/{icon}") { e ->
            val qid = e.arguments?.getString("qid").orEmpty()
            val title = e.arguments?.getString("title").orEmpty()
            val icon = e.arguments?.getString("icon").orEmpty()
            DoItScreen(title, icon, narrator, onBack = { nav.popBackStack() }) {
                vm.complete(qid) { outcome ->
                    when (outcome) {
                        is CompletionOutcome.Verified -> nav.navigate("celebrate") { popUpTo("today") }
                        is CompletionOutcome.WaitingForGrownup,
                        is CompletionOutcome.QueuedOffline -> nav.navigate("waiting") { popUpTo("today") }
                        is CompletionOutcome.Rejected -> nav.popBackStack()
                    }
                }
            }
        }
        composable("waiting") { WaitingScreen { nav.popBackStack("today", false) } }
        composable("celebrate") { CelebrationScreen(vm) { vm.clearCelebration(); nav.popBackStack("today", false) } }
        composable("progress") { ProgressScreen(vm) { nav.popBackStack() } }
        composable("rewards") { RewardsScreen(vm, narrator) { nav.popBackStack() } }
    }
}

@Composable
private fun AvatarRow(vm: ChildViewModel) {
    val kids by vm.deviceChildren.collectAsStateSafe()
    val active by vm.activeChildId.collectAsStateSafe()
    if (kids.size < 2) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kids.forEach { k ->
            val on = k.childId == active
            AssistChip(
                onClick = { vm.switchChild(k.childId) },
                label = { Text(k.name, style = MaterialTheme.typography.titleMedium) },
                leadingIcon = { Text(k.name.take(1)) },
                colors = if (on) AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) else AssistChipDefaults.assistChipColors(),
                border = if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else AssistChipDefaults.assistChipBorder(true),
                modifier = Modifier.heightIn(min = 44.dp)
                    .semantics { contentDescription = k.name + (if (on) "، انتخاب‌شده" else "") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayScreen(
    vm: ChildViewModel,
    narrator: Narrator,
    onGrownUps: () -> Unit,
    onOpen: (TodayQuest) -> Unit,
    onProgress: () -> Unit,
    onRewards: () -> Unit,
) {
    val state by vm.today.collectAsStateSafe()
    when (val s = state) {
        is TodayUi.Loading -> Loading()
        is TodayUi.NeedsCode -> CallAGrownUp(onGrownUps)
        is TodayUi.Error -> Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            BigButton(stringResource(R.string.retry), Modifier.padding(top = 16.dp)) { vm.refresh() }
        }
        is TodayUi.Ready -> {
            val view = s.view
            LaunchedEffect(view.onDate, view.childId) {
                if (view.profile.autoReadOnOpen) {
                    narrator.say(view.visibleQuests.joinToString("، ") { it.title })
                }
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // grown-up gate — a visible, single-tap chip (the PIN pad is the
                // actual barrier; a hidden long-press was undiscoverable).
                TextButton(
                    onClick = onGrownUps,
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = 44.dp)
                        .semantics { contentDescription = "بزرگترها" },
                ) {
                    Text(
                        "بزرگترها ›",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AvatarRow(vm)
                if (view.stale) Text(
                    stringResource(R.string.kid_offline_banner),
                    Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        .semantics { contentDescription = "آفلاین. تختهٔ قبلی نشان داده می‌شود." },
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (s.queued > 0) Text(
                    stringResource(R.string.kid_queued, s.queued.fa()),
                    color = MaterialTheme.colorScheme.secondary,
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(view.visibleQuests, key = { it.questId }) { q ->
                        QuestCard(q, showLabel = view.profile.showLabels) {
                            if (q.state == QuestVisualState.AVAILABLE) onOpen(q)
                        }
                    }
                }
                if (view.allDone) Text(
                    stringResource(R.string.kid_all_done),
                    Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SecondaryButton(
                        "⭐ " + stringResource(R.string.kid_stars),
                        Modifier.weight(1f), minHeight = 56.dp, onClick = onProgress,
                    )
                    SecondaryButton(
                        stringResource(R.string.kid_rewards),
                        Modifier.weight(1f), minHeight = 56.dp, onClick = onRewards,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestCard(q: TodayQuest, showLabel: Boolean, onClick: () -> Unit) {
    val done = q.state == QuestVisualState.VERIFIED
    val waiting = q.state == QuestVisualState.PENDING_GROWNUP || q.state == QuestVisualState.QUEUED_OFFLINE
    val stateText = when {
        done -> stringResource(R.string.state_done)
        waiting -> stringResource(R.string.state_waiting)
        else -> null
    }
    Card(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .combinedClickable(onClick = onClick, onLongClick = {})
            .semantics { contentDescription = q.title + (stateText?.let { "، $it" } ?: "") },
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(q.icon.ifBlank { "⭐" }, fontSize = 64.sp)
            if (showLabel) Text(
                q.title,
                Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            stateText?.let {
                Text(
                    it, Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun DoItScreen(title: String, icon: String, narrator: Narrator, onBack: () -> Unit, onDid: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onBack) {
            Text("‹ " + stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon.ifBlank { "⭐" }, fontSize = 96.sp)
            Text(
                title, Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            )
            if (narrator.hasVoice) BigButton(
                stringResource(R.string.kid_hear),
                Modifier.padding(top = 20.dp),
                contentDescription = stringResource(R.string.cd_hear, title),
            ) { narrator.say(title) }
        }
        BigButton(
            stringResource(R.string.kid_i_did_it),
            contentDescription = stringResource(R.string.cd_i_did, title),
        ) { onDid() }
    }
}

@Composable
private fun WaitingScreen(onBack: () -> Unit) {
    CentredHero("👍", stringResource(R.string.kid_waiting_title), stringResource(R.string.kid_back_to_today), onBack)
}

@Composable
private fun CelebrationScreen(vm: ChildViewModel, onDone: () -> Unit) {
    val cel by vm.celebration.collectAsStateSafe()
    val reduced = isReducedMotion()
    val scale = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "pop")
        t.animateFloat(
            0.9f, 1.1f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s",
        ).value
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("⭐", fontSize = 110.sp, modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale))
        cel?.let { Text("+${it.pointsAwarded.fa()}", style = MaterialTheme.typography.displaySmall) }
        BigButton(stringResource(R.string.kid_back_to_today), Modifier.padding(top = 8.dp)) { onDone() }
    }
}

@Composable
private fun ProgressScreen(vm: ChildViewModel, onBack: () -> Unit) {
    val p by vm.progress.collectAsStateSafe()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) {
            Text("‹ " + stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.kid_this_week), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(7) { i ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(
                        if (i < (p?.weekActiveDays ?: 0)) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }
        Text(stringResource(R.string.kid_week_days, "${(p?.weekActiveDays ?: 0).fa()} روز"))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.kid_stars), style = MaterialTheme.typography.titleLarge)
        Text("${(p?.lifetimeAchievement ?: 0).fa()} ⭐", style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.kid_to_spend, (p?.spendableBalance ?: 0).fa()))
    }
}

@Composable
private fun RewardsScreen(vm: ChildViewModel, narrator: Narrator, onBack: () -> Unit) {
    val state by vm.rewards.collectAsStateSafe()
    var asking by remember { mutableStateOf<KidReward?>(null) }
    var flash by remember { mutableStateOf<String?>(null) }
    val askedGrownupText = stringResource(R.string.kid_reward_asked)

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) {
            Text("‹ " + stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.kid_rewards_title), style = MaterialTheme.typography.headlineSmall)

        when (val s = state) {
            is RewardsUi.Loading -> Loading(Modifier.height(120.dp))
            is RewardsUi.Error -> {
                Text(s.message, style = MaterialTheme.typography.titleMedium)
                BigButton(stringResource(R.string.retry)) { vm.loadRewards() }
            }
            is RewardsUi.Ready -> {
                Text(
                    stringResource(R.string.kid_balance, s.rewards.spendableBalance.fa()),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                flash?.let {
                    Text(it, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }
                if (s.rewards.rewards.isEmpty()) {
                    Text(stringResource(R.string.kid_rewards_empty), style = MaterialTheme.typography.bodyLarge)
                }
                s.rewards.rewards.forEach { r -> RewardCard(r) { asking = r } }
            }
        }
    }

    asking?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.kid_reward_ask_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${r.icon}  ${r.name}", style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.kid_reward_ask_body, r.cost.fa()))
                    if (r.needsGrownup) Text(stringResource(R.string.kid_reward_ask_grownup))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = r
                    asking = null
                    vm.redeem(chosen) { outcome ->
                        flash = when (outcome) {
                            is RedeemOutcome.Granted -> { narrator.say(chosen.name); "🎉 ${chosen.name}" }
                            is RedeemOutcome.AskedGrownup -> askedGrownupText
                            is RedeemOutcome.Rejected -> outcome.detail
                        }
                    }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.no)) } },
        )
    }
}

@Composable
private fun RewardCard(r: KidReward, onGet: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(r.icon.ifBlank { "🎁" }, fontSize = 40.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.kid_reward_cost, r.cost.fa()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                r.pending -> Text(
                    stringResource(R.string.kid_reward_pending),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                r.affordable -> SecondaryButton(
                    stringResource(R.string.kid_reward_get), minHeight = 56.dp, onClick = onGet,
                )
                else -> Text(
                    stringResource(R.string.kid_reward_locked),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CallAGrownUp(onGrownUps: () -> Unit) {
    CentredHero("🧑‍🍼", "به بزرگترت بگو کمکت کند", stringResource(R.string.grownups), onGrownUps)
}

@Composable
private fun CentredHero(emoji: String, title: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 96.sp)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        BigButton(button, Modifier.padding(top = 8.dp)) { onClick() }
    }
}
