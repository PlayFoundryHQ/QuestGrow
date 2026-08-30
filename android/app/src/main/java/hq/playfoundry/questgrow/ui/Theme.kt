package hq.playfoundry.questgrow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import hq.playfoundry.questgrow.R

// Warm, calm palette (UX_PRINCIPLES). Ink / Cream / Pink / Sky / Leaf, kept
// from the first client — v0.6.0 fills in the container tones so cards and
// chips read as soft tints instead of stark white-on-cream.
private val Ink = Color(0xFF001858)
private val Cream = Color(0xFFFEF6E4)
private val Pink = Color(0xFFF582AE)
private val PinkDeep = Color(0xFFD84D86)
private val Sky = Color(0xFF8BD3DD)
private val Leaf = Color(0xFF7CC47C)
private val Sun = Color(0xFFF9C74F)

private val Light = lightColorScheme(
    primary = PinkDeep, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E6), onPrimaryContainer = Color(0xFF5A0A2C),
    secondary = Color(0xFF2E7D8A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEEF2), onSecondaryContainer = Color(0xFF06313A),
    tertiary = Color(0xFF3F8B3F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6EFD6), onTertiaryContainer = Color(0xFF0E320E),
    background = Cream, onBackground = Ink,
    surface = Color(0xFFFFFBF3), onSurface = Ink,
    surfaceVariant = Color(0xFFEFE7D4), onSurfaceVariant = Color(0xFF5A5540),
    surfaceContainer = Color(0xFFF7EFDE),
    surfaceContainerHigh = Color(0xFFF2E9D6),
    outline = Color(0xFFC9BEA3), outlineVariant = Color(0xFFE2D8C1),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Pink, onPrimary = Color(0xFF2A0A18),
    primaryContainer = Color(0xFF7A2B4E), onPrimaryContainer = Color(0xFFFFD9E6),
    secondary = Sky, onSecondary = Color(0xFF04222A),
    secondaryContainer = Color(0xFF1E4A52), onSecondaryContainer = Color(0xFFCDEEF2),
    tertiary = Leaf, onTertiary = Color(0xFF07240F),
    tertiaryContainer = Color(0xFF2C5A2C), onTertiaryContainer = Color(0xFFD6EFD6),
    background = Color(0xFF14131C), onBackground = Color(0xFFF0EEF5),
    surface = Color(0xFF1F1E2A), onSurface = Color(0xFFF0EEF5),
    surfaceVariant = Color(0xFF35333F), onSurfaceVariant = Color(0xFFCDC9D8),
    surfaceContainer = Color(0xFF262530),
    surfaceContainerHigh = Color(0xFF302F3B),
    outline = Color(0xFF635F70), outlineVariant = Color(0xFF3B3A45),
    error = Color(0xFFF2B8B5), onError = Color(0xFF3B1310),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
)

/** Semantic accents that aren't Material roles — used sparingly for delight. */
val AccentSun get() = Sun
val AccentSky get() = Sky
val AccentLeaf get() = Leaf

/** Vazirmatn (SIL OFL) — the Persian UI face. */
val Vazir = FontFamily(
    Font(R.font.vazirmatn_400, FontWeight.Normal),
    Font(R.font.vazirmatn_500, FontWeight.Medium),
    Font(R.font.vazirmatn_700, FontWeight.Bold),
)

// Large, readable type. Persian needs a little more line height than Latin.
private val AppType = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 50.sp),
        displaySmall = displaySmall.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 42.sp),
        headlineMedium = headlineMedium.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 36.sp),
        headlineSmall = headlineSmall.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 32.sp),
        titleLarge = titleLarge.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = titleMedium.copy(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp),
        bodyLarge = bodyLarge.copy(fontFamily = Vazir, fontSize = 17.sp, lineHeight = 28.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Vazir, fontSize = 15.sp, lineHeight = 24.sp),
        labelLarge = labelLarge.copy(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = labelMedium.copy(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun QuestGrowTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppType,
        shapes = AppShapes,
        content = content,
    )
}
