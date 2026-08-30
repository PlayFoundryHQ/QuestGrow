package hq.playfoundry.questgrow.ui.child

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Tap-to-hear narration (MVP item 3 / grant §6, §10). Degrades silently. */
class Narrator(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.getDefault()
        }
    }

    fun say(text: String) {
        if (text.isBlank()) return
        runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "qg") }
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
    }
}

@Composable
fun rememberNarrator(): Narrator {
    val context = LocalContext.current
    val narrator = remember { Narrator(context) }
    DisposableEffect(Unit) { onDispose { narrator.shutdown() } }
    return narrator
}
