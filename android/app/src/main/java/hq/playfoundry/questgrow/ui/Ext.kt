package hq.playfoundry.questgrow.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.StateFlow

/** thin alias so screens don't import the coroutines-compose symbol everywhere. */
@Composable
fun <T> StateFlow<T>.collectAsStateSafe(): State<T> = collectAsState()

/** honours the OS "remove animations" accessibility setting (grant §10). */
@Composable
fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
