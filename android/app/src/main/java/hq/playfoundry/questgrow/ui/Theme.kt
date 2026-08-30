package hq.playfoundry.questgrow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import hq.playfoundry.questgrow.R

// Warm, calm palette (UX_PRINCIPLES). Kept from the first client.
private val Ink = Color(0xFF001858)
private val Cream = Color(0xFFFEF6E4)
private val Pink = Color(0xFFF582AE)
private val Sky = Color(0xFF8BD3DD)
private val Leaf = Color(0xFF7CC47C)

private val Light = lightColorScheme(
    primary = Pink, onPrimary = Color.White,
    secondary = Sky, onSecondary = Ink,
    tertiary = Leaf, onTertiary = Ink,
    background = Cream, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFF3ECDC), onSurfaceVariant = Color(0xFF4A4636),
    error = Color(0xFFB3261E), onError = Color.White,
)
private val Dark = darkColorScheme(
    primary = Pink, onPrimary = Color(0xFF2A0A18),
    secondary = Sky, onSecondary = Color(0xFF04222A),
    tertiary = Leaf, onTertiary = Color(0xFF07240F),
    background = Color(0xFF12121A), onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF1E1E28), onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF2A2A36), onSurfaceVariant = Color(0xFFC9C6D6),
    error = Color(0xFFF2B8B5), onError = Color(0xFF3B1310),
)

/** Vazirmatn (SIL OFL) — the Persian UI face. Latin digits/letters render from it too. */
val Vazir = FontFamily(
    Font(R.font.vazirmatn_400, FontWeight.Normal),
    Font(R.font.vazirmatn_500, FontWeight.Medium),
    Font(R.font.vazirmatn_700, FontWeight.Bold),
)

// Large, readable type (UX_PRINCIPLES "readable typography"). Persian needs a
// touch more line height than Latin defaults.
private val AppType = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 44.sp),
        headlineMedium = headlineMedium.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 36.sp),
        headlineSmall = headlineSmall.copy(fontFamily = Vazir, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 32.sp),
        titleLarge = titleLarge.copy(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 30.sp),
        titleMedium = titleMedium.copy(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
        bodyLarge = bodyLarge.copy(fontFamily = Vazir, fontSize = 18.sp, lineHeight = 30.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Vazir, fontSize = 16.sp, lineHeight = 26.sp),
        labelLarge = labelLarge.copy(fontFamily = Vazir, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    )
}

@Composable
fun QuestGrowTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppType,
        content = content,
    )
}
