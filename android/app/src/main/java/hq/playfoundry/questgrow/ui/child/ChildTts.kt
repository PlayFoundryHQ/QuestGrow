package hq.playfoundry.questgrow.ui.child

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val FA = Locale("fa")

/**
 * Tap-to-hear Persian narration ([[DECISION-020]]). Tries the device's default
 * TTS engine first; if it has no Persian voice, walks the other installed
 * engines (e.g. a dedicated Persian engine like AvaCore) and keeps the first
 * that accepts `fa`. Degrades silently — [hasVoice] is false and callers keep
 * the visible label.
 *
 * The `TextToSpeech` binding is built off the main thread ([connect], driven by
 * a `LaunchedEffect`) so a slow/absent TTS service never stalls the board.
 * [hasVoice] is Compose-observable so the "بشنو" button appears once ready.
 */
class Narrator(private val appContext: Context) {

    private var tts: TextToSpeech? = null
    var hasVoice: Boolean by mutableStateOf(false)
        private set

    private val enginesToTry = ArrayDeque<String?>()
    @Volatile private var started = false

    /** Build the TTS binding. Safe to call more than once; call off the UI thread. */
    fun connect() {
        if (started) return
        started = true
        start(null)
    }

    private fun start(engine: String?) {
        runCatching { tts?.shutdown() }
        tts = TextToSpeech(appContext, { status ->
            val ok = status == TextToSpeech.SUCCESS && persianAccepted()
            if (ok) {
                hasVoice = true
                tts?.language = FA
            } else {
                if (enginesToTry.isEmpty() && engine == null) {
                    runCatching {
                        tts?.engines?.map { it.name }?.forEach { if (it != null) enginesToTry.addLast(it) }
                    }
                }
                val next = if (enginesToTry.isNotEmpty()) enginesToTry.removeFirst() else null
                if (next != null && next != engine) start(next) else hasVoice = false
            }
        }, engine)
    }

    private fun persianAccepted(): Boolean {
        val r = runCatching { tts?.setLanguage(FA) }.getOrNull() ?: return false
        return r == TextToSpeech.LANG_AVAILABLE ||
            r == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    fun say(text: String) {
        if (text.isBlank() || !hasVoice) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "qg") }
    }

    fun stop() { runCatching { tts?.stop() } }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }
}

@Composable
fun rememberNarrator(): Narrator {
    val context = LocalContext.current.applicationContext
    val narrator = remember { Narrator(context) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { narrator.connect() } }
    DisposableEffect(Unit) { onDispose { narrator.shutdown() } }
    return narrator
}
