package com.lucas.mobilemanager.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import java.util.Locale

/** Langues système par app sur Android 13+ ; contexte localisé et préférence persistée sur Android 10–12. */
object AppLanguage {
    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore(",")
    } else context.getSharedPreferences("language", Context.MODE_PRIVATE).getString("tag", "").orEmpty()

    fun wrap(context: Context): Context {
        val tag = selected(context)
        if (tag.isEmpty()) {
            Locale.setDefault(context.resources.configuration.locales[0])
            return context
        }
        Locale.setDefault(Locale.forLanguageTag(tag))
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(config)
    }

    fun set(activity: ComponentActivity, tag: String) {
        require(tag in listOf("", "fr", "en"))
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences("language", Context.MODE_PRIVATE).edit().putString("tag", tag).apply()
            val config = Configuration(activity.application.resources.configuration)
            config.setLocales(if (tag.isEmpty()) android.content.res.Resources.getSystem().configuration.locales else LocaleList.forLanguageTags(tag))
            // Services read application resources; activities receive a fresh context.
            @Suppress("DEPRECATION")
            activity.application.resources.updateConfiguration(config, activity.application.resources.displayMetrics)
            activity.recreate()
        }
        Locale.setDefault(if (tag.isEmpty()) android.content.res.Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag))
    }
}

open class LocalizedActivity : ComponentActivity() {
    private var appliedLanguage = ""
    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AppLanguage.selected(newBase)
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }
    override fun onResume() {
        super.onResume()
        if (appliedLanguage != AppLanguage.selected(this)) recreate()
    }
}
