package com.profecuaderno.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.profecuaderno.app.notifications.NotificationHelper
import com.profecuaderno.app.notifications.ReminderScheduler

@Composable
fun NotificationPermissionCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var askedOnce by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        allowed = NotificationHelper.canNotify(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (allowed) {
            runCatching { ReminderScheduler.ensureDaily(context) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ExternalActivityGuard.active = false
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ExternalActivityGuard.active = false
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ExternalActivityGuard.active = false
        askedOnce = true
        refreshPermissionState()
        permissionMessage = if (granted || allowed) {
            runCatching { ReminderScheduler.ensureDaily(context) }
                .fold(
                    onSuccess = { "Recordatorios activados correctamente." },
                    onFailure = { "El permiso se concedió, pero no pude programar los recordatorios. Intenta de nuevo." }
                )
        } else {
            "Android no permitió las notificaciones. Usa 'Abrir configuración' para activarlas manualmente."
        }
    }

    fun openNotificationSettings() {
        permissionMessage = null
        ExternalActivityGuard.active = true
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            DocumentPickerCompat.appSettingsIntent(context)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                runCatching { context.startActivity(DocumentPickerCompat.appSettingsIntent(context)) }
                    .onFailure { ExternalActivityGuard.active = false; permissionMessage = "No se pudo abrir la configuración del sistema." }
            }
    }

    fun requestNotificationPermission() {
        permissionMessage = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            refreshPermissionState()
            if (!allowed) openNotificationSettings()
            return
        }
        ExternalActivityGuard.active = true
        runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            .onFailure {
                ExternalActivityGuard.active = false
                permissionMessage = "No se pudo abrir la solicitud de permiso. Usa la configuración del sistema."
            }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (allowed) "Notificaciones activadas" else "Notificaciones desactivadas", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (allowed) "Recibirás avisos de cumpleaños y actividades de tu cuaderno."
                        else "Activa este permiso para recibir recordatorios de exámenes, prácticas, entregas, cumpleaños y otras fechas.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!allowed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedOnce) {
                        Button(
                            onClick = { requestNotificationPermission() },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("Solicitar permiso") }
                    }
                    OutlinedButton(
                        onClick = { openNotificationSettings() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Abrir configuración")
                    }
                }
            }

            permissionMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
