package hq.playfoundry.questgrow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One spacing scale for the whole app. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 36.dp
}

private val ContentMax = 560.dp

/**
 * The shared screen frame for every non-kid surface. A fixed header — a back
 * affordance on the leading edge, the title, an optional trailing action —
 * over scrollable, width-capped, evenly-padded content, with an optional
 * pinned bottom bar.
 */
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "بازگشت" }) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                Spacer(Modifier.width(Space.sm))
            }
            Text(
                title,
                Modifier.weight(1f).padding(horizontal = Space.sm),
                style = MaterialTheme.typography.titleLarge,
            )
            trailing?.invoke()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val col: @Composable ColumnScope.() -> Unit = {
                Column(
                    Modifier.widthIn(max = ContentMax).fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = Space.xl, vertical = Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                    content = content,
                )
            }
            if (scroll) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { col() }
            } else {
                Column(Modifier.fillMaxSize()) { col() }
            }
        }
        if (bottomBar != null) {
            Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.md)) {
                    Box(
                        Modifier.widthIn(max = ContentMax).fillMaxWidth().align(Alignment.Center),
                    ) { bottomBar() }
                }
            }
        }
    }
}

/** A titled group heading with an optional count badge. */
@Composable
fun SectionHeader(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(top = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        if (count != null && count > 0) {
            Box(
                Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .heightIn(min = 22.dp).widthIn(min = 22.dp).padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count.fa(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Muted helper line. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A round monogram avatar for a child. */
@Composable
fun Avatar(
    name: String,
    selected: Boolean = false,
    size: Dp = 52.dp,
    tint: Color = MaterialTheme.colorScheme.secondaryContainer,
    onTint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(if (selected) 3.dp else 0.dp)
            .clip(CircleShape)
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifBlank { "؟" },
            style = MaterialTheme.typography.titleLarge,
            color = onTint,
            textAlign = TextAlign.Center,
        )
    }
}

/** A selectable pill — for a small set of mutually-exclusive options. */
@Composable
fun SelectPill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
    }
}

/** A full-width selectable row (a soft card that fills when chosen). */
@Composable
fun SelectRow(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Box(
        modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

/** Progress dots for a short guided flow — the current step is a wider pill. */
@Composable
fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(8.dp)
                    .width(if (i == current) 26.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}
