package hq.playfoundry.questgrow.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * QuestGrow is Persian-only, RTL, regardless of the device locale
 * ([[DECISION-020]]). Apply in `attachBaseContext` of the Application and every
 * Activity so resources, formatting and layout direction are all `fa`.
 */
fun Context.persianRtl(): Context {
    val locale = Locale("fa")
    Locale.setDefault(locale)
    val cfg = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(cfg)
}
