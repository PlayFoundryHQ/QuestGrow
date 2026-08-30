package hq.playfoundry.questgrow.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.R
import androidx.compose.ui.unit.sp
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.ui.DigitPad
import hq.playfoundry.questgrow.ui.GhostButton
import hq.playfoundry.questgrow.ui.Space

/** The everyday grown-up gate: 4-digit PIN, no email/password (stored on device). */
@Composable
fun ParentGate(container: AppContainer, onUnlocked: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length == 4 && !busy) {
            busy = true; wrong = false
            when (container.authRepo.unlockWithPin(pin)) {
                is ApiResult.Ok -> onUnlocked()
                else -> { wrong = true; pin = ""; busy = false }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 44.sp)
        Text(stringResource(R.string.gate_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            if (wrong) stringResource(R.string.gate_wrong) else " ",
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        DigitPad(value = pin, onValue = { if (!busy) pin = it }, length = 4)
        GhostButton(stringResource(R.string.back)) { onCancel() }
    }
}
