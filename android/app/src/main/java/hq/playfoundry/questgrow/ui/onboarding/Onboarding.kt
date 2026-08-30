package hq.playfoundry.questgrow.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.R
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.ui.BigButton
import hq.playfoundry.questgrow.ui.Field
import hq.playfoundry.questgrow.ui.STARTERS
import hq.playfoundry.questgrow.ui.SecondaryButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private enum class Step { Who, Account, Child, Routines, Pair }

/** Guided first-run: pick who owns the device → parent wizard, or a child
 *  device pairs with a 6-digit code. */
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

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun show(r: ApiResult<*>) {
        error = when {
            r is ApiResult.Failure -> r.detail.ifBlank { r.code }
            r is ApiResult.Offline -> "آفلاین — الان نمی‌شود این را انجام داد."
            else -> "مشکلی پیش آمد"
        }
        busy = false
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when (step) {
            Step.Who -> {
                Text(stringResource(R.string.onb_who), style = MaterialTheme.typography.headlineSmall)
                BigButton(stringResource(R.string.onb_who_parent)) { step = Step.Account }
                SecondaryButton(stringResource(R.string.onb_who_child), minHeight = 64.dp) { step = Step.Pair }
            }

            Step.Pair -> {
                var code by remember { mutableStateOf("") }
                var wrong by remember { mutableStateOf(false) }
                Text(stringResource(R.string.pair_enter), style = MaterialTheme.typography.headlineSmall)
                if (wrong) Text(stringResource(R.string.pair_wrong), color = MaterialTheme.colorScheme.error)
                hq.playfoundry.questgrow.ui.DigitPad(value = code, onValue = { code = it; wrong = false }, length = 6)
                BigButton(stringResource(R.string.code_start), enabled = !busy && code.length == 6) {
                    busy = true; error = null
                    scope.launch {
                        when (val r = container.authRepo.pairWithCode(code)) {
                            is ApiResult.Ok -> { busy = false; onDone() }
                            else -> { wrong = true; busy = false }
                        }
                    }
                }
                SecondaryButton(stringResource(R.string.back), minHeight = 56.dp) { step = Step.Who }
            }

            Step.Account -> {
                Text(stringResource(R.string.onb_account_title), style = MaterialTheme.typography.headlineSmall)
                Field(stringResource(R.string.onb_email), email, { email = it }, keyboard = KeyboardType.Email, ltr = true)
                Field(stringResource(R.string.onb_password), pass, { pass = it }, keyboard = KeyboardType.Password)
                Field(
                    stringResource(R.string.onb_pin), pin,
                    { if (it.length <= 4) pin = it.filter(Char::isDigit) },
                    keyboard = KeyboardType.NumberPassword,
                )
                Text(stringResource(R.string.onb_pin_help), style = MaterialTheme.typography.bodyMedium)
                BigButton(
                    stringResource(R.string.onb_create_account),
                    enabled = !busy && email.isNotBlank() && pass.length >= 6 && pin.length == 4,
                ) {
                    busy = true; error = null
                    scope.launch {
                        when (val r = container.authRepo.registerParent(email, pass, pin)) {
                            is ApiResult.Ok -> { container.useParentScope(); busy = false; step = Step.Child }
                            else -> show(r)
                        }
                    }
                }
            }

            Step.Child -> {
                Text(stringResource(R.string.onb_child_title), style = MaterialTheme.typography.headlineSmall)
                Field(stringResource(R.string.onb_child_name), childName, { childName = it })
                Text(stringResource(R.string.onb_child_age), style = MaterialTheme.typography.titleMedium)
                AgeChoice(ageBand) { ageBand = it }
                BigButton(stringResource(R.string.onb_next), enabled = !busy && childName.isNotBlank()) {
                    busy = true; error = null
                    scope.launch {
                        when (val r = container.parentRepo.addChild(childId, childName.trim(), ageBand)) {
                            is ApiResult.Ok -> { busy = false; step = Step.Routines }
                            else -> show(r)
                        }
                    }
                }
            }

            Step.Routines -> {
                Text(stringResource(R.string.onb_routines_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.onb_routines_help), style = MaterialTheme.typography.bodyMedium)
                RoutinePicker(picked)
                BigButton(stringResource(R.string.onb_finish), enabled = !busy && picked.isNotEmpty()) {
                    busy = true; error = null
                    scope.launch {
                        for (s in STARTERS.filter { it.id in picked }) {
                            container.parentRepo.createQuest(s.id, s.title, s.icon, s.points, "daily")
                            container.parentRepo.assign(childId, s.id)
                        }
                        container.parentRepo.materialise(LocalDate.now().toString())
                        when (val t = container.authRepo.issueChildToken(childId)) {
                            is ApiResult.Ok -> {
                                container.authRepo.useChildToken(t.value)
                                container.tokenStore.setDefaultChild(childId, childName.trim())
                                busy = false; onDone()
                            }
                            else -> show(t)
                        }
                    }
                }
                SecondaryButton(stringResource(R.string.back), minHeight = 56.dp) { step = Step.Child }
            }
        }
    }
}

@Composable
private fun AgeChoice(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("3-4" to R.string.age_3_4, "5-6" to R.string.age_5_6, "7-8" to R.string.age_7_8).forEach { (band, res) ->
            val on = selected == band
            Card(
                Modifier.fillMaxWidth().selectable(on) { onSelect(band) },
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    stringResource(res),
                    Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutinePicker(picked: MutableList<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        STARTERS.forEach { s ->
            val on = s.id in picked
            Card(
                Modifier.toggleable(on) { if (on) picked.remove(s.id) else picked.add(s.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (on) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    "${s.icon}  ${s.title}",
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (on) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
