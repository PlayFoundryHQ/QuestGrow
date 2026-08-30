package hq.playfoundry.questgrow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import hq.playfoundry.questgrow.R

/** ≥64dp touch target — UX_PRINCIPLES "touch targets ≥ 64×64pt" (child surface). */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) { Text(text, style = MaterialTheme.typography.titleLarge) }
}

/**
 * Secondary action — a soft filled-tonal button (reads as tappable without
 * competing with the primary). 52dp default; child-surface callers pass
 * [minHeight] = 64.dp.
 */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 52.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Tertiary action — plain text button, for low-emphasis choices ("cancel", "back"). */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
    }
}

@Composable
fun ErrorRetry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onGrownUp: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        BigButton(stringResource(R.string.retry), modifier = Modifier.padding(top = 16.dp)) { onRetry() }
        if (onGrownUp != null) {
            SecondaryButton(stringResource(R.string.grownups), modifier = Modifier.padding(top = 8.dp), minHeight = 64.dp) { onGrownUp() }
        }
    }
}

/** Labelled single-line field. */
@Composable
fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    ltr: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = if (ltr) {
            LocalTextStyle.current.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left)
        } else LocalTextStyle.current,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A numeric keypad for the parent gate / pairing code. [length] digits, calls
 * [onComplete] when full. Deliberately plain — this is the grown-up gate.
 */
@Composable
fun DigitPad(
    value: String,
    onValue: (String) -> Unit,
    length: Int = 4,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(length) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape)
                        .background(
                            if (i < value.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Box(Modifier.size(76.dp))
                    } else FilledTonalButton(
                        onClick = {
                            when {
                                key == "⌫" -> if (value.isNotEmpty()) onValue(value.dropLast(1))
                                value.length < length -> onValue(value + key)
                            }
                        },
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(if (key == "⌫") key else key.faDigits(), style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }
}
