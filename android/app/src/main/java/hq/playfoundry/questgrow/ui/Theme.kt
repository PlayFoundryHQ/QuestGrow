package hq.playfoundry.questgrow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF001858)
private val Cream = Color(0xFFFEF6E4)
private val Pink = Color(0xFFF582AE)
private val Sky = Color(0xFF8BD3DD)
private val Leaf = Color(0xFF7CC47C)

private val Light = lightColorScheme(
    primary = Pink, onPrimary = Color.White,
    secondary = Sky, tertiary = Leaf,
    background = Cream, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
)
private val Dark = darkColorScheme(
    primary = Pink, onPrimary = Color.White,
    secondary = Sky, tertiary = Leaf,
    background = Color(0xFF12121A), onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF1E1E28), onSurface = Color(0xFFF0F0F0),
)

// large, readable type — UX_PRINCIPLES "readable typography"
private val AppType = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp),
    titleLarge = TextStyle(fontSize = 22.sp),
    bodyLarge = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 16.sp),
)

@Composable
fun QuestGrowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppType,
        content = content,
    )
}
