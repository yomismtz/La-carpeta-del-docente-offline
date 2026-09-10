package com.profecuaderno.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.profecuaderno.app.security.AppSecurityManager
import com.profecuaderno.app.data.TeacherDbHelper
import com.profecuaderno.app.notifications.ReminderScheduler
import com.profecuaderno.app.ui.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("agenda_preferences", MODE_PRIVATE) }
            val appLanguage = remember { AppLanguagePrefs.load(context) }
            var selectedTheme by remember { mutableStateOf(AgendaThemeStyle.fromKey(prefs.getString("theme_style", null))) }
            val activeTheme = selectedTheme ?: AgendaThemeStyle.MINT_LAVENDER
            val darkMode = prefs.getBoolean("ui_dark", false)
            val fontScale = prefs.getFloat("font_scale", 1f)
            val fontStyle = AppFontStyle.fromKey(prefs.getString("font_style", null))

            fun saveTheme(style: AgendaThemeStyle) {
                prefs.edit().putString("theme_style", style.key).apply()
                selectedTheme = style
            }

            CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                ProfeCuadernoTheme(style = activeTheme, darkMode = darkMode, fontScale = fontScale, fontStyle = fontStyle) {
                    val db = remember { TeacherDbHelper(context) }
                    LaunchedEffect(Unit) { ReminderScheduler.ensureDaily(context) }
                    var refresh by remember { mutableIntStateOf(0) }
                    val teacher = remember(refresh) { db.getTeacher() }
                    var unlocked by remember { mutableStateOf(!AppSecurityManager.isLockEnabled(context)) }
                    val lifecycleOwner = LocalLifecycleOwner.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP && AppSecurityManager.isLockEnabled(context) && !ExternalActivityGuard.active) unlocked = false
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    NotebookBackground(style = activeTheme) {
                        when {
                            selectedTheme == null -> ThemeSelectionScreen(initial = AgendaThemeStyle.MINT_LAVENDER, onSelected = { saveTheme(it) })
                            !unlocked && AppSecurityManager.isLockEnabled(context) -> AppLockScreen(onUnlocked = { unlocked = true })
                            teacher == null -> TeacherSetupScreen(onSave = { db.saveTeacher(it); refresh++ })
                            else -> ProfeCuadernoApp(db = db, onDataChanged = { refresh++ }, globalRefresh = refresh, currentTheme = activeTheme, onThemeChanged = { saveTheme(it) })
                        }
                    }
                }
            }
        }
    }
}
