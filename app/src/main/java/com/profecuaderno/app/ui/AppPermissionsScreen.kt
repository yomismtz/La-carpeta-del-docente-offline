package com.profecuaderno.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun AppPermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val legacyFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val fileSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ExternalActivityGuard.active = false
        refresh++
        infoMessage = if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            "Acceso a archivos concedido."
        } else {
            "El acceso a archivos no fue concedido. Puedes continuar y autorizarlo después."
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ExternalActivityGuard.active = false
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ExternalActivityGuard.active = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val refreshKey = refresh
    val notificationsGranted = remember(refreshKey) {
        Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS)
    }
    val calendarGranted = remember(refreshKey) {
        has(Manifest.permission.READ_CALENDAR) && has(Manifest.permission.WRITE_CALENDAR)
    }
    val filesGranted = remember(refreshKey) {
        when {
            Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= 23 -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> true
        }
    }

    fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
            runCatching { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure { infoMessage = "No se pudo abrir el permiso de notificaciones." }
        }
    }

    fun requestCalendar() {
        if (!calendarGranted) {
            runCatching {
                calendarLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }.onFailure { infoMessage = "No se pudo abrir el permiso de calendario." }
        }
    }

    fun requestFiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            infoMessage = "Android abrirá la pantalla del sistema para autorizar archivos. Activa el permiso y usa Atrás para regresar a la app."
            val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            ExternalActivityGuard.active = true
            runCatching { fileSettingsLauncher.launch(appIntent) }
                .onFailure {
                    val generalIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    runCatching { fileSettingsLauncher.launch(generalIntent) }
                        .onFailure {
                            ExternalActivityGuard.active = false
                            infoMessage = "No se pudo abrir la configuración de acceso a archivos."
                        }
                }
        } else if (!filesGranted) {
            runCatching { legacyFilesLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
                .onFailure { infoMessage = "No se pudo abrir el permiso de archivos." }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Permisos de la aplicación", style = MaterialTheme.typography.headlineSmall)
        Text("La app solo solicita notificaciones, calendario y archivos.")

        PermissionCard("Notificaciones", notificationsGranted, Icons.Default.Notifications) { requestNotification() }
        PermissionCard("Calendario", calendarGranted, Icons.Default.CalendarMonth) { requestCalendar() }
        PermissionCard("Archivos del teléfono", filesGranted, Icons.Default.Folder) { requestFiles() }

        Text(
            if (filesGranted) "Acceso a archivos: concedido ✓. La app usará su explorador interno."
            else "Acceso a archivos: no concedido. Puedes continuar y autorizarlo más tarde.",
            style = MaterialTheme.typography.bodySmall
        )

        infoMessage?.let {
            AssistChip(onClick = { infoMessage = null }, label = { Text(it) })
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("Continuar") }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (granted) "Concedido" else "No concedido", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClick, enabled = !granted) {
                Text(if (granted) "Listo" else "Permitir")
            }
        }
    }
}
