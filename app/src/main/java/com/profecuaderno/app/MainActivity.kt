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
            val appLanguage = remember { AppLanguagePrefs.load(context) }
            var selectedTheme by remember { mutableStateOf(AgendaThemeStyle.MINT_LAVENDER) }
            var darkMode by remember { mutableStateOf(false) }
            var fontScale by remember { mutableFloatStateOf(1f) }
            var fontStyle by remember { mutableStateOf(AppFontStyle.SANS) }

            CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                ProfeCuadernoTheme(style = selectedTheme, darkMode = darkMode, fontScale = fontScale, fontStyle = fontStyle) {
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

                    NotebookBackground(style = selectedTheme) {
                        when {
                            !unlocked && AppSecurityManager.isLockEnabled(context) -> AppLockScreen(onUnlocked = { unlocked = true })
                            teacher == null -> TeacherSetupScreen(onSave = { db.saveTeacher(it); refresh++ })
                            else -> ProfeCuadernoApp(
                                db = db,
                                onDataChanged = { refresh++ },
                                globalRefresh = refresh,
                                currentTheme = selectedTheme,
                                currentDarkMode = darkMode,
                                currentFontScale = fontScale,
                                currentFontStyle = fontStyle,
                                onAppearanceChanged = { style, dark, scale, font ->
                                    selectedTheme = style
                                    darkMode = dark
                                    fontScale = scale
                                    fontStyle = font
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
