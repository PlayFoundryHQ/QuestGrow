package hq.playfoundry.questgrow.ui.child

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "QG.Narrator"
private val FA = Locale("fa", "IR")

/**
 * Tap-to-hear Persian narration ([[DECISION-020]]). Tries the device's default
 * TTS engine first; if it has no Persian voice, walks the other installed
 * engines (e.g. a dedicated Persian engine like AvaCore) and keeps the first
 * that accepts `fa`. Degrades silently — [hasVoice] is false and callers keep
 * the visible label.
 *
 * Needs the `<queries>` TTS_SERVICE entry in the manifest — without it, on
 * Android 11+ a separate-app engine is invisible and every attempt fails.
 *
 * The `TextToSpeech` binding is built off the main thread ([connect], driven
 * by a `LaunchedEffect`); [hasVoice] and [status] are Compose-observable.
 */
class Narrator(private val appContext: Context) {

    private var tts: TextToSpeech? = null
    var hasVoice: Boolean by mutableStateOf(false)
        private set
    /** short human-readable state, for a debug row in Settings. */
    var status: String by mutableStateOf("در حال بررسی…")
        private set

    private val enginesToTry = ArrayDeque<String?>()
    @Volatile private var started = false

    fun connect() {
        if (started) return
        started = true
        start(null)
    }

    private fun start(engine: String?) {
        runCatching { tts?.shutdown() }
        tts = TextToSpeech(appContext, { code ->
            if (code != TextToSpeech.SUCCESS) {
                Log.w(TAG, "engine=${engine ?: "default"} init failed ($code)")
                advance(engine, failReason = "موتور صدا در دسترس نیست")
                return@TextToSpeech
            }
            val lang = runCatching { tts?.setLanguage(FA) }.getOrNull() ?: TextToSpeech.LANG_NOT_SUPPORTED
            val ok = lang == TextToSpeech.LANG_AVAILABLE ||
                lang == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                lang == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            val name = engine ?: runCatching { tts?.defaultEngine }.getOrNull() ?: "default"
            Log.i(TAG, "engine=$name setLanguage(fa-IR)=$lang ok=$ok")
            if (ok) {
                hasVoice = true
                status = "متصل ($name)"
            } else {
                advance(engine, failReason = "این موتور صدا فارسی ندارد")
            }
        }, engine)
    }

    private fun advance(engine: String?, failReason: String) {
        if (enginesToTry.isEmpty() && engine == null) {
            runCatching {
                tts?.engines?.map { it.name }?.forEach { if (it != null && it != tts?.defaultEngine) enginesToTry.addLast(it) }
            }
            Log.i(TAG, "other engines to try: $enginesToTry")
        }
        val next = if (enginesToTry.isNotEmpty()) enginesToTry.removeFirst() else null
        if (next != null && next != engine) {
            start(next)
        } else {
            hasVoice = false
            status = "$failReason — یک موتور TTS فارسی نصب و پیش‌فرض کن"
        }
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
