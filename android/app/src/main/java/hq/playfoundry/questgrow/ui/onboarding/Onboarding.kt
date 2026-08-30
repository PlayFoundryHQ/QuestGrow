package hq.playfoundry.questgrow.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.R
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.model.ChildProfile
import hq.playfoundry.questgrow.ui.AppScaffold
import hq.playfoundry.questgrow.ui.Avatar
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.DigitPad
import hq.playfoundry.questgrow.ui.Field
import hq.playfoundry.questgrow.ui.GhostButton
import hq.playfoundry.questgrow.ui.HintText
import hq.playfoundry.questgrow.ui.STARTERS
import hq.playfoundry.questgrow.ui.Space
import hq.playfoundry.questgrow.ui.StepDots
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private enum class Step { Who, Account, SignIn, PickChild, Child, Routines, Pair }

private fun Step.dotIndex() = when (this) {
    Step.Who -> 0
    Step.Account, Step.Pair -> 1
    Step.Child -> 2
    Step.Routines -> 3
    Step.SignIn, Step.PickChild -> -1   // recovery branch — no dots
}

/**
 * Guided first-run: a 4-step stepper for a new family, plus a returning-parent
 * sign-in branch (account already exists server-side) and the child-device
 * pairing branch.
 */
@Composable
fun OnboardingFlow(container: AppContainer, onDone: () -> Unit) {
    var step by remember { mutableStateOf(Step.Who) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var ageBand by remember { mutableStateOf("5-6") }
    val childId = remember { "c" + UUID.randomUUID().toString().take(8) }
    val picked = remember { mutableStateListOf("teeth", "get-dressed", "tidy-up") }
    var existingKids by remember { mutableStateOf<List<ChildProfile>>(emptyList()) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fail(r: ApiResult<*>) {
        error = when {
            r is ApiResult.Failure && r.detail.contains("already registered", true) ->
                "این ایمیل قبلاً ثبت شده — روی «قبلاً حساب دارم» بزن."
            r is ApiResult.Failure && (r.status == 401 || r.status == 403) ->
                "ایمیل، رمز عبور یا رمز والد درست نیست."
            r is ApiResult.Failure -> r.detail.ifBlank { r.code }
            r is ApiResult.Offline -> "آفلاین — الان نمی‌شود این را انجام داد."
            else -> "مشکلی پیش آمد"
        }
        busy = false
    }

    /** activate [id] on this device and finish. */
    fun activateAndFinish(id: String, name: String) {
        busy = true; error = null
        scope.launch {
            when (val t = container.authRepo.issueChildToken(id)) {
                is ApiResult.Ok -> {
                    container.tokenStore.putChildToken(id, name.trim(), t.value)
                    busy = false; onDone()
                }
                else -> fail(t)
            }
        }
    }

    val title = when (step) {
        Step.Who -> stringResource(R.string.onb_who)
        Step.Account -> stringResource(R.string.onb_account_title)
        Step.SignIn -> stringResource(R.string.signin_title)
        Step.PickChild -> stringResource(R.string.onb_pick_child_title)
        Step.Child -> stringResource(R.string.onb_child_title)
        Step.Routines -> stringResource(R.string.onb_routines_title)
        Step.Pair -> stringResource(R.string.pair_enter)
    }
    val back: (() -> Unit)? = when (step) {
        Step.Who -> null
        Step.Account, Step.Pair, Step.SignIn -> ({ error = null; step = Step.Who })
        Step.PickChild -> ({ error = null; step = Step.SignIn })
        Step.Child -> ({ error = null; step = if (existingKids.isNotEmpty()) Step.PickChild else Step.Account })
        Step.Routines -> ({ error = null; step = Step.Child })
    }

    AppScaffold(title = title, onBack = back) {
        if (step.dotIndex() >= 0) {
            StepDots(4, step.dotIndex(), Modifier.padding(top = Space.xs, bottom = Space.sm))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        when (step) {
            Step.Who -> {
                Hero("🌱")
                HintText(stringResource(R.string.onb_who_sub), Modifier.fillMaxWidth().padding(bottom = Space.sm))
                ChoiceCard("🧑‍🍼", stringResource(R.string.onb_who_parent)) { step = Step.Account }
                ChoiceCard("🧒", stringResource(R.string.onb_who_child)) { step = Step.Pair }
            }

            Step.Pair -> {
                var code by remember { mutableStateOf("") }
                var wrong by remember { mutableStateOf(false) }
                Hero("🔑")
                if (wrong) Text(stringResource(R.string.pair_wrong), color = MaterialTheme.colorScheme.error)
                DigitPad(value = code, onValue = { code = it; wrong = false }, length = 6, modifier = Modifier.fillMaxWidth())
                BigButton(stringResource(R.string.code_start), Modifier.padding(top = Space.md), enabled = !busy && code.length == 6) {
                    busy = true; error = null
                    scope.launch {
                        when (container.authRepo.pairWithCode(code)) {
                            is ApiResult.Ok -> { busy = false; onDone() }
                            else -> { wrong = true; busy = false }
                        }
                    }
                }
                GhostButton(stringResource(R.string.back)) { step = Step.Who }
            }

            Step.Account -> {
                Hero("✉️")
                HintText(stringResource(R.string.onb_account_sub))
                Field(stringResource(R.string.onb_email), email, { email = it }, keyboard = KeyboardType.Email, ltr = true)
                Field(stringResource(R.string.onb_password), pass, { pass = it }, keyboard = KeyboardType.Password)
                Field(
                    stringResource(R.string.onb_pin), pin,
                    { if (it.length <= 4) pin = it.filter(Char::isDigit) },
                    keyboard = KeyboardType.NumberPassword,
                )
                HintText(stringResource(R.string.onb_pin_help))
                BigButton(
                    stringResource(R.string.onb_create_account), Modifier.padding(top = Space.sm),
                    enabled = !busy && email.isNotBlank() && pass.length >= 6 && pin.length == 4,
                ) {
                    busy = true; error = null
                    scope.launch {
                        when (val r = container.authRepo.registerParent(email, pass, pin)) {
                            is ApiResult.Ok -> { container.useParentScope(); busy = false; step = Step.Child }
                            else -> fail(r)
                        }
                    }
                }
                GhostButton(stringResource(R.string.onb_have_account)) { error = null; step = Step.SignIn }
            }

            Step.SignIn -> {
                Hero("👋")
                HintText(stringResource(R.string.signin_sub))
                Field(stringResource(R.string.onb_email), email, { email = it }, keyboard = KeyboardType.Email, ltr = true)
                Field(stringResource(R.string.onb_password), pass, { pass = it }, keyboard = KeyboardType.Password)
                Field(
                    stringResource(R.string.onb_pin), pin,
                    { if (it.length <= 4) pin = it.filter(Char::isDigit) },
                    keyboard = KeyboardType.NumberPassword,
                )
                BigButton(
                    stringResource(R.string.signin_title), Modifier.padding(top = Space.sm),
                    enabled = !busy && email.isNotBlank() && pass.isNotBlank() && pin.length == 4,
                ) {
                    busy = true; error = null
                    scope.launch {
                        when (val r = container.authRepo.signInExisting(email, pass, pin)) {
                            is ApiResult.Ok -> {
                                container.useParentScope()
                                existingKids = (container.parentRepo.children() as? ApiResult.Ok)?.value ?: emptyList()
                                busy = false
                                step = if (existingKids.isEmpty()) Step.Child else Step.PickChild
                            }
                            else -> fail(r)
                        }
                    }
                }
            }

            Step.PickChild -> {
                Hero("🧒")
                HintText(stringResource(R.string.onb_pick_child_sub))
                existingKids.forEach { kid ->
                    Card(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { activateAndFinish(kid.childId, kid.name) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.md),
                        ) {
                            Avatar(kid.name, size = 44.dp)
                            Text("${kid.name}  (${kid.ageBand})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                GhostButton(stringResource(R.string.onb_add_child)) { error = null; step = Step.Child }
            }

            Step.Child -> {
                Hero("🧒")
                HintText(stringResource(R.string.onb_child_sub))
                Field(stringResource(R.string.onb_child_name), childName, { childName = it })
                Text(stringResource(R.string.onb_child_age), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.sm))
                AgeChoice(ageBand) { ageBand = it }
                BigButton(stringResource(R.string.onb_next), Modifier.padding(top = Space.sm), enabled = !busy && childName.isNotBlank()) {
                    busy = true; error = null
                    scope.launch {
                        when (val r = container.parentRepo.addChild(childId, childName.trim(), ageBand)) {
                            is ApiResult.Ok -> { busy = false; step = Step.Routines }
                            else -> fail(r)
                        }
                    }
                }
            }

            Step.Routines -> {
                Hero("✨")
                HintText(stringResource(R.string.onb_routines_sub))
                RoutinePicker(picked)
                BigButton(stringResource(R.string.onb_finish), Modifier.padding(top = Space.sm), enabled = !busy && picked.isNotEmpty()) {
                    busy = true; error = null
                    scope.launch {
                        for (s in STARTERS.filter { it.id in picked }) {
                            container.parentRepo.createQuest(s.id, s.title, s.icon, s.points, "daily")
                            container.parentRepo.assign(childId, s.id)
                        }
                        container.parentRepo.materialise(LocalDate.now().toString())
                        activateAndFinish(childId, childName)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(emoji: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Space.md), contentAlignment = Alignment.Center) {
        Box(
            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp),
        ) { Text(emoji, fontSize = 52.sp) }
    }
}

@Composable
private fun ChoiceCard(emoji: String, label: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(emoji, fontSize = 34.sp)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgeChoice(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        listOf("3-4" to R.string.age_3_4, "5-6" to R.string.age_5_6, "7-8" to R.string.age_7_8).forEach { (band, res) ->
            val on = selected == band
            Card(
                Modifier.weight(1f).selectable(on) { onSelect(band) },
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    stringResource(res),
                    Modifier.fillMaxWidth().padding(vertical = Space.md, horizontal = Space.xs),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutinePicker(picked: MutableList<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        STARTERS.forEach { s ->
            val on = s.id in picked
            Card(
                Modifier.toggleable(on) { if (on) picked.remove(s.id) else picked.add(s.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    "${s.icon}  ${s.title}",
                    Modifier.padding(horizontal = Space.md, vertical = Space.md),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
