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
            val savedAppearance = remember { AppearancePrefs.load(context) }
            var selectedTheme by remember { mutableStateOf(savedAppearance.theme) }
            var darkMode by remember { mutableStateOf(savedAppearance.darkMode) }
            var fontScale by remember { mutableFloatStateOf(savedAppearance.fontScale) }
            var fontStyle by remember { mutableStateOf(savedAppearance.fontStyle) }

            CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
                ProfeCuadernoTheme(style = selectedTheme, darkMode = darkMode, fontScale = fontScale, fontStyle = fontStyle) {
                    val db = remember { TeacherDbHelper(context) }
                    LaunchedEffect(Unit) { ReminderScheduler.ensureDaily(context) }
                    var refresh by remember { mutableIntStateOf(0) }
                    val teacher = remember(refresh) { db.getTeacher() }
                    var unlocked by remember { mutableStateOf(!AppSecurityManager.isLockEnabled(context)) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val permissionPrefs = remember { context.getSharedPreferences("permission_setup", 0) }
                    var showPermissionSetup by remember { mutableStateOf(!permissionPrefs.getBoolean("completed", false)) }

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
                            showPermissionSetup -> AppPermissionsScreen(onContinue = {
                                permissionPrefs.edit().putBoolean("completed", true).apply()
                                showPermissionSetup = false
                            })
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
                                    AppearancePrefs.save(
                                        context,
                                        AppearanceSettings(style, dark, scale, font)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
